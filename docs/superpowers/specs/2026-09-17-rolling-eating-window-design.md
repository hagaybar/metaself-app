# The eating window stops counting days — design

> Settled 2026-09-17. What was asked for was a rolling window, and then the better question behind
> it: whether days need be accounted for at all, rather than hours alone. They need not, and this
> is that. Amends D29 (the measured window). The fixed-hours window is untouched.

## 1. What is wrong with what is there

The measured window judges a **calendar day**: it takes the first and last timed input between
midnight and 23:59 and asks whether that span fits the eating half of the ratio. Midnight resets it,
and the gap since the previous night is never looked at.

So the thing it is taken to be checking is the one thing it does not check. Take a case (the
times are illustrative): eating stops at 22:00, the ratio is 14/10, and the question is whether
coffee at 12:05 the next day is alright. Fourteen hours from 22:00 is 12:00, so by that
reckoning it clears by five minutes — but the app never did that sum. It would have said exactly
the same nothing about coffee at 10:00, twelve hours after dinner and well short of the fast the
ratio sets.

A ratio is a statement about **hours**, not about dates. Midnight is an artefact of the log.

## 2. The rule

An **eating stretch** is a run of timed inputs where each consecutive gap is shorter than the
fasting hours. A gap of **at least** the fasting hours closes the stretch and the next input opens a
new one.

- A stretch is **closed** once the fasting hours have passed since its last input. Until then it is
  **open**.
- A closed stretch with two or more timed inputs is **judged**. It is **kept** when its span — first
  input to last input — is no longer than the eating hours.
- An open stretch is not judged. It has a **closing time**: its first input plus the eating hours.

Fourteen hours exactly counts as fasted, the same reading the fixed window already gives its end
hour: "until 20:00" means shut at 20:00, not at 20:59.

### 2.1 One input is still not judged

A stretch holding a single input has a span of nothing and would keep any ratio, which is a
compliment for having logged almost nothing. As with days today, it is neither kept nor broken and
counts in neither half of the tally. The reason carries over unchanged; only the thing it applies to
has moved from a day to a stretch.

### 2.2 Gaps are measured between instants, not between clock readings

The current code subtracts minutes-from-midnight, which is only meaningful inside one day. Stretches
compare the moments themselves, so an hour that the clocks moved does not manufacture or erase a
fast. This is a correctness gain that falls out of the change rather than a feature of it.

### 2.3 A meal with no trustworthy hour is excluded first

Unchanged from D29. A meal typed on a different day from the one it belongs to keeps when it was
typed, not when it was eaten, and a span is far more exposed to that than a count is. Such meals are
dropped before any stretch is worked out, and counted separately as they are now.

### 2.4 A rule still never applies backwards

The rule's start day still gates everything: **a stretch is judged only if it begins on or after the
day the rule began.** Working out whether a stretch begins at all may look at an input from before
that day — that is reading the record, not judging it. Nothing already logged acquires a mark from a
decision made afterwards, which was a stated constraint and is the whole of D27.

## 3. What a day means now

**A stretch belongs to the day it began on.** Decided here rather than asked, because every
alternative double-counts: a stretch that starts on Monday evening and ends on Tuesday morning is
one stretch, and giving it to both days would make one late dinner break two days.

So the day screen shows the stretches that **started** on the day being looked at:

- **Today, stretch open:** "Started eating at 12:00 — done by 22:00." When the closing time falls
  after midnight it says so in words rather than printing a bare time that reads as this morning.
  No countdown; the no-new-notification rule (D15, D27) stands.
- **A stretch that closed and was kept:** silent. The mark says it, exactly as a kept day is silent
  today.
- **A stretch that closed and was broken:** "You ate over 11h 40m; your ratio allows 10h."
- **A stretch still open on a day already past:** "Still open — nothing is judged until you have
  fasted 14 hours." This is new, and is the honest consequence of hours instead of days.
- **A day with no stretch of its own** (you ate only inside a stretch that began yesterday): the
  window says nothing about it. It is not a broken day and not a kept one.

### 3.0a The one thing said while a stretch is still running

> Added 2026-09-17, during the build. A review found that the rule as first written
> goes completely silent for exactly the person failing to keep it: a ratio whose fast is never
> actually reached — 16/8 with meals at 08:00 and 20:00, every gap twelve hours — is one stretch
> that never closes, so no day has a stretch of its own, nothing is ever judged, and the app says
> nothing at all. That is the mirror image of the problem this whole change exists to fix.

