# The trainer sees the whole week — D110, D111, D112 (amends D84, D101)

> Written 2026-10-10 and approved the same day. Part 1 of four in reworking Movement and the Trainer:
> (1) this — what the trainer is told; (2+3) each screen redrawn with a coach's voice; (4) a
> conversation with memory. **Nothing on screen changes here** except the line under each ask button
> that says what gets sent. Every figure in an example is invented.

## Why

A weekly plan of two walks counts, today, only the sessions that tick or attempt one of its places
(D95, D105). Once both places are ticked or attempted, every further walk is dropped from what the
trainer is told about the plan; a swim, when the plan holds only walks, is dropped at once. The six
weekly totals (D84) carry a session count with no kinds, minutes or effort, and the everyday walking
that never becomes a session reaches the trainer only as distance. So a week holding six walks can be
reported as "1 of 2", with nothing beside it that recognises the other five — the plan asked for less
than was done, and nothing joined the two.

Sleep, resting heart rate, HRV and food were kept from every trainer path (D84). The owner has now
agreed that weekly summaries of them may go.

## D110 — The week, in two ledgers

For any Monday-to-Sunday week the phone counts one **week ledger**. Every visible, counted session
(D74, D81, combined per D92) of the week belongs to **exactly one** of its two ledgers, so nothing is
counted twice and nothing is dropped.

**1. Practice** — present only for a week a kept weekly plan covers (D94, counted from D105's moment).
Each planned session with its outcome — done, done short, not done — exactly as `PlanProgress` counts
it today. **A planned session not done stays not done**; nothing from the other ledger fills it.

**2. Beyond the plan** — everything else the record holds for the week:

- **Other sessions**: every session that ticked no planned place — attempts (D105), sessions with
  nothing of their kind left to tick, and every session of a week no plan covers — **grouped by kind**.
  For each kind: how many, total minutes, total distance where recorded, and **brisk minutes**: minutes
  in heart-rate zones 2 to 5 (at or above 60 % of the maximum, D70), summed over the sessions that have
  zones. A kind none of whose sessions has zones has no brisk figure — **nothing measured is not zero**.
  An attempt keeps its mark: "an attempt at a planned 30-minute walk" is said beside it.
- **Everyday movement**: steps a day and distance a day, each averaged over the days that record it.
  **Distance is never split** into "in sessions" and "outside them": the day's figure and the sessions'
  can come from different sources, and a subtraction of the two would be a figure nobody measured.

**3. Context**

- **Food**: the days logged, and the average of the logged days' calories and protein — the letter's
  figures (D100) — with today's calorie target beside them.
- **Recovery**: average sleep a night, average resting heart rate and average HRV, each over the days
  that record it, and each with **its usual**: the same average over the 28 days before the week's
  Monday. Fewer than 7 recorded days in those 28 and there is no usual — a usual of three nights is not
  a usual. **A week recording a figure on fewer than 2 days has no average for it**: an average of
  one night is that night, which D111 never sends. The phone states both figures; it never says "worse" or "better".

A week with no practice ledger simply has none. A figure no day recorded is absent, never zero.

The ledger is pure and counted afresh from the record every time, like `PlanProgress` (D95): only
stored answers are kept, so a session deleted, split or combined later changes the ledger with no
bookkeeping.

## D111 — Who is told, and what may leave the phone (amends D84, D101)

**Every trainer request** — feedback after a session (D87), plan my next session (D86), evaluate and
plan (D93), adjust (D97) — carries **six week ledgers**, this week (so far) and the five before it.
They replace the six weekly totals (D84's "six weekly totals"); the 42 days of sessions and the monthly
lines (D89) are unchanged. This week's rhythm (D87) is unchanged.

**The weekly letter** (D101) carries the ledger of its week. Its figures (D100) and its box are
unchanged.

**What may now leave the phone**, in addition to D84's list and D101's:

- a week's average sleep a night, average resting heart rate and average HRV, and their usuals;
- a week's days logged, average calories and protein a day, and the calorie target.

**Still never sent:** a meal, a food's name, an amount, a time of eating, a single night's sleep, a
single day's heart rate, raw readings, sleep stages, oxygen, breathing rate, names, device or app
names. Still **only when the owner taps** an ask button, or for the Sunday letter while its switch is
on (D101) — never otherwise in the background.

**The line under each ask button** that says what gets sent (D84) names the additions: "…; weekly
averages of your sleep, resting heart rate, heart-rate variability and food; …".

## D112 — How the trainer uses it

The trainer's instructions, on every path and in the letter, gain these rules:

1. **Both ledgers, every time.** Planned practice is judged as a commitment: a planned session not done
   is named as not done, and never excused by other activity. What was done beyond the plan is
   recognised **specifically**, with its figures ("three other walks, 90 minutes, 40 of them brisk"),
   never as "keep it up".
2. **Lead with what matters most** this week, whichever ledger it is in. A plan that asks for less than
   the owner already does is itself worth saying, and is a reason to suggest more.
3. **Recovery shapes intensity.** Sleep below its usual, or a resting heart rate above its usual, is a
   reason for an easier session or a rest day, and is said as such. The trainer does not diagnose.
4. **Food is context.** The trainer may note eating well below or above the target alongside the
   training load. It never prescribes a diet, a food or an amount.
5. **No figure the phone did not count.** Every figure the trainer states comes from the request. It
   may compare two of them; it never estimates one.

## Not built here

- Any change to a screen beyond the privacy line (part 2).
- A conversation, goals, reminders, an observed heart-rate maximum (D70), sets and reps.
- A split of distance into sessions and everyday movement (D110 says why).

## Tests

- **The ledger** (pure): each session lands in exactly one ledger; a planned session not done stays not
  done however much else the week holds; attempts are marked; a week no plan covers has no practice
  ledger and all its sessions beyond it; brisk minutes absent, not zero, for a kind without zones; the
  recovery usual is absent under 7 recorded days; every average is over the days that record it.
- **The request**: six ledgers, newest first; the never-sent list still holds (the existing request
  test's forbidden fields, updated for D111's additions and no further).
- **The prompt**: the D112 rules are present in each path's instructions and in the letter's.
- **The privacy line** names the additions.
