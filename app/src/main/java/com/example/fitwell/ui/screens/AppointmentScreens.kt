package com.example.fitwell.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitwell.data.local.AppointmentEntity
import com.example.fitwell.data.local.AppointmentStatus
import com.example.fitwell.ui.components.*
import com.example.fitwell.ui.theme.*
import com.example.fitwell.util.*
import com.example.fitwell.viewmodel.AppointmentDraft
import com.example.fitwell.viewmodel.AppointmentViewModel
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime

private fun AppointmentSection.color(): Color = when (this) {
    AppointmentSection.TODAY -> FitGreen
    AppointmentSection.UPCOMING -> FitCyan
    AppointmentSection.COMPLETED -> TextSecondary
    AppointmentSection.PAST -> CaloriesOrange
    AppointmentSection.CANCELLED -> HeartCoral
}

// ====================================================================================
// List (Appointments tab)
// ====================================================================================
@Composable
fun AppointmentsScreen(onOpen: (Long) -> Unit, onEdit: (Long) -> Unit, onAdd: () -> Unit, vm: AppointmentViewModel = viewModel()) {
    val list by vm.appointments.collectAsStateWithLifecycle()
    val now by vm.clock.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<AppointmentEntity?>(null) }
    var cancelTarget by remember { mutableStateOf<AppointmentEntity?>(null) }
    val ctx = LocalContext.current

    val grouped = remember(list, now) { list.groupBy { it.section(now) } }
    val nextId = remember(list, now) {
        list.filter { it.section(now) == AppointmentSection.TODAY || it.section(now) == AppointmentSection.UPCOMING }
            .minByOrNull { it.startDateTime }?.appointmentId
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { ReminderPermissionBanner() }
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        "No appointments yet", "Add a visit and choose when you want to be reminded.",
                        icon = Icons.Default.Event, actionLabel = "Add appointment", onAction = onAdd
                    )
                }
            }
            AppointmentSection.entries.forEach { section ->
                val sectionItems = grouped[section].orEmpty().let { if (section == AppointmentSection.UPCOMING || section == AppointmentSection.TODAY) it.sortedBy { a -> a.startDateTime } else it.sortedByDescending { a -> a.startDateTime } }
                if (sectionItems.isNotEmpty()) {
                    item(key = "h_${section.name}") { SectionHeader(section.label) }
                    items(sectionItems, key = { it.appointmentId }) { a ->
                        AppointmentCard(
                            a, section, isNext = a.appointmentId == nextId, now = now,
                            onOpen = { onOpen(a.appointmentId) },
                            onCancel = { cancelTarget = a }, onDelete = { deleteTarget = a },
                            onEdit = { onEdit(a.appointmentId) }
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd, containerColor = FitGreen, contentColor = Midnight,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Default.Add, contentDescription = null) }, text = { Text("Add appointment") }
        )
        MessageSnackbar(message, vm::clearMessage, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }

    deleteTarget?.let { a ->
        ConfirmDialog("Delete appointment?", "“${a.title}” and its reminders will be removed.", "Delete",
            onConfirm = { vm.delete(a) }, onDismiss = { deleteTarget = null }, destructive = true)
    }
    cancelTarget?.let { a ->
        ConfirmDialog("Cancel appointment?", "Its reminders will be turned off. You can keep it in your list as cancelled.", "Cancel appointment",
            onConfirm = { vm.setStatus(a, AppointmentStatus.CANCELLED) }, onDismiss = { cancelTarget = null }, destructive = true)
    }
}

