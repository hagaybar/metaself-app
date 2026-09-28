# The trainer, before and after a session — Implementation Plan (D84–D88)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** D84–D88. Movement's top bar gains **Trainer**; an open day's session line gains **How did it
go?**. The Trainer screen shows the newest unreviewed session of the last three days, **Plan my next
session** with the kept plan, and earlier reviewed sessions. The plan form (four single-choice rows and
optional words) asks the model for a suggestion (title, 3–6 steps, why) that can be kept; the review
(figures with sources, matched plan, Easy/Right/Hard, words) is saved and may ask for feedback
(headline and four parts). Every request is built fresh from the stored record, only when the owner
taps; every answer is labelled "advice, not a measurement". Room version 8 adds `trainer_plans` and
`trainer_reviews`; the backup becomes format 5.

**Decision:** the owner's, 2026-09-28 — `docs/superpowers/specs/2026-09-28-trainer-before-and-after-a-session-design.md`
(D84 amends D16; D88 amends D71's backup).

**Architecture:**

```
domain/movement/Workout.kt              + maxHeartRate, zoneSeconds, zoneMaxSource (read, D70)  JUnit 5
domain/health/HeartRateZones.kt         + zonesFromText("600,1200,…")                            JUnit 5
data/health/MovementRecord.kt           maps the three                                           JUnit 5
domain/trainer/Trainer.kt               the vocabulary: form answers, SessionPlan, Feedback,
                                        TrainerPlan, TrainerReview, TrainerReply                 compiles only
domain/trainer/PlanMatch.kt             kept plan offered (7 days); matched to a session         JUnit 5
domain/trainer/TrainerRequest.kt        pure: everything one request holds (D84); the Trainer port JUnit 5
data/ai/TrainerPrompt.kt                pure: the request body, system prompt, two schemas        JUnit 5
data/ai/TrainerResponse.kt              pure: reply → SessionPlan / Feedback; encode for storage  JUnit 5
data/ai/OpenAiTrainer.kt                the thin caller over OpenAiCall                           JUnit 5 + MockWebServer
data/trainer/TrainerEntities.kt, TrainerDao.kt                                                   CI (Room)
data/day/TrainerMigration.kt            MIGRATION_7_8, verbatim from 8.json                      tools/check-migration-7-8.py + MigrationTest (CI)
data/trainer/TrainerStore.kt            port + RoomTrainerStore + mapping                        JUnit 5 (mapping) + HealthRecordStoreTest (CI)
domain/backup/Backup.kt + data/backup/BackupRepository.kt   format 5                             JUnit 5 codec + order; BackupRoundTripTest (CI)
data/trainer/AskTheTrainer.kt           gathers the record, calls, stores                        JUnit 5 (fakes)
domain/trainer/TrainerHome.kt           pure: the Trainer screen's three parts                   JUnit 5
ui/trainer/TrainerWording.kt            every sentence the three screens build                   JUnit 5
ui/screen/trainer/*                     Trainer, plan, review screens and view models            JUnit 5 VM + Robolectric render
ui/screen/movement/*, ui/nav/MetaSelfNavHost.kt   the two entry points and three destinations
```

**Tech Stack:** Kotlin, Room (schema v8, hand-written migration), kotlinx.serialization (JSON in and
out), OkHttp via the shared `OpenAiCall`, Compose (Material 3, `FilterChip` + `FlowRow` as the workout
sheet uses them), Hilt, JUnit 5 + Truth, `okhttp3.mockwebserver`, Robolectric (JUnit 4) for render and
Room tests. No new dependency.

**Red lines (stop and report if crossed):**

- **Only what D84 lists leaves the phone, and only on a tap.** No meal, nothing eaten, no sleep, no
  raw reading, no single weigh-in (not the profile's `weightKg`), no target weight, no owner name, no
  app or device name — so no workout `title` and no `origin`. `TrainerPromptTest` pins the shape and
  fails on a new field. Nothing is sent from `init`, a flow, or a background job.
- **An AI answer is never shown as a measurement (D4).** Every suggestion is under "Suggested by the AI
  trainer · advice, not a measurement"; every feedback under "From the AI trainer · advice, not a
  measurement". The felt effort never overwrites a workout's `effort` (D77).
- **The rhythm is counted on the phone** (`TrainerRequest.thisWeek`) and handed to the model; the system
  prompt tells it to use that count and never count for itself.
- **The migration is the exported schema's own SQL**, character for character, checked by
  `tools/check-migration-7-8.py` here and `MigrationTest` in CI. `app/schemas` gains `8.json` only.
- **D8:** a failed call leaves the form or the words as they were and says why in the meal estimator's
  sentence shapes; a failed write is said (`ActionRefused`) and logged; nothing throws upwards. The
  problem log gets a failure's kind and HTTP status, **never the owner's words or the model's answer**.
- **CI-only database tests go inside `HealthRecordStoreTest`, `MigrationTest` or `BackupRoundTripTest`**
  so the local skip list stays at exactly the ten classes `CLAUDE.md` names.
- **No early return out of an inline composable** (`return@Column` and kin): Compose compiler 1.5.9
  miscompiles it; `InlineComposableReturnGuardTest` fails the build. Branch with `when`/`if` instead.
- **Anonymisation:** every figure and date in a test or a comment is invented and round, built on
  `TEST_EPOCH_DAY` (Thursday 3 September 2026) and `aProfile()`. Nothing is copied from the design
  mock-ups' figures (`build/trainer-canvas`, private): only their wording. Owner's words in fixtures are
  invented and say so.
- **Never `git add -A`; never stage `app/.settings/*` or `tools/__pycache__`; never bare `./gradlew`;
  never pipe a build whose result is reported.** No push. The release APK is the controller's
  (`~/bin/ms-release`).

## Design questions the spec does not answer — settled here

1. **Every suggestion that arrives is stored** (`kept = false`); **Keep this plan** marks it kept and
   unmarks any other, in one transaction. D88's `kept` column only means something if unkept plans are
   rows too. **Ask again** leaves the unkept row behind (a few hundred bytes).
2. **"Kept within seven days"** is measured from the plan's `createdAtMillis`: Keep follows the answer
   by seconds, and D88 stores no second time. Offered on the Trainer screen while `now − created ≤ 7
   days`; matched to a session when `0 ≤ session start − created ≤ 7 days`. A plan made after a session
   started never matches it.
3. **"The suggestion as returned (JSON text)" and "feedback (JSON text)"** are stored in the reply's own
   JSON shape (the same field names as the reply schema), written by the app from the parsed answer
   (`TrainerResponse.encodePlan` / `encodeFeedback`). So exactly what was read and shown is what is
   kept, and it reads back with the same parser. A stored text that no longer parses reads as no plan /
   no feedback, never as a crash.
4. **The form's answers are four columns plus words** (`activity`, `minutes`, `feeling`, `wish`,
   `words`); "60 min or more" is stored as 60 and sent with `"or_more": true`.
5. **`felt` is nullable.** A review may be words alone or a felt effort alone; **Just save** and **Save
   and get feedback** are enabled once either is given.
6. **No foreign key from `trainer_reviews` to `workouts`.** A sync that drops a session deleted in the
   writing app must not silently delete the owner's words. A review whose session is gone is shown
   nowhere, sent nowhere, and not written to the backup (it rides inside its workout there — item 8).
   (Superseded during build: such a review IS written to the backup, on its own, so a restore does not
   lose the words; it is restored under workout id -1, -2, … in file order — `BackupReviews`.)
7. **The review's row on Movement says one of three things**: no review → "How did it go?"; saved
   without feedback → "Get feedback" (D87's later offer); with feedback → "See feedback". All open the
   review screen; a review with feedback opens on the feedback. Changing words after feedback is not
   offered in this version.
8. **Backup format 5.** A review is written **inside its workout** (`"trainer_review": {…}`), so it
   needs no workout id in the file; plans are a top-level `"trainer_plans"` list with their ids, which
   a review's `plan_id` names. On restore, workouts are inserted with ids 1…n in file order (the table
   was just emptied), so each review is attached to the id its workout received; plans keep their ids;
   a review naming a plan the file does not hold gets no plan. A version 1–4 file restores with no
   plans and no reviews (a restore replaces, as before).
9. **"Earlier sessions"** lists every session with a review, newest first, hidden ones left out. Its
   line: "Felt right · feedback read · as planned" — felt when given; "no words added" when the words
   are blank; "feedback read" when feedback is stored (it is shown the moment it arrives), else "no
   feedback yet"; and whether it followed its plan, **as the trainer judged it**: the feedback reply
   carries a required `plan_followed` of `yes | partly | no | no_plan`, worded "as planned", "partly as
   planned", "not as planned", or nothing. The phone never compares a session with a plan itself.
10. **"The last three days"** are today, yesterday and the day before. The waiting card is the newest
    visible, counted session (D81) in them with no review.
11. **Sessions sent** are the visible, counted sessions (the ones the Movement week counts) of the 42
    days ending today, oldest first. **Six weekly totals** are the six Monday-based weeks ending with
    this one, newest first, each from `MovementWeek.of` with no meals: distance, movement kcal a day,
    and sessions (`workoutCount + walkCount`); this week marked "so far".
12. **Weight** is `WeightTrend.of(readings)`'s last point (with its date) and `MeasuredRate.of(trend,
    today)`'s `kgPerWeek` and `spanDays` — the weight screen's own figures; either may be absent.
    **Goal** is direction and weekly rate only (no target). **Body** is age (current year − birth year),
    sex, height. No profile → the three are null and said as unknown.
13. **This week's rhythm** sent: sessions this week so far, and days left in it (Sunday − today).
14. **The last three feedback texts** are the three newest stored by `feedbackAtMillis`, the session
    being reviewed excluded, sent as their five parts.
15. **Sources in words the model reads** (no app or device name): distance `synced` (Health Connect's
    total over the session), `file`, `typed`; energy `band`, `estimated` (MET), `typed`, `file`; heart
    rate `from readings` (D70); zones against a maximum `estimated` (220 − age) or `observed`; steps
    `file`.
16. **Failures are worded as the meal estimator words them**, minus "type the numbers": a trainer
    sentence per `EstimateResult` (Task 12). A failed feedback call after the words were saved says
    "Your words are saved." first.
17. **The model's language:** the system prompt asks for plain English, second person, short parts.
18. **Step minutes** are shown "0–8" (en dash). A plan's steps must start at 0 or later, each `to >
    from`, and not go backwards; steps may leave gaps. Fewer than three or more than six steps is
    unreadable.
19. **Plan screen, opened from the kept-plan card:** shows the kept plan with its advice line, "Ask
    again" (to the form, empty) and no Keep button (it is kept).
20. **Privacy line under each ask button** (the design's wording): "Sends these answers, your last six
    weeks of sessions and your weight trend to OpenAI with your key. One of today's 30 AI requests." —
    the number is the daily ceiling from settings. (The settings page already names OpenAI.)
    (Superseded during build: the line lists everything D84 sends — `TrainerWording.privacyPlan` /
    `privacyReview` hold the shipped wording.)

## The shared box — read before any build

Another project builds on this machine. `~/bin/gradlew-safe` and `~/bin/ms-release` share one lock; a
finished build's daemon lingers up to ten minutes. **Before every Gradle command run `free -m`;** under
about 4000 MB available (6000 MB before `ms-release`), wait and check again. Never `gradlew --stop`.
One build at a time; subagents build one at a time.

```
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "<pattern>" > /tmp/ms-trainer.log 2>&1; echo "exit $?"
```

Read the result from the log and from `app/build/test-results/testDebugUnitTest/*.xml`, never from a
pipe. SQLite classes skip locally (the ten in `CLAUDE.md`); report them as skipped, not passed.

All paths below are under `app/src/main/java/com/metaself/app/` (main) or
`app/src/test/java/com/metaself/app/` (test) unless written in full.

---

### Task 1: A session's heart-rate detail reaches the domain

D84 sends each session's highest heart rate and minutes per zone. They are stored (`WorkoutEntity`)
but the domain `Workout` has only the average.

**Files:**
- Modify: `domain/health/HeartRateZones.kt` (+ `zonesFromText`)
- Modify: `domain/movement/Workout.kt` (three defaulted fields)
- Modify: `data/health/MovementRecord.kt` (`toWorkout` maps them)
- Test: `domain/health/HeartRateZonesTest.kt`, `data/health/MovementRecordTest.kt`

- [ ] **Step 1: Failing tests.** Append to `HeartRateZonesTest`:

```kotlin
    /** The stored form is five comma-separated second counts, zone 1 to 5 (invented figures). */
    @Test
    fun `stored zones read back as five numbers, and anything else as none`() {
        assertThat(HeartRateZones.zonesFromText("600,1200,600,0,0")).containsExactly(600, 1200, 600, 0, 0).inOrder()
        assertThat(HeartRateZones.zonesFromText(HeartRateZones.Figures(110, 130, listOf(1, 2, 3, 4, 5)).zonesAsText()))
            .containsExactly(1, 2, 3, 4, 5).inOrder()
        assertThat(HeartRateZones.zonesFromText(null)).isNull()
        assertThat(HeartRateZones.zonesFromText("600,1200")).isNull()
        assertThat(HeartRateZones.zonesFromText("600,x,600,0,0")).isNull()
        assertThat(HeartRateZones.zonesFromText("600,-1,600,0,0")).isNull()
    }
```

Append to `MovementRecordTest` (invented figures):

```kotlin
    /** D84 sends these; D70 worked them out from the readings. */
    @Test
    fun `a stored workout keeps its highest heart rate and its zones`() {
        val stored = WorkoutEntity(
            epochDay = 20_699, startedAtMillis = 0, durationMinutes = 40, kind = "WALK", title = null,
            distanceM = 3_000, energyKcal = 200, energySource = "BAND", effort = null, source = "SYNCED",
            origin = "com.example.band", originId = "w-1", note = null,
            avgHeartRate = 110, maxHeartRate = 130, zoneSeconds = "600,1200,600,0,0", zoneMaxSource = "ESTIMATED",
        )

        val workout = stored.toWorkout()

        assertThat(workout.maxHeartRate).isEqualTo(130)
        assertThat(workout.zoneSeconds).containsExactly(600, 1200, 600, 0, 0).inOrder()
        assertThat(workout.zoneMaxSource).isEqualTo("ESTIMATED")
    }
```

- [ ] **Step 2: Run, expect compile failure:**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.health.HeartRateZonesTest" --tests "com.metaself.app.data.health.MovementRecordTest" > /tmp/ms-trainer.log 2>&1; echo "exit $?"`

- [ ] **Step 3: Implement.** In `HeartRateZones`:

```kotlin
    /**
     * The stored form ([Figures.zonesAsText]) read back: five whole, non-negative second counts, zone
     * 1 to 5; anything else is none rather than a guess.
     */
    fun zonesFromText(text: String?): List<Int>? {
        val parts = text?.split(",")?.map { it.trim().toIntOrNull() } ?: return null
        if (parts.size != 5 || parts.any { it == null || it < 0 }) return null
        return parts.map { it!! }
    }
```

In `Workout`, after `stepsSource`:

```kotlin
    /** The session's highest heart rate, worked out from its readings (D70); null with none. */
    val maxHeartRate: Int? = null,
    /** Seconds in heart-rate zones 1 to 5 (D70); null with no readings. */
    val zoneSeconds: List<Int>? = null,
    /** What the zones were measured against, as stored: ESTIMATED (220 − age) until an observed maximum exists. */
    val zoneMaxSource: String? = null,
```

In `toWorkout`, after `stepsSource = …`:

```kotlin
    maxHeartRate = maxHeartRate,
    zoneSeconds = HeartRateZones.zonesFromText(zoneSeconds),
    zoneMaxSource = zoneMaxSource,
```

(`toTypedEntity` is unchanged: a typed workout has no readings.)

- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit** the five files: `feat: a workout carries its highest heart rate and zones (D84)`.

---

### Task 2: The trainer's vocabulary, and which kept plan counts

**Files:**
- Create: `domain/trainer/Trainer.kt`, `domain/trainer/PlanMatch.kt`
- Test: `domain/trainer/PlanMatchTest.kt`

- [ ] **Step 1: Write `domain/trainer/Trainer.kt`** (types only; it compiles or the later tasks do not):

```kotlin
package com.metaself.app.domain.trainer

import com.metaself.app.domain.ai.EstimateResult

/** D86's first row. Stored by name, never by ordinal. */
enum class PlanActivity { TREADMILL_WALK, OUTDOOR_WALK, RUN, SOMETHING_ELSE }

/** D86's second row. The last is "60 min or more", stored as 60. */
enum class TimeAvailable(val minutes: Int) {
    MIN_20(20), MIN_30(30), MIN_45(45), MIN_60_OR_MORE(60);

    val orMore: Boolean get() = this == MIN_60_OR_MORE

    companion object {
        fun ofMinutes(minutes: Int): TimeAvailable? = entries.firstOrNull { it.minutes == minutes }
    }
}

/** D86's third row. */
enum class Feeling { FRESH, NORMAL, TIRED }

/** D86's fourth row: "Easy · A push · Not sure". */
enum class Wish { EASY, PUSH, NOT_SURE }

/** The form (D86), all four rows answered. [words] is "" for none. */
data class PlanAnswers(
    val activity: PlanActivity,
    val time: TimeAvailable,
    val feeling: Feeling,
    val wish: Wish,
    val words: String = "",
)

/** One step of a suggestion: a minute range, what, and how (speed, incline or zone where they apply; may be ""). */
data class PlanStep(val fromMinute: Int, val toMinute: Int, val what: String, val how: String)

/** A suggestion as the model gave it (D86): advice, never a measurement (D4). */
data class SessionPlan(val title: String, val steps: List<PlanStep>, val why: String)

/** A stored suggestion (D88). At most one is [kept]. */
data class TrainerPlan(
    val id: Long,
    val createdAtMillis: Long,
    val answers: PlanAnswers,
    val plan: SessionPlan,
    val model: String,
    val kept: Boolean,
)

/** "How it felt" (D87). Stored with the review, never over the workout's own `effort` (D77). */
enum class Felt { EASY, RIGHT, HARD }

/** Whether the session followed its plan, as the trainer judged it (design question 9). */
enum class PlanFollowed { YES, PARTLY, NO, NO_PLAN }

/** Feedback (D87): a headline and four short parts. Advice, never a measurement (D4). */
data class Feedback(
    val headline: String,
    val againstPlan: String,
    val numbers: String,
    val nextTime: String,
    val thisWeek: String,
    val followed: PlanFollowed,
)

/** The owner's words on one session (D87, D88). One per workout. */
data class TrainerReview(
    val id: Long = 0,
    val workoutId: Long,
    val planId: Long?,
    val felt: Felt?,
    val words: String?,
    val feedback: Feedback? = null,
    val feedbackAtMillis: Long? = null,
    val model: String? = null,
)

/**
 * What one trainer call came to: the answer and the model that gave it, or one of the call's failures
 * — [EstimateResult]'s closed set, so the screens word them as the meal estimator does (D86).
 */
sealed interface TrainerReply<out T> {
    data class Answered<T>(val value: T, val model: String) : TrainerReply<T>

    data class Failed(val failure: EstimateResult) : TrainerReply<Nothing> {
        init {
            require(failure !is EstimateResult.Proposed && failure !is EstimateResult.AmountMissing) {
                "a trainer call fails for want of a key, allowance, network, permission or sense; not $failure"
            }
        }
    }
}
```

