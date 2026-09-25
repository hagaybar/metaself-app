# The review may suggest anything but the brand, in the boxes — Implementation Plan

**Goal:** a review (D54) may propose the name, the unit (naming one where there is none, or a better
one), every figure of both groups whatever its source, and what one weighs — never the brand — each
with a reason; its suggestions go straight into the page's boxes, pending in the teal accent, each
with its reason and *Back to …* / *Clear*; while any is pending, **Accept changes and save** (one
tap) and **Cancel** (back to the page as it was when Review was pressed) replace Save and Leave it
alone; an accepted weight is stored as an estimate.

**Amended 2026-09-25 with the owner's settlement of the spec's two open questions** (D54 §12.6,
§12.12): no *accepted, not saved* state and no Undo; one tap accepts and saves; a refused
accept-and-save keeps every suggestion pending.

**Decision:** the owner's, 2026-09-25. Recorded as D54 §12 in
`docs/superpowers/specs/2026-09-23-nutritionist-review-design.md`, with pointers in D55 §2 and D56.
No public issue exists for it yet.

**Red lines (stop and report if crossed):**

- **No schema, migration, DAO statement, backup-format or rank change.** `GramsPerUnit` already
  carries source, rank and confidence; `AI_ESTIMATE` is already a value it holds. If a step seems to
  need a new column or statement, the design is wrong — stop.
- **Nothing computes a weight.** No arithmetic in the app produces `gramsPerUnit`; the only new way
  in is a model's proposal accepted by a tap.
- **Nothing a model wrote is stored but by Accept changes and save.** Plain Save is not drawn while
  anything is pending, and the view model ignores it then too.
- **Nothing new is sent.** `ReviewPromptTest`'s *nothing else is sent* test must pass unchanged in
  what it forbids; `weightAsked` is never serialised.
- **A group all of whose suggestions were put back reaches the repository exactly as it would have
  with no review** — the stored doubles, so `Correction.Keep`, no statement. The one relabel of an
  unchanged figure is the weight's, downward, and only when he accepted it (§12.7).

**Branch:** `the-review-may-suggest-anything` (this plan and the spec are its first two commits).
Version at the end: **0.50.0** (versionCode +1) — a new behaviour, not a fix.

---

## The shape of the change

