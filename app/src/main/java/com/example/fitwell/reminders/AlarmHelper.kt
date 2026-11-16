package com.example.fitwell.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle

/** Thin AlarmManager wrapper. Each reminder has a unique key, so it can be updated or cancelled reliably. */
object AlarmHelper {

    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun intentFor(ctx: Context, key: String) =
        Intent(ctx, ReminderReceiver::class.java).apply { data = Uri.parse("fitlife://reminder/$key") }

    /** @return true if an exact alarm was set, false if the system only allowed an inexact one. */
    fun set(ctx: Context, key: String, triggerAt: Long, extras: Bundle): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val intent = intentFor(ctx, key).apply { putExtras(extras) }
        val pi = PendingIntent.getBroadcast(ctx, key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (canScheduleExact(ctx)) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                return true
            } catch (_: SecurityException) {
                // permission revoked meanwhile: fall through to the inexact alarm
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        return false
    }

    fun cancel(ctx: Context, key: String) {
        val pi = PendingIntent.getBroadcast(
            ctx, key.hashCode(), intentFor(ctx, key), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        ctx.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }
}