(The `Trainer` port itself needs `TrainerRequest`, so it is written at the end of Task 3's file.)

- [ ] **Step 2: Failing test** `domain/trainer/PlanMatchTest.kt` (JUnit 5; times are invented):

```kotlin
package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import org.junit.jupiter.api.Test

/** D86's "older than seven days stops being offered"; D87's "kept within seven days before the session started". */
class PlanMatchTest {

    private val start = TEST_EPOCH_DAY * DAY + 7 * HOUR

    @Test
    fun `a kept plan is offered for seven days, then not`() {
        val plan = aPlan(createdAt = start)

        assertThat(PlanMatch.offered(plan, start + HOUR)).isEqualTo(plan)
        assertThat(PlanMatch.offered(plan, start + 7 * DAY)).isEqualTo(plan)
        assertThat(PlanMatch.offered(plan, start + 7 * DAY + 1)).isNull()
        assertThat(PlanMatch.offered(plan.copy(kept = false), start + HOUR)).isNull()
        assertThat(PlanMatch.offered(null, start)).isNull()
    }

    @Test
    fun `a session matches the kept plan made up to seven days before it started`() {
        val session = aSession(startedAt = start)

        assertThat(PlanMatch.forSession(aPlan(createdAt = start - HOUR), session)).isNotNull()
        assertThat(PlanMatch.forSession(aPlan(createdAt = start - 7 * DAY), session)).isNotNull()
        assertThat(PlanMatch.forSession(aPlan(createdAt = start - 7 * DAY - 1), session)).isNull()
        assertThat(PlanMatch.forSession(aPlan(createdAt = start + 1), session)).isNull()
        assertThat(PlanMatch.forSession(aPlan(createdAt = start - HOUR).copy(kept = false), session)).isNull()
    }

    private fun aPlan(createdAt: Long) = TrainerPlan(
        id = 1, createdAtMillis = createdAt,
        answers = PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.NOT_SURE),
        plan = SessionPlan("Steady walk", listOf(PlanStep(0, 45, "Walk", "")), "Invented."),
        model = "a-model", kept = true,
    )

    private fun aSession(startedAt: Long) = Workout(
        id = 7, epochDay = TEST_EPOCH_DAY, startedAtMillis = startedAt, durationMinutes = 40,
        kind = WorkoutKind.WALK, title = null, distanceM = null, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
    }
}
```

- [ ] **Step 3: Run, expect compile failure**
  (`--tests "com.metaself.app.domain.trainer.PlanMatchTest"`).
- [ ] **Step 4: Implement `domain/trainer/PlanMatch.kt`:**

```kotlin
package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.Workout

/**
 * Which kept plan counts (D86, D87). Measured from the plan's creation: Keep follows the answer by
 * seconds and no second time is stored (design question 2).
 */
object PlanMatch {

    const val DAYS = 7
    private const val WINDOW_MILLIS = DAYS * 86_400_000L

    /** The kept plan while it is still offered on the Trainer screen. */
    fun offered(plan: TrainerPlan?, nowMillis: Long): TrainerPlan? =
        plan?.takeIf { it.kept && nowMillis - it.createdAtMillis in 0..WINDOW_MILLIS }

    /** The kept plan when it was made within seven days before [session] started; never one made after. */
    fun forSession(kept: TrainerPlan?, session: Workout): TrainerPlan? =
        kept?.takeIf { it.kept && session.startedAtMillis - it.createdAtMillis in 0..WINDOW_MILLIS }
}
```

- [ ] **Step 5: Run, expect PASS.**
- [ ] **Step 6: Commit** `domain/trainer/Trainer.kt`, `domain/trainer/PlanMatch.kt`, the test:
  `feat: the trainer's vocabulary and the seven-day plan rule (D86, D87)`.

---

### Task 3: Everything one request holds — pure

**Files:**
- Create: `domain/trainer/TrainerRequest.kt`
- Modify: `domain/profile/Sex.kt` (KDoc: the trainer now reads it, D84)
- Test: `domain/trainer/TrainerRequestTest.kt`

- [ ] **Step 1: Failing test** `domain/trainer/TrainerRequestTest.kt`. Fixtures (all invented, round):
  today `TEST_EPOCH_DAY` = Thu 3 Sep (this Monday 20_696); a synced WALK on 20_699 of 40 min, 3,000 m
  (no distance source), 200 kcal BAND, heart 110/130, zones `[600,1200,600,0,0]` ESTIMATED; a typed
  STRENGTH on 20_697 of 45 min with 150 kcal MET_ESTIMATE; a synced WALK on 20_658 (first day in) with
  a FILE distance 3,250 m and 4,000 FILE steps; a synced RUN on 20_657 (one day too early); a hidden RUN
  on 20_698; a walk not counted (`counted = false`) on 20_698.

```kotlin
package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFigureSource
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.profile.Sex
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.weight.WeightReading
import org.junit.jupiter.api.Test

/** D84: what one request holds. Every figure, date and word is invented. */
class TrainerRequestTest {

    private val walk = session(id = 1, day = 20_699, kind = WorkoutKind.WALK, minutes = 40).copy(
        distanceM = 3_000, energyKcal = 200, energySource = EnergySource.BAND,
        avgHeartRate = 110, maxHeartRate = 130, zoneSeconds = listOf(600, 1_200, 600, 0, 0), zoneMaxSource = "ESTIMATED",
    )
    private val strength = session(id = 2, day = 20_697, kind = WorkoutKind.STRENGTH, minutes = 45).copy(
        source = WorkoutSource.TYPED, energyKcal = 150, energySource = EnergySource.MET_ESTIMATE,
    )
    private val fromFile = session(id = 3, day = 20_658, kind = WorkoutKind.WALK, minutes = 40).copy(
        distanceM = 3_250, distanceSource = WorkoutFigureSource.FILE, steps = 4_000, stepsSource = WorkoutFigureSource.FILE,
    )
    private val tooEarly = session(id = 4, day = 20_657, kind = WorkoutKind.RUN, minutes = 30)
    private val hidden = session(id = 5, day = 20_698, kind = WorkoutKind.RUN, minutes = 30).copy(hidden = true)
    private val uncounted = session(id = 6, day = 20_698, kind = WorkoutKind.WALK, minutes = 30).copy(counted = false)
    private val all = listOf(walk, strength, fromFile, tooEarly, hidden, uncounted)

    private val plan = TrainerPlan(
        id = 9, createdAtMillis = 0,
        answers = PlanAnswers(PlanActivity.OUTDOOR_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.EASY),
        plan = SessionPlan("Easy loop", listOf(PlanStep(0, 45, "Walk", "easy pace")), "Invented."),
        model = "a-model", kept = false,
    )
    private val review = TrainerReview(id = 1, workoutId = 1, planId = 9, felt = Felt.RIGHT, words = "Invented words.")

    private fun request(
        question: TrainerQuestion = TrainerQuestion.Plan(
            PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_30, Feeling.FRESH, Wish.PUSH),
        ),
        readings: List<WeightReading> = listOf(WeightReading(20_671, 80.0), WeightReading(20_685, 80.0), WeightReading(20_699, 80.0)),
        profile: com.metaself.app.domain.profile.Profile? = aProfile(),
        feedback: List<Feedback> = emptyList(),
    ) = TrainerRequest.of(
        question = question, today = TEST_EPOCH_DAY, workouts = all, reviews = listOf(review),
        plans = mapOf(9L to plan),
        days = listOf(HealthDay(20_699, distanceM = 4_000, activeKcal = 300), HealthDay(20_698, distanceM = 2_000, activeKcal = 100),
            HealthDay(20_690, distanceM = 5_000)),
        readings = readings, profile = profile, currentYear = TEST_YEAR, earlierFeedback = feedback,
    )

    @Test
    fun `the sessions are the counted ones of the last 42 days, oldest first`() {
        assertThat(request().sessions.map { it.epochDay }).containsExactly(20_658L, 20_697L, 20_699L).inOrder()
    }

    @Test
    fun `a session carries each figure with where it came from`() {
        val sessions = request().sessions

        val sent = sessions.last()
        assertThat(sent.kind).isEqualTo(WorkoutKind.WALK)
        assertThat(sent.minutes).isEqualTo(40)
        assertThat(sent.distanceM).isEqualTo(3_000)
        assertThat(sent.distanceFrom).isEqualTo(Origin.SYNCED)
        assertThat(sent.energyKcal).isEqualTo(200)
        assertThat(sent.energyFrom).isEqualTo(EnergySource.BAND)
        assertThat(sent.avgHeartRate).isEqualTo(110)
        assertThat(sent.maxHeartRate).isEqualTo(130)
        assertThat(sent.zoneMinutes).containsExactly(10, 20, 10, 0, 0).inOrder()
        assertThat(sent.zoneMaxEstimated).isTrue()

        val typed = sessions[1]
        assertThat(typed.energyFrom).isEqualTo(EnergySource.MET_ESTIMATE)
        assertThat(typed.distanceM).isNull()
        assertThat(typed.distanceFrom).isNull()

        val filed = sessions.first()
        assertThat(filed.distanceFrom).isEqualTo(Origin.FILE)
        assertThat(filed.steps).isEqualTo(4_000)
        assertThat(filed.stepsFrom).isEqualTo(Origin.FILE)
    }

    @Test
    fun `a reviewed session carries how it felt, the words and its plan`() {
        val sent = request().sessions.last()

        assertThat(sent.felt).isEqualTo(Felt.RIGHT)
        assertThat(sent.words).isEqualTo("Invented words.")
        assertThat(sent.plan).isEqualTo(plan.plan)
        assertThat(request().sessions.first().felt).isNull()
    }

    /** D74's figures, per Monday-based week, newest first; no meals. */
    @Test
    fun `six weeks of totals, this one first and so far`() {
        val weeks = request().weeks

        assertThat(weeks.map { it.monday }).containsExactly(20_696L, 20_689L, 20_682L, 20_675L, 20_668L, 20_661L).inOrder()
        assertThat(weeks.first().current).isTrue()
        assertThat(weeks.drop(1).none { it.current }).isTrue()
        assertThat(weeks[0].distanceM).isEqualTo(6_000)
        assertThat(weeks[0].averageActiveKcal).isEqualTo(200)
        assertThat(weeks[0].sessions).isEqualTo(2) // the walk and the strength session; hidden and uncounted left out
        assertThat(weeks[1].distanceM).isEqualTo(5_000)
        assertThat(weeks[1].sessions).isEqualTo(0)
    }

    /** The weight screen's figures: the smoothed line now, and its measured weekly change. */
    @Test
    fun `the weight is the trend and its measured rate, never a weigh-in`() {
        val weight = request().weight!!

        assertThat(weight.trendKg).isEqualTo(80.0)
        assertThat(weight.asOfEpochDay).isEqualTo(20_699L)
        assertThat(weight.kgPerWeek).isEqualTo(0.0)
        assertThat(weight.overDays).isEqualTo(28)
        assertThat(request(readings = emptyList()).weight).isNull()
    }

    @Test
    fun `the goal is direction and rate, the body is age, sex and height`() {
        val request = request()

        assertThat(request.goal).isEqualTo(GoalFacts(GoalDirection.LOSE, 0.5))
        assertThat(request.body).isEqualTo(BodyFacts(ageYears = 46, sex = Sex.MALE, heightCm = 180))
        assertThat(request(profile = null).goal).isNull()
        assertThat(request(profile = null).body).isNull()
    }

    /** The rhythm is counted here, never by the model (D87). */
    @Test
    fun `this week's sessions so far and the days left are counted on the phone`() {
        assertThat(request().thisWeek).isEqualTo(Rhythm(sessionsSoFar = 2, daysLeft = 3))
    }

    @Test
    fun `at most three earlier feedback texts go`() {
        val four = (1..4).map { Feedback("Headline $it", "", "", "", "", PlanFollowed.NO_PLAN) }

        assertThat(request(feedback = four).earlierFeedback.map { it.headline })
            .containsExactly("Headline 1", "Headline 2", "Headline 3").inOrder()
    }

    @Test
    fun `a review's question is its session with the words being asked about`() {
        val question = TrainerRequest.reviewQuestion(walk, review, plan)

        assertThat(question.session.felt).isEqualTo(Felt.RIGHT)
        assertThat(question.session.plan).isEqualTo(plan.plan)
    }

    /**
     * D84's "never sent", as a shape: there is no field for a meal, sleep, a raw reading, a weigh-in, a
     * target, a name, a title or an origin. A new field fails here before it can leave the phone.
     */
    @Test
    fun `the request has room for exactly what D84 lists`() {
        assertThat(fieldsOf(TrainerRequest::class.java)).containsExactly(
            "question", "today", "sessions", "weeks", "weight", "goal", "body", "thisWeek", "earlierFeedback",
        )
        assertThat(fieldsOf(SessionFacts::class.java)).containsExactly(
            "epochDay", "kind", "minutes", "distanceM", "distanceFrom", "energyKcal", "energyFrom",
            "avgHeartRate", "maxHeartRate", "zoneMinutes", "zoneMaxEstimated", "steps", "stepsFrom",
            "felt", "words", "plan",
        )
        assertThat(fieldsOf(WeekFacts::class.java)).containsExactly("monday", "distanceM", "averageActiveKcal", "sessions", "current")
        assertThat(fieldsOf(WeightFacts::class.java)).containsExactly("trendKg", "asOfEpochDay", "kgPerWeek", "overDays")
        assertThat(fieldsOf(GoalFacts::class.java)).containsExactly("direction", "kgPerWeek")
        assertThat(fieldsOf(BodyFacts::class.java)).containsExactly("ageYears", "sex", "heightCm")
    }

    private fun fieldsOf(type: Class<*>): List<String> =
        type.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }

    private fun session(id: Long, day: Long, kind: WorkoutKind, minutes: Int) = Workout(
        id = id, epochDay = day, startedAtMillis = day * 86_400_000L + 7 * 3_600_000L, durationMinutes = minutes,
        kind = kind, title = "Invented title", distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )
}
```

- [ ] **Step 2: Run, expect compile failure** (`--tests "com.metaself.app.domain.trainer.TrainerRequestTest"`).

- [ ] **Step 3: Implement `domain/trainer/TrainerRequest.kt`:**

```kotlin
package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFigureSource
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.profile.Profile
import com.metaself.app.domain.profile.Sex
import com.metaself.app.domain.weight.MeasuredRate
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.domain.weight.WeightTrend

/** Where a distance or steps figure came from, as the model is told it (no app or device name, D84). */
enum class Origin { SYNCED, FILE, TYPED }

/** What is being asked (D84's "the question"). */
sealed interface TrainerQuestion {
    data class Plan(val answers: PlanAnswers) : TrainerQuestion

    /** [session] carries the felt effort, the words and the matched plan being asked about. */
    data class Review(val session: SessionFacts) : TrainerQuestion
}

/** One session as D84 sends it. [title] and [origin] of the workout are deliberately absent. */
data class SessionFacts(
    val epochDay: Long,
    val kind: WorkoutKind,
    val minutes: Int,
    val distanceM: Int?,
    val distanceFrom: Origin?,
    val energyKcal: Int?,
    val energyFrom: EnergySource?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val zoneMinutes: List<Int>?,
    val zoneMaxEstimated: Boolean,
    val steps: Int?,
    val stepsFrom: Origin?,
    val felt: Felt?,
    val words: String?,
    val plan: SessionPlan?,
)

/** One Monday-based week's D74 figures. [current] is this week, so far. */
data class WeekFacts(
    val monday: Long,
    val distanceM: Int?,
    val averageActiveKcal: Int?,
    val sessions: Int,
    val current: Boolean,
)

/** The weight screen's figures: the smoothed line's last point and its measured weekly change. */
data class WeightFacts(val trendKg: Double, val asOfEpochDay: Long, val kgPerWeek: Double?, val overDays: Int?)

/** The profile's goal: direction and weekly rate. No target (D84 does not list one). */
data class GoalFacts(val direction: GoalDirection, val kgPerWeek: Double)

data class BodyFacts(val ageYears: Int, val sex: Sex, val heightCm: Int)

/** This week's rhythm, counted on the phone and handed to the model (D87). */
data class Rhythm(val sessionsSoFar: Int, val daysLeft: Int)

/**
 * Everything one trainer request holds (D84) — built fresh from the stored record every time; no
 * conversation is kept or replayed. There is deliberately no field for a meal, sleep, a raw reading,
 * a single weigh-in, a target weight, a name, or an app or device name; `TrainerRequestTest` fails if
 * one is added.
 */
data class TrainerRequest(
    val question: TrainerQuestion,
    val today: Long,
    val sessions: List<SessionFacts>,
    val weeks: List<WeekFacts>,
    val weight: WeightFacts?,
    val goal: GoalFacts?,
    val body: BodyFacts?,
    val thisWeek: Rhythm,
    val earlierFeedback: List<Feedback>,
) {
    companion object {
        const val SESSION_DAYS = 42
        const val WEEKS = 6
        const val FEEDBACK_COUNT = 3

        /** The first day whose sessions go (42 days ending today). */
        fun firstDay(today: Long): Long = today - (SESSION_DAYS - 1)

        /** The first day whose summaries the six weeks need: the Monday five weeks before this one. */
        fun firstSummaryDay(today: Long): Long = MovementWeek.mondayOf(today) - 7L * (WEEKS - 1)

        /**
         * @param workouts any workouts; only the visible, counted ones of the 42 days are sent.
         * @param reviews any reviews; each is attached to its session.
         * @param plans the stored plans the reviews name, by id.
         * @param days the daily summaries from [firstSummaryDay] to [today]; meals are never read.
         * @param earlierFeedback newest first; the first [FEEDBACK_COUNT] are sent.
         */
        fun of(
            question: TrainerQuestion,
            today: Long,
            workouts: List<Workout>,
            reviews: List<TrainerReview>,
            plans: Map<Long, TrainerPlan>,
            days: List<HealthDay>,
            readings: List<WeightReading>,
            profile: Profile?,
            currentYear: Int,
            earlierFeedback: List<Feedback>,
        ): TrainerRequest {
            val first = firstDay(today)
            val byWorkout = reviews.associateBy { it.workoutId }
            val sessions = workouts
                .filter { !it.hidden && it.counted && it.epochDay in first..today }
                .sortedBy { it.startedAtMillis }
                .map { workout ->
                    val review = byWorkout[workout.id]
                    session(workout, review, review?.planId?.let(plans::get)?.plan)
                }
            val thisMonday = MovementWeek.mondayOf(today)
            val weeks = (0 until WEEKS).map { back ->
                val week = MovementWeek.of(today, days, workouts, emptyMap(), thisMonday - 7L * back)
                WeekFacts(week.monday, week.distanceM, week.averageActiveKcal, week.workoutCount + week.walkCount, back == 0)
            }
            val trend = WeightTrend.of(readings)
            val rate = MeasuredRate.of(trend, today)
            return TrainerRequest(
                question = question,
                today = today,
                sessions = sessions,
                weeks = weeks,
                weight = trend.lastOrNull()?.let { WeightFacts(it.trendKg, it.reading.epochDay, rate?.kgPerWeek, rate?.spanDays) },
                goal = profile?.let { GoalFacts(it.goal.direction, it.goal.kgPerWeek) },
                body = profile?.let { BodyFacts(currentYear - it.birthYear, it.sex, it.heightCm) },
                thisWeek = Rhythm(weeks.first().sessions, (thisMonday + 6 - today).toInt()),
                earlierFeedback = earlierFeedback.take(FEEDBACK_COUNT),
            )
        }

        /** The question for D87: [workout] with the review and plan being asked about. */
        fun reviewQuestion(workout: Workout, review: TrainerReview, plan: TrainerPlan?): TrainerQuestion.Review =
            TrainerQuestion.Review(session(workout, review, plan?.plan))

        private fun session(workout: Workout, review: TrainerReview?, plan: SessionPlan?): SessionFacts = SessionFacts(
            epochDay = workout.epochDay,
            kind = workout.kind,
            minutes = workout.durationMinutes,
            distanceM = workout.distanceM,
            distanceFrom = workout.distanceM?.let {
                when (workout.distanceSource) {
                    WorkoutFigureSource.FILE -> Origin.FILE
                    WorkoutFigureSource.TYPED -> Origin.TYPED
                    null -> if (workout.source == WorkoutSource.TYPED) Origin.TYPED else Origin.SYNCED
                }
            },
            energyKcal = workout.energyKcal,
            energyFrom = workout.energyKcal?.let { workout.energySource },
            avgHeartRate = workout.avgHeartRate,
            maxHeartRate = workout.maxHeartRate,
            // Rounded to the nearest minute; a zone under half a minute reads 0.
            zoneMinutes = workout.zoneSeconds?.map { (it + 30) / 60 },
            zoneMaxEstimated = workout.zoneMaxSource != "OBSERVED",
            steps = workout.steps,
            stepsFrom = workout.steps?.let { if (workout.stepsSource == WorkoutFigureSource.TYPED) Origin.TYPED else Origin.FILE },
            felt = review?.felt,
            words = review?.words?.takeIf { it.isNotBlank() },
            plan = plan,
        )
    }
}

