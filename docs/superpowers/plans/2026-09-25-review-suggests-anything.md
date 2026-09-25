# The review may suggest anything but the brand, in the boxes — Implementation Plan

**Goal:** a review (D54) may propose the name, the unit (naming one where there is none, or a better
one), every figure of both groups whatever its source, and what one weighs — never the brand — each
with a reason; its suggestions go straight into the page's boxes, pending in the teal accent, each
with its reason and *Back to …* / *Clear*; **Accept all** / **Dismiss all**; an accepted weight is
stored as an estimate.

**Decision:** the owner's, 2026-09-25. Recorded as D54 §12 in
`docs/superpowers/specs/2026-09-23-nutritionist-review-design.md`, with pointers in D55 §2 and D56.
No public issue exists for it yet.

**Red lines (stop and report if crossed):**

- **No schema, migration, DAO statement, backup-format or rank change.** `GramsPerUnit` already
  carries source, rank and confidence; `AI_ESTIMATE` is already a value it holds. If a step seems to
  need a new column or statement, the design is wrong — stop.
- **Nothing computes a weight.** No arithmetic in the app produces `gramsPerUnit`; the only new way
  in is a model's proposal accepted by a tap.
- **Nothing a model wrote is stored without Accept all.** Save with anything pending is refused.
- **Nothing new is sent.** `ReviewPromptTest`'s *nothing else is sent* test must pass unchanged in
  what it forbids; `weightAsked` is never serialised.
- **A group all of whose suggestions were put back reaches the repository exactly as it would have
  with no review** — the stored doubles, so `Correction.Keep`, no statement.

**Branch:** `the-review-may-suggest-anything` (this plan and the spec are its first two commits).
Version at the end: **0.50.0** (versionCode +1) — a new behaviour, not a fix.

---

## The shape of the change

