package com.example.fitwell

import android.app.Application
import com.example.fitwell.data.auth.Account
import com.example.fitwell.data.auth.AuthRepository
import com.example.fitwell.data.backup.CloudBackup
import com.example.fitwell.data.local.FitLifeDatabase
import com.example.fitwell.data.prefs.AppPreferences
import com.example.fitwell.data.repository.*
import com.example.fitwell.data.sample.SampleData
import com.example.fitwell.features.adaptive.AdaptiveWellnessEngine
import com.example.fitwell.features.coach.CoachRepository
import com.example.fitwell.features.coach.MlKitPoseCoachService
import com.example.fitwell.features.leagues.LeagueRepository
import com.example.fitwell.features.mealanalysis.OnDeviceMealAnalyzer
import com.example.fitwell.features.mealanalysis.MealPhotoRepository
import com.example.fitwell.features.pulse.CameraPulseMeasurementService
import com.example.fitwell.features.pulse.PulseRepository
import com.example.fitwell.features.steps.SensorStepTrackingService
import com.example.fitwell.features.steps.StepsRepository
import com.example.fitwell.reminders.AppointmentReminderScheduler
import com.example.fitwell.reminders.MedicationReminderScheduler
import com.example.fitwell.reminders.NotificationHelper
import com.example.fitwell.reminders.ReminderCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Application class doubles as a small manual service locator (no DI framework).
 *
 * Everything that touches personal data lives in [Session], which exists only while someone is signed in
 * and is built on that person's own database file. Signing out drops it; signing in as someone else builds a new one.
 */
class FitLifeApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val prefs by lazy { AppPreferences(this) }
    val auth by lazy { AuthRepository(this) }

    private class Session(app: FitLifeApp, val account: Account) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val db = FitLifeDatabase.create(app, account.id)
        val workoutRepo = WorkoutRepository(db)
        val mealRepo = MealRepository(db)
        val goalRepo = GoalRepository(db)
        val profileRepo = ProfileRepository(db)
        val medicationRepo = MedicationRepository(db)
        val appointmentRepo = AppointmentRepository(db)
        val healthRepo = HealthRepository(app, db.stepsDao(), db.pulseDao())
        val cloudBackup = CloudBackup(db)
        val pulseRepo = PulseRepository(db.pulseDao(), CameraPulseMeasurementService(db.pulseDao()))
        val stepsService = SensorStepTrackingService(db.stepsDao(), scope, account.id)
        val stepsRepo = StepsRepository(db.stepsDao(), stepsService)
        val coachRepo = CoachRepository(db.coachDao(), MlKitPoseCoachService(db.coachDao()))
        val leagueRepo = LeagueRepository(db.leagueDao())
        val mealPhotoRepo = MealPhotoRepository(db.mealPhotoDao(), OnDeviceMealAnalyzer(app, db.mealPhotoDao()))
        val adaptiveEngine = AdaptiveWellnessEngine(db.adaptiveDao())
    }

    @Volatile private var session: Session? = null
    private fun s(): Session = session ?: error("No one is signed in")

    val hasSession: Boolean get() = session != null
    val currentAccount: Account? get() = session?.account
    /** Stable id for the signed-in person (used by leagues). */
    val currentUserId: String get() = s().account.id

    val db get() = s().db
    val workoutRepo get() = s().workoutRepo
    val mealRepo get() = s().mealRepo
    val goalRepo get() = s().goalRepo
    val profileRepo get() = s().profileRepo
    val medicationRepo get() = s().medicationRepo
    val appointmentRepo get() = s().appointmentRepo
    val healthRepo get() = s().healthRepo
    val cloudBackup get() = s().cloudBackup
    val pulseRepo get() = s().pulseRepo
    val stepsService get() = s().stepsService
    val stepsRepo get() = s().stepsRepo
    val coachRepo get() = s().coachRepo
    val leagueRepo get() = s().leagueRepo
    val mealPhotoRepo get() = s().mealPhotoRepo
    val adaptiveEngine get() = s().adaptiveEngine

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        removeLegacyDemoData()
        // Already signed in on a previous run (e.g. after a reboot): reopen that person's data so reminders keep working.
        auth.currentAccount()?.let { openSession(it) }
    }

    /** Builds the signed-in person's data layer. Call after a successful login or registration. */
    @Synchronized
    fun openSession(account: Account) {
        if (session?.account?.id == account.id) return
        val old = session
        val next = Session(this, account)
        session = next
        old?.let { it.scope.cancel(); runCatching { it.db.close() } }
        next.scope.launch {
            SampleData.seedWorkoutsIfEmpty(next.db)
            SampleData.ensureProfile(next.db, account.name)
        }
        next.stepsService.startListening(this) // no-ops until the Activity Recognition permission is granted
        appScope.launch { ReminderCoordinator.rescheduleAll(this@FitLifeApp) }
    }

    /**
     * Signs out: forgets the account, cancels this person's alarms (so their medication reminders never fire for
     * the next person) and closes their database. Navigate away from signed-in screens before calling this.
     */
    suspend fun signOut() {
        val cur = session ?: run { auth.logout(); return }
        runCatching {
            MedicationReminderScheduler.cancel(this, cur.db.medicationDao().getAllReminders())
            AppointmentReminderScheduler.cancel(this, cur.db.appointmentDao().getAllReminders())
        }
        auth.logout()
        cur.stepsService.stopListening()
        session = null
        cur.scope.cancel()
        runCatching { cur.db.close() }
    }

    /** Permanently deletes the signed-in account and every piece of its data on this device. */
    suspend fun deleteAccount() {
        val cur = session ?: return
        val id = cur.account.id
        signOut()
        auth.deleteAccount(id)
        deleteDatabase(FitLifeDatabase.nameFor(id))
        getSharedPreferences("fitlife_steps_$id", MODE_PRIVATE).edit().clear().commit()
    }

    /**
     * Earlier builds shipped a pre-made "Trevor Mbugua" profile and sample meals in one shared database.
     * That file belongs to no real account, so it is deleted once instead of being shown to the next person.
     */
    private fun removeLegacyDemoData() {
        val sp = getSharedPreferences("fitlife_migration", MODE_PRIVATE)
        if (sp.getBoolean("legacy_demo_removed", false)) return
        deleteDatabase("fitwell.db")
        sp.edit().putBoolean("legacy_demo_removed", true).apply()
    }
}
