---
name: nanfeng-download-android-development
description: Implement or refactor a scoped 南枫下载 Android feature across Compose, Room, discovery, transfer, media processing, or MediaStore. Use when changing user-visible behavior, task lifecycle, platform support, or delivery code in this repository.
---

# 南枫下载 Android 开发

1. Read `AGENTS.md`, current `PROJECT_HANDOFF.md`, Gradle configuration and the owning module before editing. Treat `android/` as the active product; isolate the legacy desktop prototype unless it is explicitly in scope.
2. Translate the request into one user-observable outcome, a single source of truth, failure states and a minimum verification level. Keep UI state out of DAOs; route task/history mutations through `DownloadRepository`.
3. Preserve the pipeline: input → discovery → Room task → Worker → resolver/transfer → validated output → history. Add a dependency or a shortcut only at its owning boundary.
4. For persistence changes, update entity, DAO, Room schema export, explicit migration and migration tests together. Preserve existing task, history and output data.
5. For external sources, constrain platform, official host, target work identity and credential scope before adding fallbacks. Do not make a generic extractor overwrite a task-owned trusted source.
6. For media output, validate real content before publishing; make MediaStore/SAF writes recoverable and never overwrite existing user media.
7. Add the narrowest matching JVM/Python regression, then use `$nanfeng-download-android-testing` for the appropriate build and device evidence. Synchronize facts with `$nanfeng-download-android-doc-sync` only after verification.

Stop and request direction when implementation would require deleting user data, changing a formal signing chain, installing an incompatible package, bypassing access control, or creating a new persistent entry point not already approved.
