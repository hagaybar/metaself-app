# The letter sounds like a coach — D140 to D143

> Written 2026-10-11 and approved the same day, from the mockup "The letter, rewritten". Amends D101
> (what the letter is sent) and D102 (what it says, and how) and the letter page of D103. **Every figure
> in an example is invented.**

## Why

The letter's figures are right and its delivery fails. It is a form of five headings, each filled with
a recital of totals; the one finding that mattered was softened into "a useful point to notice, not a
verdict"; and effort is acknowledged by listing it, not by saying what it took. The instructions caused
much of this: "never as a failure" became "never say anything", and a model that sees only weekly totals
can only repeat them. The owner has agreed to more of his record being sent so the letter can do better.

## D140 — The letter's shape

The reply's texts (all required, non-empty), and how the letter page shows them:

| Part | Shown as |
|---|---|
| headline | the title — the coach's judgement of the week, warm where the week earned it |
| recognition | a tinted card, **WHAT YOU DID THIS WEEK**: two to four short paragraphs |
| paying_off | a paragraph, its first words in bold — how the effort is showing in the record |
| look_after | a card, **ONE THING I WANT TO LOOK AFTER** |
| rests_on | under look_after, small: what it rests on ("7 of 7 days logged · this week against the 4 before") |
| next_week | an outlined card, **FOR NEXT WEEK** |
| close | one line in italics, set apart |

Under "From your trainer · counted on your phone", above the parts. **The figures table (D103) moves
behind "The week's figures ›"**, opening in place. The notification and the day's margin note use the
headline, as now.

**Letters already written keep their shape**: a stored letter with D102's six parts is drawn as it was.

## D141 — What the letter is sent (amends D101)

Besides D101's weekly figures, the ledger (D110) and the plan:

- **The last 35 days, one row a day**: the date and weekday; planned minutes and own minutes (counted as
  the ledger counts them); the sessions' kinds and how they felt; steps; the night's sleep in minutes
  (the night ending that morning); calories and protein logged that day, or null when the day was not
  logged.
- **The patterns of D142.**
- **The owner's own words**: this week's answers to "How did it go?" (D85); the words given with plan
  requests in the last five weeks; About me.
- **The plan's goal** (D119, D127) and the trainer's view of it (D129) when one runs.
- **The last three letters'** look_after and next_week (or D102's look_at and next_week for older
  letters), with their weeks.

The "nothing else is sent" test is updated to exactly this list. Still never sent: a meal, a food's
name, an amount, a time of eating, a weigh-in, raw readings.

## D142 — Patterns the phone works out

Counted on the phone over the 35 days, each sent with its figures and how many days or sessions it
rests on, and **only when each side rests on at least three**:

1. **Sleep and sessions**: sessions after a night under the period's median sleep, against after a
   longer night — count and average minutes each.
2. **Weekdays and weekends**: counted minutes a day on each.
3. **Food on moving days**: calories logged a day on days with at least 20 counted minutes, against
   days with fewer — logged days only.
4. **How sessions felt**: the share felt easy this week, against the four weeks before.
5. **Longest run**: the longest stretch of consecutive days with at least one counted session.

## D143 — How it is written (replaces D102's rules)

- **Recognition first, and specific.** Name what the effort took, not only what was done: going on the
  day it did not fit, choosing to do more, keeping a habit like logging every day. Say why it matters.
- **Sessions beyond the plan are the owner's own choice** and are described so — "of your own", "your
  own choice". Never "nobody asked for", "unasked", "extra" or anything that makes them sound
  unwanted.
- **Write as the coach, in the first person, to him in the second.** Short sentences. Warmth comes from
  specifics; no flattery, no exclamation marks, no emoji, no slogans.
- **One thing to look after**, stated plainly and framed as protecting his progress. It connects two
  things or shows a change — never repeats a total. No hedging: phrases like "not a verdict" or "a
  useful point to notice" are not used. rests_on says what it rests on, and says so when that is few
  days.
- **Prefer a pattern (D142) or a thread across letters** for paying_off or look_after when one is
  telling; never claim a pattern that is not in the patterns or plainly visible in the day rows.
- **next_week follows** from recognition and look_after: one or two concrete steps. When last letter's
  next_week can be checked in the record, say how it went.
- **Figures**: only those given, or a difference or ratio of two given ones (as "350 under your target").
- Nothing medical is diagnosed; an injury in his words is planned around (as D102).
- week_note (D116) is still returned.

## Not built here

A conversation with the trainer; letters on another day than Sunday; sending the letter's day rows to
any other trainer path.

## Tests

- D140: the new shape parsed and drawn in order with its headings; rests_on under look_after; the figures
  table closed and opening; an old six-part letter drawn as before.
- D141: the request carries the 35 day rows (unlogged food null, sleep per night), the owner's words, the
  goal and its view, the last three letters (old and new shapes); the "nothing else is sent" test.
- D142: each pattern's counts, the median split, the at-least-three rule omitting a pattern, logged days
  only for food.
- D143: the instructions carry recognition first, the own-choice wording and its banned phrasings, the
  no-hedging rule, the figures rule.
