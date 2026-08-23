---
name: nanfeng-download-android-testing
description: Select, run, and report safe evidence for 南枫下载 Android changes. Use for unit tests, lint, release builds, isolated emulator acceptance, OPPO formal overwrite checks, or claims about real media downloads.
---

# 南枫下载 Android 验证

1. Start with the requested claim and choose only the layers that prove it: unit/Python contracts, lint/build, isolated emulator normal UI path, formal package overwrite, OPPO user path, and external service behavior are distinct.
2. Run targeted JVM/Python tests for the changed boundary first. For broader code changes, run Debug/Release JVM tests and lint from `android/`; record exact commands and results. A build or APK proves packaging, not a real download.
3. Permanently forbid every `connected*AndroidTest`. Do not deploy Debug or instrumentation packages to OPPO and do not use deep links, database injection, Activity extras or manual file insertion as a substitute for normal UI acceptance.
4. For media claims, inspect final MediaStore/SAF outputs: count, type, dimensions or decodeability as appropriate. Source URL validation is not pixel-level watermark proof.
5. Before an OPPO formal overwrite, read installed version, debuggable state, certificate and stable data fingerprints. Continue only with an authorized, incremented, same-signature formal APK; after install read back package metadata and compare pulled `base.apk` hash. Do not launch or operate user tasks unless explicitly in scope.
6. Report passed, not-run and remaining-risk layers separately. Give the final evidence to `$nanfeng-download-android-doc-sync`.
