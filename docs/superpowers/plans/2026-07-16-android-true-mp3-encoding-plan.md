# Android True MP3 Encoding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make the Android `AUDIO_MP3` workflow produce a standards-compliant MPEG Layer III file, then verify the feature and the wider application on the dedicated emulator and the connected OPPO PKH120 without deleting existing app data.

**Architecture:** Keep task state, history, and MediaStore publication in the existing download pipeline. Add one narrowly owned audio conversion boundary between source download and publication: Android decodes supported compressed audio to PCM16, a separately built LAME shared library encodes PCM16 to MP3, and an Android validator rejects mislabeled or corrupt output before it can be published. A valid source MP3 may bypass conversion, but an M4A/MP4/WebM file must never be renamed to `.mp3`.

**Tech Stack:** Kotlin, Android MediaExtractor/MediaCodec, JNI/C++, LAME 4.0, CMake 3.22.1, Android NDK 26.1.10909125, Gradle 8.7/AGP 8.5, JUnit4, AndroidX instrumentation tests, ADB.

## Global Constraints

- Work on branch `codex/android-notification-history-checkpoint-20260716` and preserve all unrelated user files.
- Use TDD for each behavioral change: write a failing focused test, run it and record the expected failure, implement the minimum production change, then rerun the focused and surrounding suites.
- Keep LAME as a separately built native library and record its exact upstream source URL, version, SHA-256, and license. Do not copy a prebuilt binary from an unknown source.
- Fix the native toolchain to NDK `26.1.10909125`, CMake `3.22.1`, and ABIs `arm64-v8a` plus `x86_64`.
- Preserve the existing repository/Room ownership of download state and history. Native and transcoder layers must never write Room rows or publish MediaStore records.
- Never uninstall the app or clear its data on the OPPO. Use `adb install -r` only after recording the current installed state.
- Run Gradle with `--no-daemon --max-workers=1`, `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`, and `NANZHUFENG_BUILD_PYTHON=/opt/homebrew/bin/python3.13` to avoid the already observed dependency-download instability.
- Completion requires evidence from Mac build, JVM tests, x86_64 emulator tests, arm64 OPPO tests, a controlled real-MP3 artifact, and the app's principal screens/workflows. MP3-only success is not full-app completion.

## Task 1: Pin the Native Toolchain and Vendor Verifiable LAME Source

**Files:**

- Modify: `android/app/build.gradle.kts`
- Create: `android/app/src/main/cpp/CMakeLists.txt`
- Create: `android/app/src/main/cpp/third_party/lame-4.0/` from the official LAME 4.0 source archive
- Create: `android/app/src/main/assets/licenses/lame-4.0/COPYING`
- Create: `android/app/src/main/assets/licenses/lame-4.0/README.md`

**Step 1: Verify the source archive before copying**

Run:

```bash
shasum -a 256 /tmp/lame-4.0.tar.gz
```

Expected: exactly `3df5124d5ad3a98312ffd7ba6a9b36230e4f8a3e66d3ce0f425e336c32d216eb`.

If `/tmp/lame-4.0.tar.gz` is absent, download LAME 4.0 from the upstream SourceForge project page to `/tmp`, then verify the same checksum before extraction. Do not proceed on a checksum mismatch.

**Step 2: Install the fixed Android native packages**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  /Users/nanzhufeng/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager \
  --sdk_root=/Users/nanzhufeng/Library/Android/sdk \
  "ndk;26.1.10909125" "cmake;3.22.1"
```

Expected: both packages finish installing and their directories exist beneath the Android SDK.

**Step 3: Vendor the verified source mechanically**

Extract the archive to a temporary directory, verify the extracted root is `lame-4.0`, and copy that full source tree into `android/app/src/main/cpp/third_party/lame-4.0/`. Preserve upstream license files. Copy `COPYING` into the app asset license directory and create `README.md` containing:

- Project: LAME
- Version: 4.0
- Upstream: `https://sourceforge.net/projects/lame/files/lame/4.0/`
- Archive SHA-256: `3df5124d5ad3a98312ffd7ba6a9b36230e4f8a3e66d3ce0f425e336c32d216eb`
- License: GNU Library General Public License v2 as supplied by upstream
- Note that the app links LAME as the separate `libmp3lame.so` shared library

**Step 4: Add the CMake build**

Define a shared `mp3lame` target from these upstream files:

```text
VbrTag.c bitstream.c encoder.c fft.c gain_analysis.c id3tag.c lame.c
newmdct.c presets.c psymodel.c quantize.c quantize_pvt.c reservoir.c
set_get.c tables.c takehiro.c util.c vbrquantize.c version.c
mpglib_interface.c
```

