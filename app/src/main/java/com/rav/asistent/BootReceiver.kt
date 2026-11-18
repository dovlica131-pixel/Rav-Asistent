package com.rav.asistent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Kad se uređaj upali (paljenje auta), asistent se sam pokrene ako je bio uključen. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.autostart(context) && Settings.canDrawOverlays(context)) {
            try {
                context.startForegroundService(Intent(context, AsistentService::class.java))
            } catch (ignored: Exception) {
            }
        }
    }
}
