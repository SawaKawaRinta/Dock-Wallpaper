# Dock Wallpaper

Android 液态玻璃 Dock。基于 Jetpack Compose + [Kyant0 backdrop](https://github.com/Kyant0/Backdrop) 实现**实时背景模糊、折射、边缘高光**，所有玻璃参数都可以在应用里实时调节，支持预设、导出导入、悬浮窗常驻。

> 不是静态贴图：玻璃每一帧都从当前背景（壁纸 / 实时画面）采样，模糊、折射、色散、边缘光都是 GPU 实时计算。

---

## 功能

### 玻璃 Dock
- 实时背景模糊（原生 RenderEffect 高斯）
- 边缘折射（SDF 圆角矩形 + 折射带 + 深度推挤 + 色散）
- 边缘高光、黑边、白边、内阴影、投影
- 按下缩放、拖拽拉扯 + 弹性回弹
- 尺寸、位置、圆角全部可调

### 参数调节
参数页分组，滑条实时生效：

| 分组 | 参数 |
| --- | --- |
| 玻璃 | 模糊度（0–200 px）、折射强度（0–400 px）、扭曲强度（0–300%）、色散（0–100%）、折射带（2%–100%）、深度 |
| 色彩 | 饱和度、亮度、对比度 |
| Dock 尺寸 | 高度、宽度、上下位置、圆角 |
| 描边 | 黑边粗细/长度、白边粗细/长度/模糊度/模糊范围/透明度 |
| 内阴影 | 透明度、模糊度 |
| 边缘光 | 强度 |
| 壁纸 | 亮度（-100–100） |

### 预设
- 保存当前全部参数为预设、应用、改名、删除
- 一键恢复出厂默认
- 导出参数为 JSON / 从 JSON 导入（导入后自动存为预设）

### 壁纸
- 从相册选择壁纸、裁剪
- 保存当前画面为 PNG（走 MediaStore，不需要存储权限）
- 一键应用为系统壁纸

### 悬浮窗
- `OverlayService`：Dock 常驻桌面
- 悬浮调节面板：不打断当前应用即可调参
- 预览 / 返回 APP / 恢复默认

---

## 环境要求

| 项 | 版本 |
| --- | --- |
| 最低系统 | Android 7.0（API 24），**玻璃效果需要 Android 12+（API 31），折射需要 Android 13+（API 33）** |
| compileSdk | 36 |
| targetSdk | 33 |
| JDK | 17 |
| AGP / Kotlin | 8.x / 2.x（见 `gradle/libs.versions.toml`） |

依赖锁定（不要随意升级，2.x 要求 compileSdk 37）：

```kotlin
implementation("io.github.kyant0:backdrop:1.0.6")
implementation("io.github.kyant0:shapes:1.2.0")
```

---

## 构建

```bash
# Debug
./gradlew assembleDebug

# Release（使用仓库根目录 SawaKawa.jks 签名，见 app/build.gradle.kts）
./gradlew assembleRelease
```

也可以在 AndroidIDE 里直接 Build。

---

## 项目结构

```
app/src/main/kotlin/com/apple/dock/
├── MainActivity.kt              # 主界面 + 参数页 + 预设/导入导出/壁纸保存
├── OverlayService.kt            # 悬浮 Dock 服务
├── OverlayDockContent.kt        # 悬浮 Dock 内容
├── OverlayGlassContent.kt       # 悬浮调节面板
├── OverlayToggle.kt             # 悬浮窗用无玻璃开关
├── BackgroundStore.kt           # 壁纸持久化
├── BackgroundCropScreen.kt      # 壁纸裁剪
├── NumericInput.kt              # 数值精确输入
└── Implementation/
    ├── GlassConfig.kt           # 全部参数 + SharedPreferences 持久化
    ├── HighRadiusBlur.kt        # 自定义模糊 / 折射（见下方实现说明）
    ├── LiquidInteractiveSurface.kt  # 玻璃主体：模糊+折射+边缘光+内阴影
    ├── GlassButton.kt           # 玻璃按钮 + Modifier.glassSurface
    ├── LiquidDock.kt            # Dock 容器
    ├── LiquidSlider.kt          # 玻璃滑块
    ├── LiquidToggle.kt          # 玻璃开关
    ├── LiquidBottomTabs.kt      # 底部胶囊标签栏
    ├── LiquidTitleCapsule.kt    # 标题胶囊
    ├── GlassWallpaperLayer.kt   # 壁纸层（含亮度 ColorMatrix）
    ├── DampedDragAnimation.kt   # 阻尼拖拽/按压动画
    ├── InteractiveHighlight.kt  # 交互高光
    └── ColorFilterUtil.kt       # 饱和度/亮度/对比度矩阵
```

---

## 实现说明（踩过的坑）

### 1. 大半径模糊：`HighRadiusBlur.kt`
- **HyperOS/MIUI 的原生模糊半径超过 ~90px 会崩坏**（`libhwui` 把 RenderEffect 接进了自家 blur 管线），所以拆成多遍 `≤85px` 串联，`σ_total = σ_pass · √n`。
- **边缘吃不到模糊**：Dock 离屏幕边缘只有几十像素，模糊采样会取到屏幕外的透明像素，把 alpha 平均低 → 玻璃透明 → 透出未模糊的壁纸。解法是模糊前先跑一个「边缘填充」pass，用附近真实内容补满并强制不透明。
- **折射锯齿**：AGSL 的 `content.eval()` 是最近邻采样，坐标被折射拉开后必然出台阶，所以在 shader 里手写双线性插值 + 2×2 超采样。

### 2. 悬浮窗限制
MIUI/HyperOS 的悬浮窗内使用 backdrop 的 blur/lens 会触发 `MiBackgroundBlurBlend` 栈溢出崩溃，所以悬浮窗组件（`OverlayToggle`、`OverlayGlassContent`）一律不使用 backdrop 玻璃，只使用纯色/渐变。

### 3. Compose 快照订阅
`glassSurface` / 组件默认参数里的 `GlassConfig.XXX` 会在 `$default` 方法里求值，**不会注册快照订阅**，导致滑条失效。必须在 `@Composable` 内显式读 State 再显式传参。

### 4. 已知限制
- 折射强度 × 扭曲强度过大时，位移峰值会被 `maxShift` 钳制，视觉上进入饱和（继续拉滑条变化不明显）。
- 模糊度 0 时，玻璃里内容的硬边有一部分来自壁纸本身的黑描边，不是渲染锯齿。

---

## 版本记录

### v1.0
- 首次发布
- 液态玻璃 Dock（模糊 / 折射 / 色散 / 边缘光 / 内阴影 / 投影）
- 全参数实时调节 + 预设 + 导入导出
- 壁纸选择、裁剪、保存、应用
- 悬浮 Dock + 悬浮调节面板
