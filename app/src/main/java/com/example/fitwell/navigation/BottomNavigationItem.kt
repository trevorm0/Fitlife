package com.example.fitwell.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.ui.graphics.vector.ImageVector

/** [route] is the destination pattern (used to detect selection); [target] is what we navigate to. */
data class BottomNavigationItem(val route: String, val label: String, val icon: ImageVector, val target: String = route)

val bottomNavigationItems = listOf(
    BottomNavigationItem(Screen.HOME, "Home", Icons.Default.Home),
    BottomNavigationItem(Screen.WORKOUTS, "Workouts", Icons.Default.FitnessCenter),
    BottomNavigationItem(Screen.HEALTH, "Health", Icons.Default.Favorite, target = Screen.health(0)),
    BottomNavigationItem(Screen.NUTRITION, "Nutrition", Icons.Default.Restaurant),
    BottomNavigationItem(Screen.PROFILE, "Profile", Icons.Default.Person)
)
