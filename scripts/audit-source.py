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
assert 'versionName = "0.4.1"' in read('app/build.gradle.kts')
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
assert 'versionCode = 5' in read('app/build.gradle.kts')
assert 'EclipseCompanion-v0.4.1-debug-apk' in read('.github/workflows/android-debug.yml')
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
print('PASS: 57 static source/privacy/build checks (NOT runtime security validation)')
