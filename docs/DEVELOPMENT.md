# 开发指南

面向开发者的环境、构建、架构、数据存储与发布说明。使用和安装介绍见 [README](../README.md)。

## 环境与技术栈

- 最低 Android 10 / API 29；Compile/Target API 37（Android 17）。
- 单个 app module，Kotlin、Compose、Material 3、MVVM/Repository/单向数据流。
- AGP 9.4.1 + Gradle 9.7.1；AGP 内置 Kotlin 2.2.10，Compose Compiler 同版本。
- Compose BOM 2026.09.00、CameraX 1.6.2、Room 2.8.5、Hilt 2.60.1、KSP 2.3.12、DataStore 1.2.1。
- JDK 17 或 21；建议使用支持 AGP 9.4 的新版 Android Studio。
- 依赖使用 `gradle/libs.versions.toml` 固定管理；无动态版本。首次构建需要下载依赖，安装后的拍照不需要网络。

版本核对来源：[AGP 兼容性](https://developer.android.com/build/releases/agp-9-4-0-release-notes)、[Android 17 SDK](https://developer.android.com/about/versions/17/setup-sdk)、[Compose BOM](https://developer.android.com/develop/ui/compose/bom)、[CameraX](https://developer.android.com/jetpack/androidx/releases/camera)、[Room](https://developer.android.com/jetpack/androidx/releases/room)。

## 编译和运行

1. 用 Android Studio 打开项目根目录，选择 JDK 17/21 并同步 Gradle。
2. SDK Manager 安装 Android SDK Platform 37.0、Build Tools 36.0.0 和 Platform Tools。
3. 在本机 `local.properties` 配置 `sdk.dir=/你的/Android/sdk`，该文件不提交。
4. 执行：

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。连接 Android 10+ 手机，使用 Android Studio Run 或：

```sh
adb -s <设备序列号> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <设备序列号> shell am start -n cn.kindyear.kwatermarkcam/.MainActivity
```

打开相机页按需授予相机权限。定位默认关闭，点击位置行开启并授权。地点字段留空时采用自动定位，填写内容时覆盖自动地点。首页“水印模板”行可切换模板；“内容预设”区左侧显示名称，右侧铅笔图标编辑当前、文件夹图标切换预设。预设选择与管理共用文件浏览器：通过文件夹和面包屑浏览所有模板的预设，每个条目标注所属模板；选中预设会同步切换模板。管理页支持新建多级文件夹、重命名文件夹、移动预设、新建/编辑/重命名/复制/置顶/删除预设，以及当前文件夹内长按排序。删除文件夹会把内容移到上一级，不会删除预设。

Release 使用 R8 和资源缩减。发布签名由环境变量注入，私钥不包含在仓库中。没有签名变量时本地 `assembleRelease` 只产生未签名 APK；发行必须配置完整签名，详见发布指南。应用商店上架前还需准备隐私政策并完成兼容性矩阵。

## 权限与隐私

- `CAMERA`：进入拍摄页面申请；拒绝时显示重新授权及系统设置入口。
- `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION`：仅启用定位后按需申请；允许粗略位置，无后台定位权限。
- 定位依赖包含普通权限 `ACCESS_NETWORK_STATE`；应用未声明 `INTERNET`。
- 不申请存储/全相册读取权限；通过 MediaStore 管理本应用拥有的 URI。
- 不写入 GPS EXIF、设备序列号或 MakerNote；保留方向规范化后的尺寸、拍摄时间及常用曝光参数。**可见水印中的地点仍会出现在照片上**，可手动编辑、隐藏地点或关闭水印。
- 预设、设置、照片记录和失败拍摄结果留在本机，应用备份关闭。卸载应用会移除私有数据，已发布的相册照片由系统管理。
- Fused Location Provider 优先使用；没有 Google Play services 时使用系统 GPS/网络定位。系统 Geocoder 可能依赖网络，离线时可使用坐标或手填地点，定位失败不影响拍照。

预设编辑页按预览、预设信息、水印内容、自动信息分区；显示字段在独立面板调整，恢复默认需确认，未保存内容仍有退出提示。设置中的应用名称进入独立关于页，包含本地存储说明、项目链接、版本与第三方许可证。

## 数据存储

Room 数据库 `kwatermarkcam.db`：`preset_folders` 保存文件夹 ID、名称、父目录和创建时间，跨模板组织预设；`presets` 保存文件夹 ID、模板 ID、名称、版本化 JSON 字段/隐藏状态、置顶、当前文件夹内分组排序及时间；`photos` 保存媒体 URI、尺寸、模板和预设 ID。模板定义是 Kotlin 配置，不占用数据库表。

DataStore：主题、动态颜色、相机默认配置、网格线、水印/定位开关及最后选择；独立 `location_cache` 保存位置测量时间、精度与地址，最多沿用 24 小时；界面与自动地点水印均明确标记缓存及测量时间。

成功照片：`Pictures/WatermarkCamera/WM_yyyyMMdd_HHmmss_SSS_<唯一标识>.jpg`。原始拍摄结果和冻结水印暂存在 `files/pending_photos/`；只有成功发布并写入记录后才清除。保存失败时可在相机页重试或确认删除，进程重启后仍可恢复。MediaStore 使用 `IS_PENDING`，写入完成后公开；重试沿用已创建的 URI。

数据库版本为 2，v1/v2 导出的 schema 在 `app/schemas/`。`MIGRATION_1_2` 将旧预设保留在根目录，ID、内容、置顶、排序及照片记录均保留；升级不要求清除数据。未来升级必须增加显式 `Migration` 并使用旧 schema 测试，不允许破坏性迁移。字段 JSON 的 `schemaVersion` 独立于数据库版本；未知未来版本会报错并保留原数据。

## 项目结构

```text
app/src/main/java/cn/kindyear/kwatermarkcam/
├── MainActivity.kt / WatermarkApplication.kt
├── core/
│   ├── camera/          CameraX 生命周期和控制
│   ├── location/        前台定位、缓存、地理编码接口
│   ├── watermark/       模板注册表和独立 Canvas 布局引擎
│   ├── storage/         持久化拍摄日志、高清合成、MediaStore
│   ├── database/        Room 和 JSON 编解码
│   ├── datastore/       设置持久化
│   └── designsystem/    Material 3 Theme 和页面骨架
├── data/repository/     预设事务和持久化规则
├── domain/              不可变业务模型和 Repository 接口
├── feature/             camera / preset / watermark / gallery / settings
├── navigation/          Navigation Compose
└── di/                  Hilt 配置
```

详细设计见 [架构说明](ARCHITECTURE.md)，功能与边界见 [交付清单](FEATURES.md)，实际验证证据见 [测试记录](TESTING.md)。

## 增加模板与字段

在 `TemplateRegistry.templates` 注册新的 `WatermarkTemplate`：使用永久稳定的模板 ID 和字段 ID，标题/标签放入 `strings.xml`；字段指定类型、默认值、可编辑/隐藏状态和排序。每个模板可定义 `WatermarkLayoutSpec` 的相对宽度、字体、边距、行数等布局参数。现有表单自动根据字段定义生成，预设仍只保存字段值，不保存相机对象或 Compose 结构。

`FieldFormatter` 支持文本、数字、日期、时间、日期时间、地址与坐标。新增字段类型时扩展枚举及格式化分支，并为需要的新编辑方式添加表单控件；图片、Logo、二维码等需要新的渲染块类型和 JSON schema 迁移。新增布局样式时扩展 `WatermarkStyle` 和独立 renderer 的测量/绘制策略，注册模板时提供样式、说明资源与字段；无需修改 CameraX 或预设表。拍摄日志保存布局参数，升级后重试照片仍使用快门时的布局。

新字段读取不到值时使用模板默认值；不要重用/更名已有字段 ID。未知旧字段通过 Map 保留，内容编辑不会主动丢弃，避免模板升级导致数据损失。

## 测试

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebugAndroidTest
# 连接真实设备后（此命令会运行实际相机/相册测试）
ANDROID_SERIAL=<设备序列号> ./gradlew connectedDebugAndroidTest
```

测试包括格式化、相机能力过滤、模板默认值、预设 CRUD/排序/置顶/数据库重开、JSON 版本保护、长文本布局、全部八种 EXIF 方向、GPS 排除、MediaStore 发布与失败结果恢复；另有相机初始化、拍照、镜头切换和后台/前台恢复的真机测试。测试照片会在测试结束清理。不要把编译成功视作所有 Android 版本或所有厂商相机均已验证。

构建排查：Debug Lint 与 Release 代码生成请分开运行；若工具出现读取 Hilt 临时生成源的内部错误，用 `--max-workers=1` 重试，详见测试记录。

## CI / 自动发布

GitHub Actions 使用固定提交 SHA 的 Actions、JDK 21 和已校验的 Gradle Wrapper。不创建或下载模拟器。

| 触发条件 | 执行内容 |
| --- | --- |
| 推送 `main`、对 `main` 提交 PR、手动运行 CI | 版本校验、发布脚本测试、Debug APK、单元测试、Android 测试 APK 编译、Lint、独立 Release APK/AAB 构建、Room schema 检查；上传 Debug APK 与报告 |
| 推送 `v*` 标签 | 校验标签与应用版本、执行测试和 Lint；从 Secrets 恢复发布密钥，构建并验签 APK/AAB，生成 SHA-256 和证书信息，创建 GitHub Release |
| 在版本标签上手动运行 Release | 重试尚未成功发布的标签；已有 Release 不覆盖 |

版本的唯一来源是 `gradle.properties` 中的 `app.versionName` 和 `app.versionCode`。修改版本、等待 CI 成功，再推送完全一致的 `v<版本>` 标签。PR 工作流只有读取权限，不访问发布 Secrets；只有发布任务具备创建 Release 的权限。GitHub Actions 无法代替真机相机和定位测试。

签名设置、首发和后续升级流程见 [发布指南](RELEASING.md)。Actions 依赖由 Dependabot 每月检查更新。
