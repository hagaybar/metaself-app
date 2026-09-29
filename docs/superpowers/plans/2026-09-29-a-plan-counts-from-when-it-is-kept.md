# A plan counts from when it is kept — Implementation Plan (D105)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (or
> superpowers:subagent-driven-development) to implement this plan task by task. Steps use checkbox
> (`- [ ]`) syntax for tracking.

**Goal:** D105. A weekly plan's sessions are ticked only by sessions that start after the plan (the first
version of its chain) was kept. A session at least as long as planned ticks; one at least half as long is a
*candidate* the owner confirms on the plan card ("Walking, 20 min — count it for this?" · Yes · No); one
under half, a candidate answered No, and a candidate left open are *attempts*, which tick nothing. Only the
owner's answers are stored — Room version 11 adds `plan_confirmations`; the backup becomes format 9. The
adjust request and the last evaluation (D93) carry, week by week, each planned session's outcome and the
attempts; the instructions say attempts are effort and a signal, never a failure. Version 0.69.0.

**Decision:** the owner's, 2026-09-29 — `docs/superpowers/specs/2026-09-29-a-plan-counts-from-when-it-is-kept-design.md`
(D105 amends D95, D93 and D97). Background: `2026-09-29-the-trainer-evaluates-and-plans-weeks-design.md`
and its plan, whose conventions this one copies.

**Architecture:**

```
domain/trainer/PlanProgress.kt        Tick gains `short` and `candidate`; WeekProgress gains `attempts`;
                                      PlanCounting (chain id, from, answers); PlanProgress.of takes it;
                                      outcomes for the trainer; Programmes.rootOf                        JUnit 5
domain/trainer/Programme.kt           PlanConfirmation; LastEvaluation gains `weeks` (outcomes)          JUnit 5
domain/trainer/TrainerHome.kt         PlanCard.of / TrainerHome.of take the counting                     JUnit 5
domain/trainer/TrainerRequest.kt      Adjust gains `howItWent`                                           JUnit 5 (pinned)
data/ai/TrainerPrompt.kt              how_it_went sent with adjust and last_evaluation; instructions     JUnit 5 (pinned)
data/trainer/TrainerEntities.kt, TrainerDao.kt, ProgrammeStore.kt   + plan_confirmations            JUnit 5 fake + HealthRecordStoreTest (CI)
data/day/ConfirmationMigration.kt     MIGRATION_10_11, verbatim from 11.json    tools/check-migration-10-11.py + MigrationTest (CI)
data/trainer/AskTheTrainer.kt         counting from the chain root with answers; answerCandidate;
                                      outcomes into adjust and the last evaluation                       JUnit 5 (fakes)
domain/backup/Backup.kt, data/backup/BackupConfirmations.kt, BackupRepository.kt   format 9         JUnit 5 + BackupRoundTripTest (CI)
ui/trainer/ProgrammeWording.kt, res/values/strings.xml   the candidate line, Yes, No                    JUnit 5
ui/screen/trainer/TrainerViewModel.kt, TrainerScreen.kt, ui/nav/MetaSelfNavHost.kt   observe answers;
                                      the candidate row; guarded answer                                  JUnit 5 VM + Robolectric render
```

**Red lines (stop and report if crossed):**

- **Ticks are still counted afresh from the record (D95)**; only the owner's answers are stored. No tick,
  candidate or attempt is ever written.
- **Only minutes decide** a tick, a candidate or an attempt. Distance, effort and heart rate never do.
- **What leaves the phone grows by exactly D105's list** — each planned session's outcome and the
  attempts, inside the adjust question and the last evaluation — and only on a tap. `TrainerPromptTest`'s
  "nothing else is sent" key pins are updated deliberately, key by key. The plan and feedback questions
  do not change.
- **The migration is the exported schema's own SQL**, character for character, checked by
  `tools/check-migration-10-11.py` here and `MigrationTest` in CI. `app/schemas` gains `11.json` only.
- **D8:** the answer write is guarded; a failure is said (`ActionRefused.NOTHING_CHANGED`) and logged;
  nothing throws upwards. A second tap while the first is being written does nothing.
- **CI-only database tests go inside `HealthRecordStoreTest`, `MigrationTest` or `BackupRoundTripTest`**,
  so the local skip list stays at exactly the ten classes `CLAUDE.md` names.
- **No early return out of an inline composable.** Touch targets of the Yes and No buttons are at least
  48 dp.
