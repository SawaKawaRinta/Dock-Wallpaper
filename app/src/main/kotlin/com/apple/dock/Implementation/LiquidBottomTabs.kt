package com.apple.dock.Implementation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

internal val LocalLiquidBottomTabScale = staticCompositionLocalOf { { 1f } }
internal val LocalLiquidBottomTabClick = staticCompositionLocalOf<(Int) -> Unit> { {} }

@Composable
fun RowScope.LiquidBottomTab(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidBottomTabScale.current
    val onClick = LocalLiquidBottomTabClick.current

    Column(
        modifier
            .clip(Capsule())
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = { onClick(index) }
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

/**
 * 液态玻璃底部导航（照搬 Liquid_Glass_PageProject）。
 *
 * 三层结构：
 *   1. 玻璃容器（静止外观）
 *   2. 隐藏采样层（alpha=0，只用来给顶部胶囊提供折射内容）
 *   3. 顶部滑动胶囊（跟随手指弹性滑动）
 */
@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    // 显式订阅 GlassConfig，滑块一改立刻重组
    val cfgBlur = GlassConfig.BLUR
    val cfgLens = GlassConfig.LENS
    val cfgLensPress = GlassConfig.LENS_PRESS
    val cfgSurface = GlassConfig.SURFACE
    val cfgSurfacePress = GlassConfig.SURFACE_PRESS
    val cfgChromatic = GlassConfig.CHROMATIC_INTENSITY

    val containerColor = Color.White.copy(cfgSurface)
    val tabsBackdrop = rememberLayerBackdrop()
    val capsule = remember { Capsule() }
    val cubicEase = remember { CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f) }
    val panelSpec = remember { tween<Float>(400, easing = cubicEase) }

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()

        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, panelSpec)
                    }
                    onTabSelected(targetIndex)
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        val clickHandler: (Int) -> Unit = remember(dampedDragAnimation) {
            { index ->
                dampedDragAnimation.animateToValue(index.toFloat())
                onTabSelected(index)
            }
        }

        CompositionLocalProvider(
            LocalLiquidBottomTabClick provides clickHandler
        ) {
            // ===== 底层：玻璃容器 =====
            Row(
                Modifier
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { capsule },
                        effects = {
                            val chromaticOn = cfgChromatic > 0f
                            val scale = if (chromaticOn) cfgChromatic else 1f
                            vibrancy()
                            highRadiusBlur(cfgBlur)
                            val lensAmt = cfgLens * scale
                            refractionLens(lensAmt, lensAmt, chromaticAberration = chromaticOn)
                        },
                        layerBlock = {
                            val progress = dampedDragAnimation.pressProgress
                            val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                            scaleX = scale
                            scaleY = scale
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(64.dp)
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )

            // ===== 隐藏采样层：给顶部胶囊提供可折射内容 =====
            CompositionLocalProvider(
                LocalLiquidBottomTabScale provides {
                    lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                }
            ) {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffset }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { capsule },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                val chromaticOn = cfgChromatic > 0f
                                val scale = if (chromaticOn) cfgChromatic else 1f
                                vibrancy()
                                highRadiusBlur(cfgBlur)
                                val lensAmt = cfgLens * progress * scale
                                refractionLens(
                                    lensAmt,
                                    lensAmt,
                                    chromaticAberration = chromaticOn
                                )
                            },
                            highlight = {
                                Highlight.Default.copy(style = HighlightStyle.Default(angle = 90f), alpha = dampedDragAnimation.pressProgress)
                            },
                            onDrawSurface = { drawRect(containerColor) }
                        )
                        .then(interactiveHighlight.modifier)
                        .height(56.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }

            // ===== 顶层：滑动胶囊 =====
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .offset {
                        val x = if (isLtr) {
                            dampedDragAnimation.value * tabWidth + panelOffset
                        } else 0f
                        IntOffset(x.roundToInt(), 0)
                    }
                    .then(interactiveHighlight.gestureModifier)
                    .then(dampedDragAnimation.modifier)
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                        shape = { capsule },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            val chromaticOn = cfgChromatic > 0f
                            val scale = if (chromaticOn) cfgChromatic else 1f
                            refractionLens(
                                cfgLens * progress * scale,
                                cfgLensPress * progress * scale,
                                chromaticAberration = chromaticOn
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(style = HighlightStyle.Default(angle = 90f), alpha = dampedDragAnimation.pressProgress * 0.8f)
                        },
                        layerBlock = {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            val v = dampedDragAnimation.velocity / 10f
                            scaleX /= 1f - (v * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (v * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            val progress = dampedDragAnimation.pressProgress
                            drawRect(
                                Color.White.copy(cfgSurface + cfgSurfacePress * progress)
                            )
                        }
                    )
                    .height(56.dp)
                    .fillMaxWidth(1f / tabsCount)
            )
        }
    }
}
