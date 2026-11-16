package com.example.fitwell.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitwell.data.local.*
import com.example.fitwell.ui.components.*
import com.example.fitwell.ui.theme.*
import com.example.fitwell.util.*
import com.example.fitwell.viewmodel.*
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private fun ScheduleState.color(): Color = when (this) {
    ScheduleState.ACTIVE -> FitGreen
    ScheduleState.UPCOMING -> FitCyan
    ScheduleState.COMPLETED -> TextSecondary
    ScheduleState.PAUSED -> CaloriesOrange
}

private fun IntakeStatus.color(): Color = when (this) {
    IntakeStatus.TAKEN -> FitGreen
    IntakeStatus.SKIPPED -> CaloriesOrange
    IntakeStatus.MISSED -> HeartCoral
}

private fun medDates(m: MedicationEntity) =
    "Starts ${formatDate(m.startDate)}" + (m.endDate?.let { " • Ends ${formatDate(it)}" } ?: " • No end date")

// ====================================================================================
// Medication tab (list + today's doses)
// ====================================================================================
@Composable
fun MedicationScreen(
    onOpenMedication: (Long) -> Unit, onAddMedication: () -> Unit, onHistory: () -> Unit,
    vm: MedicationViewModel = viewModel()
) {
    val meds by vm.medications.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val doses by vm.todayDoses.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ReminderPermissionBanner() }
            item { SectionHeader("Today's doses", actionText = "History", onAction = onHistory) }
            if (doses.isEmpty()) {
                item { Text("Nothing is scheduled for today.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
            }
            items(doses, key = { "${it.occurrence.medication.medicationId}_${it.occurrence.scheduledAt}" }) { row ->
                DoseCard(row, onRecord = { s -> vm.recordIntake(row.occurrence.medication, row.occurrence.scheduledAt, s) }, onUndo = { row.intake?.let(vm::undoIntake) })
            }
            if (doses.any { it.intake?.status != null && it.intake.status != IntakeStatus.TAKEN }) {
                item { Text(MEDICAL_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
            }

            item { SectionHeader("My medications") }
            if (meds.isEmpty()) {
                item {
                    EmptyState(
                        "No medications yet", "Add a medication and the times you take it. FitLife will remind you.",
                        icon = Icons.Default.Medication, actionLabel = "Add medication", onAction = onAddMedication
                    )
                }
            }
            items(meds, key = { it.medicationId }) { m -> MedicationCard(m, reminders[m.medicationId].orEmpty(), onClick = { onOpenMedication(m.medicationId) }) }
            item { Text(MEDICAL_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
        }
        ExtendedFloatingActionButton(
            onClick = onAddMedication, containerColor = FitGreen, contentColor = Midnight,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Default.Add, contentDescription = null) }, text = { Text("Add medication") }
        )
        MessageSnackbar(message, vm::clearMessage, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }
}

@Composable
private fun DoseCard(row: DoseRow, onRecord: (IntakeStatus) -> Unit, onUndo: () -> Unit) {
    val m = row.occurrence.medication
    FitLifeCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatTime(row.occurrence.scheduledAt), style = MaterialTheme.typography.titleMedium, color = FitGreen)
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.titleSmall)
                    Text(m.dosage, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                row.intake?.let { StatusChip(it.status.label, it.status.color()) }
            }
            if (row.intake == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FitLifePrimaryButton("Taken", { onRecord(IntakeStatus.TAKEN) }, Modifier.weight(1f))
                    FitLifeSecondaryButton("Skipped", { onRecord(IntakeStatus.SKIPPED) }, Modifier.weight(1f), color = CaloriesOrange)
                    FitLifeSecondaryButton("Missed", { onRecord(IntakeStatus.MISSED) }, Modifier.weight(1f), color = HeartCoral)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recorded by you at ${formatTime(row.intake.recordedAt ?: row.occurrence.scheduledAt)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                    TextButton(onClick = onUndo) { Text("Undo", color = FitGreen) }
                }
            }
        }
    }
}

@Composable
private fun MedicationCard(m: MedicationEntity, reminders: List<MedicationReminderEntity>, onClick: () -> Unit) {
    val state = m.scheduleState()
    FitLifeCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Medication, null, tint = FitGreen)
                Text(m.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(state.label, state.color())
            }
            Text("${m.dosage}${m.form?.let { " • $it" } ?: ""}", style = MaterialTheme.typography.bodyMedium)
            Text(
                reminders.sortedBy { it.hour * 60 + it.minute }.joinToString(", ") { formatTimeHM(it.hour, it.minute) }.ifBlank { "No reminder times" },
                style = MaterialTheme.typography.bodyMedium, color = FitCyan
            )
            Text(medDates(m), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            m.instructions?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
            StatusChip(if (m.remindersEnabled) "Reminders on" else "Reminders off", if (m.remindersEnabled) FitGreen else TextSecondary)
        }
    }
}

