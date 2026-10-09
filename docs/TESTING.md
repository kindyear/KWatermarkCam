# 实际验证记录

初始验证日期：2026-10-08；文件库改版回归日期：2026-10-09（Asia/Singapore）。设备：用户配对的 Xiaomi MI 6，Android 14 / API 34，arm64-v8a。构建主机为 macOS，JDK 21，Gradle 9.7.1，Android SDK 37.0 / Build Tools 36.0.0。

## 应用开发阶段构建结果

| 命令 | 结果 |
|---|---|
| `./gradlew assembleDebug` | 成功，生成并安装 Debug APK |
| `./gradlew testDebugUnitTest` | 10 项通过，0 失败 |
| `./gradlew assembleDebugAndroidTest` | 成功 |
| `./gradlew lintDebug --max-workers=1` | 成功，0 错误，12 项版本更新建议/KTX 风格警告 |
| `./gradlew assembleRelease --max-workers=1` | 成功，R8 和资源缩减通过，生成未签名 Release APK，约 4.5 MiB |
| 小米 6 AndroidJUnitRunner | **13 项通过，0 失败，12.958 秒** |

Debug 版本包含 UI 调试工具和未缩减图标库，体积明显大于正式 Release。上述开发阶段的 Release APK 未签名；后续发布配置及验证见本文末尾。

设备测试直接通过 ADB 安装两个 APK 并执行：

```sh
adb -s <设备序列号> shell am instrument -w \
  cn.kindyear.kwatermarkcam.test/androidx.test.runner.AndroidJUnitRunner
```

可复用的 Gradle 入口为 `ANDROID_SERIAL=<设备序列号> ./gradlew connectedDebugAndroidTest`；本次设备测试采用上述 Runner 命令，未将未执行的 Gradle connected 命令记为通过。

[原始设备测试结果](verification/device-tests.txt)包含 `OK (13 tests)`；[结果与 APK SHA-256](verification/summary.json)保存开发阶段包指纹。单元测试详细报告位于 `app/build/reports/tests/testDebugUnitTest/`，Lint 报告位于 `app/build/reports/lint-results-debug.html`。

## 单元测试：10 项

- 日期、时间和日期时间在指定时区冻结格式化。
- 手填地址覆盖定位地址；无地址时显示坐标或不可用文案。
- 经纬度输出不随数字区域格式改变。
- 模板缺失字段采用默认值，长文本在布局前不被截断。
- 明确清空的文本不会错误恢复成默认值。
- 后续定位快照不会改变已冻结内容。
- 两项 CameraInfo 能力模型测试：倍率过滤及未开放超广角时不显示 0.5x。
- 两项定位提供商权限策略测试：只有粗略授权时不请求 GPS，精确授权时也只请求已启用的提供商。

## 真机设备测试：13 项

### 相机集成：1 项

在 MainActivity 上实际启动 CameraX，检测有效缩放范围，发送缩放、自动闪光灯及对焦请求，拍照并等待高分辨率水印照片发布。实际生成 **3016 × 4022** 像素 JPEG，方向规范化后比例符合预览框，URI 可读取。随后完成前后摄像头切换、删除临时当前预设后的自动选择恢复、Activity 进入 CREATED 时关闭就绪状态、恢复 RESUMED 后重新打开相机。

此测试真实运行拍摄与文件管线，并清理测试照片；它没有逐个比较三种闪光曝光结果、断言对焦画质，也没有证明不同物理镜头已经切换。

### 模板和预设：7 项

- 内置单模板、六个字段、默认值及动态/隐藏字段。
- 新建、内容编辑、重命名、复制、置顶、取消置顶、分组排序及删除最后预设自动补建。
- 不合法跨分组排序回滚。
- 选择持久化与删除后替换。
- 磁盘数据库关闭再打开仍保留内容与排序。
- JSON 字符/隐藏状态往返，未知 schemaVersion 不被覆盖。
- 缓存地点在照片水印中标记缓存时间；手填地点保持原样。

### 渲染、EXIF 与保存：5 项

- 五组照片尺寸/横竖比例上的中文长文本、行数、文字块和背景边界。
- 所有八种 EXIF Orientation 通过真实 ImageDecoder/MediaStore 管线规范化；检查尺寸、颜色分区对应的镜像/旋转以及 GPS 排除。
- MediaStore 写入后 IS_PENDING=0，JPEG 可以打开，成功后清理拍摄日志。
- 无效原图合成失败保留冻结快照和原始拍摄文件，能够恢复并明确删除。
- 照片已经公开但 Room 记录写入失败时保留日志；恢复后重试相同 URI，写入记录并清理日志，不重复发布。
- 关闭水印不会绘制像素。（与 EXIF/保存断言分布在上述五个测试方法中。）

