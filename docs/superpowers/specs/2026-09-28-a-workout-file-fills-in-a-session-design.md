# A workout file fills in a session — D82

> Written 2026-09-28 and approved the same day. Health Connect may not carry a workout's distance
> (D81), but the band's app can export a session as a TCX file through
> Android's share list. The file it writes holds only the session's totals — one lap with total time,
> distance, calories, average heart rate and steps, and a start time — not second-by-second points.
> **Every figure in an example is invented.**

## D82 — A TCX file adds what Health Connect did not carry, never replacing a measurement

**Two ways in:**
1. **MetaSelf appears in Android's share list** for workout files (TCX; accept the MIME types a TCX
   share arrives as — the TCX types, `application/xml`, `text/xml`, `application/octet-stream` with a
   `.tcx` name, and `text/plain` — checked by content, not trusted by type). A share with no attached
   file but text instead (`EXTRA_TEXT`) is read the same way, capped at the same size.
2. **"Import a workout file"** on the Movement screen opens the system file picker (for a file already
   saved, e.g. in Downloads).

**Reading the file** (pure, tested): the TCX `Activity` `Id` (start), the laps' summed
`TotalTimeSeconds`, `DistanceMeters`, `Calories`, `HeartRateBpm` (average; the band's app writes a
plain number inside it), and `Steps` (a non-standard element the band's app adds). Trackpoints, when a
file has them, are ignored in this version. Anything unreadable is refused whole with a plain reason.

**The start time's zone is not trusted.** The band's app writes the local wall-clock time with a `Z`
(UTC) marker. The matcher therefore compares the file's time of day as written against the stored
session's local start, and also tries it as true UTC; whichever lies within the window counts.

**Matching** a stored synced workout: same local day, start within ±10 minutes, duration within ±10 %
(both choices, stated as such in code). Exactly one match → fill it in. None → offer **"Add it as a
workout"** (a TYPED workout filled from the file: kind from the file's sport if given, else Other;
energy TYPED from the file's calories, labelled "from the file"). More than one → list them to choose.

**What is filled, and how it is labelled (D4):**
- **distance** — set when the session has none; if it has one, kept, and the file's is shown beside it
  only in the confirmation line. Stored with its source `FILE`.
- **steps in the session** — stored (new column), source `FILE`.
- **calories** — only if the session has none (source `FILE`); a band figure is never replaced.
- average heart rate from the file is **not** stored: the session's heart rate is worked out from the
  readings (D70), and two averages would disagree.
- The open day in Movement shows "3.25 km (from file)"; pace for runs follows (D78).

**Confirmation:** one line, e.g. "Added 3.25 km and 4,000 steps to Walking, Thu 3 Sep 10:00." or
"This session already had 3.2 km; nothing changed." or the refusal reason. Importing the same file
twice changes nothing the second time.

**Schema v7** (hand-written migration 6 → 7, Python check, CI MigrationTest): `workouts` gains
`distanceSource TEXT` (null = Health Connect's total, as before; `FILE`; `TYPED`) and `steps INTEGER`,
`stepsSource TEXT`. Backup format gains the three fields (defaults keep older files readable; version
bump per the backup's own rule). Nothing else changes.

**Not built:** reading images; trackpoint detail (per-km pace, cadence, stride); GPX/FIT formats.

## Amended during build

- **Every import outcome is also written to the problem log**, kind `import`, as one plain line in
  the words of what happened — a success as a success ("added distance and steps to Walking on Thu 3
  Sep"): which figures, never their values; a stored workout by name and day; a refusal with its
  reason. The line on the Movement screen still stays until Done, across a trip to the file picker.
- **Copying the health record runs only in the foreground.** Health Connect refuses reads from an app
  in the background, and those refusals were logged as problems and, for a workout's aggregate,
  stored as a missing figure. The copy now does nothing in the background and is cancelled when the
  app leaves it, keeping the bookmarks already saved; a refusal for the background stops the pass
  without a log line and saves nothing for that read. The next foreground copy after a pass stopped
  that way asks again for the missing figures of the last 30 days' workouts (within the per-open cap)
  and re-totals the last three days (a choice), then owes nothing more. Kept as a marker row in
  `health_sync`; no schema change.
