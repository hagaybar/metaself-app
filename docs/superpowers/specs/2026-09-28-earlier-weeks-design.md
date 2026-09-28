# Earlier weeks on the Movement screen — D83

> Written 2026-09-28 and approved the same day. The Movement screen (D73–D75) showed only the current
> week, so a session filled in from a file (D82) on an earlier day had nowhere to be seen, and on a
> Monday the screen held a single day. **Every figure in an example is invented.**

## D83 — Step back and forward a week at a time

- Beside the kicker, **‹** (previous week) and **›** (next week) buttons, 48 dp targets with labels
  ("Previous week", "Next week"). **›** is absent on the current week; nothing after this week is shown.
- The kicker reads "THIS WEEK · FROM MON 31 AUG" for the current week, "LAST WEEK · FROM MON 24 AUG"
  for the one before, and "WEEK OF MON 17 AUG" further back. *(Amended during build:)* a date outside
  today's year — the kicker's Monday or a day row's heading — also says its year ("WEEK OF MON 16 JUN
  2025", "Sun 22 Jun 2025"), judged date by date, so a week across New Year gives the year only to
  last year's days.
- A past week shows **all seven days, Sunday first** (today-first order applies only to the current
  week), with the same row rules (D73); **no day is open** when a past week is shown; the headline is
  that week's distance, average movement calories and workouts (D74); the "last four weeks" line
  stays relative to the week shown.
- How far back: as far as the stored record holds days (the earliest `health_days` row or workout);
  **‹** is absent at the earliest week.
- Leaving the screen and coming back returns to the current week. *(Amended during build:)* a same-day
  trip to the file picker or to another app is not leaving — the shown week is kept, the same rule
  D82's file import already needed (so a filled-in session stays visible where it landed); Back still
  returns to the current week. "Log a workout" logs onto the open day; when no day is open (a past
  week with nothing opened) it is disabled with a line "Open a day to log onto it".
- Swiping left/right between weeks is not built (buttons only), to keep day rows' taps unambiguous.
