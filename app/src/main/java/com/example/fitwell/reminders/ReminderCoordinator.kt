package com.example.fitwell.reminders

import android.content.Context
import com.example.fitwell.FitLifeApp

object ReminderCoordinator {
    /**
     * Reconciles alarms with the database: app start, reboot, app update, clock / time-zone change.
     * Inactive, deleted or cancelled items get their alarms cancelled; everything else is (re)scheduled.
     */
    suspend fun rescheduleAll(ctx: Context): ScheduleResult {
        val app = ctx.applicationContext as FitLifeApp
        if (!app.hasSession) return ScheduleResult() // nobody signed in: nothing to schedule
        val medDao = app.db.medicationDao()
        val apptDao = app.db.appointmentDao()
        var total = 0
        var inexact = false
        var error: String? = null

        val medReminders = medDao.getAllReminders().groupBy { it.medicationId }
        medDao.getAllMedications().forEach { m ->
            val r = MedicationReminderScheduler.schedule(app, m, medReminders[m.medicationId].orEmpty())
            total += r.scheduled; inexact = inexact || r.inexact; error = error ?: r.error
        }
        val apptReminders = apptDao.getAllReminders().groupBy { it.appointmentId }
        apptDao.getAllAppointments().forEach { a ->
            val r = AppointmentReminderScheduler.schedule(app, a, apptReminders[a.appointmentId].orEmpty())
            total += r.scheduled; inexact = inexact || r.inexact; error = error ?: r.error
        }
        return ScheduleResult(total, inexact, error)
    }
}
