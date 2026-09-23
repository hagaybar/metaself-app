# The weight screen says how fast he is ACTUALLY going — design

> Settled 2026-09-21, from a question asked of the shipped line "About 16 weeks at 0.5 kg a week,
> if it keeps up.": is that statement updated from the record, or not?
>
> Half of it was. The number of weeks is recalculated from the smoothed trend every time the screen
> draws. The rate is `Goal.kgPerWeek` — a setting chosen once in setup from four offered values —
> and **nothing in the app has ever measured how fast the weight is actually changing.** A search of
> `app/src/main` for an observed rate, a slope, or a per-week change returns nothing.
>
> The requirement: a real picture of progress alongside the projected finish line.
>
> Four details were left open and decided (§6). Adds D47. Amends D21's projection wording.
> The chart is deliberately untouched (§7).

## 1. What changes, in one sentence

The goal block on the weight screen stops stating only the plan and starts stating the plan and the
reality side by side: the finish line at the rate he chose, and the finish line at the rate the
trend says he is managing.

## 2. Why "if it keeps up" is a defect and not a phrasing preference

D21 requires the projection to read as the division it is, and D4 forbids an estimate being
presented as a measurement. The sentence that shipped satisfies the letter of both — the rate is in
the same breath as the weeks — and defeats them in the reading.

**"If it keeps up" presupposes that something is keeping up.** It invites exactly one interpretation:
that the app has watched him lose half a kilogram a week and is asking whether that continues. It
has not looked. Every word after "at" is a value he typed into a form, wearing the grammar of an
observation. That is D4's failure mode, reached through tone rather than through a data field.

The phrase is deleted. It cannot survive the measured line being printed underneath it in any case —
with two rates on screen, "it" has no referent.

## 3. D47 — the app measures the rate, and says both

**D47 — ADDED 2026-09-21. The trend's own rate of change is measured over a fixed recent window, and
is reported next to the chosen rate, with a finish line for each.**

The measurement is of the smoothed trend and never of the raw readings, on D11's grounds: the trend
is what this app believes about his weight, and a rate computed from two mornings' readings would
report a gain across a fortnight of loss whenever the last morning was salty. `WeightTrend.changeKg`
already exists and already says so in its own doc comment; this is that idea given a denominator.

**It is not a prediction and nothing may word it as one.** It is arithmetic on the past — how far
the line moved, divided by how long it took. The sentence states the window in the same breath as
the rate, for the same reason D21 requires the rate beside the weeks: so that the reader can see
what was divided by what.

### The window

Twenty-eight days, matching `MeasuredBurnCalculator.WINDOW_DAYS` exactly. It was chosen over a
fortnight and over eight weeks. The existing constant's reasoning — "long enough that water and
glycogen stop dominating; short enough to still be about now" — applies unchanged to a rate, and
the two figures on screen agreeing about what "recently" means is worth more than tuning them apart.

### When there is nothing truthful to say

The measured line is absent, and the screen shows nothing in its place. This follows
`MeasuredBurnCalculator`'s rule and its reasoning: a figure showing "—" invites the owner to wonder
what is broken.

Absent when any of these holds:

| Condition | Constant | Why |
|---|---|---|
| No trend point at or near the window's start | `GAP_DAYS = 4` | A weight from before the window began is not a reading of where the window began. Same rule, same constant, as the measured burn. |
| The latest trend point is stale | `GAP_DAYS = 4` | A rate ending a fortnight ago is not a rate he is managing now. |
| The two points span fewer than 14 days | `MIN_SPAN_DAYS = 14` | Multiplying a four-day change by 1.75 to reach a week reports noise as a rate. |

Note the difference from the measured burn: that calculator divides by the fixed `WINDOW_DAYS`
because it averages over the window. A **rate** divides by the span actually observed between the
two trend points, which is what makes `MIN_SPAN_DAYS` necessary.

### The two-year rule — decided against the recommendation

**A measured finish line is shown only when it falls within two years; beyond that the rate is still
stated and no finish line is given.** `MAX_PROJECTION_WEEKS = 104`.

The recommendation had been to withhold the finish line whenever the rate does not reach the goal at
all. The two-year threshold is better and replaces it: one comparison disposes of the stalled trend,
the flat trend and the trend going the wrong way without any of them being a special case in the
code, and without the app ever having to decide that a stretch is "bad".

It also disposes of an arithmetic absurdity. At 20 kg to go, a trend moving 0.02 kg a week projects
about 1,000 weeks. That figure is true, useless, and reads as mockery.

**The rate itself is always stated, including when it is flat or going the wrong way.** This is the
one place this design knowingly sits against D22's "nothing is ever said about going the wrong way".
D22 governs milestones — praise, and the withholding of praise. A factual rate readout is not
praise, and the alternative was considered and rejected on its own terms: a line that disappears on
a bad fortnight teaches the reader that its absence means bad news, which nags by implication while
pretending not to, and makes the figure untrustworthy on the good fortnights too. Silence would also
contradict the requirement this design exists to meet: *a real picture*.

