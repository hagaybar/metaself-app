# MetaSelf — Milestone 1 design

**Date:** 2026-09-02
**Status:** approved by the owner, not yet planned or implemented
**Scope:** the first shippable version of the app, and the decisions that outlive it

---

## 1. Why this app exists

MetaSelf is a personal, single-user Android fitness tracker for one person: the owner. It is
modelled on MyFitnessPal in its basics — log food, see what that leaves you for the day — and
departs from it in one decisive way: **you describe a meal in plain words or photograph it, and an
AI model turns that into numbers.** You do not hunt a food database.

The competitor is not MyFitnessPal. A diet that is abandoned usually ends because **life gets in the
way — travel, stress, work — and it falls away**; nothing about the tracking itself fails. So the
app is competing with *not tracking at all*, and its first job is to make starting again feel light and to make resuming after an absence cost
nothing.

That single fact is the reason for several decisions below that would otherwise look arbitrary.

## 2. What the first version does

- **Setup, once.** Height, year of birth, sex, current weight, activity level, goal (lose / hold /
  gain, and how fast). Produces a daily calorie target and a protein / carbs / fat split, and shows
  the arithmetic behind them.
- **Today.** Calories remaining, the three macros, the list of what has been logged, one obvious
  button to add a meal.
- **Add a meal** by typing words, by photographing the plate, by repeating something eaten before,
  or by typing the numbers directly.
- **Confirm before saving.** The AI proposes items, portions and numbers; the owner edits and
  accepts. Nothing reaches the record unconfirmed.
- **Weight.** Log a reading; see a smoothed trend over weeks. The daily target follows that trend.
- **Any day.** Open, edit, and log to a past date. Logging late is always possible.
- **A streak**, counted from the record, plus a daily reminder the owner sets, plus a warning colour
  when today is over target.
- **Backup**: a file the owner can export and restore, and automatic backup to their own Google
  Drive.

Explicitly **not** in this milestone: reading steps or workouts from the fitness band; the
earn-calories-back mechanism that depends on it; barcode or label scanning; recipes; insight charts
beyond the weight trend; anything multi-user.

## 2a. Milestone 1 — CLOSED, 2026-09-05, by the owner

**Four days from an empty repository to an app in real use.** He judged it accomplished, and
this section records what that means so the claim can be checked later rather than taken on trust.

**Nineteen steps done, five more built and awaiting only time.** Thirty-five decisions, five risks
closed, nineteen plans, 216 commits, 748 tests. Shipped as v0.26.0-rc1 on his own phone, release
signed with a key that has never left this machine.

**What it does.** Food logged by describing a meal in words or scanning a barcode, with the amount
adjustable everywhere it appears. A daily target that computes itself from body and goal, follows the
smoothed weight trend weekly, and now measures itself against what his weight actually does. A goal
weight with distance, projection and a one-day celebration on arrival. Milestones on the way.
Streaks, an eating window, steps and workouts earning calories back, a daily reminder, export and
restore, an automatic daily copy, and products contributed back to the database he takes them from.

**What was dropped, and why that matters.** Photographs — not deferred, dropped, because use
showed that describing a meal does the same job without the weakness a photograph would bring.
The design said on day one that portion estimation from a photograph was the weakest part of the
whole idea (R3), and use confirmed it. **A feature correctly not built is a result.**

**What is deferred by choice, for about a month:** correcting a day's movement by hand, and badges.
Both are fixes or embellishments for problems that have not happened yet, and a month of ordinary use
will say whether they are wanted and in what shape.

**What is still unverified, and cannot be hurried.** The measured target needs a month of logging.
The milestones and the goal celebration fire when his trend reaches them. Contributing needs the
database to be missing a product. Exercise calories need him to wear the band. **None of these is
work left undone; they are questions only living with the app can answer**, and the record says so
rather than quietly counting them as finished.

**The one standing risk that never closes.** Losing the release signing key is unrecoverable, and it
is deliberately absent from every backup this app makes.

---

## 3. Decisions, with their reasons

Each of these was settled with the owner during the design interview. The reason is recorded because
the reason is what a later change has to argue with.

**D1 — It is a fitness app, not a general self-tracker.** The name is broad; the scope is not.
Body only: food, weight, and later movement.

**D2 — The AI provider is undecided, so it stays swappable.** All model access goes through one
narrow interface with one implementation behind it. Changing vendor is a small job by construction,
not a rewrite. This is built in milestone 1 even though only one provider exists.

**D3 — SUPERSEDED 2026-09-04 by D23. Originally: exactly one source of nutrition numbers, the AI's
own estimate.** No food database built or bundled; the AI answers "about 450 calories, 15 g protein"
directly, which is what makes home-cooked and restaurant food loggable at all. **That part still
stands and is still the main way to log.**

What no longer stands is "exactly one". Barcode scanning added a second and better source for
packaged food: a manufacturer's printed label, via Open Food Facts, recorded as `Source.LABEL`. D4's
source field is what made that possible without rewriting anything, which is precisely what D4 was
for. The "second, more trustworthy source of numbers for packaged food" listed under **After
milestone 1** below is therefore already delivered.

**D4 — Every stored number records where it came from.** An enumerated source (AI estimate, typed by
the owner, repeated from a past meal) travels with each entry, alongside the model's stated
confidence. The owner intends more sources later, ranked by credibility; this field is what lets
them arrive without a migration of meaning. **An estimate is never presented as a measurement.**

**D5 — The AI shows its assumptions, not just its answer.** Each proposed item carries the portion
the model assumed ("pita — 1 medium, 90 g"). A bare total is unarguable and therefore untrustworthy;
an assumption is correctable in one edit.

**D6 — Nothing is saved until the owner accepts it.** The estimate is a proposal on screen. Once
saved it is the owner's record, and no later version of the app silently re-estimates it.

**D7 — Low confidence is visible and stays visible.** A wrapped sandwich and an unidentifiable stew
must not be reported with the same certainty. Confidence shows on the entry and persists in the
record.

**D7a — AMENDED 2026-09-05, by correction. Provenance belongs where a number is ACCEPTED,
not under every row of the day.** The first build printed a line under each logged item saying where
its figures came from — "Estimated, high confidence", "Repeated from an earlier meal". That line was
asked for and removed, rightly: upholding transparency can in some cases add information that is not
relevant to the daily display of a fitness app.

D4 is about what the RECORD stores and D7 about a low guess not passing for a certain number. Neither
asks for a line of provenance under every row of a daily list, and treating them as though they did
was applying a rule about storage to a question about a screen. He opens that screen to see what he
has eaten, not to audit it.

Where it still is, and why that is enough: **on the proposal screen**, at the moment he is deciding
whether to accept a figure, which is when it can actually change what he does; **in the item editor**,
if he goes looking; **on the record and in every export**, unchanged. Nothing about D4 moves — the
source is still stored on every number ever logged.

**D8 — Logging never depends on the network.** If the model is unreachable or fails, the owner can
still type the numbers or repeat a past meal. The failure mode of a habit app is the day it refuses
to work.

**D9 — The daily target is computed, and its arithmetic is shown.** Resting burn from body and age,
multiplied by an activity factor, shifted by the goal rate. Two guardrails: it will not propose a
target below a safe floor (the exact floor is chosen and justified in step 2's plan, not here),
and a requested rate of loss that implies one is stated plainly and then allowed — the owner's body, the owner's call, but never silently.

**D10 — Macros are derived, not invented.** Protein from body weight, a floor for fat, carbohydrate
takes the remainder. Reasoning shown, as with the target.

**D11 — The target follows the smoothed weight trend, weekly, and announces when it changes.**
Never from a single day's reading — daily scale noise is water. A number that moves under you
without saying so is a number you stop trusting.

**D12 — Movement earns calories back only as surplus, discounted and capped — designed now, built
later.** The target already assumes a normal day, so a normal day earns nothing; only movement above
the owner's own typical amount counts, only a fraction of it is credited (wrist-band burn estimates
run high), and a daily ceiling applies. Nothing of this is built in milestone 1 because no movement
data source exists yet.

**D12a — ADDED 2026-09-05, by correction. Movement is shown for its own sake, not only as
an input to the arithmetic.** D12 was written entirely as a rule about calories, and the first build
followed it literally: an ordinary day earned nothing and was therefore said nothing about. What was
missing: activity is not only an input to a calculation, it is also motivation.

Two silences had been run together, and only one of them was right:

- **Not crediting an ordinary day is correct** and does not change. The target already assumes he
  moves a normal amount.
- **Not showing an ordinary day was wrong.** A step count is worth seeing whether or not it buys
  anything, and hiding it until it earns calories makes the app's only view of his body's effort
  conditional on that effort being unusual.

So the day screen shows the steps and the usual day beside them, plainly and without being opened —
the requirement was that they be reachable easily, not behind doors. The credit, when there is one,
is an additional line rather than the reason the count exists.

**D12b — ADDED 2026-09-05. Exercise joins steps as ONE number, never as a second one added to it.**
A band that records a ten-kilometre walk writes it twice: as steps, and as a session with calories
attached. Crediting both would pay the owner twice for one walk — the exact error D12 exists to
prevent, arriving through a different door.

So a day has **one** figure for what its movement cost, and it is the LARGER of the two readings, not
their sum:

- what the day's steps are worth, as now; and
- what the band says the day's active calories were.

Whichever is bigger wins. A walk is captured by either and counted once. A swim moves no steps at
all, so the band's figure carries it. Weights likewise. A day with no band and no session behaves
exactly as it does today, because the steps reading is then the only one there is.

**Max rather than sum is deliberately the stingy choice.** A swim on top of a normal walk is
under-credited by whichever of the two is smaller. That is the safe error and the app already prefers
it: eating back calories nobody burned is invisible and stalls everything, while being slightly
under-credited on a big day is something the owner can see and eat through.

**Everything downstream is unchanged.** The normal day is still the rolling median — of this figure
rather than of steps — so his own typical exercise is baked into the baseline and earns nothing,
exactly as his typical walking does. The three-quarters discount and the cap still apply, and the
band's calorie estimates running high is precisely why the discount was there in the first place.

**Sessions are also shown by name**, because a screen saying "Swimming, 45 minutes" is worth more to
somebody deciding whether to go for another session than the same energy folded into a number (D12a).

**D12c — ADDED 2026-09-05, from the owner's question. There are TWO ways a walk gets counted twice,
and they are defended against differently.**

**One walk, two kinds of record.** A band writes a long walk as steps AND as a session with calories.
That is D12b's job: a day's movement is the LARGER of the two readings, never their sum.

**One walk, two apps.** Two fitness apps on one phone can both write steps.
Health Connect's **aggregation** deduplicates Activity data by the user's own app-priority list and
keeps one app's version; raw records do not, and summing them would double-count silently.

**This app therefore reads through the aggregation API, and that is now a correctness requirement
rather than a convenience.** It arrived as a fix for something else — the raw read returned only its
first page and reported a month of walking as four days — and only afterwards turned out to be the
thing standing between the owner and a permanently inflated step count. **Anybody tempted to go back
to reading records should read this paragraph first.**

Two limits worth writing down. Only Activity and Sleep are deduplicated this way; other data types
are summed across every app that wrote them, so anything added later must be checked rather than
assumed. And **exercise sessions themselves are not deduplicated for display** — two apps recording
the same walk produce two entries on screen. That is cosmetic rather than arithmetic, since sessions
are shown and never counted, but a day listing "Walking · 45 min" twice is still wrong and is
collapsed by name and duration.

**D12d — ADDED 2026-09-05, by request. A day's movement can be corrected by hand, and says
when it has been.** Health Connect will sometimes be wrong — a phone left in a car, two apps that
disagree, a band that credits a bus ride. A day showing something unreasonable must be fixable by
hand.

Four constraints, and the third is the one that keeps it honest:

- **The correction lives here, not there.** This app never writes to Health Connect. Another app's
  store is not its to edit, and a correction pushed back would then be read by everything else on
  the phone as though it were measured.
- **A corrected day says so.** The same principle as every other number in this record: it carries
  where it came from (D4). "You set this" is a different fact from "your phone counted this", and a
  screen that shows them identically is lying by omission.
- **A correction is a fact about that day, not a rule.** It applies to the day it was made about and
  is not carried forward, because the reason for it — a car journey, a forgotten phone — belongs to
  that day.
- **It feeds the baseline like any other day.** A corrected day is the owner's best knowledge of what
  he actually did, which is exactly what the usual-day median should be built from.

**D30 — ADDED 2026-09-16, issue #11, the owner from three drawn options. The foods and the meals he
has built have a way in of their own: one item in the day's top-right menu, one screen, two tabs.**
Looking after those two lists was reachable only by starting to log something, so putting a
duplicate right — a job done sitting down, well after the meal that produced it — read as part of
eating. This reverses a deliberate arrangement and the reversal must say so: the way into the foods
manager was the food list on the logging screen, "because that is where he notices a duplicate".
That reason is right about where a duplicate is *noticed* and wrong as the only way in. **The
existing ways in from the logging screen stay exactly where they are** — this adds a front door, it
does not move the side one. The full design is
`docs/superpowers/specs/2026-09-16-foods-and-meals-manager-design.md`, whose §3.1 was corrected
during the build (back from the manager returns where he came from, not always to the day; the
correction note is in that file).

- **Two other shapes were considered and declined, and are recorded so neither returns as a new
  idea.** A **permanent bottom navigation bar** is what the comparable apps do and is the largest
  change of the three — every screen's position would move, for a destination visited occasionally rather
  than hourly. A **hub that opens on what the record cannot account for** — how many foods know only
  a portion, which two entries look like the same thing — is genuinely attractive, because it
  surfaces the one thing no competitor does, and was declined only for the extra tap it puts in
  front of a plain list. **That idea is not dead**: those counts belong above the foods list later,
  where they cost nothing.
- **Logging did not move into the manager.** The meals tab is where a meal is *looked after*;
  logging stays on "Add something", where he is when he is eating. A manager that also logged would
  make the two jobs one screen again, which is the mistake being corrected.
- **A meal can be made from foods chosen in the list, or from some or all of a day's rows.** Both
  are additions the owner made while choosing between the three shapes. In the food list, choosing
  is held outside the search, so it survives one — collecting the parts of one salad across several
  searches is the point, and a search that quietly emptied the choice would make it impossible. At
  **exactly two** chosen, the same gesture also offers to join them into one food instead, because
  merging is pairwise and has no meaning at three.
- **A food knows what it is, not how much of it he puts in.** A chosen food arrives in the builder
  waiting for an amount with **nothing filled in**, and becomes part of the meal only when he says
  so. This is D4 applied to a form default: a number typed into a box he then saves is
  indistinguishable afterwards from a number he entered himself.
