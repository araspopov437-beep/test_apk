package com.example.liftlog

import com.example.liftlog.data.WorkoutWithSets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun fmt(d: Double): String =
    if (d % 1.0 == 0.0) d.toLong().toString() else String.format(Locale.getDefault(), "%.1f", d)

fun formatDate(ts: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ts))

fun formatShortDate(ts: Long): String =
    SimpleDateFormat("d.MM", Locale.getDefault()).format(Date(ts))

fun formatDuration(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val r = s % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, r)
    else String.format(Locale.getDefault(), "%d:%02d", m, r)
}

/** Начало недели (понедельник, 00:00) для заданного момента. */
fun weekStart(ts: Long): Long {
    val c = Calendar.getInstance()
    c.firstDayOfWeek = Calendar.MONDAY
    c.timeInMillis = ts
    c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** Начала последних n недель, от старой к новой. */
fun weeksBack(n: Int): List<Long> {
    val current = weekStart(System.currentTimeMillis())
    return (n - 1 downTo 0).map { i ->
        Calendar.getInstance().apply {
            timeInMillis = current
            add(Calendar.WEEK_OF_YEAR, -i)
        }.timeInMillis
    }
}

/** Лучший подход упражнения в самой свежей тренировке, где оно было. */
fun lastPerformance(workouts: List<WorkoutWithSets>, exercise: String): String? {
    val key = exercise.trim()
    if (key.isEmpty()) return null
    val sets = workouts.firstNotNullOfOrNull { w ->
        w.sets.filter { it.exercise.equals(key, ignoreCase = true) }.ifEmpty { null }
    } ?: return null
    val best = sets.maxWith(compareBy({ it.weight }, { it.reps }))
    return if (best.weight == 0.0) "${best.reps} повт." else "${fmt(best.weight)} кг × ${best.reps}"
}
