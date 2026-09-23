# The foods and meals manager — design

> Settled 2026-09-16 from three drawn options; the two-tab one was chosen, with both selection
> routes added to it. This document is the record of what was decided and why. The build order is
> `docs/superpowers/plans/2026-09-16-foods-and-meals-manager.md`; where the two disagree, this wins.
>
> Covers issue #11 (a way in that is not "Add something") and the two requests made while
> choosing: a meal built from foods picked out of one's own list, and a meal made out of some or all
> of what a day already holds.

## 1. The problem

Looking after the food list and the meals is reachable only by starting to log something. The foods
manager is a text link above the food list on the "My foods" tab of the "Add something" screen, drawn
only when that tab has something on it; the meal builder is a button on its "My meals" tab. Nothing
on the day screen, in its menu, or in settings says either list exists.

Two things follow, and both were observed rather than theorised:

1. **Maintenance reads as part of logging.** Putting a duplicate right is a job of its own, which can
   come well after the meal that produced it.
2. **Building a meal is not an act of eating.** A salad is composed once, sitting down; today it is
   behind the button whose whole purpose is the thing done at every meal.

The decision recorded when the manager was built — the way in is the food list itself, "because that
is where he notices a duplicate" — is right about where a duplicate is noticed and wrong as the only
way in.

## 2. What every comparable app does

