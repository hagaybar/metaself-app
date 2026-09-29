# The trainer evaluates and plans the weeks ahead — D93 to D98

> Written 2026-09-29 and approved the same day. This is the third use of the trainer (D84–D88):
> where the owner stands now, read from the stored record, and a plan for the next few weeks that
> the phone follows and the single-session suggestion (D86) serves. It builds on D89's monthly lines
> and D90's note. **Every figure in an example is invented.**

## What was chosen, and against what

- **The evaluation reads the record.** No fitness test, no score computed on the phone: the model
  reads what D84, D89 and D90 already send and writes a judgement. A timed test walk that would give a
  comparable estimated score was offered and not chosen; it can be added later without changing this.
- **The plan is a weekly outline.** Each week holds a number of sessions, each with a kind, minutes and
  an effort, on no particular day. A day-by-day calendar was rejected because one missed day puts it
  out of step.
- **The phone follows it; the model is asked only on a tap.** Ticking off planned sessions is counted
  on the phone. The model is asked again only when the owner taps "Adjust the plan"; a weekly automatic
  revision was rejected because nothing is sent without his tap (D84).

## D93 — Asking: the form, and what is sent (amends D84)

The Trainer screen offers **Evaluate me and plan the weeks ahead** (D97 says where). Its page has:

| Row | Choices |
|---|---|
| How many weeks | 2 weeks · 4 weeks · 6 weeks |
| Sessions a week I can manage | 2 · 3 · 4 · 5 |

and **Anything else (optional)**, free words, then **Ask the trainer**. The privacy line under it names
what goes: these answers; the note about himself; a year of monthly lines; six weeks of sessions with
his words; the weight trend; age, sex and height; the last evaluation. Never meals.

The request is D84's, with D89's monthly lines and D90's note, and the question is the form's answers
plus:

