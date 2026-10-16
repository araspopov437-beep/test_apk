package com.example.shorts

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Настройки приложения: хранятся на устройстве, значения — состояние Compose. */
class AppSettings(context: Context) {

    private val p = context.applicationContext.getSharedPreferences("shorts_settings", Context.MODE_PRIVATE)

    // воспроизведение
    var autoplay by mutableStateOf(p.getBoolean("autoplay", true)); private set
    var startMuted by mutableStateOf(p.getBoolean("startMuted", false)); private set
    var loopVideo by mutableStateOf(p.getBoolean("loopVideo", true)); private set
    var autoNext by mutableStateOf(p.getBoolean("autoNext", false)); private set
    var fitVideo by mutableStateOf(p.getBoolean("fitVideo", false)); private set
    var speed by mutableFloatStateOf(p.getFloat("speed", 1f)); private set

    // лента
    var friendReposts by mutableStateOf(p.getBoolean("friendReposts", true)); private set
    var showCounts by mutableStateOf(p.getBoolean("showCounts", true)); private set
    var haptics by mutableStateOf(p.getBoolean("haptics", true)); private set

    // уведомления («Входящие» и push)
    var pushEnabled by mutableStateOf(p.getBoolean("pushEnabled", true)); private set
    var notifLikes by mutableStateOf(p.getBoolean("notifLikes", true)); private set
    var notifComments by mutableStateOf(p.getBoolean("notifComments", true)); private set
    var notifFollows by mutableStateOf(p.getBoolean("notifFollows", true)); private set
    var notifReposts by mutableStateOf(p.getBoolean("notifReposts", true)); private set

    // загрузка и оформление
    var wifiOnlyUpload by mutableStateOf(p.getBoolean("wifiOnlyUpload", false)); private set
    var accentIndex by mutableIntStateOf(p.getInt("accentIndex", 0)); private set

    init {
        ThemeState.accent = (AccentChoices.getOrNull(accentIndex) ?: AccentChoices[0]).second
    }

    fun setBool(key: String, value: Boolean) {
        when (key) {
            "autoplay" -> autoplay = value
            "startMuted" -> startMuted = value
            "loopVideo" -> loopVideo = value
            "autoNext" -> autoNext = value
            "fitVideo" -> fitVideo = value
            "friendReposts" -> friendReposts = value
            "showCounts" -> showCounts = value
            "haptics" -> haptics = value
            "pushEnabled" -> pushEnabled = value
            "notifLikes" -> notifLikes = value
            "notifComments" -> notifComments = value
            "notifFollows" -> notifFollows = value
            "notifReposts" -> notifReposts = value
            "wifiOnlyUpload" -> wifiOnlyUpload = value
            else -> return
        }
        p.edit().putBoolean(key, value).apply()
    }

    fun updateSpeed(value: Float) {
        speed = value
        p.edit().putFloat("speed", value).apply()
    }

    fun setAccent(index: Int) {
        accentIndex = index
        ThemeState.accent = (AccentChoices.getOrNull(index) ?: AccentChoices[0]).second
        p.edit().putInt("accentIndex", index).apply()
    }
}
