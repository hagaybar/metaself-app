# The trainer, before and after a session — D84 to D88

> Written 2026-09-28 and approved the same day. This is the
> first part of project 3 of the health record's vision (D65): a trainer that reasons over the stored
> record. It serves two aims: **the profile's weight goal**, and **a steady rhythm of sessions**. Of
> four uses planned for it, this spec builds the first two — feedback after a session and a
> suggestion before one. A fitness evaluation with a few weeks' plan comes next, and following a
> defined target after that; the tables here are shaped so neither needs a rework. **Every figure in
> an example is invented.**

## Why the app, not a chat

The same loop can be run by hand in a chat window: describe the plan, do the session, paste
screenshots, ask for feedback. It degrades over weeks because the chat's memory is a long transcript
that the model reads less reliably as it grows. Here **the record is the memory**: every request is
built fresh from stored rows — sessions, their numbers, the owner's words, earlier plans and feedback —
and no conversation history is kept or replayed.

## D84 — What the trainer sends, and when (amends D16)

D16 says one kind of thing leaves the phone for the AI provider: a meal description, with the owner's
own key. The owner has agreed that the trainer may send his activity record the same way. **Only when
he taps** "Ask the trainer" or "Save and get feedback"; never in the background.

One request carries exactly:

- **The question:** the form's answers (D86) or the review being asked about (D87).
- **Sessions of the last 42 days:** for each, the date, kind, duration, distance, energy, average and
  highest heart rate, minutes per heart-rate zone, steps, and each figure's source (D69, D82), plus the
  owner's felt effort, his words, and the plan it was matched to, where they exist.
- **Six weekly totals:** distance, average movement energy a day, and number of sessions (D74's figures).
- **Weight trend:** the smoothed weight now and its change a week over the last 28 days (the weight
  screen's figures), and the profile's goal direction and weekly rate. No single weigh-in.
- **The body:** age, sex and height from the profile.
- **The last three feedback texts**, so advice stays consistent.

Never sent: meals or anything eaten, raw readings, sleep, the owner's name, the key's owner, device or
app names. A request counts against the same daily ceiling as a meal estimate (D57's call, shared).

## D85 — Where the trainer lives

- The Movement screen's top bar gains **Trainer**. A synced or typed session row, once open, gains
  **How did it go?** which opens D87 on that session.
- The Trainer screen shows, top to bottom:
  1. The newest session of the last three days that has no review, as a card with its main figures and
     **How did it go?** Absent when there is none.
  2. **Plan my next session** (D86). When a kept plan exists, a card with its title and "Open".
  3. **Earlier sessions**, newest first: kind, date, and one line — how it felt, whether feedback was
     read, and whether it followed its plan.

## D86 — Before a session: a suggestion

The form has four single-choice rows and optional words:

| Row | Choices |
|---|---|
| What | Treadmill walk · Outdoor walk · Run · Something else |
| Time I have | 20 · 30 · 45 · 60 min or more |
| How I feel | Fresh · Normal · Tired |
| Today I want | Easy · A push · Not sure |

**Ask the trainer** sends D84's request. The answer is asked for as JSON — a title, three to six steps
(minute range, what, how: speed, incline or heart-rate zone where they apply), and one paragraph on why
— and is shown as a list under the line "Suggested by the AI trainer · advice, not a measurement" (D4).
An answer that fails to parse is one of the call's failures, worded as the meal estimator words them.

**Keep this plan** stores it as the kept plan; there is at most one, and a new one replaces it. **Ask
again** returns to the form with the answers kept and costs another request. A kept plan older than
seven days stops being offered.

## D87 — After a session: the owner's words and feedback

For one session: its figures with their sources, the plan matched to it, then

- **How it felt:** Easy · Right · Hard — the owner's felt effort. It is stored with the review, never
  written over the session's `effort` (which on a typed workout drives the energy estimate, D77).
- **In your words:** free text; the keyboard's own microphone is the voice input.
- **Save and get feedback**, or **Just save**.

The matched plan is the kept plan when it was kept within seven days before the session started; a
"Not this plan" link detaches it. A review with feedback clears the kept plan it used.

Feedback is asked for as JSON with a headline and four short parts — against the plan, what the numbers
say, for next time, and this week (the rhythm, counted on the phone from stored sessions, never assumed
by the model) — and is shown under "From the AI trainer · advice, not a measurement". If the call fails,
the words are saved anyway and the session row offers **Get feedback** later.

If the owner's words in the question itself mention pain, dizziness or chest discomfort, the prompt
tells the model to advise stopping and seeing a doctor before anything else. "The question itself" is
the form's words when he asks for a plan (D86), and the review's words when he asks for feedback on a
session. Words on older sessions, sent as part of the 42 days, are context only: the model may mention
them, but they do not force the doctor advice first. It is a trainer for walking and running, not a
medical service.

## D88 — What is stored

Room version 8 adds two tables; both go into the backup (format 5) and so into the daily and Drive
copies. Earlier backup formats still read.

- `trainer_plans`: id, createdAtMillis, the form's answers, the suggestion as returned (JSON text), the
  model's name, and kept (boolean).
- `trainer_reviews`: id, workoutId (unique; a review belongs to one session), planId (nullable), felt,
  words, feedback (nullable JSON text), feedbackAtMillis, model.

A future target (the fourth use) will be a third table that plans and reviews can point to; nothing
here needs changing for it.

## Not built here

- The fitness evaluation and a few weeks' plan (the third use) — its own spec, once some weeks of
  reviewed sessions exist.
- Following a defined target (the fourth use).
- Talking back to the feedback as a chat; charts; notifications reminding the owner to review.
- Anything using sleep.
