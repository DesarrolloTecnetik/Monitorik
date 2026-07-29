package com.example.monitorik.location

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Sin esto, cada vez que el teléfono se reinicia el Service muere y NO vuelve
 * a levantarse solo hasta que el usuario abra Monitorik manualmente.
 * Solo arranca si ya había un device_token guardado y los permisos siguen
 * concedidos (si el usuario los revocó manualmente, no se fuerza nada).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val backgroundGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted && backgroundGranted && TokenManager.hasToken(context)) {
            LocationForegroundService.start(context)
        }
    }
}
