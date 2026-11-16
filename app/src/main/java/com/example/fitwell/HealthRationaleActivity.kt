package com.example.fitwell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fitwell.ui.theme.FitLifeTheme

/** Plain-language explanation of how FitLife uses Health Connect data, opened from Health Connect's permission screen. */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitLifeTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                        Text("How FitLife uses your health data", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "FitLife asks to read your steps, heart rate, sleep and active calories from Health Connect so it can show them on the Health screen.\n\n" +
                                "• Read-only: FitLife never writes or changes anything in Health Connect.\n" +
                                "• Not stored: the values are read when you open the screen and shown on your phone. FitLife does not copy them into its database or upload them.\n" +
                                "• You decide: you can allow or remove each data type at any time in Health Connect settings.\n" +
                                "• Not medical advice: these numbers are for general wellness, not diagnosis.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}
