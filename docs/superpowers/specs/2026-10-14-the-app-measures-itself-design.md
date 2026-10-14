# The app measures itself — D152 (temporary) and D153

> Written 2026-10-14 and approved the same day. **Every figure in an example is invented.**

## Why

0.86.1 fixed one query that slowed down as the band's record grew (full scans of `health_readings` by
`recordId`, inside the copy's write transactions, which `@Transaction` food reads then waited behind).
Others may exist that have not hurt yet. Two ways to find them: measure on the owner's phone (D152), and
measure here against a made-up store of a year's size (D153).

## D152 — Measuring on the phone (temporary)

**Off by default.** Settings → Recent problems gets a switch, **Measure the app for 3 days**, which turns
itself off 72 hours after it was turned on. Turned off, the measuring costs one check of a flag per event.

**What is recorded — timings and counts, never content:**

- Every SQL statement the app runs: its text **with placeholders only** (bound arguments are never
  recorded), how long it ran, and the thread. Every transaction: how long it waited to begin and how long
  it was held, and which statements ran inside it.
- Each Health Connect copy pass: per kind and page, records read, records stored, ids deleted, time
  reading from Health Connect, time holding the database.
- Time to first data on screen: the Add something lists (foods, meals), the day, the Trainer page; and,
  for every Log, the time from the tap to the row being written.
- App open: from process start to the first frame of the day.

No food name, weight, heart rate, message or any other value from the record is written. Statement text
is the app's own SQL, which names tables and columns only.

**How it stays out of the way:**

- It **never touches the app's database** while recording. Events go into memory and are written to a
  separate file in the app's private storage, in batches (every few seconds or few hundred events), on
  one background thread of its own — never on the database's executors, never inside a transaction.
- The file is capped at 5 MB; the oldest lines go first.
- **Not in the backup**, never sent anywhere by the app.

**Download.** A **Download diagnostics** button, beside the switch, saves one file where the owner
chooses (Downloads by default). Only then is the database read: row counts per table, and the database
file's size. The file's header names the app version, the phone model, the Android version and the hours
measured.

**Temporary.** It is removed in a later version, once its results are read and acted on. The removal is
part of this decision.

## D153 — Measuring here

A script in `tools/` builds a store from the exported schema with invented data the size of a year of a
band's record (sizes chosen and stated in the script, never taken from the owner's), runs every DAO query
on a hot path — app open, the copy, the day, Add something, the Trainer page, Your movement, the letter —
with `EXPLAIN QUERY PLAN` and a timing, and reports the slow ones. A query that scans a table that grows
with the record is fixed (an index, or the query) in this release, each fix with its plan before and after.

## Not here

- Restructuring the copy's transactions; that is decided from D152's results.
- Any measurement that leaves the phone without the owner's hand.
