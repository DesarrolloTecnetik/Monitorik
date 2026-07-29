package com.example.monitorik

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCM_DEBUG", "Push recibido: data=${remoteMessage.data}")

        val data = remoteMessage.data
        val type        = data["type"]         ?: ""
        val clickAction = data["click_action"] ?: ""
        val actionUrl   = data["action_url"]   ?: ""
        val title       = data["title"]        ?: remoteMessage.notification?.title ?: "Monitorik"
        val body        = data["body"]         ?: remoteMessage.notification?.body ?: ""
        val notifId     = data["notif_id"]     ?: System.currentTimeMillis().toString()

        mostrarNotificacion(title, body, clickAction, actionUrl, type, notifId)

        val webView = MonitorikApp.activeWebView
        if (webView != null) {
            webView.post {
                val js = buildJsCall(type, actionUrl, clickAction)
                webView.evaluateJavascript(js, null)
            }
        }
    }

    private fun mostrarNotificacion(
        title: String, body: String,
        clickAction: String, actionUrl: String,
        type: String, notifId: String
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("push_type",         type)
            putExtra("push_click_action", clickAction)
            putExtra("push_action_url",   actionUrl)
            putExtra("push_title",        title)
            putExtra("push_body",         body)
            putExtra("push_from_notif",   true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, notifId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "monitorik_push"
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification) // La "m" blanca
            .setColor(ContextCompat.getColor(this, R.color.monitorik_push_circle)) // Círculo Morado
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Notificaciones Monitorik",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        manager.notify(notifId.hashCode(), notification)
    }

    override fun onNewToken(token: String) {
        Log.d("FCM", "Nuevo Token: $token")
        MonitorikApp.activeWebView?.post {
            MonitorikApp.activeWebView?.evaluateJavascript(
                "if(window.recibirTokenFCM) { recibirTokenFCM('$token'); }",
                null
            )
        }
    }

    private fun buildJsCall(type: String, actionUrl: String, clickAction: String): String {
        val t  = jsString(type)
        val au = jsString(actionUrl)
        val ca = jsString(clickAction)
        return "if(typeof recibirAccionPush==='function'){recibirAccionPush($t,$au,$ca);}"
    }

    private fun jsString(value: String?): String {
        if (value == null) return "null"
        val escaped = value.replace("\\", "\\\\").replace("'", "\\'")
        return "'$escaped'"
    }
}
