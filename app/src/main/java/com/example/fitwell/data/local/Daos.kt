package com.example.fitwell.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY workoutId") fun observeWorkouts(): Flow<List<WorkoutEntity>>
    @Query("SELECT * FROM workouts WHERE workoutId = :id") fun observeWorkout(id: Long): Flow<WorkoutEntity?>
    @Query("SELECT * FROM workouts WHERE workoutId = :id") suspend fun getWorkout(id: Long): WorkoutEntity?
    @Query("SELECT * FROM exercises WHERE workoutId = :id ORDER BY orderIndex") fun observeExercises(id: Long): Flow<List<ExerciseEntity>>
    @Query("SELECT * FROM exercises WHERE workoutId = :id ORDER BY orderIndex") suspend fun getExercises(id: Long): List<ExerciseEntity>
    @Query("SELECT COUNT(*) FROM workouts") suspend fun countWorkouts(): Int
    @Insert suspend fun insertWorkout(w: WorkoutEntity): Long
    @Insert suspend fun insertExercises(list: List<ExerciseEntity>)

    @Query("SELECT * FROM workout_sessions ORDER BY startTimestamp DESC") fun observeSessions(): Flow<List<WorkoutSessionEntity>>
    @Insert suspend fun insertSession(s: WorkoutSessionEntity): Long
    @Query("DELETE FROM workout_sessions WHERE sessionId = :id") suspend fun deleteSession(id: Long)
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meals WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp") fun observeBetween(from: Long, to: Long): Flow<List<MealEntity>>
    @Query("SELECT * FROM meals ORDER BY timestamp DESC") fun observeAll(): Flow<List<MealEntity>>
    @Query("SELECT COUNT(*) FROM meals") suspend fun count(): Int
    @Upsert suspend fun upsert(m: MealEntity)
    @Delete suspend fun delete(m: MealEntity)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM fitness_goals ORDER BY createdTimestamp DESC") fun observeGoals(): Flow<List<FitnessGoalEntity>>
    @Query("SELECT * FROM fitness_goals WHERE isActive = 1 ORDER BY createdTimestamp DESC LIMIT 1") fun observeActiveGoal(): Flow<FitnessGoalEntity?>
    @Query("UPDATE fitness_goals SET isActive = 0") suspend fun deactivateAll()
    @Upsert suspend fun upsert(g: FitnessGoalEntity): Long
    @Delete suspend fun delete(g: FitnessGoalEntity)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE userId = 1") fun observe(): Flow<UserProfileEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(p: UserProfileEntity)
}

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY name COLLATE NOCASE") fun observeMedications(): Flow<List<MedicationEntity>>
    @Query("SELECT * FROM medications WHERE medicationId = :id") fun observeMedication(id: Long): Flow<MedicationEntity?>
    @Query("SELECT * FROM medications WHERE medicationId = :id") suspend fun getMedication(id: Long): MedicationEntity?
    @Query("SELECT * FROM medications") suspend fun getAllMedications(): List<MedicationEntity>
    @Upsert suspend fun upsertMedication(m: MedicationEntity): Long
    @Delete suspend fun deleteMedication(m: MedicationEntity)

    @Query("SELECT * FROM medication_reminders ORDER BY hour, minute") fun observeAllReminders(): Flow<List<MedicationReminderEntity>>
    @Query("SELECT * FROM medication_reminders WHERE medicationId = :id ORDER BY hour, minute") suspend fun getReminders(id: Long): List<MedicationReminderEntity>
    @Query("SELECT * FROM medication_reminders") suspend fun getAllReminders(): List<MedicationReminderEntity>
    @Query("SELECT * FROM medication_reminders WHERE reminderId = :id") suspend fun getReminder(id: Long): MedicationReminderEntity?
    @Insert suspend fun insertReminders(list: List<MedicationReminderEntity>)
    @Query("DELETE FROM medication_reminders WHERE medicationId = :id") suspend fun deleteReminders(id: Long)

    @Query("SELECT * FROM medication_intakes WHERE scheduledDateTime BETWEEN :from AND :to") fun observeIntakesBetween(from: Long, to: Long): Flow<List<MedicationIntakeEntity>>
    @Query("SELECT * FROM medication_intakes WHERE medicationId = :id ORDER BY scheduledDateTime DESC") fun observeIntakesFor(id: Long): Flow<List<MedicationIntakeEntity>>
    @Query("SELECT * FROM medication_intakes ORDER BY scheduledDateTime DESC") fun observeAllIntakes(): Flow<List<MedicationIntakeEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveIntake(i: MedicationIntakeEntity)
    @Query("DELETE FROM medication_intakes WHERE intakeId = :id") suspend fun deleteIntake(id: Long)
}

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY startDateTime") fun observeAppointments(): Flow<List<AppointmentEntity>>
    @Query("SELECT * FROM appointments WHERE appointmentId = :id") fun observeAppointment(id: Long): Flow<AppointmentEntity?>
    @Query("SELECT * FROM appointments WHERE appointmentId = :id") suspend fun getAppointment(id: Long): AppointmentEntity?
    @Query("SELECT * FROM appointments") suspend fun getAllAppointments(): List<AppointmentEntity>
    @Upsert suspend fun upsertAppointment(a: AppointmentEntity): Long
    @Delete suspend fun deleteAppointment(a: AppointmentEntity)

    @Query("SELECT * FROM appointment_reminders") fun observeAllReminders(): Flow<List<AppointmentReminderEntity>>
    @Query("SELECT * FROM appointment_reminders WHERE appointmentId = :id ORDER BY minutesBefore DESC") suspend fun getReminders(id: Long): List<AppointmentReminderEntity>
    @Query("SELECT * FROM appointment_reminders") suspend fun getAllReminders(): List<AppointmentReminderEntity>
    @Query("SELECT * FROM appointment_reminders WHERE reminderId = :id") suspend fun getReminder(id: Long): AppointmentReminderEntity?
    @Insert suspend fun insertReminders(list: List<AppointmentReminderEntity>)
    @Query("DELETE FROM appointment_reminders WHERE appointmentId = :id") suspend fun deleteReminders(id: Long)
}

