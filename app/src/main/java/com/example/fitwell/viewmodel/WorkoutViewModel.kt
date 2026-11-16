package com.example.fitwell.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitwell.data.local.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Workout library: search text and category filter are applied together. */
class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = application.fitLife().workoutRepo

    val query = MutableStateFlow("")
    val category = MutableStateFlow<WorkoutCategory?>(null) // null = All

    val workouts: StateFlow<List<WorkoutEntity>> = combine(repo.observeWorkouts(), query, category) { all, q, c ->
        all.filter { w ->
            (c == null || w.category == c) &&
                (q.isBlank() || w.name.contains(q.trim(), ignoreCase = true) || w.targetArea.contains(q.trim(), ignoreCase = true))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeWorkout(id: Long): Flow<WorkoutEntity?> = repo.observeWorkout(id)
    fun observeExercises(id: Long): Flow<List<ExerciseEntity>> = repo.observeExercises(id)
}

data class SessionUiState(
    val loading: Boolean = true,
    val workoutName: String = "",
    val exercises: List<ExerciseEntity> = emptyList(),
    val index: Int = 0,
    val remainingSeconds: Int = 0,
    val paused: Boolean = false,
    val finished: Boolean = false,
    val endedEarly: Boolean = false,
    val completedCount: Int = 0,
    val elapsedSeconds: Int = 0,
    val error: String? = null
) {
    val current: ExerciseEntity? get() = exercises.getOrNull(index)
    val next: ExerciseEntity? get() = exercises.getOrNull(index + 1)
    val progress: Float get() = if (exercises.isEmpty()) 0f else completedCount / exercises.size.toFloat()
}

/**
 * Runs an interactive workout: per-exercise countdown (or tap-Done for rep exercises), pause/resume,
 * skip, end early, and a saved session on completion. It does NOT measure heart rate or calories.
 */
class ActiveWorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = application.fitLife().workoutRepo
    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state

    private var startedFor: Long? = null
    private var startTs = 0L
    private var workoutId: Long? = null
    private var ticker: Job? = null

    fun start(id: Long) {
        if (startedFor == id) return
        startedFor = id
        viewModelScope.launch {
            val w = repo.getWorkout(id)
            val ex = repo.getExercises(id)
            if (w == null || ex.isEmpty()) {
                _state.value = SessionUiState(loading = false, error = "This workout has no exercises.")
                return@launch
            }
            workoutId = id
            startTs = System.currentTimeMillis()
            _state.value = SessionUiState(loading = false, workoutName = w.name, exercises = ex, remainingSeconds = ex[0].durationSeconds)
            ticker = launch {
                while (true) {
                    delay(1_000)
                    val s = _state.value
                    if (s.finished || s.paused) continue
                    val cur = s.current ?: continue
                    if (cur.durationSeconds > 0) {
                        val left = s.remainingSeconds - 1
                        _state.update { it.copy(remainingSeconds = left, elapsedSeconds = it.elapsedSeconds + 1) }
                        if (left <= 0) advance(countAsCompleted = true)
                    } else {
                        _state.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
                    }
                }
            }
        }
    }

    fun togglePause() = _state.update { it.copy(paused = !it.paused) }
    fun skip() = advance(countAsCompleted = false)
    /** For rep-based exercises (and "done early" on timed ones). */
    fun markDone() = advance(countAsCompleted = true)

    fun endWorkout() {
        val s = _state.value
        if (s.finished) return
        ticker?.cancel()
        _state.update { it.copy(finished = true, endedEarly = true, paused = false) }
        if (s.completedCount > 0) save(completed = false) // only record sessions where something was done
    }

    private fun advance(countAsCompleted: Boolean) {
        val s = _state.value
        if (s.finished) return
        val completed = s.completedCount + if (countAsCompleted) 1 else 0
        val nextIndex = s.index + 1
        if (nextIndex >= s.exercises.size) {
            ticker?.cancel()
            _state.update { it.copy(completedCount = completed, finished = true, remainingSeconds = 0) }
            save(completed = true)
        } else {
            _state.update {
                it.copy(index = nextIndex, completedCount = completed, remainingSeconds = it.exercises[nextIndex].durationSeconds, paused = false)
            }
        }
    }

    private fun save(completed: Boolean) {
        val s = _state.value
        viewModelScope.launch {
            repo.saveSession(
                WorkoutSessionEntity(
                    workoutId = workoutId, workoutName = s.workoutName, startTimestamp = startTs,
                    endTimestamp = System.currentTimeMillis(), completed = completed,
                    durationSeconds = s.elapsedSeconds, exercisesCompleted = s.completedCount, exercisesTotal = s.exercises.size
                )
            )
        }
    }

    override fun onCleared() { ticker?.cancel() }
}