- **The last evaluation**, when one exists: its date and its text (D98), so the model can say what has
  changed since. The plan that went with it is sent too, with how many of its sessions were done, week
  by week (D95's count).
- **The start date** the plan will have (D94), so the model can name dates.

D87's safety rule applies to the form's words: pain, dizziness or chest discomfort there makes the
model advise stopping and seeing a doctor before anything else.

It counts against the same daily ceiling as every trainer request (D84).

## D94 — The answer: an evaluation and a plan

Asked for as JSON:

- **evaluation:** a headline; *going well*; *to work on*; and *since last time*, which is empty when no
  earlier evaluation was sent.
- **plan:** a title; one entry per week, as many as asked, each with a short focus and its sessions;
  each session has a kind (walk, run, cycle, swim, strength, other — the record's workout kinds
  without "unrecognised"), minutes, an effort (easy · steady · push) and one line on what it is; then one
  paragraph on why.

The phone checks the answer before showing it: the number of weeks is the one asked; every week holds
between 1 and the sessions-a-week asked; minutes are 5 to 180; kind and effort are from the lists. An
answer that fails any check is one of the call's failures, worded as the meal estimator words them
(D86).

It is shown under "From the AI trainer · advice, not a measurement" (D4): **Where you stand** — the
headline and the three parts, the third only when not empty — then **The plan**, with its weeks, start
and end dates and the paragraph. Below: **Keep this plan** and **Ask again**, and the line "Keeping it
replaces any plan you have now. The evaluation is kept either way." Ask again returns to the form with
the answers kept and costs another request.

**Week 1 starts** on the Monday of the week the plan is kept, if it is kept Monday to Thursday; on the
next Monday if it is kept Friday to Sunday. Weeks are Monday to Sunday, as everywhere else (D74). The
start shown before keeping is the one keeping it now would give; keeping it on a later day recomputes
it, and if the start then moves, the dates shown move with it (the model's plan names weeks, not
dates, so nothing it wrote goes wrong). The plan ends on the Sunday of its last week.

## D95 — Following the plan on the phone

Nothing here is sent anywhere.

- **Which sessions count:** the same as everywhere — visible, counted by the week (D74, D81), a session
  seen by several witnesses once (D92) — whose start falls inside the plan's weeks.
- **Ticking:** within each week, the sessions taken in start order; each ticks the first unticked planned
  session **of the same kind** in the plan's order. A walk ticks a planned walk whether on a treadmill or
  outdoors. A session with no unticked planned session of its kind ticks nothing and is simply a session.
  An unrecognised kind never ticks. Duration and effort do not have to match: the plan is a guide, and
  whether the session followed it is what feedback judges (D87).
- **Recomputed, not stored:** ticks are counted afresh from the record each time, so a session deleted,
  split or combined later changes them without any bookkeeping.

The **plan card** on the Trainer screen, while a plan runs: "YOUR 4-WEEK PLAN · WEEK 2 OF 4" (invented),
the title, this week's dates, this week's planned sessions each with a tick or an empty circle (a ticked
one says which session and day ticked it), one line per past week ("Week 1: 3 of 3 done"), and **See the
plan** and **Adjust the plan**.

## D96 — The plan feeds the single session (amends D86, D87)

- **Next in your plan** is this week's first unticked planned session. When there is one, the **Plan my
  next session** button carries the line "Next in your plan: steady walk, 40 min" (invented), and the form
  (D86) opens with *Time I have* set to the smallest choice at least that long (60-or-more when longer)
  and *Today I want* set from its effort — easy to Easy, push to A push, steady to Not sure. Every row
  stays changeable. The request carries that planned session, and the prompt tells the model to shape the
  suggestion around it unless the form's answers say otherwise. When this week's plan is all ticked, or
  no plan runs, the form opens as today.
- **Feedback** (D87) is told which planned session, if any, the reviewed session ticked, and of which week.
- A single-session suggestion kept under D86 is unchanged: it can still match a session as before. The
  two are independent; the weekly plan never becomes a D86 kept plan.

## D97 — Adjusting, stopping, ending, and where it lives (amends D85)

- **Where:** on the Trainer screen, under the About-me card and the session waiting for words (D85), comes
  the plan card (D95) while a plan runs; when none runs, a card with **Evaluate me and plan the weeks
  ahead**. Then **Plan my next session** and the earlier sessions, as today.
- **Adjust the plan** opens a page showing, counted on the phone, each past week's "n of m done", this
  week's so far, and which weeks will be rewritten; a text field **What should change?**; the line "The
  trainer rewrites this week's remaining sessions and the weeks after. Weeks already over stay as they
  were. The end date stays <date>."; **Adjust**; and a link **Stop this plan**.
- **Adjust** sends D84's request with the running plan, its ticks, and the words, and asks for the same
  JSON as D94's plan (no evaluation) covering **this week and the weeks after**; for this week the model
  is told which planned sessions are already ticked and returns only the rest. The phone composes the new
  version: past weeks unchanged, this week's ticked sessions followed by the model's, later weeks the
  model's. The checks of D94 apply, with this week allowed as many sessions as are not yet ticked, down to
  none. It is shown like D94's plan, with **Keep this version** and **Keep the old one**; keeping it makes
  it the running plan and the old one is kept on record. Neither the start nor the number of weeks changes.
  An adjust in the plan's last week is allowed and rewrites only that week.
- **Stop this plan** asks "Stop this plan? It is kept on record." and, on yes, ends it now. The card shows
  the ask-for-a-plan card again.
- **Ending:** the day after the plan's last Sunday, the card becomes "YOUR 4-WEEK PLAN HAS ENDED", its
  title, "10 of 12 planned sessions done" and the per-week counts (invented), and **Evaluate me and plan
  again**. It stays until a new plan is kept or 14 days have passed; then the ask card returns.
- **See the plan** shows the running plan with its evaluation, weeks, ticks and paragraph; nothing on it
  can be edited by hand.

## D98 — What is stored

Room version 10 adds one table, `trainer_programmes`; it goes into the backup, whose format becomes **8**;
formats 1–7 still read (no programmes).

- One row per answer that arrives, kept or not: id, createdAtMillis, the form's weeks, perWeek and words,
  the evaluation (JSON text, null on an adjusted version), the plan (JSON text, the composed version),
  the model's name, startEpochDay (null until kept), status, stoppedEpochDay (the day it stopped running —
  stopped, replaced, or adjusted — else null), and replacesId (the version an adjustment
  was made from, else null).
- **status:** OFFERED (arrived, not kept), RUNNING (at most one row), REPLACED (a newer plan was kept),
  ADJUSTED (a newer version of it was kept) or STOPPED. There is no ENDED: a RUNNING plan past its
  last Sunday is ended by date.
- **The last evaluation** sent by D93 is the newest row with an evaluation that was kept at some point
  (status RUNNING, REPLACED, ADJUSTED or STOPPED); an offered-and-not-kept evaluation is not sent, since
  the owner did not take it up. Its plan is the newest kept version of it, and that plan's counts are
  D95's, over the weeks it ran, up to the day it stopped running.
- Ticks are not stored (D95). No foreign key to workouts; nothing is renumbered on restore. A restore
  replaces the table, and the confirmation before a restore counts plans on both sides, as D88's does.

## Not built here

- A timed fitness test or any computed fitness score.
- Following a defined target (the trainer's fourth use).
- Moving a planned session to a particular day, reminders or notifications, charts of plan progress.
- Editing the plan by hand; a chat with the trainer about it.
