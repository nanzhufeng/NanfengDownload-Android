---
name: nanfeng-download-android-code-review
description: Review 南枫下载 Android changes for data loss, source trust, task-state, media-output, session, migration, device-safety, and verification regressions. Use before checkpoint, release, or when reviewing a pull request or local diff in this repository.
---

# 南枫下载 Android 代码审查

1. Read the scoped diff, owning models and tests before judging behavior. Do not infer current facts from old README, plans or prior release notes.
2. Check ownership: UI must not bypass the repository; state transitions must remain legal; task work must remain recoverable; source resolution must not replace a task-owned trusted source with a generic result.
3. Check persistence changes as one unit: entity, DAO, database version, migration, schema export and migration regression. Flag any path that can erase tasks, history or user media.
4. Check transfer and output paths for final URL, credential host scoping, cancellation, partial files, real media validation, transactional MediaStore/SAF cleanup and duplicate-output behavior.
5. Check user safety: no credential values in code/docs/tests, no hidden access-control bypass, no automatic OPPO mutation, no `connected*AndroidTest`, and no release-signing fallback that weakens partial-configuration failure.
6. Check test claims against actual evidence. State whether a conclusion is code-only, automated, isolated emulator, formal-install, or real-device user-path evidence.
7. Emit findings with file/line evidence and priority. If there are no findings, state the review scope and the verification gaps that remain.