- **Anonymisation:** every figure is invented and round, built on `TEST_EPOCH_DAY` (Thursday
  3 September 2026; its Monday is 31 August). No real session length, day or story.
- **Never `git add -A`; never stage `app/.settings/*`, `build/` or `tools/__pycache__`; never bare
  `./gradlew`; never pipe a build whose result is reported.** No push. The release APK is the controller's.

## Design questions the spec leaves open — settled here

1. **Which planned session a session goes to.** Within its week, in start order, among the planned
   sessions of its kind that are not yet ticked: the first, in plan order, that it fills **in full**;
   failing that, the first it is **at least half** of and that does not already hold an open candidate;
   failing that, it is an attempt. "That it could fill" is read this way so that a session as long as a
   later, shorter planned session ticks that one rather than being asked about an earlier, longer one.
   A full session may take a place that holds an open candidate — the candidate becomes an attempt (D105).
2. **A second short session for a place already holding an open candidate** passes over it (question 1).
   If no other place fits, it is an attempt; once the first candidate is answered No, the next count makes
   the second the candidate. So one question is on screen per planned session.
3. **Answers are looked up by workout** in the chain's answers: Yes makes the candidate a tick marked
   `short` (it holds its place like any tick); No makes it an attempt against that planned session. A
   session with a stored Yes is placed by the half rule **before** the full rule — onto the first unticked
   planned session of its kind it is at least half of, pushing out an open candidate there — so an
   adjustment that adds a shorter planned session cannot move it off the one it was confirmed for, and a
   late-syncing earlier session cannot take its place. It is `short` only when under the planned minutes.
4. **An attempt's "nearest" planned session** (for the trainer): the place it was a candidate for, else
   the unticked planned session of its kind whose minutes are closest to its own, first in plan order on a
   tie. A session of a kind with every place already ticked is **not** an attempt — it is an extra, and it
   is already in the record sent.
5. **"After the plan was kept"**: `startedAtMillis >= fromMillis`, where `fromMillis` is the chain's first
   version's `createdAtMillis`. The chain root is found by following `replacesId` back, bounded as
   `evaluationOf` is.
6. **Only this week's candidates are asked.** The card shows this week only (D95); once its week is over,
   an unanswered candidate stays an attempt. The ended card asks nothing.
7. **Asked once, double-tap safe.** `answerCandidate` re-reads the running card and refuses (throws) unless
   the workout is an open candidate this week; the store inserts with IGNORE, so the first answer stands.
   The view model ignores a tap on a workout whose answer is being written.
8. **The line under a confirmed short tick** is the ordinary tick line ("Done Mon · Walking, 20 min",
   invented); the card does not single it out. Done-counts count it (D105).
