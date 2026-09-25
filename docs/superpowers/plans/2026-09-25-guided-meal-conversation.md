# A meal can be worked out in conversation — Implementation Plan

**Goal:** describing a meal may become a short conversation — the model decides whether it needs
questions, asks up to five one at a time with ready-made answers, then works the meal out like a
nutritionist at higher thinking — and the result opens on the existing proposal screen with *Log it /
Keep as a meal / Log it and keep as a meal*, from Add something and from a new button in My meals.

**Decision:** the owner's, 2026-09-25. Recorded as D58 in
`docs/superpowers/specs/2026-09-25-guided-meal-conversation-design.md`. No public issue exists.
Section numbers below (§N) are that spec's.

**Red lines (stop and report if crossed):**

- **No database change.** No new entity, column or migration; `app/schemas` gains no file. The one
  new stored thing is an optional field in D57's remembered-profile JSON, in the settings store.
- **Nothing is stored before the end choice**, and the conversation is never stored, backed up or
  logged — not the description, a question, an option or an answer.
- **Nothing new about the owner is sent.** The bodies carry the description, the model's own
  questions and his answers, and nothing else. `EstimatePromptTest`'s *nothing else is sent* test
  passes unchanged, and the new prompt's test forbids the same things.
- **Every request that reaches the provider is counted**; one request is always kept for the result
  (§7).
- **The everyday profile D57 remembers is never changed by a final analysis** (§8.5).
- **An estimate is never presented as a measurement** (D4): every proposed row is `AI_ESTIMATE`.

**Build rules:** `~/bin/gradlew-safe`, never bare Gradle; `export ANDROID_HOME=/home/ubuntu/android-sdk`;
redirect build output to a file and check `$?`; stage by explicit path. JUnit 5 + Truth for pure code;
JUnit 4 only for Robolectric and Compose. Locally exactly the eight database classes skip.

## Steps

Each step is test-first: write the tests named, see them fail, then build.

