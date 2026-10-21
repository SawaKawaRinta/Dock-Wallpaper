package com.apple.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 一次数字输入请求：标题 + 初始值 + 单位 + 范围 + 步进 + 确认回调。
 */
data class NumericInputTarget(
    val title: String,
    val initial: String,
    val unit: String = "",
    val min: Float = 0f,
    val max: Float = 100f,
    val step: Float = 1f,
    val onConfirm: (Float) -> Unit
)

/**
 * 悬浮数字输入面板（macOS 风格）：
 * 标题 + 大数值显示（带单位）+ 步进 +/- + 横向滑块微调 + 数字键盘 + 确认。
 * 首次点数字键会清空原值。点遮罩关闭，点面板内部不关闭。
 */
@Composable
fun NumericInputOverlay(
    target: NumericInputTarget,
    onDismiss: () -> Unit
) {
    var text by remember(target) { mutableStateOf(target.initial) }
    var touched by remember(target) { mutableStateOf(false) }

    val current = text.toFloatOrNull() ?: target.min

    fun fmt(v: Float): String {
        val d = when {
            target.step >= 1f -> 0
            target.step >= 0.1f -> 1
            target.step >= 0.01f -> 2
            else -> 2
        }
        return "%.${d}f".format(v)
    }

    fun press(key: String) {
        when (key) {
            "⌫" -> { text = text.dropLast(1); touched = true }
            "." -> {
                if (touched) {
                    if (!text.contains('.')) text += if (text.isEmpty()) "0." else "."
                } else {
                    text = "0."
                    touched = true
                }
            }
            else -> {
                if (touched) {
                    if (text.length < 8) text += key
                } else {
                    text = key
                    touched = true
                }
            }
        }
    }

    fun commit() {
        text.toFloatOrNull()?.let { target.onConfirm(it.coerceIn(target.min, target.max)) }
        onDismiss()
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .background(Color.Black.copy(alpha = 0.25f))
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xF5F5F5F7))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp)
        ) {
            Text(
                target.title,
                color = Color(0xFF1C1C1E),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(14.dp))

            // 数值显示区 + 步进
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepButton("−") {
                    text = fmt((current - target.step).coerceIn(target.min, target.max))
                }
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text.ifEmpty { "0" },
                        color = Color(0xFF1C1C1E),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (target.unit.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            target.unit,
                            color = Color(0xFF8A8A8E),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                StepButton("＋") {
                    text = fmt((current + target.step).coerceIn(target.min, target.max))
                }
            }

            Spacer(Modifier.height(6.dp))

            // 横向滑块微调
            Slider(
                value = current.coerceIn(target.min, target.max),
                onValueChange = { text = fmt(it) },
                valueRange = target.min..target.max,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF007AFF),
                    inactiveTrackColor = Color(0xFFE0E0E5)
                )
            )

            Spacer(Modifier.height(8.dp))

            val keyShape = RoundedCornerShape(10.dp)
            val keyBg = Color(0xFFECECEE)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumKey("1", keyBg, keyShape, Modifier.weight(1f)) { press("1") }
                    NumKey("2", keyBg, keyShape, Modifier.weight(1f)) { press("2") }
                    NumKey("3", keyBg, keyShape, Modifier.weight(1f)) { press("3") }
                    NumKey("⌫", keyBg, keyShape, Modifier.weight(1f)) { press("⌫") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumKey("4", keyBg, keyShape, Modifier.weight(1f)) { press("4") }
                    NumKey("5", keyBg, keyShape, Modifier.weight(1f)) { press("5") }
                    NumKey("6", keyBg, keyShape, Modifier.weight(1f)) { press("6") }
                    NumKey(".", keyBg, keyShape, Modifier.weight(1f)) { press(".") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumKey("7", keyBg, keyShape, Modifier.weight(1f)) { press("7") }
                    NumKey("8", keyBg, keyShape, Modifier.weight(1f)) { press("8") }
                    NumKey("9", keyBg, keyShape, Modifier.weight(1f)) { press("9") }
                    Spacer(Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumKey("0", keyBg, keyShape, Modifier.weight(1f)) { press("0") }
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(keyShape)
                            .background(Color(0xFF007AFF))
                            .clickable { commit() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "✓",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFFE9E9EB))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color(0xFF1C1C1E),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NumKey(
    label: String,
    bg: Color,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color(0xFF1C1C1E),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
