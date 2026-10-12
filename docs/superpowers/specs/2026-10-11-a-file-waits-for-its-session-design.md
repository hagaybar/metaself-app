# A workout file waits for its session; Recent problems holds problems — D138, D139

> Written 2026-10-11 and approved the same day. Amends D82 and D91 (a workout file fills in a session)
> and D8 (the problem log). **Every figure in an example is invented.**

## Why

A band's app hands its sessions to Health Connect on its own schedule, and the same app can export the
session's file at once. A file shared straight after a workout therefore often arrives before its
session, finds nothing to match, and is lost unless the owner adds it by hand. And because every import
outcome — successes included — is written to Recent problems, that list reads as a list of failures
when most of it is a record.

## D138 — A file that matches nothing waits for its session

1. **Fetch first.** Before a file is matched, the phone runs the same copy from Health Connect it runs
   on its own schedule (as far as it is allowed; a failed copy does not stop the import). The matching
   rule (D82) is unchanged.
2. **No match, nothing of its kind that day** — the screen says "The band's session for this file
   hasn't reached the phone yet. It will be filled in when it does." with **Add it as a workout now**
   (D91, unchanged) and **OK**. The file's figures (not the file) are kept as **waiting**.
3. **No match, sessions of its kind that day** (D91) — as now, "Is it one of these?", plus a third
   choice **None of these — wait for the band**, which keeps it waiting.
4. **Each time the copy from Health Connect brings sessions**, every waiting file is matched by D82's
   rule. A match fills the session (D82's fill: only what it lacks, source FILE) and the file stops
   waiting. Several matches: it keeps waiting (the owner chooses on Your movement).
5. **On Your movement**, while any file waits, a line above the day rows: "A file from Sat 10 Oct 09:05
   is waiting for the band's session" (one line per file, at most three, then "and N more"), each with
   **Add it as a workout** and **Drop it**. A file matched in the background shows once, the next time
   the page opens: "Filled in your walk of Sat 10 Oct from the file that was waiting."
6. **Waiting files are kept on the phone** (a small store), not in backups. A file waiting more than 14
   days is dropped and written to Recent problems: "a file from Sat 10 Oct 09:05 never found its
   session".

## D139 — Recent problems holds problems

- **Import outcomes that worked, or that wait, are no longer written to Recent problems**: filled,
  nothing new, added as a workout, waiting, filled later. Refused files, failed writes and files that
  waited out their 14 days still are.
- **A backup that fails for want of a connection** (the host cannot be resolved, or no network) is
  written as "drive: no connection at 06:58; the next backup tries again" — one line, not two — provided
  the next scheduled backup does carry what this one missed (to be confirmed in the code; if it does
  not, the line says what was missed).

## Not built here

Matching rules beyond D82's; carrying waiting files in backups.

## Tests

- D138: the copy runs before matching, and its failure does not stop the import; the two no-match
  screens and their buttons; a waiting file filled when a later copy brings its session, and only what
  the session lacked; several matches keep it waiting; the Your movement lines, the "and N more", Add
  and Drop; the once-only "Filled in" line; the 14-day drop and its log line.
- D139: each outcome's log presence; the connection-failure line, once per failed backup.
