# The day is a page — D48 and D49

> Written 2026-09-21, after one of three directions worked up by the design committee
> (`docs/superpowers/reviews/2026-09-21-design-committee.md`, issue #55) was chosen. All three were
> drawn as phone mockups beside the screen as it stands, and **the Typeset Day** was picked.
>
> The complaint that started it: the day screen is too crowded, and is designed neither to be
> attractive nor to be useful.

---

## Why there is a decision here at all

The app has never had a design. It has a colour scheme and nothing else: no typeface was chosen, no
type scale, no ink ladder, no spacing rule beyond one named set of four gaps that most screens
ignore. Everything is Android's stock default, which means **the biggest number in the app is set in
the same face and the same weight as the small print under it**.

The consequence is that complaint, and it is a structural one rather than a matter of taste:

- **Nothing ranks.** Fourteen separate things on the day screen are drawn at one volume, so the
  screen cannot be read at a glance and has to be read line by line.
- **Nothing groups.** A single 16 dp gap separates everything from everything, so the gap between a
  heading and its own content is the gap between two unrelated sections.
- **Numbers wobble.** Proportional digits mean a column of calorie figures never lines up, which is
  the one thing a column of numbers exists to do.
- **Grey does all the work.** Brand, aliases, calories and footnotes are one secondary grey, so
  finding a food means reading rather than scanning. **This, and not contrast, is the reason for an
  ink ladder** — the small print has always been legible enough; it has never been *ranked*. The
  colour actually doing every job is `secondary`, with 91 uses across the app, and moving those onto
  the ladder is the work that makes the difference visible on the phone.

---

## D48 — The app has a face: one display serif, one text sans, four ink steps, and figures that line up

**The app bundles two typefaces and defines every text role from them. No screen sets a size, a
weight or a colour of its own.**

### The two faces

| | Face | Weights bundled | What it is for |
|---|---|---|---|
| Display | **Fraunces** | 200 ExtraLight, 300 Light | Numbers that answer a question: the calories left, a meal's total, a weight. Nothing else. |
| Text | **Work Sans** | 400 Regular, 500 Medium, 600 SemiBold | Every word in the app. |

Both are SIL Open Font License 1.1; the licences are committed at `docs/licences/`. Five static
instances, 476 KB in total, in `app/src/main/res/font/`.

**Two Fraunces weights, not three.** A Medium (500) instance was bundled at first and no slot in
the scale below pairs the display face with Medium, so it was 71.6 KB that shipped in every APK and
could never be drawn. It was removed. Fraunces' ExtraLight file is really `usWeightClass` 250 and is
registered at 200 because Compose has no 250 and 300 is already the Light file's — the nearest
position that is still its own instance, rather than a synthesised smear of a neighbour.

**Static instances rather than the variable fonts**, though both families ship one and `minSdk 26`
would carry it. A variable font needs `FontVariation.Settings` at every call site that wants a
weight the axis can reach, which is a per-use decision — exactly the thing this decision exists to
remove. Five files are smaller than the argument.

**Bundled rather than downloadable.** This app is offline-first and single-user. A downloadable font
provider needs Google Play Services, a first-run fetch, and a fallback for the fetch failing — three
new ways for the app to look different from itself on the morning it is opened without a signal.

### The type scale

Seven roles, and Material 3's fifteen slots map onto them. A screen names a role; it never names a
size.

| Material slot | Face / weight | Size / line | Figures | Used for |
|---|---|---|---|---|
| `displayLarge` | Fraunces 200 | 72 / 64 sp | tabular | The one number a screen exists to answer. |
| `displayMedium` | Fraunces 200 | 44 / 44 sp | tabular | A secondary answer: a trend, a total. |
| `headlineLarge` · `headlineMedium` | Fraunces 300 | 28 / 34 sp | tabular | A figure inside a block. |
| `headlineSmall` · `titleLarge` | Work Sans 600 | 20 / 26 sp | — | A screen's own title. |
| `titleMedium` | Work Sans 600 | 16 / 22 sp | — | A section heading. |
| `titleSmall` | Work Sans 600 | 13 / 18 sp, +0.14 em, upper | — | A small-caps kicker over a group. |
| `bodyLarge` | Work Sans 400 | 16 / 24 sp | — | A thing on a list: a food's name. |
| `bodyMedium` | Work Sans 400 | 14 / 21 sp | — | Ordinary prose. |
| `bodySmall` | Work Sans 400 | 12.5 / 18 sp | tabular | A row's figures, a caption. |
| `labelLarge` | Work Sans 500 | 14 / 20 sp | — | A button. |
| `labelMedium` | Work Sans 500 | 12 / 16 sp | tabular | A chip, a small figure. |
| `labelSmall` | Work Sans 600 | 11 / 15 sp, +0.16 em, upper | — | The colophon, an axis label. |

**Every slot that can carry a figure sets `FontFeatureSettings("tnum", "lnum")`.** Tabular lining
figures are why a column of calories lines up and why a number that changes does not shuffle the
words beside it. This is not a preference; a proportional digit in a table is a defect.

**Nothing in the scale is smaller than 11 sp**, and nothing relies on a weight lighter than 400 for
running text.

### The ink ladder

Four steps, and no fifth. Grey stops being a single colour used for everything.

| Step | Light | Dark | For |
|---|---|---|---|
| **Ink** | `#161A17` | `#E9EBE6` | Anything that is an answer: names, figures, headings. |
| **Ink two** | `#2B322D` | `#D5DBD4` | Prose that is read but not scanned. |
| **Ink three** | `#414942` | `#C0C9C1` | Captions, origins, the colophon. |
| **Rule** | `#DAE0D8` | `#333A34` | Hairlines and dividers. Never text. |

Every text step is measured against **both** backgrounds it can land on — the page, and
`surfaceVariant`, which is a real card background in this app — in both schemes. Every one clears
4.5:1, and **no step is worse than the value it replaces**:

| Step | Light on page | Light on card | Dark on page | Dark on card |
|---|---|---|---|---|
| Ink | 17.17 | 13.64 | 15.44 | 7.75 |
| Ink two | 12.84 | 10.20 | 13.17 | 6.61 |
| Ink three | 9.09 | 7.22 | 10.92 | 5.48 |

`outline` keeps its role as a **border colour and never text**. It is not part of this ladder, and
the one place drawing WITH it — the shut-window mark, a ring-and-dot glyph rather than text —
must move to `onSurfaceVariant`. It is the whole of what tells him the window is shut, so it
belongs on the ladder rather than at a border's tone.

> **Corrected twice on 2026-09-21, and the second correction matters.**
>
> The first correction fixed a single ratio stated as 5.1 when it was 5.71.
>
> The second was found by an independent verifier and is worse: **this ladder's whole premise was
> false.** It claimed the app's small print failed contrast and needed lightening. It did not. The
> caption colour on `main` was `#414942`, which measures **9.09:1** on the light page — comfortably
> passing. No colour in the palette was ever the "3.9:1" this document asserted. Built on that
> premise, the first draft of this ladder made every value it touched *worse* (light captions
> 9.09 → 5.71, dark 10.92 → 6.91), and put dark captions on a card at **3.47:1**, a real AA failure
> that did not exist before.
>
> The steps above are the repaired ones. **The reason for an ink ladder was never contrast** — the
> app's small print was always legible. It is that one grey was doing every job, so nothing ranked.
> That is what the ladder fixes, and it must fix it without costing a single point of legibility.
>
> The lesson is the app's own D4, turned on its documentation: a number that was never measured must
> not be written as though it were.

**Red stops meaning three things.** It currently means over target, a refusal, and a typo in a
field, so a day that ends 180 calories over target looks like a validation error. It
keeps the two that are errors, and **over target becomes ink, not red** — a fact about the day, said
in the same voice as every other fact (D14 already says the past is never coloured; this extends the
same reasoning to today).

### The spacing scale

`Spacing` already names four gaps and is already right. The decision is that **screens use them and
stop passing raw `dp`**: 4 inside one thing, 8 between things that belong together, 16 between
sections that do not, 24 around the edge of a screen.

---

## D49 — The day is a page, not a dashboard

**The day screen answers one question in one number, and everything else recedes a full step.**

The order down the page, and what each thing is set in:

1. **The date, as a kicker.** `labelSmall`, letterspaced, upper, Ink three. It stops being a title
   bar's headline and becomes the quiet line a page begins with.
2. **The number.** `displayLarge`, Fraunces 200, tabular, Ink. Nothing else on the screen is within
   two steps of it.
3. **What it means**, on one line: `bodyMedium` Ink, then `bodyMedium` Ink three — "kcal left" and
   "of 2,090". **Never wrapping and never saying "kcal" twice**, which the ring's headline does
   today.
4. **One hairline rule**, 2 dp, filled to the proportion eaten. It carries what the ring carried and
   costs 2 dp of height instead of 200.
5. **The three macros as columns**: a `titleSmall` kicker, a `headlineMedium` figure, a 1 dp rule
   under each.
6. **The step count, on its own line directly beneath them**, in the macros' own treatment: a
   `titleSmall` kicker, the count as a `headlineMedium` Fraunces figure, and what it earned beside
   it. Both numbers, because the earned calories are what actually move the figure at the top of the
   page and D9 says he must be able to see exactly what earned them.

   **This was asked for by name on 2026-09-21**, and the first draft of this decision was
   wrong to bury it: steps were folded into the colophon at item 8, in the smallest type in the app.
   The argument for moving it up is stronger than preference — **a thing that changes the headline
   cannot be a footnote.** D12a is untouched: the count still shows every day whether or not it
   earned anything, and a day that earned nothing still says nothing about the zero, because
   printing "0 kcal earned" would turn an ordinary day into a reproach.
7. **Notices as margin notes**: a 2 dp accent edge on the leading side, `bodyMedium` Ink two, a
   dismiss glyph. They stop being cards that interrupt the page.
8. **The day's sittings, one line each — and every line is a door.** What the sitting was, when it
   was, its total. No items, no macros, no detail. Tapping a line opens the day's record (D50) there,
   which is where detail and correction live. **A sitting is D51's grouping, not a stored thing.**

   > **Revised twice, and the second time because of an error caught in the mockups.** The first
   > revision said "one line per meal". **A `Meal` in this app is one logging
   > action** — its own KDoc says "one logging event: a moment, and the things eaten at it" — so
   > porridge and coffee logged separately are two meals, not one breakfast. The mockups drew
   > "Breakfast / Lunch / Snack / Dinner" as though daily meal slots existed. **They do not exist
   > anywhere in this app**, and one line per meal would therefore have been close to one line per
   > food, which is barely shorter than what it replaced. See D51.

   > **Revised 2026-09-21, after 0.35.0 was in use and four options were drawn.** The one chosen:
   > one line per meal, with each line a way through to the full food log, where an edit can be
   > made.
   >
   > **This item previously put every item of every meal on the day**, with its figures beneath. That
   > is what pushed the colophon past the bottom of the page on a day with only a handful of meals, so
   > the footnotes could only be reached by scrolling, and scrolling took the number at
   > the top of the page away with it.
   >
   > **The reasoning, which is his and is sharper than this decision's first draft:** recording
   > something and reviewing what you recorded are two different jobs. The day answers *how am I
   > doing*. The record answers *what exactly did I write down, and is it right* — and that second
   > job is rare, deliberate, and deserves the whole screen when it happens.
   >
   > **One line per meal grows with meals rather than with things eaten**, which is what makes the
   > page fit. It keeps the shape of the day — when he ate, and how much — and gives up only the
   > detail that is rarely wanted. The two rejected alternatives are recorded because the reasons
   > matter: a single summary line frees the most space but leaves no shape at all; the two most
   > recent meals cost the most and overflow again on a busy day.
9. **The colophon.** The remaining small grey lines — the window, the streak, the ratio tally —
   collect into one `labelSmall` block at the foot of the page. With the item list gone it now fits
   on an ordinary day, which was the whole point of moving it there.

**The ring goes.** It was added to make the day readable in half a second and a 200 dp circle whose
own headline wraps over its stroke does not do that. The rule does, in a hundredth of the height.

> **Corrected 2026-09-21, and this is the second time in one project.** This paragraph said "a 160 dp
> circle" and "a fortieth of the height". `RING_SIZE` is **200.dp** — measured, not remembered — so 2 dp
> is a **hundredth**. The old figure was wrong twice over: wrong about the ring, and "a fortieth" was
> not even right for the 160 it invented (that would be an eightieth).
>
> It then propagated exactly as the contrast figure did in D48: into `ProportionRule`'s KDoc, and into
> a brand-new test file's KDoc — the one artifact whose whole job is to hold facts.
>
> **The rule this project now has, written twice because it was learned twice: a number in a spec is a
> measurement or it is not written.** There is no third kind.

**What does not change.** Not one stored number, not one calculation, not one thing the app knows.
D4 stands: an estimate is still never presented as a measurement, and every badge that says where a
figure came from stays exactly where it is. D14 stands: the past is never coloured and never nags.
D13's streak figures are unchanged in what they count — **where they are shown is issue #65's
decision and is not settled here.**

---

> ## D49 WAS REVISED — resolved 2026-09-21, the same day it shipped
>
> In use, 0.35.0's day screen scrolled because of the day's food list, and scrolling took the number
> at the top of the page away with it. The sharper point this decision had missed: **recording
> something and reviewing what you recorded are two different jobs**, and D49 had put them on one
> page.
>
> The obvious over-correction was refused too. A hard non-scrolling rule is a bad bargain — it would
> be enforced by deleting things and would break on a day with four notices at a large font. But
> treating scrolling as a given is worse in a quieter way: once scrolling exists it solves every
> space problem by scrolling, and that deal is not as good as it looks. It answers every space
> question before the question is asked, so nothing is ever ranked, and the page grows back into the
> dashboard it just stopped being. **That is how the day reached fourteen items at one volume in the
> first place.**
>
> **So the standing rule is: the page is designed as though it must fit, and scrolling exists as an
> overflow it is not meant to need.**
>
> Item 8 above carries the revision; **D50 below** is the screen the record moved to. Tracked in
> #68, with #69 for the three ways in becoming real buttons.

---

## D50 — The day's record has a screen of its own

**Chosen 2026-09-21, as the other half of D49's revision.**

> The reasoning it was chosen on: the food log is what the app is built on, but after logging, it
> is rarely of interest. When it is looked at, it is because something in it needs fixing — and
> then it deserves the whole screen.

**One tap from the day, from any meal's line.** It opens at the meal that was tapped.

### What it shows

1. **What the day came to**, in one line at the top: the total recorded and how many things — not
   the target, not what is left. This screen is about the record, not about progress.
2. **Every meal, in full**: the `titleSmall` kicker with its time, then each food as `bodyLarge` with
   its figures beneath, exactly the treatment D49 first asked the day for. **It belongs here**, where
   there is room for it and where correcting it is the point.
3. **Choosing rows**, and the actions for what is chosen **pinned to the bottom edge**.

### Why the actions belong here

This is the answer to two issues that had nowhere good to live:

- **#41** wanted the chosen-items bar pinned so it cannot scroll away. On a screen whose whole job is
  the list, a bar rising from the bottom edge is the standard shape and has somewhere to be.
- **#50** wanted a logged meal removable in one go. Ticking a meal's kicker takes its whole meal.
  **Moving the list made both easier rather than harder**, which is the test of whether a structural
  change was the right one.

### How a day is chosen

**It shows the day it was reached from, and offers no way to travel to another.** Days are chosen on
the day screen, which is where they have always been chosen; this screen is reached from a day, for
that day.

> **Amended before any code, 2026-09-21.** The first draft of this decision gave the title bar a date
> picker. The study pass showed that would be a defect rather than a convenience, for a reason
> neither this decision nor its plan had noticed:
>
> **`EditEntry` resolves the row to correct by searching the day view model's currently selected
> day.** Finding nothing, it pops straight back out. That works today only because the pager's day and
> the view model's day are always the same — and a picker on this screen is precisely what breaks
> that. **Every "correct this row" tap on a day the pager was not on would silently bounce.**
>
> The alternative — having this screen drive the selected day — collides with an invariant
> `DayPager` states in its own KDoc: the pager is the source of truth for which day is shown, and two
> owners of "which day" is how a pager ends up drawing one date under another's heading. Changing
> that is a real piece of work and is outside what was asked for.
>
> So: **no travel on this screen.** It costs nothing that was asked for, removes an entire class of
> bug, and matches what this screen is — reached deliberately, rarely, from a particular day, to fix
> something on it. A picker can be added later as its own decision, with the ownership question
> settled first.

### What does not change

Not one stored number, calculation or provenance badge. Correcting a row is the editor that already
exists (`EntryEditorScreen`), reached from here instead of from the day. Deleting still offers Undo,
and the receipt still comes from the store in the same transaction (#37).

---

## D51 — The day is four fixed parts of the clock, and they are never named after meals

**Revised 2026-09-22, before anything was built.** The requirement, which the first version of this
decision could not meet:

> A grouping that ends in at most four rows, and that is clear enough to follow without being
> explained — because a single item logged on its own, a lone apple, still has to be findable.

### Why the gap rule went

The first version grouped loggings within 90 minutes of each other. It fails both halves of that
requirement, and the failure is structural rather than a matter of tuning:

- **The row count is not bounded.** Six loggings spread through a day produce six rows — which is the
  option he had already rejected, arrived at by accident.
- **It cannot be followed in the head.** To know which row holds a thing, he would have to remember
  every logging's time and compute the gaps between them. A rule he cannot run is a rule he cannot
  use to find anything.

### The rule

**The day has four parts, by the clock, and every logging falls into exactly one:**

| Part | Hours |
|---|---|
| **Morning** | 06:00 – 11:59 |
| **Midday** | 12:00 – 17:59 |
| **Evening** | 18:00 – 23:59 |
| **Night** | 00:00 – 05:59 |

**On the screen they are drawn in clock order — Night, Morning, Midday, Evening.** The table above
reads in the order a person describes a day; the screen runs 00:00 to 23:59. Drawing Night last would
make it the one row where a later logging sits above an earlier one, which is the rule breaking
itself on the very screen that prints the hours.

**A part with nothing in it is not drawn**, so an ordinary day is two or three rows and can never be
more than four.

**Locating anything is one step: he knows when he ate it, so he knows its row.** No arithmetic, no
dependence on what else he logged, no dependence on the order he logged it in. That is the whole
point, and it is what the gap rule could not give him.

### These are parts of the clock, not names for meals

The second part is named **Midday** rather than Afternoon, decided 2026-09-22. Kept as one word,
which is the ordinary spelling; the band it labels is unchanged.

**"Morning" is true by definition of the hour. "Breakfast" is a claim about what the eating was.**
The first is a fact; the second is an inference the app would be printing as a fact, which is D4's
defect and would be wrong on any day an eating window puts the first meal late in the morning.

So a row says the part **and its hours** — the rule is on the screen, not buried in the code — and
says nothing about what kind of eating it was.

### What a row says

The part and its hours; what is in it, from what already exists (a meal he built keeps its name,
otherwise the first food's name, with a count when there is more than one thing); and its total.

### The one exception to "max four", and it is deliberate

**A logging written down on another day has no honest time** (D33), so it belongs to no part of the
clock. Those get **one further row, always last**, and it can make five.

That is correct rather than a flaw: it is the only row that is asking to be fixed, and the colophon
already tells him to go and fix it. **Hiding it inside a part of the day it might not belong to would
be guessing, which is the thing this decision refuses to do.**

### Still not stored

Computed from `loggedAtMillis`, which is already there. No migration, no schema change, no new
column. The four boundaries are named constants; **changing them changes a view and loses nothing.**

### The consequence D33 has to absorb

A part of the day holds several loggings with several times, so **the day's row cannot be the
tappable control that sets an unknown time.** That moves to the record screen, where each logging is
its own row again — and the colophon's *"Tap its time to set it"* must say where instead. **That
wording change is part of building this.**

## What this is not

- **Not motion.** Touch feedback and haptics are the agreed follow-on (issue #54) and are a
  separate decision. Nothing here animates.
- **Not the hour dial.** The 24-hour clock from the third direction is wanted later and is not part
  of this.
- **Not the time-lit background.** Explicitly rejected: it would fight every contrast decision
  underneath it for ever.
- **Not the chart.** Giving the weight chart a plot area, gridlines and a legend (issue #53) lands
  on top of this and after it.

---

## The order it is built in

**Two steps, two branches, because the first is worth having on its own.**

**Step one — the face.** The fonts, the type scale, the ink ladder, and the theme that serves them.
Every screen in the app changes the day it lands, and not one layout moves: the app has 242 calls to
`MaterialTheme.typography.*` and no `Typography` of its own, so defining one reaches all of them.
Issue #52.

**Step two — the page.** The day screen relaid out as described in D49. Issue #55's direction, now
decided.

Step one has to be first and has to be separable: if the face is wrong, that is one revert and the
day screen is untouched.
