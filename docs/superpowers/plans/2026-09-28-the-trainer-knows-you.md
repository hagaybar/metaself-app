# The trainer knows you (D89–D91) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every trainer request also carries up to twelve monthly lines and the owner's "About me" note;
a Movement session line shows its start time, and a workout file that matches nothing offers the day's
sessions of the same kind before "Add it as a workout".

**Architecture:** The monthly lines are a pure computation in `domain/trainer/MonthlyLines.kt`, called
from `TrainerRequest.of`, which gains two inputs (the record's earliest day and the note). `AskTheTrainer`
reads thirteen months instead of 42 days. The note lives in the shared preferences DataStore behind a
small interface of its own (`AboutMeStore`), so the many `AiSettingsStore` fakes are untouched. The
backup carries it as format 6. D91 extends `ImportOutcome.NoMatch` with the same-kind sessions of the
file's day and teaches the screen and view model to choose one of them.

**Tech Stack:** Kotlin 1.9.22, Compose (compiler 1.5.9), Hilt, DataStore Preferences, kotlinx.serialization,
JUnit 5 + Truth (JUnit 4 only for Robolectric/Compose render tests).

**Spec:** `docs/superpowers/specs/2026-09-28-the-trainer-knows-you-design.md`.

---

## Ground rules for every task

- `export ANDROID_HOME=/home/ubuntu/android-sdk`; build only with `~/bin/gradlew-safe`; `free -m` first
  (under 4000 MB available: wait, recheck). Never pipe a build whose result is reported: redirect to a file
  under `/home/ubuntu/.claude/jobs/ab41799a/tmp/` and check `$?`.
- A targeted run: `~/bin/gradlew-safe :app:testDebugUnitTest --tests '<fqcn>' > $LOG 2>&1; echo $?`.
- Stage explicit paths only. Commit messages end with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and carry no session link.
- Public repository: every figure in a test or comment is invented and round; test names are published
  prose; no device or app brand in copy.
- Compose: never an early `return` out of an inline composable lambda (compiler 1.5.9 miscompiles);
  branch instead. `InlineComposableReturnGuardTest` must pass.

## Dates used by the tests

`TEST_EPOCH_DAY = 20_699` is Thursday 3 September 2026. The 42-day detail begins at
`TrainerRequest.firstDay = 20_658` (24 July), so the cut is 20_657 (23 July). The twelve months are
September 2025 … August 2026; August lies wholly inside the detail and gets no line; July is the part
month 1–23 July (20_635..20_657). June 2026 is 20_605..20_634 and begins on a Monday (20_605); its
Mondays are 20_605, 20_612, 20_619, 20_626, 20_633. May 2026 is 20_574..20_604.

## File map

| File | Change |
|---|---|
| `app/src/main/java/com/metaself/app/domain/trainer/MonthlyLines.kt` | **new**: `MonthFacts`, `KindFacts`, `FeltCounts`, `MonthlyLines.of` |
| `app/src/main/java/com/metaself/app/domain/trainer/TrainerRequest.kt` | `months`, `aboutMe` fields; `of` takes `earliestDay`, `aboutMe`; `firstRecordDay(today)` |
| `app/src/main/java/com/metaself/app/data/ai/TrainerPrompt.kt` | `months` and `about_me` in the user message; system prompt names both |
| `app/src/main/java/com/metaself/app/data/trainer/AboutMeStore.kt` | **new**: interface + `DataStoreAboutMeStore` |
| `app/src/main/java/com/metaself/app/di/AiModule.kt` | provides `AboutMeStore` |
| `app/src/main/java/com/metaself/app/data/trainer/AskTheTrainer.kt` | reads 13 months, the earliest day and the note |
| `app/src/main/java/com/metaself/app/ui/trainer/TrainerWording.kt` | privacy line names the note and the monthly lines; About me card wording |
| `app/src/main/java/com/metaself/app/ui/screen/trainer/TrainerScreen.kt`, `TrainerViewModel.kt` | About me card |
| `app/src/main/java/com/metaself/app/ui/screen/trainer/AboutMeScreen.kt`, `AboutMeViewModel.kt` | **new**: the page with the field and Save |
| `app/src/main/java/com/metaself/app/ui/nav/MetaSelfNavHost.kt` | `trainer/about` destination |
| `app/src/main/res/values/strings.xml` | the new screen's strings |
| `app/src/main/java/com/metaself/app/domain/backup/Backup.kt`, `data/backup/BackupRepository.kt` | format 6, `about_me` |
| `app/src/main/java/com/metaself/app/ui/movement/MovementWeekWording.kt`, `ui/screen/movement/MovementScreen.kt` | start time on a session line |
| `app/src/main/java/com/metaself/app/domain/movement/WorkoutFile.kt`, `WorkoutFileMatch.kt`, `data/health/WorkoutFiles.kt`, `ui/movement/WorkoutFileWording.kt`, `ui/screen/movement/MovementScreen.kt`, `MovementViewModel.kt` | D91's "Is it one of these?" |
| `app/build.gradle.kts` | 0.65.0 / 121 |

---

### Task 1: The monthly lines, as a pure computation (D89)

**Files:**
- Create: `app/src/main/java/com/metaself/app/domain/trainer/MonthlyLines.kt`
- Test: `app/src/test/java/com/metaself/app/domain/trainer/MonthlyLinesTest.kt`

- [ ] **Step 1: Write the failing tests** (JUnit 5 + Truth; every figure invented and round). One test per rule:
  1. `the months are the twelve before this one, cut the day before the detail` — record from 20_332
     (1 Sep 2025): eleven lines, first 1 Sep 2025, last 1–23 July 2026 marked part; no August line.
  2. `a month before the record begins is left out, and the month it begins in says its first day` —
     earliest 20_620 (16 June): lines are June (from 20_620, part) and July (part).
  3. `a month with no sessions still gets a line` — zero sessions, 0 minutes, nulls for the rest.
  4. `with nothing stored there are no lines` — earliest null → empty.
  5. `sessions are the visible, counted ones, by kind, with their minutes` — hidden and uncounted left out;
     kinds most sessions first.
  6. `distance per kind only where a session of that kind has one` — a walk with 3 km and one without:
     walk 3_000; a strength session: null.
  7. `the longest session's minutes`.
  8. `the best week is the highest-distance week whose Monday is in the month` — day distances in the
     weeks of 20_605 (10 km) and 20_612 (12 km); a week whose Monday is 25 May (20_598) with 20 km on
     2 June is not June's.
  9. `heart rate is averaged by minutes over sessions that have one` — 100 bpm × 20 min and 130 × 40 →
     120; a session without heart rate ignored.
  10. `felt counts come from the month's reviews` — easy 1, right 2, hard 0; no felt at all → null.
  11. `steps a day are averaged over days that have a count` — 6_000 and 8_000 on two days, another day
      with no steps → 7_000.
  12. `the weight change is the trend's, from the month's first day to its last` — readings 80.0 on
      31 May (20_604), 79.0 on 30 June (20_634): trend at start 80.0, at end 80 + (79 − 80)(1 − 0.9^30)
      = 79.0423912… → change −0.957609 (computed in the test from `WeightTrend.of`, not typed as a
      literal); a month with no reading of its own → null; no reading on or before the first day → null.

- [ ] **Step 2: Run, see it fail** (unresolved `MonthlyLines`).

- [ ] **Step 3: Implement**

```kotlin
package com.metaself.app.domain.trainer

data class KindFacts(val kind: WorkoutKind, val sessions: Int, val distanceM: Int?)
data class FeltCounts(val easy: Int, val right: Int, val hard: Int)

/** One month's line (D89). [firstDay]..[lastDay] are the days it covers; [part] when not the whole month. */
data class MonthFacts(
    val firstDay: Long, val lastDay: Long, val part: Boolean,
    val kinds: List<KindFacts>, val minutes: Int, val longestMinutes: Int?,
    val bestWeekMonday: Long?, val bestWeekM: Int?, val avgHeartRate: Int?,
    val felt: FeltCounts?, val stepsADay: Int?, val weightChangeKg: Double?,
) { val sessions: Int get() = kinds.sumOf { it.sessions } }

object MonthlyLines {
    const val MONTHS = 12
    fun firstDay(today: Long): Long = YearMonth.from(LocalDate.ofEpochDay(today)).minusMonths(MONTHS.toLong()).atDay(1).toEpochDay()
    fun of(today, earliestDay: Long?, workouts, reviews, days, trend: List<TrendPoint>): List<MonthFacts>
}
```

  Rules as the tests state them: cut = `TrainerRequest.firstDay(today) - 1`; each month's covered range is
  `max(monthStart, earliestDay)..min(monthEnd, cut)`, skipped when empty or when `earliestDay` is null;
  sessions `!hidden && counted`; best week = `MovementWeek.of`'s distance rule (sum of days' `distanceM`)
  over each Monday in the covered range, ties to the earlier, null when no week has one; trend at a day =
  the last `TrendPoint` whose reading is on or before it; change null unless both ends exist and are
  different points.

