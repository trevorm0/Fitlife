package com.example.fitwell.data.repository

import androidx.room.withTransaction
import com.example.fitwell.data.local.*
import com.example.fitwell.util.startOfDay
import com.example.fitwell.util.DAY_MS
import kotlinx.coroutines.flow.Flow

class WorkoutRepository(private val db: FitLifeDatabase) {
    private val dao = db.workoutDao()
    fun observeWorkouts(): Flow<List<WorkoutEntity>> = dao.observeWorkouts()
    fun observeWorkout(id: Long): Flow<WorkoutEntity?> = dao.observeWorkout(id)
    fun observeExercises(id: Long): Flow<List<ExerciseEntity>> = dao.observeExercises(id)
    suspend fun getWorkout(id: Long) = dao.getWorkout(id)
    suspend fun getExercises(id: Long) = dao.getExercises(id)
    fun observeSessions(): Flow<List<WorkoutSessionEntity>> = dao.observeSessions()
    suspend fun saveSession(s: WorkoutSessionEntity) = dao.insertSession(s)
    suspend fun deleteSession(id: Long) = dao.deleteSession(id)
}

class MealRepository(private val db: FitLifeDatabase) {
    private val dao = db.mealDao()
    fun observeDay(dayMs: Long): Flow<List<MealEntity>> {
        val start = startOfDay(dayMs)
        return dao.observeBetween(start, start + DAY_MS - 1)
    }
    fun observeAll(): Flow<List<MealEntity>> = dao.observeAll()
    suspend fun save(m: MealEntity) = dao.upsert(m)
    suspend fun delete(m: MealEntity) = dao.delete(m)
}

class GoalRepository(private val db: FitLifeDatabase) {
    private val dao = db.goalDao()
    fun observeGoals(): Flow<List<FitnessGoalEntity>> = dao.observeGoals()
    fun observeActive(): Flow<FitnessGoalEntity?> = dao.observeActiveGoal()

    /** Only one goal is active at a time; saving an active goal deactivates the others. */
    suspend fun save(goal: FitnessGoalEntity) = db.withTransaction {
        if (goal.isActive) dao.deactivateAll()
        dao.upsert(goal)
    }
    suspend fun delete(goal: FitnessGoalEntity) = dao.delete(goal)
}

class ProfileRepository(private val db: FitLifeDatabase) {
    private val dao = db.profileDao()
    fun observe(): Flow<UserProfileEntity?> = dao.observe()
    suspend fun save(p: UserProfileEntity) = dao.upsert(p)
}

class MedicationRepository(private val db: FitLifeDatabase) {
    private val dao = db.medicationDao()

    fun observeMedications() = dao.observeMedications()
    fun observeMedication(id: Long) = dao.observeMedication(id)
    fun observeAllReminders() = dao.observeAllReminders()
    fun observeIntakesBetween(from: Long, to: Long) = dao.observeIntakesBetween(from, to)
    fun observeIntakesFor(id: Long) = dao.observeIntakesFor(id)
    fun observeAllIntakes() = dao.observeAllIntakes()
    suspend fun getMedication(id: Long) = dao.getMedication(id)
    suspend fun getReminders(id: Long) = dao.getReminders(id)

    /** Saves the medication and replaces its reminder rows in one transaction. Returns the medication id. */
    suspend fun save(med: MedicationEntity, reminders: List<MedicationReminderEntity>): Long = db.withTransaction {
        val rowId = dao.upsertMedication(med)
        val id = if (med.medicationId == 0L) rowId else med.medicationId
        dao.deleteReminders(id)
        dao.insertReminders(reminders.map { it.copy(reminderId = 0, medicationId = id) })
        id
    }

    /** Deleting a schedule removes its reminders (cascade) but keeps recorded intake history. */
    suspend fun delete(med: MedicationEntity) = dao.deleteMedication(med)

    suspend fun setActive(med: MedicationEntity, active: Boolean) = dao.upsertMedication(med.copy(isActive = active))
    suspend fun setRemindersEnabled(med: MedicationEntity, enabled: Boolean) = dao.upsertMedication(med.copy(remindersEnabled = enabled))

    /** Records what the USER says happened. Replaces any earlier record for the same scheduled dose. */
    suspend fun recordIntake(med: MedicationEntity, scheduledAt: Long, status: IntakeStatus) =
        dao.saveIntake(
            MedicationIntakeEntity(
                medicationId = med.medicationId, medicationName = med.name,
                scheduledDateTime = scheduledAt, status = status, recordedAt = System.currentTimeMillis()
            )
        )
    suspend fun clearIntake(intakeId: Long) = dao.deleteIntake(intakeId)
}

class AppointmentRepository(private val db: FitLifeDatabase) {
    private val dao = db.appointmentDao()

    fun observeAppointments() = dao.observeAppointments()
    fun observeAppointment(id: Long) = dao.observeAppointment(id)
    fun observeAllReminders() = dao.observeAllReminders()
    suspend fun getAppointment(id: Long) = dao.getAppointment(id)
    suspend fun getReminders(id: Long) = dao.getReminders(id)

    suspend fun save(a: AppointmentEntity, reminders: List<AppointmentReminderEntity>): Long = db.withTransaction {
        val rowId = dao.upsertAppointment(a)
        val id = if (a.appointmentId == 0L) rowId else a.appointmentId
        dao.deleteReminders(id)
        dao.insertReminders(reminders.distinctBy { it.minutesBefore }.map { it.copy(reminderId = 0, appointmentId = id) })
        id
    }
    suspend fun setStatus(a: AppointmentEntity, status: AppointmentStatus) = dao.upsertAppointment(a.copy(status = status))
    suspend fun delete(a: AppointmentEntity) = dao.deleteAppointment(a)
}
