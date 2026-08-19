package com.tecnetik.monitorik

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.webkit.WebView

class MonitorikApp : Application() {

    companion object {
        var activeWebView: WebView? = null
    }

    override fun onCreate() {
        super.onCreate()
        crearCanalNotificaciones()
    }

    private fun crearCanalNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "monitorik_push",
                "Notificaciones Monitorik",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas y recordatorios de Monitorik"
                enableLights(true)
                enableVibration(true)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
