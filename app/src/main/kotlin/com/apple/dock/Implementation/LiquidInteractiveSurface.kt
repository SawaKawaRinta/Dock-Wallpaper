package com.apple.dock.Implementation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.colorFilter
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.tanh
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asAndroidPath

/**
 * 液态玻璃交互表面。
 *
 * 边缘（iOS 26 Dock 样式）：
 * - 黑边：左右两条连续曲率弧线（与玻璃本体同轮廓），描边样式，
 *   透明度固定 100%，两端渐隐，仅粗细可调。画在全部玻璃内容之上。
 * - 白边：上下纯白高光，带高斯模糊向内拓展（粗细 / 模糊度 / 模糊范围）
 */
@Composable
fun LiquidInteractiveSurface(
    menuBackdrop: Backdrop,
    shape: Shape,
    modifier: Modifier = Modifier,
    blurBase: Float = 20f,
    blurPress: Float = 8f,
    lensBase: Float = 24f,
    lensPress: Float = 10f,
    highlightBase: Float = 0.5f,
    highlightPress: Float = 0.4f,
    surfaceAlphaBase: Float = 0.30f,
    surfaceAlphaPress: Float = 0.15f,
    lightRadiusFactor: Float = 0.45f,
    maxStretch: Float = 0.006f,
    stretchThreshold: Float = 30f,
    chromaticIntensity: Float = 1f,
    refractBand: Float = 0.2f,
    depth: Float = 0.48f,
    shadowAlpha: Float = 0.55f,
    shadowRange: Float = 12f,
    shadowOffsetY: Float = 9f,
    edgeHighlightWidth: Float = 0f,
    edgeHighlightAlpha: Float = 0.45f,
    edgeHighlightBlur: Float = 0f,
    edgeHighlightRange: Float = 0f,
    edgeSideWidth: Float = 0f,
    edgeSideAlpha: Float = 1f,
    edgeSideBlur: Float = 0f,
    edgeCornerRadius: Float = 0f,
    twist: Float = 1f,
    edgeHighlightLength: Float = 50f, edgeSideLength: Float = 50f,
edgeGlowAlpha: Float = 0.8f,
edgeOutlineEnabled: Boolean = false,
edgeOutlineAlpha: Float = 0.5f,
edgeOutlineBlur: Float = 8f,
edgeSideEnabled: Boolean = true,
edgeHighlightEnabled: Boolean = true,
highlightEnabled: Boolean = true,
    saturation: Float = 1f,
    brightness: Float = 0f,
    contrast: Float = 1f,
    pressScale: Float = 0.05f,
    dropShadowAlpha: Float = 0.12f,
    dropShadowRange: Float = 16f,
    onDragDelta: ((dx: Float, dy: Float) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    val pressSpec = spring<Float>(
        dampingRatio = 0.45f,
        stiffness = 620f,
        visibilityThreshold = 0.001f
    )
    val positionSpec = spring<Offset>(
        dampingRatio = 0.6f,
        stiffness = 500f,
        visibilityThreshold = Offset.VisibilityThreshold
    )
    val stretchSpec = spring<Float>(
        dampingRatio = 0.45f,
        stiffness = 550f,
        visibilityThreshold = 0.001f
    )

    val press = remember { Animatable(0f, 0.001f) }
    val pointer = remember {
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    }
    val stretchX = remember { Animatable(1f, 0.001f) }
    var start by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .graphicsLayer {
                val dragDx = pointer.value.x - start.x
                val dragDy = pointer.value.y - start.y
                translationX = dragDx * 0.03f
                translationY = dragDy * 0.03f
                scaleX = stretchX.value
                scaleY = 1f
            }
            .drawWithContent {
                drawContent()

                // 黑边：左右两条连续曲率弧线，描边，画在全部玻璃内容之上，两端渐隐
                // 黑边：沿轮廓描边 + SweepGradient 弧长控制（左右段）
                if (edgeSideEnabled && edgeSideWidth > 0f) {
                    val sW = edgeSideWidth.dp.toPx()

                    val outline = shape.createOutline(size, layoutDirection, this)
                    val path = when (outline) {
                        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                        is Outline.Generic -> outline.path
                    }

                    val colors = (0..72).map { i ->
                        Color.Black.copy(
                            alpha = edgeMask(i * 5f, floatArrayOf(0f, 180f), edgeSideLength, 0.4f, 15f) * edgeSideAlpha
                        )
                    }
                    val brush = Brush.sweepGradient(colors, center = Offset(size.width / 2f, size.height / 2f))
                    drawPath(path, brush, style = Stroke(width = sW))
                }

                // 黑色描边 + 内黑模糊（可开关，全轮廓）
                if (edgeOutlineEnabled) {
                    val outlineAlpha = edgeOutlineAlpha.coerceIn(0f, 1f)
                    val blurRadius = edgeOutlineBlur.dp.toPx().coerceAtLeast(0f)
                    val outlineW = 1.5f.dp.toPx()

                    val ol = shape.createOutline(size, layoutDirection, this)
                    val olPath = when (ol) {
                        is Outline.Rectangle -> Path().apply { addRect(ol.rect) }
                        is Outline.Rounded -> Path().apply { addRoundRect(ol.roundRect) }
                        is Outline.Generic -> ol.path
                    }

                    val nativeCanvas = drawContext.canvas.nativeCanvas
                    val androidPath = olPath.asAndroidPath()

                    // 裁剪到 Dock 内部，模糊和描边都不超出 Dock
                    nativeCanvas.save()
                    nativeCanvas.clipPath(androidPath)
                    try {
                        // 内黑模糊（先画，在描边下面）
                        if (blurRadius > 0.1f) {
                            nativeCanvas.drawPath(androidPath, android.graphics.Paint().apply {
                                isAntiAlias = true
                                style = android.graphics.Paint.Style.STROKE
                                strokeWidth = outlineW + 2f * blurRadius
                                color = android.graphics.Color.argb((outlineAlpha * 140).toInt(), 0, 0, 0)
                                maskFilter = android.graphics.BlurMaskFilter(
                                    blurRadius,
                                    android.graphics.BlurMaskFilter.Blur.NORMAL
                                )
                            })
                        }

                        // 黑色描边（后画，在上面）
                        nativeCanvas.drawPath(androidPath, android.graphics.Paint().apply {
                            isAntiAlias = true
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = outlineW
                            color = android.graphics.Color.argb((outlineAlpha * 255).toInt(), 0, 0, 0)
                        })
                    } finally {
                        nativeCanvas.restore()
                    }
                }
            }
            .drawBackdrop(
                backdrop = menuBackdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    buildGlassColorFilter(saturation, brightness, contrast)?.let { colorFilter(it) }
                    highRadiusBlur(blurBase)

                    val band = refractBand.coerceIn(0.02f, 1f)
                    val bandPx = size.minDimension * 0.5f * band
                    val lensAmount = lensBase * twist
                    // 折射越界（屏幕外无内容）时 shader 会回退到本像素
                    refractionLens(
                        refractionHeight = bandPx,
                        refractionAmount = lensAmount,
                        depthEffect = depth > 0.4f,
                        chromaticAberration = chromaticIntensity > 0f
                    )
                },
                highlight = {
                    if (highlightEnabled) {
                        Highlight.Default.copy(style = HighlightStyle.Default(angle = 90f), 
                            alpha = highlightBase
                        )
                    } else null
                },
                shadow = if (dropShadowAlpha > 0f) {
                    {
                        Shadow(
                            radius = dropShadowRange.dp,
                            color = Color.Black.copy(alpha = dropShadowAlpha)
                        )
                    }
                } else null,
                innerShadow = {
                    InnerShadow(
                        color = Color.White,
                        radius = shadowRange.dp,
                        offset = DpOffset(0.dp, shadowOffsetY.dp),
                        alpha = shadowAlpha
                    )
                },
                onDrawSurface = {
                    drawRect(
                        Color.White.copy(surfaceAlphaBase)
                    )

                    // 上下白边：真实高斯模糊（BlurMaskFilter），从黑边内侧向内扩散
                    if (edgeHighlightEnabled && edgeHighlightWidth > 0f) {
                        val hlW = edgeHighlightWidth.dp.toPx()
                        val rangePx = edgeHighlightRange.dp.toPx()
                        val blurAmt = edgeHighlightBlur.coerceIn(0f, 1f)
                        val blurRadius = (rangePx * blurAmt).coerceAtLeast(0f)
                        val sW = edgeSideWidth.dp.toPx()
                        val base = sW + hlW / 2f

                        val outline = shape.createOutline(
                            Size(size.width - 2f * base, size.height - 2f * base),
                            layoutDirection,
                            this
                        )
                        val path = when (outline) {
                            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                            is Outline.Generic -> outline.path
                        }
                        path.translate(Offset(base, base))

                        val center = Offset(size.width / 2f, size.height / 2f)
                        val baseMask = { i: Int ->
                            edgeMask(i * 5f, floatArrayOf(90f, 270f), edgeHighlightLength, 1.1f, 15f)
                        }
                        // 锐利白线颜色
                        val crispArgb = IntArray(73) { i ->
                            val a = (baseMask(i) * edgeHighlightAlpha * 255f).toInt().coerceIn(0, 255)
                            android.graphics.Color.argb(a, 255, 255, 255)
                        }
                        // 高斯辉光颜色（提透明度，补偿糊开后的衰减）
                        val glowArgb = IntArray(73) { i ->
                            val a = (baseMask(i) * edgeGlowAlpha * 255f).toInt().coerceIn(0, 255)
                            android.graphics.Color.argb(a, 255, 255, 255)
                        }

                        val nativeCanvas = drawContext.canvas.nativeCanvas
                        val androidPath = path.asAndroidPath()

                        // 先画白色高斯辉光（在白线下面，向内扩散）
                        if (blurRadius > 0.1f) {
                            nativeCanvas.drawPath(androidPath, android.graphics.Paint().apply {
                                isAntiAlias = true
                                style = android.graphics.Paint.Style.STROKE
                                strokeWidth = hlW + 2f * blurRadius
                                shader = android.graphics.SweepGradient(center.x, center.y, glowArgb, null)
                                maskFilter = android.graphics.BlurMaskFilter(
                                    blurRadius,
                                    android.graphics.BlurMaskFilter.Blur.NORMAL
                                )
                            })
                        }

                        // 再画锐利白线（盖在辉光上面）
                        nativeCanvas.drawPath(androidPath, android.graphics.Paint().apply {
                            isAntiAlias = true
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = hlW
                            shader = android.graphics.SweepGradient(center.x, center.y, crispArgb, null)
                        })
                    }
                },
                layerBlock = {
                    val dragDx = pointer.value.x - start.x
                    val dragDy = pointer.value.y - start.y
                    translationX = dragDx * 0.02f
                    translationY = dragDy * 0.02f
                    scaleX = stretchX.value
                    scaleY = 1f
                }
            )
            // ===== 统一手势：整个区域都能按、都能拖 =====
            .pointerInput(enabled, onClick, maxStretch, stretchThreshold) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    start = down.position
                    scope.launch { press.snapTo(0.45f) }
                    scope.launch { press.animateTo(1f, pressSpec) }
                    scope.launch { pointer.snapTo(down.position) }

                    var dragged = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val dx = change.position.x - down.position.x
                        if (!dragged && abs(dx) > viewConfiguration.touchSlop) {
                            dragged = true
                        }

                        if (dragged) {
                            change.consume()
                            scope.launch { pointer.snapTo(change.position) }
                            onDragDelta?.invoke(
                                change.positionChange().x,
                                change.positionChange().y
                            )

                            val absDx = abs(dx)
                            if (absDx > stretchThreshold) {
                                val extra = (absDx - stretchThreshold).coerceAtMost(200f)
                                val t = tanh(extra / 150f)
                                scope.launch { stretchX.snapTo(1f + maxStretch * t) }
                            } else {
                                scope.launch { stretchX.snapTo(1f) }
                            }
                        }
                    }

                    scope.launch { press.animateTo(0f, pressSpec) }
                    scope.launch { pointer.animateTo(start, positionSpec) }
                    scope.launch { stretchX.animateTo(1f, stretchSpec) }

                    if (!dragged) {
                        onClick?.invoke()
                    }
                }
            }
            .clip(shape),
        content = content
    )
}

/** 沿轮廓角度遮罩：span 控制每段弧覆盖的角度（往外延展），中心实心、边缘 smoothstep 渐隐。 */
private fun edgeMask(deg: Float, centers: FloatArray, span: Float, plateScale: Float, fall: Float): Float {
    val plate = span.coerceIn(0f, 100f) * plateScale
    val half = plate + fall
    var best = 0f
    for (c in centers) {
        var d = abs(deg - c) % 360f
        if (d > 180f) d = 360f - d
        val m = when {
            d <= plate -> 1f
            d >= half -> 0f
            else -> {
                val t = (half - d) / fall
                t * t * (3f - 2f * t)
            }
        }
        if (m > best) best = m
    }
    return best
}
