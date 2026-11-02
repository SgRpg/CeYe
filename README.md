# 册页 CeYe

Android 原生、本地优先的照片分类应用。打开后发现新图片，先归入手机已有相册，再进入常规浏览。

**当前稳定版：v0.1.4 · 支持 Android 11 及以上**

### [下载册页 v0.1.4 稳定版 APK](https://github.com/SgRpg/CeYe/releases/download/v0.1.4/CeYe-v0.1.4-release.apk)

[版本说明](https://github.com/SgRpg/CeYe/releases/tag/v0.1.4) · [全部历史版本](https://github.com/SgRpg/CeYe/releases) · [SHA-256 校验文件](https://github.com/SgRpg/CeYe/releases/download/v0.1.4/SHA256SUMS.txt)

已安装旧版的用户可以直接覆盖安装，保留已有整理记录和分类决定，无需先卸载。应用不申请网络权限，不上传照片。

## v0.1.4 修复与更新

- **修复指定相册后照片自动取消选中**：选中照片并指定归档相册后，继续保持勾选，可直接点击另一个相册更改归档位置。手动取消或确认提交归档时清除选中；仅打开确认窗口不会清除。
- **相册入口改为封面卡片**：显示每个相册的首图和名字。收起时按当前宽度展示一行完整卡片，点击“全部相册”后展示可滚动的卡片网格。
- **精简收件箱文字**：移除“本次 × 张／× 张待提交”、长按滑动说明和“归入相册”标题，保留已选数量、全选与日期分组操作。
- **取消归档相册预选**：授权后直接进入应用，分类时使用全部可读取的本地相册，不再要求预先勾选；设置只保留“照片权限”。
- **使用系统照片权限弹窗**：首次打开直接请求系统权限，拒绝后不会在重开应用时自动反复弹窗，可通过授权入口主动重试。

本版已通过 4 项单元测试、4 项 Android API 37 模拟器设备测试、Android Lint 和签名构建；已在 iQOO 12 / Android 16 上验证覆盖安装与冷启动。选中保留、连续更换相册、手动取消和提交清除流程已通过模拟器端到端验证。

## 功能

- 首次建立历史基线，检测之后新增的本地图片。
- 所有来源的新图片进入收件箱，成功移动或移入系统回收站后标记为已处理；旧照片另有历史整理入口。
- 单张分类、三列网格、日期选择、长按范围选择/取消与边缘滚动。
- 分类决定立即保存，选定相册后保留勾选，允许反复更改目的地并分批提交；系统授权取消时保留决定。
- 移动照片、回收站、撤销分类、归档记录、中断恢复、待修复。
- 相册/照片浏览、缩放与系统分享、历史整理。
- 设置仅保留“照片权限”。历史整理与最近归档位于相册页“整理”。

## 构建

需要 Android SDK 36、Build-Tools 36.0.0、JDK 17 或支持当前 Gradle 的更新 JDK。配置 ANDROID_HOME 与 JAVA_HOME。

```shell
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Windows 正式签名构建：`powershell -ExecutionPolicy Bypass -File tools/build-release.ps1`。
首次执行会在用户目录 `.ceye` 创建长期签名密钥和 Windows 加密凭据，随后构建正式 APK。两者必须妥善备份；不进入 Git。其他平台可设置 CEYE_KEYSTORE / CEYE_STORE_PASSWORD / CEYE_KEY_PASSWORD 后执行 assembleRelease。

## 版本

源码位于 [SgRpg/CeYe](https://github.com/SgRpg/CeYe)。各版安装包使用 [GitHub Releases](https://github.com/SgRpg/CeYe/releases) 长期归档，并附 SHA-256 和对应源码提交。

当前稳定版为 **v0.1.4**。仅使用本地媒体 API，无网络权限、账号或云同步。历史发布版本和说明保留，首页下载入口指向当前稳定版。

## 设计与数据

`DESIGN.md` 保存最初确认的视觉基准；`docs/settings-simplification-v2.md` 和 `docs/inbox-album-cards-v0.1.3.md` 记录后续界面调整。早期交互草案与说明作为历史设计资料保留。实际业务以原生代码和当前发行说明为准。

媒体文件仍保存在系统共享相册；Room 只保存索引、分类决定及操作记录。卸载应用会删除本地整理记录；覆盖安装相同签名的更新版保留记录。数据库结构升级必须新增迁移，禁止破坏性清库。

以完整照片访问为最佳工作模式；部分访问仅整理已授权范围。同名冲突不覆盖已有文件，跨卷移动暂不支持。相册列表来自系统开放的本地媒体索引，云端、私密及空相册不保证能读取；大图库的纯增量与分页优化尚未实现。
