# 项目协作指引

## 项目概览

「码上闪记」是 Android 应用，通过截图、图片分享、短信、划词等入口识别取件码、取餐码和券码，保存到本机并发送通知。技术栈为 Kotlin、Jetpack Compose / Material3、ML Kit、Room、DataStore 和协程。

先阅读 `README.md`、`CONTRIBUTING.md`、`docs/BUILDING.md`；改动识别逻辑时同时阅读 `docs/CORPUS.md`。文档与实现不一致时核对实际代码：目前已有 `app/src/test`，测试使用 JUnit 5，贡献指南的“暂无测试目录”说明已过时。

## 目录与职责

主要源码位于 `app/src/main/java/com/pickupcode/app/`：

- `App.kt`、`MainActivity.kt`：应用初始化、入口和导航。
- `service/`：无障碍服务、快捷磁贴、短信入口，以及 `RecognitionPipeline` / `PostVerifier`。
- `share/`、`ocr/`：外部分享接收、图片处理入口和 ML Kit OCR。
- `extractor/`：码值提取、评分、地址定位、品牌归属、AI 和二维码识别。
- `learner/`：规则学习、统计、常用站点及加密预存地址。
- `data/`：Room 实体、DAO、数据库迁移及仓储。
- `preferences/`：DataStore 设置和 `SecretCipher` 加密。
- `notification/`、`util/`：通知操作、提醒、截图治理、身份码跳转和敏感页面防护。
- `ui/`：Compose 页面、组件和主题；资源在 `app/src/main/res/`。

测试位于 `app/src/test/java/`，语料位于 `app/src/test/resources/corpus/`。`app/src/debug/` 包含调试语料工具，保持其仅在 debug 中使用。

## 构建与验证

使用仓库自带 Gradle Wrapper：Gradle 8.9、AGP 8.7.3、Kotlin 2.0.21、JDK 17。Android 配置为 compileSdk / targetSdk 35、minSdk 26；环境准备见 `docs/BUILDING.md`。通过本地 `local.properties` 或 `ANDROID_HOME` 配置 SDK。

在项目根目录的 PowerShell 中运行：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
# 识别语料回归
.\gradlew.bat :app:testDebugUnitTest --tests "com.pickupcode.app.corpus.CorpusRegressionTest"
# 单个测试类（替换类名）
.\gradlew.bat :app:testDebugUnitTest --tests "com.pickupcode.app.extractor.CodeExtractorTest"
# 涉及混淆、资源、依赖或发布构建时
.\gradlew.bat assembleRelease
```

macOS / Linux 使用 `./gradlew`。CI 执行 lint、debug / release 构建和单测。按改动范围完成相关验证；环境缺失时说明具体阻碍，不将未运行的检查报告为通过。

Debug APK 位于 `app/build/outputs/apk/debug/`，仅生成 `arm64-v8a`。Release 启用 R8、资源收缩和原生库压缩；未配置签名时输出 unsigned APK。

## 修改约定

- 遵循已有 Kotlin 风格、明确命名和职责分包。注释使用中文，解释原因、误报规避和权衡。
- 保持改动聚焦，不顺带升级依赖或重构无关模块。兼容 Android 26，对高版本 API 使用现有的版本保护方式。
- 修改正则、评分、地址窗口或品牌归属时，检查相关单测并运行语料回归；为实际缺陷补充同形态脱敏用例，覆盖正向识别与负向噪声。
- 语料的 `E` 段是人工核对的真值，不可直接用当前识别输出替代。不要将新回归加入 `known-failures.txt` 来绕过失败；已知问题修复后移除对应条目。
- 修改持久化字段时同步检查 Room 数据库版本、迁移链和 schema 导出配置，保护已有用户数据，避免破坏性迁移。
- 修改识别行为时同步检查识别规则页面、设置摘要、系统权限入口名称、README 和测试说明；规则列表应由生产定义直接提供，分段/上下文/地址/期限等技术说明放在开发文档中，面向用户的规则页面只展示可管理的规则，避免界面声称仅靠正则。
- Compose 改动复用现有主题、组件和图标；品牌资源的来源与许可见 `ICONS.md`。

## 隐私与敏感信息

- 记录默认留在本机。AI、地图和快递100 等网络功能须保持用户显式配置和开启的行为。
- 保持 API Key 与预存地址的 AndroidKeyStore / AES-GCM 加密存储，不把敏感内容写入日志。
- 保持 `SensitivePageGuard` 对身份码 / 出库码页面的拒采逻辑，不绕过其读取、截图或入库保护。
- 公开语料、日志、测试和注释中的码值、券号、运单号、电话、姓名与地址须脱敏，保留位数、分隔符、跨行位置及 OCR 噪声形态。
- 不提交 `local.properties`、`keystore.properties`、签名文件或凭据；不输出密钥和密码。
- 修改删除、回收站或截图流程时检查记录与截图文件的生命周期一致性。

## 提交与交付

提交说明可用中文或英文，简洁描述实际变更。交付时说明改了什么、验证结果和未验证项。只有涉及版本发布时才更新 `versionCode` / `versionName`；发布 tag 须与 APK 版本一致，参照 `.github/workflows/release.yml`。
