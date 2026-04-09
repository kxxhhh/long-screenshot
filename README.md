# LongScreenshot

Android 滚动长截图工具，支持截取任意 App 的长图（跨进程）。

[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

[English](#english) | 中文

---

## 功能特性

- **跨进程截图**：截取任意 App 界面，而不仅限于自身
- **三套方案自动切换**：根据设备权限自动选择最优方案
- **实时预览**：截图过程中实时拼接预览
- **自动保存**：截图完成后自动保存到相册

## 方案说明

| 方案 | 截图 API | 滑动 API | 前置条件 | 用户成本 |
|------|---------|---------|---------|---------|
| **Shizuku** | `SurfaceControl.screenshot()` | `IInputManager.injectInputEvent()` | 安装 Shizuku，执行一次 adb 或无线调试授权 | 低 |
| **Root** | `su -c screencap` | AccessibilityService | 设备已 root | 低 |
| **MediaProjection** | `MediaProjection` + `VirtualDisplay` | AccessibilityService | 同意屏幕录制弹窗 + 开启无障碍服务 | 中 |

优先级：`SHIZUKU > ROOT > MEDIA_PROJECTION`，启动时自动检测。

## 架构设计

```
┌─────────────────────────────────────────────────┐
│                  MainActivity                    │
│           (Composition Root / Facade)            │
└──────────────────────┬──────────────────────────┘
                       │ DI 组装
         ┌─────────────┼─────────────┐
         ▼             ▼             ▼
   IPermission   IScreenCapture  IScrollController
   Provider      ────────────    ────────────────
   (Proxy)       SurfaceControl  InputManager
                 MediaProjection Accessibility
                 RootCapture
         │             │             │
         └─────────────┼─────────────┘
                       ▼
             ScrollCaptureOrchestrator
             (Observer: ScrollCallback)
                       │
              ┌────────┴────────┐
              ▼                 ▼
        IFrameComposer    IPreviewRenderer
        (SRP: Store +     (Observer: UI)
         Composer 分离)
```

**设计模式：**
- **Strategy**：`IScreenCapture` / `IScrollController` 三套实现可自由切换
- **Proxy**：`ShizukuPermissionProvider` 隐藏 Shizuku 细节，对外统一 `IPermissionProvider`
- **Observer**：`ScrollCallback` / `OrchestratorCallback` 解耦滚动与截图时序
- **Facade**：`ScrollCaptureOrchestratorImpl` 对外只暴露 `start/stop`
- **DI**：`MainActivity` 作为 Composition Root，构造器注入所有依赖

## 模块结构

```
app/src/main/java/com/wanjian/longscreenshot/
├── permission/          # 权限代理层（Shizuku / 无需权限）
├── capture/             # 截图策略层（SurfaceControl / MediaProjection / Root）
├── scroll/              # 滑动控制层（IInputManager / AccessibilityService）
├── compose/             # 帧合成层（FrameStore + FrameComposer，SRP 分离）
├── render/              # 渲染层（预览 + 保存）
├── orchestrator/        # 调度层（Facade 协调以上所有模块）
├── CaptureStrategy.java # 方案枚举
├── StrategySelector.java# 自动检测最优方案
└── MainActivity.java    # Composition Root
```

## 快速开始

### 方案一：Shizuku（推荐）

1. 从 [Play Store](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) 或 [GitHub](https://github.com/RikkaApps/Shizuku/releases) 安装 Shizuku
2. Android 11+ 手机：开发者选项 → 无线调试 → 打开 Shizuku → 通过无线调试启动
3. 安装本应用，打开后授权 Shizuku，直接点"开始截图"

### 方案二：MediaProjection（普通用户）

1. 安装本应用
2. 打开应用，按引导开启无障碍服务（设置 → 无障碍 → LongScreenshot）
3. 点"开始截图"，同意屏幕录制弹窗

## 编译

```bash
git clone https://github.com/wellorbetter/long-screenshot.git
cd long-screenshot
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

**环境要求：**
- Android Studio Hedgehog+
- JDK 17
- Android SDK 34

## 依赖

```groovy
implementation 'dev.rikka.shizuku:api:13.1.5'
implementation 'dev.rikka.shizuku:provider:13.1.5'
implementation 'androidx.appcompat:appcompat:1.6.1'
```

## 已知限制

- Shizuku 方案：`SurfaceControl.screenshot()` 为系统隐藏 API，通过反射调用，未来系统版本可能失效
- Root 方案：`screencap` 命令在部分定制 ROM 路径不同
- MediaProjection 方案：Android 10+ 需前台服务；无障碍服务为系统要求，无法绕过

## License

```
Copyright 2024 wellorbetter

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

---

## English

### Overview

LongScreenshot is an Android tool for capturing full-page scrolling screenshots across any app (cross-process).

### Three Capture Strategies

| Strategy | Screenshot | Scroll Injection | Requirement |
|----------|-----------|-----------------|-------------|
| **Shizuku** | `SurfaceControl.screenshot()` via reflection | `IInputManager.injectInputEvent()` via reflection | Shizuku installed & authorized |
| **Root** | `su -c screencap` | AccessibilityService | Rooted device |
| **MediaProjection** | `MediaProjection` + `VirtualDisplay` | AccessibilityService | Screen record permission + Accessibility enabled |

Auto-detection priority: `SHIZUKU > ROOT > MEDIA_PROJECTION`

### Build

```bash
./gradlew assembleDebug
```

### Architecture

Follows Strategy + Proxy + Observer + Facade + DI patterns. See 架构设计 section above for diagram.
