# MetaSelf

A personal, offline-first Android fitness tracker. Describe a meal in plain words; the numbers
follow.

**[metaself-app pages →](https://hagaybar.github.io/metaself-app/)**

- [Privacy](https://hagaybar.github.io/metaself-app/privacy.html)
- [Terms of use](https://hagaybar.github.io/metaself-app/terms.html)

MetaSelf is a personal project, built for one person: it is not on any app store, has no accounts,
no analytics and no advertising, and there is no server behind it. Everything lives on the phone.

## What it does

**Logging food, four ways.** Describe a meal in ordinary language and a model returns calories and
macros. Scan a barcode, which is looked up in Open Food Facts. Type the numbers yourself. Or repeat
a food or a meal you have logged before.

**Every number records where it came from.** A typed figure, a label, a model's estimate and a
figure copied from an earlier meal are stored as different things and ranked against each other, so
a guess can fill a blank but never overwrite something known. **An estimate is never presented as a
measurement** — this is the rule the whole record hangs on, and the reason the app can be trusted
about its own uncertainty.

**The day answers one question.** How much is left today, as one number, with the macros and the
step count beneath it. The food itself is grouped into at most four parts of the clock — Night,
Morning, Mid day, Evening — each showing its own hours, so a day fits on the screen without
scrolling. The full record, where entries are corrected and deleted, has a screen of its own.

**Foods and meals are yours to keep.** A food learns its figures as you log it. A meal is something
you build and name from foods you already have; nothing is ever promoted into one behind your back.

**Weight, as a trend rather than a jitter.** Readings are smoothed, charted over a range you pick,
and measured against a goal with a projected finish. The scale's own number is always shown beside
the trend, so the smoothing is never mistaken for the app disagreeing with the scale.

**Movement, if you want it.** Steps and active calories come from Health Connect. Only movement
above an ordinary day earns anything, so a normal day's walking is not counted twice.

**An eating window, if you want one.** Either fixed hours or a fasting ratio. The app says when you
may eat next rather than telling you off, and it never judges a day that was logged before the rule
was set.

**Backup you can read.** The whole record exports as a plain JSON file, optionally to Google Drive.
A backup nobody can read is a backup nobody can check.

## This repository

This is the **buildable source** of the app, plus the pages above. It is a published copy of a
private development repository: the design record, the tooling and the commit history stay there.
That is why this repository has no history to speak of, and why comments referring to numbered
decisions and issues point at a tracker you cannot see.

It is published so its CI can run, and because there is no reason for the code to be secret. It is
not published as something to install — there are no releases here, and the app is signed with a key
that is not in this repository.

## Building it

```
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

Kotlin, Jetpack Compose and Material 3, Room for storage, Hilt for wiring. `minSdk 26`,
`targetSdk 34`, JVM 17. Exact versions are in `gradle/libs.versions.toml`.

Describing a meal needs an API key for a model provider, entered in the app's settings and stored on
the device. Everything else — logging by hand, barcodes, weight, the eating window, backup — works
without one.

The test suite is around 1,800 tests and runs on the JVM; there is no instrumented suite, because
there is no emulator in CI. Tests that need a real SQLite build are skipped on hosts that cannot
provide one and are required to run in CI, which fails the build if anything is skipped there.

## Licences

The code carries no licence and is published to be read, not reused: all rights reserved.

The two bundled typefaces are third-party and are licensed under the SIL Open Font License,
Version 1.1. Their licence texts travel with them in [`licences/`](licences/), as that licence
requires.

The published pages — `index.html`, `privacy.html` and `terms.html` — describe how the app handles
data.