Include `include/`, `libmp3lame/`, and `mpglib/`. Link the target to Android's math library. Define a separate shared `nanzhufeng_mp3` target for the JNI bridge and link it to `mp3lame`, `log`, and `m`.

**Step 5: Pin Gradle native configuration**

In `android/app/build.gradle.kts` set:

```kotlin
ndkVersion = "26.1.10909125"

externalNativeBuild {
    cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
    }
}
```

Keep the existing `arm64-v8a` and `x86_64` ABI filters.

**Step 6: Prove both native ABIs build**

Run:

```bash
cd android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
NANZHUFENG_BUILD_PYTHON="/opt/homebrew/bin/python3.13" \
./gradlew --no-daemon --max-workers=1 :app:externalNativeBuildDebug :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`; the APK contains `lib/arm64-v8a/libmp3lame.so`, `lib/arm64-v8a/libnanzhufeng_mp3.so`, and matching x86_64 libraries.

**Step 7: Commit**

```bash
git add android/app/build.gradle.kts android/app/src/main/cpp android/app/src/main/assets/licenses/lame-4.0
git commit -m "build(android): add verifiable LAME native toolchain"
```

## Task 2: Make Generic Media Validation Content-Aware

**Files:**

- Modify: `android/app/src/test/java/com/nanzhufeng/videodownloader/probe/MediaFileValidatorTest.kt`
- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/probe/HttpFileDownloader.kt`

**Step 1: Add failing JVM tests**

Add cases proving:

- an ID3-prefixed file slightly larger than 1 KiB is accepted as a plausible MP3;
- a frame-sync-prefixed file slightly larger than 1 KiB is accepted as a plausible MP3;
- an `ftyp` file below the existing 64 KiB media threshold is rejected;
- HTML/JSON error bodies remain rejected regardless of size.

Use temporary files and deterministic byte arrays; do not bundle binary fixtures for this unit.

**Step 2: Run the focused test and observe RED**

```bash
cd android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
NANZHUFENG_BUILD_PYTHON="/opt/homebrew/bin/python3.13" \
./gradlew --no-daemon --max-workers=1 :app:testDebugUnitTest \
  --tests com.nanzhufeng.videodownloader.probe.MediaFileValidatorTest
```

Expected: the small-MP3 cases fail under the current unconditional 64 KiB threshold.

**Step 3: Implement the minimum validator change**

Read a bounded header first. For ID3 or valid MPEG audio frame sync, require at least 1 KiB. For ISO BMFF `ftyp` and WebM EBML signatures, retain the 64 KiB minimum. Reject obvious text/HTML/JSON and unknown signatures. Do not treat extension or MIME alone as proof.

**Step 4: Run GREEN and the probe suite**

Run the focused command, then:

```bash
./gradlew --no-daemon --max-workers=1 :app:testDebugUnitTest \
  --tests 'com.nanzhufeng.videodownloader.probe.*'
```

Expected: all tests pass.

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/nanzhufeng/videodownloader/probe/HttpFileDownloader.kt \
  android/app/src/test/java/com/nanzhufeng/videodownloader/probe/MediaFileValidatorTest.kt
git commit -m "fix(android): validate small MP3 files by content"
```

## Task 3: Add a Streaming LAME Encoder Boundary

**Files:**

- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/audio/PcmFormat.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/audio/Mp3Encoder.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/audio/NativeLameBridge.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/audio/LameMp3Encoder.kt`
- Create: `android/app/src/main/cpp/nanzhufeng_mp3_jni.cpp`
- Create: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/domain/download/audio/LameMp3EncoderInstrumentedTest.kt`

**Step 1: Define the Kotlin ownership contracts**

`PcmFormat` contains sample rate and channel count and accepts only 1 or 2 channels. `Mp3Encoder` exposes a closeable session with `encode(interleavedPcm: ShortArray, frames: Int)` and `finish()`. `LameMp3Encoder` chooses 128 kbps CBR mono or 192 kbps CBR joint-stereo and quality 2.

`NativeLameBridge` loads `nanzhufeng_mp3` and declares:

```kotlin
external fun open(path: String, sampleRate: Int, channels: Int, bitRateKbps: Int): Long
external fun encode(handle: Long, pcm: ShortArray, frames: Int): Int
external fun finish(handle: Long): Int
external fun close(handle: Long)
```

