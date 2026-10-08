# 实际验证记录

日期：2026-10-08（Asia/Singapore）。设备：用户配对的 Xiaomi MI 6，Android 14 / API 34，arm64-v8a。构建主机为 macOS，JDK 21，Gradle 9.7.1，Android SDK 37.0 / Build Tools 36.0.0。

## 最终构建结果

| 命令 | 结果 |
|---|---|
| `./gradlew assembleDebug` | 成功，生成并安装 Debug APK |
| `./gradlew testDebugUnitTest` | 10 项通过，0 失败 |
| `./gradlew assembleDebugAndroidTest` | 成功 |
| `./gradlew lintDebug --max-workers=1` | 成功，0 错误，12 项版本更新建议/KTX 风格警告 |
| `./gradlew assembleRelease --max-workers=1` | 成功，R8 和资源缩减通过，生成未签名 Release APK，约 4.5 MiB |
| 小米 6 AndroidJUnitRunner | **13 项通过，0 失败，12.958 秒** |

Debug 版本包含 UI 调试工具和未缩减图标库，体积明显大于正式 Release。Release APK 未配置发布私钥，不能把未签名 APK 当作已发布版本。

设备测试直接通过 ADB 安装两个 APK 并执行：

```sh
adb -s <设备序列号> shell am instrument -w \
  cn.kindyear.kwatermarkcam.test/androidx.test.runner.AndroidJUnitRunner
```

可复用的 Gradle 入口为 `ANDROID_SERIAL=<设备序列号> ./gradlew connectedDebugAndroidTest`；本次设备测试采用上述 Runner 命令，未将未执行的 Gradle connected 命令记为通过。

[原始设备测试结果](verification/device-tests.txt)包含 `OK (13 tests)`；[结果与 APK SHA-256](verification/summary.json)保存最终包指纹。单元测试详细报告位于 `app/build/reports/tests/testDebugUnitTest/`，Lint 报告位于 `app/build/reports/lint-results-debug.html`。

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
| Android 13 / API 33 | 未验证 |
| Android 14 / API 34 | 小米 6 真机验证 |
| Android 15 / API 35 | 未验证 |
| Android 16 / API 36 | 未验证 |
| Android 17 / API 37 | 编译目标；未运行设备验证 |

按照用户要求，已停止并删除本次下载的 Android 17 模拟器、系统镜像及 AVD，没有用模拟器结果替代真机结果。

仍需专门验证：真实 GPS/Geocoder 在不同网络与位置环境下的稳定性；首次/永久拒绝授权；设备仅一个摄像头；真实闪光曝光；多物理镜头；手势拖动与无障碍交互；字体最大缩放及屏幕旋转中的连续操作；真实磁盘满和低内存压力；断网条件下的完整拍摄回归。实现含相应处理，但这些场景不能仅凭当前测试宣称全部通过。
