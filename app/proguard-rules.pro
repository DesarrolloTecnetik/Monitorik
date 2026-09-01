# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Reglas específicas de Monitorik
# ---------------------------------------------------------------------------

# WebView + JS bridge: si en algún punto se agrega addJavascriptInterface,
# las clases/métodos expuestos a JS deben tener @JavascriptInterface y
# quedar protegidos de la ofuscación, o el WebView no podrá invocarlos.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Firebase Cloud Messaging: el servicio se referencia desde el manifest, y
# Firebase usa reflexión internamente para inicializar sus componentes.
-keep class com.google.firebase.messaging.** { *; }
-keep class com.tecnetik.monitorik.MyFirebaseMessagingService { *; }
-keep class com.tecnetik.monitorik.MonitorikApp { *; }
-dontwarn com.google.firebase.**

# Google Play Services Location (FusedLocationProviderClient, LocationRequest,
# LocationCallback, etc.) — la librería ya trae sus propias reglas via su AAR,
# pero se refuerza por si el modo full de R8 es más agresivo que lo esperado.
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# OkHttp / Okio: reglas recomendadas oficialmente por OkHttp para evitar
# warnings y fallos por referencias a clases opcionales de plataformas
# (Conscrypt, Android internals) que no siempre están presentes en runtime.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# org.json (JSONObject) es parte del SDK de Android, no se ofusca por
# defecto, pero se deja explícito por claridad ya que es el mecanismo de
# serialización usado en TokenManager y LocationForegroundService.
-keep class org.json.** { *; }

# Clases del paquete de ubicación en background: usan reflexión indirecta
# vía companion objects y callbacks anónimos; se protegen sus nombres para
# que los logs (Log.e con stacktrace) sigan siendo legibles en Play Console.
-keep class com.tecnetik.monitorik.location.** { *; }