# Older history can be read — Implementation Plan (health record, phase 4)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans, task by task. Steps use checkbox syntax for tracking.

**Goal:** D72. Upgrade the toolchain so the stable Health Connect client (`1.1.0`) can be used, then ask
for `READ_HEALTH_DATA_HISTORY` and let the copying reach back past the default limit.

**Architecture:** two halves, two separate commits groups, so a toolchain problem is never tangled
with a behaviour change.

1. **The toolchain, with no behaviour change** (Tasks 1–2): Gradle 8.5 → 8.11.1+, AGP 8.2.2 → 8.9.1+,
   compileSdk 34 → 36, Health Connect `1.1.0-alpha07` → `1.1.0`, and whatever those force (Hilt, KSP,
   Robolectric, lint). **targetSdk stays 34** — raising it changes runtime behaviour and is its own
   decision. The whole suite, lint and a release build must be green with the app behaving exactly as
   0.56.0.
2. **The history permission** (Tasks 3–4): the permission, the feature check, resuming finished
   catch-ups once it is granted, one Settings line.

**Decision:** D64 / D72 (`docs/superpowers/specs/2026-09-26-health-record-design.md`); the owner approved
installing Android SDK 36 and build-tools into the shared `~/android-sdk` on 2026-09-27 (additive).

**Red lines (stop and report if crossed):**

- **Toolchain tasks change no behaviour.** No production logic edits beyond what an API change forces;
  each forced edit is listed in the report with its reason.
- **targetSdk stays 34.** minSdk stays 26.
- **No schema change** (`app/schemas` untouched) and **no lint baseline** added to hide new warnings —
  each new lint finding is fixed or explicitly suppressed at its site with a reason.
- **Measured, not remembered:** every version chosen is checked against its published requirements
  (AAR `minCompileSdk` / `minAndroidGradlePluginVersion` metadata, AGP's Gradle/JDK requirements) and the
  check is quoted in the report.
- **The shared SDK folder only gains packages.** Nothing is removed or updated in place; guitar-pal's
  platform 34 / build-tools 34 stay.
- **Shared box:** `free -m` before every Gradle command (≥ ~4000 MB available, ≥ ~6000 MB for
  `ms-release`); `~/bin/gradlew-safe` only; never `gradlew --stop`; one build at a time. A build with the
  new AGP may need more memory: if a build is killed or OOMs, stop and report rather than raising the
  heap in `~/.gradle/gradle.properties` (that file is shared with the other project).
- **Never `git add -A`.** Commits end with the co-author line; no session links.

---

### Task 1: Install SDK 36

- [ ] `~/android-sdk/cmdline-tools/latest/bin/sdkmanager --list_installed` (record before).
- [ ] `yes | ~/android-sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-36" "build-tools;36.0.0"`
  (if `36.0.0` is not offered, the newest `36.x`; record which).
- [ ] `--list_installed` again: exactly the new packages were added; android-34 and build-tools 34.0.0 are
  still there.

### Task 2: The toolchain

Branch `older-history-can-be-read` from main. One step at a time, building after each
(`:app:assembleDebug` then `:app:testDebugUnitTest`), committing each green step:

- [ ] **Gradle wrapper → 8.11.1** (`gradle/wrapper/gradle-wrapper.properties`, and the wrapper jar/scripts
  via `~/bin/gradlew-safe wrapper --gradle-version 8.11.1` — check the distribution checksum if one is
  pinned).
- [ ] **AGP → 8.9.1** (or the newest 8.9.x/8.10.x whose requirements this project meets; quote them). Fix
  what AGP forces: `namespace`, `packaging`, `buildFeatures`, deprecated DSL, `android.suppressUnsupportedCompileSdk`
  removal if present.
- [ ] **compileSdk → 36** (targetSdk stays 34).
- [ ] **Health Connect → 1.1.0** (check the AAR's `minCompileSdk`/`minAndroidGradlePluginVersion`). Fix API
  changes it forces in `data/movement/HealthConnectSteps.kt` and `data/health/HealthConnectReader.kt`
  (e.g. `Metadata` constructors, deprecated properties) — behaviour identical.
- [ ] **Anything else forced** (Hilt Gradle plugin, KSP, Robolectric's SDK support, Compose compiler) —
  only what the build demands, each with the error that forced it.
- [ ] **CI** (`.github/workflows/android-ci.yml`): the comment says the job installs android-34 and the
  build tools; make it install what compileSdk 36 needs, or confirm AGP downloads it on the runner.
- [ ] Whole suite: 0 failures, skipped exactly the ten SQLite classes. `:app:lintDebug` exit 0. `git status
  app/schemas` clean. `~/bin/ms-release` builds a signed APK (memory check first).

Commit messages like `build: Gradle 8.11.1`, `build: AGP 8.9.1 and compileSdk 36`,
`build: Health Connect 1.1.0 stable`.

### Task 3: The history permission

- [ ] Manifest: `<uses-permission android:name="android.permission.health.READ_HEALTH_DATA_HISTORY" />`,
  with a comment citing D72.
- [ ] `HealthPermissions.ALL` includes `HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY` (verify the
  constant's name in 1.1.0).
- [ ] `HealthSource` gains `suspend fun historyGranted(): Boolean` — true only when the feature
  `HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY` is available **and** the permission is granted
  (verify the feature API in 1.1.0; on a phone whose Health Connect lacks the feature, false).
- [ ] `HealthRecordSync`: the first open on which `historyGranted()` is true and a marker says it has not
  yet been acted on, every kind whose catch-up is done is set not-done **keeping its cursor**, so the
  catch-up carries on further back from where it stopped; then the marker is written. The marker is a
  `health_sync` row with kind `"_HISTORY"` (no schema change). Every reader of `health_sync` rows must
  ignore kinds `HealthKind.parse` does not know — check `HealthRecordState.from` and any other reader, and
  test it. The empty-weeks counter starts afresh (it already does each open).
- [ ] Tests (JUnit 5, fakes): history granted for the first time re-opens done catch-ups at their cursors
  and writes the marker; a second open does not re-open them again; history not granted changes nothing;
  the `_HISTORY` row does not appear in Settings' state (not counted as a kind, not "catching up").

### Task 4: Settings says whether older history is allowed

- [ ] `HealthRecordState` gains `historyAllowed: Boolean?` (null = the phone's Health Connect cannot offer
  it). Wording (`HealthRecordWording`, tested):
  - `false` → "Reading history older than 30 days is not allowed — tap Connect to allow it."
  - `null` → no line.
  - `true` → no line.
  The Connect button also shows when `historyAllowed == false`.
- [ ] Settings/VM tests as the existing health-record ones.

### Task 5: Version, suite, CI, release

- [ ] `app/build.gradle.kts`: 110 → 111, "0.56.0" → "0.57.0".
- [ ] Whole suite, lint, schemas clean, anonymisation read.
- [ ] PR `0.57.0: older history can be read (D72, phase 4)`: body lists every version change with the
  requirement check that justified it, every forced code edit, and what the phone must show. CI green with
  0 skipped → squash-merge → `ms-release` → APK.
- [ ] **Phone checks:** the app installs over 0.56.0 and behaves as before; Settings → Movement → Connect
  now also asks for "past data" / history; after allowing it and a few opens, the status line's "from"
  date moves earlier and "still catching up" appears again until it finishes.
