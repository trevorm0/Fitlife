package com.example.fitwell.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fitwell.ui.components.FitLifeTopBar
import com.example.fitwell.ui.theme.TextSecondary

val helpText = listOf(
    "Reminders are late or missing" to "Open Settings and check that notifications and “Alarms & reminders” are allowed. On some phones you also need to exclude FitLife from battery optimisation. After a reboot or time-zone change FitLife rebuilds your reminders automatically.",
    "Recording doses" to "Reminders never mark a dose as taken. Open Health → Medication and tap Taken, Skipped or Missed yourself.",
    "Missed or skipped a dose?" to "FitLife can't tell you what to do. Ask your prescriber or pharmacist.",
    "Medical advice" to "FitLife is an organiser, not medical software. It doesn't diagnose, prescribe, recommend doses or monitor emergencies. In an emergency call your local emergency number.",
    "Health numbers on the Health tab" to "They are sample data for the prototype and are labelled as such. No watch, phone sensor or Health Connect source is connected yet."
)

val aboutText = listOf(
    "FitLife" to "Fitness, nutrition, medication reminders and appointments in one app. Version 2.0.",
    "Your data" to "Everything is stored on this device. Cloud backup is optional, off by default and only starts after you agree and sign in.",
    "Sample content" to "Workouts, the starter meals and the Health tab values are sample content. Add your own photos by dropping files into res/drawable (see README)."
)

@Composable
fun InfoScreen(title: String, sections: List<Pair<String, String>>, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        FitLifeTopBar(title, onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            sections.forEach { (h, body) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(h, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        }
    }
}
