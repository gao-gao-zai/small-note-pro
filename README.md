# small-note-pro
腕上小纸条Pro

一款用于在手机/手表上快速阅读纯文本的小工具，支持文件选择、编码识别、阅读进度保存、收藏、Markdown 渲染与阅读样式自定义。

## 功能

- **打开文件**
  - 通过系统文件选择器打开文本文件（会申请并持久化读取权限）。
- **自动识别编码**
  - 优先识别 BOM，其次在多种常见编码中选择最可能的结果。
  - 小文件会一次性读取（最多 5MB）；超过会自动进入“大文件模式”（分页加载）。
- **阅读体验**
  - 保存并恢复阅读进度。
  - 可隐藏/显示文本，支持“单击隐藏、长按显示/菜单”等交互。
- **收藏**
  - 收藏/取消收藏当前文件，支持查看收藏列表并一键打开。
- **样式设置**
  - 背景色、文字颜色、字号、亮度等。
  - UI 配色（菜单/对话框背景、UI 文字、强调色）。
- **局部样式（非 Markdown 模式）**
  - 支持对指定范围/行号添加局部字号、亮度、颜色覆盖。
- **Markdown 渲染（按文件开关）**
  - 开启后以 Markdown 方式渲染文本；开启会使“局部样式”失效。
  - 大文件模式下会禁用 Markdown 与局部样式。

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **导航**: `androidx.navigation:navigation-compose`
- **数据存储**: DataStore Preferences（收藏、全局偏好、每文件 Markdown 开关、阅读进度等）
- **Markdown**: `org.commonmark:commonmark`

## 环境要求

- **Android Studio**: 建议使用最新稳定版
- **JDK**: 17
- **Android Gradle Plugin**: 8.5.2
- **Kotlin**: 1.9.24
- **minSdk**: 30
- **targetSdk/compileSdk**: 34

## 运行

用 Android Studio 打开项目后直接运行 `app` 模块即可。

也可以通过命令行构建：

```bash
./gradlew :app:assembleDebug
```

## 打包

Release 构建（开启混淆与资源压缩）：

```bash
./gradlew :app:assembleRelease
```

本项目开启了 ABI Split（`arm64-v8a`、`armeabi-v7a`），Release 默认会输出按 ABI 拆分的 APK。

## 使用说明

1. 启动后在阅读页长按打开菜单。
2. 选择“选择文件”打开一个纯文本文件。
3. 长按打开菜单可进行：收藏、进入收藏列表、样式设置、局部样式、Markdown 渲染开关。

## 常见问题

- **提示“不是纯文本文件/无法识别文本编码”**
  - 该文件可能包含二进制内容，或编码不在当前识别候选中。
- **提示“文件过大”**
  - 小文件全文读取上限为 5MB；更大的文件会自动进入“大文件模式”（分页加载）。
  - 大文件模式下会禁用 Markdown 与局部样式。
- **为什么开启 Markdown 后“局部样式”不可用？**
  - Markdown 渲染会重新生成富文本样式，为避免冲突当前选择禁用局部覆盖。

## 项目结构（节选）

```text
app/src/main/java/io/github/gaozaiya/smallnotepro/
  MainActivity.kt
  ui/
    SmallNoteProApp.kt
    screens/
      ReaderScreen.kt
      FavoritesScreen.kt
      StyleSettingsScreen.kt
    components/
      ColorPicker.kt
  data/
    AppDataStore.kt
    FavoritesRepository.kt
    ReaderPreferencesRepository.kt
  util/
    TextContentLoader.kt
  markdown/
    MarkdownRenderer.kt
```

## License

MIT License. See `LICENSE`.
