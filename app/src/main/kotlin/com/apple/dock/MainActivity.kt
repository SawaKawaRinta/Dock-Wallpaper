package com.apple.dock

import android.Manifest
import android.app.WallpaperManager
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ripple
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import com.apple.dock.Implementation.GlassButton
import com.apple.dock.Implementation.GlassConfig
import com.apple.dock.Implementation.GlassWallpaperLayer
import com.apple.dock.Implementation.LiquidDock
import com.apple.dock.Implementation.LiquidInteractiveSurface
import com.apple.dock.Implementation.LiquidSlider
import com.apple.dock.Implementation.LiquidToggle
import com.apple.dock.Implementation.glassSurface
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private var wallpaperIsLight by mutableStateOf(false)
private val textColor: Color get() = if (wallpaperIsLight) Color(0xFF1C1C1E) else Color.White

class MainActivity : ComponentActivity() {

    private var wallpaperBitmap by mutableStateOf<Bitmap?>(null)
    private var pendingBitmap: Bitmap? = null
    private var cropSource by mutableStateOf<Bitmap?>(null)
    private var numericInput by mutableStateOf<NumericInputTarget?>(null)
private var presets by mutableStateOf<List<ParamPreset>>(emptyList())
private var pendingExportJson by mutableStateOf<String?>(null)

