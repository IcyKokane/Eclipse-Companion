# Eclipse Companion · V0.5.1 Phone CPU Performance Controls

**Android alpha development preview; not yet device-benchmarked.** This milestone adds a supported way to compare native CPU inference speed on different phones without introducing internet access or third-party API services.

## V0.5.1 changes

- **CPU performance selection** under **Privacy → Response speed**: **Auto** (recommended), **Efficient** (low heat), **Balanced** (up to 4 workers), and **Performance** (up to 6 workers). The implementation caps requests to the phone's reported logical CPU count and 8 threads. Auto reserves headroom and uses at most 4 threads. More threads may be slower or hotter, so compare results rather than assuming Performance is best.
- Shows the reported logical CPU count, selected thread target, and active loaded-model thread count. **Selection persists in Android Keystore-encrypted settings and is included in portable encrypted backups.**
- **Explicit apply:** Change a mode, then tap **Load local model** again to unload/reload model weights on the worker. The new mode cannot change a running inference. Existing chats, personality and GGUF file are not deleted by this reload.
- **Safer JNI state:** a failed native reload can no longer leave a stale “model ready” state or stale active-thread report.
- Adds six CPU-policy JUnit tests including dual-core and high-core device boundary cases, plus strict source/build guards.
- Builds isolated Android test application **Eclipse CPU Test 0.5.1** (`dev.privatecompanion.eclipse.preview51`) and advances source to `versionCode 15`, `versionName 0.5.1`.
- Owner-controlled signing workflow version is aligned to **v0.5.1-alpha1**. An owner-signed release still cannot be produced until the owner configures the secure PKCS12 signing secrets and pinned certificate. This test APK is not the stable update-channel APK.

## Phone test

1. Launch V0.5.1, then import and load a compatible GGUF model under Privacy.
2. In **Privacy → Response speed**, note the current Auto thread count and leave **Reply length: Quick** selected.
3. Exchange two or three short replies; use **Copy local response performance** for token and timing stats without conversation text.
4. Change CPU mode to Efficient or Balanced. Tap **Load local model** to apply the selected setting, then repeat similar exchanges to compare actual speed, device warmth and battery.
5. Check that a failed model load shows **not loaded** rather than a false-ready indicator. Reopen Privacy and confirm the CPU setting persists after relaunch.
6. Keep older installations with any valued data; V0.5.1 has a separate debug package. V0.4.7+ backups move supported settings and conversations, not model weights or portraits.

**Limitations:** This does not add GPU inference, guarantee a response latency or solve model incompatibility. Device thermal limits, model size, prompt length and CPU architecture strongly affect token speed. Owner-signed installation, key setup and physical device tests remain pending.

---

## V0.5.0 Owner-Signed Update Foundation (previous milestone)

**This is still an Android alpha, not a finished production companion.** The ordinary V0.5.0 CI APK is an isolated, automatically debug-signed test build. The owner-signed stable-package APK requires the owner to configure private signing credentials in GitHub Actions; **it has not been issued automatically**.

## V0.5.0 changes

- New **manual owner-signed release workflow** under `.github/workflows/android-owner-release.yml`. It runs only from `main` after a deliberate `SIGN` confirmation. It does not run on PRs, pushes, or forks.
- A stable release identity of `dev.privatecompanion.eclipse` (no debug suffix), `versionCode 15`, `versionName 0.5.1`, and launcher label **Eclipse Companion**. Once this build is owner-signed, **future** builds with the exact same certificate and higher versionCode can update it in place without wiping its Android Keystore data or locally imported GGUF.
- The signing workflow refuses to proceed without four owner-controlled repository secrets and the owner-pinned SHA256 signing certificate fingerprint in a repository variable. It uses `zipalign` and Android `apksigner`, then validates **the final signed APK**, its exact package/version/label, certificate, APK signature v2, required native ARM64 library, and absence of internet/network-state permissions.
- Successful authorized runs upload the validated APK + SHA256SUMS and create a **private GitHub v0.5.1-alpha1 prerelease (current workflow)**. Signing key files/passwords are never intentionally committed or uploaded as artifacts. Local keystore files are blocked in `.gitignore`.
- Added a standalone strict release validator and 15 regression tests for wrong signatures, wrong app identity, tampered version metadata, network permissions and corrupt/missing APK payloads.
- Added **Privacy → App updates & privacy** explaining whether this specific installed APK is a separate debug preview or the stable owner-signed package. Earlier Eclipse features remain, including compact futuristic UI, local streamed chat, Qwen GGUF onboarding, safe prompt caching, and encrypted backup.

