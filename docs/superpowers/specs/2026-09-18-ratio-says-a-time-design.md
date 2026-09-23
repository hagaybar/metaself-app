# The ratio tells him a time he can act on — design

> Settled 2026-09-18, after the live line was found counting the hours of a fast as hours of
> eating, and so reporting a kept fast as a broken window. The requirement, in three parts:
>
> - On waking, the day greets and says that if the fast is to be kept, the first meal can be eaten
>   at X.
> - Once that first meal is logged, it says that if the window is to be kept, the last meal should
>   be eaten by Y.
> - Beyond that, a marker saying how many stretches of the ratio have been kept since the ratio was
>   decided.
>
> Four details were left to judgement and the recommendation was taken on each (§4). Amends D29
> item 23 and D31's live line. The fixed-hours window is untouched.

## 1. What changes, in one sentence

Today stops describing a STATE ("still open", "nothing is judged", "you have been eating for") and
starts giving a TIME he can act on: when the fast allows the next meal, and when the window wants the
last one.

## 2. What the app can and cannot know

Written down because every sentence below is shaped by it, and the shipped defect came from
forgetting it. The app knows three things: when each thing was logged, the ratio, and the clock.

- It cannot know a meal is the LAST one when it is eaten. It learns that in hindsight, when the fast
  after it completes with nothing logged.
- It assumes nothing logged means nothing eaten. That fails in one direction only: eating that is not
  logged looks like fasting.
- It takes the moment something is logged as the moment it was eaten. D33 lets him correct that.

So **every sentence about the future is conditional** — "if you're keeping your fast", "if you're
keeping your window" — which is how the requirement was put, and is the only honest phrasing there is.
The one that shipped went wrong exactly where it stopped being conditional and stated a duration as
a fact about a present the app cannot see.

## 3. What today says

Let the latest stretch begin at **F** and its last input be at **L**. The window's closing time is
**E = F + the eating hours**; the fast completes at **X = L + the fasting hours**. Exactly one of
these is said, on today and on no other day. Every ratio and time below is illustrative:

| # | Situation | What today says |
|---|---|---|
| 1 | No stretch under this ratio yet | nothing |
| 2 | The fast after the latest stretch has completed, nothing since | "Your 14 hours were up at 12:00 — eat when you like." |
| 3 | A stretch is open and its closing time has not come | "If you're keeping your window, last meal by 22:00." |
| 4 | Past the closing time, the eating inside the hours | "If you're keeping your fast, next meal from 12:00." |
| 5 | Past the closing time, the eating over the hours | "You've eaten for 12h 0m — next meal from 12:00 if you're keeping your fast." |

- **Before noon, the sentence opens with "Good morning."** Only before noon, because the same
  sentence is said all evening too, and "good morning" at 22:00 would be wrong.
- **A time on a different calendar day from now says so**: "by 06:10 tomorrow", "from 12:00
  tomorrow". A time on an earlier day, in row 2, says "yesterday" or the date: "were up on 16 Sep
  at 12:00" — the date rather than a weekday, which is ambiguous once it is more than a week back.
- **Row 5 keeps both facts in one sentence.** The next-meal time is what he acts on. The over-run is
  what the live line of D31 exists for — a ratio whose fast is never reached is one stretch that
  never closes, and without it the app would say nothing to exactly the person failing to keep it.
- **Row 5 cannot happen before the closing time**: a stretch's eating cannot exceed the eating hours
  while less than the eating hours have passed since it began.
- Row 4 replaces, on today, the still-open line that 0.30.1 put there. On a PAST day that line is
  unchanged, as are the span and the one-input line — those describe the day's own stretches, and a
  day gone by has nothing to act on.

## 4. The four judgement calls

1. **The greeting**: before noon only.
2. **Waking after the fast has already completed**: say it was up and when (row 2), rather than
   saying nothing.
3. **Evening, over the hours**: one sentence carrying both the duration and the next-meal time
   (row 5), rather than the next-meal time alone.
4. **The correction to the live line ships first, alone**, as 0.30.1, so the app stops telling
   anyone keeping a ratio that they broke it while this is built.

Not added, and considered: an "I'm done eating" button. It would let the app say "fasting since"
with certainty, at the cost of a tap every evening, a wrong answer whenever one is forgotten, and a
second record that can contradict the log. The log already carries the information.

## 5. The marker

The bare fraction becomes words, and counts from the day the ratio was set rather than the last
fortnight: **"Kept 10 of 14 stretches since 3 Sep."** (an invented tally, the tests' own) On today and on past days alike, because it
is a fact about the record and not about the day on screen.

## 6. What does not change

- No notification of any kind (D15, D27). Everything here is on screen when he looks.
- No countdown. Every sentence is a clock time, never time remaining.
- Nothing is judged before the fast completes. Rows 3 to 5 are all about an open stretch.
- A rule never applies backwards (D27).
- The fixed-hours window, its ring and its sentences.

## 7. The decision for the record

Goes into `2026-09-02-milestone-1-design.md` §3 as **D32**, amending D29 item 23 ("before the first
meal the mark says nothing at all") and D31's live line.

## 8. What review found, 2026-09-18, and what was left

An independent review of the build, run before it reached the owner's phone. Fixed:

- **A morning after the app was kept alive overnight opened on yesterday**, under the heading "Today":
  no greeting, no next-meal time, and breakfast filed onto the day before with a time on another
  date. Older than D32, but it broke D32's main use. The day screen's "today" now moves when the
  screen comes to the front on a new date, and the pages are rebuilt around it.
- **Three clocks fed one screen**: the sentence, the tally and each day's verdicts each read the time
  at a different moment, so the tally could lag a stretch behind the sentence beside it. There is now
  one moment, read when the record changes and when the screen comes to the front, and the tally is
  recounted with it.
- **The untimed line spoke on pages with nothing untimed**, because it counted the whole read — two
  days either side. It now counts the day on screen, by the same test the walk and the meal times use.
- **A past fixed-hours day drew the ratio's count as a bare "10/14"**; it now draws the words.

Left, deliberately, and stated so nobody mistakes them for oversights:

- After three days with nothing logged, today's sentence goes quiet rather than reporting a fast that
  ended days ago; the read looks back three days.
- On the day a ratio is set, a stretch that began the night before is not spoken about until a full
  fast has passed: a rule never applies backwards (D27).
- Switching between the two kinds of window can show the old kind's count for a moment, until the
  recount finishes.
- The tally reads every day since the ratio was set, on every change to the record. Negligible for
  weeks of history, noticeable for years; worth a summary table if it ever is.
