package com.example.fitwell.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ---------- Enums (Room stores them by name) ----------
enum class WorkoutCategory(val label: String) {
    STRENGTH("Strength"), CARDIO("Cardio"), FLEXIBILITY("Flexibility"), CORE("Core"), ENDURANCE("Endurance")
}
enum class Difficulty(val label: String) { LOW("Low"), MODERATE("Moderate"), HIGH("High") }
enum class MealCategory(val label: String) { BREAKFAST("Breakfast"), LUNCH("Lunch"), DINNER("Dinner"), SNACK("Snack") }
enum class GoalType(val label: String) {
    LOSE_WEIGHT("Lose Weight"), BUILD_MUSCLE("Build Muscle"),
    IMPROVE_ENDURANCE("Improve Endurance"), STAY_HEALTHY("Stay Healthy")
}
enum class FrequencyType(val label: String) {
    ONCE_DAILY("Once daily"), MULTIPLE_DAILY("Multiple times daily"),
    SELECTED_DAYS("Selected days of the week"), CUSTOM("Custom schedule")
}
enum class IntakeStatus(val label: String) { TAKEN("Taken"), SKIPPED("Skipped"), MISSED("Missed") }
enum class AppointmentStatus(val label: String) { UPCOMING("Upcoming"), COMPLETED("Completed"), CANCELLED("Cancelled") }

// ---------- Fitness ----------
@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val workoutId: Long = 0,
    val name: String,
    val description: String,
    val category: WorkoutCategory,
    val durationMinutes: Int,
    val difficulty: Difficulty,
    val targetArea: String,
    /** Name of a drawable in res/drawable (e.g. "workout_full_body"). A placeholder is shown if it is missing. */
    val imageReference: String
)

@Entity(
    tableName = "exercises",
    foreignKeys = [ForeignKey(WorkoutEntity::class, ["workoutId"], ["workoutId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workoutId")]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val exerciseId: Long = 0,
    val workoutId: Long,
    val name: String,
    val instructions: String,
    /** 0 means rep-based: the user taps Done after finishing [targetRepetitions]. */
    val durationSeconds: Int,
    val targetRepetitions: Int? = null,
    val orderIndex: Int,
    val imageReference: String = ""
)

@Entity(tableName = "workout_sessions", indices = [Index("workoutId"), Index("startTimestamp")])
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val workoutId: Long?,
    val workoutName: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val completed: Boolean,
    val durationSeconds: Int,
    val exercisesCompleted: Int = 0,
    val exercisesTotal: Int = 0
)

// ---------- Nutrition / goals / profile ----------
@Entity(tableName = "meals", indices = [Index("timestamp")])
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val mealId: Long = 0,
    val name: String,
    val mealCategory: MealCategory,
    val calories: Int,
    val proteinGrams: Int,
    val carbohydrateGrams: Int,
    val fatGrams: Int,
    val timestamp: Long,
    val description: String = ""
)

@Entity(tableName = "fitness_goals")
data class FitnessGoalEntity(
    @PrimaryKey(autoGenerate = true) val goalId: Long = 0,
    val goalType: GoalType,
    val title: String,
    val description: String,
    val startValue: Double,
    val targetValue: Double,
    val currentValue: Double,
    val unit: String,
    val createdTimestamp: Long,
    val isActive: Boolean = true
) {
    /** 0..1. Works for goals that go down (weight) and up (minutes, workouts). */
    val progress: Float
        get() = if (targetValue == startValue) 0f
        else ((currentValue - startValue) / (targetValue - startValue)).toFloat().coerceIn(0f, 1f)
}

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val userId: Long = 1,
    val displayName: String,
    val profileImageReference: String? = null,
    val preferredWorkoutCategory: String = "Strength",
    val tagline: String = "Fitness Enthusiast",
    val createdTimestamp: Long
)

