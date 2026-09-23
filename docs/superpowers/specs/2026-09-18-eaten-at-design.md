# A meal says when it was eaten, and he can correct it — design

> Settled 2026-09-18. The requirement: a meal must be loggable at a chosen hour rather than at the
> moment of logging, so that something eaten earlier but written down late can be recorded at the
> hour it was actually eaten. Two places to put the time were offered — on every screen that logs,
> or on the day — and the day was chosen. Companion to D32, whose every time is derived from when
> meals were eaten.

## 1. Why it matters now

Everything the ratio says (D32) is computed from meal times: the closing time from the first, the
next-meal time from the last. The app records a meal's time as the moment it was LOGGED, so a
meal eaten at 12:00 and logged at 15:00 moves the whole day three hours. And the day screen
has never shown a meal's time at all, so he cannot even see what the app believes.

## 2. What he sees

- **Each meal on the day shows its time**, "12:10", at the start of its row.
- **Tapping the time lets him change it**, hours and minutes, and save.
- A meal whose time is not known shows that instead of a time, and tapping it sets one.

To log something he missed: log it the usual way, then tap its time.

## 3. Rules

1. **One time per meal.** A described meal, a saved meal or a gathered group was eaten together
   and carries one time; changing it moves the whole group. This is already how the record is
   stored: the time belongs to the meal, and its rows carry none of their own.
2. **Never in the future.** A future time would hold a stretch open that should have closed, and
   the closing and next-meal times would follow it. On today the latest time he can set is now.
3. **Within the day it is on.** A meal belongs to the day it is logged on, and its time stays inside
   that day, 00:00 to 23:59. Something eaten at 00:30 belongs on the next day's page.
4. **A known time makes the meal count.** A meal logged onto an earlier day is stamped with the
   moment of logging, which falls on another date, so today it is "untimed" and left out of the
   window ("One meal was written down on another day, so its time is not known."). Setting its time
   puts it back in. That is the other half of the requirement: a missed meal filled in later
   counts at the time he ate it.
5. **Correcting a time corrects the record, and verdicts follow it.** Moving a meal can change
   whether a stretch was kept. That is the record being put right, not a rule reaching backwards
   (D27): the ratio in force on that day is the one that judges it, as always.
6. **The time is his word.** It is what he says happened, so it is stored and shown as fact. Only
   the time he can see and change is stored; nothing is shown as a time that he did not either log
   at that moment or set.

7. **The time is a control of its own**, 48 points square at least, one line whatever the font
   size, and its spoken name says which meal it is for. While rows are being chosen for a meal it is
   not a separate control, so a tap there ticks the row.
8. **A time inside the hour the clocks skip in spring** is stored as the hour after: 02:30 on that
   night becomes 03:30. Rare, and deliberately left.

## 4. What does not change

- Logging itself. Every route still logs at the moment of logging, with no new step. Setting a
  time is done afterwards, on the day.
- The database layout. The time is already stored for every meal; the change is being able to see
  it and write it.

## 5. Found while mapping this, recorded separately

- **Undoing a delete re-logs the item as a new meal stamped with the moment of undoing.** It loses
  the original time, the meal it belonged to and the saved meal it came from, and on a past day it
  turns a timed meal into an untimed one. *Fixed in 0.31.6: undo puts back the row under its
  own id, in its own meal, at its own time (issue #25; see the plan
  `docs/superpowers/plans/2026-09-19-undo-restores-the-entry.md`).*
- **The window tally is read once, at start-up**, so it does not move when something is logged
  until the app is reopened. Relevant to D32's marker. *Fixed in 0.31.1: the tally is recounted
  whenever the record changes and when the screen comes to the front (see the D32 design, §8).*

## 6. The decision for the record

Goes into `2026-09-02-milestone-1-design.md` §3 as **D33**.
