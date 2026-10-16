package com.example.shorts

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShortsTheme { Root() }
        }
    }
}

@Composable
private fun Root() {
    val vm: AppViewModel = viewModel()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            vm.loading -> Loader()
            !vm.authed -> AuthScreen(vm)
            else -> MainScreen(vm)
        }
    }
}

@Composable
private fun MainScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var lastBack by remember { mutableLongStateOf(0L) }
    val anyOverlay = vm.viewerVideos != null || vm.cameraOpen || vm.draft != null

    // «Назад»: порядок важен — последний зарегистрированный обработчик главнее.
    BackHandler(enabled = true) {
        if (vm.tab != Tab.HOME) {
            vm.goHome()
        } else {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBack < 2000) {
                (context as? Activity)?.finish()
            } else {
                lastBack = now
                vm.toast("Нажмите ещё раз для выхода")
            }
        }
    }
    BackHandler(enabled = vm.viewerVideos != null) { vm.closeViewer() }
    BackHandler(enabled = vm.cameraOpen) { vm.closeCamera() }
    BackHandler(enabled = vm.draft != null) { vm.discardDraft() }
    BackHandler(enabled = vm.settingsOpen) { vm.closeSettings() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                FeedScreen(vm, active = vm.tab == Tab.HOME && !anyOverlay)
                if (vm.tab != Tab.HOME) {
                    Box(Modifier.fillMaxSize().blockTouches()) {
                        when (vm.tab) {
                            Tab.SEARCH -> SearchScreen(vm)
                            Tab.NOTIFICATIONS -> NotificationsScreen(vm)
                            Tab.PROFILE -> ProfileScreen(vm)
                            Tab.HOME -> {}
                        }
                    }
                }
            }
            BottomNav(vm)
        }

        vm.viewerVideos?.let { ViewerOverlay(vm, it) }
        if (vm.cameraOpen) CameraScreen(vm)
        vm.draft?.let { PublishScreen(vm, it) }

        if (vm.settingsOpen) SettingsScreen(vm)

        // окна поверх всего
        if (vm.commentsVideoId != null) CommentsSheet(vm)
        vm.shareVideo?.let { ShareSheet(it, vm) }
        if (vm.reportVideo != null) ReportSheet(vm)
        if (vm.editOpen) EditProfileSheet(vm)
    }
}

@Composable
private fun BottomNav(vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.94f)).navigationBarsPadding().height(62.dp)
    ) {
        NavItem("⌂", "Главная", vm.tab == Tab.HOME, Modifier.weight(1f)) { vm.openTab(Tab.HOME) }
        NavItem("⌕", "Поиск", vm.tab == Tab.SEARCH, Modifier.weight(1f)) { vm.openTab(Tab.SEARCH) }
        NavItem("＋", "Создать", false, Modifier.weight(1f), iconSize = 30) { vm.openCamera() }
        NavItem("♡", "Входящие", vm.tab == Tab.NOTIFICATIONS, Modifier.weight(1f)) { vm.openTab(Tab.NOTIFICATIONS) }
        NavItem("●", "Профиль", vm.tab == Tab.PROFILE, Modifier.weight(1f)) { vm.openTab(Tab.PROFILE) }
    }
}

@Composable
private fun NavItem(
    icon: String, label: String, active: Boolean, modifier: Modifier,
    iconSize: Int = 22, onClick: () -> Unit
) {
    Column(
        modifier.fillMaxSize().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(icon, color = if (active) Color.White else Muted, fontSize = iconSize.sp)
        Text(label, color = if (active) Color.White else Muted, fontSize = 10.sp)
    }
}

/** Полноэкранный просмотр списка видео (из профиля или поиска). */
@Composable
private fun ViewerOverlay(vm: AppViewModel, videos: List<Video>) {
    Box(Modifier.fillMaxSize().background(Color.Black).blockTouches()) {
        VideoPager(videos, vm.viewerStart, vm, true, Modifier.fillMaxSize())
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "‹", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { vm.closeViewer() }.padding(horizontal = 14.dp)
            )
            Text(
                vm.viewerTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
            )
            Box(Modifier.width(54.dp))
        }
    }
}
