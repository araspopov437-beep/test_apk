package com.example.shorts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

// ======================================================================= ОБЩАЯ ОБОЛОЧКА

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarkSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = SheetBg,
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF555555)) }
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(bottom = 10.dp),
            content = content
        )
    }
}

@Composable
private fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
        textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)
    )
}

// ======================================================================= КОММЕНТАРИИ

@Composable
fun CommentsSheet(vm: AppViewModel) {
    var text by remember { mutableStateOf("") }
    DarkSheet(onDismiss = { vm.closeComments() }) {
        SheetTitle("Комментарии")
        Box(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp)) {
            when {
                vm.comments.isNotEmpty() -> LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    items(vm.comments, key = { it.id }) { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Avatar(c.profile?.avatarUrl, c.profile.name(), 38.dp, onClick = { vm.viewUserProfile(c.profile?.id) })
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(
                                    "@" + c.profile.nick(),
                                    color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { vm.viewUserProfile(c.profile?.id) }
                                )
                                Text(c.text, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
                vm.commentsLoading -> Loader()
                else -> CenterMessage("Комментариев пока нет", Modifier.align(Alignment.Center))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppField(text, { text = it }, "Добавить комментарий...", Modifier.weight(1f), maxLength = 500)
            Text(
                "➤", color = Color.White, fontSize = 20.sp,
                modifier = Modifier.padding(start = 10.dp).clip(CircleShape).background(Accent)
                    .clickable {
                        val t = text
                        vm.sendComment(t) { text = "" }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}

// ======================================================================= ПОДЕЛИТЬСЯ

@Composable
fun ShareSheet(video: Video, vm: AppViewModel) {
    var confirmDelete by remember { mutableStateOf(false) }
    val isOwner = video.userId == vm.meId

    DarkSheet(onDismiss = { vm.closeShare() }) {
        SheetTitle("Поделиться")
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
            ShareOption(if (vm.shareReposted) "Репостнуто" else "Репост", Modifier.weight(1f), { vm.toggleRepost() }) {
                CircleIcon(Color(0xFF1F3A2C)) { Text("⇄", color = GreenOk, fontSize = 22.sp) }
            }
            ShareOption("Копировать", Modifier.weight(1f), { vm.copyShareLink() }) {
                CircleIcon(Color(0xFF3A3A3A)) { Text("🔗", fontSize = 22.sp) }
            }
            ShareOption("MAX", Modifier.weight(1f), { vm.shareToApp("max") }) {
                LogoIcon("M", "https://irkutsk.expert-tk.ru/assets/mgr/images/biglogofab6eaa89b.png")
            }
            ShareOption("ВКонтакте", Modifier.weight(1f), { vm.shareToApp("vk") }) {
                LogoIcon("VK", "https://static.vecteezy.com/system/resources/previews/068/764/243/large_2x/vk-icon-logo-vk-app-transparent-background-free-png.png")
            }
            ShareOption("Discord", Modifier.weight(1f), { vm.shareToApp("discord") }) {
                LogoIcon("D", "https://i.pinimg.com/originals/2a/6f/24/2a6f24bea8c035244a557b6fc8a1dd80.png")
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
            ShareOption("Пожаловаться", Modifier.weight(1f), { vm.openReport() }) {
                CircleIcon(Color(0xFF2A2A2A)) { Text("🚩", fontSize = 22.sp) }
            }
            ShareOption("Не интересно", Modifier.weight(1f), { vm.notInterested() }) {
                CircleIcon(Color(0xFF2A2A2A)) { Text("🚫", fontSize = 22.sp) }
            }
            ShareOption("Скачать", Modifier.weight(1f), { vm.downloadCurrentVideo() }) {
                CircleIcon(Color(0xFF2A2A2A)) { Text("⬇️", fontSize = 22.sp) }
            }
            ShareOption("Почему это показано", Modifier.weight(1f), { vm.whyShown() }) {
                CircleIcon(Color(0xFF2A2A2A)) { Text("ℹ️", fontSize = 22.sp) }
            }
            if (isOwner) {
                ShareOption("Удалить", Modifier.weight(1f), { confirmDelete = true }) {
                    CircleIcon(Color(0xFF3A1620)) { Text("🗑", color = Color(0xFFFF4D6D), fontSize = 22.sp) }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = SheetBg,
            titleContentColor = Color.White,
            title = { Text("Удалить это видео безвозвратно?") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteCurrentVideo() }) { Text("Удалить", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена", color = Color.White) }
            }
        )
    }
}

@Composable
private fun ShareOption(label: String, modifier: Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Text(
            label, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun CircleIcon(bg: Color, content: @Composable () -> Unit) {
    Box(Modifier.size(54.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { content() }
}

/** Логотип сервиса; пока картинка не загрузилась (или нет сети) — видны буквы. */
@Composable
private fun LogoIcon(letters: String, url: String) {
    Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF2F2F2F)), contentAlignment = Alignment.Center) {
        Text(letters, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        AsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.size(54.dp).padding(9.dp)
        )
    }
}

// ======================================================================= ЖАЛОБА

private class ReportCat(val id: String, val title: String, val sub: List<String>)

private val REPORT_REASONS = listOf(
    ReportCat("violence", "Насилие и опасные действия", listOf("Угрозы и насилие", "Опасные трюки", "Жестокое обращение с животными", "Оружие")),
    ReportCat("hate", "Оскорбления и травля", listOf("Оскорбления", "Травля пользователя", "Разжигание ненависти")),
    ReportCat("adult", "Материалы для взрослых", listOf("Обнажённое тело", "Сексуализированный контент", "Контент с участием несовершеннолетних")),
    ReportCat("harm", "Опасное поведение и самоповреждение", listOf("Суицид и самоповреждение", "Расстройства пищевого поведения", "Опасные челленджи")),
    ReportCat("illegal", "Запрещённые товары и действия", listOf("Наркотики", "Продажа оружия", "Мошенничество", "Азартные игры")),
    ReportCat("misinfo", "Дезинформация", listOf("Ложная информация", "Медицинская дезинформация", "Обман и фейки")),
    ReportCat("spam", "Спам и вводящий в заблуждение контент", listOf("Спам", "Накрутка", "Поддельный аккаунт")),
    ReportCat("ip", "Нарушение авторских прав", listOf("Моё видео", "Чужая музыка", "Чужой товарный знак")),
    ReportCat("other", "Другое", emptyList())
)

@Composable
fun ReportSheet(vm: AppViewModel) {
    var cat by remember { mutableStateOf<ReportCat?>(null) }
    var state by remember { mutableStateOf(0) } // 0 — выбор, 1 — отправка, 2 — готово, 3 — ошибка
    val scope = rememberCoroutineScope()

    fun submit(c: ReportCat, reason: String) {
        state = 1
        scope.launch { state = if (vm.submitReport(c.id, reason)) 2 else 3 }
    }

    DarkSheet(onDismiss = { vm.closeReport() }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (cat != null && state == 0) {
                Text("‹", color = Color.White, fontSize = 28.sp,
                    modifier = Modifier.clickable { cat = null }.padding(horizontal = 12.dp))
            } else {
                Spacer(Modifier.size(48.dp))
            }
            SheetTitle(
                when {
                    state == 2 -> "Жалоба отправлена"
                    cat != null && state == 0 -> cat?.title ?: ""
                    else -> "Пожаловаться"
                },
                Modifier.weight(1f)
            )
            Text("×", color = Color.White, fontSize = 28.sp,
                modifier = Modifier.clickable { vm.closeReport() }.padding(horizontal = 14.dp))
        }

        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            when (state) {
                1 -> StatusBlock("⏳", "Отправляем жалобу...") {}
                2 -> StatusBlock("✓", "Спасибо за обращение") {
                    Text(
                        "Мы проверим видео на соответствие правилам сообщества и примем меры, если нарушение подтвердится.",
                        color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                    )
                    PrimaryButton("Готово", Modifier.fillMaxWidth()) { vm.closeReport() }
                }
                3 -> StatusBlock("⚠️", "Не удалось отправить жалобу") {
                    PrimaryButton("Попробовать снова", Modifier.fillMaxWidth().padding(top = 14.dp)) {
                        cat = null; state = 0
                    }
                }
                else -> {
                    val current = cat
                    if (current == null) {
                        Text(
                            "Выберите причину. Мы не сообщаем автору, кто отправил жалобу.",
                            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)
                        )
                        REPORT_REASONS.forEach { r ->
                            ReportItem(r.title) {
                                if (r.sub.isEmpty()) submit(r, r.title) else cat = r
                            }
                        }
                    } else {
                        current.sub.forEach { s -> ReportItem(s) { submit(current, s) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBlock(icon: String, text: String, extra: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 44.sp, color = Color.White)
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        extra()
    }
}

@Composable
private fun ReportItem(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("›", color = Muted, fontSize = 22.sp)
    }
}

// ======================================================================= РЕДАКТИРОВАНИЕ ПРОФИЛЯ

@Composable
fun EditProfileSheet(vm: AppViewModel) {
    val me = vm.me ?: return
    var username by remember { mutableStateOf(me.nick()) }
    var name by remember { mutableStateOf(me.displayName ?: "") }
    var bio by remember { mutableStateOf(me.bio ?: "") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) avatarUri = uri
    }

    DarkSheet(onDismiss = { vm.closeEdit() }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(48.dp))
            SheetTitle("Редактировать профиль", Modifier.weight(1f))
            Text("×", color = Color.White, fontSize = 28.sp,
                modifier = Modifier.clickable { vm.closeEdit() }.padding(horizontal = 14.dp))
        }
        Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = avatarUri ?: me.avatarUrl?.takeIf { it.isNotBlank() } ?: avatarFallback(me.name()),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp).clip(CircleShape).background(Color(0xFF222222))
                    .clickable { picker.launch("image/*") }
            )
            Text(
                "Изменить фото", color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.clickable { picker.launch("image/*") }.padding(top = 8.dp, bottom = 14.dp)
            )
            FieldLabel("Ник")
            AppField(username, { username = it }, "ник без пробелов", maxLength = 24)
            FieldLabel("Имя")
            AppField(name, { name = it }, "Ваше имя", maxLength = 40)
            FieldLabel("Описание")
            AppField(bio, { bio = it }, "Пара слов о себе...", singleLine = false, minLines = 3, maxLength = 300)
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                if (saving) "Сохранение..." else "Сохранить",
                Modifier.fillMaxWidth(), enabled = !saving
            ) {
                scope.launch {
                    saving = true
                    val ok = vm.saveProfile(username, name, bio, avatarUri)
                    saving = false
                    if (ok) vm.closeEdit()
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(
        text, color = Color(0xFFCCCCCC), fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp)
    )
}

// ======================================================================= ВЫБОР ОБЛОЖКИ

@Composable
fun CoverPickerSheet(
    frames: List<File>,
    selected: File?,
    onPick: (File) -> Unit,
    onUpload: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onUpload(uri)
    }
    DarkSheet(onDismiss = onDismiss) {
        SheetTitle("Выбрать обложку")
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            frames.chunked(3).forEach { rowFrames ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowFrames.forEach { f ->
                        val isSel = f == selected
                        AsyncImage(
                            model = f, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).aspectRatio(3f / 4f).clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF111111))
                                .border(if (isSel) 3.dp else 0.dp, if (isSel) Accent else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { onPick(f) }
                        )
                    }
                    repeat(3 - rowFrames.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(6.dp))
            PrimaryButton("Загрузить своё изображение", Modifier.fillMaxWidth(), color = Color(0xFF2A2A2A)) {
                picker.launch("image/*")
            }
        }
    }
}
