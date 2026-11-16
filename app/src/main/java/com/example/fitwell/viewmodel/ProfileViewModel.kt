package com.example.fitwell.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitwell.data.local.UserProfileEntity
import com.example.fitwell.data.local.WorkoutSessionEntity
import com.example.fitwell.util.toLocalDate
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ProfileStats(val workouts: Int = 0, val streakDays: Int = 0, val totalMinutes: Int = 0)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.fitLife()
    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val profile: StateFlow<UserProfileEntity?> = app.profileRepo.observe().state(null)
    val sessions: StateFlow<List<WorkoutSessionEntity>> = app.workoutRepo.observeSessions().state(emptyList())

    val stats: StateFlow<ProfileStats> = sessions.map { list ->
        val done = list.filter { it.completed }
        ProfileStats(done.size, streak(done), done.sumOf { it.durationSeconds } / 60)
    }.state(ProfileStats())

    /** Consecutive days with a completed workout, ending today (or yesterday, so the streak is not lost before you train today). */
    private fun streak(done: List<WorkoutSessionEntity>): Int {
        val days = done.map { it.startTimestamp.toLocalDate() }.toSet()
        var d = LocalDate.now()
        if (d !in days) d = d.minusDays(1)
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        return n
    }

    fun saveProfile(name: String, tagline: String, category: String) {
        val current = profile.value
        viewModelScope.launch {
            app.profileRepo.save(
                (current ?: UserProfileEntity(displayName = name, createdTimestamp = System.currentTimeMillis()))
                    .copy(displayName = name.trim().ifBlank { "Athlete" }, tagline = tagline.trim(), preferredWorkoutCategory = category)
            )
            app.currentAccount?.let { app.auth.updateName(it.id, name) }
        }
    }

    val email: String? get() = app.currentAccount?.email

    /**
     * The caller navigates to the sign-in screen first; the database is closed a moment later, on the app scope
     * (this ViewModel is cleared by that navigation, so its own scope must not be used).
     */
    fun signOut() { app.appScope.launch { kotlinx.coroutines.delay(700); app.signOut() } }
    fun deleteAccount() { app.appScope.launch { kotlinx.coroutines.delay(700); app.deleteAccount() } }

    fun deleteSession(id: Long) = viewModelScope.launch { app.workoutRepo.deleteSession(id) }

    /** "Reset profile": clears workout history, meals and goals; medications and appointments are kept. */
    fun resetFitnessData(onDone: () -> Unit) {
        viewModelScope.launch {
            val db = app.db
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                db.runInTransaction(Runnable {
                    db.openHelper.writableDatabase.apply {
                        execSQL("DELETE FROM workout_sessions"); execSQL("DELETE FROM meals"); execSQL("DELETE FROM fitness_goals")
                    }
                })
            }
            db.invalidationTracker.refreshVersionsAsync()
            onDone()
        }
    }
}
