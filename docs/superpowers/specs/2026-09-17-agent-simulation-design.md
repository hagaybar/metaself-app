# A robot user walks the app — design

> Asked for 2026-09-17, while the foods-and-meals freeze stands. The requirement: an agent that
> inserts foods and meals through the app itself, so as to experience the doing of it, with that
> experience and its result reviewed adversarially. A binding constraint came with it: the agent
> must **exercise multiple paths until the paths are exhausted**, not walk one route and stop.
>
> This changes no production code. It is a test-side harness, a walk, and a report.

## 1. Why this is not just another test

The 1235 tests already green all share one shape: a screen is handed a state object, drawn once, and
asserted about. Nothing types. Nothing holds a row down. Nothing presses a button and then looks at
what the screen says next — `click` invokes the action and the helper's node list is left pointing
at the render from before. And nothing crosses from one screen to the next, because every screen
composable is stateless and the wiring that gives them behaviour lives in `MetaSelfNavHost`, behind
Hilt.

So the suite proves that each screen draws what it is given. It cannot prove that a person holding
the phone can get from an empty food list to a logged meal, because no test has ever tried.

That gap is exactly where the owner's uncertainty sits. He froze foods and meals on 2026-09-17
because he could not tell whether the lists were unfamiliar, incomplete or broken. A walk cannot
tell him which of the three either — only he can judge habit. What a walk can do is find the places
where **no amount of habit would help**: a refusal with no way out, a step that needs knowledge the
screen never gave, a route that simply ends.

## 2. What gets built

Four pieces, all under `app/src/test`, none touching `app/src/main`.

### 2.1 A live session, not a series of renders

`ComposeRender` today builds a fresh `ComponentActivity` on every `texts()` call, which is correct
for asserting on one drawing and fatal for a session: the second look throws away the state the
first look created. The session mode keeps one composition alive for the whole walk.

Three additions:

- **`type(prefix, text)`** through `SemanticsActions.SetText`. `ui-test-junit4` is not a dependency
  and `performTextInput` is therefore unavailable, but `SetText` lives in `compose-ui` proper, which
  is already on the test classpath.
- **`hold(prefix)`** through `SemanticsActions.OnLongClick`. This is not decoration: holding a row
  is how choosing begins on both the food list and the day, the two gestures the manager release
  introduced, and no test has ever performed one.
- **Re-reading after an interaction** — idle the main looper and re-walk the same semantics owner,
  so the node list reflects what the screen says *now*. Without this the driver is blind the instant
  it presses anything, which is the single largest gap.

### 2.2 A shell that joins screens together

`MetaSelfNavHost` cannot be driven: ten of its twelve `hiltViewModel()` calls sit inside
`composable { }` blocks with nothing to override, and `hilt-android-testing` is not a dependency.
Adding that dependency to reach a nav graph would be a larger change to the build than the thing
being tested.

Instead, a test-side shell holds a small route stack and renders the real screen composables with
real `ViewModel`s constructed by hand. Every view model in the walk takes repository **interfaces**
through a plain constructor, so this is construction, not mocking:

| View model | Takes |
|---|---|
| `FoodsViewModel` | `FoodRepository`, `Now` |
| `MealsViewModel` | `SavedMealRepository` |
| `ManagerViewModel` | nothing |
| `MealBuilderViewModel` | `SavedMealRepository`, `FoodRepository`, `Now`, `SavedStateHandle` |
| `DayViewModel` | `ProfileRepository`, `MealRepository`, `WeightRepository`, `SavedMealRepository`, `FoodRepository`, `Today`, and the moment |

**This shell is a second copy of the navigation wiring, and that is a real cost.** It can drift from
`MetaSelfNavHost` and then prove something about a route the app does not have. The mitigation is
narrow and deliberate: the shell copies the `onX ->` navigation lambdas verbatim from the nav host
and is reviewed against it as part of the adversarial pass (§4, third target). It is not a
substitute for the nav host and nothing about it should be read as testing navigation itself.

### 2.3 Storage that is a stand-in, and says so

Room cannot run on this machine: Robolectric's SQLite is a native library with no aarch64 build, so
eight test classes stand aside here and run in CI. The walk therefore runs against the in-memory
fakes — `FakeFoodRepository` and `FakeSavedMealRepository` already exist and are faithful about the
identity rule and provenance ranking; `FakeMealRepository` has to be lifted out of
`DayViewModelTest` where it is currently private; a fake `WeightRepository` has to be written.

**Every finding therefore carries an asterisk**: it was found against a stand-in for the database.
A fake that is kinder than Room — accepting a name Room's uniqueness rule would refuse, say —
manufactures a clean walk that the phone would not give. Attacking this is the third target of the
review and is not optional.

### 2.4 A command file and a transcript

One run of one screen's tests on this box takes 32 seconds, so an agent that stopped to think after
every tap would crawl. The driver therefore reads a **batch** of commands from a file, applies them
in order to the live composition, and writes a transcript recording, after every single step, what
the screen said. The agent appends the next batch and re-runs. Thinking is batched; observation is
not — every step is still recorded individually.

Commands: `press <text>`, `type <text> into <field>`, `hold <text>`, `back`, `look`. A command that
finds no such node is not a crash; it is recorded as **"nothing on this screen said that"**, which
is itself a finding — it is what being stuck looks like from the inside.

The transcript ends with a dump of what was stored: every food, every saved meal, every logged row,
with its numbers and its provenance.

## 3. The walk

The agent is told what it wants to eat, in plain words, and nothing about how the app works. It does
not get the source, the screen names, or this document. It starts where a new user starts: both
lists empty.

