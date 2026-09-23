# Typed amounts, and his own foods used (D53) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. **One task per agent.** Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** public issues #1 (a real, typed quantity replaces *Less / As described / More*) and #4 (a
part already in a saved meal can have its amount changed), as the owner decided them on #1 on
2026-09-23.

**Spec:** `docs/superpowers/specs/2026-09-23-typed-amounts-design.md` — **D53**. Read all of it
before starting any task; §3's provenance table and §5's unit rule are the contract, and every figure
in the tests below is one of the spec's invented worked examples.

**Architecture:** The model answers with a *worth* (per 100 g / per 100 ml / per one piece) and an
*amount*, separately. A pure item type multiplies them, says where the result came from, and rounds
once. A pure matcher finds his own food for each item's plain name, on the phone, after the reply.
The proposal screen, the repeat screen's one-day adjuster and the meal builder all get one typed
amount box. Nothing stored changes shape.

**Tech Stack:** Kotlin 1.9.22, JVM 17. JUnit 5 + Truth for everything pure and for view models;
JUnit 4 + Robolectric only for render tests. No new dependency.

**Schema: NONE — no red task.** No Room entity, DAO, database version or `app/schemas` file changes,
so no migration and nothing that only CI can verify beyond what already skips locally. Why none is
needed: the worth lives in the proposal's memory until it is saved; a saved row keeps its existing
whole-number figures, amount, unit, source, confidence and food link; the detail goes into the row's
existing portion-words text; a food's three facts already carry their own provenance. The backup
format does not change. **If any task finds itself editing `data/day/*Entities*`, `FoodEntities.kt`,
a DAO or `MetaSelfDatabase`, stop: the plan is wrong, not the code.**

**Before every Gradle command:** `export ANDROID_HOME=/home/ubuntu/android-sdk`; use
`~/bin/gradlew-safe`, never bare `./gradlew`; redirect output to a file and check `$?`, never pipe a
build whose result you report; stage by explicit path, never `git add -A`. Locally, exactly the eight
Room classes listed in `CLAUDE.md` skip — nothing else may.

**Commit trailer** on every commit in this plan:

```
Refs #1
Refs #4
Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01LUpjHa9kZic9ZJrYzja45g
```

Tasks 1–7 carry `Refs #1` only; task 8 carries `Refs #4` only; task 9 commits nothing unless a
check fails, and then carries both.

---

## The worked examples every test uses (invented; the spec's)

| Name | Worth | Amount | Row |
|---|---|---|---|
| Beef burger, estimated MEDIUM | per 100 g: 250 kcal · P 18 · C 0 · F 20 | 200 g → typed 150 g | 500 · 36 · 0 · 40 → 375 · 27 · 0 · 30, `AI_ESTIMATE` MEDIUM both times |
| Pita, his food, TYPED | per pita: 250 kcal · P 8 · C 50 · F 1 | 1 pita | 250 · 8 · 50 · 1, `TYPED`, *From your foods* |
| Pita, the model's | per pita: 165 kcal · P 5 · C 33 · F 1, MEDIUM | 1 pita | 165 · 5 · 33 · 1 (after *Use the estimate*) |
| Hamburger bun, his food, TYPED | per 100 g: 270 kcal · P 9 · C 50 · F 4 | model says 1 bun; switched to g, typed 60 | 162 · 5 · 30 · 2, `TYPED` (9×0.6=5.4→5, 50×0.6=30, 4×0.6=2.4→2) |
| Hamburger bun, the model's | per bun: 150 kcal · P 5 · C 28 · F 2, MEDIUM | 1 bun | 150 · 5 · 28 · 2 |
| Greek yoghurt, his food | (any) | — | offered as the close match for the model's "Yoghurt" |

---

## Task 1 — The item: worth times amount (pure)

**Files:**
- Create `app/src/main/java/com/metaself/app/domain/amount/Worth.kt`
- Create `app/src/main/java/com/metaself/app/domain/amount/ItemToLog.kt`
- Modify `app/src/main/java/com/metaself/app/domain/portion/Portions.kt` — add `isMillilitres(unit)`
  (spellings `ml`, `millilitre`, `millilitres`, `milliliter`, `milliliters`, `מ"ל`, `מל`; its own set,
  folded into `MASS_UNITS` the way `GRAM_SPELLINGS` is)