**Once an open stretch has run past the eating hours, the app says so, without judging it.** "You
have been eating for 38h; your ratio allows 8." A statement of what has happened so far, not a
verdict: there is still no `kept` and no `broken` until the fast completes, and there is no reproach
in it (D14) and no countdown (D15, D27).

**It is about the stretch that is open right now, whichever day it began on** — a fact about this
moment, as "the window is open now" already is, and not about the day being looked at. That is what
makes it reach the case above, where today has no stretch of its own. It is therefore said on today
and on no other day.

It replaces the closing time rather than joining it: before the eating hours are up, the app says
when eating is done; after, it says how long it has been. One sentence about one open stretch.

### 3.1 The tally counts stretches

"Kept 10 of your last 14 stretches" (an invented tally), in settings and on the day screen, over the same recent span of
days the day tally already covers. Only closed, judged stretches count. An open stretch is in
neither half — the app does not guess at a verdict it cannot yet have.

### 3.2 The weekly compliment

Today the app says something kind when the seven days ending yesterday were every one judged and
every one kept. The translation is: **the last seven closed judged stretches, all kept**, and
silence otherwise. The spirit is unchanged — a whole run of success, never a partial one — and the
unit is the one everything else now counts in.

## 4. What does not change

- **The fixed-hours window.** "Eat between 14:00 and 22:00" is a statement about a day and keeps its
  day verdict, its day mark and its day tally. Two kinds of window, two shapes of answer, exactly as
  the code already keeps them apart.

  > Corrected 2026-09-17, after a fidelity review of the build. Two things about the fixed kind did
  > change, and "untouched" as written above did not admit either. Its verdict, its sentences and
  > its tally are unchanged; these are what the stretch rule cost it.
  >
  > **The weekly compliment is now silent in a week the window's kind was switched.** Its fixed-hours
  > branch counted the seven days ending yesterday with no regard for when those hours began, so
  > switching from a ratio to fixed hours mid-week pulled in days a RATIO had governed and scored
  > each of them from its own meals alone. That day-sliced reading is narrower than the record: it
  > calls an evening's eating kept and never sees it run on to noon the next day. Seven flattering
  > days, and a compliment not earned. The run is therefore clamped to the day those hours
  > began — the day screen's tally and the settings tally were always clamped that way; only the
  > compliment was not — and the consequence is accepted rather than worked around: in the week of a
  > switch, seven days the hours in force actually governed do not exist, so nothing is said. A run
  > those hours really did govern still earns it, and a test pins each half.
  >
  > **A fixed day paged back to while a ratio is in force draws its tally, and no ring.** The tally
  > is the fix: that day used to draw neither mark, because the ring was gated on today's rule and
  > the measured mark on the shown day's, so a day caught between the two lost its count and its
  > only way into window settings. The ring stays away on purpose. A stretch does have an
  > open-or-shut state, but a ring filled by whether eating is going on right now, drawn beside a
  > day judged by the hours on a clock, describes a different thing from the one that day was scored
  > by — and where it sits, beside that day's own verdict, it reads as belonging to that day. So the
  > ring belongs to the fixed hours alone, a ratio lends it nothing, and the measured kind keeps its
  > own mark. It was briefly built the other way, which is what this note corrects.
- **Everything else the app organises by day**: the food log, the calorie target, the ring, the
  macro bars, the logging streak. Only the ratio stops caring about midnight.
- **No stored data changes.** Stretches are computed from times already on the record. No schema, no
  migration, no database version bump.
- **No new notification.** D15 and D27 hold.

## 5. What this costs, stated plainly

A verdict arrives later than it used to. A day was over at midnight and could always be judged; a
stretch is not over until the fast completes, so "was last night alright?" may have no answer until
the following afternoon. The screen says "still open" rather than guessing. This was put to the
owner before the work began and accepted.

## 6. The decision for the record

Goes into `docs/superpowers/specs/2026-09-02-milestone-1-design.md` §3 as **D31** when built,
amending D29: the measured window judges eating stretches bounded by the fast, not calendar days; a
stretch belongs to the day it began on; an open stretch is not judged; the tally and the weekly
compliment count stretches; and the fixed-hours window is untouched.
