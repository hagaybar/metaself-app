# The trainer remembers — release 2 (D149–D151) — plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (or subagent-driven-development). One
> Gradle build at a time, through `~/bin/gradlew-safe`; check `free -m` first. Steps are test first.

Spec: `docs/superpowers/specs/2026-10-12-talking-with-the-trainer-design.md`, D149–D151. Release 1 (D145–D148,
`docs/superpowers/plans/2026-10-12-talking-with-the-trainer.md`) is the starting point and its interpretations stand:
a reply names the message it answers (`replyTo`); a message is at most 2,000 characters; the reply's window counts
words only and keeps the newest contiguous run under 24,000 characters; one request at a time.

**Goal:** a sheet of what the trainer knows about the owner — facts in four groups, rewritten every Sunday from the
conversation, editable by him, every version kept — read by every trainer request and by the letter.

**Architecture:** one new table, `trainer_sheets` (Room 14), one row per version, behind a `SheetStore`. A pure
`Sheets` object owns the rules: the cap, what a rewrite's answer becomes (ids continued, facts dropped, the 28 days
of No longer true), an edit, a removal, going back, and the span the next rewrite reads. The rewrite is its own ask,
`SheetWriter` (like `LetterWriter`): a lean request — the sheet, About me and the conversation since the last
rewrite — never the activity record. `RewriteTheSheet` runs it from the Sunday letter's worker. Every other request
gains one field, `sheet`: `TrainerRequest.sheet` (filled in `AskTheTrainer.request`, which every trainer path and the
workbench already use) and `LetterRequest.sheet`.

**Tech stack:** Kotlin, Room, Compose/Material 3, Hilt, kotlinx.serialization; JUnit 5 + Truth for pure code,
JUnit 4 + Robolectric for Compose and Room (Room tests skip locally, run in CI).

## Interpretations (stated, because the spec leaves them open)

1. **A fact** is: an id (numbered on the phone, so a rewrite can say which fact it continues), its group (Body, How
   you like to train, Goals in your words, Life), its text, the day it became true (`sinceEpochDay`), its source
   (they said / I noticed), whether the owner edited it, and — once dropped — the day it was dropped and whether he
   removed it. Dropped facts live inside the version's JSON, carried from version to version until 28 days have
   passed (shown while `today − dropped < 28`; pruned when a version is made and filtered when shown).
2. **The cap (40 facts, 4,000 characters of fact text) is enforced on the phone after the model answers.** What is
   kept when over: the facts the owner edited (and the model kept) are considered first, then the rest, each group in
   the model's order; a fact is kept while the sheet stays within both caps, and a fact that would cross either is
   left out (later, shorter ones are still tried). So an owner-edited fact is never dropped before a model-written one.
   A fact left out by the cap is dropped like any other: it shows under No longer true. An exact repeat (same text,
   ignoring case) is left out.
3. **A rewrite's answer** is the whole new sheet: each fact with `id` (the fact it continues, or null for a new one),
   group, text, `since` (YYYY-MM-DD) and source. On the phone: an id that names a current fact continues it (once);
   any other id is a new fact with a fresh id. `since` after today, or unreadable, becomes the continued fact's own
   `since`, else today. A continued fact keeps "edited" only if its text is unchanged. Current facts not continued are
   dropped today. Text is trimmed; a blank fact is left out.
4. **"You said" for an owner edit.** An edited fact becomes "you said" — the owner wrote it — and is marked edited.
   An edit is at most 300 characters (a chosen size) and refused if the sheet would pass 4,000 characters.
5. **Removing** moves the fact to No longer true, marked removed by him, as a new version. The rewrite is sent every
   owner-removed fact still within its 28 days, as removed, and told not to bring one back unless the conversation
   since says otherwise. Rewrite-dropped facts are not sent.
