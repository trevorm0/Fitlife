package com.example.fitwell.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.example.fitwell.data.repository.HealthConnection
import com.example.fitwell.ui.components.*
import com.example.fitwell.ui.theme.*
import com.example.fitwell.viewmodel.HealthViewModel

private val healthTabs = listOf("Overview", "Medication", "Appointments")

@Composable
fun HealthScreen(
    initialTab: Int,
    onOpenMedication: (Long) -> Unit, onAddMedication: () -> Unit, onMedicationHistory: () -> Unit,
    onOpenAppointment: (Long) -> Unit, onEditAppointment: (Long) -> Unit, onAddAppointment: () -> Unit
) {
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, healthTabs.lastIndex)) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Health", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(healthTabs.indices.toList()) { i -> CategoryFilterChip(healthTabs[i], tab == i, { tab = i }) }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> HealthOverview(onOpenTab = { tab = it })
                1 -> MedicationScreen(onOpenMedication, onAddMedication, onMedicationHistory)
                else -> AppointmentsScreen(onOpenAppointment, onEditAppointment, onAddAppointment)
            }
        }
    }
}

@Composable
private fun HealthOverview(onOpenTab: (Int) -> Unit, vm: HealthViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { vm.refresh() }

    val st = state
    if (st == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = FitGreen) }
        return
    }
    val h = st.snapshot
    fun open(intent: android.content.Intent) { runCatching { context.startActivity(intent) } }

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            FitLifeCard(Modifier.fillMaxWidth(), borderColor = FitCyan.copy(alpha = 0.6f)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Watch, null, tint = FitCyan)
                        Text(
                            when (st.connection) {
                                HealthConnection.CONNECTED -> "Connected to Health Connect"
                                HealthConnection.NEEDS_PERMISSION -> "Connect your watch and health apps"
                                HealthConnection.NEEDS_INSTALL -> "Health Connect needs to be installed or updated"
                                HealthConnection.UNSUPPORTED -> "Health Connect isn't available on this phone"
                            }, style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        when (st.connection) {
                            HealthConnection.CONNECTED ->
                                "Reading ${st.grantedCount} of ${st.totalPermissions} data types. FitLife only reads this data and keeps none of it."
                            HealthConnection.NEEDS_PERMISSION ->
                                "Health Connect brings steps, heart rate, sleep and calories from your watch, Samsung Health, Google Fit and other apps into one place. " +
                                    "FitLife only reads them, and only if you allow it."
                            HealthConnection.NEEDS_INSTALL -> "Install or update the Health Connect app, then come back here to connect."
                            HealthConnection.UNSUPPORTED -> "You still get this phone's step counter and your own pulse readings below."
                        }, style = MaterialTheme.typography.bodySmall, color = TextSecondary
                    )
                    when (st.connection) {
                        HealthConnection.NEEDS_PERMISSION -> {
                            FitLifePrimaryButton("Connect Health Connect", { permissionLauncher.launch(vm.permissions) }, Modifier.fillMaxWidth())
                            TextButton(onClick = { open(android.content.Intent(androidx.health.connect.client.HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }) {
                                Text("Permission dialog not showing? Open Health Connect settings", color = FitCyan)
                            }
                        }
                        HealthConnection.NEEDS_INSTALL -> FitLifePrimaryButton("Get Health Connect", {
                            open(android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"))
                                .setPackage("com.android.vending"))
                        }, Modifier.fillMaxWidth())
                        HealthConnection.CONNECTED -> {
                            if (st.grantedCount < st.totalPermissions) FitLifeSecondaryButton("Allow more data types", { permissionLauncher.launch(vm.permissions) }, Modifier.fillMaxWidth())
                            TextButton(onClick = { open(android.content.Intent(androidx.health.connect.client.HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }) {
                                Text("Manage in Health Connect", color = FitCyan)
                            }
                        }
                        HealthConnection.UNSUPPORTED -> Unit
                    }
                }
            }
        }
        item {
            FitLifeCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Favorite, null, tint = HeartCoral, modifier = Modifier.size(36.dp))
                        Column {
                            Text("Heart Rate", style = MaterialTheme.typography.titleSmall)
                            val shown = h.avgHeartRate ?: h.latestPulse
                            Text(shown?.let { "$it bpm" } ?: "No data yet", style = MaterialTheme.typography.titleLarge)
                            if (h.avgHeartRate != null) Text("Average today", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            else if (h.latestPulse != null) Text("Latest pulse reading", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                    if (h.heartRateSeries.size >= 2) {
                        LineChart(h.heartRateSeries, FitGreen, Modifier.fillMaxWidth().height(120.dp), emptyList(), h.heartRateLabel)
                        Text(h.heartRateLabel, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    } else {
                        Text(
                            "Measure your pulse with the camera (Profile → Pulse), or connect a watch above, to see your heart rate here.",
                            style = MaterialTheme.typography.bodySmall, color = TextSecondary
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HealthMetricCard("Steps", h.steps?.let { "%,d".format(it) } ?: "—", "/ %,d · ${h.stepsSource}".format(h.stepsGoal), Icons.Default.DirectionsWalk, FitGreen,
                    Modifier.weight(1f), ((h.steps ?: 0) / h.stepsGoal.toFloat()).coerceIn(0f, 1f))
                HealthMetricCard("Active calories", h.activeCalories?.let { "$it kcal" } ?: "—",
                    if (h.activeCalories == null) "Needs Health Connect" else "Today", Icons.Default.LocalFireDepartment, CaloriesOrange, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HealthMetricCard("Sleep", h.sleepMinutes?.let { "${it / 60} h ${it % 60} m" } ?: "—",
                    if (h.sleepMinutes == null) "Needs Health Connect" else "Last night", Icons.Default.Bedtime, SleepPurple,
                    Modifier.weight(1f), h.sleepMinutes?.let { (it / 480f).coerceIn(0f, 1f) })
                HealthMetricCard("Pulse", h.latestPulse?.let { "$it bpm" } ?: "—",
                    if (h.latestPulse == null) "Not measured yet" else "From your camera reading", Icons.Default.MonitorHeart, FitCyan, Modifier.weight(1f))
            }
        }
        if (h.stepsWeek.any { it > 0f }) item {
            FitLifeCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Steps, last 7 days", style = MaterialTheme.typography.titleSmall)
                    BarChart(h.stepsWeek, h.weekLabels, FitGreen, description = "Steps per day over the last 7 days")
                }
            }
        }
        if (h.sleepWeekHours.any { it > 0f }) item {
            FitLifeCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sleep, last 7 nights (hours)", style = MaterialTheme.typography.titleSmall)
                    BarChart(h.sleepWeekHours, h.weekLabels, SleepPurple, description = "Hours of sleep per night over the last 7 days")
                }
            }
        }
        item { SectionHeader("Manage your health") }
        item {
            GoalProgressCard("Medication", "Schedules, reminders and dose history", Icons.Default.Medication, FitGreen, onClick = { onOpenTab(1) })
        }
        item {
            GoalProgressCard("Appointments", "Doctor, dental and wellness visits", Icons.Default.Event, FitCyan, onClick = { onOpenTab(2) })
        }
    }
}
