# 个人IP打造 (PersonalIP)

本地优先的个人 IP 内容管理工具，帮助用户管理减重、塑形、皮肤亚健康、抗衰等素材，按周自动排期，调用 AI 生成朋友圈文案，保证一周内素材和文案不重复。用户审核后复制到微信发布。

> 本项目分阶段开发，当前进度：**全部 9 阶段完成**（骨架 / SAF / Room / 素材库 / 排期 / AI 生成 / 今日待发 / 设置与备份 / 合规检测）。

## 技术栈

- Kotlin + Jetpack Compose（Material 3）
- Hilt（依赖注入）
- Room、DataStore、WorkManager
- Retrofit + OkHttp
- Coil
- ML Kit（中文 OCR）
- SAF（Storage Access Framework，DocumentFile）
- 目标 SDK 34，最低 SDK 29（Android 10）

## 目录结构

```
.
├─ settings.gradle.kts
├─ build.gradle.kts
├─ gradle.properties
├─ gradle/
│  ├─ libs.versions.toml          # 版本目录
│  └─ wrapper/gradle-wrapper.properties
└─ app/
   ├─ build.gradle.kts
   ├─ proguard-rules.pro
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ res/                       # 主题、颜色、字符串、图标
      └─ java/com/personalip/app/
         ├─ PersonalIpApp.kt        # @HiltAndroidApp + WorkManager 配置
         ├─ MainActivity.kt         # 单 Activity 入口
         ├─ ui/
         │  ├─ RootState.kt         # 根目录授权状态抽象（第 2 阶段接入）
         │  ├─ theme/                # Color / Type / Theme
         │  └─ navigation/           # 路由与 NavHost
         └─ ui/screens/              # 四个底部导航占位页
```

## 运行方式

1. 用 **Android Studio Hedgehog 及以上**（或 Iguana）打开根目录。
2. 若提示缺少 Gradle Wrapper jar，Android Studio 会在首次 Sync 时根据
   `gradle/wrapper/gradle-wrapper.properties` 自动下载并补齐。
   命令行用户也可在装有本地 Gradle 的环境下执行 `gradle wrapper` 生成 wrapper。
3. 选择 API 29+ 的模拟器或真机，点击 Run。

> 命令行构建：`./gradlew :app:assembleDebug`（需先生成 wrapper jar）。

## 如何选择根文件夹（第 2 阶段）

1. 首次启动 App，进入授权引导页，点击「选择文件夹」。
2. 系统弹出文件夹选择器（SAF `ACTION_OPEN_DOCUMENT_TREE`），任意选择一个**你有权读写的目录**
   （如内部存储的某个目录或 SD 卡根目录）。
3. App 会自动在该目录下创建 `PersonalIP/` 根目录，以及全部子目录：
   ```
   PersonalIP/
   ├─ 素材库/{男生大体重,男生小体重,女生大体重,女生小体重,10斤内塑形,改善亚健康,抗衰}
   ├─ 输出/
   ├─ 人设/
   ├─ 配置/
   └─ 备份/
   ```
4. 授权通过 `takePersistableUriPermission` 持久化，重启后无需再次询问。
5. 后续素材、文案、人设、备份均落盘到上述目录，数据库只存元数据。
6. **卸载 App 不会删除素材**；重新安装后再次进入引导页，选择**同一个根文件夹**即可恢复
   （App 会幂等补齐目录结构，已有文件保留）。
7. 「重新选择根文件夹」入口在设置页（第 8 阶段接入），切换时可选是否迁移旧数据。

> 本项目**不申请** `MANAGE_EXTERNAL_STORAGE`，全部本地读写走 SAF / DocumentFile。

## 第 1 阶段交付说明

- 已配置全部依赖（Hilt / Room / DataStore / WorkManager / Retrofit / OkHttp / Coil / ML Kit / DocumentFile / Security-Crypto / Moshi）。
- Hilt 已启用：`PersonalIpApp` 标注 `@HiltAndroidApp`，`MainActivity` 标注 `@AndroidEntryPoint`，WorkManager 通过 `HiltWorkerFactory` 注入（manifest 已移除默认 `WorkManagerInitializer`）。
- Compose Navigation：底部导航四个标签页（今日待发 / 素材库 / 排期 / 设置），均为占位页，后续阶段逐步实现。

## 第 2 阶段交付说明