// ====================================================================================
// Add / edit
// ====================================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddMedicationScreen(id: Long, onBack: () -> Unit, vm: MedicationViewModel = viewModel()) {
    var draft by remember { mutableStateOf(MedicationDraft()) }
    var attempted by remember { mutableStateOf(false) }
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(id) { if (id != 0L) vm.loadDraft(id)?.let { draft = it } }
    val errors = remember(draft) { vm.validate(draft) }

    var timeEditIndex by remember { mutableStateOf<Int?>(null) } // index, or -1 when adding
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    val multiDay = draft.frequencyType == FrequencyType.SELECTED_DAYS || draft.frequencyType == FrequencyType.CUSTOM

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            FitLifeTopBar(if (id == 0L) "Add medication" else "Edit medication", onBack = onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ReminderPermissionBanner()
                FitLifeTextField(draft.name, { draft = draft.copy(name = it) }, "Medication name", error = errors["name"].takeIf { attempted })
                FitLifeTextField(
                    draft.dosage, { draft = draft.copy(dosage = it) }, "Dosage as prescribed",
                    error = errors["dosage"].takeIf { attempted }, placeholder = "e.g. as written on your prescription"
                )
                Text("FitLife never suggests or checks dosages. Enter exactly what your prescriber told you.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                Text("Form (optional)", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Tablet", "Capsule", "Liquid", "Other").forEach { f ->
                        CategoryFilterChip(f, draft.form == f, { draft = draft.copy(form = if (draft.form == f) null else f) })
                    }
                }

                Text("Schedule", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FrequencyType.entries.forEach { t ->
                        CategoryFilterChip(t.label, draft.frequencyType == t, {
                            draft = draft.copy(frequencyType = t, times = if (t == FrequencyType.ONCE_DAILY) draft.times.take(1) else draft.times)
                        })
                    }
                }
                if (multiDay) {
                    Text("Days", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WEEK_DAYS.forEach { d ->
                            val on = draft.days and dayBit(d) != 0
                            CategoryFilterChip(d.shortLabel(), on, { draft = draft.copy(days = draft.days xor dayBit(d)) })
                        }
                    }
                    errors["days"]?.takeIf { attempted }?.let { Text(it, color = HeartCoral, style = MaterialTheme.typography.bodySmall) }
                }

                Text("Reminder times", style = MaterialTheme.typography.titleSmall)
                draft.times.forEachIndexed { i, (h, m) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PickerField("Time ${i + 1}", formatTimeHM(h, m), { timeEditIndex = i }, Modifier.weight(1f), icon = Icons.Default.AccessTime)
                        if (draft.times.size > 1) IconButton(onClick = { draft = draft.copy(times = draft.times.filterIndexed { j, _ -> j != i }) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove time ${i + 1}", tint = TextSecondary)
                        }
                    }
                }
                errors["times"]?.takeIf { attempted }?.let { Text(it, color = HeartCoral, style = MaterialTheme.typography.bodySmall) }
                if (draft.frequencyType != FrequencyType.ONCE_DAILY) {
                    FitLifeSecondaryButton("Add another time", { timeEditIndex = -1 }, icon = Icons.Default.Add)
                }

                PickerField("Start date", formatDate(draft.startDate.startOfDayMs()), { pickStart = true }, icon = Icons.Default.CalendarMonth)
                PickerField(
                    "End date (optional)", draft.endDate?.let { formatDate(it.startOfDayMs()) } ?: "No end date", { pickEnd = true },
                    error = errors["endDate"].takeIf { attempted }, icon = Icons.Default.CalendarMonth
                )
                if (draft.endDate != null) TextButton(onClick = { draft = draft.copy(endDate = null) }) { Text("Clear end date", color = FitGreen) }

                FitLifeTextField(draft.instructions, { draft = draft.copy(instructions = it) }, "Instructions (optional)", singleLine = false, minLines = 2)
                FitLifeTextField(draft.clinicianNotes, { draft = draft.copy(clinicianNotes = it) }, "Prescriber / pharmacy notes (optional)", singleLine = false, minLines = 2)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Send reminders", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        draft.remindersEnabled, { draft = draft.copy(remindersEnabled = it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = FitGreen, checkedThumbColor = Midnight)
                    )
                }
                Text(MEDICAL_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                FitLifePrimaryButton("Save medication", {
                    attempted = true
                    if (errors.isEmpty()) vm.save(draft) { onBack() }
                }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
            }
        }
        MessageSnackbar(message, vm::clearMessage, Modifier.align(Alignment.BottomCenter))
    }

    timeEditIndex?.let { idx ->
        val (h0, m0) = draft.times.getOrNull(idx) ?: (8 to 0)
        FitLifeTimePickerDialog(h0, m0, onDismiss = { timeEditIndex = null }) { h, m ->
            draft = draft.copy(times = if (idx == -1) draft.times + (h to m) else draft.times.mapIndexed { j, t -> if (j == idx) h to m else t })
        }
    }
    if (pickStart) FitLifeDatePickerDialog(draft.startDate, { pickStart = false }, { draft = draft.copy(startDate = it) })
    if (pickEnd) FitLifeDatePickerDialog(draft.endDate ?: draft.startDate, { pickEnd = false }, { draft = draft.copy(endDate = it) })
}

