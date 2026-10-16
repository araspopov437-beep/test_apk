package com.example.shorts

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.video.AudioConfig
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File

private const val MAX_RECORD_SECONDS = 180 // 3 минуты, как в оригинале

@SuppressLint("MissingPermission")
@Composable
fun CameraScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun hasPermissions() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(hasPermissions()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        granted = res.isNotEmpty() && res.values.all { it }
        errorText = if (granted) null else "Доступ к камере или микрофону запрещён. Разрешите их в настройках приложения."
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) vm.onGalleryVideo(uri)
    }

    LaunchedEffect(Unit) {
        if (!granted) permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    val camController = remember {
        LifecycleCameraController(ctx).apply {
            setEnabledUseCases(CameraController.VIDEO_CAPTURE)
            videoCaptureQualitySelector = QualitySelector.from(
                Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )
        }
    }

    // по умолчанию — задняя камера (как в оригинале)
    var backCamera by remember { mutableStateOf(true) }
    LaunchedEffect(backCamera) {
        try {
            camController.cameraSelector =
                if (backCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        } catch (e: Exception) {
            vm.toast("Эта камера недоступна")
            backCamera = !backCamera
        }
    }

    DisposableEffect(granted, lifecycleOwner) {
        if (granted) {
            try {
                camController.bindToLifecycle(lifecycleOwner)
                errorText = null
            } catch (e: Exception) {
                errorText = "Не удалось открыть камеру. Проверьте разрешения."
            }
        }
        onDispose {
            try {
                camController.unbind()
            } catch (_: Exception) {
            }
        }
    }

    // ---------------- запись
    var recording by remember { mutableStateOf<Recording?>(null) }
    var seconds by remember { mutableIntStateOf(0) }
    val ignoreResult = remember { booleanArrayOf(false) }
    val isRecording = recording != null

    fun startRec() {
        if (!granted) {
            vm.toast("Камера ещё не готова")
            return
        }
        val file = File(ctx.cacheDir, "rec_" + System.currentTimeMillis() + ".mp4")
        ignoreResult[0] = false
        try {
            recording = camController.startRecording(
                FileOutputOptions.Builder(file).build(),
                AudioConfig.create(true),
                ContextCompat.getMainExecutor(ctx)
            ) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    recording = null
                    if (ignoreResult[0]) {
                        file.delete()
                    } else if (!event.hasError() && file.length() > 0) {
                        vm.onRecorded(file)
                    } else {
                        file.delete()
                        vm.toast("Видео не записалось")
                    }
                }
            }
        } catch (e: Exception) {
            vm.toast("Не удалось начать запись")
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            seconds = 0
            while (true) {
                delay(1000)
                seconds++
                if (seconds >= MAX_RECORD_SECONDS) {
                    recording?.stop()
                    break
                }
            }
        }
    }

    // Экран закрыли во время записи — останавливаем и выбрасываем результат.
    DisposableEffect(Unit) {
        onDispose {
            ignoreResult[0] = true
            try {
                recording?.stop()
            } catch (_: Exception) {
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).blockTouches()) {

        if (granted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    PreviewView(c).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        this.controller = camController
                    }
                }
            )
        }

        // верхняя панель: закрыть / сменить камеру
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RoundGlyph("×") { vm.closeCamera() }
            RoundGlyph("↻") { if (!isRecording) backCamera = !backCamera }
        }

        // таймер записи
        if (isRecording) {
            Text(
                String.format("%02d:%02d", seconds / 60, seconds % 60),
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 18.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Accent)
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }

        // ошибка камеры
        val err = errorText
        if (err != null) {
            Column(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Камера недоступна", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    err, color = Muted, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp, bottom = 20.dp)
                )
                PrimaryButton("Повторить") {
                    if (hasPermissions()) {
                        granted = true
                        errorText = null
                    } else {
                        permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                    }
                }
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Открыть настройки", color = Color(0xFF2A2A2A)) {
                    try {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                    }
                }
            }
        }

        // нижняя панель
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (isRecording) "Нажмите, чтобы остановить" else "Нажмите для записи",
                color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 14.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 30.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(Modifier.size(64.dp))

                // кнопка записи
                Box(
                    Modifier.size(78.dp).clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .clickable { if (isRecording) recording?.stop() else startRec() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(Accent))
                    } else {
                        Box(Modifier.size(60.dp).clip(CircleShape).background(Color.White))
                    }
                }

                // галерея
                Column(
                    Modifier.size(64.dp).clickable(enabled = !isRecording) { galleryLauncher.launch("video/*") },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) { Text("🖼", fontSize = 20.sp) }
                    Text("Загрузить", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun RoundGlyph(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, color = Color.White, fontSize = 24.sp)
    }
}