**Path exhaustion is the requirement, and it is the scope of the run.** The agent does not stop
at the first route that works. For each of the goals below it enumerates the ways in that the
screens actually offer, tries each, and records what each one cost. The run ends when a full pass
turns up no route it has not already walked.

The goals, in the order a person would meet them:

1. **Get a food into the list at all.** Known routes: creating one directly in the manager, and one
   arriving as a side effect of logging something. Both must be tried; the decision record requires
   them to apply the same naming and duplicate rules, and nothing has ever checked that they do.
2. **Build a meal.** Known routes: the plain "build a meal" button, picking foods out of the food
   list and making a meal of the chosen, and making a meal out of rows already on a day.
3. **Log it, then change it.** A saved meal logged onto a day, then adjusted for that day only —
   adding, removing and changing amounts, which the decision record says is allowed and no walk has
   confirmed.
4. **Put something right.** Rename a food, correct a number, join two entries that are the same
   thing. This is the manager being a manager rather than a list.
5. **Hit the three things already in the feedback log.** A drink described in words, which comes
   back as its ingredients with no way to name the whole; something liquid, which the app can only
   think of in grams; and a row on a day that is not attached to a food and so cannot join a meal.
   The walk is not there to confirm they exist — it
   is there to find out how they behave when met without foreknowledge, and how far the path gets
   before it stops.

Every point where the agent could not tell what to press is recorded as prominently as an outright
failure, because that is the class of defect this whole exercise exists to surface.

## 4. The adversarial review

A **separate agent**, which did no walking, attacks three targets. Separation is the point: the
walker has an interest in its own route having been sensible.

1. **The path.** Where did it stall, what did it have to guess, what did a screen refuse without
   saying how to proceed, what did it get right only by accident. A route that worked because the
   agent happened to press the right thing is a route that failed.
2. **The result.** What ended up stored. Are the numbers what the inputs imply; did anything acquire
   a value nobody supplied — **D4 is the standing rule that an estimate is never presented as a
   measurement**, and a walk is a good way to catch a number quietly inventing itself; did one food
   end up in the list twice.
3. **The simulation itself.** The target most easily skipped and the one that decides whether the
   other two mean anything. Is the shell's wiring still what `MetaSelfNavHost` does? Are the fakes
   kinder than Room? Did a command silently match the wrong node — `press` takes the *first* node
   whose text starts with the given words, so two buttons sharing a prefix make the transcript a
   lie. Did the walk pass because the harness was gentle?

## 5. What comes out, and what does not

Findings, sorted into the three categories the requirement names: **a path not yet got used to,
something genuinely missing, or a bug.** They go into the feedback log alongside what has been
reported from the phone.

**Nothing is fixed.** The freeze on foods and meals stands until the findings have been read and
lifted. A finding from an agent that has never used this app is weaker evidence than a complaint
from real use, and the two must not be merged.

## 6. What this cannot prove

- **Anything about how it looks.** No layout, no size, no colour, nothing about whether a control is
  too small to hit or a row too cramped to read.
- **That an unfamiliar path is a bad one.** The agent has no habits, so it cannot distinguish a path
  that is wrong from a path it simply has not learned. Only the owner can.
- **That the database agrees.** Everything is against fakes. A finding survives to CI or it does not.
- **That the real navigation does this.** The shell is a copy, and §2.2 says what that costs.

## 7. The decision for the record

No `D` number. This adds no rule to the app and amends nothing: it is a test harness and a report.
If a finding from it changes a decision, that change gets its own number then, argued on the finding
rather than on the existence of the walk.

## 8. Known artefacts of the harness, recorded as they were found

Kept here so the adversarial pass has a head start and the report does not blame the app for them.

- **Back does nothing on the first screen.** The walk starts on one screen with nothing behind it, so
  the back arrow is drawn and has no effect. In the app this screen is reached from the day and Back
  returns there. A walk beginning at the manager therefore meets a silent dead control that the phone
  does not have. Starting at the day (`simulation/start.txt`) avoids it, which is why the day is the
  default.
- **Three routes are dead ends by construction**: describing a meal in words, scanning a barcode, and
  editing a single entry. They need a language model, a camera and a screen the shell does not carry.
  Nothing happens when they are pressed, and that is the harness, not the app.
- **A stand-in can be STUPIDER than the real thing, not merely kinder — and that is worse.** Twice,
  before the adversarial pass even ran, this harness manufactured a false finding that the walk
  reported in good faith at the top of its report:
  1. `onAdd` and `onRepeat` were wired to the same screen, so "Type the numbers" opened the repeat
     list. The walk reported a button whose name promises one thing and delivers another, and said it
     pressed it twice assuming a mis-hit.
  2. `FakeSavedMealRepository.put` silently drops a food it has not been told about through
     `knowsAbout(...)`. The walk creates its foods as it goes and told it about none, so every "Put it
     in" did nothing, with no refusal and no message. The walk concluded, over four independent
     routes: *"I never built the Greek salad. I do not think anyone could."* It would have been the
     headline of the report to the owner.

  Both were caught by reading the walk's notes against the app's own wiring, by hand — nothing
  automatic caught either, and the walk could not have caught them by construction, because a walk
  cannot tell a silent app from a silent stand-in. **That is the single most important thing this
  exercise has established about itself.** A finding of the form "nothing happens at all" is far more
  likely to be the harness than the app, and must be reproduced against the real wiring before it is
  believed.

- **Storage is kinder than Room.** No constraints, no foreign keys, no uniqueness beyond what the
  fakes implement by hand. A walk that never hits a refusal has not shown that the database would not
  give one.
