#!/usr/bin/env python3
"""Conservative static source assertions; not a substitute for an APK/device test."""
from pathlib import Path
import sys
root = Path(__file__).resolve().parents[1]
read = lambda p: (root / p).read_text()
manifest = read('app/src/main/AndroidManifest.xml')
model = read('app/src/main/java/dev/privatecompanion/eclipse/LocalModel.kt')
secure = read('app/src/main/java/dev/privatecompanion/eclipse/PrivateStore.kt')
activity = read('app/src/main/java/dev/privatecompanion/eclipse/MainActivity.kt')
assert 'android.permission.INTERNET' not in manifest, 'Manifest must not request internet'
assert 'android:allowBackup="false"' in manifest
assert 'android:dataExtractionRules=' in manifest
assert 'AES/GCM/NoPadding' in secure and 'AndroidKeyStore' in secure
assert 'confirmAdult()' in secure and 'showAgeGate()' in activity
assert 'store.profile.adultMode' in activity
assert 'nativeReply' in model and 'System.loadLibrary("eclipse_jni")' in model
assert 'Import GGUF model from phone' in activity
assert 'Import reference image' in activity
assert 'Delete conversation history' in activity
assert 'Erase all Eclipse data' in activity
assert 'android:exported="true"' in manifest
assert 'FLAG_SECURE' in activity
assert 'dailyStarters(' in activity
assert 'conversationStyle' in activity
assert 'relationshipPace' in activity
assert 'Curiosity' in activity and 'Initiative' in activity
assert 'Graphic Novel' in activity and 'Monochrome' in activity
assert 'ECLIPSE_ERROR:' in model
assert '}.onFailure { error ->' in activity
assert 'd81235049384534c167caea52b85a694f6103d14' in read('scripts/setup-llama.sh')
assert 'versionName = "0.4.8"' in read('app/build.gradle.kts')
assert 'android.permission.ACCESS_NETWORK_STATE' not in manifest
assert 'messages.takeLast(1000)' in secure
assert 'nativeCancel' in model and 'nativeResetCancel' in model
assert 'LocalModel.beginRequest()' in activity
assert 'Stop current response' in activity
assert 'Generate/retry reply to last message' in activity
assert 'conversationRevision' in activity
assert 'ModelFiles.importInto' in activity
assert 'model_previous.bak' in read('app/src/main/java/dev/privatecompanion/eclipse/ModelFiles.kt')
assert 'stop_requested.store(true' in read('app/src/main/cpp/bridge.cpp')
assert 'roles.erase(roles.begin() + 1, roles.begin() + 1 + discard)' in read('app/src/main/cpp/bridge.cpp')
assert 'llama_sampler_accept' in read('app/src/main/cpp/bridge.cpp')
assert 'model_previous.bak' in secure
assert 'versionCode = 12' in read('app/build.gradle.kts')
assert 'EclipseCompanion-v0.4.8-ui-performance-apk' in read('.github/workflows/android-debug.yml')
assert 'replyGate.mayCommit(ticket)' in activity
assert 'replyGate.cancel()' in activity
assert 'override fun onStop()' in activity
assert 'override fun onSaveInstanceState(' in activity
assert 'eclipse_draft' in activity
assert 'draftText = s?.toString()' in activity
assert 'modelBusy = true' in activity
assert 'if (sending || modelBusy)' in activity
assert 'if (!isChangingConfigurations)' in activity
assert 'Verify APK payload and offline permissions' in read('.github/workflows/android-debug.yml')
assert 'lib/arm64-v8a/libeclipse_jni.so' in read('.github/workflows/android-debug.yml')
assert 'android.permission.INTERNET' in read('.github/workflows/android-debug.yml')  # deny-list check only
assert 'ReplySessionGateChecks.run()' in read('app/src/test/java/dev/privatecompanion/eclipse/CompanionLogicTest.kt')
assert 'openUiSafely()' in activity and 'showStartupFailure(' in activity
assert 'Retry startup' in activity
assert 'ageConfirmed = false' in secure
assert 'Portrait unavailable; using fallback icon' in activity
assert 'cipher.init(Cipher.ENCRYPT_MODE, key())' in secure
assert 'val nonce = cipher.iv' in secure
assert 'Cipher.ENCRYPT_MODE, key(), GCMParameterSpec' not in secure
assert 'WindowInsets.Type.systemBars()' in activity
assert 'WindowInsets.Type.displayCutout()' in activity
assert 'root.requestApplyInsets()' in activity
assert 'listOf("Chat", "Personality", "Appearance", "Privacy")' in activity
assert 'backgroundTintList = null' in activity
assert 'setSingleChoiceItems(' in activity
assert 'Screen capture: ALLOWED' in activity and 'Screen capture: BLOCKED' in activity
assert '"screenshotsAllowed"' not in secure, 'Screenshot permission must never persist'
assert 'android:name="dev.privatecompanion.eclipse.MainActivity"' in manifest
assert 'applicationIdSuffix = ".preview48"' in read('app/build.gradle.kts')
assert 'Eclipse UI Test 0.4.8' in read('app/build.gradle.kts')
assert 'WindowInsets.Type.ime()' in activity
assert 'MobileWindowPadding.resolve(' in activity
assert 'MobileWindowPaddingTest.kt' in '\n'.join(p.name for p in (root / 'app/src/test/java/dev/privatecompanion/eclipse').glob('*'))
assert 'private fun navigateTo(name: String)' in activity
assert 'chatScroll.scrollTo(0, 0)' in activity
assert 'copyLayoutDiagnostics()' in activity
assert 'captureSession.background()' in activity
assert 'captureSession.choose(allow)' in activity
assert 'store.screenshotsAllowed' not in activity
assert 'class CaptureSession' in read('app/src/main/java/dev/privatecompanion/eclipse/CaptureSession.kt')
assert 'CaptureSessionTest.kt' in '\n'.join(p.name for p in (root / 'app/src/test/java/dev/privatecompanion/eclipse').glob('*'))
assert 'Set up local GGUF model' in activity
assert 'updateCaptureSecurity()' in activity
assert "private fun composerPane()" in activity
assert "private fun showStarterDialog()" in activity
assert 'root.addView(composerDock' in activity
assert 'composerDock.visibility = View.GONE' in activity
assert 'composerDock.addView(edit' in activity
assert 'ComposerPolicy.evaluate(' in activity
assert 'actionState.showRetry' in activity and 'actionState.showStop' in activity
assert 'composerDock.addView(actions, LinearLayout.LayoutParams' in activity
assert 'ComposerPolicyTest.kt' in '\n'.join(p.name for p in (root / 'app/src/test/java/dev/privatecompanion/eclipse').glob('*'))
model_guide = read('app/src/main/java/dev/privatecompanion/eclipse/ModelSetupGuide.kt')
assert 'QWEN_DOWNLOAD_URL' in model_guide
assert 'Qwen2.5-1.5B-Instruct-GGUF/resolve/main/' in model_guide
assert 'qwen2.5-1.5b-instruct-q4_k_m.gguf' in model_guide
assert 'Intent(Intent.ACTION_VIEW, Uri.parse(ModelSetupGuide.QWEN_DOWNLOAD_URL))' in activity
assert 'Open official Qwen GGUF download' in activity
assert 'Step 1 · Get a GGUF model' in activity
assert 'Step 2 · Import from phone storage' in activity
assert 'Step 3 · Load into device memory' in activity
assert 'installedModelDescription()' in activity
assert 'ModelSetupGuide.checkImport(size, filesDir.usableSpace)' in activity
assert 'freeBytes() >= reserveFreeBytes' in read('app/src/main/java/dev/privatecompanion/eclipse/ModelFiles.kt')
assert 'ModelSetupGuideTest.kt' in '\n'.join(p.name for p in (root / 'app/src/test/java/dev/privatecompanion/eclipse').glob('*'))
backup = read('app/src/main/java/dev/privatecompanion/eclipse/PortableBackup.kt')
assert 'PBKDF2WithHmacSHA256' in backup and '250_000' in backup
assert 'AES/GCM/NoPadding' in backup and 'cipher.updateAAD(' in backup
assert 'MAX_BACKUP_BYTES' in backup and 'readBounded(' in backup
assert 'PortableBackupTest.kt' in '\n'.join(p.name for p in (root / 'app/src/test/java/dev/privatecompanion/eclipse').glob('*'))
assert 'exportPlaintextSnapshot()' in secure
assert 'decodePortable(archive: ByteArray, passphrase: CharArray)' in secure
assert 'fun restorePortable(snapshot: PortableSnapshot)' in secure
assert 'oldMessages = messages.toList()' in secure and 'messages.addAll(oldMessages)' in secure
assert 'Export encrypted chats & settings' in activity
assert 'Import encrypted chats & settings' in activity
assert 'requestBackupCreate = 203' in activity and 'requestBackupOpen = 204' in activity
assert 'Intent.ACTION_CREATE_DOCUMENT' in activity and 'Intent.ACTION_OPEN_DOCUMENT' in activity
assert 'Backup unlock/validation failed' in activity
assert 'Replace chats and character settings?' in activity
assert 'pendingEncryptedExport?.fill(0)' in activity
assert 'ModelFiles.importInto' in activity
assert 'fun interface ReplyProgress' in model
assert 'ReplyProgress?' in model
assert 'length.maxTokens' in model
assert 'length.historyTurns' in model
assert 'env->CallVoidMethod(listener, onPartial, partial)' in read('app/src/main/cpp/bridge.cpp')
assert 'setTypeface(null, Typeface.BOLD)' not in activity
assert 'typeface = Typeface.create(' in activity
assert 'ChatLayout.NAV_HEIGHT_DP' in activity
assert 'ChatLayout.COMPOSER_GAP_DP' in activity
assert 'ChatLayout.bubbleWidth(' in activity
assert 'streamingPreview' in activity and 'previewLabel?.text = streamingPreview' in activity
assert 'if (actionState.showStop) "■  Stop response"' in activity
assert 'Generate/retry reply to last message' in activity
assert 'Reply length:' in activity
assert 'ReplyLength.fromStored(' in secure
assert 'replyLength' in secure
assert 'ReplyLength.kt' in '\n'.join(p.name for p in (root / 'app/src/main/java/dev/privatecompanion/eclipse').glob('*'))
assert 'ChatLayoutAndReplyLengthTest.kt' in '\n'.join(p.name for p in (root / 'app/src/test/java/dev/privatecompanion/eclipse').glob('*'))
print('PASS: V0.4.8 compact UI, streamed local replies, offline/privacy/build assertions')
