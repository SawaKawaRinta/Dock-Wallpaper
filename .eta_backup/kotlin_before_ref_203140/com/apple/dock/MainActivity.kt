package com.apple.dock

import android.Manifest
import android.app.WallpaperManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.apple.dock.Implementation.GlassBackground
import com.apple.dock.Implementation.GlassConfig
import com.apple.dock.Implementation.LiquidGlassButton
import com.apple.dock.Implementation.liquidGlass
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 纯 Compose 入口，没有任何 XML 布局。
 * 主题直接使用系统内置的 Theme.Material.NoActionBar，不走项目里的 style 资源。
 */
class MainActivity : ComponentActivity() {

    private var wallpaperBitmap by mutableStateOf<Bitmap?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadWallpaper()
        } else {
            Toast.makeText(this, "未授予存储权限", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏 Compose 自己画背景，窗口本身必须透明，否则启动时会有系统底色一闪
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        checkPermissionAndLoadWallpaper()

        setContent {
            val backdrop = rememberLayerBackdrop()
            val enter = remember { Animatable(0f) }

            LaunchedEffect(Unit) {
                enter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f))
            }

            Box(modifier = Modifier.fillMaxSize()) {

                // 背景层：壁纸 / 渐变 + 游走光斑，作为玻璃可折射的内容
                GlassBackground(
                    bitmap = wallpaperBitmap,
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop)
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val e = enter.value
                                alpha = e
                                translationY = lerp(70f, 0f, e)
                            }
                            .liquidGlass(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(GlassConfig.RADIUS.dp),
                                surfaceAlpha = GlassConfig.SURFACE
                            )
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Liquid Glass Dock",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = "点击下方按钮开启悬浮 Dock。拖动面板顶部三个圆点可以移动窗口，✕ 关闭。",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 13.sp
                        )

                        Spacer(Modifier.height(16.dp))

                        LiquidGlassButton(
                            backdrop = backdrop,
                            onClick = { onDockButtonClicked() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    val e = enter.value
                                    alpha = e
                                }
                        ) {
                            Text(
                                text = "开启 Dock",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissionAndLoadWallpaper() {
        val permission = Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadWallpaper()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun loadWallpaper() {
        try {
            val wallpaperManager = WallpaperManager.getInstance(this)
            val wallpaper = wallpaperManager.drawable
            if (wallpaper is BitmapDrawable) {
                wallpaperBitmap = wallpaper.bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "读取壁纸失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onDockButtonClicked() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }

        // 顺序很重要：先把服务拉起来，再回桌面。反过来在部分 ROM 上会因进程退后台导致服务启动失败。
        val intent = Intent(this, OverlayService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "启动服务失败: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finish()
    }
}
