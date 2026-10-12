# Talking with the trainer — release 1 (D145–D148) — plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (or subagent-driven-development). One
> Gradle build at a time, through `~/bin/gradlew-safe`; check `free -m` first.

Spec: `docs/superpowers/specs/2026-10-12-talking-with-the-trainer-design.md`. This plan builds **D145–D148 only**.
D149–D151 (the sheet) are release 2; nothing here blocks them: the sheet goes into every request through
`AskTheTrainer.request` (D151), which the conversation's reply already uses, and `trainer_sheets` is Room 14.

**Goal:** a conversation with the trainer on its own screen, Your trainer, whose replies read the fresh record and
the last 14 days of the conversation, with the session feedback and weekly letters shown in it.

**Architecture:** one new table, `trainer_messages` (Room 13), behind a `ConversationStore`. The thread is built on
the phone from three sources — the messages, the reviews' feedback, the letters — and never copies the last two
(D146). A pure `Conversation` object orders the thread and cuts the request's window (14 days, the newest 24,000
characters, an item cut by the cap left out whole). The reply is one more trainer path, `TALK`, with the conversation
inside its question, so `TrainerRequest`'s fields (D84's shape, pinned by `TrainerRequestTest`) do not change.
`TalkToTheTrainer` stores, asks and stores the reply; `TalkViewModel` and `TalkScreen` draw it.

**Tech stack:** Kotlin, Room, Compose/Material 3, Hilt, kotlinx.serialization; JUnit 5 + Truth for pure code,
JUnit 4 + Robolectric for Compose and Room (Room tests skip locally, run in CI).

## Interpretations (stated, because the spec leaves them open)