## Next-step setup before stable in-place upgrades

See **[Owner signing setup](docs/OWNER_SIGNING.md)** for the detailed, one-time PC/JDK key-generation and GitHub Actions secret/variable configuration procedure. Do **not** send the private keystore or signing passwords in any chat.

**Crucial migration limit:** Previous `.previewXX` installations will **not** magically update to the owner-signed package because Android isolates every package ID. Keep the old install; use V0.4.7+ password-encrypted conversation/settings backup to transfer supported data. Imported GGUF weights and personal images still require separate handling. If you have an old APK with the same base package ID but a different certificate, Android will refuse the upgrade rather than overwrite its data.

## Development APK in this milestone

CI builds `dev.privatecompanion.eclipse.preview50`, labelled **Eclipse Signing Test 0.5.0**, to exercise the existing app, source audits and release policy regression tests **without** requesting your owner signing key. This test APK is still separately installed and cannot itself establish the stable update track.

**Known gaps:** Signed release has not been created without owner keys; in-place updates are not verified on a physical Android device. True model inference speed and prompt-cache compatibility depend on the installed phone/model. A stable signing credential is not a substitute for UI/device validation.

---

## V0.4.9 Incremental Offline Prompt Cache (previous milestone)

**Android development preview.** This release targets the slow replies reported from the owner's phone after V0.4.8 introduced shorter generation modes and streamed previews. No app INTERNET permission, server-side inference or cloud state is introduced.

## V0.4.9 — safe token prefix reuse

- Instead of clearing the entire llama.cpp context on every chat reply, Eclipse now compares the new tokenized prompt with the **previous successfully processed prompt** and retains the longest safe token-identical prefix. Previous generated tokens and any mismatching suffix are removed from the current llama.cpp KV state before evaluating new tokens.
- **Correctness safeguard:** at least the last prompt token is always decoded again to obtain valid fresh logits. Truncation is attempted only if the KV sequence is continuous from position zero; when llama.cpp reports unsupported partial KV removal, the engine clears KV and fully recomputes the prompt. Model unloading/replacement invalidates all cached tokens.
- **Cancellation guard:** failed or canceled replies discard the cache bookkeeping. On the next request, the inference engine falls back to a full prefill rather than trusting partially processed state. No KV data is saved to disk or transferred over a network.
- **Performance diagnostics:** the app shows per-reply token counts and time spent in prefill versus generation under **Privacy → Response speed**. Tap **Copy local response performance** to copy counts/timings for troubleshooting; this explicitly excludes message text and memories.
- Ten standalone C++ prefix-policy regression cases are run by GitHub Actions before the existing JVM/native Android build and offline APK audit. They cover exact repeats, changed prefixes, shortened inputs, empty prompts, and the minimum-last-token rule.
- Preserves the compact 0.4.8 UI, partial-text preview, Quick/Balanced/Detailed output modes, encrypted history/backup functionality and privacy defaults.

## What to test

Installed development name: **Eclipse Cache Test 0.4.9**, Android package `dev.privatecompanion.eclipse.preview49`. It is a **separate** install, not an upgrade to V0.4.8, so don't uninstall earlier apps containing irreplaceable chats, images or models.

