# The trainer's instructions workbench — D106, D107

> Written and approved 2026-09-29. A hand test, run in a chat window on an invented record, sent the
> feedback path's data twice — once under today's instructions, once under a looser brief (a short note
> from a coach who knows the record; say what the band cannot) — and the looser brief put the session in
> the context of the weeks before it where today's read the band's figures back. It also overclaimed a
> trend from a few days. One run in a chat window is not the API, so the comparison is to be repeated
> on the phone, on the real record, through the real call: this page.
> **Every figure in an example is invented.**

## D106 — A page for trying other instructions on the real record

A testing tool, not a feature: it changes nothing the trainer does elsewhere.

### Where and what

**Settings → the last entry, "Test the trainer's instructions".** The page has three parts, top to bottom.

1. **The path**, one of the trainer's four (D86, D87, D93, D97), each with the inputs its real screen asks:
   - **Feedback on a session** — pick one session of the last 42 days (the same visible, counted sessions
     the request carries). Its stored review, if any, supplies felt, words and its session plan; nothing is
     typed here. The planned tick is the one the plan card gives it (D95, D105).
   - **Plan my next session** — the same five answers as the real form (activity, time, feeling, wish,
     words). The next planned session goes along when a plan runs, as it does for real.
   - **Review and plan the weeks ahead** — weeks, sessions a week, words; the start day and the last
     evaluation as the real ask computes them.
   - **Change the rest of my plan** — words. Offered only while a plan runs; otherwise the path says
     "No plan is running."
2. **The instructions.**
   - **Load instructions from a file** — the system file picker (Storage Access Framework), plain text,
     so a file on Drive works. The loaded text lives in memory for this visit only.
   - **Save the app's instructions to a file** — the exact system message the chosen path sends today
     (the shared part and the path's own, joined as `TrainerPrompt` joins them), as a starting point.
   - A line always states which will be sent: the file's name, or "the app's own instructions".
   - The file's text replaces the **whole** system message. No shared part is kept.
3. **Send**, then the reply as plain text, with **Copy reply** and **Copy what was sent** (the request
   body, verbatim).

### What is sent

- **The user message is the real one**, built by the same code from the same record: the request
  (`TrainerRequest`) the real path would build for these inputs, serialised by `TrainerPrompt`'s own
  user-message function. The workbench adds and removes nothing.
- **The model and its settings are the owner's** (Settings, D57): its `RequestProfile` — temperature,
  reasoning effort — applies as it does for real.
- **No reply shape is imposed.** No `response_format`, and no schema instruction appended. The reply is
  shown as the model wrote it, JSON or prose; it is not parsed or checked.
- With the app's own instructions loaded, the system message is today's exactly, but the reply is still
  unpinned — so "the app's own" is the fair baseline to compare a file against.
- Nothing leaves the phone that the chosen path does not already send (D84).

### What it never does

- It stores nothing: no review, no feedback, no session plan, no programme, no confirmation. A reply is
  never sent back to the trainer as earlier feedback (D87), never ticks a planned session, and is never
  offered to keep.
- Feedback on a session does **not** save the review first, as the real path does; it reads the stored one.
- It never runs in the background, and the real screens are unchanged.
- A failed call shows the same failure wording the real paths use; nothing is retried by itself.

### How it is checked

- For each path, the workbench's user message equals the real path's for the same inputs and record.
- A workbench run over a fake store writes nothing (every write method of the stores fails the test).
- The body sent carries the loaded text as the only system message and no `response_format`.
- Building the question is shared with `AskTheTrainer`, not copied: the real paths and the workbench
  call the same question builders, so the first check cannot pass by coincidence.

## D107 — The trainer is told how long the plan is

Wherever a planned tick is sent (`question.planned`, in the plan and feedback questions), it carries the
plan's length beside its week: `"week": 1, "of_weeks": 2`. The shared instructions say so in one clause.
This goes to the real trainer as well as the workbench: without it, no instructions can say "the first
week of two".

## Not built here

- **The food estimate and the weekly letter (D99–D104).** The workbench covers the trainer's four paths
  only; the letter is prose to the owner too and is the obvious next path if the page earns its keep.
- Changing today's instructions. The workbench is how a change is chosen; the change itself is a
  separate decision, recorded against D87.
- Editing the data sent, keeping a history of runs, choosing another model on the page, or comparing two
  replies side by side.
