<div align="center">

# LongScreenshot

**Android cross-process scrolling screenshot tool**

截取任意 App 的全页长图，无需 root 也可使用

[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Build](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Platform](https://img.shields.io/badge/platform-Android-orange.svg)]()

[中文](#中文) · [English](#english)

</div>

---

## 中文

### 这是什么

大多数 App 的长截图功能只能截自己的界面（直接 `view.draw(canvas)`）。

**LongScreenshot 做的事不同**：通过系统级 API 拿到设备像素，再模拟滑动手势，把每一帧拼接成完整长图——**可以截任意 App，包括其他进程**。

### 三套方案

App 启动时自动检测设备环境，按优先级选择最优方案：

| 优先级 | 方案 | 截图实现 | 滑动实现 | 前置条件 |
|:---:|------|---------|---------|---------|
| 1 | **Shizuku** | `SurfaceControl.screenshot()` 反射 | `IInputManager.injectInputEvent()` 反射 | 安装 Shizuku + 一次授权 |
| 2 | **Root** | `su -c screencap` | AccessibilityService 手势 | 设备已 root |
| 3 | **MediaProjection** | `MediaProjection` + `VirtualDisplay` | AccessibilityService 手势 | 同意录屏弹窗 + 开启无障碍 |

> **为什么 MediaProjection 方案需要无障碍？**
> MediaProjection 解决了"截图"问题，但普通 App 没有注入系统级滑动事件的权限。
> AccessibilityService 是目前 Android 上唯一对普通应用开放的手势注入接口。
> 微信、截图猫等工具的长截图功能均采用此方案。

### 架构设计

```
┌──────────────────────────────────────────────────────┐
│                    MainActivity                       │
│              Composition Root · Facade               │
└────────────────────────┬─────────────────────────────┘
                         │ constructor injection
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
  IPermissionProvider  IScreenCapture  IScrollController
  ─────────────────    ──────────────  ─────────────────
  ShizukuProvider  →   SurfaceControl  InputManager
  (null for others)    MediaProjection Accessibility
                       RootCapture
          │              │              │
          └──────────────┼──────────────┘
                         ▼
            ScrollCaptureOrchestratorImpl
                  Observer pattern
              (ScrollCallback driven)
                         │
               ┌─────────┴─────────┐
               ▼                   ▼
         IFrameComposer       IPreviewRenderer
         ┌────┬────────┐      real-time UI update
         │    │        │
      Store  Composer  SRP separation
```

**设计模式对应关系：**

| 模式 | 应用位置 | 作用 |
|------|---------|------|
| **Strategy** | `IScreenCapture` / `IScrollController` | 三套实现运行时可互换 |
| **Proxy** | `ShizukuPermissionProvider` | 对外隐藏 Shizuku 细节，统一 `IPermissionProvider` 接口 |
| **Observer** | `ScrollCallback` / `OrchestratorCallback` | 解耦滚动节奏与截图时序 |
| **Facade** | `ScrollCaptureOrchestratorImpl` | 对外只暴露 `start(request)` / `stop()` |
| **DI** | `MainActivity` | Composition Root，构造器注入所有依赖，无框架 |
| **SRP** | `FrameStoreImpl` + `FrameComposerImpl` | 数据存储与业务合成职责分离 |

### 模块结构

```
app/src/main/java/com/wanjian/longscreenshot/
│
├── permission/                   # 权限代理层
│   ├── IPermissionProvider.java  # 接口：request / isGranted / getSystemService
│   ├── ShizukuPermissionProvider # Shizuku 实现，IBinder 包装
│   └── PermissionCallback.java
│
├── capture/                      # 截图策略层
│   ├── IScreenCapture.java       # 接口：capture / setInsets / release
│   ├── SurfaceControlCapture     # Shizuku 方案：反射 SurfaceControl
│   ├── MediaProjectionCapture    # 普通方案：VirtualDisplay + ImageReader
│   ├── MediaProjectionService    # 前台服务（Android 10+ 要求）
│   └── RootCapture               # Root 方案：su + screencap
│
├── scroll/                       # 滑动控制层
│   ├── IScrollController.java    # 接口：start / stop / isScrolling
│   ├── InputManagerScrollController   # Shizuku：IInputManager 反射注入
│   ├── AccessibilityScrollController  # 普通：GestureDescription 手势
│   └── LongScrollAccessibilityService # 无障碍服务实体
│
├── compose/                      # 帧合成层（SRP 分离）
│   ├── IFrameStore / FrameStoreImpl   # 帧数据存储
│   └── IFrameComposer / FrameComposerImpl  # 裁剪重叠、Canvas 拼接、RGB_565
│
├── render/                       # 渲染层
│   ├── PreviewRendererImpl       # 实时预览回调
│   └── ResultRendererImpl        # 保存 PNG 到相册，MediaScanner 通知
│
├── orchestrator/                 # 调度层（Facade）
│   └── ScrollCaptureOrchestratorImpl  # 协调所有模块时序
│
├── CaptureStrategy.java          # 枚举：SHIZUKU / ROOT / MEDIA_PROJECTION
├── StrategySelector.java         # 自动检测最优方案
└── MainActivity.java             # Composition Root
```

### 快速开始

**方案一：Shizuku（推荐，体验最佳）**

```
1. 安装 Shizuku：https://github.com/RikkaApps/Shizuku/releases
2. Android 11+：开发者选项 → 无线调试 → 打开 Shizuku App → 通过无线调试启动
   Android 10-：需连接电脑执行一次 adb 命令（Shizuku App 内有说明）
3. 安装本 App → 授权 Shizuku → 点"开始截图"
```

**方案二：MediaProjection（无需安装额外软件）**

```
1. 安装本 App
2. 首次打开：按引导前往 设置 → 无障碍 → LongScreenshot → 开启
3. 点"开始截图" → 同意录屏弹窗 → 开始滚动截图
```

### 编译运行

```bash
git clone https://github.com/wellorbetter/long-screenshot.git
cd long-screenshot
./gradlew assembleDebug
# 输出：app/build/outputs/apk/debug/app-debug.apk
```

**环境要求**

| 工具 | 版本 |
|------|------|
| Android Studio | Hedgehog (2023.1.1)+ |
| JDK | 17 |
| compileSdk | 34 |
| minSdk | 24 (Android 7.0) |
| AGP | 8.2.2 |
| Gradle | 8.2 |

### 核心依赖

```groovy
// Shizuku：adb 权限桥接
implementation 'dev.rikka.shizuku:api:13.1.5'
implementation 'dev.rikka.shizuku:provider:13.1.5'

// AndroidX
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'androidx.core:core:1.12.0'
```

### 帧合成原理

```
第 N 帧截图（高度 frameH）
滚动了 stepPx 像素

overlap = frameH - stepPx   ← 与上一帧重叠的像素行数
cropY   = overlap            ← 从这里开始才是新内容
新内容高度 = frameH - overlap

Canvas.drawBitmap(croppedFrame, 0, currentOffsetY, paint)
currentOffsetY += 新内容高度
```

内存优化：使用 `RGB_565`（比 `ARGB_8888` 节省 50% 内存），中间帧合成后立即 `recycle()`。

### 已知限制

- `SurfaceControl.screenshot()` 为隐藏 API，通过反射调用，AOSP 版本升级可能失效
- `screencap` 在部分深度定制 ROM 上路径或行为不同
- MediaProjection 方案在 Android 10+ 强制要求前台服务，会显示通知栏图标
- 无障碍服务为 Android 系统设计约束，普通 App 无法绕过

### License

```
Copyright 2024 wellorbetter

Licensed under the Apache License, Version 2.0
http://www.apache.org/licenses/LICENSE-2.0
```

---

## English

### What is this

Most "long screenshot" features in Android apps only capture their **own** UI by calling `view.draw(canvas)` — no special permissions needed, but limited to self.

**LongScreenshot works differently**: it uses system-level APIs to capture raw screen pixels and injects scroll gestures to stitch frames into a complete long image — **works across any app, any process**.

### Three Strategies

Auto-detected at launch, highest priority wins:

| Priority | Strategy | Screenshot | Scroll Injection | Requirement |
|:---:|----------|-----------|-----------------|-------------|
| 1 | **Shizuku** | `SurfaceControl.screenshot()` via reflection | `IInputManager.injectInputEvent()` via reflection | Shizuku installed + one-time authorization |
| 2 | **Root** | `su -c screencap` | AccessibilityService gesture | Rooted device |
| 3 | **MediaProjection** | `MediaProjection` + `VirtualDisplay` | AccessibilityService gesture | Screen record consent + Accessibility enabled |

> **Why does MediaProjection need Accessibility?**
> MediaProjection solves the "screenshot" part but grants no permission to inject touch events into other apps.
> `AccessibilityService` is the only gesture injection interface Android exposes to regular apps.
> This is the same approach used by WeChat, screenshot tools, etc.

### Architecture

Follows **Strategy + Proxy + Observer + Facade + DI** patterns.
See the 架构设计 section above for the full diagram.

### Build

```bash
git clone https://github.com/wellorbetter/long-screenshot.git
cd long-screenshot
./gradlew assembleDebug
```

### Frame Stitching

```
Frame N height = frameH, scroll step = stepPx
overlap  = frameH - stepPx   ← rows shared with previous frame
cropY    = overlap            ← start of new content
new rows = frameH - overlap

drawBitmap(croppedFrame, offsetY)
offsetY += new rows
```

Memory: `RGB_565` config (50% less than `ARGB_8888`), intermediate bitmaps recycled immediately.

### Known Limitations

- `SurfaceControl.screenshot()` is a hidden API accessed via reflection — may break on future AOSP versions
- `screencap` path/behavior may differ on heavily customized ROMs
- Android 10+ requires a foreground service for MediaProjection (notification icon shown)
- AccessibilityService requirement is an Android platform constraint, not bypassable for regular apps
