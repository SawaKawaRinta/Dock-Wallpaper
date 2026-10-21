package com.apple.dock.Implementation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle

/**
 * App 内的 Dock 预览条。
 *
 * - 宽高用 requiredWidth / requiredHeight：不受父布局约束
 * - 高度以固定基准为中心变化（centerOffset 的 62 = 与悬浮窗统一的基准）
 * - 圆角变化时 key(corner) 重建，强制折射 shader 重算 cornerRadii
 * - 玻璃用 LiquidInteractiveSurface：按下缩放 + 拖动拉扯 + 弹性回弹
 */
@Composable
fun LiquidDock(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    // ===== 显式读取：滑块能不能实时生效的关键 =====
    val blur = GlassConfig.BLUR
    val blurPress = GlassConfig.BLUR_PRESS
    val lens = GlassConfig.LENS
    val lensPress = GlassConfig.LENS_PRESS
    val chromatic = GlassConfig.CHROMATIC_INTENSITY
    val saturation = GlassConfig.SATURATION
    val brightness = GlassConfig.BRIGHTNESS
    val contrast = GlassConfig.CONTRAST
    val surface = GlassConfig.SURFACE
    val surfacePress = GlassConfig.SURFACE_PRESS
    val highlight = GlassConfig.HIGHLIGHT
    val highlightPress = GlassConfig.HIGHLIGHT_PRESS
    val refractBand = GlassConfig.REFRACT_BAND
    val depth = GlassConfig.REFRACT_DEPTH
    val shadowAlpha = GlassConfig.SHADOW_ALPHA
    val shadowRange = GlassConfig.SHADOW_RANGE
    val shadowOffsetY = GlassConfig.SHADOW_OFFSET_Y
    val dropShadow = GlassConfig.DROP_SHADOW
    val dropShadowRange = GlassConfig.DROP_SHADOW_RANGE
    val pressScale = GlassConfig.PRESS_SCALE
    val edgeHlWidth = GlassConfig.EDGE_HIGHLIGHT_WIDTH
    val edgeHlAlpha = GlassConfig.EDGE_HIGHLIGHT_ALPHA
    val edgeHlBlur = GlassConfig.EDGE_HIGHLIGHT_BLUR
    val edgeHlRange = GlassConfig.EDGE_HIGHLIGHT_RANGE
    val edgeSideWidth = GlassConfig.EDGE_SIDE_WIDTH
    val edgeSideAlpha = GlassConfig.EDGE_SIDE_ALPHA
    val edgeSideBlur = GlassConfig.EDGE_SIDE_BLUR
val edgeGlowAlpha = GlassConfig.EDGE_GLOW_ALPHA
    val edgeOutlineEnabled = GlassConfig.EDGE_OUTLINE_ENABLED
    val edgeOutlineAlpha = GlassConfig.EDGE_OUTLINE_ALPHA
    val edgeOutlineBlur = GlassConfig.EDGE_OUTLINE_BLUR
    val edgeSideEnabled = GlassConfig.EDGE_SIDE_ENABLED
    val edgeHighlightEnabled = GlassConfig.EDGE_HIGHLIGHT_ENABLED
    val highlightEnabled = GlassConfig.HIGHLIGHT_ENABLED

    val corner = GlassConfig.DOCK_CORNER
    val twist = GlassConfig.TWIST
    val edgeHlLength = GlassConfig.EDGE_HL_LENGTH
    val edgeSideLength = GlassConfig.EDGE_SIDE_LENGTH
    val dockWidth = GlassConfig.DOCK_WIDTH
    val dockHeight = GlassConfig.DOCK_HEIGHT
    val offsetY = GlassConfig.DOCK_OFFSET_Y

    val density = LocalDensity.current

    val shape = remember(corner) {
        RoundedRectangle(cornerRadius = corner.dp, style = RoundedCornerStyle.Continuous)
    }

    BoxWithConstraints(
        modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 中心固定：按基准高度 104dp 底部对齐的位置算
        val centerOffset = (maxHeight / 2f) - 62.dp + offsetY.dp

        key(corner) {
            LiquidInteractiveSurface(
                menuBackdrop = backdrop,
                shape = shape,
                modifier = Modifier
                    .offset(y = centerOffset)
                    .requiredWidth(dockWidth.coerceIn(20f, 500f).dp)
                    .requiredHeight(dockHeight.coerceAtLeast(8f).dp),
                blurBase = blur,
                blurPress = blurPress,
                lensBase = lens,
                lensPress = lensPress,
                highlightBase = highlight,
                highlightPress = highlightPress,
                surfaceAlphaBase = 0f,
                surfaceAlphaPress = surfacePress,
                lightRadiusFactor = 0.55f,
                maxStretch = 0.05f,
                stretchThreshold = 12f,
                chromaticIntensity = chromatic,
                saturation = saturation,
                brightness = brightness,
                contrast = contrast,
                refractBand = refractBand,
                depth = depth,
                shadowAlpha = shadowAlpha,
                shadowRange = shadowRange,
                shadowOffsetY = shadowOffsetY,
                dropShadowAlpha = dropShadow,
                dropShadowRange = dropShadowRange,
                pressScale = pressScale,
                edgeHighlightWidth = edgeHlWidth,
                edgeHighlightAlpha = edgeHlAlpha,
                edgeHighlightBlur = edgeHlBlur,
                edgeHighlightRange = edgeHlRange,
                edgeSideWidth = edgeSideWidth,
                edgeSideAlpha = edgeSideAlpha,
                edgeSideBlur = edgeSideBlur,
edgeGlowAlpha = edgeGlowAlpha,
                edgeOutlineEnabled = edgeOutlineEnabled,
                edgeOutlineAlpha = edgeOutlineAlpha,
                edgeOutlineBlur = edgeOutlineBlur,
                edgeSideEnabled = edgeSideEnabled,
                edgeHighlightEnabled = edgeHighlightEnabled,
                highlightEnabled = highlightEnabled,
                edgeCornerRadius = corner,
                twist = twist,
                edgeHighlightLength = edgeHlLength, edgeSideLength = edgeSideLength,
                onDragDelta = { _, dy ->
                    GlassConfig.DOCK_OFFSET_Y += dy / density.density
                },
                onClick = onClick,
                enabled = false,
            )
        }
    }
}
