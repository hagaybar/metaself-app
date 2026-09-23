# Measured rate of weight change (D47) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The weight screen states the rate the smoothed trend has actually been moving at, beside the rate that was chosen in setup, with a finish line for each. Public issue #17.

**Architecture:** Three units. `MeasuredRate` measures the trend over a 28-day window and knows nothing about goals. `GoalForecast` is the only unit that knows about both a goal and a measurement; it owns the two-year rule and resolves the measurement's sign against the goal's direction. `GoalWording` turns a forecast into sentences. No existing caller of `GoalProgress` changes (`Milestones.reached` and `DayViewModel` use it for distance and arrival only).

**Tech Stack:** Kotlin 1.9.22, JVM 17. JUnit 5 + Truth for the pure units and the view model; JUnit 4 + Robolectric for the one render test, because Robolectric's runner is JUnit 4. No database, no new dependency.

**Spec:** `docs/superpowers/specs/2026-09-21-measured-rate-design.md`. Read §3 and §4 before starting; the sentence table in §4 is the contract, and §4's worked example (below) is the one every test here uses.

**The worked example — invented, round on purpose, and the same as spec §4:** a trend at 80 kg against a target of 72 kg, a goal to lose at 0.5 kg a week, drawn on 8 August 2024 (epoch day 19,943). 8 kg to go; at 0.5 kg a week that is 16 weeks = 112 days → 28 November 2024; at a measured 0.25 kg a week it is 32 weeks = 224 days → 20 March 2025. The screen reads:

```
8 kg to go, to 72 kg
At the 0.5 kg a week you're aiming for: about 16 weeks, around November 2024.
Over the last 28 days you've averaged 0.25 kg a week: about 32 weeks, around March 2025.
```

**Before every Gradle command:** `export ANDROID_HOME=/home/ubuntu/android-sdk`
**Never run bare `./gradlew`** — a hook blocks it. Use `~/bin/gradlew-safe` with the same arguments.
**Never pipe a build whose result you report** — redirect to a file and check `$?`.
**Stage by explicit path only.** Never `git add -A`.

---

## Changes from the archived plan

The archived plan (`docs/superpowers/plans/2026-09-21-measured-rate.md` in the archived private
repository) was written against older code and a different worked example. This copy differs in:

1. **The worked example is the spec's.** Every goal figure, derived week count, month name and finish
   day in the tests is recomputed from 80 → 72 kg at 0.5 and 0.25 kg a week on 8 August 2024. The
   `GoalWordingTest` fixture's starting trend is 82 kg, as the current test's is.
2. **The window opens `WINDOW_DAYS` days before today, not `WINDOW_DAYS - 1`.** The archived code
   copied `MeasuredBurnCalculator`'s `todayEpochDay - WINDOW_DAYS + 1`, which counts 28 calendar days
   *inclusive* — right for averaging 28 days of food, wrong for a span between two points: a record
   with a reading every day then measures 27 days, and neither the archived `MeasuredRateTest`
   (`spanDays == 28`) nor its render test (`"Over the last 28 days"`) could pass. Spec §4 says 28 is
   the usual case. The window start is now `todayEpochDay - WINDOW_DAYS`.
3. **`MeasuredRateTest`'s shorter-span test is rebuilt.** The archived one used a trend starting 21
   days ago, which has no point near the window's start and so returned null (the test would have
   thrown on `!!`). The 21-day span now runs from 24 days ago to 3 days ago.
