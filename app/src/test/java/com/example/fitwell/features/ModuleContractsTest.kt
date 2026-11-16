package com.example.fitwell.features

import com.example.fitwell.data.local.LeagueScoreMode
import com.example.fitwell.features.adaptive.AdaptiveWellnessEngine
import com.example.fitwell.features.adaptive.WellnessSnapshot
import com.example.fitwell.features.coach.CoachExercises
import com.example.fitwell.features.coach.ExerciseTracker
import com.example.fitwell.features.coach.Pose
import com.example.fitwell.features.coach.Pt
import com.example.fitwell.features.leagues.LeagueRepository
import com.example.fitwell.features.mealanalysis.FoodGuess
import com.example.fitwell.features.mealanalysis.FoodMapper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lightweight contract checks that do not require Room or Android instrumentation.
 * Wire real in-memory DAOs in CI for fuller coverage.
 */
class ModuleContractsTest {

    @Test
    fun coachSupportsCoreExercises() {
        val keys = CoachExercises.all.map { it.key }
        assertTrue(keys.containsAll(listOf("squat", "pushup", "plank", "lunge")))
    }

    /** Side-on standing pose with the given knee angle (180 = straight leg) and torso lean in degrees. */
    private fun sidePose(kneeDeg: Double, leanDeg: Double = 0.0): Pose {
        val ankle = Pt(0.5f, 0.9f, 0.95f)
        val knee = Pt(0.5f, 0.65f, 0.95f)
        val rad = Math.toRadians(kneeDeg)
        val hip = Pt((0.5 + 0.25 * Math.sin(rad)).toFloat(), (0.65 + 0.25 * Math.cos(rad)).toFloat(), 0.95f)
        val lean = Math.toRadians(leanDeg)
        val shoulder = Pt((hip.x + 0.3 * Math.sin(lean)).toFloat(), (hip.y - 0.3 * Math.cos(lean)).toFloat(), 0.95f)
        val pts = HashMap<String, Pt>()
        listOf("L_", "R_").forEach { s -> pts[s + "ANKLE"] = ankle; pts[s + "KNEE"] = knee; pts[s + "HIP"] = hip; pts[s + "SHOULDER"] = shoulder }
        return Pose(pts, 0.75f)
    }

    private fun run(t: ExerciseTracker, angles: List<Int>, lean: Double = 0.0) = angles.forEach { t.update(sidePose(it.toDouble(), lean)) }

    @Test
    fun squatTrackerCountsFullRepsOnly() {
        var now = 0L
        val t = ExerciseTracker("squat") { now += 100; now }
        val fullRep = listOf(175, 170, 150, 120, 95, 95, 120, 150, 170, 175, 175)
        repeat(3) { run(t, fullRep) }
        assertEquals(3, t.reps)
        // A shallow dip that never gets below 125 degrees must not count.
        run(t, listOf(175, 150, 135, 135, 150, 170, 175, 175, 175))
        assertEquals(3, t.reps)
        assertNotNull(t.formScore())
    }

    @Test
    fun squatTrackerFlagsLeaningTorso() {
        var now = 0L
        val t = ExerciseTracker("squat") { now += 100; now }
        run(t, listOf(175, 150, 120, 95, 95, 120, 150, 175, 175, 175), lean = 70.0)
        assertEquals(1, t.reps)
        assertTrue(t.formScore()!! < 1f)
    }

    @Test
    fun trackerSaysSoWhenItCannotSeeYou() {
        val t = ExerciseTracker("squat")
        val u = t.update(Pose(emptyMap(), 0.75f))
        assertFalse(u.tracking)
        assertEquals(0, u.reps)
    }

    @Test
    fun angleMathIsCorrect() {
        val a = ExerciseTracker.angle(Pt(0.5f, 0.2f, 1f), Pt(0.5f, 0.5f, 1f), Pt(0.5f + 0.3f / 1f, 0.5f, 1f), 1f)
        assertEquals(90f, a, 0.5f)
    }

    @Test
    fun mealMapperMapsLabelsToCatalogFoods() {
        val r = FoodMapper.map(listOf(FoodGuess("Food", 0.95f), FoodGuess("Rice", 0.8f), FoodGuess("Fish", 0.6f), FoodGuess("Rice", 0.5f)))
        assertEquals(listOf("White rice", "Tilapia (fried)"), r.items.map { it.name })
        assertTrue(r.items.all { it.confidence <= 0.9f })
        assertEquals(r.items.sumOf { it.calories }, r.totalCalories)
        assertTrue(r.uncertaintyNotes.isNotBlank())
    }

    @Test
    fun mealMapperDoesNotInventFoodWhenNothingMatches() {
        val none = FoodMapper.map(listOf(FoodGuess("Sky", 0.9f), FoodGuess("Cloud", 0.8f)))
        assertTrue(none.items.isEmpty()); assertEquals(0, none.totalCalories)
        val genericOnly = FoodMapper.map(listOf(FoodGuess("Dish", 0.9f)))
        assertTrue(genericOnly.items.isEmpty())
        assertTrue(genericOnly.uncertaintyNotes.contains("could not tell"))
    }

    @Test
    fun adaptiveEngineProducesExplanation() = runBlocking {
        val engine = AdaptiveWellnessEngine(FakeAdaptiveDao())
        val plan = engine.generate(
            WellnessSnapshot(
                recentSteps = emptyList(),
                recentWorkouts = emptyList(),
                todayMeals = emptyList(),
                recentPulse = emptyList(),
                activeGoal = null,
                calorieTarget = 2000,
                stepTarget = 8000
            )
        )
        assertTrue(plan.explanation.isNotBlank())
        assertTrue(plan.suggestedStepsTarget != null || plan.suggestedWorkoutMinutes != null)
    }

    @Test
    fun leagueScoreModesExist() {
        assertTrue(LeagueScoreMode.entries.size >= 4)
    }
}

// Minimal fakes — replace with Room in-memory in full test suite
private class FakeCoachDao : com.example.fitwell.data.local.CoachDao {
    override fun observeSessions() = kotlinx.coroutines.flow.flowOf(emptyList<com.example.fitwell.data.local.CoachSessionEntity>())
    override suspend fun insert(s: com.example.fitwell.data.local.CoachSessionEntity) = 1L
    override suspend fun finish(id: Long, ended: Long, reps: Int, score: Float?, summary: String, stopped: Boolean) {}
}

private class FakeMealPhotoDao : com.example.fitwell.data.local.MealPhotoDao {
    override fun observeAll() = kotlinx.coroutines.flow.flowOf(emptyList<com.example.fitwell.data.local.MealPhotoAnalysisEntity>())
    override suspend fun insert(a: com.example.fitwell.data.local.MealPhotoAnalysisEntity) = 1L
    override suspend fun upsert(a: com.example.fitwell.data.local.MealPhotoAnalysisEntity) {}
    override suspend fun delete(id: Long) {}
}

private class FakeAdaptiveDao : com.example.fitwell.data.local.AdaptiveDao {
    override fun observeRecent(limit: Int) = kotlinx.coroutines.flow.flowOf(emptyList<com.example.fitwell.data.local.AdaptiveRecommendationEntity>())
    override suspend fun insert(r: com.example.fitwell.data.local.AdaptiveRecommendationEntity) = 1L
}