1. Import/load a small compatible GGUF model in Privacy; leave Quick reply mode selected.
2. Send two or three short messages in the *same chat* (the first message usually cannot reuse any prompt tokens).
3. After the second and third answers, open Privacy → Response speed. Read the reported `Prefill: X cached, Y computed (Z ms)` and generation tokens/time.
4. Compare first versus follow-up responses on your phone, which is the only reliable way to assess actual speed.
5. Change personality or delete conversation, then send a new message to confirm cache invalidation happens when the input changes.
6. Stop a response midway and Retry; stale partial replies must not be saved.

**Limits:** Cache hits depend on exact tokenizer prefix matching, model architecture and available RAM. This optimization reduces *repeated prefill*, not the CPU cost of generating each new token. There is no GPU acceleration, full context persistence after the app exits, or model benchmark guarantee yet. Android device validation is still required. Consistent APK signing for future in-place upgrades remains a separate milestone.

---

## V0.4.8 Cleaner Chat UI and Faster Local Replies (previous milestone)

**Android development preview.** This release addresses actual owner screenshot feedback from a V0.4.6 phone build: oversized 2×2 navigation buttons, crowded Send/Ideas/Stop controls, an ornate inherited Android font, and a long wait before local model responses became visible.

## V0.4.8 implementation

- **Compact futuristic chat interface:** slim navy header, visible offline indicator, four horizontally aligned navigation buttons with a clear selected state, accessible sans-serif text independent of decorative system fonts, cleaner panel colors, small portrait header, subtle colored borders and cyan/violet accent labels.
- **Message layout:** distinct user-right and companion-left chat bubbles, a gutter around messages, 12dp separation and selectable conversation text. Chat history remains vertically scrollable while the composer stays docked outside it and respects Android keyboard insets.
- **Composer no longer overlaps:** an inset message field and one 50dp-tall button row with a minimum 10dp gap. Send changes to **Stop response** while generating; **Retry reply** replaces Send when there is an unanswered user message and no typed draft, rather than adding an extra full-width action row.
- **Shorter CPU inference:** on-device responses now use **Quick (default) 96 output tokens and 6 recent messages**, **Balanced 160 tokens and 9 messages** or **Detailed 240 tokens and 12 messages**. The preference is stored in the encrypted private store, and future portable backups preserve it. Choose in **Privacy → Response speed**. Quick lowers work; it cannot guarantee any fixed response time or model performance on a specific phone.
- **Streaming preview:** llama.cpp emits the first generated token, then partial text periodically into the active chat bubble. Partial responses are **never saved**; only successful completed replies enter the encrypted conversation history. Stop, backgrounding, erasing chats, and stale-session guards still cancel/discard output. Prefill and CPU latency before first token can remain substantial.
- **Elapsed-time hint:** completed replies display measured local elapsed time to make performance troubleshooting more concrete.

## Important installation and test notes

Android test package `dev.privatecompanion.eclipse.preview48`, launcher label **Eclipse UI Test 0.4.8**. Installs alongside V0.4.1–V0.4.7 without erasing existing installations. Because earlier debug versions each used different package IDs, V0.4.8 **does not automatically inherit** the local model, portrait or messages from V0.4.6. Users can import a GGUF again and in V0.4.7+ use a password-protected backup to transfer chat/settings. Keep old installs holding valued data.

Suggested phone test:
1. Open Eclipse V0.4.8 and verify that the tabs fit in one row, text uses plain sans-serif, message bubbles are separated and Send/Ideas never touch, including with keyboard open.
2. Use Privacy to import and load a small compatible GGUF.
3. In Privacy → Response speed select **Quick**.
4. Send a short message, verify that partial reply text appears before the final reply, observe elapsed-time status, then compare Balanced/Detailed.
5. Tap Stop during generation and verify partial text disappears without being saved; tap Retry and confirm a successful reply is saved.
6. Check adult-mode, appearance, encrypted backups and temporary screenshot consent still work.