9. **The backup** writes `plan_confirmations` as a top-level list, each naming its workout by position in
   the file's `workouts` from 1, as a session split does (D92); an answer whose workout is not in the file
   is not written. A restore replaces them. The restore confirmation does not name them (they are the
   owner's answers about sessions it already counts).
10. **Outcomes sent**: `how_it_went`, one entry per week that had begun (adjust: every week up to and
    including this one), each `{ week, sessions: [{kind, planned_minutes, outcome, minutes_done}],
    attempts: [{kind, minutes, planned_minutes}] }`; outcome `done`, `done_short_confirmed` or `not_done`;
    `minutes_done` null when not done. An open candidate is sent among the attempts.

## The shared box — read before any build

`free -m` before every Gradle command; under about 4000 MB available, wait (60 s) and check again. One
build at a time (`~/bin/gradlew-safe` holds the lock). Never `gradlew --stop`.

```
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "<pattern>" > <log> 2>&1; echo "exit $?"
```

Read results from the log and `app/build/test-results/testDebugUnitTest/*.xml`, never from a pipe.

All paths are under `app/src/main/java/com/metaself/app/` (main) or `app/src/test/java/com/metaself/app/`
(test) unless written in full.

---

### Task 1: Counting from the keep, candidates and attempts — pure

**Files:** Modify `domain/trainer/PlanProgress.kt`, `domain/trainer/Programme.kt`, `domain/trainer/TrainerHome.kt`;
tests `domain/trainer/PlanProgressTest.kt`, `TrainerHomeTest.kt`, and every caller of `PlanProgress.of` /
`PlanCard.of` in the tests (compile only).

- [ ] **Step 1: Failing tests** in `PlanProgressTest` (plan: week 1 walk 30, walk 40, run 20; all invented):
  - a session starting before `fromMillis` ticks nothing and is no attempt;
  - a 30-min walk ticks walk 30; a 40-min walk ticks walk 30 first in plan order, the next ticks walk 40;
  - a 20-min walk against walk 40 only is a candidate (`tick.candidate`), not ticked, `done` 0, `next` still it;
  - answered Yes → ticked, `short` true, `done` 1; answered No → an attempt against walk 40;
  - under half (a 10-min walk against walk 30) → an attempt, nearest walk 30;
  - an open candidate displaced by a later full session → the place ticked, the candidate an attempt;
  - a second short session passes over a place holding an open candidate (question 2);
  - a walk after every walk place is ticked is neither tick nor attempt;
  - `Programmes.rootOf` follows `replacesId` back to the first version, and a self-reference ends;
  - `outcomes()` gives done / done_short_confirmed / not_done with minutes done, and the attempts with
    open candidates among them.
- [ ] **Step 2: Run, expect compile failure.**
- [ ] **Step 3: Implement.** Sketch:

```kotlin
data class Tick(val planned: PlannedSession, val by: Workout?, val short: Boolean = false, val candidate: Workout? = null)
data class Attempt(val workout: Workout, val against: PlannedSession)
data class WeekProgress(val index: Int, val monday: Long, val focus: String, val ticks: List<Tick>, val attempts: List<Attempt> = emptyList())

/** D105: counted from [fromMillis] (the chain's first version kept); [answers] by workout id, true = Yes. */
data class PlanCounting(val chainId: Long, val fromMillis: Long, val answers: Map<Long, Boolean>)

// in of(): filter startedAtMillis >= counting.fromMillis; per week, mutable slots; for each session:
//   open = unticked places of its kind; none -> skip (an extra)
//   full = open.first { minutes >= planned }                       -> tick; any open candidate there -> attempt
//   half = open.first { 2 * minutes >= planned && candidate == null }
//          answers[id] == true -> tick(short) · false -> attempt(against) · null -> candidate
//   else -> attempt(nearest(open))
```

  `PlanCard.of(running, workouts, today, counting)` and `TrainerHome.of(..., counting: PlanCounting? = null)`
  pass it through (no counting → no card, as no running plan). `PlanConfirmation(programmeId, workoutId,
  confirmed, answeredAtMillis)` and the outcome types (`PlannedOutcome`, `SessionOutcome`, `AttemptFacts`,
  `WeekOutcome`; `WeekProgress.outcome()`) go in `Programme.kt`/`PlanProgress.kt`; `LastEvaluation` gains
  `weeks: List<WeekOutcome>`.
- [ ] **Step 4: Update callers in tests** to pass a `PlanCounting` (from 0, no answers, unless the test is
  about it). Existing fixtures whose sessions are shorter than their planned session are lengthened, not
  re-asserted.
- [ ] **Step 5: Run** `PlanProgressTest`, `TrainerHomeTest` → pass. **Commit.**

### Task 2: The answers' table, version 11

**Files:** `data/trainer/TrainerEntities.kt` (`PlanConfirmationEntity`, table `plan_confirmations`,
primary key `(programmeId, workoutId)`, no foreign key), `TrainerDao.kt` (insert IGNORE, observe and read by
programme, all / delete / insert all for the backup), `data/day/MetaSelfDatabase.kt` (version 11, entity),
`data/day/ConfirmationMigration.kt` (`MIGRATION_10_11`), `di/DataModule.kt`, `data/trainer/ProgrammeStore.kt`
(`observeConfirmations(programmeId)`, `confirmations(programmeId)`, `confirm(PlanConfirmation)`),
`FakeProgrammeStore`, `tools/check-migration-10-11.py`, `tools/README.md`; tests in `MigrationTest` and
`HealthRecordStoreTest` (CI).

- [ ] **Step 1:** entity, DAO, version 11; compile once to export `app/schemas/.../11.json`.
- [ ] **Step 2:** `MIGRATION_10_11` with `11.json`'s `createSql` verbatim; register it.
- [ ] **Step 3:** `tools/check-migration-10-11.py`, the 9→10 script's pattern: statements equal `11.json`'s,
  old tables untouched, new shape matches, a second answer for the same pair is refused by the key.
- [ ] **Step 4:** `MigrationTest`: 10 → 11 validates; `HealthRecordStoreTest`: confirm, observe by programme,
  a second confirm for the pair leaves the first.
- [ ] **Step 5:** run the script and compile; **commit.**

### Task 3: Asking — the counting, the answer, and the outcomes

**Files:** `data/trainer/AskTheTrainer.kt`, `domain/trainer/TrainerRequest.kt` (Adjust gains
`howItWent: List<WeekOutcome>`), `AskTheTrainerTest`, `TrainerRequestTest`.

- [ ] **Step 1: Failing tests:** a session before the keep does not tick (card, feedback's planned tick,
  adjust's counts); an adjusted version counts from its first version's keep and sees its answers;
  `answerCandidate` stores Yes under the chain's first id and the card ticks; refused (throws) for a
  workout that is not an open candidate; adjust and evaluate send `howItWent` / `last.weeks` with outcomes
  and attempts; `doneByWeek` counts confirmed short ticks.
- [ ] **Step 2: Implement.** `card()` and `lastEvaluation()` build `PlanCounting(root.id, root.createdAtMillis,
  programmes.confirmations(root.id))`. Existing fixtures that expected sessions before `offered()`'s
  creation to tick get an earlier `createdAtMillis`.
- [ ] **Step 3: Run; commit.**

### Task 4: What the trainer is told

**Files:** `data/ai/TrainerPrompt.kt`, `TrainerPromptTest`.

- [ ] **Step 1: Failing tests:** the adjust question's keys gain `how_it_went`; `last_evaluation`'s keys gain
  `how_it_went`; the entry and item keys are pinned exactly; outcomes are the three words; the ADJUST and
  EVALUATE instructions say attempts are effort to recognise and a signal for the next plan, never a failure.
- [ ] **Step 2: Implement; run; commit.**

### Task 5: The plan card asks

**Files:** `ui/trainer/ProgrammeWording.kt` (`candidateLine(workout)` = "Walking, 20 min — count it for
this?"), `res/values/strings.xml` (`plan_candidate_yes` "Yes", `plan_candidate_no` "No"),
`ui/screen/trainer/TrainerViewModel.kt` (AskTheTrainer injected; the plan flow finds the chain root and
combines the record with `observeConfirmations(root.id)`; `answer(workoutId, confirmed)` guarded, an
in-flight set, `refused`), `TrainerScreen.kt` (under a planned session with a candidate: the line, then
Yes and No at 48 dp, disabled while in flight; the refusal sentence under the card), `MetaSelfNavHost.kt`;
tests `ProgrammeWordingTest`, `TrainerViewModelTest`, `TrainerScreenRenderTest`.

- [ ] **Step 1: Failing tests:** the wording; the VM shows a candidate, a Yes ticks it at once, a failing
  store says `NOTHING_CHANGED` and logs, a second tap in flight writes once; the render shows the line and
  both buttons, and pressing Yes calls back with the workout id.
- [ ] **Step 2: Implement; run; commit.**

### Task 6: The backup, format 9

**Files:** `domain/backup/Backup.kt` (`CURRENT_VERSION = 9`, `plan_confirmations`, `BackupPlanConfirmation`),
`data/backup/BackupConfirmations.kt` (to file by position; rows by kept position), `BackupRepository.kt`
(export; restore deletes and inserts in the one transaction), tests `BackupConfirmationsTest`,
`BackupCodecTest` (a format-8 file still reads, with none), `BackupRoundTripTest` (CI), `BackupRestoreOrderTest`
if it pins the deletes.

- [ ] **Step 1: Failing tests; Step 2: implement; Step 3: run; commit.**

### Task 7: Privacy page, version, the suite and the build

- [ ] `privacy.html`: the adjust and evaluation sentences add "and, for each planned session, whether it was
  done, done shorter with your say-so, or not done, and the shorter sessions that did not count".
- [ ] `app/build.gradle.kts`: `versionCode = 125`, `versionName = "0.69.0"`.
- [ ] Full `:app:testDebugUnitTest` (skips: exactly the ten), `:app:lintDebug`, `:app:assembleDebug`,
  `python3 tools/check-migration-10-11.py`. Anonymisation read of the whole diff. **Commit** `0.69.0: a plan
  counts from when it is kept (D105)`.

## What this plan does not build

- Judging a session by distance, pace or heart-rate zone.
- Moving a session to another planned slot by hand.
- Asking about a past week's unanswered candidate, or changing an answer once given.
