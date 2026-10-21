package com.apple.dock

import android.Manifest
import android.app.WallpaperManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color // 状态栏用
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.apple.dock.Implementation.GlassConfig
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

class MainActivity : AppCompatActivity() {

    private var wallpaperBitmap by mutableStateOf<android.graphics.Bitmap?>(null)

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

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        checkPermissionAndLoadWallpaper()

        setContent {
            val backgroundBackdrop = rememberLayerBackdrop()

            Box(modifier = Modifier.fillMaxSize()) {
                wallpaperBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "系统壁纸",
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(backgroundBackdrop),
                        contentScale = ContentScale.Crop
                    )
                } ?: run {
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.DarkGray))
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                        .size(72.dp)
                        .clip(CircleShape)
                        .clickable { onDockButtonClicked() }
                        .drawBackdrop(
                            backdrop = backgroundBackdrop,
                            shape = { CircleShape },
                            effects = {
                                vibrancy()
                                blur(GlassConfig.BLUR)
                                lens(GlassConfig.LENS, GlassConfig.LENS, chromaticAberration = true)
                            },
                            highlight = { Highlight.Ambient },
                            onDrawSurface = {
                                drawRect(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f))
                            }
                        )
                ) {
                    Text(
                        text = "Dock",
                        fontSize = 16.sp,
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
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
            val wallpaper: Drawable? = wallpaperManager.drawable
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

        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}