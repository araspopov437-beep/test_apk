package com.example.shorts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Варианты цвета акцента (настройки → оформление). */
val AccentChoices = listOf(
    "Розовый" to Color(0xFFFF0050),
    "Красный" to Color(0xFFE53935),
    "Оранжевый" to Color(0xFFFF7A00),
    "Зелёный" to Color(0xFF22C55E),
    "Синий" to Color(0xFF3B82F6),
    "Фиолетовый" to Color(0xFF8B5CF6)
)

object ThemeState {
    var accent by mutableStateOf(AccentChoices[0].second)
}

val Accent: Color get() = ThemeState.accent
val GreenOk = Color(0xFF4ADE80)
val Gold = Color(0xFFFFD400)
val SheetBg = Color(0xFF1C1C1C)
val FieldBg = Color(0xFF1E1E1E)
val Muted = Color(0xFF888888)
val Dim = Color(0xFF777777)

private val Scheme = darkColorScheme(
    primary = Color(0xFFFF0050),
    onPrimary = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = SheetBg,
    onSurface = Color.White,
    surfaceVariant = FieldBg,
    onSurfaceVariant = Color(0xFFCCCCCC)
)

@Composable
fun ShortsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme.copy(primary = Accent), content = content)
}

@Composable
fun Loader(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White, trackColor = Color(0xFF333333), strokeWidth = 4.dp)
    }
}
