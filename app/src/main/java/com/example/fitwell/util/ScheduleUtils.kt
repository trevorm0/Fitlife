package com.example.fitwell.util

import com.example.fitwell.data.local.AppointmentEntity
import com.example.fitwell.data.local.AppointmentStatus
import com.example.fitwell.data.local.MedicationEntity
import com.example.fitwell.data.local.MedicationReminderEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

// ---------------- Medication ----------------
enum class ScheduleState(val label: String) {
    ACTIVE("Active"), UPCOMING("Starts later"), COMPLETED("Completed"), PAUSED("Paused")
}

fun MedicationEntity.scheduleState(today: LocalDate = LocalDate.now()): ScheduleState = when {
    !isActive -> ScheduleState.PAUSED
    startDate.toLocalDate().isAfter(today) -> ScheduleState.UPCOMING
    endDate != null && endDate.toLocalDate().isBefore(today) -> ScheduleState.COMPLETED
    else -> ScheduleState.ACTIVE
}

/** One scheduled dose on a specific day. Its identity is (medicationId, scheduledAt). */
data class DoseOccurrence(
    val medication: MedicationEntity,
    val reminder: MedicationReminderEntity,
    val scheduledAt: Long
)

/** Next time (strictly after [after]) this reminder fires, honouring start/end dates and weekdays. */
fun nextOccurrence(med: MedicationEntity, rem: MedicationReminderEntity, after: Long = System.currentTimeMillis()): Long? {
    if (!med.isActive || !med.remindersEnabled || !rem.isEnabled) return null
    val zone = ZoneId.systemDefault()
    val startDay = med.startDate.toLocalDate()
    val endDay = med.endDate?.toLocalDate()
    var day = maxOf(startDay, after.toLocalDate())
    repeat(8) {
        if (endDay != null && day.isAfter(endDay)) return null
        if (rem.daysOfWeek and dayBit(day.dayOfWeek) != 0) {
            // atZone() resolves DST gaps/overlaps, so reminders stay at the same wall-clock time.
            val t = LocalDateTime.of(day, LocalTime.of(rem.hour, rem.minute)).atZone(zone).toInstant().toEpochMilli()
            if (t > after) return t
        }
        day = day.plusDays(1)
    }
    return null
}

/** Soonest upcoming dose across all active schedules. */
fun nextDose(meds: List<MedicationEntity>, reminders: List<MedicationReminderEntity>, after: Long = System.currentTimeMillis()): DoseOccurrence? {
    val byId = meds.associateBy { it.medicationId }
    return reminders.mapNotNull { r ->
        val m = byId[r.medicationId] ?: return@mapNotNull null
        nextOccurrence(m, r, after)?.let { DoseOccurrence(m, r, it) }
    }.minByOrNull { it.scheduledAt }
}

/** All doses scheduled on [date] (used for the "today" list and for recording intake). */
fun dosesOn(date: LocalDate, meds: List<MedicationEntity>, reminders: List<MedicationReminderEntity>): List<DoseOccurrence> {
    val byId = meds.associateBy { it.medicationId }
    return reminders.mapNotNull { r ->
        val m = byId[r.medicationId] ?: return@mapNotNull null
        if (!m.isActive || !r.isEnabled) return@mapNotNull null
        if (m.startDate.toLocalDate().isAfter(date)) return@mapNotNull null
        if (m.endDate != null && m.endDate.toLocalDate().isBefore(date)) return@mapNotNull null
        if (r.daysOfWeek and dayBit(date.dayOfWeek) == 0) return@mapNotNull null
        DoseOccurrence(m, r, date.atTimeMs(r.hour, r.minute))
    }.sortedBy { it.scheduledAt }
}

// ---------------- Appointments ----------------
enum class AppointmentSection(val label: String) {
    TODAY("Today"), UPCOMING("Upcoming"), COMPLETED("Completed"), PAST("Past"), CANCELLED("Cancelled")
}

/** Derived from date AND status, not from a stored label alone. */
fun AppointmentEntity.section(now: Long = System.currentTimeMillis()): AppointmentSection = when (status) {
    AppointmentStatus.CANCELLED -> AppointmentSection.CANCELLED
    AppointmentStatus.COMPLETED -> AppointmentSection.COMPLETED
    AppointmentStatus.UPCOMING -> {
        val end = endDateTime ?: (startDateTime + HOUR_MS)
        when {
            end < now -> AppointmentSection.PAST
            startDateTime.toLocalDate() == now.toLocalDate() -> AppointmentSection.TODAY
            else -> AppointmentSection.UPCOMING
        }
    }
}

val APPOINTMENT_CATEGORIES = listOf(
    "Doctor", "Hospital", "Dental", "Physiotherapy", "Laboratory", "Nutrition", "Mental wellness", "Other"
)

/** (minutesBefore, label) choices for appointment reminders. */
val APPOINTMENT_REMINDER_OPTIONS = listOf(
    0 to "At appointment time", 15 to "15 minutes before", 30 to "30 minutes before",
    60 to "1 hour before", 1440 to "1 day before"
)

fun reminderLabel(minutesBefore: Int): String =
    APPOINTMENT_REMINDER_OPTIONS.firstOrNull { it.first == minutesBefore }?.second
        ?: if (minutesBefore % 1440 == 0) "${minutesBefore / 1440} days before"
        else if (minutesBefore % 60 == 0) "${minutesBefore / 60} hours before" else "$minutesBefore minutes before"
