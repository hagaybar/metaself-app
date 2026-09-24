# The model reviews a food's figures — D54

> Decided 2026-09-23 by the owner, on this repository's issue #18 (the old repository's #57). Three
> decisions on that issue are the contract, and this file writes them down as a numbered decision
> and settles what they left to the build:
>
> - **2026-09-21** — the model is told where each figure came from and weighs it: a packet-label
>   figure is near-certain, contradicted only when the figures are internally impossible, and then
>   it must say why.
> - **2026-09-23, the storage rule** — per group of four numbers, all or nothing; an accepted group
>   that changed is `AI_ESTIMATE`; an untouched group keeps its source and is not rewritten;
>   acceptance is the only way a guess replaces something better; no new marks on screen.
> - **2026-09-23, consent** — a review may send that one food's name and existing figures to the
>   model, only when the owner asks for it, and the privacy page says so.
> - **2026-09-24, amendment** — the weakest-member rule read literally: an accepted group is stored
>   as the weaker of `AI_ESTIMATE` and the source of the figures the model kept in it (§5).
> - **2026-09-24, second amendment** — a review keeps what it was told: a figure echoed back
>   rounded is kept, not changed; how an unusable answer is said, and the model's answer shown on
>   request; stored figures shown rounded in the editor and kept when their box is untouched (§8).
>
> Every figure below is invented to illustrate the rule beside it.

---

## Why there is a decision here at all

- **Filling a food by hand is tedious**, and the model can already estimate what a food is worth
  from its name (D53 asks it for exactly that on every description).
- **But a food is not a description.** It holds up to three facts, each with its own source (D4), and
  some of those are better than anything a model knows — a packet's label above all. A reviewer that
  ignored where a figure came from would be a blank-filler with the power to overwrite a label.
- **And the food form cannot store a guess today.** `FoodForm` makes everything `TYPED`, and
  `RoomFoodRepository.correct()` clears all three groups before it writes. That is safe only while
  the form produces nothing below `TYPED`. The moment it can produce `AI_ESTIMATE`, the clear is how
  a rank-1 guess would wipe a rank-3 label without anyone choosing it.

---

## D54 — A food's figures can be reviewed by the model, on request; what he accepts is an estimate, and nothing he did not accept is rewritten

### 1. Where it is offered

| Where | In v1? | Process sent |
|---|---|---|
| **My foods → a food's editor** (an existing food) | **Yes** | `existing_food` |
| **The meal builder's *Make a food* panel** (a new food, the only place one is made by hand) | **Yes** | `new_food` |
| **Joining two foods** | **No — later** | (`merge`, reserved) |

**Why joining waits.** D36, as amended for issue #19, promises that the food that stays keeps its
name *and its numbers*, and the join question says so on screen. Reconciling two records inside the
join would either break that promise or turn one irreversible question into two. What v1 gives
instead is enough for the common case: after the join, open the food that stayed and ask for a
review of it — the `existing_food` process, with every figure it now holds. What that cannot see is
a group the absorbed food held where the survivor held one too, since D36 lets that go with the
absorbed food. Reconciling *both* records before the join is the later piece, and it will need its
own amendment to D36.

**The one control.** A text button, **Review the figures**, in the editor directly beneath the name
(and brand) and above the three groups, with one line of small print under it:

> *Sends this food's name, brand and figures, and where each came from, to the model, with your key.
> Nothing else.*

It is offered whenever the name is one the form would accept; the figures may be empty (a new food
with only a name is the plainest case). While the request is out the button reads **Reviewing…**
and does nothing.

### 2. What is sent

**Exactly this, and nothing else** — no other food, no alias, no barcode, no date, no history of
when or how much of it was eaten, nothing about the owner (D16, as amended below):

| Field | What it holds |
|---|---|
| `process` | `"new_food"` or `"existing_food"`. |
| `name` | The name in the editor's name box, as it stands. |
| `brand` | The brand box, or `""` for no brand (D41's `NA` spellings are sent as `""`). The meal builder's panel has no brand box, so `""`. |
| `per_100g` | The four figures, with `source` and `confidence`, or `null` when the group is not known. |
| `unit_name` | What "one" is, from the unit box, or `""`. Sent even when the per-one figures are empty, because a named unit is what lets the model fill them (§3). |
| `per_unit` | The four per-one figures, with `source` and `confidence`, or `null`. |
| `grams_per_unit` | What one weighs, with its `source`, or `null`. Sent so the reviewer can see that 100 g and one piece disagree — **never asked for** (§3). |

**What "the figures" are.** The form as it stands when he presses the button, not the stored food —
he may have typed since opening it. Each group's `source` is worked out by one pure rule, the same
rule Save uses (§5):

- the group's boxes equal the stored group (figures to D45's nine significant figures, unit name
  exactly) → the stored `source` and `confidence`;
