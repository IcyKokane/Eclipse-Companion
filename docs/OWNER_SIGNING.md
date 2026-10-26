# Owner-controlled Android signing for Eclipse Companion

This file documents the V0.5.0 **manual release path**, not an already-signed APK.

## Why this is necessary

Android only permits an in-place upgrade when the installed package ID is unchanged,
the new APK is signed with the same accepted signing certificate, and the new
`versionCode` is greater than the old one. Re-signing with another key does
**not** update the original installation. Eclipse V0.4.x debug previews use
separate `.previewXX` package IDs and transient CI debug keys, so they cannot
be upgraded into one another.

Starting with the **first owner-signed release**, the stable ID will be
`dev.privatecompanion.eclipse`. Later owner-signed releases can upgrade it
if and only if the exact signing key/certificate is retained and the versionCode
increases beyond 14. The first owner-signed release will **not automatically
inherit any existing .previewXX app's data**. Preserve those installs, and use
the V0.4.7+ encrypted export/import (Chats and Settings only) when available.
Older V0.4.0 builds may also use `dev.privatecompanion.eclipse`, but with a
different signature; the owner-signed release cannot replace those directly.
Do not uninstall or clear data in an effort to bypass Android's signature check.

## One-time owner setup (performed outside ChatGPT/Git)

Never share the actual keystore bytes, passwords, base64 value or private key in a
chat message, GitHub issue, PR, source commit or screenshot. Back up the keystore
and passwords separately in secure, owner-controlled locations. Losing the key
prevents normal in-place updates to an app signed with that key.

1. With Java JDK installed on an owner-controlled computer, generate a new,
   dedicated signing key; pick a strong **unique** password. PKCS12 uses the
   same store and key password with commonly available keytool versions:

   ```bash
   keytool -genkeypair -v -storetype PKCS12 -keystore eclipse-owner-release.p12 -alias eclipse-companion -keyalg RSA -keysize 3072 -validity 10000
   ```

2. Obtain the **public** SHA-256 signing certificate fingerprint (this is
   safe to store as a repository variable):

   ```bash
   keytool -list -v -storetype PKCS12 -keystore eclipse-owner-release.p12 -alias eclipse-companion
   ```

   Copy the certificate's `SHA256:` fingerprint, **not** the fingerprint of
   the APK file or the keystore file itself.

3. Convert the binary keystore to base64 locally. On Linux:

   ```bash
   base64 -w0 eclipse-owner-release.p12
   ```

   On Windows PowerShell:

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\\path\\to\\eclipse-owner-release.p12"))
   ```

4. In the private repository **Settings → Secrets and variables → Actions**,
   add these **repository secrets**:

   | Secret name | Value |
   |---|---|
   | `ECLIPSE_KEYSTORE_B64` | One-line base64 of the entire PKCS12 file |
   | `ECLIPSE_STORE_PASSWORD` | Keystore password |
   | `ECLIPSE_KEY_ALIAS` | `eclipse-companion` |
   | `ECLIPSE_KEY_PASSWORD` | Password for that signing key (often equal to store password for PKCS12) |

   Under **Variables**, add the repository variable
   `ECLIPSE_CERT_SHA256` containing the 64-hex-digit certificate fingerprint,
   optionally colon-separated. This is a public pin, **not** a secret.

5. Open **Actions → Eclipse Companion owner-signed Android release →
   Run workflow** on branch `main`. Enter `SIGN` exactly in the confirmation
   input. The workflow **fails closed** if any secret/pin is missing, then
   assembles an unsigned release, signs with the protected key, verifies the
   final package name/version/label, offline permissions, native ARM64 library,
   APK signature v2 and the owner-pinned SHA-256 signing certificate, and only
   then uploads and publishes the APK as a private GitHub prerelease
   `v0.5.0-alpha1`.

6. Back up your keystore **before** installing this as the first stable
   release. Store the certificate fingerprint, passphrase and backup location
   in your own secure records. Confirm the release's SHA256SUMS file before
   transferring the APK.

## Security boundaries and release status

- The owner signing workflow runs **only by manual dispatch from main** and
  requires an explicit `SIGN` input. It never runs with secrets from a PR.
- Only the **final signed APK** and its ordinary SHA-256 checksum are uploaded;
  the key, the encoded keystore and the passwords remain GitHub secrets.
- Missing signing configuration means **no stable APK can be published**. The
  normal CI produces an independently installable debug-test APK under
  `dev.privatecompanion.eclipse.preview50` with a distinct label.
- The signing certificate's fingerprint is pinned to catch an accidental key
  change. The certificate alone does not protect against a compromised owner
  account, compromised GitHub runner, or malicious source changes.
- You must retain the same owner key for every later release, update the
  source `versionCode`, and review and repeat the manual signing workflow.
- **V0.5.0 is an alpha**. Passing CI is not a substitute for Android device
  validation, especially for local GGUF inference, backup restoration and
  app lifecycle behavior.
