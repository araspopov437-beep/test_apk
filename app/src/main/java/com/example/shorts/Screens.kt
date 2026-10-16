package com.example.shorts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// ======================================================================= АВТОРИЗАЦИЯ

@Composable
fun AuthScreen(vm: AppViewModel) {
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFF333333), Color.Black),
                        center = Offset(size.width / 2f, size.height * 0.1f),
                        radius = size.height * 0.6f
                    )
                )
            }
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(25.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                buildAnnotatedString {
                    append("Short")
                    withStyle(SpanStyle(color = Accent)) { append("s") }
                },
                color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black, letterSpacing = (-4).sp
            )
            Text(
                "Смотри. Снимай. Создавай.", color = Muted, fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 26.dp)
            )

            // вкладки «Войти / Регистрация»
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E1E1E)).padding(4.dp)
            ) {
                AuthTab("Войти", !register, Modifier.weight(1f)) { register = false; vm.clearAuthError() }
                AuthTab("Регистрация", register, Modifier.weight(1f)) { register = true; vm.clearAuthError() }
            }
            Spacer(Modifier.height(16.dp))

            AppField(
                email, { email = it }, "Email",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )
            if (register) {
                Spacer(Modifier.height(12.dp))
                AppField(name, { name = it }, "Ваше имя", maxLength = 40)
            }
            Spacer(Modifier.height(12.dp))
            AppField(
                password, { password = it }, "Пароль",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                visualTransformation = PasswordVisualTransformation()
            )
            if (vm.authError.isNotEmpty()) {
                Text(
                    vm.authError, color = Color(0xFFFF6B6B), fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = if (register) "Создать аккаунт" else "Войти",
                modifier = Modifier.fillMaxWidth(),
                enabled = !vm.authBusy && email.isNotBlank() && password.length >= (if (register) 6 else 1)
            ) { vm.submitAuth(register, email, password, name) }
        }
    }
}

@Composable
private fun AuthTab(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(9.dp))
            .background(if (active) Color(0xFF333333) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (active) Color.White else Muted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

// ======================================================================= СЕТКА ВИДЕО (профиль / поиск)

@Composable
fun GridVideoCell(video: Video, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(Color(0xFF111111)).clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = video.thumbnailUrl ?: video.videoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Text(
            "♥ " + formatNumber(video.likesCount),
            color = Color.White, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun LazyGridScope.videoCells(videos: List<Video>, onClick: (Int) -> Unit) {
    items(videos.size) { index -> GridVideoCell(videos[index]) { onClick(index) } }
}

// ======================================================================= ПОИСК

@Composable
fun SearchScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().padding(horizontal = 15.dp)) {
        Text(
            "Поиск", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
        )
        AppField(vm.searchQuery, { vm.onSearchInput(it) }, "Поиск людей, видео и #хэштегов")
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Люди", vm.searchTab == SearchTab.USERS) { vm.switchSearchTab(SearchTab.USERS) }
            Chip("Видео", vm.searchTab == SearchTab.VIDEOS) { vm.switchSearchTab(SearchTab.VIDEOS) }
        }

        val msg = vm.searchMessage
        if (vm.searchTab == SearchTab.VIDEOS) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (msg != null) item(span = { GridItemSpan(maxLineSpan) }) { CenterMessage(msg) }
                videoCells(vm.searchVideos) { index -> vm.openViewer(vm.searchVideos, index, "Видео") }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (msg != null) item { CenterMessage(msg) }
                items(vm.searchUsers, key = { it.id }) { user -> UserRow(user, vm) }
            }
        }
    }
}

