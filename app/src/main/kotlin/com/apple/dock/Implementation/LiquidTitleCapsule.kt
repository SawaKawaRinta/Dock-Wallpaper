package com.apple.dock.Implementation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule

/**
 * 通用标题胶囊：液态玻璃 + 拉扯变形 + 白光跟随 + Q 弹回弹。
 */
@Composable
fun LiquidTitleCapsule(
    text: String,
    menuBackdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val titleCapsule = remember { Capsule() }

    LiquidInteractiveSurface(
        menuBackdrop = menuBackdrop,
        shape = titleCapsule,
        modifier = modifier,
        blurBase = GlassConfig.BLUR,
        blurPress = GlassConfig.BLUR_PRESS,
        lensBase = GlassConfig.LENS,
        lensPress = GlassConfig.LENS_PRESS,
        highlightBase = GlassConfig.HIGHLIGHT,
        highlightPress = GlassConfig.HIGHLIGHT_PRESS,
        surfaceAlphaBase = GlassConfig.SURFACE,
        surfaceAlphaPress = GlassConfig.SURFACE_PRESS,
        lightRadiusFactor = 0.6f,
        maxStretch = 0.006f
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 5.dp)) {
            Text(
                text = text,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
