# Typing in a workout — D76 to D78

> Written 2026-09-27 and approved by the owner the same day. It builds the activity spec's phase 4
> (`2026-09-25-physical-activity-module-design.md` §4.3, D60, §8 item 4) on the Movement screen
> (`2026-09-27-movement-screen-design.md`, D73–D75), and adds two tidy-ups the owner asked for after
> seeing the screen. **Every figure in an example is invented.**

## D76 — A workout is typed in from the Movement screen, onto the day that is open

- A **"Log a workout"** button at the foot of the Movement screen (kept in view, D75's promise). It
  logs to **the day that is open** (today unless another is open), at the current clock time on that
  day. No date picker.
- **The sheet**, top to bottom: kind (Run · Walk · Cycle · Swim · Strength · Other); minutes (required);
  distance in km (optional; shown for Run, Walk, Cycle, Swim only; for a run with both minutes and
  distance, the pace shows live beneath, "5:30 /km"); effort (Easy · Moderate · Hard, **starts on
  Moderate and cannot be cleared** — §8 item 4); energy as a line, "about 150 kcal, estimated from the
  effort" (`MetEstimate` on the profile's weight), with "set it yourself" turning it into a number field
  (`energySource = TYPED`); an optional note; **Save kept in view above the keyboard** (the meal-naming
  sheet's lesson). Nothing beyond kind and minutes is required.
- **A "set it yourself" figure is taken as the workout's movement calories beyond resting**, as the other
  two readings are net; a machine's gross figure, which includes resting, may therefore overstate it.
- Stored in `workouts` as `source = TYPED`, `origin`/`originId` null, `energySource` MET_ESTIMATE or TYPED.
- **A typed workout the owner can change or delete**: tapping it in an open day opens the same sheet,
  filled, with Delete. A synced workout is not editable here (corrections are a later phase).

## D77 — A typed workout is the third reading, never an addition (D60 made live)

`DayMovement.typedWorkoutsKcal` (zero since phase 1) becomes the sum of that day's **visible typed**
workouts' energy. `ActivityEnergy` already takes the largest of steps, band and typed: a run typed on a
day the band also caught counts once. The day's summary (`health_days`) counts the typed workout among
the day's workouts. The step line's hairline and fill follow whichever reading decided the credit
(public issue #58), fixed here because typed workouts make band- and typed-driven days common.

## D78 — Tidier weeks

- **In a day's one-line summary, same-kind sessions are combined**: "Walking ×3 · 2 h 30" (count and
  total time; distance total instead of time when every one has a distance). The open day still lists
  each session.
- **Pace is shown for runs only.**
- **The headline counts walks apart from workouts**: "2 workouts · 3 walks · 4 h 30" — either part left
  out when zero; the time is all sessions' total.

## Not built here

Editing or hiding a synced session, correcting a day (the corrections phase, after the pilot month);
sets × reps (D61); any date picker.