Checked before designing, because the shape of this is well-trodden: MyFitnessPal ("My Meals,
Recipes & Foods", from the More menu or the profile icon), Lose It ("My Foods" with tabs),
Cronometer (a permanent Foods tab), MacroFactor (a Library). Every one of them gives the owner's own
foods a named destination of its own, and none of them makes the owner begin logging to reach it.
Inside that destination, all of them use tabs.

Two findings worth carrying:

- **Cronometer already distinguishes "Retire" from "Delete"** — take it off my list but keep the days
  it is already on, versus remove the history with it. That is this app's hide-versus-delete,
  arrived at independently, which is evidence the distinction is real rather than fussy.
- **None of them can join two entries that are the same food.** MyFitnessPal's nearest offer is
  copying a food to make another one. So merging, which this app can do, is the one thing in the
  manager that has no equivalent elsewhere — and it is currently the best-hidden thing in the app.

## 3. The shape

**One item in the day screen's top-right menu**, beside "Your weight", "Your numbers" and
"Settings", opening **one screen with two tabs: My foods and My meals**.

Rejected, and why, so neither is re-proposed as new:

- **A bottom navigation bar** (Today · Foods · Meals · Weight). The most discoverable and much the
  largest change: the app has no such bar, so every screen's layout and the floating button's
  position would move for a destination visited now and then, not at every meal.
- **A hub that opens on what the record cannot account for** — how many foods know only a portion,
  which two entries look like the same thing. Genuinely attractive, because it surfaces the one
  thing no competitor does, and rejected only for the extra tap it puts in front of a plain list.
  **The idea is not dead**: those counts belong above the foods list, in the tab, where they cost
  nothing. Not built here.
- **Two menu items, two screens.** One tap to the exact list, at the price of a four-item menu
  becoming six and nowhere showing the two lists together.

### 3.1 The way in

One new menu item, "Foods & meals". Back from the manager returns wherever he came from: the day
when he came from the menu, the logging screen when he stepped out of it to fix a duplicate.

> Corrected 2026-09-16, during the build. This first read "back returns to the day, never into the
> logging screen", which was written as if the menu were the only way in. It is not — §3.1 keeps
> the logging screen's link deliberately — and a back arrow that skips a screen would throw away a
> half-finished log he had stepped out of. The intent behind the original sentence, that reaching
> the manager from the day never drops him into logging, holds under the plain back contract.

**The existing ways in stay.** The link above the food list on the logging screen and the "Build a
meal" button on its meals tab are right where they are: that is where a duplicate is noticed and
where a missing meal is felt. This design adds a front door; it does not move the side one.

### 3.2 My foods

The foods manager as it already exists — search, rename, correct each of the three facts with its
own provenance, hide, delete, join two duplicates — now with a home of its own rather than a link
from the logging screen. Nothing about what the screen does changes.

### 3.3 My meals

New. Every meal the owner has built: its name, what it comes to, and what is in it. Renaming,
changing what is in it and deleting it all happen from here, through the builder that already
exists.

**Logging a meal does not move here.** It stays on "Add something", where he is when he is eating.
This tab is where a meal is looked after, and a manager that also logged would make the two jobs one
screen again, which is the mistake being corrected.

### 3.4 Making a meal out of foods he picks

Hold a food on "My foods" to start choosing, then tap the others. A count appears above the search,
and the action is "Make a meal from these 3".

> Corrected 2026-09-16, during the build. This first read "a count replaces the screen's title",
> which the build could not do and should not have promised. The title bar belongs to the manager
> that draws the food list, not to the list itself, so a count written into it would have to reach
> up out of the list and would say "3 chosen" over the meals tab as well. The count is a line in the
> body, above the search, where it also outlives a search the way the choice itself does.

**The same gesture serves merging.** With exactly two chosen, joining them into one food is offered
beside it. Merging is pairwise and stays pairwise; choosing three offers only the meal.

**A food knows what it is, not how much of it he puts in.** So the chosen foods arrive in the
builder waiting for an amount, with the running total updating as he types, and **nothing is filled
in for him**. Where a food knows what one of it weighs, counting in whole ones or spoons is offered
beside grams; where nothing knows, that way of counting is shown with its reason, exactly as the
builder already does (D4: an estimate is never presented as a measurement, and a default typed into
a box the owner then saves is indistinguishable from his own number).

### 3.5 Making a meal out of a day

Hold anything on a day to start choosing, then tap the rest — all of it or some of it. The amounts
come from what was logged, so there is nothing to type: the sheet asks for a name, lists what is
going in with those amounts, and says what it comes to.

> **A second way in, added 2026-09-19 (D46, issue #24).** Accepting the model's answer to a
> described meal can go straight on to name those rows, with this same sheet drawn over that screen.
> It is not a second way of making a meal: the rows are logged first, and what names them is this
> path's own code, so everything below holds of it word for word.

**Afterwards the day shows those rows as one titled row** (the owner's decision, 2026-09-16). Not
one stored number moves: the day gains a title, the parts stay exactly as they were logged, and
opening the row shows them. This is the same titled-row display that already exists for a meal
logged from one he built.

Three consequences, all deliberate:

- **Rows logged at different moments can be pulled together.** The day's grouping is not a claim
  about when things were eaten — the items keep their own record — so grouping four rows under
  "Shakshuka breakfast" is a labelling act, not a rewriting one.
- **A row that is not attached to a food cannot join a meal**, because a meal is made of foods and
  amounts. This is rare — everything logged since the foods release attaches on the way in — and
  when it happens the sheet says which row and why rather than dropping it silently.
- **The meal is a definition from that moment on.** Changing it later does not change that day, and
  the day does not change it: the existing rule, unchanged.

### 3.6 What the new meal is made of

Each chosen row becomes one component: its food, its amount, and how that amount is counted —
weighed (grams) or counted out (whole ones). A row's stored portion decides which: a mass unit is
weighed, anything else is counted, and a row with no portion at all is counted as one of whatever
the food calls one, which for a food that has never known better is one "portion". That is the
existing arithmetic of logging, used backwards; no new rule is invented for it.

A component whose amount cannot be costed is refused **by name**, with the reason, before the meal
is made — never silently left out. That refusal is the likely first answer on the route added by
D46: a meal just described is where rows in units this app converts nothing into come from, and the
items stay logged on the day when it happens.

> Corrected 2026-09-16, after the build, by review. Two things this section got wrong.
>
> **"Each chosen row becomes one component" cannot be had.** A meal stores its parts keyed on the
> food, so putting one food in twice changes the amount rather than adding a second row. Two rows of
> the same coffee therefore made two components of which the second overwrote the first, and the
> meal came out worth one coffee where the sheet had just totalled two — silently, until the meal was
> logged. It is now **one component per food**: rows of one food in the same unit are added
> together, which invents nothing and leaves the meal worth exactly what the sheet said, and rows of
> one food in two different units — 100 g of bread at breakfast, 2 slices at lunch — are refused by
> the food's name, because adding those needs a weight for one slice and a food that happens to know
> one would still be having a row re-counted behind his back.
>
> **"A mass unit is weighed" is too wide.** The list of mass units holds kilograms, ounces, pounds
> and millilitres as well as grams, and the arithmetic of logging reads a weighed amount AS GRAMS.
> Half a kilo, which is what a described row keeps when the model answers in kilos, became half a
> gram — worth nothing, and worth nothing is a number, so nothing refused it. What is weighed is
> **the gram**, however it is spelled; what is counted is **the food's own unit**, and a row logged
> in some other unit is refused by name with both units said. There is no conversion in this app and
> D4 does not allow one to be invented here.

## 4. What this design does not do

- **It does not add the maintenance counts to the foods tab.** Worth doing, decided against here
  only to keep this change one thing (§3, rejected hub).
- **It does not touch how a meal is logged**, adjusted for one day, or shown on the day beyond the
  titled row that already exists.
- **It does not add "save this day as a meal" in the sense of the whole day at once** as a separate
  button. Choosing everything is the same gesture with every row ticked, and "All" is one tap.
- **It changes no stored number, no table and no schema.** Grouping rows under a meal writes the
  meal's identity onto rows that already exist.
- **It does nothing about Hebrew labels** (#9) or the typed-amount control (#1).

## 5. The decision for the record

Goes into `docs/superpowers/specs/2026-09-02-milestone-1-design.md` §3 as **D30** when this is
built: the manager gets a way in of its own; two tabs in one destination; a meal can be made from
foods chosen in the list or from rows chosen on a day; and a day whose rows became a meal shows them
under its name without a single stored number changing.
