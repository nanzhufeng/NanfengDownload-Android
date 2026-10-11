---
name: nanfeng-download-android-doc-sync
description: Synchronize 南枫下载 Android handoff, verification, architecture, and reusable-experience documents from current code, Git, artifacts, and device evidence. Use after a verified increment, checkpoint, release, or when documentation conflicts with the repository.
---

# 南枫下载 Android 文档同步

1. Inventory current Gradle version, database schema, source owner, tests, Git commit and artifact/device evidence before editing documentation. Treat code and verified output as higher authority than old prose.
2. Put dynamic current state in `PROJECT_HANDOFF.md` and a dated `docs/verification/` record. Put stable architecture in `docs/南枫下载 Android 完整开发档案.md`; keep long-term developer constraints in `AGENTS.md`.
3. For every claim, record the evidence type and its boundary. Do not turn a passing test, queued task, APK hash, source URL check or coverage installation into a real device download conclusion.
4. When documents conflict, identify the exact stale value, cite the current code/configuration or verification record, and preserve historical documents unless the task explicitly requests their correction.
5. Do not place secrets, account data, cookies, device identifiers beyond necessary operational evidence, or stale temporary worktree state in permanent documentation.
6. Before checkpoint, inspect the staged file list, whitespace and sensitive literals. Exclude unrelated/untracked local artifacts; do not push or create a Release unless separately authorized.
7. When preserving a frozen Android release, run `tools/verify_frozen_release.py` against its release metadata, exact existing APK and verified production commit. This only proves tracked source/byte/archive identity; reuse or rerun the other evidence layers separately. Never rebuild a frozen artifact merely to update documentation.
