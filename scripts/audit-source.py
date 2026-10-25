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
assert 'versionName = "0.4.4"' in read('app/build.gradle.kts')
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
assert 'versionCode = 8' in read('app/build.gradle.kts')
assert 'EclipseCompanion-v0.4.4-sticky-chat-test-apk' in read('.github/workflows/android-debug.yml')
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
assert 'listOf(listOf("Chat", "Personality")' in activity
assert 'backgroundTintList = null' in activity
assert 'setSingleChoiceItems(' in activity
assert 'Screen capture: ALLOWED' in activity and 'Screen capture: BLOCKED' in activity
assert '"screenshotsAllowed"' not in secure, 'Screenshot consent must never be stored'
assert 'android:name="dev.privatecompanion.eclipse.MainActivity"' in manifest
assert 'applicationIdSuffix = ".preview44"' in read('app/build.gradle.kts')
assert 'Eclipse Chat Test 0.4.4' in read('app/build.gradle.kts')
assert 'WindowInsets.Type.ime()' in activity
assert 'MobileWindowPadding.resolve(' in activity
assert 'MobileWindowPaddingTest.kt' in '\n'.join(p.name for p in (root / 'app/src/test/java/dev/privatecompanion/eclipse').glob('*'))
assert 'private fun navigateTo(name: String)' in activity
assert 'chatScroll.scrollTo(0, 0)' in activity
assert 'copyLayoutDiagnostics()' in activity
assert 'captureSession.background()' in activity
assert 'updateCaptureSecurity()' in activity
assert 'private lateinit var composerBar: LinearLayout' in activity
assert 'composerBar.addView(actions)' in activity
assert 'composerBar.visibility = if (pane == "Chat") View.VISIBLE else View.GONE' in activity
assert 'Set up local model in Privacy' in activity
assert 'private fun showConversationIdeas(edit: EditText)' in activity
assert 'setItems(starters.map { it.title }.toTypedArray())' in activity
assert 'CaptureSessionTest.kt' in '\\n'.join(p.name for p in (root / 'app/src/test/java/dev/privatecompanion/eclipse').glob('*'))
assert 'class CaptureSession' in read('app/src/main/java/dev/privatecompanion/eclipse/CaptureSession.kt')
assert 'store.screenshotsAllowed' not in activity
assert activity.index('val send = button(') < activity.index('private fun showConversationIdeas(')
print('PASS: 88 static source/privacy/build checks (NOT runtime security validation)')
