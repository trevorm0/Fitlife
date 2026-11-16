package com.example.fitwell.features.adaptive

import com.example.fitwell.data.local.AdaptiveDao
import com.example.fitwell.data.local.AdaptiveRecommendationEntity
import com.example.fitwell.data.local.DailyStepsEntity
import com.example.fitwell.data.local.FitnessGoalEntity
import com.example.fitwell.data.local.GoalType
import com.example.fitwell.data.local.MealEntity
import com.example.fitwell.data.local.PulseReadingEntity
import com.example.fitwell.data.local.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Unified adaptive wellness: combines steps, workouts, meals, pulse and goals into
 * proportionate, explainable recommendations. No guilt messaging; flexible goals;
 * works offline / without wearables.
 */
data class WellnessSnapshot(
    val recentSteps: List<DailyStepsEntity>,
    val recentWorkouts: List<WorkoutSessionEntity>,
    val todayMeals: List<MealEntity>,
    val recentPulse: List<PulseReadingEntity>,
    val activeGoal: FitnessGoalEntity?,
    val calorieTarget: Int,
    val stepTarget: Int
)

data class AdaptivePlan(
    val title: String,
    val explanation: String,
    val suggestedStepsTarget: Int?,
    val suggestedWorkoutMinutes: Int?,
    val suggestedCalorieTarget: Int?,
    val basedOnSummary: String
)

class AdaptiveWellnessEngine(
    private val dao: AdaptiveDao
) {
    fun observeRecommendations(limit: Int = 10): Flow<List<AdaptiveRecommendationEntity>> =
        dao.observeRecent(limit)

    suspend fun generate(snapshot: WellnessSnapshot): AdaptivePlan {
        val avgSteps = snapshot.recentSteps.map { it.steps }.average().takeIf { !it.isNaN() }?.toInt()
        val workoutsThisWeek = snapshot.recentWorkouts.size
        val todayCals = snapshot.todayMeals.sumOf { it.calories }
        val lastPulse = snapshot.recentPulse.firstOrNull()?.bpm

        val goal = snapshot.activeGoal
        var stepsTarget = snapshot.stepTarget
        var workoutMin: Int? = null
        var calTarget = snapshot.calorieTarget
        val reasons = mutableListOf<String>()

        // Proportionate adjustments — never punitive
        if (avgSteps != null && avgSteps < snapshot.stepTarget * 0.5) {
            stepsTarget = maxOf(4000, (snapshot.stepTarget * 0.75).toInt())
            reasons += "Recent steps were lower; a gentler target helps rebuild consistency."
        } else if (avgSteps != null && avgSteps > snapshot.stepTarget * 1.15) {
            stepsTarget = minOf(15000, snapshot.stepTarget + 500)
            reasons += "You have been exceeding your step goal; a small increase keeps progress challenging."
        }

        when (goal?.goalType) {
            GoalType.LOSE_WEIGHT -> {
                if (todayCals > calTarget * 1.1) {
                    reasons += "Today's logged meals are a bit above target — no need to overcorrect; aim closer tomorrow."
                }
                workoutMin = 25
            }
            GoalType.BUILD_MUSCLE -> {
                workoutMin = 35
                reasons += "Strength focus: prioritize 2–4 quality sessions this week over daily volume."
            }
            GoalType.IMPROVE_ENDURANCE -> {
                workoutMin = 30
                stepsTarget = maxOf(stepsTarget, 9000)
            }
            GoalType.STAY_HEALTHY, null -> {
                workoutMin = 20
            }
        }

        if (workoutsThisWeek == 0) {
            reasons += "No workouts logged this week yet — a short session counts. Resume when ready."
            workoutMin = workoutMin?.coerceAtMost(20) ?: 15
        }

        if (lastPulse != null && lastPulse > 100) {
            reasons += "A recent elevated resting pulse reading was noted; consider lighter activity and recovery if you feel off."
        }

        if (reasons.isEmpty()) {
            reasons += "Keeping current targets; they match your recent activity and goal."
        }

        val plan = AdaptivePlan(
            title = "Today's focus",
            explanation = reasons.joinToString(" "),
            suggestedStepsTarget = stepsTarget,
            suggestedWorkoutMinutes = workoutMin,
            suggestedCalorieTarget = calTarget,
            basedOnSummary = buildString {
                append("steps≈${avgSteps ?: "n/a"}, workouts=$workoutsThisWeek, ")
                append("calsToday=$todayCals, pulse=${lastPulse ?: "n/a"}")
            }
        )
        dao.insert(
            AdaptiveRecommendationEntity(
                title = plan.title,
                explanation = plan.explanation,
                suggestedStepsTarget = plan.suggestedStepsTarget,
                suggestedWorkoutMinutes = plan.suggestedWorkoutMinutes,
                suggestedCalorieTarget = plan.suggestedCalorieTarget,
                basedOnSummary = plan.basedOnSummary
            )
        )
        return plan
    }
}
