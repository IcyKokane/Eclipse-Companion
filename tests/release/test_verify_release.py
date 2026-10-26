import importlib.util
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

CODE = Path(__file__).resolve().parents[2] / "scripts" / "verify-release.py"
spec = importlib.util.spec_from_file_location("eclipse_verify_release", CODE)
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)

PIN = "a1" * 32
BADGING = (
    "package: name='dev.privatecompanion.eclipse' versionCode='14' "
    "versionName='0.5.0' platformBuildVersionName='15'\n"
    "application-label:'Eclipse Companion'\n"
)
SIGNATURES = (
    "Verifies\n"
    "Verified using v1 scheme (JAR signing): false\n"
    "Verified using v2 scheme (APK Signature Scheme v2): true\n"
    "Verified using v3 scheme (APK Signature Scheme v3): true\n"
    f"Signer #1 certificate SHA-256 digest: {PIN}\n"
)


class ReleasePolicyTests(unittest.TestCase):
    def verify(self, badging=BADGING, permissions="", signatures=SIGNATURES, pin=PIN):
        validator.verify_metadata(badging, permissions, signatures, 14, "0.5.0", pin)

    def test_valid_release(self):
        self.verify()

    def test_expected_sha256_accepts_colons(self):
        self.verify(pin=":".join(PIN[i:i + 2] for i in range(0, len(PIN), 2)))

    def test_wrong_package(self):
        with self.assertRaisesRegex(ValueError, "Unexpected APK identity"):
            self.verify(badging=BADGING.replace("dev.privatecompanion.eclipse",
                                                "dev.privatecompanion.eclipse.preview50"))

    def test_wrong_version_code(self):
        with self.assertRaisesRegex(ValueError, "Unexpected APK identity"):
            self.verify(badging=BADGING.replace("versionCode='14'", "versionCode='13'"))

    def test_wrong_label(self):
        with self.assertRaisesRegex(ValueError, "Unexpected APK identity"):
            self.verify(badging=BADGING.replace("Eclipse Companion", "Different App"))

    def test_unexpected_internet_permission(self):
        with self.assertRaisesRegex(ValueError, "INTERNET"):
            self.verify(permissions="uses-permission: name='android.permission.INTERNET'")

    def test_unexpected_network_state(self):
        with self.assertRaisesRegex(ValueError, "network state"):
            self.verify(permissions="uses-permission: name='android.permission.ACCESS_NETWORK_STATE'")

    def test_untrusted_cert(self):
        with self.assertRaisesRegex(ValueError, "certificate"):
            self.verify(pin="b2" * 32)

    def test_missing_pin(self):
        with self.assertRaisesRegex(ValueError, "exactly 64"):
            self.verify(pin="")

    def test_unsigned(self):
        with self.assertRaisesRegex(ValueError, "v2"):
            self.verify(signatures=SIGNATURES.replace("Scheme v2): true", "Scheme v2): false"))

    def test_extra_signer(self):
        with self.assertRaisesRegex(ValueError, "certificate"):
            self.verify(signatures=SIGNATURES + f"Signer #2 certificate SHA-256 digest: {PIN}\n")

    def test_truncated_badging(self):
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            self.verify(badging="unexpected")

    def test_zip_payload_valid(self):
        with tempfile.TemporaryDirectory() as t:
            apk = Path(t) / "test.apk"
            with ZipFile(apk, "w") as z:
                z.writestr("AndroidManifest.xml", b"manifest")
                z.writestr("lib/arm64-v8a/libeclipse_jni.so", b"native")
            validator.validate_archive(apk)

    def test_zip_payload_missing_jni(self):
        with tempfile.TemporaryDirectory() as t:
            apk = Path(t) / "bad.apk"
            with ZipFile(apk, "w") as z:
                z.writestr("AndroidManifest.xml", b"manifest")
            with self.assertRaisesRegex(ValueError, "inference library"):
                validator.validate_archive(apk)

    def test_empty_apk(self):
        with tempfile.TemporaryDirectory() as t:
            apk = Path(t) / "empty.apk"
            apk.write_bytes(b"")
            with self.assertRaisesRegex(ValueError, "missing or empty"):
                validator.validate_archive(apk)


if __name__ == "__main__":
    unittest.main()
