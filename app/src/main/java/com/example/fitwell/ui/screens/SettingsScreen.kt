package com.example.fitwell.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitwell.ui.components.*
import com.example.fitwell.ui.theme.*
import com.example.fitwell.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val cloud by vm.cloud.collectAsStateWithLifecycle()
    var consent by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        FitLifeTopBar("Settings", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Text("Reminders & privacy", style = MaterialTheme.typography.titleMedium)
            ReminderPermissionBanner()
            SwitchRow("Hide details on lock screen", "Medication and appointment names appear only after you unlock your phone", prefs.hideLockScreenDetails) { vm.setHideLockScreenDetails(it) }
            FitLifeSecondaryButton("Refresh all reminders", { vm.refreshReminders() }, Modifier.fillMaxWidth(), icon = Icons.Default.Refresh)
            Text("Android may delay notifications to save battery. If reminders are late, allow “Alarms & reminders” and exclude FitLife from battery optimisation.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

            HorizontalDivider(color = DividerBlue)
            Text("Cloud backup (optional)", style = MaterialTheme.typography.titleMedium)
            Text("Your data stays on this device unless you turn this on.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            SwitchRow("Enable cloud backup", if (prefs.cloudBackupEnabled) "On: you choose when to back up" else "Off: nothing is sent anywhere", prefs.cloudBackupEnabled) {
                if (it) consent = true else vm.setCloudEnabled(false)
            }

            if (prefs.cloudBackupEnabled) {
                if (!cloud.available) {
                    FitLifeCard(Modifier.fillMaxWidth(), borderColor = CaloriesOrange) {
                        Text(
                            "Cloud backup isn't set up in this build. Add your Firebase google-services.json to the app/ folder and rebuild (see README).",
                            Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = TextSecondary
                        )
                    }
                } else if (cloud.email == null) {
                    FitLifeTextField(email, { email = it }, "Email", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    OutlinedTextField(
                        password, { password = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Password (6+ characters)") },
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = com.example.fitwell.ui.theme.FitLifeDimens.fieldShape, colors = fitLifeFieldColors()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FitLifePrimaryButton("Sign in", { vm.signIn(email, password, false) }, Modifier.weight(1f), enabled = !cloud.busy)
                        FitLifeSecondaryButton("Create account", { vm.signIn(email, password, true) }, Modifier.weight(1f), enabled = !cloud.busy)
                    }
                } else {
                    Text("Signed in as ${cloud.email}", style = MaterialTheme.typography.bodyMedium)
                    FitLifePrimaryButton("Back up now", { vm.backUpNow() }, Modifier.fillMaxWidth(), icon = Icons.Default.CloudUpload, enabled = !cloud.busy)
                    FitLifeSecondaryButton("Restore from backup", { confirmRestore = true }, Modifier.fillMaxWidth(), icon = Icons.Default.CloudDownload, enabled = !cloud.busy)
                    FitLifeSecondaryButton("Delete cloud backup", { confirmDelete = true }, Modifier.fillMaxWidth(), icon = Icons.Default.DeleteForever, color = HeartCoral, enabled = !cloud.busy)
                    TextButton(onClick = vm::signOut) { Text("Sign out", color = TextSecondary) }
                }
                if (cloud.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = FitGreen, trackColor = DividerBlue)
                cloud.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FitCyan) }
            }
            Text(MEDICAL_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (consent) AlertDialog(
        onDismissRequest = { consent = false }, containerColor = CardElevated, title = { Text("Turn on cloud backup?") },
        text = {
            Text(
                "If you back up, a copy of your FitLife data is uploaded to your own Firebase account: workouts, meals, goals, profile, medications, " +
                    "dose history and appointments (including notes). Nothing is uploaded until you sign in and tap “Back up now”. " +
                    "You can delete the cloud copy at any time.",
                color = TextSecondary
            )
        },
        confirmButton = { TextButton(onClick = { vm.setCloudEnabled(true); consent = false }) { Text("I agree, turn on", color = FitGreen) } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("Keep it off", color = TextSecondary) } }
    )
    if (confirmRestore) ConfirmDialog("Restore from backup?", "This REPLACES the data on this device with your cloud backup.", "Restore", { vm.restore() }, { confirmRestore = false }, destructive = true)
    if (confirmDelete) ConfirmDialog("Delete cloud backup?", "The copy in the cloud is permanently deleted. Data on this device is not affected.", "Delete", { vm.deleteBackup() }, { confirmDelete = false }, destructive = true)
}