**Known limitations:** CPU-only llama.cpp remains the backend, so large models and long prompts may still be slow. Full KV-cache reuse, device-specific multithreading optimization and GPU acceleration are future work. Streaming UI and performance must be verified on physical hardware; source/CI tests alone do not establish real-device performance. Stable release signing is still pending.

---

## V0.4.7 Portable Encrypted Backup Preview (previous milestone)

**V0.4.7 is a development preview.** Device validation, long-term release signing and backup compatibility across future versions remain pending.

## New in V0.4.7 — encrypted chat and character backup

- From **Privacy → Portable encrypted backup**, export your companion personality, settings, memories and last 1,000 chat messages as one password-protected `.eclipsebackup` document using Android's system document picker.
- Import a compatible backup file from your phone's storage. Eclipse decrypts and validates the file **before** showing the profile name/message count and requiring a separate confirmation to replace your existing local settings and chat history.
- Archives use **AES-256-GCM authenticated encryption**, a distinct random 16-byte salt and 12-byte nonce per export, with a key derived by **PBKDF2-HMAC-SHA256 at 250,000 iterations** from a user-chosen password of at least 12 characters. Header, contents and format version are authenticated; wrong passwords or modified bytes fail closed.
- Only encrypted archive bytes are written to shared Android storage. Passwords are not stored. Import is bounded (maximum roughly 16 MiB of decrypted JSON); invalid or oversized data is rejected before the current encrypted app store is modified. If the local Keystore or disk write fails during import, old settings and chats are restored in memory, and AtomicFile maintains the previous committed encrypted state.
- No internet permission, accounts, uploaded data or cloud services. Backup read/write is through the user's Android system file picker.

**Important limitations:** The archive contains **settings and conversation text only**. It does **not** include portrait/reference images, any downloaded GGUF weights, or the Android Keystore key. Existing V0.4.1–V0.4.6 installs cannot export the new format, since those versions do not have this feature; this adds migration capability for V0.4.7 and later builds, not retroactive access to older apps' isolated sandboxes. Keep older installations holding valuable data.

**Password warning:** Losing the backup password makes the document unrecoverable. Choose a strong, unique password and keep it safely. A document provider failure may leave a partial encrypted file in shared storage; delete it and retry if write fails.

## Installation and testing

This debug milestone uses an independent Android package `dev.privatecompanion.eclipse.preview47`, shown as **Eclipse Backup Test 0.4.7**. It will not overwrite or erase earlier Eclipse Companion builds.

1. Confirm 18+ and create a character and a short conversation (a GGUF model is only needed to generate AI replies).
2. In Privacy, choose **Export encrypted chats & settings** and enter the same password twice.
3. Save the `.eclipsebackup` file to a suitable Android document folder.
4. Change the character name or memories, then choose **Import encrypted chats & settings**, select the file and enter the password.
5. Review the confirmation showing the archive's profile name and message count. Choose **Replace** to restore, or **Keep current data** to cancel without changes.
6. Test a wrong password and an interrupted picker; neither should change the current data. Models and portraits must remain untouched during restore.

The next engineering priority is using a *consistently signed* Android APK so future versions can update the same package without needing a fresh install. GitHub Actions debug signing currently varies across build workers. The archive provides a forward migration mechanism, not a substitute for stable release signing.

---

## V0.4.6 Guided Offline Model Setup (previous milestone)

**Development preview: built for Android testing, not yet verified on a physical phone.** No account, app INTERNET permission, online chat endpoint, bundled GGUF weights or automatic background activity.

## V0.4.6 improvements

