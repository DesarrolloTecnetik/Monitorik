# Monitorik APK

**Monitorik** es una aplicación Android nativa (desarrollada en Kotlin) que funciona como contenedor web, cargando un sitio web en un `WebView` apuntando por defecto a `https://tecnserv.com/monitorik/`. Incluye integración completa con Firebase Cloud Messaging (FCM) para notificaciones push, manejo avanzado de permisos en tiempo de ejecución, y soporte para subida de archivos/captura de fotos.

Este README cubre instalación, compilación, ejecución, configuración de Firebase, personalización de iconos de notificación y dónde cambiar la URL.

## Características principales
- 📱 **WebView optimizado** apuntando a `https://tecnserv.com/monitorik/`
- 🔔 **Firebase Cloud Messaging (FCM)** con notificaciones push personalizadas
- 📸 **Manejo completo de fotos y archivos** - cámara, galería y selector de archivos
- 🔐 **Sistema de permisos en tiempo de ejecución** - ubicación, cámara, audio, lectura de imágenes, notificaciones
- 🎨 **Splash screen personalizado** con tema de marca Monitorik
- 📡 **Envío automático del token FCM** a la página web (mediante `recibirTokenFCM()`)
- 🎯 **Icono de notificación personalizado** - usa el icono de la aplicación
- 🔗 **Comunicación JavaScript ↔ Native** para integración bidireccional con la web

## Requisitos
- **JDK 11** o superior
- **Android SDK** con compileSdk 34 y targetSdk 34
- **Gradle** (proporciona wrapper incluido)
- **Android Studio 2022.2+** (recomendado para desarrollo y depuración)
- **Proyecto Firebase** configurado con Google Services

### Configuración del SDK
- **minSdk:** 24 (Android 7.0)
- **compileSdk:** 34
- **targetSdk:** 34

## Estructura del proyecto
```
Monitorik-APK/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/monitorik/
│   │   │   ├── MainActivity.kt              # Actividad principal, WebView y permisos
│   │   │   ├── MonitorikApp.kt             # Inicialización de app y canal de notificaciones
│   │   │   └── MyFirebaseMessagingService.kt # Servicio FCM para notificaciones push
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml    # Layout principal
│   │   │   ├── mipmap-*/                   # Iconos de app en diferentes densidades
│   │   │   ├── drawable/                   # Assets vectoriales e imágenes
│   │   │   ├── values/                     # Strings, colores, estilos
│   │   │   └── xml/                        # Configuración de provider paths
│   │   ├── AndroidManifest.xml             # Permisos, servicios y meta-datos Firebase
│   │   └── google-services.json            # Configuración de Firebase (NO versionar)
│   └── build.gradle.kts                    # Dependencias del módulo app
├── build.gradle.kts                        # Configuración root del proyecto
├── settings.gradle.kts                     # Configuración de módulos
├── gradle.properties                       # Propiedades de Gradle
└── README.md                               # Este archivo
```

## Cambiar la URL cargada
La URL por defecto está definida en `MainActivity.kt` como la constante `URL_DESTINO`. Para usar otra URL, edita esa constante.

---

## Firebase Cloud Messaging (FCM) - Configuración

### ¿Qué es FCM?
Firebase Cloud Messaging permite enviar notificaciones push desde un servidor a la aplicación en tiempo real.

### Configuración inicial
1. **Crear proyecto en Firebase Console:**
   - Accede a https://console.firebase.google.com/
   - Crea un nuevo proyecto o selecciona uno existente
   - Ve a "Configuración del proyecto" → "Aplicaciones"
   - Selecciona tu aplicación Android

2. **Descargar `google-services.json`:**
   - De Firebase Console, descarga el archivo `google-services.json`
   - Colócalo en `app/google-services.json` ⚠️ **No versiones este archivo en Git**
   - El archivo contiene claves privadas de tu proyecto

3. **Habilitar Cloud Messaging API:**
   - Desde Firebase Console, ve a "Cloud Messaging"
   - Asegúrate de que esté habilitado
   - Obtén tu **Server API Key** (o clave del servidor) para enviar notificaciones

### Estructura de notificaciones
La aplicación maneja notificaciones con esta estructura de datos JSON:

