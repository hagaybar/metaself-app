# Plan — Settings holds Your numbers, and says what changed (D133, D134)

Spec: `docs/superpowers/specs/2026-10-11-settings-holds-your-numbers-design.md`. Branch
`settings-holds-your-numbers`. Release 0.81.0 (versionCode 137).

## Task 1 — the change log (D134), pure

- `ui/about/ChangeLog.kt`: `ChangeLogEntry(version, date: LocalDate, sentence)` and `ChangeLog.entries`,
  newest first, 0.81.0 back to 0.67.0. KDoc: every version bump adds its entry at the top, in the words
  its release was announced in.
- `ChangeLogTest` (JUnit 5): the first entry is `BuildConfig.VERSION_NAME`; versions strictly
  descending, so no duplicates; no entry names a decision or issue number.
- `CLAUDE.md` "Working rhythm": one line saying the bump adds its entry, and a test fails without it.
- The first-entry test is red until Task 4 bumps the version; that is the test doing its job.

## Task 2 — About this app (D134)

- `ui/about/AboutWording.kt` (pure, replaces `ui/VersionMarker.kt`): "MetaSelf 0.81.0", "Build 137 ·
  installed version", an entry's heading "0.81.0 · 11 Oct", and the index line "Version 0.81.0 · what
  changed". `VersionMarkerTest` becomes `AboutWordingTest`.
- `SettingsPage.ABOUT` (last), its page `AboutSettingsPage`, wired in `SettingsDestination`; the
  version reaches Settings from `MetaSelfRoot` through `MetaSelfNavHost`, as it reached Your numbers.
- `SettingsIndexWording.statusOf` takes the version name for the About row.
- The version line leaves Your numbers (`HomeScreen` loses `versionName`/`versionCode`).
- Tests: index row and status; the page's version, build and entries in order.

## Task 3 — Your numbers is Settings' first row (D133)

- `TargetWording.goal(profile)`: "lose 0.5 kg a week" / "hold your weight" / "gain 0.25 kg a week" —
  the goal line's own words, which now builds on it.
- `SettingsIndexWording.yourNumbers(target, profile)`: "1,650 kcal a day · lose 0.5 kg a week".
- `SettingsScreen` draws a first row, Your numbers, above the pages; it calls the root's existing
  callback that raises the Your numbers cover. Back from the cover drops it, and `AppOrCover` hands the
  app's saved navigation back — so it lands on the Settings index it was opened from (the mechanism
  public issue #11 built for the Weight page).
- Today's menu loses Your numbers.
- Tests: wording for lose / hold / gain; the row's place, line and tap; the cover returns to Settings
  (`AppOrCoverSessionTest`); Today's menu without it.

## Task 4 — 0.81.0

versionName 0.81.0, versionCode 137. Unit tests and lint; only the ten Room classes may skip.

## Not built

Longer spans of training data; the weekly letter's rework (both named in the spec as not here).
