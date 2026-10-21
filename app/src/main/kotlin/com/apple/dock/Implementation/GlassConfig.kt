package com.apple.dock.Implementation

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object GlassConfig {

    // ===== 模糊（px） =====
    var BLUR by mutableStateOf(8f)
    var BLUR_PRESS by mutableStateOf(20f)

    // ===== 折射位移（px） =====
    var LENS by mutableStateOf(400f)
    var LENS_PRESS by mutableStateOf(30f)
    var TWIST by mutableStateOf(1.0f)

    // ===== 折射带 / 深度 =====
    var REFRACT_BAND by mutableStateOf(0.25f)
    var REFRACT_DEPTH by mutableStateOf(0.5f)

    // ===== 色散 =====
    var CHROMATIC_INTENSITY by mutableStateOf(0f)
    val CHROMATIC: Boolean get() = CHROMATIC_INTENSITY > 0f

    // ===== 色彩 =====
    var SATURATION by mutableStateOf(1.0f)
    var BRIGHTNESS by mutableStateOf(0f)
    var CONTRAST by mutableStateOf(1.0f)

    // ===== 边缘光 =====
    var HIGHLIGHT_ENABLED by mutableStateOf(true)
    var HIGHLIGHT by mutableStateOf(1.0f)
    var HIGHLIGHT_PRESS by mutableStateOf(0.60f)

    // ===== 内阴影 =====
    var SHADOW_ALPHA by mutableStateOf(0.25f)
    var SHADOW_RANGE by mutableStateOf(8f)
    var SHADOW_OFFSET_Y by mutableStateOf(4f)

    // ===== 投影 / 按压 =====
    var DROP_SHADOW by mutableStateOf(0.18f)
    var DROP_SHADOW_RANGE by mutableStateOf(24f)
    var PRESS_SCALE by mutableStateOf(0f)

    // ===== 白膜 =====
    var SURFACE by mutableStateOf(0.5f)
    var SURFACE_PRESS by mutableStateOf(0.18f)

    // ===== 白边 =====
    var EDGE_HIGHLIGHT_ENABLED by mutableStateOf(true)
    var EDGE_HIGHLIGHT_WIDTH by mutableStateOf(1.2f)
    var EDGE_HIGHLIGHT_ALPHA by mutableStateOf(1f)
    var EDGE_HIGHLIGHT_BLUR by mutableStateOf(0.75f)
    var EDGE_HIGHLIGHT_RANGE by mutableStateOf(5.0f)
    var EDGE_GLOW_ALPHA by mutableStateOf(0.8f)
    var EDGE_HL_LENGTH by mutableStateOf(50f)

    // ===== 黑边 =====
    var EDGE_SIDE_ENABLED by mutableStateOf(true)
    var EDGE_SIDE_WIDTH by mutableStateOf(1.2f)
    var EDGE_SIDE_LENGTH by mutableStateOf(50f)
    var EDGE_SIDE_ALPHA by mutableStateOf(1f)
    var EDGE_SIDE_BLUR by mutableStateOf(20f)

    // ===== 黑色描边 + 内黑模糊 =====
    var EDGE_OUTLINE_ENABLED by mutableStateOf(false)
    var EDGE_OUTLINE_ALPHA by mutableStateOf(0.5f)
    var EDGE_OUTLINE_BLUR by mutableStateOf(8f)

    var DOCK_MASK_DARK by mutableStateOf(false)

    // ===== Dock 几何 =====
    var DOCK_HEIGHT by mutableStateOf(106f)
    var DOCK_WIDTH by mutableStateOf(355f)
    var DOCK_CORNER by mutableStateOf(42f)
    var DOCK_OFFSET_Y by mutableStateOf(5.5f)

    // ===== 面板圆角 =====
    var RADIUS by mutableStateOf(24f)

    // ===== 壁纸亮度 =====
    var WALLPAPER_BRIGHTNESS by mutableStateOf(0f)
var WALLPAPER_BRIGHTNESS_ENABLED by mutableStateOf(false)

    // ===================== 持久化 =====================
    private const val PREFS = "glass_config"

    fun save(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putFloat("BLUR", BLUR)
            .putFloat("BLUR_PRESS", BLUR_PRESS)
            .putFloat("LENS", LENS)
            .putFloat("LENS_PRESS", LENS_PRESS)
            .putFloat("TWIST", TWIST)
            .putFloat("REFRACT_BAND", REFRACT_BAND)
            .putFloat("REFRACT_DEPTH", REFRACT_DEPTH)
            .putFloat("CHROMATIC_INTENSITY", CHROMATIC_INTENSITY)
            .putFloat("SATURATION", SATURATION)
            .putFloat("BRIGHTNESS", BRIGHTNESS)
            .putFloat("CONTRAST", CONTRAST)
            .putFloat("HIGHLIGHT", HIGHLIGHT)
            .putFloat("HIGHLIGHT_PRESS", HIGHLIGHT_PRESS)
            .putFloat("SHADOW_ALPHA", SHADOW_ALPHA)
            .putFloat("SHADOW_RANGE", SHADOW_RANGE)
            .putFloat("SHADOW_OFFSET_Y", SHADOW_OFFSET_Y)
            .putFloat("DROP_SHADOW", DROP_SHADOW)
            .putFloat("DROP_SHADOW_RANGE", DROP_SHADOW_RANGE)
            .putFloat("PRESS_SCALE", PRESS_SCALE)
            .putFloat("SURFACE", SURFACE)
            .putFloat("SURFACE_PRESS", SURFACE_PRESS)
            .putFloat("EDGE_SIDE_WIDTH", EDGE_SIDE_WIDTH)
            .putFloat("EDGE_SIDE_LENGTH", EDGE_SIDE_LENGTH)
            .putFloat("EDGE_SIDE_ALPHA", EDGE_SIDE_ALPHA)
            .putFloat("EDGE_SIDE_BLUR", EDGE_SIDE_BLUR)
            .putFloat("EDGE_HIGHLIGHT_WIDTH", EDGE_HIGHLIGHT_WIDTH)
            .putFloat("EDGE_HIGHLIGHT_ALPHA", EDGE_HIGHLIGHT_ALPHA)
            .putFloat("EDGE_HIGHLIGHT_BLUR", EDGE_HIGHLIGHT_BLUR)
            .putFloat("EDGE_HIGHLIGHT_RANGE", EDGE_HIGHLIGHT_RANGE)
            .putFloat("EDGE_GLOW_ALPHA", EDGE_GLOW_ALPHA)
            .putFloat("EDGE_HL_LENGTH", EDGE_HL_LENGTH)
            .putFloat("EDGE_OUTLINE_ALPHA", EDGE_OUTLINE_ALPHA)
            .putFloat("EDGE_OUTLINE_BLUR", EDGE_OUTLINE_BLUR)
            .putFloat("DOCK_HEIGHT", DOCK_HEIGHT)
            .putFloat("DOCK_WIDTH", DOCK_WIDTH)
            .putFloat("DOCK_CORNER", DOCK_CORNER)
            .putFloat("DOCK_OFFSET_Y", DOCK_OFFSET_Y)
            .putFloat("RADIUS", RADIUS)
            .putFloat("WALLPAPER_BRIGHTNESS", WALLPAPER_BRIGHTNESS)
.putBoolean("WALLPAPER_BRIGHTNESS_ENABLED", WALLPAPER_BRIGHTNESS_ENABLED)
            .putBoolean("EDGE_SIDE_ENABLED", EDGE_SIDE_ENABLED)
            .putBoolean("EDGE_HIGHLIGHT_ENABLED", EDGE_HIGHLIGHT_ENABLED)
            .putBoolean("HIGHLIGHT_ENABLED", HIGHLIGHT_ENABLED)
            .putBoolean("EDGE_OUTLINE_ENABLED", EDGE_OUTLINE_ENABLED)
            .putBoolean("DOCK_MASK_DARK", DOCK_MASK_DARK)
            .apply()
    }

    fun load(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        BLUR = sp.getFloat("BLUR", BLUR)
        BLUR_PRESS = sp.getFloat("BLUR_PRESS", BLUR_PRESS)
        LENS = sp.getFloat("LENS", LENS)
        LENS_PRESS = sp.getFloat("LENS_PRESS", LENS_PRESS)
        TWIST = sp.getFloat("TWIST", TWIST)
        REFRACT_BAND = sp.getFloat("REFRACT_BAND", REFRACT_BAND)
        REFRACT_DEPTH = sp.getFloat("REFRACT_DEPTH", REFRACT_DEPTH)
        CHROMATIC_INTENSITY = sp.getFloat("CHROMATIC_INTENSITY", CHROMATIC_INTENSITY)
        SATURATION = sp.getFloat("SATURATION", SATURATION)
        BRIGHTNESS = sp.getFloat("BRIGHTNESS", BRIGHTNESS)
        CONTRAST = sp.getFloat("CONTRAST", CONTRAST)
        HIGHLIGHT = sp.getFloat("HIGHLIGHT", HIGHLIGHT)
        HIGHLIGHT_PRESS = sp.getFloat("HIGHLIGHT_PRESS", HIGHLIGHT_PRESS)
        SHADOW_ALPHA = sp.getFloat("SHADOW_ALPHA", SHADOW_ALPHA)
        SHADOW_RANGE = sp.getFloat("SHADOW_RANGE", SHADOW_RANGE)
        SHADOW_OFFSET_Y = sp.getFloat("SHADOW_OFFSET_Y", SHADOW_OFFSET_Y)
        DROP_SHADOW = sp.getFloat("DROP_SHADOW", DROP_SHADOW)
        DROP_SHADOW_RANGE = sp.getFloat("DROP_SHADOW_RANGE", DROP_SHADOW_RANGE)
        PRESS_SCALE = sp.getFloat("PRESS_SCALE", PRESS_SCALE)
        SURFACE = sp.getFloat("SURFACE", SURFACE)
        SURFACE_PRESS = sp.getFloat("SURFACE_PRESS", SURFACE_PRESS)
        EDGE_SIDE_WIDTH = sp.getFloat("EDGE_SIDE_WIDTH", EDGE_SIDE_WIDTH)
        EDGE_SIDE_LENGTH = sp.getFloat("EDGE_SIDE_LENGTH", EDGE_SIDE_LENGTH)
        EDGE_SIDE_ALPHA = sp.getFloat("EDGE_SIDE_ALPHA", EDGE_SIDE_ALPHA)
        EDGE_SIDE_BLUR = sp.getFloat("EDGE_SIDE_BLUR", EDGE_SIDE_BLUR)
        EDGE_HIGHLIGHT_WIDTH = sp.getFloat("EDGE_HIGHLIGHT_WIDTH", EDGE_HIGHLIGHT_WIDTH)
        EDGE_HIGHLIGHT_ALPHA = sp.getFloat("EDGE_HIGHLIGHT_ALPHA", EDGE_HIGHLIGHT_ALPHA)
        EDGE_HIGHLIGHT_BLUR = sp.getFloat("EDGE_HIGHLIGHT_BLUR", EDGE_HIGHLIGHT_BLUR)
        EDGE_HIGHLIGHT_RANGE = sp.getFloat("EDGE_HIGHLIGHT_RANGE", EDGE_HIGHLIGHT_RANGE)
        EDGE_GLOW_ALPHA = sp.getFloat("EDGE_GLOW_ALPHA", EDGE_GLOW_ALPHA)
        EDGE_HL_LENGTH = sp.getFloat("EDGE_HL_LENGTH", EDGE_HL_LENGTH)
        EDGE_OUTLINE_ALPHA = sp.getFloat("EDGE_OUTLINE_ALPHA", EDGE_OUTLINE_ALPHA)
        EDGE_OUTLINE_BLUR = sp.getFloat("EDGE_OUTLINE_BLUR", EDGE_OUTLINE_BLUR)
        DOCK_HEIGHT = sp.getFloat("DOCK_HEIGHT", DOCK_HEIGHT)
        DOCK_WIDTH = sp.getFloat("DOCK_WIDTH", DOCK_WIDTH)
        DOCK_CORNER = sp.getFloat("DOCK_CORNER", DOCK_CORNER)
        DOCK_OFFSET_Y = sp.getFloat("DOCK_OFFSET_Y", DOCK_OFFSET_Y)
        RADIUS = sp.getFloat("RADIUS", RADIUS)
        WALLPAPER_BRIGHTNESS = sp.getFloat("WALLPAPER_BRIGHTNESS", WALLPAPER_BRIGHTNESS)
WALLPAPER_BRIGHTNESS_ENABLED = sp.getBoolean("WALLPAPER_BRIGHTNESS_ENABLED", WALLPAPER_BRIGHTNESS_ENABLED)
        EDGE_SIDE_ENABLED = sp.getBoolean("EDGE_SIDE_ENABLED", EDGE_SIDE_ENABLED)
        EDGE_HIGHLIGHT_ENABLED = sp.getBoolean("EDGE_HIGHLIGHT_ENABLED", EDGE_HIGHLIGHT_ENABLED)
        HIGHLIGHT_ENABLED = sp.getBoolean("HIGHLIGHT_ENABLED", HIGHLIGHT_ENABLED)
        EDGE_OUTLINE_ENABLED = sp.getBoolean("EDGE_OUTLINE_ENABLED", EDGE_OUTLINE_ENABLED)
        DOCK_MASK_DARK = sp.getBoolean("DOCK_MASK_DARK", DOCK_MASK_DARK)
    }
}
