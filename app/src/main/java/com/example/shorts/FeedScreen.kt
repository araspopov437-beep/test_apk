package com.example.shorts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ======================================================================= ЛЕНТА (вкладка «Главная»)

@Composable
fun FeedScreen(vm: AppViewModel, active: Boolean) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            vm.feed.isNotEmpty() -> VideoPager(vm.feed, 0, vm, active, Modifier.fillMaxSize(), onNearEnd = { vm.loadMore() })
            vm.feedLoading -> Loader()
            vm.feedMessage != null -> Box(Modifier.fillMaxSize().padding(30.dp), contentAlignment = Alignment.Center) {
                Text(vm.feedMessage ?: "", color = Dim, textAlign = TextAlign.Center)
            }
        }

        // Переключатель «Подписки / Для вас»
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(25.dp, Alignment.CenterHorizontally)
        ) {
            FeedTab("Подписки", vm.feedType == FeedType.FOLLOWING) { vm.switchFeed(FeedType.FOLLOWING) }
            FeedTab("Для вас", vm.feedType == FeedType.FORYOU) { vm.switchFeed(FeedType.FORYOU) }
        }
    }
}

@Composable
private fun FeedTab(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (active) Color.White else Color(0xFFAAAAAA),
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

// ======================================================================= ПЕЙДЖЕР

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoPager(
    videos: List<Video>, startIndex: Int, vm: AppViewModel, active: Boolean,
    modifier: Modifier = Modifier, onNearEnd: (() -> Unit)? = null
) {
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
    ) { videos.size }
    val pagerScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage, videos.size) {
        if (onNearEnd != null && videos.isNotEmpty() && pagerState.currentPage >= videos.size - 3) onNearEnd()
    }

    VerticalPager(
        state = pagerState,
        modifier = modifier,
        key = { index -> videos.getOrNull(index)?.id ?: index }
    ) { page ->
        val video = videos.getOrNull(page)
        if (video != null) {
            VideoPage(video, active && pagerState.currentPage == page, vm) {
                if (page + 1 < videos.size) pagerScope.launch { pagerState.animateScrollToPage(page + 1) }
            }
        }
    }
}

// ======================================================================= ОДНА КАРТОЧКА ВИДЕО

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoPage(video: Video, isActive: Boolean, vm: AppViewModel, onEnded: () -> Unit = {}) {
    val st = vm.settings
    val haptic = LocalHapticFeedback.current
    var paused by remember(video.id) { mutableStateOf(!st.autoplay) }
    var rendered by remember(video.id) { mutableStateOf(false) }
    val pauseAlpha = remember { Animatable(0f) }
    var pauseGlyph by remember { mutableStateOf("❚❚") }
    val heartAnim = remember { Animatable(0f) }
    var heartPos by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val liked = video.id in vm.likedIds
    val saved = video.id in vm.savedIds
    val isOwn = video.userId == vm.meId
    val following = video.userId in vm.followingIds
    fun buzz() { if (st.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    fun count(n: Int) = if (st.showCounts) formatNumber(n) else "•"

    // Если страница стала неактивной — следующий показ начнёт с «первого кадра».
    LaunchedEffect(isActive) { if (!isActive) rendered = false }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // ---- видео + жесты: тап = пауза/пуск, двойной тап = лайк
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(video.id) {
                    detectTapGestures(
                        onTap = {
                            paused = !paused
                            pauseGlyph = if (paused) "❚❚" else "▶"
                            scope.launch {
                                pauseAlpha.snapTo(1f)
                                pauseAlpha.animateTo(0f, tween(700))
                            }
                        },
                        onDoubleTap = { offset ->
                            heartPos = offset
                            scope.launch {
                                heartAnim.snapTo(0f)
                                heartAnim.animateTo(1f, tween(850))
                            }
                            if (video.id !in vm.likedIds) {
                                buzz()
                                vm.toggleLike(video)
                            }
                        }
                    )
                }
        ) {
            if (isActive) {
                VideoSurface(
                    url = video.videoUrl,
                    playing = !paused,
                    onFirstFrame = { rendered = true },
                    modifier = Modifier.fillMaxSize(),
                    muted = vm.muted,
                    loop = st.loopVideo && !st.autoNext,
                    fit = st.fitVideo,
                    speed = st.speed,
                    onEnded = if (st.autoNext) onEnded else null,
                    onStats = { watched, duration -> vm.recordView(video, watched, duration) }
                )
            }
            if (!isActive || !rendered) {
                AsyncImage(
                    model = video.thumbnailUrl ?: video.videoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(Color(0xFF111111))
                )
            }
        }

        // ---- затемнение сверху и снизу
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.2f to Color.Transparent,
                    0.6f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.75f)
                )
            )
        )

        // ---- кнопка звука
        if (isActive) {
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 52.dp, end = 12.dp)
                    .size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f))
                    .clickable { vm.toggleMute() },
                contentAlignment = Alignment.Center
            ) {
                Text(if (vm.muted) "🔇" else "🔊", fontSize = 17.sp)
            }
        }

        // ---- иконка паузы по центру
        if (pauseAlpha.value > 0.01f) {
            Box(
                Modifier.align(Alignment.Center).alpha(pauseAlpha.value).size(80.dp)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(pauseGlyph, color = Color.White, fontSize = 30.sp)
            }
        }

        // ---- сердце при двойном тапе
        val hp = heartAnim.value
        if (hp > 0.001f && hp < 0.999f) {
            val half = with(density) { 45.dp.toPx() }
            Text(
                "♥",
                color = Accent,
                fontSize = 90.sp,
                modifier = Modifier
                    .offset { IntOffset((heartPos.x - half).roundToInt(), (heartPos.y - half * 1.3f).roundToInt()) }
                    .alpha(1f - hp)
                    .scale(0.6f + hp * 0.9f)
            )
        }

        // ---- текст: автор, название, описание, теги, звук
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 82.dp, bottom = 26.dp)
        ) {
            if (video.repostedBy.isNotEmpty()) {
                RepostLabel(video.repostedBy) { vm.viewUserProfile(video.repostedBy.first().id) }
            }
            Text(
                video.authorName(),
                color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { vm.viewUserProfile(video.userId) }.padding(bottom = 7.dp)
            )
            if (!video.title.isNullOrBlank()) {
                Text(
                    video.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
            if (!video.caption.isNullOrBlank()) {
                Text(
                    video.caption, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp,
                    maxLines = 4,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }
            if (video.hashtags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 7.dp)
                ) {
                    video.hashtags.forEach { tag ->
                        Text(
                            "#$tag", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { vm.openHashtagSearch(tag) }
                        )
                    }
                }
            }
            Text("♪ " + (video.music?.takeIf { it.isNotBlank() } ?: "Original sound"), color = Color(0xFFDDDDDD), fontSize = 13.sp)
        }

        // ---- колонка действий справа
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 9.dp, bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Avatar(
                url = video.avatarUrl, name = video.authorName(), size = 47.dp, border = true,
                onClick = { vm.viewUserProfile(video.userId) }
            )
            if (!isOwn) {
                Text(
                    if (following) "Вы подписаны" else "+",
                    color = Color.White, fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(15.dp)).background(Accent)
                        .clickable { vm.toggleFollow(video.userId) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            ActionButton("♥", count(vm.likesOf(video)), if (liked) Accent else Color.White) {
                if (!liked) buzz()
                vm.toggleLike(video)
            }
            ActionButton("💬", count(vm.commentsOf(video)), Color.White) { vm.openComments(video.id) }
            ActionButton("★", if (saved) "Сохранено" else "Сохранить", if (saved) Gold else Color.White) { vm.toggleSave(video) }
            ActionButton("↗", "Поделиться", Color.White) { vm.openShare(video) }
        }
    }
}