/** Asking the model (D84): one call each, no retry, nothing in its vocabulary that names a vendor. */
interface Trainer {
    /** [request]'s question must be [TrainerQuestion.Plan]. */
    suspend fun suggest(request: TrainerRequest): TrainerReply<SessionPlan>

    /** [request]'s question must be [TrainerQuestion.Review]. */
    suspend fun feedback(request: TrainerRequest): TrainerReply<Feedback>
}
```

  In `domain/profile/Sex.kt` replace "Nothing else in the app reads this field, and nothing else should."
  with "The trainer sends it with age and height (D84), because advice on effort depends on it; nothing
  else reads it."

- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** `TrainerRequest.kt`, `Sex.kt`, the test: `feat: what one trainer request holds (D84)`.

---

### Task 4: The request body and the instructions — pure

**Files:**
- Create: `data/ai/TrainerPrompt.kt`
- Test: `data/ai/TrainerPromptTest.kt`

- [ ] **Step 1: Failing test** `data/ai/TrainerPromptTest.kt` (JUnit 5). Build requests with
  `TrainerRequest.of(...)` from invented fixtures as in Task 3 — include a workout with
  `title = "Quillberry loop"` and a `HealthDay` with `sleepMinutes = 430` and `restingHeartRate = 58`, a
  profile `aProfile(weightKg = 83.0, goal = Goal.lose(0.5, targetKg = 71.5))` (invented overrides:
  a weigh-in and a target that must not be sent), and readings all at 80.0 kg. Tests:

```kotlin
    @Test
    fun `a plan request names its schema and holds exactly D84's parts`() {
        val body = Json.parseToJsonElement(TrainerPrompt.planBody("a-model", planRequest(), RequestProfile.DETERMINISTIC)).jsonObject
        val user = Json.parseToJsonElement(userContent(body)).jsonObject

        assertThat(body.toString()).contains("session_plan")
        assertThat(user.keys).containsExactly(
            "question", "today", "sessions", "weeks", "weight", "goal", "body", "this_week", "earlier_feedback",
        )
        assertThat(user.getValue("question").jsonObject.getValue("kind").jsonPrimitive.content).isEqualTo("plan")
        assertThat(user.getValue("this_week").jsonObject.getValue("sessions_so_far").jsonPrimitive.int).isEqualTo(2)
    }

    @Test
    fun `a feedback request names its schema and asks about one session`() {
        val body = TrainerPrompt.feedbackBody("a-model", reviewRequest(), RequestProfile.DETERMINISTIC)
        val user = Json.parseToJsonElement(userContent(Json.parseToJsonElement(body).jsonObject)).jsonObject

        assertThat(body).contains("session_feedback")
        val question = user.getValue("question").jsonObject
        assertThat(question.getValue("kind").jsonPrimitive.content).isEqualTo("review")
        assertThat(question.getValue("session").jsonObject.getValue("felt").jsonPrimitive.content).isEqualTo("right")
    }

    @Test
    fun `a session is sent with each figure's source, in words`() {
        val session = sentSessions(planRequest()).last()

        assertThat(session.getValue("distance_source").jsonPrimitive.content).isEqualTo("synced")
        assertThat(session.getValue("energy_source").jsonPrimitive.content).isEqualTo("band")
        assertThat(session.getValue("heart_rate").jsonObject.getValue("source").jsonPrimitive.content).isEqualTo("from readings")
        assertThat(session.getValue("zone_minutes").jsonArray.map { it.jsonPrimitive.int }).containsExactly(10, 20, 10, 0, 0).inOrder()
        assertThat(session.getValue("zone_max").jsonPrimitive.content).isEqualTo("estimated")
    }

    /** D84's "never sent". If this fails because the prompt's own wording used a word, rephrase the prompt. */
    @Test
    fun `no meal, sleep, weigh-in, target, title or app name is sent`() {
        listOf(TrainerPrompt.planBody("a-model", planRequest()), TrainerPrompt.feedbackBody("a-model", reviewRequest()))
            .map { it.lowercase() }
            .forEach { body ->
                listOf("quillberry", "com.example", "sleep", "slept", "meal", "eaten", "breakfast", "83.0", "71.5", "430", "resting")
                    .forEach { forbidden -> assertThat(body).doesNotContain(forbidden) }
            }
    }

    @Test
    fun `pain, dizziness or chest discomfort come before anything else, and it is not medical`() {
        val system = systemContent(TrainerPrompt.feedbackBody("a-model", reviewRequest()))

        assertThat(system).contains("pain, dizziness or chest discomfort")
        assertThat(system).contains("stop and see a doctor")
        assertThat(system).contains("not a medical service")
        assertThat(systemContent(TrainerPrompt.planBody("a-model", planRequest()))).contains("pain, dizziness or chest discomfort")
    }

    @Test
    fun `the rhythm is the phone's count, never the model's`() {
        val system = systemContent(TrainerPrompt.feedbackBody("a-model", reviewRequest()))

        assertThat(system).contains("this_week")
        assertThat(system).contains("never count sessions yourself")
    }

    @Test
    fun `a plan is asked for as three to six steps with a minute range, what and how`() {
        val system = systemContent(TrainerPrompt.planBody("a-model", planRequest()))

        assertThat(system).contains("three to six steps")
        assertThat(system).contains("from_minute")
    }

    @Test
    fun `each body is built one way, from one request`() {
        listOf("planBody", "feedbackBody").forEach { name ->
            val ways = TrainerPrompt::class.java.declaredMethods.filter { it.name == name }
            assertThat(ways).hasSize(1)
            assertThat(ways.single().parameterTypes.toList())
                .containsExactly(String::class.java, TrainerRequest::class.java, RequestProfile::class.java).inOrder()
        }
    }

    @Test
    fun `a plan request for a review, or a review for a plan, is refused`() {
        assertThrows<IllegalArgumentException> { TrainerPrompt.planBody("a-model", reviewRequest()) }
        assertThrows<IllegalArgumentException> { TrainerPrompt.feedbackBody("a-model", planRequest()) }
    }
```

  Helpers in the test: `userContent(body)` / `systemContent(body)` read `messages[role].content`;
  `sentSessions(request)` returns the user JSON's `sessions` as objects. (`systemContent` must work
  whether or not the profile is strict — use `RequestProfile.DETERMINISTIC` or read the first system
  message.)

- [ ] **Step 2: Run, expect compile failure.**

- [ ] **Step 3: Implement `data/ai/TrainerPrompt.kt`:**

```kotlin
package com.metaself.app.data.ai

import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Origin
import com.metaself.app.domain.trainer.SessionFacts
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.TrainerRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate

/**
 * What leaves the phone when the owner asks the trainer (D84) — the third thing this app sends, and
 * like [EstimatePrompt] and [ReviewPrompt] a pure function with its own "nothing else is sent" test.
 *
 * Everything comes from one [TrainerRequest], built fresh from the stored record; no earlier
 * conversation is replayed. The replies are pinned by strict schemas and checked again on the phone
 * ([TrainerResponse]).
 */
object TrainerPrompt {

    private val COMMON = """
        You are a walking and running trainer for one person. You are given his activity record: every
        session of the last 42 days with its figures and where each came from, six weekly totals (this
        week first, so far), his smoothed weight trend and its weekly change, his goal's direction and
        weekly rate, his age, sex and height, and your last feedback to him.

        His two aims are his weight goal and a steady rhythm of sessions. Keep your advice consistent
        with your earlier feedback unless the record gives a reason to change it.

        Safety comes first. If his words anywhere in this request mention pain, dizziness or chest
        discomfort, tell him to stop and see a doctor before saying anything else. You are a trainer for
        walking and running, not a medical service; never diagnose.

        Sources: "synced" is his phone and band's total over the session; "file" came from a workout
        file; "typed" he typed himself; "band" is the band's own energy figure; "estimated" is this
        app's estimate from the kind of session and its effort; heart rate is worked out from the band's
        readings; zones are measured against a maximum that is "estimated" (220 minus age) unless it
        says "observed". An estimate is weaker evidence than a measurement.

        The rhythm: this_week gives the sessions he has done this week so far and the days left in it,
        counted by the app. Use those numbers; never count sessions yourself.

        Write plain English, to him, in the second person. Short sentences. Every figure you mention
        must be one given here or one you propose for the next session.
    """.trimIndent()

    private val PLAN = """
        He is asking what to do in his next session. The question gives what he wants to do, the time he
        has ("or_more" means at least that), how he feels and what he wants today, and any words of his.

        Reply with a title, three to six steps in order, and one paragraph on why. Each step has
        from_minute and to_minute (whole minutes from the start), what it is, and how: a speed, an
        incline or a heart-rate zone where they apply, otherwise an empty string. The steps must fit the
        time he has.
    """.trimIndent()

    private val FEEDBACK = """
        He has done the session in the question and is telling you how it went: how it felt, his words,
        and the plan it was matched to, if any. Reply with a one-line headline and four short parts:
        against_plan (how it went against the plan; if there was no plan, say so in a few words),
        numbers (what its figures say), next_time (one concrete change for the next session), and
        this_week (the rhythm, from this_week). Give plan_followed: "yes", "partly" or "no" against the
        plan, or "no_plan" when there was none.
    """.trimIndent()

    fun planBody(model: String, request: TrainerRequest, profile: RequestProfile = RequestProfile.guess(model)): String {
        require(request.question is TrainerQuestion.Plan) { "a plan is asked with a plan question" }
        return ChatRequest.body(model, profile, messages(PLAN, request), "session_plan", PLAN_SCHEMA)
    }

    fun feedbackBody(model: String, request: TrainerRequest, profile: RequestProfile = RequestProfile.guess(model)): String {
        require(request.question is TrainerQuestion.Review) { "feedback is asked with a review question" }
        return ChatRequest.body(model, profile, messages(FEEDBACK, request), "session_feedback", FEEDBACK_SCHEMA)
    }

    private fun messages(task: String, request: TrainerRequest) = listOf(
        ChatRequest.Message("system", COMMON + "\n\n" + task),
        ChatRequest.Message("user", user(request).toString()),
    )

    private fun user(request: TrainerRequest): JsonObject = buildJsonObject {
        put("question", question(request.question))
        put("today", date(request.today))
        putJsonArray("sessions") { request.sessions.forEach { add(session(it)) } }
        putJsonArray("weeks") {
            request.weeks.forEach { week ->
                add(
                    buildJsonObject {
                        put("from", date(week.monday))
                        put("so_far", week.current)
                        put("distance_m", week.distanceM?.let(::JsonPrimitive) ?: JsonNull)
                        put("movement_kcal_a_day", week.averageActiveKcal?.let(::JsonPrimitive) ?: JsonNull)
                        put("sessions", week.sessions)
                    },
                )
            }
        }
        put(
            "weight",
            request.weight?.let { weight ->
                buildJsonObject {
                    put("trend_kg", round1(weight.trendKg))
                    put("as_of", date(weight.asOfEpochDay))
                    put("change_kg_a_week", weight.kgPerWeek?.let { JsonPrimitive(round2(it)) } ?: JsonNull)
                    put("measured_over_days", weight.overDays?.let(::JsonPrimitive) ?: JsonNull)
                }
            } ?: JsonNull,
        )
        put(
            "goal",
            request.goal?.let { goal ->
                buildJsonObject {
                    put("direction", goal.direction.name.lowercase())
                    put("kg_a_week", goal.kgPerWeek)
                }
            } ?: JsonNull,
        )
        put(
            "body",
            request.body?.let { body ->
                buildJsonObject {
                    put("age", body.ageYears)
                    put("sex", body.sex.name.lowercase())
                    put("height_cm", body.heightCm)
                }
            } ?: JsonNull,
        )
        putJsonObject("this_week") {
            put("sessions_so_far", request.thisWeek.sessionsSoFar)
            put("days_left", request.thisWeek.daysLeft)
        }
        putJsonArray("earlier_feedback") { request.earlierFeedback.forEach { add(feedback(it)) } }
    }

    private fun question(question: TrainerQuestion): JsonObject = when (question) {
        is TrainerQuestion.Plan -> buildJsonObject {
            put("kind", "plan")
            put("what", question.answers.activity.name.lowercase().replace('_', ' '))
            put("minutes_available", question.answers.time.minutes)
            put("or_more", question.answers.time.orMore)
            put("feeling", question.answers.feeling.name.lowercase())
            put("wants", question.answers.wish.name.lowercase().replace('_', ' '))
            put("words", question.answers.words.trim())
        }
        is TrainerQuestion.Review -> buildJsonObject {
            put("kind", "review")
            put("session", session(question.session))
        }
    }

    private fun session(session: SessionFacts): JsonObject = buildJsonObject {
        put("date", date(session.epochDay))
        put("kind", kind(session.kind))
        put("minutes", session.minutes)
        put("distance_m", session.distanceM?.let(::JsonPrimitive) ?: JsonNull)
        put("distance_source", session.distanceFrom?.let { JsonPrimitive(origin(it)) } ?: JsonNull)
        put("energy_kcal", session.energyKcal?.let(::JsonPrimitive) ?: JsonNull)
        put("energy_source", session.energyFrom?.let { JsonPrimitive(energy(it)) } ?: JsonNull)
        put(
            "heart_rate",
            if (session.avgHeartRate == null && session.maxHeartRate == null) {
                JsonNull
            } else {
                buildJsonObject {
                    put("average", session.avgHeartRate?.let(::JsonPrimitive) ?: JsonNull)
                    put("highest", session.maxHeartRate?.let(::JsonPrimitive) ?: JsonNull)
                    put("source", "from readings")
                }
            },
        )
        put("zone_minutes", session.zoneMinutes?.let { zones -> JsonArray(zones.map(::JsonPrimitive)) } ?: JsonNull)
        put("zone_max", if (session.zoneMinutes == null) JsonNull else JsonPrimitive(if (session.zoneMaxEstimated) "estimated" else "observed"))
        put("steps", session.steps?.let(::JsonPrimitive) ?: JsonNull)
        put("steps_source", session.stepsFrom?.let { JsonPrimitive(origin(it)) } ?: JsonNull)
        put("felt", session.felt?.let { JsonPrimitive(it.name.lowercase()) } ?: JsonNull)
        put("words", session.words?.let(::JsonPrimitive) ?: JsonNull)
        put("plan", session.plan?.let(::plan) ?: JsonNull)
    }

    private fun plan(plan: SessionPlan): JsonObject = planJson(plan)

    private fun feedback(feedback: Feedback): JsonObject = feedbackJson(feedback)

    /** A plan in the reply schema's own shape: sent as an earlier plan, and what `TrainerResponse.encodePlan` stores. */
    fun planJson(plan: SessionPlan): JsonObject = buildJsonObject {
        put("title", plan.title)
        putJsonArray("steps") {
            plan.steps.forEach { step ->
                add(
                    buildJsonObject {
                        put("from_minute", step.fromMinute)
                        put("to_minute", step.toMinute)
                        put("what", step.what)
                        put("how", step.how)
                    },
                )
            }
        }
        put("why", plan.why)
    }

    /** Feedback in the reply schema's own shape: sent as earlier feedback, and what `TrainerResponse.encodeFeedback` stores. */
    fun feedbackJson(feedback: Feedback): JsonObject = buildJsonObject {
        put("headline", feedback.headline)
        put("against_plan", feedback.againstPlan)
        put("numbers", feedback.numbers)
        put("next_time", feedback.nextTime)
        put("this_week", feedback.thisWeek)
        put("plan_followed", feedback.followed.name.lowercase())
    }

    private fun kind(kind: WorkoutKind): String = when (kind) {
        WorkoutKind.RUN -> "run"
        WorkoutKind.WALK -> "walk"
        WorkoutKind.CYCLE -> "cycle"
        WorkoutKind.SWIM -> "swim"
        WorkoutKind.STRENGTH -> "strength"
        WorkoutKind.OTHER, WorkoutKind.UNRECOGNISED -> "other"
    }

    private fun origin(origin: Origin): String = origin.name.lowercase()

    private fun energy(source: EnergySource): String = when (source) {
        EnergySource.BAND -> "band"
        EnergySource.MET_ESTIMATE -> "estimated"
        EnergySource.TYPED -> "typed"
        EnergySource.FILE -> "file"
        EnergySource.NONE -> "unknown"
    }

    private fun date(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()

    private fun round1(value: Double): Double = Math.round(value * 10) / 10.0

    private fun round2(value: Double): Double = Math.round(value * 100) / 100.0

    private fun string() = buildJsonObject { put("type", "string") }

    private fun integer() = buildJsonObject { put("type", "integer") }

    private fun strictObject(vararg properties: Pair<String, JsonObject>) = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonObject("properties") { properties.forEach { (name, schema) -> put(name, schema) } }
        putJsonArray("required") { properties.forEach { add(it.first) } }
    }

    private val PLAN_SCHEMA: JsonObject = strictObject(
        "title" to string(),
        "steps" to buildJsonObject {
            put("type", "array")
            put("items", strictObject("from_minute" to integer(), "to_minute" to integer(), "what" to string(), "how" to string()))
        },
        "why" to string(),
    )

    private val FEEDBACK_SCHEMA: JsonObject = strictObject(
        "headline" to string(),
        "against_plan" to string(),
        "numbers" to string(),
        "next_time" to string(),
        "this_week" to string(),
        "plan_followed" to buildJsonObject {
            put("type", "string")
            putJsonArray("enum") { add("yes"); add("partly"); add("no"); add("no_plan") }
        },
    )
}
```

- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** `TrainerPrompt.kt`, its test: `feat: the trainer's request, and nothing else (D84)`.

---

### Task 5: Reading the replies, and writing them back for storage — pure

**Files:**
- Create: `data/ai/TrainerResponse.kt`
- Test: `data/ai/TrainerResponseTest.kt`

- [ ] **Step 1: Failing tests** (JUnit 5; `reply(content)` wraps content as
  `{"choices":[{"message":{"content":…}}]}` exactly as `OpenAiFoodReviewerTest.reply` does):

