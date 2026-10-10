# The Trainer page shows the week — D113, D114, D115, D116

> Written 2026-10-10 and approved the same day. Part 2 of four in reworking Movement and the Trainer
> (part 1: D110–D112, the week ledger). This part redraws the **top of the Trainer page** and gives the
> Trainer a way in from Today. The plan screen is the next part; Movement and the letter after it. A
> mockup was chosen over a card-based alternative. **Every figure in an example is invented.**

## Why

The Trainer page opens with the About me box and a heading with nothing under it; the week is a list
of ticks in grey text; what the week held beyond the plan, the night's sleep and the food are nowhere
on it; and the page is reachable only through a small word in Movement's title bar. Everything the
page needs is now counted by the week ledger (D110). This part shows it.

## D113 — The Trainer from Today

**Today's top bar gains the word "Trainer"**, beside the menu button, opening the Trainer page — a
word, not an icon, for the reason the bar already gives (no core icon means it). The menu also lists
"Trainer" after "Movement". Movement keeps every way in it has: the step bar and the menu. Movement's
own "Trainer" button stays.

## D114 — The top of the Trainer page

From top to bottom:

1. **The week's header**: "THIS WEEK · MON 5 – SUN 11 OCT", then **the verdict** (D115) in the display
   face.
2. **The week strip**, on a card. Seven columns, Monday to Sunday, then — only when the week holds
   open planned sessions — an eighth column headed "To do".
   - Each counted session of the week is a **block** in its day's column, stacked oldest at the bottom,
     its height proportional to its minutes. The scale is set so the tallest column fills the strip;
     a block is never shorter than a legible minimum.
   - A session that **ticked a planned session** (the ledger's practice) is drawn **solid in the dark
     green** with a tick above its column; every other session (beyond the plan) **in the light
     green**. A confirmed short session counts as ticked.
   - Each **open planned session** is a **dashed outline** in the "To do" column, labelled with its
     minutes ("40′"). Planned sessions have no day (D94), so none is drawn on a day.
   - Days after today are empty columns; **today's letter is bold**.
   - A legend under the strip: Planned, done · Planned, open · Beyond the plan. With no plan running
     the legend has one entry and the strip is all light green.
   - **Tapping the strip opens Movement** at this week.
   - **The shorter-session question** (D105: "count it for this?" with Yes / No) sits on the card under
     the strip whenever one is open, unchanged in what it does.
3. **Three boxes**, side by side:
   - **Steps**: the week's steps a day.
   - **Sleep**: the week's average a night, and beside it the difference from its usual, rounded to
     ten minutes — "30 min under usual", "20 min over usual", or "as usual" when it rounds to zero.
     **Tinted amber when under**. No usual: the average alone. No average: the box is absent.
   - **Food**: the week's calories a day and the target ("1,300 kcal · target 1,650"), and "over N
     logged days" when fewer than seven were logged. Nothing logged: the box is absent.
   Absent boxes leave the others to share the row. Each figure is the ledger's (D110), so it is the
   one the trainer was sent.
4. **The coach's note** (D116), with when it was written. None yet: nothing is shown.
5. **One main button**, the next thing to do:
   - a plan running with an open session: "Plan the open walk" (the open session's kind), which opens
     Plan my next session as today;
   - a plan running with nothing open: "Plan my next session";
   - **no plan running, or one that has ended: "Evaluate me and plan the weeks ahead"** — now the
     filled button, where it was the outlined one.
6. **Waiting for your words** (D85), unchanged.
7. **Your kept plan** (the single-session plan, seven days), unchanged, when there is one.
8. **A list of plain rows**, each opening what it names: **Your plan · week 2 of 2** (See the plan —
   the plan screen; absent with no plan), **Weekly letters**, **Earlier sessions**, **About me**.
   "Adjust the plan" moves to the plan screen. Earlier sessions become a page of their own, the list
   that is on the Trainer page today.

The old plan card, the old position of About me, and the "Weekly letters" heading go.

## D115 — The verdict, written by the phone

One sentence, two clauses at most, counted from this week's ledger; **never the AI's**, so it is
current the moment the page opens and costs nothing.

**The first clause is the week's minutes**: "156 minutes moving this week." — the sum of every counted
session's minutes this week. No session yet: "Nothing recorded yet this week."

**The second clause is the plan**, when one runs this week:

| Situation | Clause |
|---|---|
| every planned session done | "Every planned session done." |
| one open | "One planned walk still to do." (its kind: walk, run, swim, ride, session) |
| more than one open | "Two planned sessions still to do." (number in words to ten) |
| no plan this week | no second clause |

No praise and no reproach in it (D14): the coach's note says what it means.

## D116 — The coach's note, written by the AI

**Every trainer answer** — feedback after a session, plan my next session, evaluate, adjust — **and
the weekly letter** now also return **week_note**: at most two sentences about the week as a whole,
following D112's rules (both ledgers; lead with what matters; recovery shapes intensity; food is
context; no figure the app did not count). It is asked for in the same request; **no request is ever
made just for it**.

**The newest week_note is kept** on the phone with the moment it was written and what wrote it, and
shown on the Trainer page as the coach's note with "Written Sat 09:12" — or "From Sunday's letter".
A note written in an earlier week is not shown: a week's note is about its week. A reply the owner
discards (Ask again, Keep the old one) still leaves its note: it was written from the same record.

A reply without week_note is still a valid reply (a model that leaves it out loses only the note).

## Not built here

- The plan screen's new design (next part), Movement's, the letter's.
- A conversation; goals; reminders.
- Tapping a block to open its session.

## Tests

- **The verdict**: each row of D115's table, the minutes clause, the empty week, numbers in words.
- **The strip's model** (pure): each session in one column, ticked or beyond as the ledger says; open
  planned sessions in "To do" only; days after today empty; the scale's tallest column; the minimum
  block height.
- **The boxes**: rounding to ten minutes, "as usual", amber only when under, absent boxes.
- **The note**: parsed from each path's reply and the letter's; kept with its time and source; an
  earlier week's note not shown; a reply without it still valid.
- **The page** (Compose render): the order of D114; the main button's three cases; the shorter-session
  question still answerable; Today's bar shows "Trainer" and it opens the page.
