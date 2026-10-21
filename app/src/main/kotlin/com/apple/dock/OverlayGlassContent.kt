package com.apple.dock

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apple.dock.Implementation.GlassConfig
import kotlin.math.roundToInt

/**
 * 悬浮调节面板 —— 纯静态，带滚动。
 */
@Composable
fun OverlayGlassContent(
    wallpaper: Bitmap? = null,
    sidePaddingDp: Float = 16f,
    verticalPaddingDp: Float = 16f,
    onClose: () -> Unit = {},
    onDrag: (dx: Float, dy: Float) -> Unit = { _, _ -> },
    onGlassChanged: () -> Unit = {}
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 350f))
    }

    LaunchedEffect(Unit) {
        snapshotFlow {
            Triple(
                GlassConfig.DOCK_WIDTH,
                GlassConfig.DOCK_HEIGHT,
                GlassConfig.DOCK_OFFSET_Y
            )
        }.collect {
            onGlassChanged()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = sidePaddingDp.dp, vertical = verticalPaddingDp.dp)
            .graphicsLayer {
                val e = enter.value
                alpha = e
                translationY = (1f - e) * 40f
            }
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xE6FFFFFF))
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrafficDot(Color(0xFFFF5F57))
                Spacer(Modifier.width(8.dp))
                TrafficDot(Color(0xFFFEBC2E))
                Spacer(Modifier.width(8.dp))
                TrafficDot(Color(0xFF28C840))
            }

            Spacer(Modifier.width(12.dp))

            Text(
                "预览调节",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1C1C1E),
                modifier = Modifier.weight(1f)
            )

            TextButton(onClick = onClose) {
                Text("返回APP", color = Color(0xFF007AFF), fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        PanelSlider("Dock 高度", GlassConfig.DOCK_HEIGHT, 20f, 600f, decimals = 1, step = 0.5f) { GlassConfig.DOCK_HEIGHT = it }
        PanelSlider("Dock 宽度", GlassConfig.DOCK_WIDTH, 100f, 500f, decimals = 1, step = 0.5f) { GlassConfig.DOCK_WIDTH = it }
        PanelSlider("上下位置", GlassConfig.DOCK_OFFSET_Y, -900f, 900f, damp = 0.35f, decimals = 1, step = 0.5f) { GlassConfig.DOCK_OFFSET_Y = it }
        PanelSlider("圆角", GlassConfig.DOCK_CORNER, 0f, 80f, decimals = 1, step = 0.5f) { GlassConfig.DOCK_CORNER = it }

        Spacer(Modifier.height(4.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("黑遮罩", fontSize = 13.sp, color = Color(0xFF1C1C1E))
            Spacer(Modifier.weight(1f))
            OverlayToggle(
                selected = { GlassConfig.DOCK_MASK_DARK },
                onSelect = { GlassConfig.DOCK_MASK_DARK = it }
            )
        }

        Spacer(Modifier.height(4.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = {
                GlassConfig.DOCK_HEIGHT = 106f
                GlassConfig.DOCK_WIDTH = 355f
                GlassConfig.DOCK_OFFSET_Y = 5.5f
                GlassConfig.DOCK_CORNER = 42f
                GlassConfig.SURFACE = 0.5f
            }) {
                Text("恢复默认", color = Color(0xFF007AFF), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PanelSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    damp: Float = 1f,
    decimals: Int = 0,
    step: Float = 0f,
    onChange: (Float) -> Unit
) {
    var editing by remember { mutableStateOf(false) }

    fun snap(v: Float): Float =
        if (step > 0f) (v / step).roundToInt() * step else v

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 13.sp, color = Color(0xFF1C1C1E))
            Spacer(Modifier.weight(1f))
            Text(
                if (decimals > 0) "%.${decimals}f".format(value) else "${value.roundToInt()}",
                fontSize = 12.sp,
                color = Color(0xFF007AFF),
                modifier = Modifier.clickable { editing = true }
            )
        }

        if (editing) {
            InlineNumberKeyboard(
                initial = if (decimals > 0) "%.${decimals}f".format(value) else value.roundToInt().toString(),
                onConfirm = { input ->
                    input.toFloatOrNull()?.let { onChange(snap(it.coerceIn(min, max))) }
                    editing = false
                },
                onCancel = { editing = false }
            )
        } else {
            Slider(
                value = value,
                onValueChange = { target ->
                    val snapped = snap(target)
                    if (damp >= 1f) onChange(snapped)
                    else onChange(value + (snapped - value) * damp)
                },
                valueRange = min..max,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF007AFF),
                    inactiveTrackColor = Color(0xFFE0E0E5)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 内嵌数字键盘（macOS 风格，悬浮窗内用）。
 * 首次点数字键会清空原值。
 */
@Composable
private fun InlineNumberKeyboard(
    initial: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    var touched by remember { mutableStateOf(false) }

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
            "-" -> {
                if (!touched || text.isEmpty() || text == "0") text = "-"
                touched = true
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

    val keyShape = RoundedCornerShape(10.dp)
    val keyBg = Color(0xFFECECEE)

    Column(Modifier.fillMaxWidth()) {
        // 显示区
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text.ifEmpty { "0" },
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1C1C1E),
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(8.dp))

        val rows = listOf(
            listOf("7", "8", "9"),
            listOf("4", "5", "6"),
            listOf("1", "2", "3"),
            listOf("-", "0", ".", "⌫")
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { key ->
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(keyShape)
                                .background(keyBg)
                                .clickable { press(key) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(key, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1C1C1E))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(keyShape)
                    .background(Color(0xFFF2F2F4))
                    .clickable { onCancel() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("取消", fontSize = 15.sp, color = Color(0xFF8A8A8E))
            }

            Box(
                Modifier
                    .weight(1f)
                    .clip(keyShape)
                    .background(Color(0xFF007AFF))
                    .clickable { onConfirm(text) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("确定", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

@Composable
private fun TrafficDot(color: Color) {
    Box(
        Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}