- **A guided three-step local AI setup in Privacy:** choose an example GGUF file, import a downloaded file from the Android document picker, and explicitly load it for chat. The download choice opens a separate browser after confirmation; Eclipse's own inference remains entirely offline.
- **Verified starter model reference:** the official [Qwen2.5-1.5B-Instruct-GGUF Q4_K_M](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/blob/main/qwen2.5-1.5b-instruct-q4_k_m.gguf) single file (~1.12 GB). The in-app **Open official Qwen GGUF download** button opens its directly downloadable `.gguf` link in an external browser. This is an example, not a guaranteed phone-compatible model. A mobile browser's download manager handles transfer; Eclipse itself never downloads remotely.
- **Installed model diagnostics:** the app displays imported GGUF version (header-only check), model size, loaded/not-loaded state and currently available storage. A valid header alone does not prove architecture/model compatibility; the local engine validates during loading.
- **Safer GGUF copy:** known-size file import requires space for a second staged copy plus 128 MiB headroom. Unknown-size Android document providers no longer require an arbitrary 3 GiB free up front; they can start above a smaller minimum, but the streaming importer checks free space continuously and aborts if the 128 MiB reserve is threatened. Failed imports remove staging and preserve the prior model.
- **Additional tests:** eight JUnit `ModelSetupGuideTest` cases plus four extra `ModelFilesChecks` assertions including simulated low storage and rollback. Prior composer, privacy and Android keyboard inset tests remain.

## Phone test

This debug package appears as **Eclipse Model Test 0.4.6**, Android ID `dev.privatecompanion.eclipse.preview46`. It installs separately from 0.4.1 through 0.4.5 and cannot delete or upgrade their data. Keep old installations holding important conversations.

1. Open Chat and tap **Set up local GGUF model** to go to Privacy.
2. In Privacy choose **Open official Qwen GGUF download**, and accept the browser confirmation. The phone browser should download a file named `qwen2.5-1.5b-instruct-q4_k_m.gguf`; it is about 1.12 GB. Internet access is only needed for the browser download.
3. Return to Eclipse, choose **Import GGUF model from phone**, and select the downloaded file in Android's Files/Downloads picker.
4. Observe copy progress and imported file size, then choose **Load local model**. This may use significant RAM; actual success and speed must be verified on your hardware.
5. Once loaded, return to Chat and send a short test message. Test all screens including keyboard layout and screenshot debugging.

**Limitations:** models larger than the 8 GiB import cap and models with unsupported GGUF versions cannot be imported. Many GGUF architectures may not work with this particular pinned llama.cpp build. There is no encrypted chat export/migration or stable production signing yet. This is still a development preview.

---

## V0.4.5 Android Privacy & Onboarding Preview

**Development preview. Not an on-device-verified stable release.**

V0.4.5 builds on the **already merged V0.4.4 fixed chat composer**: chat history scrolls separately, while Send, Ideas, Stop, and Retry stay in a bottom dock above the keyboard. This update retains the V0.4.4 tested `ComposerPolicy` behavior instead of replacing it with a conflicting implementation.

## Changes

- **Screen capture permission is truly session-only.** The private encrypted store no longer loads or saves `screenshotsAllowed`. Legacy saved copies of that field are ignored. `FLAG_SECURE` remains on by default, and any temporary screenshot permission is revoked as soon as the app backgrounds or is recreated. The Privacy control refreshes when returning.
- **Simple on-device GGUF onboarding.** Chat clearly explains why messages cannot be generated without a loaded compatible GGUF model, and provides a button to open Privacy for import and loading. It does not pretend the model is bundled or loaded automatically.
- **Privacy tests:** the pure `CaptureSessionTest` checks default blocking, temporary enabling, turning it off, background revocation, and fresh-session isolation.
- **No new network capability:** Android manifest still has no INTERNET permission; on-device user data stays in app-private storage.

## Install and device validation

The debug APK has a distinct launcher **Eclipse Privacy Test 0.4.5** and Android package `dev.privatecompanion.eclipse.preview45`. It installs beside prior test builds and the original app without overwriting their local chats, imported GGUF files or settings. It has its own empty encrypted storage, so age confirmation and model import must be performed independently if you choose to test those features.

