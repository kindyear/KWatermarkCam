# 架构说明

## 单向数据流

Compose 页面发送意图给 `CameraViewModel`。ViewModel 组合 Room 和 DataStore 的 Flow；时钟与定位单独派生水印 Flow，秒级更新只驱动叠加层。它产生明确的 `CameraUiState`、`CaptureState` 和 `EditorUiState`；相机硬件能力由 `CameraController.State` 表达。UI 使用 `collectAsStateWithLifecycle`，成功与错误提示通过 Channel 传递。没有 Activity 引用进入 ViewModel，UI 不访问 DAO。

`PresetRepository` 定义业务接口，`RoomPresetRepository` 对创建、编辑、删除、置顶与排序使用 Room 事务。模板与预设分离：模板定义字段结构和布局参数，预设定义用户内容，`WatermarkContext` 定义按快门时的动态值。选择在 DataStore 中持久化，读到不存在的预设 ID 时选择同模板首个有效预设并修复选择；跨数据库和 DataStore 的进程中断也可恢复。

## 相机与生命周期

相机控制器封装 CameraX 的 Preview、ImageCapture、CameraSelector、CameraInfo 和 CameraControl。UI 的 AndroidView 只提供 PreviewView，控制器根据 LifecycleOwner、预览尺寸、比例和镜头绑定。DisposableEffect 只在这些输入改变时执行，水印秒更新、预设切换、闪光灯更新不会重复绑定。

Preview 和 ImageCapture 放入共享 UseCaseGroup/ViewPort，以预览实际矩形裁剪输出。比例框在竖屏使用 3:4 / 9:16，在横屏使用 4:3 / 16:9。触控对焦使用 PreviewView 的 MeteringPointFactory；缩放倍率过滤当前 min/maxZoomRatio。多物理镜头由 CameraX 标准逻辑相机能力决定，未直接调用厂商私有接口。闪光灯使用 ImageCapture flashMode，不启用 Torch。

前置预览与输出采用镜像一致策略，通过 ImageCapture.Metadata.isReversedHorizontal 记录，随后 ImageDecoder 统一规范化所有 EXIF 方向。设备方向变化更新拍摄旋转，Activity 配置重建后重新绑定正确尺寸。相机后台由 CameraX 生命周期关闭；方向传感器只在 RESUMED 状态开启，定位与秒级时钟暂停，ON_STOP 清除就绪标记，ON_START 重新确认相机状态。解绑时移除 CameraState/ZoomState 观察器和传感器监听。

ImageCapture 的候选尺寸按可用堆的预算限制，优先使用设备支持的最高安全分辨率。不会把输出降为屏幕尺寸，也不会强行显示设备未开放的 0.5 倍或长焦倍率。

## 水印引擎

`TemplateRegistry` 格式化稳定字段 ID，生成不可变 `WatermarkSnapshot`。预览与最终 JPEG 均调用 `WatermarkRenderer`，使用照片短边归一化字体、内边距、背景和左下角坐标。StaticLayout 支持中文测量、换行和行尾省略，最多显示配置的行数，内容过高时缩小字体。输入字段最多 500 字符。

实时预览直接在 Compose Canvas 的 nativeCanvas 上绘制，没有拍摄 UI 截图。JPEG 输出由 ImageDecoder 解码成可变软件 Bitmap，在同一 Bitmap 上直接绘制，不创建另一张全图合成层；合成、JPEG 压缩、EXIF 与媒体写入都在 IO dispatcher。解码前检查像素和剩余堆预算，OOM 转成可恢复状态，保留原图而非静默缩图。

## 保存状态机

```text
就绪 → 同步锁定快门 → 冻结水印 → 写入私有日志 → CameraX 原图
     → 高分辨率合成 → 创建/恢复 IS_PENDING URI → 写入 JPEG
     → IS_PENDING=0 → Room 照片记录 → 清理私有日志 → 就绪

合成/写入失败 → 保留原图及日志 → UI 重试/确认删除
公开照片后记录失败 → 提示系统相册已保存 → 日志保留 → 重试同一 URI 的记录
```

文件命名含毫秒和 UUID 片段。日志写入先 fsync 临时文件再原子重命名，JPEG 合成也使用临时文件。重新启动优先恢复尚未完成的拍摄，存在待保存照片时禁用新增拍摄。没有把临时缓存目录当作唯一照片副本。

## 定位与隐私

位置服务封装独立 Flow，订阅时先检查授权和系统开关，读取有测量时间的缓存、Fused 最近位置，再请求低频更新。小于两分钟且精度不超过 150 米才标记为新位置；其他有效位置标记缓存，最多沿用 24 小时；自动地点水印也注明缓存测量时间，手填地址不追加该标记。地址解析失败保留经纬度，20 秒无新位置有超时状态。离开拍摄页或切后台取消订阅并移除更新。

`GeocodingRepository` 是提供商扩展接口，目前由 SystemGeocodingRepository 实现；Android 13+ 使用异步接口，Android 10–12 的兼容调用在 IO 上执行。无 Google Play services 时使用系统定位提供商：粗略授权仅请求网络定位，精确授权才请求 GPS，注册失败时也取消此前的监听。冻结快门快照使后续异步位置更新无法修改已拍照片。

EXIF 仅复制明确允许的曝光和基本拍摄信息，不写入 GPS；水印显示的地点由用户决定。没有后台定位、全相册读取、网络后台、账号或云服务。

## 演进约束

- 目前单 module 和一个 ViewModel 协调六个紧密相关页面，编辑草稿独立于 Room 实体。规模扩大后可以按页面拆 ViewModel，不需要拆数据库或相机引擎。
- Room schema 1 已导出；任何后续升级需增加显式 Migration、保留历史 schema 并执行迁移测试。
- 预设字段 JSON schemaVersion=1，未来字段或布局策略以稳定 ID 演进；未知格式报错，不能使用 destructive migration。
- 拍摄日志与照片记录保留模板/预设 ID，删除预设不删除已生成照片；历史水印像素已冻结，不依赖预设仍存在。
