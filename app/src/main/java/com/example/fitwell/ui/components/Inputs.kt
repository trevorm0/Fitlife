package com.example.fitwell.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.fitwell.ui.theme.*
import com.example.fitwell.util.fromPickerMillis
import com.example.fitwell.util.toPickerMillis
import java.time.LocalDate

@Composable
fun FitLifePrimaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector? = null, enabled: Boolean = true
) {
    Button(
        onClick = onClick, modifier = modifier.heightIn(min = 52.dp), enabled = enabled,
        shape = FitLifeDimens.buttonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = FitGreen, contentColor = Midnight,
            disabledContainerColor = DividerBlue, disabledContentColor = TextSecondary
        )
    ) {
        if (icon != null) { Icon(icon, contentDescription = null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun FitLifeSecondaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector? = null, enabled: Boolean = true, color: Color = FitGreen
) {
    OutlinedButton(
        onClick = onClick, modifier = modifier.heightIn(min = 52.dp), enabled = enabled,
        shape = FitLifeDimens.buttonShape, border = BorderStroke(1.dp, if (enabled) color else DividerBlue),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color, disabledContentColor = TextSecondary)
    ) {
        if (icon != null) { Icon(icon, contentDescription = null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun CategoryFilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) FitGreen else CardNavy)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, style = MaterialTheme.typography.labelMedium,
            color = if (selected) Midnight else TextPrimary
        )
    }
}

@Composable
fun fitLifeFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CardNavy, unfocusedContainerColor = CardNavy, disabledContainerColor = CardNavy,
    focusedBorderColor = FitGreen, unfocusedBorderColor = DividerBlue,
    focusedLabelColor = FitGreen, unfocusedLabelColor = TextSecondary,
    cursorColor = FitGreen, errorBorderColor = HeartCoral, errorLabelColor = HeartCoral,
    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
)

@Composable
fun FitLifeTextField(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    error: String? = null, singleLine: Boolean = true, minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, placeholder: String? = null
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth(),
        label = { Text(label) }, placeholder = placeholder?.let { { Text(it, color = TextSecondary) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = singleLine, minLines = minLines, keyboardOptions = keyboardOptions,
        shape = FitLifeDimens.fieldShape, colors = fitLifeFieldColors()
    )
}

@Composable
fun SearchBar(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Search…") {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = TextSecondary) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) {
                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextSecondary)
            }
        },
        singleLine = true, shape = FitLifeDimens.searchShape, colors = fitLifeFieldColors()
    )
}

/** Read-only field that opens a picker when tapped (used for dates and times). */
@Composable
fun PickerField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier, error: String? = null, icon: ImageVector? = null) {
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
            isError = error != null, supportingText = error?.let { { Text(it) } },
            trailingIcon = icon?.let { { Icon(it, contentDescription = null, tint = FitCyan) } },
            modifier = Modifier.fillMaxWidth(), shape = FitLifeDimens.fieldShape, colors = fitLifeFieldColors()
        )
        // transparent overlay so the whole field is tappable, even though it is read-only
        Box(Modifier.matchParentSize().padding(bottom = if (error != null) 22.dp else 0.dp).clickable(role = Role.Button, onClickLabel = "Change $label", onClick = onClick))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitLifeDatePickerDialog(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit, minDate: LocalDate? = null) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toPickerMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                minDate == null || !utcTimeMillis.fromPickerMillis().isBefore(minDate)
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPicked(it.fromPickerMillis()) }; onDismiss() }) { Text("OK", color = FitGreen) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitLifeTimePickerDialog(initialHour: Int, initialMinute: Int, onDismiss: () -> Unit, onPicked: (Int, Int) -> Unit) {
    val ctx = LocalContext.current
    val state = rememberTimePickerState(initialHour, initialMinute, is24Hour = DateFormat.is24HourFormat(ctx))
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardElevated,
        confirmButton = { TextButton(onClick = { onPicked(state.hour, state.minute); onDismiss() }) { Text("OK", color = FitGreen) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } }
    )
}
