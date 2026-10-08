"""Tests protect tag/version checks and published checksum generation."""
import hashlib
from pathlib import Path
import tempfile
import unittest
from release import sha256, validate_version


class ReleaseTests(unittest.TestCase):
    def test_matching_release(self):
        self.assertEqual(('0.1.0', 1), validate_version({'app.versionName': '0.1.0', 'app.versionCode': '1'}, 'v0.1.0'))

    def test_tag_mismatch_rejected(self):
        with self.assertRaises(ValueError):
            validate_version({'app.versionName': '1.0.0', 'app.versionCode': '2'}, 'v0.1.0')

    def test_prerelease_validated(self):
        self.assertEqual(('1.2.3-rc.1', 20), validate_version({'app.versionName': '1.2.3-rc.1', 'app.versionCode': '20'}, 'v1.2.3-rc.1'))

    def test_invalid_versions_rejected(self):
        for version in ('01.0.0', '1.0', 'v1.0.0', '1.0.0+build', '1.0.0-01', '1.0.0;echo bad'):
            with self.subTest(version=version), self.assertRaises(ValueError):
                validate_version({'app.versionName': version, 'app.versionCode': '1'})

    def test_invalid_codes_rejected(self):
        for code in ('0', '-1', '01', 'a', '2100000001'):
            with self.subTest(code=code), self.assertRaises(ValueError):
                validate_version({'app.versionName': '1.0.0', 'app.versionCode': code})

    def test_artifact_checksum(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'artifact.apk'
            data = b'APK binary\x00\xff' * 1000
            path.write_bytes(data)
            self.assertEqual(hashlib.sha256(data).hexdigest(), sha256(path))


if __name__ == '__main__':
    unittest.main()
