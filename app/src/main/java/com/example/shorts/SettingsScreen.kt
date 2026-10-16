package com.example.shorts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private fun speedLabel(v: Float) = (if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()) + "×"

private fun formatBytes(b: Long): String = when {
    b >= 1024L * 1024 -> String.format(java.util.Locale.US, "%.1f МБ", b / 1024.0 / 1024.0)
    b >= 1024 -> (b / 1024).toString() + " КБ"
    else -> "$b Б"
}

private enum class Page(val icon: String, val title: String, val subtitle: String) {
    HOME("", "Настройки", ""),
    ACCOUNT("👤", "Аккаунт", "Профиль, пароль, выход"),
    PLAYBACK("▶", "Воспроизведение", "Звук, повтор, скорость"),
    FEED("☰", "Лента и рекомендации", "Репосты друзей, счётчики"),
    NOTIFS("🔔", "Уведомления", "Push и «Входящие»"),
    PUBLISH("⬆", "Публикация", "Сжатие и загрузка видео"),
    LOOK("🎨", "Оформление", "Цвет акцента"),
    STORAGE("💾", "Хранилище", "Кэш"),
    ABOUT("ℹ", "О приложении", "Версия")
}

private enum class Dlg { NONE, SPEED, ACCENT, PASSWORD, RESET, LOGOUT, DELETE }

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings
    val ctx = LocalContext.current
    var dlg by remember { mutableStateOf(Dlg.NONE) }
    var page by remember { mutableStateOf(Page.HOME) }
    var cacheSize by remember { mutableStateOf(vm.cacheSizeBytes()) }
    val version = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "—"
        } catch (e: Exception) {
            "—"
        }
    }

    BackHandler(enabled = page != Page.HOME) { page = Page.HOME }

    Column(Modifier.fillMaxSize().background(Color.Black).blockTouches().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { if (page == Page.HOME) vm.closeSettings() else page = Page.HOME }
                    .padding(horizontal = 14.dp)
            )
            Text(page.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            when (page) {
                Page.HOME -> {
                    Spacer(Modifier.height(6.dp))
                    Page.values().filter { it != Page.HOME }.forEach { pg ->
                        CategoryRow(pg.icon, pg.title, pg.subtitle) { page = pg }
                    }
                }

                Page.ACCOUNT -> {
                    ActionRow("Редактировать профиль", "Имя, описание и фото") { vm.openEdit() }
                    ActionRow("Сменить пароль") { dlg = Dlg.PASSWORD }
                    ActionRow("Выйти из аккаунта") { dlg = Dlg.LOGOUT }
                    ActionRow("Удалить аккаунт", "Видео, лайки и комментарии удалятся навсегда", danger = true) { dlg = Dlg.DELETE }
                }

                Page.PLAYBACK -> {
                    SwitchRow("Автовоспроизведение", "Видео запускается сразу, как только открылось", s.autoplay) { s.setBool("autoplay", it) }
                    SwitchRow("Видео без звука", "Ролики будут проигрываться с выключенным звуком", s.startMuted) {
                        s.setBool("startMuted", it)
                    }
                    SwitchRow("Повтор видео", "Ролик крутится по кругу", s.loopVideo) { s.setBool("loopVideo", it) }
                    SwitchRow("Автопереход", "После конца видео сразу открывать следующее", s.autoNext) { s.setBool("autoNext", it) }
                    SwitchRow("Видео целиком", "Не обрезать края горизонтальных видео", s.fitVideo) { s.setBool("fitVideo", it) }
                    ValueRow("Скорость воспроизведения", speedLabel(s.speed)) { dlg = Dlg.SPEED }
                }

                Page.FEED -> {
                    SwitchRow("Репосты друзей в ленте", "Видео, которые репостнули друзья, показываются первыми", s.friendReposts) {
                        s.setBool("friendReposts", it)
                        vm.loadFeed()
                    }
                    SwitchRow("Показывать счётчики", "Числа лайков и комментариев под видео", s.showCounts) { s.setBool("showCounts", it) }
                    SwitchRow("Вибрация при лайке", null, s.haptics) { s.setBool("haptics", it) }
                    ActionRow(
                        "Сбросить рекомендации",
                        "Забыть историю просмотров и «Не интересно» — лента начнётся с нуля"
                    ) { dlg = Dlg.RESET }
                }

                Page.NOTIFS -> {
                    SettingsSection("Общее")
                    SwitchRow("Push-уведомления", "Показывать уведомления на экране устройства, даже когда приложение закрыто", s.pushEnabled) {
                        s.setBool("pushEnabled", it)
                        if (it && !Notifier.canPost(ctx)) {
                            vm.toast("Разрешите уведомления в настройках телефона")
                        }
                    }
                    SettingsSection("О чём уведомлять")
                    SwitchRow("Лайки", null, s.notifLikes) { s.setBool("notifLikes", it) }
                    SwitchRow("Комментарии", null, s.notifComments) { s.setBool("notifComments", it) }
                    SwitchRow("Новые подписчики", null, s.notifFollows) { s.setBool("notifFollows", it) }
                    SwitchRow("Репосты", null, s.notifReposts) { s.setBool("notifReposts", it) }
                }

                Page.PUBLISH -> {
                    SwitchRow("Сжимать видео", "Перед загрузкой уменьшать размер файла (720p) — видео занимает меньше места и грузится быстрее", s.compressVideo) {
                        s.setBool("compressVideo", it)
                    }
                    SwitchRow("Загружать только по Wi-Fi", "Не тратить мобильный интернет на публикацию", s.wifiOnlyUpload) {
                        s.setBool("wifiOnlyUpload", it)
                    }
                }

                Page.LOOK -> {
                    ValueRow("Цвет акцента", (AccentChoices.getOrNull(s.accentIndex) ?: AccentChoices[0]).first) { dlg = Dlg.ACCENT }
                }

                Page.STORAGE -> {
                    ValueRow("Очистить кэш", formatBytes(cacheSize)) {
                        vm.clearCache()
                        cacheSize = vm.cacheSizeBytes()
                    }
                }

                Page.ABOUT -> {
                    ValueRow("Версия", version) {}
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    when (dlg) {
        Dlg.SPEED -> ChoiceDialog(
            "Скорость воспроизведения",
            SPEEDS.map { speedLabel(it) to it }, s.speed,
            onSelect = { s.updateSpeed(it); dlg = Dlg.NONE }, onDismiss = { dlg = Dlg.NONE }
        )
        Dlg.ACCENT -> ChoiceDialog(
            "Цвет акцента",
            AccentChoices.mapIndexed { i, c -> c.first to i }, s.accentIndex,
            onSelect = { s.setAccent(it); dlg = Dlg.NONE }, onDismiss = { dlg = Dlg.NONE }
        )
        Dlg.PASSWORD -> PasswordDialog(vm) { dlg = Dlg.NONE }
        Dlg.RESET -> ConfirmDialog(
            "Сбросить рекомендации?",
            "История просмотров и отметки «Не интересно» будут удалены. Лента перестроится заново.",
            "Сбросить", false, { vm.resetRecommendations(); dlg = Dlg.NONE }, { dlg = Dlg.NONE }
        )
        Dlg.LOGOUT -> ConfirmDialog(
            "Выйти из аккаунта?", null, "Выйти", true, { dlg = Dlg.NONE; vm.logout() }, { dlg = Dlg.NONE }
        )
        Dlg.DELETE -> ConfirmDialog(
            "Удалить аккаунт?",
            "Профиль, все ваши видео, комментарии и лайки будут удалены навсегда. Отменить это нельзя.",
            "Удалить навсегда", true, { dlg = Dlg.NONE; vm.deleteAccount() }, { dlg = Dlg.NONE }
        )
        Dlg.NONE -> {}
    }
}

// ----------------------------------------------------------------------- строки настроек

@Composable
private fun CategoryRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center
        ) { Text(icon, fontSize = 18.sp, color = Color.White) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Text(subtitle, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text("›", color = Muted, fontSize = 22.sp)
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        title.uppercase(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 6.dp)
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Accent,
                uncheckedThumbColor = Muted, uncheckedTrackColor = Color(0xFF2A2A2A),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (danger) Color(0xFFFF4D6D) else Color.White, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text("›", color = Muted, fontSize = 22.sp)
    }
}

// ----------------------------------------------------------------------- диалоги

@Composable
private fun <T> ChoiceDialog(
    title: String, options: List<Pair<String, T>>, selected: T,
    onSelect: (T) -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SheetBg,
        titleContentColor = Color.White,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = value == selected, onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = Muted)
                        )
                        Text(label, color = Color.White, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть", color = Color.White) } }
    )
}

