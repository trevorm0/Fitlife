package com.example.fitwell.features.coach

import com.example.fitwell.data.local.CoachDao
import com.example.fitwell.data.local.CoachSessionEntity
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Camera-based form coach, fully on-device:
 * CameraX frames -> ML Kit Pose Detection (body landmarks) -> [ExerciseTracker] (joint-angle rules, rep counting, cues).
 * Video is analysed frame by frame and never stored or uploaded. Never claims injury diagnosis or prevention.
 */
data class SupportedExercise(
    val key: String,
    val displayName: String,
    val cameraGuidance: String,
    val cues: List<String>
)

data class LiveCoachFeedback(
    val exerciseKey: String,
    val message: String,
    val severity: FeedbackSeverity,
    val confidence: Float,
    val repCount: Int,
    val holdSeconds: Int = 0
)

enum class FeedbackSeverity { INFO, GOOD, CORRECT, WARN }

interface PoseCoachService {
    val supportedExercises: List<SupportedExercise>
    val isAnalyzing: StateFlow<Boolean>
    val liveFeedback: StateFlow<LiveCoachFeedback?>
    val cameraHint: StateFlow<String>
    /** Latest body landmarks, for drawing the skeleton overlay. */
    val skeleton: StateFlow<Pose?>
    /** A configured CameraX analysis use case; the UI binds it to the camera together with a preview. */
    fun createAnalysisUseCase(): ImageAnalysis

    suspend fun startSession(exerciseKey: String): Result<Long> // sessionId
    suspend fun stopSession(sessionId: Long, userInitiated: Boolean = true)
    fun observeSessions(): Flow<List<CoachSessionEntity>>
}

object CoachExercises {
    val all: List<SupportedExercise> = listOf(
        SupportedExercise(
            "squat", "Bodyweight Squat",
            "Prop the phone 2–3 m away at hip height, stand side-on, whole body in frame.",
            listOf("Depth", "Chest up / torso lean")
        ),
        SupportedExercise(
            "pushup", "Push-up",
            "Phone on the floor 2 m to your side so your whole body is visible. Side view only.",
            listOf("Depth", "Body in a straight line")
        ),
        SupportedExercise(
            "plank", "Front Plank",
            "Phone on the floor 2 m to your side, whole body in frame. Hold time is counted while your body line is visible.",
            listOf("Hips level (no sag or pike)")
        ),
        SupportedExercise(
            "lunge", "Forward Lunge",
            "Side view, whole body in frame, with room to step. Step fully into frame.",
            listOf("Depth", "Upright torso")
        )
    )
}

class CoachRepository(
    private val dao: CoachDao,
    private val service: PoseCoachService
) {
    val supported = service.supportedExercises
    val isAnalyzing = service.isAnalyzing
    val liveFeedback = service.liveFeedback
    val cameraHint = service.cameraHint
    val skeleton = service.skeleton
    fun createAnalysis() = service.createAnalysisUseCase()
    fun observeSessions() = service.observeSessions()
    suspend fun start(exerciseKey: String) = service.startSession(exerciseKey)
    suspend fun stop(sessionId: Long, userInitiated: Boolean = true) =
        service.stopSession(sessionId, userInitiated)
}


