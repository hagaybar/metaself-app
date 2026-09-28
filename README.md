# MetaSelf

A personal, offline-first Android fitness tracker. Describe a meal in plain words; the numbers
follow.

**[metaself-app pages →](https://hagaybar.github.io/metaself-app/)**

- [Privacy](https://hagaybar.github.io/metaself-app/privacy.html)
- [Terms of use](https://hagaybar.github.io/metaself-app/terms.html)

MetaSelf is a personal project, built for one person: it is not on any app store, has no accounts,
no analytics and no advertising, and there is no server of its own behind it. Everything lives on the
phone; the only things that leave it are a backup to your own Google Drive, if you switch it on, and
what you send to the model provider when you ask it something.

## What it does

**Logging food, four ways.** Describe a meal in ordinary language and a model returns calories and
macros; when a plate is hard to judge, the model may ask a few short questions first, and you can
always tell it to use its best guess. Scan a barcode, which is looked up in Open Food Facts. Type
the numbers yourself. Or repeat a food or a meal you have logged before. A drink is counted by the
100 ml, not by the gram.

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
you build and name from foods you already have; nothing is ever promoted into one behind your back. A food's
figures can be sent to the model for a second look, as a nutritionist would read them; its
suggestions are offered, never applied on their own.

**Weight, as a trend rather than a jitter.** Readings are smoothed, charted over a range you pick,
and measured against a goal with a projected finish. The scale's own number is always shown beside
the trend, so the smoothing is never mistaken for the app disagreeing with the scale. Turn the
phone sideways and the chart fills the screen.

**Movement, if you want it.** The app reads Health Connect — Android's shared store for health
data, which a fitness band's own app writes into — and keeps its own copy: steps, distance, heart
rate, sleep, workouts and the rest, each figure saying which app it came from. Only movement above an
ordinary day earns extra food, so a normal day's walking is not counted twice. A Movement screen
shows the week: distance, workouts, and a short line per day.

**Workouts from wherever they were recorded.** A session can come from the band, be typed in by
hand, or be filled in from a workout file shared from another app. When two apps recorded the same
walk it shows once, each figure taken from the best source and labelled with it, and it can be split
back into two sessions if the app guessed wrong.

**A trainer, if you ask for one.** Before a session the model can suggest what to do next; after it,
you say how it felt and get feedback. It sees your sessions, weekly and monthly totals, the weight
trend and a short note you write about yourself — never your meals or your sleep — and everything it
says is labelled as advice, not a measurement.

**An eating window, if you want one.** Either fixed hours or a fasting ratio. The app says when you
may eat next rather than telling you off, and it never judges a day that was logged before the rule
was set.

**Backup you can read.** The whole record exports as a plain JSON file, optionally to Google Drive
every day. The detailed health readings, too many for a daily file, go to Drive as one compressed
file per month and can be brought back after a restore. A backup nobody can read is a backup nobody
can check.

## This repository

This is where the app is developed: the source, the design record in
[`docs/superpowers/specs/`](docs/superpowers/specs/) and the plans that built it, the small checking
scripts in [`tools/`](tools/), and the pages above. The code cites numbered design decisions
(*"D4"*, *"D92"*); each is written out in a spec there.

Development began in a private repository, which is now archived. Issue numbers in older code
comments refer to its tracker and have no counterpart here; newer comments cite this repository's
issues as "public issue #N".

It is not published as something to install — there are no releases here, and the app is signed
with a key that is not in this repository.

## Building it

```
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

Kotlin, Jetpack Compose and Material 3, Room for storage, Hilt for wiring. `minSdk 26`,
`targetSdk 34`, `compileSdk 36`, JVM 17. Exact versions are in `gradle/libs.versions.toml`.

Describing a meal, the nutritionist's review and the trainer need an API key for a model provider,
entered in the app's settings and stored on the device. Everything else — logging by hand, barcodes,
weight, movement, the eating window, backup — works without one.

The test suite is nearly 3,900 tests and runs on the JVM; there is no instrumented suite, because
there is no emulator in CI. Tests that need a real SQLite build are skipped on hosts that cannot
provide one and are required to run in CI, which fails the build if anything is skipped there.

## Licences

The code carries no licence and is published to be read, not reused: all rights reserved.

The two bundled typefaces are third-party and are licensed under the SIL Open Font License,
Version 1.1. Their licence texts travel with them in [`licences/`](licences/), as that licence
requires.

The published pages — `index.html`, `privacy.html` and `terms.html` — describe how the app handles
data.
