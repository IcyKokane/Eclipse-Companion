#!/usr/bin/env python3
"""Fail-closed validation for owner-signed Eclipse release artifacts.

This is intentionally separate from the automatically published debug APK.
All checks apply to the *signed* final file, after apksigner has run.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path
from zipfile import ZipFile, BadZipFile


PACKAGE = "dev.privatecompanion.eclipse"
LABEL = "Eclipse Companion"


def read_badging(badging: str) -> tuple[str, int, str, str]:
    package = re.search(r"^package: name='([^']+)' versionCode='(\\d+)' versionName='([^']+)'", badging, re.M)
    label = re.search(r"^application-label:'([^']+)'", badging, re.M)
    if package is None or label is None:
        raise ValueError("Incomplete Android package metadata")
    return package.group(1), int(package.group(2)), package.group(3), label.group(1)


def normalize_digest(text: str) -> str:
    result = re.sub(r"[:\\s]", "", text).lower()
    if not re.fullmatch(r"[0-9a-f]{64}", result):
        raise ValueError("Expected signing certificate SHA-256 must be exactly 64 hex digits")
    return result


def verify_metadata(badging: str, permissions: str, signatures: str,
                    expected_code: int, expected_version: str,
                    expected_cert: str, expected_package: str = PACKAGE,
                    expected_label: str = LABEL) -> None:
    pkg, code, version, label = read_badging(badging)
    if (pkg, code, version, label) != (expected_package, expected_code, expected_version, expected_label):
        raise ValueError(
            f"Unexpected APK identity: {pkg}, {code}, {version}, {label}; "
            f"expected {expected_package}, {expected_code}, {expected_version}, {expected_label}"
        )
    if re.search(r"android\\.permission\\.INTERNET\\b", permissions):
        raise ValueError("Release APK must not request the INTERNET permission")
    if re.search(r"android\\.permission\\.ACCESS_NETWORK_STATE\\b", permissions):
        raise ValueError("Release APK must not request network state")
    if not re.search(r"Verified using v2 scheme.*: true", signatures, flags=re.I):
        raise ValueError("Release must be verified with APK Signature Scheme v2")
    hashes = re.findall(r"Signer #\\d+ certificate SHA-256 digest:\\s*([0-9a-fA-F: ]{64,95})",
                        signatures, flags=re.I)
    if len(hashes) != 1 or normalize_digest(hashes[0]) != normalize_digest(expected_cert):
        raise ValueError("Release certificate does not match the owner-pinned SHA-256 digest")


def validate_archive(apk: Path) -> None:
    if not apk.is_file() or apk.stat().st_size == 0:
        raise ValueError("Signed APK missing or empty")
    try:
        with ZipFile(apk) as archive:
            if archive.testzip() is not None:
                raise ValueError("APK ZIP integrity check failed")
            files = set(archive.namelist())
            if "lib/arm64-v8a/libeclipse_jni.so" not in files:
                raise ValueError("Required offline ARM64 inference library missing")
            if "AndroidManifest.xml" not in files:
                raise ValueError("AndroidManifest.xml missing")
    except BadZipFile as err:
        raise ValueError("Signed APK is not a valid ZIP") from err


def run_cmd(*args: str) -> str:
    result = subprocess.run(args, check=True, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE)
    return result.stdout


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--cert-sha256", required=True)
    args = parser.parse_args()
    try:
        # Validate the pin before running any subprocess.
        normalize_digest(args.cert_sha256)
        validate_archive(args.apk)
        badging = run_cmd(args.aapt, "dump", "badging", str(args.apk))
        permissions = run_cmd(args.aapt, "dump", "permissions", str(args.apk))
        signatures = run_cmd(args.apksigner, "verify", "--verbose", "--print-certs", str(args.apk))
        verify_metadata(badging, permissions, signatures, args.version_code,
                        args.version_name, args.cert_sha256)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print(f"RELEASE VALIDATION FAILED: {error}", file=sys.stderr)
        return 1
    print("PASS: owner-signed release package, version, certificate, signature, JNI, and offline permissions")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
