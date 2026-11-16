package com.example.fitwell.ui.screens.features

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitwell.data.local.LeagueScoreMode
import com.example.fitwell.data.local.MealCategory
import com.example.fitwell.features.coach.FeedbackSeverity
import com.example.fitwell.features.mealanalysis.RecognizedFoodItem
import com.example.fitwell.viewmodel.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PulseScreen(vm: PulseViewModel = viewModel()) {
    val recent by vm.recent.collectAsStateWithLifecycle()
    val measuring by vm.isMeasuring.collectAsStateWithLifecycle()
    val live by vm.liveBpm.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val hint by vm.hint.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.measure(context) else vm.setStatus("Camera permission is needed to measure your pulse. You can still enter a reading manually.") }
    var manual by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Pulse", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Wellness estimate only — not a medical device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        live?.let { "$it bpm" } ?: "— bpm",
                        style = MaterialTheme.typography.displaySmall
                    )
                    if (measuring) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                                    android.content.pm.PackageManager.PERMISSION_GRANTED) vm.measure(context)
                                else cameraLauncher.launch(android.Manifest.permission.CAMERA)
                            },
                            enabled = !measuring
                        ) { Text(if (measuring) "Measuring…" else "Measure (camera)") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = manual,
                            onValueChange = { manual = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text("Manual BPM") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            manual.toIntOrNull()?.let { vm.recordManual(it); manual = "" }
                        }) { Text("Save") }
                    }
                }
            }
        }
        item {
            Text(vm.limitations, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Text("Recent readings", style = MaterialTheme.typography.titleMedium) }
        items(recent) { r ->
            ListItem(
                headlineContent = { Text("${r.bpm} bpm · ${r.source.label}") },
                supportingContent = {
                    Text(
                        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(r.measuredAt)) +
                            (r.confidence?.let { " · conf ${(it * 100).toInt()}%" } ?: "")
                    )
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun StepsScreen(vm: StepsViewModel = viewModel()) {
    val steps by vm.todaySteps.collectAsStateWithLifecycle()
    val entity by vm.todayEntity.collectAsStateWithLifecycle()
    val week by vm.week.collectAsStateWithLifecycle()
    val available by vm.sensorAvailable.collectAsStateWithLifecycle()
    val target = entity?.targetSteps ?: 8000
    var targetInput by remember { mutableStateOf("") }
    val needsPermission by vm.permissionNeeded.collectAsStateWithLifecycle()
    val stepsContext = LocalContext.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { vm.startSensor(stepsContext) }
    LaunchedEffect(Unit) { vm.startSensor(stepsContext) } // registers right away if already allowed

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Steps", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (available) "Using device step sensor when available."
                else "Step sensor not detected — you can log steps manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (needsPermission) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Allow step counting", style = MaterialTheme.typography.titleMedium)
                    Text("Android needs your permission (Physical activity) before FitLife can count steps. Until then you can still add steps manually.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { permissionLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION) }) { Text("Allow") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$steps", style = MaterialTheme.typography.displaySmall)
                    Text("of $target steps today", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(
                        progress = { (steps.toFloat() / target).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.addManual(500) }) { Text("+500") }
                        OutlinedButton(onClick = { vm.addManual(1000) }) { Text("+1000") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = targetInput,
                            onValueChange = { targetInput = it.filter { c -> c.isDigit() }.take(5) },
                            label = { Text("New daily target") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            targetInput.toIntOrNull()?.let { vm.setTarget(it); targetInput = "" }
                        }) { Text("Set") }
                    }
                }
            }
        }
        item { Text("This week", style = MaterialTheme.typography.titleMedium) }
        items(week) { d ->
            val label = SimpleDateFormat("EEE MMM d", Locale.getDefault()).format(Date(d.dayStartMs))
            ListItem(
                headlineContent = { Text(label) },
                supportingContent = { Text("${d.steps} / ${d.targetSteps} · ${d.source}") },
                trailingContent = {
                    Text("${((d.steps.toFloat() / d.targetSteps) * 100).toInt().coerceAtMost(999)}%")
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun CoachScreen(vm: CoachViewModel = viewModel()) {
    val analyzing by vm.isAnalyzing.collectAsStateWithLifecycle()
    val feedback by vm.liveFeedback.collectAsStateWithLifecycle()
    val hint by vm.cameraHint.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingKey by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val cameraPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingKey?.let { vm.start(it) } else message = "Camera permission is needed so the coach can see your form. Nothing is recorded or uploaded."
    }
    fun begin(key: String) {
        message = null
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) vm.start(key)
        else { pendingKey = key; cameraPermission.launch(android.Manifest.permission.CAMERA) }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Form coach", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Your camera is analysed on this phone, frame by frame. Nothing is recorded or uploaded. " +
                    "Cues are assistive only: this does not diagnose injuries or guarantee safety. Stop anytime, and stop if anything hurts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        if (!analyzing) {
            item { Text("Choose an exercise", style = MaterialTheme.typography.titleMedium) }
            items(vm.exercises) { ex ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(ex.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(ex.cameraGuidance, style = MaterialTheme.typography.bodySmall)
                        Text("Checks: " + ex.cues.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { begin(ex.key) }) { Text("Start analysis") }
                    }
                }
            }
        } else {
            item {
                CoachCameraView(vm, Modifier.fillMaxWidth().aspectRatio(3f / 4f))
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        feedback?.let { fb ->
                            val color = when (fb.severity) {
                                FeedbackSeverity.GOOD, FeedbackSeverity.INFO -> MaterialTheme.colorScheme.primary
                                FeedbackSeverity.CORRECT -> MaterialTheme.colorScheme.tertiary
                                FeedbackSeverity.WARN -> MaterialTheme.colorScheme.error
                            }
                            Text(fb.message, color = color, style = MaterialTheme.typography.titleMedium)
                            Text(
                                (if (fb.exerciseKey == "plank") "Held: ${fb.holdSeconds}s" else "Reps: ${fb.repCount}") +
                                    " · tracking ${(fb.confidence * 100).toInt()}%"
                            )
                        } ?: Text(hint, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { vm.stop(true) }) { Text("Stop analysis") }
                    }
                }
            }
        }
        item { Text("Past sessions", style = MaterialTheme.typography.titleMedium) }
        items(sessions.take(15)) { s ->
            ListItem(
                headlineContent = { Text(s.exerciseName) },
                supportingContent = {
                    Text(
                        (if (s.exerciseKey != "plank") "${s.repsDetected} reps · " else "") +
                            (s.formScore?.let { "form ${(it * 100).toInt()}% · " } ?: "") +
                            s.feedbackSummary
                    )
                }
            )
            HorizontalDivider()
        }
    }
}

