package com.example.fitwell.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitwell.data.local.ExerciseEntity
import com.example.fitwell.ui.components.*
import com.example.fitwell.ui.theme.*
import com.example.fitwell.util.formatDuration
import com.example.fitwell.viewmodel.WorkoutViewModel
import kotlinx.coroutines.flow.map

@Composable
fun WorkoutDetailsScreen(id: Long, onBack: () -> Unit, onStart: () -> Unit, vm: WorkoutViewModel = viewModel()) {
    val loaded by remember(id) { vm.observeWorkout(id).map { Loaded(it) } }.collectAsStateWithLifecycle(initialValue = null)
    val exercises by remember(id) { vm.observeExercises(id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val state = loaded
    if (state == null) { LoadingState(Modifier.statusBarsPadding()); return }
    val w = state.value
    if (w == null) {
        Column { FitLifeTopBar("Workout", onBack = onBack); EmptyState("Workout not found", "It may have been removed.") }
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(260.dp)) {
                    FitLifeImage(w.imageReference, "${w.name} workout", Modifier.matchParentSize())
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Midnight.copy(alpha = 0.5f), Color.Transparent, Midnight))))
                    IconButton(onClick = onBack, Modifier.statusBarsPadding().padding(8.dp).background(Midnight.copy(alpha = 0.55f), CircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(w.name, style = MaterialTheme.typography.headlineMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        InfoPill(Icons.Default.Timer, "${w.durationMinutes} minutes")
                        InfoPill(Icons.Default.BarChart, w.difficulty.label)
                        InfoPill(Icons.Default.FitnessCenter, w.targetArea)
                    }
                    HorizontalDivider(color = DividerBlue)
                    Text("About this workout", style = MaterialTheme.typography.titleMedium)
                    Text(w.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    HorizontalDivider(color = DividerBlue)
                    Text("Exercises (${exercises.size})", style = MaterialTheme.typography.titleMedium)
                }
            }
            itemsIndexed(exercises, key = { _, e -> e.exerciseId }) { i, e ->
                ExerciseRow(i + 1, e, Modifier.padding(horizontal = 16.dp, vertical = 5.dp))
            }
        }
        FitLifePrimaryButton(
            "Start Workout", onStart, icon = Icons.Default.PlayArrow, enabled = exercises.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)
        )
    }
}

@Composable
private fun InfoPill(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = FitGreen, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ExerciseRow(number: Int, e: ExerciseEntity, modifier: Modifier = Modifier) {
    FitLifeCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FitLifeImage(e.imageReference, null, Modifier.size(52.dp).background(CardElevated, RoundedCornerShape(10.dp)), Icons.Default.SelfImprovement)
            Column(Modifier.weight(1f)) {
                Text("$number. ${e.name}", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (e.durationSeconds > 0) "${e.durationSeconds} sec" else "${e.targetRepetitions ?: 10} reps",
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary
                )
            }
        }
    }
}
