---
name: nanfeng-download-android-debugging
description: Diagnose 南枫下载 Android link discovery, session, transfer, media validation, MediaStore, history, player, or foldable-state failures. Use when a real download, external platform, task state, output file, or playback path is incorrect or unreliable.
---

# 南枫下载 Android 排错

1. State the failing user path and its evidence level. Separate discovery, task persistence, network transfer, processing, output publication, history and playback; do not diagnose from a final toast alone.
2. Locate the owning boundary before editing: discovery in `domain/discovery`/`probe`, state in `data/repository`, execution in `domain/download`, output in `MediaStoreOutputStore`, presentation in `feature`/`navigation`.
3. For a platform case, capture only minimal public evidence and verify official host, canonical work ID, effective request headers, final redirect URL, content length and media bytes. Never log or commit cookies, tokens or account material.
4. Treat a successful HTTP response, nonempty file, queued task or URL predicate as insufficient. Verify declared quantity, content/container validity, published URI count and readability; use a real user-path sample when the requirement is visual or end-to-end.
5. Reproduce safely with JVM/MockWebServer or an isolated emulator first. Keep failures explicit; do not add a silent fallback that changes source identity, replaces a complete gallery with a first item, or deletes old data.
6. Add a regression at the fixed boundary and report what remains unverified. Use `$nanfeng-download-android-testing` for verification selection and `$nanfeng-download-android-doc-sync` for evidence recording.

Never run `connected*AndroidTest`, mutate an OPPO database, or use uninstall/clear-data to manufacture a repro.
