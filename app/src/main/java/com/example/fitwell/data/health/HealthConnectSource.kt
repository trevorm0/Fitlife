package com.example.fitwell.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

enum class HcAvailability { AVAILABLE, NEEDS_INSTALL_OR_UPDATE, UNSUPPORTED }

/** What was read from Health Connect. A null means "no data" (not permitted, or nothing recorded), never a made-up value. */
data class HcData(
    val steps: Int? = null,
    val activeKcal: Int? = null,
    val avgHeartRate: Int? = null,
    val heartRateSeries: List<Float> = emptyList(),
    val sleepMinutes: Int? = null,
    val sleepWeekHours: List<Float> = emptyList(),
    val stepsWeek: List<Float> = emptyList()
)

/**
 * Read-only access to Health Connect (Android's shared store that watches, Samsung Health, Google Fit and other
 * apps write into). Nothing read here is copied into FitLife's database.
 */
class HealthConnectSource(private val context: Context) {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    )

    fun availability(): HcAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HcAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcAvailability.NEEDS_INSTALL_OR_UPDATE
        else -> HcAvailability.UNSUPPORTED
    }

    suspend fun grantedPermissions(): Set<String> =
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()

    suspend fun read(granted: Set<String>): HcData {
        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = Instant.now()
        val startToday = today.atStartOfDay(zone).toInstant()
        val weekStart = today.minusDays(6).atStartOfDay()

        var steps: Int? = null; var stepsWeek = emptyList<Float>()
        if (HealthPermission.getReadPermission(StepsRecord::class) in granted) runCatching {
            steps = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(startToday, now)))[StepsRecord.COUNT_TOTAL]?.toInt()
            val groups = client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(weekStart, today.plusDays(1).atStartOfDay()), Period.ofDays(1))
            )
            stepsWeek = (0..6).map { i ->
                val day = today.minusDays((6 - i).toLong())
                (groups.firstOrNull { it.startTime.toLocalDate() == day }?.result?.get(StepsRecord.COUNT_TOTAL) ?: 0L).toFloat()
            }
        }

        var kcal: Int? = null
        if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted) runCatching {
            kcal = client.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), TimeRangeFilter.between(startToday, now)))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
                ?.inKilocalories?.toInt()
        }

        var avgHr: Int? = null; var hrSeries = emptyList<Float>()
        if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) runCatching {
            avgHr = client.aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_AVG), TimeRangeFilter.between(startToday, now)))[HeartRateRecord.BPM_AVG]?.toInt()
            hrSeries = client.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(setOf(HeartRateRecord.BPM_AVG), TimeRangeFilter.between(now.minus(Duration.ofHours(24)), now), Duration.ofHours(1))
            ).mapNotNull { it.result[HeartRateRecord.BPM_AVG]?.toFloat() }
        }

        var sleepToday: Int? = null; var sleepWeek = emptyList<Float>()
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) runCatching {
            val sessions = client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(weekStart.minusDays(1).atZone(zone).toInstant(), now))
            ).records
            // A night belongs to the day you wake up. Time awake or out of bed during the session is not counted as sleep.
            fun minutes(s: SleepSessionRecord): Long {
                val total = Duration.between(s.startTime, s.endTime).toMinutes()
                val awake = s.stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_AWAKE || it.stage == SleepSessionRecord.STAGE_TYPE_OUT_OF_BED }
                    .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                return (total - awake).coerceAtLeast(0)
            }
            val byDay = sessions.groupBy { it.endTime.atZone(zone).toLocalDate() }.mapValues { (_, v) -> v.sumOf { minutes(it) } }
            sleepWeek = (0..6).map { i -> (byDay[today.minusDays((6 - i).toLong())] ?: 0L) / 60f }
            sleepToday = byDay[today]?.toInt()?.takeIf { it > 0 }
        }

        return HcData(steps, kcal, avgHr, hrSeries, sleepToday, sleepWeek, stepsWeek)
    }
}
