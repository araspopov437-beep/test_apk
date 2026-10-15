package com.example.liftlog

import android.media.AudioManager
import android.media.ToneGenerator
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

private val DEFAULT_EXERCISES = listOf(
    "Жим лёжа", "Приседания", "Становая тяга", "Жим стоя",
    "Подтягивания", "Тяга штанги в наклоне", "Отжимания", "Бицепс со штангой"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(vm: WorkoutViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val names by vm.exerciseNames.collectAsStateWithLifecycle()
    val history by vm.workouts.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    // Если процесс был убит и экран восстановился без черновика
    LaunchedEffect(Unit) { if (vm.startedAt == 0L) vm.startWorkout() }

    // Общее время тренировки
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    // Таймер отдыха
    var restLeft by remember { mutableIntStateOf(0) }
    LaunchedEffect(restLeft > 0) {
        var finished = false
        while (restLeft > 0) {
            delay(1000)
            restLeft--
            if (restLeft == 0) finished = true
        }
        if (finished) {
            runCatching {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100).startTone(ToneGenerator.TONE_PROP_BEEP)
            }
        }
    }

    BackHandler { confirmExit = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Тренировка")
                        Text(
                            formatDuration((now - vm.startedAt) / 1000),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { confirmExit = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        vm.finish { ok ->
                            if (ok) onClose() else Toast.makeText(
                                ctx, "Добавьте хотя бы один подход с повторениями", Toast.LENGTH_SHORT
                            ).show()
                        }
                    }) { Text("Завершить") }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = vm.workoutName,
                    onValueChange = { vm.workoutName = it },
                    label = { Text("Название (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                RestTimer(restLeft, onStart = { restLeft = it }, onStop = { restLeft = 0 })
            }
            items(vm.draft) { ex ->
                ExerciseCard(
                    ex = ex,
                    last = lastPerformance(history, ex.name),
                    onAddSet = { vm.addSet(ex) },
                    onRemove = { vm.draft.remove(ex) }
                )
            }
            item {
                Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить упражнение")
                }
            }
        }
    }

    if (showAdd) {
        AddExerciseDialog(
            names = (names + DEFAULT_EXERCISES).distinct(),
            onAdd = { vm.addExercise(it); showAdd = false },
            onDismiss = { showAdd = false }
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Выйти без сохранения?") },
            text = { Text("Текущая тренировка будет потеряна.") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; vm.discard(); onClose() }) { Text("Выйти") }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Продолжить") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestTimer(left: Int, onStart: (Int) -> Unit, onStop: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (left > 0) {
                Text(
                    "Отдых %d:%02d".format(left / 60, left % 60),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = onStop) { Text("Стоп") }
            } else {
                Text("Отдых", Modifier.weight(1f))
                listOf(60, 90, 120).forEach { s ->
                    AssistChip(onClick = { onStart(s) }, label = { Text("$s с") })
                }
            }
        }
    }
}

@Composable
fun ExerciseCard(
    ex: DraftExercise,
    last: String?,
    onAddSet: () -> Unit,
    onRemove: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ex.name, style = MaterialTheme.typography.titleMedium)
                    if (last != null) {
                        Text(
                            "Прошлый раз: $last",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить упражнение")
                }
            }
            ex.sets.forEachIndexed { i, s ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("${i + 1}", Modifier.width(24.dp))
                    OutlinedTextField(
                        value = s.weight,
                        onValueChange = { s.weight = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("кг") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = s.reps,
                        onValueChange = { s.reps = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f),
                        label = { Text("повт.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    IconButton(onClick = { if (ex.sets.size > 1) ex.sets.remove(s) }) {
                        Icon(Icons.Default.Close, contentDescription = "Удалить подход")
                    }
                }
            }
            TextButton(onClick = onAddSet) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Подход")
            }
        }
    }
}

@Composable
fun AddExerciseDialog(names: List<String>, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val q = text.trim()
    val suggestions = names
        .filter { it.contains(q, ignoreCase = true) && !it.equals(q, ignoreCase = true) }
        .take(5)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Упражнение") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                suggestions.forEach { n -> TextButton(onClick = { onAdd(n) }) { Text(n) } }
            }
        },
        confirmButton = { TextButton(enabled = q.isNotEmpty(), onClick = { onAdd(q) }) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
