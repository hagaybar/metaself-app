# One coach, one box — D157 to D165

> Written 2026-11-02 and agreed the same day in conversation with the owner. A revision of the Trainer: amends
> D85, D114, D145, D146 and D147, and D84 where said. **Every example here is invented.** Release 2's amendments are
> under "Release 2 as built".

## Why

The Trainer does a lot, and all of it is behind its own button, row or page. The aim is one point of entry with the
power behind it: a coach screen with one box, and a coach that opens the owner's file for whatever he asks. The
screen comes first; abilities are then added by priority, connected where they exist and built where they do not.
The old Trainer page may be changed radically, but it stays one tap away until the new screen answers a list of real
sentences the owner would say on an ordinary day.

Priority of the coach's abilities: (1) talking, building, protecting; (2) noticing and speaking first, remembering;
(3) recognising, holding to account; (4) finding what works, teaching. Coaching during a session is left out. Logging
food from the coach is left out.

## Releases

1. **The coach screen and separate conversations** (D157–D160). No new ability; the AI reads what it read.
2. **The file** (D161–D163): the coach reads the record on demand; measured habits; the "Read:" line.
3. **Acting** (D164): the coach names an action, the phone runs the job, a card asks for Confirm.
4. **Showing** (D165): replies may show the app's own charts.

Protecting is in every release (D163, D164).

## D157 — The coach screen

- **Trainer**, wherever it is opened from, is now the coach screen, top to bottom: the coach's note on this week
  (D116), when there is one, with Reply ›; the box; up to three suggestions; the three latest conversations with
  All conversations ›; Everything else ›; the line saying what leaves the phone.
- **Everything else ›** opens the old Trainer page, otherwise unchanged. Its Talk to your trainer goes back to the
  coach screen.
- The screen asks the AI for nothing by itself (D84). If the AI or the internet is down, everything on it but Send
  still works.

## D158 — Suggestions

Worked out on the phone, never by the AI, in this order, at most three:

1. A session waiting for words (D85): opens its review.
2. A shorter session waiting for "count it for this?" (D105): opens Everything else, where the question is.
3. The next thing to do (D114, D124), with the label and destination it has on the old page.
4. "How is my week going?", which fills the box and sends nothing. It shows what the box is for.

## D159 — Separate conversations, one memory

Amends D145's "one continuous thread".

- Each subject has its own conversation. **Writing in the box on the coach screen always starts a new one.**
  Writing inside a conversation continues it.
- A session's feedback (D87) opens its own conversation, as does a letter (D101, D140): replying discusses exactly that
  item. Both are still read from where they live and never copied (D146). Reply › on the coach's note opens the
  note's conversation; the note is copied in only when the first message there is sent, because the note itself is
  replaced (D116).
- A conversation's name is made on the phone: the first words of the owner's first message; "After your run · 60 min"
  (invented); the letter's week. Conversations are listed newest activity first.
- A conversation with no words in it yet, apart from a feedback or letter, shows that item alone. **Delete this
  conversation** removes its messages and replies, and leaves feedback and letters where they are. A fact already on
  the sheet (D149) stays there, where the owner can remove it. Deleting one of his messages (D145) stays.
- **One memory.** What a reply reads changes only in this: the conversation being written in is sent first and whole,
  whatever its age, marked `this_conversation`; the rest of the last 14 days follows, all inside D147's cap. The
  instructions say the others are other subjects it remembers and may draw on.

**Storage.** Room version 17 adds `conversation` (text) to `trainer_messages`: `talk:<millis it began>`,
`session:<workout id>`, `letter:<Monday>`, `note:<millis the note was written>`, or for messages from before this
release `day:<local date>`. Each older message goes in its local day's conversation, and a reply goes wherever the
message it answers went. Old messages are not sorted by subject: that would cost AI requests to guess. There is no
table of conversations: one exists while something is in it. Backup format 13 carries the field; a file without it is
grouped by day in the same way.

## D160 — All conversations

**All conversations ›** opens every conversation in the same rows as the coach screen: its name, the start of its
newest words, and when. A message not answered says so. The sheet's Full conversation › opens this list.

## D166 — A clean coach screen

Amends D157, D158 and D160, after seeing D157 on the phone: seven things competed with the box, and the largest was the
paragraph on what is sent. The chat is the focus.

- **The box is at the bottom**, where it is in a conversation, and stays there; the screen's other content sits just above it.
- **What leaves the phone is one line** under the box, "Sent to OpenAI with your key · What's sent ›", and the whole text is
  one tap away. The same line replaces the paragraph at the head of a conversation. The other AI paths keep their text.