| Layer | File(s) | What changes |
|---|---|---|
| Domain types | `domain/ai/FoodReviewer.kt`, `domain/food/FactGroup.kt`, `domain/food/AcceptedGroup.kt` | `ReviewRequest.weightAsked` (not sent). `FoodReview` gains `name`, `unit`, `weight` suggestions; `Suggestion` gains `heldSource` (the group's source as sent, for the weakest-member rule worked out when he accepts). Set-aside lines are per `ReviewItem` (name, per 100 g, per one, weight). `FactGroup` stays two values; the weight's acceptance travels as its own confidence (`toFacts(weightEstimate = …)`), which touches fewer exhaustive `when`s than a third group. |
| Prompt | `data/ai/ReviewPrompt.kt` | Instructions per §12.5; schema gains `name`, `unit_name`, `grams_per_unit`, all required and nullable; the weight sentences removed; the cross-check includes the weight; weight rule only when `weightAsked`. |
| Parse | `data/ai/ReviewResponse.kt` | Four items, each set aside whole (§12.4): name, per 100 g, per-one bundle, weight. Unusable = every changing item set aside. |
| Form state | `ui/food/FormReview.kt` | Replaced core: **pending boxes** (`FormBox`, eleven values → original text, suggested text, reason, bundled), the page as it stood when Review was pressed (for Cancel), Back per box / per bundle, typed-over, and `accepted()` — what Accept changes and save hands `toFacts`. `Applied`, `apply`, `undo`, `ReviewedBox` go. |
| Form → facts | `domain/food/FoodForm.kt`, `domain/food/FormOrigins.kt`, `domain/food/FoodFacts.kt`, `domain/food/FactGroup.kt` | `toFacts(weightEstimate)` stores an accepted weight as `AI_ESTIMATE` with its confidence; `FormOrigins` takes the same. KDocs: *never computed*, not *never accepted*. |
| Drawing | `ui/food/ReviewBlock.kt`, `ui/food/FoodFormParts.kt`, `ui/food/ReviewWording.kt`, `ui/screen/food/FoodPageScreen.kt`, `ui/screen/mealbuilder/MealBuilderScreen.kt` | Change list, Apply / Keep mine / Undo removed. Pending boxes in the teal family (`suggestedBoxColors()`, renamed from `changedBoxColors()`), reason caption + Back button beneath, one per row while a group is pending; at the foot, **Accept changes and save** / **Cancel** in place of Save / Leave it alone while pending (*…and make it* / **Cancel** in the builder); outcome wording. The builder's private `Field` copy gives way to the shared one. |
| View models | `FoodPageViewModel.kt`, `MealBuilderViewModel.kt` | `putBack(box)`, `acceptAndSave()` / `acceptAndCreate()`, `cancelReview()`; plain Save / Make it ignored while pending; Review ignored while pending; `weightAsked` false in the builder. |
| Words | `res/values/strings.xml` | New: pending state description, Back to / Clear and their spoken forms, Accept changes and save / …and make it, outcome rows, set-aside lines for name / weight. Changed: `foods_weight_never_guessed` (§12.7's text). Removed: `review_apply`, `review_keep_mine`, `review_undo`, `review_applied*`, `review_changed_box`, `review_changes_applied`. |
| Simulated app | `test/.../sim/*`, `LiveFoodPage.kt` | Wiring moved to the new actions. |

No change to `RoomFoodRepository`, `FoodDao`, the backup, `privacy.html` or `terms.html` (spec §12.2
and §12.11 say why). **`Correction` gains exactly one case, for the weight only** (spec §12.7): an
arriving `AI_ESTIMATE` weight over the same figure held at a higher rank is written, not kept. The
form produces an `AI_ESTIMATE` weight only when he accepted one — a weight suggestion, or a weight
echoed in an accepted unit bundle — so that is the only way the case fires; it is pinned in
`CorrectionTest`, `FoodFormTest` and, in CI, `RoomFoodRepositoryTest`.

---

## Steps, in order — tests first in every step

### Step 1 — The reply's shape and its reading (pure; JUnit 5)

**Build:** `ReviewPrompt` schema and instructions; `ReviewRequest.weightAsked`; `ReviewResponse`
reads four items; `FoodReview` carries them.

**Tests (JUnit 5 + Truth), in `ReviewPromptTest`, `ReviewResponseTest`, `ReviewInMillilitresTest`:**

- The schema's `required` is exactly the seven fields; `name`, `unit_name`, `grams_per_unit` are
  `anyOf` object / null, `additionalProperties: false`, every inner field required.
- The user message is byte-for-byte what it was for the same form (nothing new sent); `weightAsked`
  appears nowhere in the body.
- Instructions: contain the name, unit and weight rules; no longer contain *never change it, state it
  or guess it* or *never name a unit*; with `weightAsked` false, say to return `grams_per_unit` null;
  the cross-check names the weight as a candidate.
- Name: echo (trimmed) kept; a change with a reason read; with no reason set aside; a value
  `FoodKeys.nameKey` refuses (only punctuation) set aside.
- Per 100 g: every existing §3 / §8 / §9 case still passes unchanged (the regression net).
- Bundle: unit `null` → figures judged against held, as before; unit echo (display-name equal) kept;
  for an ml food, *ml*, *millilitres* and *100 ml* all kept; a naming with full figures → a fill at
  per-one ceilings; a naming with `per_unit: null` → set aside; a proposed *g*, *100 g*, *kg*,
  *portion* → set aside; a rename on the same side of ml with figures echoed → a rename with the
  figures kept exactly as held; a rename to *ml* → figures a fill judged by per-100 ceilings and
  rounded at that scale; a rename from ml to *glass* → per-one ceilings; `per_unit` with no unit
  anywhere → set aside (was silently ignored — this test is new and changes behaviour).
- Rename with a held weight: weight echo → bundle used, weight kept; weight `null` → bundle **and**
  weight set aside; rename to ml → any weight set aside, held weight untouched.
- Weight: `weightAsked` false → ignored whatever it says, never a set-aside line; echo at the
  model's precision kept (§9.1 cases with a grams figure); 0, negative, 5000.1 → set aside; a fill
  with no unit held or proposed → set aside; rounded to one decimal.
- Unusable: every changing item set aside → `Unusable`; one usable item → `Proposed`.
- The spec's invented example of §12.4 (figures invented there) end to end: eight boxes suggested, correct items.

### Step 2 — Pending, put back, typed over, accepted (pure; JUnit 5)

**Build:** `FormReview` rewritten around pending boxes. `asked(form)` keeps the page as it stands;
`answered(answer, raw, form)` writes every suggestion into the form and records a pending box for
each; `putBack(form, box)` (bundle-aware); `typed(before, after)`; `cancel()` (the page as it stood
when Review was pressed); `accepted(form)` → the accepted groups and the weight's confidence;
`hasPending`, `pendingCount`.

**Tests (`FormReviewTest`, rewritten; JUnit 5):**

- An answer writes each suggestion into its box and keeps the box's text as it stood, exactly (a
  stored figure shown rounded is kept as that text, so putting it back hands Save the stored double
  — asserted through `FoodForm.toFacts` with the stored facts).
- `putBack` restores exactly that box's text; an empty box goes back to empty.
- Bundle: putting back the unit restores the unit, the four per-one boxes and a bundled weight; a
  bundle figure has no put-back of its own while the unit is pending; a bundle box typed over is left
  as typed when the unit is put back.
- Typing into a pending box: it stops pending; nothing else moves. Typing the name or brand while
  pending moves nothing. While **asking**: typing in a group withdraws that group; a new name or
  brand withdraws the whole answer (*no suggestions left*).
- `cancel` returns the form as it stood when Review was pressed, including typing done before, and
  drops typing done after; the review is gone.
- `accepted`: a group is accepted iff at least one of its boxes is still pending; `keptFrom` is the
  sent source when any of its four figures holds the text it had when the answer arrived (put back
  or echoed), null when all four came from the model or the group was filled; a group whose every
  suggestion was put back or typed over is not accepted; the weight carries its own confidence.

### Step 3 — What Save hands the repository (pure; JUnit 5) — **the red step**

**Build:** `FoodForm.toFacts` takes the accepted weight; `FormOrigins` reports it; the KDocs of
`FoodForm`, `FoodFacts.GramsPerUnit`, `FactGroup`, `FormOrigins` say *never computed; typed, a
packet, or a model's proposal he accepted*.

**Tests (`FoodFormTest`, `FormOriginsTest`, `CorrectionTest`, new `AcceptedGroupTest` cases):**

- Accepted per 100 g over a `LABEL`, one figure changed → `AI_ESTIMATE` with the review's confidence;
  `Correction.plan` → `Replace`.
- Accepted group whose kept figures were `REPEATED` → stored `REPEATED`, no confidence (unchanged rule).
- A group all put back, stored as `LABEL` → `toFacts` hands the stored doubles; `Correction.plan` →
  `Keep` (no statement). Same for a stored `TYPED` group and a stored weight.
- Accepted weight over a `TYPED` weight → `GramsPerUnit(…, AI_ESTIMATE, confidence)`; plan
  `Replace`. A weight never suggested → `TYPED` as before, or the stored one untouched.
- Unit renamed and accepted, figures kept → per-one group `AI_ESTIMATE` (label respelled becomes an
  estimate), plan `Replace` (unit differs).
- Rename to ml accepted: the per-100 ml figures are stored ÷ 100 (decimal shift), per D56.
- `LoggedFrom`: a food with `LABEL` per 100 g and `AI_ESTIMATE` weight, counted in units, logs as
  `AI_ESTIMATE` (the weaker) — a test that pins D4 through an estimated weight.
- `Correction.plan`: an arriving `AI_ESTIMATE` weight over the same figure held `TYPED` or `LABEL`
  is `Replace`; over a `REPEATED` one, or an estimate, `Keep`. Groups unchanged.

**How the red writes are verified, given Room's tests only run in CI.** Three layers:

1. **Locally, pure:** `FoodForm.toFacts` + `Correction.plan` decide every statement the repository
   makes; the tests above pin both for every case in §12.7. `Correction` is the one place that
   decides *keep / clear / replace*, and `RoomFoodRepository.correct()` is unchanged.
2. **Locally, through the view model:** `FakeFoodRepository.correct()` uses the same
   `Correction.plan`, and its `rename` refuses collisions as Room's does. `FoodPageViewModelTest`
   cases (step 5) assert the stored provenance after Save through the fake.
3. **In CI, against Room:** new `RoomFoodRepositoryTest` cases, which skip locally by design
   (`assumeSqliteRuntime()`, linux/aarch64) and run in CI, where *No test skipped* fails the run on
   any skip: an accepted estimate over a label group replaces it with `AI_ESTIMATE`; an untouched
   label group keeps source, confidence **and** `per100gSetAtMillis`; an `AI_ESTIMATE` weight
   replaces a `TYPED` weight and reads back with its confidence; `saveForm` with a colliding name and
   an accepted group rolls **everything** back (group still `LABEL`, name unchanged); a weight
   echoed under an accepted rename is relabelled `AI_ESTIMATE` though its figure is unchanged. They
   go into `RoomFoodRepositoryTest`, which already stands aside by `assumeSqliteRuntime()`, so the
   list of eight classes that skip locally does not grow. The PR is not
   merged until CI shows these ran and passed — locally the eight known classes skip and nothing
   else does.

### Step 4 — The boxes drawn (Robolectric render tests; JUnit 4)

**Build:** `suggestedBoxColors()` (the teal family, renamed from `changedBoxColors()`; no new colour
token); a pending box's reason caption and its Back / Clear `TextButton` under it, the button's
content description naming the box; the pending state description; a group with a pending box drawn
one per row, otherwise D55's two by two; the unit box's bundle button; at the foot, Accept changes
and save / Cancel in place of Save / Leave it alone while pending; the change list, Apply these
changes, Keep mine and Undo removed; the outcome rows; the weight caption's new words. The same in
*Make a food*, without a weight.

**Tests (`FoodPageReviewRenderTest`, `FoodPageScreenRenderTest`, `MealBuilderScreenRenderTest`,
`ReviewWordingTest`; JUnit 4 + Robolectric, 320 dp canvas — no width, wrap or touch-size
assertions):**

- Pending: the suggested value is in the box (read through `EditableText`); the reason text and
  *Back to 19.6* follow it in order; an empty box's button reads *Clear*; the state description is
  present; a button's content description names its box.
- No *Apply these changes*, no *Keep mine*, no *Undo*, no *old → new* line anywhere.
- While pending: *Accept changes and save* and *Cancel* are drawn; *Save* and *Leave it alone* are
  not. With nothing pending: the reverse.
- Bundle: the per-one figure boxes have no Back of their own; the unit box's does.
- Review button absent while pending.
- One per row while pending: assert relative order (each box's reason directly after it), never
  sizes.
- Weight caption reads §12.7's text; the ml caption unchanged.
- *Make a food*: the same pending behaviour for name, unit and figures; no weight box, and no weight
  suggestion drawn even when the fake reviewer returns one.
- Colour: `InkLadderTest` gains no pair — the tokens are the ones it already measures in both
  schemes; the render tests assert the pending state description, not a hex.

### Step 5 — The view models

**Build:** `FoodPageViewModel` and `MealBuilderViewModel`: `putBack(box)`, `acceptAndSave()` /
`acceptAndCreate()`, `cancelReview()`; plain `save` / `createFood` ignored while pending; `review`
ignored while pending; the builder's request says no weight box.

**Tests (`FoodPageViewModelTest`, `MealBuilderViewModelTest`, the `…Session` tests):**

- The spec's invented example (§12.4–§12.7) end to end through the fake reviewer and fake
  repository: after Back on the name and Accept changes and save, per 100 g `AI_ESTIMATE`/`MEDIUM`,
  per tablespoon `AI_ESTIMATE`/`MEDIUM` under the new unit, weight 15 `AI_ESTIMATE`/`MEDIUM`, name
  unchanged; the page closes.
- Every suggestion put back, then Save: the stored food is unchanged (every group `Keep`).
- Cancel: the form is exactly as when Review was pressed; nothing stored.
- An accepted name another food holds: refused with *Another food is already called “…”…*, nothing
  stored, every suggestion still pending.
- A Back that leaves a group half filled, then Accept changes and save: refused by the form's error,
  suggestions still pending.
- Plain Save while pending: nothing happens.
- Builder: accept-and-make stores the estimates; no weight ever asked or written.

### Step 6 — Version, sim harness, spec check

`versionName 0.50.0`, versionCode +1. The simulated app (`test/.../sim`) moved to the new actions. Full `:app:testDebugUnitTest`, `:app:lintDebug`, `~/bin/ms-release` for the owner's install.
Re-read every new string and KDoc against the repository's publishing rules (invented figures say
so; no frequency, no real food story).

---

## Commands

```
export ANDROID_HOME=/home/ubuntu/android-sdk
~/bin/gradlew-safe :app:testDebugUnitTest > /tmp/…/test.log 2>&1; echo $?
~/bin/gradlew-safe :app:lintDebug > /tmp/…/lint.log 2>&1; echo $?
~/bin/ms-release
```

Never piped; the exit code is read.

## Risks

- **The model's obedience on the bundle.** A rename without `per_unit`, or without the weight when
  one is held, is common model behaviour; the parse sets the bundle aside rather than trusting it,
  which may make renames rarer than the owner expects. Visible as a set-aside line and in *Show the
  model's answer*; watched on the phone.
- **A label replaced by one tap.** The owner chose this; the guard is the suggestion colour, the
  reason, *Back to …*, the words *Accept changes*, and the estimate label.
- **Layout under pending.** Eight or more reasons and buttons make the page long; one per row is
  chosen for readability. Robolectric cannot measure it (320 dp, no font), so it is checked on the
  phone.
- **The bundle across typing and put-back.** Eleven boxes, a bundle of up to six, and typing that
  takes one box out of it — the most intricate pure code in the change. Step 2's tests are the net;
  write them first.
- **The name suggestion vs. D54 §4's "a new name withdraws the answer".** That rule stays for a
  request in flight only; mixing the two in `FormReview.typed` is the likely bug. Tested both ways.
- **Schema size and strictness.** Three more nullable objects; OpenAI's strict mode accepts `anyOf`
  with null below the root (already relied on). A reply failing the new shape is *could not be
  understood*, as before. Checked against the live API once on the phone.

## What is not built

- A name collision warned about when the answer arrives (spec §12.10: refused at Save instead).
- A weight box in *Make a food*, and so any weight suggestion there.
- Per-figure provenance (would need a schema change; the weakest-member rule stands).
- Reviewing a food during a join (D54 §1, unchanged).

## Size

Roughly **1,500–2,000 lines changed, about half of them tests**: parse and prompt ~350 + ~450 tests;
`FormReview` ~350 + ~400 tests; form / origins ~80 + ~200 tests; drawing ~250 + ~300 render tests;
view models ~120 + ~250 tests; strings ~30. Six steps; one working session with a babysitter run of
six iterations, or two sessions by hand. No database work, which is what keeps it at that size.
