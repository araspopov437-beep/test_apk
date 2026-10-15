package com.example.liftlog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: WorkoutViewModel) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val sets = workouts.flatMap { it.sets }
    val volume = sets.sumOf { it.weight * it.reps }

    val weeks = weeksBack(8)
    val counts = weeks.map { ws -> workouts.count { weekStart(it.workout.date) == ws } }
    val maxCount = (counts.maxOrNull() ?: 0).coerceAtLeast(1)

    val records = sets.groupBy { it.exercise }
        .map { (name, list) -> name to list.maxWith(compareBy({ it.weight }, { it.reps })) }
        .sortedBy { it.first.lowercase() }

    Scaffold(topBar = { TopAppBar(title = { Text("Статистика") }) }) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Тренировок", workouts.size.toString(), Modifier.weight(1f))
                    StatTile("На этой неделе", counts.last().toString(), Modifier.weight(1f))
                    StatTile("Объём, т", fmt(volume / 1000), Modifier.weight(1f))
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Тренировки по неделям", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth().height(110.dp), verticalAlignment = Alignment.Bottom) {
                            counts.forEach { c ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(if (c > 0) "$c" else "", style = MaterialTheme.typography.labelSmall)
                                        Box(
                                            Modifier
                                                .width(18.dp)
                                                .height((c * 80f / maxCount + 2f).dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth()) {
                            weeks.forEach {
                                Text(
                                    formatShortDate(it),
                                    Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Личные рекорды", style = MaterialTheme.typography.titleMedium)
                        if (records.isEmpty()) {
                            Text(
                                "Данные появятся после первой тренировки.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        records.forEach { (name, best) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, Modifier.weight(1f))
                                val text = if (best.weight == 0.0) {
                                    "${best.reps} повт."
                                } else {
                                    val oneRm = best.weight * (1 + best.reps / 30.0)
                                    "${fmt(best.weight)} кг × ${best.reps}  (1ПМ ≈ ${fmt(Math.round(oneRm).toDouble())})"
                                }
                                Text(text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