- **Up to three quick links** show above the box while it is empty: what needs the owner comes first (D158's order), then
  "See your weekly letter", when there is one, which opens the newest letter's page, then "How am I doing this week?", which
  still fills the box and sends nothing. With a letter one thing that needs him is shown, without one two.
- **All conversations and Everything else are in the title bar's menu.** The screen lists no conversations; All
  conversations keeps its rows with their newest words. A session's feedback is reached there.
- The coach's note (D116), when there is one, stays on the screen above the links.

## Later releases, as decided

### D161 — The memory model and the file

Four layers: the **record** (the truth, which memory never changes); **measured habits** (D162); the coach's **sheet**
(D149, rewritten weekly); **recent talk** (the last 14 days).

The coach reads like a coach opening a client's file before a meeting:

- A short **front page** is always sent: today, goal and rate, the running plan and this week, the coach's note, About
  me, **the whole current sheet** (it is capped, D149, and safety matters more than the few characters saved), age, sex
  and height, the calorie floor and today's target, how far back each part of the record goes, and the list of drawers.
  It is always sent because a coach cannot ask for what he does not know exists, and a missed injury is the costliest
  miss.
- The record is fixed, read-only **drawers** read by date range: sleep, recovery, individual meals, habits, workouts,
  weight, plan, past conversations (searchable, so memory no longer ends at 14 days). In a conversation the coach
  may open any of them, individual meals included. This amends D84 for the conversation.
- The AI names what it needs, the phone fetches it, the AI asks again or answers: **at most three AI requests per
  reply**, in the app's existing structured-reply form, so no special model feature is needed.
- A drawer's answer tells apart *not logged*, *logged as zero* and *before the record began*.
- If the file does not hold the answer the coach asks the owner, and the answer goes on the sheet. It may use general
  knowledge, but never invents a fact about him.
- Each trigger will bring a prepared folder (a letter already knows what it needs), and may ask for more. The
  conversation moves first.

### D162 — Measured habits

Calculated by the phone, never by the AI, over the last 4 weeks against the 4 before (a chosen span); a habit with too
few logged days is not stated. Each figure says how much of it rests on estimates (D4). Parts of the day, never
"breakfast" (D51); a logging with no honest time (D33) is left out of timing. Logging coverage comes first, because a
half-logged day reads as under-eating and a gap as a fast.

Coverage; calories and macros against target; protein against body weight; first and last eating time, eating hours,
fasting hours and their regularity; late-day share; eating occasions a day; weekday against weekend; most frequent
foods and combinations, variety, and the share of calories from a few foods; training days against rest days, and
before and after a session; label against estimate.

Food quality (fibre, sugar, salt, vegetables, processed food) is not stored. At first the coach judges roughly from the
names of frequent foods; later the AI may label each food once with a food group, marked as an estimate.

### D163 — Protecting

- The coach has no action that changes the calorie target, a food record or a weight (D164).
- It is told the calorie floor and never advises below it. That is an instruction and can fail, so it is said so here
  rather than presented as a guarantee.
- The pain and warning-signs rule (D147) stays; under-eating and exhaustion join it.
- A few safety questions are asked once: medication, allergies, conditions. Everything else is asked when it matters.
- Each reply shows **Read:** and what it opened, so its grounds can be checked. The trainer test page (D106) gains the
  conversation, and shows what it read, so a risky sentence can be tried without touching the real one.

**Food as a reason.** What was eaten is said only from the food record read for that reply, never from an old letter or
note, because a half-logged week reads as under-eating. The model marks a reply that speaks of food, and the phone also
looks for food words in it. With no food read, the phone opens the last 14 days itself (a chosen size), once, and asks
again, within the same 3 requests. On the last request there is no again, and the Read line says "food not checked". These
are words and a mark: a sentence that has neither goes unchecked.

### D164 — Acting

**The owner has the last word.** Asked for a change the coach disagrees with, it says so and why, and still names the
change, so the card is how the owner overrules it. It holds back only when the message mentions pain, dizziness or chest
discomfort, and only from adding or increasing exercise; that is an instruction and can fail, since the phone cannot tell a
plan made harder from one made easier. Nothing about the floor or the calorie target changes: neither can be named.

**Amends D97.** A plan change may give this week one session more than are left, so that a session can be added. It
applies on the plan page too. The model is told to go above what is left only when asked.

A reply may name an action. The phone runs the existing job with its existing checks, and shows a card with the change
and **Confirm**; nothing changes until it is pressed. The plan, goals and the sheet may change this way, and nothing
else.

**As built.** A reply names at most one change, of these kinds and no others: change the rest of the plan, a new plan with
its goal, stop the plan, fill in the next week, plan the next block, add a noticed fact to the sheet, change or remove a fact.
The running plan is changed by these only; a plan's goal changes by a new plan. The weight goal and the calorie target are
not plan goals and are out of reach.

- A plan change or a new plan is prepared straight after the reply, under the same tap, by the existing job (one more AI
  request, counted, and stored as an offered version as the plan page does). The card shows the title, its reason and See the
  weeks. A failed or interrupted job leaves the card with Try again.
- Filling a week and the next block keep their answer at once by design (D121), so they run on Confirm, and the card says
  so. There is nothing to show beforehand.
- Confirm uses the job's own checks. A plan since stopped or replaced, a fact no longer on the sheet, or a job no longer
  offered ends the card as no longer applying, and changes nothing.
- A fact the owner confirms stays "I noticed" (D4); his own sentences still go in through remember (D161).
- Preparing does write two things that are not the change: the offered version, which nothing runs, and the job's week note
  (D116), which is the coach's words on the week and is kept even for an answer later discarded, as on the plan page.
- A name the phone cannot read leaves the card saying so, because the reply's words may promise it.
- The coach is told how its earlier proposals ended, so a declined one is not repeated unasked. This is an instruction and
  can fail. Its stand's moderated goal (D129) is not offered on the card.
- The workbench shows what a real conversation would propose and prepares nothing.

Storage: Room version 19 adds `proposal` (JSON text) to `trainer_messages`. Backup format 15 carries it; a file without it
restores with no cards.

### D165 — Showing

A reply may show the app's own charts, drawn from the record, not from the AI's figures. A tapped suggestion can show
its chart at once; a typed question waits for the AI, because the phone cannot understand words without it.

## Release 2 as built

Amendments to D161–D163, decided while building. Every figure below is a chosen size.

**Scope.** Only a conversation opens the file; letters, plans and feedback keep their fixed requests for now. A
conversation's request is its own type (`CoachRequest`), so `TrainerRequest` still has no field for a meal. The old
reply path that sent the whole fixed request with a conversation in it is gone, not left beside the new one.

**The loop.** The reply's shape is the app's strict structured form, not a model's tool-calling: `open`, `reply`,
`remember`. On the last of three looks the request says so, an `open` is ignored, and no words is a failure (the message
stays Not answered). A look with neither words nor a drawer is a failure at once. A look with words *and* a drawer opens:
it had not finished reading. Asking again for a drawer already open fetches nothing. Each look counts towards the daily
ceiling like any other call, so a ceiling reached part-way through is a failure and the message waits for Try again.

**Limits.** At most 4 drawers a look, 92 days an ask (the newest days kept, and it says so), and 60,000 characters of
drawers for one reply, past which the model is told to answer from what it has. Each drawer has its own character cap
and keeps its newest part. A bad drawer, bad dates or a failed read is said to the model in words; it never fails the
reply and never reaches the Read line.

**Drawer answers.** `days`, `food_days` and `meals` give each day a state, `not_logged`, `logged_as_zero` or `logged`;
days before the record began are one line, `before_record`. `sessions` and `weight` list what exists and count the days
without. Food is read against today's target, because the record holds no target by day. A `meals` row sends no
saved-meal name and no note: those are words the owner chose for other reasons. A repeated row that carries a confidence
repeats an estimate, and is sent as one.

**D162.** Spans are whole days ending yesterday: today half-logged would read as under-eating. Below 7 logged days a span
states only its coverage; a split (weekend, training days) needs 3 days and food around a session 3 sessions. Averages rest
on days with calories; a day logged as zero is in coverage only. **An eating day runs from the start of morning to the
end of the following night** (D51's parts), so eating past midnight ends the evening it continues rather than opening a
new day. A day holding a logging with no honest time (D33) is out of timing whole, since what it hides could be the
first or last thing eaten; its calories still count. A fast is measured only between two neighbouring such days. Weekend
means Saturday and Sunday, one named constant. Late-day share is the evening and night parts. Loggings within 30 minutes are one
occasion; around a session means within 3 hours.

**Remembering.** `remember` is added at once, as a version made by `CONVERSATION`, source SAID, after the reply is stored;
a failure to write it is logged by kind and the reply stands. That a fact was truly said and not inferred is an
instruction to the model: the phone cannot check it. The sheet gains an **eating** group. A fact is left out when it is a
repeat, is over a cap, or is one the owner removed. As with his own changes, a version made after a rewrite ends Go back
for that rewrite (D150). The weekly rewrite may still reword a said fact; only his edits are protected (D150).

**The Read line.** Stored on the reply without its "Read:", each drawer once with the days it really covered in all. A
reply that opened nothing shows *Read: the front page only*, so the line is never read as an omission. Replies from before
this release show none. Room 18 adds `trainer_messages.opened`; backup format 14 carries it, optional.

**D84 amended, for the conversation only.** Individual meals and food names may leave the phone there, on the owner's
message. The screen's line and the privacy page say so. Every other path's wording stands, Never meals included.

**The floor.** Being told the floor and never advising below it is an instruction and can fail; so can the warning-signs
rule and the safety questions. "Asked once" rests on the model reading the conversation; nothing counts it on the phone.

**The workbench.** Its conversation path runs the real loop on the real record, shows the reply, what it read and what
it would have remembered, and stores nothing. Unlike the other paths it keeps the reply's shape, because the loop needs
it; a loaded file replaces only the instructions.

## Not in release 1

The file, measured habits, the Read line, acting, showing, the daily note, the safety questions, and removing anything
from the old page. Until release 2 the coach's note is the weekly one, and the AI reads what it reads today.
