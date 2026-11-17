# CMCLauncher-PE

面向 Android 的 Minecraft: Java Edition 启动器纯净版。

PE 版移除了完整版中全部视觉特效与过渡动画，界面直接呈现，不做任何渲染修饰。

## 与完整版的区别

- 无雨滴效果
- 无节日特效（雨、雪、烟花、萤火虫、蝙蝠、爱心）
- 无节日标题替换
- 无液态玻璃高光
- 无页面切换过渡、无淡入淡出、无滑动动画
- 无加载闪烁动画
- 所有界面切换为瞬时切换

## 安装

与完整版使用不同包名（`com.cmc.launcher.pe`），可同时安装。

## 构建

```bash
./gradlew :CMCLauncher:assembleDebug -Darch=arm64
```

构建产物位于 `ZalithLauncher/build/outputs/apk/debug/`。

## 说明

启动核心基于 PojavLauncher，UI 使用 Jetpack Compose 与 Material Design 3。