- SAF 根目录授权：`data/storage/RootFolderRepository` 负责权限持久化、目录结构创建与校验。
- 目录结构常量：`data/storage/FolderConstants`（根名 `PersonalIP`、显示名「个人IP打造」、7 个默认分类、禁止字符）。
- 根 Uri 持久化使用 DataStore（`data/settings/AppDataStore`），保证启动期先于数据库可读。
- 首次启动引导页：`ui/onboarding/OnboardingScreen` + `OnboardingViewModel`，调用系统 `OpenDocumentTree` 选择器。
- 全局授权网关：`ui/RootStateHost` + `ui/root/RootViewModel`，按授权状态切换 Loading / 引导页 / 主界面。
- 已实现「重新选择根文件夹 + 迁移」能力（`reselectRoot`），UI 入口在第 8 阶段接入。
- 幂等结构补齐（`ensureStructure`），用于升级或重选同一文件夹后恢复。

## 第 3 阶段交付说明

- Room 数据库 `PersonalIpDatabase`（version=1，`@TypeConverters(Converters)`），7 张表：
  - `categories(id, displayName, folderName, createdAt)` — `folderName` 唯一索引。
  - `materials(id, filePath, categoryId, tags, mimeType, ocrText, useCount, lastUsedAt, createdAt)` — `filePath` 唯一、`categoryId` 索引；`filePath` 存「相对 PersonalIP 根的相对路径」以便重选根后可解析。
  - `usage_records(id, materialId, usedAt, weekId, postId)` — 冷却期 / 周内不重复判定。
  - `weekly_plans(id, weekId, dayOfWeek, time, materialId, postId, status)` — `dayOfWeek` ISO(1=周一)。
  - `posts(id, materialId, content, alternative1..3, tags, imageSuggestion, publishTime, status, createdAt)`。
  - `persona(id=1 单行, nickname, identity, background, personality, catchphrase, targetAudience, forbiddenWords)`。
  - `settings(key, value)` — 用户面向应用设置（模型名/冷却期/每日条数等）；系统级设置仍走 DataStore。
- `PostStatus` 枚举（草稿/待发/已发/跳过）+ `Converters`（List<String> 用 Moshi 序列化、枚举用 name）。
- 7 个 DAO（含排期选材 `pickCandidates`、周内已用 `usedInWeek` 等查询）。
- `DatabaseSeeder` 幂等写入 7 个默认分类与空人设；在 `RootViewModel` 首次创建时触发。
- `di/DatabaseModule` 提供数据库与各 DAO（`@Singleton`）。

## 第 4 阶段交付说明

- 素材库完整闭环：导入 → 分类目录复制 →（图片）OCR → 写元数据；真实文件仍在 SAF 根目录。
- `data/storage/FileNameUtil`：`时间戳_短UUID` 文件名，过滤非法字符；按 MIME/原名推断扩展名。
- `data/ocr/OcrService`：ML Kit 中文识别器，大图下采样后识别，失败返回空串（非关键路径）。
- `data/material/MaterialRepository`：导入、标签、重试 OCR、删除（含真实文件）、解析真实 Uri。
  - `filePath` 存「相对 PersonalIP 根的相对路径」，重选根后可重新解析。
- `MaterialDao.observeFiltered(categoryId, query)`：分类筛选 + 自由文本搜索（匹配 filePath / ocrText / tags）。
- UI `ui/library/LibraryScreen` + `LibraryViewModel`：
  - 分类筛选条（含「全部」）、搜索框（250ms 防抖）、标签筛选条（去重）。
  - 3 列网格，Coil 加载图片缩略图，视频/文本显示图标；显示分类徽标与使用次数。
  - 每条素材：编辑标签、重新 OCR、复制 OCR 文字、删除。
  - 导入 FAB：`OpenDocumentMultiple` 选择 image/video/text/application，导入到当前选中分类。

## 第 5 阶段交付说明

- `data/schedule/ScheduleRepository`：一周闭环排期核心算法。
  - 输入：weekId、每日时段（如 `["08:00","12:00","18:00"]`）、参与分类、冷却期。
  - 选材：按 `useCount` 升序、`lastUsedAt` 升序取最少使用的素材；过滤冷却期（7/14/30 天）内已发过的。
  - 填充 `7 × 每日条数` 个槽位，每个素材只取一次 → **本周内不重复**。
  - 素材不足时返回 `Shortfall`，按分类列出可用数量，提示「是否本周少发或先导入」。
  - 支持 `replaceMaterial`（换素材）、`movePlan`（上移/下移调整顺序）、`setStatus`。
