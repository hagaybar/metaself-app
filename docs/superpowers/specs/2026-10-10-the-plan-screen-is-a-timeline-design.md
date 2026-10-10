# The plan screen is a timeline — D117, D118

> Written 2026-10-10 and approved the same day. Part 3 of four in reworking Movement and the Trainer
> (D110–D112 the week ledger; D113–D116 the Trainer page). The mockup "The plan, simplified" was shown
> with the Trainer page's and not objected to; this is it. **Every figure in an example is invented.**

## Why

"See the plan" opens a screen of text: where you stand in four paragraphs, then every week's focus and
sessions, then why — read top to bottom before any of it says where the owner is. The plan is a path
through weeks; the screen should show the path, and the owner's place on it, first.

## D117 — A kept plan, drawn as its weeks

The screen that **See the plan** opens (the Trainer page's "Your plan" row) shows, top to bottom:

1. **The plan's title** in the display face, and under it "4 weeks · 28 Sep – 25 Oct · you're in week 2"
   ("starts Mon 12 Oct" before its first week; "ended Sun 25 Oct" after its last, or "stopped
   Wed 14 Oct" when stopped).
2. **The timeline**: one row per week, joined by a vertical line, each row headed
   "Week 2 · <the week's focus>".
   - **A past week**: a filled node with a tick. Under its heading, one line from the week ledger
     (D110): "2 of 2 planned" — and, when the week held sessions beyond the plan, "· plus 4 of your
     own" (the count of its beyond-the-plan sessions). The line between it and the next week is
     solid.
   - **This week**: an outlined node, and "— this week" after the heading. Under it, **one tile per
     planned session**, as the Trainer page draws them: **done** — filled, "Easy 30′" and "Done Mon"
     (the day of the session that ticked it); **done short** — filled, "Done Mon · 25 min"; **open** —
     dashed outline, "Open". The shorter-session question (D105) stays on the Trainer page, not here.
   - **A week ahead**: a small grey node, the heading in grey, and its sessions on one line,
     "Easy 30′ · Steady 45′ · Brisk 25′".
   - **A plan stopped or ended**: every week is drawn as past or not reached; weeks never reached are
     drawn as weeks ahead, greyed, under the stop.
3. **Where you stand, in one line**: the kept evaluation's headline (D93) — already a single sentence
   — under "WHERE YOU STOOD" with the evaluation's date. Below it, **"Read the trainer's full
   evaluation"** opens the rest, as the screen shows it today: going well, to work on, since last
   time, and why. Closed by default. A plan with no evaluation (adjusted from one that had none) shows
   neither.
4. **Two buttons**: **Adjust the plan** (outlined, D97, as now) and, beside it, the Trainer page's main
   action for this plan (D114): "Plan the open walk" or "Plan my next session". **A plan ended or
   stopped** has one filled button instead: "Evaluate me and plan again".

"Stop this plan" stays where Adjust the plan offers it.

## D118 — An offered plan, drawn the same way

The answer to **Evaluate me and plan the weeks ahead** (D93) and to **Adjust the plan** (D97) is drawn
with the same timeline before it is kept: every week as a week ahead (an adjusted plan: weeks already
past drawn as past, as D117), with this week's sessions as open tiles. Above it the evaluation's
headline and the closed "Read the trainer's full evaluation", as D117; below it **Keep this plan** /
**Ask again** (or Keep this version / Keep the old one), unchanged in what they do.

## Not built here

- Days for planned sessions (D94 still gives them none), reminders, a goal.
- Tapping a tile to open its session.
- Movement's redesign and the letter's (part 4 onward).

## Tests

- **The timeline's model** (pure): past / this week / ahead for a running plan, before its start,
  after its end, and stopped mid-way; the past week's line with and without sessions of its own; tile
  states done, done short, open, with the ticking session's day.
- **Wording**: the subtitle's four forms; the one-line session list; "plus N of your own" only when
  N > 0.
- **The page** (Compose render): the order of D117; the evaluation closed by default and opening; the
  buttons in each state; an offered plan drawn as D118 with its Keep buttons.
