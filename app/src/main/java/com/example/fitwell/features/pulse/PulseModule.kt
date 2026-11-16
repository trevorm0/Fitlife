package com.example.fitwell.features.pulse

import android.content.Context
import android.content.ContextWrapper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import com.example.fitwell.data.local.PulseDao
import com.example.fitwell.data.local.PulseReadingEntity
import com.example.fitwell.data.local.PulseSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pulse measurement module.
 *
 * Production path (camera PPG):
 * - Use CameraX + ImageAnalysis with a fingertip over the rear flash/torch.
 * - Extract green-channel temporal signal, band-pass filter, peak detection for BPM.
 * - Validated approaches exist in literature (e.g. smartphone PPG studies); do not claim medical accuracy.
 * - Prefer on-device processing; discard frames after measurement.
 *
 * Optional: Health Connect / Google Fit / Samsung Health for wearable heart-rate samples.
 *
 * Always show limitations: camera PPG is an estimate, affected by motion, skin tone lighting,
 * pressure, and is not a diagnostic device.
 */
interface PulseMeasurementService {
    val isMeasuring: StateFlow<Boolean>
    val liveBpm: StateFlow<Int?>
    val lastError: StateFlow<String?>
    /** 0..1 while a camera measurement runs. */
    val progress: StateFlow<Float>
    /** Live guidance such as "Cover the camera and flash with your fingertip". */
    val hint: StateFlow<String?>

    /** Start a short (~15–30s) camera-based measurement when hardware supports it. */
    suspend fun startCameraMeasurement(context: Context): Result<PulseReadingEntity>

    /** Import or record from wearable / health platform when available. */
    suspend fun recordFromHealthPlatform(bpm: Int, measuredAt: Long = System.currentTimeMillis()): PulseReadingEntity

    suspend fun recordManual(bpm: Int, note: String = ""): PulseReadingEntity

    fun observeRecent(limit: Int = 30): Flow<List<PulseReadingEntity>>

    fun limitationsText(): String
}

class PulseRepository(
    private val dao: PulseDao,
    val measurement: PulseMeasurementService
) {
    fun observeRecent(limit: Int = 30) = measurement.observeRecent(limit)
    suspend fun measureWithCamera(context: Context) = measurement.startCameraMeasurement(context)
    suspend fun recordManual(bpm: Int, note: String = "") = measurement.recordManual(bpm, note)
    fun limitations() = measurement.limitationsText()
}

/**
 * Real camera photoplethysmography (PPG): the fingertip covers the rear camera and torch, each frame's average
 * red/green brightness rises and falls with blood volume, and the beat rate is read from that signal.
 * It is an estimate (motion, pressure and lighting matter); readings with a weak signal are rejected, not guessed.
 */
