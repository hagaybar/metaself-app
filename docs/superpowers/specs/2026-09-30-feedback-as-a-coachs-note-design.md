# Feedback as a coach's note — D108 (amends D87)

> Written and approved 2026-09-30. Under D87 the feedback after a session was a headline and four headed
> parts (against the plan, what the numbers say, for next time, this week). It read as a formal report,
> mostly restating figures the band already shows, and the heading "Against the plan" read as a
> reproach. Instructions tried on the workbench (D106) that ask for one short note — the session placed
> in the record's story, the band's figures not read back — read better, and are adopted here.
> **Every figure in an example is invented.**

## D108 — A headline and a note

- **The reply is a headline, a note and plan_followed.** The note is one short paragraph written to the
  owner. The four parts are no longer asked for.
- **The instructions are the ones tried on the workbench**, recorded in `TrainerPrompt`'s feedback
  task: write as a coach who has followed them for months; say what the band cannot; place the session
  in the weekly plan (week and of_weeks, D107, and whether it is the first planned session), against the
  recent weeks and months, and against their two aims; more than planned at an easy effort is a good
  sign, held back only for a reason in the record or their words; a figure's source or estimate named
  only when it changes the advice; no trend claimed from a few days; at least one concrete comparison
  with a figure from the record, never an estimated one; no sentence that would fit anyone; a next-time
  line only when it follows from the record. The shared part (COMMON) is unchanged except that its
  "Short sentences." is not sent with this task.
- **plan_followed is still judged**, only against the session plan the review names (design question 9),
  and still overridden to no_plan when none was sent. The note does not mention it.
- **The screen** shows the headline and then the note. The headings go.
- **Feedback already stored keeps its four parts** and is shown as before. Nothing is rewritten; a stored
  feedback is new-shaped when it carries a note.
- **Earlier feedback sent to the trainer** (D87, the last three) goes in the shape it was stored: a new
  one as headline, note and plan_followed; an old one as before.
- **The backup** carries a feedback as it is stored; an old app reading a new feedback is not a case this
  app supports (backups restore forward).

## Also in this release — the workbench keeps its choices (amends D106)

The workbench's chosen path and its inputs (the session, the plan answers, the weeks-ahead answers, the
change words) survive Android closing the app in the background while a file picker is open. A loaded
file's text still lives in memory for the visit only (D106): if the app is closed, the page returns to
the app's own instructions and says so by its "Will send" line.

## Not built here

- "Use these for real": adopting a workbench file as the trainer's instructions.
- Changing the plan, weeks-ahead or adjust paths, or the weekly letter.
- Rewriting stored feedback into the new shape.
