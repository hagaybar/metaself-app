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
> - **2026-09-24, third amendment** — a kept figure is judged at the precision the model wrote it
>   in; the model cross-checks per one against per 100 g when it can; and every review ends in a
>   plain line under the button, with the model's one-sentence verdict (§9).
> - **2026-09-24, fourth amendment** — when per one and per 100 g contradict each other through
>   what one weighs, the review proposes correcting the group it believes wrong, even a packet
>   label, always as a suggestion he accepts or dismisses; a label group standing alone is still
>   changed only when its own figures are impossible; the model writes in the owner's words; it
>   says whether it found a problem in a field of its own, and the line under the button says so;
>   and the button's small print sits directly under it, with the outcome below (§10).
> - **2026-09-24, fifth amendment** — what would change is listed in one place, under the verdict,
>   with **Apply these changes** and **Keep mine**; figures applied are drawn in the teal accent
>   and counted, *not saved yet*, with **Undo**; the per-group lines go. The owner's earlier *no new
>   marks* is superseded for the review by his feedback (§11).
>
> - **2026-09-25, sixth amendment** — the review may propose a value for every box on the food's
>   page but the brand — the name, the unit (naming one where there is none), both groups whatever
>   their source, and what one weighs — each with a reason; its suggestions go straight into the
>   boxes, drawn in the teal accent while pending, each with its reason and a way back; **Accept
>   changes and save** in one tap, or **Cancel** back to the page as it was; an accepted weight is an
>   estimate; the app still never derives one (§12).
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
| **My foods → a food's editor** (an existing food) *(Amended 2026-09-24, D55: My foods → a food's page)* | **Yes** | `existing_food` |
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
| `grams_per_unit` | What one weighs, with its `source`, or `null`. Sent so the reviewer can see that 100 g and one piece disagree — **never asked for** (§3). *(Amended 2026-09-25, §12: the review may now propose it.)* |

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
*(Amended 2026-09-25: temperature 0 is sent only to a model that takes it; a reasoning model — the
GPT-5 and GPT-6 families, o1, o3, o4 — refuses it, and is sent none and `reasoning_effort: "low"`
instead, by one rule for every request (`ModelParams`).)*

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
  named, and only when one is named. *(Removed 2026-09-25, §12.5: it may name a unit, propose a
  better one, and propose a weight.)* `portion` means an unnamed serving whose size nobody recorded:
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
| `verdict` | `"consistent"` or `"problem_found"` — what the model concluded (added 2026-09-24, §10.3). |

(Nullability is `anyOf: [{the object}, {"type": "null"}]`, which strict structured outputs accept.)

**There is no weight field, by construction** — the schema cannot carry a guess at what one piece
weighs, so `foods_weight_never_guessed` stays true without relying on the model's obedience. The
unit name is not in the reply either: the proposal is always for the unit in the editor. *(Amended
2026-09-25, §12.3: the reply now carries `name`, `unit_name` and `grams_per_unit`; the weight line
says a review may suggest one, kept as an estimate.)*

**Read strictly, as `EstimateResponse` is** (a pure `ReviewResponse.parse`):

