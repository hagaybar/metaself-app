# What the band sends — D80

> Written 2026-09-27, approved the same day. Before the trainer is designed, it must be known which of
> the figures the band's own app shows actually reach this app through Health Connect: only what the
> writing app chooses to share arrives, and some of its figures (stress, training load) have no Health
> Connect record at all. The answer must be read off the phone, next to the band's app, without any of
> the record leaving it. **Every figure in an example is invented.**

## D80 — A page that says what has arrived, kind by kind

**Where:** Settings → Movement and health → a row **"What the band sends"** opening its own page.

**What it shows**, read from the stored health record (D65) — so it answers "what arrived", not "what
Health Connect holds right now" — over **the last 30 days** (a choice, matching the copying window):

1. **Each kind of reading** (the thirteen of D66, in their order): its name, then either
   - "412 readings · 30 days · from com.example.band" (count; how many distinct days have one; each
     writing app, shown by its app label where the phone knows it, else its package name), and
     "first 29 Aug · last today"; or
   - "nothing arrived" — and, when the kind is not allowed, "not allowed" instead.
2. **Workouts**: how many; by kind ("Walking 12 · Running 2"); by source (band / typed); and for the
   band's ones, how many carry each detail: distance, calories, heart rate (computed here), title —
   "distance 12 of 14 · calories 0 of 14 · heart rate (worked out here) 14 of 14".
3. **Daily summary**: for each figure of `health_days` (steps, distance, movement calories, total
   calories, resting heart rate, heart-rate variability, blood oxygen, breathing rate, sleep, workouts),
   on how many of the last 30 days it has a value — "steps 30 of 30 days".
4. A line: "Stress, training load and recovery time are not shared through Health Connect; they stay
   in the band's app. VO₂ max has a Health Connect record, but this app does not copy it." (Checked
   against the pinned client, connect-client 1.1.0: no record type for stress, training load,
   recovery or readiness; a `Vo2MaxRecord` exists.)
5. **Copy as text**: puts the page's counts on the clipboard (counts, dates and app names only — no
   readings), so they can be pasted into a conversation.

Nothing here writes; nothing here reads Health Connect; D8 (a failed read says so on the page).
