# The Movement screen — D73 to D75

> Written 2026-09-27 from a design conversation with the owner, who chose among four mock-ups and
> approved the result. It amends §4.2 of `2026-09-25-physical-activity-module-design.md` (the activity
> screen, D62) now that the health record (D65–D72) exists. Built as 0.58.0.
>
> **Every figure in an example is invented.**

---

## Decisions

**D73 — Short day rows, one open at a time.** Chosen by the owner from four mock-ups (movement only;
plus a sleep line; everything; short rows that open). The screen lists this week's days, Monday to
Sunday, today first. Each day is one line; tapping it opens the day's full detail beneath; tapping an
open day closes it; **today starts open**; one day open at a time.

- **A closed day:** the weekday and date, then up to three parts joined by " · ", each shown only when
  recorded: the day's movement calories, its workouts by name and distance or time, and its sleep —
  "410 kcal · Running 6.2 km · slept 7 h 10". A day with none of the three shows its first detail line
  instead (a day with only steps: "9,000 steps · phone and band"); a day with nothing: "nothing recorded".
- **An open day:** one line per part, each only when recorded (a missing figure is left out, never
  written as zero — D4, D69):
  - movement: "410 kcal of movement · phone and band" and, when read, "9,000 steps" — the figures of that day's
    health summary with their source (TOTAL shows as "phone and band", CORRECTED as "you set this");
  - each visible workout: "Running · 6.2 km · 32 min · 5:10 /km", plus "· avg 142 bpm" when the
    workout has heart-rate figures;
  - sleep: "Slept 7 h 10 — deep 1 h 20 · REM 1 h 35 · light 4 h 15" (stages only when the night had them);
  - body: "Resting 58 · HRV 42 ms · oxygen 97% · breathing 14/min", each part only when recorded;
  - food: "1,840 kcal eaten", from the meals logged that day.
- **No allowance per day.** The day's allowance (target plus movement credit) is not stored for past
  days, and recomputing it here would present an estimate as a record (D4). Today's allowance stays
  on the day screen, where it is live.

**D74 — The headline is this week's distance and the average movement calories.** Chosen by the owner
over the week's running distance and the week's workout time. Top to bottom:

1. Kicker: "THIS WEEK · FROM MON 31 AUG".
2. The one large figure: the week's distance so far — the sum of the health summaries' `distanceM`
   (Health Connect's de-duplicated totals, D69), shown in km to one decimal: "42.6 km". Absent when
   no day this week has a distance.
3. Beneath: "355 kcal of movement a day, on average" — the mean of `activeKcal` over the days this
   week that have one (days without are not counted as zero). Absent when none has one.
4. Small grey: "4 workouts · 2 h 21" — visible workouts this week, count and total time. Absent with
   none.
5. The day rows (D73).
6. At the foot: "Last four weeks: 38.1 · 45.0 · 40.2 · 44.7 km", newest first, each week's summed
   distance; a week with no distance shows "—".

**This supersedes** the activity spec's running-distance headline, its hairline to a weekly running
target, and the `running_weekly_target_km` setting, which is not built. The Monday-based week of
D59's query stands. **No net-calories figure** (D63 unchanged): movement calories are shown, never a
total burn and never eaten-minus-burned.

**D75 — Reached from the day and from the top menu; nothing new drawn on the day.** The step line on
the day screen becomes a door to this screen (D49 item 8's rule, as D62 planned), as does a
"Movement" entry in the app's top-right menu. The screen's "Log a workout" button arrives with the
next step (logging by hand), not before: no button that does nothing.

---

## Where the numbers come from

Everything is read from the stored health record (D65) and the meal log; nothing on this screen reads
Health Connect. `health_days` for movement, steps, distance, sleep and body figures; `workouts` (not
hidden) for sessions; the meal log for eaten. The screen observes them, so a copy that lands while it
is open updates it.

## Not built here

Browsing earlier weeks (only this week plus the four-week line); editing, hiding or correcting a day or
a workout (the corrections phase); charts; the weekly running target; any trainer text.