## 排查并修复的问题

1. 初始环境没有 Android SDK。已补齐平台、构建工具和 ADB；SDK 下载失败经本机代理安装恢复。`local.properties` 为本机配置，不应提交。
2. Compose 预览网格使用了 IntSize 与 Float Canvas 坐标混合，已修正。
3. Lint 检出资源读取不随配置变化和未使用的 BoxWithConstraints，已改为配置感知资源、正确的布局容器；没有创建忽略基线。
4. 早期相机测试分别受到息屏和离开应用影响，记录了失败，没有算作通过。相机页现保持屏幕亮起，测试要求前台稳定运行。
5. 生命周期测试发现 LiveData 在 STOP 后不再通知 CameraState，因此状态中的 ready 未及时清除。控制器现直接处理 ON_STOP/ON_START，并移除旧观察器；修复后相机与生命周期测试通过。
6. 无 Google Play services 的分支按粗略/精确权限筛选定位提供商，注册途中出错或退出时也清理监听。权限策略通过单元测试，未伪称已在无 GMS 手机运行。
7. 编辑保存成功立即更新草稿 ID，Snackbar 显示不会阻塞保存完成后的导航事件；相机集成测试校验保存后的草稿身份和清洁状态。
8. 同一个 Gradle 调用并行执行 Debug 测试 Lint 与 Release Hilt 生成时出现一次工具内部读取临时生成源失败。独立执行 Debug 检查与 Release 构建、限制 worker 后均通过。建议按 README 分开运行命令，不禁用 Lint。

## 未执行的兼容性和场景

| Android 版本 | 实际验证 |
|---|---|
| Android 10 / API 29 | 无对应真机，未验证 |
| Android 11 / API 30 | 未验证 |
| Android 12 / API 31–32 | 未验证 |
| Android 13 / API 33 | Redmi K40 真机验证，见下方场景模板改版记录 |
| Android 14 / API 34 | 小米 6 真机验证 |
| Android 15 / API 35 | 未验证 |
| Android 16 / API 36 | 未验证 |
| Android 17 / API 37 | 编译目标；未运行设备验证 |

按照用户要求，已停止并删除本次下载的 Android 17 模拟器、系统镜像及 AVD，没有用模拟器结果替代真机结果。

仍需专门验证：真实 GPS/Geocoder 在不同网络与位置环境下的稳定性；首次/永久拒绝授权；设备仅一个摄像头；真实闪光曝光；多物理镜头；手势拖动与无障碍交互；字体最大缩放及屏幕旋转中的连续操作；真实磁盘满和低内存压力；断网条件下的完整拍摄回归。实现含相应处理，但这些场景不能仅凭当前测试宣称全部通过。

## CI / 发布配置验证（2026-10-08）

- 本机再次执行 `assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug --no-daemon --max-workers=1`：成功，10 项 Android 单元测试通过。
- 发布脚本的 6 项 Python 单元测试：通过，覆盖标签/版本匹配、非法版本、versionCode 边界与附件校验值。
- actionlint 1.7.12 检查两份工作流：通过。
- 使用仓库外持久化发布密钥执行 `assembleRelease bundleRelease`：成功。
- 发布脚本检查 APK 实际包名和版本、`apksigner verify`、`jarsigner -verify`：通过，生成签名 APK、AAB、证书信息和 SHA256SUMS。
- 本轮没有重跑真机测试，也没有在真机上卸载 Debug 应用或替换成 Release 包。

