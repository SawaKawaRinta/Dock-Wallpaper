package com.apple.dock.Implementation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * 背景层：只画壁纸本身，不叠任何彩色光斑 / 白色雾层。
 *
 * 支持壁纸亮度调整（ColorMatrix 缩放），只作用于壁纸，不影响 Dock 玻璃。
 */
@Composable
fun GlassWallpaperLayer(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    brightness: Float = 0f,           // -100..100，0 = 原始
    brightnessEnabled: Boolean = false
) {
    Box(modifier) {
        if (bitmap != null) {
            val colorFilter = if (brightnessEnabled && brightness != 0f) {
                val scale = (1f + brightness / 100f).coerceIn(0f, 2f)
                ColorFilter.colorMatrix(
                    ColorMatrix().apply {
                        setToScale(scale, scale, scale, 1f)
                    }
                )
            } else null
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFFD6DAE0),
                                Color(0xFFF3F5F8),
                                Color(0xFFDDE1E6)
                            )
                        )
                    )
            )
        }
    }
}
