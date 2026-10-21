package com.apple.dock.Implementation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object GlassConfig {
    var BLUR by mutableStateOf(22f)
    var BLUR_PRESS by mutableStateOf(8f)
    var LENS by mutableStateOf(32f)
    var LENS_PRESS by mutableStateOf(18f)
    var CHROMATIC_INTENSITY by mutableStateOf(1.0f)
    var HIGHLIGHT by mutableStateOf(0.45f)
    var HIGHLIGHT_PRESS by mutableStateOf(0.40f)
    val SURFACE: Float get() = (BLUR / 40f * 0.45f + 0.05f).coerceIn(0.05f, 0.5f)
    var SURFACE_PRESS by mutableStateOf(0.15f)
}