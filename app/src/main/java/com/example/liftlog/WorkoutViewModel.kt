package com.example.liftlog

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.liftlog.data.AppDb
import com.example.liftlog.data.Workout
import com.example.liftlog.data.WorkoutSet
import com.example.liftlog.data.WorkoutWithSets
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DraftSet(weight: String = "", reps: String = "") {
    var weight by mutableStateOf(weight)
    var reps by mutableStateOf(reps)
}

class DraftExercise(val name: String) {
    val sets = mutableStateListOf(DraftSet())
}

class WorkoutViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDb.get(app)
    private val dao = db.dao()

    val workouts: StateFlow<List<WorkoutWithSets>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val exerciseNames: StateFlow<List<String>> = dao.observeExerciseNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Текущая (ещё не сохранённая) тренировка
    var workoutName by mutableStateOf("")
    var startedAt by mutableLongStateOf(0L)
    val draft = mutableStateListOf<DraftExercise>()

    fun startWorkout() {
        draft.clear()
        workoutName = ""
        startedAt = System.currentTimeMillis()
    }

    fun discard() {
        draft.clear()
        workoutName = ""
        startedAt = 0L
    }

    fun addExercise(name: String) {
        val n = name.trim()
        if (n.isNotEmpty()) draft.add(DraftExercise(n))
    }

    fun addSet(ex: DraftExercise) {
        val last = ex.sets.lastOrNull()
        ex.sets.add(DraftSet(last?.weight ?: "", last?.reps ?: ""))
    }

    fun finish(onResult: (Boolean) -> Unit) {
        val sets = mutableListOf<WorkoutSet>()
        var pos = 0
        draft.forEach { ex ->
            ex.sets.forEach { s ->
                val reps = s.reps.trim().toIntOrNull() ?: 0
                if (reps > 0) {
                    sets += WorkoutSet(
                        workoutId = 0,
                        exercise = ex.name.trim(),
                        weight = s.weight.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        reps = reps,
                        position = pos++
                    )
                }
            }
        }
        if (sets.isEmpty()) {
            onResult(false)
            return
        }
        val now = System.currentTimeMillis()
        val workout = Workout(
            name = workoutName.trim().ifEmpty { "Тренировка" },
            date = startedAt,
            durationSec = (now - startedAt) / 1000
        )
        viewModelScope.launch {
            db.withTransaction {
                val id = dao.insertWorkout(workout)
                dao.insertSets(sets.map { it.copy(workoutId = id) })
            }
            discard()
            onResult(true)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { dao.deleteWorkout(id) }
    }
}
