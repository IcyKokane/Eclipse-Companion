# V0.2 change log

- Added Mysterious and Adventurous personality presets (eight total).
- Extended trait sliders with Curiosity and Initiative (seven total).
- Added character interests, conversation speech style (five choices), relationship pace (three choices).
- Added deterministic offline daily starters, including adult-mode-only romantic starter.
- Added Graphic Novel and Monochrome image transformations to the reference-image studio. Non-generative.
- Added Android FLAG_SECURE screen-capture protection.
- Capped persisted private chat state to newest 1,000 records, 3,000 characters per record.
- Upgraded encrypted JSON payload schema to 2, with backward load compatibility for schema 1.
- Pinned llama.cpp v0.6.0 by immutable SHA rather than fetching floating master.
- Native errors now use explicit error prefix and are not stored as normal assistant messages.
- Added JUnit test adapter and Gradle test execution in Android CI; 46 host-verified logic assertions.
- Static audit expanded to 24 checks; added bounded message-store and privacy checks.
- Debug Android versionCode=2, versionName=0.2.0.

**Not claimed:** APK build, installation, device chat, real photo-to-image generation.
