# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.2.1] - 2026-09-24

### 修复

- **相册选择器 SQL 语法兼容**：修复 OEM 定制 ROM（小米/OPPO/华为等）ContentResolver sortOrder 中使用 `LIMIT` 子句时报错 `Invalid token LIMIT` 的问题。原实现 `DATE_ADDED DESC LIMIT 1` 在部分设备上无法解析，改用扫描媒体时同步收集 bucket 名称，不再依赖 LIMIT 子句。
  - 影响文件：`data/gallery/MediaGallery.kt`（重写 `queryBucketName`、`queryMedia`，新增 SecurityException 捕获）
- **权限预检查**：`startImport()` 现在先用 `ContextCompat.checkSelfPermission` 校验存储权限，已授权则直接打开相册选择器，避免重复弹出系统权限申请窗。
  - 影响文件：`ui/library/LibraryScreen.kt`

### 技术债

- `build.gradle.kts`：Release 构建临时关闭 R8 压缩（`isMinifyEnabled = false`），规避 AGP 8.5.2 在 minifyReleaseWithR8 阶段触发 `ConcurrentModificationException` 的内部 Bug。后续升级 AGP 版本后需重新启用。

---

## [1.2.0] - 2026-09-24

### 新增

- **自定义相册选择器**（替代系统 Photo Picker）：通过 `MediaStore + ContentResolver` 直接查询系统全部相册（按 bucketId 分组），解决了 `ActivityResultContracts.GetMultipleContents()` 只能展示 3 个系统预设相册（相机/屏幕截图/下载内容）的限制。
  - 两级导航：相册列表页（封面+名称+数量）→ 相册网格页（3 列多选，支持图片/视频，视频有标记，选中有遮罩和计数）
  - 影响文件：
    - `data/gallery/MediaGallery.kt`（新建）— 查询系统相册与媒体的核心工具
    - `ui/gallery/AlbumPickerDialog.kt`（新建）— 两级相册选择 Dialog UI
    - `ui/library/LibraryScreen.kt`（重写导入流程）
- **存储权限声明**：`AndroidManifest.xml` 新增 `READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`（Android 13+）和 `READ_EXTERNAL_STORAGE`（Android 12-）权限。

---

## [1.1.0] - 2026-09-24

### 新增

- **素材库两级导航架构**：
  - 文件夹列表页 → 文件夹素材页，通过 `currentFolderId` StateFlow 管理页面切换
  - 影响文件：`ui/library/LibraryViewModel.kt`、`ui/library/LibraryScreen.kt`
- **文件夹管理功能**：新建 / 删除 / 重命名 / 设置
- **文件夹级设置持久化**：`CategoryEntity` 新增 `settingsJson` 字段，存储 AI 配置（autoOcr、autoTags、postTone、postGoal、cooldownDays）
  - 影响文件：`data/local/entity/CategoryEntity.kt`、`domain/model/CategorySettings.kt`（新建）、`data/local/CategorySettingsMapper.kt`（新建）
- **Room 数据库 V1→V2 迁移**：新增 `MigrationV1ToV2`，自动为已有 categories 表添加 `settingsJson` 列
- **闹钟式时间选择 + 自动排序**：排期页用 Material3 `TimePicker` 替换 `OutlinedTextField`，时间增删改后自动升序排列
  - 影响文件：`ui/schedule/ScheduleViewModel.kt`、`ui/schedule/ScheduleScreen.kt`
- **级联数据清理**：删除文件夹时，先删除关联素材，再删除排期和使用记录，保证数据完整性
  - 影响文件：`data/local/dao/MaterialDao.kt`、`data/local/dao/WeeklyPlanDao.kt`、`data/local/dao/UsageRecordDao.kt`

---

## [1.0.0] - 2026-09-23

### 初始版本

- 9 阶段完整交付：SAF 根目录授权 / Room 数据库 / 素材库 / 排期 / AI 文案生成 / 今日待发 / 设置与备份 / 合规检测
- 技术栈：Kotlin + Jetpack Compose (Material 3) + Hilt + Room + DataStore + WorkManager + Retrofit + Coil + ML Kit OCR
- 目标 SDK 34，最低 SDK 29（Android 10）