// ---------- Medication ----------
@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val medicationId: Long = 0,
    val name: String,
    /** Free text exactly as entered by the user. The app never suggests or infers a dosage. */
    val dosage: String,
    val form: String? = null,
    val frequencyType: FrequencyType = FrequencyType.ONCE_DAILY,
    val frequencyDescription: String = "",
    /** Start of the start day (local time, epoch ms). */
    val startDate: Long,
    /** Start of the last day (local time, epoch ms), or null for no end date. */
    val endDate: Long? = null,
    val instructions: String? = null,
    val clinicianNotes: String? = null,
    val isActive: Boolean = true,
    val remindersEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "medication_reminders",
    foreignKeys = [ForeignKey(MedicationEntity::class, ["medicationId"], ["medicationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("medicationId")]
)
data class MedicationReminderEntity(
    @PrimaryKey(autoGenerate = true) val reminderId: Long = 0,
    val medicationId: Long,
    val hour: Int,
    val minute: Int,
    /** Bitmask, bit 0 = Monday ... bit 6 = Sunday. 127 = every day. */
    val daysOfWeek: Int = 127,
    val isEnabled: Boolean = true
)

/**
 * A user-recorded outcome for one scheduled dose. No foreign key on purpose: history is kept when the
 * schedule is deleted, so the medication name is stored as a snapshot.
 * (medicationId, scheduledDateTime) is unique, so one occurrence can never have duplicate records.
 */
@Entity(
    tableName = "medication_intakes",
    indices = [Index(value = ["medicationId", "scheduledDateTime"], unique = true), Index("scheduledDateTime")]
)
data class MedicationIntakeEntity(
    @PrimaryKey(autoGenerate = true) val intakeId: Long = 0,
    val medicationId: Long,
    val medicationName: String,
    val scheduledDateTime: Long,
    val status: IntakeStatus,
    val recordedAt: Long? = null
)

// ---------- Appointments ----------
@Entity(tableName = "appointments", indices = [Index("startDateTime")])
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true) val appointmentId: Long = 0,
    val title: String,
    /** Free text; the UI suggests common categories but does not restrict it. */
    val category: String,
    val startDateTime: Long,
    val endDateTime: Long? = null,
    val location: String? = null,
    val contactName: String? = null,
    val notes: String? = null,
    val status: AppointmentStatus = AppointmentStatus.UPCOMING,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "appointment_reminders",
    foreignKeys = [ForeignKey(AppointmentEntity::class, ["appointmentId"], ["appointmentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("appointmentId")]
)
data class AppointmentReminderEntity(
    @PrimaryKey(autoGenerate = true) val reminderId: Long = 0,
    val appointmentId: Long,
    /** 0 = at the appointment time. */
    val minutesBefore: Int,
    val isEnabled: Boolean = true
)

// ---------- Pulse / Steps / Activity (feature modules) ----------
enum class PulseSource(val label: String) {
    CAMERA("Camera PPG"), WEARABLE("Wearable"), HEALTH_PLATFORM("Health platform"), MANUAL("Manual")
}

@Entity(tableName = "pulse_readings", indices = [Index("measuredAt")])
data class PulseReadingEntity(
    @PrimaryKey(autoGenerate = true) val readingId: Long = 0,
    val bpm: Int,
    val measuredAt: Long,
    val source: PulseSource,
    /** 0..1 confidence from sensor / algorithm when available. */
    val confidence: Float? = null,
    val note: String = ""
)

@Entity(tableName = "daily_steps", indices = [Index(value = ["dayStartMs"], unique = true)])
data class DailyStepsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayStartMs: Long,
    val steps: Int,
    val targetSteps: Int = 8000,
    val source: String = "sensor", // sensor | health_connect | manual
    val updatedAt: Long = System.currentTimeMillis()
)

// ---------- Camera coach sessions ----------
@Entity(tableName = "coach_sessions", indices = [Index("startedAt")])
data class CoachSessionEntity(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val exerciseKey: String,
    val exerciseName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val repsDetected: Int = 0,
    val formScore: Float? = null, // 0..1 aggregate
    val feedbackSummary: String = "",
    val stoppedByUser: Boolean = false
)

// ---------- Invite-only leagues ----------
enum class LeagueScoreMode(val label: String) {
    CONSISTENCY("Consistency"), WORKOUTS("Completed workouts"),
    STEPS("Steps"), IMPROVEMENT("Personal improvement")
}

@Entity(tableName = "leagues")
data class LeagueEntity(
    @PrimaryKey(autoGenerate = true) val leagueId: Long = 0,
    val name: String,
    val inviteCode: String,
    val createdByUserId: String,
    val scoreMode: LeagueScoreMode,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

@Entity(
    tableName = "league_members",
    foreignKeys = [ForeignKey(LeagueEntity::class, ["leagueId"], ["leagueId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("leagueId"), Index(value = ["leagueId", "memberUserId"], unique = true)]
)
data class LeagueMemberEntity(
    @PrimaryKey(autoGenerate = true) val memberId: Long = 0,
    val leagueId: Long,
    val memberUserId: String,
    val displayName: String,
    val joinedAt: Long = System.currentTimeMillis(),
    val shareSteps: Boolean = true,
    val shareWorkouts: Boolean = true,
    val sharePulse: Boolean = false,
    val isBlocked: Boolean = false
)

@Entity(
    tableName = "league_challenges",
    foreignKeys = [ForeignKey(LeagueEntity::class, ["leagueId"], ["leagueId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("leagueId")]
)
data class LeagueChallengeEntity(
    @PrimaryKey(autoGenerate = true) val challengeId: Long = 0,
    val leagueId: Long,
    val title: String,
    val weekStartMs: Long,
    val weekEndMs: Long,
    val scoreMode: LeagueScoreMode
)

@Entity(
    tableName = "league_scores",
    foreignKeys = [ForeignKey(LeagueChallengeEntity::class, ["challengeId"], ["challengeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("challengeId"), Index(value = ["challengeId", "memberUserId"], unique = true)]
)
data class LeagueScoreEntity(
    @PrimaryKey(autoGenerate = true) val scoreId: Long = 0,
    val challengeId: Long,
    val memberUserId: String,
    val points: Int,
    val detail: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

// ---------- Meal photo analysis ----------
@Entity(tableName = "meal_photo_analyses", indices = [Index("createdAt")])
data class MealPhotoAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val analysisId: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** Local path or content URI string; video/photo not retained longer than needed. */
    val localImageRef: String? = null,
    val recognizedItemsJson: String, // JSON array of {name, portion, cal, conf}
    val estimatedCalories: Int,
    val estimatedProteinG: Int,
    val estimatedCarbsG: Int,
    val estimatedFatG: Int,
    val overallConfidence: Float,
    val userCorrected: Boolean = false,
    val linkedMealId: Long? = null,
    val notes: String = ""
)

// ---------- Adaptive recommendations log (explainable) ----------
@Entity(tableName = "adaptive_recommendations", indices = [Index("createdAt")])
data class AdaptiveRecommendationEntity(
    @PrimaryKey(autoGenerate = true) val recId: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val title: String,
    val explanation: String,
    val suggestedStepsTarget: Int? = null,
    val suggestedWorkoutMinutes: Int? = null,
    val suggestedCalorieTarget: Int? = null,
    val basedOnSummary: String
)
