package com.example.fitwell.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitwell.FitLifeApp
import com.example.fitwell.data.local.LeagueScoreMode
import com.example.fitwell.data.local.MealCategory
import com.example.fitwell.features.adaptive.AdaptivePlan
import com.example.fitwell.features.adaptive.WellnessSnapshot
import com.example.fitwell.features.mealanalysis.FoodMapper
import com.example.fitwell.features.mealanalysis.MealAnalysisResult
import com.example.fitwell.features.mealanalysis.RecognizedFoodItem
import com.example.fitwell.util.startOfDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PulseViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FitLifeApp).pulseRepo
    val recent = repo.observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val isMeasuring = repo.measurement.isMeasuring
    val liveBpm = repo.measurement.liveBpm
    val progress = repo.measurement.progress
    val hint = repo.measurement.hint
    val limitations = repo.limitations()

    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()

    fun measure(context: android.content.Context) {
        viewModelScope.launch {
            _status.value = "Measuring…"
            repo.measureWithCamera(context)
                .onSuccess { _status.value = "Recorded ${it.bpm} bpm" }
                .onFailure { _status.value = it.message ?: "Failed" }
        }
    }

    fun setStatus(message: String) { _status.value = message }

    fun recordManual(bpm: Int) {
        viewModelScope.launch {
            repo.recordManual(bpm)
            _status.value = "Saved $bpm bpm"
        }
    }
}

class StepsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FitLifeApp).stepsRepo
    val todaySteps = repo.todaySteps
    val sensorAvailable = repo.sensorAvailable
    val permissionNeeded = repo.permissionNeeded
    fun startSensor(context: android.content.Context) = repo.start(context)
    private val day = startOfDay(System.currentTimeMillis())
    val todayEntity = repo.observeDay(day).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val week = repo.observeWeek(day - 6 * 24 * 60 * 60 * 1000L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTarget(target: Int) {
        viewModelScope.launch { repo.setTarget(day, target) }
    }

    fun addManual(delta: Int) {
        viewModelScope.launch { repo.addManual(day, delta) }
    }
}

class CoachViewModel(private val app: Application) : AndroidViewModel(app) {
    private val fitApp = app as FitLifeApp
    private val repo = fitApp.coachRepo
    val exercises = repo.supported
    val isAnalyzing = repo.isAnalyzing
    val liveFeedback = repo.liveFeedback
    val cameraHint = repo.cameraHint
    val skeleton = repo.skeleton
    val sessions = repo.observeSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId = _activeSessionId.asStateFlow()

    fun createAnalysis() = repo.createAnalysis()

    fun start(key: String) {
        viewModelScope.launch {
            repo.start(key).onSuccess { _activeSessionId.value = it }
        }
    }

    fun stop(userInitiated: Boolean = true) {
        val id = _activeSessionId.value ?: return
        _activeSessionId.value = null
        // App scope, so the session is still saved if this screen is leaving.
        fitApp.appScope.launch { repo.stop(id, userInitiated) }
    }

    /** Leaving the screen ends the camera session (the camera is released by the screen itself). */
    override fun onCleared() { stop(userInitiated = false) }
}

class LeagueViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FitLifeApp).leagueRepo
    private val profileRepo = (app as FitLifeApp).profileRepo
    val leagues = repo.observeLeagues().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val localUserId = (app as FitLifeApp).currentUserId

    fun create(name: String, mode: LeagueScoreMode) {
        viewModelScope.launch {
            val profile = profileRepo.observe().first()
            val display = profile?.displayName ?: "You"
            val id = repo.createLeague(name, localUserId, display, mode)
            _message.value = "League created. Share the invite code from league details."
        }
    }

    fun join(code: String) {
        viewModelScope.launch {
            val profile = profileRepo.observe().first()
            val display = profile?.displayName ?: "You"
            repo.joinByCode(code, localUserId, display)
                .onSuccess { _message.value = "Joined league" }
                .onFailure { _message.value = it.message }
        }
    }

    fun leave(leagueId: Long) {
        viewModelScope.launch { repo.leave(leagueId, localUserId) }
    }

    fun members(leagueId: Long) = repo.observeMembers(leagueId)
    fun challenges(leagueId: Long) = repo.observeChallenges(leagueId)
    fun scores(challengeId: Long) = repo.observeScores(challengeId)
}

class MealPhotoViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FitLifeApp).mealPhotoRepo
    private val mealRepo = (app as FitLifeApp).mealRepo
    val history = repo.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val catalog get() = FoodMapper.catalog

    private val _result = MutableStateFlow<MealAnalysisResult?>(null)
    val result = _result.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    /** The photo being reviewed (shown on screen). Camera shots are temp files that are deleted when done. */
    private val _photo = MutableStateFlow<android.net.Uri?>(null)
    val photo = _photo.asStateFlow()

    private var corrected = false

    fun analyze(uri: android.net.Uri?) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            discardPhoto()
            _photo.value = uri
            corrected = false
            _result.value = repo.analyze(uri?.toString())
            _busy.value = false
        }
    }

    /** Start an empty entry (no photo) so foods can be added by hand. */
    fun startManual() = analyze(null)

    private fun rebuild(items: List<RecognizedFoodItem>) {
        val cur = _result.value ?: return
        corrected = true
        _result.value = FoodMapper.result(items, cur.uncertaintyNotes)
    }

    /** Scales calories AND macros together (portion size), e.g. 0.8 or 1.25. */
    fun scaleItem(index: Int, factor: Double) {
        val cur = _result.value ?: return
        val list = cur.items.toMutableList()
        val it = list.getOrNull(index) ?: return
        list[index] = it.copy(
            portionLabel = "adjusted", calories = (it.calories * factor).toInt().coerceAtLeast(1),
            proteinG = (it.proteinG * factor).toInt(), carbsG = (it.carbsG * factor).toInt(), fatG = (it.fatG * factor).toInt(),
            confidence = it.confidence
        )
        rebuild(list)
    }

    fun removeItem(index: Int) {
        val cur = _result.value ?: return
        rebuild(cur.items.filterIndexed { i, _ -> i != index })
    }

    fun addFood(name: String) {
        val cur = _result.value ?: return
        val food = FoodMapper.find(name) ?: return
        // Foods the person adds themselves are certain by definition.
        rebuild(cur.items + FoodMapper.item(food, 1f))
    }

    fun confirm(name: String, category: MealCategory) {
        viewModelScope.launch {
            val r = _result.value ?: return@launch
            if (r.items.isEmpty()) return@launch
            // The photo itself is not stored: only the confirmed numbers are.
            val id = repo.save(r, null, corrected)
            repo.confirm(id, r, name, category, mealRepo)
            clear()
        }
    }

    fun clear() { _result.value = null; discardPhoto() }

    private fun discardPhoto() {
        _photo.value?.let { u ->
            if (u.authority?.endsWith(".fileprovider") == true) {
                // Our own temp camera file: remove it. Gallery images belong to the user and are never touched.
                val name = u.lastPathSegment
                if (name != null) runCatching { java.io.File(java.io.File(getApplication<Application>().cacheDir, "meal_photos"), name).delete() }
            }
        }
        _photo.value = null
    }

    override fun onCleared() { discardPhoto() }



}
