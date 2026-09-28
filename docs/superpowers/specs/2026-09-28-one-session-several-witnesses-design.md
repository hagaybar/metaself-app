# One session, several witnesses — D92

> Written 2026-09-28 and approved the same day. The same session can reach the record more than once:
> as a session from the band's app, a walk another app detected by itself, a workout added from a
> file, or one the owner typed. Each copy knows part of the truth — one has the heart rate, another
> the distance — and today each is shown and counted as a session of its own. This decision treats
> overlapping copies as **witnesses of one session**, keeps all of them, and builds the session from
> the best of each. **Every figure in an example is invented.**

## D92 — Overlapping sessions are one session

### Which copies are one session

- Two sessions are witnesses of one when their times overlap by **at least half of the shorter
  one**. A session wholly inside a longer one always qualifies.
- Candidates: every visible workout — synced from any app, added from a file, or typed. A hidden
  workout is never a witness (it was hidden on purpose).
- Grouping is transitive, as sleep's is (D68): if A overlaps B and B overlaps C, all three are one
  session. Accepted: such a chain is rare, and the split below undoes a wrong one.
- A pair the owner has split (below) is never grouped again, whatever their overlap.
- Worked on the phone from the stored rows, every time the record is read. Nothing is merged in
  storage; every witness stays as it was stored.

### Which witness leads

The **lead** gives the session its identity: its id (so a review, a plan match and a file fill attach
to it), its start and its end. The lead is, in order: a synced session carrying a heart-rate average;
another synced session; a session added from a file; a typed one. Ties go to the longer, then the
lower id.

### Each figure from the witness that knows it best

Every figure keeps its source (D4, D69), so a combined figure says which witness it came from.

| Figure | Taken from, first available wins |
|---|---|
| Kind | The lead's, unless it is "other"; then the first witness with a specific kind |
| Distance | Typed by the owner; a workout file; a witness whose own app recorded distance during it (D81); Health Connect's total over the session |
| Steps | A workout file; any witness's |
| Heart rate (average, highest, zones) | The lead's; else the first witness that has one |
| Calories | Band; file; typed; an estimate (labelled as one); none |
| Felt, words, plan | The owner's review. The lead's review; if only another witness has one, that one |

When two witnesses' distances differ by more than **15 %**, the session shows both: "3.4 km (file) ·
another app said 3.1 km" (invented figures).

### What the owner sees

- Movement shows one line per session, with "also recorded by 2 more" under it when it has other
  witnesses, and each figure's source as today.
- Everything that counts sessions counts combined sessions: Movement's headline and week figures
  (D74), the trainer's sessions and monthly lines (D84, D89), and the day's reading of a typed
  workout (D77).
- **These are two sessions**, on an open combined session, splits the lead from each other witness
  (one split per pair) and they are shown separately from then on. The split is stored, survives the
  next sync, and goes into the backup.
- Right after a split, **Undo** is offered and removes exactly the split just made.
- A session that was split from one it still overlaps shows **Put back together**, which removes that
  split and shows them as one session again.
- **Hide** on a combined session hides every synced witness in it; a typed witness is deleted as
  today, on its own line after a split.

### Storage

Room version 9 adds `session_splits` (the two workout ids, the lower first, unique as a pair). The
backup format becomes **7** and carries the splits, renumbered with the workouts on restore; a split
whose workout is gone is dropped. Formats 1–6 still read, with no splits.

## Not built here

- Steps counted from step readings inside the session's time (only a file's steps are per-session).
- Combining sleep with sessions, or a nap with a walk.
- Any merge of stored rows: the combination is always worked out, never written.
