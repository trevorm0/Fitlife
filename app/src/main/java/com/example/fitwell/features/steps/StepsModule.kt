package com.example.fitwell.features.steps

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.fitwell.data.local.DailyStepsEntity
import com.example.fitwell.data.local.StepsDao
import com.example.fitwell.util.startOfDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Automatic step tracking.
 *
 * Production:
 * - TYPE_STEP_COUNTER / TYPE_STEP_DETECTOR when available.
 * - Health Connect (Android) for aggregated steps from wearables and other apps.
 * - Request ACTIVITY_RECOGNITION (and BODY_SENSORS where needed); handle denial gracefully.
 * - Background: use a foreground service or WorkManager with user-visible rationale; respect battery restrictions.
 */
interface StepTrackingService {
    val todaySteps: StateFlow<Int>
    val sensorAvailable: StateFlow<Boolean>
    /** True while the Activity Recognition permission (Android 10+) is missing, so no steps can be counted. */
    val permissionNeeded: StateFlow<Boolean>
    fun observeDay(dayStartMs: Long): Flow<DailyStepsEntity?>
    fun observeWeek(weekStartMs: Long): Flow<List<DailyStepsEntity>>
    suspend fun setTarget(dayStartMs: Long, target: Int)
    suspend fun addManualSteps(dayStartMs: Long, delta: Int)
    fun startListening(context: Context)
    fun stopListening()
}

class StepsRepository(
    private val dao: StepsDao,
    private val service: StepTrackingService
) {
    val todaySteps = service.todaySteps
    val sensorAvailable = service.sensorAvailable
    val permissionNeeded = service.permissionNeeded
    fun observeDay(dayStartMs: Long) = service.observeDay(dayStartMs)
    fun observeWeek(weekStartMs: Long) = service.observeWeek(weekStartMs)
    suspend fun setTarget(dayStartMs: Long, target: Int) = service.setTarget(dayStartMs, target)
    suspend fun addManual(dayStartMs: Long, delta: Int) = service.addManualSteps(dayStartMs, delta)
    fun start(context: Context) = service.startListening(context)
    fun stop() = service.stopListening()
}

class SensorStepTrackingService(
    private val dao: StepsDao,
    private val scope: CoroutineScope,
    accountId: String
) : StepTrackingService, SensorEventListener {

    private val prefsName = "fitlife_steps_$accountId"
    private var prefs: SharedPreferences? = null
    private var sensorManager: SensorManager? = null
    private var loadJob: Job? = null
    private var registered = false
    private val mutex = Mutex()

    private val _today = MutableStateFlow(0)
    override val todaySteps: StateFlow<Int> = _today.asStateFlow()

    private val _available = MutableStateFlow(false)
    override val sensorAvailable: StateFlow<Boolean> = _available.asStateFlow()

    private val _permissionNeeded = MutableStateFlow(false)
    override val permissionNeeded: StateFlow<Boolean> = _permissionNeeded.asStateFlow()

    override fun observeDay(dayStartMs: Long) = dao.observeDay(dayStartMs)

    override fun observeWeek(weekStartMs: Long): Flow<List<DailyStepsEntity>> {
        val end = weekStartMs + 7 * 24 * 60 * 60 * 1000L - 1
        return dao.observeRange(weekStartMs, end)
    }

    override suspend fun setTarget(dayStartMs: Long, target: Int) {
        val existing = dao.latest()?.takeIf { it.dayStartMs == dayStartMs }
        dao.upsert(
            (existing ?: DailyStepsEntity(dayStartMs = dayStartMs, steps = _today.value))
                .copy(targetSteps = target.coerceIn(1000, 50000), updatedAt = System.currentTimeMillis())
        )
    }

    override suspend fun addManualSteps(dayStartMs: Long, delta: Int) = mutex.withLock {
        val existing = dao.latest()?.takeIf { it.dayStartMs == dayStartMs }
        val steps = ((existing?.steps ?: _today.value) + delta).coerceAtLeast(0)
        dao.upsert(
            DailyStepsEntity(
                id = existing?.id ?: 0, dayStartMs = dayStartMs, steps = steps,
                targetSteps = existing?.targetSteps ?: 8000, source = "manual", updatedAt = System.currentTimeMillis()
            )
        )
        if (dayStartMs == startOfDay(System.currentTimeMillis())) {
            _today.value = steps
            // Keep the sensor anchor in step with manual edits so the next sensor reading does not undo them.
            prefs?.let { p -> if (p.contains(K_BASE)) p.edit().putFloat(K_BASE, p.getFloat(K_BASE, 0f) - delta).apply() }
        }
    }

    private fun hasPermission(ctx: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ctx.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Safe to call again after the permission is granted: it registers the sensor then. */
    override fun startListening(context: Context) {
        if (registered) return
        val ctx = context.applicationContext
        if (prefs == null) prefs = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        if (loadJob == null) {
            loadJob = scope.launch {
                dao.observeDay(startOfDay(System.currentTimeMillis())).collect { e -> if (e != null) _today.value = e.steps }
            }
        }
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        _available.value = sensor != null
        if (sensor == null) return
        if (!hasPermission(ctx)) { _permissionNeeded.value = true; return }
        _permissionNeeded.value = false
        sensorManager = sm
        registered = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun stopListening() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        registered = false
        loadJob?.cancel(); loadJob = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val type = event.sensor.type
        val value = event.values[0]
        scope.launch { mutex.withLock { handle(type, value) } }
    }

    private suspend fun handle(type: Int, value: Float) {
        val p = prefs ?: return
        val now = System.currentTimeMillis()
        val day = startOfDay(now)
        val saved = dao.latest()?.takeIf { it.dayStartMs == day }

        val steps = when (type) {
            Sensor.TYPE_STEP_COUNTER -> {
                // The hardware counter is cumulative since boot. Anchor it so "today" = counter - anchor.
                // Re-anchor on a new day, first run, or after a reboot (counter went backwards), keeping steps already saved today.
                val baseDay = p.getLong(K_BASE_DAY, -1L)
                val lastCum = p.getFloat(K_LAST, -1f)
                var base = p.getFloat(K_BASE, -1f)
                if (baseDay != day || base < 0f || value < lastCum) {
                    base = value - (if (baseDay == day || saved != null) saved?.steps ?: 0 else 0)
                    p.edit().putLong(K_BASE_DAY, day).putFloat(K_BASE, base).apply()
                }
                p.edit().putFloat(K_LAST, value).apply()
                (value - base).toInt().coerceAtLeast(0)
            }
            else -> (saved?.steps ?: 0) + 1 // TYPE_STEP_DETECTOR: one event per step
        }
        _today.value = steps
        dao.upsert(
            DailyStepsEntity(
                id = saved?.id ?: 0, dayStartMs = day, steps = steps,
                targetSteps = saved?.targetSteps ?: 8000, source = "sensor", updatedAt = now
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val K_BASE_DAY = "base_day"
        const val K_BASE = "base"
        const val K_LAST = "last_counter"
    }
}
