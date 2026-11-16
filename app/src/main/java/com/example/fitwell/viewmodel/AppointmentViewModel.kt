package com.example.fitwell.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitwell.data.local.*
import com.example.fitwell.reminders.AppointmentReminderScheduler
import com.example.fitwell.reminders.ScheduleResult
import com.example.fitwell.util.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class AppointmentDraft(
    val id: Long = 0,
    val title: String = "",
    val category: String = "Doctor",
    val date: LocalDate? = null,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val location: String = "",
    val contactName: String = "",
    val notes: String = "",
    val reminderMinutes: Set<Int> = setOf(60),
    val status: AppointmentStatus = AppointmentStatus.UPCOMING,
    val createdAt: Long = System.currentTimeMillis(),
    /** Start time when loaded for editing: lets the user edit an already-past appointment without moving it. */
    val originalStart: Long? = null
)

class AppointmentViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.fitLife()
    private val repo = app.appointmentRepo
    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val appointments: StateFlow<List<AppointmentEntity>> = repo.observeAppointments().state(emptyList())
    val reminders: StateFlow<Map<Long, List<AppointmentReminderEntity>>> =
        repo.observeAllReminders().map { l -> l.groupBy { it.appointmentId } }.state(emptyMap())
    val clock: StateFlow<Long> = minuteTicker().state(System.currentTimeMillis())

    fun observeAppointment(id: Long): Flow<AppointmentEntity?> = repo.observeAppointment(id)
    fun observeReminders(id: Long): Flow<List<AppointmentReminderEntity>> =
        repo.observeAllReminders().map { l -> l.filter { it.appointmentId == id }.sortedByDescending { it.minutesBefore } }

    val message = MutableStateFlow<String?>(null)
    fun clearMessage() { message.value = null }

    suspend fun loadDraft(id: Long): AppointmentDraft? {
        val a = repo.getAppointment(id) ?: return null
        val s = a.startDateTime.toLocalDateTime()
        return AppointmentDraft(
            id = a.appointmentId, title = a.title, category = a.category, date = s.toLocalDate(), start = s.toLocalTime(),
            end = a.endDateTime?.toLocalDateTime()?.toLocalTime(), location = a.location.orEmpty(), contactName = a.contactName.orEmpty(),
            notes = a.notes.orEmpty(), reminderMinutes = repo.getReminders(id).filter { it.isEnabled }.map { it.minutesBefore }.toSet(),
            status = a.status, createdAt = a.createdAt, originalStart = a.startDateTime
        )
    }

    private fun startMs(d: AppointmentDraft): Long? =
        if (d.date == null || d.start == null) null else d.date.atTimeMs(d.start.hour, d.start.minute)

    fun validate(d: AppointmentDraft, now: Long = System.currentTimeMillis()): Map<String, String> = buildMap {
        if (d.title.isBlank()) put("title", "Enter a title")
        if (d.category.isBlank()) put("category", "Choose or type a category")
        if (d.date == null) put("date", "Choose a date")
        if (d.start == null) put("start", "Choose a start time")
        val s = startMs(d)
        // Past appointments are rejected unless an existing one is being edited without changing its time.
        if (s != null && s < now && s != d.originalStart) put("date", "That date and time is in the past")
        val st = d.start
        val en = d.end
        if (st != null && en != null && !en.isAfter(st)) put("end", "End time must be after the start time")
    }

    fun save(d: AppointmentDraft, onSaved: (Long) -> Unit) {
        if (validate(d).isNotEmpty()) return
        viewModelScope.launch {
            if (d.id != 0L) AppointmentReminderScheduler.cancel(app, repo.getReminders(d.id))
            val start = startMs(d)!!
            val appt = AppointmentEntity(
                appointmentId = d.id, title = d.title.trim(), category = d.category.trim(), startDateTime = start,
                endDateTime = d.end?.let { d.date!!.atTimeMs(it.hour, it.minute) },
                location = d.location.trim().ifBlank { null }, contactName = d.contactName.trim().ifBlank { null },
                notes = d.notes.trim().ifBlank { null }, status = d.status, createdAt = d.createdAt
            )
            val id = repo.save(appt, d.reminderMinutes.map { AppointmentReminderEntity(appointmentId = 0, minutesBefore = it) })
            reschedule(id)
            onSaved(id)
        }
    }

    private suspend fun reschedule(id: Long) {
        val a = repo.getAppointment(id) ?: return
        report(AppointmentReminderScheduler.schedule(app, a, repo.getReminders(id)))
    }

    private fun report(r: ScheduleResult) {
        message.value = when {
            r.error != null -> "Saved, but ${r.error}"
            r.inexact -> "Saved. Reminder times may be a few minutes late until 'Alarms & reminders' is allowed in system settings."
            else -> null
        }
    }

    /** Cancelling or completing stops all reminders (the scheduler cancels when status is not UPCOMING). */
    fun setStatus(a: AppointmentEntity, status: AppointmentStatus) = viewModelScope.launch {
        repo.setStatus(a, status); reschedule(a.appointmentId)
    }

    fun delete(a: AppointmentEntity, onDone: () -> Unit = {}) = viewModelScope.launch {
        AppointmentReminderScheduler.cancel(app, repo.getReminders(a.appointmentId))
        repo.delete(a)
        onDone()
    }
}
