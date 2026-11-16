package com.example.fitwell.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitwell.data.repository.HealthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HealthViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = application.fitLife().healthRepo
    private val _state = MutableStateFlow<HealthState?>(null)
    /** Null while the first load runs. */
    val state: StateFlow<HealthState?> = _state

    /** Permissions to request from Health Connect (read-only). */
    val permissions: Set<String> get() = repo.permissionSet

    fun refresh() { viewModelScope.launch { _state.value = repo.load() } }

    init { refresh() }
}
