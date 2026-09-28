# Which apps' sessions count as workouts — D81

> Written 2026-09-28 and approved the same day. A phone fitness app may log ordinary walks as exercise
> sessions by itself, which are not sessions chosen as exercise; the owner decides per app whether its
> walks count. **Every figure in an example is invented.**

## D81 — Each writing app's walks can be left out of workouts

- On "What the band sends", each app that has written workouts gets a switch, **"Count its walks as
  workouts"**, on by default; the owner switches it off for an app whose walks are not sessions he
  chose to do. The choice is stored
  on the phone (profile DataStore), keyed by the writing app's package; **no app is named in code**.
- A session left out this way stays in the table (nothing is deleted, the next sync cannot bring it
  back as new) but is not counted anywhere a workout is counted: the Movement screen's rows and
  headline, `health_days.workoutCount/Minutes`, heart-rate figures, the trainer later. It is still shown
  on the band page's counts, marked "not counted".
- Only WALK sessions are affected; runs, rides, swims and other kinds from the same app still count.
- Steps, distance and movement calories are unaffected: they come from Health Connect's daily totals.

## Investigate, then fix or explain: workout distance

A synced workout may carry no distance even when daily distance arrives. Find out why from the code
and Health Connect's semantics (the per-session `DISTANCE_TOTAL` aggregate over the session's time;
whether the band's app writes distance records during a session; whether de-duplication by the
owner's priority list drops them), and either fix it or record the reason on the band page ("distance
not shared for workouts by this app").