class CameraPulseMeasurementService(
    private val dao: PulseDao
) : PulseMeasurementService {

    private class Sample(val tMs: Double, val red: Double, val green: Double)
    private class Estimate(val bpm: Int, val confidence: Float)

    private val _measuring = MutableStateFlow(false)
    override val isMeasuring: StateFlow<Boolean> = _measuring.asStateFlow()
    private val _liveBpm = MutableStateFlow<Int?>(null)
    override val liveBpm: StateFlow<Int?> = _liveBpm.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _error.asStateFlow()
    private val _progress = MutableStateFlow(0f)
    override val progress: StateFlow<Float> = _progress.asStateFlow()
    private val _hint = MutableStateFlow<String?>(null)
    override val hint: StateFlow<String?> = _hint.asStateFlow()

    private fun Context.lifecycleOwner(): LifecycleOwner? {
        var c: Context? = this
        while (c is ContextWrapper) { if (c is LifecycleOwner) return c; c = c.baseContext }
        return c as? LifecycleOwner
    }

    private suspend fun cameraProvider(ctx: Context): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val f = ProcessCameraProvider.getInstance(ctx)
        f.addListener({ runCatching { f.get() }.onSuccess { cont.resume(it) }.onFailure { cont.resumeWithException(it) } },
            ContextCompat.getMainExecutor(ctx))
    }

    override suspend fun startCameraMeasurement(context: Context): Result<PulseReadingEntity> {
        if (_measuring.value) return Result.failure(IllegalStateException("Already measuring"))
        val owner = context.lifecycleOwner() ?: return Result.failure(IllegalStateException("Camera is not available here"))
        _error.value = null; _liveBpm.value = null; _progress.value = 0f
        _measuring.value = true
        val samples = ArrayList<Sample>()
        val executor = Executors.newSingleThreadExecutor()
        var provider: ProcessCameraProvider? = null
        try {
            provider = cameraProvider(context)
            val analysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { img ->
                try {
                    val plane = img.planes[0]; val buf = plane.buffer
                    val rs = plane.rowStride; val ps = plane.pixelStride
                    val w = img.width; val h = img.height
                    var r = 0L; var g = 0L; var n = 0
                    for (y in h / 4 until 3 * h / 4 step 3) for (x in w / 4 until 3 * w / 4 step 3) {
                        val o = y * rs + x * ps
                        r += buf.get(o).toInt() and 0xFF; g += buf.get(o + 1).toInt() and 0xFF; n++
                    }
                    if (n > 0) synchronized(samples) {
                        samples += Sample(img.imageInfo.timestamp / 1_000_000.0, r.toDouble() / n, g.toDouble() / n)
                    }
                } finally { img.close() }
            }
            val camera = withContext(Dispatchers.Main) {
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
            }
            if (!camera.cameraInfo.hasFlashUnit()) throw IllegalStateException("This phone has no flash, which pulse measuring needs. You can enter a reading manually.")
            withContext(Dispatchers.Main) { camera.cameraControl.enableTorch(true) }

            val startedAt = System.currentTimeMillis()
            var goodSince = -1L
            var measuredFrom = -1.0
            while (true) {
                delay(500)
                val now = System.currentTimeMillis()
                val snap = synchronized(samples) { samples.toList() }
                val recent = snap.takeLast(10)
                val covered = recent.size >= 5 && recent.map { it.red }.average() > 100 &&
                    recent.map { it.red }.average() > 1.5 * recent.map { it.green }.average()
                if (!covered) {
                    // Restart the clock until the fingertip is properly placed.
                    goodSince = -1L; _progress.value = 0f; _liveBpm.value = null
                    _hint.value = if (snap.isEmpty()) "Starting camera…" else "Cover the rear camera AND flash fully with your fingertip, pressing lightly"
                    continue
                }
                if (goodSince < 0) { goodSince = now; measuredFrom = snap.last().tMs }
                _hint.value = "Hold still and keep your finger in place"
                val elapsed = now - goodSince
                _progress.value = (elapsed.toFloat() / DURATION_MS).coerceIn(0f, 1f)
                if (elapsed > 12_000) estimate(snap.filter { it.tMs >= measuredFrom }.takeLast(WINDOW_S * 30))?.let { _liveBpm.value = it.bpm }
                if (elapsed >= DURATION_MS) {
                    val result = estimate(snap.filter { it.tMs >= measuredFrom })
                    if (result == null || result.confidence < MIN_CONFIDENCE) {
                        throw IllegalStateException("The signal was too weak or unsteady to read. Keep still, press lightly, and try again.")
                    }
                    val reading = PulseReadingEntity(
                        bpm = result.bpm, measuredAt = System.currentTimeMillis(), source = PulseSource.CAMERA,
                        confidence = result.confidence, note = "Camera PPG estimate"
                    )
                    dao.insert(reading)
                    return Result.success(reading)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message ?: "Measurement failed"
            return Result.failure(e)
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                runCatching { provider?.unbindAll() } // also switches the torch off
            }
            executor.shutdown()
            _measuring.value = false; _liveBpm.value = null; _progress.value = 0f; _hint.value = null
        }
    }

    /** Resamples to 30 Hz, removes slow drift, smooths, finds beats and takes the median beat interval. */
    private fun estimate(raw: List<Sample>): Estimate? {
        if (raw.size < 60) return null
        val useGreen = raw.map { it.red }.average() > 250.0 // saturated red carries no pulse
        val t0 = raw.first().tMs; val t1 = raw.last().tMs
        if (t1 - t0 < 8_000) return null
        val n = ((t1 - t0) / 1000.0 * FS).toInt()
        val sig = DoubleArray(n)
        var j = 0
        for (i in 0 until n) {
            val t = t0 + i * 1000.0 / FS
            while (j < raw.size - 2 && raw[j + 1].tMs < t) j++
            val a = raw[j]; val b = raw[j + 1]
            val f = if (b.tMs == a.tMs) 0.0 else ((t - a.tMs) / (b.tMs - a.tMs)).coerceIn(0.0, 1.0)
            val va = if (useGreen) a.green else a.red; val vb = if (useGreen) b.green else b.red
            sig[i] = va + (vb - va) * f
        }
        val detr = DoubleArray(n) { i -> sig[i] - movingAvg(sig, i, 45) }   // ~1.5 s window: drops drift below ~40 bpm
        val smooth = DoubleArray(n) { i -> movingAvg(detr, i, 5) }          // drops noise above ~6 Hz
        val sd = sqrt(smooth.map { it * it }.average())
        if (sd < 0.05) return null // no pulsation at all
        val peaks = ArrayList<Int>()
        val minGap = (FS * 0.35).toInt()
        for (i in 2 until n - 2) {
            if (smooth[i] > 0.25 * sd && smooth[i] >= smooth[i - 1] && smooth[i] > smooth[i + 1] && smooth[i] >= smooth[i - 2] && smooth[i] > smooth[i + 2]) {
                if (peaks.isEmpty() || i - peaks.last() >= minGap) peaks += i
                else if (smooth[i] > smooth[peaks.last()]) peaks[peaks.size - 1] = i
            }
        }
        if (peaks.size < 8) return null
        val intervals = peaks.zipWithNext { a, b -> (b - a) / FS }.filter { it in 0.33..1.5 }
        if (intervals.size < 6) return null
        val median = intervals.sorted()[intervals.size / 2]
        val consistent = intervals.count { abs(it - median) <= 0.2 * median }.toFloat() / intervals.size
        val bpm = (60.0 / median).roundToInt().coerceIn(40, 200)
        val enough = (intervals.size / 15f).coerceAtMost(1f)
        return Estimate(bpm, (consistent * (0.5f + 0.5f * enough)).coerceIn(0f, 1f))
    }

    private fun movingAvg(a: DoubleArray, i: Int, w: Int): Double {
        val lo = maxOf(0, i - w / 2); val hi = minOf(a.size - 1, i + w / 2)
        var s = 0.0
        for (k in lo..hi) s += a[k]
        return s / (hi - lo + 1)
    }

    override suspend fun recordFromHealthPlatform(bpm: Int, measuredAt: Long): PulseReadingEntity {
        val reading = PulseReadingEntity(
            bpm = bpm.coerceIn(30, 220), measuredAt = measuredAt, source = PulseSource.HEALTH_PLATFORM,
            confidence = 0.9f, note = "Imported from health platform"
        )
        dao.insert(reading)
        return reading
    }

    override suspend fun recordManual(bpm: Int, note: String): PulseReadingEntity {
        val reading = PulseReadingEntity(
            bpm = bpm.coerceIn(30, 220), measuredAt = System.currentTimeMillis(), source = PulseSource.MANUAL,
            confidence = null, note = note
        )
        dao.insert(reading)
        return reading
    }

    override fun observeRecent(limit: Int) = dao.observeRecent(limit)

    override fun limitationsText(): String =
        "Camera pulse is a wellness estimate, not a medical measurement. It is affected by lighting, finger pressure, " +
            "movement and the phone. Do not use it for diagnosis or emergencies. Unclear readings are rejected rather than guessed."

    private companion object {
        const val FS = 30.0
        const val DURATION_MS = 30_000L
        const val WINDOW_S = 12
        const val MIN_CONFIDENCE = 0.6f
    }
}
