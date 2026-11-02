# 册页 CeYe

Android 原生、本地优先的照片分类应用。打开后发现新图片，先归入手机已有相册，再进入常规浏览。

## 功能

- 首次建立历史基线，检测之后新增的本地图片。
- 已位于指定目标相册中的新图片自动归档。
- 单张分类、三列网格、日期选择、长按范围选择/取消与边缘滚动。
- 目标选择立即保存，分批提交；系统授权取消时保留决定。
- 移动照片、回收站、撤销分类、归档记录、中断恢复、待修复。
- 相册/照片浏览、缩放与系统分享、历史整理。
- 设置仅保留“归档相册”和“照片权限”。历史整理与最近归档位于相册页“整理”。

## 构建

需要 Android SDK 36、Build-Tools 36.0.0、JDK 17 或支持当前 Gradle 的更新 JDK。配置 ANDROID_HOME 与 JAVA_HOME。

```shell
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Windows 正式签名构建：`powershell -ExecutionPolicy Bypass -File tools/build-release.ps1`。
首次执行会在用户目录 `.ceye` 创建长期签名密钥和 Windows 加密凭据，随后构建正式 APK。两者必须妥善备份；不进入 Git。其他平台可设置 CEYE_KEYSTORE / CEYE_STORE_PASSWORD / CEYE_KEY_PASSWORD 后执行 assembleRelease。

## 版本

源码与各版 APK 位于 https://github.com/SgRpg/CeYe 。安装包使用 GitHub Releases 长期归档，并附 SHA-256 和对应源码提交。

当前为 v0.1.0 预发布实现。仅使用本地媒体 API，无网络权限、账号或云同步。需要真实手机验证厂商相册行为和触屏体验；浏览器设计草案不作为已完成实机截图验收的证明。

## 设计与数据

`DESIGN.md` 保存最初确认的视觉基准；`docs/settings-simplification-v2.md` 覆盖设置页入口。早期交互草案与说明作为历史设计资料保留。实际业务以原生代码和当前发行说明为准。

媒体文件仍保存在系统共享相册；Room 只保存索引、分类决定及操作记录。卸载应用会删除本地整理记录；覆盖安装相同签名的更新版保留记录。数据库结构升级必须新增迁移，禁止破坏性清库。

首版以完整媒体访问为最佳工作模式；部分访问仅整理已授权范围。同名冲突不覆盖已有文件，跨卷移动暂不支持。
