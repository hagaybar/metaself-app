# A refused save says so — plan

> Branch `a-refused-save-says-so`. Extends D8 ("the failure mode of a habit app is the day it refuses
> to work — a crash is the loudest refusal"), which so far covered only the AI estimate, to every
> action on a screen.

## The problem

Every action a screen starts — save, delete, join, log, restore — runs in `viewModelScope.launch`
with no handler. Any exception from storage (a constraint, a full disk, a bug) is uncaught and kills
the process. 73 launches across 9 view models. The only record is one line in Recent problems.

## The rule

1. **Nothing a screen starts may take the app down.** Every action launched from a view model goes
   through one shared guard that catches `Exception` (never `CancellationException`, never `Error`),
   records it in the problem log (`kind = "refused"`, detail = exception class, message and first
   frame — the same shape the crash handler writes), and hands the screen a sentence to show.
2. **The sentence is true.** It says nothing was changed only where the action is all-or-nothing. So
   every action that writes in more than one step is made one transaction first (the food form's
   Save today is three: rename, brand, numbers). Where one cannot be made atomic, the sentence says
   it may have partly happened.
3. **It is shown where he was working**, on the screen's existing refusal surface if it has one
   (Foods, Day, meal builder already show refusals), otherwise a line on that screen, dismissible.
4. **Reads that merely look something up** are guarded the same way; a failed lookup says it could
   not open the thing, not that nothing was saved.
5. **Out of scope:** exceptions thrown while drawing, and inside long-lived observed flows. Those
   still crash and are still recorded by the process-wide handler.
   Two long-lived streams are started from a view model's own launch and so do pass through the
   guard, where a throw ends the stream rather than the app. Each is restarted rather than left
   frozen: the meals list reads again when its "could not be opened" sentence is dismissed, and the
   day's follower of the open eating stretch (the sentence and tally under the ratio) starts again
   the next time the day screen comes to the front.

## Wording (strings.xml)

- `action_refused_nothing_changed`: "That didn't work, and nothing was changed. What went wrong is
  under Settings → Recent problems."
- `action_refused_maybe_partial`: "That didn't finish, and may have only partly happened. What went
  wrong is under Settings → Recent problems."
- `action_refused_could_not_open`: "That couldn't be opened. What went wrong is under Settings →
  Recent problems."

## Order

1. The guard (`ui/Guarded.kt`), the strings, the atomic food-form save
   (`FoodRepository.saveForm`, one `withTransaction` around rename + brand + facts), and
   `FoodsViewModel`, `WeightViewModel`, `MealsViewModel`, `ProposalViewModel`.
2. `DayViewModel`, `MealBuilderViewModel`, `ScanViewModel`.
3. `SettingsViewModel`, `RootViewModel` — wrapping only; backup and restore logic is not changed.

Each view model: a test with a repository fake that throws on the write, asserting the sentence is
shown, the problem is recorded, and the exception does not escape (an escaping exception fails a
`runTest` on the test Main dispatcher, so the test proves the net).