云端执行结果可查看 [CI 记录](https://github.com/kindyear/KWatermarkCam/actions/workflows/ci.yml) 与 [Release 记录](https://github.com/kindyear/KWatermarkCam/actions/workflows/release.yml)。以具体运行的完成状态为准；云端 CI 不执行真机测试。

## 首页与预设文件库改版（2026-10-09）

- Debug APK、Android 测试 APK、10 项单元测试、Lint：通过，Lint 0 错误。
- 独立 `assembleRelease --max-workers=1`：通过，R8 和资源缩减通过；本轮本地 Release 未注入签名。
- 最终主包和测试包顺序覆盖安装后，在 Android 14 小米 MI 6 执行 Runner：**17 项通过，0 失败，9.68 秒**。
- 新增 4 项文件库设备测试：多级文件夹创建/重命名/移动/复制、删除文件夹保留内容及同名子目录处理；跨模板置顶分组排序与非法跨文件夹排序回滚；目录与归属重启持久化；从真实导出 v1 schema 创建数据库后迁移到 v2，验证名称、字段、隐藏、ID、置顶、排序、时间和照片记录保留。
- 原有 13 项模板、预设、相机生命周期、高清保存和 EXIF 回归均通过；旧排序用例已改为文件夹范围。
- 手动真机界面检查：首页模板入口可见并进入模板页；预设选择显示模板名称；新建文件夹、选择模板在该目录新建预设、面包屑导航、选择后首页同步、移动到根目录均走通。临时预设和文件夹已删除，原有选择已恢复。
- 第一轮设备执行早于主包安装完成，出现新旧 APK 方法签名不匹配；未计为通过。后续一次测试发现用例错误假定根目录始终有预设，修正为显式创建根目录测试数据。最终完整重跑通过。
- 本轮没有安装模拟器，也未改变之前的跨版本、厂商、压力场景未验证状态。

[最终设备原始结果](verification/folder-device-tests.txt)与[构建摘要及 APK SHA-256](verification/folder-summary.json)保留本轮证据。界面截图包含本机工程内容及定位，未提交仓库。

## 拍摄页 Material 3 动态配色修复（2026-10-09）

- 拍摄页固定背景、文字、青色/黄色强调色改为 Material 3 语义颜色；倍率、快门、模板容器、定位与焦点反馈同步主题。系统栏图标随实际背景亮度变化。
- `assembleDebug lintDebug --max-workers=1`：成功，Lint 0 错误；覆盖安装到小米 MI 6，保留应用数据。
- 真机通过设置页面依次切换浅色/深色及动态配色开关，并从实际截图读取拍摄页背景和快门像素，确认四种组合都响应设置。结果见 [配色检查记录](verification/theme-summary.json)。
- 结束时设置为跟随系统、动态配色开启。检查期间手机息屏曾导致 UI 自动化无法定位控件；唤醒并验证目标应用前台后重新完成检查。
- 本轮只验证配色、构建与 Lint，未再次运行上一节的 17 项设备测试；工程水印输出样式和拍照业务逻辑未修改。截图包含本机内容，未提交仓库。

## 内容预设行布局调整（2026-10-09）

- 内容预设改为左侧标签/名称、右侧编辑与切换两个图标按钮，名称最多两行；图标按钮保留 48dp 触控区域及中文无障碍说明。
- `assembleDebug --max-workers=1`：成功，覆盖安装到小米 MI 6。
- 真机 UI 层级确认标签/名称位于左侧、两个操作图标位于右侧，按钮没有可见文字；实际点击编辑和切换分别进入对应页面，再返回拍摄页。
- 本轮仅调整布局，没有重跑数据库或相机业务测试。

## 预设选中标记对齐（2026-10-09）

- 对勾从标题行移到独立右侧区域，图标 24dp、容器 40dp，与整个条目垂直居中并保留右侧内边距；选择页整张卡片响应选择，点击反馈不再局限于文字矩形。
- `assembleDebug --max-workers=1`：成功，已覆盖安装到小米 MI 6。
- 设备自动截图验证时，UI 层级只有 `com.android.systemui`，无法访问应用控件；未将截图或点击验证记为通过。本轮没有重跑业务测试。

## 场景模板、预设编辑与关于页（2026-10-09）

- 按用户要求切换到 Redmi K40（M2012K11AC）调试；ADB 实际系统为 Android 13 / API 33。本轮没有使用小米 6 或安装模拟器。
- `assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug --max-workers=1`：成功，10 项单元测试通过，Lint 0 错误。独立 `assembleRelease --max-workers=1`：成功；本轮本地 Release 未注入签名。
- 顺序覆盖安装主包和对应测试包，保留已有应用数据。K40 上完整 Runner **21 项通过、0 失败、23.614 秒**，包含相机生命周期、高清水印保存/方向、预设、文件夹和新增场景模板测试。
- 新增 4 项设备测试覆盖：5 个模板 ID/字段/样式独立性及水印不打印模板名称；不同横竖比例和 3000×4000 尺寸的中文长文本布局及样式差异；按需建立新模板默认预设、重复调用不重复创建及无效模板拒绝；全部样式的待保存拍摄恢复。
- 手动 K40 界面检查：模板样式预览、编辑页预览与分区、关于页及许可证弹窗、显示字段开关、恢复默认确认/取消、未保存退出提示均走通；编辑交互检查后放弃草稿，没有覆盖原有预设内容。外部 GitHub 链接未点击验证。
- 初次编译因图标 API 名称不适配失败，修正后以上构建成功。部分早期 UI 自动化未找到控件，未计为通过；根据实际前台层级重新定位后完成检查。
- 未扩大其他 Android 版本、字体最大缩放、低内存/磁盘满、真实 GPS 环境及多物理镜头等测试范围。

[设备原始结果](verification/k40-scene-device-tests.txt)与[构建摘要及 APK SHA-256](verification/k40-scene-summary.json)保留本轮证据。真机截图仅保留本机，未提交含定位内容的图片。

## 预设管理全面回归与删除修复（2026-10-09）

### 发现与修复

1. 删除某模板最后一套预设会立即补建同模板默认预设；启动又会补回工程模板预设。K40 的 4 项新增删除测试在修复前 3 项失败，见 [修复前结果](verification/preset-delete-before.txt)。现仅在整个预设库为空时补建一套工程默认，其他模板为空允许保留；主动选择该模板才创建默认。
2. 删除后读取即时 UiState 来判断当前选择存在 Room Flow 回调竞争。现在由数据订阅从提交后的列表选择同模板有效项或其他模板项，同时修复 DataStore 中的模板和预设。
3. 未结束的拖动会使本地排序列表保留已经删除的旧条目，再次操作报“本地数据”错误。文件夹或已保存列表变化时取消旧拖动并刷新完整列表，数据库状态优先于临时排序。

### K40 界面检查

使用 `QA_` 临时目录与预设验证，测试完成后清理这些临时内容，保留原有默认工程。下表为真实界面操作；[完整界面检查结果](verification/preset-management-ui.txt)记录成功流程。

| 功能 | 检查结果 |
|---|---|
| 创建文件夹、子文件夹、指定模板预设 | 通过 |
| 重命名预设、复制预设 | 通过 |
| 编辑草稿并保存 | 通过 |
| 置顶、取消置顶 | 通过 |
| 上移、下移 | 通过 |
| 长按拖动排序，退出目录后排序仍保持 | 通过 |
| 移动到子文件夹，目录显示同步 | 通过 |
| 文件夹重命名、删除时预设上移保留 | 通过 |
| 删除确认取消，原项仍存在 | 通过 |
| 拖动后删除、连续删除预设、删除空文件夹 | 通过 |
| 快速切换同步水印模板 | 通过 |
| 强制结束进程后记住最后选择 | 通过 |
| 删除当前模板唯一预设后跨模板回退 | 通过 |
| 进程重启不补回被删除模板的预设 | 通过 |

整个预设库最后一项删除自动补建、持久化字段/隐藏/置顶/顺序、非法排序回滚与数据库迁移由隔离数据库的设备测试覆盖；未为验证空库而删除用户已有内容。前一轮编辑页的字段显示、恢复默认确认/取消与未保存退出检查见场景模板章节。

早期自动化受密码锁屏影响；已向用户请求解锁。测试期间临时延长屏幕超时，结束后恢复原先 60 秒。自动化中曾把图标说明当文本、尝试移动到同一目录导致断言不成立，这些没有计为成功；修正脚本后完整重跑。完整 Runner 多次卡在相机用例启动阶段（没有初始化日志），前台重试也未完成；设置超时后停止执行，不计为通过。跨模板删除的新增 ActivityScenario 用例未保留，相关行为已通过真实界面及隔离数据库回归验证。

### 最终构建与设备结果

- `assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug --max-workers=1`：成功，10 项单元测试通过，Lint 0 错误。测试包调整后再次 `assembleDebugAndroidTest` 成功。
- 独立 `assembleRelease --max-workers=1`：成功，本地未签名；最终 Debug 覆盖安装在 K40。
- 最终 K40 运行预设删除、预设数据库、文件夹、水印样式与照片管线五组设备测试：**24 项通过，0 失败，3.942 秒**。新增 4 项删除回归全部通过；修复前同组为 3 项失败。
- 本轮相机生命周期自动化启动未完成，不覆盖上一轮 21 项成功记录，也不把本轮相机测试记为通过。原始未完成输出见 [记录](verification/preset-camera-runner-incomplete.txt)。
- [最终设备结果](verification/preset-repository-tests.txt)、[构建与验证摘要](verification/preset-management-summary.json)。

## 0.2.1 图标、README 与许可证（2026-10-09）

- 应用、关于页、README 使用同源矢量品牌图形；增加 API 26 自适应图标与 API 33 单色主题图标，Manifest 设置 icon/roundIcon。Android 10+ 均可使用自适应资源。
- 实际渲染并检查新图标和 README 横幅；本地文档链接检查通过。单色资源已编译，但未声称经过不同桌面主题实测。
- 关于页内置 GPL 第 3 版和有限附加许可，许可证文件后台读取，提供概览/全文切换与版权、无担保、源码说明。
- `assembleDebug testDebugUnitTest lintDebug --max-workers=1` 成功；10 项单元测试通过，Lint 无错误。发布脚本 6 项测试通过。
- 本轮 ADB 设备列表为空，没有连接 K40；未安装模拟器，未重跑真机相机、主题图标或许可证交互测试。0.2.0 的设备结果不能当作本轮新图标验证。
- 开发技术说明从 README 移到 DEVELOPMENT.md，README 以下载、使用、场景、隐私和常见问题为主。
