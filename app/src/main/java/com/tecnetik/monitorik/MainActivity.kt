

package com.tecnetik.monitorik

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.TextView
import android.widget.Button
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.view.View
import androidx.core.view.WindowCompat
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tecnetik.monitorik.location.LocationForegroundService
import com.tecnetik.monitorik.location.TokenManager
import com.google.firebase.messaging.FirebaseMessaging
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Request codes para el flujo de permisos de ubicación en background ---
private const val REQUEST_FINE_LOCATION = 100
private const val REQUEST_BACKGROUND_LOCATION = 101
private const val REQUEST_NOTIFICATIONS = 102

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var imgSplash: ImageView
    private lateinit var errorView: TextView
    private lateinit var reconnectingOverlay: View
    private lateinit var reconnectMessage: TextView
    private lateinit var btnManualRetry: Button
    private var countdownRunnable: Runnable? = null
    private var countdownRemainingSec: Int = 0
    private val URL_DESTINO = "https://tecnserv.com/monitorik/"

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var photoURI: Uri? = null
    private var isWebViewReady = false
    private var pendingFcmToken: String? = null

    private var _isInBackground = false
    private var _pendingPushType: String? = null
    private var _pendingActionUrl: String? = null
    private var _pendingClickAction: String? = null

    // --- Manejo de reconexión silenciosa (sin mostrar error nativo ni texto) ---
    private val retryHandler = Handler(Looper.getMainLooper())
    private val retryIntervalMs = 10000L
    private var isOffline = false
    private var lastUrl: String = URL_DESTINO

    private val connectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // En cuanto vuelva la red, reintenta de inmediato en vez de esperar el intervalo
            runOnUiThread {
                if (isOffline) {
                    Log.d("FCM_DEBUG", "Red disponible de nuevo, reintentando: $lastUrl")
                    webView.loadUrl(lastUrl)
                }
            }
        }

        override fun onLost(network: Network) {
            // Cuando se pierde la conexión, muestra el overlay de inmediato
            runOnUiThread {
                if (!isOffline) {
                    isOffline = true
                    isWebViewReady = false
                    Log.d("FCM_DEBUG", "Conexión perdida, mostrando overlay de reconexión")
                    mostrarReconnectingOverlay()
                    scheduleRetry()
                }
            }
        }
    }
    // --------------------------------------------------------------------------

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val readGranted = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                (permissions[Manifest.permission.READ_MEDIA_IMAGES] == true)
            else ->
                (permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true)
        }

        if (fileUploadCallback != null) {
            if (cameraGranted || readGranted) {
                startFileChooser()
            } else {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = null
                photoURI = null
            }
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (fileUploadCallback != null) {
            var results: Array<Uri>? = null
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                if (data == null || (data.data == null && data.clipData == null)) {
                    photoURI?.let { results = arrayOf(it) }
                } else if (data.clipData != null) {
                    val count = data.clipData!!.itemCount
                    results = Array(count) { i -> data.clipData!!.getItemAt(i).uri }
                } else if (data.data != null) {
                    results = arrayOf(data.data!!)
                }
            }
            fileUploadCallback?.onReceiveValue(results)
            fileUploadCallback = null
            photoURI = null
        } else {
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        MonitorikApp.activeWebView = null

        WindowCompat.setDecorFitsSystemWindows(window, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.parseColor("#111239")
            window.navigationBarColor = Color.parseColor("#0D0D2B")
        }

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webViewMonitorik)
        imgSplash = findViewById(R.id.imgSplash)
        errorView = findViewById(R.id.errorView)
        reconnectingOverlay = findViewById(R.id.reconnectingOverlay)
        reconnectMessage = findViewById(R.id.reconnectMessage)
        btnManualRetry = findViewById(R.id.btnManualRetry)

        // Ya no usamos errorView como texto clickeable de "reintentar",
        // pero lo dejamos referenciado por si el layout aún lo declara.
        errorView.visibility = View.GONE
        reconnectingOverlay.visibility = View.GONE

        imgSplash.visibility = View.VISIBLE

        configurarWebView()
        solicitarPermisosEsenciales()
        obtenerTokenFCM()
        registrarNetworkCallback()

        procesarIntentPush(intent)

        // Listener para reintento manual
        btnManualRetry.setOnClickListener {
            // Cancela cualquier reintento automático programado y fuerza uno inmediato
            retryHandler.removeCallbacksAndMessages(null)
            stopCountdown()
            reconnectMessage.text = "Reintentando..."
            if (isOffline) {
                Log.d("FCM_DEBUG", "Reintento manual: $lastUrl")
                webView.loadUrl(lastUrl)
            }
        }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            lastUrl = URL_DESTINO
            // Verifica conectividad antes de cargar
            if (!isNetworkAvailable()) {
                Log.d("FCM_DEBUG", "Sin conexión en onCreate, mostrando overlay")
                isOffline = true
                isWebViewReady = false
                mostrarReconnectingOverlay()
                scheduleRetry()
            } else {
                webView.loadUrl(URL_DESTINO)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        procesarIntentPush(intent)
    }

    override fun onResume() {
        super.onResume()
        _isInBackground = false
        MonitorikApp.activeWebView = webView
    }

    override fun onPause() {
        super.onPause()
        _isInBackground = true
        if (MonitorikApp.activeWebView === webView) {
            MonitorikApp.activeWebView = null
        }
    }

    override fun onDestroy() {
        retryHandler.removeCallbacksAndMessages(null)
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Puede lanzar si nunca se registró correctamente; se ignora de forma segura
        }
        super.onDestroy()
    }

    fun isInBackground() = _isInBackground

    private fun registrarNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.w("MainActivity", "No se pudo registrar NetworkCallback: ${e.message}")
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun procesarIntentPush(intent: Intent?) {
        if (intent == null) return
        if (!intent.getBooleanExtra("push_from_notif", false)) {
            if (!intent.hasExtra("push_type")) return
        }

        val type        = intent.getStringExtra("push_type")         ?: return
        val clickAction = intent.getStringExtra("push_click_action") ?: ""
        val actionUrl   = intent.getStringExtra("push_action_url")   ?: ""

        Log.d("FCM_DEBUG", "procesarIntentPush -> type=$type, actionUrl=$actionUrl")

        intent.removeExtra("push_from_notif")

        invocarAccionPushCuandoListo(type, actionUrl, clickAction)
    }

    fun invocarAccionPushCuandoListo(type: String, actionUrl: String, clickAction: String) {
        if (isWebViewReady) {
            invocarAccionPush(type, actionUrl, clickAction)
        } else {
            _pendingPushType    = type
            _pendingActionUrl   = actionUrl
            _pendingClickAction = clickAction
            Log.d("FCM_DEBUG", "Push guardado como PENDIENTE (WebView no listo)")
        }
    }

    fun invocarAccionPush(type: String, actionUrl: String, clickAction: String) {
        val t  = type.replace("\\", "\\\\").replace("'", "\\'")
        val a  = actionUrl.replace("\\", "\\\\").replace("'", "\\'")
        val c  = clickAction.replace("\\", "\\\\").replace("'", "\\'")
        val js = "if(typeof recibirAccionPush==='function'){recibirAccionPush('$t','$a','$c');}else{console.warn('recibirAccionPush no definida');}"

        runOnUiThread {
            Log.d("FCM_DEBUG", "Ejecutando JS: $js")
            webView.evaluateJavascript(js, null)
        }
    }

    private fun obtenerTokenFCM() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            val token = task.result
            pendingFcmToken = token
            enviarTokenFcmAWebView(token)
        }
    }

    private fun enviarTokenFcmAWebView(token: String) {
        if (!isWebViewReady) return
        webView.evaluateJavascript(
            "if(window.recibirTokenFCM){recibirTokenFCM('$token');}",
            null
        )
    }

    private fun configurarWebView() {
        webView.setBackgroundColor(Color.parseColor("#111239"))
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setGeolocationEnabled(true)
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString += " MonitorikApp/2.2"
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            javaScriptCanOpenWindowsAutomatically = true
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                // Guarda cada URL a la que navega el usuario dentro del WebView,
                // para poder reintentar la URL correcta si se cae la conexión ahí.
                request?.url?.toString()?.let { lastUrl = it }

                // Verifica si hay conexión disponible
                if (!isNetworkAvailable()) {
                    Log.d("FCM_DEBUG", "shouldOverrideUrlLoading: sin conexión, mostrando overlay para: $lastUrl")
                    if (!isOffline) {
                        isOffline = true
                        isWebViewReady = false
                        mostrarReconnectingOverlay()
                        scheduleRetry()
                    }
                    return true // Bloquea la carga
                }

                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                // about:blank es solo un paso de limpieza interno, no una carga real
                if (url == "about:blank") return

                isWebViewReady = true

                if (isOffline) {
                    isOffline = false
                    retryHandler.removeCallbacksAndMessages(null)
                    ocultarReconnectingOverlay()
                }

                webView.visibility = View.VISIBLE
                errorView.visibility = View.GONE

                imgSplash.animate().alpha(0f).setDuration(600).withEndAction {
                    imgSplash.visibility = View.GONE
                }

                pendingFcmToken?.let { enviarTokenFcmAWebView(it) }

                // Arranca (o reintenta) el flujo de ubicación en background ahora que
                // sabemos que la página cargó y probablemente el usuario ya está logueado.
                // Es seguro llamarlo varias veces: si ya hay token guardado no vuelve a
                // golpear la red.
                iniciarUbicacionSiCorresponde()

                val pendingType   = _pendingPushType    ?: return
                val pendingAction = _pendingActionUrl   ?: ""
                val pendingClick  = _pendingClickAction ?: ""

                _pendingPushType    = null
                _pendingActionUrl   = null
                _pendingClickAction = null

                Log.d("FCM_DEBUG", "onPageFinished: ejecutando push pendiente con delay 600ms")
                webView.postDelayed({
                    invocarAccionPush(pendingType, pendingAction, pendingClick)
                }, 600)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    Log.w("FCM_DEBUG", "onReceivedError (main frame): ${error?.description}")

                    isOffline = true
                    isWebViewReady = false

                    // Guarda la URL que falló para reintentar la correcta, no siempre URL_DESTINO
                    request.url?.toString()?.let { lastUrl = it }

                    // Evita que se pinte la página de error nativa de Chrome/WebView,
                    // y limpia cualquier render a medias que deje el fondo en blanco
                    view?.stopLoading()
                    view?.loadUrl("about:blank")

                    // Muestra el overlay animado en vez de texto de error
                    errorView.visibility = View.GONE
                    mostrarReconnectingOverlay()

                    scheduleRetry()
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.proceed()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                callback.invoke(origin, true, false)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                val permisosNecesarios = mutableListOf<String>()
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    permisosNecesarios.add(Manifest.permission.CAMERA)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                        permisosNecesarios.add(Manifest.permission.READ_MEDIA_IMAGES)
                    }
                } else {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        permisosNecesarios.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }

                return if (permisosNecesarios.isEmpty()) {
                    startFileChooser()
                    true
                } else {
                    requestPermissionLauncher.launch(permisosNecesarios.toTypedArray())
                    true
                }
            }
        }
    }

    private fun scheduleRetry() {
        // Limpia cualquier callback previo y programa un reintento automático.
        retryHandler.removeCallbacksAndMessages(null)
        stopCountdown()

        countdownRemainingSec = (retryIntervalMs / 1000L).toInt()
        updateReconnectMessage(countdownRemainingSec)
        startCountdown()

        retryHandler.postDelayed({
            if (isOffline) {
                // Solo intenta si hay conexión disponible
                if (isNetworkAvailable()) {
                    Log.d("FCM_DEBUG", "Reintentando cargar: $lastUrl")
                    webView.loadUrl(lastUrl)
                } else {
                    // Si aún no hay conexión, reprograma el reintento
                    Log.d("FCM_DEBUG", "Sin conexión aún, esperando...")
                    scheduleRetry()
                }
            }
        }, retryIntervalMs)
    }

    private fun mostrarReconnectingOverlay() {
        if (reconnectingOverlay.visibility == View.VISIBLE) return

        Log.d("FCM_DEBUG", "Mostrando reconnectingOverlay")
        webView.visibility = View.INVISIBLE
        imgSplash.visibility = View.GONE
        reconnectingOverlay.alpha = 0f
        reconnectingOverlay.visibility = View.VISIBLE
        reconnectingOverlay.animate().alpha(1f).setDuration(250).start()
        // Muestra spinner, texto actualizado y botón de reintento manual
        btnManualRetry.visibility = View.VISIBLE
        countdownRemainingSec = (retryIntervalMs / 1000L).toInt()
        updateReconnectMessage(countdownRemainingSec)

        startCountdown()
    }

    private fun ocultarReconnectingOverlay() {
        stopCountdown()
        btnManualRetry.visibility = View.GONE
        reconnectingOverlay.animate()
            .alpha(0f)
            .setDuration(250)
            .withEndAction { reconnectingOverlay.visibility = View.GONE }
            .start()
    }

    private fun updateReconnectMessage(seconds: Int) {
        runOnUiThread {
            reconnectMessage.text = "Fallo por la conexión a Internet, se reintentará en ${seconds} segundos."
        }
    }

    private fun startCountdown() {
        stopCountdown()
        if (countdownRemainingSec <= 0) return
        countdownRunnable = object : Runnable {
            override fun run() {
                countdownRemainingSec -= 1
                if (countdownRemainingSec > 0) {
                    updateReconnectMessage(countdownRemainingSec)
                    retryHandler.postDelayed(this, 1000)
                } else {
                    // Cuando el tiempo llega a 0, dejamos que el postDelayed programado haga el reintento
                    updateReconnectMessage(0)
                }
            }
        }
        retryHandler.postDelayed(countdownRunnable!!, 1000)
    }

    private fun stopCountdown() {
        countdownRunnable?.let { retryHandler.removeCallbacks(it) }
        countdownRunnable = null
    }

    private fun startFileChooser() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            val photoFile = createImageFile()
            photoURI = FileProvider.getUriForFile(
                this@MainActivity,
                "${packageName}.provider",
                photoFile
            )
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
        } catch (ex: IOException) {
            photoURI = null
        }

        val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

        val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
            putExtra(Intent.EXTRA_TITLE, "Seleccionar archivo o cámara")
            if (photoURI != null) putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(takePictureIntent))
        }

        fileChooserLauncher.launch(chooserIntent)
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun solicitarPermisosEsenciales() {
        val lista = mutableListOf<String>()
        lista.add(Manifest.permission.ACCESS_FINE_LOCATION)
        lista.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        lista.add(Manifest.permission.CAMERA)
        lista.add(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lista.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val faltantes = lista.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (faltantes.isNotEmpty()) {
            requestPermissionLauncher.launch(faltantes.toTypedArray())
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // ============================================================
    // --- Ubicación en background (integrado desde el snippet) ---
    // ============================================================

    // 1. Punto de entrada — se llama desde onPageFinished() del WebViewClient
    private fun iniciarUbicacionSiCorresponde() {
        if (TokenManager.hasToken(this)) {
            asegurarPermisosYArrancarServicio()
            return
        }

        TokenManager.bootstrap(this) { success, _ ->
            runOnUiThread {
                if (success) {
                    asegurarPermisosYArrancarServicio()
                }
                // Si falla (p.ej. cookie no disponible aún), no pasa nada — se
                // reintenta solo la próxima vez que se llame a esta función.
            }
        }
    }

    // 2. Verifica/pide permisos en el orden correcto y arranca el Service
    private fun asegurarPermisosYArrancarServicio() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_FINE_LOCATION
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!notifGranted) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val backgroundGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!backgroundGranted) {
                mostrarDivulgacionYPedirBackground()
                return
            }
        }

        LocationForegroundService.start(this)
    }

    // 3. Divulgación prominente — Google Play EXIGE explicar esto antes de pedir
    //    ACCESS_BACKGROUND_LOCATION, o rechazan la app en revisión. Ajustar el
    //    texto real a la política de privacidad publicada.
    private fun mostrarDivulgacionYPedirBackground() {
        AlertDialog.Builder(this)
            .setTitle("Ubicación en segundo plano")
            .setMessage(
                "Monitorik necesita acceder a tu ubicación incluso cuando la app está " +
                        "minimizada o la pantalla apagada, para que tu empresa pueda dar " +
                        "seguimiento a tu jornada laboral. Puedes desactivar esto en cualquier " +
                        "momento desde Ajustes del sistema."
            )
            .setPositiveButton("Continuar") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    REQUEST_BACKGROUND_LOCATION
                )
            }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    // 4. Callback de permisos "clásico" (ActivityCompat.requestPermissions),
    //    independiente del requestPermissionLauncher que ya usás para
    //    cámara/archivos, que usa el contrato nuevo y no pasa por acá.
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_FINE_LOCATION, REQUEST_BACKGROUND_LOCATION, REQUEST_NOTIFICATIONS ->
                asegurarPermisosYArrancarServicio()
        }
    }
}