- `null` for a group means *leave it as it is*. A reply cannot remove a group.
- `per_unit` is ignored when the editor names no unit.
- A figure is compared with the one the group holds using D45's comparison; **equal is kept, exactly
  as held** — a model echoing 3.25 does not turn a label's 3.25 into 3.3. So is a figure that is
  the held one written at the model's own precision (§8.1, as amended by §9.1).
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
  after (the owner's *no new marks*). *(Superseded 2026-09-24, §11: the suggestion is listed under
  the verdict, and a box it changed is drawn in the teal accent until saved.)*
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
  aside is not a failure to understand it, and is said under the review button (§8.3). *(Amended
  2026-09-24, §9.4: a review's failure is now said under the review button too, not in the sentence
  slot.)*

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
| What one weighs | Never accepted from anything; `TYPED` when typed, as today. *(Amended 2026-09-25, §12.7: accepted from a review, `AI_ESTIMATE` with the review's confidence for it.)* |

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

*(Amended 2026-09-25: offered after every answer, suggestions waiting in the boxes (§12.6)
included — the owner reads the answers. With suggestions waiting it sits under the outcome line,
and **Cancel** takes it down with them.)*

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

### 9. Amendment, 2026-09-24 (third) — a review always answers

A review came back with every figure echoed — per 100 g at two decimals, per one
exactly — every reason empty and no note. The screen showed nothing that read as an answer: the
echo of one per-100 g figure had been taken for a change (§9.1), the group set aside, and the
reply said as *could not be used* in the captions' small grey type, directly under the button's own
small print and indistinguishable from it — with no verdict, because the model gave none. What
follows is settled so that an echo is always an echo, a disagreement inside the food is looked for,
and every review ends in a line he can read.

**9.1 A kept figure is judged at the model's precision.** §8.1's rule compared both figures at one
decimal, which fails when the model's two-decimal echo rounds up at one decimal while the held
figure rounds down — *invented:* 4.848484848484849 g, 1.6 g in a 33 g piece, echoed as 4.85, is
4.9 against 4.8. A figure the model returns is now **kept, exactly as held**, when any of these holds:

- it equals the held figure by D45's comparison;
- it is within half a unit of its own last decimal place of the held figure — the decimals read from
  the JSON number's text as the model wrote it (4.85 is two, 4.0 is one, 5 is none), at most three;
- it equals the held figure rounded half up one decimal at a time, from three places down to its own
  (4.8485 → 4.848 → 4.85 → 4.9) — a model rounding its own echo again;
- the two are equal once both are rounded to one decimal (§8.1's rule, kept: a change is rounded to
  one decimal, §3, so one that lands on the held figure's tenth could not be shown as a change).

So for the held 4.848484848484849: 4.85, 4.848, 4.9, 4.8 and 5 are kept; 5.0 is a change. The cost:
a model that writes a figure with fewer decimals can propose less — a whole number within half of
the held figure is read as the held figure.

**9.2 The two groups are checked against each other.** When the request holds both groups and what
one weighs, the instructions add: the figures for one should equal the figures per 100 g times
`grams_per_unit` / 100, within label rounding. If they disagree beyond rounding, the model says so
in the note — which group it believes and why — and proposes the corrected figures for the group it
does not believe; a `LABEL` group it still changes only when its own figures are impossible, and a
label that merely disagrees with the other group is flagged in the note, with the reason, and left
as it is. *(Amended 2026-09-24, §10.1: a label group that contradicts the other group may now be
corrected.)* What one weighs is still never stated, never changed and never guessed: it is given only to
check the groups against each other. *(Amended 2026-09-25, §12.5: the weight is inside the
cross-check, and may be the one proposed for correction.)* Without both groups and the weight, the rule is not sent.

**9.3 The note is a verdict, and never empty.** The instructions ask for the note on every reply:
*what you concluded, in one sentence* — that the figures are consistent and kept, or what was changed
and why. The schema describes it so (a `description`; strict structured outputs accept no length
constraint), and an empty note is still read, not refused: the line below appears without it.

**9.4 Every outcome ends in a line under the button.** Directly under **Review the figures**, above
its small print *(amended 2026-09-24, §10.4: below its small print)*, in the body's own type and ink (no new colour, badge or icon), the editor says what
the review came to, and the screen scrolls it into view when the answer arrives:

| Outcome | The line |
|---|---|
| Nothing suggested | *Reviewed: no changes suggested — {note}* — only when the verdict is `consistent`; otherwise *Reviewed: a problem found — {note}* (§10.3) |
| Suggestions (N groups with **Use these**) | *Reviewed: N suggestion(s) below — {note}* |
| Every change set aside (§8.3) | *The model's answer arrived, but its suggestions could not be used — {note}* |
| Suggestions all withdrawn by his typing, or all used, with the note or a set-aside line left | *Reviewed: no suggestions left — {note}* |
| A failure (§4) | `ProposalWording.failure`'s sentence, unchanged |
| The request threw, or the food was gone when it was built | *That couldn't be opened…* (`ActionRefused.COULD_NOT_OPEN`) |

With no note, the line ends at a full stop instead. The set-aside lines, **Use all**, **Dismiss** and
**Show the model's answer** follow as before; a failure's line is taken down by **Dismiss** or a new
review. **This moves a review's failure out of the sentence slot** (§4): it is said under the button
it answers, in both editors, and no longer above Save in My foods or at the top of the meal builder
— where it was off screen from the button, and a review looked as if it had done nothing. An answer
whose every suggestion was withdrawn while it was out is no longer silently dropped: it says *no
suggestions left*.

### 10. Amendment, 2026-09-24 (fourth) — a contradiction gets a proposal

A food scanned from a packet held per 100 g as a label and per one whose protein, carbohydrate and
fat were not per 100 g times the weight over 100, while the calories agreed. The review saw it,
said in its note that the per-one figures were wrong — and, obeying §9.2's *a label that merely
disagrees is flagged and left as it is*, proposed nothing, so the screen read *Reviewed: no changes
suggested* above a note naming a problem. What follows is settled so that a contradiction the
model finds is one he can act on.

**10.1 When the groups contradict, the one believed wrong gets corrected figures.** The owner's
decision of 2026-09-24, on issue #18: when per 100 g and per one contradict each other (per one ≠
per 100 g × `grams_per_unit` / 100 beyond label rounding), the review may propose correcting the
group it believes wrong **even if that group is a packet label** — always as a suggestion he
accepts or dismisses. The cross-check instructions (§9.2) now say: the two groups contradict and
at least one is wrong; decide which you believe; propose the corrected figures for the one you
believe is wrong, even a `LABEL` group, with a reason for each figure changed; and say in the note
which you believe and why. **A label group standing alone is still changed only when its own
figures are impossible** (§2's rule, unchanged): a contradiction is the one case in which a label
whose own figures are possible may be changed. *(Superseded 2026-09-25, §12.1: any figure, a
label's included, may be proposed when the model judges it wrong or improvable, with its reason.)* Nothing about storage changes — an accepted
correction of a label group is stored as §5 says, `AI_ESTIMATE`, the safe direction.

**10.2 The note and the reasons are written in his words.** They are drawn on screen as they come,
and the same answer had written *the per_unit figures* and *the figures for one* — the request's
field names and the instructions' own shorthand. The instructions now say: write the note and every
reason in plain words, saying *per 100 g* and *per {the food's own unit name}* (*per cup*, *per
biscuit*), never a field name such as `per_unit`, `per_100g`, `grams_per_unit` or `kcal_reason`,
and never *the figures for one*. With no unit named, only *per 100 g* is given. The cross-check
(§9.2) speaks of the unit by its name too — *the figures per cup should equal…* — since a model
mirrors the words it is given.

**10.3 Whether a problem was found is its own field.** The same answer proposed nothing and its note
named a problem, and the line read *Reviewed: no changes suggested — {a note naming a problem}*,
which is not true. Rather than reading the note's prose, the strict schema gains a required
`verdict`, an enum of `"consistent"` and `"problem_found"`, and the instructions say: *consistent*
only when nothing was found wrong, missing or contradictory; *problem_found* when anything was,
whether or not corrected figures are proposed. It is read strictly the other way round: only
`"consistent"` is consistent, and anything else — a missing verdict included — is a problem found,
so the line may say nothing is wrong only when the model said so. The line under the button (§9.4)
is now:

| Outcome | The line |
|---|---|
| Suggestions (N groups with **Use these**), whatever the verdict | *Reviewed: N suggestion(s) below — {note}* |
| Nothing suggested, verdict `problem_found` | *Reviewed: a problem found — {note}* |
| Nothing suggested, verdict `consistent` | *Reviewed: no changes suggested — {note}* |

The other rows of §9.4 are unchanged. The verdict is not stored and not shown on its own; it
chooses the words of the line and nothing else.

**10.4 The small print stays with the button; the outcome comes below it.** With the outcome line
between **Review the figures** and its small print, the small print (*Sends this food's name…*)
read as part of the answer, and the answer's **Dismiss** and **Show the model's answer** sat apart
from the line they belong to. The order is now: the button; directly under it, its small print;
then, together, the outcome line, any *couldn't be used* lines, **Use all** and **Dismiss**, and
**Show the model's answer**. Same type, ink and scrolling into view as §9.4; both editors.

### 11. Amendment, 2026-09-24 (fifth) — a review says what changes

*(Superseded 2026-09-25 by §12.6 for how a suggestion is shown: in the boxes, pending in the teal
accent, with **Accept changes and save** / **Cancel**. The change list, **Apply these changes**,
**Keep mine**, **Undo** and the after-apply teal marks go; the outcome line and **Show the model's
answer** stay.)*

With the fourth amendment's build it was not clear from the screen whether anything had changed,
which value, or whether to save: each group's suggestion sat under its own heading with its own
**Use these**, **Use all** sat under the button, and after accepting, the boxes looked exactly as
they had. The owner's decision of 2026-09-24, on issue #18: *the changes are listed under
the verdict (group, figure, old → new) with Apply these changes / Keep mine; applied figures are
highlighted in the app's teal accent with a line "N figures changed by the review — not saved yet.
Save to keep them, or Undo."; the per-group suggestion lines go. This supersedes the earlier
no-new-colours rule for the review.*

**11.1 One place to look.** Under the outcome line (§9.4, §10.4), for each group with a suggestion,
one line in the body's ink — *Per 100 g: Protein 6.25 → 8 · Carbs 4.5 → 4 · Fat 5.75 → 5* (invented figures), or
*Per {the unit's name}: …* (*Per one:* with no unit named) — each figure old → new, written as the
editor's boxes write them (§8.5), so the old figure reads as the box does. A group the form did not
know reads *Per 100 g: Filled: 60 kcal · P 4 · C 9 · F 1*. Under each group's line, in the
captions' ink, its reasons, each said once. Then two buttons: **Apply these changes** and **Keep
mine**. Nothing is drawn under the groups' headings any more: **Use these** and **Use all** are
gone. **Dismiss** stays for an answer with nothing to apply (no changes, a problem found, unusable,
a failure). *Invented figures throughout.*

**Partial acceptance is not offered.** A per-group *Apply* was considered and left out: the owner
asked for one place to look and two answers, and a review's two groups are usually one judgement
(§9.2 cross-checks them against each other). The cost: to keep one group and not the other he
applies both and types over the one he does not want, or undoes and types.

**11.2 Apply these changes** accepts every group with a suggestion through the one accept path §4
and §5 already describe — nothing about what is stored, or how it is labelled, changes. **Keep
mine** is **Dismiss**: what is left of the review goes, and the form is untouched.

**11.3 What was applied is marked until it is saved.** Every box whose value came from the review
— every box of a group it filled, and only the changed figures of the others; never a figure it
kept — is drawn in the teal accent: a `tertiary` border and label and a `tertiaryContainer` fill,
the family the D52 milestone uses, which means nothing else in the editor. The figure keeps the
body's ink. Every pairing is measured against the 4.5:1 text floor in both schemes by
`InkLadderTest`. Each marked box also says to a screen reader *changed by the review, not saved*,
since a colour alone says nothing to one. Directly under the outcome line:

> *N figures changed by the review — not saved yet. Save to keep them, or Undo.*

(*1 figure … Save to keep it, or Undo.* for one), with **Undo** under it. In *Make a food*, whose
button that saves is **Make it**, the line reads *… Press Make it to keep them, or Undo.* N is
the number of boxes still marked. With a note left on screen after applying, the outcome line
reads *Reviewed: changes applied — {note}*, not *no suggestions left*.

**11.4 How a mark goes.**

- **Typing in a marked box** takes its mark off, and only its: that figure is his again. The count
  drops with it; when no mark is left, the line and **Undo** go. Typing in the group still leaves
  it accepted, stored as an estimate (§5, unchanged).
- **Undo** puts back, in every box the apply wrote, what it held before — except a box he has typed
  in since, which is his answer and stays — and returns the review to where it stood before he
  applied: its lines, **Apply these changes** and **Keep mine**, and the groups accepted as they
  were. A group he typed in since stays withdrawn, as typing always withdraws it (§4). A review he
  dismissed after applying is not brought back by **Undo**.
- **Save** (or **Make it**) writes as §5 says and closes the editor; **Cancel** discards
  everything. Both take every mark with the editor. A Save that is refused leaves the editor, and
  the marks, as they were: the figures are still not saved.
  *(Amended 2026-09-24, D55: in My foods, Save closes the food's page rather than an editor, and
  Cancel is Leave it alone, or Back; a refused Save leaves the page and its marks as they were.)*
- A new review leaves the marks up; applying it adds its own.

Nothing about what is sent (§2), what comes back (§3), or what is stored (§5) changes. Both
editors, My foods' and *Make a food*.

### 12. Amendment, 2026-09-25 (sixth) — the review may suggest anything but the brand, in the boxes

The owner's decisions of 2026-09-25. A review had three limits that the page no longer needs: it could
not name a unit for a food that had none, so a food known only per 100 g stayed that way; it could
not say what one weighs, so the one figure that joins the two ways of counting was left to him; and a
suggestion was a list of *old → new* lines above the boxes, which he then had to find in the boxes
below. What follows is settled so that the model may propose a value for **every box on the food's
page except the brand**, each with its reason, and so that what it proposes is drawn **in the boxes
themselves**, where he accepts, reverts or types over it. Everything that is sent (§2) is unchanged;
what may come back, how it is shown, and how an accepted weight is stored change.

*Every name and figure in this section is invented to illustrate the rule beside it.*

**12.1 What may be proposed.** Eleven boxes, each on its own:

| Box | May be proposed | Never |
|---|---|---|
| **Name** | A clearer or correctly spelled name for the same food. | A different food; a brand added to or taken out of the name. |
| **Brand** | — | Anything. A brand makes it a different food (D41, `foods_brand_splits`), which is not a correction. The brand is still *sent*, as context. |
| **One what?** (the unit) | A unit when the box is empty (*slice*, *cup*, *tablespoon*, *piece*…); a different unit when one is named and the model judges its own better. | A mass unit (`Portions.isMass` other than the millilitre: *g*, *100 g*, *kg*, *oz*, *litre*…) — per 100 g is the way to say that; the app's own *portion*. |
| **Per 100 g**, four boxes | Any figure, filled or changed, **whatever its source** — `LABEL` and `TYPED` included. | — |
| **Per one**, four boxes (per 100 ml for a food counted in ml, D56) | Any figure, filled or changed, whatever its source — for the unit that will stand once the suggestion is taken. | — |
| **What one weighs**, grams | A typical weight of one of the unit that will stand, filled or changed, whatever its source. | A weight for a food counted in millilitres (a density, D4, D56 §3). A weight when the editor has no weight box (*Make a food*, 12.9). |

**This supersedes §2's and §10.1's special treatment of a label.** A `LABEL` figure is no longer
changed *only when impossible or contradicted*: the model is told the source of every figure and that
a label is the packet's own statement — strong evidence — and it may change any figure it judges
wrong or improvable, saying why. What stops a guess replacing a label is unchanged and was always the
real guard: **only his tap accepts it** (§5's *acceptance is the only way a guess replaces something
better*), and a figure accepted is stored as an estimate (12.7).

**12.2 What is sent.** Exactly §2's fields, unchanged: the name and brand were already sent, and so
were the unit name and what one weighs, as context. So **D16 (as amended by §7) and the privacy
page's item do not change** — checked against `privacy.html` item 2 and `terms.html`: both describe
what leaves the phone, and nothing new leaves it. What one weighs is still not sent for a food counted
in millilitres (D56 §3). One thing is added to the request object that is **not sent**: whether the
editor can take a weight (`weightAsked`: true on a food's page unless its unit is the millilitre, false
in *Make a food*), which only chooses the instructions (12.4).

**12.3 What comes back.** The strict schema gains three required, nullable fields; `null` still
means *leave it exactly as it is*:

| Field | What it holds |
|---|---|
| `name` | `null`, or `{value, reason}` — strings. |
| `unit_name` | `null`, or `{value, reason}` — strings. |
| `per_100g` | As §3: `null`, or the four figures, four reasons and a confidence. |
| `per_unit` | As §3, **for the unit that will stand**: the proposed `unit_name` when there is one, else the one named. Per 100 ml when that unit is the millilitre. |
| `grams_per_unit` | `null`, or `{grams, reason, confidence}` — a number, a string, `LOW`/`MEDIUM`/`HIGH`. |
| `note`, `verdict` | As §9.3 and §10.3. |

Nullability is `anyOf: [{the object}, {"type": "null"}]` for each, as §3 already does. The schema's
top-level `required` lists all seven.

**12.4 How it is read** (`ReviewResponse.parse`, pure, strict, as before). The answer is read as
**four independent items**, each used whole or set aside whole, never repaired:

1. **The name.** Kept when its value, trimmed, equals the name box trimmed — or differs from it only
   in case or spacing (`FoodKeys.nameKey` equal) **and** comes with a blank reason: an echo is not a
   proposal. Otherwise a **change**: it needs a non-blank reason and a value `FoodKeys.nameKey`
   accepts, or the item is set aside. (A case or spacing change *with* a reason is a real proposal —
   a capital put right.)
2. **Per 100 g.** Exactly §3, §8.1, §8.2 and §9.1: an echo at the model's own precision is kept
   exactly as held; a change or fill is rounded to one decimal, needs a reason (its own or its
   group's first), and is judged by D42's per-100 g ceilings; the group is set aside whole otherwise.
3. **The unit and per one — one item, the *per-one bundle*.** The unit that will stand decides the
   scale and the ceilings of the four figures, so the two are read together:
   - `unit_name` is **kept** when it is `null`; when its value is blank (an empty unit box echoed
     back as `""`); when its value is the unit box's as Save stores it (`FoodKeys.displayName`,
     exactly), or differs from it only in case or spacing with a blank reason (the name's rule);
     and, for a food counted in millilitres, when it is any millilitre spelling or *100 ml* (what §2
     sends such a food as). A proposed *100 ml* is read as
     *ml*, the spelling the one-tap switch writes (D56).
   - Otherwise it is a **rename** (a named unit replaced) or a **naming** (the box was empty). Either
     needs a non-blank reason, a value `FoodKeys.displayName` accepts, not a mass unit (12.1), not
     *portion* — and **`per_unit` in full** for the new unit: `null` beside a new unit would relabel
     figures stated for one thing as figures for another, so the bundle is set aside.
   - `per_unit` is read against the held per-one figures by §3's rules — an echo kept exactly as
     held, a difference a change needing a reason — when the unit is kept, and when it is renamed on
     the same side of the millilitre (a respelling, or *slice* for *piece*): the held figures are
     then at the same scale, and a figure the model leaves alone is not new. After a **naming**, or a
     rename **to or from the millilitre**, every figure is new — a fill of the group, rounded to one
     decimal, needing one reason. Its ceilings are the new unit's: per 100 (1000 kcal, 110 g) when it is the
     millilitre, per one (5000 kcal, 500 g) otherwise; its scale is per 100 ml when it is the
     millilitre (D56), so a figure is rounded at that scale and stored divided by 100 by the form, as
     one typed there is.
   - `per_unit` with no unit named and none proposed is set aside (a figure of nothing) — where §3
     ignored it silently.
   - **A rename, when the food holds a weight and the new unit is not the millilitre, needs
     `grams_per_unit` too** — an echo of the held weight is its answer that the weight stands. A
     rename with `grams_per_unit: null` there leaves the old unit's weight under the new unit's name,
     so the bundle is set aside, and the weight item with it. A rename *to* the millilitre leaves the
     held weight where D56 §3 puts it — shown, with its line, until he clears it; the app deletes
     nothing — and any weight in the answer is set aside.
   - **A rename *away from* the millilitre, on a food that still holds a weight** (D56 §3 keeps one
     it held before), is allowed although that weight was never sent: when the answer proposes no
     weight for the new unit, the page adds one pending suggestion of its own to the bundle — the
     weight box **cleared**, with the reason *A weight per millilitre is not a weight per {new
     unit}.* — so a weight stated per millilitre never silently becomes one per glass. It goes back
     with the bundle.
   - Each bundle carries a confidence: the `per_unit` answer's, which is present whenever a unit is
     named or renamed (above).
4. **What one weighs.** Read only when the request says a weight is asked (12.2); otherwise ignored,
   never shown. Kept when it is an echo of the held weight (§9.1's rules, grams at one decimal);
   otherwise a change or fill rounded to one decimal, needing a non-blank reason, greater than 0 and
   within D42's ceiling for grams (5000 g), for **the unit that stands once the bundle is read** —
   the one held, or the one proposed if the bundle was used; a weight with no standing unit, or for
   the millilitre, is set aside; and it is set aside with the bundle when the bundle is (above).

**Unusable** (§8.3) is now *every item that changed or filled something was set aside*. *No changes
suggested* (§3) is every item kept or `null`. The set-aside lines are one per item: *Its suggestion
for the name couldn't be used.*, *…for per 100 g…*, *…for per {unit}…* (the unit as it stands, or
*one*), *…for what one weighs…*.

*Invented example.* A food named *Humus*, brand none, per 100 g from the packet: 166 kcal, protein
7.9, carbohydrate 14.3, fat 19.6; no unit; no weight. Its label's macros come to 265 kcal against the
166 stated. The reply proposes: name *Hummus* (*The usual spelling.*); per 100 g fat 9.6 (*At 19.6 g
of fat the macros come to 265 kcal, far above the 166 stated; 9.6 g fits.*), the other three kept,
`MEDIUM`; unit *tablespoon* (*A dip is usually counted by the spoon.*) with per tablespoon 24.9 ·
1.2 · 2.1 · 1.4 (*One tablespoon of it, from the figures per 100 g.*), `MEDIUM`; one tablespoon
weighs 15 g (*A level tablespoon of a thick dip holds about 15 g.*), `MEDIUM`. Read: a name change,
one change in per 100 g, a per-one bundle that names a unit and fills its group, and a weight fill —
eight boxes suggested.

**12.5 The instructions**, in substance, replacing §2's list where they differ (the source
descriptions, the one-number rule, the language rule, the plain-words rule of §10.2, the verdict and
the note stay):

- You review **everything on this food's page except its brand**: its name, what one of it is
  called, its figures per 100 g, its figures for one, and what one weighs. Propose a value for
  anything you judge wrong, missing or improvable; return what is right as `null`, or exactly as
  given.
- Where each figure came from matters, and you are told it. `LABEL` is the packet's own statement and
  strong evidence; change it when you judge it wrong or improvable, and say why. `TYPED` is the
  owner's own number: change it when you judge it wrong, and say why. (The rest as §2.)
- **The name**: correct its spelling or make it clearer; it must stay the same food. Never add a
  brand to it or take one out of it.
- **The unit**: when none is named, you may name the one this food is most often counted in, with
  its figures for one and — if a weight is asked — what one weighs. When one is named, you may
  propose a better one; then `per_unit` must be the figures for **your** unit, and, when a weight is
  known, `grams_per_unit` must be the weight of **your** unit (the same number if it still holds).
  Never a mass unit such as g, 100 g, kg or oz: that is what per 100 g is for. `ml` means the drink
  is counted by volume, and then the figures for one are **per 100 ml** and no weight is given.
  *portion* is a serving whose size nobody recorded: you may name a real unit for it only with
  figures for that unit.
- **What one weighs** *(only when a weight is asked)*: you may propose what one of the unit weighs,
  in grams, as a typical figure, with a reason. It will be shown to the owner as a suggestion and
  kept as an estimate. *(Otherwise: return `grams_per_unit` as null.)*
- **The cross-check** (§9.2, §10.1) is kept for a food holding both groups and a weight, with one
  change: the weight is no longer outside it. When the three disagree, decide which of the three you
  believe is wrong — either group, or the weight — and propose the correction for that one.
- A reason for every change and every fill, one sentence each; for a group you fill, one reason for
  the group.

The two sentences that §2 and §9.2 gave about the weight (*never state what one piece weighs, and
never name a unit*; *grams_per_unit is given only for this check: never change it, state it or guess
it*) are removed.

**12.6 How it is shown — in the boxes.** §11's change list, **Apply these changes**, **Keep mine**,
**Undo** and the teal *changed by the review, not saved* marks are replaced by this. The button, its
small print, the outcome line and its note (§9.4, §10.3, §10.4), the set-aside lines and **Show the
model's answer** (§8.4) stay where they are — the button offered with suggestions waiting too
(§8.4, amended 2026-09-25). *(Settled with the owner on 2026-09-25, after the first
draft of this section: the draft's separate Accept all / Dismiss all, its "accepted, not saved"
line and its Undo are replaced by one act that accepts and saves, and one that cancels.)*

- **When the answer arrives, every suggestion goes straight into its box.** Each such box is
  **pending**: drawn in the suggestion colour, with, directly beneath it, the model's reason in the
  captions' ink, and one text button that goes back — *Back to 19.6* (the box's text as it stood,
  written as the box writes it, §8.5), or *Clear* for a box that was empty. Fills and changes
  alike are pending and drawn the same way. A reason shared by several boxes of one group is said
  once, under the first of them.
- **The suggestion colour is the teal family §11 introduced** — `tertiary` border and label,
  `tertiaryContainer` fill, the figure in the body's ink. It keeps **one meaning: suggested, not yet
  accepted** — nothing else in the editor uses it, and no box is marked once saved. No new colour is
  added (D48), and every pairing is already measured against the 4.5:1 text floor in both schemes by
  `InkLadderTest`. A pending box also says to a screen reader *suggested by the review, not
  accepted*; its **Back** button is named with its box (*Calories per 100 g back to 166*), since
  *Back to 166* alone could be any of eight (public issue #3).
- **While a group holds a pending box, its four figure boxes are drawn one per row**, full width, so
  each reason sits under its own box at a readable width; when nothing in it is pending, it returns to
  D55's two by two. (*Make a food* draws one per row already.)
- **The per-one bundle goes back as one.** When the suggestion renamed or named the unit, the unit
  box's button reads *Back to {the old unit}* or *Clear* and takes the unit **and** every
  pending box of the bundle back together, the weight included when the bundle carried one. The
  bundle's figure boxes and its weight show their reasons and no button of their own: four figures
  stated for a *tablespoon* under a unit put back to *slice* would be a silent wrong answer. (A
  respelled unit whose four figures were all kept is a bundle of one box, the unit's.) When the unit
  was kept, each per-one box has its own button, as every other box does.
- **Typing into a pending box makes it his**: the normal colours, its reason and button go, and the
  text he typed is his own figure (12.7). Nothing else moves. A pending figure or weight box of the
  bundle typed over is taken out of the bundle; the unit's **Back** still takes the rest. Typing in
  any other box — the brand, a box the review did not touch — is his, as always, and takes no
  suggestion down.
- **Typing a new unit takes down every suggestion tied to the unit**, whether or not the unit itself
  was pending: the unit is what he typed, and every per-one figure and weight still pending goes
  back to what it held before the answer, no longer pending. They were stated for a unit — the
  model's, or the old one — and must not stand under a unit he chose instead.
- **The weight goes with the unit it describes.** A weight suggestion goes back with the bundle it
  arrived in; and when the unit box ends up empty by a **Back**, a weight suggested for that unit
  goes back with it, so no weight of nothing is accepted.
- **Saved meals that count the food by the piece are named.** When a unit is pending — named or
  renamed — on a food that saved meals count **in units** (a meal counting it in grams is not
  affected, and is not counted), one line under the unit box says so: *2 saved
  meals count this food in slices; after saving they will count it in tablespoons.* (invented; one
  meal: *1 saved meal counts…*). Their amounts do not change; what *2* means does.
- **While anything is pending, the page has exactly two answers to it, in place of Save and Leave it
  alone** (the foot of the page; in *Make a food*, in place of **Make it** and **Cancel**):
  - **Accept changes and save** (*Accept changes and make it* in *Make a food*) — **one tap** that
    accepts every suggestion still pending, as it stands, and saves the food through the one Save
    there is (12.7). This is the deliberate act: nothing a model wrote is stored by any other.
  - **Cancel** (*Cancel suggestions* in *Make a food*, whose own **Cancel** closes the panel, so
    that no two buttons on one screen say *Cancel* and do different things) — every pending
    suggestion goes, nothing is saved, and **the page is put back exactly
    as it was when he pressed Review the figures**: every box, his own unsaved typing from before the
    review included, and the review's answer taken down. Typing done after he pressed the button
    goes with it — it was typed on the model's answer, and a page half the model's and half restored
    is the state this answer exists to avoid. The page stays open; *Leave it alone*, Back and the
    system Back still leave it and discard everything (D55 §2).
  **Save** and **Leave it alone** (**Make it** and the panel's **Cancel**) are not drawn while
  anything is pending, so no tap can save a suggestion without saying *accept*, and none can be read
  as keeping only his figures. Once nothing is pending — every suggestion put back or typed over —
  the ordinary two return, and Save saves what the boxes hold, all of it his.
- **A refused Accept changes and save loses nothing.** When Save is refused — a name another food
  holds (12.7), a group left half filled by a **Back** or by his typing, anything the repository
  refuses — nothing is stored, the refusal is said in the slot above the two buttons as any refused
  Save is, and **every suggestion is still pending on screen**, with its reason and its button. The
  form's own errors show under their boxes, as after any refused Save.
- **Review the figures is not offered while anything is pending.** A second review would be sent the
  model's own figures as if they were the form's, labelled as his.
- **While the request is out**, §4's rules stand, extended to the new items: typing in per 100 g
  withdraws that group; typing in the unit or a per-one box withdraws the bundle **and** any weight
  suggestion; typing in the weight withdraws the weight; a new name or brand withdraws the whole
  answer; and an answer with nothing left says *no suggestions left*. The snapshot **Cancel** returns to is the page as it stood when he pressed
  the button.
- An answer that lands after the page began closing is dropped (D55 §8).
- **The outcome line** (§9.4, §10.3) now reads, where it differs:

| Outcome | The line |
|---|---|
| Suggestions pending | *Reviewed: N suggestions, in the boxes below — {note}* (*1 suggestion…*), N the pending boxes |
| All put back or typed over, with a note left | *Reviewed: no suggestions left — {note}* |

*Invented example, continuing 12.4.* Eight boxes arrive teal: the name box reads *Hummus* with *Back
to Humus*; per 100 g's fat reads 9.6 with *Back to 19.6*; the unit box reads *tablespoon* with *Clear*, and the four per-tablespoon boxes read 24.9 · 1.2 · 2.1 · 1.4 with the one reason under
the first; the weight box reads 15, its reason under it. At the foot, **Accept changes and save** and
**Cancel** stand where Save and Leave it alone were. He presses *Back to Humus* — the name box reads
*Humus* again, in the normal colours — then **Accept changes and save**. The food is saved as 12.7
says and the page closes.

**12.7 How Save stores it.** §5's machinery, unchanged in the repository — `saveForm`, one
transaction, `Correction.plan` group by group — with these rules for what the form hands it:

- **A group is accepted when Accept changes and save took at least one of its boxes.** A group
  whose suggested boxes were all put back or typed over is **not accepted**: its boxes hold what they held,
  so it reaches the repository as it would have with no review — untouched, if it is the stored
  group, by §8.5 and `Correction.Keep`: no statement, **its figures, source, confidence and date
  exactly as they were**.
- **An accepted group is stored by the weakest-member rule of §5 as amended 2026-09-24, kept.** A
  box put back, or never changed, keeps its **figure** exactly — but the stored food carries one
  source per group, not per figure, so it cannot keep its **own** source inside a group whose other
  figures are now a guess. The group is labelled by its weakest member: `AI_ESTIMATE` with the
  confidence the review gave the group, or the kept figures' source where that ranks lower
  (`REPEATED`, `UNRECOGNISED`). A per-figure source would need a schema change and new marks on
  screen; the owner's own rule prefers the honest downgrade. So a label's three kept figures, beside
  one accepted change, are stored as an estimate, as §5 already says. What *kept from* means is
  worked out when he accepts, not at parse: the group's source as sent, when any of its four figures is
  one it was sent with — put back, or echoed. A figure typed over is his (`TYPED`, above an
  estimate) and changes nothing.
- **The per-one bundle.** A unit renamed or named by an accepted suggestion is part of the group, as
  it always has been (`Correction` compares the unit name exactly): the per-one group is stored as
  accepted, by the rule above, under the new unit name. A rename that kept all four figures (a
  spelling of the unit) is still an accepted change, so a label's per-one group respelled by the
  model is stored as an estimate — the safe direction, and the same cost §5 records for a hand
  respelling, which stores it as typed. A unit renamed to or from the millilitre moves the group's
  scale as D56 says: the form divides a per-100 ml figure by 100 on Save, exactly as for typing.
- **A weight echoed under an accepted rename or naming is part of the bundle.** It now states what
  one of the model's unit weighs, so it is stored by the weakest-member rule like the per-one group
  beside it: `AI_ESTIMATE` with the bundle's confidence, or its own source where that ranks lower.
  Because its figure did not change, `Correction` gains one case for the weight, and only for it:
  **an arriving estimate over the same figure held at a higher rank is written**, not kept — the one
  way an unchanged figure is relabelled, always downward. (Groups need no such case: an accepted
  group always differs from the stored one, in a figure or in its unit name.)
- **What one weighs, accepted, is `AI_ESTIMATE`** with the confidence the review gave it —
  `GramsPerUnit` with that provenance, through the same clear-then-write any changed group uses. A
  weight put back or never suggested is his, as before: the stored one untouched, or `TYPED` when he
  typed it. **The app still never derives a weight**: no arithmetic anywhere produces one (`FoodForm`
  and `FoodFacts` keep saying so); a weight is typed, or proposed by the model on request, shown as a
  suggestion and accepted by his tap. Logging through an estimated weight already labels the logged
  row by the weaker of the two sources it was computed from (`LoggedFrom`), so a meal counted by the
  tablespoon through an estimated 15 g is logged as an estimate, never as a measurement (D4).
  **The weight line** (`foods_weight_never_guessed`, the page's caption under *What one of it
  weighs*) now reads: *Nothing in the app works this out. A review may suggest one, which is kept as
  an estimate. It is what turns grams into units and back, so a wrong one would follow into
  everything you log afterwards.* The millilitre's line (`foods_weight_not_asked_ml`) is unchanged.
- **The name.** An accepted name goes to the repository exactly as a name he typed would: `saveForm`
  renames first, by `FoodKeys.nameKey` under the food's own brand. **Collision is handled as a hand
  rename is**: when another food already holds that name and brand, the whole Save is refused and
  rolled back — the name, the brand and every group, accepted figures included — and the sentence
  above Save is today's *Another food is already called “{name}”. Join the two, or pick a different
  name.* Nothing is lost: every suggestion is still pending on screen, and the name's **Back** returns
  it to his, after which Accept changes and save may be pressed again. A change of case or spacing only (the same `nameKey`)
  is a rename of the name's shown form, as by hand.
- **A figure he typed over a suggestion is his own**, `TYPED`, as any typing is. Its group is
  stored as `TYPED` when no suggestion of it was accepted; when one was, the group is a mix with a
  guess in it and is labelled by its weakest member, as above.
- **Nothing is stored but by Accept changes and save, or by Save when nothing is pending** (D6);
  **Cancel** and *Leave it alone* store nothing.

*Invented example, continuing 12.6.* He accepts and saves. The name is unchanged — no rename. Per 100 g arrives
with fat 9.6, accepted, the other three figures the label's: stored as all four, `AI_ESTIMATE`,
`MEDIUM` (a label beside a guess is labelled by the guess). Per tablespoon and its unit arrive new,
accepted: stored `AI_ESTIMATE`, `MEDIUM`. One tablespoon weighs 15 g, accepted: stored `AI_ESTIMATE`,
`MEDIUM`. The page's origin lines read *This came an estimate* for all three, as they already can.

**12.8 What a new food made with accepted suggestions does**, in the meal builder's *Make a food* —
unchanged from §5: `findOrCreate`, as for a name he typed. An accepted name is the name the food is
found or made by; if a food of that name and no brand exists, the panel's groups are offered to it
through the guarded statements, so an accepted estimate does not replace a figure it holds better
(he never saw it here), and D45's line says what was replaced. That is exactly what typing that name
does.

**12.9 The meal builder's *Make a food*: the same, for the boxes it has.** It has a name, the two
groups and the unit, and no brand box and no weight box. It takes this section's behaviour for all of
them — the in-box suggestions, the per-one bundle, **Accept changes and make it** / **Cancel** in
place of **Make it** / **Cancel** while anything is pending, a refusal that keeps them pending —
through the same shared review state and the same composables, so
the two editors cannot drift (§4's reason for sharing them). **What one weighs is not asked there**
(`weightAsked` false, 12.2): the panel has nowhere to show it, and a suggestion he cannot see must not
be stored. Adding a weight box to the panel is not part of this decision.

**12.10 Costs accepted.**

- **A packet's figure can be replaced by a guess in one tap** (Accept changes and save), where before a label was
  touched only when impossible or contradicted. It is shown in the box, in the suggestion colour,
  with its reason and *Back to {the packet's figure}*, and it is stored as an estimate — never as a
  label.
- **A label's kept figures become an estimate beside one accepted change**, and a label's per-one
  group becomes one when the model only respells its unit (12.7). Downgrades, never upgrades.
- **An estimated weight is still a weight.** A wrong one follows into every gram-counted log of the
  food, which is what the weight line warned of; it is now possible only by his tap on a suggestion
  he can see, it is labelled an estimate on the page, and every row logged through it is labelled an
  estimate.
- **A name that collides is refused at Save, not when it is suggested** — one refused Save and a
  **Back**, the same as a collision typed by hand. Checking the name when the answer
  arrives was considered and left out: it is a second copy of the rename rule, and a food could take
  the name between the check and the Save anyway.
- **No partial acceptance.** To keep some suggestions and not others he puts back or types over
  the ones he does not want, then accepts the rest; there is no *accept, then decide whether to
  save* in between.
- **Cancel also discards his own typing from after he pressed Review the figures** (12.6).
- **A unit rename changes what saved meals mean.** A saved meal counts a food by a number in its
  unit; after an accepted rename, *2* means two of the new unit. The line under the unit box names
  how many meals count it that way (12.6); their amounts are not rewritten.
- **Figures in different groups are not tied.** Putting back a per-100 g figure that the model used
  to work out its per-one figures leaves those per-one figures standing; only the unit, its figures
  and its weight go back together.
- **The page is taller while suggestions are pending** (one box per row, a reason and a button under
  each).
- **A review costs somewhat more** — a larger schema and a longer answer on the same call, against
  the same daily ceiling (§6).

**12.11 What does not change.** What is sent (§2), the key, the model setting, the timeout, the
failures and their sentences, the daily ceiling (§6), the problem log's silence about the food's name
(§6, §8.3), **Show the model's answer** (§8.4; offered after every answer from 2026-09-25), D42's ceilings, §8.1/§9.1's echo rules, §8.5's shown
figures, D56's scale, the brand, the join, and every stored shape: **no schema version, no migration,
no DAO statement, no backup-format change** — a food's weight already carries a source, a rank and a
confidence, and `AI_ESTIMATE` is already a value it can hold.

**12.12 Settled with the owner, 2026-09-25.** The first draft of this section left two questions —
whether Save with suggestions pending should be refused or accept them, and whether an accepted,
unsaved state should keep a line and an Undo. The owner's answer replaces both: **one tap accepts
and saves; one tap cancels; there is no state in between** (12.6).

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
- *(2026-09-25, §12)* **D4 as `GramsPerUnit` and `foods_weight_never_guessed` state it** — *the only
  ways a weight is ever set are the owner typing it or a packet stating it* becomes: typed, stated
  by a packet, or proposed by the model on request and accepted by his tap, stored as
  `AI_ESTIMATE`. What stays: **nothing in the app computes a weight**, and every row logged through
  one carries the weaker of its sources. The weight line on the page says so.
- *(2026-09-25, §12)* **D41's brand rule is not amended**: a review never proposes a brand.
- *(2026-09-25, §12)* **D54 itself** — §2's and §10.1's limits on changing a label, §2's *never
  name a unit*, §3's reply shape, §4's and §11's presentation, and §5's *what one weighs is never
  accepted*.
- *(2026-09-25, §12)* **D55 §2** — the page's review block and its weight group (pointers there).

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
- **What one weighs is never guessed.** It is sent as context and cannot come back. *(Amended
  2026-09-25, §12: the app never works it out; the model may propose it on request, shown as a
  suggestion, stored as an estimate when he accepts it.)*

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