1. **The prompts and schemas** (`data/ai/ConversationPrompt.kt`, pure). Three request bodies through
   `ChatRequest.body`: `opening(model, description, profile)`, `step(model, description, asked,
   cap, profile)`, `final(model, description, asked, moreDetail, missingAmounts, profile)`, where
   `asked` is the list of (question, answer) pairs. Messages as §3's table. Instructions as §3.1 and
   §3.2; the opening appends `EstimatePrompt`'s instructions verbatim (expose them `internal`, do not
   copy them). Schemas as §4 — the question schema once, the step schema, the opening = step + `items`
   + `note` (reusing `EstimatePrompt`'s item schema object, not a copy), the final = `plate`, `items`,
   `note`. Tests (JUnit 5, `ConversationPromptTest`): message roles and order with zero, one and three
   answered pairs; the cap sentence's numbers; `plate` is the first property of the final schema;
   every schema is `additionalProperties: false` with every property required; the `json_object`
   fallback carries the schema in the first system message; **nothing else is sent** — a body built
   with a profile-shaped world around it contains none of a weight, a date, a food name of his
   (mirroring `EstimatePromptTest`'s case).
2. **The readers** (`data/ai/ConversationResponse.kt`, pure). Refactor `EstimateResponse` so its
   content reader is callable on a parsed payload (`EstimateResponse.readContent(content)`), with
   no change in behaviour — its existing tests are the guard. `ConversationResponse.opening(body)` →
   `Estimate(EstimateResult)` | `Ask(question, planned)` | `Unreadable(why, raw)`;
   `.step(body)` → `Ask` | `Enough` | `Unreadable`; `.final(body)` → `EstimateResult` via the
   refactored reader, with `MealProposal.answer` always set (§5.1). Tests (JUnit 5): every row of
   §4.1's table; options trimmed, blanks and case-insensitive repeats removed; 1 option and 7 options
   are unreadable, 2 and 6 read; `total_planned` 0 → 1, 9 → 5; `items` on an asking opening ignored;
   a final reply with `plate` and a dropped item names it; a final reply missing an amount is
   `AmountMissing`.
3. **The conversation, as a pure state machine** (`domain/ai/MealConversation.kt`). States:
   `Describing`, `Waiting(kind)`, `Offer(question1, cap)`, `Asking(index, question, answers, cap,
   kept)`, `StepFailed(...)`, `FinalFailed(...)`, `Done(result)`; events: opened, ok, bestGuess,
   answer(text), enough, back, retry, stepArrived, finalArrived, failed. It decides, and never sends:
   each transition returns the next state and the one request to make, if any (`None`,
   `Step(asked, cap)`, `Final(asked)`), so the view model and the tests share one rule. Includes the
   allowance arithmetic of §7 as a function of *remaining* passed in. Tests (JUnit 5,
   `MealConversationTest`): no questions → done with no further request; offer cap = min(planned, 5,
   remaining), none offered at 0; OK shows question 1 with no request; answering below the cap asks a
   step, at the cap asks the final; *Enough* asks the final; the model's *no more* asks the final;
   **Back** from question 3 shows question 2 with its answer, no request; the same answer forward
   again is no request and restores question 3 with its answer; a different answer discards 3 and
   asks a step; back from question 1 → offer, from the offer → describing with the words; one
   remaining before a step → final with the allowance line; zero before the final → the ceiling
   failure with the words and answers kept; a failed step offers retry (same request) and best guess
   (final with answers so far); a failed final retries the final, not the questions.
4. **D57, aimed at a wanted level** (`RequestFix.kt`, `RequestProfile.kt`, `RequestProfileStore`,
   `OpenAiCall.kt`). `RequestFix.candidates(sent, refusal, wanted = "low")`: with a list, the lowest
   accepted value at or above `wanted`, else the highest below; without a list, the next up — or,
   when `wanted` is `high`, the next down. `OpenAiCall.send(effort = Effort.EVERYDAY | Effort.DEEP,
   build)`: DEEP raises a profile that sends an effort to the remembered deep level or `high`, leaves a
   profile that sends none alone, and on a read answer remembers only the deep level
   (`rememberDeep(model, effort)`); an everyday `remember` keeps the entry's deep field. The deep
   request uses a client with a 120-second call timeout (`client.newBuilder()`, §8.6). Tests:
   `RequestFixTest` — every existing case unchanged, plus `high` refused with lists `[low, medium]`
   → medium, `[minimal]` → minimal, without a list → medium then low; `RequestProfileTest` /
   `DataStoreRequestProfileStoreTest` — the deep field round-trips, an entry without it reads as
   none, an everyday write keeps it, a deep write changes nothing else; `OpenAiCallLearningTest` —
   DEEP on a reasoning profile sends `high`; on a temperature profile sends it unchanged; `high`
   refused with a list learns `medium`, remembers it as deep and leaves `reasoning_effort` in the
   everyday profile untouched; every request counted.
5. **The conversation over the wire** (`data/ai/OpenAiMealConversation.kt` behind a domain
   interface `MealConversationAsker` with `open`, `next`, `finish`). Uses `OpenAiCall` (EVERYDAY for
   opening and steps, DEEP for the final); D34's second ask after an opening estimate is today's
   `EstimatePrompt` retry, and after a final is the final again with the missing-amounts sentence;
   the problem log records kinds only (§9). **Fake-server tests** (`MockWebServer`, as
   `OpenAiCallTest` does; `OpenAiMealConversationTest`): a three-question conversation end to end
   — four requests recorded by the server and counted by the settings store, each body's messages
   exactly as §3 (the pairs grow by one each step), the final carries `reasoning_effort: "high"` for
   a reasoning model; an apple — one request; best guess — two; an unreadable step then retry; a
   refusal mid-conversation; the ceiling reached between steps (the step not sent); D34 on the final
   — the second final names the item; **the problem log holds no word of the description, questions
   or answers** in any of these.
6. **The view model** (`ProposalViewModel`). Drives `MealConversation` and the asker; new
   `ProposalUiState` cases `Offer`, `Asking`, `StepFailed`, `Waiting(kind)`; the final opens today's
   `Proposed` (own-food matching as today). *Ask again* after a conversation calls `finish` with
   `moreDetail`. Back handling exposed as `back(): Boolean` (false = leave). Tests (JUnit 5 with a
   fake asker, as `ProposalViewModelTest` does): the flow of step 3 through the view model; a result
   after a conversation carries the answer for *Show the model's answer*; *Ask again* after a
   conversation sends the answers again; nothing reaches `FoodRepository` or the day before the
   end choice.
7. **The screens** (`ProposalScreen.kt` and a new `ConversationContent.kt`). The offer; the question
   card (heading, question, answer buttons, *Other* box + *Answer*, *That's enough, go ahead*, *Back*,
   the chosen answer marked on return); the two waiting lines; the step failure with *Try again* and
   *Use your best guess with what you've said so far*; `BackHandler` wired to `back()`. Strings in
   `strings.xml` (the offer is a plural). Render tests (Robolectric + Compose, JUnit 4,
   `ConversationScreenRenderTest`): the offer's line and two buttons; a question's options, *Other*,
   *Enough* and *Back* all drawn; a tapped option calls answer with its text; *Other* is disabled while
   blank; the returned-to question shows its answer; the step failure's two buttons; long options wrap
   at `+w150dp` without overlapping (relative geometry only — no absolute sizes, per CLAUDE.md).
8. **The end choice** (§5.2). Rename *Save this meal* → *Log it* and *Save, and keep these as a meal*
   → *Log it and keep as a meal* (same acts). Add **Keep as a meal**: the day's `MealNamingSheet`
   over preview rows built from the `ItemToLog`s; on confirm, a new `MealsFromProposal.keep(name,
   rows)` (in `data/food/`, using `LoggedFoods.attach`, `MealFromDay.from` and
   `SavedMealRepository.createThen` + `put`) inside **one** `database.withTransaction`. One row →
   neither meal button, and §5.2's line instead. The filled button follows the entry point.
   Tests: `MealsFromProposalTest` (Room, Robolectric — **skips locally, runs in CI**) — a meal made
   with its parts and new foods taught, nothing logged; a duplicate name leaves no new food; a part
   that cannot join is refused with the day's sentence and leaves no new food. Render tests: three
   buttons over two rows, one button and the line over one; the filled one per entry point.
   `KeepingAsMealTest`'s existing cases green under the new label.
9. **My meals' button and the landing rule** (`MealsContent.kt`, `ManagerScreen.kt`,
   `MetaSelfNavHost.kt`). *Describe a meal* beside *Build a meal*, in both the empty and the listed
   state; the describe route gains an optional `from` argument; after *Log it* / *Log it and keep as a
   meal* land on the day, after *Keep as a meal* on My meals (§5.3). Tests: render test for the
   button in both states; a navigation test that each choice lands where §5.3 says.
10. **Privacy, wording, version.** `privacy.html`'s first item as §10 and its *Last updated*;
    `EstimatePrompt`'s and `ProposalViewModel`'s KDoc that say *one call, no conversation* rewritten to
    cite D58; `versionName` 0.52.0 / `versionCode` +1. Verify `:app:testDebugUnitTest` (exactly the
    eight database classes skip, plus the new `MealsFromProposalTest` — **nine** locally; CI must
    show none) and `:app:lintDebug`, each redirected to a file with `$?` checked. Read every new
    comment, string, test name and the spec's example once more for anything personal (CLAUDE.md,
    *This repository is PUBLIC*). Push the branch; no PR until asked.

