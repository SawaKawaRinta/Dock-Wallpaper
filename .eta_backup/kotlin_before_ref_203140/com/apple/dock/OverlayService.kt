package com.apple.dock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner

class OverlayService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private lateinit var handler: Handler
    private var composeView: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams

    /** 卡片最大宽度（dp），避免平板上拉满 */
    private val maxCardWidthDp = 380f

    /** 卡片左右留白（dp），给玻璃阴影和高光留位置 */
    private val cardSidePaddingDp = 16f

    /** 卡片上下留白（dp），给按压回弹 / 入场下坠留位置 */
    private val cardVerticalPaddingDp = 16f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        handler = Handler(Looper.getMainLooper())
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        hidePopup()
        handler.postDelayed({ showPopup() }, 400)
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Dock 服务", NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("Dock 服务运行中")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Dock 服务运行中")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        }
        startForeground(1, notification)
    }

    /** 读取系统壁纸，为玻璃提供可折射的内容 */
    private fun loadWallpaper(): Bitmap? {
        return try {
            val drawable = WallpaperManager.getInstance(this).drawable
            (drawable as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun showPopup() {
        if (composeView != null) return

        val dm = resources.displayMetrics
        val density = dm.density

        val sidePaddingPx = (cardSidePaddingDp * density).toInt()
        val cardWidthPx = minOf(
            (dm.widthPixels * 0.94f).toInt() - sidePaddingPx * 2,
            (maxCardWidthDp * density).toInt()
        )
        val windowWidth = cardWidthPx + sidePaddingPx * 2

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            windowWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
        }

        val wallpaper = loadWallpaper()

        try {
            val view = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@OverlayService)
                setContent {
                    OverlayGlassContent(
                        wallpaper = wallpaper,
                        sidePaddingDp = cardSidePaddingDp,
                        verticalPaddingDp = cardVerticalPaddingDp,
                        onClose = { hidePopup() },
                        onDrag = { dx, dy ->
                            params.x += dx.toInt()
                            params.y += dy.toInt()
                            try {
                                windowManager.updateViewLayout(composeView, params)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                }
            }
            windowManager.addView(view, params)
            composeView = view
            Log.d("OverlayService", "悬浮窗添加成功 cardWidth=$cardWidthPx windowWidth=$windowWidth")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("OverlayService", "悬浮窗失败: ${e.message}")
            Toast.makeText(this, "悬浮窗失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun hidePopup() {
        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        composeView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        hidePopup()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
