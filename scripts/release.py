"""Validate Android release versions and package verified, signed artifacts."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'cn.kindyear.kwatermarkcam'
SEMVER = re.compile(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?')


def validate_version(properties, tag=None):
    version = properties.get('app.versionName', '')
    match = SEMVER.fullmatch(version)
    if not match or (match[4] and any(part.isdigit() and len(part) > 1 and part[0] == '0' for part in match[4].split('.'))):
        raise ValueError('app.versionName must be a semantic version without build metadata')
    raw_code = properties.get('app.versionCode', '')
    if not re.fullmatch(r'[1-9]\d*', raw_code) or not 1 <= int(raw_code) <= 2100000000:
        raise ValueError('app.versionCode must be an integer between 1 and 2100000000')
    if tag is not None and tag != f'v{version}':
        raise ValueError(f'Tag must match app.versionName: expected v{version}')
    return version, int(raw_code)


def read_properties(root=ROOT):
    return dict(line.strip().split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines()
                if '=' in line and not line.lstrip().startswith('#'))


def sha256(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def sdk_path():
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk and (ROOT / 'local.properties').exists():
        sdk = next((line.split('=', 1)[1].replace('\\:', ':').replace('\\ ', ' ')
                    for line in (ROOT / 'local.properties').read_text().splitlines() if line.startswith('sdk.dir=')), None)
    if not sdk:
        raise ValueError('Android SDK path is missing')
    return Path(sdk)


def run_checked(command):
    result = subprocess.run(command, check=True, capture_output=True, text=True)
    return result.stdout


def package_release(tag):
    version, code = validate_version(read_properties(), tag)
    apk = ROOT / 'app/build/outputs/apk/release/app-release.apk'
    bundle = ROOT / 'app/build/outputs/bundle/release/app-release.aab'
    for source in (apk, bundle):
        if not source.is_file():
            raise ValueError(f'Missing signed artifact: {source.relative_to(ROOT)}')
    tools = sdk_path() / 'build-tools/36.0.0'
    certificate = run_checked([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)])
    bundle_result = run_checked(['jarsigner', '-J-Duser.language=en', '-verify', str(bundle)])
    if 'jar verified.' not in bundle_result:
        raise ValueError('Android App Bundle is not signed')
    badging = run_checked([str(tools / 'aapt'), 'dump', 'badging', str(apk)])
    expected = f"package: name='{PACKAGE}' versionCode='{code}' versionName='{version}'"
    if not badging.startswith(expected):
        raise ValueError('Built APK package/version does not match the release tag')
    destination = ROOT / 'release-assets'
    destination.mkdir(exist_ok=True)
    if any(destination.iterdir()):
        raise ValueError('release-assets must be empty to prevent publishing stale files')
    for source, extension in ((apk, 'apk'), (bundle, 'aab')):
        shutil.copyfile(source, destination / f'KWatermarkCam-{tag}.{extension}')
    (destination / 'signing-certificate.txt').write_text(certificate)
    assets = sorted(destination.iterdir())
    (destination / 'SHA256SUMS').write_text(''.join(f'{sha256(path)}  {path.name}\n' for path in assets))
    maturity = '此版本为预发布。' if version.startswith('0.') or '-' in version else '此版本为正式发行。'
    notes_path = ROOT / 'docs/releases' / f'{version}.md'
    changes = notes_path.read_text() if notes_path.is_file() else '功能包括 CameraX 拍摄、水印实时预览与高清合成、多预设管理、时间与定位、系统相册保存、Material 3 主题。业务数据保存在本机。'
    (ROOT / 'release-assets-notes.md').write_text(f'''Android 原生工程水印相机 **{version}**（versionCode {code}），最低支持 Android 10。

下载 `.apk` 安装；`.aab` 用于应用商店分发，不能直接安装。签名证书信息和 SHA-256 校验文件随附件提供。后续正式发行使用相同发布密钥，可保留数据直接升级。

{changes}

对应源码与构建说明：[版本源码 ZIP](https://github.com/kindyear/KWatermarkCam/archive/refs/tags/{tag}.zip) · [开发指南](https://github.com/kindyear/KWatermarkCam/blob/{tag}/docs/DEVELOPMENT.md)。许可证及第三方许可说明包含在源码中。

{maturity}具体真机验证范围与未验证场景见 docs/TESTING.md。自动流水线执行单元测试、Android 测试 APK 编译、Lint 和签名验证；不使用模拟器。

首次从开发版 Debug APK 切换到发行 APK 时，Android 会因签名不同拒绝覆盖安装。请先保存需要的预设信息，再自行卸载开发版；卸载会清除应用私有数据。相册照片由系统管理。

更多使用方法与已知限制见仓库 README 和 docs/FEATURES.md。
''')
    print(f'Packaged verified signed release {tag} ({code})')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('validate', 'package'))
    parser.add_argument('--tag')
    args = parser.parse_args()
    try:
        if args.command == 'package':
            if not args.tag:
                parser.error('package requires --tag')
            package_release(args.tag)
        else:
            version, code = validate_version(read_properties(), args.tag)
            print(f'Validated version {version} ({code})')
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error)) from error


if __name__ == '__main__':
    main()
