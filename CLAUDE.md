# CLAUDE.md — MetaSelf Project Guide

## What this is

A personal, single-user, offline-first Android fitness tracker for one owner. Food is logged by
describing a meal in plain words, scanning a barcode, typing the numbers, or repeating something
logged before; an AI model turns a description into calories and macros. **There is no photograph
path — the camera is used for barcodes only.** Weight is tracked with a smoothed trend.

## This repository is PUBLIC

Work happens here. Everything committed here is world-readable, immediately and permanently.

There is an **archived private repository**, `hagaybar/MetaSelf`, holding the project's full history,
44 plan files, and (until they are moved) the design specs. It is read-only reference. Nothing is
pushed to it.

### Nothing personal goes in this repository

The app has one user, and this repository must not become a record of him. Five separate passes were
needed to get it to this state, so these rules are hard-won rather than theoretical.

- **The leaks are semantic, not lexical. No grep finds them — they are sentences.** A sentence like
  *"the warning seen on most days"* is an adherence rate; *"after a week away"* is an absence; *"the
  case this pins: a sandwich logged, then repeated the next day"* is a record of a meal somebody ate.
  (These three are invented to show the shape.) Checking means reading the prose.
- **A food NAME in a fixture is fine; a story about eating it is not.** `name = "Pizza"` is data.
  Do not strip harmless vocabulary and feel productive.
- **Never record a real weight, a real goal, a real food eaten, or a real rate**, and never state a
  frequency: "most days", "usually", "thirty mornings" are observations of one person's record.
- **Write "the owner", never a name. Never quote him in his own voice.** Keep the observation, drop
  the speaker: *"the owner reported that ticking two foods did nothing"* becomes *"ticking two foods
  appears to do nothing"*.
- **Search by SUBJECT, not by the phrasing a finding was reported in.** Three passes each removed one
  copy of the same episode and left the others, because each searched the sentence it was handed.
- **Every anonymising edit creates its own leaks.** Two signatures: a comment whose figures no longer
  match the code beneath it, and an assertion still forbidding a word the test no longer uses, which
  passes vacuously and looks like coverage. Re-verify after every pass.
- **An invented figure says so beside itself** — `aProfile()`'s KDoc is the pattern. But never write
  that a value was *changed*; that invites the question it was meant to close.
- **A test name is published output**, because it prints in CI. It gets the same reading as a comment.
- **No denylist of his real values may live here.** A check that fails when a particular weight
  appears would publish the fact that the weight matters.

### The standard fixtures

- **`aProfile()`** in `app/src/test/java/com/metaself/app/domain/profile/Profiles.kt`: a man of 80 kg
  and 180 cm, born 1980, moderately active, losing 0.5 kg a week, **no target weight**. That body was
  chosen because every intermediate number it produces is round. Apart from that function's KDoc,
  this is the only place it is written out in prose; when it changes, both change.
- **A second, contrasting body on purpose** for the calorie floor: a sedentary woman of 65 kg and
  165 cm, born 1986, losing 1 kg a week. The standard profile never reaches the floor.
- **`TEST_EPOCH_DAY = 20_699`** (2026-09-03) is the shared "today". Fixtures that need a date use it.
- **Invent the figures in every override**, and pick ones that keep the arithmetic round.

## Hard rules

1. **Never run bare `./gradlew` — a hook blocks it.** Use `~/bin/gradlew-safe` with the same
   arguments. It holds a lock so only one Gradle build runs at a time; concurrent builds once
   exhausted the development box and locked its owner out of SSH.
2. **Never pipe a build whose result you intend to report.** The pipe returns the filter's exit code,
   so a failed build reports success. Redirect to a file and check `$?`.
3. **`export ANDROID_HOME=/home/ubuntu/android-sdk` before any Gradle command.**
4. **The release APK is built with `~/bin/ms-release`**, not `assembleRelease` — R8 needs 4g and the
   box's default is 2048m. Every build the owner installs must be release-signed.
5. **An estimate is never presented as a measurement.** Every stored number carries where it came
   from. This is decision D4 and it is not negotiable for convenience.
6. **A number in a comment is a measurement or it is not written.** This project has shipped a
   fabricated contrast ratio and a fabricated ring size, both of which propagated into tests before
   being caught. Compute it first.