1. **`replyTo` column.** D148 lists id, atMillis, fromOwner, text, model, answered. A reply also stores `replyTo`
   (the owner message's id, null on his own), so "delete my message and the reply it got" is exact even when a
   failed message is tried again after a later one was answered. Nothing else reads it.
2. **One request at a time.** Send and Try again are not pressable while the trainer is writing.
3. **A message is at most 2,000 characters** (a chosen size, like About me's 1,000), so the message being answered
   always fits the 24,000-character window.
4. **The cap counts the words** of each item (a message's text, the feedback's headline and note, the letter's
   texts), not the JSON around them. A contiguous newest run is kept: the first item that does not fit ends the
   window, older ones are not tried.
5. **The question carries the message being answered (`reply_to`) as well as the window**, so Try again on an
   older message still says which one is answered.
6. **No week_note on the reply.** The chat reply is `{"reply": …}` only; a reply does not replace the coach's note.
7. **Trainer page buttons.** The row is Log a workout (outlined) and Talk to your trainer (filled), as the mockup
   shows. The plan action that used to be filled in that row (Plan the open walk, Plan my next session, Plan ahead,
   or the plan-page buttons) moves under the row as an outlined full-width button, so nothing becomes unreachable;
   the session action that already sat under it stays under it.
8. **Advice label (D4).** One standing line at the top of the thread says the trainer is an AI and its replies are
   advice, not measurements, with the privacy list under it; bubbles carry no per-message label.
9. **Deleting** is offered by tapping his own message (Delete), then a confirmation naming the reply that goes too.

## File map

- Create `app/src/main/java/com/metaself/app/domain/trainer/Conversation.kt` — `TrainerMessage`, `ThreadItem`,
  `ConversationLine`, `TalkPlan`, `Conversation` (thread order, window, the text of each item).
- Modify `domain/trainer/TrainerRequest.kt` — `TrainerQuestion.Talk`; `Trainer.talk`; KDoc ("no conversation is
  replayed" now excepts the Talk question).
- Modify `domain/trainer/TrainerPath.kt` — `TALK` (not on the workbench).
- Modify `data/ai/TrainerPrompt.kt` — `TALK` instructions, `OWN_CHOICE` shared with the letter, `talkBody`, the
  question's JSON, `TALK_SCHEMA`.
- Modify `data/ai/LetterPrompt.kt` — uses `TrainerPrompt.OWN_CHOICE` (the same words, one place).
- Modify `data/ai/TrainerResponse.kt` — `parseTalk`.
- Modify `data/ai/OpenAiTrainer.kt` — `talk`.
- Modify `data/trainer/TrainerEntities.kt` — `TrainerMessageEntity`.
- Modify `data/trainer/TrainerDao.kt` — messages' queries and the backup's three.
- Create `data/trainer/ConversationStore.kt` — interface + `RoomConversationStore`.
- Create `data/trainer/TalkToTheTrainer.kt` — the thread, say, answer, delete.
- Create `data/day/ConversationMigration.kt` — `MIGRATION_12_13`, verbatim from `13.json`.
- Modify `data/day/MetaSelfDatabase.kt` (version 13, entity), `di/DataModule.kt` (migration, store binding).
- Create `app/schemas/.../13.json` (exported by the build).
- Create `tools/check-migration-12-13.py`; README line.
- Modify `domain/backup/Backup.kt` (format 11, `trainer_messages`), `data/backup/BackupRepository.kt`,
  `ui/settings/BackupWording.kt`.
- Create `ui/trainer/TalkWording.kt`; modify `ui/trainer/TrainerWording.kt` (`privacyTalk`),
  `ui/trainer/WorkbenchWording.kt` (`TALK`'s name).
- Create `ui/screen/trainer/TalkViewModel.kt`, `ui/screen/trainer/TalkScreen.kt`.
- Modify `ui/screen/trainer/TrainerScreen.kt` (buttons, Reply ›), `ui/nav/MetaSelfNavHost.kt` (route).
- Modify `privacy.html`, `ui/about/ChangeLog.kt`, `app/build.gradle.kts` (0.85.0, versionCode 142).
- Tests: `ConversationTest`, `TrainerPromptTest`, `TrainerResponseTest`, `OpenAiTrainerTest`, `LetterPromptTest`
  (unchanged sentence), `TalkToTheTrainerTest`, `InMemoryConversationStore`, `FakeTrainer`, `MigrationTest`,
  `RoomConversationStoreTest` (Room, skips locally — added to CLAUDE.md's list), `BackupCodecTest`,
  `BackupRestoreOrderTest`, `BackupRoundTripTest`, `BackupWordingTest`, `TalkViewModelTest`,
  `TalkScreenRenderTest`, `TrainerScreenRenderTest`, `ChangeLogTest` (existing).

## Tasks

### Task 1: the thread and its window, pure (D146, D147)

`domain/trainer/Conversation.kt`:

```kotlin
data class TrainerMessage(
    val id: Long, val atMillis: Long, val fromOwner: Boolean, val text: String,
    val model: String?, val answered: Boolean, val replyTo: Long? = null,
)

sealed interface ThreadItem {
    val atMillis: Long
    data class Said(val message: TrainerMessage) : ThreadItem
    /** D146: a session's feedback, shown from its review; [kind]/[minutes] null when the session is gone. */
    data class AfterSession(val workoutId: Long, override val atMillis: Long, val kind: WorkoutKind?, val minutes: Int?, val feedback: Feedback) : ThreadItem
    /** D146: a weekly letter, shown from its row. */
    data class Letter(val weekMonday: Long, override val atMillis: Long, val texts: LetterTexts) : ThreadItem
}

enum class LineKind { MESSAGE, SESSION_FEEDBACK, LETTER }

data class ConversationLine(
    val at: LocalDateTime, val fromOwner: Boolean, val kind: LineKind, val text: String,
    val sessionKind: WorkoutKind? = null, val sessionMinutes: Int? = null, val letterWeek: Long? = null,
)

/** D147: the running weekly plan, its week (from 0) and its goal, as a reply is told them. */
data class TalkPlan(val ask: ProgrammeAsk, val startEpochDay: Long, val plan: WeeksPlan, val weekIndex: Int)

object Conversation {
    const val WINDOW_DAYS = 14
    const val MAX_CHARS = 24_000      // a chosen size, not a measured limit (D147)
    const val MAX_MESSAGE = 2_000     // a chosen size (interpretation 3)

    fun thread(messages: List<TrainerMessage>, feedback: List<ThreadItem.AfterSession>, letters: List<ThreadItem.Letter>): List<ThreadItem>
    fun text(item: ThreadItem): String
    fun window(thread: List<ThreadItem>, nowMillis: Long): List<ThreadItem>
    fun line(item: ThreadItem, zone: ZoneId): ConversationLine
    fun cut(text: String): String     // at most MAX_MESSAGE, never splitting a surrogate pair
}
```

Tests first (`ConversationTest`, JUnit 5; all invented):
- the thread is ordered by time, the three kinds interleaved; ties by message id;
- the window keeps only items newer than 14 days before now; oldest first;
- with more than 24,000 characters, the oldest go; an item that would cross the cap is left out whole and so is
  everything older (a short older item is not squeezed in);
- a letter's and a feedback's text are their words in order (Coach and SixParts; note and four parts);
- `cut` keeps 2,000 characters and never half an emoji.

Commit: `feat(trainer): the conversation's thread and its 14-day window, pure (D146, D147)`.

### Task 2: the Talk question, its instructions and its reply (D147)

- `TrainerQuestion.Talk(conversation: List<ConversationLine>, replyTo: ConversationLine, plan: TalkPlan?)`;
  `Trainer.talk(request): TrainerReply<String>`; `TrainerPath.TALK` (not in `WORKBENCH`).
- `TrainerPrompt`: `OWN_CHOICE` (the letter's sentence, moved here and used by `LetterPrompt`), `TALK` text:
  same coach; a few sentences unless asked for more; the record's numbers only, never invented; may disagree and
  say why (D129); cannot change the plan in these releases, says what to change and where, never claims it has;
  pain/dizziness/chest discomfort in reply_to first; `OWN_CHOICE`; reply with `reply`. Path instructions: COMMON,
  six weeks, ledger, week rules, `VOICE_NOTE`, `TALK` — no `WEEK_NOTE`.
- Question JSON: `{"kind":"talk","conversation":[{"at":"2026-10-12T08:30","from":"them"|"you","kind":"message"|
  "session_feedback"|"letter","session":{"kind","minutes"}|null,"week":"YYYY-MM-DD"|null,"text"}],"reply_to":{"at","text"},
  "plan":{"goal","weeks","sessions_a_week","starts","plan","this_week_number"}|null}`.
- `talkBody` with `TALK_SCHEMA = {"reply": string}`; `TrainerResponse.parseTalk` (a non-blank string, trimmed);
  `OpenAiTrainer.talk`.

Tests first: `TrainerPromptTest` — the talk body names its schema, carries the conversation oldest first with
from/kind/session/week, reply_to and the plan (or null); the instructions hold each coach rule and the banned
phrase sentence exactly as the letter's; `TALK` asks for no week_note (the "every path asks for week_note" test now
says every path but TALK, and why); `TrainerResponseTest` — a reply reads, a blank or missing one is unreadable;
`OpenAiTrainerTest` — one call, the reply and the model.

Commit: `feat(trainer): a reply in the conversation, its request and its instructions (D147)`.

### Task 3: the table, its migration, its store (D148)

- `TrainerMessageEntity` (`trainer_messages`: id autoincrement, atMillis, fromOwner, text, model?, answered,
  replyTo?), no index, no foreign key. `TrainerDao`: `insertMessage`, `observeMessages` (by atMillis, id),
  `message(id)`, `markAnswered(id)`, `deleteOwnMessage(id)` =
  `DELETE FROM trainer_messages WHERE (id = :id AND fromOwner = 1) OR replyTo = :id`, `allMessages`,
  `deleteMessages`, `insertMessages`.
- Database version 13; build once to export `13.json`; `MIGRATION_12_13` = its createSql verbatim; registered in
  `DataModule`; `tools/check-migration-12-13.py` (pattern of 11-12; proves the reply-and-message delete too) passes.
- `ConversationStore` (`observe`, `message`, `addOwn`, `addReply` — one transaction: the reply inserted, the
  message marked answered — `deleteOwn`), `RoomConversationStore`; Hilt binding. `InMemoryConversationStore` for tests.
- Tests: `MigrationTest` 12→13 (CI); `RoomConversationStoreTest` (CI): a reply marks its message answered; deleting
  his message deletes its reply and nothing else; a trainer message cannot be deleted.

Commit: `feat(trainer): a table for the conversation, Room 13 (D148)`.

### Task 4: talking — store, ask, store the reply (D145, D146, D147)

`TalkToTheTrainer @Inject constructor(ask: AskTheTrainer, trainer: Trainer, messages: ConversationStore,
store: TrainerStore, letters: LetterStore, now: Now)`:
- `observeThread(): Flow<List<ThreadItem>>` — messages + reviews with feedback (labelled from the reviewed
  workouts) + letters, via `Conversation.thread`; nothing copied.
- `say(text): Long` — trimmed, cut to `MAX_MESSAGE`; blank refused (throws).
- `answer(id): Talked` — `Replied`, `Failed(failure)`, or `NothingToAnswer` (gone, not his, already answered);
  the question is `Conversation.window` of the thread at now, as lines, `replyTo`, and the running plan
  (`ask.running()`); the request is `ask.request(question)` (where release 2 adds the sheet). Failure leaves the
  message stored, not answered.
- `delete(id)`.

Tests first (`TalkToTheTrainerTest`): a sent message is stored, asked once and answered; a failed request keeps
the message, not answered, and `answer` again re-sends it and stores the reply; deleting his message removes the
reply; feedback and letters appear in the thread, in time order, without a row in the messages; the request's
conversation is the window (an item 15 days old is left out; the cap drops the oldest); the plan goes when one runs.

Commit: `feat(trainer): talking with the trainer, from the stored record (D145–D147)`.

### Task 5: the backup carries the conversation, format 11 (D148)

`Backup.trainerMessages` (`trainer_messages`, each with its id, time, from_owner, text, model, answered,
reply_to); `CURRENT_VERSION = 11` with its KDoc paragraph; export, prepare (one per id), restore (delete and insert
inside the transaction), `RestoreResult.trainerMessages`, `whatIsHere`, `inFile`; `BackupWording` names "N messages
with the trainer" when either side has any.

Tests: `BackupCodecTest` (format 11; the block round-trips; a version 10 file reads with none);
`BackupRestoreOrderTest` (the log; rows restored with their ids; an older file leaves none);
`BackupRoundTripTest` (CI); `BackupWordingTest`.

Commit: `feat(backup): the conversation rides in the backup, format 11 (D148)`.

### Task 6: the screen, Your trainer (D145, D146)

- `TalkWording`: "Your trainer", "Talk to your trainer", "Reply ›", "Write to your trainer…", "Send", "Your trainer
  is writing…", "Not answered", "Try again", "Delete", the confirmation, the AI line, the empty-thread line, day
  separators ("SUN 11 OCT"), "AFTER YOUR RUN · 60 MIN" (or "AFTER A SESSION"), "Your letter · week of 5 Oct",
  "Arrived 20:00 · tap to read". `TrainerWording.privacyTalk(ceiling)`.
- `TalkViewModel`: the thread, the draft (cut), `writing` (the id being answered), the failure, the selected
  message, the delete being confirmed, the ceiling. `send` stores first (the draft is cleared only once stored),
  then answers; `tryAgain(id)`; `select`, `askDelete`, `confirmDelete`, `cancelDelete`. Failures said (D8).
- `TalkScreen`: the thread (LazyColumn, scrolled to the newest), the composer at the bottom with Send.
- Trainer page: the row Log a workout (outlined) + Talk to your trainer (filled); the plan action outlined under it;
  Reply › on the coach's note. Route `trainer/talk`; a letter card opens the letter page.

Tests: `TalkViewModelTest` (send, failure keeps the message and the draft is not lost, Try again, delete);
`TalkScreenRenderTest` (bubbles, label, letter card opens its week, Try again on an unanswered message, the writing
line, Send disabled while writing, delete confirmation); `TrainerScreenRenderTest` updated (the row, Reply ›).

Commit: `feat(trainer): Your trainer, the conversation's screen (D145, D146)`.

### Task 7: what leaves the phone, 0.85.0

`privacy.html` (stores the conversation; the trainer bullet names the conversation: each message is sent with
the last 14 days of it and the record; the backup bullet names it); the code's "what leaves the phone" KDocs;
`ChangeLog` 0.85.0; versionCode 142, versionName 0.85.0; CLAUDE.md's list of locally skipped Room classes.
Verify: `:app:testDebugUnitTest` (only the Room classes skip), `:app:lintDebug`, `~/bin/ms-release`.

Commit: `docs: what leaves the phone names the conversation (D148)` and `0.85.0: talking with the trainer (D145–D148)`.

## Not built

D149–D151 (the sheet, its weekly rewrite, the What I know about you row and page); check-ins; the trainer changing
anything; search; voice, photographs, attachments; Hebrew.
