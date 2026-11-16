package com.example.fitwell.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.fitwell.reminders.AlarmHelper
import com.example.fitwell.reminders.NotificationHelper
import com.example.fitwell.ui.theme.*

/** Wraps a possibly-missing value so "still loading" and "does not exist" can be told apart. */
data class Loaded<T>(val value: T?)

/**
 * Explains and fixes the two things that stop reminders: notification permission and exact-alarm access.
 * Re-checks whenever the user returns from system settings.
 */
@Composable
fun ReminderPermissionBanner(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    var asked by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val notificationsOk = remember(tick) { NotificationHelper.areNotificationsAllowed(ctx) }
    val exactOk = remember(tick) { AlarmHelper.canScheduleExact(ctx) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    if (!notificationsOk) {
        PermissionCard(
            "Notifications are off",
            "FitLife can save your schedules, but it cannot show reminders until notifications are allowed.",
            "Allow notifications", modifier
        ) {
            if (Build.VERSION.SDK_INT >= 33 && !asked) {
                asked = true
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
        }
    } else if (!exactOk) {
        PermissionCard(
            "Reminder timing may be late",
            "Android only allows approximate timing until you allow “Alarms & reminders” for FitLife. Reminders still work, but can arrive a few minutes late.",
            "Open settings", modifier
        ) {
            if (Build.VERSION.SDK_INT >= 31) {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
            }
        }
    }
}

@Composable
private fun PermissionCard(title: String, body: String, action: String, modifier: Modifier, onClick: () -> Unit) {
    FitLifeCard(modifier.fillMaxWidth(), borderColor = CaloriesOrange) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = CaloriesOrange)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { Text(action, color = FitGreen) }
            }
        }
    }
}

/** Shows [message] once in a snackbar, then calls [onShown] so it is not repeated. */
@Composable
fun MessageSnackbar(message: String?, onShown: () -> Unit, modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            host.showSnackbar(message, duration = SnackbarDuration.Long)
            onShown()
        }
    }
    SnackbarHost(host, modifier)
}

@Composable
fun ConfirmDialog(
    title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardElevated,
        title = { Text(title) }, text = { Text(message, color = TextSecondary) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = if (destructive) HeartCoral else FitGreen) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

const val MEDICAL_DISCLAIMER =
    "FitLife records and reminds. It does not give medical advice, recommend doses, or tell you what to do about a missed dose. " +
        "Ask your prescriber or pharmacist."