7. **Never `git add -A` mid-run.** Stage by explicit path. This has three times swept unrelated code
   into a commit labelled `docs:`.

## Stack

Kotlin 1.9.22 · JVM 17 · AGP 8.2.2 · Gradle 8.5 · Compose BOM 2024.02.00 · Material 3 · Hilt 2.50
over KSP. Exact versions: `gradle/libs.versions.toml`. `minSdk 26 · targetSdk 34 · compileSdk 34`.

## Testing

- **JUnit 5 + Truth** for everything pure. **JUnit 4 ONLY where a framework demands it** —
  Robolectric and Compose. The two `@Test` annotations look identical at the call site and mixing the
  imports produces a test that silently never runs.
- **Robolectric lives in `src/test`, never `androidTest`.** There is no emulator here or in CI, so an
  instrumented suite would never execute.
- **This box is linux/aarch64.** Conscrypt has no build for it, so `testOptions` switches it off. Do
  not remove that line.
- **Room's tests skip locally and run in CI, by design.** Robolectric's SQLite is a native library
  with no aarch64 build, so every test needing a database stands aside via `assumeSqliteRuntime()` in
  `app/src/test/java/com/metaself/app/data/SqliteRuntime.kt`. **The condition is the processor, never
  a caught exception.** So **"0 skipped" is the CI invariant, not the local one** — CI's "No test
  skipped" step fails the run on any skip. Locally, expect exactly these ten classes to skip and
  nothing else: `MigrationTest`, `MealDaoTest`, `RoomMealRepositoryTest`, `RoomFoodRepositoryTest`,
  `RoomSavedMealRepositoryTest`, `WeightDaoTest`, `RoomWeightRepositoryTest`, `BackupRoundTripTest`,
  `HealthRecordDaoTest`, `HealthRecordStoreTest`.
- **A migration can be checked locally without Robolectric, and should be.** Extract its SQL and run
  it against Python's `sqlite3` from the previous version's exported schema. It proves the statements
  parse and the invariants hold; it does not prove what Room validates. `tools/check-migration-4-5.py`
  is the pattern.
- **The schema is exported to `app/schemas` and committed.** A schema change with no new file there
  means the version was not bumped.
- **A Compose render test reads text fields through `EditableText`, not `Text`.** `ComposeRender`
  returns both; a helper reading only `Text` sees a form's labels and nothing typed into it.
- **Robolectric here renders on a 320 dp canvas with no real font, and that bounds what a render test
  can assert.** `ComposeRender.PHONE_WIDTH_PX` has never had any effect — ask for 1080 and the decor
  comes back 320 px at density 1. And with no font a label measures about one pixel per character, so
  **nothing about real width, wrapping, truncation or a touch target's true height is assertable**. A
  48 dp height assertion would pass on a 40 dp control. To test wrapping, set a narrow
  `@Config(qualifiers = "+w150dp")` and assert relative geometry, never absolute sizes.
- **`ComposeRender.dispose()` drains the main looper, and must keep doing so.** Work left queued at
  teardown leaves Compose's UI dispatcher stuck for the JVM, and it shows only in a LATER
  `createComposeRule` test hanging on idle.

Commands:

```
export ANDROID_HOME=/home/ubuntu/android-sdk
~/bin/gradlew-safe :app:testDebugUnitTest
~/bin/gradlew-safe :app:lintDebug
~/bin/gradlew-safe :app:assembleDebug
```

## The numbered decisions

The code cites roughly 48 decisions, D2 to D51 — *"D4"*, *"(D33)"*, *"design §4"*. **They are the
record: argue with a decision in its spec, never in code.** The specs are in `docs/superpowers/specs/`.
A spec is published prose and gets the same reading as a comment: its worked examples are invented and
say so.

Issue numbers in code comments are the OLD private ones and were deliberately not rewritten — many
are closed and have no counterpart here. This repository's own issues start from 1. New comments
cite this repository's issues as "public issue #N", so they cannot be mistaken for the old ones.

## Working rhythm

One step = one plan = one branch. Branch names describe the behaviour that changes, not an issue
number. Test first where the test is about behaviour. Report what was *not* built — work quietly
trimmed is worse than work openly refused.