- [ ] **Step 4: Run, see it pass.**
- [ ] **Step 5: Commit** `feat(trainer): a line for each of the last twelve months (D89)`.

### Task 2: The request carries the months and the note (D89, D90)

**Files:** Modify `domain/trainer/TrainerRequest.kt`; Test `domain/trainer/TrainerRequestTest.kt`.

- [ ] **Step 1: Tests first.** Update `the request has room for exactly what D84 lists` to the new shape
  (`months`, `aboutMe` added to `TrainerRequest`; field lists for `MonthFacts`, `KindFacts`, `FeltCounts`).
  Add: `the months come from the record, oldest first`; `the note is sent as written, and a blank one is
  none`; `a month's line holds no weigh-in` (readings in a month; `toString()` of the months holds none of
  their kg values).
- [ ] **Step 2: See them fail.**
- [ ] **Step 3: Implement.** `of(…, earliestDay: Long? = null, aboutMe: String? = null)`; `months =
  MonthlyLines.of(today, earliestDay, workouts, reviews, days, trend)`; `aboutMe = aboutMe?.trim()?.takeIf
  { it.isNotEmpty() }`; `firstRecordDay(today) = MonthlyLines.firstDay(today)`. KDoc updated.
- [ ] **Step 4: Pass.** **Step 5: Commit** `feat(trainer): the request carries the months and the note`.

