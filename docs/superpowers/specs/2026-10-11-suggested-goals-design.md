# Suggested goals, and a goal of more time each week — D126, D127

> Written 2026-10-11 and approved the same day. Follows "one plan, in one place" (D119–D125). **Every
> figure in an example is invented.**

## Why

Plan ahead (D124) asks for a goal from a blank form, with two kinds to choose from: keep a steady rhythm,
or a distance by a date. Choosing a goal is itself coaching work: what is within reach depends on the
record the trainer already sees. And the two kinds leave a gap — building up when the sessions are mostly
walks, where distance is not the point.

## D126 — Suggest goals

**Plan ahead's form gains an outlined button, "Suggest goals", above the goal choice.** Tapping it asks
the trainer — one request, only when tapped (D84) — for **three goals** fitted to the record. While it is
asked the button reads "Suggesting…"; a failure is shown as the form's other failures are, and the form
stays usable.

**The request** is a new trainer path, *suggest goals*, carrying what the plan paths carry: About me,
the six week ledgers (D110), how past planned weeks went (D105), the running plan's goal and outline when
one runs (D125), and the sessions a week and words already on the form (words may be empty). Its
instructions:

- three goals that **differ in kind** where the record allows — one about consistency (a steady rhythm),
  one about building time (D127), one about a distance — and otherwise differ in ambition;
- each fitted to what the ledgers show was actually done, both ledgers counted (D111), and within reach
  by its date under the coaching conventions (D125);
- the form's words, when given, weigh more than the record;
- each with a title of at most eight words, the goal, sessions a week (2 to 5), and **one sentence of
  why** that names something the record shows — the voice rules of D108 and D112 apply; no figure the
  app did not count.

**The reply** is three suggestions, each `{title, goal, per_week, why}`, the goal in the shape it is sent
and stored (D119, D127), plus the optional `week_note` (D116). The phone checks each one: a distance or
time goal's date moved to the Sunday of its week and required 6 to 30 weeks ahead, its figures in the
form's bounds, sessions a week 2 to 5. **A suggestion that fails is dropped; a reply with none left is
refused** as a reply that does not fit.

**On the form**, the suggestions are cards under the button: the title, the goal in the phone's words
(D119's goal line, e.g. "150 min a week · by Sun 20 Dec · 10 weeks to go"), "2 sessions a week", and the
why. **Tapping a card fills the form** — the goal, its figures, sessions a week — and marks that card
chosen; the words are left as typed. Everything stays editable. The suggestions are not stored: they live
while the form is open, and asking again replaces them.

The workbench (D106) does not try this path.

## D127 — A goal of more time each week

A third goal joins D119's two: **More time each week** — a number of minutes a week, 30 to 900, by a
date 6 to 30 weeks ahead (a Sunday). The plan ends on that date's week, as a distance's does.

- **The form**: a third choice, "More time each week", with rows MINUTES A WEEK (moved in steps of 10,
  starting at 150) and BY, as a distance's.
- **The goal line**: "150 min a week · by Sun 20 Dec · 10 weeks to go"; then "goal week", "goal date
  passed", as D119.
- **Sent and stored** as kind `weekly_minutes`, with `minutes` and `by`; sent with `weeks_to_go` counted
  by the phone, as a distance is (D125).
- **The trainer is told**: build the weekly total towards the goal's minutes, reaching it in the goal's
  week and holding it there — no taper, there is no race; base and build blocks, with the conventions'
  easier weeks.
- The outline's coverage check (D120) is a distance's: every week from the first Monday to the goal's
  week.

## Not built here

Pace or speed goals (pace is not reliably recorded); goals for sleep or food (the trainer plans
sessions); the workbench's distance and time goals; storing suggestions or showing them elsewhere.

## Tests

- **D126**: the request carries the ledgers, the running goal and the form's words; the instructions ask
  for three of differing kind; a reply's dates moved to Sunday; an out-of-range suggestion dropped, a
  reply with none left refused; a reply without week_note still valid; tapping a card fills goal, figures
  and sessions a week and leaves the words; asking again replaces the cards; a failure leaves the form
  usable.
- **D127**: the goal line's forms; stored and sent JSON, with weeks_to_go; the plan's weeks run to the
  goal's week; the form's third choice, its step and bounds; the instructions carry the time goal's rule.
