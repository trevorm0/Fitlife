package com.example.fitwell.data.local

import android.content.ContentValues
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.fitwell.util.ALL_DAYS
import com.example.fitwell.util.DAY_NAMES_LEGACY

@Database(
    entities = [
        WorkoutEntity::class, ExerciseEntity::class, WorkoutSessionEntity::class,
        MealEntity::class, FitnessGoalEntity::class, UserProfileEntity::class,
        MedicationEntity::class, MedicationReminderEntity::class, MedicationIntakeEntity::class,
        AppointmentEntity::class, AppointmentReminderEntity::class,
        // Feature modules (v3)
        PulseReadingEntity::class, DailyStepsEntity::class, CoachSessionEntity::class,
        LeagueEntity::class, LeagueMemberEntity::class, LeagueChallengeEntity::class, LeagueScoreEntity::class,
        MealPhotoAnalysisEntity::class, AdaptiveRecommendationEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class FitLifeDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun mealDao(): MealDao
    abstract fun goalDao(): GoalDao
    abstract fun profileDao(): ProfileDao
    abstract fun medicationDao(): MedicationDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun pulseDao(): PulseDao
    abstract fun stepsDao(): StepsDao
    abstract fun coachDao(): CoachDao
    abstract fun leagueDao(): LeagueDao
    abstract fun mealPhotoDao(): MealPhotoDao
    abstract fun adaptiveDao(): AdaptiveDao

    companion object {
        const val NAME = "fitwell.db" // same file name as the FitWell prototype so its data is migrated

        /** One database file per account, so signing in as someone else never shows the previous person's data. */
        fun nameFor(accountId: String) = "fitlife_$accountId.db"

        fun create(context: Context, accountId: String): FitLifeDatabase =
            Room.databaseBuilder(context, FitLifeDatabase::class.java, nameFor(accountId))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}

/** v2 -> v3: pulse, steps, coach, leagues, meal photo analysis, adaptive recommendations. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `pulse_readings` (`readingId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bpm` INTEGER NOT NULL, `measuredAt` INTEGER NOT NULL, `source` TEXT NOT NULL, `confidence` REAL, `note` TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pulse_readings_measuredAt` ON `pulse_readings` (`measuredAt`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `daily_steps` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dayStartMs` INTEGER NOT NULL, `steps` INTEGER NOT NULL, `targetSteps` INTEGER NOT NULL, `source` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_steps_dayStartMs` ON `daily_steps` (`dayStartMs`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `coach_sessions` (`sessionId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `exerciseKey` TEXT NOT NULL, `exerciseName` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `repsDetected` INTEGER NOT NULL, `formScore` REAL, `feedbackSummary` TEXT NOT NULL, `stoppedByUser` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_coach_sessions_startedAt` ON `coach_sessions` (`startedAt`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `leagues` (`leagueId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `inviteCode` TEXT NOT NULL, `createdByUserId` TEXT NOT NULL, `scoreMode` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `isActive` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `league_members` (`memberId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `leagueId` INTEGER NOT NULL, `memberUserId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `joinedAt` INTEGER NOT NULL, `shareSteps` INTEGER NOT NULL, `shareWorkouts` INTEGER NOT NULL, `sharePulse` INTEGER NOT NULL, `isBlocked` INTEGER NOT NULL, FOREIGN KEY(`leagueId`) REFERENCES `leagues`(`leagueId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_league_members_leagueId` ON `league_members` (`leagueId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_league_members_leagueId_memberUserId` ON `league_members` (`leagueId`, `memberUserId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `league_challenges` (`challengeId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `leagueId` INTEGER NOT NULL, `title` TEXT NOT NULL, `weekStartMs` INTEGER NOT NULL, `weekEndMs` INTEGER NOT NULL, `scoreMode` TEXT NOT NULL, FOREIGN KEY(`leagueId`) REFERENCES `leagues`(`leagueId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_league_challenges_leagueId` ON `league_challenges` (`leagueId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `league_scores` (`scoreId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `challengeId` INTEGER NOT NULL, `memberUserId` TEXT NOT NULL, `points` INTEGER NOT NULL, `detail` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`challengeId`) REFERENCES `league_challenges`(`challengeId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_league_scores_challengeId` ON `league_scores` (`challengeId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_league_scores_challengeId_memberUserId` ON `league_scores` (`challengeId`, `memberUserId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `meal_photo_analyses` (`analysisId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `createdAt` INTEGER NOT NULL, `localImageRef` TEXT, `recognizedItemsJson` TEXT NOT NULL, `estimatedCalories` INTEGER NOT NULL, `estimatedProteinG` INTEGER NOT NULL, `estimatedCarbsG` INTEGER NOT NULL, `estimatedFatG` INTEGER NOT NULL, `overallConfidence` REAL NOT NULL, `userCorrected` INTEGER NOT NULL, `linkedMealId` INTEGER, `notes` TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_photo_analyses_createdAt` ON `meal_photo_analyses` (`createdAt`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `adaptive_recommendations` (`recId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `createdAt` INTEGER NOT NULL, `title` TEXT NOT NULL, `explanation` TEXT NOT NULL, `suggestedStepsTarget` INTEGER, `suggestedWorkoutMinutes` INTEGER, `suggestedCalorieTarget` INTEGER, `basedOnSummary` TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_adaptive_recommendations_createdAt` ON `adaptive_recommendations` (`createdAt`)")
    }
}

/**
 * v1 (FitWell prototype: String ids, comma-separated times, sync columns) -> v2 (FitLife schema).
 * Medications (with their reminder times), appointments and workout sessions are converted.
 * Old medication reminder logs and cloud sync flags are dropped (the logs were only created when an alarm fired).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()

        // ---- create the new tables (SQL must match Room's generated schema) ----
        db.execSQL("CREATE TABLE IF NOT EXISTS `workouts` (`workoutId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `category` TEXT NOT NULL, `durationMinutes` INTEGER NOT NULL, `difficulty` TEXT NOT NULL, `targetArea` TEXT NOT NULL, `imageReference` TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `exercises` (`exerciseId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workoutId` INTEGER NOT NULL, `name` TEXT NOT NULL, `instructions` TEXT NOT NULL, `durationSeconds` INTEGER NOT NULL, `targetRepetitions` INTEGER, `orderIndex` INTEGER NOT NULL, `imageReference` TEXT NOT NULL, FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`workoutId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_workoutId` ON `exercises` (`workoutId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `meals` (`mealId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `mealCategory` TEXT NOT NULL, `calories` INTEGER NOT NULL, `proteinGrams` INTEGER NOT NULL, `carbohydrateGrams` INTEGER NOT NULL, `fatGrams` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `description` TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_timestamp` ON `meals` (`timestamp`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `fitness_goals` (`goalId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goalType` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `startValue` REAL NOT NULL, `targetValue` REAL NOT NULL, `currentValue` REAL NOT NULL, `unit` TEXT NOT NULL, `createdTimestamp` INTEGER NOT NULL, `isActive` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_profile` (`userId` INTEGER NOT NULL, `displayName` TEXT NOT NULL, `profileImageReference` TEXT, `preferredWorkoutCategory` TEXT NOT NULL, `tagline` TEXT NOT NULL, `createdTimestamp` INTEGER NOT NULL, PRIMARY KEY(`userId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `medication_intakes` (`intakeId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `medicationId` INTEGER NOT NULL, `medicationName` TEXT NOT NULL, `scheduledDateTime` INTEGER NOT NULL, `status` TEXT NOT NULL, `recordedAt` INTEGER)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_medication_intakes_medicationId_scheduledDateTime` ON `medication_intakes` (`medicationId`, `scheduledDateTime`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_medication_intakes_scheduledDateTime` ON `medication_intakes` (`scheduledDateTime`)")

        // ---- rename old tables out of the way, then create the new versions ----
        db.execSQL("ALTER TABLE `medications` RENAME TO `old_medications`")
        db.execSQL("ALTER TABLE `appointments` RENAME TO `old_appointments`")
        db.execSQL("ALTER TABLE `workout_sessions` RENAME TO `old_workout_sessions`")
        db.execSQL("DROP TABLE IF EXISTS `medication_logs`")

        db.execSQL("CREATE TABLE IF NOT EXISTS `medications` (`medicationId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `dosage` TEXT NOT NULL, `form` TEXT, `frequencyType` TEXT NOT NULL, `frequencyDescription` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER, `instructions` TEXT, `clinicianNotes` TEXT, `isActive` INTEGER NOT NULL, `remindersEnabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `appointments` (`appointmentId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `startDateTime` INTEGER NOT NULL, `endDateTime` INTEGER, `location` TEXT, `contactName` TEXT, `notes` TEXT, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointments_startDateTime` ON `appointments` (`startDateTime`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `workout_sessions` (`sessionId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workoutId` INTEGER, `workoutName` TEXT NOT NULL, `startTimestamp` INTEGER NOT NULL, `endTimestamp` INTEGER NOT NULL, `completed` INTEGER NOT NULL, `durationSeconds` INTEGER NOT NULL, `exercisesCompleted` INTEGER NOT NULL, `exercisesTotal` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_workoutId` ON `workout_sessions` (`workoutId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_startTimestamp` ON `workout_sessions` (`startTimestamp`)")
        // Child tables are created AFTER the renames: SQLite rewrites foreign keys that point at a renamed table.
        db.execSQL("CREATE TABLE IF NOT EXISTS `medication_reminders` (`reminderId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `medicationId` INTEGER NOT NULL, `hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `daysOfWeek` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, FOREIGN KEY(`medicationId`) REFERENCES `medications`(`medicationId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_medication_reminders_medicationId` ON `medication_reminders` (`medicationId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `appointment_reminders` (`reminderId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `appointmentId` INTEGER NOT NULL, `minutesBefore` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, FOREIGN KEY(`appointmentId`) REFERENCES `appointments`(`appointmentId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointment_reminders_appointmentId` ON `appointment_reminders` (`appointmentId`)")

        // ---- copy workout sessions ----
        db.query("SELECT workoutName, durationMinutes, completedAt FROM old_workout_sessions WHERE isDeleted = 0").use { c ->
            while (c.moveToNext()) {
                val minutes = c.getInt(1)
                val end = c.getLong(2)
                db.execSQL(
                    "INSERT INTO workout_sessions (workoutId, workoutName, startTimestamp, endTimestamp, completed, durationSeconds, exercisesCompleted, exercisesTotal) VALUES (NULL, ?, ?, ?, 1, ?, 0, 0)",
                    arrayOf<Any?>(c.getString(0) ?: "Workout", end - minutes * 60_000L, end, minutes * 60)
                )
            }
        }

        // ---- copy appointments (+ one 30-minute reminder, as the old scheduler did) ----
        db.query("SELECT title, scheduledAt, notes, status FROM old_appointments WHERE isDeleted = 0").use { c ->
            while (c.moveToNext()) {
                val status = c.getString(3).takeIf { it in listOf("UPCOMING", "COMPLETED", "CANCELLED") } ?: "UPCOMING"
                val v = ContentValues().apply {
                    put("title", c.getString(0) ?: "Appointment"); put("category", "Other"); put("startDateTime", c.getLong(1))
                    put("notes", (c.getString(2) ?: "").ifBlank { null }); put("status", status); put("createdAt", now)
                }
                val id = db.insert("appointments", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, v)
                db.execSQL("INSERT INTO appointment_reminders (appointmentId, minutesBefore, isEnabled) VALUES (?, 30, 1)", arrayOf<Any?>(id))
            }
        }

        // ---- copy medications and split "08:00,20:00" into reminder rows ----
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        db.query("SELECT name, instructions, times, daysOfWeek, isActive FROM old_medications WHERE isDeleted = 0").use { c ->
            while (c.moveToNext()) {
                val times = (c.getString(2) ?: "").split(",").map { it.trim() }.filter { it.matches(Regex("\\d{1,2}:\\d{2}")) }
                val mask = (c.getString(3) ?: "").split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                    .fold(0) { acc, d ->
                        val i = DAY_NAMES_LEGACY.indexOf(d) // SUN=0..SAT=6 -> Mon=bit0 .. Sun=bit6
                        if (i < 0) acc else acc or (1 shl ((i + 6) % 7))
                    }.let { if (it == 0) ALL_DAYS else it }
                val v = ContentValues().apply {
                    put("name", c.getString(0) ?: "Medication"); put("dosage", "As prescribed")
                    put("frequencyType", if (times.size > 1) "MULTIPLE_DAILY" else "ONCE_DAILY")
                    put("frequencyDescription", "Migrated from FitWell")
                    put("startDate", todayStart); put("instructions", (c.getString(1) ?: "").ifBlank { null })
                    put("isActive", c.getInt(4)); put("remindersEnabled", 1); put("createdAt", now)
                }
                val id = db.insert("medications", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, v)
                times.forEach { t ->
                    val (h, m) = t.split(":").map { it.toInt() }
                    db.execSQL(
                        "INSERT INTO medication_reminders (medicationId, hour, minute, daysOfWeek, isEnabled) VALUES (?, ?, ?, ?, 1)",
                        arrayOf<Any?>(id, h, m, mask)
                    )
                }
            }
        }

        db.execSQL("DROP TABLE `old_medications`")
        db.execSQL("DROP TABLE `old_appointments`")
        db.execSQL("DROP TABLE `old_workout_sessions`")
    }
}
