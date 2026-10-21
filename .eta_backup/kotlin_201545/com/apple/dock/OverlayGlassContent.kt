package com.apple.dock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apple.dock.Implementation.GlassConfig
import com.apple.dock.Implementation.LiquidSlider
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.tanh

@Composable
fun OverlayGlassContent(
    onClose: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit
) {
    val mockBackgroundBackdrop = rememberLayerBackdrop()
    val scope = rememberCoroutineScope()

    // 弹性形变引擎
    val stretchX = remember { Animatable(1f) }
    val stretchY = remember { Animatable(1f) }
    // dampingRatio = 0.5f 让回弹有更明显的橡皮筋感
    val stretchSpec = spring<Float>(dampingRatio = 0.5f, stiffness = 400f)

    // 用一个大全屏容器，让面板居中，这样放大的时候不会被外层截断
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(320.dp) // 卡片宽度
                .graphicsLayer {
                    scaleX = stretchX.value
                    scaleY = stretchY.value
                }
                .drawBackdrop(
                    backdrop = mockBackgroundBackdrop,
                    shape = { RoundedCornerShape(24.dp) },
                    effects = {
                        vibrancy()
                        blur(GlassConfig.BLUR)
                        lens(GlassConfig.LENS, GlassConfig.LENS, chromaticAberration = true)
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = GlassConfig.SURFACE))
                    }
                )
                .layerBackdrop(mockBackgroundBackdrop)
                .padding(16.dp)
        ) {
            // 渐变模拟背景
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF4A90E2), Color(0xFF50E3C2), Color(0xFFF5A623))
                        )
                    )
            )

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ================= 红绿灯拖拽把手 =================
                    Row(
                        modifier = Modifier
                            .pointerInput(Unit) {
                                var dragOffsetX = 0f
                                var dragOffsetY = 0f
                                
                                detectDragGestures(
                                    onDragStart = {
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                    },
                                    onDragEnd = {
                                        scope.launch {
                                            launch { stretchX.animateTo(1f, stretchSpec) }
                                            launch { stretchY.animateTo(1f, stretchSpec) }
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch {
                                            launch { stretchX.animateTo(1f, stretchSpec) }
                                            launch { stretchY.animateTo(1f, stretchSpec) }
                                        }
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.x, dragAmount.y)

                                    dragOffsetX += dragAmount.x
                                    dragOffsetY += dragAmount.y

                                    val absX = abs(dragOffsetX)
                                    val absY = abs(dragOffsetY)
                                    
                                    // ★ 拉伸上限调整为 25%，夸张的形变
                                    val maxStretch = 0.25f 

                                    if (absX > 20f) {
                                        val extra = (absX - 20f).coerceAtMost(200f)
                                        val t = tanh(extra / 100f) // 改小分母，让形变更快发生
                                        scope.launch {
                                            stretchX.snapTo(1f + maxStretch * t)
                                            stretchY.snapTo(1f - maxStretch * t * 0.4f)
                                        }
                                    } else if (absY > 20f) {
                                        val extra = (absY - 20f).coerceAtMost(200f)
                                        val t = tanh(extra / 100f)
                                        scope.launch {
                                            stretchY.snapTo(1f + maxStretch * t)
                                            stretchX.snapTo(1f - maxStretch * t * 0.4f)
                                        }
                                    }
                                }
                            }
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFFFF5F56)))
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF27C93F)))
                    }

                    // ================= 关闭按钮 =================
                    Text(
                        text = "✕",
                        fontSize = 20.sp,
                        color = Color(0xFF666666),
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures { onClose() }
                            }
                            .padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ================= 滑块列表 =================
                val sliderValues = remember { mutableStateListOf(20f, 40f, 60f, 80f, 100f) }

                sliderValues.forEachIndexed { index, value ->
                    Column(modifier = Modifier.padding(bottom = 16.dp)) {
                        Text(
                            text = "进度 ${index + 1}",
                            fontSize = 13.sp,
                            color = Color(0xFF333333),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        LiquidSlider(
                            value = { value },
                            onValueChange = { newValue: Float -> sliderValues[index] = newValue },
                            valueRange = 0f..100f,
                            visibilityThreshold = 0.1f,
                            backdrop = rememberCombinedBackdrop(mockBackgroundBackdrop, mockBackgroundBackdrop),
                            modifier = Modifier.height(40.dp)
                        )
                    }
                }
            }
        }
    }
}