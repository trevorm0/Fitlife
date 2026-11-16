package com.example.fitwell.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fitwell.ui.components.FitLifeImage
import com.example.fitwell.ui.components.FitLifePrimaryButton
import com.example.fitwell.ui.theme.*

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Midnight)) {
        // Add a photo named welcome_hero.jpg to res/drawable; a gradient placeholder shows until then.
        FitLifeImage("welcome_hero", null, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Midnight.copy(alpha = 0.75f), Midnight.copy(alpha = 0.25f), Midnight.copy(alpha = 0.9f)))
            )
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Box(Modifier.size(72.dp).background(FitGreen, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = Midnight, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = TextPrimary)) { append("Fit") }
                    withStyle(SpanStyle(color = FitGreen)) { append("Life") }
                },
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp)
            )
            Spacer(Modifier.height(8.dp))
            Text("Healthier You,\nStronger Tomorrow", style = MaterialTheme.typography.titleMedium, color = TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            FitLifePrimaryButton("Get Started", onGetStarted, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Filled.ArrowForward)
        }
    }
}