/** Live pose coach. Needs the Camera permission, which the UI requests before starting a session. */
class MlKitPoseCoachService(
    private val dao: CoachDao
) : PoseCoachService {

    override val supportedExercises: List<SupportedExercise> = CoachExercises.all

    private val _analyzing = MutableStateFlow(false)
    override val isAnalyzing: StateFlow<Boolean> = _analyzing.asStateFlow()
    private val _feedback = MutableStateFlow<LiveCoachFeedback?>(null)
    override val liveFeedback: StateFlow<LiveCoachFeedback?> = _feedback.asStateFlow()
    private val _hint = MutableStateFlow("Position the camera so your full body is visible.")
    override val cameraHint: StateFlow<String> = _hint.asStateFlow()
    private val _skeleton = MutableStateFlow<Pose?>(null)
    override val skeleton: StateFlow<Pose?> = _skeleton.asStateFlow()

    private val executor = Executors.newSingleThreadExecutor()
    private val detector by lazy {
        PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
    }
    private val lock = Any()
    private var tracker: ExerciseTracker? = null
    private var activeSessionId: Long? = null

    private val landmarkMap = mapOf(
        "L_SHOULDER" to PoseLandmark.LEFT_SHOULDER, "R_SHOULDER" to PoseLandmark.RIGHT_SHOULDER,
        "L_ELBOW" to PoseLandmark.LEFT_ELBOW, "R_ELBOW" to PoseLandmark.RIGHT_ELBOW,
        "L_WRIST" to PoseLandmark.LEFT_WRIST, "R_WRIST" to PoseLandmark.RIGHT_WRIST,
        "L_HIP" to PoseLandmark.LEFT_HIP, "R_HIP" to PoseLandmark.RIGHT_HIP,
        "L_KNEE" to PoseLandmark.LEFT_KNEE, "R_KNEE" to PoseLandmark.RIGHT_KNEE,
        "L_ANKLE" to PoseLandmark.LEFT_ANKLE, "R_ANKLE" to PoseLandmark.RIGHT_ANKLE
    )

    override fun createAnalysisUseCase(): ImageAnalysis =
        ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().also { it.setAnalyzer(executor) { image -> analyze(image) } }

    private fun analyze(image: ImageProxy) {
        if (!_analyzing.value) { image.close(); return }
        val rotation = image.imageInfo.rotationDegrees
        val bitmap = try { image.toBitmap() } catch (e: Exception) { image.close(); return }
        val upright = rotation == 90 || rotation == 270
        val w = (if (upright) image.height else image.width).toFloat()
        val h = (if (upright) image.width else image.height).toFloat()
        // The frame is closed only when detection finishes, so slow phones drop frames instead of piling them up.
        detector.process(InputImage.fromBitmap(bitmap, rotation))
            .addOnSuccessListener { pose -> onPose(pose, w, h) }
            .addOnCompleteListener { image.close() }
    }

    private fun onPose(pose: com.google.mlkit.vision.pose.Pose, w: Float, h: Float) {
        val pts = HashMap<String, Pt>()
        for ((key, type) in landmarkMap) {
            pose.getPoseLandmark(type)?.let { pts[key] = Pt(it.position.x / w, it.position.y / h, it.inFrameLikelihood) }
        }
        val frame = Pose(pts, w / h)
        _skeleton.value = frame
        synchronized(lock) {
            val t = tracker ?: return
            val u = t.update(frame)
            val ex = supportedExercises.first { it.key == t.exerciseKey }
            _hint.value = if (u.tracking) ex.cameraGuidance else u.message
            _feedback.value = LiveCoachFeedback(t.exerciseKey, u.message, u.severity, u.confidence, u.reps, u.holdSeconds)
        }
    }

    override suspend fun startSession(exerciseKey: String): Result<Long> {
        if (_analyzing.value) return Result.failure(IllegalStateException("Session already active"))
        val ex = supportedExercises.find { it.key == exerciseKey }
            ?: return Result.failure(IllegalArgumentException("Unsupported exercise"))
        val id = dao.insert(CoachSessionEntity(exerciseKey = exerciseKey, exerciseName = ex.displayName, startedAt = System.currentTimeMillis()))
        synchronized(lock) { tracker = ExerciseTracker(exerciseKey) }
        activeSessionId = id
        _hint.value = ex.cameraGuidance
        _feedback.value = null
        _analyzing.value = true
        return Result.success(id)
    }

    override suspend fun stopSession(sessionId: Long, userInitiated: Boolean) {
        _analyzing.value = false
        val (reps, score, summary) = synchronized(lock) {
            val t = tracker
            tracker = null
            Triple(t?.reps ?: 0, t?.formScore(), t?.summary() ?: "Session ended early.")
        }
        _feedback.value = null
        _skeleton.value = null
        dao.finish(
            id = sessionId, ended = System.currentTimeMillis(), reps = reps, score = score,
            summary = summary, stopped = userInitiated
        )
        activeSessionId = null
    }

    override fun observeSessions(): Flow<List<CoachSessionEntity>> = dao.observeSessions()
}