- Modify `app/src/main/java/com/metaself/app/domain/food/LoggedFrom.kt` — make the private
  `numbers(...)` rounding reachable as `Logging.rounded(nutrients, provenance, amount, unit)` so the
  one place that rounds stays the one place
- Test: `app/src/test/java/com/metaself/app/domain/amount/ItemToLogTest.kt`,
  `.../domain/portion/PortionsTest.kt`

**Shapes** (names may be refined, meanings may not):

```kotlin
enum class Per(val divisor: Double) { HUNDRED(100.0), ONE(1.0) }
data class Rate(val nutrients: Nutrients, val per: Per)

sealed interface Worth {
    data class Estimated(val rate: Rate, val confidence: Confidence) : Worth
    data class Typed(val rate: Rate) : Worth
    data class YourFood(val food: Food, val countedAs: CountedAs) : Worth
}

data class ItemToLog(
    val name: String,          // plain name
    val detail: String,        // "" when none
    val amountText: String,    // as typed; a String because "1." is a real state
    val unit: String,
    val worth: Worth,
    val foodId: Long?,         // set only for YourFood, or Typed over YourFood (spec §3)
)
```

`ItemToLog` answers: `most` (`BelievableAmount.amountIn(unit)`), `amountOrNull` (positive and
believable, comma accepted as the builder's box does), `amountTooMuch`, `numbers: LoggedFrom.Numbers?`
(null while the amount is not usable; `YourFood` → `Logging.log(food.facts, amount, countedAs)`;
`Estimated`/`Typed` → `Logging.rounded(rate.nutrients * (amount / rate.per.divisor), provenance, amount,
unit)` with provenance `AI_ESTIMATE`+confidence or `TYPED`), `rateLine` data for the screen (for
`YourFood`, the food's worth at 100 g or at one, from `Logging.log(facts, 100.0 or 1.0, countedAs)`),
and `toFoodItem(): FoodItem?` (null while not loggable; `portion` =
`Portions.words(amount, unit)` plus ` (detail)` when the detail is not blank).

Also `fun ItemToLog.withTypedRate(rate: Rate): ItemToLog` — worth becomes `Typed`, `foodId` kept.

- [ ] **Step 1: failing tests** in `ItemToLogTest` (JUnit 5 + Truth), each named for the behaviour:
  - `an estimate times a typed amount is still an estimate` — burger, amount "150": 375/27/0/30,
    `AI_ESTIMATE`, MEDIUM.
  - `the amount changes the total and nothing about the worth` — burger at "200" then "150": `worth`
    equal both times.
  - `typing one figure of the worth makes the row typed with no confidence` — burger with
    `withTypedRate` changing only kcal: source `TYPED`, confidence null.
  - `his food's figures carry his food's source` — Pita (TYPED per pita), amount "1", `YourFood(pita,
    UNITS)`: 250/8/50/1, `TYPED`.
  - `a figure computed from two of his food's facts carries the weaker source` — a food knowing per
    slice as `AI_ESTIMATE` MEDIUM and what one slice weighs as `TYPED`, amount "60" g, `YourFood(food,
    GRAMS)` → source `AI_ESTIMATE` MEDIUM. (Pins that `Logging`'s `weakerOf` is what decides.)
  - `per 100 ml divides by 100` — a juice at per 100 ml 45 kcal · P 0 · C 11 · F 0, amount "300" ml →
    135 · 0 · 33 · 0.
  - `rounding happens once, figure by figure` — Hamburger bun per 100 g at "60" g: 162/5/30/2.
  - `a blank, zero or refused amount cannot be logged` — "", "0", "abc", "5001" g, "101" slice →
    `toFoodItem()` null; `amountTooMuch` true only for the last two.
  - `a comma is a decimal point` — "1,5" slice → 1.5.
  - `the detail is kept in the row's portion words` — bun, detail "sesame, toasted" → portion
    `"1 bun (sesame, toasted)"`; blank detail → `"1 bun"`.
  - `the app's own portion is stored as the app writes it` — amount "2", unit "portion": `toFoodItem()`
    stores `"2 portion"`, so D37's plural, chosen when a row is drawn, still applies.
  - In `PortionsTest`: `millilitres are recognised however spelled` and `millilitres are not grams`.
- [ ] **Step 2:** run `~/bin/gradlew-safe :app:testDebugUnitTest --tests '*ItemToLogTest' --tests
  '*PortionsTest'` → FAIL (does not compile).
- [ ] **Step 3:** implement. `Rate`'s nutrients are checked with D42's per-basis ceilings by the
  caller that builds one (Task 3, Task 4), not here — keep this type total.
- [ ] **Step 4:** the same command → PASS. Then the full `:app:testDebugUnitTest` → PASS (nothing
  else uses the new types yet; `Logging.rounded` must leave `LoggingTest` green).
- [ ] **Step 5: commit** `feat: an item is what it is worth times how much — rounded once` (explicit
  paths).

---

## Task 2 — Finding his own food (pure)

**Files:**
- Create `app/src/main/java/com/metaself/app/domain/food/FoodMatching.kt`
- Test: `app/src/test/java/com/metaself/app/domain/food/FoodMatchingTest.kt`

```kotlin
sealed interface FoodMatch {
    data object None : FoodMatch
    data class Exact(val food: Food) : FoodMatch
    data class Close(val food: Food) : FoodMatch
}

object FoodMatching {
    /** [offered]: the foods on offer, in the order the food list shows them. */
    fun match(plainName: String, offered: List<Food>): FoodMatch
    /** How his food can cost an amount in [unit] with no conversion, or null (spec §5). */
    fun countedAsFor(food: Food, unit: String): CountedAs?
}
```

- **Exact:** a food with `FoodKeys.brandKey(brand) == FoodKeys.NO_BRAND_KEY` and some name in
  `everyName` whose `FoodKeys.nameKey` equals `nameKey(plainName)`. A name that does not key
  (`runCatching`) matches nothing.
- **Close:** only when there is no exact; `FoodSearch.matching(offered, plainName).firstOrNull()`.
- **countedAsFor:** `GRAMS` when `Portions.isGrams(unit)` and `Logging.canWeigh(facts) == null`;
  `UNITS` when not grams, `Logging.canCount(facts) == null`, and `FoodKeys.nameKey(unit)` equals
  `nameKey(facts.perUnit?.unitName ?: FoodFacts.PORTION)`; otherwise null.

- [ ] **Step 1: failing tests:**
  - `the same name is an exact match` / `an alternative name is an exact match` (a food with
    `alsoKnownAs = listOf("Pitta")`, searched "pitta").
  - `case, spacing and accents do not stop an exact match` — "  PITA " finds *Pita*.
  - `a branded food is never an exact match` — *Milk* branded `Dairyco`, searched "Milk" → `Close`.
  - `only the foods given are searched` — a food absent from `offered` (as a hidden one is) → `None`.
  - `with no exact match, the first food the search lists is the close one` — offered
    `[Greek yoghurt, Yoghurt drink]`, searched "Yoghurt" → `Close(Greek yoghurt)`.
  - `an exact match is never shadowed by a close one` — offered `[Greek yoghurt, Yoghurt]` → `Exact(Yoghurt)`.
  - `a longer model name does not find a shorter food` — "Hamburger bun" against *Bun* → `None`
    (pins spec §4's stated limit).
  - `nothing matches` → `None`; a name of only punctuation → `None`.
  - `countedAsFor`: grams + per 100 g → GRAMS; grams + per-slice + weight → GRAMS; grams +
    per-slice, no weight → null; "Slice" vs food's "slice" → UNITS; "slices" vs "slice" → null
    (spec's cost); "portion" vs a food naming no unit → UNITS; "bun" vs per 100 g only → null;
    "ml" vs per 100 g → null; "kg" vs per 100 g → null.
- [ ] **Step 2:** run the class → FAIL. **Step 3:** implement. **Step 4:** class, then the full suite
  → PASS.
- [ ] **Step 5: commit** `feat: his own food is found for a described item — exactly, or as one suggestion`.

---

## Task 3 — The model answers worth and amount apart; the three buttons go

The one task that must change several files at once, because `ProposedItem`'s shape is what the
scaling buttons are built on. At its end the proposal screen has a typed amount box and a read-only
worth line; editing the worth (Task 4) and matching (Task 5) come after.

**Files:**
- Modify `data/ai/EstimatePrompt.kt` — instructions and `SCHEMA` exactly as spec §2 (fields `name`,
  `detail`, `amount`, `unit`, `figures_per` enum `["100","1"]`, `kcal`/`protein_g`/`carbs_g`/`fat_g`
  as `number`, `confidence`; all required; `additionalProperties: false`). Keep the D34 retry message
  and the drink rule; replace the amount rule with: state the unit given, else the natural piece in
  the singular; never convert a stated amount; never give a weight for an unstated one, not even in
  the detail; figures are per 100 of g/ml or per one piece, never the total; confidence is about the
  figures per piece or per 100 including the size of piece assumed; the name is the plain food name,
  everything else goes in the detail.
- Modify `data/ai/EstimateResponse.kt` — parse to the new `ProposedItem`; drop an item missing a
  figure, or whose worth is not finite, negative, or past `BelievableAmount`'s per-100 / per-one
  ceilings for its `figures_per`; keep `AmountMissing` for amount ≤ 0 or blank unit; keep "unknown
  confidence reads LOW"; an unknown `figures_per` drops the item.
- Modify `domain/ai/MealProposal.kt` — `ProposedItem(name, detail, amount, unit, rate: Rate,
  confidence)`; `MealProposal.totalKcal` goes (nothing on screen reads it once rows are `ItemToLog`).
- Delete `domain/ai/PortionScale.kt`, `domain/portion/PortionControl.kt`, `Portions.controlFor`,
  and their tests (`PortionScaleTest`; the `controlFor` block of `PortionsTest`). `Portions.LESS /
  AS_IT_WAS / MORE` stay until Task 7.
- Modify `ui/screen/propose/ProposalUiState.kt` — `ProposalRow(estimate: ProposedItem, item:
  ItemToLog)`; `Proposed.totalKcal` = sum of rows' `numbers?.kcal ?: 0`; `Proposed.blockedBy: Int?`
  = index of the first row that cannot be logged.
- Modify `ui/screen/propose/ProposalViewModel.kt` — rows built as `ItemToLog(name, detail,
  amountText = Portions.format(amount), unit, Worth.Estimated(rate, confidence), foodId = null)`;
  `setAmount(index, text)`, `step(index, by: Int)` (counted units only; never below 1; no-op
  otherwise), `remove`, `accepted()` returns `emptyList()` while `blockedBy != null`. `scale` and
  `setCount` go.
- Modify `ui/screen/propose/ProposalScreen.kt` — per row: name; detail (when not blank); amount
  `OutlinedTextField` (decimal keyboard) with the unit as its suffix, − / + around it when the unit is
  neither grams nor millilitres, and `R.string.amount_too_much` style sentence under it past the
  ceiling (reuse the builder's existing *At most … at a time* resources); the worth line *per 100 g:
  250 kcal · P 18 · C 0 · F 20* / *per bun: …* (figures printed as a person would type them: whole
  when whole, one decimal otherwise); the total; the origin line from
  `DayTotalsWording.origin(item.toFoodItem())`; Remove. *Save* and *Keep as a meal* disabled while
  `blockedBy != null`, with one sentence above them naming that row. `onScale`/`onCount` parameters
  replaced by `onSetAmount(Int, String)` and `onStep(Int, Int)`.
- Modify `res/values/strings.xml` — remove `propose_less`, `propose_as_described`, `propose_more`
  **only if** nothing else uses them (the repeat screen does until Task 7 — leave them); add
  `propose_amount_label`, `propose_per_100` (*per 100 %1$s*), `propose_per_one` (*per %1$s*),
  `propose_blocked` (*Say how much %1$s was to save this.*).
- Modify `ui/nav/MetaSelfNavHost.kt` — the new callbacks.
- Tests: `EstimatePromptTest`, `EstimateResponseTest`, `MealProposalTest`, `domain/ai/Proposals.kt`
  (fixtures), `ProposalViewModelTest`, `ProposalScreenRenderTest`, `DescribeWithoutKeySessionTest`.

- [ ] **Step 1: failing tests first, in this order:**
  - `EstimatePromptTest`: `every item field is required`, `figures_per is 100 or 1`, `the worth is a
    number, not an integer`, `the instructions forbid inventing a weight for an unstated amount`
    (asserts the sentence is present), **and the existing `NOTHING about the owner is sent` test
    stays untouched and green** — add `the request is built from the words alone` asserting
    `requestBody`'s parameters are (model, description, moreDetail, missingAmounts) and nothing else
    (reflection on the declared function is enough).
  - `EstimateResponseTest`: burger JSON (`"figures_per":"100"`, 200 g) → `ProposedItem` with
    `Rate(per = HUNDRED)`, 250/18/0/20; bun JSON (`"1"`, 1 bun) → `Per.ONE`; a `kcal` of 1200 with
    `"100"` → item dropped; `"figures_per":"per serving"` → dropped; negative fat → dropped (was
    clamped to 0 before — the test that pinned clamping changes, and says why in its name: `a
    negative figure drops the item rather than becoming 0`); amount 0 → `AmountMissing`; detail
    absent → dropped (strict schema says required); amount 6000 g → **kept** (the box refuses it on
    screen, spec §2).
  - `ProposalViewModelTest`: `typing an amount changes that row's total and nothing else` (burger
    "150" → 375; the other row unchanged); `the worth survives any amount` ; `a blank amount blocks
    saving and names the row` (`accepted()` empty, `blockedBy == 0`); `plus and minus step a counted
    row by one and never below one` (bun "1" − → stays "1"; + → "2"); `plus and minus do nothing to
    grams`; `accepted rows are estimates with the model's confidence at the typed amount`.
  - `ProposalScreenRenderTest` (JUnit 4 + Robolectric): the amount is in an `EditableText` holding
    "200" with "g" beside it; no node with text *Less*, *As described* or *More*; the worth line
    reads *per 100 g: 250 kcal · P 18 · C 0 · F 20*; *Save* is disabled when a row's box is blank.
- [ ] **Step 2:** run those classes → FAIL.
- [ ] **Step 3:** implement, file by file as listed.
- [ ] **Step 4:** those classes → PASS; then full `:app:testDebugUnitTest` and `:app:lintDebug` →
  PASS (lint catches the now-unused strings if any were missed).
- [ ] **Step 5: commit** `feat: the model answers what it is worth and how much apart; the amount is typed`.

---

## Task 4 — The worth can be typed over

**Files:** `ui/screen/propose/ProposalUiState.kt` (a per-row `editingWorth: WorthBoxes?` holding four
strings), `ProposalViewModel.kt` (`openWorth(index)`, `setWorthBox(index, which, text)`,
`closeWorth(index)`), `ProposalScreen.kt` (*Change* under the worth line opens four boxes: kcal,
protein, carbohydrate, fat, per the row's basis), `res/values/strings.xml`, tests
`ProposalViewModelTest`, `ProposalScreenRenderTest`.

Rules (spec §1, §3): the boxes open holding the current worth as a person would type it; each box
accepts a decimal point or comma; each is judged by `BelievableAmount` against the per-100 or per-one
ceiling and refuses in the food form's words with the ceiling named (reuse the food form's refusal
resources if they are resources; if they are still inline strings in `FoodForm`, reuse the same
sentences via a shared function — do not write new wording); **the row becomes `Worth.Typed` the
moment any box holds a value different from what it opened with**, compared as numbers (re-typing
"250" as "250.0" is no change); while any box is refused the row is blocked as a blank amount is.
`foodId` is kept (Task 5 sets it).

- [ ] **Step 1: failing tests:** `changing one figure of the worth makes the row typed` (burger kcal
  "250" → "240": `TYPED`, no confidence, 480 kcal at 200 g, protein still 36);
  `retyping the same figure changes nothing` ("250" → "250.0": still `AI_ESTIMATE` MEDIUM);
  `a worth past its ceiling blocks the row and says the ceiling` (per 100 g kcal "1001");
  `the amount and the worth move independently` (type worth, then amount: both kept);
  render: *Change* opens four `EditableText`s holding "250", "18", "0", "20", and after a change the
  origin line *Estimated — moderate confidence* is gone.
- [ ] **Steps 2–4:** FAIL, implement, PASS (class, then full suite and lint).
- [ ] **Step 5: commit** `feat: what an item is worth can be typed over, and then it is his`.

---

## Task 5 — His own foods on the proposal screen

**Files:** `ui/screen/propose/ProposalViewModel.kt` (inject `FoodRepository`; on a `Proposed` result
read `foods.observeOffered().first()` once and run `FoodMatching.match` per row), `ProposalUiState.kt`
(`ProposalRow` gains `match: FoodMatch` and `usingEstimate: Boolean`), `ProposalScreen.kt`,
`res/values/strings.xml`, `ui/nav/MetaSelfNavHost.kt` (only if Hilt wiring needs it — it should
not), tests `ProposalViewModelTest` (add `FakeFoodRepository`), `ProposalScreenRenderTest`,
`DescribeWithoutKeySessionTest` (constructor).

Behaviour (spec §4, §5):

- `Exact(food)` and `countedAsFor(food, unit) != null` → the row starts as `Worth.YourFood(food,
  countedAs)`, `foodId = food.id`, amount kept. It shows *From your foods* — a line of this screen's
  own, drawn for a row whose worth is `YourFood`, **above** the row's usual origin line (so a food
  whose figure is the label's still says *From the package label*, and one whose figure is an
  estimate still says its confidence) — and a button *Use the estimate (N kcal)*, N being the
  estimate's total at the current amount.
- `Exact(food)` and `countedAsFor == null` → the row starts as the estimate; line *Your %1$s is
  counted in %2$s. Say how many %2$s to use its figures.* (unit words: *grams*, or the food's unit
  name) with **Count it in %2$s** → unit becomes the food's (`g`, or `perUnit.unitName ?: portion`),
  worth `YourFood`, `foodId` set, **amountText ""**.
- `Close(food)` → the row starts as the estimate; line *Use your %1$s?* One tap applies the Exact
  rules above for that food (including the unit switch sentence when its unit does not fit); the row's
  `name` becomes the food's name.
- *Use the estimate* on a row using his food → `Worth.Estimated` from the kept `ProposedItem`,
  `foodId = null`; the amount is kept when the unit did not change, otherwise the estimate's amount
  and unit return. A second tap on *Use your …* goes back.
- A worth typed over a `YourFood` row keeps `foodId` (Task 4's rule); *Use the estimate* is still
  offered.
- The describe request is **not** touched: matching reads nothing until the reply is in.

- [ ] **Step 1: failing tests** (`ProposalViewModelTest`, fake foods in the fake repository):
  `an exact match takes his food's figures and keeps the described amount` (Pita: 250, `TYPED`,
  `foodId` = pita's); `one tap uses the estimate and one tap goes back` (165 `AI_ESTIMATE` MEDIUM ↔ 250);
  `his food counted in another unit leaves the estimate and offers the switch` (bun: 150 estimate;
  after the switch, amountText "" and the row blocked; typing "60" → 162 `TYPED` from the food);
  `a close match is offered, never applied by itself` (Yoghurt → Close(Greek yoghurt), row still the
  estimate until the tap); `a hidden food is not matched` (not in `observeOffered`); `no food is read
  before the answer arrives` (the fake counts `observeOffered` subscriptions: 0 after `describe` while
  the estimator is suspended); `nothing about his foods reaches the estimator` (the fake estimator
  records its arguments: exactly the words).
  Render: *From your foods* shown on the Pita row; *Use the estimate (165 kcal)* present; the bun row
  shows the counted-in-grams sentence and **Count it in grams**.
- [ ] **Steps 2–4:** FAIL, implement, PASS; full suite and lint.
- [ ] **Step 5: commit** `feat: a described item uses his own food when it is the same one, and says so`.

---

## Task 6 — A food is taught the worth itself, not one worked back from a rounded row

Separable: if it is dropped, everything above still works and the food learns a figure worked back
from the whole-number row, as today.

**Files:** `data/food/LoggedFoods.kt` (`attach(item, …, taught: FoodFacts? = null)` — when given,
it **replaces** the derived facts in the one offer, exactly as `labelPer100g` does, and a row with a
`foodId` still returns early and teaches nothing), `domain/amount/ItemToLog.kt`
(`fun ItemToLog.teaches(): FoodFacts?` — null for `YourFood` or any row with a `foodId`; per 100 g →
`FoodFacts(per100g = …)`; per 100 ml → `FoodFacts(perUnit = PerUnit("ml", rate / 100, …))`; per one
→ `FoodFacts(perUnit = PerUnit(unit, rate, …))`; provenance `AI_ESTIMATE`+confidence or `TYPED`,
`setAtMillis = 0` as the label path uses), `ui/screen/day/DayViewModel.kt` (`logMeal` and
`logMealAndChoose` take `List<Pair<FoodItem, FoodFacts?>>` — or a small `ToLog` data class — and
`writeMeal` attaches each with its taught facts), `ProposalViewModel.accepted()` returns that shape,
`ui/nav/MetaSelfNavHost.kt`. Tests: `LoggedFoodsTest`, `ItemToLogTest`, `DayViewModelTest` (its two
`logMeal` call sites), `ProposalViewModelTest`.

- [ ] **Step 1: failing tests:** `LoggedFoodsTest` — `a described item teaches its food the worth
  itself` (a per-100 g estimate of butter at 717.4 kcal logged at 7 g: the food's per-100 g kcal is
  717.4, not 714.3 worked back from the 50-kcal row); `a row carrying its food teaches it nothing`
  (foodId set + taught facts → the fake repository's `findOrCreate` is never called);
  `an estimate still cannot replace a figure he typed` (food with TYPED per 100 g; offered
  `AI_ESTIMATE` → unchanged, no D45 line); `ItemToLogTest` — the three shapes above, and
  `his own food's row teaches nothing`.
- [ ] **Steps 2–4:** FAIL, implement, PASS; full suite and lint.
- [ ] **Step 5: commit** `feat: a described food learns the figures it was described with, unrounded`.

> The butter figures are invented to make the rounding visible: 717.4 × 0.07 = 50.218 → 50 kcal;
> 50 ÷ 7 × 100 = 714.29. Compute them again when writing the test (CLAUDE.md rule 6).

---

## Task 7 — The repeat screen's one-day adjuster takes a typed amount

**Files:** `ui/screen/repeat/RepeatUiState.kt` (`Adjusting` gains `typed: Map<Long, String>` — the
text in each part's box, seeded with `Portions.format(component.amount)`; `blockedBy: Long?`, the
first part whose box is not a usable amount; `adjusted`/`totalKcal` read rows as now),
`RepeatViewModel.kt` (`setComponentAmount(componentId, text: String)` replaces the `Double` version:
stores the text, and updates the row's amount only when the text is a positive believable amount for
its `countedAs` via `BelievableAmount.amountEaten`; `stepComponent(componentId, by)` for UNITS parts,
never below 1; `adjusted()` returns null while `blockedBy != null`), `RepeatScreen.kt` (`Adjuster`:
the Less / As it was / More row becomes the shared amount box with − / + for a counted part, the
part's unit beside it, and the *At most …* sentence past the ceiling; Remove stays; *Log it*
disabled while blocked), `domain/repeat/AdjustedItem.kt` (delete `scaledBy`; keep `withAmount` and
`canBeAdjusted`, which *Correct this item* uses), `domain/portion/Portions.kt` (delete `LESS`,
`AS_IT_WAS`, `MORE`), `res/values/strings.xml` (delete `propose_less`, `propose_as_described`,
`propose_more`, `repeat_as_it_was`), `ui/nav/MetaSelfNavHost.kt`, `test/.../sim/SimulatedApp.kt`.
Tests: `RepeatViewModelTest`, `RepeatUiStateTest`, `RepeatScreenRenderTest`, `AdjustedItemTest`
(drop the `scaledBy` cases).

**Shared control:** move the amount box drawn in Task 3 into `ui/portion/AmountBox.kt` (a
composable taking text, unit words, whether counted, most, and callbacks) and use it on both
screens. Task 8 uses it too.

- [ ] **Step 1: failing tests:** `each part opens holding the meal's own amount` (a salad with
  cucumber 2 portions and oil 10 g: boxes "2" and "10"); `typing an amount changes that part for today
  and the meal not at all` (`asDefined` unchanged; `adjusted` true); `typing the meal's amount back
  is not an adjustment` (`adjusted` false); `a blank box blocks logging and names the part`;
  `plus and minus step a counted part and never go below one`; render: no *Less*, *As it was* or
  *More*; the cucumber box is an `EditableText` holding "2".
- [ ] **Steps 2–4:** FAIL, implement, PASS; full suite **and** `:app:lintDebug` (the deleted strings
  must have no remaining reference).
- [ ] **Step 5: commit** `feat: a built meal, just for today, takes a typed amount for each part`.

---

## Task 8 — A part already in a meal can have its amount changed (#4)

**Files:** `ui/screen/mealbuilder/MealBuilderUiState.kt` (`Adding` gains `changing: Long?` — the
component id when the panel is open for a part already in), `MealBuilderViewModel.kt`
(`beginChanging(componentId)` opens `Adding(food = component.food, countedAs = component.countedAs,
amount = Portions.format(component.amount), changing = component.id)`; `confirmAdding()` writes
`meals.put(id, food.id, amount, countedAs)` in both cases — `put` already updates in place;
`beginChangingFood(foodId)` for a food named in `alreadyIn`), `MealBuilderScreen.kt` (tapping a
part's row opens it — the row's name and amount area is clickable, Up/Down/Remove stay; the panel's
button reads **Change it** when `changing != null`; each name in the *already in this meal* sentence
is tappable and opens the same panel), `res/values/strings.xml` (`builder_change_it`). Tests:
`MealBuilderViewModelTest`, `MealBuilderUiStateTest`, `MealBuilderScreenRenderTest`,
`FakeSavedMealRepository` if it does not already upsert.

- [ ] **Step 1: failing tests:** `tapping a part opens its own amount and way of counting` (cucumber
  2 portions → panel holds "2", UNITS); `changing it keeps the part where it was` (three parts,
  change the middle one to "3": order unchanged, amount 3, still one cucumber part);
  `a part can be switched to grams only if its food can be weighed` (the reason sentence shows
  otherwise — existing `cannotWeigh`); `a food already in the meal, found by the search, opens that
  part` (search "cucum" → `alreadyIn` → `beginChangingFood` → panel for the part);
  `the panel's box follows the same ceiling` ("101" portions → *At most 100 at a time.*);
  render: the part row is clickable; the panel button reads *Change it*.
- [ ] **Steps 2–4:** FAIL, implement, PASS; full suite and lint.
- [ ] **Step 5: commit** `feat: a part already in a meal can have its amount changed` (trailer:
  `Refs #4` only).

---

## Task 9 — Verify the whole, and hand over

**Files:** none new, unless a check below fails.

- [ ] `~/bin/gradlew-safe :app:testDebugUnitTest > /tmp/…/test.log 2>&1; echo $?` → 0; the skip list
  is exactly the eight Room classes.
- [ ] `~/bin/gradlew-safe :app:lintDebug` → 0. `~/bin/gradlew-safe :app:assembleDebug` → 0.
- [ ] `git grep -n "PortionScale\|PortionControl\|Portions.LESS\|Portions.MORE\|propose_less\|repeat_as_it_was"`
  → nothing.
- [ ] `git diff main --stat -- app/schemas app/src/main/java/com/metaself/app/data/day app/src/main/java/com/metaself/app/data/food/FoodEntities.kt`
  → empty (the no-schema promise).
- [ ] Re-read `privacy.html` §*What leaves your phone*: still true word for word; `EstimatePromptTest`'s
  *NOTHING about the owner is sent* unchanged and green.
- [ ] Re-read every comment and KDoc touched for a figure that no longer matches the code beneath it,
  and every test that forbids a word (`Less`, `More`) for whether it still exercises a screen that
  could show it (CLAUDE.md: vacuous assertions).
- [ ] Version bump and release notes are the finishing step's, not this plan's.

**Phone checks owed (release build via `~/bin/ms-release`, by hand):**
1. Describe "a 200 g burger and a bun": the burger row shows 200 g and a per-100 g line; the bun row
   shows 1 bun and a per-bun line; no grams invented for the bun.
2. Type 150 into the burger's box: its total moves, its worth line does not.
3. Describe a food he already has by its exact name: *From your foods*, and *Use the estimate* works
   both ways.
4. A food he has counted in grams, described as a piece: the sentence, the switch, the empty box.
5. A close match line appears and does nothing until tapped.
6. *Change* a worth figure: the origin line goes; saved, the day shows the typed total.
7. Repeat → a built meal → *Just for today*: typed amounts, −/+ on counted parts.
8. Meal builder: tap a part, change its amount, it stays in place.
9. How the proposal row wraps on the phone's real width (render tests cannot measure it).