What protects it from becoming a reprimand is register. The sentence reports a number and stops. It
does not say "only", "just", "still", or "behind".

## 4. What the screen says

> **Every figure below is an example, and nothing more** — invented, and round on purpose: a trend
> sitting at 80 kg against a target of 72 kg, drawn on 8 August 2024, a fixed "today" chosen so the month names in the assertions never move.
> The plan (`docs/superpowers/plans/2026-09-21-measured-rate.md`, in the archived private
> repository) and its tests must use this same example:
> the same sentence worked twice, from two different bodies, is how a wrong number survives being
> checked.

The first line is unchanged. The projection line is rewritten and a second line is added.

```
8 kg to go, to 72 kg

At the 0.5 kg a week you're aiming for: about 16 weeks, around November 2024.

Over the last 28 days you've averaged 0.25 kg a week: about 32 weeks, around March 2025.
```

**Both weeks and a month, on both lines** — chosen over weeks alone and over an exact
date. A count of weeks cannot be converted into a date in the head, which is most of why the
shipped line said so little; an exact date ("28 November 2024") is the promise D4 exists to
prevent. A month with "around" in front of it is readable and cannot be read as a commitment.

Every sentence the measured line can produce:

| # | Situation | What it says |
|---|---|---|
| 1 | Insufficient or stale data (§3) | nothing |
| 2 | Moving toward the goal, finish within 104 weeks | "Over the last 28 days you've averaged 0.25 kg a week: about 32 weeks, around March 2025." |
| 3 | Moving toward the goal, finish beyond 104 weeks | "Over the last 28 days you've averaged 0.05 kg a week." |
| 4 | Trend flat to the rounding shown | "Over the last 28 days your trend has held steady." |
| 5 | Moving away from the goal | "Over the last 28 days your trend is up 0.1 kg a week." |

**The rows are decided in this order, and the order matters.** Flat is tested first, because a rate
of -0.002 kg a week is moving away from a LOSE goal by the sign and is held steady by any honest
reading. **Flat means `abs(kgPerWeek) < 0.005`** — precisely the values that would print as "0" at
the two decimals §4 formats to. Rounding and meaning are then the same test, and no sentence can
ever state a rate of 0.

Only once flat is excluded does direction decide between rows 2-3 and row 5.

- **The window is stated in days, always, and is the span actually observed** — not "4 weeks" when
  the two points are 26 days apart. Row 2's "28" is the usual case, not a constant in the string.
- **Row 5 says "up" or "down" rather than signing the number**, and says it of the trend rather than
  of him. "Your trend is up 0.1 kg a week" is a fact about a line. "You gained" is a fact about him,
  and D22's instinct is right that the app has no business having that opinion.
- **Rows 3, 4 and 5 give no finish line at all.** Not "no finish line yet", not an em dash. The
  sentence simply ends.
- **Rate is formatted to two decimals with trailing zeros trimmed** — 0.25, 0.5, 0.05 — following
  `TargetWording.rate`. `GoalWording.kg`'s single decimal would round 0.05 to 0.1 and 0.02 to 0.0.
- **Arrival is unchanged.** Once `arrived` is true the whole block is replaced by D21's arrival
  sentence, and neither projection is shown. A goal to HOLD produces no `GoalProgress` at all, so
  the block is absent as it is today; nothing here gives holding a rate.
- **"Less than a week" survives, on both lines.** The shipped projection says "Less than a week at
  0.5 kg a week." when the weeks round below one, and that case is kept rather than forced into a
  month: "around August 2024" for something six days away is absurdly coarse. The measured line
  gets the matching form — "Over the last 28 days you've averaged 0.25 kg a week: less than a week
  to go." — reachable whenever he is nearly there and still moving.
- **Both finish dates are counted from today, not from the last weigh-in.** A finish line is a
  statement about the future from now. Counting from `asOfEpochDay` would silently date the
  projection from a reading up to four days old, and the two lines would then be anchored to
  different days — the chosen one having no reading to be anchored to at all.

## 5. Structure

Three units, each answerable on its own. The guiding constraint is that **no existing caller of
`GoalProgress` changes**: `Milestones.reached` and `DayViewModel` use it for distance and arrival
and have no business acquiring a dependency on today's date.

**`MeasuredRate` — `domain/weight/MeasuredRate.kt`, new.**
Knows nothing about goals. Given the trend and today, returns the signed rate or null.

```kotlin
data class MeasuredRate(
    val spanDays: Int,        // actually observed between the two trend points
    val changeKg: Double,     // signed; negative means the trend went down
    val kgPerWeek: Double,    // changeKg / spanDays * 7, same sign
    val asOfEpochDay: Long,   // the latest trend point used
)
```

Sign convention: **negative is downward**, direction-agnostic, because a measurement of a line has
no opinion about which way the owner wants to go. `Goal.kgPerWeek`'s convention — a positive
magnitude with the direction carrying the sign — is deliberately not copied here; it is right for an
intention and wrong for an observation.