### Task 3: The prompt sends them (D89, D90)

**Files:** Modify `data/ai/TrainerPrompt.kt`; Test `data/ai/TrainerPromptTest.kt`.

- [ ] **Step 1: Tests.** Keys now `question, today, about_me, sessions, weeks, months, weight, goal, body,
  this_week, earlier_feedback`. New: `a month is sent with its days, its sessions by kind and each figure`
  (JSON shape below); `the note is sent unchanged and the instructions say the numbers win`; `the note is
  context, not a reason to stop` (system text says about_me is context for the safety rule); the D84
  "never sent" test gains a month holding the day's sleep and the weigh-ins.
- [ ] **Step 2: Fail.**
- [ ] **Step 3: Implement.** Month JSON:

```json
{"from":"2026-06-01","to":"2026-06-30","whole_month":true,"sessions":3,"minutes":120,
 "by_kind":[{"kind":"walk","sessions":2,"distance_m":6000}],"longest_minutes":50,
 "best_week":{"from":"2026-06-08","distance_m":12000},"heart_rate_average":120,
 "felt":{"easy":1,"right":2,"hard":0},"steps_a_day":7000,"weight_trend_change_kg":-0.96}
```

  System text: describe the months (oldest first, a part month's dates say which days it covers, a month
  with no sessions is a month with none) and `about_me` ("their own standing description … weigh it with
  the record; where it conflicts with the numbers, the numbers are what happened"; context, like words on
  earlier sessions, for the safety rule). No forbidden word ("sleep", "meal", "resting" …) in the text.
- [ ] **Step 4: Pass.** **Step 5: Commit** `feat(trainer): the prompt sends the months and the note`.

### Task 4: The note's store (D90)

**Files:** Create `data/trainer/AboutMeStore.kt`; modify `di/AiModule.kt`; test
`data/trainer/DataStoreAboutMeStoreTest.kt`; test fake `data/trainer/InMemoryAboutMeStore.kt`.

```kotlin
interface AboutMeStore {
    val note: Flow<String>          // "" when none
    suspend fun save(note: String)  // trimmed, at most MAX characters
    companion object { const val MAX = 1_000 }
}
class DataStoreAboutMeStore(private val store: DataStore<Preferences>) : AboutMeStore  // key "trainer_about_me"
```

- [ ] Tests: empty by default; saved and read back trimmed; cut at 1,000 characters; it sits in the same
  DataStore as the AI settings (a settings write does not disturb it). Fail → implement → pass → commit
  `feat(trainer): a place for the owner's note`.

### Task 5: Asking the trainer reads a year (D89, D90)

**Files:** Modify `data/trainer/AskTheTrainer.kt`; tests `data/trainer/AskTheTrainerTest.kt`,
`ui/screen/trainer/TrainerScreens.kt` (constructor).