@Composable
private fun AppointmentCard(
    a: AppointmentEntity, section: AppointmentSection, isNext: Boolean, now: Long,
    onOpen: () -> Unit, onEdit: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    FitLifeCard(Modifier.fillMaxWidth(), onClick = onOpen, borderColor = if (isNext) FitGreen else DividerBlue.copy(alpha = 0.6f)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // date block: dates and times are the first thing you see
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                Text(a.startDateTime.toLocalDate().dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, color = FitCyan)
                Text(a.startDateTime.toLocalDate().month.name.take(3), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(a.title, style = MaterialTheme.typography.titleSmall)
                Text(formatDateTime(a.startDateTime), style = MaterialTheme.typography.bodyMedium)
                a.location?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isNext) StatusChip("Next • ${timeUntil(a.startDateTime, now)}", FitGreen)
                    StatusChip(section.label, section.color())
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options for ${a.title}", tint = TextSecondary) }
                DropdownMenu(menu, { menu = false }, containerColor = CardElevated) {
                    DropdownMenuItem(text = { Text("View") }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text("Edit / reschedule") }, onClick = { menu = false; onEdit() })
                    if (a.status == AppointmentStatus.UPCOMING) DropdownMenuItem(text = { Text("Cancel appointment") }, onClick = { menu = false; onCancel() })
                    DropdownMenuItem(text = { Text("Delete", color = HeartCoral) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

// ====================================================================================
// Add / edit
// ====================================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddAppointmentScreen(id: Long, onBack: () -> Unit, vm: AppointmentViewModel = viewModel()) {
    var draft by remember { mutableStateOf(AppointmentDraft()) }
    var attempted by remember { mutableStateOf(false) }
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(id) { if (id != 0L) vm.loadDraft(id)?.let { draft = it } }
    val errors = remember(draft) { vm.validate(draft) }
    var pickDate by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            FitLifeTopBar(if (id == 0L) "Add appointment" else "Edit appointment", onBack = onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReminderPermissionBanner()
                FitLifeTextField(draft.title, { draft = draft.copy(title = it) }, "Title", error = errors["title"].takeIf { attempted })

                Text("Category", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    APPOINTMENT_CATEGORIES.forEach { c -> CategoryFilterChip(c, draft.category == c, { draft = draft.copy(category = c) }) }
                }
                FitLifeTextField(draft.category, { draft = draft.copy(category = it) }, "Or type your own category", error = errors["category"].takeIf { attempted })

                PickerField("Date", draft.date?.let { formatDate(it.startOfDayMs()) } ?: "Choose a date", { pickDate = true },
                    error = errors["date"].takeIf { attempted }, icon = Icons.Default.CalendarMonth)
                PickerField("Start time", draft.start?.let { formatTimeHM(it.hour, it.minute) } ?: "Choose a time", { pickStart = true },
                    error = errors["start"].takeIf { attempted }, icon = Icons.Default.AccessTime)
                PickerField("End time (optional)", draft.end?.let { formatTimeHM(it.hour, it.minute) } ?: "Not set", { pickEnd = true },
                    error = errors["end"].takeIf { attempted }, icon = Icons.Default.AccessTime)
                if (draft.end != null) TextButton(onClick = { draft = draft.copy(end = null) }) { Text("Clear end time", color = FitGreen) }

                FitLifeTextField(draft.location, { draft = draft.copy(location = it) }, "Location (optional)")
                FitLifeTextField(draft.contactName, { draft = draft.copy(contactName = it) }, "Healthcare professional / contact (optional)")
                FitLifeTextField(draft.notes, { draft = draft.copy(notes = it) }, "Notes (optional)", singleLine = false, minLines = 3)

                Text("Reminders", style = MaterialTheme.typography.titleSmall)
                Text("Pick as many as you like.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    APPOINTMENT_REMINDER_OPTIONS.forEach { (min, label) ->
                        val on = min in draft.reminderMinutes
                        CategoryFilterChip(label, on, { draft = draft.copy(reminderMinutes = if (on) draft.reminderMinutes - min else draft.reminderMinutes + min) })
                    }
                }
                FitLifePrimaryButton("Save appointment", {
                    attempted = true
                    if (errors.isEmpty()) vm.save(draft) { onBack() }
                }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
            }
        }
        MessageSnackbar(message, vm::clearMessage, Modifier.align(Alignment.BottomCenter))
    }

    // Past dates are not selectable unless an already-past appointment is being edited.
    val minDate = if (draft.originalStart != null && draft.originalStart!! < System.currentTimeMillis()) null else LocalDate.now()
    if (pickDate) FitLifeDatePickerDialog(draft.date ?: LocalDate.now(), { pickDate = false }, { draft = draft.copy(date = it) }, minDate)
    if (pickStart) FitLifeTimePickerDialog(draft.start?.hour ?: 9, draft.start?.minute ?: 0, { pickStart = false }) { h, m -> draft = draft.copy(start = LocalTime.of(h, m)) }
    if (pickEnd) FitLifeTimePickerDialog(draft.end?.hour ?: draft.start?.hour ?: 10, draft.end?.minute ?: draft.start?.minute ?: 0, { pickEnd = false }) { h, m -> draft = draft.copy(end = LocalTime.of(h, m)) }
}

// ====================================================================================
// Details
// ====================================================================================
@Composable
fun AppointmentDetailsScreen(id: Long, onBack: () -> Unit, onEdit: () -> Unit, vm: AppointmentViewModel = viewModel()) {
    val loaded by remember(id) { vm.observeAppointment(id).map { Loaded(it) } }.collectAsStateWithLifecycle(initialValue = null)
    val reminders by remember(id) { vm.observeReminders(id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val now by vm.clock.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var calendarError by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            FitLifeTopBar("Appointment", onBack = onBack)
            val state = loaded
            val a = state?.value
            when {
                state == null -> LoadingState()
                a == null -> EmptyState("Appointment not found", "It may have been deleted.", actionLabel = "Back", onAction = onBack)
                else -> {
                    val section = a.section(now)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FitLifeCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.Event, null, tint = FitCyan)
                                    Text(a.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatusChip(section.label, section.color()); StatusChip(a.category, FitCyan)
                                }
                                DetailLine("Date and time", formatDate(a.startDateTime) + ", " + formatTime(a.startDateTime) + (a.endDateTime?.let { " – ${formatTime(it)}" } ?: ""))
                                if (section == AppointmentSection.TODAY || section == AppointmentSection.UPCOMING) DetailLine("Time remaining", timeUntil(a.startDateTime, now))
                                a.location?.takeIf { it.isNotBlank() }?.let { DetailLine("Location", it) }
                                a.contactName?.takeIf { it.isNotBlank() }?.let { DetailLine("Contact", it) }
                                a.notes?.takeIf { it.isNotBlank() }?.let { DetailLine("Notes", it) }
                            }
                        }
                        FitLifeCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Reminders", style = MaterialTheme.typography.titleSmall)
                                if (reminders.isEmpty()) Text("No reminders set", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                                reminders.forEach { r -> Text(reminderLabel(r.minutesBefore), style = MaterialTheme.typography.bodyMedium) }
                                if (a.status != AppointmentStatus.UPCOMING) Text("Reminders are off because this appointment is ${a.status.label.lowercase()}.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                        FitLifePrimaryButton("Edit / reschedule", onEdit, Modifier.fillMaxWidth(), icon = Icons.Default.Edit)
                        FitLifeSecondaryButton("Add to calendar", {
                            try {
                                // Sends only title and location; notes stay in FitLife.
                                ctx.startActivity(
                                    Intent(Intent.ACTION_INSERT).apply {
                                        data = CalendarContract.Events.CONTENT_URI
                                        putExtra(CalendarContract.Events.TITLE, a.title)
                                        a.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, a.startDateTime)
                                        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, a.endDateTime ?: (a.startDateTime + HOUR_MS))
                                    }
                                )
                            } catch (e: ActivityNotFoundException) {
                                calendarError = "No calendar app was found on this device."
                            }
                        }, Modifier.fillMaxWidth(), icon = Icons.Default.CalendarMonth, color = FitCyan)
                        if (a.status == AppointmentStatus.UPCOMING) {
                            FitLifeSecondaryButton("Mark as completed", { vm.setStatus(a, AppointmentStatus.COMPLETED) }, Modifier.fillMaxWidth(), icon = Icons.Default.CheckCircle)
                            FitLifeSecondaryButton("Cancel appointment", { confirmCancel = true }, Modifier.fillMaxWidth(), icon = Icons.Default.EventBusy, color = CaloriesOrange)
                        } else if (a.startDateTime > now) {
                            FitLifeSecondaryButton("Restore appointment", { vm.setStatus(a, AppointmentStatus.UPCOMING) }, Modifier.fillMaxWidth(), icon = Icons.Default.Restore)
                        }
                        FitLifeSecondaryButton("Delete", { confirmDelete = true }, Modifier.fillMaxWidth(), icon = Icons.Default.Delete, color = HeartCoral)
                        Spacer(Modifier.height(24.dp))
                    }
                    if (confirmDelete) ConfirmDialog("Delete appointment?", "“${a.title}” and its reminders will be removed.", "Delete",
                        onConfirm = { vm.delete(a) { onBack() } }, onDismiss = { confirmDelete = false }, destructive = true)
                    if (confirmCancel) ConfirmDialog("Cancel appointment?", "Its reminders will be turned off.", "Cancel appointment",
                        onConfirm = { vm.setStatus(a, AppointmentStatus.CANCELLED) }, onDismiss = { confirmCancel = false }, destructive = true)
                }
            }
        }
        MessageSnackbar(calendarError ?: message, { calendarError = null; vm.clearMessage() }, Modifier.align(Alignment.BottomCenter))
    }
}
