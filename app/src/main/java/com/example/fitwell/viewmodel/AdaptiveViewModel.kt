package com.example.fitwell.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitwell.FitLifeApp
import com.example.fitwell.features.adaptive.AdaptivePlan
import com.example.fitwell.features.adaptive.WellnessSnapshot
import com.example.fitwell.util.startOfDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdaptiveViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = (app as FitLifeApp).adaptiveEngine
    private val stepsRepo = (app as FitLifeApp).stepsRepo
    private val workoutRepo = (app as FitLifeApp).workoutRepo
    private val mealRepo = (app as FitLifeApp).mealRepo
    private val pulseRepo = (app as FitLifeApp).pulseRepo
    private val goalRepo = (app as FitLifeApp).goalRepo
    private val prefs = (app as FitLifeApp).prefs

    val recommendations = engine.observeRecommendations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _plan = MutableStateFlow<AdaptivePlan?>(null)
    val plan = _plan.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val day = startOfDay(System.currentTimeMillis())
            val weekStart = day - 6 * 24 * 60 * 60 * 1000L
            val steps = stepsRepo.observeWeek(weekStart).first()
            val workouts = workoutRepo.observeSessions().first().filter { it.startTimestamp >= weekStart }
            val meals = mealRepo.observeDay(day).first()
            val pulse = pulseRepo.observeRecent(5).first()
            val goal = goalRepo.observeActive().first()
            val p = prefs.prefs.first()
            val snapshot = WellnessSnapshot(
                recentSteps = steps,
                recentWorkouts = workouts,
                todayMeals = meals,
                recentPulse = pulse,
                activeGoal = goal,
                calorieTarget = p.calorieTarget,
                stepTarget = steps.lastOrNull()?.targetSteps ?: 8000
            )
            _plan.value = engine.generate(snapshot)
        }
    }
}