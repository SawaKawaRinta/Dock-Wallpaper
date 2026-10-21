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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.LifecycleService
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.apple.dock.Implementation.GlassConfig
import kotlinx.coroutines.flow.collect

/**
 * 桌面预览服务：同时挂两个悬浮窗
 *
 *  1. Dock 窗    —— 尺寸严格等于 Dock 尺寸，位置按 GlassConfig 实时重排
 *  2. 调节面板窗 —— 浮在上方，可拖动，七个滑块实时改 Dock 参数
 *
 * ⚠️ Compose 1.7 起，Service 里的 ComposeView 除了 LifecycleOwner，
 * 还必须能拿到 ViewTreeSavedStateRegistryOwner，否则 onAttachedToWindow 直接抛
 * IllegalStateException。所以这里自带一个 OverlayOwner 把三个 owner 都补上。
 */
class OverlayService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private var dockView: ComposeView? = null
    private var panelView: ComposeView? = null
    private var dockParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var wallpaper: Bitmap? = null

    private val handler = Handler(Looper.getMainLooper())
    private val overlayOwner = OverlayOwner()

    /** Compose 里要读这两个值来计算壁纸裁剪偏移 */
    private var dockLeft by mutableStateOf(0)
    private var dockWidth by mutableStateOf(0)
    private var dockHeight by mutableStateOf(0)
    private var dockTop by mutableStateOf(0)

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayOwner.attach()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        hideAll()
        handler.postDelayed({ showAll() }, 350)
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
                .setContentTitle("Dock 预览中")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Dock 预览中")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        }
        startForeground(1, notification)
    }

    private fun loadWallpaper(): Bitmap? {
        BackgroundStore.load(this)?.let { return it }
        return try {
            val drawable = WallpaperManager.getInstance(this).drawable
            (drawable as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

    /** 把 Compose 需要的三个 owner 一次性挂到 View 上 */
    private fun ComposeView.bindComposeOwners(): ComposeView {
        setViewTreeLifecycleOwner(overlayOwner)
        setViewTreeViewModelStoreOwner(overlayOwner)
        setViewTreeSavedStateRegistryOwner(overlayOwner)
        return this
    }

    /* ============================  Dock 窗  ============================ */

    private fun showAll() {
        wallpaper = loadWallpaper()
        showDockWindow()
        showPanelWindow()
    }

    private fun showDockWindow() {
        if (dockView != null) return

        val dm = resources.displayMetrics

        val params = WindowManager.LayoutParams(
            calcDockWidth(dm.widthPixels),
            calcDockHeight(),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        dockParams = params

        try {
            val view = ComposeView(this).bindComposeOwners().apply {
                setContent {
                    LaunchedEffect(Unit) {
                        snapshotFlow {
                            listOf(
                                GlassConfig.DOCK_WIDTH, GlassConfig.DOCK_CORNER, GlassConfig.SURFACE,
                                GlassConfig.BLUR, GlassConfig.LENS, GlassConfig.CHROMATIC_INTENSITY,
                                GlassConfig.HIGHLIGHT, GlassConfig.SHADOW_ALPHA, GlassConfig.SHADOW_RANGE,
                                GlassConfig.SHADOW_OFFSET_Y, GlassConfig.EDGE_HIGHLIGHT_WIDTH,
                                GlassConfig.EDGE_HIGHLIGHT_ALPHA, GlassConfig.EDGE_HIGHLIGHT_BLUR,
                                GlassConfig.EDGE_SIDE_WIDTH, GlassConfig.EDGE_SIDE_ALPHA,
                                GlassConfig.EDGE_SIDE_BLUR, GlassConfig.PRESS_SCALE,
                                GlassConfig.DOCK_HEIGHT,
                                GlassConfig.DOCK_OFFSET_Y
                            )
                        }.collect {
                            applyDockLayout()
                        }
                    }

                    OverlayDockContent(
                        wallpaper = wallpaper,
                        screenWidthPx = dm.widthPixels,
                        screenHeightPx = dm.heightPixels,
                        windowLeftPx = dockLeft,
                        windowTopPx = dockTop,
                        windowWidthPx = dockWidth,
                        windowHeightPx = dockHeight,
                    )
                }
            }
            dockView = view
            applyDockLayout()
            windowManager.addView(view, params)
            Log.d("OverlayService", "Dock 窗 ${params.width}x${params.height} @ ${params.x},${params.y}")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Dock 悬浮窗失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun calcDockWidth(screenWidth: Int): Int {
        val density = resources.displayMetrics.density
        return (GlassConfig.DOCK_WIDTH * density)
            .toInt()
            .coerceIn((20 * density).toInt(), (500 * density).toInt())
    }

    private fun calcDockHeight(): Int {
        val density = resources.displayMetrics.density
        return (GlassConfig.DOCK_HEIGHT * density).toInt().coerceAtLeast((12 * density).toInt())
    }

    private fun applyDockLayout() {
        val view = dockView ?: return
        val params = dockParams ?: return

        val dm = resources.displayMetrics
        val density = dm.density

        val dockW = calcDockWidth(dm.widthPixels)
        val dockH = calcDockHeight()

        val left = (dm.widthPixels - dockW) / 2
        // 高度按中心点扩展：默认高度 92 时中心距屏幕底 62dp（16 底边距 + 46 半高）
        val centerDp = 62f
        val offsetY = (GlassConfig.DOCK_OFFSET_Y * density).toInt()
        val centerY = dm.heightPixels - (centerDp * density).toInt() + offsetY
        val top = centerY - dockH / 2

        params.width = dockW
        params.height = dockH
        params.x = left
        params.y = top

        dockLeft = left
        dockTop = top
        dockWidth = dockW
        dockHeight = dockH

        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /* ============================  调节面板窗  ============================ */

    private fun showPanelWindow() {
        if (panelView != null) return

        val dm = resources.displayMetrics
        val density = dm.density

        val sidePaddingDp = 16f
        val sidePx = (sidePaddingDp * density).toInt()
        val cardWidthPx = minOf(
            (dm.widthPixels * 0.92f).toInt() - sidePx * 2,
            (340 * density).toInt()
        )

        val params = WindowManager.LayoutParams(
            cardWidthPx + sidePx * 2,
            (dm.heightPixels * 0.50f).toInt(),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = (60 * density).toInt()
        }
        panelParams = params

        try {
            val view = ComposeView(this).bindComposeOwners().apply {
                setContent {
                    OverlayGlassContent(
                        wallpaper = wallpaper,
                        sidePaddingDp = sidePaddingDp,
                        verticalPaddingDp = 12f,
                        onClose = {
                            hideAll()
                            stopSelf()
                            val back = Intent(this@OverlayService, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(back)
                        },
                        onDrag = { dx, dy ->
                            val p = panelParams ?: return@OverlayGlassContent
                            p.x += dx.toInt()
                            p.y += dy.toInt()
                            try {
                                windowManager.updateViewLayout(panelView, p)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                }
            }
            windowManager.addView(view, params)
            panelView = view
            Log.d("OverlayService", "调节面板已添加 ${params.width}x${params.height}")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "调节面板失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /* ============================  清理  ============================ */

    private fun hideAll() {
        dockView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        dockView = null
        dockParams = null

        panelView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        panelView = null
        panelParams = null
    }

    override fun onDestroy() {
        hideAll()
GlassConfig.save(this)
        overlayOwner.detach()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}

/**
 * 给悬浮窗里的 ComposeView 提供完整的 owner：
 * Lifecycle + ViewModelStore + SavedStateRegistry。
 */
private class OverlayOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun attach() {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun detach() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
