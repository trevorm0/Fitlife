package com.example.fitwell

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.fitwell.navigation.AppNavigation
import com.example.fitwell.reminders.NotificationHelper
import com.example.fitwell.reminders.ReminderCoordinator
import com.example.fitwell.ui.theme.FitLifeTheme
import com.example.fitwell.ui.theme.Midnight
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // system splash (Android splash-screen API); the designed welcome screen is separate
        super.onCreate(savedInstanceState)
        val dark = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(dark), navigationBarStyle = SystemBarStyle.dark(dark))
        pendingRoute = intent?.getStringExtra(NotificationHelper.EXTRA_ROUTE)
        setContent {
            FitLifeTheme {
                Surface(Modifier.fillMaxSize(), color = Midnight) {
                    AppNavigation(pendingRoute = pendingRoute, onRouteConsumed = { pendingRoute = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(NotificationHelper.EXTRA_ROUTE)?.let { pendingRoute = it }
    }

    override fun onStart() {
        super.onStart()
        // Reconcile alarms with the database (covers DST/time-zone changes missed while the app was stopped).
        lifecycleScope.launch { ReminderCoordinator.rescheduleAll(applicationContext) }
    }
}
