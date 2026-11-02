# 下一阶段实现交接

先读：`../DESIGN.md` → `design-contract.md` → `TECH_STACK.md` → `design-reference/approved-ui-v1.html` → `design-reference/tokens.json`。

1. 使用 Kotlin / Jetpack Compose 创建 Android 原生应用。首轮目标 API 36、minSdk 30；依赖使用兼容的稳定版并锁入 Version Catalog。
2. 先实现应用内的 DesignTokens / Typography / Shapes、PhotoTile、AlbumDestination、BottomActionBar、归档面板，再实现页面。
3. 颜色、大小、间距、圆角、图片显示方式以冻结源稿和 DESIGN.md 为准。不要启用 dynamic color，不得沿用 Material 默认尺寸覆盖设计。
4. 第一个可验收产物：用固定本地示例图片复刻网格页、单张页和确认面板；参考宽 390、默认字体缩放下截图对比。先对齐视觉再接真实数据，不同时调整设计。
5. 现有 HTML 仅作为可运行交互参考；不能直接包装为 WebView 成品。重用其选择行为，不照搬模拟延时/重置演示状态。
6. 图标采用统一 Lucide 矢量资产，核对许可；示例照片仅用于视觉验证，不作为用户照片，也不硬编码远程图片到生产界面。
7. 网格用稳定媒体 ID、LazyVerticalGrid；原生手势独立实现长按选择/取消、范围回拖、边缘滚动、正常滚动冲突处理和系统触觉反馈。
8. 首版 app 单模块、按功能包拆分；ViewModel 暴露 StateFlow，Repository 访问 Room/MediaStore；首版采用构造器和 AppContainer 手动注入。
9. Room 同事务保存入队与各卷扫描游标；选择目标立即持久化。归档计划、执行结果、原路径、撤销与重试记录不能只存于 ViewModel。
10. 移动与回收站使用系统 API/授权。相册是已有本地目录；自动归档只针对用户指定的目标集合，不能把所有来源目录都视为已分类。
11. 固定顶部状态/底部操作；其他高度差由网格可用区域吸收。处理真实 WindowInsets，不复制参考外壳。系统字体/用户字体缩放遵循 DESIGN.md 的基准与适配要求。
12. 将草案中未完成的错误/权限页面列为扩展，先给出可审阅设计，再实现；不得假称其外观已确认。

已有文件保护规则持续有效：默认新增文件；修改任务开始前已有源码须事先获得用户对具体文件修改的明确授权；保留未提交修改并核查差异。