- **A day whose rows became a meal shows them under that meal's name with NOT ONE STORED NUMBER
  CHANGED.** The gathering writes the new meal's identity onto rows that already exist; it does not
  recompute, re-cost or re-source anything.
- **Grouping rows logged at different moments is a labelling act, not a rewriting one.** The items
  keep their own record of when they were eaten, so nothing that reads times — the day's order, the
  eating window — is told a different story by the grouping.
- **A row that cannot become part of a meal stops the whole thing and is named. Half a meal is worse
  than none.** A row not attached to a food, a row whose food has since been deleted, a row whose
  amount cannot be costed, a name another meal already holds, and a row that vanished between his
  ticking it and his naming the meal: each refuses the whole gathering and says which row and why,
  rather than producing a meal quietly smaller than what he chose.
- **What this does not do.** The maintenance counts above the foods list are not built. Bilingual
  names (#9) are untouched, so the two halves of a cross-language duplicate still have to be found
  by eye before they can be joined. The typed-amount control for logging (#1) is not part of this
  either — the amount box described here belongs to the meal builder, not to the day.
- **AMENDED 2026-09-24, D55: a food no longer edits in place.** *My foods* keeps finding, choosing,
  making a meal and joining; tapping a food opens a page of its own, which corrects, reviews, hides
  and deletes it. The manager is still one menu item, one screen, two tabs.
  `docs/superpowers/specs/2026-09-24-food-page-design.md`.

**D28 — ADDED 2026-09-14, issue #10. The way in is the owner's own foods; describing is what a
miss falls through to.** This reverses a deliberate arrangement and the reversal must say so: until
now describing a meal was the day's main action and held the floating button, where Android puts a
main action — it read "Describe a meal" — and the list of what he had eaten before was a quiet link
beside it. `DayScreen` stated that as a decision in so many words. Now the floating button reads
"Add something" and opens the search over what the owner has already logged; describing is offered
when that search finds nothing.

- **The reason is entity hygiene.** Nothing resolves a model proposal against food already on the
  record, so every described meal manufactures fresh items. Putting his own foods in front of
  describing stops the duplicate being created rather than detecting it afterwards. When the search
  genuinely misses, a new food IS the right outcome — which is why every empty state in that screen
  falls through to describing instead of stopping, carrying whatever was typed into the search so a
  miss costs a tap and not a retype.
- **The path changed because the matching could not.** Handing the model the owner's food list so
  it could answer with what he already has is closed by D16: exactly one thing leaves the phone, and
  a list of his foods is history. Matching in the app would have to be fuzzy, and the search's own
  matching is a plain case-insensitive contains on purpose — anything cleverer would need a stemmer
  for two languages and would fail in ways he could not predict. Both being closed, what was left
  to change was the order of the paths, not the resolving.
- **This is not a cost argument.** Describing is cheap and what a model call costs is irrelevant to
  this decision. Recorded explicitly so nobody later re-justifies the arrangement on price and then
  reverses it when the price changes.
- **D8 is untouched.** Typing the numbers is still one tap from the day, in the quiet row, named
  "Type the numbers" so it cannot be confused with the floating button. It is never behind the
  search, and logging still never depends on the network. Describing on its own stays one tap away
  too, for the case the owner already knows is new.
- **When a search misses, the describe offer appears only if BOTH lists missed.** The sentence
  saying nothing matched is about the list in front and stays per-tab; the offer is not. A search
  on "My meals" that misses there but matches a food would otherwise invite a duplicate of something
  he already has, one tab away.
- **Correcting an item is not "typing the numbers".** The same editor serves both entrances, so it
  now takes its heading from which one opened it: "Type the numbers" when adding by hand, "Correct
  this item" when the day's edit tap opened it. One heading for both would tell the owner he is
  starting from nothing when he is fixing an estimate.
- **A note, not a decision: the photo path probably should not sit behind the food search.**
  Photographing a restaurant plate is precisely the case where the owner already knows it is not in
  his list. When that path arrives it should be argued on its own; nothing is built for it here.
- **What this does not fix.** Searching `יוגורט` will not find a food stored as `Yoghurt`, so a
  describe-and-duplicate still happens across languages. That is the bilingual-names issue (#9) and
  the merge-and-rename one (#7), and this decision does not pretend otherwise. Nor does anything
  here match a described item against an existing food *entity* — there are no entities yet (#2,
  #3). This changes the path so the duplicate is not created; it does not add matching.

**D27 — ADDED 2026-09-05, by request. BUILT: an eating window.** The requirement is not eating
late, and having the app keep tab on that. The feature is choosing a window — a start hour and an
end hour — and **it applies only from a given day forwards, never backwards.**

That last point is the whole design, and it came with the request:

- **A window is set with a date, and days before it have none.** A rule invented today does not get
  to judge last Tuesday. Nothing already logged is re-scored, no past day acquires a red mark, and no
  streak is retroactively broken by a decision he made after the fact.
- **Changing the window changes it from now.** The old one still governs the days it governed, so
  the record of whether he kept to what he had actually decided stays true.

What it does with that, in the app's existing grain:

- **Today may say a meal is outside the window; the past states it without colour** (D14). The point
  is a decision he is keeping, not a scorecard he is failing.
- **A count of days kept, from the record**, the way the streak is counted rather than stored (D13) —
  so filling in a day late repairs it, and no counter can drift out of step with what he actually
  ate.
- **No new notification.** D15 says one reminder exists and no other. A warning that the window is
  about to close is a second one, and would need to be argued for on its own rather than smuggled in.

*Amended by D29: a second kind of window, measured rather than declared.*

**D29 — ADDED 2026-09-14, issue #5. A second kind of eating window, measured rather than
declared.** D27 describes the window as choosing a window — a start hour and an end hour. This adds
a second kind beside it: the owner sets a *ratio*, and the window is measured after the fact from
the first and the last input of the day. It amends D27 rather than replacing it.

- **A second kind, not a replacement.** Both exist. The owner chooses which he is keeping, and days
  the old kind governed keep being read by the old kind's rule. Nothing about the fixed window's
  behaviour changes.
- **The verdict says which kind it is, and neither kind can be read as the other.** A count of
  meals inside and outside does not describe a span, and a span does not describe a count, so a
  day's verdict is one shape or the other and never one shape with the other's fields left empty.
  The two still add into one count of days kept, because that count asks only whether a day was
  judged and whether it was kept — never how it was measured.

  > Corrected 2026-09-17, after a fidelity review of the build. The last sentence is no longer true.
  > D31 made the two kinds count in different UNITS — the fixed kind in days kept, the measured kind
  > in stretches kept — and a count that adds them would be adding two things that are not the same
  > thing. Each kind now counts its own, the screen shows whichever the rule on the day was, and the
  > shared day-counting function's measured branch is documented as unreachable in production. The
  > rest of the bullet stands: the verdict still says which kind it is, and neither shape is ever
  > read as the other.
- **The ratio is written fasting first, and is also said in words.** "16/8" is sixteen hours fasting
  and eight eating. Every screen that shows the figure also shows "16 hours fasting, 8 hours eating",
  so the meaning is never left to be inferred from a slash.
- **Judged to the minute.** 09:30 to 21:15 is 11h45, not "about twelve hours". The record already
  carries the minute. The **fixed** window keeps its whole hours: the hours-not-minutes habit is
  D27's and stays D27's alone.
- **A day with one input is not judged** — neither kept nor broken, and not in the denominator of
  the count. One input is a span of zero and would trivially keep any ratio, which would be a
  compliment for having logged almost nothing. This follows D27's own precedent of declining to
  judge a day no window governed. Its consequence is accepted and recorded here rather than
  discovered later: **the "seven days in a row" encouragement will rarely fire under a measured
  window**, because a single quiet day in the week makes the week un-perfect by being unjudged
  rather than by being broken. The app has no opinion about a day it declined to judge, and
  inventing one to keep a compliment alive would be exactly the retroactive scoring this whole
  feature refuses. Loosening that condition is a separate decision and is not taken here.
- **Meals that cannot be timed are excluded before the first and last are taken.** A meal written
  down on a different day from the one it belongs to has no trustworthy hour, and one meal typed two
  days late would otherwise stretch the day's span to fifty hours and produce a false verdict on its
  own. The exclusion comes first, then the earliest and the latest.
- **The twenty-four hours are the calendar day**, midnight to 23:59, which is the grain the streak,
  the day's totals and the fixed window already judge by. **Its consequence is accepted**: a meal
  after midnight becomes the *next* day's first input and starts that day's span early, so eating
  from 14:00 to 01:00 two days running reads as eight hours on the first day and twenty-one and a
  half on the second. There is to be no rolling period, no session detection and no special handling
  of meals near midnight.

  > Corrected 2026-09-17. This bullet's calendar-day reading was replaced by D31: the
  > measured window judges eating stretches bounded by the fast, and not days at all. A ratio is a
  > statement about hours and midnight is an artefact of the log, so taking the first and the last
  > input between midnight and 23:59 never does the sum the ratio actually asks for — it said nothing
  > about a coffee twelve hours after dinner on a fourteen-hour fast. The consequence
  > accepted here — eating from 14:00 to 01:00 two days running reading as eight hours and then
  > twenty-one and a half — is therefore the defect D31 fixes, not a cost still being paid. The rest
  > of D29 stands.

- **It never applies backwards.** The measured kind goes through the *same* from-this-day-forward
  gate as the fixed one — D27's central point, and the one that matters most here, because
  everything needed to score last month is already in the record and only the decision not to
  prevents it.
- **Nothing new is said during the day beyond a closing time.** Before the day's first input the app
  says nothing at all about the measured window; after it, it says when eating is done — "Eating
  done by 17:40". No countdown, and **no new notification**: D27's reading of D15 holds unchanged.

  > Corrected 2026-09-17, after a fidelity review of the build. That string no longer exists. The
  > sentence is now "Started eating at 12:00 — done by 22:00.", because a stretch is bounded by the
  > fast rather than by midnight (D31): when it began is what the closing time is measured from, and
  > a closing time falling after midnight says "tomorrow" in words rather than printing a bare time
  > that reads as this morning. One more sentence has been added since, and it is the only one the
  > app says while a stretch is still running: once an open stretch has run past the eating hours,
  > today says "You have been eating for 38h; your ratio allows 8." and the closing time goes silent
  > — one sentence about one open stretch, replacing the other rather than joining it. Both remain
  > within this bullet's actual rule: no countdown, and no new notification.

*Amended by D31: the measured window judges eating stretches bounded by the fast, not calendar days,
and an open stretch that has run past the eating hours says how long it has been.*

**D31 — ADDED 2026-09-17, the owner. A ratio is a statement about hours, so the measured window
judges eating STRETCHES bounded by the fast, not calendar days.** Midnight is an artefact of the
log. D29 measured a span from the first and the last input between midnight and 23:59; this replaces
that reading of the same ratio and leaves the rest of D29 as written — two kinds of window side by
side, the ratio written fasting first, judged to the minute, meals that cannot be timed excluded
first, and never applying backwards.

- **An example shows why** (ratio and times illustrative). Eating stops at 22:00 on a 14/10 ratio, and the
  question is whether coffee at 12:05 the next day is alright. Fourteen hours from 22:00 is 12:00, so
  it cleared by five minutes — but the app never did that sum, and would have said the same nothing
  about coffee at 10:00, twelve hours after dinner and well short of the fast the ratio
  sets.
- **A stretch is a run of inputs where each gap is shorter than the fast.** A gap of at least the
  fasting hours closes one, and the next input opens another. A stretch is closed once the fast has
  passed since its last input; it is judged only when it is closed and holds two or more inputs; and
  it is kept when its span — first input to last input — fits the eating hours.
- **A stretch belongs to the day it BEGAN on**, and never to two. Giving it to both days would make
  one late dinner break both of them.
- **An open stretch is not judged, and the screen says so rather than guessing.** This is the cost
  the owner accepted before the work began, and it is real: a day was over at midnight and could
  always be judged, a stretch is not over until the fast completes, so "was last night alright?" may
  have no answer until the following afternoon.
- **Once an open stretch runs past the eating hours, today says how long it has been, without
  judging it** — "You have been eating for 38h; your ratio allows 8." Added during the
  build, because review found the rule as first written goes silent for exactly the person failing
  to keep it: a ratio whose fast is never reached — 16/8 with meals at 08:00 and 20:00, every gap
  twelve hours — is ONE stretch that never closes, so nothing is ever judged and the app says
  nothing at all. It is a fact about the stretch open RIGHT NOW, whichever day it began on, which is
  what makes it reach that case; it is said on today and on no other day, and it judges nothing.
  > Corrected 2026-09-18. "How long it has been" is the EATING — first
  > input to last — and it speaks only once that has run over, by the same rule the verdict judges
  > with (`EatingStretch.ranOver`). It first shipped measuring first input to NOW, so every hour of
  > the fast after the last meal was counted as eating: eating from 12:00 to 21:00 on a 14/10 is
  > inside the ten, and yet at 09:00 the next morning the line would say "You have been eating for
  > 21h". It fired from the closing time until the fast completed. Durations are now hours and minutes, in the
  > shape the span uses ("13h 0m; your ratio allows 10h"). Where a stretch begun today stopped
  > inside its hours and is past its closing time, the still-open line now speaks on today — the
  > one gap the two sentences about now leave, which §3.0a does not allow to be silent.
- **Gaps are measured between moments, not between clock readings**, so an hour the clocks moved
  cannot manufacture a fast or erase one. The rule this replaces differenced minutes-from-midnight,
  which is only meaningful inside a single day.
- **A read declares where it was cut.** Nothing on screen reads a whole lifetime of meals, and a
  stretch that outran the days a screen read is treated as OPEN rather than having a span invented
  from where the read happened to stop. D4 forbids presenting an artefact as a measurement, and a
  span that is really the edge of a read is exactly that.
- **The tally and the weekly compliment count stretches**, and only closed, judged ones. That is the
  unit everything else now counts in.
- **The fixed-hours window keeps its verdict, its sentences, its day mark and its day tally.** "Eat
  between 14:00 and 22:00" is a statement about a calendar day and is still read as one. Two kinds
  of window, two shapes of answer. Two things about it did change, and both are below.
- **The weekly compliment under fixed hours is now clamped to the day those hours began, and so is
  silent in a week the window's kind was switched.** It counted the seven days ending yesterday with no
  such clamp, so switching from a ratio to fixed hours mid-week pulled in days a RATIO had governed
  and scored each of them from its own meals alone — a day-sliced reading, narrower than the record,
  which calls an evening's eating kept and never sees it run on to noon the next day. Seven
  flattering days, and a compliment he had not earned. The consequence is accepted rather than
  worked around: in a switch week, seven days the hours in force actually governed do not exist, so
  nothing is said. A run those hours really did govern still earns it. (The day screen's tally and
  the settings tally were always clamped this way.)
- **The open-or-shut ring belongs to the fixed hours alone; a ratio lends it nothing.** A stretch
  does have an open-or-shut state, and for one release a past FIXED day paged back to while a ratio
  governed today drew a ring filled by whether eating is going on right now. That describes a
  different thing from the one that day was scored by, and beside that day's own verdict it reads as
  belonging to that day. Such a day now draws its tally with no ring — which is still more than it
  drew before this change, when it drew neither mark and lost its only way into window settings.
- **No stored data changed.** A stretch is computed from times already on the record: no schema, no
  migration.

*Amended by D32: today gives a time to act on rather than a state to read.*

**D32 — ADDED 2026-09-18, by request. Under a ratio, today tells him a TIME he can act on: when the
fast allows the next meal, and when the window wants the last one.** The requirement: on waking, be
greeted and told that if the fast is being kept he can eat from X; after the first meal, that if the
window is being kept the last meal is by Y; and a marker of how many stretches were kept since the
ratio was set. Design: `docs/superpowers/specs/2026-09-18-ratio-says-a-time-design.md`.

- **Every sentence about the future is conditional** — "if you're keeping your fast", "if you're
  keeping your window" — because the app cannot know a meal is the last one until the fast after it
  completes, and assumes that nothing logged means nothing eaten. That is how the requirement was
  put, and it is the only honest phrasing there is.
- **Exactly one sentence on today, from five situations**: nothing under this ratio yet (nothing);
  the fast complete and nothing since ("Your 14 hours were up at 12:00 — eat when you like.");
  before the closing time ("If you're keeping your window, last meal by 22:00."); past it with the
  eating inside the hours ("If you're keeping your fast, next meal from 12:00."); past it with the
  eating over ("You've eaten for 12h 0m — next meal from 12:00 if you're keeping your fast.").
- **"Good morning." opens it before noon, and only then**, because the same sentence is said all
  evening.
- **The tally is words, counted from the day the ratio was set**: "Kept 10 of 14 stretches since
  3 Sep", replacing the bare fraction counted over the last fortnight.
- **Amends D29 item 23** ("before the first meal the mark says nothing, because nothing true can be
  said") — under stretches something true can: when the fast completes. And **amends D31's live
  line**, which is now the first half of the fifth sentence. The still-open line keeps its place on
  past days, as do the span and the one-input line.
- Considered and not added: an "I'm done eating" button. Certainty, at the price of a tap every
  evening, a wrong answer when one is forgotten, and a second record that can contradict the log.

**D33 — ADDED 2026-09-18, the owner. A meal shows when it was eaten, and he can correct it on the
day.** The ratio's every time is derived from meal times, the app stamps a meal with the moment it
was LOGGED, and the day has never shown a time at all. Offered a time field on every logging screen
or a correctable time on the day, he chose the day. Design:
`docs/superpowers/specs/2026-09-18-eaten-at-design.md`.

- Each meal on the day shows its time; tapping it changes it. One time per meal, since a meal's rows
  carry none of their own, so a group moves together.
- Never in the future, and always within the day the meal is on.
- A meal logged onto an earlier day — untimed today, and left out of the window — counts once its
  time is set. That is the case the requirement names: something missed, filled in later, at the
  time eaten.
- Correcting a time corrects the record, and verdicts follow it. That is not a rule reaching
  backwards (D27): the ratio in force that day is still the one that judges it.
- Logging itself is unchanged: no new step on any screen. No schema change — the time is already
  stored.

**D34 — ADDED 2026-09-18, the owner; decided 2026-09-14 while settling issue #1, and built for issue
#23. Every item the model proposes comes with a number greater than zero and a unit.** The prompt asks
for a best estimate always — lower the confidence rather than leave the amount out — and no longer
offers "0 and \"\"" as an answer. A reply in which any item lacks one is asked again ONCE, with an
instruction from the app naming the items (at temperature 0 the same request gets the same answer);
if it still lacks one, nothing is logged and the owner is told which items, and how to say amounts in
his description. The second ask is a second paid call and is not made past the day's ceiling.

- **Why.** An item logged with a unit and no amount cannot join a meal — reading it as one of that unit
  once made a 250 kcal row into one gram — and on the day it read "amount not stated", exactly like
  an item with no unit at all, which can. The owner met the refusal with no way to tell which rows
  would cause it (#23).
- **Cost he accepted when deciding it.** The model will sometimes invent a plausible amount rather
  than admit it does not know, and that guess looks like any other number. The item is still an AI
  estimate, with its confidence, and D4's labelling is what carries that.
- **Rows already on the record** keep their shape. Opening one on the day asks how much, and the
  amount he types is recorded — the figures stay as stated, the words stop saying "amount not
  stated" — after which it joins a meal. The refusal says so.
- Not to be confused with the owner's numbered answers of 2026-09-14 kept outside this document, where
  this was his seventeenth; that list is not the D-numbering.

**D35 — ADDED 2026-09-18, the owner. A drink is one item.** A cappuccino, a latte, tea with milk, a
smoothie, juice, beer: the model returns it as a single item counted in its usual serving — "Cappuccino,
1 cup" — not as espresso and milk. Amends the rule that every meal is broken into its parts, which
stands for everything on a plate.

- **Why.** Described, a milky coffee can come back as espresso and milk, two rows. Keeping it as the
  thing drunk then means gathering the two rows into a meal, and — before #26 was fixed — typing
  grams for the milk. A drink like that is thought of as one thing, and is logged again as one thing.
- **D5 still holds.** The assumption is still shown: the serving is the item's amount, and the size
  the model assumed goes in its note when it matters.
- **Cost accepted.** No separate milk and espresso foods to reuse elsewhere, and "1 cup" is the model's
  idea of a cup: a large one is 1.5 of it, or a correction. A drink he wants broken down, he can still
  describe that way.
- Milk poured over cereal or cooked into a dish stays an ingredient of that food.

**D36 — ADDED 2026-09-19, issue #16; the recommended option, taken under the owner's standing
instruction of 2026-09-19 to choose the recommended option. Deleting a whole food or a whole saved
meal asks first, naming what will go; joining two foods asks first on both routes; and every such
question or refusal appears where he can see it.**

- The question is *Delete “Greek salad”? This cannot be undone.* with **Delete** and **Keep it**,
  asked in place of the Delete button — the way joining already asks — not on a screen of its own.
- **Why ask rather than undo.** Neither can be put back by the owner. An undo would have to hold a
  food's names and aliases, its brand, its three facts with their provenance, and what one of it
  weighs; or a meal's parts with their amounts. A question costs one tap and holds nothing.
- **The day's one-entry delete keeps delete-then-Undo** (#25): a row is cheap to restore exactly,
  and asking before every row would slow the screen he uses most.
- **A food a saved meal uses is refused before anything is asked**, and the refusal is drawn in the
  food's editor directly above its buttons — where he pressed Delete, with Hide (which the refusal
  recommends) still in reach. Asking "Delete it?" and then refusing would be a question with no real
  answer; refusing somewhere he cannot see would look like nothing happened.
- **Joining warns on both routes, in one wording.** From a food's own screen, picking the duplicate
  now asks exactly what choosing mode asks — which food stays, that it answers to both names, that
  this cannot be undone — with **Join them / Not now**. Same irreversible act, same question, one
  piece of screen drawing it; the screen scrolls to it, because the duplicate he picked may be far
  down a long list.
- **Cost accepted.** The questions are drawn in place, not in a pop-up, so they are not modal: every
  other control stays live, and every way off the editor lets go of its question — though switching
  the manager's tab to Meals and back is not a way off, and leaves a food's question up. Pressing
  **Join with a duplicate** moves the screen up to the pick prompt, away from the food and probably
  its duplicate, and picking one moves it to the question: two visible jumps, each to where the next
  answer is. Keep it changes nothing; Not now joins nothing, and on the ticked-two route it also
  ends choosing, letting go of the ticks. No schema change.
- **AMENDED 2026-09-23, issue #19: what a join carries across.** The question says the food that
  stays keeps its name and its numbers, and that promise now governs the figures: **every group it
  holds stays, whatever the ranking would say** — a label on the absorbed food does not replace a
  figure he typed on the one that stays. Only a group it lacks (per 100 g, per one) is filled from
  the absorbed food, before that food is deleted and in the same transaction, with its provenance
  unchanged: source, confidence and the date it came to be believed. **What one weighs travels only
  to the same "one"**: it is carried when the food that stays takes the absorbed food's per-one
  group, or already counts in a unit of the same name (case and spacing aside; a food naming no unit
  counts in portions); otherwise it is left behind, because one bar's weight set against one slice
  is a measurement nobody made (D4). Nothing is averaged or worked out. Where both hold a group, the
  absorbed food's goes with it, unannounced — the question already said whose numbers stay. No
  schema change, no new wording.
- **AMENDED 2026-09-24, D55: the delete question and its refusal are drawn at the foot of the
  food's own page**, in place of its buttons, in the same words and the same order; *picking the
  duplicate from a food's own screen* now means the page closes and the pick is made on the list,
  with its search kept. *Every way off the editor* reads *every way off the page*, and the tab
  switch named above cannot happen on a page. The join question and its answers are unchanged.
  `docs/superpowers/specs/2026-09-24-food-page-design.md`.

**D37 — ADDED 2026-09-19, issue #15, extended by issue #27; the recommended option, taken under the
owner's standing instruction of 2026-09-19 to choose the recommended option. Where a sentence on
screen promises more than the app does, the words change to what is true, rather than the app
growing to match — unless the words were right and the app wrong.**

- **Add something** says what each list does: *Tap a meal and it goes on the day you are on, as you
  built it. Tap a food and it asks how much first. Nothing is sent anywhere.* Logging a food is not
  made one tap: an amount is the one thing the app will not fill in for him (D4).
  **AMENDED 2026-09-24, public issue #21 (from 0.48.0):** a meal's tap now opens it, so the meal half
  reads *Tap a meal to open it: Log it puts it on the day you are on, as it is or changed for
  today.* — the words changed to what the app now does (see step 9).
- **The meal builder** says that what is *in* the meal is kept as he goes, and that a food picked but
  not yet put in is not there when he comes back: *Nothing to save. What is in the meal is kept as
  you go, so you can leave and come back. A food you have picked but not put in yet is not in the
  meal when you come back.* Said as what he will find on returning, not as "forgotten when you
  leave", because that is the moment he would notice. Keeping the half-finished part would mean
  storing an amount-less row the record cannot hold; not worth it for a part that takes one number
  to finish.
- **The naming sheet never shows an empty name.** Before a name is typed — and a name of only spaces
  is no name, by the same test that enables **Make the meal** — it says the rows will show "under the
  name you give it": a sentence that needs no name, not a placeholder in one that does. Once a name
  is typed it goes into the sentence trimmed, so a space typed before it draws no gap; the field
  keeps exactly what was typed.
- **Counts read naturally at one and at many** — *Make a meal from this one*, *…from these 4* —
  through Android plural resources rather than a test for 1 in code: Hebrew (#9) has more plural
  forms than English, and will translate these same resources.
- **"2 portion" becomes "2 portions"** on a day's rows, in the naming sheet, on a saved meal's parts
  in Add something, on the builder's parts, in the Just-for-today adjuster, and on the proposal
  screen (issue #27, one shared rule). Only the app's own
  word for "one of it" is pluralised — on any row whose unit is "portion", whoever put it there
  (the model can answer it too), and only while the row's words are in the app's own form of its
  amount and unit ("2 portion"). Words that say anything else ("2 portion (large)") are drawn as
  stored, because the words are what is displayed (D5). A unit the model or the owner named
  ("2 slice") is shown exactly as it was written, because the app cannot know another word's plural.
  The stored words on the row are not rewritten: the plural is chosen when the row is drawn, from
  its amount and unit, so every row already logged reads right. A part-portion takes the plural
  ("1.5 portions"); Android's plural API takes whole numbers only, and Hebrew's rule for fractions
  is for #9. A meal's part stores no words — its amount is drawn from its amount and what it is
  counted in — so there it is always the app's own form and takes the plural directly.
- **Cost accepted.** An unconfirmed rename in the builder is lost on leaving and the heading does
  not say so: it has its own button directly beneath the field. A saved meal with a part the app
  can no longer cost is logged without that part, so "as you built it" is short by that part; its
  card on Add something already says so, and the part is left out rather than logged as nothing. No schema
  change, no stored text rewritten.

**D38 — ADDED 2026-09-19, issue #18, amended by issues #28, #31, #30 and #29; the recommended option, taken under
the owner's standing instruction of 2026-09-19 to choose the recommended option. What is logged on a
day keeps whole grams of protein, carbohydrate and fat, and whole calories; the form he types them
into says so before he types, and a decimal is refused with its reason, never rounded for him.**

- **Nothing stored changes.** A day's row keeps `Int` macros: no schema version, no migration, no
  backup-format change, no change to any total. Decimal macros on the day would need all four and
  are left for a decision of their own if the owner ever wants them.
- **Said first.** *Type the numbers* (and *Correct this item*, the same form) says, above the
  calories and below the amount when there is one: *Whole numbers only: calories, and protein,
  carbohydrate and fat in whole grams. A number with a decimal point or comma is refused, not
  rounded.* The refusal under a box uses the same words: *Whole grams — no decimal point or
  comma.* under a macro, *A whole number of calories.* under the calories. The two used to share
  *A number, in whole units.*, said only after **Add it**. Both name the comma as well as the
  point, because "0,7" is what a comma-decimal keyboard types and it is refused the same way.
- **Refused, not rounded.** Rounding a typed 0.7 to 1 without saying so would store an altered
  number as his own (D4). The box keeps what he typed so he can see what was refused. No input
  filter strips the decimal point as he types — that would be rounding by another name. "1.0" is
  refused too: the rule is the form of what he typed, not its value.
- **Only where it is true.** The brief asked for the whole-grams sentence on every form that asks for
  macros; it is said only on the form where whole grams is the rule. A food's facts (per 100 g and
  per one, in My foods and in the meal builder's *Make a food*) are stored as decimals and keep them;
  those two forms say the opposite, as plainly: *Numbers here can have a decimal point: 0.5 g is
  kept as 0.5 g on this food.* A packet's label figures (Scan → *Add it yourself*) are kept as
  printed too, on the packet's own record (the barcode's product row). What reaches the day from a
  scan is a logged row in whole grams; since issue #28 (0.32.1) a food learned from a scan takes the
  packet's own per-100 g figures, as printed, under LABEL. The packet form still has its own
  sentence, because the form itself has no food: what is typed there goes onto the packet's record,
  and reaches a food only when that packet is logged. So it names the packet — *Type the numbers as
  printed — 0.5 g is kept as 0.5 g for this packet. What goes on the day is counted in whole
  grams.* — "for this packet", not "on this food". Either way the whole-grams rule is not mistaken
  for the app's and a packet's 0.5 g is not rounded by hand.
- **Cost accepted.** A food genuinely 0.5 g of fat per serving, typed straight onto a day, has to be
  entered as 0 or 1 — he is told so before typing, and can make it a food instead, which keeps 0.5.
  A day row logged from a 0.5 g food is still whole grams, rounded by logging's arithmetic (facts ×
  how much he ate, as it always has been); that is a computed amount, not a typed one, and the food
  itself keeps 0.5. The packet-label form refuses a comma ("0,7") where the food form accepts one —
  pre-existing; neither sentence promises a comma (the food forms' says "decimal point"). But "as
  printed" invites a label's printed "0,5", which that form refuses as "Fat per 100 g, in grams (at
  most 110)." (the ceiling named since D42) —
  accepting a comma there, as the food form does, is still open — it was not part of #28's fix and
  has no issue of its own yet. Calories follow the same whole-number rule as before. Fixed by #28: a
  food learned from a scan no longer has its per-100 g figures worked back from the whole-gram row;
  it keeps the packet's. Since D39 (issue #31, 0.32.2) a packet whose label is no quantity of food
  (a negative, infinite or not-a-number figure) is refused where it enters and never reaches
  logging; one logged before then taught its food nothing and made none — though restoring a backup
  makes one from that row; see below. Three older edges of a malformed label — a not-a-number figure
  crashing before anything is logged, a negative large enough that the logged amount rounds below
  zero (fat -5 g per 100 g at 30 g gives -1, which `FoodItem` refuses), and the packet-label form
  accepting "Infinity" — are fixed by D39 (issue #31): each such figure is refused at the door.
  Two doors still work per-100 g back from a scanned whole-gram row: correcting a scanned row under
  a new name teaches the newly named food that way (issue #29); and restoring a backup does it for
  every item of a version-1 file, and for any item of a version-2 file naming no food — as such an
  unattached scan exports — or a food the file does not declare (`BackupRepository.kt:232, 236`).
  Until D43 (issue #29) both filed that figure under LABEL; since, both file it as copied from a
  past meal (REPEATED), below a typed or a label figure, never as the label's. **Foods already
  learned from a scan before 0.32.1 keep their worked-back figures:** nothing repairs them, and
  each is corrected only when that packet is scanned again (a label replaces a label) or by hand.
  A macro Open Food Facts leaves out used to be stored on the packet as 0.0 and so reached the food
  as a LABEL 0.0; since D40 (issue #30, 0.32.3) it is never invented — the scan opens the label
  form with that box empty. Packets and foods that took an invented 0.0 before then keep it.

**D39 — ADDED 2026-09-19, issue #31; the recommended option, taken under the owner's standing
instruction of 2026-09-19 to choose the recommended option. A per-100 g figure that is no quantity
of food — not a number, infinite, or negative — is refused where it enters the app, never carried
to the screen.**

- **From Open Food Facts**, a product with such a figure is treated as not found: the scan offers
  *Add it yourself* — type it from the packet — so a number exists only once he reads it off the
  packet (D4). Nothing is saved on the phone for it. A zero it states is a quantity and is not
  refused; a figure it leaves out is not a zero at all and opens the label form instead (D40, issue
  #30). A reply with an impossible figure is refused even when another is left out: this rule is
  checked first.
- **On the typed label form**, such a figure is refused under its box with that box's own reason
  (*Fat per 100 g, in grams.*, since D42 *Fat per 100 g, in grams (at most 110).*), as a negative
  already was; the box keeps what he typed and nothing is saved. "NaN" was already refused;
  "Infinity" and "1e999" — which Kotlin reads as numbers — now are too, in the serving box as
  well.
- **A packet already on this phone** with such a figure, from before this rule, is not believed:
  the scan asks Open Food Facts as if it had never been seen, and a good answer, or what he types,
  replaces it. One whose serving is "Infinity" (the form took it until now) is believed, but that
  serving is not offered: the amount box opens at 100 g, as for a packet that names no serving.
- **The rule is the value, not the spelling:** whatever parses to a figure that is not finite, or is
  below zero, is refused, however it was written. One rule, in one place
  (`Product.isQuantityOfFood`), for every door. Since D42 (issue #32) it also refuses a figure past
  its per-100 g ceiling — 1000 kcal, 110 g of each macro — and delegates to D42's one definition
  (`BelievableAmount`) rather than holding a rule of its own. Logging (`Product.toFoodItem`)
  refuses such a product too, as a last line, instead of crashing.
- **Cost accepted.** The not-found screen says the barcode "is not in the food database"; for a
  refused entry it is there but unusable. What he does next is the same — type it from the packet —
  so the wording is not split into a third case. Nothing records why an entry was refused. The
  same "Infinity", and a finite but absurd figure (1e12 kcal per 100 g), got through My foods' and
  the builder's food form and the amount boxes until D42 (issue #32). No schema change.

**D40 — ADDED 2026-09-19, issue #30; the recommended option, taken under the owner's standing
instruction of 2026-09-19 to choose the recommended option. A figure Open Food Facts leaves out is
never invented as 0: the scan opens the type-it-from-the-packet form, filled with what the database
does have and the missing box empty.**

- **Missing means the database holds no number for it** — the key absent, `null`, blank, or not a
  number at all ("unknown"). A `0` it states, as a number or as `"0"`, is a real 0 and is kept. A
  figure it states that is no quantity of food is still refused as not found (D39), checked first,
  so a reply is never offered half-filled next to a figure the app has just called impossible.
- **The same for all four figures.** Missing calories used to mean "not found" and a blank form; it
  now opens the same pre-filled form with the calories box empty. An entry with a name and no
  nutrition at all opens it with all four empty, the name, brand and serving kept.
- **Filled with what is known.** Name, brand, the figures it holds (written as a person would type
  them: "17", "0.7", "0") and the serving size; the missing boxes empty; above them, first on the
  form, one sentence naming what the database lacks, in the form's order, singular or plural by
  count: *The food database has this packet but no fat figure for it. Copy it from the packet — the
  figures it has are filled in from the database.* — or, for several, *…no protein or fat figures
  for it. Copy them from the packet — the figures it has are filled in from the database.* It
  takes the place of the blank form's *Copy these from the package itself. Nothing estimated goes
  into the shared database.*, which above boxes already filled would tell him to copy what is there;
  *Add it yourself* keeps that line. The sentence stays while he types (it describes the database,
  which his typing does not change). The form's own rule refuses the save, under the empty box in
  its usual words, until every missing box is filled from the packet.
- **Nothing is saved or logged until he saves.** An incomplete reply is never written to the phone,
  so scanning it again asks the database again and the same form comes back. Once saved it is a
  packet he typed: kept on the phone, logged and learned exactly like one, and — with an account —
  offered to Open Food Facts, which is how the database's gap gets filled. *Add it yourself* still
  opens a blank form, with no sentence about the database.
- **Also closed on the way:** a reply whose product or nutrition entry is present but not a JSON
  object threw out of the parser and so out of the lookup; it is now not found. A nutrition entry of
  `null` is read as no nutrition (all four missing). A name or brand of JSON `null` is absent, not
  the word "null" — which the form would otherwise have pre-filled, saved and sent up.
- **Cost accepted.** Packets scanned before this fix whose figure was invented as 0.0 are still on
  the phone, found there before the network is asked, and cannot be told from a stated 0; nor can a
  food that learned one. Neither is repaired. An entry whose energy is only in kJ now opens the form
  with calories empty (before: not found); nothing converts kJ. The pre-filled form has no *Describe
  it instead* or *Scan another* — Back leaves the scan, as from *Add it yourself*. Sending the saved
  packet to Open Food Facts resends its name, brand and serving as well as the figure he filled.
  Where the database has no serving size, the serving box is pre-filled with the pack's net weight
  ("500 g" read as 500), as the found screen's suggested amount always was; he can change or clear
  it, and if he saves it unchanged that is the serving that goes up. The pre-filled form does not
  repeat *Nothing estimated goes into the shared database*; its sentence says to copy from the
  packet, which is the same rule (D24), and the save is refused until he has. The wider fix —
  product figures that can be "unknown", a schema change — was not taken. No schema change.

**D41 — ADDED 2026-09-19, issue #14; extended the same day, issue #33; the recommended option,
taken under the owner's standing instruction of 2026-09-19 to choose the recommended option.
Searching his foods matches the brand printed on a food as well as its names, and the meal builder
names a food it will not offer because the meal already holds it, instead of saying nothing
matches.**

- **The brand is searched exactly when it is printed** — a real brand, never `NA`, which would
  make every unbranded food match `a`. Case-insensitive, whitespace cleaned as a name's is; no
  other folding (D28's plain contains). A scan is the main way a real brand arrives (D23), so this
  matters more as scanning grows. Every search over his foods gains it: My foods, Add something's
  foods tab, and the meal builder, whose offered rows now print the brand as the other two do.
- **Found by name first.** Everything a search found before comes back in the same order;
  foods found only by their brand follow, then foods found only word by word.
- **Brand and name typed together find the food** (issue #33). A food the whole search is not
  in is still found when every word of the search is in its name, an alternative name or its
  real brand — each word anywhere, in any order, with the same plain case-insensitive contains
  (never `NA`). `Tnuva milk`, `milk tnuva` and `TNUVA  Milk` all find the milk branded Tnuva,
  on all three screens; on Add something that means describing it afresh is not offered.
  Decided food by food, so whether a food is found never depends on what else is in the list.
  Words split on any whitespace, a non-breaking space included, and the whole search is the
  words rejoined by single spaces, so a doubled space, a tab or a non-breaking space typed inside
  a name now finds that name, or a brand, as a whole. That widens the whole-name and whole-brand
  parts too: every stored name, and the brand as the search reads it, is tidied to single spaces,
  so such a search matched nothing before; nothing found before moves. A one-word search is
  unchanged.
- **A food in the meal is still offered once, never twice** (a meal holds a food once). A search
  that finds it names it — *Cucumber is already in this meal.* — whether or not other foods are
  offered beside it; *Nothing matches* is said only when nothing matched. A food chosen in the
  list and still waiting for an amount is named as that — *…is already here, waiting for an
  amount.* — not as in the meal (D37). Singular or plural by count, from resources. What is named
  is searched over the meal itself, so a food hidden since it went in is still named.
- **Not taken.** Offering the food already in so its amount can be changed (issue #17);
  matching one food logged in two languages, which is what joining two foods is for (D30). No
  schema change.
- **Cost accepted.** A short search can now list a food by its brand (`ex` finds a food branded
  Examplebrand), always after every name match and with the brand printed on the row. On Add something, a
  food found only by its brand counts as found, so describing afresh is not offered for it — the
  duplicate D28 exists to prevent. A new food's brand is still stored as it arrived (untidy
  spacing and all) where renaming a brand tidies it; the search tidies it as it reads, and the
  storage is not changed. The waiting-for-an-amount sentence goes a step past the issue's letter —
  the same lie by the same filter — and can be dropped on its own. A search of several short
  words can list a food whose fields happen to hold each of them (`a b` lists any food with an
  `a` and a `b` somewhere), always after every whole-search match. Word order no longer matters —
  `oil olive` finds Olive oil. A zero-width character pasted between words is not whitespace and
  does not split them, so such a search still misses. Punctuation does not split words either:
  `Tnuva, milk` finds nothing, and Add something still offers to describe it. Add something's
  *My meals* tab still matches a meal's name as one piece; a meal has no brand.

**D42 — ADDED 2026-09-19, issue #32; the recommended option, taken under the owner's standing
instruction of 2026-09-19 to choose the recommended option. Every box where he types a number
shares one "believable amount" rule: a number, not negative, and not above a ceiling for what that
box holds. A value past the ceiling is refused under the box — never rounded, clamped or logged
(D4) — and the scan's *Log it* never leaves the screen when nothing was logged. The label form, the
food form and *Correct this item*'s amount name the ceiling in the refusal each already gave; the
four amount boxes that had none (the scan's grams, Add something's, the meal builder's two) name it
only for a number past it, and stay quiet on a blank, negative, zero or word, as before.**

- **The ceilings**, in one place (`BelievableAmount`) and recorded here so changing one is a
  decision: per 100 g — 1000 kcal, and 110 g each of protein, carbohydrate and fat (past the
  chemistry, so a rounded label is taken as printed — see *Cost accepted*); per one of
  something — 5000 kcal, 500 g each; an amount eaten, a serving, or what one weighs, in grams —
  5000 g (5000 of whatever mass unit a logged row names); a count of portions or units — 100; a
  whole item typed by hand (*Type the numbers*, *Correct this item*) — 10000 kcal and 1000 g each,
  the bounds that form already had. A value exactly at a ceiling is accepted. Body weight and
  target keep their ranges and now also refuse "NaN", which slipped past both and crashed Save
  (the weight box says *A number, like 80.5*; the profile's weight and target say their range).
- **The rule is the value, as D39's is:** "Infinity" and "1e999" are past every ceiling, however
  written. What each box accepts otherwise is unchanged — a comma where it was accepted, decimals
  where kept, whole numbers on *Type the numbers*.
- **The words.** A box that already had a refusal keeps it, with its ceiling added: *Fat per 100
  g, in grams (at most 110).*; *All four per 100 g (at most 1000 kcal, and 110 g of protein,
  carbohydrate or fat), or leave them all empty.* (the food form refuses a group under its
  calories box, as it always has); *How much of it? A number greater than nothing (at most
  5000 g).* *Type the numbers* already said *the most is 10000*. The amount boxes that had no
  refusal — the scan's grams, Add something's amount and the meal builder's two — now say *At most
  5000 g at a time.* or *At most 100 at a time.* when what he typed is past it; a blank, a zero or
  a half-typed number still just leaves the button off. Those new sentences are in resources; the
  three forms' sentences stay beside the rest of each form's wording, in the form, and move to
  resources with it (issue #9).
- **Packets:** the per-100 g ceilings are D39's rule too. An Open Food Facts entry, or a packet
  already on this phone, with a figure past its ceiling (1e12 kcal; 3700 "kcal" that is really kJ)
  is not found, as an impossible one is: *Add it yourself* types it from the packet. A serving past
  5000 g is neither offered as the amount nor pre-filled on the label form.
- **The scan never closes silently.** *Log it* leaves the screen only when a row was logged; if
  nothing could be, the screen stays and says why under the button — *Nothing was logged — the
  amount above is not one this can count.*, or, for a packet whose figures no row can be made
  from, *…this packet's figures are not ones this app can count. Scan again to look it up afresh.*
- **Typing only.** Nothing stored is changed or repaired, and restoring a backup reads its
  numbers as they are, however large. A food or logged item already holding such a number keeps
  it until he next saves it through a form, which then refuses it until corrected — so a
  correction to anything else on it waits on that box too.
  *Amended 2026-09-23, issue #7, the owner's choice of clearing over flagging:* **foods are now
  repaired.** Each time the app opens, a food's number group — per 100 g, per one, or what one
  weighs — holding a figure the food form would refuse is cleared, and nothing else: the food keeps
  its other groups, a cleared box reads as not known (D4), and the food does not move in the list.
  A food left knowing nothing is not deleted — its logged rows still point at it — but reads as no
  food, so it leaves every list, and a saved meal counting it loses that part from view, until
  logging it again under its name teaches it a figure. **Logged items are still not repaired**:
  what a past day holds is the record, and keeps this rule. A backup restoring such a figure is
  cleared at the next open. Exporting never fails on a figure that is not finite: a food's group
  holding one is written as null, and any other such number — a logged item's or a meal part's
  amount — as null, read back as no amount.
- **Cost accepted.** Counting more than 100 of something (almonds one by one) is refused: weigh
  them. 5000 kg or 5000 l is a slipped-finger guard, not a believable portion. The per-100 g
  ceilings sit past the chemistry (pure fat is about 900 kcal, and 100 g of anything holds at most
  100 g of a macro) because a label is rounded: an oil worked out from a US label (120 kcal and
  14 g of fat per 13.6 g tablespoon) prints about 882 kcal and 103 g of fat per 100 g. Refusing
  that would leave him to log nothing or type a number the packet does not state, stored as the
  label's (D4); at 1000 and 110 it is taken as printed, from Open Food Facts or typed. The price: a
  figure between the chemistry and the ceiling (950 kcal, 105 g of fat) is accepted though no food
  holds it. Kilojoules filed under the kcal key are caught only when the figure is above 1000; a kJ
  figure of 1000 or less (a food under about 240 kcal per 100 g) still passes as calories, as it
  did before. Foods and logged rows saved with an infinite or absurd figure before this release are
  not repaired: such a food still previews as a 2,147,483,647-kcal row in Add something and the
  meal builder, and exporting a backup that holds an infinite figure is expected to fail
  (unverified) — issue #34, not this one's. (Since issue #7, 2026-09-23: such a food's group is
  cleared on opening the app, and exporting was verified to fail and no longer does — see *Typing
  only* above. A logged row's saturated figure stays.) The daily limit on model calls
  in Settings is a number box with no ceiling here — a count of calls, not food; a negative still
  becomes 0, as before. A proposal's count (Fewer / More) is not typed and has no ceiling. A meal
  part saved with an infinite amount before this rule is not repaired. No schema change.

**D43 — ADDED 2026-09-19, issue #29; the recommended option, taken under the owner's standing
instruction of 2026-09-19 to choose the recommended option. A per-100 g (or per-one) figure a food
learns by working back from a logged row is never credited to the packet label, even when the row
came from a scan. It is filed as copied from a past meal (REPEATED), which ranks below a typed
figure and below a real label figure (decision 9's ranking), so it can fill a blank but never
overwrite one he typed or one read off a packet.**

- **Where.** The one conversion every such route shares (`DerivedFoods`): renaming a row in
  *Correct this item* (#22's route, the one #29 names), restoring a backup whose items name no food
  (or a food the file does not declare), and the upgrade to the food list. A row's own number and
  source are untouched — a renamed scanned row is still LABEL on the day; the scan still gives its
  food the packet's exact per-100 g as LABEL (#28, D38).
- **Why REPEATED and not a new source.** On a food it already means a figure taken from a meal
  logged before, with no claim about where that meal's number came from — which is what this is.
  No door makes a REPEATED row of its own accord today: such rows come from before the food list
  (a database upgraded to version 5, an old backup), or were logged from a food whose figure is
  itself REPEATED, which copies that source on. The food list has always filed a figure from such
  a row as REPEATED, and the food screen words it *copied from a past meal*, never *off the
  packet*. A new source would need no schema change, and older versions would read it as
  unrecognised, but every row logged from such a food copies the fact's source onto itself, so it
  would widen the change to the day's rows, the day's wording and the backup; nothing needs that.
- **Which row.** Within a group of rows, the row chosen is the one whose figure would rank highest
  *as a food's fact*, so a typed row is not passed over for a label row whose figure can only be
  copied. The upgrade from version 4 changes with it: LABEL rows there now give REPEATED figures.
  It has already run on the owner's phone and never runs there again, so nothing there moves.
- **Not decided here.** Whether a LABEL row whose figures he edits in *Correct this item* should
  stay LABEL on the day — a question about the row, not the food — is issue #35 — **decided by
  D44**.
- **Cost accepted.** It ranks below a model's estimate as well: a worked-back figure cannot replace
  an estimate, and a later estimate of the same name replaces it. Something logged from such a food
  no longer says *From the package label* on the day; it says nothing, as a copied figure without
  an estimate does. **Foods that learned a worked-back LABEL figure before this release keep it,
  and only correcting the food by hand in My foods repairs it.** The food list does not record
  which figures were worked back, so nothing finds them. Scanning the packet again does not reach
  them: a scan finds its food by barcode first, then by name and brand, and such a food has no
  barcode and no brand — one taught by a rename also carries the new name — so the packet lands on
  the food that holds its barcode (for a rename, the one first scanned) or on one of its own name
  and brand, never on this one. A scan reaches such a food only when it has since learned that
  barcode, or when the packet has no brand, bears this food's name, and no food holds its barcode.
  Logging the figure again by hand does not move it either: a typed figure ranks below a label
  figure. No schema change.

**D44 — ADDED 2026-09-19, issue #35; the recommended (narrower) option, taken under the owner's
standing instruction of 2026-09-19 to choose the recommended option. A figure of a packet-label row
that he changes in *Correct this item* becomes his number, not the label's: the saved row's source
is TYPED. Change only the name, only the amount, or nothing at all, and the row keeps LABEL.** The
mirror of D4's own rule for corrections — correcting a model's guess does not make it a measurement,
and retyping a packet's figure does not keep it a packet reading.

- **What counts as changing a figure.** The calories or any of the three macros, compared as numbers
  against the row as it stood, after allowing for the amount. Changing the amount rescales the
  figures and is **not** a correction of them: the row is compared with what the amount alone would
  have produced. Re-typing the same number changes nothing (the editor stores whole numbers and
  refuses a decimal outright, so no re-formatting can reach the comparison). **Both ends have to be
  a label:** the rule fires only when the row being saved still calls itself LABEL as well as the
  row it replaces. The editor carries a row's source through untouched, so a saved row that says it
  is something else did not come from the editor — something else built it and stated what it is,
  and overwriting that would be this rule inventing provenance. No route can produce that today; it
  is the conservative reading of D4 rather than a live case.
- **Where.** The one place a correction becomes a stored row (`DayViewModel.correctItem`), so every
  route into the editor behaves the same. The editor itself still carries a row's source and
  confidence through untouched, which is what keeps an estimate an estimate.
- **Every other source is unchanged.** An AI_ESTIMATE row corrected by hand is still an estimate with
  its confidence (D4); TYPED, REPEATED and UNRECOGNISED rows are untouched. A freshly scanned row is
  still LABEL (D38), and the food a scan lands on still gets the packet's exact per-100 g as LABEL
  (#28).
- **What follows the source.** **Nothing on the day changes on screen:** the day's item list prints
  no per-row origin line, so there is no *From the package label* there to disappear. What the row
  would say if asked — the wording the proposals screen uses — becomes the typed one, which is to say
  nothing, as a typed row does. Where the change actually surfaces is the export and backup, which
  record TYPED, and, after a rename, *My foods*. If the correction also renames the row, the food
  that name belongs to learns a **TYPED** figure rather than the copied one D43 gives an untouched
  label row: no contradiction, since D43 demotes a figure only because the packet did not state it,
  and here he did. It then ranks as anything he types does — above a copied or estimated figure,
  never above a real label figure.
- **Cost accepted.** **Rows corrected before this release keep their stale LABEL** and nothing finds
  them: the record does not store what a row's figures were when it was logged. Correcting such a row
  again repairs it. **The source belongs to the row, not to each figure, so correcting one figure
  promotes all four.** Correct only the calories of a scanned row and rename it, and the new food
  learns worked-back per-100 g protein, carbs and fat as TYPED too — figures he never typed,
  including the fat of 0 g that is only D38's rounding of half a gram, which is exactly what D43
  would have demoted for a row he left alone. Nothing stored is wrong and none of it outranks a real
  label figure, but the justification above holds strictly only of the figure he changed. Demoting
  figure by figure is the alternative and is a larger change; it is not taken here. No schema change,
  no new source value, nothing new on screen.

**D45 — ADDED 2026-09-19, issue #13; the recommended option, taken under the owner's standing
instruction of 2026-09-19 to choose the recommended option. Re-using a food's name goes on attaching
to that food and may go on changing what it knows — but never silently. When typing or logging
teaches an existing food a figure that REPLACES one it already held, the app says so: which food,
what it held, what it holds now, without blocking anything.**

- **Why not leave the numbers alone.** Decision 9's ranking deliberately lets a re-typed figure
  replace an older typed one and a fresh scan replace an older scan (`>=` in the three guarded
  statements), and D44 relies on exactly that. What was lost was not the figure but the knowledge
  that it had changed, so that is what is restored. Not one `WHERE` clause, rank or `>=` moves.
- **Why not refuse the name, as a meal name is refused.** A food is an **identity that entries attach
  to**: re-using its name is how the owner says "the same food again", and refusing it would
  manufacture duplicates and detach his history. A meal name **labels a new saved thing**: re-using
  it would leave two things under one name with no way to tell which he meant. Two opposite answers
  to two different questions, and this is where that is written down.
- **What counts as a replacement.** The food held that fact, still holds it, and the figures —
  calories and macros, the unit's name, the grams — are not the same figures. Compared to about nine
  significant figures rather than bit for bit, because two routes to one real number differ in a
  double's last bit and "changed from 67 to 67 kcal" would be a defect of its own; the smallest
  change any form can express is far larger than that tolerance. Filling a blank is not a
  replacement. Re-logging an identical figure is not, whatever its source — and the DAO's row count
  cannot answer this, since `>=` lets an identical figure through and reports a row written, so the
  before-value is READ from the store immediately before the offer, inside the same transaction. A
  figure that loses the ranking changes nothing and says nothing. A figure whose source improved but
  whose numbers did not is not a replacement: nothing he can act on moved.
- **What the sentence carries is calories.** A food's own row prints its calories and nothing else,
  and the notice speaks in that same voice: the food's name, which of its three facts moved, and the
  calories before and after. When only the MACROS moved it is still a replacement — they are his to
  see and to correct — but the line says the calories are the same figure reached from different
  figures rather than naming the macro that moved; *Correct the food* is where the macros are shown
  and where he would change them. A case-only or spacing-only change to a unit's name is NOT a
  replacement: the stored name moves, nothing he can act on does.
- **One notice per action, one line per food.** A food is named once however many of its three facts
  moved, and once however many rows of that action named it: the line compares what the food held
  **before the first row that touched it** with what it holds **after the last**. So an action that
  creates a food, or fills a blank, and then writes over what it just wrote says nothing — the food
  held nothing before the action began. An action that moves a figure and moves it back says
  nothing. Every food that action really changed gets its own line. The grouping is by the food's
  identity, never by its spelling, since two foods can print one name.
- **On the day it stays until dismissed** rather than fading, survives moving between days — a food
  is not a property of a day — and is replaced by the next logging action, including replaced by
  nothing. Nothing else clears it: correcting a row without renaming it teaches no food, so it is not
  one of the actions that speaks and leaves the last sentence exactly where it was. It has its own *Got it* and sits under the target-change notice (D11): the two are about
  different things and neither may hide the other.
- **Who speaks, and where.** Logging in all its forms, and a correction that renames a row onto
  another food: on the day screen, dismissible. **Making a food inside the meal builder** speaks too,
  as a line in the *how much of it* panel that opens next, worded for typing rather than logging:
  that door types figures into an existing food exactly as logging does, there is no day screen to
  carry a notice while he is mid-meal, and the panel shows the food's name but none of its figures.
  The line goes when the panel does and has no dismiss of its own. **Restoring a backup and the
  upgrade to the food list stay silent**: they replay history rather than record something he just
  did.
- **Cost accepted.** The notice lives in memory and dies with the process, unlike the target
  notice's persisted "seen" flag: a new store for a sentence about an action seconds old is a larger
  change than the decision asks for. Foods already overwritten silently before this release are not
  repaired and cannot be — nothing records what a food used to hold. Nothing on any logged day
  changes, no schema change, no change to the ranking, no change to which figure wins.

**D46 — ADDED 2026-09-19, issue #24; the recommended option, taken under the owner's standing
instruction of 2026-09-19 to choose the recommended option. The screen showing the model's answer
can keep what it has just logged as a saved meal, named there and then — by RE-USING the gathering
path, not by a second way of making a meal.**

- **The route.** The items are accepted onto the day exactly as they are today; those same rows are
  then gathered through `MealFromDay` and `DayViewModel.makeMealFromChosen`, with the naming sheet
  the day already uses, drawn over the accept screen. There is one set of rules about what a part is
  worth and which rows can join, and the manual route and this one are indistinguishable because
  they are the same code. The rows are named by the ids the write handed back — `MealRepository.log`
  now answers with them — never by reading the day back, which could not tell them from rows logged
  in the same second.
- **(a) Offered only when the answer has more than one item.** A meal of one is a food already.
- **(b) Accepting never fails because the meal does not happen.** The items land on the day first, in
  their own write; the meal is attempted afterwards, on a separate tap. If gathering refuses — a row
  in a unit nothing converts, or a row that never said how much (#19, #23, #22) — the day keeps every
  item and a sentence says what stood in the way, with what he typed still in the field.
- **(c) The name is his**, taken in the sheet the day already uses; a name another saved meal holds
  is refused there exactly as it is today, and retyping makes the meal out of the SAME rows, because
  the logging happened on the earlier tap and cannot be reached again.
- **(d) Nothing about the stored numbers changes.** Gathering moves no figure (D4): the rows keep
  their calories, macros, portion, source and confidence, and the day's total is the same number
  afterwards.
- **Cost accepted.** The choice is ASSIGNED from what was just written, so **rows he had ticked on
  the day before going to describe something are un-ticked** by taking the offer and by leaving the
  sheet. Un-ticked on the tap itself, before the write comes back: the sheet opens as soon as the
  day has rows chosen, so a choice left standing while the write is in flight would be a sheet over
  the old rows, and a Confirm pressed there would gather those. The alternative — adding to the choice — makes a meal that quietly swallows a row he ticked
  an hour ago, which is worse. No schema change, no database version, no `app/schemas` file, no
  change to `MealFromDay`, and a plain *Save this meal* behaves exactly as it did.

**D13 — The streak is counted from the record, never stored as a counter.** A day counts as logged
once it holds at least one meal. Because any past day can be filled in at any time, **backfilling
repairs the streak automatically** — it was always intact, the writing-down was simply late. This is
the chosen resolution of the tension between wanting a streak and losing one to a spell away
from the app. There is no forgiveness rule and no grace period; there is a record that
can be completed. Alongside it: a lifetime count of days logged, and days logged in the last 30.

**Today in progress does not break the run.** The count walks back from the most recent day that
holds a meal, so a morning with nothing logged yet shows yesterday's run intact rather than zero.
Any other reading would make the app hostile before breakfast.

**D14 — Today may nag; the past never does.** Over target *today* shows in a warning colour: present
tense, actionable, gone by morning. Past days are stated, never coloured. After a gap the app opens
on today, asks nothing about the missing days, and shows no gap-shaped hole.

**D15 — One reminder, set by the owner, saying only that the day is unlogged.** No other
notification exists.

**D16 — Exactly one thing leaves the phone: the meal text or photo, at the moment of logging, to the
AI provider, with the owner's own key.** Not weight, not history, not identity. No account, no
analytics, no third party. Repeating a meal or typing numbers sends nothing. The photo is uploaded
once and thereafter lives only on the device.

**D16a — AMENDED, 2026-09-03. Android's automatic backup to the owner's own Google account is
allowed.** D16 as written forbade it, and the app had been quietly contradicting it since step 3:
`allowBackup` was left at the Android template's default of true, so the system may already have
been copying meals, weights and the profile to Google's backup. Found while working out where the
API key would live.

The owner's decision on being told is to **keep it, deliberately**. His reasoning: it is his own
Google account, and until the export file (step 12) and the Drive backup (step 13) exist, losing a
phone means losing the food log with it. An automatic copy he did not have to remember is worth more
than a purity he would only notice after the loss.

So D16 now reads: exactly the meal description leaves the phone *to a third party*; the app's own
data may additionally be backed up by Android to the owner's own Google account. What is NOT allowed
is unchanged: no account, no analytics, nobody else's server, and nothing about his body sent to the
AI provider.

**The API key is excluded from that backup.** It is encrypted against this phone's hardware
keystore, so a restored copy would be undecryptable rubbish — and a spending credential should not
travel even in a form nobody can read. Re-entering it after a restore takes ten seconds. See
`app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml`.

**This does not replace D18.** Android's backup is opaque — the owner cannot open it, inspect it or
be sure what is in it — and D18's whole reasoning is that "an automatic backup nobody can inspect is
not a backup anyone should trust". Steps 12 and 13 still stand. This is a safety net under them, not
instead of them.

**D17 — The API key is the owner's, entered in settings, stored encrypted on device.** The app ships
with no key and routes through nothing operated by anyone else.

*Note, 2026-09-03:* the spending cap is a **prepaid balance with auto-recharge off**,
which is a harder ceiling than any limit setting — when it is spent, requests fail, and nothing can
silently roll over. This was the deciding factor in choosing the provider and it is worth recording
that the mechanism is prepayment rather than a configured limit.

**D18 — Backup is both a file the owner controls and an automatic upload to their own Google
Drive.** The manual export exists because an automatic backup nobody can inspect is not a backup
anyone should trust.

**D19 — MetaSelf gets its own Google Cloud project and its own release signing key.** Drive's
per-app private folder is scoped to the Cloud project, not the package, so sharing another app's
project would have the two apps overwriting each other's backups. The signing key must exist before
the OAuth client can be created, and losing it means no future update can install over what is on
the phone.

**D21 — ADDED 2026-09-04. A goal has a destination, not only a direction and a rate.** Until now
the profile said only how fast — "lose half a kilogram a week", say — and nothing about until when, so the app could tell
the owner where he was going but never how far. A **target weight** is the first thing "goal" means;
the field is named for a goal rather than for a weight because a weight is only a start, and
a goal may later mean more than a number on a scale.

What it makes possible, in the order the owner is likely to want it:

- **How far there is to go**, from the smoothed trend and not from a single reading — the same rule
  as everything else about weight.
- **How long that will take at the rate he chose**, stated as the arithmetic it is. "About sixteen
  weeks at half a kilogram a week" (an illustrative 8 kg to go) is a division, not a forecast, and it must read as one. Decision
  D4's principle — an estimate is never presented as a measurement — applies to predictions about
  the future at least as strongly as to guesses about a plate.
- **The target as a line on the weight chart**, which is the cheapest way to show distance.
- **Something to say when it is reached.**

**What happens on arrival — DECIDED AGAINST THE RECOMMENDATION, 2026-09-04. The app celebrates for
one day, then switches the goal to "hold" by itself.** The recommendation had been the opposite: say
he has arrived and leave the change to him, because a target that moves on its own is what D11
exists to prevent. The automatic switch was chosen instead, and the reasoning holds — arriving is
not an ambiguous event, and the alternative is an app that goes on prescribing a deficit to somebody who has finished
losing weight until he thinks to go and turn it off.

What that obliges the build to get right:

- **The celebration lasts one day and is not a nag.** It is shown on the day the trend arrives and
  then it is gone. Decision D14 — today may nag, the past never does — applies here in its strongest
  form: an achievement that keeps announcing itself becomes a reprimand for not having a new goal.
- **The switch is announced, not silent.** D11 already requires that a change to the daily target
  says so out loud. This one changes the target by the whole size of the deficit, so it must be the
  most legible announcement the app makes, and it must say what the new number is and why.
- **It is measured from the trend, never from a single morning's reading**, or a heavy dinner
  followed by a light one would "arrive" twice.
- **It fires once.** Arriving, drifting up a kilogram, and arriving again must not re-celebrate.
- **It is reversible in the obvious place.** He sets a new goal in the profile like any other.

**D22 — ADDED 2026-09-04, from a request. Once a goal exists, the way there is worth marking too.**
Arrival is one moment and a goal is months. The first kilogram, five kilograms, a fortnight of steady
decrease — these are the evidence that the thing is working, arriving at a time when the scale on any
given morning says nothing at all.

The whole difficulty is in **the first kilogram happening only once**. A milestone is
a threshold crossed, and a weight that crosses it, drifts back and crosses again has not achieved
anything twice. So every milestone is recorded as reached, permanently, and never fires a second
time — the record of what was celebrated is the state, not a recomputation from today's numbers.

Three kinds, and they are not the same kind of claim:

- **Distance from the start** — the first kilogram, then every fifth. Measured from the trend against
  the weight when the goal was set.
- **Distance remaining** — halfway there, and the last kilogram.
- **Consistency** — some number of weeks of the trend moving the right way. This one is about
  behaviour rather than about the body, and it is the only one of the three that is really praise.

Two constraints, both of which are about not becoming unbearable:

- **Nothing is ever said about going the wrong way.** A milestone missed, a regained kilogram, a bad
  fortnight — silence. The app has no opinion. D14 says the past never nags; this is the same
  principle applied to a number that went up. Comment on the good and stay quiet on the rest, or the
  weight screen becomes a thing to avoid opening.
- **Rarity is the point.** Every fifth kilogram, not every hundred grams. A celebration that happens
  weekly is wallpaper, and one that happens for something trivial devalues the ones that are not.

Built as its own step, after the goal itself, and sharing the arrival machinery from D21 — arriving
is simply the last milestone.

**D23 — ADDED 2026-09-04. Barcodes: a shortcut for packaged food, never a new way to log.**
MyFitnessPal's barcode database is its own, built from fifteen years of users typing products in, and
is not available to anybody else. The open equivalent is **Open Food Facts** — a non-profit database
with a free API, no key and no account needed to read it.

**Measured, not assumed, on 2026-09-04:** Open Food Facts holds **8,278 products tagged Israel, of
which 7,162 have complete nutrition data**. The United Kingdom has 193,758, so Israeli coverage is
about four per cent of that. A test lookup of Bamba's barcode returned the Hebrew brand and full
macros. The honest expectation is therefore that a scan finds the product **some** of the time — a
few thousand products against a supermarket's tens of thousands — and that is the whole reason this
is a shortcut rather than a feature the app depends on.

**A miss falls straight through to describing the meal**, which is what the owner would have done
anyway. Nothing blocks, nothing fails, and the barcode path can be abandoned mid-way at no cost.

**The model is never asked to resolve a barcode.** It will invent a product for a number it has never
seen, confidently and in the right format. That is precisely the fabrication D3 and D4 exist to
prevent, and a barcode is the easiest possible way to invite it.

**A local memory of scanned products, keyed by barcode.** The second time a product is scanned
it is instant and works with no network at all (D8). This is worth having even where Open
Food Facts knows nothing: a product he entered once by hand is a product he never enters again.

**D23a — What leaves the phone changes, and the owner must choose it.** D16 says exactly one thing
leaves: the meal description, to the AI provider, with his key. A barcode lookup sends a number to a
second outsider. The camera work is entirely on the device — no image is uploaded to read a barcode —
but the lookup itself is new, and it tells Open Food Facts that somebody is interested in that
product. The recipient is a non-profit, there is no account and no key, and the information is of the
same kind D16 already allows out. It is still an amendment, and it is made deliberately.

**D24 — ADDED 2026-09-04, from a request. Contributing back, as a separate and deliberate act.**
Open Food Facts is open in both directions. Its documented write API accepts product data from
third-party applications, either with a personal account or with a **global application account plus
`app_name`, `app_version` and an `app_uuid`** — a salted per-user identifier, so that a moderator can
act on one bad contributor without banning a whole app. Every write requires a User-Agent naming the
application and a contact address.

So the owner can put a missing product **in**, not merely fail to find it. Four constraints, and the
third is the one that matters:

- **Only what is printed on the package.** A contribution carries the label's own figures, entered by
  hand or read from a photograph of the label. **An AI estimate is never contributed.** D4 says an
  estimate is never presented as a measurement; publishing a guess into a database other people rely
  on is that same sin committed against strangers, and it is worse because they cannot see where it
  came from.
- **Nothing about the owner goes with it.** A product, a brand, a barcode and the label's numbers.
  Not his weight, not his history, not what he ate or when. D16's list is unchanged in that respect.
- **It is opt-in, per product, and never automatic.** The app does not become a data-entry job. The
  offer appears once he has entered a product for himself, and declining it costs him nothing.
- **The local copy is saved either way.** Contributing is a gift to other people; the app's own use of
  the product does not depend on it succeeding, or on there being a network at all.

**D25 — ADDED 2026-09-04. The target stops guessing what the owner burns and starts measuring it.**

**The formula is not the problem, and is not changing.** Mifflin-St Jeor is what this app already
uses and is the right choice: it is the equation the Academy of Nutrition and Dietetics recommends,
and it beats Harris-Benedict, which is from 1919 and runs about five per cent high. Katch-McArdle is
more accurate still but needs a body-fat percentage the app does not have, so it is not
available. Nothing here changes the resting-burn calculation.

**The error lives in the activity multiplier.** Mifflin-St Jeor predicts RESTING burn to about ten
per cent. What turns resting burn into a day's burn is one of five buckets — 1.2, 1.375, 1.55, 1.725,
1.9 — chosen from a dropdown. For a resting burn near 1,700 (illustrative) the gap between two adjacent buckets is
about three hundred calories a day. The formula is a scalpel and the multiplier is a shovel.

So: **keep the formula, measure the multiplier.** Over a long enough window the app already holds
both halves of the answer — what was logged, and what the smoothed trend did — and energy that did
not come from food came from the body, at the 7,700 kcal per kilogram the target arithmetic already
uses.

**D25a — Imperfect logging mostly cancels, and this is why the feature is worth having anyway.**
Logging will not be accurate even with effort. It does not need to
be. If he consistently records, say, nine tenths of what he eats, the measurement comes out in his
own units, and a target expressed in those same units produces the right result in the world. The
number this produces is therefore **not a claim about his metabolism** and must never be worded as
one. It is *the number that, counted the way he counts, has actually produced the weight change he
is getting.* That is more useful than a physiological truth he cannot verify.

What breaks it is not inaccuracy but **inconsistency** — a careful fortnight followed by a sloppy
one. Nothing in the app can detect that, so the screen states the assumption every time it states
the number, and the guards below exist to bound the damage when the assumption fails.

**Four guards, because a wrong answer here is systematically wrong in one direction.** Under-logging
looks exactly like a slow metabolism, and the correction it invites is to cut the target, which
invites more under-logging.

- **It will not speak without enough data.** Twenty-eight days, of which at least twenty-two hold
  food, and weight readings near both ends. Otherwise it says nothing at all.
- **It moves slowly.** At most 100 kcal per weekly revision, so a bad month cannot crater the target.
- **It is bounded absolutely.** The whole correction may never exceed 600 kcal in either direction —
  about the distance between two activity buckets, twice. Beyond that the profile is wrong, not the
  metabolism.
- **The safe floor still binds.** D9 is not overruled by a measurement.

**It applies itself, and announces.** Being automatic was a requirement. D11 already requires
that a target which moves says so; this uses the same weekly rhythm and the same notice, and the
notice shows the arithmetic (D9) rather than only the conclusion.

**D20 — Same stack and conventions as an earlier app built on this machine.** Kotlin, Jetpack Compose, Room, a single module
with package-level layering, JUnit + Truth for pure logic, Robolectric only where a framework
demands it, CI on every push. Not for tidiness: these patterns are proven on this machine and this
toolchain, which makes for fewer mistakes.

## 4. Risks and open items

**R1 — CLOSED, 2026-09-02. The package name is `com.metaself.app`,** registered by the owner
before any build existed. Android's developer-verification console accepts only package names never
previously seen on a device; an earlier app had to be renamed for exactly this reason after sideloaded
installs burnt its original name. The name is now permanent — it is the namespace, the
applicationId, and every package declaration in the source.

**R2 — Losing the signing key is unrecoverable, and the owner has undertaken to verify his copy
(2026-09-05).** It is the one thing deliberately absent from every backup this app makes, so no
export, folder copy or Google account restore will bring it back. Store it outside the repository, with its
credentials as Gradle properties, and back it up somewhere that is not this machine.

**R3 — CLOSED, 2026-09-05, by dropping the photograph (step 8). Originally: portion estimation from a photograph is the weakest part of the whole idea.** The model will
identify the food far better than it judges how much of it there is. D5, D6 and D7 exist to contain
this; if it proves worse than that in real use, the answer is likely to be an explicit portion
prompt, not a better model.

**R4 — CLOSED, 2026-09-04. The band's data does reach Health Connect.** A real device's screen shows
Health Connect installed, **the band's companion app connected to it**, and the platform itself announcing that the device
"now tracks and stores activity data such as steps, distance, and calories".

So the movement half of D12 has a source, and the largest unbuilt thing in this document stops being
blocked. **What is still unverified is not whether the pipe exists but what is in it:** how much data
the band's app actually writes, and whether the owner wears the band consistently enough for a quiet day
to mean a quiet day rather than a day the band was on a shelf.

**R5 — CLOSED, 2026-09-05. The running cost is smaller than it is possible to be billed for.** Real use
had cost too little to register on the balance, which is exactly what the arithmetic predicts
rather than a sign that nothing was charged.

At `gpt-4o-mini`, one meal estimate is roughly 600 tokens in and 250 out — about **$0.00024, or two
hundredths of a cent.** Some forty estimates to the cent. Each prepaid dollar, at an illustrative four estimates a day,
lasts on the order of **three years**, and the daily ceiling of thirty calls bounds the absolute
worst case at about **$2.60 a year** even if something went badly wrong in a loop.

So the ceiling is not there to control spending — nothing here can meaningfully spend. It is there to
bound a bug, which is what it was always for.

**R6 — PART CLOSED, 2026-09-03. The screens are built plainly and the density does not scale.**
Raised on first real use of step 3: one logged item fills a large card, and two leave
almost no room.

*Two further rounds came from screenshots, both of the weight chart.* The axis labels
were computed, unit-tested and never drawn — a test that passed and proved nothing about the screen.
The vertical axis covered only the data, so a change of a few hundred grams filled the chart and read as a collapse;
it now covers at least two kilograms. Dots were sized in raw pixels and were a third of their
intended size on a real phone. And the largest figure on the screen — the smoothed trend — carried
no label, so it read as the app getting his weight wrong. **The numbers most in need of a name are
the ones that look self-evident to whoever built them.**

*The considered pass is now DUE, 2026-09-03.* R6 said it should happen once, after step 7, when the
app has all its shapes — and step 7 is done. The same conclusion was reached independently on
first using the finished feature: it works, but the design needs attention later. The screens now exist that the pass would govern: the day, the
proposal, the weight trend, the settings.

*The density half is fixed.* A logged row cost 368 px of vertical space and now costs 61 — a plain
row with a divider instead of a card, and the portion and origin lines costing nothing when there
are none. A test measures the pitch and holds it under 120, so it cannot creep back. *The considered
pass over how the app looks and feels remains open, and is still scheduled for after step 7.* This is not a matter of taste — a food tracker on which three meals do not fit is failing at
the thing it exists to do, and every later step adds more to the same screen.

Two separate pieces of work, deliberately kept apart:

- **Density is a defect and is fixed as one, close to when it is noticed.** A compact row per item
  instead of a card, at the point where the list first becomes uncomfortable. It needs no research;
  it needs less padding.
- **A considered pass over how the app looks and feels is worth doing ONCE, after step 7**, when the
  app has all its shapes: past days (step 4), the weight trend (step 5), and above all the AI
  proposal screen (step 7), which is the most demanding screen in the milestone and does not exist
  yet. Doing it before those exist means doing it twice, and the second time is the one that counts.
  Doing it after step 13 means living with it for the whole build.

Until then screens are built plainly and honestly on purpose: it is cheaper to redraw a plain screen
than a decorated one.

## 5. The build, as discrete steps

Each step below is small enough for its own detailed implementation plan, ships something the owner
can see or verify, and leaves the app working. They are ordered; where two are genuinely
independent it is noted.

**Step 1 — Skeleton that installs. DONE, 2026-09-02.** An empty but buildable app: package name
chosen and registered (R1), release signing key created (R2, D19), version marker on screen, CI
running tests and building on every push, the test frameworks wired up. *Done when:* an APK installs
on the owner's phone and shows its version.

*Verdict:* the owner installed `app-release.apk` (v0.1.0-rc1, versionCode 1, signed with the
MetaSelf release key) and confirmed the green "M" launcher icon and a first screen reading
`MetaSelf` / `v0.1.0-rc1 (1)`. The build was verified on this aarch64 machine as well as on CI's
x86_64: 4 tests, 0 skipped, lint clean. The key's certificate fingerprints are in `docs/signing.md`
and were registered against `com.metaself.app` in the Android developer console, which the device
required before it would install.

**Step 2 — Profile and daily target. DONE, 2026-09-03.** The one-time setup form; the target and
macro calculation as pure, tested logic; the reasoning shown on screen; the profile stored.
*Done when:* entering the owner's real numbers produces a target he agrees is sane, with the
arithmetic visible. (D9, D10)

*Verdict:* the owner installed v0.2.0-rc1 (versionCode 2), entered his real numbers, and confirmed
the target and the arithmetic behind it with nothing surprising in either. His figures are not
recorded here.
75 tests, 0 skipped, lint clean, on this aarch64 machine.

*Decisions this step settled*, argued in full at the head of
`docs/superpowers/plans/2026-09-02-step-2-profile-and-target.md`: Mifflin-St Jeor for resting burn;
the safe floor is the higher of resting burn and 1500 kcal (men) / 1200 (women); 7,700 kcal per
kilogram of body fat; rates of 0.25-1.0 kg per week; protein 1.8 g/kg, fat floor 0.8 g/kg,
carbohydrate the remainder. Three departures from this design, each argued in place: profile
editing is included though setup was called one-time, no navigation library is added for what is a
two-way conditional, and the current year is injected rather than read inside the arithmetic.

**Step 3 — Today, and logging a meal by hand. DONE, 2026-09-03.** The local store for meals and
entries, including the source and confidence fields (D4); the Today screen with remaining calories,
macros and the day's list; adding an entry by typing a name and numbers; deleting one. *Done when:*
the app is usable end-to-end for a full day with no network and no AI. This step also builds the
editor that the AI proposal screen will reuse.

*Verdict:* the owner installed v0.3.0-rc1 (versionCode 3), logged items, and confirmed it works. He
also raised the density problem now recorded as R6 — one item fills a large card and two nearly fill
the screen. A full day of use had not yet elapsed when this was recorded; the functional verdict is
his, the endurance one is still to come.

148 tests, 0 failures. Nine of them — the DAO and repository tests — cannot run on the aarch64
development box and were confirmed executed and passing on CI's x86_64 before this was called
done.

**Step 4 — Any day. DONE, 2026-09-03.** Navigate to a past or future date, view it, edit it, and log
to it. *Done when:* a meal from three days ago can be added today. (Prerequisite for D13's repair
promise.)

*Verdict:* the owner installed v0.4.0-rc1 (versionCode 5) and confirmed it works, swipe included.
182 tests, 0 failures; the twelve database tests were confirmed executed and passing on CI's x86_64,
including the two that prove correcting a number cannot move food to another day.

**Step 5 — Weight and its trend. DONE, 2026-09-03; the fortnight verdict is OUTSTANDING.** Log a
reading; a smoothed trend line over weeks; the raw readings visible behind it. Independent of steps
3 and 4. *Done when:* a fortnight of readings shows a trend that does not lurch on a single heavy
day.

*Verdict:* the owner installed it and used it, and the screen was corrected twice on what he found.
**The completion criterion has NOT been met and cannot be yet — it needs a fortnight of real
readings.** What is proved is the arithmetic: a simulated fortnight losing 100 g a day with one 2 kg
salty day ends 0.11 kg from the same fortnight without it. The judgement on real data is still to
come, and the number to argue with is `PULL` in `WeightTrend`.

*The database changed for the first time here.* The migration adding the weights table was
confirmed on CI against a real version 1 database holding a meal. It failed there twice first —
for want of the exported schema in the test's assets, not for anything wrong with the migration —
which is exactly the failure the local build could never have shown.

*Corrected after real use:* the weight screen showed a number and no history, gave no sign
that a save had worked, and offered no way to correct or remove a reading. Logging is now its own
screen that confirms and returns, every reading is listed with the day it was taken, and each
carries an Edit and a Delete. The second correction was the more instructive: an Edit had been
argued away on the grounds that re-stating a weight IS changing it, which is true of the code and
irrelevant to the person using it.

**Step 6 — The target follows the trend. BUILT, 2026-09-03; the on-device verdict is
OUTSTANDING.** Weekly recalculation from the smoothed weight, and a clear notice when the target
changes and why. *Done when:* a simulated month of weight loss moves the target once a week, with
each change explained. (D11)

*Verdict:* the completion criterion is met as a test — a simulated month losing a kilo a week
produces four revisions, one a week, each knowing what it changed from. **The owner has installed it
but has not yet watched a revision fire on his own phone**, which he can do without waiting a week by
backfilling a weight against a day eight or more days ago.

*Measured while building:* at a goal rate of half a kilo a week, the weekly change to
the target is about 8 kcal. Every change is announced anyway, because D11 is explicit. If that
proves to be noise, the threshold belongs on the NOTICE and never on the revision — a silenced
revision would let the target drift from the trend unannounced, which is the thing D11 forbids.

*A bug caught by writing the test first:* saving the profile cleared the whole preferences store,
which was correct while the profile was the only thing in it and would have deleted the target
revision on every profile edit. The target would have silently stopped following the weight.

**Step 7 — The AI seam, and logging a meal in words. DONE, 2026-09-03.** The provider interface and
its first implementation; the API key in settings, stored encrypted; the request contract; the
proposal screen with per-item assumptions, confidence and editing; save on acceptance; fall back to
step 3's manual path on any failure. *Done when:* "chicken shawarma in a pita, hummus, side salad"
produces an editable, plausible, confirmable meal. (D2, D3, D5, D6, D7, D8, D16, D17)

*Verdict:* a described mixed dish came back as separate components, correctly identified, with
plausible estimates, and was saved. Re-logging a counted item with
the count control gave numbers identical to a typed entry — which proves consistency rather than
accuracy, since those typed numbers had themselves come from a model.

*Hebrew is verified.* A description may be written in Hebrew, and decision 7 of the step 7
plan — that the model answers in the language it was asked in — holds on the phone and not merely in
a test.

*Not yet exercised:* the fall back to typing when the network is gone. Built and unit-tested; not
seen on the phone.

*The provider is OpenAI*, chosen for a hard spending cap and nothing else, after the same meal
description was run past two models. Their answers looked far apart — one about twice the other — and
were 8% apart once the portions were matched and an ingredient one of them had silently omitted from
its table was added back. That comparison is what made "one row per component, never a total" the
load-bearing decision of the step: an omission you can see is a correction, and an omission inside
one number is a silent error carried for months. The cap is a prepaid balance with auto-recharge
off, which is a harder ceiling than any setting.

*Two things were found only on the phone.* The app had no INTERNET permission — true and correct
through step 6, never revisited when step 7 added networking — so the first press of Test crashed
rather than failing politely. Neither 345 passing tests, nor a clean lint, nor a successful release
build could have caught it. The permission fixed that bug; catching everything at the seam fixed the
class of it. The other was R8 refusing to finish over an annotation the encryption library
references, which only the release build can find.

**Step 8 — Logging a meal from a photograph. DROPPED, 2026-09-05.** Not deferred again — dropped,
with a reason worth keeping.

The reason: the existing ways of logging already give great value, and a photograph is unlikely to
do better than they do. It cannot really assess size, nor weight, nor ingredients.

That is **R3 confirmed from use rather than from argument.** R3 was written on day one saying portion
estimation from a photograph is the weakest part of the whole idea; real logging has shown
that describing a meal in words does the same job without the weakness, because the owner already
knows the things a photograph cannot supply — how much was on the plate, and what went into it.

It stays here as a dropped step rather than being deleted, because the reasoning is the useful part:
**a photograph would have been a worse version of a feature that already works.**

**Step 9 — Repeat a meal. DONE, 2026-09-04, verified on the owner's phone.** Any previously logged
meal re-logged in one tap, sending nothing. *Verdict:* one tap logs it as it was; the refinement in
step 9a added the portions to the list and the ability to change the amount first.

> **AMENDED 2026-09-24, public issue #21 (from 0.48.0).** On *Add something*, a tap on a saved
> meal's row no longer logs it: it opens the meal's one-day view in place — its parts and amounts,
> with *Log it* and *Leave it alone* — and writes nothing. Logging is the press on *Log it*; left
> untouched, that logs exactly what the tap used to (the same items, the same meal, not marked as
> changed for the day). The separate *Adjust* button is gone, since the tap does its job; *Change it*
> still opens the meal builder. The manager's *My meals* rows already opened on a tap, so on both
> screens a tap on a meal opens it and never writes to the day. An empty meal still opens, and its
> *Log it* is off.

**Step 10 — The streak and the counts. DONE, 2026-09-04.** All derived from the record: current run
of logged days, lifetime days logged, days in the last 30. Today's over-target warning colour was
already in place from step 3 and already limited to today. *Verdict:* filling in a skipped day
restores the run with no other action, proved end to end against a real store on CI. Two decisions
worth keeping in view — the run counts back from yesterday when today is still empty, so the app is
not hostile before breakfast; and a run that has ended prints nothing at all, because "0 days in a
row" is a reproach dressed as a statistic and D14 says the past never nags. (D13, D14)

**Step 11 — The reminder. DONE, 2026-09-04, verified on the owner's phone.** One daily notification
at a time the owner sets, sent only when the day holds no meal, saying nothing more than that.
*Verdict:* the arithmetic of when it falls due, the decision of whether the day earned it, the
wording, and that switching it off cancels the alarm are all tested and green on CI. **That it fires cannot be tested here** — no emulator, no real AlarmManager under
Robolectric, and a reminder is by definition something that happens later on a sleeping phone. So a
"send it now" button was added, on the same reasoning as the API key's Test button, and the owner
confirmed the notification arrives. The alarm is exact where
the phone grants it and inexact where it does not; a failure to schedule at all is written to the
problem log rather than swallowed. (D15)

**Step 12 — Export and restore. DONE, 2026-09-04, verified on the owner's phone.** The whole history
to a single file the owner saves where he likes, and read back. *Verdict:* the format, the refusal of
a file from a later version, and a wipe-and-restore round trip against a real database are tested and
green on CI, including that the portion's numbers and an item's confidence survive. **The API key is
deliberately absent and there is no field for one** — an export ends up in a cloud drive and an
email. A restore replaces rather than merges, and asks first with the counts of what it will destroy.
Both directions have been confirmed on a real phone. (D18)

**Step 9a — Portions survive being saved. DONE, 2026-09-04, unverified against real data.**
The fault: a repeated meal gave no sign that an item's quantity had carried, and no way to
change it. Two faults: the repeat list showed only a meal's name and total, and the record
kept a portion as words with the numbers behind them dropped at the boundary of the database, so
nothing downstream could scale it. The list now shows every item's portion; `FoodItem` carries the
amount and unit beside the words; database version 3 adds the columns and backfills the rows already
there by parsing what they were written as, leaving anything unparseable unadjustable rather than
guessed. Adjusting happens in place on the repeat screen with the same controls the model's
proposals use. One tap still logs a meal exactly as it was. *(Amended 2026-09-24, public issue
#21: from 0.48.0 the tap opens the meal and *Log it* logs it — see step 9.)* *Verdict:* the arithmetic and the
migration are tested and green on CI; whether a real repeated meal comes back adjustable is the
part only a phone check can confirm.

**Step 14 — The goal has a destination. DONE, 2026-09-04, the celebration unverified.** A target weight on the profile; how far there is to go,
measured from the trend; how long it would take at the chosen rate, stated as arithmetic rather than
as a forecast; the target drawn on the weight chart; and something said on arrival. *Done when:* the
owner can see how far he has to go and roughly how long it would take, and neither number pretends
to be a promise. (D21)

*Verdict:* the arithmetic, the once-only recording of arrival and the automatic switch to holding
are tested and green on CI. The celebration itself cannot be verified on demand — it fires when the
trend reaches the goal — so what it looks like on the morning it happens is
unproven. This was chosen to be built before the streak (step 10).

**Step 15 — Milestones along the way. DONE, 2026-09-04, unverified in the wild.** The first kilogram and every fifth; halfway and the last
kilogram; a run of weeks going the right way. Each recorded as reached and never repeated. Nothing
said about a kilogram regained. *Verdict:* tested and green on CI — the arithmetic, the once-only recording, the silence about a
weight going the wrong way, and that yesterday's milestone is not shown today. What cannot be shown
on demand is a milestone actually landing, because that waits on a real trend. Built as
four/twelve weeks for the consistency milestone, every week required to have moved rather than the
average of them. (D22)

**Step 16 — Scanning a barcode. DONE, 2026-09-04, verified on the owner's phone.** Reading a
barcode on the device; looking it up in Open Food Facts; remembering what it found so the second
scan needs no network; falling through to describing the meal when it finds nothing. *Verdict:* everything behind the camera is tested and green on CI — the per-100 g arithmetic, the
reading of a real Open Food Facts reply, the local table that makes a second scan work with no
signal, the version 3 to 4 migration, and telling an unknown product apart from an unreachable
database. **The camera cannot be tested here**; a real packet scanned on the phone worked. A label is
recorded as its own source, `Source.LABEL`, carrying no confidence: not typed and not guessed, but
capable of being stale, which is exactly why D4 wants the record to say where it came from.
Adding the reader took the APK from 2 MB to 21 MB; restricting the build to arm64 brought it to
8 MB, the discarded ten megabytes being x86 models for emulators. (D23, D23a)

**Step 17 — Contributing a product back. BUILT 2026-09-04; an account is set up; no scan has yet
met a product the database is missing.** After the owner has entered a product the database did not
have, an offer to send it — the label's own figures only, nothing about him, never automatic.
*Verdict:* the payload, the host, the reply reading and all three failure paths are tested and green
on CI, including that nothing about the owner goes up beyond the username he is credited by. What remains is a
product the database has never heard of, and no scan so far has met one — which is a better result than the coverage estimate in D23 predicted, and means the
send path is the one thing here that no amount of ordinary use has yet exercised. **The local half needs no account at all** — a packet typed in works for ever
afterwards on his phone, and the contribute button does not appear without credentials rather than
appearing and failing. The write goes to `world.openfoodfacts.org`; their own tutorial demonstrates
`.net`, which is their test instance and would discard his products while looking like success.
(D24)

**Step 18 — The target measures what it used to guess. BUILT 2026-09-04, and unverifiable for a month.** Over a rolling window, what was logged
against what the trend actually did, turned into a correction to the activity multiplier, applied
weekly and announced with its arithmetic. *Verdict:* the arithmetic, the guards and the wording are tested and green on CI, including a test
that the app never calls this a metabolism. **Whether it is RIGHT cannot be known for about a
month** — it needs twenty-two logged days out of twenty-eight and a weight trend across them, and
until then the screen says how many days are still missing rather than guessing. The working sits
behind a button by request; the assumption behind the number does not, because it is a
heading rather than a detail. (D25, D25a)

**D26 — ADDED 2026-09-04. Automatic backup goes to a FOLDER the owner picks, before it goes to
Google.** He wants the copy to land in Drive without him remembering to make it. There are two ways
to arrive there and they cost very different things.

**The folder route needs nothing from anybody.** Android's own directory picker hands an app a
folder and a permission that survives reboots. The owner picks one once — a Drive folder, a card, a
plain folder — and the app writes the same export file into it, by itself. No Cloud project, no
OAuth client, no consent screen, no secret in the APK, and no dependency on Google at all: the same
mechanism backs up to a memory card or a NAS if he ever wants that instead.

**The Drive API route needs a Cloud console project**, an OAuth client tied to the app's package name
and signing fingerprint, a consent screen, and the `drive.appdata` scope. It is the only way to write
to Drive's hidden app folder, and it is what to fall back to if Google's own provider will not accept
a folder from the picker — which is a real possibility and cannot be settled from this machine.

**So the folder route is built first, and the Drive API only if it has to be.** Building the harder
one first, on the chance the easier one fails, would be doing the work in the order that suits the
tooling rather than the owner.

**One export file per day, and the last fourteen kept.** A backup that overwrites itself is one
corrupted write away from being nothing, and a backup that never deletes anything fills a folder
with a thousand files. Fourteen days is long enough to notice something went wrong and go back.

**Step 22 — Correcting a day's movement by hand. DEFERRED 2026-09-05, for about a
month.** Deliberately, and for the same reason as the badges: it is a fix for a problem that has not
happened yet, and a month of ordinary use will say whether Health Connect actually produces days
worth correcting — and which kind. Building it now would be guessing at the shape of a defect nobody
has met. An override for a day whose steps or calories are
plainly wrong, marked as the owner's own number rather than a measurement, feeding the usual-day
baseline like any other day. *Done when:* a day that read 30,000 steps from a car journey can be set
to what he actually walked, and says that he set it. (D12d)

**Step 24 — Encouragement, and the window as a mark. BUILT 2026-09-05.** The sentence about the
window replaced by a drawn ring — filled while open, empty while shut — carrying the count of days
kept and opening the settings when tapped. Yesterday judged once, at or after the window's opening
hour rather than at midnight, because a verdict delivered at one minute past is delivered to nobody.
And one encouraging line a day at most, chosen from what the record already holds. *Verdict:* the
occasions and the budget are tested; whether one a day is the right number is a thing only living
with it can say. (D22, D27)

**Step 23 — The eating window. BUILT 2026-09-05, awaiting a phone check.** A start hour and an end hour, effective from a chosen day and never
applied backwards; today saying when a meal falls outside it, the past stating it without colour, and
a count of days kept derived from the record. *Verdict:* setting a window today leaves every previous day exactly as it was, proved end to end
against a late dinner logged yesterday. Windows are stored as a HISTORY rather than a single value,
so changing one leaves the old governing the days it governed. A meal written down on a different
day from the one it belongs to has no trustworthy hour — the record keeps when it was typed, not
when it was eaten — and is counted as untimed rather than judged, so filling in Tuesday on Thursday
cannot produce a false accusation. (D27)

**Step 13a — The Drive copy. DONE, 2026-09-05, verified on the owner's phone.** A second destination
beside the folder, never a replacement: Drive is attempted whether or not the folder copy worked, and
its failure cannot cost him the local one. A backup with a single way to fail is not a backup.

**`drive.file`, not `drive.appdata`** — corrected before building. `appdata` writes to a hidden
folder he cannot open or download, so the case the feature exists for, a lost phone, would need the
app reinstalled rather than a browser. `drive.file` grants access only to files this app created,
which is the same guarantee with the file left visible to him.

**No refresh token is ever held.** Google's authorisation client is asked for an access token each
time one is wanted, so there is no credential on disk, in the backup, or to leak. And **no Google
Java API client libraries**: `play-services-auth` for the token, then OkHttp — which this app already
had — for the upload. One dependency rather than a tree of them, and a request whose contents can be
read.

**Step 13 — Automatic backup, to a folder the owner picks. DONE, 2026-09-04, verified on the
owner's phone with a folder on the device.** One export a day, written when he next opens the app,
the last fourteen kept, and nothing deleted that the app did not name.

**The phone-loss gap is now closed** by step 13a: the same copy also goes to his own Drive, where he
can download it from a browser with no phone involved. The folder copy remains the first destination
and is unaffected by anything Drive does — two places, failing independently, which is the whole
point of having two.

*The Drive route remains deferred at his choice*, pending the console work only he can do; it is
written down in `docs/drive-backup-console-setup.md`. The Drive API alternative, if it is ever needed, wants the
Cloud project and OAuth client (owner console work,
written out step by step), connecting the account in settings, a scheduled daily upload to the app's
own private Drive folder, and restore on reinstall. *Done when:* a reinstall recovers the history
with no file handling by the owner. (D18, D19)

## 6. After milestone 1

Named so the road exists, deliberately not designed:

1. **Steps DONE 2026-09-05, verified on the owner's phone; workouts still to come.** Two defects were found by
   using it rather than by testing it. The first read of the step history asked Health Connect for
   raw records and took only the first page of a thousand — a phone writes one every few minutes, so
   a month of walking came back as four days and the app reported itself as still learning; asking
   for daily totals instead fixed it and is a better call besides. The second was a design fault:
   the count was hidden on any day that earned nothing, which was corrected (D12a).

   Exercises such as swimming were to be planned but not built yet, because steps miss weights, cycling
   and swimming almost entirely. The credit records which
   signal produced it, so a swim becomes an addition beside steps rather than a rewrite of them:
   the same trick that let a barcode's label join the AI's estimate without disturbing it.

   ~~Steps and workouts arriving automatically from the band (R4 first), and with them the
   surplus-only earn-back mechanism of D12.~~ *This is the one genuinely large thing left anywhere in
   this document.* It is designed (D12) and unbuilt, it would change the daily target every day
   rather than weekly, and it is blocked on a question nobody has answered: **whether the owner's
   band actually publishes its data where an app can read it (R4).** That question costs him a
   few minutes on his own phone and costs this machine nothing, because it cannot be answered here.
2. ~~A second, more trustworthy source of numbers for packaged food, ranked above the AI estimate by
   D4's source field.~~ **DONE early, 2026-09-04** — barcode scanning against Open Food Facts, stored
   as `Source.LABEL`. The *ranking* is not built and nothing yet needs it: the record says where each
   number came from, which is what D4 asked for, and no behaviour depends on ordering them.
3. **Badges, if the encouragement earns them.** Raised as an idea, and explicitly for later — a
   month or so on. The reason to wait came with it: too much praise diminishes its
   value, and a badge system is praise made permanent and collectable. It is worth building only if
   the plainer encouragement turns out to be worth having, and worth abandoning if it does not.

4. **The pilot: one real user, from 5 September 2026 to roughly 5 October.** No building during it
   unless something is broken. This slot was left intentionally blank on day one because neither the
   owner nor the design could honestly predict what a habit would need before the habit existed. It
   now has a start date and a set of questions it is meant to answer:

   - **Does the self-measuring target agree with his body?** It needs twenty-two logged days out of
     twenty-eight before it speaks at all. When it does, the number it produces against the formula's
     is the single most interesting result this app can produce.
   - **Is a hundred calories a week the right speed for that correction, and 600 the right bound?**
   - **Does the credit for a long walk feel fair?** Too stingy and he ignores it; too generous and
     it quietly undoes the deficit.
   - **Does one encouraging line a day wear well, or grate?** Too much praise diminishes it. This is the test of whether the budget was set right.
   - **Does the eating window's mark read at a glance**, and does the verdict at its opening hour
     arrive when he is actually looking?
   - **Does Health Connect ever produce a day worth correcting by hand** — and of which kind? That
     answer is what step 22 should be built against, rather than a guess at the shape of a defect
     nobody has met.
   - **Does his band report active calories at all?** Settings says. Until it does, swimming and
     weights are worth nothing. Whether the permission is granted is visible in Settings, so this is
     a thing to check rather than to wonder about.
   - **KNOWN GAP, not yet fixed: on a band-driven day the screen explains itself in the wrong
     currency.** The credit is decided on activity ENERGY — the larger of the steps figure and the
     band's own (D12b) — but the line beneath the count still compares STEPS. On a swimming day those
     say opposite things: steps barely move, so the screen would read "your usual day is" some step count (illustrative) as
     though it had been quiet, while the band's figure was earning calories underneath. It cannot
     appear while the band's active calories are not reaching the app; it will appear the
     moment they do. The fix is for that line to speak in whichever
     reading decided the credit. Half-written once and abandoned when a more urgent defect arrived,
     then superseded by the ActivityEnergy work — so it is recorded here rather than left in a stash
     nobody would find.
   - **Does the Drive copy keep working unattended?** It has been written once. An access token is
     asked for afresh each time so nothing should expire, but that is a claim about Google's
     behaviour rather than something this app can guarantee, and only the month will show it.

   Whatever else the month shows to be missing. This is intentionally blank: neither the
   owner nor the design can honestly predict what the habit will need until it exists.