```json
{
  "type": "alert",
  "click_action": "HANDLE_PUSH",
  "action_url": "https://my-domain.com/push-action",
  "title": "Título de la notificación",
  "body": "Contenido de la notificación",
  "notif_id": "unique_id_123"
}
```

### Características de las notificaciones
- ✅ Icono personalizado (icono de la app)
- ✅ Color de fondo personalizado (color Monitorik)
- ✅ Título y cuerpo de texto
- ✅ Prioridad ALTA para visualización inmediata
- ✅ Estilo BigText para contenido largo
- ✅ Click action que abre MainActivity con datos extras
- ✅ Vibración y luces personalizadas (Android 8+)

### Iconos de notificación
Los iconos de las notificaciones se configuran automáticamente:
- **Archivo:** `app/src/main/AndroidManifest.xml` (meta-data)
- **Icono usado:** `R.mipmap.ic_launcher` (icono de la aplicación)
- **Color:** Usa el color `R.color.monitorik_bg` de fondo
- **Densidades:** Los iconos están en `mipmap-mdpi/`, `mipmap-hdpi/`, etc.

Si necesitas cambiar el icono de notificación, edita esta línea en `MyFirebaseMessagingService.kt`:
```kotlin
.setSmallIcon(R.mipmap.ic_launcher) // Aquí va tu recurso de icono
```

---

## Permisos y comportamiento
- La app solicita al inicio los permisos esenciales (ubicación, cámara, audio, notificaciones) según la implementación actual.
- Cuando la web solicita subir un archivo (input file), la app comprueba permisos (CAMERA o lectura de imágenes). Si no están concedidos, solicita los permisos en tiempo de ejecución y, si el usuario acepta, se abre el chooser.
- Para Android 13+ (API 33) se emplea `READ_MEDIA_IMAGES`; en versiones previas se usa `READ_EXTERNAL_STORAGE`.

## Construir y ejecutar

### En Windows (cmd.exe)
Desde la raíz del proyecto (`C:\Users\demo\AndroidStudioProjects\Monitorik-APK`):

**Compilar APK debug:**
```cmd
gradlew.bat assembleDebug
```

**Instalar en dispositivo/emulador conectado:**
```cmd
gradlew.bat installDebug
```

**Ejecutar directamente (compilar + instalar + lanzar):**
```cmd
gradlew.bat runDebug
```

**Compilar APK release:** ⚠️ Requiere configuración de firma
```cmd
gradlew.bat assembleRelease
```

**Limpiar build (recomendado si hay problemas):**
```cmd
gradlew.bat clean
```

### En Android Studio (Recomendado)
1. Abre el proyecto: `File → Open → Selecciona la carpeta Monitorik-APK`
2. Espera a que se complete la sincronización de Gradle
3. Haz clic en ▶️ **Run** (o presiona Shift + F10)
4. Selecciona tu dispositivo/emulador

**Ventajas:**
- ✅ Depuración en vivo (logcat, breakpoints)
- ✅ Interfaz visual más amigable
- ✅ Sincronización automática de cambios

## Comunicación JavaScript ↔ Native (WebView Bridge)

### Recibir el Token FCM en la página web
Cuando la app obtiene un token FCM nuevo, automáticamente lo envía a la página web llamando:

```javascript
// Se ejecuta en el contexto de la página web cargada
recibirTokenFCM('token_fcm_aqui_muy_largo')
```

**Implementa esta función en tu JavaScript:**
```javascript
function recibirTokenFCM(token) {
    console.log("Token FCM recibido:", token);
    // Envía el token a tu servidor
    fetch('/api/save-fcm-token', {
        method: 'POST',
        body: JSON.stringify({ token }),
        headers: { 'Content-Type': 'application/json' }
    });
}
```

### Recibir acciones de notificaciones push
Cuando el usuario hace clic en una notificación, la app ejecuta:

```javascript
// Se ejecuta cuando se hace clic en una notificación
recibirAccionPush(tipo, actionUrl, clickAction)
```