Return negative LAME/native errors to Kotlin and throw a descriptive `IOException`; always close the native handle in `finally`/`close()`.

**Step 2: Write a failing instrumentation test**

Generate two seconds of deterministic 440 Hz PCM in memory, encode it to the instrumentation cache directory, and assert:

- output is larger than 1 KiB;
- output begins with ID3 or MPEG audio frame sync;
- Android `MediaExtractor` reports an `audio/mpeg` track with positive sample rate, channel count, and duration.

Cover stereo 44.1 kHz and mono 48 kHz.

**Step 3: Run RED on the dedicated emulator**

```bash
cd android
ANDROID_SERIAL=emulator-5556 \
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
NANZHUFENG_BUILD_PYTHON="/opt/homebrew/bin/python3.13" \
./gradlew --no-daemon --max-workers=1 :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nanzhufeng.videodownloader.domain.download.audio.LameMp3EncoderInstrumentedTest
```

Expected: compilation/linking or behavior failure because the bridge is not implemented yet.

**Step 4: Implement the JNI session**

The native session owns `lame_t`, `FILE*`, and an encoded byte buffer. On open:

- open the destination with `fopen(..., "wb")`;
- call `lame_init`, set input sample rate, channel count, CBR bitrate, quality 2, and `MONO` or `JOINT_STEREO`;
- initialize ID3v2 with `id3tag_init`, `id3tag_add_v2`, and `id3tag_v2_only`;
- call `lame_init_params`.

On encode, validate `frames * channels <= pcm.size`. Use `lame_encode_buffer` for mono and `lame_encode_buffer_interleaved` for stereo. Size the output buffer to at least `ceil(1.25 * frames) + 7200`. Write exactly the returned bytes. On finish, allocate at least 7200 bytes, call `lame_encode_flush`, write the result, flush the file, and return errors without hiding them. Close must be idempotent.

**Step 5: Run GREEN on x86_64 emulator**

Rerun the focused instrumentation command. Expected: two tests pass and Android recognizes both files as `audio/mpeg`.

**Step 6: Commit**

```bash
git add android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/audio \
  android/app/src/main/cpp/nanzhufeng_mp3_jni.cpp \
  android/app/src/androidTest/java/com/nanzhufeng/videodownloader/domain/download/audio/LameMp3EncoderInstrumentedTest.kt
git commit -m "feat(android): add streaming LAME MP3 encoder"
```

## Task 4: Decode Android Audio and Validate Finished MP3 Files

**Files:**

- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/audio/AudioTranscoder.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/audio/AndroidPcmDecoder.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/audio/Mp3FileValidator.kt`
- Create: `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/audio/Mp3AudioTranscoder.kt`
- Create: `android/app/src/androidTest/assets/audio/tone-2s-aac.m4a`
- Create: `android/app/src/androidTest/java/com/nanzhufeng/videodownloader/domain/download/audio/Mp3AudioTranscoderInstrumentedTest.kt`

**Step 1: Create a deterministic AAC-LC fixture**

Use local FFmpeg only as a test-fixture generator:

```bash
ffmpeg -f lavfi -i 'sine=frequency=440:duration=2:sample_rate=44100' \
  -ac 2 -c:a aac -b:a 128k -movflags +faststart \
  android/app/src/androidTest/assets/audio/tone-2s-aac.m4a