- `WeekIdUtil`：ISO 周编号 `2026-W38`、周几中文（周一…周日），基于 java.time（minSdk 29 无需脱糖）。
- `MaterialDao.getAllSortedByUsage()`：按使用次数升序的选材基础查询。
- UI `ui/schedule/ScheduleScreen` + `ScheduleViewModel`：
  - 配置区：每日时段可增删、冷却期 7/14/30 天、参与分类多选。
  - 「生成本周计划」按钮 + 进度态。
  - 排期卡片：缩略图、周几/时段、分类、使用次数、标签、状态；操作：上移/下移/换素材/标记已发/跳过。
  - 不足时通过 snackbar 展示 shortfall 文案。

> 拖拽调整：当前以「上移/下移 + 换素材」实现调整能力；真·长按拖拽可作为后续增强。

## 第 6 阶段交付说明

- AI 生成朋友圈完整闭环：配置接口 → 组装 prompt → 调 OpenAI 兼容接口 → 解析 JSON → 合规扫描 → 落库 + 写 Markdown。
- `data/ai/AiRepository`：核心生成逻辑。
  - 配置校验 → 组装消息（人设 + 合规红线 + 素材 OCR/图片 + 语气/目标）→ 调用接口（失败重试一次）→ 解析 JSON 草稿 → 本地合规扫描 → 落库 posts → 写 Markdown 到 输出/<周>/<周几>/<时间_分类_编号>.md → 关联 weekly_plans。
  - 图片素材通过 `ImageEncoder` 转 DataUri 附给支持多模态的模型（best-effort）。
- `data/ai/GenerationInput`：语气（专业/亲和/真实/励志/干货）+ 转化目标（点赞/评论/私信/成交）。
- `data/ai/PostDraft`：AI 返回的 JSON 草稿（正文 + 3 个备选 + 话题标签 + 配图建议 + 发布时间 + 合规提醒）。
- `data/ai/remote`：Retrofit `OpenAiApi`（`@Url` 动态 baseUrl）+ DTO（`ChatRequest`/`ChatMessage`/`ContentPart`/`ImageUrl`）。
- `data/settings/AiConfigRepository`：baseUrl/model（DataStore）+ apiKey（`EncryptedSharedPreferences` 加密存储）。
- `di/NetworkModule`：OkHttp 认证拦截器（Bearer Key）+ 日志（BASIC 不打印 body）+ Retrofit。
- UI `ui/ai/AiConfigDialog` + `AiConfigViewModel`：配置 baseUrl / 模型 / API Key，预置 DeepSeek/通义/Kimi/智谱/OpenAI 示例。
- 排期页集成：每条排期卡片有 ✨ 生成按钮 → 选语气/目标 → 生成 → snackbar 反馈（含合规命中数）。

## 第 7 阶段交付说明

- 今日待发首页：展示当天（ISO 周几）的排期项 + 已生成的朋友圈文案。
- `ui/screens/HomeViewModel` + `HomeScreen`：
  - 列表项：时段 · 分类、缩略图、文案正文（6 行省略）、话题标签、配图建议、备选 1、合规提醒、状态徽标。
  - 「复制文案」：拼装正文 + 话题标签 → 系统剪贴板。
  - 「微信分享」：`Intent.ACTION_SEND` + `setPackage("com.tencent.mm")` 直接拉起微信；未安装则回退系统分享选择器。
  - 状态标记：草稿 / 待发 / 已发 / 跳过（同步 `weekly_plans` 与 `posts`）。
  - 未生成文案时提示「请到排期页点击 ✨ 生成」。
  - **不做自动发布朋友圈**，仅复制到剪贴板与系统分享。
- `AndroidManifest.xml` 增加 `<queries><package android:name="com.tencent.mm"/></queries>`（Android 11+ 包可见性）。

## 第 8 阶段交付说明

- 人设设置 `data/persona/PersonaRepository`：
  - 读写 `persona` 表（单行 id=1）+ 同步落盘 `人设/persona.json`（Moshi 序列化）。
  - 支持 `importFromJson`（重装后从 JSON 恢复人设）。
- 设置页 `ui/screens/SettingsScreen` + `SettingsViewModel`：
  - 根目录：显示当前 Uri、重新选择根文件夹（SAF 选择器 + 迁移选项）。
  - AI 接口：复用 `AiConfigDialog`（baseUrl / 模型 / API Key 加密存储）。
  - 冷却期：7/14/30 天（存 settings 表）。
  - 人设编辑：昵称、身份、专业背景、性格、口头禅、目标客户、禁用词（每行一个）。
  - 新建分类：输入名 → 在 素材库/ 下创建子文件夹 → 写 categories 表。
  - 备份导出 Zip：`data/backup/BackupRepository` 遍历根目录树打包到 备份/backup_yyyyMMdd_HHmmss.zip（排除 备份 目录自身）。

