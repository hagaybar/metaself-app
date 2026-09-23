# Typed amounts, and his own foods used — D53

> Decided 2026-09-23 by the owner, on this repository's issues #1 (the old repository's #1: replace
> *Less / As described / More* with a real quantity) and #4 (the old repository's #17: a part already
> in a meal cannot have its amount changed). The decision comment on #1 is the contract; this file
> writes it down as a numbered decision and settles what the comment left to the build.
>
> Every figure below is invented to illustrate the rule beside it.

---

## Why there is a decision here at all

- **"A bit less than whatever the model guessed" is not a thing anyone knows.** What is known is
  the amount: 2 slices, 1 bun, 180 g. The three buttons scaled a guess by 0.75 or 1.5; a count could
  be nudged but not typed; an item whose portion carried no number had no control at all.
- **A described meal manufactured a fresh food every time.** Nothing resolved the model's answer
  against the foods already on the phone (D28 says so in as many words), so the figures he had typed
  or scanned for a food were ignored the moment he described it instead of picking it.
- **An estimate for an unstated amount invented a weight.** Asked about "a bun", the model answered
  in grams it had made up, and that weight then looked like any other number (the cost D34 recorded).
- **A saved meal's part could not be changed** except by removing it and putting it back.

---

## D53 — An item is what it is worth times how much was had; both are his to set, and his own foods are used

### 1. The item

Every item being logged from a description is **two things, each editable, neither derived from the
other**:

- **What it is worth** — four figures (calories, protein, carbohydrate, fat) **per 100 g**, **per
  100 ml**, or **per one** of a named piece (per bun, per slice, per cup).
- **How much was had** — a number and its unit: 200 g, 1 bun, 2 slices, 330 ml.

The total is one times the other, divided by 100 for a per-100 figure. **Changing either leaves the
other alone.** Typing 150 where the model said 200 changes the total and nothing about the worth;
typing a new calorie figure changes the total and nothing about the amount.

