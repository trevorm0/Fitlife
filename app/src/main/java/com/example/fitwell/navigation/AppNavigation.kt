package com.example.fitwell.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.fitwell.FitLifeApp
import com.example.fitwell.ui.components.FitLifeBottomNavigation
import com.example.fitwell.ui.screens.*
import com.example.fitwell.ui.screens.features.*
import com.example.fitwell.ui.theme.Midnight
import kotlinx.coroutines.launch

private fun NavController.navigateTopLevel(target: String) = navigate(target) {
    popUpTo(Screen.HOME) { saveState = true } // never stack duplicate copies of a tab
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppNavigation(pendingRoute: String?, onRouteConsumed: () -> Unit) {
    val app = LocalContext.current.applicationContext as FitLifeApp
    val prefs by app.prefs.prefs.collectAsStateWithLifecycle(initialValue = null)
    val loaded = prefs
    if (loaded == null) {
        Box(Modifier.fillMaxSize().background(Midnight)) // system splash is still covering this
    } else {
        // Start destination is decided once, from the saved onboarding flag.
        val app2 = LocalContext.current.applicationContext as FitLifeApp
        val start = remember {
            when {
                !loaded.onboardingDone -> Screen.WELCOME
                !app2.hasSession -> Screen.AUTH       // nobody signed in: always ask, never assume a user
                else -> Screen.HOME
            }
        }
        AppShell(start, pendingRoute, onRouteConsumed)
    }
}

@Composable
private fun AppShell(start: String, pendingRoute: String?, onRouteConsumed: () -> Unit) {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as FitLifeApp
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBottomBar = destination?.hierarchy?.any { d -> bottomNavigationItems.any { it.route == d.route } } == true

    // Deep link from a reminder notification (e.g. "medication/12").
    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            // A notification tapped while signed out must not open someone's data.
            if (app.hasSession) nav.navigate(pendingRoute) { launchSingleTop = true }
            onRouteConsumed()
        }
    }

    Scaffold(
        containerColor = Midnight,
        contentWindowInsets = WindowInsets.navigationBars,
        bottomBar = {
            if (showBottomBar) {
                FitLifeBottomNavigation(
                    items = bottomNavigationItems,
                    currentRoute = destination?.hierarchy?.firstNotNullOfOrNull { d -> bottomNavigationItems.firstOrNull { it.route == d.route }?.route },
                    onSelect = { nav.navigateTopLevel(it.target) }
                )
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = start, modifier = Modifier.padding(padding)) {

            composable(Screen.WELCOME) {
                WelcomeScreen(onGetStarted = {
                    scope.launch { app.prefs.setOnboardingDone() }
                    nav.navigate(if (app.hasSession) Screen.HOME else Screen.AUTH) { popUpTo(Screen.WELCOME) { inclusive = true } }
                })
            }

            composable(Screen.AUTH) {
                AuthScreen(onAuthenticated = {
                    nav.navigate(Screen.HOME) { popUpTo(Screen.AUTH) { inclusive = true } }
                })
            }

            composable(Screen.HOME) {
                HomeScreen(
                    onOpenWorkout = { nav.navigate(Screen.workout(it)) },
                    onStartWorkout = { nav.navigate(Screen.session(it)) },
                    onOpenHealthTab = { nav.navigate(Screen.health(it)) },
                    onOpenMedication = { nav.navigate(Screen.medication(it)) },
                    onOpenAppointment = { nav.navigate(Screen.appointment(it)) },
                    onAddMedication = { nav.navigate(Screen.medicationEdit()) },
                    onAddAppointment = { nav.navigate(Screen.appointmentEdit()) },
                    onOpenGoals = { nav.navigate(Screen.GOALS) },
                    onOpenProfile = { nav.navigateTopLevel(Screen.PROFILE) }
                )
            }
            composable(Screen.WORKOUTS) { WorkoutsScreen(onOpenWorkout = { nav.navigate(Screen.workout(it)) }) }
            composable(
                Screen.HEALTH, arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })
            ) { entry ->
                HealthScreen(
                    initialTab = entry.arguments?.getInt("tab") ?: 0,
                    onOpenMedication = { nav.navigate(Screen.medication(it)) },
                    onAddMedication = { nav.navigate(Screen.medicationEdit()) },
                    onMedicationHistory = { nav.navigate(Screen.medicationHistory()) },
                    onOpenAppointment = { nav.navigate(Screen.appointment(it)) },
                    onEditAppointment = { nav.navigate(Screen.appointmentEdit(it)) },
                    onAddAppointment = { nav.navigate(Screen.appointmentEdit()) }
                )
            }
            composable(Screen.NUTRITION) { NutritionScreen() }
            composable(Screen.PROFILE) {
                ProfileScreen(
                    onOpenGoals = { nav.navigate(Screen.GOALS) }, onOpenHistory = { nav.navigate(Screen.HISTORY) },
                    onOpenSettings = { nav.navigate(Screen.SETTINGS) }, onOpenHelp = { nav.navigate(Screen.HELP) },
                    onOpenAbout = { nav.navigate(Screen.ABOUT) },
                    onOpenPulse = { nav.navigate(Screen.PULSE) },
                    onOpenSteps = { nav.navigate(Screen.STEPS) },
                    onOpenCoach = { nav.navigate(Screen.COACH) },
                    onOpenLeagues = { nav.navigate(Screen.LEAGUES) },
                    onOpenMealPhoto = { nav.navigate(Screen.MEAL_PHOTO) },
                    onOpenAdaptive = { nav.navigate(Screen.ADAPTIVE) },
                    onSignedOut = {
                        // Leave every signed-in screen first so their ViewModels stop reading the database, then close it.
                        nav.navigate(Screen.AUTH) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
                    }
                )
            }

            // ----- fitness flow -----
            val idArg = listOf(navArgument("id") { type = NavType.LongType })
            composable(Screen.WORKOUT_DETAILS, idArg) { e ->
                val id = e.arguments?.getLong("id") ?: 0L
                WorkoutDetailsScreen(id, onBack = { nav.popBackStack() }, onStart = { nav.navigate(Screen.session(id)) })
            }
            composable(Screen.SESSION, idArg) { e ->
                ActiveWorkoutScreen(e.arguments?.getLong("id") ?: 0L, onExit = { nav.popBackStack() })
            }
            composable(Screen.HISTORY) { HistoryScreen(onBack = { nav.popBackStack() }) }
            composable(Screen.GOALS) { GoalsScreen(onBack = { nav.popBackStack() }) }
            composable(Screen.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable(Screen.HELP) {
                InfoScreen("Help & Support", helpText, onBack = { nav.popBackStack() })
            }
            composable(Screen.ABOUT) {
                InfoScreen("About FitLife", aboutText, onBack = { nav.popBackStack() })
            }

            // ----- medication -----
            composable(Screen.MEDICATION_DETAILS, idArg) { e ->
                val id = e.arguments?.getLong("id") ?: 0L
                MedicationDetailsScreen(
                    id, onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Screen.medicationEdit(id)) }, onHistory = { nav.navigate(Screen.medicationHistory(id)) }
                )
            }
            composable(Screen.MEDICATION_EDIT, listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L })) { e ->
                AddMedicationScreen(e.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
            composable(Screen.MEDICATION_HISTORY, listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { e ->
                MedicationHistoryScreen(e.arguments?.getLong("id") ?: -1L, onBack = { nav.popBackStack() })
            }

            // ----- appointments -----
            composable(Screen.APPOINTMENT_DETAILS, idArg) { e ->
                val id = e.arguments?.getLong("id") ?: 0L
                AppointmentDetailsScreen(id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Screen.appointmentEdit(id)) })
            }
            composable(Screen.APPOINTMENT_EDIT, listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L })) { e ->
                AddAppointmentScreen(e.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }

            // ----- advanced features -----
            composable(Screen.PULSE) { PulseScreen() }
            composable(Screen.STEPS) { StepsScreen() }
            composable(Screen.COACH) { CoachScreen() }
            composable(Screen.LEAGUES) { LeaguesScreen() }
            composable(Screen.MEAL_PHOTO) { MealPhotoScreen() }
            composable(Screen.ADAPTIVE) { AdaptiveDashboardScreen() }
        }
    }
}