@Dao
interface PulseDao {
    @Query("SELECT * FROM pulse_readings ORDER BY measuredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<PulseReadingEntity>>
    @Query("SELECT * FROM pulse_readings WHERE measuredAt BETWEEN :from AND :to ORDER BY measuredAt DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<PulseReadingEntity>>
    @Insert suspend fun insert(r: PulseReadingEntity): Long
    @Query("DELETE FROM pulse_readings WHERE readingId = :id") suspend fun delete(id: Long)
}

@Dao
interface StepsDao {
    @Query("SELECT * FROM daily_steps WHERE dayStartMs = :dayStart LIMIT 1")
    fun observeDay(dayStart: Long): Flow<DailyStepsEntity?>
    @Query("SELECT * FROM daily_steps WHERE dayStartMs BETWEEN :from AND :to ORDER BY dayStartMs")
    fun observeRange(from: Long, to: Long): Flow<List<DailyStepsEntity>>
    @Upsert suspend fun upsert(s: DailyStepsEntity)
    @Query("SELECT * FROM daily_steps ORDER BY dayStartMs DESC LIMIT 1") suspend fun latest(): DailyStepsEntity?
}

@Dao
interface CoachDao {
    @Query("SELECT * FROM coach_sessions ORDER BY startedAt DESC") fun observeSessions(): Flow<List<CoachSessionEntity>>
    @Insert suspend fun insert(s: CoachSessionEntity): Long
    @Query("UPDATE coach_sessions SET endedAt = :ended, repsDetected = :reps, formScore = :score, feedbackSummary = :summary, stoppedByUser = :stopped WHERE sessionId = :id")
    suspend fun finish(id: Long, ended: Long, reps: Int, score: Float?, summary: String, stopped: Boolean)
}

@Dao
interface LeagueDao {
    @Query("SELECT * FROM leagues WHERE isActive = 1 ORDER BY createdAt DESC") fun observeLeagues(): Flow<List<LeagueEntity>>
    @Query("SELECT * FROM leagues WHERE leagueId = :id") fun observeLeague(id: Long): Flow<LeagueEntity?>
    @Query("SELECT * FROM leagues WHERE inviteCode = :code LIMIT 1") suspend fun findByCode(code: String): LeagueEntity?
    @Insert suspend fun insertLeague(l: LeagueEntity): Long
    @Query("UPDATE leagues SET isActive = 0 WHERE leagueId = :id") suspend fun deactivate(id: Long)

    @Query("SELECT * FROM league_members WHERE leagueId = :leagueId AND isBlocked = 0") fun observeMembers(leagueId: Long): Flow<List<LeagueMemberEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMember(m: LeagueMemberEntity)
    @Query("DELETE FROM league_members WHERE leagueId = :leagueId AND memberUserId = :userId") suspend fun removeMember(leagueId: Long, userId: String)
    @Query("UPDATE league_members SET isBlocked = 1 WHERE memberId = :memberId") suspend fun blockMember(memberId: Long)

    @Query("SELECT * FROM league_challenges WHERE leagueId = :leagueId ORDER BY weekStartMs DESC") fun observeChallenges(leagueId: Long): Flow<List<LeagueChallengeEntity>>
    @Insert suspend fun insertChallenge(c: LeagueChallengeEntity): Long

    @Query("SELECT * FROM league_scores WHERE challengeId = :challengeId ORDER BY points DESC") fun observeScores(challengeId: Long): Flow<List<LeagueScoreEntity>>
    @Upsert suspend fun upsertScore(s: LeagueScoreEntity)
}

@Dao
interface MealPhotoDao {
    @Query("SELECT * FROM meal_photo_analyses ORDER BY createdAt DESC") fun observeAll(): Flow<List<MealPhotoAnalysisEntity>>
    @Insert suspend fun insert(a: MealPhotoAnalysisEntity): Long
    @Upsert suspend fun upsert(a: MealPhotoAnalysisEntity)
    @Query("DELETE FROM meal_photo_analyses WHERE analysisId = :id") suspend fun delete(id: Long)
}

@Dao
interface AdaptiveDao {
    @Query("SELECT * FROM adaptive_recommendations ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<AdaptiveRecommendationEntity>>
    @Insert suspend fun insert(r: AdaptiveRecommendationEntity): Long
}