@Composable
private fun ConfirmDialog(
    title: String, text: String?, confirm: String, danger: Boolean,
    onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SheetBg,
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCCCCCC),
        title = { Text(title) },
        text = if (text != null) ({ Text(text) }) else null,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = if (danger) Color(0xFFFF4D6D) else Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Color.White) } }
    )
}

@Composable
private fun PasswordDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val error = when {
        p1.isNotEmpty() && p1.length < 6 -> "Минимум 6 символов"
        p2.isNotEmpty() && p1 != p2 -> "Пароли не совпадают"
        else -> null
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = SheetBg,
        titleContentColor = Color.White,
        title = { Text("Сменить пароль") },
        text = {
            Column {
                AppField(
                    p1, { p1 = it }, "Новый пароль",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(Modifier.height(10.dp))
                AppField(
                    p2, { p2 = it }, "Повторите пароль",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (error != null) {
                    Text(error, color = Color(0xFFFF6B6B), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && p1.length >= 6 && p1 == p2,
                onClick = {
                    busy = true
                    vm.changePassword(p1) { ok ->
                        busy = false
                        if (ok) onDismiss()
                    }
                }
            ) { Text(if (busy) "Сохранение..." else "Сохранить", color = Accent) }
        },
        dismissButton = { TextButton(onClick = { if (!busy) onDismiss() }) { Text("Отмена", color = Color.White) } }
    )
}
