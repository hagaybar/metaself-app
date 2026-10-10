# One plan, in one place, at three resolutions — D119 to D125

> Written 2026-10-10 and approved the same day. Part 3b of the Movement and Trainer rework, after the
> plan screen became a timeline (D117, D118). **Every figure in an example is invented.**

## Why

The trainer plans in two places that never meet. **Plan my next session** (D86) gives one detailed
session; kept, it sits on the Trainer page for seven days and appears on no plan. **Evaluate me and
plan the weeks ahead** (D93) gives two, four or six weeks of sessions, offered only when no plan runs.
Nothing plans towards a goal — a distance by a date — and nothing plans further than six weeks; but a
plan of six fully written weeks is also too rigid to follow a week that went differently.

## What coaching practice does

The structure is settled practice in endurance coaching (periodisation; see TrainingPeaks' "3 cycles
of periodization" and Joe Friel's base / build / peak / race phases):

- **A goal**, and the whole road to it (the *macrocycle*).
- **Blocks** of about four weeks (*mesocycles*), each with one focus — base, build, peak, then a
  taper before a race. A common loading pattern is three building weeks and one easier week.
- **Weeks** (*microcycles*): the sessions, most of them easy.
- **Sessions**: the steps of one workout.

Adaptive coaching apps (Runna, Garmin Coach) sketch the road to the goal up front and **fill in each
week's sessions as it comes**, adjusting to what was actually done. That is the model here: **robust
in outline, flexible in detail**. Rules of thumb used below — weekly load rising by about a tenth,
every fourth week easier, one to two taper weeks — are coaching conventions, not measured standards,
and are given to the model as such; the phone does not enforce them.

## D119 — A goal

A plan has one goal, chosen when it is made:

- **Keep a steady rhythm** — no end date. The plan runs block after block.
- **A distance by a date** — a kind (run, walk, swim, ride), a distance in km, and a date 6 to 30
  weeks ahead. The plan ends on that date's week.

The goal is shown at the top of the plan in words: "15 km run · by Sun 14 Mar · 22 weeks to go",
"Keep a steady rhythm". The owner's words (D93) still go with the ask, for anything the two goals do
not cover.

## D120 — The outline: blocks and weekly targets

Making a plan returns, besides the evaluation (D93), an **outline**: the blocks from now to the goal —
for a steady rhythm, the next block only — each with:

- its **phase** — base, build, peak, taper, or steady — and a one-line focus;
- one **target** per week: total minutes, the longest session's minutes, and whether it is an easier
  week.

The phone checks that the blocks cover every week from the plan's first Monday to the goal's week
(steady: exactly four weeks) and that each target lies in the session bounds (D94); an outline that
does not is refused as a reply that does not fit (as D94 refuses one today).

## D121 — Weeks are filled in as they come

**Only this week and the next carry sessions.** Every later week carries its target only.

- **Fill in next week** — when next week has no sessions, the plan offers it from Friday of this week
  (the Trainer page's main button from Sunday, D114), and once that week has begun it reads **Plan
  this week**. The request carries the outline, this week's ledger and the weeks before (D110), and
  returns that week's sessions — fitted to its target, or a changed target with one sentence of why.
  Only when tapped; never in the background (D84).
- **Plan the next block** — on a steady-rhythm plan, from the last week of its last block: the next
  four weeks' outline, made from how the block went.
- **Adjust the plan** (D97) is unchanged in purpose: it rewrites the filled-in weeks and may change
  later targets.

## D122 — A session's steps belong to its planned session

**Plan my next session** (D86), while a plan runs, is for the plan's next open session (D96 already
shapes it so). **Keeping it attaches its steps to that planned session.** The tile then reads "steps
ready", and the steps are shown where the session is (D123). A planned session holds at most one set
of steps; asking again and keeping replaces them. If the session the steps were for is ticked by a
different session, the steps stay with the planned session.

With **no plan running**, a kept session is the plan page's **Next session** until it is done or seven
days pass, as today's kept-plan card is.

**The Trainer page's "Your kept plan" card goes** (D114 item 7): the steps live on the plan page.

## D123 — Three resolutions: Month, Week, Session

The plan page opens with a three-way switch under the title — **Month · Week · Session**, Week chosen
first.

- **Month** — the road to the goal. The goal line (D119), then each block as a row: its phase and
  focus, and its weeks as small bars of their target minutes — past weeks with the minutes actually
  done drawn over the target, easier weeks lighter, this week marked "you are here". A plan made
  before outlines existed shows "This plan has no outline — plan ahead to set a goal."
- **Week** — the timeline of D117. A week beyond the filled-in ones shows its target instead of
  sessions: "about 150 min · longest 60′ · easier week".
- **Session** — the sessions coming up, in order: this week's open ones, then next week's. Each is a
  card — kind, effort, minutes — that opens to its steps when it has them, or offers **Get the steps**
  (Plan my next session for that planned session) when it has none. This week's done sessions sit
  folded above them. With no plan running, the Next session (D122) is the only card.

## D124 — Plan ahead, always within reach

**Evaluate me and plan the weeks ahead becomes Plan ahead.** Its form asks: the goal (D119); sessions
a week (2 to 5, as now); words. The number of weeks is no longer asked — it follows from the goal. The
answer is drawn as D118 (now with the Month view's outline above the weeks) and kept as today.

**Plan ahead is on the plan page always.** While a plan runs, keeping a new one replaces it from its
first Monday, after a confirmation: "This replaces your current plan from Mon 19 Oct." The Trainer
page's main button (D114) reads **Plan ahead** when no plan runs, or when the plan ends this week.

## D125 — What the trainer is told

The plan requests (D93, D97, D121) carry the goal, the outline with every week's target, which weeks
are filled in, and — as now — how each past week went (D105) and the six week ledgers (D110). The
model's instructions gain the coaching conventions above, labelled as conventions, and the rule that
a week's sessions should sum near its target unless the week's ledger argues otherwise, in which case
it says why.

## Built in two releases

1. **One place for sessions** — D122 and D123's Week and Session views, and D124's "Plan ahead is on
   the plan page always" with the current form. This fixes the steps that vanish.
2. **Goals and blocks** — D119, D120, D121, D124's form, D123's Month view and D125.

## Not built here

Days for planned sessions (D94), reminders, pace targets, a race-result prediction, strength sets.

## Tests

- **Release 1**: keeping steps attaches them to the named planned session and replaces earlier ones;
  the tile reads "steps ready"; Session view's order and folding; Get the steps opens the form for
  that session; no-plan Next session and its seven days; the kept-plan card gone; Plan ahead present
  with a plan running, its confirmation, and the replacement from the next Monday.
- **Release 2**: the goal's two forms and their wording; the outline's coverage check (refused when a
  week is missing or extra); only two weeks filled; Fill in next week's offer from Friday and its
  Sunday button; Plan this week once the week has begun; the steady plan's next block; Month view's
  bars, past done-minutes, easier weeks and "you are here"; a plan without an outline; the request
  carrying goal and outline; the instructions carrying the conventions.
