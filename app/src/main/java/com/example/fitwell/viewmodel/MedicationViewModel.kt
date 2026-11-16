package com.example.fitwell.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitwell.data.local.*
import com.example.fitwell.reminders.MedicationReminderScheduler
import com.example.fitwell.reminders.ScheduleResult
import com.example.fitwell.util.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Everything the Add/Edit form edits. Values are exactly what the user typed or picked. */
data class MedicationDraft(
    val id: Long = 0,
    val name: String = "",
    val dosage: String = "",
    val form: String? = null,
    val frequencyType: FrequencyType = FrequencyType.ONCE_DAILY,
    val times: List<Pair<Int, Int>> = listOf(8 to 0),
    val days: Int = ALL_DAYS,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val instructions: String = "",
    val clinicianNotes: String = "",
    val remindersEnabled: Boolean = true,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/** One scheduled dose plus what the user recorded for it (if anything). */
data class DoseRow(val occurrence: DoseOccurrence, val intake: MedicationIntakeEntity?)

fun scheduleSummary(type: FrequencyType, times: List<Pair<Int, Int>>, days: Int): String {
    val t = times.sortedBy { it.first * 60 + it.second }.joinToString(", ") { formatTimeHM(it.first, it.second) }
    return when (type) {
        FrequencyType.ONCE_DAILY -> "Once daily at $t"
        FrequencyType.MULTIPLE_DAILY -> "${times.size} times daily: $t"
        FrequencyType.SELECTED_DAYS -> "${daysLabel(days)} at $t"
        FrequencyType.CUSTOM -> "Custom: ${daysLabel(days)} at $t"
    }
}

class MedicationViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.fitLife()
    private val repo = app.medicationRepo
    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val medications: StateFlow<List<MedicationEntity>> = repo.observeMedications().state(emptyList())
    val reminders: StateFlow<Map<Long, List<MedicationReminderEntity>>> =
        repo.observeAllReminders().map { l -> l.groupBy { it.medicationId } }.state(emptyMap())

    /** Today's doses, derived from saved schedules and joined with user-recorded intake. */
    val todayDoses: StateFlow<List<DoseRow>> = combine(
        repo.observeMedications(), repo.observeAllReminders(),
        repo.observeIntakesBetween(startOfDay(), startOfDay() + DAY_MS - 1)
    ) { meds, rems, intakes ->
        val byKey = intakes.associateBy { it.medicationId to it.scheduledDateTime }
        dosesOn(LocalDate.now(), meds, rems).map { DoseRow(it, byKey[it.medication.medicationId to it.scheduledAt]) }
    }.state(emptyList())

    val allIntakes: StateFlow<List<MedicationIntakeEntity>> = repo.observeAllIntakes().state(emptyList())
    fun intakesFor(id: Long): Flow<List<MedicationIntakeEntity>> = repo.observeIntakesFor(id)
    fun observeMedication(id: Long): Flow<MedicationEntity?> = repo.observeMedication(id)

    /** Shown by screens in a snackbar (scheduling problems, permission hints). */
    val message = MutableStateFlow<String?>(null)
    fun clearMessage() { message.value = null }

    suspend fun loadDraft(id: Long): MedicationDraft? {
        val m = repo.getMedication(id) ?: return null
        val r = repo.getReminders(id)
        return MedicationDraft(
            id = m.medicationId, name = m.name, dosage = m.dosage, form = m.form, frequencyType = m.frequencyType,
            times = r.map { it.hour to it.minute }.distinct().ifEmpty { listOf(8 to 0) },
            days = r.firstOrNull()?.daysOfWeek ?: ALL_DAYS,
            startDate = m.startDate.toLocalDate(), endDate = m.endDate?.toLocalDate(),
            instructions = m.instructions.orEmpty(), clinicianNotes = m.clinicianNotes.orEmpty(),
            remindersEnabled = m.remindersEnabled, isActive = m.isActive, createdAt = m.createdAt
        )
    }

    fun validate(d: MedicationDraft): Map<String, String> = buildMap {
        if (d.name.isBlank()) put("name", "Enter the medication name")
        if (d.dosage.isBlank()) put("dosage", "Enter the dosage exactly as prescribed")
        if (d.times.isEmpty()) put("times", "Add at least one reminder time")
        if (d.times.size != d.times.distinct().size) put("times", "Two reminders have the same time")
        if (d.frequencyType == FrequencyType.ONCE_DAILY && d.times.size > 1) put("times", "Once daily allows one time; choose Multiple times daily")
        if ((d.frequencyType == FrequencyType.SELECTED_DAYS || d.frequencyType == FrequencyType.CUSTOM) && d.days == 0) put("days", "Choose at least one day")
        if (d.endDate != null && d.endDate.isBefore(d.startDate)) put("endDate", "End date cannot be before the start date")
    }

    /**
     * Order matters (spec section 9): save to the database FIRST, then schedule. If scheduling fails the
     * saved schedule is kept and the user is told.
     */
    fun save(d: MedicationDraft, onSaved: (Long) -> Unit) {
        if (validate(d).isNotEmpty()) return
        viewModelScope.launch {
            if (d.id != 0L) MedicationReminderScheduler.cancel(app, repo.getReminders(d.id)) // cancel obsolete alarms
            val days = if (d.frequencyType == FrequencyType.SELECTED_DAYS || d.frequencyType == FrequencyType.CUSTOM) d.days else ALL_DAYS
            val med = MedicationEntity(
                medicationId = d.id, name = d.name.trim(), dosage = d.dosage.trim(), form = d.form?.takeIf { it.isNotBlank() },
                frequencyType = d.frequencyType, frequencyDescription = scheduleSummary(d.frequencyType, d.times, days),
                startDate = d.startDate.startOfDayMs(), endDate = d.endDate?.startOfDayMs(),
                instructions = d.instructions.trim().ifBlank { null }, clinicianNotes = d.clinicianNotes.trim().ifBlank { null },
                isActive = d.isActive, remindersEnabled = d.remindersEnabled, createdAt = d.createdAt
            )
            val id = repo.save(med, d.times.map { (h, m) -> MedicationReminderEntity(medicationId = 0, hour = h, minute = m, daysOfWeek = days) })
            reschedule(id)
            onSaved(id)
        }
    }

    private suspend fun reschedule(id: Long) {
        val med = repo.getMedication(id) ?: return
        report(MedicationReminderScheduler.schedule(app, med, repo.getReminders(id)))
    }

    private fun report(r: ScheduleResult) {
        message.value = when {
            r.error != null -> "Saved, but ${r.error}"
            r.inexact -> "Saved. Reminder times may be a few minutes late until 'Alarms & reminders' is allowed in system settings."
            else -> null
        }
    }

    fun setActive(m: MedicationEntity, active: Boolean) = viewModelScope.launch { repo.setActive(m, active); reschedule(m.medicationId) }
    fun setRemindersEnabled(m: MedicationEntity, on: Boolean) = viewModelScope.launch { repo.setRemindersEnabled(m, on); reschedule(m.medicationId) }

    fun delete(m: MedicationEntity, onDone: () -> Unit = {}) = viewModelScope.launch {
        MedicationReminderScheduler.cancel(app, repo.getReminders(m.medicationId))
        repo.delete(m) // reminders cascade; recorded intake history is kept
        onDone()
    }

    /** Records what the user says happened. Never triggered by a notification being shown or opened. */
    fun recordIntake(m: MedicationEntity, scheduledAt: Long, status: IntakeStatus) =
        viewModelScope.launch { repo.recordIntake(m, scheduledAt, status) }
    fun undoIntake(intake: MedicationIntakeEntity) = viewModelScope.launch { repo.clearIntake(intake.intakeId) }
}
