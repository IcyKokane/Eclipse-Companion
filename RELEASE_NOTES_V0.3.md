# Eclipse Companion V0.3 — chat interruption and GGUF import hardening

Release date: 2026-10-25. **Source milestone only, NOT an installable APK.**

## Changes from V0.2

1. A visible Stop current response action requests native cancellation without holding the native inference mutex. Cancellation is cooperative at prompt/decode boundaries. The owner can retry an unanswered last user message without duplicating it.
2. Conversation revision tracking drops late in-flight responses after the chat history was deleted. Failed user-message saves roll back the in-memory append rather than pretending the message is persisted.
3. llama.cpp token count, not character estimates, drives context trimming. Old completed user+assistant turns are removed before a native inference request; system instructions and the newest user turn are kept. A hard error remains if those alone exceed the model context.
4. Model replacement now stages a validated GGUF version 2 or 3 stream, flushes it, and only then swaps the file. If copying fails, the old model is intact. On crash during file promotion, on-launch recovery restores the old model if the new target was not installed. A progress indicator is shown about every 64 MiB.
5. JNI generation sampler now accepts generated tokens; extra guards cover invalid input boundaries and failed sampler allocation.
6. Static source guard checks expanded; 27 new pure JVM tests verify header checks, bounded copies, failures, replacement, interrupted promotion, and recovery.
7. Application identity remains `dev.privatecompanion.eclipse`; versionCode 3 / versionName 0.3.0. State schema and Keystore alias remain unchanged.

## Verified here

- `bash scripts/test-core.sh`: 46 companion logic and 27 GGUF import tests passing.
- `python3 scripts/audit-source.py`: 37 source/privacy assertions passing.
- Shell script syntax and release ZIP/checksum integrity tested.

## Not verified here

- Android SDK/NDK/Gradle compilation, native linking, APK installation, hardware performance, genuine on-phone inference, or pixel-perfect layout; dependencies are unavailable in this session.
- Portrait generation is still deterministic vector drawing plus local filtering of imported images; not diffusion/inpainting. Voice functionality is not implemented. No GGUF or image model weights are bundled.
- Stop cannot preempt a blocking hardware operation inside llama.cpp; cancellation is between inference calls.

## Privacy

The manifest still lacks `INTERNET` permission; no user account or telemetry. The app keeps an 18+ self-attestation and adult mode opt-in, allows romantic/adult roleplay subject to the local model, and never guarantees explicit model output. Models and images are app-private but not individually encrypted. Chat settings/history are Keystore AES-GCM encrypted.
