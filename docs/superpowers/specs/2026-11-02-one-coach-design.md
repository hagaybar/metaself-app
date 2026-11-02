# One coach, one box — D157 to D165

> Written 2026-11-02 and agreed the same day in conversation with the owner. A revision of the Trainer: amends
> D85, D114, D145, D146 and D147, and D84 where said. **Every example here is invented.**

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

## Later releases, as decided

### D161 — The memory model and the file

Four layers: the **record** (the truth, which memory never changes); **measured habits** (D162); the coach's **sheet**
(D149, rewritten weekly); **recent talk** (the last 14 days).

The coach reads like a coach opening a client's file before a meeting:

- A short **front page** is always sent: goal, this week's plan, facts on the sheet about the body (aches, injuries,
  illness), the calorie floor, and the list of drawers. It is always sent because a coach cannot ask for what he
  does not know exists, and a missed injury is the costliest miss.
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

### D164 — Acting

A reply may name an action. The phone runs the existing job with its existing checks, and shows a card with the change
and **Confirm**; nothing changes until it is pressed. The plan, goals and the sheet may change this way, and nothing
else.

### D165 — Showing

A reply may show the app's own charts, drawn from the record, not from the AI's figures. A tapped suggestion can show
its chart at once; a typed question waits for the AI, because the phone cannot understand words without it.

## Not in release 1

The file, measured habits, the Read line, acting, showing, the daily note, the safety questions, and removing anything
from the old page. Until release 2 the coach's note is the weekly one, and the AI reads what it reads today.
