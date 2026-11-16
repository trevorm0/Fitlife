package com.example.fitwell.features.coach

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A body landmark in the upright camera frame: x,y in 0..1 (y grows downward), v = how sure the model is it is in frame. */
data class Pt(val x: Float, val y: Float, val v: Float)

/** Landmarks for one frame. Keys are "L_SHOULDER", "R_KNEE", ... [aspect] = frame width / height, needed for true angles. */
class Pose(val points: Map<String, Pt>, val aspect: Float)

data class TrackerUpdate(
    val message: String,
    val severity: FeedbackSeverity,
    val reps: Int,
    val holdSeconds: Int,
    val confidence: Float,
    val tracking: Boolean
)

/**
 * Rule-based form checking from joint angles. No Android code, so it is unit-tested with synthetic poses.
 *
 * What it can and cannot do: it measures angles in a 2D camera view, so it works best from the side with the whole body
 * visible. It gives coaching cues (depth, torso lean, body line), not injury diagnosis, and it says so when it cannot see you.
 */
class ExerciseTracker(
    val exerciseKey: String,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    var reps = 0; private set
    val holdSeconds: Int get() = (holdMs / 1000).toInt()

    private var holdMs = 0L
    private var goodMs = 0L
    private var trackedMs = 0L
    private var lastT = -1L

    private var inRep = false
    private var minAngle = 180f
    private var worstLean = 0f
    private var worstLine = 180f
    private var lineSagging = false
    private val smoothed = HashMap<String, Float>()
    private val repScores = ArrayList<Float>()
    private val issues = HashMap<String, Int>()

    private var message = "Get into position. I'll start when I can see you."
    private var severity = FeedbackSeverity.INFO
    private var messageUntil = 0L

    private fun say(text: String, sev: FeedbackSeverity, ms: Long = 2500) {
        message = text; severity = sev; messageUntil = clock() + ms
    }

    private fun smooth(name: String, value: Float): Float {
        val prev = smoothed[name] ?: value
        return (prev + 0.4f * (value - prev)).also { smoothed[name] = it }
    }

    fun update(pose: Pose): TrackerUpdate {
        val now = clock()
        val asp = pose.aspect
        val side = pickSide(pose)
        val required = when (exerciseKey) {
            "pushup" -> listOf("SHOULDER", "ELBOW", "WRIST", "HIP", "ANKLE")
            "plank" -> listOf("SHOULDER", "HIP", "ANKLE")
            "lunge" -> listOf("HIP", "KNEE", "ANKLE")
            else -> listOf("SHOULDER", "HIP", "KNEE", "ANKLE")
        }
        val usable = if (exerciseKey == "lunge") lungeSides(pose).isNotEmpty() else side != null
        val pts = if (side != null) required.associateWith { pose.points[side + it]?.takeIf { p -> p.v >= MIN_V } } else emptyMap()
        if (!usable || (exerciseKey != "lunge" && pts.values.any { it == null })) {
            lastT = -1L
            return TrackerUpdate("I can't see your whole body. Step back and turn side-on.", FeedbackSeverity.WARN, reps, holdSeconds, 0f, false)
        }
        val dt = if (lastT < 0) 0L else min(now - lastT, 500L)
        lastT = now

        when (exerciseKey) {
            "squat" -> squat(pts, asp)
            "pushup" -> pushup(pts, asp)
            "plank" -> plank(pts, asp, dt)
            "lunge" -> lunge(pose, side, asp)
        }
        val conf = (if (exerciseKey == "lunge") lungeSides(pose).flatMap { s -> listOf("HIP", "KNEE", "ANKLE").mapNotNull { pose.points[s + it]?.v } }
        else pts.values.filterNotNull().map { it.v }).average().toFloat()
        val live = if (now < messageUntil || exerciseKey == "plank") message else idleCue()
        val sev = if (now < messageUntil || exerciseKey == "plank") severity else FeedbackSeverity.INFO
        return TrackerUpdate(live, sev, reps, holdSeconds, conf, true)
    }

    private fun idleCue() = if (inRep) "Good — keep going" else "Ready. Start your next rep."

    // ---- exercises ----
    private fun squat(p: Map<String, Pt?>, asp: Float) {
        val knee = smooth("knee", angle(p["HIP"]!!, p["KNEE"]!!, p["ANKLE"]!!, asp))
        val lean = trunkLean(p["SHOULDER"]!!, p["HIP"]!!, asp)
        if (!inRep) {
            if (knee < 140f) { inRep = true; minAngle = knee; worstLean = lean }
        } else {
            minAngle = min(minAngle, knee); worstLean = max(worstLean, lean)
            if (knee > 160f) {
                inRep = false
                if (minAngle > 125f) { say("Go a little lower to count a rep", FeedbackSeverity.CORRECT); return }
                var score = 1f
                val notes = ArrayList<String>()
                if (minAngle > 100f) { score -= 0.15f; notes += "Good — go a little deeper if comfortable"; issue("depth") }
                if (worstLean > 55f) { score -= 0.25f; notes += "Keep your chest up"; issue("torso lean") }
                finishRep(score, notes.firstOrNull() ?: "Great rep — good depth", notes.isEmpty())
            }
        }
    }

    private fun pushup(p: Map<String, Pt?>, asp: Float) {
        val s = p["SHOULDER"]!!; val a = p["ANKLE"]!!
        if (abs(s.x - a.x) * asp < abs(s.y - a.y)) {
            say("Push-ups need a side view with your body horizontal", FeedbackSeverity.WARN); return
        }
        val elbow = smooth("elbow", angle(s, p["ELBOW"]!!, p["WRIST"]!!, asp))
        val line = smooth("line", angle(s, p["HIP"]!!, a, asp))
        val sag = isSagging(s, p["HIP"]!!, a)
        if (!inRep) {
            if (elbow < 130f) { inRep = true; minAngle = elbow; worstLine = line; lineSagging = sag }
        } else {
            minAngle = min(minAngle, elbow)
            if (line < worstLine) { worstLine = line; lineSagging = sag }
            if (elbow > 150f) {
                inRep = false
                if (minAngle > 100f) { say("Lower until your chest is near the floor", FeedbackSeverity.CORRECT); return }
                var score = 1f
                var note: String? = null
                if (worstLine < 160f) {
                    score -= 0.3f; issue("body line")
                    note = if (lineSagging) "Hips are sagging — squeeze your core" else "Hips are high — lower them in line"
                }
                finishRep(score, note ?: "Strong rep — body stayed in line", note == null)
            }
        }
    }

    private fun plank(p: Map<String, Pt?>, asp: Float, dt: Long) {
        val s = p["SHOULDER"]!!; val h = p["HIP"]!!; val a = p["ANKLE"]!!
        if (abs(s.x - a.x) * asp < abs(s.y - a.y)) { say("Plank needs a side view with your body horizontal", FeedbackSeverity.WARN, 800); return }
        val line = smooth("line", angle(s, h, a, asp))
        holdMs += dt; trackedMs += dt
        when {
            line >= 165f -> { goodMs += dt; say("Strong line — keep breathing", FeedbackSeverity.GOOD, 800) }
            line >= 150f -> say("Slightly off line — adjust your hips", FeedbackSeverity.CORRECT, 800)
            else -> { issue("body line"); say(if (isSagging(s, h, a)) "Hips are sagging — lift them" else "Hips are too high — lower them", FeedbackSeverity.WARN, 800) }
        }
    }

    private fun lunge(pose: Pose, side: String?, asp: Float) {
        val knees = lungeSides(pose).map { s -> angle(pose.points[s + "HIP"]!!, pose.points[s + "KNEE"]!!, pose.points[s + "ANKLE"]!!, asp) }
        val knee = smooth("knee", knees.min())
        val lean = side?.let { sd ->
            val sh = pose.points[sd + "SHOULDER"]; val hp = pose.points[sd + "HIP"]
            if (sh != null && hp != null && sh.v >= MIN_V && hp.v >= MIN_V) trunkLean(sh, hp, asp) else 0f
        } ?: 0f
        if (!inRep) {
            if (knee < 130f) { inRep = true; minAngle = knee; worstLean = lean }
        } else {
            minAngle = min(minAngle, knee); worstLean = max(worstLean, lean)
            if (knee > 155f) {
                inRep = false
                if (minAngle > 110f) { say("Lower a little more to count a rep", FeedbackSeverity.CORRECT); return }
                var score = 1f
                var note: String? = null
                if (worstLean > 25f) { score -= 0.25f; note = "Keep your torso upright"; issue("torso lean") }
                finishRep(score, note ?: "Nice lunge — controlled and upright", note == null)
            }
        }
    }

    // ---- helpers ----
    private fun finishRep(score: Float, text: String, good: Boolean) {
        reps++
        repScores += score.coerceIn(0f, 1f)
        say("Rep $reps: $text", if (good) FeedbackSeverity.GOOD else FeedbackSeverity.CORRECT)
    }

    private fun issue(name: String) { issues[name] = (issues[name] ?: 0) + 1 }

    private fun lungeSides(pose: Pose): List<String> = listOf("L_", "R_").filter { s ->
        listOf("HIP", "KNEE", "ANKLE").all { (pose.points[s + it]?.v ?: 0f) >= MIN_V }
    }

    /** The side of the body the camera sees best (the near side when you stand side-on). */
    private fun pickSide(pose: Pose): String? {
        fun vis(side: String) = listOf("SHOULDER", "ELBOW", "WRIST", "HIP", "KNEE", "ANKLE").map { pose.points[side + it]?.v ?: 0f }.average()
        val l = vis("L_"); val r = vis("R_")
        return when { l == 0.0 && r == 0.0 -> null; l >= r -> "L_"; else -> "R_" }
    }

    /** Hip below the shoulder-ankle line means sagging; above means piked. */
    private fun isSagging(s: Pt, h: Pt, a: Pt): Boolean {
        val dx = a.x - s.x
        if (abs(dx) < 1e-4f) return false
        val t = (h.x - s.x) / dx
        return h.y > s.y + t * (a.y - s.y)
    }

    fun formScore(): Float? = when {
        exerciseKey == "plank" -> if (trackedMs >= 3000) goodMs.toFloat() / trackedMs else null
        repScores.isEmpty() -> null
        else -> repScores.average().toFloat()
    }

    fun summary(): String {
        val common = issues.maxByOrNull { it.value }?.key
        return when (exerciseKey) {
            "plank" -> if (trackedMs < 3000) "Session ended early. Resume whenever you like — no pressure."
            else "Held about ${holdSeconds}s with a good body line ${(100 * goodMs / trackedMs.coerceAtLeast(1))}% of the time." +
                (common?.let { " Watch your $it next time." } ?: "")
            else -> when {
                reps == 0 -> "Session ended early. Resume whenever you like — no pressure."
                else -> "$reps reps counted." + (common?.let { " Most common cue: $it." } ?: " Form looked consistent.")
            }
        }
    }

    companion object {
        const val MIN_V = 0.5f

        /** Interior angle at [b] formed by a-b-c, in degrees, in true pixel proportions. */
        fun angle(a: Pt, b: Pt, c: Pt, aspect: Float): Float {
            val ax = (a.x - b.x) * aspect; val ay = a.y - b.y
            val cx = (c.x - b.x) * aspect; val cy = c.y - b.y
            val m = hypot(ax, ay) * hypot(cx, cy)
            if (m < 1e-6f) return 180f
            return Math.toDegrees(acos(((ax * cx + ay * cy) / m).toDouble().coerceIn(-1.0, 1.0))).toFloat()
        }

        /** Degrees the torso (hip -> shoulder) leans from straight up. */
        fun trunkLean(shoulder: Pt, hip: Pt, aspect: Float): Float {
            val dx = (shoulder.x - hip.x) * aspect; val dy = shoulder.y - hip.y
            return Math.toDegrees(atan2(abs(dx).toDouble(), (-dy).toDouble())).toFloat()
        }
    }
}