**`GoalForecast` — `domain/goal/GoalForecast.kt`, new.**
The only unit that knows about both. Given a `GoalProgress`, an optional `MeasuredRate` and today,
it produces both projections, applies the two-year rule, and resolves the measured rate's sign
against the goal's direction into "toward the goal" or "away from it".

```kotlin
data class GoalForecast(
    val arrived: Boolean,                      // carried so the wording can fall silent
    val chosenKgPerWeek: Double,
    val chosenWeeks: Double?,
    val chosenFinishEpochDay: Long?,
    val measured: MeasuredRate?,
    val measuredTowardGoalKgPerWeek: Double?,  // positive = closing the distance
    val measuredWeeks: Double?,                // null past MAX_PROJECTION_WEEKS or not closing
    val measuredFinishEpochDay: Long?,
)
```

Null semantics, stated because three fields share the type and not the meaning:

- `chosenWeeks` and `chosenFinishEpochDay` are null together, and only when `goal.kgPerWeek <= 0.0`
  — the existing rule inherited from `GoalProgress.weeksToGo`, that a destination with no speed has
  no arrival date and must not be given one.
- `measured` is null when §3's data conditions are not met: there is no measurement to report. Note
  that it is **not** null once the goal is reached — the trend went on moving and the measurement is
  still valid — which is why `arrived` has to travel on the forecast. Without it the measured
  sentence would print underneath D21's arrival announcement.
- `measuredWeeks` and `measuredFinishEpochDay` are null together whenever `measured` is null, and
  **also** when there is a measurement that is flat, moving away, or projecting past
  `MAX_PROJECTION_WEEKS`. This is the pair that distinguishes row 1 from rows 3-5: a null `measured`
  says nothing at all, a present `measured` with null weeks states the rate and stops.

**`GoalWording` — extended.** `projection` is rewritten to take a `GoalForecast`; a new `measured`
returns row 2-5 or null. Date formatting lives here, epoch-day arithmetic lives in `GoalForecast` —
the split the chart code already uses, and for its stated reason: arithmetic only a screen can reach
is arithmetic nobody checks.

**`GoalProgress` — one removal.** `weeksToGo` moves to `GoalForecast`, which is now the only place a
projection is computed. Leaving it would leave two sources for one number.

**Wiring.** `WeightViewModel` already holds a `Today`; it builds the `MeasuredRate` and the
`GoalForecast` and puts the forecast on `WeightUiState`. `DayViewModel` and `Milestones` are not
touched.

## 6. The four open details, and how they were decided

| # | Question | Chosen | Recommended |
|---|---|---|---|
| 1 | Which rate drives the finish line | Show both | same |
| 2 | Behaviour on a flat or wrong-way stretch | Finish line when within two years, rate always | withhold finish line when not closing |
| 3 | Weeks, a date, or both | Both | month-level date |
| 4 | Does the chart get a forward line | Not now | draw it |

Question 2 is the only one where the decision displaced the recommendation, and §3 records why the
decision is better.

## 7. Out of scope, deliberately

- **The chart is untouched.** It already draws the goal as a dashed horizontal line (D21); it draws
  nothing about the future. Extending the trend forward to where it meets that line is the obvious
  next step and is wanted — the decision was to ship the sentences first rather than hold them for
  it. `GoalForecast` is the unit that step will consume, which is why it carries finish days rather
  than only weeks.
- **No change to the daily calorie target.** The measured rate is reported, never fed back into
  `DailyTargetCalculator`. A target that retunes itself from a measured rate is a much larger
  decision and collides with D11.
- **No change to milestones.** D22's celebrations are untouched.

## 8. Testing

Pure JUnit 5 + Truth throughout; nothing here needs a database or a framework.

- **`MeasuredRateTest`** — rate from a known trend; the sign of a rising trend; null on each of the
  three insufficiency conditions in turn; that the divisor is the observed span and not 28, proved
  by a 21-day span giving a different rate from the same change over 28.
- **`GoalForecastTest`** — both projections from one fixture; the two-year boundary exercised on
  both sides at 104 weeks; a rising trend against a LOSE goal yielding no measured finish line; a
  rising trend against a GAIN goal yielding one; finish days landing where the arithmetic says.
- **`GoalWordingTest`** — one case per row of §4's table, asserting the exact sentence; the flat
  boundary asserted on both sides of 0.005, since that single constant is what keeps a printed rate
  of "0 kg a week" impossible; "less than a week" on both lines; that no sentence contains "only",
  "just", "still" or "behind", in the manner of `WindowWordingTest`'s existing banned-word
  assertion; that "if it keeps up" appears nowhere.
- **`WeightScreenRenderTest`** — the measured line renders when the data supports it and is absent
  when it does not, read through `EditableText` as the existing test does.
- **`GoalProgressTest`** — updated for the removal of `weeksToGo`.

## 9. Build order

One branch. `MeasuredRate` with its tests, then `GoalForecast` with its tests, then the wording,
then the wiring and the render test. Each step compiles and its tests pass before the next begins.
