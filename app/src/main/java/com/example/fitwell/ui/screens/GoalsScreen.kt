package com.example.fitwell.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitwell.data.local.FitnessGoalEntity
import com.example.fitwell.data.local.GoalType
import com.example.fitwell.ui.components.*
import com.example.fitwell.ui.theme.*
import com.example.fitwell.viewmodel.GoalViewModel
import com.example.fitwell.viewmodel.defaultDescription
import com.example.fitwell.viewmodel.defaultUnit

private fun GoalType.icon(): ImageVector = when (this) {
    GoalType.LOSE_WEIGHT -> Icons.Default.MonitorWeight
    GoalType.BUILD_MUSCLE -> Icons.Default.FitnessCenter
    GoalType.IMPROVE_ENDURANCE -> Icons.Default.Speed
    GoalType.STAY_HEALTHY -> Icons.Default.Favorite
}

private fun GoalType.color(): Color = when (this) {
    GoalType.LOSE_WEIGHT -> FitCyan
    GoalType.BUILD_MUSCLE -> SleepPurple
    GoalType.IMPROVE_ENDURANCE -> CaloriesOrange
    GoalType.STAY_HEALTHY -> FitGreen
}

private fun fmt(d: Double) = if (d == d.toLong().toDouble()) d.toLong().toString() else "%.1f".format(d)

@Composable
fun GoalsScreen(onBack: () -> Unit, vm: GoalViewModel = viewModel()) {
    val active by vm.activeGoal.collectAsStateWithLifecycle()
    // dialog target: (type, goal being edited or null for a new goal)
    var dialog by remember { mutableStateOf<Pair<GoalType, FitnessGoalEntity?>?>(null) }
    var progressDialog by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        FitLifeTopBar("Goals", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Choose what matters to you and set a target you can measure.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
            GoalType.entries.forEach { type ->
                item(key = type.name) {
                    val isActive = active?.goalType == type
                    GoalProgressCard(
                        type.label, type.defaultDescription(), type.icon(), type.color(),
                        progress = if (isActive) active?.progress else null, selected = isActive,
                        onClick = { dialog = type to (if (isActive) active else null) }
                    )
                }
            }
            item { SectionHeader("Your Current Goal") }
            val g = active
            if (g == null) {
                item { EmptyState("No goal selected", "Tap a goal above and set a target.", icon = Icons.Default.Flag) }
            } else {
                item {
                    FitLifeCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(g.goalType.icon(), null, tint = g.goalType.color())
                                Text(g.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text("${(g.progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = FitGreen)
                            }
                            LinearProgressIndicator({ g.progress }, Modifier.fillMaxWidth().height(8.dp), color = FitGreen, trackColor = DividerBlue)
                            Text("${fmt(g.currentValue)} / ${fmt(g.targetValue)} ${g.unit}", style = MaterialTheme.typography.bodyMedium)
                            val remaining = kotlin.math.abs(g.targetValue - g.currentValue)
                            Text(
                                if (g.progress >= 1f) "Target reached 🎉" else "${fmt(remaining)} ${g.unit} to go",
                                style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                            )
                        }
                    }
                }
                item { FitLifePrimaryButton("Update progress", { progressDialog = true }, Modifier.fillMaxWidth(), icon = Icons.Default.Insights) }
                item { FitLifeSecondaryButton("Edit Goal", { dialog = g.goalType to g }, Modifier.fillMaxWidth(), icon = Icons.Default.Edit) }
                item { FitLifeSecondaryButton("Remove goal", { confirmRemove = true }, Modifier.fillMaxWidth(), icon = Icons.Default.Delete, color = HeartCoral) }
            }
        }
    }

    dialog?.let { (type, existing) -> GoalDialog(type, existing, vm, onDismiss = { dialog = null }) }
    if (progressDialog && active != null) ProgressDialog(active!!, onDismiss = { progressDialog = false }) { vm.updateProgress(active!!, it) }
    if (confirmRemove && active != null) ConfirmDialog("Remove goal?", "Your goal and its progress will be deleted.", "Remove", { vm.delete(active!!) }, { confirmRemove = false }, destructive = true)
}

@Composable
private fun GoalDialog(type: GoalType, existing: FitnessGoalEntity?, vm: GoalViewModel, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(existing?.title ?: type.label) }
    var unit by remember { mutableStateOf(existing?.unit ?: type.defaultUnit()) }
    var start by remember { mutableStateOf(existing?.startValue?.let(::fmt) ?: "0") }
    var current by remember { mutableStateOf(existing?.currentValue?.let(::fmt) ?: "0") }
    var target by remember { mutableStateOf(existing?.targetValue?.let(::fmt) ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val dec = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardElevated,
        title = { Text(if (existing == null) "Set up: ${type.label}" else "Edit goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FitLifeTextField(title, { title = it }, "Goal name")
                FitLifeTextField(unit, { unit = it }, "Unit (kg, minutes, sessions…)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { FitLifeTextField(start, { start = it }, "Start", keyboardOptions = dec) }
                    Box(Modifier.weight(1f)) { FitLifeTextField(current, { current = it }, "Now", keyboardOptions = dec) }
                }
                FitLifeTextField(target, { target = it }, "Target", keyboardOptions = dec)
                Text("Choose values that suit you; FitLife doesn't recommend targets.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                error?.let { Text(it, color = HeartCoral, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = vm.save(existing, type, title, unit, start, current, target)
                if (error == null) onDismiss()
            }) { Text("Save", color = FitGreen) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
private fun ProgressDialog(goal: FitnessGoalEntity, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(fmt(goal.currentValue)) }
    val v = text.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardElevated, title = { Text("Update progress") },
        text = { FitLifeTextField(text, { text = it }, "Current value (${goal.unit})", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), error = "Enter a number".takeIf { v == null }) },
        confirmButton = { TextButton(enabled = v != null, onClick = { onSave(v!!); onDismiss() }) { Text("Save", color = FitGreen) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}
