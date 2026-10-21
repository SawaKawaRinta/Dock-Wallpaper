package com.apple.dock.Implementation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 全局液态玻璃参数。
 * 所有玻璃组件都从这里取光学参数，改一个数字全局生效。
 */
object GlassConfig {

    // ===== 模糊 / 折射 =====
    var BLUR by mutableStateOf(22f)          // 静止模糊半径
    var BLUR_PRESS by mutableStateOf(8f)     // 按下时模糊半径（越按越清澈）
    var LENS by mutableStateOf(34f)          // 静止折射高度
    var LENS_PRESS by mutableStateOf(20f)    // 按下折射高度
    var CHROMATIC_INTENSITY by mutableStateOf(1.0f) // 色散强度，>0 开启
    val CHROMATIC: Boolean get() = CHROMATIC_INTENSITY > 0f

    // ===== 高光 / 阴影 =====
    var HIGHLIGHT by mutableStateOf(0.45f)
    var HIGHLIGHT_PRESS by mutableStateOf(0.70f)

    // ===== 表面着色 =====
    var SURFACE_ALPHA by mutableStateOf(0.12f)       // 表面白膜透明度
    var SURFACE_PRESS by mutableStateOf(0.28f)       // 按下时表面白膜
    val SURFACE: Float
        get() = (BLUR / 40f * 0.45f + 0.05f).coerceIn(0.05f, 0.5f)

    // ===== 几何 =====
    var RADIUS by mutableStateOf(30f)   // 面板圆角 dp
    var SPACING by mutableStateOf(14f)  // 元素间距 dp

    // ===== 交互动画 =====
    var PRESS_SCALE by mutableStateOf(0.94f)
    var ENTER_DURATION by mutableStateOf(420)
    var STRETCH_MAX by mutableStateOf(0.22f)
    var STRETCH_DEAD_ZONE by mutableStateOf(18f)
}
