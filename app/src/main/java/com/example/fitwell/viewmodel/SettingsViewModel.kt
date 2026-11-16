package com.example.fitwell.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitwell.data.prefs.UserPrefs
import com.example.fitwell.reminders.ReminderCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CloudUiState(
    val available: Boolean = false, val email: String? = null, val busy: Boolean = false, val message: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.fitLife()
    private val backup = app.cloudBackup

    val prefs: StateFlow<UserPrefs> = app.prefs.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPrefs())

    private val _cloud = MutableStateFlow(CloudUiState(available = backup.isAvailable(app), email = backup.currentEmail(app)))
    val cloud: StateFlow<CloudUiState> = _cloud

    fun setHideLockScreenDetails(v: Boolean) = viewModelScope.launch { app.prefs.setHideLockScreenDetails(v) }
    fun setCloudEnabled(v: Boolean) = viewModelScope.launch { app.prefs.setCloudBackupEnabled(v) }
    fun refreshReminders() = viewModelScope.launch { ReminderCoordinator.rescheduleAll(app) }
    fun clearCloudMessage() = _cloud.update { it.copy(message = null) }

    private fun run(okMessage: String, block: suspend () -> Unit) = viewModelScope.launch {
        _cloud.update { it.copy(busy = true, message = null) }
        try {
            block()
            _cloud.update { it.copy(busy = false, message = okMessage, email = backup.currentEmail(app)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _cloud.update { it.copy(busy = false, message = e.localizedMessage ?: "Something went wrong") }
        }
    }

    fun signIn(email: String, password: String, register: Boolean) {
        if (email.isBlank() || password.length < 6) {
            _cloud.update { it.copy(message = "Enter an email and a password of at least 6 characters") }; return
        }
        run("Signed in") { backup.signIn(email, password, register) }
    }
    fun signOut() { backup.signOut(); _cloud.update { it.copy(email = null, message = "Signed out") } }
    fun backUpNow() = run("Backup uploaded") { backup.backUp() }
    fun restore() = run("Restored from cloud backup") {
        if (!backup.restore()) error("No cloud backup found for this account")
        ReminderCoordinator.rescheduleAll(app) // alarms must match the restored data
    }
    fun deleteBackup() = run("Cloud backup deleted") { backup.deleteBackup() }
}
