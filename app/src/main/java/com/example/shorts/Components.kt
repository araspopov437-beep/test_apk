package com.example.shorts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** Круглый аватар. Если url пустой — ui-avatars по имени (как в оригинале). */
@Composable
fun Avatar(
    url: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    border: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    var m = modifier.size(size).clip(CircleShape).background(Color(0xFF222222))
    if (border) m = m.border(BorderStroke(2.dp, Color.White), CircleShape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    AsyncImage(
        model = if (url.isNullOrBlank()) avatarFallback(name) else url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = m
    )
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Accent,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = color.copy(alpha = 0.5f),
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        ),
        contentPadding = PaddingValues(horizontal = 18.dp)
    ) {
        Text(text, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun AppField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLength: Int = Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= maxLength) onValueChange(it) },
        placeholder = { Text(placeholder, color = Dim) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            disabledTextColor = Muted,
            focusedContainerColor = FieldBg,
            unfocusedContainerColor = FieldBg,
            disabledContainerColor = FieldBg,
            focusedBorderColor = Color(0xFF555555),
            unfocusedBorderColor = Color(0xFF2A2A2A),
            disabledBorderColor = Color(0xFF2A2A2A),
            cursorColor = Color.White
        )
    )
}

@Composable
fun CenterMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Dim, textAlign = TextAlign.Center)
    }
}

/** Не пропускает касания к нижним слоям (экраны-наложения поверх ленты). */
fun Modifier.blockTouches(): Modifier =
    this.pointerInput(Unit) { detectTapGestures { } }
