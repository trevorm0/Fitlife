package com.example.fitwell.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

const val MINUTE_MS = 60_000L
const val HOUR_MS = 3_600_000L
const val DAY_MS = 86_400_000L

/** Days of week are stored as a bitmask: bit 0 = Monday ... bit 6 = Sunday. */
const val ALL_DAYS = 127
fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)
val WEEK_DAYS = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
)
fun DayOfWeek.shortLabel(): String = name.take(3).lowercase().replaceFirstChar { it.uppercase() }

fun daysLabel(mask: Int): String = when {
    mask and ALL_DAYS == ALL_DAYS -> "Every day"
    mask == 0 -> "No days selected"
    else -> WEEK_DAYS.filter { mask and dayBit(it) != 0 }.joinToString(", ") { it.shortLabel() }
}

private val zone: ZoneId get() = ZoneId.systemDefault()
private val dateFmt: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
private val shortDateFmt: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val timeFmt: DateTimeFormatter get() = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

fun Long.toLocalDateTime(): LocalDateTime = Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()
fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
fun LocalDateTime.toEpochMs(): Long = atZone(zone).toInstant().toEpochMilli()
fun LocalDate.startOfDayMs(): Long = atStartOfDay(zone).toInstant().toEpochMilli()
fun LocalDate.atTimeMs(hour: Int, minute: Int): Long = LocalDateTime.of(this, LocalTime.of(hour, minute)).toEpochMs()

/** Material3 DatePicker works in UTC millis. */
fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
fun Long.fromPickerMillis(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

fun startOfDay(ms: Long = System.currentTimeMillis()): Long = ms.toLocalDate().startOfDayMs()

fun formatDate(ms: Long): String = ms.toLocalDateTime().format(dateFmt)
fun formatTime(ms: Long): String = ms.toLocalDateTime().format(timeFmt)
fun formatTimeHM(hour: Int, minute: Int): String = LocalTime.of(hour, minute).format(timeFmt)
fun formatDateTime(ms: Long): String = "${relativeDay(ms)}, ${formatTime(ms)}"

fun relativeDay(ms: Long, today: LocalDate = LocalDate.now()): String {
    val d = ms.toLocalDate()
    return when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(shortDateFmt)
    }
}

/** "in 2 h 15 min", "in 3 days", "now" or "5 min ago". */
fun timeUntil(ms: Long, now: Long = System.currentTimeMillis()): String {
    val diff = ms - now
    val abs = kotlin.math.abs(diff)
    val text = when {
        abs < MINUTE_MS -> return "now"
        abs < HOUR_MS -> "${abs / MINUTE_MS} min"
        abs < DAY_MS -> "${abs / HOUR_MS} h ${(abs % HOUR_MS) / MINUTE_MS} min"
        else -> "${abs / DAY_MS} day${if (abs / DAY_MS == 1L) "" else "s"}"
    }
    return if (diff >= 0) "in $text" else "$text ago"
}

fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
val DAY_NAMES_LEGACY = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
