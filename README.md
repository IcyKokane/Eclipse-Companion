# Eclipse Companion · Android V0.4.1 (startup recovery candidate)

A **private, offline-first Android AI companion for consenting adults 18+**. This is a standalone project, not part of The Fool, Icarus, Vesper, or any other agent. The Android application includes native Kotlin UI and a local C++ llama.cpp bridge, and requests **no INTERNET permission**. The owner imports a compatible GGUF file. No accounts, analytics, or API keys.

**V0.4.1 startup recovery:** The first physical-device recording showed the 18+ screen followed by an unexpected return to the Android launcher. Its exact cause is not established without device crash logs. This patch catches age-confirmation secure-store failures and interface startup exceptions, displays a recoverable error view, and tolerates portrait-rendering failures. The age-confirmation flag now rolls back in memory if encryption/persistence fails. The patch must still pass Android CI and on-device validation before calling the issue resolved.

**V0.4.0 historical release: FIRST CI-BUILT DEBUG APK.** GitHub Actions run [40224719071](https://github.com/IcyKokane/Eclipse-Companion/actions/runs/40224719071) passed Android compilation, JVM tests, native arm64 library and offline-permission checks. Download the `EclipseCompanion-v0.4-debug-apk` artifact from that run or install the independently extracted `Eclipse_Companion_v0.4_Debug.apk` provided with the release. **No GGUF model is bundled; Android-device chat, performance, image import and cancellation are not yet tested.** The debug build targets Android 28+ arm64.

## What's new in V0.4

- **Reply commit gate:** stopping a reply, leaving the app, or deleting a conversation invalidates the generation ticket immediately. A native result already queued on Android's UI thread cannot become a saved message after cancellation. This is a pure Kotlin state gate (`ReplySessionGate`) with 16 JVM assertions; it does not interrupt a blocking native call itself.
- **Foreground-only replies:** when the Activity goes to the background, in-flight generation is cancelled and the user must explicitly retry on return. This is cooperative cancellation between model inference steps, not a guarantee that an in-progress native kernel stops instantaneously.
- **Draft continuity:** the chat composer keeps unsent text when the interface redraws or the Activity is recreated (the draft is saved only in Android's instance state, not encrypted persistent memory). Sending clears the draft only once the user's message has been securely stored.
- **Model operation guard:** simultaneous model import and model load are blocked, preventing overlapping operations or confusing queue buildup. Model import/load state returns to idle whether the action succeeds or fails.
- **Rotation safety:** Activity recreation no longer queues a shared-native-model unload that can race the new Activity's model use.
- **Android CI APK verification gate:** on a successful Actions build, check that the APK includes the arm64 inference library and declares no `INTERNET` permission before it is offered as a downloadable artifact.

## Core features retained from V0.3

- Chat interface with saved recent messages, character portrait, local GGUF inference, and on-device conversation context.
- **V0.3 generation reliability (retained):** cooperative Stop control, automatic retry of the latest unanswered user turn after failure/interruption, and protection against replies resurrecting wiped conversations. Generation remains on-device and runs only while the app is active.
- **V0.3 long-chat handling (retained):** native token-aware prompt trimming keeps system constraints and the newest user turn, dropping old complete user/assistant pairs first. Full conversation history remains encrypted; trimming affects only the inference request.
- **V0.3 transactional GGUF importer (retained):** verifies the 8-byte magic/version header, bounds copy size, shows progress, preserves the prior model if copying fails, and restores the prior model after an interrupted replacement on next app launch. Full GGUF compatibility is checked when the engine loads the model.
- 18+ self-attestation on initial launch; adult conversation switch OFF by default. Adult prompts focus on mutual consent, adult fictional characters and boundaries. Model output depends on the owner's imported GGUF model, not guaranteed by the UI.
- **Eight personality presets:** Affectionate, Playful, Shy, Confident, Romantic, Intellectual, Mysterious, Adventurous.
- **Seven adjustable characteristics:** warmth, playfulness, confidence, romance, humor, curiosity, initiative.
- **Five speaking styles:** Natural, Gentle, Witty, Poetic, Direct; **three relationship paces:** Slow, Balanced, Expressive.
- Companion name, interests/hobbies, owner-approved pinned memory notes, last 12 turns in the current prompt. The newest 1,000 chat messages are persisted to keep the secure store bounded (each at most 3,000 characters on disk).
- Four selectable offline daily-conversation starters, plus a fifth adult-mode-only romantic starter. They pre-fill a message for editing. **They are deterministic text suggestions, not model-generated messages, push notifications or autonomous background chat.**
- Appearance Studio with hair/eye/skin/outfit options and five styles: Illustrated, Soft, Vivid, Graphic Novel, Monochrome.
- Owner-supplied images can become stylized offline portraits using cropping, color filters and, for Graphic Novel, posterization. **This is not generative image-to-image or AI photo reconstruction.** Appearance traits do not transform a supplied photo's anatomy/hair/face; effects have limited control.
- Android Keystore AES-256-GCM storage for profile and history. Private image and model files use app-private platform-protected storage; **the images/models are not additionally encrypted by the Keystore code**.
- FLAG_SECURE hides in-app content from ordinary screenshots and recent-app screen captures. Backups disabled. History/delete-all controls; secure flash erasure is not guaranteed.
- Invalid native inference output is shown as an error instead of being added to a conversation. User can retry, and can cancel a reply between native decoding steps; stopping a long native step may take time. No model processing in a persistent background service.
- llama.cpp **v0.6.0 pinned at `d81235049384534c167caea52b85a694f6103d14`** for reproducible ABI compatibility. CPU-based GGUF inference; Android CPU performance depends on device/model.

## Build and test

Requirements on a development machine or in GitHub Actions: Android SDK 35, Android build-tools 35.0.0, NDK 27.2.12479018, CMake 3.22.1, JDK 17, Gradle 8.11.1. Android Gradle plugin 8.9.2, Kotlin Gradle plugin 2.0.21.

1. Use `bash scripts/setup-llama.sh` on the build machine to download and verify the precise llama.cpp revision. This is **build-time internet use on the developer PC/CI**, not app traffic.
2. Build with `gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug` or Android Studio after creating/importing the project.
3. Result, if the build succeeds: `app/build/outputs/apk/debug/app-debug.apk`. Transfer and install on an Android arm64 phone. Android Studio can provision a Gradle wrapper; this source ZIP does not include one.
4. The public repository `.github/workflows/android-debug.yml` builds and uploads a debug APK on each push. **GitHub Actions run 40224719071 succeeded on October 25, 2026.** Build-time llama.cpp downloads do not grant the installed app internet access.
5. For local host smoke testing when `kotlinc` is installed: `bash scripts/test-core.sh` and `python3 scripts/audit-source.py`.
6. On first launch, confirm 18+, open **Privacy**, import a suitable **GGUF**, tap **Load local model**, and go to **Chat**. The model file can be obtained using a separate browser before offline use; importing a model via the Android system file picker does not require this app to have internet access.

### First language model suggestion

For a basic low-resource test: Apache-2.0 `Qwen/Qwen2.5-1.5B-Instruct-GGUF`, Q4_K_M (~1.1 GB). Source: https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF . This is a **technical compatibility choice**, not an assurance of any adult-conversation behavior. Choose a model appropriate to the phone's RAM, language and desired behavior, and review its license. The app does not download weights.

## Migration from V0.1 / V0.2 source

- Same application ID `dev.privatecompanion.eclipse`; versionCode 4, versionName 0.4.0.
- Existing V0.1/V0.2/V0.3 encrypted state (schemas 1 and 2 inside v1 encryption envelope) continues to load. Saves remain schema 2; no schema migration is required.
- Uninstalling or clearing app data can permanently lose encrypted memories because the Keystore key is scoped to the installation. There is no encrypted export yet. The V0.4 model-import backup is only a crash-recovery mechanism, not an encrypted chat backup.
- No source code or model from V0.1 is required at runtime after upgrading, but always back up important data using a future approved export feature before upgrading a production installation.

## Test status and known limitations

| Verification | Result |
|---|---|
| Pure JVM Kotlin companion logic | PASS: 46 assertions using `kotlinc`/JDK |
| Model-file import/rollback/recovery tests | PASS: 27 assertions using `kotlinc`/JDK |
| Reply-session commit/cancel invariants | PASS: 16 assertions using `kotlinc`/JDK |
| Static source, privacy and build invariants | PASS: 50 assertions |
| Shell script syntax | PASS |
| Android Gradle/Kotlin compilation | **PASS (GitHub Actions run 40224719071)** |
| Native llama.cpp Android link | **PASS: arm64 shared library present in APK** |
| Debug APK packaging | **PASS: 4,992,051-byte APK; installation on a phone NOT TESTED** |
| Physical Android model load / chat / portrait controls | **NOT TESTED** |
| Generative image-to-image portraits | **NOT IMPLEMENTED** |
| Offline voice input/output | **NOT IMPLEMENTED** |
| Automatically extracted episodic memories | **NOT IMPLEMENTED** |

A source-level API comparison with llama.cpp 0.6.0's published C header was performed, but that is **not** a compilation or a runtime test.

## Planned milestones

- **V0.3 (previous release):** improved interrupted-response handling, import robustness, long-chat inference, pinned native API compatibility. Android compile/device work remains unverified.
- **V0.4 (this release):** foreground lifecycle, cancellation, composer state and build verification gates; Android debug APK successfully compiled, on-device validation outstanding.
- **Next validation milestone:** install the debug APK on a real arm64 phone, import a compatible GGUF, assess inference speed, native crashes, storage and customization; then continue offline-generated portraits and voice.
- **V0.5:** prototype true local image-to-image with an appropriate mobile model, model import and thermal/RAM safeguards. It may need more RAM and acceleration than the base chatbot.
- **V0.6:** opt-in offline speech recognition and text-to-speech, app lock, encrypted memory review and export/import.

All app features are intended to be owner-initiated. The project does not auto-contact others, claim to be a human, or pressure users to disengage from real relationships.