private val COACH_BONES = listOf(
    "L_SHOULDER" to "R_SHOULDER", "L_SHOULDER" to "L_ELBOW", "L_ELBOW" to "L_WRIST",
    "R_SHOULDER" to "R_ELBOW", "R_ELBOW" to "R_WRIST", "L_SHOULDER" to "L_HIP", "R_SHOULDER" to "R_HIP",
    "L_HIP" to "R_HIP", "L_HIP" to "L_KNEE", "L_KNEE" to "L_ANKLE", "R_HIP" to "R_KNEE", "R_KNEE" to "R_ANKLE"
)

/** Live camera preview with the detected skeleton drawn on top. The camera is released when this leaves the screen. */
@Composable
private fun CoachCameraView(vm: CoachViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = remember(context) {
        var c: android.content.Context? = context
        var found: androidx.lifecycle.LifecycleOwner? = null
        while (c is android.content.ContextWrapper) {
            if (c is androidx.lifecycle.LifecycleOwner) { found = c; break }
            c = c.baseContext
        }
        found
    }
    val skeleton by vm.skeleton.collectAsStateWithLifecycle()
    var front by remember { mutableStateOf(true) }
    var provider by remember { mutableStateOf<androidx.camera.lifecycle.ProcessCameraProvider?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val previewView = remember {
        androidx.camera.view.PreviewView(context).apply {
            scaleType = androidx.camera.view.PreviewView.ScaleType.FIT_CENTER
            keepScreenOn = true
        }
    }

    LaunchedEffect(front, owner) {
        val lifecycleOwner = owner ?: run { cameraError = "Camera is not available here."; return@LaunchedEffect }
        try {
            val p = kotlinx.coroutines.suspendCancellableCoroutine<androidx.camera.lifecycle.ProcessCameraProvider> { cont ->
                val f = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
                f.addListener({
                    runCatching { f.get() }
                        .onSuccess { cont.resume(it) {} }
                        .onFailure { cont.resumeWith(Result.failure(it)) }
                }, androidx.core.content.ContextCompat.getMainExecutor(context))
            }
            provider = p
            val preview = androidx.camera.core.Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            p.unbindAll()
            p.bindToLifecycle(
                lifecycleOwner,
                if (front) androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA else androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                preview, vm.createAnalysis()
            )
            cameraError = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            cameraError = "Could not start the camera. Close other apps using it and try again."
        }
    }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll() } }

    Box(modifier.background(androidx.compose.ui.graphics.Color.Black)) {
        androidx.compose.ui.viewinterop.AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val sk = skeleton ?: return@Canvas
            // Same letterboxing as the FIT_CENTER preview, so the skeleton lines up with the video.
            val boxW = size.width; val boxH = size.height
            val drawW = if (boxW / boxH > sk.aspect) boxH * sk.aspect else boxW
            val drawH = if (boxW / boxH > sk.aspect) boxH else boxW / sk.aspect
            val ox = (boxW - drawW) / 2f; val oy = (boxH - drawH) / 2f
            fun at(p: com.example.fitwell.features.coach.Pt) =
                androidx.compose.ui.geometry.Offset(ox + (if (front) 1f - p.x else p.x) * drawW, oy + p.y * drawH)
            COACH_BONES.forEach { (a, b) ->
                val p1 = sk.points[a]; val p2 = sk.points[b]
                if (p1 != null && p2 != null && p1.v >= 0.5f && p2.v >= 0.5f)
                    drawLine(androidx.compose.ui.graphics.Color(0xFF4ADE80), at(p1), at(p2), strokeWidth = 6f)
            }
            sk.points.values.filter { it.v >= 0.5f }.forEach { drawCircle(androidx.compose.ui.graphics.Color.White, 8f, at(it)) }
        }
        IconButton(onClick = { front = !front }, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Default.FlipCameraAndroid, "Switch camera", tint = androidx.compose.ui.graphics.Color.White)
        }
        cameraError?.let {
            Text(it, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.align(Alignment.Center).padding(16.dp))
        }
    }
}

