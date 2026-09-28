# The trainer knows you — D89 to D91

> Written 2026-09-28 and approved the same day. The trainer (D84–D88) sees six weeks of sessions and
> nothing standing about the person it advises: from its side, the record begins six weeks ago, and a
> lasting fact — an old injury, a dislike, what the training is for — reaches it only if it was written
> on a recent session. This adds a year of short monthly lines and a note the owner writes once. It also
> makes a workout file that finds no partner harder to double-count. **Every figure in an example is
> invented.**

## D89 — A line for each of the last twelve months (amends D84)

Every trainer request also carries up to twelve **monthly lines**, computed on the phone, oldest first.

- **Which days:** the twelve calendar months before the current month, cut at the day before the
  42-day detail begins (D84), so no day is counted twice. The month holding that cut is a part month
  and says which days it covers. A month before the stored record begins is left out; a month inside
  it with no sessions still gets a line.
- **Each line holds:**
  - sessions, counted by kind, and their total minutes;
  - distance per kind, where any session of that kind has one;
  - the longest session's minutes;
  - the most active week: the highest-distance Monday-to-Sunday week whose Monday falls in the month,
    and its distance;
  - average heart rate over sessions that have one, weighted by their minutes;
  - how many sessions felt easy, right and hard, where reviews exist (D87);
  - average steps a day, over days that have a step count;
  - the change in the smoothed weight trend from the month's first day to its last, when the trend
    exists at both ends (never a single weigh-in). The trend at each end must rest on a weigh-in at
    most 14 days before it (or on it); after a longer gap the month has no weight change.
- Sessions counted are the same as everywhere else: visible, and counted by the week (D74, D81).

An invented line: *"May: 9 sessions (7 walks, 2 swims), 400 min; walking 38 km, swimming 1.5 km;
longest 75 min; best week 12 km; heart 112 average; felt 5 right, 1 hard; 6,500 steps a day; weight
−0.8 kg."*

## D90 — About me, for the trainer (amends D84)

- A single free-text note, **at most 1,000 characters**, which the owner writes once and edits any
  time: injuries, preferences, equipment, what the training is for.
- Edited from the Trainer screen: a card **About me** under the screen's title, showing the note's
  first two lines, or "Tell the trainer about yourself — injuries, likes, what you're aiming for" when
  empty; tapping opens a page with the text field and **Save**.
- Sent, unchanged, with every trainer request. The system prompt tells the model it is the owner's
  standing description, to be weighed with the record, and that where it conflicts with the numbers the
  numbers are what happened.
- Stored in the preferences store beside the AI settings, and carried by the backup, whose format
  becomes **6**; formats 1–5 still read (no note).
- The privacy line under each ask button (D84) names it: "…; your note about yourself; …".

## D91 — A workout file that finds no partner (amends D82)

- **Each session line shows its start time**: "Walking · 10:20 · 2.0 km · 25 min · avg 105 bpm" (invented), so
  a file whose time differs is visible.
- When a file matches no session (D82's rule is unchanged) but the day it falls on holds one or more
  visible sessions **of the same kind**, the result says "No session matches this file exactly (Thu 3
  Sep 10:30). Is it one of these?" (an invented time) and lists them with their start times and minutes, each a button
  that fills that session as D82's choose does. **Add it as a workout** is still offered, below them,
  under the line "Or, if it is a session the record does not have:".
- When the day has no session of that kind, the result is as before.

## Not built here

- Swimming lengths or pool size: the record does not hold laps, so swimming is distance and time.
- Monthly lines further back than twelve months, or a yearly line.
- The fitness evaluation (the trainer's third use) — its own spec.