- the group was accepted from a review in this editing session → `AI_ESTIMATE` with that
  review's confidence;
- anything else he has typed → `TYPED`;
- a group whose boxes are empty, half filled or refused by the form → `null` (not known). Its
  half-typing is not sent, and a suggestion for it would replace the half-typing if accepted.

`source` is one of `LABEL`, `TYPED`, `AI_ESTIMATE`, `REPEATED`, `UNKNOWN` (`UNRECOGNISED` is sent
as `UNKNOWN`); `confidence` is `LOW`/`MEDIUM`/`HIGH` for an estimate and `null` otherwise.

**The request's own shape** is the user message, a JSON object in exactly the table's shape, built
by one pure function (`ReviewPrompt.requestBody`) with its own "nothing else is sent" test — the
twin of `EstimatePrompt`'s *NOTHING about the owner is sent*. *Invented example:*

```json
{
  "process": "existing_food",
  "name": "Oat biscuit",
  "brand": "",
  "per_100g": {"kcal": 480, "protein_g": 7, "carbs_g": 62, "fat_g": 22,
               "source": "LABEL", "confidence": null},
  "unit_name": "biscuit",
  "per_unit": {"kcal": 90, "protein_g": 1, "carbs_g": 12, "fat_g": 1,
               "source": "TYPED", "confidence": null},
  "grams_per_unit": {"grams": 18, "source": "TYPED"}
}
```

**The instructions** (system message, temperature 0, the model from settings), in substance:

- You review the nutrition figures of **one food** as a nutritionist would, and return a complete
  set: keep what is right, fill what is missing, replace what is wrong.
- **Where each figure came from matters.** `LABEL` is printed on the packet and near-certain: keep it
  unless the figures are *internally impossible* — the energy of the macros (4 kcal per gram of
  protein or carbohydrate, 9 per gram of fat) far from the calories, beyond what fibre, alcohol or
  rounding explain; or more than 100 g of macros in 100 g — and then say why. `TYPED` is the owner's
  own number: change it only when it is clearly wrong, and say why. `AI_ESTIMATE` is an earlier guess
  with its confidence, and may be improved. `REPEATED` was copied from a past meal and its origin is
  unknown. `UNKNOWN` is a figure of unknown origin.
- **Never state what one piece weighs, and never name a unit.** Per one is filled only for the unit
  named, and only when one is named. `portion` means an unnamed serving whose size nobody recorded:
  change its figures only if they are impossible.
- Every figure is one number, never a range. For each figure you change, a short reason (one
  sentence); for a group you fill, one short reason for the group; for a figure you keep, an empty
  reason. A confidence for each group as you return it. At most one short note overall.
- Reply in the language of the food's name.

### 3. What comes back

Pinned by a `strict` JSON schema, every field required, each group nullable:

| Field | What it holds |
|---|---|
| `per_100g` | `null`, or `{kcal, protein_g, carbs_g, fat_g, kcal_reason, protein_reason, carbs_reason, fat_reason, confidence}` — numbers, decimals allowed; reasons strings; confidence `LOW`/`MEDIUM`/`HIGH`. |
| `per_unit` | The same shape, or `null`. |
| `note` | One short note, or `""`. |

(Nullability is `anyOf: [{the object}, {"type": "null"}]`, which strict structured outputs accept.)

**There is no weight field, by construction** — the schema cannot carry a guess at what one piece
weighs, so `foods_weight_never_guessed` stays true without relying on the model's obedience. The
unit name is not in the reply either: the proposal is always for the unit in the editor.

**Read strictly, as `EstimateResponse` is** (a pure `ReviewResponse.parse`):

- `null` for a group means *leave it as it is*. A reply cannot remove a group.
- `per_unit` is ignored when the editor names no unit.
- A figure is compared with the one the group holds using D45's comparison; **equal is kept, exactly
  as held** — a model echoing 3.25 does not turn a label's 3.25 into 3.3. So is a figure equal to
  the held one once both are rounded to one decimal (§8.1).
