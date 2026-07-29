package com.example.monitorik.location

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

/**
 * Maneja el device_token de ubicación en segundo plano.
 *
 * Flujo:
 * 1. bootstrap() lee la cookie de sesión que YA existe en el WebView (login normal
 *    de Monitorik) y hace una sola llamada con OkHttp a obtener_token.
 * 2. El token se guarda en SharedPreferences y de ahí en adelante NO se vuelve a
 *    usar la cookie para nada relacionado a ubicación.
 *
 * No requiere ningún cambio del lado web (sin addJavascriptInterface, sin puente JS).
 */
object TokenManager {

    private const val TAG = "LOC_DEBUG"

    private const val PREFS = "monitorik_location_prefs"
    private const val KEY_DEVICE_TOKEN = "device_token"

    // Ajustar si el dominio real difiere
    private const val COOKIE_URL = "https://tecnserv.com/monitorik/"
    private const val ENDPOINT_URL = "https://tecnserv.com/monitorik/ajax/ubicacion_update.php"

    private val client = OkHttpClient()

    fun getToken(context: Context): String? =
        prefs(context).getString(KEY_DEVICE_TOKEN, null)

    fun hasToken(context: Context): Boolean = !getToken(context).isNullOrEmpty()

    fun clearToken(context: Context) {
        Log.w(TAG, "TokenManager: clearToken() llamado — probablemente el server respondió 401")
        prefs(context).edit().remove(KEY_DEVICE_TOKEN).apply()
    }

    private fun saveToken(context: Context, token: String) {
        Log.d(TAG, "TokenManager: token guardado correctamente (len=${token.length})")
        prefs(context).edit().putString(KEY_DEVICE_TOKEN, token).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Intenta obtener el token. Seguro de llamar varias veces (p.ej. en cada
     * onPageFinished del WebView): si ya hay token guardado, responde de inmediato
     * sin red. Si no hay cookie todavía (usuario aún no logueado), falla sin
     * romper nada más — se puede reintentar después.
     */
    fun bootstrap(context: Context, onResult: (success: Boolean, error: String?) -> Unit) {
        if (hasToken(context)) {
            Log.d(TAG, "TokenManager.bootstrap: ya hay token guardado, no se llama a la red")
            onResult(true, null)
            return
        }

        val cookie = CookieManager.getInstance().getCookie(COOKIE_URL)
        if (cookie.isNullOrEmpty()) {
            Log.w(TAG, "TokenManager.bootstrap: SIN cookie de sesión todavía para $COOKIE_URL")
            onResult(false, "Sin cookie de sesión todavía")
            return
        }

        Log.d(TAG, "TokenManager.bootstrap: cookie encontrada, pidiendo token a $ENDPOINT_URL")

        val body = FormBody.Builder()
            .add("action", "obtener_token")
            .build()

        val request = Request.Builder()
            .url(ENDPOINT_URL)
            .header("Cookie", cookie)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "TokenManager.bootstrap: fallo de red -> ${e.message}", e)
                onResult(false, e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    Log.d(TAG, "TokenManager.bootstrap: respuesta HTTP ${resp.code}")
                    if (!resp.isSuccessful) {
                        onResult(false, "HTTP ${resp.code}")
                        return
                    }
                    try {
                        val raw = resp.body?.string().orEmpty()
                        Log.d(TAG, "TokenManager.bootstrap: body=$raw")
                        val json = JSONObject(raw)
                        if (json.optBoolean("ok", false)) {
                            saveToken(context, json.getString("device_token"))
                            onResult(true, null)
                        } else {
                            val err = json.optString("error", "ok=false sin detalle")
                            Log.w(TAG, "TokenManager.bootstrap: server respondió ok=false -> $err")
                            onResult(false, err)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "TokenManager.bootstrap: respuesta inválida -> ${e.message}", e)
                        onResult(false, "Respuesta inválida: ${e.message}")
                    }
                }
            }
        })
    }
}