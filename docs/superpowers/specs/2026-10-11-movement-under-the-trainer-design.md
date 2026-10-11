# Movement under the Trainer — D130, D131, D132

> Written 2026-10-11 and approved the same day, from the mockup "Movement under the Trainer". Part 4 of
> the Movement and Trainer rework. Amends D75 (Movement's ways in), D74 (its headline) and D113/D114 (the
> Trainer page). **Every figure in an example is invented.**

## Why

Movement and the Trainer are two destinations for one subject. The Trainer page now shows the week
(D114); Movement shows the same week day by day, under a headline — the week's km of every step — that
is not the training the Trainer counts. Movement should be the Trainer's detail, not a second front door.

## D130 — Movement leaves Today's menu; it is "Your movement", behind the Trainer

- **Today's menu loses "Movement".** Order: Weight, Trainer, Your numbers, Foods & meals, Settings.
- **Today's step bar still opens it**, on this week with today open — it is about today's steps.
- **The page's title becomes "Your movement"**, and its title-bar "Trainer" button goes. Its back arrow
  returns to wherever it was opened from.
- A shared workout file still opens it (D82). Everything else on it stays: day rows, earlier weeks
  (D83), logging and editing a workout (D76), splitting (D92), file import (D82), the review buttons
  (D87).

## D131 — Its headline is the week's minutes moving

The one large number becomes **the week's minutes moving** — "156 min" — counted exactly as the
Trainer's verdict counts it (D115: every counted session's minutes in the week, from the week ledger),
so the two pages never disagree. On an earlier week, that week's. No session: "0 min".

The line under it: "6 sessions · 21.4 km · 310 kcal of movement a day" — the session count, the week's
km (as today's headline), and the movement-kcal average (as today's second line); a part with nothing to
say is left out. The "2 workouts · 3 walks · 4 h 30" line it replaces goes.

## D132 — The Trainer page carries movement

On the Trainer page (D114):

1. **A summary line on the strip's card**, under the day letters (and above the legend): "6 sessions ·
   21.4 km · 4 weeks: 78 km" — this week's counted sessions, this week's km, and the last four weeks'
   km as Movement's "Last four weeks" counts it — then **"Days ›"** at its end. A part with nothing to
   say is left out. Tapping the line opens Your movement on this week, as the strip does.
2. **Log a workout**, an outlined button beside the main button (D114 item 5), the two sharing a row.
   It opens Your movement with the workout sheet open for today (D76); saving returns the sheet's
   usual result there.
3. **A row "Your movement"**, after "Your plan", with the line "Day by day, earlier weeks, import a
   file", opening Your movement on this week.

## Not built here

Merging "Your numbers" into Settings; the version number and a change log in Settings; the weekly
letter's rework; tapping a strip block to open its session.

## Tests

- D130: Today's menu has no Movement item and still has Trainer; the step bar opens Your movement; the
  title reads "Your movement" and has no Trainer button.
- D131: the headline equals the verdict's minutes for the same week (shared counting, tested on the
  pure function); earlier week; empty week "0 min"; the line's parts and omissions.
- D132: the summary line's parts and omissions; tapping it and the row open Your movement; Log a
  workout opens the sheet; the main button and Log a workout share a row; the row's order in the list.