```kotlin
    private val goodPlan = """{"title":"Steady walk with two climbs","steps":[
        {"from_minute":0,"to_minute":10,"what":"Warm up","how":"easy pace"},
        {"from_minute":10,"to_minute":35,"what":"Two climbs","how":"zone 3"},
        {"from_minute":35,"to_minute":45,"what":"Cool down","how":""}],"why":"Invented reason."}"""

    private val goodFeedback = """{"headline":"A steady session","against_plan":"As planned.","numbers":"Invented.",
        "next_time":"Invented.","this_week":"Invented.","plan_followed":"yes"}"""

    @Test
    fun `a plan reply becomes a plan, with the model that gave it`() {
        val reply = TrainerResponse.parsePlan(reply(goodPlan), "a-model") as TrainerReply.Answered

        assertThat(reply.model).isEqualTo("a-model")
        assertThat(reply.value.title).isEqualTo("Steady walk with two climbs")
        assertThat(reply.value.steps.map { it.fromMinute to it.toMinute }).containsExactly(0 to 10, 10 to 35, 35 to 45).inOrder()
        assertThat(reply.value.steps.last().how).isEmpty()
    }

    @Test
    fun `fewer than three or more than six steps is unreadable`() {
        assertUnreadable(TrainerResponse.parsePlan(reply(planOf(steps = 2)), "a-model"))
        assertUnreadable(TrainerResponse.parsePlan(reply(planOf(steps = 7)), "a-model"))
        assertThat(TrainerResponse.parsePlan(reply(planOf(steps = 3)), "a-model")).isInstanceOf(TrainerReply.Answered::class.java)
        assertThat(TrainerResponse.parsePlan(reply(planOf(steps = 6)), "a-model")).isInstanceOf(TrainerReply.Answered::class.java)
    }

    /** [steps] steps of five minutes each, back to back. Invented. */
    private fun planOf(steps: Int): String =
        """{"title":"t","steps":[""" +
            (0 until steps).joinToString(",") { """{"from_minute":${it * 5},"to_minute":${it * 5 + 5},"what":"w","how":""}""" } +
            """],"why":"y"}"""

    @Test
    fun `a step that ends before it starts, goes backwards, or says nothing is unreadable`() {
        assertUnreadable(TrainerResponse.parsePlan(reply(goodPlan.replace("\"to_minute\":10", "\"to_minute\":0")), "a-model"))
        assertUnreadable(TrainerResponse.parsePlan(reply(goodPlan.replace("\"from_minute\":35", "\"from_minute\":5")), "a-model"))
        assertUnreadable(TrainerResponse.parsePlan(reply(goodPlan.replace("\"Warm up\"", "\" \"")), "a-model"))
        assertUnreadable(TrainerResponse.parsePlan(reply(goodPlan.replace("\"Invented reason.\"", "\"\"")), "a-model"))
    }

    @Test
    fun `a feedback reply becomes feedback`() {
        val reply = TrainerResponse.parseFeedback(reply(goodFeedback), "a-model") as TrainerReply.Answered

        assertThat(reply.value.headline).isEqualTo("A steady session")
        assertThat(reply.value.followed).isEqualTo(PlanFollowed.YES)
    }

    @Test
    fun `feedback missing a part, or with an unknown judgement, is unreadable`() {
        assertUnreadable(TrainerResponse.parseFeedback(reply(goodFeedback.replace("\"A steady session\"", "\"\"")), "a-model"))
        assertUnreadable(TrainerResponse.parseFeedback(reply(goodFeedback.replace("\"yes\"", "\"maybe\"")), "a-model"))
        assertUnreadable(TrainerResponse.parseFeedback("not json", "a-model"))
    }

    /** With no plan, "against the plan" may be empty. */
    @Test
    fun `with no plan the against-plan part may be empty`() {
        val noPlan = goodFeedback.replace("\"As planned.\"", "\"\"").replace("\"yes\"", "\"no_plan\"")

        assertThat(TrainerResponse.parseFeedback(reply(noPlan), "a-model")).isInstanceOf(TrainerReply.Answered::class.java)
    }

    /** Design question 3: what is stored reads back as exactly what was read. */
    @Test
    fun `a plan and feedback survive being written for storage`() {
        val plan = (TrainerResponse.parsePlan(reply(goodPlan), "m") as TrainerReply.Answered).value
        val feedback = (TrainerResponse.parseFeedback(reply(goodFeedback), "m") as TrainerReply.Answered).value

        assertThat(TrainerResponse.readPlan(TrainerResponse.encodePlan(plan))).isEqualTo(plan)
        assertThat(TrainerResponse.readFeedback(TrainerResponse.encodeFeedback(feedback))).isEqualTo(feedback)
        assertThat(TrainerResponse.readPlan("garbled")).isNull()
        assertThat(TrainerResponse.readFeedback(null)).isNull()
    }

    private fun assertUnreadable(reply: TrainerReply<*>) {
        assertThat((reply as TrainerReply.Failed).failure).isInstanceOf(EstimateResult.Unreadable::class.java)
    }
```

- [ ] **Step 2: Run, expect compile failure.**

- [ ] **Step 3: Implement `data/ai/TrainerResponse.kt`:**

```kotlin
package com.metaself.app.data.ai

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlanStep
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.TrainerReply
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The trainer's replies, read strictly (D86, D87) — [ReviewResponse]'s twin. An answer that is not in
 * the shape asked for is [EstimateResult.Unreadable], one of the call's failures; never an exception.
 *
 * Also where a plan or feedback is written back into that same shape for storage (D88, design question
 * 3) — through [TrainerPrompt]'s own builders — so what is stored reads back with the same rules.
 */
object TrainerResponse {

    const val MIN_STEPS = 3
    const val MAX_STEPS = 6

    private val json = Json { ignoreUnknownKeys = true }
    private const val NOT_THE_SHAPE = "the reply was not in the shape this app asked for"

    fun parsePlan(body: String, model: String): TrainerReply<SessionPlan> =
        content(body)?.let(::readPlan)?.let { TrainerReply.Answered(it, model) }
            ?: TrainerReply.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content(body) ?: body))

    fun parseFeedback(body: String, model: String): TrainerReply<Feedback> =
        content(body)?.let(::readFeedback)?.let { TrainerReply.Answered(it, model) }
            ?: TrainerReply.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content(body) ?: body))

    /** A plan in the reply's shape, or null for anything else. */
    fun readPlan(content: String?): SessionPlan? = runCatching {
        val payload = json.parseToJsonElement(content!!).jsonObject
        val steps = payload.getValue("steps").jsonArray.map { element ->
            val step = element.jsonObject
            PlanStep(
                fromMinute = step.getValue("from_minute").jsonPrimitive.int,
                toMinute = step.getValue("to_minute").jsonPrimitive.int,
                what = step.getValue("what").jsonPrimitive.content.trim(),
                how = step.getValue("how").jsonPrimitive.content.trim(),
            )
        }
        val plan = SessionPlan(
            title = payload.getValue("title").jsonPrimitive.content.trim(),
            steps = steps,
            why = payload.getValue("why").jsonPrimitive.content.trim(),
        )
        plan.takeIf(::usable)
    }.getOrNull()

    /** Feedback in the reply's shape, or null for anything else. */
    fun readFeedback(content: String?): Feedback? = runCatching {
        val payload = json.parseToJsonElement(content!!).jsonObject
        fun part(name: String) = payload.getValue(name).jsonPrimitive.content.trim()
        val followed = when (part("plan_followed")) {
            "yes" -> PlanFollowed.YES
            "partly" -> PlanFollowed.PARTLY
            "no" -> PlanFollowed.NO
            "no_plan" -> PlanFollowed.NO_PLAN
            else -> null
        }!!
        val feedback = Feedback(part("headline"), part("against_plan"), part("numbers"), part("next_time"), part("this_week"), followed)
        feedback.takeIf(::usable)
    }.getOrNull()

    /** The same shape [TrainerPrompt] sends an earlier plan in, so stored and sent cannot drift. */
    fun encodePlan(plan: SessionPlan): String = TrainerPrompt.planJson(plan).toString()

    fun encodeFeedback(feedback: Feedback): String = TrainerPrompt.feedbackJson(feedback).toString()

    private fun content(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["choices"]!!.jsonArray
            .first().jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
    }.getOrNull()

    /** Design question 18. */
    private fun usable(plan: SessionPlan): Boolean =
        plan.title.isNotEmpty() && plan.why.isNotEmpty() &&
            plan.steps.size in MIN_STEPS..MAX_STEPS &&
            plan.steps.all { it.fromMinute >= 0 && it.toMinute > it.fromMinute && it.what.isNotEmpty() } &&
            plan.steps.zipWithNext().all { (a, b) -> b.fromMinute >= a.fromMinute }

    private fun usable(feedback: Feedback): Boolean =
        listOf(feedback.headline, feedback.numbers, feedback.nextTime, feedback.thisWeek).all { it.isNotEmpty() } &&
            (feedback.againstPlan.isNotEmpty() || feedback.followed == PlanFollowed.NO_PLAN)
}
```

- [ ] **Step 4: Run Tasks 4 and 5's tests, expect PASS.**
- [ ] **Step 5: Commit** `TrainerResponse.kt`, its test: `feat: read the trainer's replies strictly (D86, D87)`.

---

### Task 6: The call

