# CMCLauncher-PE

面向 Android 的 Minecraft: Java Edition 启动器。

界面直接呈现，不做任何渲染修饰：没有粒子特效，没有过渡动画，没有装饰性光效，所有页面切换瞬时完成。

## 特点

- 界面轻量，渲染开销最低
- 无雨滴、节日特效、液态玻璃等视觉层
- 无淡入淡出、滑动、缩放等过渡动画
- 无加载闪烁占位动画
- 启动核心与游戏兼容性保持完整

## 安装

包名 `com.cmc.launcher.pe`。

## 构建

```bash
./gradlew :CMCLauncher:assembleDebug -Darch=arm64
```

构建产物位于 `CMCLauncher/build/outputs/apk/debug/`。

## 说明

启动核心基于 PojavLauncher，UI 使用 Jetpack Compose 与 Material Design 3。