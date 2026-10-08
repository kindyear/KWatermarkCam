# 构建与发布

## 工作流

- `CI`：main 推送、PR 与手动触发，生成 Debug APK 和检查报告，并检查 Release 可构建。
- `Release`：版本标签推送或在标签上手动触发，先验证再签名、发布。`v0.*` 和含预发布标识的标签创建 Prerelease，其余创建正式 Release。
- 环境为 Ubuntu 24.04 / Temurin JDK 21 / SDK Platform 37.0 / Build Tools 36.0.0。使用 Gradle Wrapper 与缓存，不安装模拟器。
- Debug/Lint 与 Release 构建分开运行，避免 Hilt 临时生成目录的并发读取问题。
- 仓库没有真机 CI。Android instrumentation 测试 APK 在 CI 中仅编译，不执行；实际设备结果见 TESTING.md。

## 发布密钥

发布密钥必须长期保持不变，丢失后无法为已经安装的应用提供相同签名的升级。首次发布配置以下仓库级 GitHub Actions Secrets：

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 发布 keystore 文件的完整 Base64 编码 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 签名别名 |
| `ANDROID_KEY_PASSWORD` | 私钥密码 |

当前仓库的发布密钥在创建仓库时已配置。维护者本机密钥与凭据保存在仓库外的 `~/.config/KWatermarkCam/signing/`，目录权限 0700、文件权限 0600。请另外制作加密离线备份，不要提交或发送密钥与密码。GitHub Secrets 只能更新，无法读取恢复。

Fork 应当生成自己的密钥并配置自己的 Secrets。缺少 Secrets 时发布会明确失败，不会降级为 Debug 签名或发布未签名 APK。发布任务用临时文件恢复 keystore，结束后清除；不上传 keystore 或含私钥的目录。

本地签名构建需要注入全部环境变量：

```text
ANDROID_KEYSTORE_PATH     keystore 的绝对路径
ANDROID_KEYSTORE_PASSWORD keystore 密码
ANDROID_KEY_ALIAS        私钥别名
ANDROID_KEY_PASSWORD     私钥密码
```

使用本机安全凭据管理工具注入上述变量后执行：

```sh
./gradlew assembleRelease bundleRelease --max-workers=1
python3 scripts/release.py package --tag v0.1.0
```

`package` 验证 APK 签名、AAB 签名、APK 实际包名与版本，再生成 `release-assets/`。再次运行前移除上次生成的该目录，避免混入旧包。签名证书指纹是公开信息，可用于核对后续发行身份。

## 发布新版本

1. 修改 `gradle.properties` 中 `app.versionName`（如 `0.1.1`）和递增的 `app.versionCode`（如 `2`）。版本支持 `1.0.0-rc.1` 格式，不支持 build metadata。
2. 更新功能、限制和测试记录，完成必要的真机回归。
3. 提交并推送 main，确认 CI 完成且成功。
4. 创建与版本完全一致的标签并推送：

```sh
git tag -a v0.1.1 -m 'KWatermarkCam 0.1.1'
git push origin v0.1.1
```

5. 查看 Release 工作流。成功后 Release 包含 APK、AAB、`SHA256SUMS` 和 `signing-certificate.txt`。
6. 下载 APK 并检查签名与相册拍摄流程；商店上架需另行处理渠道政策和审核。

若发布在创建 Release 前失败，可修复环境后在同一标签上手动重试。涉及源代码或工作流修复时应提交修复并发布新的版本标签。不要移动已经公开发布的标签、覆盖已经发布的附件或重用 versionCode。

下载校验（在附件目录执行）：

```sh
shasum -a 256 -c SHA256SUMS
```

首次从 Debug 包切换到 Release 签名时 Android 拒绝覆盖安装；自行保存需要的数据后卸载 Debug 包，再安装 Release。后续同签名 Release 可直接升级。发布流水线不会自动卸载真机上的应用。