    private val readPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadSystemWallpaper()
        } else {
            toast("未授予读取权限，将使用渐变背景")
        }
    }

    private val writePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val bitmap = pendingBitmap
        pendingBitmap = null
        if (granted && bitmap != null) {
            doSaveImage(bitmap)
        } else if (!granted) {
            toast("未授予写入权限，无法保存")
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val exportParamsLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { doExportParams(it) }
    }

    private val importParamsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { doImportParams(it) }
    }

    override fun onPause() {
        super.onPause()
        GlassConfig.save(this)
    }

    private fun buildParamsJson(): String {
        val o = org.json.JSONObject()
        o.put("BLUR", GlassConfig.BLUR.toDouble())
        o.put("BLUR_PRESS", GlassConfig.BLUR_PRESS.toDouble())
        o.put("LENS", GlassConfig.LENS.toDouble())
        o.put("LENS_PRESS", GlassConfig.LENS_PRESS.toDouble())
        o.put("TWIST", GlassConfig.TWIST.toDouble())
        o.put("REFRACT_BAND", GlassConfig.REFRACT_BAND.toDouble())
        o.put("REFRACT_DEPTH", GlassConfig.REFRACT_DEPTH.toDouble())
        o.put("CHROMATIC_INTENSITY", GlassConfig.CHROMATIC_INTENSITY.toDouble())
        o.put("SATURATION", GlassConfig.SATURATION.toDouble())
        o.put("BRIGHTNESS", GlassConfig.BRIGHTNESS.toDouble())
        o.put("CONTRAST", GlassConfig.CONTRAST.toDouble())
        o.put("HIGHLIGHT", GlassConfig.HIGHLIGHT.toDouble())
        o.put("HIGHLIGHT_PRESS", GlassConfig.HIGHLIGHT_PRESS.toDouble())
        o.put("SHADOW_ALPHA", GlassConfig.SHADOW_ALPHA.toDouble())
        o.put("SHADOW_RANGE", GlassConfig.SHADOW_RANGE.toDouble())
        o.put("SHADOW_OFFSET_Y", GlassConfig.SHADOW_OFFSET_Y.toDouble())
        o.put("DROP_SHADOW", GlassConfig.DROP_SHADOW.toDouble())
        o.put("DROP_SHADOW_RANGE", GlassConfig.DROP_SHADOW_RANGE.toDouble())
        o.put("PRESS_SCALE", GlassConfig.PRESS_SCALE.toDouble())
        o.put("SURFACE", GlassConfig.SURFACE.toDouble())
        o.put("SURFACE_PRESS", GlassConfig.SURFACE_PRESS.toDouble())
        o.put("EDGE_SIDE_WIDTH", GlassConfig.EDGE_SIDE_WIDTH.toDouble())
        o.put("EDGE_SIDE_LENGTH", GlassConfig.EDGE_SIDE_LENGTH.toDouble())
        o.put("EDGE_SIDE_ALPHA", GlassConfig.EDGE_SIDE_ALPHA.toDouble())
        o.put("EDGE_SIDE_BLUR", GlassConfig.EDGE_SIDE_BLUR.toDouble())
        o.put("EDGE_HIGHLIGHT_WIDTH", GlassConfig.EDGE_HIGHLIGHT_WIDTH.toDouble())
        o.put("EDGE_HIGHLIGHT_ALPHA", GlassConfig.EDGE_HIGHLIGHT_ALPHA.toDouble())
        o.put("EDGE_HIGHLIGHT_BLUR", GlassConfig.EDGE_HIGHLIGHT_BLUR.toDouble())
        o.put("EDGE_HIGHLIGHT_RANGE", GlassConfig.EDGE_HIGHLIGHT_RANGE.toDouble())
        o.put("EDGE_GLOW_ALPHA", GlassConfig.EDGE_GLOW_ALPHA.toDouble())
        o.put("EDGE_HL_LENGTH", GlassConfig.EDGE_HL_LENGTH.toDouble())
        o.put("EDGE_OUTLINE_ALPHA", GlassConfig.EDGE_OUTLINE_ALPHA.toDouble())
        o.put("EDGE_OUTLINE_BLUR", GlassConfig.EDGE_OUTLINE_BLUR.toDouble())
        o.put("DOCK_HEIGHT", GlassConfig.DOCK_HEIGHT.toDouble())
        o.put("DOCK_WIDTH", GlassConfig.DOCK_WIDTH.toDouble())
        o.put("DOCK_CORNER", GlassConfig.DOCK_CORNER.toDouble())
        o.put("DOCK_OFFSET_Y", GlassConfig.DOCK_OFFSET_Y.toDouble())
        o.put("RADIUS", GlassConfig.RADIUS.toDouble())
        o.put("EDGE_SIDE_ENABLED", GlassConfig.EDGE_SIDE_ENABLED)
        o.put("EDGE_HIGHLIGHT_ENABLED", GlassConfig.EDGE_HIGHLIGHT_ENABLED)
        o.put("HIGHLIGHT_ENABLED", GlassConfig.HIGHLIGHT_ENABLED)
        o.put("EDGE_OUTLINE_ENABLED", GlassConfig.EDGE_OUTLINE_ENABLED)
        o.put("DOCK_MASK_DARK", GlassConfig.DOCK_MASK_DARK)
        return o.toString(2)
    }

    private fun applyParamsJson(text: String) {
        val o = org.json.JSONObject(text)
        GlassConfig.BLUR = o.optDouble("BLUR", GlassConfig.BLUR.toDouble()).toFloat()
        GlassConfig.BLUR_PRESS = o.optDouble("BLUR_PRESS", GlassConfig.BLUR_PRESS.toDouble()).toFloat()
        GlassConfig.LENS = o.optDouble("LENS", GlassConfig.LENS.toDouble()).toFloat()
        GlassConfig.LENS_PRESS = o.optDouble("LENS_PRESS", GlassConfig.LENS_PRESS.toDouble()).toFloat()
        GlassConfig.TWIST = o.optDouble("TWIST", GlassConfig.TWIST.toDouble()).toFloat()
        GlassConfig.REFRACT_BAND = o.optDouble("REFRACT_BAND", GlassConfig.REFRACT_BAND.toDouble()).toFloat()
        GlassConfig.REFRACT_DEPTH = o.optDouble("REFRACT_DEPTH", GlassConfig.REFRACT_DEPTH.toDouble()).toFloat()
        GlassConfig.CHROMATIC_INTENSITY = o.optDouble("CHROMATIC_INTENSITY", GlassConfig.CHROMATIC_INTENSITY.toDouble()).toFloat()
        GlassConfig.SATURATION = o.optDouble("SATURATION", GlassConfig.SATURATION.toDouble()).toFloat()
        GlassConfig.BRIGHTNESS = o.optDouble("BRIGHTNESS", GlassConfig.BRIGHTNESS.toDouble()).toFloat()
        GlassConfig.CONTRAST = o.optDouble("CONTRAST", GlassConfig.CONTRAST.toDouble()).toFloat()
        GlassConfig.HIGHLIGHT = o.optDouble("HIGHLIGHT", GlassConfig.HIGHLIGHT.toDouble()).toFloat()
        GlassConfig.HIGHLIGHT_PRESS = o.optDouble("HIGHLIGHT_PRESS", GlassConfig.HIGHLIGHT_PRESS.toDouble()).toFloat()
        GlassConfig.SHADOW_ALPHA = o.optDouble("SHADOW_ALPHA", GlassConfig.SHADOW_ALPHA.toDouble()).toFloat()
        GlassConfig.SHADOW_RANGE = o.optDouble("SHADOW_RANGE", GlassConfig.SHADOW_RANGE.toDouble()).toFloat()
        GlassConfig.SHADOW_OFFSET_Y = o.optDouble("SHADOW_OFFSET_Y", GlassConfig.SHADOW_OFFSET_Y.toDouble()).toFloat()
        GlassConfig.DROP_SHADOW = o.optDouble("DROP_SHADOW", GlassConfig.DROP_SHADOW.toDouble()).toFloat()
        GlassConfig.DROP_SHADOW_RANGE = o.optDouble("DROP_SHADOW_RANGE", GlassConfig.DROP_SHADOW_RANGE.toDouble()).toFloat()
        GlassConfig.PRESS_SCALE = o.optDouble("PRESS_SCALE", GlassConfig.PRESS_SCALE.toDouble()).toFloat()
        GlassConfig.SURFACE = o.optDouble("SURFACE", GlassConfig.SURFACE.toDouble()).toFloat()
        GlassConfig.SURFACE_PRESS = o.optDouble("SURFACE_PRESS", GlassConfig.SURFACE_PRESS.toDouble()).toFloat()
        GlassConfig.EDGE_SIDE_WIDTH = o.optDouble("EDGE_SIDE_WIDTH", GlassConfig.EDGE_SIDE_WIDTH.toDouble()).toFloat()
        GlassConfig.EDGE_SIDE_LENGTH = o.optDouble("EDGE_SIDE_LENGTH", GlassConfig.EDGE_SIDE_LENGTH.toDouble()).toFloat()
        GlassConfig.EDGE_SIDE_ALPHA = o.optDouble("EDGE_SIDE_ALPHA", GlassConfig.EDGE_SIDE_ALPHA.toDouble()).toFloat()
        GlassConfig.EDGE_SIDE_BLUR = o.optDouble("EDGE_SIDE_BLUR", GlassConfig.EDGE_SIDE_BLUR.toDouble()).toFloat()
        GlassConfig.EDGE_HIGHLIGHT_WIDTH = o.optDouble("EDGE_HIGHLIGHT_WIDTH", GlassConfig.EDGE_HIGHLIGHT_WIDTH.toDouble()).toFloat()
        GlassConfig.EDGE_HIGHLIGHT_ALPHA = o.optDouble("EDGE_HIGHLIGHT_ALPHA", GlassConfig.EDGE_HIGHLIGHT_ALPHA.toDouble()).toFloat()
        GlassConfig.EDGE_HIGHLIGHT_BLUR = o.optDouble("EDGE_HIGHLIGHT_BLUR", GlassConfig.EDGE_HIGHLIGHT_BLUR.toDouble()).toFloat()
        GlassConfig.EDGE_HIGHLIGHT_RANGE = o.optDouble("EDGE_HIGHLIGHT_RANGE", GlassConfig.EDGE_HIGHLIGHT_RANGE.toDouble()).toFloat()
        GlassConfig.EDGE_GLOW_ALPHA = o.optDouble("EDGE_GLOW_ALPHA", GlassConfig.EDGE_GLOW_ALPHA.toDouble()).toFloat()
        GlassConfig.EDGE_HL_LENGTH = o.optDouble("EDGE_HL_LENGTH", GlassConfig.EDGE_HL_LENGTH.toDouble()).toFloat()
        GlassConfig.EDGE_OUTLINE_ALPHA = o.optDouble("EDGE_OUTLINE_ALPHA", GlassConfig.EDGE_OUTLINE_ALPHA.toDouble()).toFloat()
        GlassConfig.EDGE_OUTLINE_BLUR = o.optDouble("EDGE_OUTLINE_BLUR", GlassConfig.EDGE_OUTLINE_BLUR.toDouble()).toFloat()
        GlassConfig.DOCK_HEIGHT = o.optDouble("DOCK_HEIGHT", GlassConfig.DOCK_HEIGHT.toDouble()).toFloat()
        GlassConfig.DOCK_WIDTH = o.optDouble("DOCK_WIDTH", GlassConfig.DOCK_WIDTH.toDouble()).toFloat()
        GlassConfig.DOCK_CORNER = o.optDouble("DOCK_CORNER", GlassConfig.DOCK_CORNER.toDouble()).toFloat()
        GlassConfig.DOCK_OFFSET_Y = o.optDouble("DOCK_OFFSET_Y", GlassConfig.DOCK_OFFSET_Y.toDouble()).toFloat()
        GlassConfig.RADIUS = o.optDouble("RADIUS", GlassConfig.RADIUS.toDouble()).toFloat()
        GlassConfig.EDGE_SIDE_ENABLED = o.optBoolean("EDGE_SIDE_ENABLED", GlassConfig.EDGE_SIDE_ENABLED)
        GlassConfig.EDGE_HIGHLIGHT_ENABLED = o.optBoolean("EDGE_HIGHLIGHT_ENABLED", GlassConfig.EDGE_HIGHLIGHT_ENABLED)
        GlassConfig.HIGHLIGHT_ENABLED = o.optBoolean("HIGHLIGHT_ENABLED", GlassConfig.HIGHLIGHT_ENABLED)
        GlassConfig.EDGE_OUTLINE_ENABLED = o.optBoolean("EDGE_OUTLINE_ENABLED", GlassConfig.EDGE_OUTLINE_ENABLED)
        GlassConfig.DOCK_MASK_DARK = o.optBoolean("DOCK_MASK_DARK", GlassConfig.DOCK_MASK_DARK)
    }

    private fun doExportParams(uri: Uri) {
        try {
            val json = pendingExportJson ?: buildParamsJson()
pendingExportJson = null
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            }
            toast("参数已导出")
        } catch (e: Exception) {
            toast("导出失败: ${e.message}")
        }
    }

    private fun doImportParams(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (text == null) {
                toast("无法读取文件")
                return
            }
            applyParamsJson(text)
            GlassConfig.save(this)
            val name = queryDisplayName(uri)?.substringBeforeLast('.')?.ifBlank { null } ?: "导入预设"
            val list = loadPresetsFromPrefs(this).toMutableList()
            list.add(ParamPreset(name, text))
            savePresetsToPrefs(this, list)
            presets = list
            toast("已导入并保存为预设「$name」")
        } catch (e: Exception) {
            toast("导入失败: ${e.message}")
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        var name: String? = null
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
        } catch (e: Exception) { }
        return name
    }

    private fun saveNewPreset(name: String) {
        val list = loadPresetsFromPrefs(this).toMutableList()
        list.add(ParamPreset(name, buildParamsJson()))
        savePresetsToPrefs(this, list)
        presets = list
        toast("预设「$name」已保存")
    }

    private fun applyPresetAt(index: Int) {
        val list = loadPresetsFromPrefs(this)
        if (index !in list.indices) return
        applyParamsJson(list[index].params)
        GlassConfig.save(this)
        toast("已应用预设「${list[index].name}」")
    }

    private fun deletePresetAt(index: Int) {
        val list = loadPresetsFromPrefs(this).toMutableList()
        if (index in list.indices) list.removeAt(index)
        savePresetsToPrefs(this, list)
        presets = list
    }

    private fun renamePresetAt(index: Int, name: String) {
        val list = loadPresetsFromPrefs(this).toMutableList()
        if (index in list.indices) list[index] = list[index].copy(name = name)
        savePresetsToPrefs(this, list)
        presets = list
    }

    private fun resetDefaults() {
        try {
            val json = getSharedPreferences("glass_defaults", MODE_PRIVATE).getString("params", null)
            if (json == null) { toast("默认值快照不可用"); return }
            applyParamsJson(json)
            GlassConfig.save(this)
            toast("已恢复默认参数")
        } catch (e: Exception) { toast("重置失败: ${e.message}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 首次运行时快照出厂默认值（此时参数还是代码初始值）
        val dprefs = getSharedPreferences("glass_defaults", MODE_PRIVATE)
        if (!dprefs.getBoolean("initialized_v2", false)) {
            dprefs.edit().putString("params", buildParamsJson()).putBoolean("initialized_v2", true).commit()
        }
GlassConfig.load(this)
presets = loadPresetsFromPrefs(this)

        // 优先用用户导入的背景，没有才读系统壁纸
        val custom = BackgroundStore.load(this)
        if (custom != null) {
            wallpaperBitmap = custom
            updateWallpaperBrightness(custom)
        } else {
            checkPermissionAndLoadWallpaper()
        }
        requestNotificationPermission()
        requestAllFilesPermission()

        setContent {
            val crop = cropSource
            if (crop != null) {
                BackgroundCropScreen(
                    source = crop,
                    onConfirm = { result ->
                        cropSource = null
                        applyCroppedBackground(result)
                    },
                    onCancel = { cropSource = null }
                )
            } else {
                DockRoot(
                    wallpaper = wallpaperBitmap,
                    onPreview = { onDockButtonClicked() },
                    onSaveImage = { bitmap -> saveImage(bitmap) },
                    onPickBackground = { uri -> importBackground(uri) },
                    onApplyWallpaper = { bitmap -> applyWallpaper(bitmap) },
                    numericInput = numericInput,
                    onDismissNumeric = { numericInput = null },
                    onRequestNumericInput = { numericInput = it },
                    onExportParams = { json -> pendingExportJson = json; exportParamsLauncher.launch("dock_params.json") },
                    onImportParams = { importParamsLauncher.launch(arrayOf("application/json", "text/plain")) },
                    presets = presets,
                    onSavePreset = { saveNewPreset(it) },
                    onApplyPreset = { applyPresetAt(it) },
                    onDeletePreset = { deletePresetAt(it) },
                    onRenamePreset = { i, n -> renamePresetAt(i, n) },
                    onResetDefaults = { resetDefaults() }
                )
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /* ============================  背景  ============================ */

    private fun checkPermissionAndLoadWallpaper() {
        // 先直接尝试读取：方式 1 的 WallpaperManager.drawable 在 Android 13+ 不需要存储权限
        loadSystemWallpaper()
        if (wallpaperBitmap == null) {
            // 方式 1 失败，且无权限时再请求权限，走方式 2（读壁纸文件）
            val permission = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                readPermissionLauncher.launch(permission)
            }
        }
    }


    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(permission)
            }
        }
    }

    private fun requestAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        }
    }
    private fun loadSystemWallpaper() {
        try {
            val wm = WallpaperManager.getInstance(this)

            // 方式 1：直接取 drawable
            val drawable = wm.drawable
            if (drawable != null) {
                val bmp = drawable.toBitmap()
                if (bmp.width > 1 && bmp.height > 1) {
                    wallpaperBitmap = bmp
                    updateWallpaperBrightness(bmp)
                    return
                }
            }

            // 方式 2：读壁纸文件（静态壁纸，API 24+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val file = wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)
                if (file != null) {
                    val bmp = BitmapFactory.decodeFileDescriptor(file.fileDescriptor)
                    if (bmp != null && bmp.width > 1) {
                        wallpaperBitmap = bmp
                        updateWallpaperBrightness(bmp)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun importBackground(uri: Uri) {
        try {
            val bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
            if (bitmap == null) {
                toast("无法读取这张图片")
                return
            }
            cropSource = bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            toast("导入失败: ${e.message}")
        }
    }

    private fun applyCroppedBackground(bitmap: Bitmap) {
        BackgroundStore.save(this, bitmap)
        wallpaperBitmap = bitmap
        updateWallpaperBrightness(bitmap)
        toast("背景已更新")
    }
    private fun updateWallpaperBrightness(bitmap: Bitmap?) {
        if (bitmap == null) {
            wallpaperIsLight = false
            return
        }
        try {
            val w = 16
            val h = 16
            val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            var sum = 0L
            for (p in pixels) {
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                sum += (0.299f * r + 0.587f * g + 0.114f * b).toLong()
            }
            wallpaperIsLight = sum / (w * h) > 160
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    /* ============================  保存渲染图  ============================ */

    private fun saveImage(bitmap: Bitmap) {
        doSaveImage(bitmap)
    }

    private fun doSaveImage(bitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.IO) {
            saveImageBlocking(bitmap)
        }
    }

    private fun saveImageBlocking(bitmap: Bitmap) {
        try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val name = "Dock_$stamp.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                runOnUiThread { toast("保存失败") }
                return
            }
            contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            runOnUiThread { toast("已保存到相册") }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread { toast("保存失败: ${e.message}") }
        }
    }

    /* ============================  预览  ============================ */

    private fun applyWallpaper(bitmap: Bitmap) {
        try {
            val wm = WallpaperManager.getInstance(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
            } else {
                @Suppress("DEPRECATION")
                wm.setBitmap(bitmap)
            }
            toast("壁纸已应用")
        } catch (e: Exception) {
            toast("应用壁纸失败: ${e.message}")
        }
    }

    private fun onDockButtonClicked() {
        if (!Settings.canDrawOverlays(this)) {
            toast("请授予悬浮窗权限")
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }

        val intent = Intent(this, OverlayService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            toast("启动服务失败: ${e.message}")
            return
        }

        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
    }
}

/* ==============================  根布局  ============================== */

@Composable
private fun DockRoot(
    wallpaper: Bitmap?,
    onPreview: () -> Unit,
    onSaveImage: (Bitmap) -> Unit,
    onPickBackground: (Uri) -> Unit,
    onApplyWallpaper: (Bitmap) -> Unit,
    numericInput: NumericInputTarget?,
    onDismissNumeric: () -> Unit,
    onRequestNumericInput: (NumericInputTarget) -> Unit,
    onExportParams: (String?) -> Unit,
    onImportParams: () -> Unit,
    presets: List<ParamPreset>,
    onSavePreset: (String) -> Unit,
    onApplyPreset: (Int) -> Unit,
    onDeletePreset: (Int) -> Unit,
    onRenamePreset: (Int, String) -> Unit,
    onResetDefaults: () -> Unit
) {
    val menuBackdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(menuBackdrop, contentBackdrop)

    val captureLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) onPickBackground(uri)
    }

    Box(Modifier.fillMaxSize()) {

        // ===== 第 1 层：背景 + Dock（可被抓图） =====
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    captureLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(captureLayer)
                }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(menuBackdrop)
            ) {
                GlassWallpaperLayer(
                    bitmap = wallpaper,
                    modifier = Modifier.fillMaxSize(),
                    brightness = GlassConfig.WALLPAPER_BRIGHTNESS,
brightnessEnabled = GlassConfig.WALLPAPER_BRIGHTNESS_ENABLED
                )
            }

            LiquidDock(
                backdrop = combinedBackdrop,
                onClick = onPreview,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp)
            )
        }

        // ===== 第 2 层：参数页（不参与抓图） =====
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop)
        ) {
            ControlPageV3(
                backdrop = menuBackdrop,
                onPreview = onPreview,
                onSaveImage = {
                    scope.launch {
                        val bitmap = captureLayer.toImageBitmap().asAndroidBitmap()
                        onSaveImage(bitmap)
                    }
                },
                onPickClick = { pickLauncher.launch("image/*") },
                onApplyWallpaper = {
                    scope.launch {
                        val bitmap = captureLayer.toImageBitmap().asAndroidBitmap()
                        onApplyWallpaper(bitmap)
                    }
                },
                onRequestNumericInput = onRequestNumericInput,
                onExportParams = onExportParams,
                onImportParams = onImportParams,
                presets = presets,
                onSavePreset = onSavePreset,
                onApplyPreset = onApplyPreset,
                onDeletePreset = onDeletePreset,
                onRenamePreset = onRenamePreset,
                onResetDefaults = onResetDefaults
            )
        }

        numericInput?.let { target ->
            NumericInputOverlay(target = target, onDismiss = onDismissNumeric)
        }
    }
}