- A figure that differs is a **change** and must carry a non-blank reason — its own, or else the
  group's first non-blank reason (§8.2). A group the form did not know is a **fill** and needs at
  least one non-blank reason. Changed and filled figures are rounded to one decimal place: a guess
  claims no finer precision.
- **A group is set aside, whole, not repaired**, when any figure is missing, not finite, negative or
  past D42's ceiling for its basis (per 100 g: 1000 kcal and 110 g; per one: 5000 kcal and 500 g),
  or when a change or fill has no reason anywhere in its group. That group stays as it was and the
  screen says one line: *Its suggestion for per 100 g couldn't be used.* If every group that changed
  is set aside, the answer is *unusable* — arrived and read, with nothing to accept — and is said as
  that, not as a failure to understand it (§8.3).
- A reply that changes nothing is a real answer: *No changes suggested.*

*Invented example, continuing §2:* the reply keeps per 100 g exactly (a label, and consistent), and
for per biscuit returns fat 4 with the reason *18 g of a food with 22 g of fat per 100 g holds about
4 g*, the other three kept, confidence `MEDIUM`. So per 100 g is **unchanged** and per biscuit has
**one change**.

### 4. How it is shown

**Nothing is written to the boxes until he accepts.** The suggestion is drawn beside the form, not
in it, so the form still shows his figures.

- **Under each group heading that has a suggestion**, one line per changed figure, then **Use
  these**:
  > *Fat 1 → 4 g — 18 g of a food with 22 g of fat per 100 g holds about 4 g.*
  A filled group is one line: *Suggested: 60 kcal · P 4 · C 9 · F 1 — {reason}.*
- **Under the review button**, the note if there is one, any *couldn't be used* line, and **Use all**
  (shown only when two groups have suggestions) and **Dismiss**.
