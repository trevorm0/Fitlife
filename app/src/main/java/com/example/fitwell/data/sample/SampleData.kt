package com.example.fitwell.data.sample

import com.example.fitwell.data.local.*
import kotlinx.coroutines.flow.firstOrNull

/** The built-in workout library (the same for everyone). No fake user data or fake meals are created here. */
object SampleData {

    private data class Ex(val name: String, val how: String, val seconds: Int = 45, val reps: Int? = null)

    private class W(
        val name: String, val description: String, val category: WorkoutCategory, val minutes: Int,
        val difficulty: Difficulty, val target: String, val image: String, val exercises: List<Ex>
    )

    private val workouts = listOf(
        W(
            "Full Body Blast", "A high-energy full body workout designed to build strength, improve endurance and burn calories. No equipment needed!",
            WorkoutCategory.STRENGTH, 30, Difficulty.MODERATE, "Full Body", "workout_full_body",
            listOf(
                Ex("Jumping Jacks", "Jump feet apart while raising your arms overhead, then return."),
                Ex("Push Ups", "Keep your body in a straight line and lower your chest toward the floor.", reps = 12),
                Ex("Squats", "Feet shoulder-width apart, sit back and down, then stand tall."),
                Ex("Mountain Climbers", "From a plank, drive your knees toward your chest alternately."),
                Ex("Plank", "Hold a straight line from head to heels, core tight."),
                Ex("Bicycle Crunches", "Lie on your back and bring opposite elbow to knee in a pedalling motion.")
            )
        ),
        W(
            "HIIT Fat Burn", "Short bursts of intense effort with brief recoveries to raise your heart rate.",
            WorkoutCategory.CARDIO, 20, Difficulty.HIGH, "Cardio", "workout_hiit",
            listOf(
                Ex("High Knees", "Run in place driving your knees to hip height."),
                Ex("Burpees", "Squat, kick back to plank, return and jump."),
                Ex("Skater Hops", "Leap side to side landing softly on one foot."),
                Ex("Fast Feet", "Quick, light steps on the spot.")
            )
        ),
        W(
            "Core & Abs", "Targeted work for your abdominals and lower back.",
            WorkoutCategory.CORE, 25, Difficulty.MODERATE, "Core", "workout_core",
            listOf(
                Ex("Crunches", "Lift your shoulders off the floor, exhale at the top."),
                Ex("Leg Raises", "Raise straight legs to vertical, lower slowly."),
                Ex("Russian Twists", "Sit leaning back and rotate your torso side to side."),
                Ex("Plank", "Hold a straight line from head to heels."),
                Ex("Dead Bug", "Lower opposite arm and leg while keeping your back flat.")
            )
        ),
        W(
            "Lower Body Strength", "Build strong legs and glutes with bodyweight movements.",
            WorkoutCategory.STRENGTH, 35, Difficulty.HIGH, "Legs & Glutes", "workout_lower_body",
            listOf(
                Ex("Squats", "Sit back and down, keep your chest tall."),
                Ex("Lunges", "Step forward and lower until both knees reach about 90 degrees."),
                Ex("Glute Bridges", "Drive your hips up, squeeze at the top."),
                Ex("Wall Sit", "Back against the wall, thighs parallel to the floor."),
                Ex("Calf Raises", "Rise onto your toes and lower slowly.")
            )
        ),
        W(
            "Yoga & Flexibility", "A gentle flow to improve mobility and relax.",
            WorkoutCategory.FLEXIBILITY, 20, Difficulty.LOW, "Mobility", "workout_yoga",
            listOf(
                Ex("Cat-Cow", "Alternate arching and rounding your spine on hands and knees.", 60),
                Ex("Downward Dog", "Hips high, heels reaching toward the floor.", 60),
                Ex("Low Lunge", "Sink your hips forward and lift your chest.", 60),
                Ex("Child's Pose", "Sit back on your heels and reach forward.", 60)
            )
        ),
        W(
            "Endurance Intervals", "Steady-state intervals to build stamina at a comfortable pace.",
            WorkoutCategory.ENDURANCE, 40, Difficulty.MODERATE, "Cardio Endurance", "workout_endurance",
            listOf(
                Ex("March in Place", "Warm up with a brisk march.", 60),
                Ex("Step-Ups", "Step up onto a stable surface and down again.", 60),
                Ex("Shadow Boxing", "Light punches with a bouncing stance.", 60),
                Ex("Cool-Down Walk", "Slow your pace and breathe deeply.", 60)
            )
        )
    )

    suspend fun seedWorkoutsIfEmpty(db: FitLifeDatabase) {
        val dao = db.workoutDao()
        if (dao.countWorkouts() > 0) return
        workouts.forEach { w ->
            val id = dao.insertWorkout(
                WorkoutEntity(
                    name = w.name, description = w.description, category = w.category,
                    durationMinutes = w.minutes, difficulty = w.difficulty, targetArea = w.target, imageReference = w.image
                )
            )
            dao.insertExercises(w.exercises.mapIndexed { i, e ->
                ExerciseEntity(
                    workoutId = id, name = e.name, instructions = e.how,
                    durationSeconds = if (e.reps != null) 0 else e.seconds, targetRepetitions = e.reps, orderIndex = i
                )
            })
        }
    }

    /** Creates the signed-in person's own profile (name from registration). Never a pre-made user. */
    suspend fun ensureProfile(db: FitLifeDatabase, displayName: String) {
        val dao = db.profileDao()
        if (dao.observe().firstOrNull() == null) {
            dao.upsert(UserProfileEntity(displayName = displayName, createdTimestamp = System.currentTimeMillis()))
        }
    }
}
