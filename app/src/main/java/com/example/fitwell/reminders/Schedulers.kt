package com.example.fitwell.reminders

import android.content.Context
import android.os.Bundle
import com.example.fitwell.data.local.AppointmentEntity
import com.example.fitwell.data.local.AppointmentReminderEntity
import com.example.fitwell.data.local.AppointmentStatus
import com.example.fitwell.data.local.MedicationEntity
import com.example.fitwell.data.local.MedicationReminderEntity
import com.example.fitwell.util.MINUTE_MS
import com.example.fitwell.util.nextOccurrence

const val EXTRA_TYPE = "type"
const val EXTRA_ID = "reminderId"
const val EXTRA_SCHEDULED_AT = "scheduledAt"
const val TYPE_MED = "MED"
const val TYPE_APPT = "APPT"

/**
 * @param scheduled number of alarms set
 * @param inexact   true if the system only allowed approximate timing (exact-alarm permission missing)
 * @param error     set when scheduling failed; the saved record is kept and the user is told
 */
data class ScheduleResult(val scheduled: Int = 0, val inexact: Boolean = false, val error: String? = null)

object MedicationReminderScheduler {
    private fun key(reminderId: Long) = "med_$reminderId"

    fun cancel(ctx: Context, reminders: List<MedicationReminderEntity>) =
        reminders.forEach { AlarmHelper.cancel(ctx, key(it.reminderId)) }

    /** Cancels this medication's alarms, then sets the next occurrence of each enabled reminder. */
    fun schedule(ctx: Context, med: MedicationEntity, reminders: List<MedicationReminderEntity>): ScheduleResult {
        cancel(ctx, reminders)
        var count = 0
        var inexact = false
        return try {
            reminders.forEach { r ->
                scheduleNext(ctx, med, r)?.let { exact -> count++; if (!exact) inexact = true }
            }
            ScheduleResult(count, inexact)
        } catch (e: Exception) {
            ScheduleResult(count, inexact, "Reminders could not be scheduled: ${e.localizedMessage ?: "unknown error"}")
        }
    }

    /** @return null if nothing to schedule, otherwise whether the alarm was exact. */
    fun scheduleNext(ctx: Context, med: MedicationEntity, r: MedicationReminderEntity, after: Long = System.currentTimeMillis()): Boolean? {
        val at = nextOccurrence(med, r, after) ?: return null
        val extras = Bundle().apply {
            putString(EXTRA_TYPE, TYPE_MED); putLong(EXTRA_ID, r.reminderId); putLong(EXTRA_SCHEDULED_AT, at)
        }
        return AlarmHelper.set(ctx, key(r.reminderId), at, extras)
    }
}

object AppointmentReminderScheduler {
    private fun key(reminderId: Long) = "appt_$reminderId"

    fun cancel(ctx: Context, reminders: List<AppointmentReminderEntity>) =
        reminders.forEach { AlarmHelper.cancel(ctx, key(it.reminderId)) }

    fun schedule(ctx: Context, appt: AppointmentEntity, reminders: List<AppointmentReminderEntity>): ScheduleResult {
        cancel(ctx, reminders)
        if (appt.status != AppointmentStatus.UPCOMING) return ScheduleResult()
        val now = System.currentTimeMillis()
        var count = 0
        var inexact = false
        return try {
            reminders.filter { it.isEnabled }.forEach { r ->
                val at = appt.startDateTime - r.minutesBefore * MINUTE_MS
                if (at > now) {
                    val extras = Bundle().apply {
                        putString(EXTRA_TYPE, TYPE_APPT); putLong(EXTRA_ID, r.reminderId); putLong(EXTRA_SCHEDULED_AT, at)
                    }
                    if (!AlarmHelper.set(ctx, key(r.reminderId), at, extras)) inexact = true
                    count++
                }
            }
            ScheduleResult(count, inexact)
        } catch (e: Exception) {
            ScheduleResult(count, inexact, "Reminders could not be scheduled: ${e.localizedMessage ?: "unknown error"}")
        }
    }
}