@Composable
private fun Chip(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (active) Color.Black else Color.White,
        fontWeight = FontWeight.Bold, fontSize = 13.sp,
        modifier = Modifier.clip(RoundedCornerShape(18.dp))
            .background(if (active) Color.White else Color(0xFF222222))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun UserRow(user: Profile, vm: AppViewModel) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(user.avatarUrl, user.name(), 47.dp, onClick = { vm.viewUserProfile(user.id) })
        Column(Modifier.weight(1f).padding(horizontal = 12.dp).clickable { vm.viewUserProfile(user.id) }) {
            Text(user.name(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            Text("@" + user.nick(), color = Muted, fontSize = 13.sp, maxLines = 1)
        }
        if (user.id != vm.meId) {
            val label = when {
                user.id in vm.friendIds -> "Друзья"
                user.id in vm.followingIds -> "Вы подписаны"
                else -> "Подписаться"
            }
            val following = user.id in vm.followingIds
            Text(
                label,
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (following) Color(0xFF2A2A2A) else Accent)
                    .clickable { vm.toggleFollow(user.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

// ======================================================================= ВХОДЯЩИЕ

@Composable
fun NotificationsScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().padding(horizontal = 15.dp)) {
        Text(
            "Входящие", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
        )
        if (vm.noticesLoaded && vm.notices.isEmpty()) {
            CenterMessage("Уведомлений нет", Modifier.padding(top = 10.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.notices, key = { it.id }) { n ->
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(n.profile?.avatarUrl, n.profile.name(), 44.dp, onClick = { vm.viewUserProfile(n.actorId) })
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("@" + n.profile.nick()) }
                            append(" ")
                            append(n.message)
                        },
                        color = Color.White, fontSize = 14.sp,
                        modifier = Modifier.padding(start = 12.dp).weight(1f)
                    )
                }
            }
        }
    }
}

// ======================================================================= ПРОФИЛЬ

@Composable
fun ProfileScreen(vm: AppViewModel) {
    val profile = vm.profileShown
    val userId = vm.profileUserId
    val isOwn = userId != null && userId == vm.meId
    var confirmLogout by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            if (profile == null) {
                Loader(Modifier.height(260.dp))
            } else {
                ProfileHeader(profile, isOwn, vm, onLogout = { confirmLogout = true })
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { ProfileTabs(isOwn, vm) }
        val msg = vm.profileGridMessage
        if (msg != null) item(span = { GridItemSpan(maxLineSpan) }) { CenterMessage(msg) }
        videoCells(vm.profileVideos) { index ->
            val title = when (vm.profileTab) {
                ProfileTab.REPOSTS -> "Репосты"
                ProfileTab.LIKED -> "Понравившиеся"
                ProfileTab.SAVED -> "Сохранённые"
                ProfileTab.VIDEOS -> "Видео"
            }
            vm.openViewer(vm.profileVideos, index, title)
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = SheetBg,
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCCCCCC),
            title = { Text("Выйти из аккаунта?") },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text("Выйти", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Отмена", color = Color.White) }
            }
        )
    }
}

@Composable
private fun ProfileHeader(profile: Profile, isOwn: Boolean, vm: AppViewModel, onLogout: () -> Unit) {
    val id = profile.id
    val friend = id in vm.friendIds
    val following = id in vm.followingIds
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Avatar(profile.avatarUrl, profile.name(), 96.dp)
        Text(
            profile.name(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
            Text("@" + profile.nick(), color = Muted, fontSize = 14.sp)
            if (!isOwn && friend) {
                Text(
                    "Друзья", color = GreenOk, fontSize = 10.sp,
                    modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1F3A2C)).padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
        if (!profile.bio.isNullOrBlank()) {
            Text(
                profile.bio, color = Color(0xFFDDDDDD), fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 16.dp)) {
            Stat(formatNumber(profile.followers), "Подписчики", Modifier.weight(1f))
            Stat(formatNumber(profile.following), "Подписки", Modifier.weight(1f))
            Stat(formatNumber(profile.likes), "Лайки", Modifier.weight(1f))
            Stat(formatNumber(vm.friendsCount), "Друзья", Modifier.weight(1f))
        }
        if (isOwn) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileButton("Редактировать", Color(0xFF2A2A2A)) { vm.openEdit() }
                ProfileButton("Выйти", Accent, onLogout)
            }
        } else {
            ProfileButton(
                when {
                    friend -> "Друзья"
                    following -> "Вы подписаны"
                    else -> "Подписаться"
                },
                if (following) Color(0xFF2A2A2A) else Accent
            ) { vm.toggleFollow(id) }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ProfileButton(text: String, bg: Color, onClick: () -> Unit) {
    Text(
        text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 11.dp)
    )
}

@Composable
private fun ProfileTabs(isOwn: Boolean, vm: AppViewModel) {
    Row(Modifier.fillMaxWidth().background(Color.Black)) {
        ProfileTabButton("▦", vm.profileTab == ProfileTab.VIDEOS, Modifier.weight(1f)) { vm.switchProfileTab(ProfileTab.VIDEOS) }
        ProfileTabButton("⇄", vm.profileTab == ProfileTab.REPOSTS, Modifier.weight(1f)) { vm.switchProfileTab(ProfileTab.REPOSTS) }
        // Лайки и сохранённое — приватные, видны только владельцу профиля.
        if (isOwn) {
            ProfileTabButton("♥", vm.profileTab == ProfileTab.LIKED, Modifier.weight(1f)) { vm.switchProfileTab(ProfileTab.LIKED) }
            ProfileTabButton("★", vm.profileTab == ProfileTab.SAVED, Modifier.weight(1f)) { vm.switchProfileTab(ProfileTab.SAVED) }
        }
    }
}

@Composable
private fun ProfileTabButton(glyph: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            glyph, color = if (active) Color.White else Dim, fontSize = 20.sp,
            modifier = Modifier.padding(vertical = 10.dp)
        )
        Box(Modifier.width(36.dp).height(2.dp).background(if (active) Color.White else Color.Transparent))
    }
}
