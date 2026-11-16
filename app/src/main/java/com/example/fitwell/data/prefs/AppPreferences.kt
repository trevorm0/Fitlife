package com.example.fitwell.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "fitlife_prefs")

data class UserPrefs(
    val onboardingDone: Boolean = false,
    /** Privacy-conscious by default: lock screen shows a generic notification. */
    val hideLockScreenDetails: Boolean = true,
    /** Cloud backup is OFF until the user explicitly opts in. */
    val cloudBackupEnabled: Boolean = false,
    val calorieTarget: Int = 2500,
    val proteinTarget: Int = 150,
    val carbTarget: Int = 300,
    val fatTarget: Int = 80,
    val weeklyWorkoutTarget: Int = 5,
    val activeMinutesTarget: Int = 150,
    val sampleDataSeeded: Boolean = false
)

/** Lightweight preferences (DataStore). Structured data lives in Room. */
class AppPreferences(private val context: Context) {
    private object K {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val hideLock = booleanPreferencesKey("hide_lock_screen_details")
        val cloud = booleanPreferencesKey("cloud_backup_enabled")
        val kcal = intPreferencesKey("calorie_target")
        val protein = intPreferencesKey("protein_target")
        val carbs = intPreferencesKey("carb_target")
        val fat = intPreferencesKey("fat_target")
        val weeklyWorkouts = intPreferencesKey("weekly_workout_target")
        val activeMinutes = intPreferencesKey("active_minutes_target")
        val seeded = booleanPreferencesKey("sample_data_seeded")
    }

    val prefs: Flow<UserPrefs> = context.dataStore.data.map { p ->
        val d = UserPrefs()
        UserPrefs(
            onboardingDone = p[K.onboarding] ?: d.onboardingDone,
            hideLockScreenDetails = p[K.hideLock] ?: d.hideLockScreenDetails,
            cloudBackupEnabled = p[K.cloud] ?: d.cloudBackupEnabled,
            calorieTarget = p[K.kcal] ?: d.calorieTarget,
            proteinTarget = p[K.protein] ?: d.proteinTarget,
            carbTarget = p[K.carbs] ?: d.carbTarget,
            fatTarget = p[K.fat] ?: d.fatTarget,
            weeklyWorkoutTarget = p[K.weeklyWorkouts] ?: d.weeklyWorkoutTarget,
            activeMinutesTarget = p[K.activeMinutes] ?: d.activeMinutesTarget,
            sampleDataSeeded = p[K.seeded] ?: d.sampleDataSeeded
        )
    }

    suspend fun setOnboardingDone() = edit { it[K.onboarding] = true }
    suspend fun setHideLockScreenDetails(v: Boolean) = edit { it[K.hideLock] = v }
    suspend fun setCloudBackupEnabled(v: Boolean) = edit { it[K.cloud] = v }
    suspend fun setCalorieTarget(v: Int) = edit { it[K.kcal] = v }
    suspend fun setSampleDataSeeded() = edit { it[K.seeded] = true }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}