- [ ] Test: `the request holds the months and the note`: a workout in June 2026, the record's earliest day
  set, a note saved → the asked request has a June line with that session and the note. Constructor gains
  `aboutMe: AboutMeStore`; reads `record.observeWorkouts(firstRecordDay, day)`, `observeDays(min(first
  summary day, firstRecordDay), day)`, `observeEarliestDay().first()`, `aboutMe.note.first()`. Fail →
  implement → pass → commit `feat(trainer): a request reads the last year`.

### Task 6: The privacy line names the note and the months (D90)

**Files:** `ui/trainer/TrainerWording.kt`; `ui/trainer/TrainerWordingTest.kt`.

- [ ] New line: "Sends to OpenAI, with your key: {first}; your note about yourself; your sessions of the last
  six weeks, with your words on them; weekly totals; a line for each month of the year before; your weight
  trend and goal rate; your age, sex and height; and the trainer's last three feedbacks. One of today's N AI
  requests." Test first, then wording, commit `feat(trainer): the privacy line names the note and the months`.

### Task 7: The About me card and page (D90)

**Files:** `ui/screen/trainer/TrainerScreen.kt`, `TrainerViewModel.kt`, new `AboutMeScreen.kt`,
`AboutMeViewModel.kt`, `ui/nav/MetaSelfNavHost.kt`, `res/values/strings.xml`, `TrainerWording.kt`;
tests `TrainerViewModelTest`, `TrainerScreenRenderTest`, new `AboutMeViewModelTest`, `AboutMeScreenRenderTest`.

- [ ] `TrainerWording.aboutMePreview(note)`: the note or "Tell the trainer about yourself — injuries, likes,
  what you're aiming for" when blank. Card under the title, labelled ABOUT ME, the preview in at most two
  lines (`maxLines = 2`, ellipsis), whole card a button → `trainer/about`.
- [ ] `AboutMeViewModel`: state `(text, loaded, saving, saved, failed)`; `edit(text)` keeps at most 1,000
  characters; `save()` writes, `saved = true` → the screen pops back; a throwing write says
  "Your note could not be saved; Recent problems says why." and logs (kind "trainer").
- [ ] `AboutMeScreen`: field (min 6 lines) with a counter "N / 1,000", Save button, the note's purpose line.
  No early return in any composable lambda.
- [ ] Tests: VM loads, edits, caps, saves, reports a failed write; the Trainer screen shows the preview or
  the invitation; render test for the page's field, counter and Save. Commit `feat(trainer): About me (D90)`.

### Task 8: The backup carries the note, format 6 (D90)

**Files:** `domain/backup/Backup.kt`, `data/backup/BackupRepository.kt`; tests `BackupCodecTest`(or the
existing codec/format test), `BackupRoundTripTest`, `BackupRestoreOrderTest`, `SettingsViewModelTest`
(constructor).

- [ ] `CURRENT_VERSION = 6`, `@SerialName("about_me") val aboutMe: String? = null`; export writes the note
  ("" when none); restore writes it inside `restoreSettings` when the file has one; a 1–5 file (null) leaves
  the phone's note alone. Tests: codec round-trip keeps it; a version-5 file still reads, with no note;
  restore writes it and a failed restore puts it back (settings snapshot, same DataStore). Commit
  `feat(backup): format 6 carries the note about yourself`.

### Task 9: A Movement session line shows its start time (D91)

**Files:** `ui/movement/MovementWeekWording.kt`, `ui/screen/movement/MovementScreen.kt`; tests
`MovementWeekWordingTest`, `MovementScreenRenderTest`.

- [ ] `detailRows(day, zone)` / `detailLines(day, zone)`; the line is name · HH:mm · distance · duration ·
  pace · heart · energy. Tests pass `ZoneOffset.UTC`; render tests match the line without depending on the
  JVM's zone. Commit `feat(movement): a session line says when it started (D91)`.

### Task 10: "Is it one of these?" (D91)

**Files:** `domain/movement/WorkoutFile.kt` (`NoMatch(file, sameKind = emptyList())`),
`domain/movement/WorkoutFileMatch.kt` (`sameKind(file, stored, zone)`, public `kindOf`),
`data/health/WorkoutFiles.kt` (NoMatch filled with them), `ui/movement/WorkoutFileWording.kt`,
`ui/screen/movement/MovementScreen.kt`, `MovementViewModel.kt` (`chooseForFile` accepts NoMatch),
`res/values/strings.xml`; tests `WorkoutFileMatchTest`, `ImportWorkoutFileTest`, `WorkoutFileWordingTest`,
`MovementViewModelTest`, `MovementScreenRenderTest`.

