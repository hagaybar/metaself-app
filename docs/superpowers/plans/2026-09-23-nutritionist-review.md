# The model reviews a food's figures (D54) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. **One task per agent.** Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** public issue #18 — a food's figures can be reviewed by the model on request, in My foods'
editor and in the meal builder's *Make a food* panel; what he accepts is stored as an estimate, and
nothing he did not accept is rewritten.

**Spec:** `docs/superpowers/specs/2026-09-23-nutritionist-review-design.md` — **D54**. Read all of
it before starting any task. §5 (the owner's storage rule, verbatim) and §2 (what is sent) are the
contract; every figure in the tests below is one of the spec's invented examples or is invented and
says so.

**Architecture:** Save learns to leave an unchanged group alone (repository, per group: keep / clear
/ replace). The form learns to hand over a group as `AI_ESTIMATE` when it was accepted from a review.
A second narrow model interface (`FoodReviewer`) sits beside `MealEstimator`, sharing one extracted
OpenAI call so key, ceiling, timeout and failures are one code path. A pure prompt builds exactly
what is sent; a pure parser reads the strict reply into per-group suggestions. The two editors hold
the pending review and the accepted groups; Save turns accepted groups into estimates.

**Tech Stack:** Kotlin 1.9.22, JVM 17. JUnit 5 + Truth for everything pure and for view models;
JUnit 4 + Robolectric only for render tests. No new dependency.

**Schema: NONE — no red task.** No Room entity, DAO statement, database version, `app/schemas` file
or backup format changes. The existing `clearPer100g/clearPerUnit/clearGramsPerUnit` and the three
guarded `write…` statements are enough: a clear followed by a guarded write lands through the
`IS NULL` arm. **If any task finds itself editing `FoodEntities.kt`, `FoodDao.kt`,
`MetaSelfDatabase` or `app/schemas`, stop: the plan is wrong, not the code.**

**Room tests are CI-only.** Task 1 changes `RoomFoodRepository.correct()`; its tests go in
`RoomFoodRepositoryTest`, which skips locally on this aarch64 box (`assumeSqliteRuntime()`). So the
decision about each group is pulled into a pure planner tested locally, and the Room class only
executes the plan. The PR's CI run is the first execution of the Room tests; do not report them as
passing before it.

**Before every Gradle command:** `export ANDROID_HOME=/home/ubuntu/android-sdk`; use
`~/bin/gradlew-safe`, never bare `./gradlew`; redirect output to a file and check `$?`, never pipe a
build whose result you report; stage by explicit path, never `git add -A`. Locally, exactly the eight
Room classes listed in `CLAUDE.md` skip — nothing else may.

**Commit trailer** on every commit in this plan:

```
Refs #18
Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01LUpjHa9kZic9ZJrYzja45g
```

---

## The worked examples every test uses (invented; the spec's)

| Name | Held | Review says | Saved as |
|---|---|---|---|
| Oat biscuit, per 100 g | 480 · P 7 · C 62 · F 22, `LABEL` | kept exactly | untouched, still `LABEL` (no statement) |
| Oat biscuit, per biscuit | 90 · P 1 · C 12 · F 1, `TYPED` | F 1 → 4, reason given, `MEDIUM` | accepted: 90 · 1 · 12 · 4, `AI_ESTIMATE` `MEDIUM` |
| Oat biscuit, weight | 18 g, `TYPED` | (cannot be in the reply) | untouched, still `TYPED` |
| Impossible label (invented), per 100 g | 120 · P 30 · C 40 · F 10, `LABEL` | kcal 120 → 370, reason: the macros alone give about 370 | accepted: `AI_ESTIMATE`; not accepted: still `LABEL` |
| Lentil soup, new food, unit "bowl" | nothing | fill per 100 g 60 · P 4 · C 9 · F 1; per bowl 180 · P 12 · C 27 · F 3, `LOW` | accepted groups `AI_ESTIMATE` `LOW` via `findOrCreate` |

---

## Task 1 — Save leaves an unchanged group alone (repository)

The owner's "untouched groups must not pass through the clear". Independent of any model code;
valuable on its own (Save stops relabelling an untouched label group `TYPED`).

**Files:**
- Create `app/src/main/java/com/metaself/app/domain/food/Correction.kt` — pure
  `Correction.plan(stored: FoodFacts?, incoming: FoodFacts): Plan`, where `Plan` holds, per group,
  `Keep | Clear | Replace(fact)`.
- Modify `app/src/main/java/com/metaself/app/domain/food/ReplacedFacts.kt` — expose the existing
  nine-significant-figure comparison (`sameFigures`) as `internal` for reuse; do not duplicate it.
- Modify `app/src/main/java/com/metaself/app/data/food/RoomFoodRepository.kt` — `correct()` runs the
  plan: `Keep` issues no statement; `Clear` clears; `Replace` clears then offers through the
  existing `offerEach`. The `NeededBySavedMeals` refusal is unchanged and still checked first.
- Modify `app/src/main/java/com/metaself/app/data/food/FoodRepository.kt` — `correct()` KDoc: "his
  hand beats the guard" now reads "a group that changed replaces what was there, whatever its rank;
  a group that did not change is not touched".
- Modify `app/src/test/java/com/metaself/app/data/food/FakeFoodRepository.kt` — same plan, so view
  model tests see the same provenance the real one stores.
- Test: `app/src/test/java/com/metaself/app/domain/food/CorrectionTest.kt` (local),
  `app/src/test/java/com/metaself/app/data/food/RoomFoodRepositoryTest.kt` (**CI-only**).

- [ ] **Red (pure):** in `CorrectionTest` — equal figures (and equal unit name) → `Keep`, whatever
  provenance the incoming group claims; figures equal to nine significant figures but not bit for
  bit → `Keep`; a different figure → `Replace`; the unit name differing only in case → `Replace`
  (spec: compared exactly); incoming null over stored → `Clear`; incoming over stored null →
  `Replace`; stored null (no food) → every non-null group `Replace`.
- [ ] Run `~/bin/gradlew-safe :app:testDebugUnitTest --tests '*CorrectionTest*' > $SCRATCH/t1.log
  2>&1; echo $?` — expect non-zero (does not compile / fails).
- [ ] **Green:** implement `Correction`.
- [ ] **Red (Room, CI-only):** in `RoomFoodRepositoryTest` — (a) Oat biscuit: saving with per 100 g
  unchanged keeps `LABEL`, its confidence and its `setAtMillis`; (b) a `TYPED` group arriving over a
  `LABEL` group with a different figure replaces it (the existing *a correction may replace a number
  that outranks it* must still pass); (c) an `AI_ESTIMATE` group with a different figure replaces a
  `LABEL` group — acceptance is allowed to; (d) an `AI_ESTIMATE` group equal to a `LABEL` group
  leaves `LABEL`; (e) *a correction may empty a group* still passes; (f) the saved-meal refusal
  still fires and rolls back. Confirm these classes SKIP locally, not fail.
- [ ] **Green:** rewire `correct()`; update the fake.
- [ ] Run the full `:app:testDebugUnitTest` to a file; `$?` is 0; exactly the eight Room classes skip.
- [ ] Commit: `Save leaves a group it did not change alone` (paths staged explicitly).

## Task 2 — The form can hand over an accepted group as an estimate (pure)

**Files:**
- Create `app/src/main/java/com/metaself/app/domain/food/FactGroup.kt` — `enum class FactGroup {
  PER_100G, PER_UNIT }` (the two groups a review can touch; the weight is deliberately not one).
- Modify `app/src/main/java/com/metaself/app/domain/food/FoodForm.kt` —
  `toFacts(setAtMillis, estimated: Map<FactGroup, Confidence> = emptyMap())`: a group in `estimated`
  gets `Provenance(AI_ESTIMATE, confidence)`, every other group `TYPED`; the weight is always
  `TYPED`. Add `fun with(group: FactGroup, nutrients: Nutrients): FoodForm` (fills four boxes, never
  the unit name or weight, numbers written as `asTyped` writes them). Update the class KDoc
  ("Everything typed here is TYPED") to name the D54 exception.
- Create `app/src/main/java/com/metaself/app/domain/food/FormOrigins.kt` — pure
  `FormOrigins.of(stored: FoodFacts?, form: FoodForm, accepted: Map<FactGroup, Confidence>)`:
  per group the `(source, confidence)` the review request sends (spec §2's four-way rule); a group
  that does not parse is null.
- Test: `.../domain/food/FoodFormTest.kt` (extend), `.../domain/food/FormOriginsTest.kt`.

- [ ] **Red:** `toFacts` with `estimated = {PER_UNIT: MEDIUM}` makes per-unit `AI_ESTIMATE MEDIUM`
  and per 100 g `TYPED`; weight never estimated; the default call is unchanged (every existing
  `FoodFormTest` passes untouched). `with` fills only the four boxes. `FormOrigins`: form equal to a
  stored `LABEL` group → `LABEL`; accepted → `AI_ESTIMATE` with its confidence; typed over → `TYPED`;
  half-typed → null; `UNRECOGNISED` stored → sent as unknown.
- [ ] Green; full unit run to a file; `$?` 0.
- [ ] Commit: `A food form can carry a group accepted from a review as an estimate`.

## Task 3 — One OpenAI call, shared (refactor, no behaviour change)

**Files:**
- Create `app/src/main/java/com/metaself/app/data/ai/OpenAiCall.kt` — the key check, the ceiling
  check, the POST, `recordCall()` only when a call happened, `IOException → Unreachable`, any other
  exception → `Refused(message)`, non-2xx → `Refused(refusalOf(...))`. Returns a small sealed
  `CallOutcome { Body(text) | Failed(EstimateResult /* one of the five failures */) }`.
- Modify `app/src/main/java/com/metaself/app/data/ai/OpenAiMealEstimator.kt` — use it. D34's retry
  and `alsoRecorded` stay where they are.
- Test: `OpenAiMealEstimatorTest` must pass **unchanged**; add `OpenAiCallTest` for the counting rule
  (a refusal counts, an unreachable server does not) using the existing local-server pattern. **No
  test makes a real network call.**

- [ ] Red (`OpenAiCallTest`), green, full run to a file, `$?` 0, `OpenAiMealEstimatorTest` untouched.
- [ ] Commit: `The meal estimator's call to the model is one shared piece`.

## Task 4 — What a review sends (pure prompt)

**Files:**
- Create `app/src/main/java/com/metaself/app/domain/ai/FoodReviewer.kt` — `ReviewRequest(process:
  ReviewProcess {NEW_FOOD, EXISTING_FOOD}, name, brand, per100g: HeldGroup?, unitName: String,
  perUnit: HeldGroup?, gramsPerUnit: HeldWeight?)`; `HeldGroup(nutrients, source, confidence)`;
  `HeldWeight(grams, source)`; `interface FoodReviewer { suspend fun review(request): ReviewResult }`;
  `ReviewResult { Proposed(FoodReview) | Failed(EstimateResult) }` — `Failed` carries one of the five
  non-proposal `EstimateResult`s so `ProposalWording.failure` is reused unchanged.
- Create `app/src/main/java/com/metaself/app/data/ai/ReviewPrompt.kt` — `requestBody(model,
  request)`: system instructions (spec §2, in substance), user message = the request as JSON in the
  spec's shape, temperature 0, `response_format` strict schema (spec §3), groups nullable via
  `anyOf`.
- Test: `app/src/test/java/com/metaself/app/data/ai/ReviewPromptTest.kt`.

- [ ] **Red:** *nothing but the one food is sent* — parse the body; the user message's keys are
  exactly `process, name, brand, per_100g, unit_name, per_unit, grams_per_unit`; the whole body holds
  no date, no id, no barcode, no alias (build the request from a `Food` with an alias and a barcode
  and assert neither string appears anywhere). Sources are sent as the four names plus `UNKNOWN`.
  The instructions say a label is kept unless impossible and a change needs a reason; say never to
  state a weight or name a unit. The schema is strict, every field required, **has no weight field**
  (assert the property names), and `confidence` is the three-value enum. Hebrew survives.
- [ ] Green; full run; `$?` 0.
- [ ] Commit: `What a review sends: one food, and nothing else`.
- [ ] **Check against the provider's current structured-output documentation** that `anyOf` with
  `{"type":"null"}` is accepted under `strict: true`. If it is not, use `type: ["object","null"]`;
  record which in the commit message. Do not guess — say it was unverified if it cannot be checked.

## Task 5 — What a review returns (pure parser)

**Files:**
- Modify `app/src/main/java/com/metaself/app/domain/ai/FoodReviewer.kt` — `FoodReview(per100g:
  Suggestion?, perUnit: Suggestion?, note: String?, setAside: List<FactGroup>)`;
  `Suggestion(nutrients, confidence, filled: Boolean, changes: List<FigureChange(figure, from, to,
  reason)>, reason: String?)`. A group with no change and no fill is `null` (nothing to show).
- Create `app/src/main/java/com/metaself/app/data/ai/ReviewResponse.kt` — `parse(body, request)`,
  spec §3's rules, inside `runCatching` (an exception becomes `Unreadable`, never crosses the seam).
- Test: `app/src/test/java/com/metaself/app/data/ai/ReviewResponseTest.kt`.

- [ ] **Red:** Oat biscuit → per 100 g `null` (kept exactly), per biscuit one change F 1 → 4 with the
  reason, `MEDIUM`; a label echoed as 3.25 is not a change; a changed figure rounded to one decimal;
  a change with a blank reason sets that group aside (named in `setAside`); a figure past D42's
  ceiling, negative or non-finite sets the group aside; `per_unit` ignored when the request named no
  unit; a fill with no reason at all is set aside; every changing group set aside → `Unreadable`;
  nothing changed → `Proposed` with both groups null; an unknown confidence reads `LOW`
  (`EstimateResponse`'s rule); garbage → `Unreadable`.
- [ ] Green; full run; `$?` 0.
- [ ] Commit: `A review's answer is read strictly, a group at a time`.

## Task 6 — The reviewer behind the interface

**Files:**
- Create `app/src/main/java/com/metaself/app/data/ai/OpenAiFoodReviewer.kt` — `OpenAiCall` +
  `ReviewPrompt` + `ReviewResponse`; one call, no retry; failures written to `ProblemLog` as
  `"review refused"`, `"review unreadable"`, `"review unreachable"` **without the food's name**.
- Modify `app/src/main/java/com/metaself/app/di/AiModule.kt` — `provideFoodReviewer`.
- Create `app/src/test/java/com/metaself/app/data/ai/FakeFoodReviewer.kt` for the view model tasks.
- Test: `app/src/test/java/com/metaself/app/data/ai/OpenAiFoodReviewerTest.kt` (local server, as the
  estimator's test does).

- [ ] **Red:** no key → `NoKey` and no request made; ceiling reached → `CeilingReached` and no
  request; one successful review counts one call; the problem log never contains the food's name
  (assert on a distinctive invented name).
- [ ] Green; full run; `$?` 0.
- [ ] Commit: `A food can be reviewed by the model`.

## Task 7 — My foods: asking, showing, accepting (view model)

**Files:**
- Modify `app/src/main/java/com/metaself/app/ui/screen/foods/FoodsUiState.kt` — `Editing` gains
  `review: Review?` (`Asking | Shown(FoodReview) `) and `accepted: Map<FactGroup, Confidence>`.
- Modify `app/src/main/java/com/metaself/app/ui/screen/foods/FoodsViewModel.kt` — inject
  `FoodReviewer`; `review()` builds the request from `FormOrigins` against the stored food
  (`EXISTING_FOOD`); `acceptGroup(group)`, `acceptAll()`, `dismissReview()`; `setForm` withdraws the
  suggestion of any group whose four boxes changed (including one still in flight); `save()` passes
  `estimated = accepted` to `toFacts`; an answer that lands after the editor closed or another food
  opened is dropped; a failure goes to the editor's sentence slot as `ProposalWording.failure(...)`.
  Everything through `act(...)`/`guarded` (a thrown review says `COULD_NOT_OPEN`: it wrote nothing).
- Test: `app/src/test/java/com/metaself/app/ui/screen/foods/FoodsViewModelTest.kt` (extend), with
  `FakeFoodRepository` and `FakeFoodReviewer`.

- [ ] **Red:** the request carries the per 100 g group as `LABEL` and per biscuit as `TYPED` for an
  untouched Oat biscuit, and `TYPED` for a group he has retyped; accepting per biscuit fills four
  boxes and not the unit or weight; Save then stores per biscuit `AI_ESTIMATE MEDIUM` and per 100 g
  still `LABEL` (through the fake's Task 1 plan); editing a figure after accepting still saves the
  group `AI_ESTIMATE`; typing in a group with a pending suggestion withdraws it; Dismiss keeps what
  was accepted; Cancel forgets everything; an answer after closing is dropped; a `CeilingReached`
  says the existing sentence and leaves the form as it was.
- [ ] Green; full run; `$?` 0.
- [ ] Commit: `My foods can ask for a review and accept it a group at a time`.

## Task 8 — My foods: drawing it (screen)

**Files:**
- Modify `app/src/main/java/com/metaself/app/ui/screen/foods/FoodsScreen.kt` — **Review the
  figures** under the name/brand block with its one line of small print; **Reviewing…** while out;
  under each group heading its change lines and **Use these**; under the button the note,
  *couldn't be used* lines, **Use all** (only with two suggestions) and **Dismiss**. No badge, colour
  or icon on any box.
- Modify `app/src/main/java/com/metaself/app/ui/screen/manager/ManagerScreen.kt` and
  `app/src/main/java/com/metaself/app/ui/nav/MetaSelfNavHost.kt` (where `foodsViewModel::setForm`
  is wired) to pass the new callbacks through.
- Modify `app/src/main/res/values/strings.xml` — the new sentences, each once. Reuse
  `ProposalWording.worthFigures` for a filled group's line.
- Create `app/src/main/java/com/metaself/app/ui/food/ReviewWording.kt` — "Fat 1 → 4 g — reason",
  figures formatted with `Portions.format`.
- Test: `ReviewWordingTest` (pure, JUnit 5); a render test (JUnit 4 + Robolectric, `ComposeRender`)
  asserting the change line and **Use these** appear under the per-one heading and not under per
  100 g, and that no suggestion text is in any `EditableText` before acceptance. **No width,
  wrapping or touch-size assertions** (320 dp canvas, no font — see `CLAUDE.md`).

- [ ] Red, green; full run; `$?` 0; `:app:lintDebug` to a file, `$?` 0.
- [ ] Commit: `My foods shows a review beside the figures it would change`.

## Task 9 — The meal builder's *Make a food* can be reviewed

**Files:**
- Modify `app/src/main/java/com/metaself/app/ui/screen/mealbuilder/MealBuilderViewModel.kt` and
  `MealBuilderUiState.kt` — hoist the new-food form out of `remember` into the view model (it has to
  outlive the request); `review()` with `NEW_FOOD` and no stored food (every typed group `TYPED`);
  accept/dismiss/withdraw as Task 7; `createFood` passes `estimated = accepted`.
- Modify `app/src/main/java/com/metaself/app/ui/screen/mealbuilder/MealBuilderScreen.kt` — `NewFood`
  reads state from the view model and draws the same review block (extract the Task 8 composables
  to `ui/food/` and share them rather than copying).
- Test: `MealBuilderViewModelTest` (extend) — Lentil soup with only a name and unit "bowl": both
  groups filled, accepted, `findOrCreate` receives both as `AI_ESTIMATE LOW`; a name that already
  exists with a `TYPED` per 100 g keeps it (guarded offer) and the D45 notice is unchanged in
  behaviour.

- [ ] Red, green; full run; `$?` 0.
- [ ] Commit: `A new food can be reviewed before it is made`.

## Task 10 — Privacy, terms, and hand-over

**Files:**
- Modify `privacy.html` — the new list item and *Five things*, verbatim from spec §7; *Last updated*
  to the release date.
- Modify `terms.html` — the OpenAI line, verbatim from spec §7.
- Modify `app/build.gradle.kts` — `versionCode` +1, `versionName` minor +1.
- Modify `docs/superpowers/specs/2026-09-23-nutritionist-review-design.md` only if the build found
  the spec wrong — and then say so in the PR, not silently.

- [ ] Re-read both pages in full after editing: every sentence about what is sent is still true
  (item 1's "typing the numbers yourself sends nothing" included).
- [ ] Full `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, each to a file, each
  `$?` 0; exactly the eight Room classes skip.
- [ ] Anonymisation read of every new comment, KDoc, test name and string (`CLAUDE.md`: the leaks are
  sentences) — invented figures only, labelled where written in prose.
- [ ] Commit: `0.46.0: the model reviews a food's figures on request (D54)`.
- [ ] Hand-over note lists what is **not** verified locally: the Room tests of Task 1 (CI), the
  strict-schema `anyOf` acceptance if Task 4 could not confirm it, and everything about real model
  behaviour (phone check: review a scanned food, review a new food, review with no key).
