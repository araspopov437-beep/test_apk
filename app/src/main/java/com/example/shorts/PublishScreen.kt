package com.example.shorts

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

private const val MAX_HASHTAGS = 5

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PublishScreen(vm: AppViewModel, draft: PublishDraft) {
    var title by remember(draft) { mutableStateOf("") }
    var caption by remember(draft) { mutableStateOf("") }
    var tagInput by remember(draft) { mutableStateOf("") }
    var tags by remember(draft) { mutableStateOf(listOf<String>()) }
    var showCovers by remember { mutableStateOf(false) }

    fun addTag() {
        val tag = normalizeTag(tagInput)
        when {
            tag.isEmpty() -> vm.toast("Введите хэштег")
            tags.size >= MAX_HASHTAGS -> vm.toast("Можно добавить не более 5 хэштегов")
            tag in tags -> vm.toast("Такой хэштег уже добавлен")
            else -> {
                tags = tags + tag
                tagInput = ""
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color.Black).blockTouches()
            .statusBarsPadding().navigationBarsPadding().imePadding()
    ) {
        // шапка
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("×", color = Color.White, fontSize = 30.sp,
                modifier = Modifier.clickable { vm.discardDraft() }.padding(horizontal = 12.dp))
            Text(
                "Новое видео", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(54.dp))
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // обложка
                Box(
                    Modifier.width(110.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1B1B1B))
                        .clickable {
                            if (vm.draftCovers.isEmpty()) vm.toast("Кадры ещё готовятся...") else showCovers = true
                        }
                ) {
                    val cover = vm.draftCover
                    if (cover != null) {
                        AsyncImage(
                            model = cover, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Text(
                        "Обложка", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f)).padding(vertical = 5.dp)
                    )
                }
                // предпросмотр видео
                Box(
                    Modifier.width(110.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF111111))
                ) {
                    VideoSurface(
                        url = Uri.fromFile(draft.videoFile).toString(),
                        playing = true,
                        onFirstFrame = {},
                        modifier = Modifier.fillMaxSize(),
                        muted = true
                    )
                }
            }
            Text(
                "Если обложка не выбрана, будет использован кадр из видео.",
                color = Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
            )

            FieldLabel("Название")
            AppField(title, { title = it }, "Название видео", maxLength = 100)

            FieldLabel("Описание")
            AppField(caption, { caption = it }, "Расскажите о видео...", singleLine = false, minLines = 3, maxLength = 2200)

            FieldLabel("Хэштеги (до 5)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppField(
                    tagInput, { tagInput = it },
                    if (tags.size >= MAX_HASHTAGS) "Достигнут лимит в 5 хэштегов" else "#например",
                    Modifier.weight(1f),
                    enabled = tags.size < MAX_HASHTAGS,
                    maxLength = 30
                )
                Text(
                    "+", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 10.dp).clip(RoundedCornerShape(12.dp)).background(Accent)
                        .clickable { addTag() }.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                tags.forEachIndexed { i, tag ->
                    Row(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2A2A))
                            .padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("#$tag", color = Color.White, fontSize = 14.sp)
                        Text(
                            "×", color = Muted, fontSize = 18.sp,
                            modifier = Modifier.clickable { tags = tags.filterIndexed { idx, _ -> idx != i } }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // подвал
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryButton("Отмена", Modifier.weight(1f), enabled = !vm.publishing, color = Color(0xFF2A2A2A)) {
                vm.discardDraft()
            }
            PrimaryButton(vm.publishLabel, Modifier.weight(1f), enabled = !vm.publishing) {
                vm.publish(title, caption, tags)
            }
        }
    }

    if (showCovers) {
        CoverPickerSheet(
            frames = vm.draftCovers,
            selected = vm.draftCover,
            onPick = { vm.pickCoverFrame(it); showCovers = false },
            onUpload = { vm.setCustomCover(it); showCovers = false },
            onDismiss = { showCovers = false }
        )
    }
}