**Files:**
- Create: `data/ai/OpenAiTrainer.kt`
- Modify: `data/ai/OpenAiCall.kt` (KDoc: "Shared by everything that asks the model something — the meal
  estimator, a food's review, and the trainer (D84)")
- Modify: `di/AiModule.kt` (provide `Trainer`)
- Test: `data/ai/OpenAiTrainerTest.kt` (JUnit 5, `MockWebServer`; copy `FakeKeys`, `FakeSettings`,
  `RecordingLog` from `OpenAiFoodReviewerTest` as private classes)

- [ ] **Step 1: Failing tests:**

```kotlin
    @Test
    fun `a good plan reply is one call, counted once, with the model's name`() = runTest {
        val settings = FakeSettings()
        server.enqueue(MockResponse().setBody(reply(GOOD_PLAN)))

        val reply = trainer(settings = settings).suggest(PLAN_REQUEST) as TrainerReply.Answered

        assertThat(reply.value.steps).hasSize(3)
        assertThat(reply.model).isEqualTo(AiSettings().model)
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(settings.calls).isEqualTo(1)
    }

    @Test
    fun `the key goes only in the header`() = runTest {
        server.enqueue(MockResponse().setBody(reply(GOOD_FEEDBACK)))

        trainer().feedback(REVIEW_REQUEST)

        val sent = server.takeRequest()
        assertThat(sent.getHeader("Authorization")).isEqualTo("Bearer a-key")
        assertThat(sent.body.readUtf8()).doesNotContain("a-key")
    }

    @Test
    fun `with no key or at the ceiling nothing is sent`() = runTest {
        assertThat(trainer(key = null).suggest(PLAN_REQUEST)).isEqualTo(TrainerReply.Failed(EstimateResult.NoKey))
        val spent = FakeSettings(AiSettings(dailyCeiling = 2, usedToday = 2))
        assertThat(trainer(settings = spent).feedback(REVIEW_REQUEST)).isEqualTo(TrainerReply.Failed(EstimateResult.CeilingReached))
        assertThat(server.requestCount).isEqualTo(0)
    }

    /** One call, no retry. */
    @Test
    fun `an unreadable answer is a failure, not asked again, and logged without its words`() = runTest {
        val log = RecordingLog()
        server.enqueue(MockResponse().setBody(reply("{\"title\": \"$WORDS\"}")))

        val reply = trainer(problems = log).feedback(REVIEW_REQUEST)

        assertThat((reply as TrainerReply.Failed).failure).isInstanceOf(EstimateResult.Unreadable::class.java)
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(log.problems.single().kind).isEqualTo("trainer unreadable")
        assertThat(log.problems.toString()).doesNotContain(WORDS)
    }

    @Test
    fun `a refusal is logged by its status, never the provider's words`() = runTest {
        val log = RecordingLog()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"$WORDS"}}"""))

        trainer(problems = log).suggest(PLAN_REQUEST)

        assertThat(log.problems.single().kind).isEqualTo("trainer refused")
        assertThat(log.problems.single().detail).isEqualTo("the provider answered 401")
    }

    @Test
    fun `no network is unreachable`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val reply = trainer().suggest(PLAN_REQUEST)

        assertThat((reply as TrainerReply.Failed).failure).isInstanceOf(EstimateResult.Unreachable::class.java)
    }
```

  `WORDS = "Invented words that must not reach the log"`. `PLAN_REQUEST` / `REVIEW_REQUEST` are
  `TrainerRequest`s built by hand with an empty record (`sessions = emptyList()`, six `WeekFacts` of
  zeros, `weight = null`, `goal = null`, `body = null`, `Rhythm(0, 3)`); the review's session carries
  `words = WORDS`. `GOOD_PLAN` / `GOOD_FEEDBACK` as in Task 5.

- [ ] **Step 2: Run, expect compile failure.**

- [ ] **Step 3: Implement `data/ai/OpenAiTrainer.kt`:**

```kotlin
package com.metaself.app.data.ai

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.Trainer
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.TrainerRequest
import okhttp3.OkHttpClient

/**
 * The one implementation behind [Trainer] (D84). What is sent is [TrainerPrompt] and what comes back is
 * [TrainerResponse], both pure; the call is [OpenAiCall], shared with the meal estimator and the food
 * review — the key, the day's ceiling, the counting and the failures are one code path. One call, no
 * retry.
 *
 * The base URL is a parameter so a test can point it at a local server. **No test in this project
 * makes a real network call.**
 */
class OpenAiTrainer(
    keys: ApiKeyStore,
    settings: AiSettingsStore,
    client: OkHttpClient,
    profiles: RequestProfileStore,
    private val problems: ProblemLog = ProblemLog.NONE,
    baseUrl: String = OpenAiCall.OPENAI_URL,
) : Trainer {

    private val call = OpenAiCall(keys, settings, client, profiles, baseUrl)

    override suspend fun suggest(request: TrainerRequest): TrainerReply<SessionPlan> =
        ask({ model, profile -> TrainerPrompt.planBody(model, request, profile) }, TrainerResponse::parsePlan)

    override suspend fun feedback(request: TrainerRequest): TrainerReply<Feedback> =
        ask({ model, profile -> TrainerPrompt.feedbackBody(model, request, profile) }, TrainerResponse::parseFeedback)

    private suspend fun <T> ask(
        build: (String, RequestProfile) -> String,
        parse: (String, String) -> TrainerReply<T>,
    ): TrainerReply<T> = when (val outcome = call.send(build = build)) {
        is OpenAiCall.Outcome.Body -> parse(outcome.text, outcome.model).also { reply ->
            if (reply is TrainerReply.Answered) call.remember(outcome) else recorded(reply as TrainerReply.Failed, null)
        }
        is OpenAiCall.Outcome.Failed -> TrainerReply.Failed(outcome.failure).also { recorded(it, outcome.status) }
    }

    /**
     * Every failure is logged by its kind, and never in words: the request holds the owner's words and
     * the answer may quote them back; a refusal's own words may too. The screen shows the words.
     */
    private fun recorded(failed: TrainerReply.Failed, status: Int?) {
        when (failed.failure) {
            is EstimateResult.Refused -> problems.record(
                "trainer refused",
                if (status != null) "the provider answered $status" else "the call could not be made",
            )
            is EstimateResult.Unreadable -> problems.record("trainer unreadable", "the reply was not in the shape this app asked for")
            is EstimateResult.Unreachable -> problems.record("trainer unreachable", "no answer")
            else -> Unit
        }
    }
}
```

  In `AiModule`:

```kotlin
    /** The trainer (D84): the same key, ceiling, client, profiles and log as the estimator. */
    @Provides
    @Singleton
    fun provideTrainer(
        keys: ApiKeyStore,
        settings: AiSettingsStore,
        client: OkHttpClient,
        profiles: RequestProfileStore,
        problems: ProblemLog,
    ): Trainer = OpenAiTrainer(keys, settings, client, profiles, problems)
```

- [ ] **Step 4: Run, expect PASS**
  (`--tests "com.metaself.app.data.ai.OpenAiTrainerTest"`).
- [ ] **Step 5: Commit** `OpenAiTrainer.kt`, `OpenAiCall.kt`, `AiModule.kt`, the test:
  `feat: ask the trainer through the shared call (D84)`.

---

### Task 7: Two tables, version 8, and the migration

**Files:**
- Create: `data/trainer/TrainerEntities.kt`, `data/trainer/TrainerDao.kt`, `data/day/TrainerMigration.kt`
- Modify: `data/day/MetaSelfDatabase.kt` (entities, `version = 8`, `trainerDao()`), `di/DataModule.kt`
  (`MIGRATION_7_8`, `provideTrainerDao`)
- Generated: `app/schemas/com.metaself.app.data.day.MetaSelfDatabase/8.json`
- Create: `tools/check-migration-7-8.py`; modify `tools/README.md` (one line)
- Test: `app/src/test/java/com/metaself/app/data/MigrationTest.kt` (CI)

- [ ] **Step 1: Entities** `data/trainer/TrainerEntities.kt`:

```kotlin
package com.metaself.app.data.trainer

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One suggestion (D86, D88). Every one that arrives is a row; at most one is [kept] (design question
 * 1). The form's answers are by name; [minutes] 60 means "60 min or more". [suggestion] is the answer
 * in the reply's own JSON shape (`TrainerResponse.encodePlan`).
 */
@Entity(tableName = "trainer_plans")
data class TrainerPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtMillis: Long,
    /** TREADMILL_WALK, OUTDOOR_WALK, RUN, SOMETHING_ELSE. */
    val activity: String,
    val minutes: Int,
    /** FRESH, NORMAL, TIRED. */
    val feeling: String,
    /** EASY, PUSH, NOT_SURE. */
    val wish: String,
    val words: String?,
    val suggestion: String,
    val model: String,
    val kept: Boolean,
)

/**
 * The owner's words on one session (D87, D88); unique per workout. **No foreign key** to `workouts`: a
 * sync that drops a session must not silently delete his words (design question 6). [felt] is EASY,
 * RIGHT or HARD, or null. [feedback] is `TrainerResponse.encodeFeedback`'s JSON, or null.
 */
@Entity(tableName = "trainer_reviews", indices = [Index(value = ["workoutId"], unique = true)])
data class TrainerReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val planId: Long?,
    val felt: String?,
    val words: String?,
    val feedback: String?,
    val feedbackAtMillis: Long?,
    val model: String?,
)
```

- [ ] **Step 2: DAO** `data/trainer/TrainerDao.kt`:

```kotlin
package com.metaself.app.data.trainer

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.metaself.app.data.health.WorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainerDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlan(plan: TrainerPlanEntity): Long

    @Query("SELECT * FROM trainer_plans WHERE id = :id")
    suspend fun plan(id: Long): TrainerPlanEntity?

    @Query("SELECT * FROM trainer_plans WHERE id IN (:ids)")
    suspend fun plans(ids: List<Long>): List<TrainerPlanEntity>

    @Query("SELECT * FROM trainer_plans WHERE kept = 1 ORDER BY createdAtMillis DESC LIMIT 1")
    fun observeKept(): Flow<TrainerPlanEntity?>

    @Query("SELECT * FROM trainer_plans WHERE kept = 1 ORDER BY createdAtMillis DESC LIMIT 1")
    suspend fun kept(): TrainerPlanEntity?

    @Query("UPDATE trainer_plans SET kept = 0 WHERE kept = 1")
    suspend fun unkeepAll()

    @Query("UPDATE trainer_plans SET kept = :kept WHERE id = :id")
    suspend fun setKept(id: Long, kept: Boolean)

    @Query("SELECT * FROM trainer_reviews WHERE workoutId = :workoutId")
    suspend fun reviewOf(workoutId: Long): TrainerReviewEntity?

    @Query("SELECT * FROM trainer_reviews ORDER BY id")
    fun observeReviews(): Flow<List<TrainerReviewEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReview(review: TrainerReviewEntity): Long

    @Update
    suspend fun updateReview(review: TrainerReviewEntity)

    /** The sessions that have a review, whatever their day; a review whose session is gone is left out. */
    @Query("SELECT w.* FROM workouts w JOIN trainer_reviews r ON r.workoutId = w.id ORDER BY w.startedAtMillis DESC")
    fun observeReviewedWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun workout(id: Long): WorkoutEntity?

    /** Newest first, leaving out [exceptWorkoutId]'s own. */
    @Query(
        "SELECT feedback FROM trainer_reviews WHERE feedback IS NOT NULL AND workoutId != :exceptWorkoutId " +
            "ORDER BY feedbackAtMillis DESC LIMIT :count",
    )
    suspend fun latestFeedback(count: Int, exceptWorkoutId: Long): List<String>

    // The backup (D88).
    @Query("SELECT * FROM trainer_plans ORDER BY id")
    suspend fun allPlans(): List<TrainerPlanEntity>

    @Query("SELECT * FROM trainer_reviews ORDER BY id")
    suspend fun allReviews(): List<TrainerReviewEntity>

    @Query("DELETE FROM trainer_plans")
    suspend fun deletePlans()

    @Query("DELETE FROM trainer_reviews")
    suspend fun deleteReviews()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlans(plans: List<TrainerPlanEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReviews(reviews: List<TrainerReviewEntity>)
}
```

- [ ] **Step 3: Version 8.** In `MetaSelfDatabase`: add `TrainerPlanEntity::class, TrainerReviewEntity::class`
  to `entities`, `version = 8`, `abstract fun trainerDao(): TrainerDao`. In `DataModule`: import and add
  `MIGRATION_7_8` after `MIGRATION_6_7`, and

```kotlin
    @Provides
    fun provideTrainerDao(database: MetaSelfDatabase): TrainerDao = database.trainerDao()
```

  Write a placeholder `data/day/TrainerMigration.kt` with an empty `migrate` so it compiles, then
  generate the schema once (`free -m` first): `~/bin/gradlew-safe :app:compileDebugKotlin > /tmp/ms-trainer.log 2>&1; echo "exit $?"`
  (Room's KSP processor writes the schema during compilation).
  Confirm `app/schemas/com.metaself.app.data.day.MetaSelfDatabase/8.json` exists and `git status app/schemas`
  shows only it new.

- [ ] **Step 4: The migration, verbatim from `8.json`.** Read each `createSql` for `trainer_plans`,
  `trainer_reviews` and its index, replace `${TABLE_NAME}`, and write them character for character. Room
  1.6 is expected to emit exactly the following; **if `8.json` differs, `8.json` wins**:

```kotlin
package com.metaself.app.data.day

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 8: the trainer's plans and the owner's reviews of his sessions (D88).
 *
 * **Two tables and one index are created, and nothing else is read or written.** No workout, meal,
 * weight or anything else is touched. Every statement is the exported schema's own, verbatim
 * (`8.json`): Room validates the finished database against that file. `tools/check-migration-7-8.py`
 * proves the statements and the finished shape on this machine; `MigrationTest` validates in CI.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trainer_plans` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `createdAtMillis` INTEGER NOT NULL, " +
                "`activity` TEXT NOT NULL, `minutes` INTEGER NOT NULL, `feeling` TEXT NOT NULL, " +
                "`wish` TEXT NOT NULL, `words` TEXT, `suggestion` TEXT NOT NULL, `model` TEXT NOT NULL, " +
                "`kept` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trainer_reviews` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workoutId` INTEGER NOT NULL, " +
                "`planId` INTEGER, `felt` TEXT, `words` TEXT, `feedback` TEXT, " +
                "`feedbackAtMillis` INTEGER, `model` TEXT)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_trainer_reviews_workoutId` " +
                "ON `trainer_reviews` (`workoutId`)",
        )
    }
}
```

- [ ] **Step 5: `tools/check-migration-7-8.py`.** Copy `tools/check-migration-5-6.py` whole and change:
  `MIGRATION` → `data/day/TrainerMigration.kt`; `NEW_TABLES = ("trainer_plans", "trainer_reviews")`;
  versions 5/6 → 7/8 throughout (`declared(8, NEW_TABLES)`, `build(7)`, `build(8)`); the docstring's
  WHAT IT PROVES → "every statement parses; the statements are, character for character, the ones
  8.json declares for the two new tables; each new table has 8.json's shape; every table that existed
  is declared identically in 7.json and 8.json and keeps exactly its rows; a second review of the same
  workout is refused; a review with no plan is accepted". Replace the version-6-specific checks after
  the shape comparison with:

```python
    for table in old_tables:
        if entities(7)[table] != entities(8)[table]:
            failures.append(f"`{table}` is declared differently in 7.json and 8.json")

    review = ("INSERT INTO trainer_reviews (workoutId, planId, felt, words) "
              "VALUES (1, NULL, 'RIGHT', 'invented words')")
    db.execute(review)
    if not refused(db, review):
        failures.append("a second review of the same workout was accepted")
    db.execute("INSERT INTO trainer_plans (createdAtMillis, activity, minutes, feeling, wish, words, "
               "suggestion, model, kept) VALUES (1000, 'RUN', 60, 'FRESH', 'PUSH', NULL, '{}', 'm', 1)")
```

  (`entities(version)` as in `check-migration-6-7.py`.) Final line: `print(f"OK: {len(migration)}
  statements, {len(old_tables)} tables untouched, both new tables match 8.json")`. Run
  `python3 tools/check-migration-7-8.py` → `OK: …`. Add to `tools/README.md` under 6-7:
  "- `check-migration-7-8.py` — the same for version 8 (D88): the two trainer tables and the review's
  unique index created verbatim from `8.json`, every other table declared identically and every row kept."

- [ ] **Step 6: MigrationTest (CI).** Append, and import `MIGRATION_7_8`:

```kotlin
    /** D88: two new tables, empty; every workout and meal kept. Invented figures. */
    @Test
    fun `a version 7 database migrates to version 8 with empty trainer tables, keeping its workouts`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 7).use { db ->
            db.execSQL(
                "INSERT INTO workouts (id, epochDay, startedAtMillis, durationMinutes, kind, energySource, source, hidden) " +
                    "VALUES (1, 20699, 1000, 40, 'WALK', 'NONE', 'SYNCED', 0)",
            )
            db.execSQL("INSERT INTO meals (id, epochDay, loggedAtMillis, note) VALUES (1, 20699, 1000, NULL)")
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        listOf("trainer_plans" to 0, "trainer_reviews" to 0, "workouts" to 1, "meals" to 1).forEach { (table, rows) ->
            migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isEqualTo(rows)
            }
        }
        migrated.close()
    }
```

- [ ] **Step 7: Compile and run what runs locally.** `free -m`, then
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.MigrationTest" > /tmp/ms-trainer.log 2>&1; echo "exit $?"`
  → exit 0, MigrationTest **skipped** (aarch64). Python check: OK.
- [ ] **Step 8: Commit** `TrainerEntities.kt`, `TrainerDao.kt`, `TrainerMigration.kt`,
  `MetaSelfDatabase.kt`, `DataModule.kt`, `app/schemas/com.metaself.app.data.day.MetaSelfDatabase/8.json`,
  `tools/check-migration-7-8.py`, `tools/README.md`, `MigrationTest.kt`:
  `feat: tables for the trainer's plans and the owner's reviews (D88, schema 8)`.

---

### Task 8: The trainer store

**Files:**
- Create: `data/trainer/TrainerStore.kt` (ports, Room implementation, mapping)
- Modify: `di/DataModule.kt` (bind it)
- Test: `data/trainer/TrainerMappingTest.kt` (JUnit 5), `data/health/HealthRecordStoreTest.kt` (CI),
  create `data/trainer/FakeTrainerStore.kt` (test fixture for Tasks 10–15)

- [ ] **Step 1: Failing mapping tests** `TrainerMappingTest` (invented figures):

```kotlin
    @Test
    fun `a stored plan reads back with its answers and its suggestion`() {
        val entity = TrainerPlanEntity(
            id = 3, createdAtMillis = 1_000, activity = "OUTDOOR_WALK", minutes = 60, feeling = "TIRED", wish = "EASY",
            words = null, suggestion = TrainerResponse.encodePlan(PLAN), model = "a-model", kept = true,
        )

        val plan = entity.toPlan()!!

        assertThat(plan.answers).isEqualTo(PlanAnswers(PlanActivity.OUTDOOR_WALK, TimeAvailable.MIN_60_OR_MORE, Feeling.TIRED, Wish.EASY, ""))
        assertThat(plan.plan).isEqualTo(PLAN)
        assertThat(plan.toEntity()).isEqualTo(entity)
    }

    @Test
    fun `a plan whose answers or suggestion this version cannot read is none`() {
        assertThat(ENTITY.copy(activity = "SWIM").toPlan()).isNull()
        assertThat(ENTITY.copy(minutes = 25).toPlan()).isNull()
        assertThat(ENTITY.copy(suggestion = "garbled").toPlan()).isNull()
    }

    @Test
    fun `a review reads back, and feedback that cannot be read is none`() {
        val review = TrainerReview(1, workoutId = 7, planId = 3, felt = Felt.HARD, words = "Invented.", feedback = FEEDBACK, feedbackAtMillis = 2_000, model = "m")

        assertThat(review.toEntity().toReview()).isEqualTo(review)
        assertThat(review.toEntity().copy(feedback = "garbled").toReview().feedback).isNull()
        assertThat(review.toEntity().copy(felt = "MEH").toReview().felt).isNull()
    }
```

- [ ] **Step 2: Run, expect compile failure.**

- [ ] **Step 3: Implement `data/trainer/TrainerStore.kt`:**

```kotlin
package com.metaself.app.data.trainer

import com.metaself.app.data.ai.TrainerResponse
import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.data.health.toWorkout
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.Wish
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** What the Movement screen reads of the trainer: which sessions have a review (D85, design question 7). */
interface TrainerReviews {
    fun observeReviews(): Flow<List<TrainerReview>>

    companion object {
        val NONE = object : TrainerReviews {
            override fun observeReviews(): Flow<List<TrainerReview>> = flowOf(emptyList())
        }
    }
}

/** The trainer's plans and the owner's reviews (D88). */
interface TrainerStore : TrainerReviews {
    fun observeKeptPlan(): Flow<TrainerPlan?>
    fun observeReviewedWorkouts(): Flow<List<Workout>>
    suspend fun keptPlan(): TrainerPlan?
    suspend fun plans(ids: Collection<Long>): Map<Long, TrainerPlan>
    suspend fun workout(id: Long): Workout?

    /** Stores a new suggestion, not kept; its id. */
    suspend fun addPlan(plan: TrainerPlan): Long

    /** Keeps [planId] and no other, in one transaction (D86). */
    suspend fun keep(planId: Long)

    suspend fun unkeep(planId: Long)
    suspend fun reviewOf(workoutId: Long): TrainerReview?

    /** Inserts or replaces the one review of its workout; its id. */
    suspend fun putReview(review: TrainerReview): Long

    /** Newest first, at most [count], leaving out [exceptWorkoutId]'s. */
    suspend fun latestFeedback(count: Int, exceptWorkoutId: Long): List<Feedback>
}

class RoomTrainerStore @Inject constructor(
    private val dao: TrainerDao,
    private val transaction: DatabaseTransaction,
) : TrainerStore {

    override fun observeReviews(): Flow<List<TrainerReview>> = dao.observeReviews().map { rows -> rows.map { it.toReview() } }
    override fun observeKeptPlan(): Flow<TrainerPlan?> = dao.observeKept().map { it?.toPlan() }
    override fun observeReviewedWorkouts(): Flow<List<Workout>> = dao.observeReviewedWorkouts().map { rows -> rows.map { it.toWorkout() } }
    override suspend fun keptPlan(): TrainerPlan? = dao.kept()?.toPlan()
    override suspend fun plans(ids: Collection<Long>): Map<Long, TrainerPlan> =
        if (ids.isEmpty()) emptyMap() else dao.plans(ids.distinct()).mapNotNull { it.toPlan() }.associateBy { it.id }
    override suspend fun workout(id: Long): Workout? = dao.workout(id)?.toWorkout()
    override suspend fun addPlan(plan: TrainerPlan): Long = dao.insertPlan(plan.copy(id = 0, kept = false).toEntity())

    override suspend fun keep(planId: Long) = transaction.run {
        dao.unkeepAll()
        dao.setKept(planId, true)
    }

    override suspend fun unkeep(planId: Long) = dao.setKept(planId, false)
    override suspend fun reviewOf(workoutId: Long): TrainerReview? = dao.reviewOf(workoutId)?.toReview()

    override suspend fun putReview(review: TrainerReview): Long {
        val existing = dao.reviewOf(review.workoutId)
        return if (existing == null) {
            dao.insertReview(review.copy(id = 0).toEntity())
        } else {
            dao.updateReview(review.copy(id = existing.id).toEntity())
            existing.id
        }
    }

    override suspend fun latestFeedback(count: Int, exceptWorkoutId: Long): List<Feedback> =
        dao.latestFeedback(count, exceptWorkoutId).mapNotNull(TrainerResponse::readFeedback)
}

/** Null when this version cannot read the answers or the suggestion: such a plan is offered nowhere. */
fun TrainerPlanEntity.toPlan(): TrainerPlan? {
    val answers = PlanAnswers(
        activity = PlanActivity.entries.firstOrNull { it.name == activity } ?: return null,
        time = TimeAvailable.ofMinutes(minutes) ?: return null,
        feeling = Feeling.entries.firstOrNull { it.name == feeling } ?: return null,
        wish = Wish.entries.firstOrNull { it.name == wish } ?: return null,
        words = words.orEmpty(),
    )
    val plan = TrainerResponse.readPlan(suggestion) ?: return null
    return TrainerPlan(id, createdAtMillis, answers, plan, model, kept)
}

fun TrainerPlan.toEntity(): TrainerPlanEntity = TrainerPlanEntity(
    id = id, createdAtMillis = createdAtMillis, activity = answers.activity.name, minutes = answers.time.minutes,
    feeling = answers.feeling.name, wish = answers.wish.name, words = answers.words.trim().ifEmpty { null },
    suggestion = TrainerResponse.encodePlan(plan), model = model, kept = kept,
)

fun TrainerReviewEntity.toReview(): TrainerReview = TrainerReview(
    id = id, workoutId = workoutId, planId = planId,
    felt = Felt.entries.firstOrNull { it.name == felt },
    words = words, feedback = TrainerResponse.readFeedback(feedback),
    feedbackAtMillis = feedbackAtMillis, model = model,
)

fun TrainerReview.toEntity(): TrainerReviewEntity = TrainerReviewEntity(
    id = id, workoutId = workoutId, planId = planId, felt = felt?.name, words = words,
    feedback = feedback?.let(TrainerResponse::encodeFeedback), feedbackAtMillis = feedbackAtMillis, model = model,
)
```

  `DataModule`:

```kotlin
    @Provides
    @Singleton
    fun provideTrainerStore(store: RoomTrainerStore): TrainerStore = store

    @Provides
    fun provideTrainerReviews(store: TrainerStore): TrainerReviews = store
```

- [ ] **Step 4: CI tests** — append to `HealthRecordStoreTest` (it already builds an in-memory
  `MetaSelfDatabase`; keeping them here keeps the skip list at ten):

```kotlin
    /** D86: keeping one plan unkeeps any other. Invented figures. */
    @Test
    fun `keeping a plan replaces the kept one`() = runTest {
        val trainer = RoomTrainerStore(db.trainerDao(), RoomDatabaseTransaction(db))
        val first = trainer.addPlan(aTrainerPlan(createdAt = 1_000))
        val second = trainer.addPlan(aTrainerPlan(createdAt = 2_000))

        trainer.keep(first)
        trainer.keep(second)

        assertThat(trainer.keptPlan()!!.id).isEqualTo(second)
        assertThat(db.trainerDao().allPlans().count { it.kept }).isEqualTo(1)
    }

    /** D88: one review per session; saving again replaces it. */
    @Test
    fun `a session has one review, and saving again replaces it`() = runTest {
        val trainer = RoomTrainerStore(db.trainerDao(), RoomDatabaseTransaction(db))
        val workoutId = db.workoutDao().insert(aSyncedWalkEntity())

        val id = trainer.putReview(TrainerReview(workoutId = workoutId, planId = null, felt = Felt.EASY, words = null))
        val again = trainer.putReview(TrainerReview(workoutId = workoutId, planId = null, felt = Felt.HARD, words = "Invented."))

        assertThat(again).isEqualTo(id)
        assertThat(trainer.reviewOf(workoutId)!!.felt).isEqualTo(Felt.HARD)
        assertThat(trainer.observeReviewedWorkouts().first().map { it.id }).containsExactly(workoutId)
    }

    @Test
    fun `the latest feedback is newest first and leaves out the session asked about`() = runTest {
        val trainer = RoomTrainerStore(db.trainerDao(), RoomDatabaseTransaction(db))
        (1L..4L).forEach { n ->
            trainer.putReview(TrainerReview(workoutId = n, planId = null, felt = null, words = null,
                feedback = aFeedback("Headline $n"), feedbackAtMillis = n * 1_000, model = "m"))
        }

        assertThat(trainer.latestFeedback(3, exceptWorkoutId = 4).map { it.headline })
            .containsExactly("Headline 3", "Headline 2", "Headline 1").inOrder()
    }
```

  (Add small private builders `aTrainerPlan`, `aSyncedWalkEntity`, `aFeedback` in the test, invented.)

- [ ] **Step 5: The fake for later tasks** `test/.../data/trainer/FakeTrainerStore.kt`: an in-memory
  `TrainerStore` over `MutableStateFlow`s of plans, reviews and workouts (`workouts` settable by the
  test), with ids assigned from 1, `keep` unkeeping others, `putReview` replacing by `workoutId`,
  `latestFeedback` sorted by `feedbackAtMillis` descending, and a `failing: Set<String>` of method names
  that throw `IllegalStateException("disk full")`.
- [ ] **Step 6: Run** `TrainerMappingTest` (PASS) and `HealthRecordStoreTest` (skipped locally).
- [ ] **Step 7: Commit** `TrainerStore.kt`, `DataModule.kt`, `TrainerMappingTest.kt`,
  `HealthRecordStoreTest.kt`, `FakeTrainerStore.kt`: `feat: store the trainer's plans and reviews (D88)`.

---

### Task 9: The backup, format 5

**Files:**
- Modify: `domain/backup/Backup.kt`, `data/backup/BackupRepository.kt`
- Test: `domain/backup/BackupCodecTest.kt`, `data/backup/BackupRestoreOrderTest.kt`,
  `ui/screen/settings/SettingsViewModelTest.kt` (its `Daos` gains `trainer`),
  `data/backup/BackupRoundTripTest.kt` (CI)

- [ ] **Step 1: Failing codec tests.** In `BackupCodecTest` change `the format is version 4` to:

```kotlin
    @Test
    fun `the format is version 5`() {
        assertThat(Backup.CURRENT_VERSION).isEqualTo(5)
    }

    /** D88: a plan, and a review inside its workout, survive the file. Invented figures and words. */
    @Test
    fun `the trainer's plans and a session's review survive the file`() {
        val plan = BackupTrainerPlan(id = 3, createdAtMillis = 1_000, activity = "RUN", minutes = 30, feeling = "FRESH",
            wish = "PUSH", words = null, suggestion = "{\"title\":\"t\"}", model = "a-model", kept = true)
        val workout = BackupWorkout(
            epochDay = 20_699, startedAtMillis = 1_000, durationMinutes = 40, kind = "WALK", energySource = "NONE", source = "SYNCED",
            trainerReview = BackupTrainerReview(planId = 3, felt = "RIGHT", words = "Invented.", feedback = null, feedbackAtMillis = null, model = null),
        )

        val text = BackupCodec.encode(Backup(exportedAtMillis = 1, workouts = listOf(workout), trainerPlans = listOf(plan)))
        val read = BackupCodec.decode(text)!!

        assertThat(text).contains("\"trainer_plans\"")
        assertThat(text).contains("\"trainer_review\"")
        assertThat(read.trainerPlans.single()).isEqualTo(plan)
        assertThat(read.workouts.single()).isEqualTo(workout)
    }

    @Test
    fun `a version 4 file reads with no plans and no reviews`() {
        val version4 = """{"version": 4, "exported_at": 1000, "workouts": [{"epoch_day": 20699, "started_at": 1000,
            "duration_minutes": 30, "kind": "WALK", "energy_source": "NONE", "source": "SYNCED"}]}"""

        val read = BackupCodec.decode(version4)!!

        assertThat(read.trainerPlans).isEmpty()
        assertThat(read.workouts.single().trainerReview).isNull()
    }
```

- [ ] **Step 2: Failing order test.** In `BackupRestoreOrderTest`'s expected log, insert
  `"trainer.deleteReviews", "trainer.deletePlans"` after `"corrections.deleteAll"`, and
  `"trainer.insertPlans", "trainer.insertReviews"` after `"corrections.insertAll"`; the `restorer()`
  gains `trainer = dao(prefix = "trainer.")`. In `SettingsViewModelTest.Daos` add
  `val trainer: TrainerDao = table(failing)` and pass `trainer = daos.trainer`.

- [ ] **Step 3: Run, expect compile failure.**

- [ ] **Step 4: Implement.** In `Backup.kt`: `CURRENT_VERSION = 5`, with a KDoc paragraph:

```
         * Version 5 adds the trainer (D88): the stored plans, and each session's review written inside
         * its workout, so a review needs no workout id in the file. Restoring a version 1–4 file leaves
         * no plans and no reviews — a restore replaces.
```

  `Backup` gains, last: `@SerialName("trainer_plans") val trainerPlans: List<BackupTrainerPlan> = emptyList(),`.
  `BackupWorkout` gains, last: `@SerialName("trainer_review") val trainerReview: BackupTrainerReview? = null,`.
  New classes:

```kotlin
/** One of the trainer's suggestions (D88). [suggestion] is the answer's own JSON, kept as text. */
@Serializable
data class BackupTrainerPlan(
    val id: Long,
    @SerialName("created_at") val createdAtMillis: Long,
    val activity: String,
    val minutes: Int,
    val feeling: String,
    val wish: String,
    val words: String? = null,
    val suggestion: String,
    val model: String,
    val kept: Boolean = false,
)

/** The owner's words on the workout it sits in (D88). [planId] names a [BackupTrainerPlan]'s id. */
@Serializable
data class BackupTrainerReview(
    @SerialName("plan_id") val planId: Long? = null,
    val felt: String? = null,
    val words: String? = null,
    val feedback: String? = null,
    @SerialName("feedback_at") val feedbackAtMillis: Long? = null,
    val model: String? = null,
)
```

  In `BackupRepository`: constructor gains `private val trainer: TrainerDao` (after `bookkeeping`).
  Export:

```kotlin
        val reviewByWorkout = trainer.allReviews().associateBy { it.workoutId }
        // …
            workouts = workouts.all().map { it.toBackup(reviewByWorkout[it.id]) },
            // …
            trainerPlans = trainer.allPlans().map { it.toBackup() },
```

  `WorkoutEntity.toBackup(review: TrainerReviewEntity?)` sets `trainerReview = review?.let { BackupTrainerReview(it.planId, it.felt, it.words, it.feedback, it.feedbackAtMillis, it.model) }`.
  `Prepared` gains `plans: List<TrainerPlanEntity>` and `reviews: List<TrainerReviewEntity>`. In
  `prepare`, after the workouts are de-duplicated (design question 8):

```kotlin
        val keptWorkouts = backup.workouts.dedupBySyncedOrigin()
        // The table is emptied first, so the file's workouts take ids 1…n in order, and each review is
        // attached to the id its own workout receives.
        val workoutRows = keptWorkouts.mapIndexed { at, workout -> workout.toEntity().copy(id = at + 1L) }
        val planRows = backup.trainerPlans.distinctBy { it.id }.map {
            TrainerPlanEntity(it.id, it.createdAtMillis, it.activity, it.minutes, it.feeling, it.wish, it.words, it.suggestion, it.model, it.kept)
        }
        val planIds = planRows.map { it.id }.toSet()
        val reviewRows = keptWorkouts.mapIndexedNotNull { at, workout ->
            workout.trainerReview?.let {
                TrainerReviewEntity(0, at + 1L, it.planId?.takeIf(planIds::contains), it.felt, it.words, it.feedback, it.feedbackAtMillis, it.model)
            }
        }
```

  and `workouts = workoutRows, plans = planRows, reviews = reviewRows`. In `restore`'s transaction,
  after `corrections.deleteAll()`: `trainer.deleteReviews(); trainer.deletePlans()`; after
  `corrections.insertAll(prepared.corrections)`: `trainer.insertPlans(prepared.plans);
  trainer.insertReviews(prepared.reviews)`. Update the order KDoc's phases if they list tables.
  `BackupRoundTripTest.repository()` gains `trainer = db.trainerDao()`.

- [ ] **Step 5: CI round trip.** Append to `BackupRoundTripTest` (invented figures):

```kotlin
    /** D88: plans and reviews restored over themselves; each review on its own session, by its new id. */
    @Test
    fun `the trainer's plans and reviews come back on their own sessions`() = runTest {
        val first = db.workoutDao().insert(aWalk(startedAt = 1_000))
        val second = db.workoutDao().insert(aWalk(startedAt = 2_000))
        val planId = db.trainerDao().insertPlan(TrainerPlanEntity(0, 500, "RUN", 30, "FRESH", "PUSH", null, "{}", "m", true))
        db.trainerDao().insertReview(TrainerReviewEntity(0, second, planId, "HARD", "Invented.", null, null, null))

        val file = BackupCodec.decode(BackupCodec.encode(repository().export(nowMillis = 5_000)))!!
        assertThat(file.version).isEqualTo(Backup.CURRENT_VERSION)
        repository().restore(file)

        val walks = db.workoutDao().all()
        val review = db.trainerDao().allReviews().single()
        assertThat(walks.first { it.id == review.workoutId }.startedAtMillis).isEqualTo(2_000L)
        assertThat(review.planId).isEqualTo(db.trainerDao().allPlans().single().id)
        assertThat(db.trainerDao().allPlans().single().kept).isTrue()
    }
```

- [ ] **Step 6: Run** `BackupCodecTest`, `BackupRestoreOrderTest`, `SettingsViewModelTest` → PASS;
  `BackupRoundTripTest` skipped locally.
- [ ] **Step 7: Commit** `Backup.kt`, `BackupRepository.kt` and the four tests:
  `feat: the backup keeps the trainer's plans and reviews (D88, backup 5)`.

---

### Task 10: Asking the trainer — gathering, calling, storing

**Files:**
- Create: `data/trainer/AskTheTrainer.kt`
- Create (test fixtures): `data/health/FakeMovementRecord.kt` (the `FakeRecord` of
  `MovementViewModelTest`, made shared: `days`, `workouts`, `earliest` flows; no asked-lists needed),
  `data/trainer/FakeTrainer.kt` (a `Trainer` returning scripted replies and recording each request)
- Test: `data/trainer/AskTheTrainerTest.kt` (JUnit 5)

- [ ] **Step 1: Failing tests** (fixtures invented; today `TEST_EPOCH_DAY`, now 15:00 that day):

```kotlin
    @Test
    fun `a suggestion is stored, not kept, and returned`() = runTest {
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")

        val outcome = ask().suggest(ANSWERS) as AskTheTrainer.Suggested.Planned

        assertThat(outcome.plan.plan).isEqualTo(PLAN)
        assertThat(outcome.plan.kept).isFalse()
        assertThat(outcome.plan.model).isEqualTo("a-model")
        assertThat(store.plans(listOf(outcome.plan.id)).values.single().answers).isEqualTo(ANSWERS)
        assertThat((trainer.asked.single().question as TrainerQuestion.Plan).answers).isEqualTo(ANSWERS)
    }

    @Test
    fun `a failed suggestion stores nothing and says why`() = runTest {
        trainer.plans += TrainerReply.Failed(EstimateResult.NoKey)

        assertThat(ask().suggest(ANSWERS)).isEqualTo(AskTheTrainer.Suggested.Failed(EstimateResult.NoKey))
        assertThat(store.plans(listOf(1L))).isEmpty()
    }

    @Test
    fun `just save stores the words and asks nothing`() = runTest {
        val outcome = ask().save(workoutId = 1, felt = Felt.RIGHT, words = " Invented words. ", planId = null, withFeedback = false)

        assertThat(outcome).isInstanceOf(AskTheTrainer.Reviewed.Saved::class.java)
        assertThat(store.reviewOf(1)!!.words).isEqualTo("Invented words.")
        assertThat(trainer.asked).isEmpty()
    }

    /** D87: feedback is stored with the review, and the kept plan it used is cleared. */
    @Test
    fun `feedback is stored and clears the kept plan it used`() = runTest {
        val planId = store.addPlan(aStoredPlan()); store.keep(planId)
        trainer.feedback += TrainerReply.Answered(FEEDBACK, "a-model")

        val outcome = ask().save(1, Felt.HARD, "Invented.", planId, withFeedback = true) as AskTheTrainer.Reviewed.WithFeedback

        assertThat(outcome.review.feedback).isEqualTo(FEEDBACK)
        assertThat(outcome.review.feedbackAtMillis).isEqualTo(NOW)
        assertThat(store.reviewOf(1)!!.feedback).isEqualTo(FEEDBACK)
        assertThat(store.keptPlan()).isNull()
    }

    /** D87: if the call fails, the words are saved anyway. */
    @Test
    fun `a failed feedback call keeps the words`() = runTest {
        trainer.feedback += TrainerReply.Failed(EstimateResult.Unreachable())

        val outcome = ask().save(1, null, "Invented.", null, withFeedback = true) as AskTheTrainer.Reviewed.NoFeedback

        assertThat(outcome.failure).isEqualTo(EstimateResult.Unreachable())
        assertThat(store.reviewOf(1)!!.words).isEqualTo("Invented.")
        assertThat(store.reviewOf(1)!!.feedback).isNull()
    }

    @Test
    fun `the feedback request is built from the record, with the session asked about`() = runTest {
        record.workouts.value = listOf(walk(id = 1, day = TEST_EPOCH_DAY))
        weights.log(WeightReading(TEST_EPOCH_DAY, 80.0))
        trainer.feedback += TrainerReply.Answered(FEEDBACK, "a-model")

        ask().save(1, Felt.RIGHT, "Invented.", null, withFeedback = true)

        val request = trainer.asked.single()
        val question = request.question as TrainerQuestion.Review
        assertThat(question.session.felt).isEqualTo(Felt.RIGHT)
        assertThat(request.sessions.single().words).isEqualTo("Invented.")
        assertThat(request.weight!!.trendKg).isEqualTo(80.0)
        assertThat(request.body!!.ageYears).isEqualTo(46)
    }

    @Test
    fun `a session no longer in the record cannot be asked about`() = runTest {
        store.workouts.value = emptyList()

        assertThrows<IllegalStateException> { ask().save(9, Felt.EASY, "", null, withFeedback = true) }
    }
```

  `ask()` builds `AskTheTrainer(record, store, weights, FakeProfileRepository(aProfile()), trainer,
  Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }, Now { NOW }, CurrentYear { TEST_YEAR })`;
  `store.workouts` holds the synced walk with id 1 (and `record.workouts` the same unless a test sets it).

- [ ] **Step 2: Run, expect compile failure.**

- [ ] **Step 3: Implement `data/trainer/AskTheTrainer.kt`:**

```kotlin
package com.metaself.app.data.trainer

import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.weight.WeightRepository
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.Trainer
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.TrainerReview
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The trainer's two uses (D86, D87): every request is built here, fresh, from the stored record, and
 * only when a screen asks — never in the background (D84). Writes throw; the screens catch them
 * (`guarded`) and say so.
 */
class AskTheTrainer @Inject constructor(
    private val record: MovementRecord,
    private val store: TrainerStore,
    private val weights: WeightRepository,
    private val profiles: ProfileRepository,
    private val trainer: Trainer,
    private val today: Today,
    private val now: Now,
    private val year: CurrentYear,
) {

    sealed interface Suggested {
        data class Planned(val plan: TrainerPlan) : Suggested
        data class Failed(val failure: EstimateResult) : Suggested
    }

    sealed interface Reviewed {
        val review: TrainerReview

        data class Saved(override val review: TrainerReview) : Reviewed
        data class WithFeedback(override val review: TrainerReview) : Reviewed

        /** The words were saved; the call for feedback failed (D87). */
        data class NoFeedback(override val review: TrainerReview, val failure: EstimateResult) : Reviewed
    }

    suspend fun suggest(answers: PlanAnswers): Suggested =
        when (val reply = trainer.suggest(request(TrainerQuestion.Plan(answers), exceptWorkoutId = NO_WORKOUT))) {
            is TrainerReply.Failed -> Suggested.Failed(reply.failure)
            is TrainerReply.Answered -> {
                val plan = TrainerPlan(0, now(), answers, reply.value, reply.model, kept = false)
                Suggested.Planned(plan.copy(id = store.addPlan(plan)))
            }
        }

    suspend fun keep(planId: Long) = store.keep(planId)

    suspend fun save(workoutId: Long, felt: Felt?, words: String, planId: Long?, withFeedback: Boolean): Reviewed {
        val workout = store.workout(workoutId) ?: error("no session $workoutId")
        val before = store.reviewOf(workoutId)
        val review = (before ?: TrainerReview(workoutId = workoutId, planId = null, felt = null, words = null))
            .copy(planId = planId, felt = felt, words = words.trim().ifEmpty { null })
        val saved = review.copy(id = store.putReview(review))
        if (!withFeedback) return Reviewed.Saved(saved)

        val plan = planId?.let { store.plans(listOf(it))[it] }
        val question = TrainerRequest.reviewQuestion(workout, saved, plan)
        return when (val reply = trainer.feedback(request(question, exceptWorkoutId = workoutId))) {
            is TrainerReply.Failed -> Reviewed.NoFeedback(saved, reply.failure)
            is TrainerReply.Answered -> {
                val answered = saved.copy(feedback = reply.value, feedbackAtMillis = now(), model = reply.model)
                store.putReview(answered)
                if (planId != null) store.unkeep(planId)
                Reviewed.WithFeedback(answered)
            }
        }
    }

    private suspend fun request(question: TrainerQuestion, exceptWorkoutId: Long): TrainerRequest {
        val day = today().toEpochDay()
        val reviews = store.observeReviews().first()
        return TrainerRequest.of(
            question = question,
            today = day,
            workouts = record.observeWorkouts(TrainerRequest.firstDay(day), day).first(),
            reviews = reviews,
            plans = store.plans(reviews.mapNotNull { it.planId }),
            days = record.observeDays(TrainerRequest.firstSummaryDay(day), day).first(),
            readings = weights.readings.first(),
            profile = profiles.profile.first(),
            currentYear = year(),
            earlierFeedback = store.latestFeedback(TrainerRequest.FEEDBACK_COUNT, exceptWorkoutId),
        )
    }

    private companion object {
        const val NO_WORKOUT = -1L
    }
}
```

- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** `AskTheTrainer.kt`, `AskTheTrainerTest.kt`, `FakeMovementRecord.kt`,
  `FakeTrainer.kt`: `feat: ask the trainer from the stored record (D84, D86, D87)`.

---

### Task 11: The Trainer screen's three parts — pure

**Files:**
- Create: `domain/trainer/TrainerHome.kt`
- Test: `domain/trainer/TrainerHomeTest.kt`

- [ ] **Step 1: Failing tests** (invented): an unreviewed walk today at 07:00 and one yesterday → the
  waiting card is today's; today's reviewed → yesterday's; only a session three days back
  (`TEST_EPOCH_DAY - 3`) unreviewed → none; a hidden or uncounted session is never waiting; the kept
  plan is offered only within seven days (`PlanMatch.offered`); earlier sessions are the reviewed ones,
  newest first, hidden left out, each with its review.

```kotlin
    @Test
    fun `the waiting card is the newest unreviewed session of the last three days`() {
        val today = walk(1, TEST_EPOCH_DAY, hour = 7)
        val yesterday = walk(2, TEST_EPOCH_DAY - 1, hour = 18)

        assertThat(home(recent = listOf(yesterday, today)).waiting).isEqualTo(today)
        assertThat(home(recent = listOf(yesterday, today), reviews = listOf(reviewOf(1))).waiting).isEqualTo(yesterday)
        assertThat(home(recent = listOf(walk(3, TEST_EPOCH_DAY - 3, hour = 7))).waiting).isNull()
        assertThat(home(recent = listOf(today.copy(hidden = true))).waiting).isNull()
        assertThat(home(recent = listOf(today.copy(counted = false))).waiting).isNull()
    }

    @Test
    fun `earlier sessions are the reviewed ones, newest first`() {
        val older = walk(1, TEST_EPOCH_DAY - 10, hour = 7)
        val newer = walk(2, TEST_EPOCH_DAY - 2, hour = 7)
        val hidden = walk(3, TEST_EPOCH_DAY - 1, hour = 7).copy(hidden = true)

        val earlier = home(reviewed = listOf(older, newer, hidden), reviews = listOf(reviewOf(1), reviewOf(2), reviewOf(3))).earlier

        assertThat(earlier.map { it.workout.id }).containsExactly(2L, 1L).inOrder()
        assertThat(earlier.first().review.workoutId).isEqualTo(2L)
    }
```

- [ ] **Step 2: Run, expect compile failure.**
- [ ] **Step 3: Implement `domain/trainer/TrainerHome.kt`:**

```kotlin
package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.Workout

/** A session with its review, for "Earlier sessions" (D85). */
data class ReviewedSession(val workout: Workout, val review: TrainerReview)

/**
 * The Trainer screen, top to bottom (D85): the session waiting for words, the kept plan still offered,
 * and earlier reviewed sessions, newest first.
 */
data class TrainerHome(val waiting: Workout?, val keptPlan: TrainerPlan?, val earlier: List<ReviewedSession>) {
    companion object {
        /** Today, yesterday and the day before (design question 10). */
        const val WAITING_DAYS = 3

        fun of(
            today: Long,
            nowMillis: Long,
            recent: List<Workout>,
            reviewed: List<Workout>,
            reviews: List<TrainerReview>,
            kept: TrainerPlan?,
        ): TrainerHome {
            val byWorkout = reviews.associateBy { it.workoutId }
            val waiting = recent
                .filter { !it.hidden && it.counted && it.epochDay in (today - (WAITING_DAYS - 1))..today && it.id !in byWorkout }
                .maxByOrNull { it.startedAtMillis }
            val earlier = reviewed
                .filter { !it.hidden }
                .mapNotNull { workout -> byWorkout[workout.id]?.let { ReviewedSession(workout, it) } }
                .sortedByDescending { it.workout.startedAtMillis }
            return TrainerHome(waiting, PlanMatch.offered(kept, nowMillis), earlier)
        }
    }
}
```

- [ ] **Step 4: Run, expect PASS.** **Step 5: Commit** both files:
  `feat: what the Trainer screen shows (D85)`.

---

### Task 12: Wording

**Files:**
- Create: `ui/trainer/TrainerWording.kt`
- Modify: `app/src/main/res/values/strings.xml` (static labels)
- Test: `ui/trainer/TrainerWordingTest.kt` (JUnit 5; zone `ZoneOffset.UTC`; invented figures)

- [ ] **Step 1: Failing tests:**

```kotlin
    @Test
    fun `the four rows and the felt effort say the design's words`() {
        assertThat(PlanActivity.entries.map(TrainerWording::activity)).containsExactly("Treadmill walk", "Outdoor walk", "Run", "Something else").inOrder()
        assertThat(TimeAvailable.entries.map(TrainerWording::time)).containsExactly("20 min", "30 min", "45 min", "60 min or more").inOrder()
        assertThat(Feeling.entries.map(TrainerWording::feeling)).containsExactly("Fresh", "Normal", "Tired").inOrder()
        assertThat(Wish.entries.map(TrainerWording::wish)).containsExactly("Easy", "A push", "Not sure").inOrder()
        assertThat(Felt.entries.map(TrainerWording::felt)).containsExactly("Easy", "Right", "Hard").inOrder()
    }

    @Test
    fun `a session's title says its kind, its day and its time`() {
        assertThat(TrainerWording.sessionTitle(walkAt(TEST_EPOCH_DAY, 7, 40), TEST_EPOCH_DAY, UTC)).isEqualTo("Walking, today 07:40")
        assertThat(TrainerWording.sessionTitle(walkAt(TEST_EPOCH_DAY - 1, 18, 10), TEST_EPOCH_DAY, UTC)).isEqualTo("Walking, yesterday 18:10")
        assertThat(TrainerWording.sessionTitle(walkAt(TEST_EPOCH_DAY - 2, 7, 0), TEST_EPOCH_DAY, UTC)).isEqualTo("Walking, Tue 1 Sep 07:00")
    }

    /** D85's card line; D87's figures with their sources (D4). */
    @Test
    fun `a session's figures say where each came from`() {
        val walk = walkAt(TEST_EPOCH_DAY, 7, 40).copy(distanceM = 3_000, energyKcal = 200, energySource = EnergySource.BAND,
            avgHeartRate = 110, maxHeartRate = 130)

        assertThat(TrainerWording.sessionLine(walk)).isEqualTo("40 min · 3.0 km · 200 kcal · heart 110 average")
        assertThat(TrainerWording.figures(walk)).containsExactly(
            "40 min", "3.0 km (phone and band)", "200 kcal (band)", "Heart 110 avg · 130 max (from the readings)",
        ).inOrder()
        val filed = walk.copy(distanceM = 3_250, distanceSource = WorkoutFigureSource.FILE, steps = 4_000,
            stepsSource = WorkoutFigureSource.FILE, energySource = EnergySource.MET_ESTIMATE)
        assertThat(TrainerWording.figures(filed)).containsAtLeast("3.25 km (from file)", "about 200 kcal, estimated", "4,000 steps (from file)")
    }

    @Test
    fun `an earlier session's line says how it felt, whether feedback was read, and the plan as judged`() {
        val read = TrainerReview(workoutId = 1, planId = 3, felt = Felt.RIGHT, words = "Invented.", feedback = aFeedback(PlanFollowed.YES))
        assertThat(TrainerWording.earlierLine(read)).isEqualTo("Felt right · feedback read · as planned")
        assertThat(TrainerWording.earlierLine(read.copy(feedback = aFeedback(PlanFollowed.PARTLY)))).isEqualTo("Felt right · feedback read · partly as planned")
        assertThat(TrainerWording.earlierLine(read.copy(feedback = null, words = null))).isEqualTo("Felt right · no words added · no feedback yet")
        assertThat(TrainerWording.earlierLine(read.copy(felt = null, feedback = aFeedback(PlanFollowed.NO_PLAN)))).isEqualTo("feedback read")
    }

    @Test
    fun `a session row offers the next step`() {
        assertThat(TrainerWording.rowAction(null)).isEqualTo("How did it go?")
        assertThat(TrainerWording.rowAction(REVIEW.copy(feedback = null))).isEqualTo("Get feedback")
        assertThat(TrainerWording.rowAction(REVIEW.copy(feedback = aFeedback(PlanFollowed.YES)))).isEqualTo("See feedback")
    }

    @Test
    fun `a step's minutes, the advice lines and the privacy line`() {
        assertThat(TrainerWording.minutes(PlanStep(0, 8, "Warm up", ""))).isEqualTo("0–8")
        assertThat(TrainerWording.SUGGESTED).isEqualTo("Suggested by the AI trainer · advice, not a measurement")
        assertThat(TrainerWording.FROM_TRAINER).isEqualTo("From the AI trainer · advice, not a measurement")
        assertThat(TrainerWording.privacyPlan(30)).isEqualTo(
            "Sends these answers, your last six weeks of sessions and your weight trend to OpenAI with your key. " +
                "One of today's 30 AI requests.",
        )
    }

    /** Design question 16: the meal estimator's shapes, without "type the numbers". */
    @Test
    fun `failures are said as the meal estimator says them`() {
        assertThat(TrainerWording.failure(EstimateResult.NoKey)).isEqualTo("No API key yet. Add one in settings.")
        assertThat(TrainerWording.failure(EstimateResult.CeilingReached))
            .isEqualTo("You have used today's AI requests. Raise the daily limit in settings, or try tomorrow.")
        assertThat(TrainerWording.failure(EstimateResult.Unreachable())).isEqualTo("Could not reach the model. Your answers are still here.")
        assertThat(TrainerWording.failure(EstimateResult.Refused("invented"))).isEqualTo("The provider refused: invented")
        assertThat(TrainerWording.failure(EstimateResult.Unreadable("x"))).isEqualTo("The answer could not be understood.")
        assertThat(TrainerWording.savedWithoutFeedback(EstimateResult.Unreachable()))
            .isEqualTo("Your words are saved. Could not reach the model. Get feedback is on the session's row.")
    }

    @Test
    fun `feedback's four parts are headed as the design heads them`() {
        assertThat(TrainerWording.parts(aFeedback(PlanFollowed.YES)).map { it.first })
            .containsExactly("AGAINST THE PLAN", "WHAT THE NUMBERS SAY", "FOR NEXT TIME", "THIS WEEK").inOrder()
    }
```

- [ ] **Step 2: Run, expect compile failure.**

- [ ] **Step 3: Implement `ui/trainer/TrainerWording.kt`:**

```kotlin
package com.metaself.app.ui.trainer

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFigureSource
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlanStep
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.Wish
import com.metaself.app.ui.movement.MovementWeekWording
import com.metaself.app.ui.movement.WorkoutFileWording
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the trainer's three screens say (D85–D87). Every AI answer is under [SUGGESTED] or
 * [FROM_TRAINER] (D4); every figure of a session says where it came from.
 */
object TrainerWording {

    const val SUGGESTED = "Suggested by the AI trainer · advice, not a measurement"
    const val FROM_TRAINER = "From the AI trainer · advice, not a measurement"

    private const val SEP = " · "
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

    fun activity(activity: PlanActivity): String = when (activity) {
        PlanActivity.TREADMILL_WALK -> "Treadmill walk"
        PlanActivity.OUTDOOR_WALK -> "Outdoor walk"
        PlanActivity.RUN -> "Run"
        PlanActivity.SOMETHING_ELSE -> "Something else"
    }

    fun time(time: TimeAvailable): String = if (time.orMore) "${time.minutes} min or more" else "${time.minutes} min"

    fun feeling(feeling: Feeling): String = when (feeling) {
        Feeling.FRESH -> "Fresh"
        Feeling.NORMAL -> "Normal"
        Feeling.TIRED -> "Tired"
    }

    fun wish(wish: Wish): String = when (wish) {
        Wish.EASY -> "Easy"
        Wish.PUSH -> "A push"
        Wish.NOT_SURE -> "Not sure"
    }

    fun felt(felt: Felt): String = when (felt) {
        Felt.EASY -> "Easy"
        Felt.RIGHT -> "Right"
        Felt.HARD -> "Hard"
    }

    /** "Walking, today 07:40"; "…, yesterday 18:10"; "…, Tue 1 Sep 07:00". */
    fun sessionTitle(workout: Workout, today: Long, zone: ZoneId): String {
        val at = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone)
        val day = when (workout.epochDay) {
            today -> "today"
            today - 1 -> "yesterday"
            else -> LocalDate.ofEpochDay(workout.epochDay).format(DAY)
        }
        return "${MovementWeekWording.name(workout)}, $day ${at.format(TIME)}"
    }

    /** The waiting card's line (D85): "40 min · 3.0 km · 200 kcal · heart 110 average". */
    fun sessionLine(workout: Workout): String = listOfNotNull(
        MovementWeekWording.duration(workout.durationMinutes),
        workout.distanceM?.let(MovementWeekWording::km),
        workout.energyKcal?.takeIf { workout.energySource != EnergySource.NONE }?.let { "${number(it)} kcal" },
        workout.avgHeartRate?.let { "heart $it average" },
    ).joinToString(SEP)

    /** D87: the session's figures, each with where it came from (D4, D69, D82). */
    fun figures(workout: Workout): List<String> = listOfNotNull(
        MovementWeekWording.duration(workout.durationMinutes),
        workout.distanceM?.let { metres ->
            when {
                workout.distanceSource == WorkoutFigureSource.FILE -> WorkoutFileWording.fileKm(metres) + " (from file)"
                workout.distanceSource == WorkoutFigureSource.TYPED || workout.source == WorkoutSource.TYPED ->
                    MovementWeekWording.km(metres) + " (you typed it)"
                else -> MovementWeekWording.km(metres) + " (phone and band)"
            }
        },
        workout.energyKcal?.let { kcal ->
            when (workout.energySource) {
                EnergySource.BAND -> "${number(kcal)} kcal (band)"
                EnergySource.MET_ESTIMATE -> "about ${number(kcal)} kcal, estimated"
                EnergySource.TYPED -> "${number(kcal)} kcal, you set this"
                EnergySource.FILE -> "${number(kcal)} kcal, from the file"
                EnergySource.NONE -> null
            }
        },
        heart(workout),
        workout.steps?.let { "${number(it)} steps (from file)" },
    )

    private fun heart(workout: Workout): String? {
        val parts = listOfNotNull(workout.avgHeartRate?.let { "$it avg" }, workout.maxHeartRate?.let { "$it max" })
        return if (parts.isEmpty()) null else "Heart " + parts.joinToString(SEP) + " (from the readings)"
    }

    fun planned(title: String): String = "Planned: $title"

    fun minutes(step: PlanStep): String = "${step.fromMinute}–${step.toMinute}"

    /** Design question 9. */
    fun earlierLine(review: TrainerReview): String = listOfNotNull(
        review.felt?.let { "Felt " + felt(it).lowercase(Locale.US) },
        "no words added".takeIf { review.words.isNullOrBlank() && review.felt != null },
        if (review.feedback != null) "feedback read" else "no feedback yet",
        when (review.feedback?.followed) {
            PlanFollowed.YES -> "as planned"
            PlanFollowed.PARTLY -> "partly as planned"
            PlanFollowed.NO -> "not as planned"
            PlanFollowed.NO_PLAN, null -> null
        },
    ).joinToString(SEP)

    /** Design question 7. */
    fun rowAction(review: TrainerReview?): String = when {
        review == null -> "How did it go?"
        review.feedback == null -> "Get feedback"
        else -> "See feedback"
    }

    fun parts(feedback: Feedback): List<Pair<String, String>> = listOf(
        "AGAINST THE PLAN" to feedback.againstPlan,
        "WHAT THE NUMBERS SAY" to feedback.numbers,
        "FOR NEXT TIME" to feedback.nextTime,
        "THIS WEEK" to feedback.thisWeek,
    ).filter { it.second.isNotBlank() }

    fun privacyPlan(ceiling: Int): String =
        "Sends these answers, your last six weeks of sessions and your weight trend to OpenAI with your key. " +
            "One of today's $ceiling AI requests."

    fun privacyReview(ceiling: Int): String =
        "Sends this session, your words, your last six weeks of sessions and your weight trend to OpenAI with your key. " +
            "One of today's $ceiling AI requests."

    /** Design question 16. */
    fun failure(result: EstimateResult): String = when (result) {
        is EstimateResult.NoKey -> "No API key yet. Add one in settings."
        is EstimateResult.CeilingReached -> "You have used today's AI requests. Raise the daily limit in settings, or try tomorrow."
        is EstimateResult.Unreachable -> if (result.afterRefusal != null) {
            "Could not reach the model. Before that, the provider refused: ${result.afterRefusal} Your answers are still here."
        } else {
            "Could not reach the model. Your answers are still here."
        }
        is EstimateResult.Refused -> "The provider refused: ${result.detail}"
        is EstimateResult.Unreadable -> "The answer could not be understood."
        is EstimateResult.Proposed, is EstimateResult.AmountMissing -> error("not a trainer failure")
    }

    fun savedWithoutFeedback(result: EstimateResult): String =
        "Your words are saved. " + failure(result).removeSuffix(" Your answers are still here.") + " Get feedback is on the session's row."

    private fun number(value: Int): String = String.format(Locale.US, "%,d", value)
}
```

  `strings.xml`, after the `movement_` block:

```xml
    <string name="trainer_title">Trainer</string>
    <string name="trainer_open">Trainer</string>
    <string name="trainer_waiting">WAITING FOR YOUR WORDS</string>
    <string name="trainer_plan_next">Plan my next session</string>
    <string name="trainer_kept_plan">YOUR KEPT PLAN</string>
    <string name="trainer_open_plan">Open</string>
    <string name="trainer_earlier">EARLIER SESSIONS</string>
    <string name="trainer_unreadable">The trainer\'s record could not be read; Recent problems says why.</string>
    <string name="plan_title">Plan my next session</string>
    <string name="plan_row_what">WHAT</string>
    <string name="plan_row_time">TIME I HAVE</string>
    <string name="plan_row_feel">HOW I FEEL</string>
    <string name="plan_row_want">TODAY I WANT</string>
    <string name="plan_words">Anything else? (optional)</string>
    <string name="plan_ask">Ask the trainer</string>
    <string name="plan_asking">Asking the trainer…</string>
    <string name="plan_result_title">Your next session</string>
    <string name="plan_why">WHY THIS ONE</string>
    <string name="plan_keep">Keep this plan</string>
    <string name="plan_kept">Kept. It is on the Trainer screen for seven days.</string>
    <string name="plan_again">Ask again</string>
    <string name="review_title">How did it go?</string>
    <string name="review_felt">HOW IT FELT</string>
    <string name="review_words">In your words</string>
    <string name="review_voice_hint">Tap the microphone on your keyboard to speak instead of typing.</string>
    <string name="review_not_this_plan">Not this plan</string>
    <string name="review_save_ask">Save and get feedback</string>
    <string name="review_just_save">Just save</string>
    <string name="review_working">Saving and asking the trainer…</string>
    <string name="review_saved">Saved.</string>
    <string name="review_gone">This session is no longer in the record.</string>
    <string name="feedback_title">Feedback</string>
    <string name="feedback_plan_next">Plan the next one</string>
    <string name="feedback_done">Done</string>
```

- [ ] **Step 4: Run, expect PASS.** **Step 5: Commit** `TrainerWording.kt`, `strings.xml`, the test:
  `feat: what the trainer's screens say (D85–D87)`.

---

### Task 13: The Trainer screen

**Files:**
- Create: `ui/screen/trainer/TrainerViewModel.kt`, `ui/screen/trainer/TrainerScreen.kt`
- Test: `ui/screen/trainer/TrainerViewModelTest.kt` (JUnit 5, `StandardTestDispatcher` +
  `Dispatchers.setMain` as `MovementViewModelTest` does), `ui/screen/trainer/TrainerScreenRenderTest.kt`
  (Robolectric, JUnit 4, `ComposeRender`)

- [ ] **Step 1: Failing view-model tests:** the state follows the store (a review saved elsewhere moves
  a session from waiting to earlier without reopening); the recent sessions are read for
  `TEST_EPOCH_DAY - 2 .. TEST_EPOCH_DAY` (assert on `FakeMovementRecord`'s asked range if you keep it,
  else on the result); a failing read sets `unreadable` and logs kind `"trainer"` once.

- [ ] **Step 2: Implement `TrainerViewModel`:**

```kotlin
/** The Trainer screen (D85), observed: a review saved or a plan kept elsewhere shows at once. */
@HiltViewModel
class TrainerViewModel @Inject constructor(
    record: MovementRecord,
    store: TrainerStore,
    today: Today,
    now: Now,
    problems: ProblemLog,
) : ViewModel() {

    data class State(val home: TrainerHome? = null, val today: Long = 0, val unreadable: Boolean = false)

    private val day = today().toEpochDay()

    val state: StateFlow<State> = combine(
        record.observeWorkouts(day - (TrainerHome.WAITING_DAYS - 1), day),
        store.observeReviewedWorkouts(),
        store.observeReviews(),
        store.observeKeptPlan(),
    ) { recent, reviewed, reviews, kept ->
        State(TrainerHome.of(day, now(), recent, reviewed, reviews, kept), day)
    }
        .catch { failure ->
            problems.record(PROBLEM_KIND, failure.message ?: failure::class.java.simpleName)
            emit(State(today = day, unreadable = true))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State(today = day))

    companion object {
        const val PROBLEM_KIND = "trainer"
    }
}
```

- [ ] **Step 3: Failing render tests:** the title "Trainer" and Back; with a waiting session, "WAITING
  FOR YOUR WORDS", its title and line, and "How did it go?" which calls back with its id; "Plan my next
  session" is a button that calls back; with a kept plan, "YOUR KEPT PLAN", its title and "Open" which
  calls back; "EARLIER SESSIONS" with each row's name and date (`MovementWeekWording.dayHeading`) and
  line, the row a button calling back with its workout id; with nothing, only "Plan my next session";
  `unreadable` shows the trainer_unreadable sentence.

- [ ] **Step 4: Implement `TrainerScreen`:**

```kotlin
@Composable
fun TrainerScreen(
    state: TrainerViewModel.State,
    onBack: () -> Unit,
    onReview: (Long) -> Unit,
    onPlan: () -> Unit,
    onOpenKept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    MetaSelfScreen(title = stringResource(R.string.trainer_title), modifier = modifier, onBack = onBack) {
        val home = state.home
        when {
            state.unreadable -> Text(stringResource(R.string.trainer_unreadable), color = MaterialTheme.colorScheme.error)
            home == null -> Unit
            else -> {
                home.waiting?.let { session ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.Related), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                            Text(stringResource(R.string.trainer_waiting), style = MaterialTheme.typography.labelSmall)
                            Text(TrainerWording.sessionTitle(session, state.today, zone), style = MaterialTheme.typography.titleMedium)
                            Text(TrainerWording.sessionLine(session), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { onReview(session.id) }) { Text(TrainerWording.rowAction(null)) }
                        }
                    }
                }
                Button(onClick = onPlan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.trainer_plan_next)) }
                home.keptPlan?.let { kept ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                        Text(stringResource(R.string.trainer_kept_plan), style = MaterialTheme.typography.labelSmall)
                        Text(kept.plan.title, style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = onOpenKept) { Text(stringResource(R.string.trainer_open_plan)) }
                    }
                }
                if (home.earlier.isNotEmpty()) {
                    Text(stringResource(R.string.trainer_earlier), style = MaterialTheme.typography.labelSmall)
                    home.earlier.forEach { row ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button, onClick = { onReview(row.workout.id) })
                                .heightIn(min = 48.dp)
                                .padding(vertical = Spacing.Tight),
                        ) {
                            Text(
                                MovementWeekWording.name(row.workout) + " · " + MovementWeekWording.dayHeading(row.workout.epochDay, state.today),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(TrainerWording.earlierLine(row.review), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
```

  (No early return anywhere: branches only.)

- [ ] **Step 5: Run both tests, expect PASS.** **Step 6: Commit** the four files:
  `feat: the Trainer screen (D85)`.

---

### Task 14: Before a session — the plan form and the suggestion

**Files:**
- Create: `ui/screen/trainer/PlanSessionViewModel.kt`, `ui/screen/trainer/PlanSessionScreen.kt`
- Test: `ui/screen/trainer/PlanSessionViewModelTest.kt`, `ui/screen/trainer/PlanSessionScreenRenderTest.kt`

- [ ] **Step 1: Failing view-model tests** (real `AskTheTrainer` over `FakeTrainerStore`,
  `FakeTrainer`, `FakeMovementRecord`, `InMemoryWeightRepository`, `FakeProfileRepository(aProfile())`;
  a fake `AiSettingsStore` with the default ceiling):
  - Ask is not possible until all four rows are answered (`canAsk`), and asks nothing before.
  - `ask()` sets `asking`, then shows the plan (`shown`) and clears `asking`; one request only.
  - A failed ask keeps the four answers and the words, and sets `failure` (NoKey).
  - `keep()` keeps the shown plan (store's kept id) and sets `kept`; `askAgain()` returns to the form
    with the answers kept and `shown = null`.
  - Opened with `show = "kept"` and a kept plan in the store: `shown` is it, `kept = true`, the form empty.
  - A `keep` that throws (store failing on "keep") sets `refused = ActionRefused.NOTHING_CHANGED` and logs
    `"refused"`.
  - `ceiling` is the settings' daily ceiling.

- [ ] **Step 2: Implement `PlanSessionViewModel`:**

```kotlin
/** D86: the form, one ask, the suggestion, Keep and Ask again. */
@HiltViewModel
class PlanSessionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val ask: AskTheTrainer,
    private val store: TrainerStore,
    settings: AiSettingsStore,
    private val problems: ProblemLog,
) : ViewModel() {

    data class Form(
        val activity: PlanActivity? = null,
        val time: TimeAvailable? = null,
        val feeling: Feeling? = null,
        val wish: Wish? = null,
        val words: String = "",
    ) {
        fun answers(): PlanAnswers? {
            return PlanAnswers(activity ?: return null, time ?: return null, feeling ?: return null, wish ?: return null, words)
        }
    }

    data class State(
        val form: Form = Form(),
        val asking: Boolean = false,
        val failure: EstimateResult? = null,
        val shown: TrainerPlan? = null,
        val kept: Boolean = false,
        val ceiling: Int = AiSettings.DEFAULT_CEILING,
        val refused: ActionRefused? = null,
    ) {
        val canAsk: Boolean get() = form.answers() != null && !asking
    }

    private val local = MutableStateFlow(State())

    val state: StateFlow<State> = combine(local, settings.settings) { s, ai -> s.copy(ceiling = ai.dailyCeiling) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    init {
        if (savedState.get<String>(SHOW) == KEPT) {
            guarded(problems, onRefused = { local.update { it.copy(refused = ActionRefused.COULD_NOT_OPEN) } }) {
                store.keptPlan()?.let { kept -> local.update { it.copy(shown = kept, kept = true) } }
            }
        }
    }

    fun change(form: Form) = local.update { it.copy(form = form, failure = null) }

    fun ask() {
        val answers = local.value.form.answers() ?: return
        if (local.value.asking) return
        local.update { it.copy(asking = true, failure = null, refused = null) }
        guarded(problems, onRefused = { local.update { it.copy(asking = false, refused = ActionRefused.NOTHING_CHANGED) } }) {
            when (val outcome = ask.suggest(answers)) {
                is AskTheTrainer.Suggested.Planned -> local.update { it.copy(asking = false, shown = outcome.plan, kept = false) }
                is AskTheTrainer.Suggested.Failed -> local.update { it.copy(asking = false, failure = outcome.failure) }
            }
        }
    }

    fun keep() {
        val plan = local.value.shown ?: return
        guarded(problems, onRefused = { local.update { it.copy(refused = ActionRefused.NOTHING_CHANGED) } }) {
            ask.keep(plan.id)
            local.update { it.copy(kept = true) }
        }
    }

    /** Back to the form, the answers kept (D86); from a kept plan opened on its own, the form is empty. */
    fun askAgain() = local.update { it.copy(shown = null, kept = false, failure = null) }

    companion object {
        const val SHOW = "show"
        const val FORM = "form"
        const val KEPT = "kept"
    }
}
```

  (`Form.answers()` uses `?: return null` inside a plain function — not a composable — so the inline
  composable guard does not apply.)

- [ ] **Step 3: Failing render tests:** the form shows the four row labels and every choice as chips, a
  chip click calls back with the changed form; "Ask the trainer" is disabled until `canAsk` and calls
  back; the privacy line with the ceiling; `asking` shows "Asking the trainer…"; a failure shows
  `TrainerWording.failure(...)`. With `shown`: "Your next session", the plan's title, the advice line
  `TrainerWording.SUGGESTED`, each step's "0–10" minutes, what and how, "WHY THIS ONE" and the why; "Keep
  this plan" and "Ask again" as buttons; with `kept`, "Kept. It is on the Trainer screen for seven days."
  and no "Keep this plan".

- [ ] **Step 4: Implement `PlanSessionScreen`** with
  `(state, onBack, onChange: (Form) -> Unit, onAsk, onKeep, onAskAgain)`. Title: `plan_title` for the form,
  `plan_result_title` when `shown != null`. The form: for each row a label (`labelMedium`) and a
  `FlowRow` of `FilterChip`s exactly as `WorkoutSheet` draws its kinds (opt in to
  `ExperimentalLayoutApi`), then `OutlinedTextField(value = form.words, label = plan_words)`, then the
  Ask button (`enabled = state.canAsk`, text `plan_asking` while asking), the privacy line
  (`bodySmall`, `onSurfaceVariant`), and the failure in `colorScheme.error`. The suggestion: title
  (`titleLarge`), `TrainerWording.SUGGESTED` (`labelSmall`), one `Row` per step (minutes in a fixed
  56 dp column; what in `titleSmall`, how in `bodyMedium` only when not blank), `plan_why` heading and
  the why, then Keep (hidden when `kept`, replaced by the `plan_kept` line) and Ask again. A `refused`
  shows `stringResource(refused.sentence)`. **Branch with `when`, never return early.**

- [ ] **Step 5: Run both tests, expect PASS.** **Step 6: Commit** the four files:
  `feat: plan my next session (D86)`.

---

### Task 15: After a session — the review and the feedback

**Files:**
- Create: `ui/screen/trainer/ReviewSessionViewModel.kt`, `ui/screen/trainer/ReviewSessionScreen.kt`
- Test: `ui/screen/trainer/ReviewSessionViewModelTest.kt`, `ui/screen/trainer/ReviewSessionScreenRenderTest.kt`

- [ ] **Step 1: Failing view-model tests** (fakes as Task 14; `SavedStateHandle(mapOf("workoutId" to 1L))`):
  - Opening loads the session; a kept plan made two hours before it started is the matched `plan`; one
    made eight days before is not.
  - An existing review opens with its felt, words and its own plan (by `planId`), whether or not kept.
  - An existing review with feedback opens on the feedback (`feedback != null`).
  - `notThisPlan()` sets `plan = null`; the save then stores `planId = null`.
  - `canSave` is false with no felt and blank words, true with either.
  - `justSave()` stores and sets `saved`; asks nothing.
  - `saveAndAsk()` with a good reply shows the feedback and the kept plan is no longer kept.
  - `saveAndAsk()` with a failed reply keeps felt and words on screen, sets `failure`, and the store holds
    the words (D87).
  - A missing session sets `gone`.
  - A store that throws on `putReview` sets `refused = MAYBE_PARTIAL` and keeps the words on screen.

- [ ] **Step 2: Implement `ReviewSessionViewModel`:**

```kotlin
/** D87 for one session: its figures, the matched plan, the felt effort and words, then feedback. */
@HiltViewModel
class ReviewSessionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val ask: AskTheTrainer,
    private val store: TrainerStore,
    settings: AiSettingsStore,
    today: Today,
    private val problems: ProblemLog,
) : ViewModel() {

    data class State(
        val workout: Workout? = null,
        val plan: TrainerPlan? = null,
        val felt: Felt? = null,
        val words: String = "",
        val working: Boolean = false,
        val saved: Boolean = false,
        val feedback: Feedback? = null,
        val failure: EstimateResult? = null,
        val gone: Boolean = false,
        val today: Long = 0,
        val ceiling: Int = AiSettings.DEFAULT_CEILING,
        val refused: ActionRefused? = null,
    ) {
        val canSave: Boolean get() = workout != null && !working && (felt != null || words.isNotBlank())
    }

    private val workoutId: Long = requireNotNull(savedState.get<Long>(WORKOUT_ID)) { "a review needs its session" }
    private val local = MutableStateFlow(State(today = today().toEpochDay()))

    val state: StateFlow<State> = combine(local, settings.settings) { s, ai -> s.copy(ceiling = ai.dailyCeiling) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        guarded(problems, onRefused = { local.update { it.copy(refused = ActionRefused.COULD_NOT_OPEN) } }) {
            val workout = store.workout(workoutId)
            if (workout == null) {
                local.update { it.copy(gone = true) }
            } else {
                val review = store.reviewOf(workoutId)
                val plan = if (review != null) {
                    review.planId?.let { store.plans(listOf(it))[it] }
                } else {
                    PlanMatch.forSession(store.keptPlan(), workout)
                }
                local.update {
                    it.copy(workout = workout, plan = plan, felt = review?.felt, words = review?.words.orEmpty(), feedback = review?.feedback)
                }
            }
        }
    }

    fun feel(felt: Felt) = local.update { it.copy(felt = felt, saved = false) }

    fun words(words: String) = local.update { it.copy(words = words, saved = false) }

    fun notThisPlan() = local.update { it.copy(plan = null, saved = false) }

    fun justSave() = save(withFeedback = false)

    fun saveAndAsk() = save(withFeedback = true)

    private fun save(withFeedback: Boolean) {
        val now = local.value
        if (!now.canSave) return
        local.update { it.copy(working = true, failure = null, refused = null) }
        guarded(problems, onRefused = { local.update { it.copy(working = false, refused = ActionRefused.MAYBE_PARTIAL) } }) {
            when (val outcome = ask.save(workoutId, now.felt, now.words, now.plan?.id, withFeedback)) {
                is AskTheTrainer.Reviewed.Saved -> local.update { it.copy(working = false, saved = true) }
                is AskTheTrainer.Reviewed.WithFeedback -> local.update { it.copy(working = false, saved = true, feedback = outcome.review.feedback) }
                is AskTheTrainer.Reviewed.NoFeedback -> local.update { it.copy(working = false, saved = true, failure = outcome.failure) }
            }
        }
    }

    companion object {
        const val WORKOUT_ID = "workoutId"
    }
}
```

- [ ] **Step 3: Failing render tests:** the form shows "How did it go?", the session title
  (`TrainerWording.sessionTitle`), every figure line, "Planned: …" with "Not this plan" (a button
  calling back) when a plan is matched, "HOW IT FELT" with Easy/Right/Hard chips (the picked one
  `isSelected`), the words field with its label and the voice hint, "Save and get feedback" and "Just
  save" disabled until `canSave`, the privacy line; `working` shows "Saving and asking the trainer…";
  `failure` after a save shows `TrainerWording.savedWithoutFeedback(...)`; `saved` without a failure and
  without feedback shows "Saved."; `gone` shows the review_gone sentence only. With `feedback`: title
  "Feedback", the headline, `TrainerWording.FROM_TRAINER`, the four headed parts in order, "Plan the
  next one" and "Done" as buttons calling back.

- [ ] **Step 4: Implement `ReviewSessionScreen`** with
  `(state, onBack, onFeel, onWords, onNotThisPlan, onSaveAndAsk, onJustSave, onPlanNext, onDone)`, the
  title `feedback_title` when `state.feedback != null` else `review_title`, drawing the two views with
  `when`, the chips as a `FlowRow` of `FilterChip` (as `WorkoutSheet`), the words an
  `OutlinedTextField` (`minLines = 3`, `keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)`)
  with the voice hint as its `supportingText`. **No early return.**

- [ ] **Step 5: Run both tests, expect PASS.** **Step 6: Commit** the four files:
  `feat: how did it go, and the trainer's feedback (D87)`.

---

### Task 16: The two entry points on Movement, and the three destinations

**Files:**
- Modify: `ui/screen/movement/MovementUiState.kt` (+ `reviews: Map<Long, TrainerReview> = emptyMap()`)
- Modify: `ui/screen/movement/MovementViewModel.kt` (+ `trainer: TrainerReviews = TrainerReviews.NONE` as
  the **last** primary-constructor parameter, after `ioDispatcher`; the `@Inject` constructor takes it and
  passes it)
- Modify: `ui/screen/movement/MovementScreen.kt` (+ `onTrainer: () -> Unit = {}`, `onReview: (Long) -> Unit = {}`)
- Modify: `ui/nav/MetaSelfNavHost.kt`
- Test: `ui/screen/movement/MovementViewModelTest.kt`, `ui/screen/movement/MovementScreenRenderTest.kt`

- [ ] **Step 1: Failing tests.** View model: with a `FakeTrainerStore` holding a review of workout 1,
  `state.reviews[1L]` is it; a review saved later shows without reopening. Render (invented week as the
  file's own): the top bar has a "Trainer" button that calls `onTrainer`; with today open, under the
  synced run's line there is "How did it go?" calling `onReview(1)`; with `reviews = mapOf(1L to
  review without feedback)` it reads "Get feedback"; with feedback "See feedback"; a closed day shows
  none.

- [ ] **Step 2: Implement.** In `MovementViewModel`, add `trainer.observeReviews()` as a fifth flow of
  the week's inner `combine` and carry `reviews.associateBy { it.workoutId }` in `Read`, then into
  `MovementUiState.reviews`. In `MovementScreen`:

```kotlin
        actions = { TextButton(onClick = onTrainer) { Text(stringResource(R.string.trainer_open)) } },
```

  and pass `reviews = state.reviews` and `onReview` to `DayRow`; under each detail line that has a
  `workout` (typed or synced), draw

```kotlin
                        line.workout?.let { session ->
                            TextButton(onClick = { onReview(session.id) }) { Text(TrainerWording.rowAction(reviews[session.id])) }
                        }
```

  In `MetaSelfNavHost`, three destinations:

```kotlin
    /** The trainer (D85). */
    data object Trainer : Destination("trainer")

    /** Plan my next session (D86); `show=kept` opens the kept plan. */
    data object PlanSession : Destination("trainer/plan?show={show}") {
        fun form(): String = "trainer/plan?show=" + PlanSessionViewModel.FORM
        fun kept(): String = "trainer/plan?show=" + PlanSessionViewModel.KEPT
    }

    /** How did it go (D87), for one session. */
    data object ReviewSession : Destination("trainer/review/{workoutId}") {
        fun of(workoutId: Long): String = "trainer/review/$workoutId"
    }
```

  Movement's `MovementScreen(...)` gains `onTrainer = { navController.navigate(Destination.Trainer.route) }`
  and `onReview = { navController.navigate(Destination.ReviewSession.of(it)) }`. Composables:

```kotlin
        composable(Destination.Trainer.route) {
            val trainerViewModel: TrainerViewModel = hiltViewModel()
            val trainerState by trainerViewModel.state.collectAsStateWithLifecycle()
            TrainerScreen(
                state = trainerState,
                onBack = { navController.popBackStack() },
                onReview = { navController.navigate(Destination.ReviewSession.of(it)) },
                onPlan = { navController.navigate(Destination.PlanSession.form()) },
                onOpenKept = { navController.navigate(Destination.PlanSession.kept()) },
            )
        }

        composable(
            route = Destination.PlanSession.route,
            arguments = listOf(navArgument(PlanSessionViewModel.SHOW) { type = NavType.StringType; defaultValue = PlanSessionViewModel.FORM }),
        ) {
            val planViewModel: PlanSessionViewModel = hiltViewModel()
            val planState by planViewModel.state.collectAsStateWithLifecycle()
            PlanSessionScreen(
                state = planState,
                onBack = { navController.popBackStack() },
                onChange = planViewModel::change,
                onAsk = planViewModel::ask,
                onKeep = planViewModel::keep,
                onAskAgain = planViewModel::askAgain,
            )
        }

        composable(
            route = Destination.ReviewSession.route,
            arguments = listOf(navArgument(ReviewSessionViewModel.WORKOUT_ID) { type = NavType.LongType }),
        ) {
            val reviewViewModel: ReviewSessionViewModel = hiltViewModel()
            val reviewState by reviewViewModel.state.collectAsStateWithLifecycle()
            ReviewSessionScreen(
                state = reviewState,
                onBack = { navController.popBackStack() },
                onFeel = reviewViewModel::feel,
                onWords = reviewViewModel::words,
                onNotThisPlan = reviewViewModel::notThisPlan,
                onSaveAndAsk = reviewViewModel::saveAndAsk,
                onJustSave = reviewViewModel::justSave,
                onPlanNext = {
                    navController.navigate(Destination.PlanSession.form()) {
                        popUpTo(Destination.ReviewSession.route) { inclusive = true }
                    }
                },
                onDone = { navController.popBackStack() },
            )
        }
```

- [ ] **Step 3: Run** `MovementViewModelTest`, `MovementScreenRenderTest`, and
  `com.metaself.app.ui.InlineComposableReturnGuardTest` → PASS.
- [ ] **Step 4: Commit** the six files: `feat: Trainer and How did it go? on Movement (D85)`.

---

### Task 17: Suite, lint, build, and version 0.64.0

**Files:** `app/build.gradle.kts` (`versionCode = 120`, `versionName = "0.64.0"`).

- [ ] **Step 1:** `free -m` (≥ 4000 MB available); whole suite
  `~/bin/gradlew-safe :app:testDebugUnitTest > /tmp/ms-trainer.log 2>&1; echo "exit $?"` → exit 0. From
  `app/build/test-results/testDebugUnitTest/*.xml`: 0 failures, 0 errors, and **skipped exactly the ten
  SQLite classes** of `CLAUDE.md` (the new CI tests live inside `MigrationTest`, `HealthRecordStoreTest`
  and `BackupRoundTripTest`).
- [ ] **Step 2:** `free -m`; `~/bin/gradlew-safe :app:lintDebug > /tmp/ms-trainer-lint.log 2>&1; echo "exit $?"` → 0.
- [ ] **Step 3:** `python3 tools/check-migration-7-8.py` → `OK`; `git status app/schemas` → only `8.json` new.
- [ ] **Step 4:** Bump `versionCode` 119 → 120 and `versionName` "0.63.0" → "0.64.0"; `free -m`;
  `~/bin/gradlew-safe :app:assembleDebug > /tmp/ms-trainer-build.log 2>&1; echo "exit $?"` → 0.
- [ ] **Step 5: Commit** `app/build.gradle.kts`: `0.64.0: the trainer, before and after a session (D84–D88)`.
  **The release APK is built by the controller with `~/bin/ms-release`** (needs ≥ 6000 MB available), not
  here. No push.

**Phone checks owed (not provable here):** the migration on the real database (after CI's
`MigrationTest`); a real plan and a real feedback round trip with the owner's key; the privacy line's
ceiling matches Settings; "How did it go?" on a synced and a typed session row; the keyboard's
microphone in the words box; a restore of a format 5 file keeps each review on its session; chips wrap
at phone width; the kept plan disappears from the Trainer screen after seven days.
