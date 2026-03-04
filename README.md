# GdxUI

纯净 UI 组件库 — 仅依赖 libGDX + VisUI。

提供通用的控制台输入栏、可选中标签、滚动面板等 Scene2d UI 组件。

## 组件列表

| 组件 | 说明 |
|------|------|
| `ConsoleTextField` | 控制台输入栏 — 历史翻阅 + Enter 提交 + Tab 补全 + 命令提示 + 焦点追踪 |
| `SelectableLabel` | 可选中文本标签 — 长按上下文菜单 + Ctrl+A/C |
| `TextFieldPasteMenu` | 长按粘贴菜单（Android 友好） |
| `HoverFocusScrollPane` | 悬停焦点滚动面板 |
| `SimpleNumPad` | 简易数字键盘 |
| `DrawerPanel` | 抽屉面板 |
| `GSplitPane` | 可拖拽分割面板 |
| `SmartTabPane` | 智能标签页面板 |
| `FreePanViewer` | 自由平移查看器 |
| `PathLabel` | 路径标签 |

## 引用

### Gradle (JitPack)

```gradle
repositories {
    maven { url "https://jitpack.io" }
}

dependencies {
    api 'com.github.shikeik:GdxUI:1.0.0'
}
```

### mavenLocal

```gradle
repositories {
    mavenLocal()
}

dependencies {
    api 'com.github.shikeik:GdxUI:1.0.0'
}
```