**Implementa esta función:**
```javascript
function recibirAccionPush(tipo, actionUrl, clickAction) {
    console.log("Push recibido:", { tipo, actionUrl, clickAction });
    // Navega a una sección específica de tu app
    if (actionUrl) window.location.href = actionUrl;
}
```

### Parámetros extras en el Intent
Cuando se abre la app desde una notificación, los extras están disponibles como parámetros GET:
- `push_type` - tipo de push (ej: "alert")
- `push_click_action` - acción a ejecutar
- `push_action_url` - URL destino
- `push_title` - título de la notificación
- `push_body` - contenido de la notificación
- `push_from_notif` - true si viene de una notificación

---

## Probar la subida de fotos (flujo de permisos)
1. Abre la app en un emulador o dispositivo real.
2. Accede a la funcionalidad en la web que usa `<input type="file">` o similar.
3. Si la app no tiene permisos necesarios, Android mostrará el diálogo de permisos. Acepta para continuar.
4. Debería abrirse un chooser que permita tomar foto o seleccionar desde galería.
5. La foto/archivo se envía a la página web.

**Comportamiento de permisos:**
- ✅ Si el usuario acepta: se abre el archivo chooser
- ❌ Si el usuario deniega: la app devuelve `null` al WebView (operación cancelada)

## Interfaz de usuario y barras del sistema

### Tema y estilos
- **Tema:** `Theme.Monitorik` - personalizado con colores de Monitorik
- **Splash Screen:** Pantalla de inicio personalizada durante la carga
- **Barras del sistema:** Status bar y navigation bar con colores sólidos

### Configuración actual
- La ventana NO dibuja detrás de las barras del sistema
- Se aplican colores personalizados a statusBar y navigationBar
- Esto se configura en `MainActivity.kt` con `WindowCompat.setDecorFitsSystemWindows(window, true)`

Si quieres cambiar a **edge-to-edge** (contenido detrás de las barras), edita esta línea.

---

## Personalización y configuración avanzada

### Cambiar URL cargada
En `MainActivity.kt`, busca la constante `URL_DESTINO` y cámbiala:
```kotlin
private val URL_DESTINO = "https://tu-domain.com/ruta"
```

### Cambiar tipos de archivo aceptados
Edita el MIME type en `MainActivity.kt` (método `onShowFileChooser`):
```kotlin
// Actual: solo imágenes y video
intent.type = "image/*|video/*"

// Para aceptar PDFs también:
intent.type = "image/*|video/*|application/pdf"

// Para aceptar cualquier archivo:
intent.type = "*/*"
```

### Modificar permisos solicitados
En `MainActivity.kt`, edita la lista `PERMISOS_REQUERIDOS`:
```kotlin
private val PERMISOS_REQUERIDOS = arrayOf(
    Manifest.permission.INTERNET,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.CAMERA,
    Manifest.permission.READ_MEDIA_IMAGES,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.POST_NOTIFICATIONS
)
```

### Cambiar icono o color de notificaciones
**Icono:**
En `MyFirebaseMessagingService.kt`, línea 61:
```kotlin
.setSmallIcon(R.drawable.ic_notification) // Tu icono personalizado
```

**Color de fondo:**
Edita `R.color.monitorik_bg` en `res/values/colors.xml`

### Deshabilitar permiso de cámara al inicio
Si solo quieres pedir cámara cuando se necesite (file chooser):
1. Quita `Manifest.permission.CAMERA` de `PERMISOS_REQUERIDOS`
2. La app la pedirá solo cuando el usuario intente subir un archivo

## Errores comunes y soluciones

### Error: "google-services.json no encontrado"
**Síntoma:** Error de compilación relacionado con Google Services
**Solución:**
1. Obtén `google-services.json` de Firebase Console
2. Colócalo en `app/google-services.json`
3. Ejecuta `gradlew.bat clean` y recompila
4. Asegúrate de que el archivo tenga formato JSON válido

### Error: "Notificaciones no se reciben"
**Motivos posibles:**
- [ ] Firebase Cloud Messaging no está habilitado en Console
- [ ] El token FCM no se está guardando en tu servidor
- [ ] La app fue desinstalada sin permiso `POST_NOTIFICATIONS`
- [ ] El servidor no está enviando al token correcto

