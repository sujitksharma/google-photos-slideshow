package com.skharma.casioclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Relaunches the clock after the tablet reboots (e.g. after a power cut),
 * so a wall-mounted device comes back up showing the watch face on its own.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launchIntent)
        }
    }
}
