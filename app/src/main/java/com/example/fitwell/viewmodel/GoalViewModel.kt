package com.example.fitwell.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitwell.data.local.FitnessGoalEntity
import com.example.fitwell.data.local.GoalType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GoalViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = application.fitLife().goalRepo
    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val activeGoal: StateFlow<FitnessGoalEntity?> = repo.observeActive().state(null)

    /** Validates the form; returns an error message or null on success. */
    fun save(existing: FitnessGoalEntity?, type: GoalType, title: String, unit: String, start: String, current: String, target: String): String? {
        val s = start.toDoubleOrNull(); val c = current.toDoubleOrNull(); val t = target.toDoubleOrNull()
        if (title.isBlank()) return "Give your goal a name"
        if (unit.isBlank()) return "Enter a unit (for example kg, minutes or sessions)"
        if (s == null || c == null || t == null) return "Enter start, current and target as numbers"
        if (s == t) return "Target must be different from the starting value"
        viewModelScope.launch {
            repo.save(
                (existing ?: FitnessGoalEntity(
                    goalType = type, title = title, description = type.defaultDescription(), startValue = s,
                    targetValue = t, currentValue = c, unit = unit, createdTimestamp = System.currentTimeMillis()
                )).copy(goalType = type, title = title.trim(), unit = unit.trim(), startValue = s, currentValue = c, targetValue = t, isActive = true)
            )
        }
        return null
    }

    fun updateProgress(goal: FitnessGoalEntity, current: Double) = viewModelScope.launch { repo.save(goal.copy(currentValue = current)) }
    fun delete(goal: FitnessGoalEntity) = viewModelScope.launch { repo.delete(goal) }
}

fun GoalType.defaultDescription() = when (this) {
    GoalType.LOSE_WEIGHT -> "Get healthier and feel better"
    GoalType.BUILD_MUSCLE -> "Get stronger and fitter"
    GoalType.IMPROVE_ENDURANCE -> "Go further, feel stronger"
    GoalType.STAY_HEALTHY -> "Better habits, better you"
}

/** Suggested measurable unit for the form; the user can change it. */
fun GoalType.defaultUnit() = when (this) {
    GoalType.LOSE_WEIGHT -> "kg"
    GoalType.BUILD_MUSCLE -> "strength sessions"
    GoalType.IMPROVE_ENDURANCE -> "active minutes"
    GoalType.STAY_HEALTHY -> "active days"
}
