package com.example.fitwell.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitwell.data.local.AppointmentEntity
import com.example.fitwell.data.local.AppointmentStatus
import com.example.fitwell.data.local.UserProfileEntity
import com.example.fitwell.data.local.WorkoutEntity
import com.example.fitwell.data.prefs.UserPrefs
import com.example.fitwell.data.repository.HealthSnapshot
import com.example.fitwell.util.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WeeklyProgress(
    val workouts: Int = 0, val workoutTarget: Int = 5,
    val activeMinutes: Int = 0, val minutesTarget: Int = 150,
    val caloriesLogged: Int = 0, val calorieTarget: Int = 2500
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.fitLife()
    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val profile: StateFlow<UserProfileEntity?> = app.profileRepo.observe().state(null)
    private val _health = MutableStateFlow(HealthSnapshot.empty())
    /** Real data only (Health Connect and this phone); fields are null when there is nothing measured yet. */
    val health: StateFlow<HealthSnapshot> = _health
    fun refreshHealth() { viewModelScope.launch { _health.value = app.healthRepo.load().snapshot } }
    init { refreshHealth() }
    val prefs: StateFlow<UserPrefs> = app.prefs.prefs.state(UserPrefs())

    /** A different workout each day, so "Today's Workout" is not always the same. */
    val featured: StateFlow<WorkoutEntity?> = app.workoutRepo.observeWorkouts()
        .map { list -> if (list.isEmpty()) null else list[LocalDate.now().dayOfYear % list.size] }
        .state(null)

    val weekly: StateFlow<WeeklyProgress> = combine(
        app.workoutRepo.observeSessions(), app.mealRepo.observeDay(System.currentTimeMillis()), app.prefs.prefs
    ) { sessions, meals, p ->
        val weekStart = LocalDate.now().minusDays(6).startOfDayMs()
        val week = sessions.filter { it.completed && it.startTimestamp >= weekStart }
        WeeklyProgress(
            workouts = week.size, workoutTarget = p.weeklyWorkoutTarget,
            activeMinutes = week.sumOf { it.durationSeconds } / 60, minutesTarget = p.activeMinutesTarget,
            caloriesLogged = meals.sumOf { it.calories }, calorieTarget = p.calorieTarget
        )
    }.state(WeeklyProgress())

    /** Next medication reminder, computed from saved schedules (never hardcoded). */
    val nextDose: StateFlow<DoseOccurrence?> = combine(
        app.medicationRepo.observeMedications(), app.medicationRepo.observeAllReminders(), minuteTicker()
    ) { meds, rems, now -> nextDose(meds, rems, now) }.state(null)

    val nextAppointment: StateFlow<AppointmentEntity?> = combine(
        app.appointmentRepo.observeAppointments(), minuteTicker()
    ) { list, now ->
        list.filter { it.status == AppointmentStatus.UPCOMING && (it.endDateTime ?: it.startDateTime) >= now }
            .minByOrNull { it.startDateTime }
    }.state(null)
}