- [ ] `sameKind`: `candidates(stored)` whose kind is `kindOf(file.sport)` and whose day is the file's wall-clock
  day, by start. Line: "No session matches this file exactly (Thu 3 Sep 10:30). Is it one of these?" with
  one button per session ("Walking · 10:00 · 40 min"), then "Or, if it is a session the record does not
  have:" and **Add it as a workout**. With none, as before. Choosing fills as D82's choose does.
- [ ] Tests first for each layer; commit `feat(movement): a file that matches nothing offers the day's sessions of its kind (D91)`.

### Task 11: 0.65.0 and the full checks

- [ ] `versionCode = 121`, `versionName = "0.65.0"`; commit `0.65.0: the trainer knows you (D89–D91)`.
- [ ] Separately: `:app:testDebugUnitTest` (only the ten known database classes skip), `:app:lintDebug`,
  `:app:assembleDebug`. No release build.

---

## Design questions settled here

1. **Which record start.** "The stored record begins" is `MovementRecord.observeEarliestDay()` — the first
   day holding a daily summary or a workout, hidden or not (D83's rule for how far back Movement steps).
   Weigh-ins do not start it. With nothing stored, no monthly lines are sent.
2. **The month the record begins in** is sent from that day, marked as a part month, rather than as a whole
   month that looks empty at its start. The spec names only the cut month as a part month; this applies the
   same honesty to the other end. Every month is sent with its first and last date, so the model never has
   to guess which days a line covers.
3. **The best week's distance** is D74's week distance — the sum of the days' de-duplicated totals, as the
   six weekly totals and the Movement screen count it — not the sum of the sessions' distances. A week
   whose Monday is in the covered range may run past the month's end (and past the cut); that is what
   "whose Monday falls in the month" says. Ties go to the earlier week. (Changed during review: the
   week's days are counted only up to the cut before the 42-day detail, so no day is counted twice; a
   week whose distance is zero is no best week, and none is sent.)
4. **Trend at a day** is the smoothed line after the last reading on or before that day. The change is sent
   only when both ends have one and the month holds a reading of its own (otherwise the change would be a
   fabricated 0.0). It is rounded to two decimals, as the weekly change is. (Changed during review: the
   trend at each end must rest on a weigh-in at most 14 days before it, or on it; otherwise the change
   would be close to the difference of two raw weigh-ins, and none is sent. Added to D89 in the spec.)
5. **Kinds order** in a line: most sessions first, ties in the kinds' own order.
6. **Felt counts** are null when no session of the month has a felt review, so "no reviews" is not sent as
   "felt nothing".
7. **Steps a day** come from the daily summaries' steps (whatever source, corrected included), over the
   covered days that have a count.
8. **The note's store** is its own small interface over the same preferences DataStore as the AI settings
   (key `trainer_about_me`), so the eight `AiSettingsStore` test fakes need no change; the settings snapshot
   already covers it, being the same file.
9. **Restore and the note:** a format-6 file writes its note (an empty one clears the phone's); a format
   1–5 file has none and leaves the phone's note as it is — the same rule the AI settings follow, since the
   note is a setting, not part of the replaced record.
10. **The note and the safety rule:** a standing note mentioning an old injury is context, like words on
    earlier sessions; it does not trigger "stop and see a doctor".
11. **The privacy line** names the monthly lines as well as the note: it has always listed everything one
    request holds (design question 20 of D84), and the monthly lines are new in it too.
12. **Saving the note** trims it and keeps at most 1,000 characters; the field stops accepting input at
    1,000 and shows a counter. Save returns to the Trainer screen.
13. **D91's same-kind sessions** are the ones a file may fill (`WorkoutFileMatch.candidates`: visible
    synced sessions, or a typed one a file made) on the file's wall-clock day. A workout the owner typed by
    hand is his own and is not offered, as D82 already rules. The kind is the file's sport, read as
    "Add it as a workout" reads it.
14. **The start time** on a Movement session line is the phone's zone, 24-hour, as every other time the
    screen shows.
15. **The problem log's line** for a no-match file is unchanged; the choice made afterwards is logged as
    D82's choose already is.
16. **(Added during review.)** D91's same-kind sessions are looked for on the days of both readings of
    the file's start, as the match itself does. The header's time is the file's wall clock, which is the
    phone's-zone reading the offered sessions' times and "Add it as a workout" use, so they compare
    directly. A choice is checked again at the tap: a session hidden or gone since the offer is not
    filled, and the offer is made again as it now stands. The note's 1,000-character limit never splits
    a character outside the basic plane.