## Risks

- **`anyOf` with `null` in a strict schema.** The provider's strict mode accepts it for the models
  known when this was written, but a model that refuses the schema would fail every conversation.
  D57 does not learn from a schema refusal of the app's own (by design). Mitigation: step 1's schema
  test pins the exact shape; step 5 includes a refusal case; if a model is found to refuse it, the
  fallback is a non-null question object with empty strings meaning *none*, read the same way.
- **The first request is now bigger for every described meal**, and a model may ask when it need
  not. Watched on the phone after release; the threshold sentence in §3.1 is the lever.
- **High effort is slow.** 120 seconds is a bound chosen, not measured. If finals time out on the
  phone, that is the number to revisit — with the fact that a timed-out request was still paid.
- **The keep-only transaction.** `LoggedFoods.attach` has never run inside a saved-meal transaction;
  Room allows nesting, but every repository call on the path must use the same database. Step 8's
  Room tests prove it in CI only — the local run cannot (aarch64). If attaching cannot join the
  transaction, stop and report rather than accept a path that can leave orphan foods.
- **Back through `BackHandler`** competes with the navigation's own back. The render test in step 7
  and the navigation test in step 9 both press back; a double pop would leave the screen with the
  conversation half-done — harmless (nothing stored) but wrong.
- **Nine skips locally, not eight.** The new Room test skips locally for the same reason the eight
  do; CLAUDE.md's list and the CI invariant (*no test skipped*) must both be kept true — update the
  list in CLAUDE.md in step 10 if the test is added as a new class.
- **Two open questions for the owner** (the spec's end): weights in the reason, and a second model
  for the final. Neither blocks the build as written; both would change §3.2 or §8 if answered
  differently.

## Not built

- No record of conversations anywhere, and no way to see one after the end choice (§6).
- No second model setting for the final analysis (open question 2).
- No per-item reason field: the existing `detail` carries it (§4.2).
- No photograph path, as always.