/* ==============================  参数页  ============================== */

@Composable
private fun ControlPage(
    backdrop: Backdrop,
    onPreview: () -> Unit,
    onSaveImage: () -> Unit,
    onPickClick: () -> Unit,
    onApplyWallpaper: () -> Unit
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()

    val cardShape = remember {
        RoundedRectangle(
            cornerRadius = GlassConfig.RADIUS.dp,
            style = RoundedCornerStyle.Continuous
        )
    }

    // 调节面板自身的玻璃样式 —— 固定，不跟滑块走
    val panelBlur = 26f
    val panelBlurPress = 10f
    val panelLens = 40f
    val panelLensPress = 20f
    val panelSurface = 0.22f
    val panelSurfacePress = 0.22f
    val panelBand = 0.55f
    val panelShadowAlpha = 0.55f
    val panelShadowRange = 12f
    val panelShadowOffsetY = 9f

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 12.dp)
    ) {
        LiquidInteractiveSurface(
            menuBackdrop = backdrop,
            shape = cardShape,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth(),
            blurBase = panelBlur,
            blurPress = panelBlurPress,
            lensBase = panelLens,
            lensPress = panelLensPress,
            highlightBase = 0.60f,
            highlightPress = 0.50f,
            surfaceAlphaBase = panelSurface,
            surfaceAlphaPress = panelSurfacePress,
            lightRadiusFactor = 0.5f,
            maxStretch = 0.02f,
            refractBand = panelBand,
            depth = 0f,
            shadowAlpha = panelShadowAlpha,
            shadowRange = panelShadowRange,
            shadowOffsetY = panelShadowOffsetY,
            saturation = 1f,
            brightness = 0f,
            contrast = 1f
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SectionLabel("玻璃")

                SliderRow("模糊度", "${GlassConfig.BLUR.roundToInt()} px") {
                    LiquidSlider(
                        value = { GlassConfig.BLUR },
                        onValueChange = { GlassConfig.BLUR = it },
                        valueRange = 0f..200f,
                        visibilityThreshold = 0.1f,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SliderRow("折射强度", "${GlassConfig.LENS.roundToInt()} px") {
                    LiquidSlider(
                        value = { GlassConfig.LENS },
                        onValueChange = { GlassConfig.LENS = it },
                        valueRange = 0f..400f,
                        visibilityThreshold = 0.5f,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SliderRow("色散", "${(GlassConfig.CHROMATIC_INTENSITY * 100).roundToInt()}%") {
                    LiquidSlider(
                        value = { GlassConfig.CHROMATIC_INTENSITY },
                        onValueChange = { GlassConfig.CHROMATIC_INTENSITY = it },
                        valueRange = 0f..1f,
                        visibilityThreshold = 0.01f,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(2.dp))
                SectionLabel("Dock 尺寸")

                SliderRow("高度", "${GlassConfig.DOCK_HEIGHT.roundToInt()} dp") {
                    LiquidSlider(
                        value = { GlassConfig.DOCK_HEIGHT },
                        onValueChange = { GlassConfig.DOCK_HEIGHT = it },
                        valueRange = 20f..600f,
                        visibilityThreshold = 0.5f,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SliderRow("宽度", "${GlassConfig.DOCK_WIDTH.roundToInt()} dp") {
                    LiquidSlider(
                        value = { GlassConfig.DOCK_WIDTH },
                        onValueChange = { GlassConfig.DOCK_WIDTH = it },
                        valueRange = 100f..500f,
                        visibilityThreshold = 0.5f,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SliderRow("上下位置", "${GlassConfig.DOCK_OFFSET_Y.roundToInt()} dp") {
                    LiquidSlider(
                        value = { GlassConfig.DOCK_OFFSET_Y },
                        onValueChange = { GlassConfig.DOCK_OFFSET_Y = it },
                        valueRange = -400f..100f,
                        visibilityThreshold = 0.5f,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SliderRow("圆角", "${GlassConfig.DOCK_CORNER.roundToInt()} dp") {
                    LiquidSlider(
                        value = { GlassConfig.DOCK_CORNER },
                        onValueChange = { GlassConfig.DOCK_CORNER = it },
                        valueRange = 0f..80f,
                        visibilityThreshold = 0.5f,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ===== 三个按钮：导入背景 / 预览桌面 / 保存壁纸 =====
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassButton(
                text = "导入背景",
                textColor = textColor,
                backdrop = backdrop,
                onClick = onPickClick,
                blur = panelBlur,
                blurPress = panelBlurPress,
                lens = panelLens,
                lensPress = panelLensPress,
                chromatic = 1f,
                surfaceAlpha = panelSurface,
                surfaceAlphaPress = panelSurfacePress,
                refractBand = panelBand,
                depth = 0f,
                shadowAlpha = panelShadowAlpha,
                shadowRange = panelShadowRange,
                shadowOffsetY = panelShadowOffsetY,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            )

            GlassButton(
                text = "预览桌面",
                textColor = textColor,
                backdrop = backdrop,
                onClick = onPreview,
                blur = panelBlur,
                blurPress = panelBlurPress,
                lens = panelLens,
                lensPress = panelLensPress,
                chromatic = 1f,
                surfaceAlpha = panelSurface,
                surfaceAlphaPress = panelSurfacePress,
                refractBand = panelBand,
                depth = 0f,
                shadowAlpha = panelShadowAlpha,
                shadowRange = panelShadowRange,
                shadowOffsetY = panelShadowOffsetY,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            )

            GlassButton(
                text = "保存壁纸",
                textColor = textColor,
                backdrop = backdrop,
                onClick = onSaveImage,
                blur = panelBlur,
                blurPress = panelBlurPress,
                lens = panelLens,
                lensPress = panelLensPress,
                chromatic = 1f,
                surfaceAlpha = panelSurface,
                surfaceAlphaPress = panelSurfacePress,
                refractBand = panelBand,
                depth = 0f,
                shadowAlpha = panelShadowAlpha,
                shadowRange = panelShadowRange,
                shadowOffsetY = panelShadowOffsetY,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = textColor.copy(alpha = 0.55f)
    )
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    onValueClick: (() -> Unit)? = null,
    slider: @Composable () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
            Text(
                text = valueText,
                fontSize = 13.sp,
                color = textColor.copy(alpha = 0.7f),
                modifier = Modifier.clickable(enabled = onValueClick != null) { onValueClick?.invoke() }
            )
        }
        Spacer(Modifier.height(5.dp))
        slider()
    }
}

/* ==============================  参数页 V2：静态玻璃 + 可滚动  ============================== */

@Composable
private fun ControlPageV2(
    backdrop: Backdrop,
    onPreview: () -> Unit,
    onSaveImage: () -> Unit,
    onPickClick: () -> Unit,
    onApplyWallpaper: () -> Unit
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()

    val cardShape = remember {
        RoundedRectangle(
            cornerRadius = GlassConfig.RADIUS.dp,
            style = RoundedCornerStyle.Continuous
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 12.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 玻璃卡片：静态玻璃 + 内部可滚动（拖拽拉扯会让滚动失效，所以这里不用交互表面）
        Box(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .weight(1f)
                .glassSurface(
                    backdrop = backdrop,
                    shape = cardShape,
                    surfaceAlpha = 0.22f,
                    surfaceAlphaPress = 0.22f,
                    blur = 26f,
                    blurPress = 10f,
                    lens = 40f,
                    lensPress = 20f,
                    chromatic = 1f,
                    highlight = 0.60f,
                    highlightPress = 0.50f,
                    refractBand = 0.55f,
                    depth = 0f,
                    shadowAlpha = 0.55f,
                    shadowRange = 12f,
                    shadowOffsetY = 9f
                )
                .padding(18.dp)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SectionLabel("玻璃")
                Spacer(Modifier.height(6.dp))

                SliderRow("模糊度", "${GlassConfig.BLUR.roundToInt()} px") {
                    LiquidSlider(
                        value = { GlassConfig.BLUR },
                        onValueChange = { GlassConfig.BLUR = it },
                        valueRange = 0f..200f,
                        visibilityThreshold = 0.1f,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SliderRow("折射强度", "${GlassConfig.LENS.roundToInt()} px") {
                    LiquidSlider(
                        value = { GlassConfig.LENS },
                        onValueChange = { GlassConfig.LENS = it },
                        valueRange = 0f..400f,
                        visibilityThreshold = 0.5f,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SliderRow("色散", "${(GlassConfig.CHROMATIC_INTENSITY * 100).roundToInt()}%") {
                    LiquidSlider(
                        value = { GlassConfig.CHROMATIC_INTENSITY },
                        onValueChange = { GlassConfig.CHROMATIC_INTENSITY = it },
                        valueRange = 0f..1f,
                        visibilityThreshold = 0.01f,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(34.dp))
                SectionLabel("色彩")
                Spacer(Modifier.height(6.dp))

                SliderRow("饱和度", "${(GlassConfig.SATURATION * 100).roundToInt()}%") {
                    LiquidSlider(
                        value = { GlassConfig.SATURATION },
                        onValueChange = { GlassConfig.SATURATION = it },
                        valueRange = 0f..2f,
                        visibilityThreshold = 0.01f,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SliderRow("亮度", "${(GlassConfig.BRIGHTNESS * 100).roundToInt()}%") {
                    LiquidSlider(
                        value = { GlassConfig.BRIGHTNESS },
                        onValueChange = { GlassConfig.BRIGHTNESS = it },
                        valueRange = -0.5f..0.5f,
                        visibilityThreshold = 0.01f,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SliderRow("对比度", "${(GlassConfig.CONTRAST * 100).roundToInt()}%") {
                    LiquidSlider(
                        value = { GlassConfig.CONTRAST },
                        onValueChange = { GlassConfig.CONTRAST = it },
                        valueRange = 0.5f..2f,
                        visibilityThreshold = 0.01f,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(34.dp))
                SectionLabel("Dock 尺寸")
                Spacer(Modifier.height(6.dp))

                SliderRow("高度", "${GlassConfig.DOCK_HEIGHT.roundToInt()} dp") {
                    LiquidSlider(
                        value = { GlassConfig.DOCK_HEIGHT },
                        onValueChange = { GlassConfig.DOCK_HEIGHT = it },
                        valueRange = 20f..600f,
                        visibilityThreshold = 0.5f,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SliderRow("宽度", "${GlassConfig.DOCK_WIDTH.roundToInt()} dp") {
                    LiquidSlider(
                        value = { GlassConfig.DOCK_WIDTH },
                        onValueChange = { GlassConfig.DOCK_WIDTH = it },
                        valueRange = 100f..500f,
                        visibilityThreshold = 0.5f,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SliderRow("上下位置", "${GlassConfig.DOCK_OFFSET_Y.roundToInt()} dp") {
                    LiquidSlider(
                        value = { GlassConfig.DOCK_OFFSET_Y },
                        onValueChange = { GlassConfig.DOCK_OFFSET_Y = it },
                        valueRange = -400f..100f,
                        visibilityThreshold = 0.5f,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SliderRow("圆角", "${GlassConfig.DOCK_CORNER.roundToInt()} dp") {
                    LiquidSlider(
                        value = { GlassConfig.DOCK_CORNER },
                        onValueChange = { GlassConfig.DOCK_CORNER = it },
                        valueRange = 0f..80f,
                        visibilityThreshold = 0.5f,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }

        // ===== 三个按钮 =====
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassButton(
                text = "导入背景",
                textColor = textColor,
                backdrop = backdrop,
                onClick = onPickClick,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            )

            GlassButton(
                text = "预览桌面",
                textColor = textColor,
                backdrop = backdrop,
                onClick = onPreview,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            )

            GlassButton(
                text = "保存壁纸",
                textColor = textColor,
                backdrop = backdrop,
                onClick = onSaveImage,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            )
        }
    }
}

/* ==============================  参数页 V3：全参数 + 滚动  ============================== */

@Composable
private fun SliderCompact(
    label: String,
    value: String,
    range: ClosedFloatingPointRange<Float>,
    get: () -> Float,
    set: (Float) -> Unit,
    backdrop: Backdrop,
    step: Float = 0.01f,
    onValueClick: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    SliderRow(label, value, onValueClick) {
        LiquidSlider(
            value = get,
            onValueChange = set,
            valueRange = range,
            visibilityThreshold = step,
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
    }
}

@Composable
private fun ControlPageV3(
    backdrop: Backdrop,
    onPreview: () -> Unit,
    onSaveImage: () -> Unit,
    onPickClick: () -> Unit,
    onApplyWallpaper: () -> Unit,
    onRequestNumericInput: (NumericInputTarget) -> Unit,
    onExportParams: (String?) -> Unit,
    onImportParams: () -> Unit,
    presets: List<ParamPreset>,
    onSavePreset: (String) -> Unit,
    onApplyPreset: (Int) -> Unit,
    onDeletePreset: (Int) -> Unit,
    onRenamePreset: (Int, String) -> Unit,
    onResetDefaults: () -> Unit
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val context = LocalContext.current
    var presetExpanded by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
var showExportDialog by remember { mutableStateOf(false) }
var paramsCollapsed by remember { mutableStateOf(false) }

    val cardShape = remember {
        RoundedRectangle(
            cornerRadius = GlassConfig.RADIUS.dp,
            style = RoundedCornerStyle.Continuous
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 12.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .then(if (paramsCollapsed) Modifier else Modifier.weight(1f))
                .glassSurface(
                    backdrop = backdrop,
                    shape = cardShape,
                    surfaceAlpha = 0.30f,
                    surfaceAlphaPress = 0.15f,
                    blur = 22f,
                    blurPress = 8f,
                    lens = 32f,
                    lensPress = 18f,
                    chromatic = 0f,
                    highlight = 0.45f,
                    highlightPress = 0.40f,
                    refractBand = 0.55f,
                    depth = 0f,
                    shadowAlpha = 0.55f,
                    shadowRange = 12f,
                    shadowOffsetY = 9f
                )
                .padding(18.dp)
        ) {
            Column(
                Modifier
                    .then(if (paramsCollapsed) Modifier else Modifier.fillMaxSize())
            ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("参数调节", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Spacer(Modifier.weight(1f))
                        Text("下滑更多参数调节", fontSize = 12.sp, color = textColor.copy(alpha = 0.5f))
                        Icon(
                            imageVector = if (paramsCollapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                            contentDescription = if (paramsCollapsed) "展开" else "收起",
                            tint = Color(0xFF007AFF),
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(22.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = false)
                                ) { paramsCollapsed = !paramsCollapsed }
                        )
                    }
                if (!paramsCollapsed) {
Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(12.dp))
                SectionLabel("玻璃")
                Spacer(Modifier.height(6.dp))

                SliderCompact("模糊度", "${GlassConfig.BLUR.roundToInt()} px", 0f..200f,
                    { GlassConfig.BLUR }, { GlassConfig.BLUR = it }, backdrop, 0.1f)
                SliderCompact("折射强度", "${GlassConfig.LENS.roundToInt()} px", 0f..400f,
                    { GlassConfig.LENS }, { GlassConfig.LENS = it }, backdrop, 0.5f)
                SliderCompact("扭曲强度", "${(GlassConfig.TWIST * 100).roundToInt()}%", 0f..3f,
                    { GlassConfig.TWIST }, { GlassConfig.TWIST = it }, backdrop)
                SliderCompact("色散", "${(GlassConfig.CHROMATIC_INTENSITY * 100).roundToInt()}%", 0f..1f,
                    { GlassConfig.CHROMATIC_INTENSITY }, { GlassConfig.CHROMATIC_INTENSITY = it }, backdrop)
                SliderCompact("折射带", "${(GlassConfig.REFRACT_BAND * 100).roundToInt()}%", 0.02f..1f,
                    { GlassConfig.REFRACT_BAND }, { GlassConfig.REFRACT_BAND = it }, backdrop)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("深度", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textColor)
                    Spacer(Modifier.weight(1f))
                    LiquidToggle(
                        selected = { GlassConfig.REFRACT_DEPTH > 0.4f },
                        onSelect = { on -> GlassConfig.REFRACT_DEPTH = if (on) 0.5f else 0f },
                        backdrop = backdrop
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("边缘光", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textColor)
                    Spacer(Modifier.weight(1f))
                    LiquidToggle(
                        selected = { GlassConfig.HIGHLIGHT_ENABLED },
                        onSelect = { GlassConfig.HIGHLIGHT_ENABLED = it },
                        backdrop = backdrop
                    )
                }
                SliderCompact("边缘光强度", "${(GlassConfig.HIGHLIGHT * 100).roundToInt()}%", 0f..1f,
                    { GlassConfig.HIGHLIGHT }, { GlassConfig.HIGHLIGHT = it }, backdrop,
                    enabled = GlassConfig.HIGHLIGHT_ENABLED)


                Spacer(Modifier.height(34.dp))
                SectionLabel("壁纸亮度")
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("壁纸亮暗", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textColor)
                    Spacer(Modifier.weight(1f))
                    LiquidToggle(
                        selected = { GlassConfig.WALLPAPER_BRIGHTNESS_ENABLED },
                        onSelect = { GlassConfig.WALLPAPER_BRIGHTNESS_ENABLED = it },
                        backdrop = backdrop
                    )
                }
                SliderCompact("亮度", "${if (GlassConfig.WALLPAPER_BRIGHTNESS > 0f) "+" else ""}${GlassConfig.WALLPAPER_BRIGHTNESS.roundToInt()}%", -100f..100f,
                    { GlassConfig.WALLPAPER_BRIGHTNESS }, { GlassConfig.WALLPAPER_BRIGHTNESS = it }, backdrop, 1f,
                    enabled = GlassConfig.WALLPAPER_BRIGHTNESS_ENABLED)
                Spacer(Modifier.height(34.dp))
                SectionLabel("色彩")
                Spacer(Modifier.height(6.dp))

                SliderCompact("饱和度", "${(GlassConfig.SATURATION * 100).roundToInt()}%", 0f..2f,
                    { GlassConfig.SATURATION }, { GlassConfig.SATURATION = it }, backdrop)
                SliderCompact("亮度", "${(GlassConfig.BRIGHTNESS * 100).roundToInt()}%", -0.5f..0.5f,
                    { GlassConfig.BRIGHTNESS }, { GlassConfig.BRIGHTNESS = it }, backdrop)
                SliderCompact("对比度", "${(GlassConfig.CONTRAST * 100).roundToInt()}%", 0.5f..2f,
                    { GlassConfig.CONTRAST }, { GlassConfig.CONTRAST = it }, backdrop)
                Spacer(Modifier.height(34.dp))
                SectionLabel("大小")
                Spacer(Modifier.height(6.dp))

                SliderCompact("高度", "${GlassConfig.DOCK_HEIGHT.roundToInt()} dp", 20f..600f,
                    { GlassConfig.DOCK_HEIGHT }, { GlassConfig.DOCK_HEIGHT = it }, backdrop, 0.5f)
                SliderCompact("宽度", "${GlassConfig.DOCK_WIDTH.roundToInt()} dp", 100f..500f,
                    { GlassConfig.DOCK_WIDTH }, { GlassConfig.DOCK_WIDTH = it }, backdrop, 0.5f)
                SliderCompact("上下位置", "${"%.1f".format(GlassConfig.DOCK_OFFSET_Y)} dp", -400f..100f,
                    { GlassConfig.DOCK_OFFSET_Y }, { v -> GlassConfig.DOCK_OFFSET_Y = (v / 0.5f).roundToInt() * 0.5f }, backdrop, 0.5f)
                SliderCompact("圆角", "${GlassConfig.DOCK_CORNER.roundToInt()} dp", 0f..80f,
                    { GlassConfig.DOCK_CORNER }, { GlassConfig.DOCK_CORNER = it }, backdrop, 0.5f)
                Spacer(Modifier.height(34.dp))
                SectionLabel("描边")
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("黑边", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textColor)
                    Spacer(Modifier.weight(1f))
                    LiquidToggle(
                        selected = { GlassConfig.EDGE_SIDE_ENABLED },
                        onSelect = { GlassConfig.EDGE_SIDE_ENABLED = it },
                        backdrop = backdrop
                    )
                }
                SliderCompact("黑边粗细", "${"%.1f".format(GlassConfig.EDGE_SIDE_WIDTH)} dp", 0f..8f,
                    { GlassConfig.EDGE_SIDE_WIDTH }, { GlassConfig.EDGE_SIDE_WIDTH = it }, backdrop, 0.1f,
                    onValueClick = {
                        onRequestNumericInput(
                            NumericInputTarget(
                                title = "黑边粗细",
                                initial = "%.1f".format(GlassConfig.EDGE_SIDE_WIDTH),
                                unit = "dp",
                                min = 0f,
                                max = 8f,
                                step = 0.1f,
                                onConfirm = { GlassConfig.EDGE_SIDE_WIDTH = it }
                            )
                        )
                    },
                    enabled = GlassConfig.EDGE_SIDE_ENABLED
                )
                SliderCompact("黑边长度", "${GlassConfig.EDGE_SIDE_LENGTH.roundToInt()}%", 0f..100f,
                    { GlassConfig.EDGE_SIDE_LENGTH }, { GlassConfig.EDGE_SIDE_LENGTH = it }, backdrop, 1f,
                    enabled = GlassConfig.EDGE_SIDE_ENABLED)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("白边", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textColor)
                    Spacer(Modifier.weight(1f))
                    LiquidToggle(
                        selected = { GlassConfig.EDGE_HIGHLIGHT_ENABLED },
                        onSelect = { GlassConfig.EDGE_HIGHLIGHT_ENABLED = it },
                        backdrop = backdrop
                    )
                }
                SliderCompact("白边粗细", "${"%.1f".format(GlassConfig.EDGE_HIGHLIGHT_WIDTH)} dp", 0f..8f,
                    { GlassConfig.EDGE_HIGHLIGHT_WIDTH }, { GlassConfig.EDGE_HIGHLIGHT_WIDTH = it }, backdrop, 0.1f,
                    onValueClick = {
                        onRequestNumericInput(
                            NumericInputTarget(
                                title = "白边粗细",
                                initial = "%.1f".format(GlassConfig.EDGE_HIGHLIGHT_WIDTH),
                                unit = "dp",
                                min = 0f,
                                max = 8f,
                                step = 0.1f,
                                onConfirm = { GlassConfig.EDGE_HIGHLIGHT_WIDTH = it }
                            )
                        )
                    },
                    enabled = GlassConfig.EDGE_HIGHLIGHT_ENABLED
                )
                SliderCompact("白边长度", "${GlassConfig.EDGE_HL_LENGTH.roundToInt()}%", 0f..100f,
                    { GlassConfig.EDGE_HL_LENGTH }, { GlassConfig.EDGE_HL_LENGTH = it }, backdrop, 1f,
                    enabled = GlassConfig.EDGE_HIGHLIGHT_ENABLED)
                SliderCompact("白边模糊度", "${(GlassConfig.EDGE_HIGHLIGHT_BLUR * 100).roundToInt()}%", 0f..1f,
                    { GlassConfig.EDGE_HIGHLIGHT_BLUR }, { GlassConfig.EDGE_HIGHLIGHT_BLUR = it }, backdrop,
                    enabled = GlassConfig.EDGE_HIGHLIGHT_ENABLED)
                SliderCompact("白边模糊范围", "${"%.1f".format(GlassConfig.EDGE_HIGHLIGHT_RANGE)} dp", 0f..30f,
                    { GlassConfig.EDGE_HIGHLIGHT_RANGE }, { GlassConfig.EDGE_HIGHLIGHT_RANGE = it }, backdrop, 0.5f,
                    enabled = GlassConfig.EDGE_HIGHLIGHT_ENABLED)
                SliderCompact("白色模糊透明度", "${(GlassConfig.EDGE_GLOW_ALPHA * 100).roundToInt()}%", 0f..1f,
                    { GlassConfig.EDGE_GLOW_ALPHA }, { GlassConfig.EDGE_GLOW_ALPHA = it }, backdrop,
                    enabled = GlassConfig.EDGE_HIGHLIGHT_ENABLED)

                Spacer(Modifier.height(34.dp))
                SectionLabel("内阴影")
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("内阴影", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textColor)
                    Spacer(Modifier.weight(1f))
                    LiquidToggle(
                        selected = { GlassConfig.EDGE_OUTLINE_ENABLED },
                        onSelect = { GlassConfig.EDGE_OUTLINE_ENABLED = it },
                        backdrop = backdrop
                    )
                }
                SliderCompact("描边透明度", "${(GlassConfig.EDGE_OUTLINE_ALPHA * 100).roundToInt()}%", 0f..1f,
                    { GlassConfig.EDGE_OUTLINE_ALPHA }, { GlassConfig.EDGE_OUTLINE_ALPHA = it }, backdrop,
                    enabled = GlassConfig.EDGE_OUTLINE_ENABLED)
                SliderCompact("描边模糊度", "${GlassConfig.EDGE_OUTLINE_BLUR.roundToInt()} dp", 0f..30f,
                    { GlassConfig.EDGE_OUTLINE_BLUR }, { GlassConfig.EDGE_OUTLINE_BLUR = it }, backdrop,
                    step = 0.5f, enabled = GlassConfig.EDGE_OUTLINE_ENABLED)
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = "预设",
                        textColor = textColor,
                        backdrop = backdrop,
                        onClick = { presetExpanded = !presetExpanded },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    )
                    GlassButton(
                        text = "重置默认",
                        textColor = textColor,
                        backdrop = backdrop,
                        onClick = onResetDefaults,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    )
                    GlassButton(
                        text = "保存",
                        textColor = textColor,
                        backdrop = backdrop,
                        onClick = { showSaveDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    )
                }

                // 预设展开卡片
                if (presetExpanded) {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x14000000))
                            .padding(12.dp)
                    ) {
                        if (presets.isEmpty()) {
                            Text("暂无预设，点击「保存」创建", fontSize = 12.sp, color = textColor.copy(alpha = 0.6f))
                        } else {
                            presets.forEachIndexed { index, p ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        p.name,
                                        fontSize = 13.sp,
                                        color = textColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text("应用", fontSize = 12.sp, color = Color(0xFF007AFF),
                                        modifier = Modifier.clickable { onApplyPreset(index) }.padding(horizontal = 8.dp))
                                    Text("改备注", fontSize = 12.sp, color = Color(0xFF007AFF),
                                        modifier = Modifier.clickable { editingIndex = index }.padding(horizontal = 8.dp))
                                    Text("删除", fontSize = 12.sp, color = Color(0xFFFF3B30),
                                        modifier = Modifier.clickable { onDeletePreset(index) }.padding(horizontal = 8.dp))
                                }
                            }
                        }
                    }
                }


                // 导出选择对话框
                if (showExportDialog) {
                    AlertDialog(
                        onDismissRequest = { showExportDialog = false },
                        title = { Text("选择要导出的参数") },
                        text = {
                            Column {
                                Text(
                                    "当前参数",
                                    fontSize = 14.sp,
                                    color = Color(0xFF007AFF),
                                    modifier = Modifier.clickable {
                                        onExportParams(null)
                                        showExportDialog = false
                                    }.padding(vertical = 8.dp)
                                )
                                if (presets.isEmpty()) {
                                    Text("（暂无预设）", fontSize = 12.sp, color = textColor.copy(alpha = 0.5f))
                                } else {
                                    presets.forEach { p ->
                                        Text(
                                            p.name,
                                            fontSize = 14.sp,
                                            color = textColor,
                                            modifier = Modifier.clickable {
                                                onExportParams(p.params)
                                                showExportDialog = false
                                            }.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showExportDialog = false }) { Text("取消") }
                        }
                    )
                }
                // 保存新预设对话框
                if (showSaveDialog) {
                    var name by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showSaveDialog = false },
                        title = { Text("保存预设") },
                        text = {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("备注名称") },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (name.isNotBlank()) {
                                    onSavePreset(name.trim())
                                    showSaveDialog = false
                                }
                            }) { Text("保存") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSaveDialog = false }) { Text("取消") }
                        }
                    )
                }

                // 改备注对话框
                if (editingIndex != null) {
                    val idx = editingIndex!!
                    var name by remember { mutableStateOf(presets.getOrNull(idx)?.name ?: "") }
                    AlertDialog(
                        onDismissRequest = { editingIndex = null },
                        title = { Text("修改备注") },
                        text = {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("备注名称") },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (name.isNotBlank()) {
                                    onRenamePreset(idx, name.trim())
                                    editingIndex = null
                                }
                            }) { Text("确定") }
                        },
                        dismissButton = {
                            TextButton(onClick = { editingIndex = null }) { Text("取消") }
                        }
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = "导出参数",
                        textColor = textColor,
                        backdrop = backdrop,
                        onClick = { showExportDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    )
                    GlassButton(
                        text = "导入参数",
                        textColor = textColor,
                        backdrop = backdrop,
                        onClick = onImportParams,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "本软件基于以下开源库构建",
                    fontSize = 11.sp,
                    color = textColor.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Kyant0 / Backdrop · Kyant0 / Shapes\nJetpack Compose · AndroidX Material 3",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = textColor.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "本软件始终开源，如果他人修改的二改版本，不要反馈！",
                    fontSize = 11.sp,
                    color = textColor.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        "点击查看源仓库",
                        fontSize = 12.sp,
                        color = Color(0xFF007AFF),
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SawaKawaRinta/Dock-Wallpaper"))
                            context.startActivity(intent)
                        }
                    )
                    Text(
                        "点击查看作者主页",
                        fontSize = 12.sp,
                        color = Color(0xFF007AFF),
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SawaKawaRinta"))
                            context.startActivity(intent)
                        }
                    )
                }
                }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassButton(
                text = "导入背景",
                textColor = textColor,
                backdrop = backdrop,
                onClick = onPickClick,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            )

            GlassButton(
                text = "预览桌面",
                textColor = textColor,
                backdrop = backdrop,
                onClick = onPreview,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            )

            GlassButton(
                text = "应用壁纸",
                textColor = textColor,
                backdrop = backdrop,
                onClick = onApplyWallpaper,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            )
            GlassButton(
                text = "保存壁纸",
                textColor = textColor,
                backdrop = backdrop,
                onClick = onSaveImage,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            )
        }
    }
}

data class ParamPreset(val name: String, val params: String)

private fun loadPresetsFromPrefs(context: Context): List<ParamPreset> {
    val sp = context.getSharedPreferences("glass_presets", Context.MODE_PRIVATE)
    val json = sp.getString("list", "[]") ?: "[]"
    return try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            try {
                val o = arr.getJSONObject(i)
                ParamPreset(o.getString("name"), o.getString("params"))
            } catch (e: Exception) { null }
        }
    } catch (e: Exception) { emptyList() }
}

private fun savePresetsToPrefs(context: Context, list: List<ParamPreset>) {
    val arr = org.json.JSONArray()
    list.forEach { p ->
        arr.put(org.json.JSONObject().put("name", p.name).put("params", p.params))
    }
    context.getSharedPreferences("glass_presets", Context.MODE_PRIVATE)
        .edit().putString("list", arr.toString()).commit()
}
