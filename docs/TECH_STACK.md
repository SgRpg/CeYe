# Android 首版技术栈

选型日期：2026-11-02。此记录是实现决策，不表示已初始化项目或安装依赖。

## 技术选择

| 层次 | 选择 | 用途 |
|---|---|---|
| 平台/语言 | Android 原生、Kotlin | 优先支持 iQOO 12 / Android 16 |
| SDK | minSdk 30、compileSdk/targetSdk 36（首版基线） | 保留 Android 11+ 的媒体 API 能力，验证 API 36 行为；正式上架时另核对发布要求 |
| UI | Jetpack Compose + Foundation + Material 3 自定义主题 | 按 DESIGN.md 复刻；尺寸/间距/token 显式覆盖，不使用默认配色、默认圆角代替 |
| 导航 | AndroidX Navigation Compose | 单张/网格、相册详情、查看器、设置；整理入口由业务状态控制 |
| 状态 | ViewModel + StateFlow + Coroutines + 单向数据流 | 生命周期感知地收集 UI 状态；媒体查询在合适的 IO 调度器运行 |
| 数据库 | Room + KSP | 照片索引、收件箱、相册映射、归档计划、操作日志、扫描游标、恢复/撤销 |
| 小型设置 | Preferences DataStore | 首次使用偏好、浏览方式等；权限状态每次实际检查，不持久化伪权限状态 |
| 图片加载 | Coil 3 / coil-compose | 从本地 content URI 加载缩略图和大图，按显示尺寸解码/缓存 |
| 媒体访问 | MediaStore + ContentResolver + ContentObserver | 枚举已有相册/图片，前台增量同步、移动路径与变更核对 |
| 授权/回收站 | Activity Result API + createWriteRequest/createTrashRequest | 用户确认后执行/记录结果，支持拒绝与中断恢复 |
| 网格/手势 | LazyVerticalGrid + LazyGridState + pointerInput + 系统触觉反馈 | 自定义长按范围选择/取消、边缘滚动、稳定 ID 与正常滚动冲突处理 |
| 大图查看 | Compose Foundation 手势/变换 + Coil | 缩放、平移、双击复位/放大；Fit 保留图片全貌 |
| 依赖注入 | 构造器注入 + AppContainer | 首版规模下保持简单，便于替换 Repository 做测试 |
| 构建 | Android Studio + Gradle Kotlin DSL + Gradle Wrapper + Version Catalog | 创建工程时核对 Kotlin/AGP/Gradle/JDK 兼容组合；统一锁版本 |
| 验证 | JUnit、kotlinx-coroutines-test、Room 测试、Compose UI 测试、截图对照 | 状态机/失败恢复/手势回归 + 固定数据视觉验收 + iQOO 实机验证 |
| 后续后台辅助 | WorkManager | 前台扫描稳定后加入后台变更感知；不依赖它实现即时弹窗或强制拦截 |

首版为本地优先应用。数据保存在设备，核心分类与浏览不依赖服务端。不会为此功能引入云数据库、跨平台桥接或必需的网络图片加载依赖。

## 数据职责

- MediaStore：设备真实媒体项、卷、已有相册路径与现时权限范围。
- Room：应用整理状态及可恢复操作。`InboxEntry`、`AlbumTarget`、`FilingDecision`、`OperationLog`、`VolumeCheckpoint` 为建议的概念实体名。
- DataStore：小型非事务性偏好。扫描游标与收件箱入队需要同一 Room 事务，不能分散到 DataStore 两边提交。
- 照片原文件继续位于手机已有相册，数据库不保存整张图片数据，不建立独立照片仓库。

## 实现顺序

1. 按冻结参考实现组件与页面，固定数据截图核对。
2. 在 iQOO 12 上验证读取现有相册、移动/移回、回收站、授权返回。
3. 接入首次基线、各卷增量扫描、Room 收件箱与归档状态机。
4. 完成长按滑选与边缘滚动实机测试，覆盖分批提交/中断/撤销。
5. 实现常规浏览、放大、分享、历史整理与异常恢复；之后再考虑后台辅助。

## 版本原则

本阶段确定库与架构，不虚构尚未验证的完整依赖版本组合。创建工程时从官方稳定渠道选择兼容版本，Compose 库使用稳定 BOM 对齐；BOM 不替代 Kotlin/Compose 编译插件/AGP 的兼容检查。将全部版本固定在 `libs.versions.toml` 和 Wrapper 中，禁止动态 `+` 版本。

## 官方依据

- [Android 架构建议](https://developer.android.com/topic/architecture/recommendations)：Compose、ViewModel、Flow、分层和单向数据流。
- [Compose Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3)：通过自定义色彩/字体/形状落实已确认设计。
- [Compose 手势](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures)：pointerInput、长按拖动与变换手势。
- [Room](https://developer.android.com/training/data-storage/room)：结构化本地持久化与 KSP。
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)：异步小型设置存储。
- [Coil Compose](https://coil-kt.github.io/coil/compose/)：AsyncImage 与显示尺寸匹配。
- [Android 16 SDK](https://developer.android.com/about/versions/16/setup-sdk)：API 36 配置。
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom)：统一 Compose 库版本。
