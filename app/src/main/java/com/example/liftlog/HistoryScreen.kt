package com.example.liftlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftlog.data.Workout
import com.example.liftlog.data.WorkoutWithSets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: WorkoutViewModel, onStart: () -> Unit) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Workout?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("LiftLog") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onStart,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Тренировка") }
            )
        }
    ) { pad ->
        if (workouts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text(
                    "Пока нет тренировок.\nНажмите «Тренировка», чтобы начать.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(pad),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(workouts, key = { it.workout.id }) { w ->
                    WorkoutCard(w, onDelete = { toDelete = w.workout })
                }
            }
        }
    }

    toDelete?.let { w ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить тренировку?") },
            text = { Text(w.name) },
            confirmButton = {
                TextButton(onClick = { vm.delete(w.id); toDelete = null }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } }
        )
    }
}

@Composable
fun WorkoutCard(w: WorkoutWithSets, onDelete: () -> Unit) {
    val volume = w.sets.sumOf { it.weight * it.reps }
    val byExercise = w.sets.sortedBy { it.position }.groupBy { it.exercise }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(w.workout.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatDate(w.workout.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Удалить") }
            }
            byExercise.forEach { (name, sets) ->
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(name) }
                    append(": " + sets.joinToString(", ") { "${fmt(it.weight)}×${it.reps}" })
                })
            }
            Text(
                "Объём: ${fmt(volume)} кг · ${formatDuration(w.workout.durationSec)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