@Composable
private fun ActionButton(icon: String, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            Modifier.size(43.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, color = tint, fontSize = 23.sp)
        }
        Text(label, color = tint, fontSize = 11.sp)
    }
}

/** «@друг репостнул(а)» с наложенными аватарками (до трёх). */
@Composable
private fun RepostLabel(people: List<MiniProfile>, onClick: () -> Unit) {
    val shown = people.take(3)
    val text = when (people.size) {
        1 -> people[0].name() + " репостнул(а)"
        2 -> people[0].name() + " и " + people[1].name() + " репостнули"
        else -> people[0].name() + " и ещё " + (people.size - 1) + " репостнули"
    }
    Row(
        Modifier.clickable(onClick = onClick).padding(bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            shown.forEachIndexed { i, p ->
                Avatar(
                    url = p.avatarUrl, name = p.name(), size = 20.dp, border = false,
                    modifier = Modifier.offset(x = (i * 11).dp)
                )
            }
        }
        Text(
            "⇄ $text",
            color = Color.White, fontSize = 13.sp,
            modifier = Modifier.padding(start = ((shown.size - 1) * 11 + 8).dp)
        )
    }
}

// ======================================================================= ПЛЕЕР (Media3 ExoPlayer)

@Composable
fun VideoSurface(
    url: String,
    playing: Boolean,
    onFirstFrame: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    loop: Boolean = true,
    fit: Boolean = false,
    speed: Float = 1f,
    onEnded: (() -> Unit)? = null,
    onStats: ((watchedMs: Long, durationMs: Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val playingState = rememberUpdatedState(playing)
    val firstFrame = rememberUpdatedState(onFirstFrame)
    val endedState = rememberUpdatedState(onEnded)
    val statsState = rememberUpdatedState(onStats)
    val watched = remember(url) { longArrayOf(0L) }

    val exo = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (muted) 0f else 1f
            setPlaybackSpeed(speed)
            setMediaItem(MediaItem.fromUri(url))
            playWhenReady = playingState.value
            prepare()
        }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrame.value()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) endedState.value?.invoke()
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            // Сигнал для рекомендаций: сколько реально смотрели и какова длина ролика.
            val duration = exo.duration
            statsState.value?.invoke(watched[0], if (duration > 0) duration else 0L)
            exo.release()
        }
    }

    // Считаем время реального просмотра (без пауз).
    LaunchedEffect(exo) {
        while (true) {
            delay(250)
            if (exo.isPlaying) { watched[0] = watched[0] + 250L }
        }
    }

    LaunchedEffect(playing, exo) { exo.playWhenReady = playing }
    LaunchedEffect(muted, exo) { exo.volume = if (muted) 0f else 1f }
    LaunchedEffect(loop, exo) { exo.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }
    LaunchedEffect(speed, exo) { exo.setPlaybackSpeed(speed) }

    // Приложение ушло в фон — ставим на паузу, вернулись — продолжаем.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exo) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> exo.pause()
                Lifecycle.Event.ON_START -> exo.playWhenReady = playingState.value
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = if (fit) AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = exo
            }
        },
        update = { view ->
            if (view.player !== exo) view.player = exo
            view.resizeMode = if (fit) AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
    )
}
