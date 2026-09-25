# Every food has its own page — D55

> Decided 2026-09-24 by the owner, from a drawing of three screens: the list of foods, one food's
> page after a review, and the same page after **Apply these changes**. The drawing is the contract
> for what the page holds and in what order; this file writes it down as a numbered decision and
> settles what the drawing left to the build — the route, what moves off the list, how joining works
> from a page, what *Where it's used* counts, and what happens when the food goes.
>
> Every figure, food and meal name below is invented to illustrate the rule beside it, as the
> drawing's were.

---

## Why there is a decision here at all

- **The inline editor has outgrown the list.** A food opened in *My foods* is drawn in place of its
  row: name, brand, **Review the figures** and its verdict block (D54 §8–§11), three groups of boxes,
  five lines of small print, and two rows of buttons. With a review on screen that is taller than the
  phone, the foods above and below it are off screen anyway, and every refusal or question has had
  to be pulled down into the editor because the top of the list is out of sight from its foot (D36).
- **The reason it was drawn in place no longer holds for the whole of it.** `FoodsScreen`'s KDoc
  gives the reason: *what he is editing stays next to the foods around it, which is what he needs
  when the thing he is deciding is whether two entries are the same thing.* That is true of joining
  — and joining keeps the list (§5). It is not true of correcting one food's figures, which is the
  job a review serves.
- **A food has things to say about itself that the list has nowhere to put** — how often it has been
  logged, and which saved meals it is in. The second is today said only as a refusal, after Delete is
  pressed.
- **D50 is the precedent.** The day's record moved to a screen of its own because, when it is looked
  at, *something in it needs fixing — and then it deserves the whole screen*. The same is true of a
  food.

---

## D55 — Every food in *My foods* opens its own page; the list is for finding, choosing and joining

### 1. The list

The list keeps everything that is about **more than one food**, and loses the editor.

- **Each food is one row**: its name, and under it one summary line —
  *No brand · 100 kcal per 100 g · one cup is 150 g* (invented figures).
  - **The brand part** is the real brand (`Food.realBrand`, D41) or *No brand*.
  - **The figure part** is the first way of counting the food knows, in the order `whatItKnows`
    already uses: per 100 g, else per one (*150 kcal per bun*).
  - **The weight part**, when the food knows what one weighs: *one cup is 150 g* — today's
    `whatItKnows` wording. The drawing wrote *a cup is …*; *one* is kept because *a* is wrong
    before a vowel (*a egg*) and the unit name is his own text, which no rule can article.
  - **Drawn as separate text runs** with the ` · ` between them, never joined into one string, for
    the reason `FoodWording`'s KDoc gives: a Hebrew brand and a Latin figure in one string are two
    runs of opposite direction that the bidirectional algorithm may reorder (#9 is still held; the
    rule is kept so it does not have to be retrofitted).
  - **Kept under the summary, only when they apply**, exactly as today: *Also: …* (the names a join
    left behind), the disagreement sentence (a food whose own facts do not multiply out), and
    *Hidden — not offered when you log something*. The drawing's foods had none of these, so it
    drew none; they are what tells two duplicates apart and are not dropped.
- **A tap opens the food's page.** Back returns to the list **where it was**: the same search, the
  same filters, the same scroll position. Nothing about looking is reset by visiting a food.
- **Stays on the list, unchanged:** the search (D41), *Only know a portion (N)*, *Show hidden*,
  *Hold a food to start choosing*, the chosen line with *Make a meal from these N* and *Join these
  two into one food* (D30), the pick-a-duplicate mode and the join question (§5), and the list's
  refusal slot for a refused join or a failed action.
- **While choosing, a tap ticks**, as today — it does not open the page. While picking a duplicate, a
  tap picks — it does not open the page.
- **The hold-to-choose hint** is drawn whenever nothing is chosen and no join is in progress (today
  it is also hidden while an editor is open; there is no editor on the list any more). The drawing's
  *Tap a food to open its page.* is its own caption for the reader, not a line on the screen.

### 2. The page

