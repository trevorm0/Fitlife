package com.example.fitwell.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.fitwell.FitLifeApp
import com.example.fitwell.data.local.AppointmentStatus
import com.example.fitwell.util.formatDateTime
import com.example.fitwell.util.formatTime
import com.example.fitwell.util.toLocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context.applicationContext, intent)
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun handle(ctx: Context, intent: Intent) {
        val app = ctx as FitLifeApp
        if (!app.hasSession) return // signed out: stay silent
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        val hide = app.prefs.prefs.first().hideLockScreenDetails

        when (intent.getStringExtra(EXTRA_TYPE)) {
            TYPE_MED -> {
                val dao = app.db.medicationDao()
                // Re-validate against the database: a deleted, paused or edited schedule must stay silent.
                val reminder = dao.getReminder(id) ?: return
                val med = dao.getMedication(reminder.medicationId) ?: return
                if (!med.isActive || !med.remindersEnabled || !reminder.isEnabled) return
                val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, System.currentTimeMillis())
                if (med.endDate != null && med.endDate.toLocalDate().isBefore(scheduledAt.toLocalDate())) return

                val details = listOfNotNull(med.dosage.takeIf { it.isNotBlank() }, med.instructions?.takeIf { it.isNotBlank() })
                    .joinToString(" · ").ifBlank { "Time for your scheduled medication" }
                NotificationHelper.show(
                    ctx, NotificationHelper.CHANNEL_MEDICATION, NotificationHelper.medicationNotificationId(id),
                    "Medication: ${med.name}", "${formatTime(scheduledAt)} · $details",
                    route = "medication/${med.medicationId}", hideOnLockScreen = hide, genericTitle = "Medication reminder"
                )
                // Chain the next occurrence (after the one that just fired). The dose is NOT marked as taken.
                MedicationReminderScheduler.scheduleNext(ctx, med, reminder, maxOf(System.currentTimeMillis(), scheduledAt))
            }

            TYPE_APPT -> {
                val dao = app.db.appointmentDao()
                val reminder = dao.getReminder(id) ?: return
                val appt = dao.getAppointment(reminder.appointmentId) ?: return
                if (!reminder.isEnabled || appt.status != AppointmentStatus.UPCOMING) return
                NotificationHelper.show(
                    ctx, NotificationHelper.CHANNEL_APPOINTMENT, NotificationHelper.appointmentNotificationId(id),
                    "Appointment: ${appt.title}",
                    formatDateTime(appt.startDateTime) + (appt.location?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    route = "appointment/${appt.appointmentId}", hideOnLockScreen = hide, genericTitle = "Appointment reminder"
                )
            }
        }
    }
}
