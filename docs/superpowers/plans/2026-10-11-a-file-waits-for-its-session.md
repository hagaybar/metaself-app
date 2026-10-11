# A workout file waits for its session; Recent problems holds problems — plan

Spec: `docs/superpowers/specs/2026-10-11-a-file-waits-for-its-session-design.md` (D138, D139). Amends D82, D91, D8.

## Tasks

1. **Waiting files store (D138.6).** `WaitingFileStore` port and `FileWaitingFileStore`: one small JSON file in the
   app's files directory, not the settings DataStore (which a restore snapshots and puts back whole) and not the
   database (so no schema change, and nothing reaches a backup). Holds each waiting file's parsed figures and when it
   started waiting, and the "filled in later" notes the Movement page shows once. A file that cannot be read is
   empty. JUnit 5 over a temporary directory.
2. **Matching waiting files (D138.4, D138.6).** `WaitingFileMatcher`: for each waiting file, dropped and logged after
   14 days ("a file from … never found its session"); otherwise D82's rule over the store — one match is filled (D82's
   fill) and stops waiting, leaving a "filled in" note when it added something; none or several keep it waiting. It
   also gives the Movement page its waiting lines, each with the sessions it matches when there are several.
   `SessionsArrived` port, called by `HealthRecordSync` after every copy pass that ran (a pass that brought no
   sessions matches nothing new, so running it after every pass is the same as running it after those that did).
3. **The importer (D138.1–3).** `ImportWorkoutFile` runs the copy before matching (`HealthRecordCopier.copyNow`;
   a failure is logged and the import goes on); no match and nothing of its kind that day keeps the file waiting and
   answers `ImportOutcome.Waiting`; `wait(file)` is "None of these — wait for the band"; a fill or an add of a file
   stops it waiting.
4. **The problem log (D139, first half).** `WorkoutFileWording.logLine` answers null for an outcome that is not a
   problem; the view model writes only Refused and Failed.
5. **The screen (D138.2, 3, 5).** The waiting outcome's line with "Add it as a workout now" and "OK"; "None of these —
   wait for the band" under D91's offer; above the day rows, one line per waiting file (at most three, then "and N
   more") with "Add it as a workout" and "Drop it" (and the sessions to choose from when several match); the once-only
   "Filled in your walk of …" line.
6. **Drive (D139, second half).** A Drive failure for want of a connection (host not resolved, connection refused or
   unreachable; Google's authorisation reporting a network error) is one line, "drive: no connection at HH:mm; the
   next backup tries again", and the month archive is not tried that run (its months stay out of date, so the next
   backup sends them). Checked in code: the daily Drive file is a whole export, so the next one carries everything;
   only that one day's dated file is never written.
7. **0.83.0**, change-log entry; tests and lint.

## Not built

Matching rules beyond D82's; carrying waiting files in backups (D138, as specified).
