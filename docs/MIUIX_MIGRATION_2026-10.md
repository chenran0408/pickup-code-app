# miuix 界面迁移

## 来源与版本

框架来自 [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)，采用 Apache-2.0 许可。早期 v1.3.0 使用单体 `miuix:0.3.0`；当前源码升级到稳定版 `0.9.4`，使用官方 Android 模块 `miuix-ui-android`、`miuix-preference-android`、`miuix-icons-android`。本次未加入 `miuix-nav`：现有 `Screen` 导航足以表达一级与二级页面，不需要为了底栏替换整个导航栈。

Miuix 0.9.4 上游以 Compose Multiplatform 1.12.0、Kotlin 2.4.20、AGP 9.4.1 和 Gradle 9.7.1 发布。Compose 1.12 官方要求 compileSdk 37，因此仅提升 compileSdk；minSdk 26 与 targetSdk 35 保持不变。AGP 9.x 构建使用内置 Kotlin 和新 DSL：移除 `org.jetbrains.kotlin.android`，并将 JVM 目标配置迁移到 `compilerOptions`。KSP 保持 Room 代码生成入口。版本和正式模块见 [Miuix v0.9.4 release notes](https://github.com/compose-miuix-ui/miuix/releases/tag/v0.9.4) 与[官方模块说明](https://compose-miuix-ui.github.io/miuix/guide/getting-started)。

Miuix 0.9.4 Android AAR 元数据要求 `minCompileSdk=37`；Compose 1.12 也要求 compileSdk 37。当前 API 37 Preview package id 为 `platforms;android-37.0`，位于 Android Preview channel；CI 通过 `sdkmanager --channel=3` 安装。minSdk 26 与 targetSdk 35 保持不变。

KSP 从 2.3.10 更新到当前稳定版 2.3.12。CI 首次执行 KSP 时，Room 2.6.1 报 `unexpected jvm signature V`；这是 KSP2 处理 Room suspend Unit 方法的已知问题，Room 2.7+ 已修复，因此 runtime、ktx、compiler 统一升级到稳定版 2.8.5。DAO、Repository、schema JSON 和迁移逻辑均未修改；CI 会运行现有 SQLite migration check 与单元测试。

## 界面范围

- 首页、详情、通知来源、设置、身份码、地址、规则、统计、重复记录和回收站使用同一套 miuix 色彩、字体与平滑圆角。
- 首页、身份码、设置为一级导航，使用 Miuix 原生 `FloatingNavigationBar` / `FloatingNavigationBarItem`；详情、统计、规则、地址、重复整理、回收站、通知来源与设置子页为二级页面，不显示底栏。统计继续从侧边栏进入。
- 一级页面使用 Miuix `Scaffold` 承载原生底栏和首页 FAB。首页保留手动添加主操作；取件码列表底部留白由 Miuix FAB 默认尺寸提供。路由仍由可恢复的 `Screen` 状态驱动，Back 优先回到首页或当前二级页面的父级。
- 可见卡片、按钮、输入、开关、复选框、滑条、顶部栏、进度、Snackbar、确认弹窗和下拉弹出层优先调用 Miuix 原生组件。设置开关、滑条和外观单选使用 `SwitchPreference`、`SliderPreference` 与 `RadioButtonPreference`。`Widgets.kt` 仅保留需映射旧调用签名的基础适配；Material3 暂留作颜色语义与抽屉手势容器。抽屉本身没有采用自制仿件，其内容仍使用 Miuix 表面和控件。
- 唯一保留的 Compose 平台 `Dialog` 是详情页的无边框全屏截图查看器；这是沉浸式图片查看窗口，不是常规提示/确认弹窗。常规业务弹窗统一走 Miuix `OverlayDialog`。
- 主题色板与文字层级来自 `MiuixTheme`；Material3 语义颜色仅映射给尚未迁移的旧页面。识别、持久化、授权、通知和分享业务路径保持原有实现。
- 跟随系统、浅色、深色三种选择保留。输入提示移到字段下方，避免与 miuix 浮动标签重叠；滑条保留拖动结束保存和无障碍调整回调。

## 验证

v1.3.0 的历史验证记录不代表本次 0.9.4 升级的构建结果。本次改造的 Gradle 验证状态应以任务交付报告为准；Android SDK 许可未获接受时，`assembleDebug`、单测、Lint 与 Release 均不能标记为通过。

miuix 包已在用户设备覆盖安装。进一步统一深浅色配对、普通卡片表面、状态标签和输入提示，修复底部三个身份码入口的文字/图标颜色；取消部分摘要和回收站正文额外的透明度。色彩对比度测试覆盖深浅色正文、摘要、输入提示、柔和强调及错误状态，相关组合均达到 4.5:1。详见 [主题统一与深浅色适配](MIUIX_THEME_2026-10.md)。

通知实际连接在用户调整 Thanox 权限并重启后恢复，本地合成通知验证结果见 [通知连接恢复](NOTIFICATION_CONNECTION_2026-10.md)。这些既有记录属于 v1.3.0，不作为本次源码重构的 UI 设备验收结果。
