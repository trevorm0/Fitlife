package com.example.fitwell.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitwell.data.local.WorkoutCategory
import com.example.fitwell.ui.components.*
import com.example.fitwell.ui.theme.*
import com.example.fitwell.util.formatDateTime
import com.example.fitwell.util.formatDuration
import com.example.fitwell.viewmodel.ProfileViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    onOpenGoals: () -> Unit, onOpenHistory: () -> Unit, onOpenSettings: () -> Unit, onOpenHelp: () -> Unit, onOpenAbout: () -> Unit,
    onOpenPulse: () -> Unit = {}, onOpenSteps: () -> Unit = {}, onOpenCoach: () -> Unit = {},
    onOpenLeagues: () -> Unit = {}, onOpenMealPhoto: () -> Unit = {}, onOpenAdaptive: () -> Unit = {},
    onSignedOut: () -> Unit = {},
    vm: ProfileViewModel = viewModel()
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDeleteAccount by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ProfileHeader(profile?.displayName ?: "Athlete", profile?.tagline.orEmpty().ifBlank { "Fitness Enthusiast" }, imageReference = profile?.profileImageReference) }
        vm.email?.let { mail ->
            item { Text(mail, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        item {
            Text("Preferred activity: ${profile?.preferredWorkoutCategory ?: "—"}", style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        item {
            FitLifeCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat("${stats.workouts}", "Workouts")
                    Stat("${stats.streakDays}", "Day streak")
                    Stat("${stats.totalMinutes}", "Active min")
                }
            }
        }
        item { FitLifeSecondaryButton("Edit profile", { edit = true }, Modifier.fillMaxWidth(), icon = Icons.Default.Edit) }
        item {
            FitLifeCard(Modifier.fillMaxWidth()) {
                Column {
                    MenuRow(Icons.Default.Insights, "My Progress", onOpenHistory)
                    HorizontalDivider(color = DividerBlue)
                    MenuRow(Icons.Default.Flag, "Goals", onOpenGoals)
                    HorizontalDivider(color = DividerBlue)
                    MenuRow(Icons.Default.Favorite, "Pulse", onOpenPulse)
                    HorizontalDivider(color = DividerBlue)
                    MenuRow(Icons.Default.DirectionsWalk, "Steps", onOpenSteps)
                    HorizontalDivider(color = DividerBlue)
                    MenuRow(Icons.Default.CameraAlt, "Form coach", onOpenCoach)
                    HorizontalDivider(color = DividerBlue)
                    MenuRow(Icons.Default.Groups, "Leagues", onOpenLeagues)
                    HorizontalDivider(color = DividerBlue)
                    MenuRow(Icons.Default.PhotoCamera, "Meal photo", onOpenMealPhoto)
                    HorizontalDivider(color = DividerBlue)
                    MenuRow(Icons.Default.AutoAwesome, "Adaptive plan", onOpenAdaptive)
                    HorizontalDivider(color = DividerBlue)
                    MenuRow(Icons.Default.Settings, "Settings", onOpenSettings)
                    HorizontalDivider(color = DividerBlue)
                    MenuRow(Icons.Default.Notifications, "Reminders", onOpenSettings)
                    HorizontalDivider(color = DividerBlue)
                    MenuRow(Icons.Default.SupportAgent, "Help & Support", onOpenHelp)
                    HorizontalDivider(color = DividerBlue)
                    MenuRow(Icons.Default.Info, "About FitLife", onOpenAbout)
                }
            }
        }
        item { FitLifeSecondaryButton("Reset fitness data", { reset = true }, Modifier.fillMaxWidth(), icon = Icons.Default.RestartAlt, color = HeartCoral) }
        item { FitLifeSecondaryButton("Sign out", { confirmSignOut = true }, Modifier.fillMaxWidth(), icon = Icons.Default.Logout) }
        item { FitLifeSecondaryButton("Delete account and data", { confirmDeleteAccount = true }, Modifier.fillMaxWidth(), icon = Icons.Default.DeleteForever, color = HeartCoral) }
        item { Text("Your data is stored on this device under your account. Signing out keeps it; signing back in restores it.", style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
    }

    if (edit) {
        var name by remember { mutableStateOf(profile?.displayName.orEmpty()) }
        var tagline by remember { mutableStateOf(profile?.tagline.orEmpty()) }
        var category by remember { mutableStateOf(profile?.preferredWorkoutCategory ?: "Strength") }
        AlertDialog(
            onDismissRequest = { edit = false }, containerColor = CardElevated, title = { Text("Edit profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FitLifeTextField(name, { name = it }, "Display name", error = "Enter a name".takeIf { name.isBlank() })
                    FitLifeTextField(tagline, { tagline = it }, "Fitness level or tagline")
                    Text("Preferred activity", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WorkoutCategory.entries.forEach { c -> CategoryFilterChip(c.label, category == c.label, { category = c.label }) }
                    }
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.saveProfile(name, tagline, category); edit = false }) { Text("Save", color = FitGreen) } },
            dismissButton = { TextButton(onClick = { edit = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }
    if (confirmSignOut) ConfirmDialog(
        "Sign out?", "Your data stays on this phone. Medication and appointment reminders pause until you sign in again.",
        "Sign out", { confirmSignOut = false; onSignedOut(); vm.signOut() }, { confirmSignOut = false }
    )
    if (confirmDeleteAccount) ConfirmDialog(
        "Delete your account?", "This permanently deletes your account and ALL its data on this phone: workouts, meals, goals, medications, appointments and readings. It cannot be undone.",
        "Delete everything", { confirmDeleteAccount = false; onSignedOut(); vm.deleteAccount() }, { confirmDeleteAccount = false }, destructive = true
    )
    if (reset) ConfirmDialog(
        "Reset fitness data?", "This deletes workout history, meals and goals on this device. Medications and appointments are not affected.",
        "Reset", { vm.resetFitnessData {} }, { reset = false }, destructive = true
    )
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickableRow(onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = TextPrimary)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextSecondary)
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick))

// ====================================================================================
// Workout history ("My Progress")
// ====================================================================================
@Composable
fun HistoryScreen(onBack: () -> Unit, vm: ProfileViewModel = viewModel()) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    var deleteId by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize()) {
        FitLifeTopBar("My Progress", onBack = onBack)
        if (sessions.isEmpty()) {
            EmptyState("No workouts yet", "Finish a workout and it will show up here.", icon = Icons.Default.FitnessCenter)
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("${stats.workouts} completed • ${stats.totalMinutes} active minutes • ${stats.streakDays}-day streak", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                items(sessions, key = { it.sessionId }) { s ->
                    FitLifeCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(s.workoutName, style = MaterialTheme.typography.titleSmall)
                                Text("${formatDateTime(s.startTimestamp)} • ${formatDuration(s.durationSeconds)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Text("${s.exercisesCompleted}/${s.exercisesTotal} exercises", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            StatusChip(if (s.completed) "Completed" else "Ended early", if (s.completed) FitGreen else CaloriesOrange)
                            IconButton(onClick = { deleteId = s.sessionId }) { Icon(Icons.Default.Delete, contentDescription = "Delete ${s.workoutName} session", tint = TextSecondary) }
                        }
                    }
                }
            }
        }
    }
    deleteId?.let { id -> ConfirmDialog("Delete this session?", "It will be removed from your history and streak.", "Delete", { vm.deleteSession(id) }, { deleteId = null }, destructive = true) }
}