| Layer | File(s) | What changes |
|---|---|---|
| Domain types | `domain/ai/FoodReviewer.kt`, `domain/food/FactGroup.kt`, `domain/food/AcceptedGroup.kt` | `ReviewRequest.weightAsked` (not sent). `FoodReview` gains `name`, `unit`, `weight` suggestions; `Suggestion` is unchanged; the per-one bundle wraps one with the proposed unit. `FactGroup` gains `WEIGHT` (KDoc rewritten: *a review may propose it; nothing computes it*). |
| Prompt | `data/ai/ReviewPrompt.kt` | Instructions per §12.5; schema gains `name`, `unit_name`, `grams_per_unit`, all required and nullable; the weight sentences removed; the cross-check includes the weight; weight rule only when `weightAsked`. |
| Parse | `data/ai/ReviewResponse.kt` | Four items, each set aside whole (§12.4): name, per 100 g, per-one bundle, weight. Unusable = every changing item set aside. |
| Form state | `ui/food/FormReview.kt` | Replaced core: **pending boxes** (box → original text, suggested text, reason, item), Accept all, Dismiss all, Back per box / per bundle, typed-over, Undo after Accept. `ReviewedBox` generalised to eleven boxes (`FormBox`). |
| Form → facts | `domain/food/FoodForm.kt`, `domain/food/FormOrigins.kt` | `toFacts` stores an accepted weight as `AI_ESTIMATE` with its confidence; `FormOrigins` reports an accepted weight as such. `with(...)` writes name, unit and weight as well as figures. KDocs: *never computed*, not *never accepted*. |
| Drawing | `ui/food/ReviewBlock.kt`, `ui/food/FoodFormParts.kt`, `ui/food/ReviewWording.kt`, `ui/screen/food/FoodPageScreen.kt`, `ui/screen/mealbuilder/MealBuilderScreen.kt` | Change list and Apply / Keep mine removed. Pending boxes in `changedBoxColors()` (renamed `suggestedBoxColors()`), reason caption + Back button beneath, one per row while a group is pending; Accept all / Dismiss all under the outcome line and in the refused-Save slot; outcome wording. |
| View models | `FoodPageViewModel.kt`, `MealBuilderViewModel.kt` | `acceptAll`, `dismissAll`, `putBack(box)`, `undo`; Save / Make it refused while pending; Review not offered while pending; `weightAsked` false in the builder. |
| Words | `res/values/strings.xml` | New: pending state description, Back / Clear, Accept all / Dismiss all, the refusal sentence, outcome rows, *N changes accepted*, set-aside lines for name / weight. Changed: `foods_weight_never_guessed` (§12.7's text). Removed: `review_apply`, `review_keep_mine`, `review_changed_box`. |
| Simulated app | `test/.../sim/*`, `FakeFoodReviewer.kt` | Whatever reads the old Apply flow is moved to Accept all. |

No change to `RoomFoodRepository`, `FoodDao`, `Correction`, the backup, `privacy.html` or
`terms.html` (spec §12.2 and §12.11 say why).

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

### Step 2 — Pending, accepted, put back, typed over (pure; JUnit 5)

**Build:** `FormReview` rewritten around pending boxes. `FormBox` (eleven values). `answered()`
writes every suggestion into the form and records `Pending(original, suggested, reason, item)` per
box; `putBack(box)` (bundle-aware); `acceptAll()`; `dismissAll()`; `typed(before, after)`;
`undo()`; `hasPending`; the count for the outcome line.

**Tests (`FormReviewTest`, rewritten; JUnit 5):**

- An answer writes each suggestion into its box; the original text is kept exactly (a stored
  8.571428571428571 shown as 8.57 is kept as the text *8.57*, so putting it back hands Save the
  stored double — asserted through `FoodForm.toFacts` with the stored facts).
- `putBack` restores exactly that box's original; *Clear* restores empty.
- Bundle: putting back the unit restores the unit, the four per-one boxes and a bundled weight; a
  bundle figure has no own put-back (`putBack` on it is a no-op); a bundle box typed over is left as
  typed when the unit is put back.
- `dismissAll` equals putting every pending box back; accepted groups from an earlier accept stay.
- `acceptAll`: no box pending; a group is accepted iff at least one of its boxes was taken;
  `keptFrom` is the sent source when any of its four figures was put back or echoed, null when all
  four came from the model; a group with every suggestion put back is not accepted; the weight
  accepted with its own confidence.
- Typing into a pending box: it stops pending; nothing else moves. Typing the name or brand while
  pending moves nothing else. While **asking**: typing in a group withdraws that group; a new name or
  brand withdraws the whole answer (*no suggestions left*).
- `undo` after accept: every box accept took goes back to pending, except one typed since; accepted
  groups revert to what they were.
- `hasPending` true blocks a new `asked()` (the view model checks it).

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
- `FormOrigins` after an accepted weight says `AI_ESTIMATE` with its confidence (what a second
  review is told).

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
   an accepted group rolls **everything** back (group still `LABEL`, name unchanged). The PR is not
   merged until CI shows these ran and passed — locally the eight known classes skip and nothing
   else does.

### Step 4 — The boxes drawn (Robolectric render tests; JUnit 4)

**Build:** `suggestedBoxColors()` (the teal family, renamed from `changedBoxColors()`; no new colour
token); a pending box's reason caption and its Back / Clear `TextButton` under it, the button's
content description naming the box (`figureSaid`); the pending state description; a group with a
pending box drawn one per row, otherwise D55's two by two; the unit box's bundle button; Accept all /
Dismiss all under the outcome line; the change list, Apply these changes and Keep mine removed;
the outcome rows; *N changes accepted — not saved yet…* with Undo; the refused-Save sentence with
the two buttons above Save; the weight caption's new words. The same in *Make a food*, without a
weight.

**Tests (`FoodPageReviewRenderTest`, `FoodPageScreenRenderTest`, `MealBuilderScreenRenderTest`,
`ReviewWordingTest`; JUnit 4 + Robolectric, 320 dp canvas — no width, wrap or touch-size
assertions):**

- Pending: the suggested value is in the box (read through `EditableText`); the reason text and
  *Back to 19.6* follow it in order; an empty box's button reads *Clear*; the state description is
  present; a button's content description names its box.
- No *Apply these changes*, no *Keep mine*, no *old → new* line anywhere.
- Accept all: no state description left, no reasons, the accepted line with Undo.
- Dismiss all: boxes read their originals; no pending marks.
- Bundle: the per-one figure boxes have no Back of their own; the unit box's does.
- Save with pending: the sentence and both buttons appear above Save; the fake repository recorded
  no write.
- Review button absent while pending.
- One per row while pending: assert relative order (each box's reason directly after it), never
  sizes.
- Weight caption reads §12.7's text; the ml caption unchanged.
- *Make a food*: the same pending behaviour for name, unit and figures; no weight box, and no weight
  suggestion drawn even when the fake reviewer returns one.
- Dark scheme: one render with `uiMode = night` confirming the pending box takes `tertiary` /
  `tertiaryContainer` from the dark scheme (colour read from the theme, not asserted as hex).
  `InkLadderTest` gains no pair: the pairs are unchanged and already measured — assert that
  `suggestedBoxColors()` still uses exactly those tokens.

### Step 5 — The view models (JUnit 4 where Robolectric is needed, else JUnit 5)

**Build:** `FoodPageViewModel` and `MealBuilderViewModel`: `acceptAll`, `dismissAll`,
`putBack(box)`, `undoReview`; `save` / `createFood` refused while pending; `review` ignored while
pending; `weightAsked` from the page (`!form.perHundredMl`) and false in the builder.

**Tests (`FoodPageViewModelTest`, `MealBuilderViewModelTest`, the two `…Session` tests):**

- The spec's invented example (§12.4–§12.7) end to end through the fake reviewer and fake repository: after Back on the
  name and Accept all, Save stores per 100 g `AI_ESTIMATE`/`MEDIUM`, per tablespoon
  `AI_ESTIMATE`/`MEDIUM` under the new unit, weight 15 `AI_ESTIMATE`/`MEDIUM`, name unchanged.
- Dismiss all then Save: the stored food is byte-identical (every group `Keep`).
- An accepted name colliding with another food: Save refused with *Another food is already called
  “…”…*, nothing stored, boxes and acceptance kept; Undo returns the name to pending.
- Save while pending: refused, nothing stored; Accept all from the refusal slot, then Save, stores.
- An answer landing after the page closed: dropped (existing test kept).
- Builder: an accepted name matching an existing food → `findOrCreate` finds it and offers the
  groups through the guards (existing D45 notice test extended); no weight ever written.

### Step 6 — Version, sim harness, spec check

`versionName 0.50.0`, versionCode +1. The simulated app (`test/.../sim`) moved from Apply to Accept
all. Full `:app:testDebugUnitTest`, `:app:lintDebug`, `~/bin/ms-release` for the owner's install.
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
  reason, *Back to …*, and the estimate label. If it proves too easy, the next lever is a per-group
  accept, which §11.1 rejected once.
- **Layout under pending.** Eight or more reasons and buttons make the page long; one per row is
  chosen for readability. Robolectric cannot measure it (320 dp, no font), so it is checked on the
  phone.
- **Undo after Accept across typing.** The existing `Applied` bookkeeping is generalised from eight
  boxes to eleven, including the bundle — the most intricate pure code in the change. Step 2's tests
  are the net; write them first.
- **`FactGroup.WEIGHT` in exhaustive `when`s.** Eleven `when (group)` / `FactGroup.values()` sites by a grep on 2026-09-25; each must decide what a weight
  means there (most: nothing). Compiler-found, but read each one.
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
