# Movement over time — Implementation Plan (D135–D137)

**Goal:** `docs/superpowers/specs/2026-10-11-movement-over-time-design.md`. Your movement gains a three-way switch —
Week, 12 weeks, Year — a chart in each span, swiping between spans, and a line under the headline counted on the
phone. Version 0.82.0 (138).

**Branch:** `movement-over-time`, from main. **No Room schema change** (one new read-only DAO query).

**Tech:** JUnit 5 + Truth for everything pure and the view model; JUnit 4 + Robolectric only for render tests.
Fixtures are invented; today is `TEST_EPOCH_DAY` (Thursday 3 September 2026).

## Choices the spec leaves open — settled here

1. **One "day in view".** The page keeps one anchor day. Week shows its Monday's week; 12 weeks the twelve weeks
   ending with its week; Year the twelve calendar months ending with its month. Switching span keeps the anchor, so
   Week → 12 weeks ends on the week that was shown, and back. Null is today: a span holding today follows the
   calendar.
2. **Moving.** ‹ and swipe right step one span back (7, 84 days, or 12 months); › and swipe left one forward, never
   past the span holding today. ‹ is offered while the record's first day lies before the span's first day (Week
   keeps D83's rule). A swipe is a horizontal drag of at least 64 dp on the page; vertical scrolling is untouched,
   because a horizontal drag detector only claims a gesture once it has moved sideways past touch slop.
3. **Where the record starts (D136).** The earliest day with a step count or a session:
   `MovementRecord.observeEarliestMovementDay()`, a new read whose Room form is `MIN(epochDay)` over daily summaries
   with steps, against the earliest workout. Its default falls back to `observeEarliestDay()`, so fakes need nothing.
   A bar whose last day is before it is blank and out of every average; with no bar recorded the span says "Nothing
   recorded this far back."
4. **Counting.** A bar's planned minutes are its counted sessions (`WeekSessions`' rule) that ticked a planned
   session in their week's practice (`WeekLedgers.practiceOf`, D110); the rest are "your own". A month takes each
   session by its own day, with the practice of the week it falls in. Km is `MovementWeek.distanceOf` over the bar's
   days; steps a day is the mean over the days that record steps. A recorded week with no session is 0 min; with no
   distance or steps it is blank for Km and Steps (D4: nothing measured is not zero).
5. **Averages.** 12 weeks: the mean of the recorded bars' values, as the spec reads — the week holding today
   included. Year: the mean over the complete months that have a record; none complete → the current month's
   minutes and "so far". The dashed line is drawn on 12 weeks (spec) and on Year when the headline is an average.
6. **D137 thresholds.** "Within a tenth" is |a − b| ≤ b / 10, inclusive, on the unrounded averages; a usual of 0
   is "about" only for 0. Week's usual is the mean of the four weeks before it that are recorded, needing two.
   12 weeks' comparison is the last four bars against the four before, needing all eight recorded (and, for Km and
   Steps, each with a figure). Otherwise "Over the N weeks recorded." ("Over the one week recorded."); none, no line.
   Year: "Over the N months recorded." over the months its average uses; none, no line.
7. **Wording.** Span line: "20 JUL – 11 OCT" (a date outside today's year carries its year), "NOV 2025 – OCT 2026".
   Headlines "148 min a week", "18.2 km a week", "8,900 steps a day", "640 min a month", "85 min so far". Line on the
   dashed average "avg 148". 12-week bars are labelled by their Monday's day of the month on every other bar,
   counted back from the last so the newest is always labelled; months by their initial. Legend: "Planned",
   "Your own", "Average", and "October so far" on Year. The Week chart's legend is "Planned" and "Your own" when a
   plan covers the week, none otherwise.
8. **The Week chart** is the Trainer's strip drawing, extracted as `WeekStripChart` (bars and day letters), fed a
   `WeekStrip` whose "To do" is emptied — so its blocks are the Trainer strip's for the same week.
9. **Plans** are read once per span read through a small `KeptPlans` port over `ProgrammeStore` (the counting
   `AskTheTrainer` used, moved so both share it). A failed read fails the page's read (D8): drawing every block light
   green would be wrong, not missing.

## Tasks

1. Pure spans: `MovementSpans` (anchors, stepping, week and month bars, averages, usual week). Tests. (D135, D136)
2. Wording: `MovementSpanWording` (span lines, headlines, avg label, bar labels, D137's sentences). Tests. (D136, D137)
3. Data: `observeEarliestMovementDay`, `KeptPlans`. (D136)
4. View model: span, anchor, measure, one read per span; tap a bar; Week's chart and usual. Tests. (D135–D137)
5. Screen: switch, span line, swipe, Week chart (extracted strip), span chart with chips and dashed average; render
   tests. (D135, D136)
6. 0.82.0 and its change-log entry.

## Not built

As the spec: tapping a block to open its session; heart-rate or zone charts; two spans side by side; history before
the record.
