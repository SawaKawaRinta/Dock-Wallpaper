package com.apple.dock

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.apple.dock.Implementation.GlassBackground
import com.apple.dock.Implementation.GlassConfig
import com.apple.dock.Implementation.GlassDot
import com.apple.dock.Implementation.LiquidGlassButton
import com.apple.dock.Implementation.LiquidGlassIconButton
import com.apple.dock.Implementation.LiquidGlassToggle
import com.apple.dock.Implementation.LiquidSlider
import com.apple.dock.Implementation.liquidGlass
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.tanh

/**
 * 悬浮窗里的「液态玻璃控制面板」。
 *
 * 这个 Composable 只在窗口内铺满自己，不要再 fillMaxSize：
 * WindowManager 的触摸区域就是窗口边界，窗口必须贴着卡片尺寸，
 * 否则卡片以外的一大片屏幕都会变成触摸死区。
 *
 * 绘制层级（顺序很关键）：
 *   Box
 *     ├─ GlassBackground      与卡片同尺寸的背景内容，记录到 backgroundBackdrop
 *     └─ 玻璃卡片             折射 backgroundBackdrop，自身记录到 panelBackdrop
 *          └─ 卡片内控件       折射 background + panel 的组合
 */
@Composable
fun OverlayGlassContent(
    wallpaper: Bitmap? = null,
    sidePaddingDp: Float = 16f,
    verticalPaddingDp: Float = 16f,
    onClose: () -> Unit = { },
    onDrag: (dx: Float, dy: Float) -> Unit = { _, _ -> }
) {
    val backgroundBackdrop = rememberLayerBackdrop()
    val panelBackdrop = rememberLayerBackdrop()
    val childBackdrop = rememberCombinedBackdrop(backgroundBackdrop, panelBackdrop)

    val scope = rememberCoroutineScope()

    // ===== 拖拽橡皮筋形变 =====
    val stretchX = remember { Animatable(1f) }
    val stretchY = remember { Animatable(1f) }
    val stretchSpec = spring<Float>(dampingRatio = 0.5f, stiffness = 400f)

    // ===== 入场 / 退场动画 =====
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 320f))
    }

    fun closeWithAnimation() {
        scope.launch {
            enter.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 700f))
            onClose()
        }
    }

    // ===== 状态 =====
    val sliderValues = remember { mutableStateListOf(52f, 42f, 100f) }
    var chromaticOn by remember { mutableStateOf(true) }
    var animeOn by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sidePaddingDp.dp, vertical = verticalPaddingDp.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val e = enter.value
                    alpha = e
                    scaleX = lerp(0.88f, 1f, e) * stretchX.value
                    scaleY = lerp(0.88f, 1f, e) * stretchY.value
                    translationY = lerp(48f, 0f, e)
                }
        ) {
            // ---------- 背景内容（与卡片同尺寸、同圆角） ----------
            GlassBackground(
                bitmap = wallpaper,
                tintAlpha = 0.62f,
                spotAlpha = 0.9f,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(GlassConfig.RADIUS.dp))
                    .layerBackdrop(backgroundBackdrop)
            )

            // ---------- 玻璃卡片 ----------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        backdrop = backgroundBackdrop,
                        shape = RoundedCornerShape(GlassConfig.RADIUS.dp),
                        surfaceAlpha = GlassConfig.SURFACE
                    )
                    .layerBackdrop(panelBackdrop)
                    .padding(18.dp)
            ) {

                // ============ 顶部：红绿灯把手 + 标题 + 关闭 ============
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .pointerInput(animeOn) {
                                var dragOffsetX = 0f
                                var dragOffsetY = 0f

                                detectDragGestures(
                                    onDragStart = {
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                    },
                                    onDragEnd = {
                                        scope.launch { stretchX.animateTo(1f, stretchSpec) }
                                        scope.launch { stretchY.animateTo(1f, stretchSpec) }
                                    },
                                    onDragCancel = {
                                        scope.launch { stretchX.animateTo(1f, stretchSpec) }
                                        scope.launch { stretchY.animateTo(1f, stretchSpec) }
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.x, dragAmount.y)

                                    dragOffsetX += dragAmount.x
                                    dragOffsetY += dragAmount.y

                                    if (!animeOn) return@detectDragGestures

                                    val absX = abs(dragOffsetX)
                                    val absY = abs(dragOffsetY)
                                    val dead = GlassConfig.STRETCH_DEAD_ZONE
                                    val maxStretch = GlassConfig.STRETCH_MAX

                                    if (absX > dead) {
                                        val extra = (absX - dead).coerceAtMost(220f)
                                        val t = tanh(extra / 110f)
                                        scope.launch {
                                            stretchX.snapTo(1f + maxStretch * t)
                                            stretchY.snapTo(1f - maxStretch * t * 0.45f)
                                        }
                                    } else if (absY > dead) {
                                        val extra = (absY - dead).coerceAtMost(220f)
                                        val t = tanh(extra / 110f)
                                        scope.launch {
                                            stretchY.snapTo(1f + maxStretch * t)
                                            stretchX.snapTo(1f - maxStretch * t * 0.45f)
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassDot(color = Color(0xFFFF5F56), backdrop = childBackdrop)
                        GlassDot(color = Color(0xFFFFBD2E), backdrop = childBackdrop)
                        GlassDot(color = Color(0xFF27C93F), backdrop = childBackdrop)
                    }

                    Spacer(Modifier.width(12.dp))

                    Text(
                        text = "液态玻璃",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(Modifier.weight(1f))

                    LiquidGlassIconButton(
                        backdrop = childBackdrop,
                        onClick = { closeWithAnimation() }
                    ) {
                        Text(
                            text = "✕",
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ============ 滑块区 ============
                Text(
                    text = "折射调节",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(10.dp))

                sliderValues.forEachIndexed { index, value ->
                    Column(modifier = Modifier.padding(bottom = 14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (index) {
                                    0 -> "模糊"
                                    1 -> "折射"
                                    else -> "色散"
                                },
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${value.toInt()}%",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LiquidSlider(
                            value = { sliderValues[index] },
                            onValueChange = { newValue -> setSlider(index, newValue, sliderValues) },
                            valueRange = 0f..100f,
                            visibilityThreshold = 0.1f,
                            backdrop = childBackdrop,
                            modifier = Modifier.height(40.dp)
                        )
                    }
                }

                // ============ 开关区 ============
                Text(
                    text = "效果开关",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(10.dp))

                LiquidGlassToggle(
                    checked = chromaticOn,
                    onCheckedChange = {
                        chromaticOn = it
                        GlassConfig.CHROMATIC_INTENSITY = if (it) 1f else 0f
                    },
                    backdrop = childBackdrop,
                    label = "色散折射"
                )

                Spacer(Modifier.height(10.dp))

                LiquidGlassToggle(
                    checked = animeOn,
                    onCheckedChange = { animeOn = it },
                    backdrop = childBackdrop,
                    label = "弹性动画"
                )

                Spacer(Modifier.height(18.dp))

                // ============ 底部按钮 ============
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LiquidGlassButton(
                        backdrop = childBackdrop,
                        onClick = {
                            sliderValues[0] = 52f
                            sliderValues[1] = 42f
                            sliderValues[2] = 100f
                            GlassConfig.BLUR = 22f
                            GlassConfig.LENS = 34f
                            GlassConfig.CHROMATIC_INTENSITY = 1f
                            chromaticOn = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "重置", color = Color.White, fontSize = 14.sp)
                    }

                    LiquidGlassButton(
                        backdrop = childBackdrop,
                        onClick = { closeWithAnimation() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "关闭", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/** 滑块 → 玻璃光学参数，让面板上的调节真正生效 */
private fun setSlider(index: Int, newValue: Float, values: MutableList<Float>) {
    values[index] = newValue
    when (index) {
        0 -> GlassConfig.BLUR = 2f + newValue / 100f * 38f
        1 -> GlassConfig.LENS = newValue / 100f * 80f
        else -> GlassConfig.CHROMATIC_INTENSITY = newValue / 100f
    }
}
