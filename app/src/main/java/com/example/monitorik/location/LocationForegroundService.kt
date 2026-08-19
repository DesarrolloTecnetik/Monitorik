package com.example.monitorik.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground Service de ubicación en segundo plano.
 *
 * Requiere que ACCESS_FINE_LOCATION y (en Android 10+) ACCESS_BACKGROUND_LOCATION
 * ya estén concedidos antes de llamar a start() — ver MainActivity_integration.kt.
 *
 * Sin este Service con foregroundServiceType="location", Android mata las
 * actualizaciones de ubicación en cuanto la app se minimiza (Android 8+) o de
 * plano las bloquea a nivel de sistema (Android 10+).
 */
class LocationForegroundService : Service() {

    companion object {
        private const val TAG = "LOC_DEBUG"

        const val CHANNEL_ID = "monitorik_location_channel"
        const val NOTIFICATION_ID = 5001
        const val ACTION_STOP = "com.tecnetik.com.tecnetik.monitorik.action.STOP_LOCATION"

        private const val ENDPOINT_URL = "https://tecnserv.com/monitorik/ajax/ubicacion_update.php"
        private const val DEFAULT_INTERVAL_MIN = 5L

        fun start(context: Context) {
            Log.d(TAG, "LocationForegroundService.start() llamado")
            val intent = Intent(context, LocationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationForegroundService::class.java))
        }
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private val httpClient = OkHttpClient()
    private var intervalMinutes = DEFAULT_INTERVAL_MIN

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LocationForegroundService.onCreate()")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "LocationForegroundService.onStartCommand() action=${intent?.action}")

        if (intent?.action == ACTION_STOP) {
            stopLocationUpdates()
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "LocationForegroundService: startForeground() OK")
        } catch (e: Exception) {
            // En Android 14+ esto lanza SecurityException si falta el permiso
            // FOREGROUND_SERVICE_LOCATION en el manifest, o si el sistema niega
            // el arranque en background. Si ves este log, el manifest es el problema.
            Log.e(TAG, "LocationForegroundService: FALLÓ startForeground() -> ${e.message}", e)
            stopSelf()
            return START_NOT_STICKY
        }

        // Ya hay un ciclo corriendo — no arranques uno nuevo encima si vuelven a
        // llamar a start() (p.ej. cada onPageFinished del WebView). Antes esto
        // podía apilar múltiples LocationCallback sobre el mismo fusedLocationClient.
        if (locationCallback != null) {
            Log.d(TAG, "LocationForegroundService: ya había un ciclo de ubicación activo, no se duplica")
            return START_STICKY
        }

        fetchIntervalConfig { minutes ->
            Log.d(TAG, "LocationForegroundService: intervalo configurado = $minutes min")
            intervalMinutes = minutes
            startLocationUpdates()
        }

        return START_STICKY
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "LocationForegroundService: sin permiso ACCESS_FINE_LOCATION, deteniendo servicio")
            stopSelf()
            return
        }

        val intervalMillis = intervalMinutes * 60_000L
        Log.d(TAG, "LocationForegroundService: registrando location updates cada ${intervalMillis}ms")

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                Log.d(TAG, "LocationForegroundService: ubicación recibida lat=${location.latitude} lng=${location.longitude}")
                reportLocation(location.latitude, location.longitude, location.accuracy)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback as LocationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    private fun reportLocation(lat: Double, lng: Double, precision: Float) {
        val token = TokenManager.getToken(this)
        if (token == null) {
            Log.w(TAG, "LocationForegroundService.reportLocation: SIN token, no se envía nada")
            return
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val json = JSONObject().apply {
            put("action", "reportar")
            put("lat", lat)
            put("lng", lng)
            put("precision", precision)
            put("capturado_en", sdf.format(Date()))
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(ENDPOINT_URL)
            .header("X-Monitorik-Device", token)
            .post(body)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "LocationForegroundService.reportLocation: fallo de red -> ${e.message}", e)
                // Silencioso: se reintenta en el siguiente ciclo de ubicación.
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "LocationForegroundService.reportLocation: respuesta HTTP ${response.code}")
                response.close()
                // 401 = token inválido o revocado (ej. admin lo revocó por robo/pérdida)
                if (response.code == 401) {
                    TokenManager.clearToken(this@LocationForegroundService)
                    stopSelf()
                }
            }
        })
    }

    private fun fetchIntervalConfig(onResult: (Long) -> Unit) {
        val token = TokenManager.getToken(this)
        if (token == null) {
            Log.w(TAG, "LocationForegroundService.fetchIntervalConfig: sin token, uso intervalo por defecto")
            onResult(DEFAULT_INTERVAL_MIN)
            return
        }

        val request = Request.Builder()
            .url("$ENDPOINT_URL?action=config")
            .header("X-Monitorik-Device", token)
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "LocationForegroundService.fetchIntervalConfig: fallo de red -> ${e.message}", e)
                onResult(DEFAULT_INTERVAL_MIN)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val minutes = try {
                        JSONObject(resp.body?.string().orEmpty())
                            .optLong("intervalo_minutos", DEFAULT_INTERVAL_MIN)
                    } catch (e: Exception) {
                        Log.e(TAG, "LocationForegroundService.fetchIntervalConfig: respuesta inválida -> ${e.message}", e)
                        DEFAULT_INTERVAL_MIN
                    }
                    onResult(minutes)
                }
            }
        })
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Monitorik")
            .setContentText("Registrando tu ubicación en segundo plano")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation) // TODO: reemplazar por ícono propio de la app
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ubicación Monitorik",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación persistente mientras se registra la ubicación"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "LocationForegroundService.onDestroy()")
        stopLocationUpdates()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}