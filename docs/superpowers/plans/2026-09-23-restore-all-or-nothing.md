# A restore is all or nothing — public issue #32

**Goal:** public issue #32 ("Restoring a backup is not all-or-nothing"). A restore either fully
happens or leaves the phone as it was, and the sentence shown when one fails says which.

**Spec:** no decision changes. D18 and step 12 ("a restore replaces rather than merges, and asks
first with the counts of what it will destroy") stand; the file format (`Backup.CURRENT_VERSION` 2)
and what a successful restore produces do not change. It extends the refused-save plan
(`2026-09-23-a-refused-save-says-so.md`, rule 2: "every action that writes in more than one step is
made one transaction first … where one cannot be made atomic, the sentence says it may have partly
happened").

**Before every Gradle command:** `export ANDROID_HOME=/home/ubuntu/android-sdk`; use
`~/bin/gradlew-safe`; redirect output to a file and check `$?`; stage by explicit path.

---

## What is there today

`BackupRepository.restore` deletes every meal and weight, then writes foods (`findOrCreate`), saved
meals (`create`, `put`), meals, items and weights one statement or one small transaction at a time,
then seven DataStore edits (profile, revision, revision-seen, arrival, milestones, AI model and
ceiling, reminder), then sets the alarm. Anything thrown after the deletes leaves a partial record.
Some throws are not even failures of storage: a food alias that normalises to nothing throws in
`BackupFoods.everyKeyOf`, uncaught, after the wipe.

**The trap.** `restoreFoods` and `restoreSavedMeals` wrap `findOrCreate` and `create` in
`runCatching`. Each opens its own `withTransaction`. Inside an outer transaction that is a nested
one, and a failure caught there still marks the whole transaction failed: SQLite rolls ALL of it
back at the end without throwing (`RoomSavedMealRepository.createThen`'s KDoc records the same
thing). Wrapping today's code in one `withTransaction` would therefore report a finished restore
that stored nothing. The catches must go, and whatever they were catching must be decided before
the transaction opens.

## The design

Four phases, in this order.

1. **Prepare — nothing written.** Every value is read and built from the file first: each file
   food as a domain `Food` (as now, `BackupFoods.toDomain`), dropped if its name normalises to
   nothing (what `findOrCreate`'s `FoodKeys` call threw on, and the `runCatching` swallowed); the
   key every surviving food will land under, aliases included; the foods derived for items the food
   block does not cover (`DerivedFoods.from`, pure); each saved meal, dropped if its name normalises
   to nothing (what `create` threw on); each meal's items (`toDomainOrNull`) and the `Meal` itself;
   the weights; the profile, revision, arrival, milestones, AI settings and reminder as domain
   values. A throw here changed nothing. What is dropped is exactly what is dropped today.
2. **Snapshot.** The whole preferences DataStore as it is now, read once (`store.data.first()`).
   The profile, revision, seen flag, arrival, milestones, AI settings and reminder all live in that
   one file (`DataModule.provideProfileDataStore`, shared by `DataStoreProfileRepository`,
   `DataStoreAiSettingsStore` and `DataStoreReminderStore`), so one snapshot covers every key the
   restore can touch — including "absent", which the per-key setters cannot write back
   (`markRevisionSeen` cannot un-see; nothing clears an arrival).
3. **One database transaction**, containing: the two deletes; the foods; the saved meals; the meals
   and items; the weights; and, **last, still inside the block**, the seven DataStore writes through
   the same repository calls as today. A throw from any of them — a database write or a DataStore
   write — leaves the block, and Room rolls back every database write. No `runCatching` inside;
   nested `withTransaction`s join the outer one.
4. **After the commit:** the alarm is set or cancelled for the restored reminder. It is a phone
   side-effect, not data, and only the committed reminder should be scheduled.

**On any throw out of phase 3** (including the COMMIT itself failing), the snapshot is written back
as one DataStore write (`edit { clear(); putAll(snapshot) }` — one atomic file replacement), under
`NonCancellable`. If that succeeds, restore throws `NothingRestored` (cause = the original failure,
its message and first frame carried over so Recent problems still names the real fault). If the
put-back itself throws, the original failure is rethrown with the second attached as suppressed, and
that is *not* `NothingRestored`.

**The sentence.** `SettingsViewModel.confirmRestore` says `NOTHING_CHANGED` for `NothingRestored`
and `MAYBE_PARTIAL` for anything else. So "nothing was changed" is shown only when restore itself
vouches for it: prepare failed before any write, or the transaction rolled back and the settings
were put back. An alarm that fails to be set after the commit, or a failed put-back, still says
"may have only partly happened", which is then true.

### What is still not atomic — the windows

- **The process dying between the DataStore writes and the COMMIT** (phone switched off, app
  killed, out of memory). SQLite undoes the uncommitted transaction on next open; the DataStore
  writes already on disk stay. The phone then holds the old meals and weights with the file's
  profile and settings. The window is the length of seven small DataStore writes plus the commit;
  it is as short as ordering can make it, and no screen sentence is involved because the process is
  gone. Closing it would need both stores in one — the settings moved into SQLite — which is a
  schema change and out of scope.
- **The put-back failing** (the same full disk that failed the restore, say). Covered by the
  sentence: it says "may have only partly happened".
- **Settings changed by something else during the restore** (the day's AI call count, the last-seen
  day) are overwritten by the snapshot only if the restore fails. They are counters, not records.

## Behaviour that changes besides the sentence

- A throw from `findOrCreate` or `create` that is not one of the name refusals above no longer skips
  that one food or meal and carries on: it rolls the whole restore back. Anything that throws there
  now is a storage failure, and carrying on past one is what made restores partial.
- A food alias no key can be made of now refuses the restore before anything is written, instead of
  throwing after the wipe. (The revision, arrival, reminder and milestones are built in the prepare
  phase too, but their constructors do not refuse anything today.)

## Seams

- `DatabaseTransaction` (`run(block)`): `RoomDatabaseTransaction` over `withTransaction`; tests with
  no database pass one that just runs the block.
- `SettingsSnapshot` (`take(): suspend () -> Unit`, returning the put-back): `DataStoreSettingsSnapshot`
  over the shared `DataStore<Preferences>`. Bound in `DataModule`.

## Steps (test first; one commit each)

1. This plan.
2. `DataStoreSettingsSnapshot` + JUnit 5 test against a real DataStore in a temp dir: values
   written after the snapshot, including new keys, are gone after the put-back; values that were
   there are back, including a seen flag that was false.
3. `BackupRepository` restructure + `DatabaseTransaction`, test first:
   - JUnit 5 with fakes (`BackupRestoreOrderTest`): the settings writes happen inside the
     transaction and after every database write; a settings write that throws → put-back taken,
     `NothingRestored`; a put-back that throws → not `NothingRestored`; a food alias no key can be
     made of → `NothingRestored` and nothing touched; alarm set only after the commit; a saved
     meal named `"!!!"` is never handed to `create`.
   - Robolectric in `BackupRoundTripTest` (CI only; skips here): with a real database, a restore
     whose DataStore write throws, and one whose database write throws midway (a saved-meal `put`
     after the deletes), leave meals, items, weights, foods and saved meals exactly as they were;
     a file with a saved meal and a food whose names normalise to nothing restores everything else
     (proves nothing inside the transaction swallows a throw and rolls it all back silently).
4. `SettingsViewModel.confirmRestore`: `NOTHING_CHANGED` for `NothingRestored`, `MAYBE_PARTIAL`
   otherwise; the existing view-model test flips, one added for a failed put-back.
5. `tools/check-restore-transaction.py`: the restore's own SQL (extracted from the DAOs) in one
   transaction on the latest exported schema with foreign keys on — a failure at each step rolls the
   record back to identical, and the successful run commits with every row attached.
6. Full suite, lint.
