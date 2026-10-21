package com.apple.dock

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 导入背景后的裁剪 / 位置选择页。
 *
 * 全屏显示原图，单指拖动、双指缩放，把想要显示的区域拖进屏幕，
 * 点"完成"后按当前可视区域从原图裁出最终壁纸。
 */
@Composable
fun BackgroundCropScreen(
    source: Bitmap,
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()

        val initialScale = remember(source) {
            max(containerW / source.width, containerH / source.height)
        }
        val minScale = initialScale
        val maxScale = initialScale * 5f

        var scale by remember(source) { mutableStateOf(initialScale) }
        var offsetX by remember(source) { mutableStateOf(0f) }
        var offsetY by remember(source) { mutableStateOf(0f) }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(source) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(minScale, maxScale)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
        ) {
            val imgW = source.width.toFloat()
            val imgH = source.height.toFloat()
            val drawW = imgW * scale
            val drawH = imgH * scale
            val centerX = size.width / 2f + offsetX
            val centerY = size.height / 2f + offsetY
            drawImage(
                image = source.asImageBitmap(),
                dstOffset = IntOffset(
                    (centerX - drawW / 2f).roundToInt(),
                    (centerY - drawH / 2f).roundToInt()
                ),
                dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt())
            )
        }

        // 顶部提示
        Text(
            "拖动图片选择显示位置 · 双指缩放",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
        )

        // 底部操作条
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("取消", color = Color.White, fontSize = 15.sp)
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF007AFF))
            ) {
                TextButton(
                    onClick = {
                        val result = cropBitmap(source, containerW, containerH, scale, offsetX, offsetY)
                        onConfirm(result)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("完成", color = Color.White, fontSize = 15.sp)
                }
            }
        }
    }
}

/** 把当前可视区域映射回原图坐标，裁出最终壁纸。 */
private fun cropBitmap(
    source: Bitmap,
    containerW: Float,
    containerH: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float
): Bitmap {
    val imgW = source.width
    val imgH = source.height
    val drawW = imgW * scale
    val drawH = imgH * scale
    val centerX = containerW / 2f + offsetX
    val centerY = containerH / 2f + offsetY
    val left = centerX - drawW / 2f
    val top = centerY - drawH / 2f

    val srcX = ((0f - left) / scale).roundToInt().coerceIn(0, imgW - 1)
    val srcY = ((0f - top) / scale).roundToInt().coerceIn(0, imgH - 1)
    val srcRight = ((containerW - left) / scale).roundToInt().coerceIn(srcX + 1, imgW)
    val srcBottom = ((containerH - top) / scale).roundToInt().coerceIn(srcY + 1, imgH)

    return Bitmap.createBitmap(source, srcX, srcY, srcRight - srcX, srcBottom - srcY)
}