4. **`MIN_SPAN_DAYS` cannot fire at today's constants, and the plan says so.** A start point must be
   at most `GAP_DAYS` after the window opens and the end point at most `GAP_DAYS` before today, so the
   span is at least 28 − 4 − 4 = 20 days. The check is kept as a guard against those constants moving,
   its KDoc states this, and the archived "too short a span" test is renamed for what it actually
   exercises (a fortnight of history: no point near the window's start). Spec §8's "null on each of
   the three conditions in turn" is met for two of them; the third is unreachable.
5. **Removing `GoalProgress.weeksToGo` moves from Task 2 to Task 3.** `GoalWording.projection` reads
   it, so removing it in Task 2 broke the main build and no Task 2 test could run. Task 2 now only
   adds `GoalForecast` and is green on its own. Task 3 rewrites the wording and removes the property
   together.
6. **Tasks 3 and 4 are one commit, stated up front** rather than as an "if the build fails" fallback:
   once `projection` takes a `GoalForecast`, `WeightScreen.kt` does not compile until Task 4 wires it,
   and Gradle compiles main before running any test.
7. **Current code reconciled:** the existing `GoalWordingTest` (77.2 → 70 kg example) and
   `GoalProgressTest` edits are quoted from today's files, including the two comments whose figures
   would otherwise go stale; `GoalProgress`'s one-line summary is updated too. The weight screen's
   projection text is now `onSurfaceVariant`, not `secondary`, and sits above the "Change your goal"
   button, which is untouched. `WeightViewModel`'s `combine` block is at lines 43-55, and
   `WeightScreen`'s projection block at 143-149.
8. **`ui/Guarded.kt` is not involved.** It guards the view model's *actions*; this work changes only
   the `state` flow, which Guarded's rules leave out of scope (long-lived observed flows).
9. **A view-model test is added** (Task 4), because the render test builds its own `WeightUiState`
   and would pass even if the view model never set `forecast`.
10. **Commit templates carry this session's attribution lines.**

---

### Task 1: `MeasuredRate` — how fast the trend actually moved

**Files:**
- Create: `app/src/main/java/com/metaself/app/domain/weight/MeasuredRate.kt`
- Create: `app/src/test/java/com/metaself/app/domain/weight/MeasuredRateTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/metaself/app/domain/weight/MeasuredRateTest.kt`:

```kotlin
package com.metaself.app.domain.weight

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MeasuredRateTest {

    /** 2.8 kg down across 28 days is 0.7 kg a week, and the sign says downwards. */
    @Test
    fun `a falling trend measures a negative rate`() {
        val rate = MeasuredRate.of(trend(fromDaysAgo = 28, changeKg = -2.8), todayEpochDay = TODAY)!!

        assertThat(rate.spanDays).isEqualTo(28)
        assertThat(rate.changeKg).isWithin(1e-9).of(-2.8)
        assertThat(rate.kgPerWeek).isWithin(1e-9).of(-0.7)
        assertThat(rate.asOfEpochDay).isEqualTo(TODAY)
    }

    /** A measurement of a line has no opinion about which way the owner wanted to go. */
    @Test
    fun `a rising trend measures a positive rate`() {
        val rate = MeasuredRate.of(trend(fromDaysAgo = 28, changeKg = 1.0), todayEpochDay = TODAY)!!

        // 1 kg over 28 days = 1/4 kg a week.
        assertThat(rate.kgPerWeek).isWithin(1e-9).of(0.25)
    }

    /**
     * The divisor is the span actually observed, NOT the 28-day window. The same 2.8 kg over 21
     * days is a faster rate, and a calculator dividing by a constant would report them identical.
     *
     * The span runs from 24 days ago — the latest a start point may sit, GAP_DAYS after the window
     * opens — to 3 days ago, within GAP_DAYS of today.
     */
    @Test
    fun `the divisor is the observed span and not the window`() {
        val short = MeasuredRate.of(
            trend(fromDaysAgo = 24, toDaysAgo = 3, changeKg = -2.8),
            todayEpochDay = TODAY,
        )!!

        assertThat(short.spanDays).isEqualTo(21)
        assertThat(short.kgPerWeek).isWithin(1e-9).of(-2.8 / 21.0 * 7.0)
        assertThat(short.asOfEpochDay).isEqualTo(TODAY - 3)
    }

    /**
     * A fortnight of history has no point near the window's start, so there is nothing to measure
     * from. (MIN_SPAN_DAYS would refuse it too, but cannot be reached first at today's constants:
     * see its KDoc.)
     */
    @Test
    fun `a fortnight of history measures nothing`() {
        assertThat(MeasuredRate.of(trend(fromDaysAgo = 13, changeKg = -1.0), TODAY)).isNull()
    }

    /** A rate that ended a fortnight ago is not a rate he is managing now. */
    @Test
    fun `a stale last reading measures nothing`() {
        val stale = trend(fromDaysAgo = 28, changeKg = -2.8).map {
            it.copy(reading = it.reading.copy(epochDay = it.reading.epochDay - 5))
        }

        assertThat(MeasuredRate.of(stale, todayEpochDay = TODAY)).isNull()
    }

    @Test
    fun `nothing at all with no readings`() {
        assertThat(MeasuredRate.of(emptyList(), TODAY)).isNull()
    }

    /** One reading is a weight, not a rate. */
    @Test
    fun `a single reading measures nothing`() {
        val one = listOf(TrendPoint(WeightReading(epochDay = TODAY, kg = 80.0), trendKg = 80.0))

        assertThat(MeasuredRate.of(one, TODAY)).isNull()
    }

    private companion object {
        const val TODAY = 20_000L

        /** A straight line of daily points from [fromDaysAgo] to [toDaysAgo], moving [changeKg]. */
        fun trend(fromDaysAgo: Int, toDaysAgo: Int = 0, changeKg: Double): List<TrendPoint> {
            val days = fromDaysAgo - toDaysAgo
            val startDay = TODAY - fromDaysAgo
            val startKg = 80.0
            return (0..days).map { n ->
                val kg = startKg + changeKg * n / days
                TrendPoint(WeightReading(epochDay = startDay + n, kg = kg), trendKg = kg)
            }
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.weight.MeasuredRateTest" > /tmp/t1.log 2>&1; echo "exit $?"
```

Expected: non-zero exit; the log shows "Unresolved reference: MeasuredRate".

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/metaself/app/domain/weight/MeasuredRate.kt`:

```kotlin
package com.metaself.app.domain.weight

/**
 * How fast the smoothed trend has ACTUALLY been moving.
 *
 * This is arithmetic about the past and nothing may word it as a prediction: how far the line moved,
 * divided by how long it took. `WeightTrend.changeKg` is the numerator's idea and already argues the
 * case for measuring the smoothed line rather than the readings — a rate computed from two mornings
 * would report a gain across a fortnight of loss whenever the last morning was salty (D11).
 *
 * Knows nothing about goals, deliberately. Which direction the owner WANTS to go is
 * [com.metaself.app.domain.goal.GoalForecast]'s business, and a measurement that already took a side
 * could not be reused by anything that took the other one.
 *
 * @property spanDays the days actually observed between the two trend points used — NOT [WINDOW_DAYS].
 *   Whatever displays this must state it, because it is the denominator and it varies.
 * @property changeKg signed: negative means the trend went DOWN. The opposite convention to
 *   [com.metaself.app.domain.profile.Goal.kgPerWeek], which is a positive magnitude with the
 *   direction carrying the sign — right for an intention, wrong for an observation.
 * @property asOfEpochDay the latest trend point used, which is at most [GAP_DAYS] before today.
 */
data class MeasuredRate(
    val spanDays: Int,
    val changeKg: Double,
    val kgPerWeek: Double,
    val asOfEpochDay: Long,
) {
    companion object {

        /**
         * Matching `MeasuredBurnCalculator.WINDOW_DAYS` exactly, and for its stated reason: long
         * enough that water and glycogen stop dominating, short enough to still be about now. The
         * two figures on screen agreeing about what "recently" means is worth more than tuning them
         * apart.
         *
         * The window opens [WINDOW_DAYS] days BEFORE today, so a reading every day spans exactly
         * 28 days. The measured burn opens one day later because it counts 28 days of food
         * inclusive; a rate is a distance between two points and counts the gap.
         */
        const val WINDOW_DAYS = 28

        /** A weight from before the window began is not a reading of where the window began. */
        const val GAP_DAYS = 4

        /**
         * Multiplying a four-day change by 1.75 to reach a week reports noise as a rate.
         *
         * At today's constants this cannot fire before the gap rules do: the start is at most
         * [GAP_DAYS] after the window opens and the end at most [GAP_DAYS] before today, so any
         * span that gets this far is at least 28 - 4 - 4 = 20 days. It stays as the guard that
         * holds if either constant moves.
         */
        const val MIN_SPAN_DAYS = 14

        /**
         * Null whenever there is nothing truthful to say. The screen then shows nothing at all
         * rather than a placeholder, because a figure marked "—" invites the owner to wonder what
         * is broken.
         */
        fun of(trend: List<TrendPoint>, todayEpochDay: Long): MeasuredRate? {
            val windowStart = todayEpochDay - WINDOW_DAYS

            val end = trend.lastOrNull()
                ?.takeIf { it.reading.epochDay >= todayEpochDay - GAP_DAYS }
                ?: return null

            // The point at or before the window opened, or failing that the first one shortly
            // after it — the same fallback, and the same constant, as the measured burn uses.
            val start = trend.lastOrNull { it.reading.epochDay <= windowStart }
                ?: trend.firstOrNull { it.reading.epochDay <= windowStart + GAP_DAYS }
                ?: return null

            val spanDays = (end.reading.epochDay - start.reading.epochDay).toInt()
            if (spanDays < MIN_SPAN_DAYS) return null

            val changeKg = end.trendKg - start.trendKg

            return MeasuredRate(
                spanDays = spanDays,
                changeKg = changeKg,
                kgPerWeek = changeKg / spanDays * DAYS_PER_WEEK,
                asOfEpochDay = end.reading.epochDay,
            )
        }

        private const val DAYS_PER_WEEK = 7.0
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.weight.MeasuredRateTest" > /tmp/t1.log 2>&1; echo "exit $?"
```

Expected: exit 0, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/metaself/app/domain/weight/MeasuredRate.kt \
        app/src/test/java/com/metaself/app/domain/weight/MeasuredRateTest.kt
git commit -m "feat: measure the trend's own rate of change over 28 days (D47)

The numerator is the idea behind WeightTrend.changeKg, which already
argues for measuring the smoothed line rather than the readings. This is
that idea given a denominator.

The denominator is the span actually observed, not WINDOW_DAYS: the
measured burn averages over its window and may divide by a constant, a
rate may not. The window opens WINDOW_DAYS before today, so a daily
record spans exactly 28 days.

Sign is direction-agnostic — negative is downwards. Goal.kgPerWeek's
convention (positive magnitude, direction carries the sign) is right for
an intention and wrong for an observation, so it is not copied here.

Refs #17

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01LUpjHa9kZic9ZJrYzja45g"
```

---

### Task 2: `GoalForecast` — both projections, and the two-year rule

**Files:**
- Create: `app/src/main/java/com/metaself/app/domain/goal/GoalForecast.kt`
- Create: `app/src/test/java/com/metaself/app/domain/goal/GoalForecastTest.kt`

`GoalProgress.weeksToGo` is NOT removed here — `GoalWording.projection` still reads it, and removing
it now would break the main build. Task 3 removes it.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/metaself/app/domain/goal/GoalForecastTest.kt`:

```kotlin
package com.metaself.app.domain.goal

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.weight.MeasuredRate
import com.metaself.app.domain.weight.TrendPoint
import com.metaself.app.domain.weight.WeightReading
import org.junit.jupiter.api.Test

/**
 * Spec §4's invented example throughout: a trend at 80 kg against a target of 72 kg, losing at
 * 0.5 kg a week. 80 - 72 = 8 kg to go.
 */
class GoalForecastTest {

    /** 8 kg at 0.5 a week = 16 weeks = 112 days. */
    @Test
    fun `the chosen projection is the distance over the chosen rate`() {
        val forecast = forecast(nowKg = 80.0, measured = null)

        assertThat(forecast.chosenKgPerWeek).isWithin(1e-9).of(0.5)
        assertThat(forecast.chosenWeeks!!).isWithin(1e-9).of(16.0)
        assertThat(forecast.chosenFinishEpochDay).isEqualTo(TODAY + 112)
    }

    /** A destination with no speed has no arrival date and must not be given one. */
    @Test
    fun `a target with no rate gets no chosen projection`() {
        val goal = Goal(direction = GoalDirection.LOSE, kgPerWeek = 0.0, targetKg = 72.0)
        val forecast = GoalForecast.of(
            goal = goal,
            progress = GoalProgress.of(goal, trendOf(82.0, 80.0))!!,
            measured = null,
            todayEpochDay = TODAY,
        )

        assertThat(forecast.chosenWeeks).isNull()
        assertThat(forecast.chosenFinishEpochDay).isNull()
    }

    /** 8 kg at a measured 0.25 a week = 32 weeks = 224 days. */
    @Test
    fun `losing weight against a LOSE goal is closing the distance`() {
        val forecast = forecast(nowKg = 80.0, measured = rate(kgPerWeek = -0.25))

        assertThat(forecast.measuredTowardGoalKgPerWeek!!).isWithin(1e-9).of(0.25)
        assertThat(forecast.measuredWeeks!!).isWithin(1e-9).of(32.0)
        assertThat(forecast.measuredFinishEpochDay).isEqualTo(TODAY + 224)
    }

    /** Going the wrong way gets no finish line, and the measurement is still carried. */
    @Test
    fun `gaining against a LOSE goal gets no measured finish line`() {
        val forecast = forecast(nowKg = 80.0, measured = rate(kgPerWeek = 0.1))

        assertThat(forecast.measured).isNotNull()
        assertThat(forecast.measuredTowardGoalKgPerWeek!!).isWithin(1e-9).of(-0.1)
        assertThat(forecast.measuredWeeks).isNull()
        assertThat(forecast.measuredFinishEpochDay).isNull()
    }

    /**
     * The same rising trend against a GAIN goal IS progress. 75 - 63 = 12 kg to go, and the
     * expectation is written as that division so it cannot be left behind if the fixture moves.
     */
    @Test
    fun `gaining against a GAIN goal is closing the distance`() {
        val goal = Goal.gain(kgPerWeek = 0.25, targetKg = 75.0)
        val forecast = GoalForecast.of(
            goal = goal,
            progress = GoalProgress.of(goal, trendOf(62.0, 63.0))!!,
            measured = rate(kgPerWeek = 0.25),
            todayEpochDay = TODAY,
        )

        assertThat(forecast.measuredTowardGoalKgPerWeek!!).isWithin(1e-9).of(0.25)
        assertThat(forecast.measuredWeeks!!).isWithin(1e-9).of(12.0 / 0.25)
    }

    /**
     * The owner's two-year rule, at its exact boundary. At 8 kg to go, a finish line landing
     * exactly 104 weeks out needs 8/104 kg a week — about 0.077 — and the rate is written as that
     * division rather than as a decimal so the test cannot drift from the fixture it derives from.
     * (8.0 / (8.0 / 104.0) is exactly 104.0 in IEEE doubles, so `<=` holds.)
     */
    @Test
    fun `a finish line exactly two years out is still given`() {
        val forecast = forecast(nowKg = 80.0, measured = rate(kgPerWeek = -8.0 / 104.0))

        assertThat(forecast.measuredWeeks!!).isWithin(1e-6).of(104.0)
    }

    /** 8 kg at 0.07 a week is about 114 weeks: past two years, so no finish line. */
    @Test
    fun `a finish line past two years is withheld`() {
        val forecast = forecast(nowKg = 80.0, measured = rate(kgPerWeek = -0.07))

        assertThat(forecast.measured).isNotNull()
        assertThat(forecast.measuredWeeks).isNull()
        assertThat(forecast.measuredFinishEpochDay).isNull()
    }

    /** Flat is tested before direction: -0.002 is "toward" by the sign and steady by any reading. */
    @Test
    fun `a trend flat to the rounding shown gets no finish line`() {
        val forecast = forecast(nowKg = 80.0, measured = rate(kgPerWeek = -0.002))

        assertThat(forecast.measuredWeeks).isNull()
    }

    @Test
    fun `no measurement leaves every measured field null`() {
        val forecast = forecast(nowKg = 80.0, measured = null)

        assertThat(forecast.measured).isNull()
        assertThat(forecast.measuredTowardGoalKgPerWeek).isNull()
        assertThat(forecast.measuredWeeks).isNull()
        assertThat(forecast.measuredFinishEpochDay).isNull()
    }

    /**
     * Arriving has its own sentence; neither projection may follow it.
     *
     * The measurement itself is still CARRIED — the trend went on moving — which is exactly why
     * `arrived` has to travel on the forecast: without it the wording would print a measured
     * sentence underneath the arrival announcement.
     */
    @Test
    fun `an arrived goal projects nothing at all`() {
        val forecast = forecast(nowKg = 72.0, measured = rate(kgPerWeek = -0.25))

        assertThat(forecast.arrived).isTrue()
        assertThat(forecast.measured).isNotNull()
        assertThat(forecast.chosenWeeks).isNull()
        assertThat(forecast.measuredWeeks).isNull()
    }

    private companion object {
        /** 2024-08-08, spec §4's fixed "today". */
        const val TODAY = 19_943L

        fun forecast(nowKg: Double, measured: MeasuredRate?): GoalForecast {
            val goal = Goal.lose(kgPerWeek = 0.5, targetKg = 72.0)
            return GoalForecast.of(
                goal = goal,
                progress = GoalProgress.of(goal, trendOf(82.0, nowKg))!!,
                measured = measured,
                todayEpochDay = TODAY,
            )
        }

        fun rate(kgPerWeek: Double) = MeasuredRate(
            spanDays = 28,
            changeKg = kgPerWeek / 7.0 * 28.0,
            kgPerWeek = kgPerWeek,
            asOfEpochDay = TODAY,
        )

        fun trendOf(vararg trendKg: Double): List<TrendPoint> =
            trendKg.mapIndexed { index, kg ->
                TrendPoint(
                    reading = WeightReading(epochDay = TODAY - 1 + index, kg = kg),
                    trendKg = kg,
                )
            }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.goal.GoalForecastTest" > /tmp/t2.log 2>&1; echo "exit $?"
```

Expected: non-zero exit; the log shows "Unresolved reference: GoalForecast".

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/metaself/app/domain/goal/GoalForecast.kt`:

```kotlin
package com.metaself.app.domain.goal

import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.weight.MeasuredRate
import kotlin.math.roundToLong

/**
 * When the goal is reached at the rate he chose, and when it would be reached at the rate he is
 * actually managing (D47).
 *
 * The only unit that knows about both a goal and a measurement, which is why the sign convention is
 * resolved here and nowhere else: [MeasuredRate] is direction-agnostic, and "is this progress?" is a
 * question only a goal can answer.
 *
 * Both are divisions and whatever displays them must say so — D21 on the chosen rate, and D4's rule
 * that an estimate is never presented as a measurement, applied to the future.
 *
 * @property measuredTowardGoalKgPerWeek the measured rate re-signed so that POSITIVE means closing
 *   the distance, whichever way the goal points.
 * @property measuredWeeks null when there is no measurement, when the trend is flat or moving away,
 *   and when the division lands past [MAX_PROJECTION_WEEKS]. A null here with a non-null [measured]
 *   is the state that says the rate and gives no finish line.
 */
data class GoalForecast(
    val arrived: Boolean,
    val chosenKgPerWeek: Double,
    val chosenWeeks: Double?,
    val chosenFinishEpochDay: Long?,
    val measured: MeasuredRate?,
    val measuredTowardGoalKgPerWeek: Double?,
    val measuredWeeks: Double?,
    val measuredFinishEpochDay: Long?,
) {
    companion object {

        /**
         * The owner's rule (spec §3): a measured finish line is shown only when it falls within
         * two years.
         *
         * One comparison disposes of the stalled trend, the flat trend and the trend going the
         * wrong way, with none of them a special case and without the app ever deciding that a
         * stretch is "bad". It also disposes of an arithmetic absurdity: at 20 kg to go, a trend
         * moving 0.02 kg a week projects about 1,000 weeks, which is true, useless, and reads as
         * mockery.
         */
        const val MAX_PROJECTION_WEEKS = 104.0

        /**
         * Below this the trend is held steady, not moving.
         *
         * Exactly the values that print as "0" at the two decimals the wording formats to, so that
         * rounding and meaning are the same test and no sentence can ever state a rate of zero.
         */
        const val FLAT_KG_PER_WEEK = 0.005

        fun of(
            goal: Goal,
            progress: GoalProgress,
            measured: MeasuredRate?,
            todayEpochDay: Long,
        ): GoalForecast {
            val chosenWeeks = when {
                progress.arrived -> null
                // No rate, no division. A goal with a destination and no speed has no arrival
                // date and must not be given one.
                goal.kgPerWeek <= 0.0 -> null
                else -> progress.toGoKg / goal.kgPerWeek
            }

            val toward = measured?.let {
                when (goal.direction) {
                    GoalDirection.LOSE -> -it.kgPerWeek
                    GoalDirection.GAIN -> it.kgPerWeek
                    GoalDirection.HOLD -> 0.0
                }
            }

            // Flat is excluded before direction is consulted: a rate of -0.002 against a LOSE goal
            // is "closing the distance" by the sign and standing still by any honest reading.
            val measuredWeeks = when {
                progress.arrived -> null
                toward == null || toward < FLAT_KG_PER_WEEK -> null
                else -> (progress.toGoKg / toward).takeIf { it <= MAX_PROJECTION_WEEKS }
            }

            return GoalForecast(
                // Carried so that the wording can fall silent on an arrived goal without being
                // handed the progress as well. A measurement still EXISTS once he has arrived —
                // the trend kept moving — and without this the measured sentence would print
                // underneath the arrival announcement.
                arrived = progress.arrived,
                chosenKgPerWeek = goal.kgPerWeek,
                chosenWeeks = chosenWeeks,
                chosenFinishEpochDay = chosenWeeks?.let { todayEpochDay + weekDays(it) },
                measured = measured,
                measuredTowardGoalKgPerWeek = toward,
                measuredWeeks = measuredWeeks,
                measuredFinishEpochDay = measuredWeeks?.let { todayEpochDay + weekDays(it) },
            )
        }

        /**
         * Counted from TODAY and never from the last weigh-in. A finish line is a statement about
         * the future from now; anchoring the measured one to a reading up to four days old would
         * date the two lines from different days, the chosen one having no reading to anchor to.
         */
        private fun weekDays(weeks: Double): Long = (weeks * 7.0).roundToLong()
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.goal.GoalForecastTest" > /tmp/t2.log 2>&1; echo "exit $?"
```

Expected: exit 0, 10 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/metaself/app/domain/goal/GoalForecast.kt \
        app/src/test/java/com/metaself/app/domain/goal/GoalForecastTest.kt
git commit -m "feat: both finish lines, and the owner's two-year rule (D47)

GoalForecast is the only unit knowing about both a goal and a measurement,
which is why the sign is resolved here: MeasuredRate is direction-agnostic,
and 'is this progress?' is a question only a goal can answer.

Flat is excluded before direction is consulted. A rate of -0.002 against a
LOSE goal is closing the distance by the sign and standing still by any
honest reading, and FLAT_KG_PER_WEEK is set to exactly the values that
print as 0 at two decimals, so rounding and meaning are one test.

Refs #17

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01LUpjHa9kZic9ZJrYzja45g"
```

---

### Task 3: The sentences, and one source for the projection

**Files:**
- Modify: `app/src/main/java/com/metaself/app/ui/goal/GoalWording.kt`
- Modify: `app/src/test/java/com/metaself/app/domain/goal/GoalWordingTest.kt` (it lives under `domain/goal` in the test tree, though the class is in `ui/goal`)
- Modify: `app/src/main/java/com/metaself/app/domain/goal/GoalProgress.kt` — remove `weeksToGo`
- Modify: `app/src/test/java/com/metaself/app/domain/goal/GoalProgressTest.kt` — remove its assertions

The contract is §4 of the spec. Every sentence below is asserted verbatim.

**This task has no green run of its own, and is committed together with Task 4.** Once
`GoalWording.projection` takes a `GoalForecast`, `WeightScreen.kt` (which still passes a
`GoalProgress`) does not compile, and Gradle compiles `main` before it runs any test. The red run in
Step 2 is still meaningful; the green run is Task 4's Step 6.

- [ ] **Step 1: Write the failing test**

Replace the whole of `app/src/test/java/com/metaself/app/domain/goal/GoalWordingTest.kt`. (Today's
file uses an 82 → 77.2 kg trend against a 70 kg target and asserts the "if it keeps up" wording;
every test in it is carried across below, moved onto spec §4's example.)

```kotlin
package com.metaself.app.domain.goal

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.weight.MeasuredRate
import com.metaself.app.domain.weight.TrendPoint
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.ui.goal.GoalWording
import org.junit.jupiter.api.Test

/**
 * Spec §4's invented example: a trend at 80 kg against a target of 72 kg, losing at 0.5 kg a week,
 * starting from a trend of 82 kg, on a fixed "today" of 8 August 2024.
 */
class GoalWordingTest {

    @Test
    fun `it says how far, and to what`() {
        assertThat(GoalWording.toGo(progress(80.0)))
            .isEqualTo("8 kg to go, to 72 kg")
    }

    /**
     * The rate must appear in the same sentence as the weeks. A bare "about 16 weeks" reads as a
     * promise; the whole point is that it is a division of a distance by a rate he chose.
     *
     * 8 kg / 0.5 = 16 weeks = 112 days after 2024-08-08 = 2024-11-28.
     */
    @Test
    fun `the chosen projection states the rate it assumed, and a month`() {
        assertThat(GoalWording.projection(forecast(80.0)))
            .isEqualTo("At the 0.5 kg a week you're aiming for: about 16 weeks, around November 2024.")
    }

    /**
     * The phrase that prompted all of this. It presupposes something is keeping up, which makes a
     * typed setting wear the grammar of an observation — D4's failure mode reached through tone.
     */
    @Test
    fun `nothing says if it keeps up any more`() {
        val sentences = listOfNotNull(
            GoalWording.projection(forecast(80.0)),
            GoalWording.measured(forecast(80.0, rate(-0.25))),
        )

        assertThat(sentences).hasSize(2)
        sentences.forEach { assertThat(it).doesNotContain("if it keeps up") }
    }

    /** 0.5 kg / 0.5 = 1 week = 7 days after 2024-08-08 = 2024-08-15. */
    @Test
    fun `one week is not one weeks`() {
        assertThat(GoalWording.projection(forecast(72.5)))
            .isEqualTo("At the 0.5 kg a week you're aiming for: about 1 week, around August 2024.")
    }

    /** A month name for something a day or two off is absurdly coarse, so this case survives. */
    @Test
    fun `almost there does not round down to nothing`() {
        assertThat(GoalWording.projection(forecast(72.1)))
            .isEqualTo("Less than a week at 0.5 kg a week.")
    }

    @Test
    fun `a goal reached says so and promises no more weeks`() {
        val reached = forecast(72.0, rate(-0.25))

        assertThat(GoalWording.arrived(progress(72.0)))
            .isEqualTo("You are at your goal weight of 72 kg.")
        assertThat(GoalWording.toGo(progress(72.0))).isNull()
        assertThat(GoalWording.projection(reached)).isNull()
        assertThat(GoalWording.measured(reached)).isNull()
    }

    // --- the measured line: one test per row of the spec's §4 table ---

    /** Row 1. */
    @Test
    fun `no measurement says nothing at all`() {
        assertThat(GoalWording.measured(forecast(80.0, measured = null))).isNull()
    }

    /** Row 2. 8 kg / 0.25 = 32 weeks = 224 days after 2024-08-08 = 2025-03-20. */
    @Test
    fun `a measured rate that reaches the goal gives a finish line`() {
        assertThat(GoalWording.measured(forecast(80.0, rate(-0.25))))
            .isEqualTo(
                "Over the last 28 days you've averaged 0.25 kg a week: " +
                    "about 32 weeks, around March 2025.",
            )
    }

    /** Row 3 — the owner's two-year rule, from the reporting end. 8 / 0.05 = 160 weeks. */
    @Test
    fun `a measured rate that would take over two years states the rate and stops`() {
        assertThat(GoalWording.measured(forecast(80.0, rate(-0.05))))
            .isEqualTo("Over the last 28 days you've averaged 0.05 kg a week.")
    }

    /** Row 4. */
    @Test
    fun `a flat trend says it held steady`() {
        assertThat(GoalWording.measured(forecast(80.0, rate(-0.002))))
            .isEqualTo("Over the last 28 days your trend has held steady.")
    }

    /** Row 5 — a fact about a line, never about him. */
    @Test
    fun `a trend going the wrong way says so of the trend`() {
        assertThat(GoalWording.measured(forecast(80.0, rate(0.1))))
            .isEqualTo("Over the last 28 days your trend is up 0.1 kg a week.")
    }

    /** The span stated is the one observed, never the constant 28. */
    @Test
    fun `the window stated is the span actually measured`() {
        val shortSpan = MeasuredRate(
            spanDays = 21,
            changeKg = -0.75,
            kgPerWeek = -0.25,
            asOfEpochDay = TODAY,
        )

        assertThat(GoalWording.measured(forecast(80.0, shortSpan)))
            .startsWith("Over the last 21 days")
    }

    /**
     * The flat threshold is exactly the value that would print as "0", so that rounding and meaning
     * are one test and no sentence can ever state a rate of zero. 0.0051 prints as 0.01, and 8 kg at
     * that rate is far past two years, so the sentence stops at the rate.
     */
    @Test
    fun `the flat boundary holds on both sides`() {
        assertThat(GoalWording.measured(forecast(80.0, rate(-0.0049))))
            .isEqualTo("Over the last 28 days your trend has held steady.")
        assertThat(GoalWording.measured(forecast(80.0, rate(-0.0051))))
            .isEqualTo("Over the last 28 days you've averaged 0.01 kg a week.")
    }

    /** Nearly there and still moving: a month would be coarser than the answer. 0.1 / 0.25 = 0.4. */
    @Test
    fun `a measured finish under a week says so`() {
        assertThat(GoalWording.measured(forecast(72.1, rate(-0.25))))
            .isEqualTo(
                "Over the last 28 days you've averaged 0.25 kg a week: less than a week to go.",
            )
    }

    /**
     * The register that keeps a factual readout from becoming a reprimand. D22 forbids the app
     * commenting on going the wrong way; D47 states the rate anyway, and this is what protects it.
     */
    @Test
    fun `no measured sentence scolds`() {
        val banned = listOf("only", "just", "still", "behind", "should", "failed")
        // Row 2, row 3, row 4, row 5, and a steeper row 5.
        val rates = listOf(-0.25, -0.05, -0.002, 0.1, 0.5)

        rates.forEach { kgPerWeek ->
            val text = GoalWording.measured(forecast(80.0, rate(kgPerWeek)))!!.lowercase()
            banned.forEach { word -> assertThat(text).doesNotContain(word) }
        }
    }

    /** 82 - 80 = 2 kg since the first reading. */
    @Test
    fun `progress so far is measured from the first reading`() {
        assertThat(GoalWording.done(progress(80.0)))
            .isEqualTo("2 kg down since you started tracking.")
    }

    /** Nothing is said about going the wrong way (D22). */
    @Test
    fun `a weight that went up says nothing about how far it has come`() {
        assertThat(GoalWording.done(progress(83.0))).isNull()
    }

    @Test
    fun `nothing at all without a goal`() {
        assertThat(GoalWording.toGo(null)).isNull()
        assertThat(GoalWording.projection(null)).isNull()
        assertThat(GoalWording.measured(null)).isNull()
        assertThat(GoalWording.done(null)).isNull()
        assertThat(GoalWording.arrived(null)).isNull()
    }

    @Test
    fun `the celebration says what the daily target has become`() {
        val text = GoalWording.celebration(targetKg = 72.0, newDailyKcal = 2450)

        assertThat(text).contains("reached 72 kg")
        assertThat(text).contains("2450 kcal")
    }

    private companion object {
        /** 2024-08-08, so that the month names in the assertions above are stable. */
        const val TODAY = 19_943L

        val GOAL = Goal.lose(kgPerWeek = 0.5, targetKg = 72.0)

        fun progress(nowKg: Double) = GoalProgress.of(
            goal = GOAL,
            trend = listOf(82.0, nowKg).mapIndexed { index, kg ->
                TrendPoint(WeightReading(epochDay = TODAY - 1 + index, kg = kg), trendKg = kg)
            },
        )

        fun forecast(nowKg: Double, measured: MeasuredRate? = null): GoalForecast =
            GoalForecast.of(
                goal = GOAL,
                progress = progress(nowKg)!!,
                measured = measured,
                todayEpochDay = TODAY,
            )

        fun rate(kgPerWeek: Double) = MeasuredRate(
            spanDays = 28,
            changeKg = kgPerWeek / 7.0 * 28.0,
            kgPerWeek = kgPerWeek,
            asOfEpochDay = TODAY,
        )
    }
}
```

**Before running:** the month names above are arithmetic, and must be checked rather than trusted:

```bash
python3 - <<'PY'
import datetime
today = datetime.date(1970,1,1) + datetime.timedelta(days=19943)
print("today:", today)
for label, weeks in [("chosen 8/0.5", 8/0.5),
                     ("chosen 0.5/0.5", 0.5/0.5),
                     ("measured 8/0.25", 8/0.25)]:
    d = today + datetime.timedelta(days=round(weeks*7))
    print(f"{label}: {weeks:.4f} wk -> {round(weeks)} weeks, {d} ({d:%B %Y})")
PY
```

Expected: `today: 2024-08-08`; chosen 16.0000 wk → 16 weeks, 2024-11-28 (November 2024); chosen
1.0000 wk → 1 week, 2024-08-15 (August 2024); measured 32.0000 wk → 32 weeks, 2025-03-20 (March 2025).

**If a printed month differs from the test above, correct the test to the printed value** — the
arithmetic is the source of truth, not the string drafted here.

- [ ] **Step 2: Run the test to verify it fails**

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.goal.GoalWordingTest" > /tmp/t3.log 2>&1; echo "exit $?"
```

Expected: non-zero exit; the log shows "Unresolved reference: measured" and a type mismatch on
`projection`, which still takes a `GoalProgress`.

- [ ] **Step 3: Rewrite the wording**

In `app/src/main/java/com/metaself/app/ui/goal/GoalWording.kt`, the current imports are
`GoalProgress`, `java.util.Locale` and `kotlin.math.roundToInt`. Add below them:

```kotlin
import com.metaself.app.domain.goal.GoalForecast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
```

Replace the whole `projection` function — its doc comment ("About 14 weeks at 0.5 kg a week.")
included, currently lines 21-35 — with the two functions below:

```kotlin
    /**
     * "At the 0.5 kg a week you're aiming for: about 16 weeks, around November 2024."
     *
     * The rate is in the same sentence as the weeks, always, and the sentence opens by naming the
     * rate as an intention — "you're aiming for" — rather than describing it. This is a division and
     * it is written as one: the owner has to be able to see that it is arithmetic from a number he
     * chose, not a date the app is promising him (D21, D4).
     *
     * The phrase "if it keeps up" was here until D47 and is deliberately gone. It presupposed that
     * something WAS keeping up, which made a value he typed into a form wear the grammar of an
     * observation; and with [measured] printed underneath it, "it" no longer has a referent.
     */
    fun projection(forecast: GoalForecast?): String? {
        val weeks = forecast?.chosenWeeks ?: return null
        val rate = rate(forecast.chosenKgPerWeek)
        val rounded = weeks.roundToInt()
        if (rounded < 1) return "Less than a week at $rate kg a week."
        val unit = if (rounded == 1) "week" else "weeks"
        val month = month(forecast.chosenFinishEpochDay) ?: return null
        return "At the $rate kg a week you're aiming for: about $rounded $unit, around $month."
    }

    /**
     * What the trend has ACTUALLY been doing, and where that lands (D47). Null when there is no
     * measurement to report.
     *
     * The span is stated in days and is the one observed, never the 28-day constant: it is the
     * denominator, and D21's rule that a projection must show what was divided by what applies to a
     * measurement at least as strongly.
     *
     * **This sentence reports a number and stops.** It says "up" or "down" of the TREND rather than
     * of the owner, and never says "only", "just", "still" or "behind". D22 forbids the app
     * commenting on going the wrong way, and register is what lets a factual readout coexist with
     * that: the alternative — a line that vanishes on a bad fortnight — teaches him that its absence
     * is the bad news, which nags by implication while pretending not to.
     */
    fun measured(forecast: GoalForecast?): String? {
        if (forecast == null || forecast.arrived) return null
        val rate = forecast.measured ?: return null
        val window = "Over the last ${rate.spanDays} days"

        if (abs(rate.kgPerWeek) < GoalForecast.FLAT_KG_PER_WEEK) {
            return "$window your trend has held steady."
        }

        // `<= 0.0` and not `< 0.0`: a HOLD goal resolves toward to exactly zero, and while it
        // cannot reach here today (holding produces no GoalProgress at all), falling through would
        // print "averaged 0 kg a week" — the one sentence FLAT_KG_PER_WEEK exists to forbid.
        val toward = forecast.measuredTowardGoalKgPerWeek
        if (toward == null || toward <= 0.0) {
            val direction = if (rate.kgPerWeek > 0.0) "up" else "down"
            return "$window your trend is $direction ${rate(abs(rate.kgPerWeek))} kg a week."
        }

        val averaged = "$window you've averaged ${rate(toward)} kg a week"
        val weeks = forecast.measuredWeeks ?: return "$averaged."

        val rounded = weeks.roundToInt()
        if (rounded < 1) return "$averaged: less than a week to go."
        val unit = if (rounded == 1) "week" else "weeks"
        val month = month(forecast.measuredFinishEpochDay) ?: return "$averaged."
        return "$averaged: about $rounded $unit, around $month."
    }
```

Then add these private helpers beside the existing `kg` at the bottom of the object:

```kotlin
    /**
     * Two decimals, trailing zeros trimmed — 0.25, 0.5, 0.05 — following `TargetWording.rate`.
     * [kg]'s single decimal would round 0.05 to 0.1 and 0.02 to 0.0, and a measured rate lives in
     * exactly that range whenever it is worth being careful about.
     */
    private fun rate(kgPerWeek: Double): String =
        String.format(Locale.US, "%.2f", kgPerWeek).trimEnd('0').trimEnd('.')

    /** "November 2024". The coarseness is the honesty: an exact date would read as a promise (D4). */
    private fun month(epochDay: Long?): String? =
        epochDay?.let { LocalDate.ofEpochDay(it).format(MONTH) }

    private val MONTH = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.US)
```

`toGo`, `done`, `arrived` and `celebration` are unchanged and still take a `GoalProgress`.
`arrived` needs no change: `GoalForecast` already nulls both projections once `arrived` is true, and
`measured` checks `forecast.arrived` itself.

- [ ] **Step 4: Remove `weeksToGo` from `GoalProgress`**

It is now read nowhere in `main`. In `app/src/main/java/com/metaself/app/domain/goal/GoalProgress.kt`:

Replace the class summary (line 8):

```kotlin
 * How far there is to go, and how long that would take at the rate the owner chose.
```

with:

```kotlin
 * How far there is to go.
```

Delete the property from the constructor (lines 30-32):

```kotlin
    val arrived: Boolean,
    val weeksToGo: Double?,
) {
```

becomes:

```kotlin
    val arrived: Boolean,
) {
```

Delete its assignment in `of` (lines 67-71):

```kotlin
                arrived = arrived,
                // No rate, no division. A goal with a destination and no speed has no arrival date
                // and must not be given one.
                weeksToGo = if (arrived || goal.kgPerWeek <= 0.0) null else toGo / goal.kgPerWeek,
            )
```

becomes:

```kotlin
                arrived = arrived,
            )
```

(The "no rate, no division" comment now lives in `GoalForecast.of`.)

Replace the `@property weeksToGo` paragraph that closes the class doc comment (lines 18-22):

```kotlin
 * @property weeksToGo the division, and nothing more. It assumes the chosen rate continues exactly,
 *   which it will not. Whatever displays it must say the rate it assumed in the same breath; a bare
 *   number of weeks reads as a promise, and decision D4's rule about estimates applies at least as
 *   strongly to the future as to the contents of a plate.
 */
```

with:

```kotlin
 *
 * Distance only. Every projection lives in [GoalForecast], which is the one place a division by a
 * rate happens — two sources for one number is how they come to disagree.
 */
```

- [ ] **Step 5: Remove the `weeksToGo` assertions from `GoalProgressTest`**

In `app/src/test/java/com/metaself/app/domain/goal/GoalProgressTest.kt`:

First test (lines 11-24) — rename it, fix its comment, drop the assertion:

```kotlin
    fun `how far there is to go, and how long at the chosen rate`() {
```

becomes:

```kotlin
    fun `how far there is to go`() {
```

```kotlin
        // Nothing here is a measurement: 82 -> 70 is an invented ladder. 77.2 - 70 = 7.2 to go, at
        // 0.5 a week = 14.4 weeks, and 82 - 77.2 = 4.8 done.
        assertThat(progress.toGoKg).isWithin(1e-9).of(7.2)
        assertThat(progress.weeksToGo).isWithin(1e-9).of(14.4)
```

becomes:

```kotlin
        // Nothing here is a measurement: 82 -> 70 is an invented ladder. 77.2 - 70 = 7.2 to go,
        // and 82 - 77.2 = 4.8 done.
        assertThat(progress.toGoKg).isWithin(1e-9).of(7.2)
```

Second test (lines 26-36) — rename it and drop the assertion:

```kotlin
    fun `reaching the target leaves nothing to go and no projection`() {
```

becomes:

```kotlin
    fun `reaching the target leaves nothing to go`() {
```

and delete:

```kotlin
        assertThat(progress.weeksToGo).isNull()
```

`a gain counts upwards` (lines 50-62) — fix its comment and drop the assertion:

```kotlin
        // 75 - 63 = 12 to go, at 0.25 a week = 48 weeks. A worked number that is not recomputed
        // with the value it derives from is the defect this project has shipped twice.
        assertThat(progress.toGoKg).isWithin(1e-9).of(12.0)
        assertThat(progress.doneKg).isWithin(1e-9).of(1.0)
        assertThat(progress.weeksToGo).isWithin(1e-9).of(48.0)
```

becomes:

```kotlin
        // 75 - 63 = 12 to go, and 63 - 62 = 1 done. A worked number that is not recomputed with
        // the value it derives from is the defect this project has shipped twice.
        assertThat(progress.toGoKg).isWithin(1e-9).of(12.0)
        assertThat(progress.doneKg).isWithin(1e-9).of(1.0)
```

Then delete the whole final test (lines 87-101), whose only subject was the removed property —
`GoalForecastTest`'s `a target with no rate gets no chosen projection` now covers it:

```kotlin
    /** A destination with no speed is a place, not a plan. It gets a distance and no date. */
    @Test
    fun `a target with no rate gets no projection`() {
        val progress = GoalProgress.of(
            goal = Goal(
                direction = com.metaself.app.domain.profile.GoalDirection.LOSE,
                kgPerWeek = 0.0,
                targetKg = 70.0,
            ),
            trend = trendOf(82.0, 80.0),
        )!!

        assertThat(progress.toGoKg).isWithin(1e-9).of(10.0)
        assertThat(progress.weeksToGo).isNull()
    }
```

Check nothing is left:

```bash
grep -rn "weeksToGo" app/src
```

Expected: no output.

Do not run Gradle yet (see the note at the top of this task). Go straight to Task 4.

---

### Task 4: Wiring — the screen actually shows it

**Files:**
- Modify: `app/src/main/java/com/metaself/app/ui/screen/weight/WeightUiState.kt`
- Modify: `app/src/main/java/com/metaself/app/ui/screen/weight/WeightViewModel.kt` (the `combine` block, lines 43-55)
- Modify: `app/src/main/java/com/metaself/app/ui/screen/weight/WeightScreen.kt` (the projection block, lines 143-149)
- Modify: `app/src/test/java/com/metaself/app/domain/weight/Weights.kt` — add `aMonth`
- Modify: `app/src/test/java/com/metaself/app/ui/screen/weight/WeightScreenRenderTest.kt`
- Modify: `app/src/test/java/com/metaself/app/ui/screen/weight/WeightViewModelTest.kt`

- [ ] **Step 1: Add the month-long fixture**

`aFortnight` in `app/src/test/java/com/metaself/app/domain/weight/Weights.kt` spans 13 days and has
no reading near a 28-day window's start, so it measures no rate — useful as the absent case and
useless as the present one. Add beside it, in the same file:

```kotlin
/**
 * A month of daily readings drifting down by 100 g a day — 28 days apart at the ends, so that a
 * rate can be measured from it. [aFortnight] spans 13 and deliberately cannot be.
 */
fun aMonth(
    startDay: Long = TEST_EPOCH_DAY - 28,
    startKg: Double = 80.0,
    dailyChangeKg: Double = -0.1,
): List<WeightReading> = (0..28).map { n ->
    WeightReading(epochDay = startDay + n, kg = startKg + dailyChangeKg * n)
}
```

- [ ] **Step 2: Write the failing render tests**

In `app/src/test/java/com/metaself/app/ui/screen/weight/WeightScreenRenderTest.kt`, add these
imports:

```kotlin
import com.metaself.app.domain.goal.GoalForecast
import com.metaself.app.domain.weight.MeasuredRate
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.domain.weight.aMonth
```

Replace the existing test `it says how far there is to go and how long that would take` (lines
180-195) — its second assertion, `"weeks at 0.5 kg a week"`, is written against the deleted wording —
with these three:

```kotlin
    @Test
    fun `it says how far there is to go and how long that would take`() {
        val texts = draw(stateWith(aMonth()))

        assertThat(texts.any { it.contains("kg to go, to 75 kg") }).isTrue()
        // The rate must be in the same sentence as the weeks: it is a division, not a promise.
        assertThat(texts.any { it.contains("0.5 kg a week you're aiming for") }).isTrue()
    }

    /** D47: the rate he is actually managing, beside the one he chose. */
    @Test
    fun `it says the rate the trend has actually been moving at`() {
        val texts = draw(stateWith(aMonth()))

        assertThat(texts.any { it.startsWith("Over the last 28 days") }).isTrue()
    }

    /**
     * A fortnight has no reading near the start of the 28-day window, so no rate is measured.
     * Nothing is shown in place of the measured line — not a dash, which would invite him to wonder
     * what is broken.
     */
    @Test
    fun `too little history says nothing about a measured rate`() {
        val texts = draw(stateWith(aFortnight()))

        assertThat(texts.any { it.startsWith("Over the last") }).isFalse()
    }
```

and add this helper beside the file's existing `drawFortnight` and `draw` (bottom of the class,
above the companion object):

```kotlin
    private fun stateWith(readings: List<WeightReading>): WeightUiState {
        val trend = WeightTrend.of(readings)
        val goal = Goal.lose(0.5, targetKg = 75.0)
        val progress = GoalProgress.of(goal, trend)
        return WeightUiState(
            readings = readings,
            trend = trend,
            progress = progress,
            forecast = progress?.let {
                GoalForecast.of(
                    goal = goal,
                    progress = it,
                    measured = MeasuredRate.of(trend, TEST_EPOCH_DAY),
                    todayEpochDay = TEST_EPOCH_DAY,
                )
            },
        )
    }
```

For the record, computed with the trend's own smoothing (a tenth a day): `aMonth()`'s trend runs
down about 1.95 kg from 80.0 over 28 days, about 0.49 kg a week, leaving about 3.05 kg to 75 kg — so both
sentences carry a finish line of about 6 weeks. Only the prefixes are asserted.

The other tests in this file that set `progress` without `forecast` (`the goal it names can be
changed from here`, `it says when the goal weight is off this view`) are unchanged: `forecast`
defaults to null and they assert nothing about the projection.

- [ ] **Step 3: Write the failing view-model test**

In `app/src/test/java/com/metaself/app/ui/screen/weight/WeightViewModelTest.kt`, add the imports:

```kotlin
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.weight.aMonth
```

and add this test beside `a fortnight of readings produces a trend point for each`:

```kotlin
    /** D47: the forecast is built where the progress is, from today's date and a month of trend. */
    @Test
    fun `a goal weight and a month of readings produce both finish lines`() = runTest {
        val viewModel = WeightViewModel(
            FakeWeightRepository(aMonth()),
            FakeProfileRepository(aProfile(goal = Goal.lose(0.5, targetKg = 75.0))),
            today,
            ProblemLog.NONE,
        )

        val forecast = viewModel.state.first { it.forecast != null }.forecast!!

        assertThat(forecast.chosenKgPerWeek).isWithin(1e-9).of(0.5)
        assertThat(forecast.chosenWeeks).isNotNull()
        assertThat(forecast.measured!!.spanDays).isEqualTo(28)
        assertThat(forecast.measuredWeeks).isNotNull()
    }
```

(`today` in this class is already `TEST_EPOCH_DAY`, which is where `aMonth()` ends.)

- [ ] **Step 4: Run the tests to verify they fail**

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
~/bin/gradlew-safe :app:testDebugUnitTest \
  --tests "com.metaself.app.ui.screen.weight.WeightScreenRenderTest" \
  --tests "com.metaself.app.ui.screen.weight.WeightViewModelTest" > /tmp/t4.log 2>&1; echo "exit $?"
```

Expected: non-zero exit. `main` fails first, at the `GoalWording.projection(state.progress)` call in
`WeightScreen.kt` (type mismatch — the Task 3 change); once that is fixed the tests would fail with
"No parameter with name 'forecast' found" on `WeightUiState`.

- [ ] **Step 5: Carry the forecast to the screen**

In `app/src/main/java/com/metaself/app/ui/screen/weight/WeightUiState.kt`, add the import:

```kotlin
import com.metaself.app.domain.goal.GoalForecast
```

and the field below `progress`:

```kotlin
    /** How far there is to go, or null when the goal has no destination (D21). */
    val progress: GoalProgress? = null,
    /** Both finish lines, and the rate the trend has actually been moving at (D47). */
    val forecast: GoalForecast? = null,
```

In `app/src/main/java/com/metaself/app/ui/screen/weight/WeightViewModel.kt`, add the imports beside
the existing `GoalProgress` and `WeightTrend` ones:

```kotlin
import com.metaself.app.domain.goal.GoalForecast
import com.metaself.app.domain.weight.MeasuredRate
```

and replace the body of the `combine` block (lines 48-54):

```kotlin
        val trend = WeightTrend.of(readings)
        WeightUiState(
            readings = readings,
            trend = trend,
            progress = profile?.goal?.let { GoalProgress.of(it, trend) },
            range = ChartRange.named(storedRange),
        )
```

with:

```kotlin
        val trend = WeightTrend.of(readings)
        val goal = profile?.goal
        val progress = goal?.let { GoalProgress.of(it, trend) }
        WeightUiState(
            readings = readings,
            trend = trend,
            progress = progress,
            forecast = if (goal != null && progress != null) {
                GoalForecast.of(
                    goal = goal,
                    progress = progress,
                    measured = MeasuredRate.of(trend, todayEpochDay),
                    todayEpochDay = todayEpochDay,
                )
            } else {
                null
            },
            range = ChartRange.named(storedRange),
        )
```

`todayEpochDay` is the view model's existing getter (`today().toEpochDay()`, declared just below
`state`); it has no backing field, so reading it from the lambda is safe. This is the `state` flow,
not an action, so it does not go through `guarded` (`ui/Guarded.kt` leaves long-lived observed flows
out of scope).

In `app/src/main/java/com/metaself/app/ui/screen/weight/WeightScreen.kt`, replace the projection
block (lines 143-149):

```kotlin
        GoalWording.projection(state.progress)?.let { projection ->
            Text(
                text = projection,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
```

with:

```kotlin
        GoalWording.projection(state.forecast)?.let { projection ->
            Text(
                text = projection,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The rate he is ACTUALLY managing, directly under the one he chose, so the two are read
        // together (D47). Same style as the projection above it: neither outranks the other.
        GoalWording.measured(state.forecast)?.let { measured ->
            Text(
                text = measured,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
```

Leave the rest of the goal block as it is: `arrived`, `toGo` and `done` still read `state.progress`,
and the "Change your goal" button below `done` is untouched.

- [ ] **Step 6: Run the goal and weight tests**

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
~/bin/gradlew-safe :app:testDebugUnitTest \
  --tests "com.metaself.app.domain.goal.*" \
  --tests "com.metaself.app.domain.weight.*" \
  --tests "com.metaself.app.ui.screen.weight.*" > /tmp/t4.log 2>&1; echo "exit $?"
```

Expected: exit 0. This is the first green run for Task 3's `GoalWordingTest` and `GoalProgressTest`.

- [ ] **Step 7: Run the whole suite**

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
~/bin/gradlew-safe :app:testDebugUnitTest > /tmp/all.log 2>&1; echo "exit $?"
```

Expected: exit 0. **Exactly eight classes skip** — `MigrationTest`, `MealDaoTest`,
`RoomMealRepositoryTest`, `RoomFoodRepositoryTest`, `RoomSavedMealRepositoryTest`, `WeightDaoTest`,
`RoomWeightRepositoryTest`, `BackupRoundTripTest`. Anything else skipping is a mis-wired framework,
not a supported case — stop and investigate rather than proceeding.

Do NOT pipe this command through a filter: the pipe returns the filter's exit code, so a failed
build would report success.

- [ ] **Step 8: Lint**

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
~/bin/gradlew-safe :app:lintDebug > /tmp/lint.log 2>&1; echo "exit $?"
```

Expected: exit 0 with no new warnings.

- [ ] **Step 9: Commit Tasks 3 and 4 together**

```bash
git add app/src/main/java/com/metaself/app/ui/goal/GoalWording.kt \
        app/src/test/java/com/metaself/app/domain/goal/GoalWordingTest.kt \
        app/src/main/java/com/metaself/app/domain/goal/GoalProgress.kt \
        app/src/test/java/com/metaself/app/domain/goal/GoalProgressTest.kt \
        app/src/main/java/com/metaself/app/ui/screen/weight/WeightUiState.kt \
        app/src/main/java/com/metaself/app/ui/screen/weight/WeightViewModel.kt \
        app/src/main/java/com/metaself/app/ui/screen/weight/WeightScreen.kt \
        app/src/test/java/com/metaself/app/ui/screen/weight/WeightScreenRenderTest.kt \
        app/src/test/java/com/metaself/app/ui/screen/weight/WeightViewModelTest.kt \
        app/src/test/java/com/metaself/app/domain/weight/Weights.kt
git commit -m "feat: say the rate he is managing beside the rate he chose (D47)

'if it keeps up' is deleted. It presupposed that something WAS keeping up,
which made a value typed into a setup form wear the grammar of an
observation; with the measured line printed underneath, 'it' has no
referent either.

Both lines carry weeks and a month. A count of weeks cannot be converted
into a date in the head; an exact date is the promise D4 exists to
prevent.

The measured sentence reports a number and stops. It says up or down of
the TREND, not of him, and a test forbids only/just/still/behind. That
register is what lets a factual readout coexist with D22.

GoalProgress loses weeksToGo: projections now have one home in
GoalForecast. Two sources for one number is how they come to disagree.

WeightViewModel already held a Today, so the forecast is built where the
progress is and travels on the same state object. The render test gains
aMonth(): aFortnight has no reading near the window's start, so it
measures no rate — the right fixture for asserting the line is ABSENT
and the wrong one for asserting it is present.

Refs #17

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01LUpjHa9kZic9ZJrYzja45g"
```

---

## Done when

- `~/bin/gradlew-safe :app:testDebugUnitTest` passes with exactly the eight expected skips.
- `~/bin/gradlew-safe :app:lintDebug` passes.
- `grep -rn "if it keeps up" app/src` returns nothing.
- `grep -rn "weeksToGo" app/src` returns nothing.
- The weight screen shows two projection lines when there is a month of readings and a goal weight,
  and one when there is only a fortnight.

## Known limits, left as they are

- **`MIN_SPAN_DAYS` is a guard that cannot fire at today's constants** (see Changes, item 4).
- **The forecast's "today" is read when the state flow recomputes**, which is when readings, the
  profile or the chart range change, or when the screen is opened again after being left for more
  than five seconds. A screen left open across midnight keeps yesterday's finish dates until one of
  those happens. The same is already true of everything else this flow computes.

## Not in this plan

Per §7 of the spec: the chart is untouched, the daily calorie target is untouched, and milestones
are untouched. `GoalForecast` carries finish days rather than only weeks so that the chart's forward
line — the owner's next step — can consume it without rework.
