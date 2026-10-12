# Talking with the trainer — D145 to D151

> Written 2026-10-12 and approved the same day, from the mockup "Talking with the Trainer". Part 4 of the
> Movement/Trainer redesign. **Every figure, food, ache and sentence in an example is invented.**

## Why

The trainer writes to the owner — feedback after a session, a note on the week, the Sunday letter — and
cannot be answered. What the owner says about how a session felt, an ache, a preference or a goal in his
own words is either typed into one box (About me, D90) or lost. Matching rules for planned sessions (D95,
D105) keep producing exceptions; the owner's direction is that the trainer should see what he did and
respond, as a coach would, rather than gain more rules.

## Three releases

1. **Talking** (this spec, D145–D148): a conversation on the Trainer page, the trainer reading the fresh
   record and the recent conversation.
2. **Memory** (this spec, D149–D151): a sheet of what the trainer knows about the owner, rewritten weekly
   from the conversation and read by every trainer request.
3. **Acting** (a later spec): the trainer proposing changes to the plan or goals in the conversation, each
   confirmed by the owner. Not designed here.

Release 1 ships before release 2. Until release 2, conversation older than the window (D147) is not read
by the trainer; it stays on the phone.

## D145 — One conversation

- One continuous thread, on its own screen, **Your trainer**. It is opened from a filled button **Talk to
  your trainer** on the Trainer page (beside Log a workout, which becomes the outlined one), and from a
  **Reply ›** on the trainer's coach note (the week note) on the Trainer page.
- The owner types; Send asks the trainer (one request) and shows the reply in the thread. While waiting,
  the thread shows the trainer is writing; on failure the owner's message stays, marked not answered, with
  **Try again**. A message is never lost because a request failed.
- Messages are text only. No voice, no photographs, no attachments.
- The owner can delete one of his own messages (and with it the reply it received). Nothing else is
  editable in the thread.

## D146 — What arrives in the thread

- The trainer's **feedback after a session** (D87) and the **Sunday letter** (D101, D140) appear in the
  thread at the moment they were written, as the trainer's messages: feedback in full, labelled with the
  session ("After your run · 60 min"); the letter as a card (its week, when it arrived) that opens the
  letter page. Both stay where they are now; the thread shows them, it does not hold a copy.
- The trainer never writes first otherwise. Check-ins are not in these releases.

## D147 — What the trainer reads when it answers

Every reply is one request carrying:

- **The fresh record**, as the other trainer requests carry it (D110's ledgers, the running plan and its
  week, the goal, About me (D90), recovery and food summaries as D84 is amended).
- **The conversation of the last 14 days, word for word**, oldest first: the owner's messages, the
  trainer's replies, and the feedback and letters of D146 in their place — capped at the newest
  24,000 characters (a chosen size, not a measured limit); a message cut by the cap is left out whole.
- From release 2, **the sheet** (D149).

The instructions tell the model: it is the same coach that writes the notes, plans and letters; answer as
that coach, in a few sentences unless asked for more; use the record's numbers, never invent one; it may
disagree and say why (D129); it cannot change the plan in these releases, so it tells the owner what to
change rather than claiming it has; never "nobody asked for".

## D148 — What is stored, and leaves the phone

- Room version 13 adds `trainer_messages`: id, atMillis, fromOwner (boolean), text, model (null for the
  owner's), answered (owner's message: whether a reply arrived). Feedback and letters are not copied
  (D146).
- It goes into the backup; formats up to the current one still read (no messages).
- "What leaves the phone" names the conversation: each message is sent with the last 14 days of
  conversation and the record above.

## D149 — What I know about you

- A short sheet of **facts**, each with a group, its text, and the day it became true. Groups: **Body**
  (aches, injuries, illness), **How you like to train**, **Goals in your words**, **Life** (what affects
  training: travel, a busy month). A fact the owner said is marked "you said"; one the trainer inferred is
  marked "I noticed" (D4: where every stored statement comes from).
- **No longer true**: a fact dropped at a rewrite is shown struck through for 28 days with the day it was
  dropped, then goes.
- The sheet is capped at **40 facts** and **4,000 characters** (chosen sizes). A rewrite over the cap
  keeps what it judges most useful; the cap is enforced on the phone, never trusted to the model.
- Its page, **What I know about you**, opened from a row on the Trainer page, shows the groups, "Last
  rewritten" and **Full conversation ›**. Each fact has **Edit** (change the text) and **Remove**.
- **About me (D90) stays** as the owner's own text, which the trainer never writes. The sheet is the
  trainer's; About me is his.

## D150 — The weekly rewrite

- Each Sunday, after the letter is written, one more request rewrites the sheet from: the current sheet,
  the conversation since the last successful rewrite (all of it, not capped to 14 days), and the
  feedback and letter of that span. The answer is a whole new sheet; facts are updated — added, changed,
  dropped — never summarised from the previous summary.
- If the rewrite fails, the sheet stays as it was and the next Sunday's rewrite covers the longer span.
- **The owner's edits win**: a fact he edited or removed is sent to the rewrite as such, and the
  instructions say to keep his version unless the conversation since says otherwise.
- Every version of the sheet is kept (one row per rewrite or edit), so a bad rewrite can be undone:
  the page offers **Go back to the previous version** for the newest rewrite.

## D151 — Who reads the sheet

Every trainer request — a reply in the conversation, session feedback, the plan, the week note,
evaluation, goals, and the letter — carries the newest sheet. Room version 14 adds `trainer_sheets` (id, atMillis,
facts as JSON, madeBy: rewrite or owner, model); it goes into the backup.

## Not built here

- The trainer changing anything (release 3).
- Check-ins and notifications from the trainer.
- Searching old conversation; the sheet is the only memory beyond 14 days.
- Hebrew; voice; photographs.