@Composable
fun LeaguesScreen(vm: LeagueViewModel = viewModel()) {
    val leagues by vm.leagues.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(LeagueScoreMode.CONSISTENCY) }
    var selectedLeague by remember { mutableStateOf<Long?>(null) }

    if (selectedLeague != null) {
        LeagueDetailScreen(leagueId = selectedLeague!!, onBack = { selectedLeague = null }, vm = vm)
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Fitness leagues", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Invite-only. You control what stats members can see. Optional and beginner-friendly scoring available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Create private league", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    Text("Scoring", style = MaterialTheme.typography.labelLarge)
                    LeagueScoreMode.entries.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode == m, onClick = { mode = m })
                            Text(m.label)
                        }
                    }
                    Button(onClick = { vm.create(name, mode); name = "" }) { Text("Create") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Join with invite code", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = code, onValueChange = { code = it.uppercase() }, label = { Text("Code") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { vm.join(code); code = "" }) { Text("Join") }
                }
            }
        }
        item { Text("Your leagues", style = MaterialTheme.typography.titleMedium) }
        items(leagues) { l ->
            Card(
                onClick = { selectedLeague = l.leagueId },
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text(l.name) },
                    supportingContent = { Text("Code: ${l.inviteCode} · ${l.scoreMode.label}") },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) }
                )
            }
        }
    }
}

@Composable
fun LeagueDetailScreen(leagueId: Long, onBack: () -> Unit, vm: LeagueViewModel) {
    val members by vm.members(leagueId).collectAsStateWithLifecycle(emptyList())
    val challenges by vm.challenges(leagueId).collectAsStateWithLifecycle(emptyList())
    val activeChallenge = challenges.firstOrNull()
    val scores by if (activeChallenge != null) {
        vm.scores(activeChallenge.challengeId).collectAsStateWithLifecycle(emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("League", style = MaterialTheme.typography.headlineSmall)
        }
        item { Text("Members", style = MaterialTheme.typography.titleMedium) }
        items(members) { m ->
            ListItem(
                headlineContent = { Text(m.displayName) },
                supportingContent = {
                    Text(
                        buildString {
                            if (m.shareSteps) append("steps ")
                            if (m.shareWorkouts) append("workouts ")
                            if (m.sharePulse) append("pulse")
                            if (isEmpty()) append("limited sharing")
                        }
                    )
                }
            )
        }
        item { Text("Leaderboard (current week)", style = MaterialTheme.typography.titleMedium) }
        items(scores) { s ->
            ListItem(
                headlineContent = { Text(s.memberUserId) },
                supportingContent = { Text(s.detail) },
                trailingContent = { Text("${s.points} pts") }
            )
        }
        item {
            OutlinedButton(onClick = { vm.leave(leagueId); onBack() }) { Text("Leave league") }
        }
    }
}

