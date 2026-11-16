package com.example.fitwell.data.repository

import android.content.Context
import com.example.fitwell.data.health.HcAvailability
import com.example.fitwell.data.health.HealthConnectSource
import com.example.fitwell.data.local.PulseDao
import com.example.fitwell.data.local.StepsDao
import com.example.fitwell.util.startOfDayMs
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Everything the Health screen shows. Every number is measured: nullable fields mean "no data yet",
 * and the UI says so instead of showing a placeholder value.
 */
data class HealthSnapshot(
    val steps: Int?, val stepsGoal: Int, val stepsSource: String,
    val activeCalories: Int?,
    val avgHeartRate: Int?, val heartRateSeries: List<Float>, val heartRateLabel: String,
    val latestPulse: Int?, val latestPulseAt: Long?,
    val sleepMinutes: Int?, val sleepWeekHours: List<Float>,
    val stepsWeek: List<Float>, val weekLabels: List<String>
) {
    companion object {
        fun empty() = HealthSnapshot(null, 8000, "", null, null, emptyList(), "", null, null, null, emptyList(), emptyList(), emptyList())
    }
}

enum class HealthConnection { CONNECTED, NEEDS_PERMISSION, NEEDS_INSTALL, UNSUPPORTED }

data class HealthState(val connection: HealthConnection, val snapshot: HealthSnapshot, val grantedCount: Int = 0, val totalPermissions: Int = 0)

/**
 * Combines two real sources: Health Connect (watch / other health apps, when the person allows it) and this phone's
 * own data (its step counter and the camera pulse readings). No sample or simulated values anywhere.
 */
class HealthRepository(
    context: Context,
    private val stepsDao: StepsDao,
    private val pulseDao: PulseDao
) {
    private val hc = HealthConnectSource(context)
    val permissionSet: Set<String> get() = hc.permissions

    suspend fun load(): HealthState {
        val availability = hc.availability()
        val granted = if (availability == HcAvailability.AVAILABLE) runCatching { hc.grantedPermissions() }.getOrDefault(emptySet()) else emptySet()
        val allowed = granted.intersect(hc.permissions)
        val connection = when (availability) {
            HcAvailability.AVAILABLE -> if (allowed.isNotEmpty()) HealthConnection.CONNECTED else HealthConnection.NEEDS_PERMISSION
            HcAvailability.NEEDS_INSTALL_OR_UPDATE -> HealthConnection.NEEDS_INSTALL
            HcAvailability.UNSUPPORTED -> HealthConnection.UNSUPPORTED
        }
        val data = if (connection == HealthConnection.CONNECTED) runCatching { hc.read(allowed) }.getOrNull() else null

        // ---- this phone's own data ----
        val today = LocalDate.now()
        val todayMs = today.startOfDayMs()
        val goal = stepsDao.latest()?.targetSteps ?: 8000
        val phoneToday = stepsDao.observeDay(todayMs).first()?.steps
        val phoneWeekRows = stepsDao.observeRange(today.minusDays(6).startOfDayMs(), todayMs).first().associateBy { it.dayStartMs }
        val phoneWeek = (0..6).map { i -> (phoneWeekRows[today.minusDays((6 - i).toLong()).startOfDayMs()]?.steps ?: 0).toFloat() }
        val pulse = pulseDao.observeRecent(20).first() // newest first

        val labels = (0..6).map {i -> today.minusDays((6 - i).toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) }
        val useHcSteps = data?.steps != null
        val hcHr = data?.heartRateSeries.orEmpty()

        return HealthState(
            connection = connection,
            grantedCount = allowed.size, totalPermissions = hc.permissions.size,
            snapshot = HealthSnapshot(
                steps = if (useHcSteps) data?.steps else phoneToday,
                stepsGoal = goal,
                stepsSource = if (useHcSteps) "Health Connect" else "This phone's step counter",
                activeCalories = data?.activeKcal,
                avgHeartRate = data?.avgHeartRate,
                heartRateSeries = if (hcHr.size >= 2) hcHr else pulse.map { it.bpm.toFloat() }.reversed(),
                heartRateLabel = if (hcHr.size >= 2) "Hourly average, last 24 hours (Health Connect)" else "Your recent pulse readings",
                latestPulse = pulse.firstOrNull()?.bpm,
                latestPulseAt = pulse.firstOrNull()?.measuredAt,
                sleepMinutes = data?.sleepMinutes,
                sleepWeekHours = data?.sleepWeekHours.orEmpty(),
                stepsWeek = data?.stepsWeek?.takeIf { it.isNotEmpty() } ?: phoneWeek,
                weekLabels = labels
            )
        )
    }
}
