package com.example.fitwell.reminders

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.fitwell.MainActivity
import com.example.fitwell.R

object NotificationHelper {
    const val CHANNEL_MEDICATION = "medication_reminders"
    const val CHANNEL_APPOINTMENT = "appointment_reminders"
    const val EXTRA_ROUTE = "fitlife_route"

    // Stable notification ids: the same reminder always maps to the same id, so it is replaced, never duplicated.
    fun medicationNotificationId(reminderId: Long) = 1_000_000 + reminderId.toInt()
    fun appointmentNotificationId(reminderId: Long) = 2_000_000 + reminderId.toInt()

    /** Separate channels so the user can configure sound/priority for each independently. */
    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MEDICATION, "Medication reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders for medication times you scheduled"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_APPOINTMENT, "Appointment reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders before your appointments"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
    }

    fun areNotificationsAllowed(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled()
    fun show(
        ctx: Context, channel: String, notificationId: Int, title: String, text: String,
        route: String, hideOnLockScreen: Boolean, genericTitle: String
    ) {
        if (!areNotificationsAllowed(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, notificationId,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_ROUTE, route)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "View details", open)

        if (hideOnLockScreen) {
            // Locked screen shows only a generic notification; details appear after unlocking.
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(
                    NotificationCompat.Builder(ctx, channel)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(genericTitle)
                        .setContentText("Unlock your phone to see details")
                        .build()
                )
        } else {
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }
        NotificationManagerCompat.from(ctx).notify(notificationId, builder.build())
    }
}