1. Open the new app and inspect the top header, chat dock, tabs and buttons on Android.
2. Confirm Chat shows **Set up local GGUF model** when no model is loaded; this should open Privacy.
3. In Privacy, allow a screenshot only while you want to document the screen. Background the app and return. The control should read **BLOCKED**.
4. Test large font size, the Android keyboard, and scrolling on all tabs.
5. Use **Copy UI layout diagnostics** on Privacy to share screen dimensions and safe-area padding. The copied text contains no conversation content or model data.

CI compilation and unit tests do **not** replace actual phone verification. Debug APKs use temporary signing keys; production signing and encrypted export/import remain future work. Avoid uninstalling any previous installation holding important data.

---

## V0.4.4 Fixed Composer Test (previous milestone)

**V0.4.4 remains an Android preview milestone, not a production release.** The APK compiles the same offline llama.cpp engine; no internet permission and no GGUF model bundled.

## What changed

- Chat history scrolls independently while a compact message composer stays docked above the Android keyboard. **Send message** and **Ideas** are always near the text field instead of being buried below conversation suggestions.
- A short Ideas chooser inserts an editable conversation starter into the composer without sending it automatically.
- Reply Stop or Retry is displayed in the fixed composer when appropriate; the JVM-tested `ComposerPolicy` ensures conflicting actions are not enabled during generation or model operations.
- The unsent draft is retained across UI refreshes and Activity recreation (as in earlier versions). Chat messages remain in encrypted local storage; the preview does not transfer private data from other installs.
- Existing Android 15/16 safe insets, debug diagnostics and temporary screenshot permission continue from V0.4.3. Screen capture is blocked at startup and after the app backgrounds.

**Safe parallel test:** the debug APK is named **Eclipse UI Test 0.4.4** with distinct Android package ID `dev.privatecompanion.eclipse.preview44`. It can be installed without removing Eclipse Companion or earlier UI previews. Its private storage is independent, and important messages/models should remain in the previously installed app until export and stable signing are available.

### Device verification

1. Launch Eclipse UI Test 0.4.4. Check Chat, Personality, Appearance and Privacy.
2. Open the keyboard in Chat. Send and Ideas should stay visible immediately above it while messages can still scroll.
3. Tap Ideas, select a prompt, and verify the text is **not** sent automatically.
4. Switch tabs and return; verify unsent draft is still present.
5. Use the Privacy screen's copyable UI diagnostics or temporarily enable screenshot capture if controls remain obscured.
6. Model import and on-device inference performance are not verified by the CI build; real hardware testing is still required.

---

## V0.4.3 Mobile Layout Test (previous milestone)

**V0.4.3 is an Android debug preview for testing, not a verified stable Android release.**

The code now preserves the status/cutout/navigation safe areas, responds to Android 15/16 IME (on-screen keyboard) insets, keeps the Send/Stop/Retry controls above prepared conversation ideas, repositions long conversations to the composer, resets settings pages to the top, and highlights the selected navigation tab.

**Screenshot safety:** screen capture still starts blocked. Privacy → Screen capture permits temporary troubleshooting, but the app re-enables its screen-capture block on backgrounding and on relaunch. Privacy → **Copy UI layout diagnostics** puts screen dimensions, Android SDK, font scale and safe-area padding on the clipboard; no conversations, companion memories or GGUF data are included.

**Safe side-by-side installation:** the V0.4.3 Android debug APK is packaged as `dev.privatecompanion.eclipse.preview43`, launcher **Eclipse UI Test 0.4.3**. It does not overwrite the existing V0.4.1 main app or V0.4.2 preview. Its private data is separate; imported models and conversations do not migrate. This avoids requiring the user to uninstall an app because debug signing keys change between CI runs.

**Verification:** Kotlin/JVM tests include `MobileWindowPaddingTest` covering 6 cases: portrait navbar, keyboard opened, keyboard closed, notch with fallback-screen padding, landscape side insets and invalid negative measurements. GitHub Actions compiles the Android APK with arm64 inference library and rejects INTERNET permission. A real-device visual check is still needed to establish whether OEM white rectangles and status bar clipping are fully resolved.

