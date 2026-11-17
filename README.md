# CMCLauncher-PE

![Platform](https://img.shields.io/badge/platform-Android-green)
![Min API](https://img.shields.io/badge/min%20API-26-blue)
![License](https://img.shields.io/badge/license-GPL--3.0-orange)

**CMCLauncher-PE** 是一个面向 **Android 设备** 的 [Minecraft: Java Edition](https://www.minecraft.net/) 启动器。

PE 代表 Pure Edition。界面直接呈现，不做任何渲染修饰：没有粒子特效，没有过渡动画，没有装饰性光效，所有页面切换瞬时完成。用最低的渲染开销换最直接的操作响应，把设备性能全部留给游戏本身。

项目使用 [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) 作为启动核心，采用 **Jetpack Compose** 与 **Material Design 3** 构建界面。

> [!IMPORTANT]
> 本项目是 [ZalithLauncher](https://github.com/ZalithLauncher/ZalithLauncher) 的非官方修改版，与 Zalith 官方团队无关。
> 依据 GPLv3 协议第七条，修改版不沿用原项目名称，并在主界面标注为非官方修改版。

## ✨ 功能

### 游戏启动

- 支持 Minecraft: Java Edition 各版本安装与启动
- 版本隔离：每个版本独立的模组、配置、存档目录
- 支持 Forge、NeoForge、Fabric、Quilt、OptiFine 等模组加载器
- 启动参数自定义，JVM 参数可调
- 游戏目录自定义，支持外置存储

### 账号系统

- 离线登录
- 微软账号登录（OAuth 2.0）
- 第三方认证服务（Authlib-Injector）支持
- 多账号管理与快速切换
- 皮肤与披风管理

### 下载中心

- 游戏本体与依赖库下载
- 模组、整合包、资源包、光影、世界下载
- 支持 CurseForge、Modrinth 等平台内容
- 下载源可切换：官方源、镜像源、自动选择
- 多线程分块下载，支持断点续传
- 下载任务队列与进度管理

### 渲染

- Vulkan 渲染后端
- OpenGL 渲染后端
- Zink（Vulkan 上的 OpenGL）后端
- 渲染器插件机制，可加载第三方渲染插件
- 渲染参数配置

### 界面

- Material Design 3 设计规范
- 支持动态取色（Android 12+）
- 多种内置配色主题
- 自定义强调色与调色风格
- 自定义背景图与背景视频
- 深色 / 浅色主题跟随系统
- 全屏沉浸模式
- 横竖屏自适应布局
- 完全中文化的界面

### 工具

- 内置文件管理器：浏览、复制、移动、压缩、解压、重命名
- 游戏日志实时查看
- Java 运行时自动管理：按版本需求自动匹配 JRE 8 / 17 / 21 / 25
- 崩溃日志收集与导出
- 游戏内虚拟按键布局编辑

## 🎨 界面风格

本项目刻意移除了全部装饰性渲染层：

- 无雨滴、水痕、节日特效、液态玻璃等视觉层
- 无淡入淡出、滑动、缩放等过渡动画
- 无加载闪烁占位动画
- 无节日标题替换
- 页面切换为瞬时切换

所有界面元素直接呈现，不做任何中间过渡。

## 📱 系统要求

| 项目 | 要求 |
|---|---|
| 系统版本 | Android 8.0（API 26）及以上 |
| 架构 | arm64-v8a |
| 存储 | 建议预留 2 GB 以上 |
| 内存 | 建议 4 GB 以上 |

## 📦 安装

1. 在 [Releases](../../releases) 页面下载最新的 `CMCLauncher-Debug-1.0.0-arm64-v8a.apk`
2. 安装应用
3. 首次启动会自动解压 Java 运行时与运行库，需要几分钟，请耐心等待
4. 添加账号 → 安装游戏版本 → 启动游戏

包名：`com.cmc.launcher.pe`

## 🔧 构建方式（开发者）

### 环境要求

- Android Studio 或命令行 Gradle 环境
- Android SDK：最低 API 26，目标 API 35
- JDK 21
- Rust 工具链（含 Android 目标）
- Android NDK

### 构建步骤

```bash
git clone https://github.com/ylh440104/CMCLauncher-PE.git
cd CMCLauncher-PE
./gradlew :CMCLauncher:assembleDebug -Darch=arm64
```

构建产物位于 `CMCLauncher/build/outputs/apk/debug/`。

项目已配置 GitHub Actions 自动构建，推送到 `main` 分支即可触发，产物自动发布到 Releases。

### 项目结构

| 模块 | 说明 |
|---|---|
| `CMCLauncher` | 主应用模块 |
| `LWJGL` | LWJGL 库的 Android 适配与预编译产物 |
| `LayerController` | 游戏内虚拟按键布局引擎 |
| `ColorPicker` | 颜色选择器组件 |
| `CardGrid` | 卡片网格布局组件 |
| `Terracotta` | 原版服务端下载相关实现 |
| `PetLib` | Rust 实现的底层支持库 |

## 📜 开源许可

本项目代码遵循 **[GPL-3.0 license](LICENSE)** 开源协议。

由于本项目基于 GPL-3.0 协议的项目构建，同样以 GPL-3.0 发布。

## 🙏 鸣谢

### ZalithLauncher

本项目大量抄用了 [ZalithLauncher](https://github.com/ZalithLauncher/ZalithLauncher) 的代码。

非常感谢 Zalith 团队、MovTery 以及所有贡献者的开源工作。没有他们，就没有这个项目。

### PojavLauncher

启动核心基于 [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)，感谢 PojavLauncherTeam 让 Minecraft: Java Edition 能在 Android 上运行。

### 其它开源项目

本项目使用的开源库及其许可信息见 [ZalithLauncher README](https://github.com/ZalithLauncher/ZalithLauncher#引用开源项目)，包括但不限于：

- **LWJGL** - Lightweight Java Game Library
- **SDL3** / **sdl2-compat** - 跨平台多媒体库
- **Mesa** - OpenGL 实现
- **ANGLE** - OpenGL ES 转译层
- **MobileGlues** / **NG-GL4ES** / **SimpleFPEWrapper** - OpenGL 兼容层
- **AndroidX** / **Jetpack Compose** / **Material Design 3** - 界面框架
- **Kotlin** / **kotlinx.coroutines** / **Ktor** - 语言与网络
- **Coil** - 图片加载
- **OkHttp** / **Okio** - 网络传输
- **MMKV** - 键值存储
- **sora-editor** / **tm4e** - 代码编辑器与语法高亮
- **ByteHook** - 原生函数 Hook
- **OpenNBT** - NBT 数据解析
- **Gson** - JSON 序列化

感谢所有开源项目的作者与维护者。
