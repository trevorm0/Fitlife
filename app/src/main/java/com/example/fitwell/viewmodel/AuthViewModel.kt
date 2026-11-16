package com.example.fitwell.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitwell.data.auth.AuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AuthUiState(val busy: Boolean = false, val error: String? = null)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.fitLife()
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    fun clearError() { _state.value = _state.value.copy(error = null) }

    fun submit(register: Boolean, name: String, email: String, password: String, confirm: String, onSuccess: () -> Unit) {
        if (_state.value.busy) return
        if (register && password != confirm) {
            _state.value = AuthUiState(error = "Passwords do not match"); return
        }
        _state.value = AuthUiState(busy = true)
        viewModelScope.launch {
            try {
                // Password hashing is deliberately slow, so keep it off the main thread.
                val account = withContext(Dispatchers.Default) {
                    if (register) app.auth.register(name, email, password) else app.auth.login(email, password)
                }
                app.openSession(account)
                _state.value = AuthUiState()
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                _state.value = AuthUiState(error = e.message)
            } catch (e: Exception) {
                _state.value = AuthUiState(error = "Something went wrong. Please try again.")
            }
        }
    }
}