**Route: `food/{foodId}`**, a required `Long` in the path. A page of no particular food is not a
thing that can be drawn (`Destination.Record`'s reasoning for its day). Title bar: the back arrow
and *My foods*, the list the food belongs to, whichever way he arrived.

From the top, in the drawing's order:

1. **The food's name** in the display face (`headlineMedium`: Fraunces at 28 sp, the size drawn),
   and under it **the same summary line the list row shows**, from the same function, so the two
   cannot drift.
2. **Name and Brand boxes**, with the brand-splits line and the decimals-kept line under them —
   exactly today's editor. *The drawing does not show them.* They are kept because renaming and
   branding a food happen nowhere else, and D54 §1 places the review button *beneath the name and
   brand*. (Open to the owner: see *Questions* at the end. The default is to keep the boxes.)
3. **Review the figures** and its small print, then the review's outcome and verdict block — **the
   0.46.4 behaviour, unchanged** (D54 §8–§11): the outcome line; the change list, group by group,
   old → new, with its reasons; **Apply these changes** / **Keep mine**; **Show the model's
   answer**; and after applying, the teal marks and *N figures changed by the review — not saved
   yet. Save to keep them, or Undo.* with **Undo**. The same composables (`ReviewTheFigures`,
   `FormReview`) — moved, not copied.
4. **The three groups**, each with its heading and origin on one line (the heading left, the origin
   right, as drawn), then its boxes:
   - *What 100 g of it are worth* — Calories, Protein, Carbs, Fat;
   - *What one of it is worth* — *One what?* and the same four;
   - *What one of it weighs* — the never-guessed line and *Grams*.

   **The four figure boxes of a group are laid out two by two**, as drawn; the unit-name box and the
   grams box are full width. **The origin wording does not change in this step**: it is today's
   `FoodWording.origin` (*This came off the packet*, *This came a close estimate*, nothing for a
   figure he typed). The drawing's *From the label* / *Typed by you* / *Estimated* is an open
   question (see the end); the default keeps today's words.
5. **The not-retroactive line**, the *nothing known* error when there is one, then **Save** and
   **Leave it alone**. A refused Save or an action that failed is said directly above them, as today.
6. **Where it's used** (§3).
7. **Join with a duplicate**, **Hide** (or **Show again** for a hidden food), **Delete**, and under
   them the hide-or-delete line. The delete question takes these buttons' place; a refusal to delete
   is drawn directly above them (D36, unchanged — §6).

**The page opens on the food as stored**, every box filled as the inline editor fills it today
(`FoodForm.of`, rounded as D54 §8.5 rounds). A group the food does not know is empty boxes, as today.

**Nothing is saved but by Save.** Leave it alone, the back arrow and the system Back all leave the
page and discard what was typed, any review, and any applied marks — what *Leave it alone* does to
the inline editor today. (Open to the owner: whether leaving with unsaved changes should ask first.
The default does not ask, which is today's behaviour.)

**After Save, the page closes** and he is back where he came from — the inline editor's Save closes
the editor today (D54 §11.4), and the list row then shows the new summary. A refused Save leaves the
page, its boxes and its marks exactly as they were.

### 3. Where it's used

One section, heading *Where it's used*, one line under it:

> *Logged 24 times · in 2 saved meals: Breakfast bowl, Snack plate* (invented)

- **"Logged" counts entries, exactly.** The number of logged rows (`food_items`) whose food is this
  one, on every day, past and future, whichever way they were logged — described, scanned, typed,
  repeated, or as a part of a saved meal logged whole (each part is its own row pointing at its own
  food). Two rows of the same food in one meal are two. **No rounding, no cap, no "99+"**: it is a
  count of the record, and the record is exact.
- **What is not counted.** A row attached to no food — one whose food was deleted, or one a
  correction detached before issue #22 was fixed — points at nothing and is not this food's. After a
  **join**, the rows of the food absorbed point at the one that stayed and are counted there
  (`movePastRows`), so the survivor's number goes up by the absorbed food's; this is what a join is.
- **Wording, from resources, singular or plural by count (D37):** *Not logged yet* at zero, *Logged
  once*, *Logged N times*. The number is written with the grouping `FoodWording.grouped` uses, so
  *1,204* not *1204*.
- **Saved meals: every saved meal that has this food as a part, by name, in name order** — the same
  list, from the same statement, as the delete refusal names (`mealsUsing`), so what this section
  says and what Delete then refuses can never disagree. *in 1 saved meal: Breakfast bowl*, *in 2
  saved meals: …*. When no meal holds it, the meals part is left out. A saved meal's own
  `hiddenAtMillis` is not consulted: nothing hides a saved meal yet, and a hidden one would still
  hold the food and still refuse its delete. Each meal name is its own text run (bidi, §1).
- **A hidden food** says the same thing: hiding keeps its rows pointing at it.
- **Observed, not read once.** Both numbers update while the page is open — after a join, a delete
  of a day's row elsewhere, or a meal changed — because a count read once goes stale the first
  time the record moves under it (the reasoning `observeOnlyAPortionCount` gives).
- **Not tappable in this step.** Opening a meal or the days from here is a later decision.
- **One new read-only query**, the count; the meals list becomes observable. No schema change, no
  migration, no backup change (§8).

### 4. How he gets to a page

| From | Goes to | Back returns to |
|---|---|---|
| A row in *My foods* | `food/{id}` | The list, where it was |
| **Give this a portion** on *Add something* | `food/{id}` directly | *Add something*, with its question still open |

**Give this a portion lands on the page.** Today it opens the manager at `foods?food={id}` with the
list searched down to the food and its editor open, and marks the argument spent (`FOOD_OPENED`) so
a recreated manager does not reopen an editor he had closed. **That whole mechanism goes**: the
route argument, the search written for him, the marker. The page is the food; there is nothing to
open inside it. After Save, the page closes onto *Add something*, whose question already refreshes
the food it holds from the observed list (`RepeatViewModel.refreshed`), so the portion he gave is on
offer — unchanged. If the food was hidden, deleted or joined away from the page, that same refresh
closes the question, as it already does.

The bare `foods` route and every other way into the manager are untouched (D30).

### 5. Joining with a duplicate, from a page

**Pressing *Join with a duplicate* on a page closes the page and puts the list into
pick-a-duplicate mode for that food.** The pick then asks the one join question, and only its answer
joins — exactly as the inline editor's button does today.

- **Why the list and not a picker on the page.** Choosing the duplicate is the job the list is for —
  the search, *Show hidden*, and every row's brand and figures side by side — and is the reason the
  editor was drawn in place (see *Why*). A second search on the page would be a second copy of the
  list to drift.
- **When the list is under the page** (he came from it), the page is popped and the list is handed
  the food's id; it opens pick mode **with its search, filters and scroll as he left them**, because
  the duplicate is probably what that search was for.
- **When it is not** (he came from *Give this a portion*), the page is replaced by the manager at
  `foods?joinFrom={id}`, on its foods tab, in pick mode. Back from there returns to *Add something*.
- **Either way the list learns it the same way**: one key, `joinFrom`, in the list's saved state. A
  route argument arrives in the saved state under its own name, and a result handed back to the
  entry below is written to the same map, so the view model reads one key whichever way it arrived.
  It is **spent once read**, so a later recreation does not begin a join he has since finished or
  walked away from.

  > Corrected 2026-09-24, during the build. "Written to the same map" is not true of Navigation
  > 2.7: the back stack entry's `savedStateHandle` is a handle of Navigation's own, with none of the
  > route's arguments in it, and the list's view model is given a different one. So the key is still
  > `joinFrom` both ways, but a result left in the entry is carried to the view model by the list's
  > destination (`TakeFromFoodPage`), taken out as it is read; the route argument is read from the
  > view model's own saved state and spent the same way. What he sees does not change.
- **The food kept is looked up by id**, not out of the list on screen — the list may be searched or
  filtered so that it does not hold it (a hidden food, with *Show hidden* off). The pick prompt names
  it either way: *Pick the food that is the same thing as “Greek yoghurt”. It will keep this name and
  these numbers, and answer to both.*
- **Everything after the pick is D36, unchanged.** The question, *Joining “%2$s” into “%1$s”.
  “%1$s” keeps its name and its numbers, and answers to both afterwards. This cannot be undone.*,
  with **Join them** / **Not now**, drawn at the top of the list and brought into view; the food
  opened is the one that stays (D36 as amended for issue #19 — its figures stay, a group it lacks is
  filled). *Not now* ends the join and leaves the list as it was. After *Join them* the list is the
  ordinary list, as today; the survivor's page is not reopened. A refused join stays on the list
  with its refusal, as today.
- **Anything typed on the page is not carried** into the join: leaving the page discards it (§2),
  as starting a join from the inline editor closes the editor today. The join question already says
  the food kept keeps *its* numbers — the stored ones.
- **Ticking two in the list and *Join these two into one food*** is unchanged.

### 6. Hiding, showing again, and deleting from a page

- **Hide** hides the food and **closes the page**, as it closes the inline editor today. Returning
  to the list, a line in the list's sentence slot says what happened, with a way back:
  *“Greek yoghurt” is hidden — it is no longer offered when you log something.* **Show again** ·
  **All right**. Hiding is one column and costs nothing to reverse, so it gets an undo in the
  shape of the existing *Show again*, where the inline editor had none: a food that silently leaves
  a list it was just in reads as deleted. The line goes on either answer, and on any search, filter,
  choosing or opening a food. Arriving from *Give this a portion*, there is no list to say it on;
  *Add something* closes its question, as it already does for a hidden food.
- **Show again** (on a hidden food's page) shows it and **stays on the page**: the hidden line goes
  and the button reads *Hide*. Bringing a food back is usually the first step of doing something
  with it.
- **Delete** asks first, in place of the buttons, in D36's words — *Delete “Greek yoghurt”? This
  cannot be undone.* **Delete** / **Keep it** — or, when a saved meal holds the food, is refused
  before anything is asked, directly above the buttons, in today's sentence (*“Breakfast bowl” uses
  this. Change the meal, or hide this instead.*). Nothing about when it asks or refuses changes;
  *Where it's used* above now shows the same meals before he presses it.
- **Delete, answered**, deletes the food and **closes the page**. No notice on the list: he has just
  answered a question naming it. A refusal that only arises at the delete (a meal began using it
  between question and answer) is shown on the page, which stays.
- **Keep it** changes nothing; the page stays with what he typed.
- **Typing, or Save, lets go of a delete question or refusal**, as today.

### 7. When the food is gone

**The page watches its food by id.** When it is not there — on opening, or at any moment after —
the page closes back to where it came from. That covers:

- his own **Delete** and **Join** (the page is already closing; the close is done once, guarded by
  "is this page still the one on top", the pattern the meal builder's close already uses);
- a page restored after the process ended, for a food that is no longer stored;
- a food joined into another from the list while its page was further down the stack — not reachable
  today, and handled anyway rather than drawn as an empty form.

A page never draws a form for a food that is not there, and never saves into one: `saveForm` on a
missing id is not a path.

### 8. Across process death

**The route survives; the unsaved page does not.** Navigation restores the back stack after the
system ends the app, so the page comes back on the same food, filled from what is stored. What was
typed and not saved, a review's answer, what was accepted from it and the teal marks are **not**
kept.

- **Why not keep the form.** The boxes and the review are one thing: a figure in a box that came
  from **Apply these changes** is stored as an estimate *because* the review marks its group as
  accepted (D54 §5). Keeping the boxes without the acceptance would save a model's figure as one he
  typed — D4's one hard line. Keeping both means putting `FormReview` — suggestions, accepted
  groups, undo snapshots, the model's raw answer — into saved state, and a review's answer carries
  text from a third party whose size is not bounded. Not worth it for a case this rare. The inline
  editor loses all of this today in the same way.
- **The list** loses its search, filters and choice on process death today and still does. A join
  in pick mode is lost the same way; `joinFrom` is spent as soon as it is read, so a restored list
  does not begin a join again that he had finished or walked away from.
- A review in flight when the page closes (Back, Save, Hide, Delete, Join, or process death) is
  dropped: its answer has no page to land on. The daily ceiling has already counted it (D54 §6).

### 9. What this amends

- **D30** (and the foods-and-meals manager design §3.2): *My foods* no longer edits in place. A food
  opens its own page; the list keeps finding, choosing, making a meal and joining. The manager is
  still one menu item, one screen, two tabs.
- **D36**: the delete question and its refusal are drawn at the foot of the food's **page**, in place
  of its buttons — the same words, the same order of asking and refusing. *From a food's own screen,
  picking the duplicate* now means: the page closes and the pick is made on the list. The cost it
  accepted — *every way off the editor lets go of its question* — reads *every way off the page*,
  and the tab switch it named cannot happen on a page. The join question, its words and its answers
  are unchanged, as is the survivor keeping its numbers (issue #19).
- **D54 §1**: *My foods → a food's editor* becomes *My foods → a food's page*. §11.4's *Save …
  closes the editor* becomes *closes the page*; *Cancel* is *Leave it alone*, or Back. Nothing about
  what is sent, what comes back, what is stored, or how it is shown changes.
- **The route `foods?food={id}`** (not a numbered decision; the code comment on `Destination.Foods`)
  is replaced by `food/{id}`.

Stated as unchanged, because each was checked: **D4** (every stored figure still says where it came
from; nothing on the page writes but Save, through `saveForm`), **D6**, **D7a** (provenance is still
shown where a figure is corrected; the page is that place), **D37** (new counts are plurals from
resources), **D38** (decimals kept), **D41** (the search), **D42** (the same ceilings refuse the same
figures), **D45**, **D48** (the display face for the heading, no new colours — the teal is D54
§11's), **D53**.

## What does not change

- **No stored shape changes.** No schema version, no migration, no backup-format change. The one new
  DAO statement is a `SELECT COUNT(*)` over `food_items` by `foodId`, which is already indexed
  (`index_food_items_foodId`); the saved-meals list is today's `mealsUsing` statement, observed.
- **Every write is today's**: `saveForm`, `hide`, `unhide`, `delete`, `merge`, each one transaction.
- **Every sentence on the editor today is on the page**, in the same words, except where §6 adds the
  hidden notice and §3 adds *Where it's used*.
- Logging, the day, the record, the meal builder's *Make a food*, and the *My meals* tab.

## Cost accepted

- **One more screen between him and a figure.** Correcting a food was: tap the row, type. It is now:
  tap the row, a page opens, type. The same number of taps; a screen change instead of an expansion.
- **Joining from a page crosses back to the list**, a visible jump (D36 already accepted two). The
  page's typing is lost when he does.
- **Leaving with unsaved changes does not ask** (the default in §2). A system Back gesture made by
  accident loses a review's applied figures, and asking again costs a call against the daily ceiling.
- **The row gives less at a glance**: one summary line instead of up to three figure lines — a food
  that knows both ways of counting shows per 100 g and what one weighs, not its per-one calories. The
  page shows everything.
- **"Logged N times" can move without his doing anything to the food**: a row deleted from a day
  lowers it; a join raises the survivor's; and the on-open repair of issue #22 can reattach a row
  whose food was deleted to a new food of the same name, raising that food's count. Each is the
  record changing, and the count tells the truth about it.
- **Nothing on the page survives process death but the food itself** (§8).

---

## Questions for the owner (each has a default the build takes if unanswered)

1. **Name and brand boxes.** The drawing's page has the name only as a heading. *Default:* keep Name
   and Brand as boxes under the heading, as today, since renaming happens nowhere else. *Other:*
   tap the heading to rename.
2. **Origin words.** The drawing labels groups *From the label* / *Typed by you* / *Estimated*;
   today's words are *This came off the packet* / nothing / *This came a close estimate* (and *a
   rough estimate*, *an estimate*, *copied from a past meal*). *Default:* keep today's words in the
   drawn position. *Other:* adopt the drawing's, which loses the confidence (*close* / *rough*) that
   D7 asks to stay visible, and adds a label to his own figures that today deliberately has none.
3. **Leaving with unsaved changes.** *Default:* Back and *Leave it alone* discard without asking, as
   today. *Other:* when anything differs from what is stored, Back asks *Leave without saving?* —
   one more question, but a review's applied figures cannot be lost to a stray gesture.

---

## D56 — A food counted in millilitres is stated per 100 ml (added 2026-09-24, public issue #5)

> Decided 2026-09-24 by the owner: option 1 of public issue #5 — millilitres work properly on a
> food's page, with no change to what is stored. The worked example below is invented.

**The rule.** When a food's unit — the unit box, as it stands, in any spelling the app already
recognises as the millilitre (`Portions.isMillilitres`: *ml*, *millilitres*, the Hebrew spellings) —
is the millilitre:

1. **Its per-one group is typed and shown per 100 ml**, the way a carton prints it: the heading reads
   *What 100 ml of it are worth*, each figure box is named "Calories per 100 ml" for a screen reader
   (public issue #3), the list row and the page head say "57 kcal per 100 ml", and a review (D54) is
   asked, answered, shown and accepted at that scale. The four boxes are judged by the per-100
   ceilings (D42: 1000 kcal, 110 g), since they are per 100 of something, like a packet's.
2. **It is stored per one ml, exactly as before.** This is the shape D53 §3 already writes when a
   model's per-100 ml worth teaches a food (its per-one figure in `ml`, divided by 100). The page only
   moves the decimal point: shown = stored × 100, saved = typed ÷ 100, as decimal shifts, never as a
   floating-point multiply — 0.57 × 100 is 56.99999999999999 as a double; shifted, it is 57. A box
   left as it opened hands back the stored figure itself (D54 §8.5), so opening a food and saving it
   untouched changes nothing stored.
3. **What one ml weighs is not asked.** It is a density, and the app assumes none (D4): nothing turns
   millilitres into grams. A food that already holds one keeps it, shown with its value and a line
   saying why it is not asked, until he clears it; the app deletes nothing. A review does not send it,
   and so does not cross-check against it.
4. **An amount of it is a number of millilitres**: typed, never stepped by one; capped at 5000 as a
   measure (the ceiling D42 already names for millilitres), not at the count of 100; the counting
   choice reads *In ml*, and a refused amount *At most 5000 ml at a time*. His ml food's worth on the
   proposal screen (D53 §4) is said per 100 ml.

*Worked example, invented:* a carton reads 57 kcal, 2.9 g protein, 4.7 g carbohydrate and 3.6 g fat
per 100 ml. A food is made with *ml* as its unit and those four figures typed under *What 100 ml of
it are worth*. It is stored as 0.57 kcal, 0.029 g, 0.047 g and 0.036 g per ml, with the source it was
given. 200 ml of it logs as 114 kcal. Opened again, the page shows 57 · 2.9 · 4.7 · 3.6.

**What it does not change.** No schema, DAO, migration, backup format or provenance rank. Decision 2
of the food model stands: per 100 g and per one are independent facts, and **volume is not a third
kind of fact** — a food still has one unit slot, and *ml* is a unit name the app understands. So a
food counted in ml cannot also be counted by a named piece such as a glass; that is the limit the
owner accepted. A food whose unit is anything else is exactly as before.

**Not reached.** The barcode scan's *add it yourself* form keeps asking per 100 g. A scanned packet
is cached as per-100 g figures in its own table and written to Open Food Facts in that shape; marking
it as per 100 ml would need a new stored field, which this decision does not make.

**Foods already counted in ml.** They were stored per ml, so they now show their figures × 100. One
whose figures had been typed per 100 ml into the old per-one boxes shows them a hundred times too
large, past the per-100 ceilings, and Save refuses them until they are corrected; opening it changes
nothing stored.