## 第 9 阶段交付说明

- 合规检测与优化：
  - `data/compliance/ComplianceChecker`：绝对化承诺黑名单（根治/包瘦/无副作用/七天瘦十斤/永不反弹等 20+ 词）+ 通用风险词提示 + 用户禁用词。
  - 三级风险：`HARD_BLOCK`（硬阻断）/ `USER_FORBIDDEN`（用户禁用词）/ `WARNING`（提示）。
  - `systemRules`：组装给 AI 的合规红线系统提示（健康类必须客观、不得绝对化承诺、需提示因人而异）。
  - `healthReminder`：面向用户的合规提醒文案。
  - AI 生成时：`AiRepository` 调用 `complianceChecker.check()` 扫描生成结果，命中写入 Markdown 合规提醒章节。
  - 复制 / 分享前：`HomeViewModel.checkCompliance()` 实时检测，命中风险词时弹窗提示（硬阻断用红色 + 「仍然继续」），用户确认后才执行。
  - 今日待发卡片：每条文案附带合规提醒（效果因人而异，严重情况请咨询专业人士）。

## 如何配置 AI 接口

1. 打开 App，进入底部导航「设置」。
2. 在「AI 接口」卡片点击「配置 baseUrl / 模型 / API Key」。
3. 填写：
   - **Base URL**：OpenAI 兼容接口地址。示例：
     - DeepSeek：`https://api.deepseek.com/v1`
     - 通义千问：`https://dashscope.aliyuncs.com/compatible-mode/v1`
     - Kimi：`https://api.moonshot.cn/v1`
     - 智谱：`https://open.bigmodel.cn/api/paas/v4`
     - OpenAI：`https://api.openai.com/v1`
   - **模型名**：如 `deepseek-chat` / `gpt-4o-mini` / `qwen-plus`。
   - **API Key**：`sk-...`（使用 `EncryptedSharedPreferences` 加密存储，不会明文落盘）。
4. 点击「保存」。
5. 进入「排期」页，点击某条排期的 ✨ 按钮，选语气和转化目标，生成朋友圈文案。

> 生成失败时 App 会提示「AI 调用失败，请检查网络与接口配置后重试」并自动重试一次。

## 如何导入素材

1. 打开 App，进入底部导航「素材库」。
2. 在顶部选择分类（如「男生大体重」），或选「全部」。
3. 点击右下角 + 按钮，从相册/文件选择图片、视频或文本。
4. App 自动将文件复制到 `素材库/<分类名>/` 下，并对图片执行 ML Kit 中文 OCR。
5. 导入后可：编辑标签、重新 OCR、复制 OCR 文字、删除（同时删除真实文件）。

> 文件名格式：`分类_yyyyMMddHHmmss_xxxxxxxx.ext`（时间戳 + 短 UUID，避免重名）。

## 如何使用（完整流程）

1. **首次启动**：选择一个文件夹作为存储位置 → 自动创建 `PersonalIP/` 目录结构。
2. **设置人设**：在「设置」页填写昵称、身份、专业背景、性格、口头禅、目标客户、禁用词。
3. **配置 AI**：在「设置」页配置 baseUrl / 模型 / API Key。
4. **导入素材**：在「素材库」按分类导入图片/视频/文本。
5. **生成排期**：在「排期」页设置每日时段、冷却期、参与分类 → 生成本周计划。
6. **生成文案**：在排期卡片点击 ✨，选语气/目标 → AI 生成朋友圈文案（含 3 个备选）。
7. **今日待发**：首页展示当天要发的文案 → 复制文案 / 微信分享 → 标记已发/跳过。

## 合规与隐私

- 仅请求 `INTERNET` 权限用于调用 AI 接口；不申请 `MANAGE_EXTERNAL_STORAGE`，所有本地读写走 SAF。
- 不做自动发布朋友圈，仅复制到剪贴板与系统分享。
- 健康减重/抗衰/皮肤调理内容必须附带合规提醒；避免绝对化承诺（根治、包瘦、无副作用等）。
- 复制/分享前自动检测禁用词并提醒；API Key 加密存储。