6. **Going back** makes a new version (made by the owner) holding the facts of the version before the newest rewrite,
   with `restoredFrom` = that version's id — one column beyond the spec's list, as release 1 added `replyTo`. It is
   offered only while the newest version *is* a rewrite that has a version before it (once the owner edits after a
   rewrite, going back is no longer offered; nothing he wrote is undone silently). A rewrite undone this way does not
   count as the last successful rewrite, so the next rewrite reads its span again.
7. **The rewrite's span**: thread items (his messages, the trainer's replies, feedback, letters — D146's thread) after
   the last successful rewrite not undone (all of them before the first), **capped at the newest 48,000 characters**
   (twice the reply's window, a chosen size; the same contiguous rule as D147). Items older than the cut are not
   read by that rewrite and are not read later either — the bound keeps one request sane after many failed Sundays.
8. **When it runs**: in the Sunday letter's worker, after the letter's run ends without a retry (written, quiet,
   already written, or given up — the conversation is remembered even if the letter failed). Skipped, with no
   request, when a rewrite was already made on or after that Sunday, or when the span holds no message from the
   owner. A failure logs its kind only (D8) and leaves the sheet; there is no retry — next Sunday reads the longer
   span. With the weekly letter turned off the worker does not run, so neither does the rewrite (the page says it
   rides on the letter).
9. **The rewrite request** carries today, the current sheet (with ids, edited flags) and owner-removed facts, About
   me (so the sheet does not repeat it), the span start, and the span as conversation lines — **not** the activity
   record. Its reply is `{"facts": [...]}` under a strict schema; it asks for no week_note.
10. **What every other request gets**: `sheet`, the current facts as `{group, text, since, source}`, or null when
    no version exists or it has no current facts. Ids, edit flags and dropped facts are not sent.
11. **Instructions for the rewrite**: update facts, never summarise the previous sheet; keep the owner's edits unless
    the conversation since says otherwise; "they said" only for what the owner actually said in his words; "you
    noticed" for an inference; no figure that is not in the conversation; group rules; most useful first within the
    caps; drop what is no longer true; one short sentence each; the shared `OWN_CHOICE` sentence (a fact about
    sessions beyond the plan uses the same words).
12. **Page**: the header line, the four eyebrow groups with a card per fact ("Since Mon 12 Oct · you said" /
    "· I noticed"; "· you changed this" is not added — an edited fact reads "you said"), Edit (a dialog with the
    text) and Remove (confirmed); NO LONGER TRUE with struck-through text and "6 Oct – 11 Oct · dropped Sun 11 Oct"
    (or "· you removed it Tue 13 Oct"); "Last rewritten Sun 11 Oct, 20:00 · Full conversation ›"; "Go back to the
    previous version" (confirmed) under it when offered. Empty state before any rewrite: what will appear and when.
    The row on the Trainer page sits after About me, which is unchanged.
13. **Room tests**: the store's Room test goes into `HealthRecordStoreTest` (CI), as release 1 did, so the locally
    skipped classes stay the ten CLAUDE.md lists.

## File map

- Create `domain/trainer/Sheet.kt` — `FactGroup`, `FactSource`, `SheetFact`, `SheetMadeBy`, `SheetVersion`,
  `RewrittenFact`, `SheetRewriteRequest`, `SheetWriter`, `Sheets` (pure rules).
- Create `data/ai/SheetPrompt.kt`, `data/ai/SheetResponse.kt` (also the facts' stored JSON codec), `data/ai/OpenAiSheetWriter.kt`.
- Modify `domain/trainer/TrainerRequest.kt` (`sheet`), `data/ai/TrainerPrompt.kt` (`sheet` in the user JSON, a
  paragraph in COMMON, `sheetJson`), `domain/letter/WeeklyLetter.kt` (`LetterRequest.sheet`), `data/ai/LetterPrompt.kt`.
- Modify `data/trainer/TrainerEntities.kt` (`TrainerSheetEntity`), `TrainerDao.kt`; create `data/trainer/SheetStore.kt`
  (interface, `RoomSheetStore`, `TrainerSheets` — the page's edits), `data/trainer/RewriteTheSheet.kt`.
- Create `data/day/SheetMigration.kt` (`MIGRATION_13_14`); modify `MetaSelfDatabase.kt` (14), `di/DataModule.kt`,
  `di/AiModule.kt`; `app/schemas/.../14.json`; `tools/check-migration-13-14.py`; `tools/README.md`.
- Modify `data/trainer/AskTheTrainer.kt` (reads the newest sheet), `data/letter/WriteWeeklyLetter.kt`,
  `data/letter/WeeklyLetterWorker.kt`.
- Modify `domain/backup/Backup.kt` (format 12, `trainer_sheets`), `data/backup/BackupRepository.kt`, `ui/settings/BackupWording.kt`.
- Create `ui/trainer/SheetWording.kt`, `ui/screen/trainer/SheetViewModel.kt`, `ui/screen/trainer/SheetScreen.kt`;
  modify `TrainerScreen.kt` (row), `TrainerWeekWording.kt`, `MetaSelfNavHost.kt` (route `trainer/sheet`).
- Modify `ui/trainer/TrainerWording.kt` (privacy), `ui/letter/LetterWording.kt` (privacyLetter), `privacy.html`,
  `ui/about/ChangeLog.kt`, `app/build.gradle.kts` (0.86.0, versionCode 143).
- Tests: `SheetsTest`, `SheetPromptTest`, `SheetResponseTest`, `OpenAiSheetWriterTest`, `TrainerPromptTest`,
  `LetterPromptTest`, `TrainerRequestTest`, `AskTheTrainerTest`, `TalkToTheTrainerTest`, `WriteWeeklyLetterTest`,
  `TrainerWorkbenchTest`, `RewriteTheSheetTest`, `TrainerSheetsTest`, `InMemorySheetStore`, `FakeSheetWriter`,
  `MigrationTest`, `HealthRecordStoreTest`, `BackupCodecTest`, `BackupRestoreOrderTest`, `BackupRoundTripTest`,
  `BackupWordingTest`, `SheetViewModelTest`, `SheetScreenRenderTest`, `TrainerScreenRenderTest`, `ChangeLogTest`.

## Tasks

### Task 1: the sheet's rules, pure (D149, D150)

`domain/trainer/Sheet.kt`:

```kotlin
enum class FactGroup { BODY, TRAINING, GOALS, LIFE }
enum class FactSource { SAID, NOTICED }
data class SheetFact(
    val id: Long, val group: FactGroup, val text: String, val sinceEpochDay: Long, val source: FactSource,
    val edited: Boolean = false, val droppedEpochDay: Long? = null, val removedByOwner: Boolean = false,
) { val current: Boolean get() = droppedEpochDay == null }
enum class SheetMadeBy { REWRITE, OWNER }
data class SheetVersion(val id: Long, val atMillis: Long, val facts: List<SheetFact>, val madeBy: SheetMadeBy,
    val model: String?, val restoredFrom: Long? = null) { val current; fun dropped(today: Long) }
data class RewrittenFact(val id: Long?, val group: FactGroup, val text: String, val sinceEpochDay: Long?, val source: FactSource)

object Sheets {
    const val MAX_FACTS = 40; const val MAX_CHARS = 4_000; const val MAX_FACT = 300
    const val DROPPED_DAYS = 28; const val REWRITE_CHARS = 48_000
    fun afterRewrite(previous: List<SheetFact>, answer: List<RewrittenFact>, today: Long): List<SheetFact>
    fun edit(previous: List<SheetFact>, id: Long, text: String, today: Long): List<SheetFact>   // throws when refused
    fun remove(previous: List<SheetFact>, id: Long, today: Long): List<SheetFact>
    fun canGoBack(versions: List<SheetVersion>): Boolean
    fun goBackTo(versions: List<SheetVersion>): SheetVersion?      // the version before the newest rewrite
    fun lastRewriteAt(versions: List<SheetVersion>): Long?         // newest rewrite not undone
    fun span(thread: List<ThreadItem>, afterMillis: Long?): List<ThreadItem>
}
```

Tests first (`SheetsTest`, JUnit 5, all invented): the cap keeps edited facts first and the model's order, skips a
fact that would cross 4,000 characters and tries the next, stops at 40; continued ids keep their id, `since` and
edited flag only when unchanged; an unknown or repeated id is a new fact with a fresh id; a future `since` falls back;
dropped facts get today and are pruned after 28 days; an edit over 300 characters or the cap is refused; removal
marks the fact removed by the owner; going back is offered only while the newest is a rewrite with a version before
it, and an undone rewrite does not count for the span; the span keeps items after the last rewrite, newest 48,000
characters, contiguous.

Commit: `feat(trainer): the sheet's facts and its rules, pure (D149, D150)`.

### Task 2: the rewrite's request, instructions and answer (D150)

`SheetRewriteRequest(today, facts: List<SheetFact> /* current + owner-removed within 28 days */, aboutMe, sinceMillis?,
conversation: List<ConversationLine>)`; `fun interface SheetWriter { suspend fun rewrite(request): TrainerReply<List<RewrittenFact>> }`.

- `SheetPrompt.INSTRUCTIONS` (interpretation 11), `body(model, request, profile)` with schema
  `{"facts":[{"id": integer|null, "group": "body"|"training"|"goals"|"life", "text", "since", "source": "they_said"|"you_noticed"}]}`;
  user JSON `{"today","since","sheet":{"facts":[{"id","group","text","since","source","edited_by_them"}],"removed_by_them":[{"text","group","removed"}]},"about_me","conversation":[TrainerPrompt's line shape]}`.
  `TrainerPrompt.lineJson` becomes internal for this.
- `SheetResponse.parse(body, model)` (unknown group/source or a missing field: unreadable; an empty list is a sheet
  with no facts); `SheetResponse.encode/read` for the stored facts JSON (`id, group, text, since, source, edited,
  dropped, removed`).
- `OpenAiSheetWriter` over `OpenAiCall`, failures logged by kind as the letter's are; bound in `AiModule`.

Tests first: `SheetPromptTest` (schema; sends exactly these keys; the rules in the instructions; OWN_CHOICE),
`SheetResponseTest` (a sheet reads, a bad group is unreadable, the codec round-trips), `OpenAiSheetWriterTest` (one
call, the facts and the model; a failure).

Commit: `feat(trainer): the weekly rewrite's request and its answer (D150)`.

### Task 3: every request carries the sheet (D151)

- `TrainerRequest.sheet: List<SheetFact>?` (current facts, null when none); `TrainerRequest.of(..., sheet)`.
  `TrainerRequestTest` field list gains `sheet`.
- `TrainerPrompt`: `put("sheet", sheetJson(request.sheet))` for every path (`[{group, text, since, source}]` or null);
  a COMMON paragraph: what the sheet is, that it is weighed with about_me and the numbers win, and that a "they_said"
  fact is theirs and "you_noticed" yours.
- `LetterRequest.sheet`, `LetterPrompt` sends it and says what it is.
- `AskTheTrainer` takes `sheets: SheetStore` and `request()` reads `sheets.newest()`; `WriteWeeklyLetter` the same.

Tests first: `TrainerPromptTest` — each of the nine bodies (plan, feedback, evaluate, adjust, fill, next block, goals,
talk, workbench) sends the sheet, null when none; `LetterPromptTest` (key list, the sheet); `AskTheTrainerTest` — every
path (suggest, feedback, evaluate, plan ahead, adjust, fill, next block, goals) asks with the newest sheet;
`TalkToTheTrainerTest`, `TrainerWorkbenchTest`, `WriteWeeklyLetterTest` — the same.

Commit: `feat(trainer): every trainer request and the letter carry the sheet (D151)`.

### Task 4: the table, its migration, its store (D150, D151)

- `TrainerSheetEntity` (`trainer_sheets`: id autoincrement, atMillis, facts, madeBy, model?, restoredFrom?).
  `TrainerDao`: `insertSheet`, `observeSheets` (by id), `allSheets`, `deleteSheets`, `insertSheets`.
- Database 14; build to export `14.json`; `MIGRATION_13_14` verbatim; `DataModule`; `tools/check-migration-13-14.py`.
- `SheetStore` (`observe(): Flow<List<SheetVersion>>`, `newest()`, `add(...)`), `RoomSheetStore` (an unreadable row
  is left out), `InMemorySheetStore`.
- Tests: `MigrationTest` 13→14 and `HealthRecordStoreTest` (store round trip) in CI.

Commit: `feat(trainer): every version of the sheet in a table, Room 14 (D150, D151)`.

### Task 5: the weekly rewrite and the owner's changes (D149, D150)

- `RewriteTheSheet(writer, sheets, talk: TalkToTheTrainer, aboutMe, now)`: `suspend fun rewrite(sunday: Long): Outcome`
  — `Skipped(reason)`, `Written(version)`, `Failed(failure)` (interpretation 8).
- `WeeklyLetterWorker`: after a run that is not a retry, `RewriteTheSheet` guarded; a failure logged by kind.
- `TrainerSheets(sheets, today, now)`: `observe`, `edit(id, text)`, `remove(id)`, `goBack()`.

Tests first: `RewriteTheSheetTest` (span read since the last rewrite, the request's facts and removed facts, a new
version made by rewrite with the model, failure leaves the sheet, skips), `TrainerSheetsTest`.

Commit: `feat(trainer): the sheet is rewritten after the Sunday letter, and the owner can change it (D149, D150)`.

### Task 6: the backup carries the sheet, format 12 (D150)

`Backup.trainerSheets` (`trainer_sheets`: id, at, facts, made_by, model, restored_from), `CURRENT_VERSION = 12` with
its KDoc; export, prepare (one per id), restore inside the transaction, `RestoreResult.trainerSheets`; `BackupWording`
names "N versions of what the trainer knows" when either side has any. Tests: `BackupCodecTest` (a version 11 file
reads with none), `BackupRestoreOrderTest`, `BackupRoundTripTest` (CI), `BackupWordingTest`.

Commit: `feat(backup): the sheet's versions ride in the backup, format 12 (D150)`.

### Task 7: the page, What I know about you (D149, D150)

`SheetWording`, `SheetViewModel`, `SheetScreen` (interpretation 12), the Trainer page row "What I know about you" /
"Kept from our talks · updated Sundays", route `trainer/sheet`, Full conversation › to `trainer/talk`.

Tests: `SheetViewModelTest` (edit, remove, go back, refusals said), `SheetScreenRenderTest` (groups, lines, dropped,
footer, empty state, go back offered), `TrainerScreenRenderTest` (the row).

Commit: `feat(trainer): What I know about you, the sheet's page (D149, D150)`.

### Task 8: what leaves the phone, 0.86.0

`TrainerWording.privacy` and `LetterWording.privacyLetter` name the sheet; `privacy.html` (the sheet in every request
and the letter; the weekly rewrite, without a tap, after the letter: the sheet, the note, the conversation since the
last rewrite; stored and backed up); ChangeLog 0.86.0; versionCode 143, versionName 0.86.0. Verify:
`:app:testDebugUnitTest` (only the ten Room classes skip), `:app:lintDebug`, `~/bin/ms-release`.

Commit: `docs: what leaves the phone names the sheet (D149–D151)` and `0.86.0: the trainer remembers (D149–D151)`.

## Not built

Release 3 (the trainer changing anything); check-ins; search; a rewrite when the weekly letter is turned off; a
Rewrite now button; going back past one rewrite or after an edit; Hebrew; voice, photographs, attachments.
