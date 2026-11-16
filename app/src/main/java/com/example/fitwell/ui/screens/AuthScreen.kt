package com.example.fitwell.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitwell.ui.components.FitLifePrimaryButton
import com.example.fitwell.ui.components.FitLifeTextField
import com.example.fitwell.ui.components.fitLifeFieldColors
import com.example.fitwell.ui.theme.*
import com.example.fitwell.viewmodel.AuthViewModel
import androidx.compose.runtime.saveable.rememberSaveable

/** Sign in / create account. Shown whenever nobody is signed in; there is no built-in or demo user. */
@Composable
fun AuthScreen(onAuthenticated: () -> Unit, vm: AuthViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    fun submit() { focus.clearFocus(); vm.submit(register, name, email, password, confirm, onAuthenticated) }

    Column(
        Modifier.fillMaxSize().background(Midnight).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(64.dp).background(FitGreen, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.DirectionsRun, null, tint = Midnight, modifier = Modifier.size(38.dp))
        }
        Text(if (register) "Create your account" else "Welcome back", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Text(
            if (register) "Your data is stored privately on this phone, under your account."
            else "Sign in to see your workouts, meals and reminders.",
            style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))

        if (register) FitLifeTextField(name, { name = it; vm.clearError() }, "Your name",
            keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words, imeAction = ImeAction.Next))
        FitLifeTextField(email, { email = it; vm.clearError() }, "Email",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
        PasswordField(password, { password = it; vm.clearError() }, if (register) "Password (8+ characters)" else "Password",
            showPassword, { showPassword = !showPassword }, if (register) ImeAction.Next else ImeAction.Done, ::submit)
        if (register) PasswordField(confirm, { confirm = it; vm.clearError() }, "Confirm password",
            showPassword, { showPassword = !showPassword }, ImeAction.Done, ::submit)

        state.error?.let { Text(it, color = HeartCoral, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }

        FitLifePrimaryButton(if (register) "Create account" else "Sign in", ::submit, Modifier.fillMaxWidth(), enabled = !state.busy)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = FitGreen, trackColor = DividerBlue)

        TextButton(onClick = { register = !register; vm.clearError() }) {
            Text(if (register) "Already have an account? Sign in" else "New here? Create an account", color = FitGreen)
        }
        Text(
            "Accounts are kept on this device only, so there is no “forgot password”. If you forget it, you will need to create a new account.",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PasswordField(
    value: String, onChange: (String) -> Unit, label: String, visible: Boolean, toggle: () -> Unit,
    ime: ImeAction, onDone: () -> Unit
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ime),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = toggle) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (visible) "Hide password" else "Show password", tint = TextSecondary)
            }
        },
        shape = FitLifeDimens.fieldShape, colors = fitLifeFieldColors()
    )
}
