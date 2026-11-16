package com.example.fitwell.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitwell.ui.components.*
import com.example.fitwell.ui.theme.*
import com.example.fitwell.util.formatDuration
import com.example.fitwell.viewmodel.ActiveWorkoutViewModel

@Composable
fun ActiveWorkoutScreen(workoutId: Long, onExit: () -> Unit, vm: ActiveWorkoutViewModel = viewModel()) {
    LaunchedEffect(workoutId) { vm.start(workoutId) }
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmEnd by remember { mutableStateOf(false) }

    // keep the screen on during a workout
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(enabled = !s.finished && !s.loading) { confirmEnd = true }

    when {
        s.loading -> LoadingState(Modifier.statusBarsPadding())
        s.error != null -> Column(Modifier.statusBarsPadding()) {
            FitLifeTopBar("Workout", onBack = onExit); ErrorState(s.error.orEmpty())
        }
        s.finished -> SummaryContent(s.workoutName, s.completedCount, s.exercises.size, s.elapsedSeconds, s.endedEarly, onExit)
        else -> {
            val cur = s.current!!
            Column(
                Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.workoutName, style = MaterialTheme.typography.titleMedium)
                        Text("Exercise ${s.index + 1} of ${s.exercises.size}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    IconButton(onClick = { confirmEnd = true }) { Icon(Icons.Default.Close, contentDescription = "End workout", tint = TextPrimary) }
                }
                LinearProgressIndicator(
                    progress = { s.progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = FitGreen, trackColor = DividerBlue
                )
                FitLifeImage(cur.imageReference, cur.name, Modifier.fillMaxWidth().height(160.dp).clip(FitLifeDimens.cardShape), Icons.Default.SelfImprovement)
                Text(cur.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (cur.durationSeconds > 0) {
                        RingProgress(s.remainingSeconds / cur.durationSeconds.toFloat(), FitGreen, Modifier.size(160.dp), 12.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(formatDuration(s.remainingSeconds.coerceAtLeast(0)), style = MaterialTheme.typography.displaySmall)
                                if (s.paused) Text("Paused", style = MaterialTheme.typography.labelMedium, color = CaloriesOrange)
                            }
                        }
                    } else {
                        RingProgress(1f, FitCyan, Modifier.size(160.dp), 12.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${cur.targetRepetitions ?: 10}", style = MaterialTheme.typography.displaySmall)
                                Text("reps", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            }
                        }
                    }
                }
                Text(cur.instructions, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

                if (cur.durationSeconds == 0) {
                    FitLifePrimaryButton("Done", vm::markDone, Modifier.fillMaxWidth(), icon = Icons.Default.Check)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (cur.durationSeconds > 0) {
                        FitLifePrimaryButton(
                            if (s.paused) "Resume" else "Pause", vm::togglePause, Modifier.weight(1f),
                            icon = if (s.paused) Icons.Default.PlayArrow else Icons.Default.Pause
                        )
                    }
                    FitLifeSecondaryButton("Skip", vm::skip, Modifier.weight(1f), icon = Icons.Default.SkipNext)
                }

                FitLifeCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Next", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(end = 12.dp))
                        val n = s.next
                        Text(
                            n?.let { "${it.name} • ${if (it.durationSeconds > 0) "${it.durationSeconds} sec" else "${it.targetRepetitions ?: 10} reps"}" } ?: "Last exercise!",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
                TextButton(onClick = { confirmEnd = true }, Modifier.align(Alignment.CenterHorizontally)) {
                    Text("End workout", color = HeartCoral)
                }
            }
        }
    }

    if (confirmEnd) {
        ConfirmDialog(
            "End workout?", "Your progress so far will be saved if you completed at least one exercise.",
            "End workout", onConfirm = { vm.endWorkout() }, onDismiss = { confirmEnd = false }, destructive = true
        )
    }
}

@Composable
private fun SummaryContent(name: String, done: Int, total: Int, seconds: Int, early: Boolean, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Icon(if (early) Icons.Default.Flag else Icons.Default.EmojiEvents, contentDescription = null, tint = FitGreen, modifier = Modifier.size(72.dp))
        Text(if (early) "Workout ended" else "Workout complete!", style = MaterialTheme.typography.headlineMedium)
        Text(name, style = MaterialTheme.typography.titleMedium, color = TextSecondary)
        FitLifeCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                SummaryStat("Exercises", "$done / $total")
                SummaryStat("Time", formatDuration(seconds))
            }
        }
        Text(
            "FitLife counts exercises and time only. It does not measure heart rate or calories.",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center
        )
        FitLifePrimaryButton("Done", onExit, Modifier.fillMaxWidth())
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}
