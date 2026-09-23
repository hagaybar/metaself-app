# One consistency figure on the day — D52

> Decided 2026-09-23, on this repository's issue #22 (the old repository's #65, which D49 left
> open: *"where they are shown is issue #65's decision and is not settled here"*).
>
> The complaint: the day's colophon printed three figures on one line — *"N days in a row · N days
> logged · N of the last 30"* — which wrapped on a phone, said one thing three ways, and was set as
> body text with middle dots, so nothing about it looked like an achievement.
>
> Every figure below is invented to illustrate the rule beside it.

---

## Why there is a decision here at all

The wrapping is the symptom. Underneath it:

- **Three answers to one question.** A run, a lifetime count and a thirty-day count all measure how
  consistently things are written down. Printed together, the reader compares them instead of taking
  one meaning away.
- **Early on they are the same number.** Until the first gap and the first month, a run of 5 is also
  5 days logged and 5 of the last 30. The line spends three slots saying one thing.
- **Nothing ranks them.** Set in the colophon's smallest face, a milestone reached looks exactly like
  any other day.

D13 is untouched: all three are still counted from the record, never stored, and backfilling a day
still repairs the run. This decision is only about which is shown, where, and how.

---

## D52 — The day shows one consistency figure, and it picks itself

### The rule

| The record | The day shows |
|---|---|
| A run in force of **3 days or more** | the run — *"12 days in a row"* |
| No run, or a run of 1 or 2 days | the thirty-day count — *"4 of the last 30 days"* |
| Nothing in the last thirty days, or nothing ever | **nothing** |

"In force" is D13's run exactly as `Streaks` counts it: ending today, or ending yesterday on a
morning before anything is logged. A run that has ended is not mentioned.

**Never a zero, and never a sentence about a run that ended.** D14 (the past never nags) and D22
(nothing is said about going the wrong way) stand unchanged. "0 of the last 30 days" is the same
reproach as "0 days in a row", so it is not printed either.

**Why 3.** A run of 1 or 2 is not yet a run worth naming, and the thirty-day count says more on those
days: after a single missed day, *"26 of the last 30 days"* still describes a steady month where
*"1 day in a row"* describes only this morning.

### Milestones look like one

**A run of exactly 7, 30, 100 or 365 days gets an achievement treatment**: the figure in the display
face at `headlineMedium` and the words beside it at `bodyMedium`, both in the `tertiary` teal, which
the palette defines and had used for almost nothing. No emoji, no new colour, no size outside D48's
scale.

It is shown **on the day the run reached that length, on that day's own page, and on no other**. The
morning after, the same run still reads *"7 days in a row"* until the day's first logging, but plainly:
the milestone was yesterday's.

### The weekly congratulation is not repeated

The encouragement that fires at every seventh unbroken day (`LOGGING_WEEK`) keeps its own rules. **On
a day it has fired, the figure stays plain**, whether or not the congratulation has since been
dismissed, so the milestone is said once. Of the four milestones only 7 is a multiple of seven, so
this is the only one the two can share.

**What it fired is held in memory, not stored.** Only the day it last spoke is stored, not what it
said, so if the app is closed and reopened on that same day the figure no longer knows, and can
show its milestone treatment after all. Storing which occasion fired would close that; it was left
out because the cost is one repeated milestone on one day.

### Nothing is lost

**All three figures move to a small "Your record" section at the foot of the day's full list** (the
record screen, D50), in the words they have always had: *"12 days in a row"*, *"45 days logged"*,
*"22 of the last 30"*. Each line keeps its own rule for saying nothing, so a run that has ended is
still absent there too.

This amends D50's "the record, not progress" in one small place. The three counts are facts about
the record — how much of it exists — not about how the day is going, and the full list is where
the record is read.

### Rejected

- **A thirty-day strip of dots.** It is a picture of the missed days, which D14 forbids in another
  form.
- **Rotating the three, a different one each day.** It reads as the app forgetting what it said
  yesterday.
- **The run only.** It says nothing at all after one missed day, which is exactly when a steady month
  is worth hearing about.

---

## What does not change

Not one stored number and not one count: `Streaks` counts what it counted. `StreakWording`'s three
sentences are unchanged and are what "Your record" prints. The congratulation's occasions, ranking
and wording are unchanged.