// ====================================================================================
// Details
// ====================================================================================
@Composable
fun MedicationDetailsScreen(id: Long, onBack: () -> Unit, onEdit: () -> Unit, onHistory: () -> Unit, vm: MedicationViewModel = viewModel()) {
    val loaded by remember(id) { vm.observeMedication(id).map { Loaded(it) } }.collectAsStateWithLifecycle(initialValue = null)
    val allReminders by vm.reminders.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            FitLifeTopBar("Medication", onBack = onBack)
            val state = loaded
            val m = state?.value
            when {
                state == null -> LoadingState()
                m == null -> EmptyState("Medication not found", "This schedule was deleted.", actionLabel = "Back", onAction = onBack)
                else -> {
                    val reminders = allReminders[id].orEmpty().sortedBy { it.hour * 60 + it.minute }
                    val st = m.scheduleState()
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ReminderPermissionBanner()
                        FitLifeCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.Medication, null, tint = FitGreen)
                                    Text(m.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                                    StatusChip(st.label, st.color())
                                }
                                DetailLine("Dosage", m.dosage)
                                m.form?.let { DetailLine("Form", it) }
                                DetailLine("Schedule", m.frequencyDescription.ifBlank { "—" })
                                DetailLine("Start date", formatDate(m.startDate))
                                DetailLine("End date", m.endDate?.let { formatDate(it) } ?: "None")
                                m.instructions?.takeIf { it.isNotBlank() }?.let { DetailLine("Instructions", it) }
                                m.clinicianNotes?.takeIf { it.isNotBlank() }?.let { DetailLine("Prescriber / pharmacy", it) }
                            }
                        }
                        FitLifeCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Reminder times", style = MaterialTheme.typography.titleSmall)
                                if (reminders.isEmpty()) Text("None saved", color = TextSecondary)
                                reminders.forEach { r ->
                                    Text("${formatTimeHM(r.hour, r.minute)} • ${daysLabel(r.daysOfWeek)}", style = MaterialTheme.typography.bodyMedium)
                                }
                                val next = reminders.mapNotNull { nextOccurrence(m, it) }.minOrNull()
                                Text(
                                    if (next != null) "Next reminder: ${formatDateTime(next)}" else "No upcoming reminder",
                                    style = MaterialTheme.typography.bodySmall, color = FitCyan
                                )
                            }
                        }
                        SwitchRow("Reminders", "Show notifications at the times above", m.remindersEnabled) { vm.setRemindersEnabled(m, it) }
                        SwitchRow("Schedule active", "Turn off to pause this medication and its reminders", m.isActive) { vm.setActive(m, it) }
                        FitLifeSecondaryButton("View dose history", onHistory, Modifier.fillMaxWidth(), icon = Icons.Default.History)
                        FitLifePrimaryButton("Edit", onEdit, Modifier.fillMaxWidth(), icon = Icons.Default.Edit)
                        FitLifeSecondaryButton("Delete medication", { confirmDelete = true }, Modifier.fillMaxWidth(), icon = Icons.Default.Delete, color = HeartCoral)
                        Text(MEDICAL_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Spacer(Modifier.height(24.dp))
                    }
                    if (confirmDelete) ConfirmDialog(
                        "Delete ${m.name}?", "This removes the schedule and its reminders. Doses you already recorded stay in your history.",
                        "Delete", onConfirm = { vm.delete(m) { onBack() } }, onDismiss = { confirmDelete = false }, destructive = true
                    )
                }
            }
        }
        MessageSnackbar(message, vm::clearMessage, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun DetailLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    FitLifeCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = FitGreen, checkedThumbColor = Midnight))
        }
    }
}

// ====================================================================================
// History
// ====================================================================================
@Composable
fun MedicationHistoryScreen(id: Long, onBack: () -> Unit, vm: MedicationViewModel = viewModel()) {
    val all by vm.allIntakes.collectAsStateWithLifecycle()
    val items = remember(all, id) { if (id < 0) all else all.filter { it.medicationId == id } }
    Column(Modifier.fillMaxSize()) {
        FitLifeTopBar("Dose history", onBack = onBack)
        if (items.isEmpty()) {
            EmptyState("No doses recorded yet", "When you mark a dose as taken, skipped or missed, it appears here.", icon = Icons.Default.History)
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Entries are what you recorded yourself. ${MEDICAL_DISCLAIMER}", style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
                items(items, key = { it.intakeId }) { i ->
                    FitLifeCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(i.medicationName, style = MaterialTheme.typography.titleSmall)
                                Text("Scheduled: ${formatDateTime(i.scheduledDateTime)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                i.recordedAt?.let { Text("Recorded: ${formatDateTime(it)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
                            }
                            StatusChip(i.status.label, i.status.color())
                        }
                    }
                }
            }
        }
    }
}