There is no *as described* to return to and no scaling of a scaled item: the worth is fixed until he
types over it, and the amount is simply the amount. (That answers the third of #1's questions, "does
retyping keep or reset *as described*": neither — the thing that was being scaled no longer exists.)

**The unit is not edited on its own.** A different unit needs a different worth, and working one out
from the other is a conversion this app does not have and may not invent (D4; the rule `Portions`
already records for kilograms, ounces and millilitres). The only unit change offered is to the unit
his own food is counted in (§5), where the worth comes with it.

**Rounding happens once.** The worth keeps decimals (it is a food's kind of figure: 0.5 g is kept as
0.5 g, as on the food form, D38). The row that goes on the day is whole calories and whole grams,
rounded once, figure by figure, when it is logged — the rounding `Logging` already does.

*Worked example, invented:* a beef burger worth 250 kcal, 18 g protein, 0 g carbohydrate, 20 g fat
per 100 g. At 200 g the row is 500 kcal · P 36 · C 0 · F 40. He types 150: 375 kcal · P 27 · C 0 ·
F 30. The worth line still reads *per 100 g: 250 kcal · P 18 · C 0 · F 20*.

### 2. What the model is asked for

The request still carries his words and nothing else (D16). The reply is pinned by a strict schema
in which **every field is required**:

| Field | What it holds |
|---|---|
| `name` | **The plain name of the food** — "Hamburger bun", "Cappuccino". No size, brand, cooking or quantity in it. |
| `detail` | Everything else worth saying about this item — "sesame, toasted", "large" — or an empty string. |
| `amount` | A number greater than zero. Always. |
| `unit` | **The unit he stated**, when he stated one ("a 200 g burger" → `g`; "330 ml of juice" → `ml`). **Otherwise the thing's natural piece** ("a bun" → `bun`, "two slices of pizza" → `slice`, "a cappuccino" → `cup`). |
| `figures_per` | `"100"` when the unit is grams or millilitres; `"1"` otherwise. |
| `kcal`, `protein_g`, `carbs_g`, `fat_g` | The worth, **per 100 of the unit or per one piece** as `figures_per` says — numbers, decimals allowed. Not the total. |
| `confidence` | LOW, MEDIUM or HIGH. |

And the rules the instructions add:

- **Never convert a stated amount and never invent a weight for an unstated one.** "A bun" is 1 bun
  at per-bun figures, not 60 g. The detail may say the size assumed in words ("large"), never in
  grams. **This amends D5**: the assumption shown is the piece and its detail, where it used to be a
  guessed weight.
- **A piece is named in the singular** ("slice", not "slices") and the name is kept plain, because
  both are what matching on the phone compares (§4, §5).
- **Confidence now describes the figures for one piece or 100 g**, the size of the piece assumed
  included — no longer "the amount more than the food". Once an unstated amount is always one
  natural piece, the doubt lives in what a piece is worth, and a confidence about an amount he can
  now type would describe nothing (D7 still holds: the confidence is shown and stored).
- **D35 is unchanged and now reads naturally**: a drink is one item in its usual serving, which is
  its natural piece.
- **D34 is unchanged in force**: an item with no positive amount or no unit is asked for again once,
  naming the items, and refused if still missing. The strict schema makes it rarer; the check stays
  because a schema is a request, not a proof.
- **Read strictly, as now.** An item missing a figure is dropped, not defaulted to zero; a reply that
  leaves nothing is unreadable. A worth figure that is not finite, is negative, or is past D42's
  ceiling for its basis (per 100: 1000 kcal, 110 g; per one: 5000 kcal, 500 g) drops that item the
  same way. An amount past D42's amount ceiling is **not** dropped: the row arrives with its amount
  box saying *At most 5000 g at a time.* (or *100*), and cannot be saved until he changes it —
  exactly as if he had typed it.

**The no-amount case stops for new meals.** That settles #1's first two questions: nothing is asked
for "when there is no unit", because a proposal can no longer arrive without one. **Rows already on
the record keep what they have** (D34's rule for them — correct it on the day — is unchanged).

### 3. Where every figure says it came from (D4)

**The row's source is the worth's source. The amount has no source and never changes one.** This is
D44's rule — changing the amount rescales the figures and is not a correction of them — applied to
every item, not only a label row.

| The worth | The row is stored as |
|---|---|
| The model's figures | `AI_ESTIMATE`, with the model's confidence — **whatever amount he types.** An estimate times his amount is still an estimate. |
| His food's own figures (§4) | The source that food's figure carries — `LABEL`, `TYPED`, `AI_ESTIMATE` with its confidence, or `REPEATED` — through `Logging.log`, so a figure computed from two of the food's facts carries **the weaker of the two** (as logging a food has always done). |
| Any figure he typed into the worth | `TYPED`, no confidence. **Typing one of the four makes all four his** — the source belongs to the row, not to each figure. D44 accepted the same cost for the same reason, and the alternative (a source per figure) is a schema change nothing else needs. |

On the proposal screen each row says which, where he is deciding (D7a): *Estimated — moderate
confidence*, *From your foods*, or nothing for a typed worth, as a typed row says nothing today.
*From your foods* is **wording, not a source**: the row still stores the food's own source, so the
record, the export and the backup say exactly what they said before this decision.

**What a row teaches a food.** A row carrying his food's own figures — or his typing over them — is
attached to that food and **teaches it nothing**: a food he already has is changed in *My foods*,
not by logging it (the rule D45 already states for a correction that does not rename). Every other
row attaches by name exactly as today, and the food it lands on is offered the row's worth through
the guarded statements — so an estimate cannot overwrite a figure he typed or read off a packet,
and D45's line still says when a figure is replaced. **The worth itself is what is offered**, as a
scan offers the packet's own per-100 g (D38, #28 in the old repository) rather than one worked back
from a rounded row: per 100 g as the food's per-100 g figure; per one piece as its per-one figure in
that piece; per 100 ml as its per-one figure in `ml`, divided by 100 — the same shapes
`DerivedFoods` already makes, without the rounding.

### 4. His own foods, matched on the phone

After the reply arrives — never before, and never sent anywhere (D16) — each item's **plain name** is
looked up among the foods he has on offer (not hidden, not left knowing nothing).

- **Exact.** A food whose name or any alternative name is the same name by the app's one identity
  rule (`FoodKeys.nameKey`), with no brand — **the very food logging this row by name would land on
  anyway**. No fuzziness: this is the match `findOrCreate` has always made at the moment of logging,
  moved forward to the moment of deciding.
  - **By default the item takes the food's own figures**, when the food can cost the described
    amount without a conversion (§5). The amount he described is kept. The row says *From your
    foods*.
  - **One tap uses the estimate instead** — *Use the estimate (N kcal)* — and one tap goes back.
    Nothing is lost either way; the model's answer is kept beside the row until he saves.
- **Close.** No exact match, but **the plain name typed into Add something's food search would list a
  food** — `FoodSearch.matching`, D28's plain contains and D41's words-in-any-order, unchanged. The
  first food that search lists is offered as one line under the row: *Use your Greek yoghurt?* One
  tap makes the item that food, on the same terms as an exact match; ignoring it logs the estimate.
  **One suggestion, never a list, and never applied by itself** — which is D28's objection to fuzzy
  matching answered: a close match is a question with his food's name in it, not a decision.
- **None.** A new food, from the estimate, as now.

*Worked examples, invented:* his *Pita*, known per pita at 250 kcal, typed. "A pita with hummus"
comes back *Pita, 1 pita, 165 kcal per pita (estimated)*: exact, same piece, so the row is 250 kcal
*From your foods*, and *Use the estimate (165 kcal)* is one tap away. "Yoghurt with honey" comes back
*Yoghurt*: no food is named that, but the search for "Yoghurt" lists his *Greek yoghurt*, so the row
offers *Use your Greek yoghurt?*.

**What this does not reach.** A food named in the other language (`יוגורט` against `Yoghurt`) is not
found — that is the bilingual-names issue and joining the two foods, as it always was. A model name
longer than his food's ("Hamburger bun" against a food called "Bun") is not a close match: the search
looks for the typed words inside a food's names, not the other way round, and the model is told to
keep names plain for exactly this reason. A branded food matches only as a close match (identity is
name and brand; the model names no brand), so his scanned milk is offered, never assumed.

### 5. When his food is counted in a different unit from the described amount

**The rule: his food's figures are used only when the food can cost the described amount exactly as
logging it by hand would — through `Logging.log`, which converts nothing.** That is:

- the amount is in **grams**, and the food can be weighed — it knows its per-100 g figure, or what
  one of it is worth **and** what one of it weighs (a stored fact, never worked out); or
- the amount is in **any other unit**, and it is **the same unit** the food is counted in — the same
  name, case and spacing aside, a food that names no unit counting in *portions* (D36's rule for
  what one of something weighs, amended 2026-09-23).

**Otherwise the item stays the estimate**, and the row says so and offers the one honest way across:
*Your Hamburger bun is counted in grams. Say how many grams to use its figures.* with **Count it in
grams**. That switches the unit to his food's, takes his food's worth, and **leaves the amount box
empty** — a number put in a box he then saves is indistinguishable from his own (D4, D30's rule for
an amount box). Until he types one the row cannot be saved. *Use the estimate* returns the model's
unit and amount with its worth.

*Worked example, invented:* his *Hamburger bun*, known only per 100 g at 270 kcal. "A burger in a bun"
comes back *Hamburger bun, 1 bun, 150 kcal per bun (estimated)*. 1 bun cannot be costed from a
per-100 g figure, so the row stays 150 kcal estimated, with the sentence above. He taps **Count it in
grams** and types 60: 162 kcal, *From your foods*. Had he left it, the row would log as the estimate
and teach his *Hamburger bun* what one bun is worth, as an estimate, beside the per-100 g figure he
typed — which is how a food comes to know both, today.

Nothing works one unit out from the other: not a bun's weight from two calorie figures, not grams
from millilitres, not grams from kilograms. That is the join rule of today (the correction to §3.6 of
the foods-and-meals design, 2026-09-16) and D36's amended rule, applied one step earlier.

### 6. What replaces *Less / As described / More*

**On the proposal screen**, each row:

1. The name, and the detail beneath it when there is one.
2. **The amount**: a number box with its unit beside it, typed. For a counted unit — anything not
   grams or millilitres — **−** and **+** either side, stepping by one and never below one (a typed
   0.5 is kept; − does nothing when a step would go below one). The box follows D42: at most 5000 g (or 5000 of
   whatever mass or volume unit the row names), at most 100 of anything counted, the sentence said
   only past it, a blank or zero just leaving *Save* off.
3. **The worth**, one line — *per 100 g: 250 kcal · P 18 · C 0 · F 20* — with **Change** opening the
   four boxes beneath it. The boxes keep decimals and name D42's per-100 or per-one ceilings in their
   refusals, as the food form's do.
4. **The total** the row will log, and its origin line (§3).
5. The match line, when there is one (§4, §5).
6. **Remove**, as now.

*Save* and *Keep as a meal* are off while any row's amount or worth is blank or refused, and a line
above them names the row. *Tell it more* is unchanged: it asks again and replaces every row.

**On the repeat screen**, *Just for today* over a meal he built: each part's **Less / As it was /
More** becomes the same amount box, in the part's own unit, holding the amount the meal has for it —
his own stored number, not a default (D30) — with − and + for a counted part. The worth there is the
food's and is **not** editable on this panel: a part has no figures of its own, it is costed from its
food, and the panel changes nothing about the meal or the food. Whether a day was adjusted is still
answered against the meal as it stood (unchanged). The foods tab's amount box is already a typed box
and does not change.

**Old rows repeated.** Nothing on the repeat screen repeats a day's row directly any more: a food is
logged at a typed amount, a meal from its parts. An old row with no amount is still corrected on the
day, where D34 put that.

### 7. A part of a saved meal gets its amount changed (#4)

**Tapping a part** in the meal builder opens the builder's own *how much* panel for it — the one a
food picked from the search opens — **holding that part's amount and way of counting**, with the
total moving as he types. **Change it** writes it through `SavedMealRepository.put`, which already
changes a food's amount in place rather than adding a second part, so the part keeps its place in
the list. **Up**, **Down** and **Remove** stay on the row.

Grams or whole ones is offered as it is for a food being put in: only the ways the food knows, the
other shown with its reason. A searched food that the meal already holds is still named, not offered
(D41) — and **that sentence now takes a tap that opens the same panel for the part**, so "remove it
and put it back" is never the only way.

Days already logged from the meal do not change: the meal is a definition from the moment it is
changed (unchanged).

---

## What this amends

- **D5** — the assumption shown is the natural piece and a detail in words, not a guessed weight.
- **D28** — "nothing resolves a model proposal against food already on the record": now something
  does, on the phone, after the reply, with no fuzziness applied by itself. The path order D28 set
  (his foods first, describing on a miss) is unchanged.
- **D30** — "the typed-amount control for logging (#1) is not part of this": it now exists, and the
  builder's amount box reaches a part already in the meal.
- **D34** — strengthened by the strict schema; an unstated amount is one natural piece. The retry and
  the refusal stand.
- **D41** — "Not taken: offering the food already in so its amount can be changed": taken.
- **D42** — "a proposal's count (Fewer / More) is not typed and has no ceiling": it is typed and has
  the ceiling of every amount box.
- **D44** — its amount-is-not-a-correction rule, and its one-figure-promotes-all-four cost, now hold
  for every item on the proposal screen, not only a label row in *Correct this item*.
- **The prompt's confidence** — now about the figures per piece or per 100, not about the amount.
- **The PortionScale rule** "scaling always applies to the original proposal" — gone with scaling.

Stated as unchanged, because each was checked: **D4** (every row still says where its figures came
from, and *From your foods* adds no source value), **D6** (nothing is saved until accepted), **D7**
(an estimate's confidence is shown and kept), **D8**, **D16** (only his words leave, and matching
happens after the reply), **D35**, **D37** (the app's own "portion" is still pluralised), **D38**
(whole numbers on the day), **D43**, **D45** (a replaced figure is still said out loud), and **D46**
(keeping the answer as a meal gathers the same rows the same way).

## What does not change

- **No stored shape changes.** A day's row keeps its whole-number figures, its amount and unit, its
  source and confidence, and its link to a food; the detail goes into the row's existing portion
  words — *1 bun (sesame, toasted)*. A food's three facts are the ones it had. **No schema version,
  no migration, no backup-format change.**
- *Type the numbers*, *Correct this item*, the scan and Add something's food tab are untouched.
- Nothing already logged moves.

## Cost accepted

- **The model may still answer the wrong basis** — total figures labelled per 100, or grams for an
  unstated amount. The schema cannot prevent that; the worth line printed on every row is what makes
  it visible, and *Change* or *Tell it more* is the fix. How often it happens is unmeasured until
  real descriptions are sent.
- **Typing one worth figure makes all four his** (§3).
- **A per-one estimate of an unstated piece is the model's idea of a piece.** A large one is 1.5 of
  it, or a *Change* of the worth — the cost D35 already accepted for a cup.
- **A unit cannot be changed on a row that matches none of his foods.** "1 bowl" that he knows was
  300 g is fixed by *Tell it more* ("300 g"), one more call, or by typing both amount and worth in
  *Type the numbers*. A switch that empties the worth for him to type was considered and not taken.
- **A close match is one line, not a list**; the second-best is not offered.
- **"Slice" and "slices" are two units.** Same-unit means the same name, case and spacing aside
  (D36's rule); nothing singularises, in either language. The model is told to name the piece in
  the singular; a food counted in "slices" still meets the §5 sentence and its switch.
- **The privacy page needs no new words.** What leaves the phone is exactly what it says: the text
  typed, nothing else. It is re-read at the end of the build to confirm, not edited.
- **Matching reads the foods on offer when the answer arrives.** A food made or renamed while the
  answer is on screen is not seen until *Tell it more* or a fresh description.
- **The proposal row is denser.** The render tests here cannot measure widths (a 320 dp canvas with
  no real font), so how it wraps on a phone is checked on the phone.
