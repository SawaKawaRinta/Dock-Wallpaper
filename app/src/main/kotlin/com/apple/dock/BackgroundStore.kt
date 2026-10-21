package com.apple.dock

import android.content.Context
import android.graphics.Bitmap
import java.io.File

/**
 * 导入的背景图存储 —— **纯内存缓存，不落盘**。
 *
 * 需求：导入的图片只在当前进程里当背景用，一旦杀掉后台重启，
 * 就自动回退成系统壁纸，不允许永久覆盖。
 *
 * 主界面与悬浮窗在同一个进程，所以内存缓存足够两边共享。
 * 历史上落盘过的 custom_background.png 会在首次构造时清掉，
 * 避免老版本留下的图继续生效。
 */
object BackgroundStore {

    private var cached: Bitmap? = null
    private var legacyCleaned = false

    private const val LEGACY_FILE_NAME = "custom_background.png"
    private const val LEGACY_PREFS = "dock_background"

    fun save(context: Context, bitmap: Bitmap): Boolean {
        cached = bitmap
        return true
    }

    fun load(context: Context): Bitmap? {
        if (!legacyCleaned) {
            legacyCleaned = true
            cleanupLegacy(context)
        }
        return cached
    }

    fun clear(context: Context) {
        cached = null
    }

    fun hasCustom(): Boolean = cached != null

    /** 清掉旧版本持久化下来的背景图，保证"不永久覆盖" */
    private fun cleanupLegacy(context: Context) {
        try {
            val file = File(context.filesDir, LEGACY_FILE_NAME)
            if (file.exists()) file.delete()

            val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            if (prefs.contains("custom_path")) {
                prefs.edit().remove("custom_path").commit()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
