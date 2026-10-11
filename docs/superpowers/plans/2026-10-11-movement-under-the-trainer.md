# Movement under the Trainer — Implementation Plan (D130–D132)

**Goal:** `docs/superpowers/specs/2026-10-11-movement-under-the-trainer-design.md`. Movement leaves Today's menu and
becomes "Your movement", the Trainer's detail page; its headline becomes the week's minutes, counted as the Trainer's
verdict counts them; the Trainer page gains a movement summary line, a "Log a workout" button and a "Your movement"
row. Version 0.80.0 (136).

**Branch:** `movement-under-the-trainer`, from main. **No Room schema change.**

**Tech:** JUnit 5 + Truth for everything pure and the view models; JUnit 4 + Robolectric only for render tests.
Fixtures are invented; today is `TEST_EPOCH_DAY` (Thursday 3 September 2026).

## Choices the spec leaves open — settled here

1. **One counting rule (D131).** A pure `WeekSessions` (domain/movement) holds the week's counted sessions — visible,
   counted (D81), Monday to today or Sunday, in start order — and their minutes. `MovementWeek`, `WeekLedgers` and
   `WeekStrip` all take their sessions from it. The ledger's `minutes` stays the sum over its partition (practice +
   beyond, D110), which covers exactly those sessions; a test pins `WeekLedger.minutes == MovementWeek.minutes` and the
   session counts on weeks with ticks, attempts, hidden, uncounted and unrecognised sessions.
2. **`MovementWeek`** drops `workoutCount`, `walkCount`, `workoutMinutes` (only the replaced line used them) for
   `sessions` and `minutes`.
3. **Headline wording:** "156 min" — always minutes, never hours, as the verdict says minutes. The line under it:
   "6 sessions · 21.4 km · 310 kcal of movement a day", "1 session" singular, each part left out when it has nothing
   to say (no session, no distance, no movement calories); no line at all when none has.
4. **The Trainer's summary line (D132.1):** `TrainerWeek` gains a `MovementSummary(sessions, distanceM, fourWeeksM)`.
   The week's km and the four weeks' km are counted by the same `MovementWeek` functions Movement uses (the sum of the
   days' distances; the four weeks before this one, each week summed, then the weeks with a figure summed). The four
   weeks are said with one decimal, like every other km in the app. "Days ›" is always there, so the line is always a
   way in, even when every part is left out.
5. **Log a workout (D132.2):** the Movement destination takes `openLog` (boolean, default false):
   `movement?openLog={openLog}`. The view model reads it from `SavedStateHandle` and opens the sheet for today once;
   it marks the handle so returning to the page (or a configuration change) does not open it again. On the Trainer
   page the row is: "Log a workout" outlined, then the main filled button, each half the width; where the main button
   has the session action under it (Plan ahead and the plan-page buttons), that stays under the row, full width.
6. **The row (D132.3):** the Trainer's rows gain an optional second line; "Your movement" sits after the plan row.
7. **Names:** the step bar's and strip's tap say "open Your movement". The unused "Movement" menu string goes.

## Tasks

1. `WeekSessions`; `MovementWeek.sessions/minutes`; ledger and strip read from it; the parity test. (D131)
2. Movement's headline and line, wording and screen. (D131)
3. Today's menu without Movement; the page called "Your movement" with no Trainer button; comments. (D130)
4. `MovementSummary` on `TrainerWeek`, its wording, the strip card's line. (D132.1)
5. `openLog` on the Movement destination; the view model opens the sheet; the Trainer's "Log a workout". (D132.2)
6. The "Your movement" row. (D132.3)
7. 0.80.0 (136).

## Not built here

As the spec: merging "Your numbers" into Settings; the version and change log in Settings; the weekly letter's
rework; tapping a strip block to open its session.