```

Record its SHA-256 in the test message or adjacent source comment. The production app must not depend on FFmpeg.

**Step 2: Write a failing end-to-end instrumentation test**

Copy the fixture to cache, call `Mp3AudioTranscoder.transcode`, then verify:

- result is not byte-identical to the M4A source;
- result has no `ftyp` signature;
- `Mp3FileValidator` accepts it;
- `MediaExtractor` reports `audio/mpeg`, positive duration, 44.1 kHz, and two channels;
- decoded duration is within a reasonable tolerance of two seconds;
- cancellation deletes the incomplete destination and leaves the source intact.

**Step 3: Run RED**

Run only `Mp3AudioTranscoderInstrumentedTest` on `emulator-5556`. Expected: class/behavior is not implemented.

**Step 4: Implement streaming decode and transcode**

`AndroidPcmDecoder` must:

- select the first `audio/*` track with `MediaExtractor`;
- configure `MediaCodec` decoder for the selected format;
- stream input until EOS and drain output until output EOS;
- react to `INFO_OUTPUT_FORMAT_CHANGED` and require PCM 16-bit, 1 or 2 channels;
- pass whole interleaved PCM frames to the encoder without retaining the entire media file in memory;
- check cancellation between input, drain, and encode operations;
- release extractor/codec in all exit paths.

`Mp3AudioTranscoder` writes to a sibling temporary `.part` file, finishes and closes the encoder, validates the finished file, and atomically moves/replaces it at the destination. On any error or cancellation it deletes both `.part` and invalid destination files but preserves the source.

`Mp3FileValidator` requires:

- size over 1 KiB;
- ID3 or MPEG frame-sync evidence;
- an Android extractor track with MIME `audio/mpeg`;
- positive duration, sample rate, and 1 or 2 channels.

**Step 5: Run GREEN and repeat twice**

Run the focused test twice on the dedicated emulator to expose resource cleanup and file lifecycle errors. Expected: both runs pass.

**Step 6: Commit**

```bash
git add android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/audio \
  android/app/src/androidTest/assets/audio/tone-2s-aac.m4a \
  android/app/src/androidTest/java/com/nanzhufeng/videodownloader/domain/download/audio/Mp3AudioTranscoderInstrumentedTest.kt
git commit -m "feat(android): transcode downloaded audio to real MP3"
```

## Task 5: Integrate True MP3 into DirectMediaTransfer

**Files:**

- Modify: `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/DirectMediaTransfer.kt`
- Create or Modify: `android/app/src/test/java/com/nanzhufeng/videodownloader/domain/download/DirectMediaTransferTest.kt`
- Modify if required: `android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download/DownloadTaskRunner.kt`

**Step 1: Add failing transfer-policy tests**

Make transfer dependencies injectable without changing production call sites. Test these behaviors:

- `AUDIO_MP3` plus a validator-confirmed source MP3 returns the source as `audio/mpeg` without transcoding;
- `AUDIO_MP3` plus M4A/MP4/WebM invokes `AudioTranscoder` and returns only the validated MP3 destination;
- transcode failure propagates and never returns the compressed source mislabeled as `audio/mpeg`;
- video modes keep their existing behavior and never invoke the audio transcoder;
- cancellation propagates to the transcoder and leaves no publishable MP3.

If Android concrete classes prevent JVM testing, extract only the decision boundary into a package-private/pure Kotlin `AudioSourcePreparation` class, keep filesystem/native work behind interfaces, and cover `DirectMediaTransfer` wiring with one instrumentation test.

**Step 2: Run RED**

Run the focused `DirectMediaTransferTest`. Expected: current implementation rejects any non-`.mp3` source and cannot call a transcoder.

**Step 3: Implement integration**

For `AUDIO_MP3`:

1. download the selected audio source to a source-extension temporary file;
2. use content validation, not extension alone, to decide MP3 passthrough;
3. otherwise transcode to a separate `.mp3` temporary file;
4. return `PreparedMedia(realMp3File, "audio/mpeg")` only after strict validation;
5. delete unused/intermediate files through the existing task cleanup boundary.

Do not alter `DownloadTaskRunner` state/history rules except for the minimum constructor wiring. A failed transcode must produce the existing failed task state and no success history row/MediaStore publication.

**Step 4: Run GREEN plus task-runner regression tests**

```bash
./gradlew --no-daemon --max-workers=1 :app:testDebugUnitTest \
  --tests 'com.nanzhufeng.videodownloader.domain.download.*'
```

Expected: all download-domain tests pass.

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/nanzhufeng/videodownloader/domain/download \
  android/app/src/test/java/com/nanzhufeng/videodownloader/domain/download
git commit -m "feat(android): route audio downloads through true MP3 conversion"
```

## Task 6: Verify the Complete Build on Mac and the Dedicated Emulator

**Files:**

- Modify as evidence changes: `PROJECT_HANDOFF.md`

**Step 1: Run the complete JVM/build gate**

```bash
cd android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
NANZHUFENG_BUILD_PYTHON="/opt/homebrew/bin/python3.13" \
./gradlew --no-daemon --max-workers=1 \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` with no failing tests or fatal lint findings.

**Step 2: Inspect the APK without dumping it**

List only native library entries and confirm both ABIs contain both libraries. Compute APK size and SHA-256. Run `unzip -t` and require a clean archive result.

**Step 3: Run all instrumentation tests on emulator-5556**

```bash
ANDROID_SERIAL=emulator-5556 \
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
NANZHUFENG_BUILD_PYTHON="/opt/homebrew/bin/python3.13" \
./gradlew --no-daemon --max-workers=1 :app:connectedDebugAndroidTest
```

Expected: all instrumentation tests pass, including Python runtime, Room, navigation, history wrapping, LAME encoder, and M4A-to-MP3 conversion.

**Step 4: Emulator UI smoke**

Launch the app, capture screenshots, and verify at minimum:

- home URL entry and platform cards render;
- settings/session entry renders;
- history filters wrap on the outer-screen-size viewport;
- notification/download state UI remains reachable;
- no startup crash appears in a filtered app log.

**Step 5: Update and commit handoff evidence**

Record exact commands, test counts, APK hash, ABI contents, and remaining OPPO-only work in `PROJECT_HANDOFF.md`, then commit with:

```bash
git add PROJECT_HANDOFF.md
git commit -m "docs(android): record true MP3 emulator verification"
```

## Task 7: Preserve User Data and Validate on OPPO PKH120

**Files:**

- Modify as evidence changes: `PROJECT_HANDOFF.md`

**Step 1: Record the device and installed-app state**

Use serial `3B157F009E800000`. Record:

- `ro.product.model`, Android release/API, primary ABI;
- whether `com.nanzhufeng.videodownloader` is installed;
- installed version name/code if present;
- app data directory existence only through non-destructive package metadata.

Do not uninstall and do not call package-data clear.

**Step 2: Install as an update**

```bash
adb -s 3B157F009E800000 install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`. If Android reports an incompatible signing certificate, stop and report the exact blocker; do not uninstall the existing app without explicit user authorization.

**Step 3: Run focused arm64 native/audio instrumentation**

Run `LameMp3EncoderInstrumentedTest` and `Mp3AudioTranscoderInstrumentedTest` with `ANDROID_SERIAL=3B157F009E800000`. Expected: both pass on arm64 and Android identifies generated files as `audio/mpeg`.

**Step 4: Run the complete OPPO instrumentation suite**

Run all connected debug Android tests against only the OPPO. Expected: all tests pass. If OEM permission dialogs block automation, record the exact screen and continue with safe manual interaction rather than disabling device security.

**Step 5: Exercise the installed app's user-facing paths**

On the OPPO, verify:

- cold launch and relaunch without crash;
- home, history, settings/session screens;
- outer-screen-width history filter wrapping;
- notification permission request/state and foreground download notification;
- controlled AAC fixture converts to a playable real MP3;
- one supported real public URL per implemented platform (Douyin, TikTok, YouTube) proceeds through probe/download or produces the correct explicit session/network error;
- successful files appear in the intended MediaStore destination and open with an installed player;
- success/failure/cancel states appear correctly in history and survive app relaunch;
- existing app data/settings remain present when an earlier installation existed.

Use only public, non-DRM test media and do not import personal cookies/accounts unless the user explicitly authorizes them.

**Step 6: Capture bounded evidence**

Save screenshots for key screens, a bounded app-only log for each exercised transfer, MediaStore metadata for the MP3 artifact, and SHA-256/size for the tested APK. Do not dump full device logs.

**Step 7: Update and commit handoff evidence**

Record confirmed facts separately from limitations and unverified platform-specific cases. Commit:

```bash
git add PROJECT_HANDOFF.md
git commit -m "docs(android): record OPPO full-app verification"
```

## Task 8: Final Release Audit and Handoff

**Files:**

- Modify: `PROJECT_HANDOFF.md`
- Create if release packaging is required by the repository: release APK/ZIP in the existing documented output location

**Step 1: Run a fresh verification gate**

Rerun the complete JVM, lint, assemble, emulator instrumentation, and OPPO instrumentation commands from Tasks 6 and 7. Do not reuse old success output for the completion claim.

**Step 2: Audit repository scope**

Use status counts and `git diff --stat` first. Confirm:

- no uncommitted generated build output is tracked;
- no local SDK path or credential was committed;
- LAME provenance/license assets are included;
- no placeholder, TODO, or fake-MP3 rename path remains in the changed scope;
- `PROJECT_HANDOFF.md` contains exact current evidence and no stale “pending” statement for completed gates.

**Step 3: Verify the distributable**

For the final APK (and ZIP if produced), record absolute path, file size, SHA-256, archive integrity, ABI contents, package/version metadata, and successful install/update on the OPPO.

**Step 4: Final commit**

Commit only if the audit changed documentation or packaging metadata. Use a narrow message such as:

```bash
git commit -m "chore(android): finalize verified OPPO delivery"
```

**Step 5: Completion decision**

Declare the app fully landed only if every required build/test/device gate above has fresh passing evidence. If a supported platform cannot be fully exercised because it requires a session, cookie, regional network, or private content, label that exact path as externally blocked and do not generalize it to “complete.”
