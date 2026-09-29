# A plan counts from when it is kept — D105 (amends D95)

> Written and approved 2026-09-29. Under D95 as written, sessions done before the plan was kept ticked it,
> and so did a session far shorter than planned; both read as wrong.
> **Every figure in an example is invented.**

## D105 — Which sessions tick a planned session

- **Only sessions that start after the plan was kept count.** "Kept" is the moment the answer arrived
  (its `createdAtMillis`): Keep follows it by seconds, as PlanMatch already assumes (D86). An adjusted
  version counts from the moment its **first** version was kept — adjusting never un-ticks what was done.
  Week 1 still runs Monday to Sunday (D94); the days before the keep simply hold nothing for the plan.
- Within each week, in start order, a session of a planned kind goes to the **first planned session of its
  kind, in plan order, that is not yet ticked** and that it could fill:
  - **at least as long as planned** → it **ticks** it;
  - **at least half as long, but shorter** → it is a **candidate** for it, until the owner answers;
  - **under half** → it is an **attempt** only, and never ticks.
- **The owner confirms a candidate.** On the plan card, under the planned session, the candidate shows as
  "Walking, 20 min — count it for this?" with **Yes** and **No**. Yes ticks the planned session; No leaves
  the session an attempt. The answer is stored and kept (below); it is asked once.
- **An open candidate does not hold its place.** If, later that week, a session at least as long as
  planned arrives for the same planned session, it ticks it, and the unanswered candidate becomes an
  attempt. A candidate the owner said Yes to holds its place like any tick.
- Only **minutes** decide. Distance, effort and heart rate do not; feedback judges how a session went (D87).
- **Ticks are still counted afresh from the record** (D95); only the owner's answers are stored.

## What the trainer is told (amends D93 and D97)

The adjust request and the last evaluation's plan (D93) carry, week by week, besides the done-counts:

- each planned session's outcome: **done** (as long as planned), **done short, confirmed** (minutes done
  against minutes planned), or **not done**;
- the **attempts**: sessions of a planned kind during the plan that ticked nothing — under half, a candidate
  answered No, or a candidate left open — each with its kind and minutes against the planned minutes of
  the session it came nearest to.

The instructions tell the model that attempts are effort to recognise and a signal for the next plan (for
instance, that planned sessions may be too long), never a failure.

The done-counts of D93, D95 and the plan card count ticks only — full, or confirmed short.

## What is stored

Room version 11 adds `plan_confirmations`: programmeId — always the **first** version of an adjusted
chain, so an answer carries to every later version and nothing is asked twice — workoutId (together the
key), confirmed (boolean), answeredAtMillis. It goes into the backup, whose format becomes **9**; formats 1–8 still read
(no answers). A confirmation names its session by position in the file, as a review does (D88).

## Not built here

- Judging a session by distance, pace or heart-rate zone.
- Moving a session to another planned slot by hand.
- Holding an attempt to the week's plan as it stood: a session that was an attempt earlier in the week can
  fully tick a shorter session an adjustment adds. Accepted, since the adjusted plan counts from the first
  keep.