@Composable
fun MealPhotoScreen(vm: MealPhotoViewModel = viewModel()) {
    val result by vm.result.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val photo by vm.photo.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var mealName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MealCategory.LUNCH) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val takePicture = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) pendingUri?.let { vm.analyze(it) } }

    fun launchCamera() {
        val dir = java.io.File(context.cacheDir, "meal_photos").apply { mkdirs() }
        val file = java.io.File.createTempFile("meal_", ".jpg", dir)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        pendingUri = uri
        takePicture.launch(uri)
    }

    val cameraPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCamera() else message = "Camera permission is needed to take a photo. You can choose one from your gallery instead." }

    val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.analyze(uri) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Meal photo analysis", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Photos are analysed on your phone and are not uploaded or kept. Results are estimates: " +
                    "the on-device model gives general food guesses, so always check and correct them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        message = null
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED) launchCamera()
                        else cameraPermission.launch(android.Manifest.permission.CAMERA)
                    },
                    enabled = !busy, modifier = Modifier.weight(1f)
                ) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Take photo") }
                OutlinedButton(
                    onClick = {
                        message = null
                        pickPhoto.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !busy, modifier = Modifier.weight(1f)
                ) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Gallery") }
            }
            TextButton(onClick = { vm.startManual() }, enabled = !busy) { Text("Or add foods without a photo") }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                Text("Analysing photo…", style = MaterialTheme.typography.bodySmall)
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        result?.let { r ->
            photo?.let { uri ->
                item {
                    coil.compose.AsyncImage(
                        model = uri, contentDescription = "Your meal photo",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "~${r.totalCalories} kcal · P ${r.totalProteinG}g · C ${r.totalCarbsG}g · F ${r.totalFatG}g",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (r.items.isNotEmpty()) Text(
                            "Overall confidence ${(r.overallConfidence * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(r.uncertaintyNotes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            itemsIndexed(r.items) { index, item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${item.name} (${item.portionLabel})", style = MaterialTheme.typography.titleSmall)
                                Text("${item.calories} kcal · P ${item.proteinG} · C ${item.carbsG} · F ${item.fatG}" +
                                    if (item.confidence < 1f) " · guess ${(item.confidence * 100).toInt()}%" else " · added by you",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { vm.removeItem(index) }) { Icon(Icons.Default.Close, "Remove ${item.name}") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.scaleItem(index, 0.8) }) { Text("− portion") }
                            OutlinedButton(onClick = { vm.scaleItem(index, 1.25) }) { Text("+ portion") }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add a food")
                }
            }
            item {
                OutlinedTextField(
                    value = mealName,
                    onValueChange = { mealName = it },
                    label = { Text("Meal name (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MealCategory.entries.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                    }
                }
                Button(
                    onClick = { vm.confirm(mealName, category); mealName = "" },
                    enabled = r.items.isNotEmpty()
                ) { Text("Save to nutrition log") }
                TextButton(onClick = { vm.clear() }) { Text("Discard") }
            }
        }
        item { Text("Recent analyses", style = MaterialTheme.typography.titleMedium) }
        items(history.take(10)) { a ->
            ListItem(
                headlineContent = { Text("~${a.estimatedCalories} kcal") },
                supportingContent = {
                    Text(
                        SimpleDateFormat("MMM d HH:mm", Locale.getDefault()).format(Date(a.createdAt)) +
                            " · conf ${(a.overallConfidence * 100).toInt()}%" +
                            if (a.userCorrected) " · corrected" else ""
                    )
                }
            )
            HorizontalDivider()
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add a food") },
            text = {
                LazyColumn {
                    items(vm.catalog) { f ->
                        ListItem(
                            headlineContent = { Text(f.name) },
                            supportingContent = { Text("${f.serving} · ${f.cal} kcal") },
                            modifier = Modifier.clickable { vm.addFood(f.name); showAdd = false }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AdaptiveDashboardScreen(
    pulseVm: PulseViewModel = viewModel(),
    stepsVm: StepsViewModel = viewModel(),
    adaptiveVm: AdaptiveViewModel = viewModel()
) {
    val plan by adaptiveVm.plan.collectAsStateWithLifecycle()
    val steps by stepsVm.todaySteps.collectAsStateWithLifecycle()
    val pulse by pulseVm.recent.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { adaptiveVm.refresh() }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Adaptive wellness", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Recommendations adapt to your activity, meals and goals. Missed days are fine — just continue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Steps", style = MaterialTheme.typography.labelLarge)
                        Text("$steps", style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Last pulse", style = MaterialTheme.typography.labelLarge)
                        Text(pulse.firstOrNull()?.let { "${it.bpm}" } ?: "—", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
        item {
            Button(onClick = { adaptiveVm.refresh() }, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh recommendations")
            }
        }
        plan?.let { p ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(p.title, style = MaterialTheme.typography.titleLarge)
                        Text(p.explanation)
                        p.suggestedStepsTarget?.let { Text("Suggested steps: $it") }
                        p.suggestedWorkoutMinutes?.let { Text("Suggested workout: ~$it min") }
                        p.suggestedCalorieTarget?.let { Text("Calorie target: $it") }
                        Text(
                            "Based on: ${p.basedOnSummary}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            Text(
                "Not medical advice. Measured data, imports and AI estimates are labeled in each feature.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