- A group with no suggestion says nothing. No badge, colour or icon is added to any box, before or
  after (the owner's *no new marks*).
- **Use these** copies the four suggested figures into that group's boxes — never the unit name,
  never the weight — records the group as accepted with the review's confidence, and takes the
  suggestion lines down. **Use all** does that for every group with a suggestion. **Dismiss** takes
  down whatever is left; groups already accepted stay accepted.
- **Typing in a group withdraws its suggestion**, whether it has arrived or is still out: he is
  answering that group himself.
- **Changing the name or brand withdraws both groups' suggestions**, arrived or still out: the
  review was of the food as it was called when he asked, and is no answer for another one.
- **After acceptance nothing new appears.** Once saved, the group's existing origin line reads as it
  does for any estimated group today — *This came a close estimate* — because the food's source is
  now `AI_ESTIMATE`. That line already exists; nothing is added.
- **Save** is still the only thing that writes. **Cancel** discards the review and everything
  accepted. An answer that arrives after the editor was closed or another food opened is dropped
  (`askToDelete`'s rule).
- A failure is said in the editor's own sentence slot, above Save, in `ProposalWording.failure`'s
  existing words — *No API key yet…*, *You have used today's estimates…*, *Could not reach the
  model…*, *The provider refused: …*, *The answer could not be understood…* — and the form is
  untouched (D8: the way on is typing). *Could not be understood* is said only of a reply that was
  not in the shape asked for. An answer that arrived in that shape with every group it changed set
  aside is not a failure to understand it, and is said under the review button (§8.3).

### 5. How it is stored — the owner's rule, and what Save does

The owner's rule of 2026-09-23, verbatim:

> - **Per group of four numbers, all or nothing.** If the suggestion changed or filled *any* figure
>   in a group and the owner accepts it, the whole group is stored as `AI_ESTIMATE`. If the
>   suggestion left a group exactly as it was, that group keeps its existing source untouched and is
>   not rewritten.
> - **Downgrading is always honest; upgrading never is.** A mixed group is labelled by its weakest
>   member, so a guess can never be dressed as a label figure (D4). The cost — a label figure that sat
>   beside a model's figure is now recorded as an estimate — is the safe direction to be wrong in.
> - **Acceptance is the only way a guess replaces something better.** A group ranked above
>   `AI_ESTIMATE` is overwritten only when the owner accepts a suggestion that changes it, never by
>   the clear-then-write in `RoomFoodRepository.correct()` as a side effect. Untouched groups must not
>   pass through the clear at all.
> - **No new marks on screen.** No per-figure or per-group source badge is added for this; the source
>   is kept in storage for the app's own rules (rank-guarded writes, what the model is told next
>   time) and stays out of the way of the layout.

**What Save builds.** The facts handed to the repository say, group by group:

| The group in the editor | Stored as |
|---|---|
| Accepted from a review this session — **even if he then changed a figure in it** | `AI_ESTIMATE`, the review's confidence for that group — or, where the figures the model kept rank below an estimate, their source (amendment of 2026-09-24, below). A mixed group is labelled by its weakest member. |
| Anything else he typed | `TYPED`, as today. |
| What one weighs | Never accepted from anything; `TYPED` when typed, as today. |

**Amendment, 2026-09-24 — the weakest member includes the figures the model kept.** The owner's
rule says a mixed group is labelled by its weakest member, and that downgrading is always honest
while upgrading never is. An accepted group mixes the figures the model changed or filled (an
estimate) with the figures it kept, which still carry the source the group was sent with. So an
accepted group is stored as **the weaker of `AI_ESTIMATE` and the source of the figures the model
kept in it**, by `Provenance.rankOf`:

- **the model kept figures that were `REPEATED` or `UNRECOGNISED`** (rank 0, below an estimate) →
  the group is stored as that source, with no confidence (on a food only an estimate carries one).
  A guess never lifts a figure copied off a past meal, or one of unknown origin, to an estimate;
- **the model kept `LABEL` or `TYPED` figures** (ranked above an estimate) → `AI_ESTIMATE` with the
  review's confidence, exactly as the table says: the estimate is already the weaker;
- **the model kept no figure** — it filled the group, or changed all four → `AI_ESTIMATE` with the
  review's confidence.

A kept earlier estimate (`AI_ESTIMATE`) ties with the new one; the group is `AI_ESTIMATE` with the
review's confidence, which the model gave for the group as it returned it, having been told the
earlier one. The source of the kept figures is the one the request sent for the group (§2), so what
the model is told next time (`FormOrigins`) and what Save stores (`FoodForm.toFacts`) are worked out
by the same rule (`AcceptedGroup.provenance`). A group stored `UNRECOGNISED` this way is the one case
in which this version writes that value; it only ever carries forward a figure that already had it.

**What the repository does with them — one transaction, `saveForm` as today.** `correct()` stops
clearing all three groups. For each group it compares what arrives with what is stored, inside the
transaction:

- **the same figures** (D45's comparison; unit name exactly) → **no statement at all**: the group
  keeps its source, confidence and date. This is what "untouched groups never pass through the clear"
  means in code, and it holds whatever provenance the incoming group claims;
- **emptied** → cleared, as today (still refused by name when a saved meal counts in it);
- **different** → cleared, then written with the provenance it arrived with. The clear is what lets
  an accepted `AI_ESTIMATE` replace a `LABEL` or `TYPED` group — and that happens only for a group
  whose figures changed, which in this editor means he typed them or accepted them.

**A new food** goes through `findOrCreate`, as now, with each accepted group carrying
`AI_ESTIMATE`. A food of that name that already exists is offered those groups through the guarded
statements: an accepted estimate does not replace a figure the existing food holds better, because
he never saw that food's figures in the panel, so he never accepted replacing them. D45's line still
says when a figure *was* replaced.

*Invented example, continuing §3:* he taps **Use these** on per biscuit and saves. Per 100 g arrives
equal to what is stored, so no statement touches it and it is still `LABEL`. Per biscuit arrives
different (fat 1 → 4) and marked accepted, so it is cleared and written as 90 · P 1 · C 12 · F 4,
`AI_ESTIMATE`, `MEDIUM` — the three figures the model kept included, because the group is labelled by
its weakest member. The 18 g is equal, untouched, still `TYPED`.

**A side effect on Save, intended.** Today, saving a food whose label group he did not touch
relabels that group `TYPED`, because every group passes through the clear. After this, it stays
`LABEL`. That is the owner's "untouched groups keep their source" applied to the Save it has to live
in, and it is also what D44 asks of a logged row — a figure is credited to whoever last stated it.

### 6. The daily ceiling, the key, the failures

- **One review is one call**, counted against the same daily ceiling as an estimate, only when a
  call actually happened (`recordCall()`'s rule). No retry: D34's second ask exists for missing
  amounts, which a review has none of.
- The same key store, the same model setting, the same 45-second timeout, the same closed set of
  failures and the same sentences (§4). The network code is shared with the meal estimator rather
  than copied, so the two cannot drift in how they count or how they fail.
- **The problem log never records the food's name** — the log is written to be shared, and a food's
  name is what he eats (`OpenAiMealEstimator`'s rule for item names). So a refusal is logged by its
  kind and the provider's status only, never in the provider's own words, which can quote back what
  was sent; the words are still shown on screen.

### 7. The privacy page and the terms

**privacy.html.** *"Four things"* becomes *"Five things"*, the *Last updated* date moves to the
release, and a new item follows the meal description:

> **One food's name and figures, to OpenAI, when you ask for a review.** When you press *Review the
> figures* on a food, that one food is sent using *your own* API key: its name and brand, the
> figures in the editor, where each came from and how sure an earlier estimate was, what one of it is
> called, what one of it weighs, and whether the food is new. Nothing else goes with it — no other food, not what or when you ate, not your weight, age, goal or
> any identifier. Nothing is sent unless you press the button.

Item 1's last sentence (*Repeating a meal, or typing the numbers yourself, sends nothing at all.*)
stays true and is not changed.

**terms.html** lists what is sent to OpenAI, so its line changes from *meal descriptions are sent for
estimation* to:

> **OpenAI** — meal descriptions are sent for estimation, and a food you ask to have reviewed is sent
> for review, using *your own* API key. Their terms and pricing apply to you directly, and any cost
> is yours.

### 8. Amendment, 2026-09-24 — a review keeps what it was told

A food scanned from a packet holds its per-100 g figures as a serving's scaled, so they are stored as
long doubles — *invented:* 8.571428571428571 g of protein, from 6 g in a 70 g serving. Reviewing such
a food ended in *The answer could not be understood*: the model echoed 8.57, the reader took the
echo for a change, a change with no reason set the group aside, and with every group set aside the
reply was read as unreadable. What follows is settled so that cannot happen, and so that when an
answer cannot be used he is told so honestly and can see it.

**8.1 An echo is kept.** A figure the model returns is **kept, exactly as held**, when it equals the
held figure by D45's comparison, or when the two are equal once **both** are rounded to one decimal
(half up). 8.57 or 8.6 for a held 8.571428571428571 is kept; so is 7.3 for a held 7.25. A figure
that differs at the first decimal is still a change. The cost: a change smaller than half a tenth
that also rounds to the held tenth cannot be proposed — below the precision a suggestion claims
(§3) anyway.

**8.2 A change may borrow its group's reason.** A change whose own reason is blank takes the first
non-blank reason in its group — models explain two linked changes once. A group is set aside for want
of a reason only when a change in it has no reason anywhere in the group, which is the fill's rule
(§3) applied to changes. The cost: a borrowed reason may explain a sibling figure rather than this
one; it is still the model's own words about the group, shown beside the figure.

**8.3 An unusable answer says what happened.** When the answer arrived in the shape asked for and
every group it changed was set aside, the result is its own (`ReviewResult.Unusable`), not
`Unreadable`. Under the review button, in the captions' ink, the editor says:

> *The model's answer arrived, but its suggestions could not be used.*

followed by the answer's note, if any, and the existing *Its suggestion for … couldn't be used.* line
for each group set aside, with **Dismiss**. Nothing goes in the sentence slot above Save, and the
form is untouched. *The answer could not be understood* stays for a reply not in the asked shape. The
problem log records it as `review unusable`, by its kind alone — none of the answer, which can
hold the food's name (§6).

**8.4 Show the model's answer.** After a review whose reply could not be read, could not be used,
proposed nothing, or set a group aside, the editor offers one more text button under the review
button: **Show the model's answer**. It shows the reply as it came — the message's content, or the
whole body when there is no content to find — pretty-printed when it is JSON, in selectable text,
with **Copy** to put it on the clipboard; pressed again (**Hide the model's answer**) it folds away. It
is not offered when every suggestion is on screen, since those speak for themselves. A new review
or **Dismiss** takes it down; closing the editor forgets it. Both editors, My foods' and *Make a
food*.

**It is shown, never kept.** The reply travels from the reviewer to the editor's state and is drawn;
it is never saved with the food, never written to the problem log, and never sent anywhere. That is
the problem log's promise — it records what failed, never what you ate — kept for a text that can
hold the food's name. What leaves the screen is what he copies himself.

**8.5 Stored figures are shown rounded, and an untouched box keeps the stored figure.** The food
editor shows a stored figure with **at most two decimals, trailing zeros trimmed** — 8.57 for a
stored 8.571428571428571, 72 for 72.0, 8.5 for 8.5. The same form fills the boxes when a suggestion
is accepted. Nothing is lost by it, because of one rule (`FoodForm.figure`), used by Save
(`FoodForm.toFacts` with the stored facts) and by what a review is told (`FormOrigins`,
`ReviewRequest.of`): **a box whose text is exactly the shown form of the stored figure is the stored
figure**. So opening a food and saving it untouched hands the repository the stored doubles; §5's
comparison finds the group unchanged, and no statement touches it — figures, source and confidence
stay as they were, never relabelled `TYPED`. Any other text is read as typed, including the same
number written another way (8.570), which is his own number. What one weighs follows the same rule.

The costs: he cannot type the shown rounding itself (8.57) over a stored 8.571428571428571 and have
it stored as exactly 8.57 — it is taken as untouched, a difference far below anything a label or a
guess claims. And in *Make a food*, where nothing is stored, a figure accepted from a review is
shown to two decimals, so a kept figure he had typed with three or more decimals is written back
rounded; the group is an estimate by then (§5).

---

## What this amends

- **D16** (and D16a) — *exactly one thing leaves the phone* becomes two to the AI provider: the meal
  description when logging, and **one food's name, brand, figures, their sources, its unit name and
  its weight, only when he presses *Review the figures***. Still nothing about his body, his history
  or when he ate, and still his own key.
- **The food form's rule that everything typed on it is `TYPED`** (`FoodForm`'s KDoc) — a group
  accepted from a review is `AI_ESTIMATE` with the review's confidence.
- **`correct()`'s clear-then-write of all three groups** — only a group that changed is cleared and
  written; an unchanged group is not touched (so, incidentally, Save no longer relabels an untouched
  label group `TYPED`).
- **D2** — the model seam gains a second narrow interface, for a review, beside the meal estimator.

Stated as unchanged, because each was checked: **D4** (every stored figure still says where it came
from; a mixed group is labelled by its weakest member), **D6** (nothing is saved until Save),
**D7/D7a** (the confidence is stored, and shown where it already is: the group's origin line),
**D8** (every failure ends at typing), **D34** (not applicable: no amounts), **D36** (joining is
untouched in v1; its promise that the survivor keeps its numbers stands), **D42** (suggested figures
are judged by the same ceilings), **D44**, **D45** (the new-food route still says when a figure was
replaced), **D53** (describing a meal is untouched).

## What does not change

- **No stored shape changes.** A food's three groups already carry source, rank, confidence and
  date; `AI_ESTIMATE` is already a value they hold. **No schema version, no migration, no DAO
  statement added, no backup-format change.** The clear and guarded write statements that exist are
  enough.
- Describing a meal, scanning, *Type the numbers*, *Correct this item*, joining and the day are
  untouched.
- **What one weighs is never guessed.** It is sent as context and cannot come back.

## Cost accepted

- **A label figure the model kept is recorded as an estimate when a sibling figure changed** — the
  owner's weakest-member rule, the safe direction.
- **Changing a figure in an accepted group before saving keeps the group an estimate.** It is still a
  mix with a guess in it. Opening the saved food later and editing that group makes it `TYPED`, as
  editing any group has always done — the four he did not touch included; the same cost D44 and D53
  accepted.
- **A per-one suggestion is the model's idea of one piece** (D53's cost for a piece), and for a new
  food it can sit beside a per-100 g suggestion with no weight to tie them — which is what the
  existing disagreement display is for, once a weight is typed.
- **Changing only a unit name's case or spacing rewrites that group `TYPED`**: the unit name is
  compared exactly so the stored name moves, and there is no statement that renames a unit without
  writing the group. Rare, and a downgrade, never an upgrade.
- **The new-food route cannot replace a better figure on a food that already has the name**; the
  accepted estimate simply does not land there. Correct that food in My foods, where its figures are
  shown and a review of them is offered.
- **Accuracy is unmeasured** until real foods are reviewed. How often the model contradicts a label,
  and whether its reasons hold, is checked on the phone.
