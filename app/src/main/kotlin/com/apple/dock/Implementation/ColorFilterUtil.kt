package com.apple.dock.Implementation

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

/**
 * 色彩控制：饱和度 / 亮度 / 对比度（backdrop 1.0.6 没有 colorControls，自己算 ColorMatrix）。
 *
 * 三个值都是中性值时返回 null，避免白白多建一个 RenderEffect 节点。
 */
internal fun buildGlassColorFilter(
    saturation: Float,
    brightness: Float,
    contrast: Float
): ColorMatrixColorFilter? {
    val sat = saturation.coerceIn(0f, 3f)
    val bri = brightness.coerceIn(-1f, 1f)
    val con = contrast.coerceIn(0.1f, 3f)

    if (sat == 1f && bri == 0f && con == 1f) return null

    val m = ColorMatrix()
    m.setSaturation(sat)

    if (bri != 0f) {
        val b = (1f + bri).coerceIn(0f, 3f)
        val scaleM = ColorMatrix()
        scaleM.setScale(b, b, b, 1f)
        m.postConcat(scaleM)
    }

    if (con != 1f) {
        val t = (1f - con) * 127.5f
        m.postConcat(
            ColorMatrix(
                floatArrayOf(
                    con, 0f, 0f, 0f, t,
                    0f, con, 0f, 0f, t,
                    0f, 0f, con, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    return ColorMatrixColorFilter(m)
}
