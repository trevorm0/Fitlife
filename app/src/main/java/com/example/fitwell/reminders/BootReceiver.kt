package com.example.fitwell.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarms are cleared by reboot and become wrong after clock/time-zone changes, so rebuild them from the database. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val handled = setOf(
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
        if (intent.action !in handled) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderCoordinator.rescheduleAll(context.applicationContext)
            } finally {
                result.finish()
            }
        }
    }
}