### Android test checklist

1. Install **Eclipse UI Test 0.4.3** without uninstalling previous versions.
2. Verify the four tabs and headers clear the status bar and camera cutout in portrait/landscape.
3. Check text and buttons on each screen; test keyboard open/closed while editing.
4. Confirm Send and Stop/Retry are directly below the chat composer.
5. Go to Privacy; verify it opens at the top, allow a screenshot, capture the issue and return to the app. Protection should be restored after switching away and back.
6. Use **Copy UI layout diagnostics** if any content remains clipped or a white rectangle persists.

---

## V0.4.2 UI preview (previous milestone)

**Android 16 interface correction and private screenshot troubleshooting** (test build; requires device verification).

A private, offline-first Android AI companion for consenting adults 18+. No INTERNET permission, cloud service, accounts or bundled GGUF model.

## V0.4.2 Android layout changes

- Respects Android 15/16 mandatory edge-to-edge status-bar, display-cutout and bottom-navigation insets using Android framework WindowInsets.
- Replaces four narrow top tabs with legible two-column navigation; scales view dimensions with screen density, increases button text and prevents OEM button background tint.
- Replaces potentially unreadable native spinners with dark buttons opening simple choice dialogs; keeps Appearance, Personality, Chat and Privacy accessible.
- Adds Privacy → Screen capture → **Allow** as an explicit, reversible choice. Defaults to blocking screenshots and screen recordings using FLAG_SECURE. Screenshot permission is encrypted with user settings. After recording a UI issue, turn protection back on.
- Includes previous 0.4.1 Android Keystore initialization fix. CI compilation is not the same as a phone validation.

**Important installation safety:** This v0.4.2 *debug preview* uses a different Android package ID `dev.privatecompanion.eclipse.preview` and launcher name **Eclipse Companion Preview**, so it can install beside the existing v0.4.1 **Eclipse Companion** without uninstalling or losing its private data. Their conversations, imported GGUF models and character settings are separate. There is no automatic migration and **do not uninstall the old installation** if it holds any important content. Future debug builds may have different signing keys; this preview should not be used as an archival store for important chats.

### Manual phone verification

1. Install the v0.4.2 UI-preview debug artifact from its successful GitHub Actions run. Open **Eclipse Companion Preview** (not the older icon).
2. Check that the first header is beneath the phone's status bar; ensure the lower controls stay above the navigation bar, with or without the keyboard.
3. Switch between Chat, Personality, Appearance and Privacy. Check readable button labels, choice dialogs and ability to scroll.
4. In Privacy → Screen capture, choose **Allow** and take a screenshot of a non-sensitive screen. Turn screen capture back to **Blocked** when finished.
5. Do not import models or copy private conversations until the user has verified the interface.

---

## V0.4.1 startup recovery (previous milestone)

A **private, offline-first Android AI companion for consenting adults 18+**. This is a standalone project, not part of The Fool, Icarus, Vesper, or any other agent. The Android application includes native Kotlin UI and a local C++ llama.cpp bridge, and requests **no INTERNET permission**. The owner imports a compatible GGUF file. No accounts, analytics, or API keys.

**V0.4.1 startup repair:** The first physical-device recording showed the 18+ screen followed by an unexpected return to the launcher. Source review found a likely cause: Android Keystore rejects caller-provided AES-GCM IVs for encryption when randomized encryption is required, and V0.4.0 supplied one while saving the age-confirmation flag. V0.4.1 lets Android Keystore generate and return the nonce, preserving the same encrypted-store format for decryption. This version also catches startup errors, displays a retry screen without clearing data, rolls back the in-memory age flag if persistence fails, and tolerates portrait-rendering errors. The specific device failure has not been confirmed with logcat; a rebuilt APK and phone re-test are required to verify the repair.

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