**Soluciones:**
1. Verifica que el token se reciba correctamente (check logcat)
2. Confirma que tu servidor tenga Server API Key de Firebase
3. Reinstala la app y acepta el permiso de notificaciones
4. Prueba enviando una notificación de prueba desde Firebase Console

### Error: "Permisos no se solicitan"
**Síntoma:** Android no muestra el diálogo de permisos
**Motivos:**
- Ya fueron concedidos previamente (reinstala la app para probar)
- El dispositivo es Android < 6.0 (M) donde los permisos son en instalación

**Solución:**
- Ve a Configuración del dispositivo → Aplicaciones → Monitorik → Permisos → Reinicia

### Error: "La cámara no abre en el chooser"
**Síntoma:** Al seleccionar "Tomar foto", no abre la cámara
**Causa probable:** `FileProvider` no configurado correctamente
**Solución:**
- Verifica que `android.support.FILE_PROVIDER_PATHS` esté en `AndroidManifest.xml`
- Comprueba que `res/xml/provider_paths.xml` exista
- Ambos ya están configurados en este proyecto

### App se congela al cargar la URL
**Motivos posibles:**
- La URL no es accesible (sin internet)
- La página tarda mucho en cargar
- Hay un error en la página web

**Soluciones:**
1. Verifica que el dispositivo tenga conexión a internet
2. Prueba con `https://example.com` para verificar que funciona
3. Abre Logcat en Android Studio para ver mensajes de error
4. Verifica que tu URL sea HTTPS (no HTTP sin certificado válido)

---

## Debugging y logs

### Ver logs en Android Studio
1. En Android Studio, abre la ventana **Logcat** (View → Tool Windows → Logcat)
2. Conecta tu dispositivo o usa un emulador
3. Ejecuta la app con `gradlew.bat installDebug` o ▶️ Run
4. Verifica los logs:
   - `FCM_DEBUG` - Tags de Firebase Messaging
   - `FCM` - Información de tokens
   - `W/WebViewClient` - Errores de carga de página
   - `E/` - Errores generales

### Comandos útiles de adb (Android Debug Bridge)
```bash
# Ver todas las notificaciones
adb logcat | grep "NotificationCompat"

# Limpiar logs
adb logcat -c

# Ver solo errores
adb logcat *:E

# Reinstalar la app
adb uninstall com.tecnetik.monitoriktik.monitorik
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Dependencias principales

Esta aplicación utiliza:
- **Kotlin** - Lenguaje principal de desarrollo
- **AndroidX** - Librerías de compatibilidad moderna
- **Firebase** - Cloud Messaging (FCM)
- **WebView** - Para cargar y mostrar contenido web
- **Gradle** - Sistema de construcción

Todas las dependencias están configuradas en `app/build.gradle.kts`.

---

## Contribuciones
Para mejoras o reportar bugs:
1. Verifica que sea un problema real (no un uso incorrecto)
2. Documenta pasos para reproducir
3. Si es posible, proporciona logs de error
4. Crea un Issue describiendo el problema

---

## Notas de seguridad

⚠️ **IMPORTANTE - No versiones estos archivos en Git:**
- `app/google-services.json` - Contiene credenciales privadas de Firebase
- `local.properties` - Contiene rutas locales de tu máquina
- Keystore para firma de release

✅ **Recomendaciones de seguridad:**
- Usa HTTPS en todas las URLs
- Valida y sanitiza datos que vengan de Firebase/WebView
- Mantén las dependencias actualizadas
- No logs de información sensible (tokens, credenciales)
- Firma el APK release con un keystore seguro

---

## Recursos útiles

- 📚 [Documentación oficial de Android](https://developer.android.com/)
- 🔥 [Firebase Cloud Messaging](https://firebase.google.com/docs/cloud-messaging)
- 🌐 [WebView Android](https://developer.android.com/guide/webapps/webview)
- 🔔 [Notificaciones en Android](https://developer.android.com/guide/topics/ui/notifiers/notifications)

---

## Versión y fecha
- **Nombre del proyecto:** Monitorik APK
- **Versión:** 2.0
- **Última actualización:** Julio 2026
- **Estado:** En desarrollo activo
