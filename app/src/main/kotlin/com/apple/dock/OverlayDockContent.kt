package com.apple.dock

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.apple.dock.Implementation.GlassConfig
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle

/**
 * 悬浮 Dock：一层半透明遮罩。
 * 什么都不做，就是 50% 透明白，桌面直接透出来。
 */
@Composable
fun OverlayDockContent(
    wallpaper: Bitmap? = null,
    screenWidthPx: Int = 0,
    screenHeightPx: Int = 0,
    windowLeftPx: Int = 0,
    windowTopPx: Int = 0,
    windowWidthPx: Int = 0,
    windowHeightPx: Int = 0
) {
    val corner = GlassConfig.DOCK_CORNER
    val shape = remember(corner) {
        RoundedRectangle(cornerRadius = corner.dp, style = RoundedCornerStyle.Continuous)
    }

    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(
                (if (GlassConfig.DOCK_MASK_DARK) Color.Black else Color.White)
                    .copy(alpha = GlassConfig.SURFACE)
            )
    )
}
