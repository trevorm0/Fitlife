package com.example.fitwell.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitwell.ui.components.*
import com.example.fitwell.ui.theme.*
import com.example.fitwell.util.*
import com.example.fitwell.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onOpenWorkout: (Long) -> Unit, onStartWorkout: (Long) -> Unit, onOpenHealthTab: (Int) -> Unit,
    onOpenMedication: (Long) -> Unit, onOpenAppointment: (Long) -> Unit,
    onAddMedication: () -> Unit, onAddAppointment: () -> Unit, onOpenGoals: () -> Unit, onOpenProfile: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val featured by vm.featured.collectAsStateWithLifecycle()
    val weekly by vm.weekly.collectAsStateWithLifecycle()
    val nextDose by vm.nextDose.collectAsStateWithLifecycle()
    val nextAppt by vm.nextAppointment.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { vm.refreshHealth() }
    val name = profile?.displayName?.substringBefore(" ")?.ifBlank { null } ?: "there"

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hello,", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    Text(name, style = MaterialTheme.typography.headlineMedium)
                    Text("Good things take effort 💪", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                androidx.compose.foundation.layout.Box(Modifier.size(48.dp)) {
                    IconButton(onClick = onOpenProfile, Modifier.fillMaxSize()) {
                        Avatar(profile?.displayName.orEmpty(), profile?.profileImageReference, Modifier.size(44.dp))
                    }
                }
            }
        }

        item {
            val f = featured
            if (f == null) LoadingState() else FeaturedWorkoutCard(f, onStart = { onStartWorkout(f.workoutId) }, onOpen = { onOpenWorkout(f.workoutId) })
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FitnessMetricCard("Steps", health.steps?.let { "%,d".format(it) } ?: "—", "/ %,d".format(health.stepsGoal),
                        ((health.steps ?: 0) / health.stepsGoal.toFloat()).coerceIn(0f, 1f), FitGreen, Icons.Default.DirectionsWalk, Modifier.weight(1f))
                    FitnessMetricCard("Active kcal", health.activeCalories?.toString() ?: "—", if (health.activeCalories == null) "no data" else "today",
                        0f, CaloriesOrange, Icons.Default.LocalFireDepartment, Modifier.weight(1f))
                    val bpm = health.latestPulse ?: health.avgHeartRate
                    FitnessMetricCard("Pulse", bpm?.toString() ?: "—", if (bpm == null) "not measured" else "bpm",
                        if (bpm == null) 0f else 1f, HeartCoral, Icons.Default.Favorite, Modifier.weight(1f))
                }
            }
        }

        item { SectionHeader("Your Progress", actionText = "Goals", onAction = onOpenGoals) }
        item {
            FitLifeCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RingProgress(weekly.workouts / weekly.workoutTarget.coerceAtLeast(1).toFloat(), FitGreen, Modifier.size(84.dp), 9.dp) {
                        Text("${weekly.workouts}/${weekly.workoutTarget}", style = MaterialTheme.typography.titleMedium)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Weekly Goal", style = MaterialTheme.typography.titleSmall)
                        ProgressRow("Workouts", "${weekly.workouts}/${weekly.workoutTarget}")
                        ProgressRow("Active minutes", "${weekly.activeMinutes}/${weekly.minutesTarget}")
                        ProgressRow("Calories logged today", "%,d/%,d".format(weekly.caloriesLogged, weekly.calorieTarget))
                    }
                }
            }
        }

        // ---- Medication reminder card (from saved data) ----
        item {
            FitLifeCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Medication, null, tint = FitGreen)
                        Text("Medication Reminder", style = MaterialTheme.typography.titleMedium)
                    }
                    val dose = nextDose
                    if (dose == null) {
                        EmptyState(
                            "No medication reminders", "Add a medication you take and FitLife will remind you at the times you choose.",
                            actionLabel = "Add medication", onAction = onAddMedication, icon = Icons.Default.Medication
                        )
                    } else {
                        Text(dose.medication.name, style = MaterialTheme.typography.titleLarge)
                        Text("Next reminder: ${formatDateTime(dose.scheduledAt)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Instructions: ${dose.medication.instructions?.takeIf { it.isNotBlank() } ?: dose.medication.dosage}",
                            style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        StatusChip(if (dose.medication.remindersEnabled && dose.reminder.isEnabled) "Reminders on" else "Reminders off", FitGreen)
                        Spacer(Modifier.height(4.dp))
                        FitLifeSecondaryButton("View Schedule", { onOpenHealthTab(1) }, Modifier.fillMaxWidth())
                    }
                }
            }
        }

        // ---- Upcoming appointment card (from saved data) ----
        item {
            FitLifeCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Event, null, tint = FitCyan)
                        Text("Upcoming Appointment", style = MaterialTheme.typography.titleMedium)
                    }
                    val a = nextAppt
                    if (a == null) {
                        EmptyState(
                            "No upcoming appointments", "Keep track of doctor, dental or wellness visits and get reminders.",
                            actionLabel = "Add appointment", onAction = onAddAppointment, icon = Icons.Default.Event
                        )
                    } else {
                        Text(a.title, style = MaterialTheme.typography.titleLarge)
                        Text(formatDateTime(a.startDateTime), style = MaterialTheme.typography.bodyMedium)
                        a.location?.takeIf { it.isNotBlank() }?.let { Text("Location: $it", style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
                        Text("Time remaining: ${timeUntil(a.startDateTime)}", style = MaterialTheme.typography.bodyMedium, color = FitCyan)
                        Spacer(Modifier.height(4.dp))
                        FitLifeSecondaryButton("View Appointment", { onOpenAppointment(a.appointmentId) }, Modifier.fillMaxWidth(), color = FitCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.CheckCircle, null, tint = FitGreen, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
