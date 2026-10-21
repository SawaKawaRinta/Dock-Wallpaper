package com.apple.dock.Implementation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.colorFilter
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

/**
 * 基础玻璃表面（静态，无交互）。
 *
 * 折射模型对齐 DesktopLiquidGlassHook：
 *   - refractionHeight = 折射带比例 × 短边一半   （hook: glassBand）
 *   - refractionAmount = 折射位移量              （hook: glassShift）
 *   - depthEffect      = REFRACT_DEPTH > 0.4     （hook: glassDepth）
 *   - chromaticAberration = 色散开关             （hook: glassChroma）
 *
 * ⚠️ 所有参数必须由调用方在 @Composable 内显式读取后传入。
 * 默认参数在编译器生成的 $default 方法里求值，不在 Composable 作用域，
 * 读 State 不会注册快照订阅，滑块拖动不会重组，绘制缓存也就不会刷新。
 */
fun Modifier.glassSurface(
    backdrop: Backdrop,
    shape: Shape,
    pressProgress: () -> Float = { 0f },
    surfaceAlpha: Float = 0f,
    surfaceAlphaPress: Float = 0.15f,
    blur: Float = 22f,
    blurPress: Float = 8f,
    lens: Float = 32f,
    lensPress: Float = 18f,
    chromatic: Float = 1f,
    saturation: Float = 1f,
    brightness: Float = 0f,
    contrast: Float = 1f,
    highlight: Float = 0.60f,
    highlightPress: Float = 0.85f,
    refractBand: Float = 0.55f,
    depth: Float = 0f,
    shadowAlpha: Float = 0.55f,
    shadowRange: Float = 12f,
    shadowOffsetY: Float = 9f,
    edgeHighlightWidth: Float = 6f,
    edgeHighlightAlpha: Float = 0.45f,
    edgeHighlightBlur: Float = 8f,
    edgeSideWidth: Float = 4f,
    edgeSideAlpha: Float = 0.76f,
    edgeSideBlur: Float = 8f,
): Modifier {
    val chromaticOn = chromatic > 0f

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {

            vibrancy()
            buildGlassColorFilter(saturation, brightness, contrast)?.let { colorFilter(it) }
            highRadiusBlur(blur)

            val band = refractBand.coerceIn(0.02f, 1f)
            val bandPx = size.minDimension * 0.5f * band
            // 折射越界时 shader 回退到本像素
            refractionLens(
                refractionHeight = bandPx,
                refractionAmount = lens,
                depthEffect = depth > 0.4f,
                chromaticAberration = chromaticOn
            )
        },
        highlight = {

            Highlight.Default.copy(style = HighlightStyle.Default(angle = 90f), alpha = highlight)
        },
        shadow = {
            Shadow(
                radius = 18.dp,
                color = Color.Black.copy(alpha = 0.14f)
            )
        },
        innerShadow = {
            InnerShadow(
                color = Color.White,
                radius = shadowRange.dp,
                offset = DpOffset(0.dp, shadowOffsetY.dp),
                alpha = shadowAlpha
            )
        },
        onDrawSurface = {

            drawRect(Color.White.copy(surfaceAlpha))
        }
    )
}

/**
 * 液态玻璃按钮：复用 LiquidInteractiveSurface，
 * 按下拖动有拉扯形变、白光跟随、弹性回弹，同时支持点击。
 */
@Composable
fun GlassButton(
    text: String,
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    blur: Float = 22f,
    blurPress: Float = 8f,
    lens: Float = 32f,
    lensPress: Float = 18f,
    chromatic: Float = 1f,
    saturation: Float = 1f,
    brightness: Float = 0f,
    contrast: Float = 1f,
    surfaceAlpha: Float = 0.30f,
    surfaceAlphaPress: Float = 0.15f,
    refractBand: Float = 0.55f,
    depth: Float = 0f,
    shadowAlpha: Float = 0.55f,
    shadowRange: Float = 12f,
    shadowOffsetY: Float = 9f,
    edgeHighlightWidth: Float = 6f,
    edgeHighlightAlpha: Float = 0.45f,
    edgeHighlightBlur: Float = 8f,
    edgeSideWidth: Float = 4f,
    edgeSideAlpha: Float = 0.76f,
    edgeSideBlur: Float = 8f,
    highlightBase: Float = 0.45f,
    highlightPress: Float = 0.40f
) {
    val shape = remember { Capsule() }

    LiquidInteractiveSurface(
        menuBackdrop = backdrop,
        shape = shape,
        modifier = modifier,
        blurBase = blur,
        blurPress = blurPress,
        lensBase = lens,
        lensPress = lensPress,
        highlightBase = highlightBase,
        highlightPress = highlightPress,
        surfaceAlphaBase = surfaceAlpha,
        surfaceAlphaPress = surfaceAlphaPress,
        lightRadiusFactor = 0.65f,
        maxStretch = 0.035f,
        stretchThreshold = 14f,
        chromaticIntensity = chromatic,
        saturation = saturation,
        brightness = brightness,
        contrast = contrast,
        refractBand = refractBand,
        depth = depth,
        shadowAlpha = shadowAlpha,
        shadowRange = shadowRange,
        shadowOffsetY = shadowOffsetY,
        onClick = onClick
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}
