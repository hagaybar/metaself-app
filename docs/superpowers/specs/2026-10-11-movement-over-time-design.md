# Movement over time — D135, D136, D137

> Written 2026-10-11 and approved the same day, from the mockup "Movement over time". Extends Your
> movement (D130, D131) and its earlier weeks (D83). **Every figure in an example is invented.**

## Why

Your movement shows one week at a time. Whether the weeks are building, holding or slipping can only be
seen by paging back week by week and remembering the numbers. The record is now long enough to draw.

## D135 — Three spans: Week, 12 weeks, Year

Under the title bar, a three-way switch: **Week · 12 weeks · Year**. Week is chosen each time the page
opens (and when it opens on a given week, D130/D132, it is that week).

- **The span line** under the switch names the span — "THIS WEEK · FROM MON 5 OCT" (as now), "20 JUL –
  11 OCT", "NOV 2025 – OCT 2026" — between ‹ › arrows. **Swiping the page sideways does what the arrows
  do**: one span back or forward. Nothing goes later than the span holding today.
- **Week** is today's page, with one addition: **a chart of the week's days**, drawn as the Trainer's
  strip draws them (D114: one block per counted session, planned in dark green, others light, by
  minutes; no "To do" column), above the day rows. Its headline and line are D131's.
- **12 weeks**: the twelve Monday-to-Sunday weeks ending with the week in view.
- **Year**: the twelve calendar months ending with the month in view.

Every count is the shared one (D131): a session's minutes are counted as the Trainer's verdict counts
them, and "planned" is the week ledger's practice (D110).

## D136 — The 12-week and Year charts

**12 weeks** — one bar per week, labelled by its Monday's day of the month (every other bar), the week
holding today drawn as far as today.

- **Chips: Minutes · Km · Steps**, Minutes first. Minutes bars are split: planned minutes (dark green) at
  the base, the rest (light green) above. Km is the week's km of every step (as D131's line); Steps is
  the week's steps a day. Km and Steps bars are one colour.
- **A dashed line at the average** of the weeks that have a record, labelled "avg 148".
- **The headline**: the average with its unit — "148 min a week", "18.2 km a week", "8,900 steps a day".
- **Tapping a bar opens that week** in the Week span.

Amended 2026-10-11: the week holding today is unfinished, so it is left out of the dashed line and the
headline average (all three chips), as Year leaves out the current month; its bar is still drawn, as far as
today. With no complete week recorded, the headline is the current week's figure and "so far" ("85 min so
far", invented). Browsing back, where no week in view holds today, nothing changes.

**Year** — one bar per calendar month: minutes, split as above; the current month labelled "so far".

- **The headline**: "640 min a month" — the average of the complete months that have a record; with
  none complete, the current month's minutes and "so far".
- **Tapping a bar opens 12 weeks** ending with that month's last week.

**Before the record**: a week or month before the first day the phone holds any movement (the earliest
day with a step count or a session) is **left blank, not drawn as zero**, and is left out of every
average. A span wholly before the record says "Nothing recorded this far back." and offers no further
‹.

## D137 — The line under the headline, written by the phone

One sentence, counted on the phone — never the AI's (as D115), so it is current and costs nothing.

- **Week**: against the average of the four weeks before it that have a record — "about your usual
  week" (within a tenth either way), "above your usual week of 140 min", "below your usual week of 140
  min" — appended to D131's line. Fewer than two such weeks: nothing appended.
- **12 weeks** (Minutes): "The last 4 weeks averaged 165, up from 120 the 4 before." — "up from",
  "down from", or "about the same as" (within a tenth). It needs a record in all eight weeks; otherwise
  "Over the N weeks recorded." Km and Steps say the same in their units. Amended 2026-10-11: the last 4 are
  the four complete weeks before the week holding today, when it is in view, and the 4 before are the four
  before those; "the N weeks recorded" counts complete weeks only.
- **Year**: "Over the N months recorded."

No praise and no reproach (D14).

## Not built here

Tapping a block to open its session; heart-rate or zone charts; comparing two spans side by side; the
history before the record (D72).

## Tests

- D135: the switch opens on Week; arrows and swipe move one span; nothing later than today's span; the
  Week chart's blocks match the Trainer strip's for the same week.
- D136: twelve weeks and twelve months end where they should; planned and other split per the ledger;
  Km and Steps values; the average excludes weeks before the record; blank (not zero) before the
  record; the current month "so far"; tapping a week opens Week, a month opens 12 weeks.
- D137: each sentence form, the tenth thresholds at their edges, too little record, the units.
