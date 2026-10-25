# Eclipse Companion V0.4 — foreground safety and build readiness

Release date: 2026-10-25. **SOURCE RELEASE. NOT A COMPILED OR INSTALLED APK.**

## Changes since V0.3

1. A request-ticket gate (`ReplySessionGate`) makes cancellation decisive for history persistence: even an already-computed reply is discarded if the user pressed Stop, deleted the conversation or left the app before its UI callback commits. This prevents stale late-generation replies from being saved. Native computation still stops cooperatively between decoding steps.
2. App backgrounding calls `LocalModel.cancel()` and invalidates the in-flight reply. The owner can explicitly retry the unanswered last message after returning to the foreground.
3. Text in the chat composer survives redraws, tab switching and Activity reconstruction via saved-instance-state. It is not stored as an unencrypted persistent draft. The draft is cleared only once the user's turn was encrypted and saved.
4. Model importing and loading are mutually guarded in the UI. A failed operation resets the busy flag. Unloading cancels pending generation, and rotation does not racefully unload process-wide weights.
5. CI validates the packaged arm64 native library and the absence of Android INTERNET permission using Android's APK inspection tool, after the Gradle test and assemble tasks.
6. Kotlin JVM tests gain 16 reply-session assertions, and static privacy/build audit expands from 37 to 50 checks. Android app identity and encrypted data schema are unchanged; versionCode 4 / versionName 0.4.0.

## Verified locally

- Pure Kotlin `scripts/test-core.sh`: 46 companion logic, 27 model import, 16 reply-session checks passed.
- `scripts/audit-source.py`: 50 static assertions passed.
- Shell scripts parsed with `bash -n`; ZIP CRC and embedded SHA256SUMS independently verified after packaging.

## Not verified

No Android SDK, NDK, Gradle or network access to dependency servers was available, so Android APK compilation, native llama.cpp compilation, runtime behavior, real phone memory use and appearance controls **were not tested**. The GitHub workflow was not executed. No APK or GGUF model weights are included. Portraits use local deterministic drawings/filters, NOT model-based image-to-image. Offline voice input/output is not implemented.

## Privacy notes

- No app INTERNET permission or background services.
- Saved character profile, preferences, pinned memories and messages are AES-GCM encrypted using Android Keystore.
- Reference and avatar images and imported GGUF weights are app-private files, but do not receive extra Keystore file encryption.
- This release does not offer portable backup/export. Clearing app data or uninstalling can permanently lose messages and memories.
