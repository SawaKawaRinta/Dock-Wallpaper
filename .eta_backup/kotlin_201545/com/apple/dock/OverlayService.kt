package com.apple.dock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.PixelFormat
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

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        handler = Handler(Looper.getMainLooper())
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        hidePopup()
        handler.postDelayed({ showPopup() }, 600)
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
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Dock 服务运行中")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()
        }
        startForeground(1, notification)
    }

    private fun showPopup() {
        if (composeView != null) return

        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        // 赋予一个足够大的固定大小，让面板有拉伸空间，不被裁剪
        val viewWidth = dm.widthPixels
        val viewHeight = dm.heightPixels

        params = WindowManager.LayoutParams(
            viewWidth,
            viewHeight,
            type,
            // 让非触碰区域不拦截底层点击，但自身区域可以响应
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            val view = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@OverlayService)
                setContent {
                    // 整个界面铺满屏幕，面板居中显示，这样四周都有空间形变
                    OverlayGlassContent(
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
            Log.d("OverlayService", "悬浮窗添加成功")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("OverlayService", "悬浮窗失败: ${e.message}")
            Toast.makeText(this, "悬浮窗失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun hidePopup() {
        composeView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
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