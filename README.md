# CMCLauncher-PE

面向 Android 的 Minecraft: Java Edition 启动器。

界面直接呈现，不做任何渲染修饰：没有粒子特效，没有过渡动画，没有装饰性光效，所有页面切换瞬时完成。用最低的渲染开销换最直接的操作响应，把设备的性能全部留给游戏本身。

## 功能

- 支持多个 Minecraft: Java Edition 版本安装与启动
- 版本隔离，每个版本独立管理模组与配置文件
- 整合包导入，支持 CurseForge、Modrinth、MCBBS 等格式
- 资源下载中心，可切换官方源与镜像源
- 登录方式：离线账号、微软账号、第三方认证
- 渲染器支持：Vulkan、OpenGL、Zink 等多种后端
- 自定义背景图与视频
- 内置文件管理器
- Java 运行时自动管理，按版本需求自动匹配 JRE 8 / 17 / 21 / 25
- 游戏日志查看
- 完全中文化的界面

## 特点

- 界面轻量，渲染开销最低
- 无雨滴、节日特效、液态玻璃等视觉层
- 无淡入淡出、滑动、缩放等过渡动画
- 无加载闪烁占位动画
- 启动核心与游戏兼容性保持完整

## 安装

包名 `com.cmc.launcher.pe`。

在 Releases 页面下载 `CMCLauncher-Debug-1.0.0-arm64-v8a.apk` 安装即可。首次启动会自动解压 Java 运行时与运行库，需要几分钟时间，请耐心等待。

## 构建

```bash
./gradlew :CMCLauncher:assembleDebug -Darch=arm64
```

构建产物位于 `CMCLauncher/build/outputs/apk/debug/`。

需要 JDK 21、Android SDK、Rust 工具链与 Android NDK。

## 说明

启动核心基于 [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)。

UI 使用 Jetpack Compose 与 Material Design 3 构建。

## 鸣谢

本项目基于 [ZalithLauncher](https://github.com/ZalithLauncher/ZalithLauncher) 开源项目构建，感谢 Zalith 团队与所有贡献者的长期付出。
