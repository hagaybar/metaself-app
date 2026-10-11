# Suggested goals, and a goal of more time each week — Implementation Plan (D126, D127)

**Goal:** `docs/superpowers/specs/2026-10-11-suggested-goals-design.md`. Plan ahead gains a third goal, **More time
each week** (minutes a week, 30 to 900, by a Sunday 6 to 30 weeks ahead), and a **Suggest goals** button that asks the
trainer for three goals fitted to the record, shown as cards that fill the form when tapped. Version 0.79.0 (135).

**Branch:** `suggested-goals`, from main. **No Room schema change**: the goal is a key of the plan's JSON (release 2's
choice 1); suggestions are not stored.

**Tech:** JUnit 5 + Truth for everything pure and the view model; JUnit 4 + Robolectric only for the render test.
Fixtures are invented; today is `TEST_EPOCH_DAY` (Thursday 3 September 2026). The time-goal fixture: **150 min a week
by Sunday 13 December 2026** (`TEST_EPOCH_DAY + 101`, the distance fixture's date), 14 weeks to go.

## Choices the spec leaves open — settled here

1. **One type for a dated goal.** `PlanGoal.Dated` (a sealed sub-interface with `byEpochDay`) is implemented by
   `Distance` and the new `WeeklyMinutes(minutes, byEpochDay)`. The plan's length (`PlanAheadAsk.toAsk`) and
   `weeks_to_go` (`TrainerPrompt.sentGoal`) are written once, for `Dated`.
2. **Stored and sent:** `{"kind": "weekly_minutes", "minutes": 150, "by": "2026-12-13"}`, plus `weeks_to_go` when
   sent. A stored goal of an unknown kind stays unreadable, as before; steady and distance goals read as before.
3. **The form:** `GoalChoice.MINUTES`, labelled "More time each week"; `Form.minutes` starts at 150 and moves by 10
   within 30..900 ("10 min less" / "10 min more"); the BY row is the distance's, shared.
4. **The request (D126):** `TrainerQuestion.SuggestGoals(perWeek: Int?, words, last: LastEvaluation?, running:
   RunningGoal?)`. "How past planned weeks went" is sent as the evaluate path's `last_evaluation` (same shape, same
   builder); the running plan as `running_plan: {goal, outline}` or null. The window for a date is sent as
   `earliest_by` and `latest_by` (Sundays 6 and 30 weeks ahead), counted by the phone.
5. **The reply's goal schema** is an `anyOf` of three strict objects — steady, distance, weekly_minutes — so each
   suggestion's goal is exactly the stored shape. ReviewPrompt already uses `anyOf`.
6. **Per-suggestion checks** (`GoalBounds`, domain): title and why non-empty; sessions a week 2..5; a dated goal's
   date moved to its week's Sunday and 6..30 weeks after this one; km more than 0, at most 1000, at most one decimal
   (what the form can show); minutes 30..900 (a multiple of 10 is asked for, not required). Failing ones are dropped;
   none left is unreadable. At most three are kept.
7. **The cards:** title; the goal line (D119's, `GoalWording.goalLine`); "2 sessions a week"; the why; a chosen card
   says "Chosen" (not only a colour). Tapping fills goal, sport, km, minutes, weeks ahead and sessions a week; words
   are untouched. Asking again replaces the cards and clears the choice. Suggest runs while the form stays editable;
   it is not offered while Ask is asking, nor Ask while Suggest is asking.
8. **What it sends is said under the button** (D84's rule that an AI request is described where it is sent):
   `TrainerWording.privacySuggest`.
9. **The workbench (D106)** does not try SUGGEST_GOALS: it is not in `TrainerPath.WORKBENCH`; its `when`s read
   NOT_HERE / null for it, as for FILL_WEEK.

## Tasks

### Task 1 — D127: the goal, its storage, its words, what the trainer is told
- `domain/trainer/Programme.kt`: `PlanGoal.Dated`, `PlanGoal.WeeklyMinutes` (30..900); `toAsk` for `Dated`.
- `data/ai/TrainerPrompt.kt`: `goalJson`, `sentGoal` for `Dated`; GOALS describes `weekly_minutes` and its rule
  (build to the minutes, reach it in the goal's week and hold it; no taper; base and build blocks, the conventions'
  easier weeks); EVALUATE's "weeks" sentence covers a time goal.
- `data/ai/TrainerResponse.kt`: `goal()` reads `weekly_minutes`.
- `ui/trainer/GoalWording.kt`: goal line "150 min a week · by Sun 13 Dec · 14 weeks to go"; MINUTES, ROW_MINUTES,
  LESS, MORE, `minutesLine`.
- Tests: `OutlineTest` (toAsk weeks; bounds), `GoalWordingTest`, `TrainerPromptTest` (sent JSON with weeks_to_go;
  the instructions' rule), `TrainerResponseTest` (stored goal round trip; old goals still read), `TrainerResponseTest`
  outline coverage for a time goal's 15 weeks.
- Commit: `feat(trainer): a goal of more time each week, stored, sent and said (D127)`.

### Task 2 — D127: the form's third choice
- `EvaluatePlanViewModel.Form`: `GoalChoice.MINUTES`, `minutes`, `fewer()`/`more()`; `ask()` builds `WeeklyMinutes`.
- `EvaluatePlanScreen.kt`: the third chip; MINUTES A WEEK row with its buttons; the BY row shared.
- Tests: `EvaluatePlanViewModelTest` (step, bounds, the ask), `EvaluatePlanScreenRenderTest` (rows drawn).
- Commit: `feat(trainer): Plan ahead offers More time each week (D127)`.

### Task 3 — D126: the path, the request, the reply
- `domain/trainer/GoalSuggestion.kt` (new): `GoalSuggestion`, `RunningGoal`, `GoalBounds`.
- `TrainerPath.SUGGEST_GOALS`; `TrainerQuestion.SuggestGoals`; `Trainer.suggestGoals`.
- `TrainerPrompt`: SUGGEST_GOALS instructions (three of differing kind; fitted to both ledgers; within reach under the
  conventions; words weigh more than the record; title ≤ 8 words; 2–5 a week; one sentence of why naming something the
  record shows; no figure the app did not count) + GOALS + CONVENTIONS; the question JSON; `suggestGoalsBody`; schema.
- `TrainerResponse.parseSuggestions(body, model, today)`; `OpenAiTrainer.suggestGoals`; `FakeTrainer`.
- Workbench `when`s; `WorkbenchWording.path`.
- `AskTheTrainer.suggestGoals(perWeek, words)` and `suggestGoalsQuestion`; week_note kept (D116).
- Tests: `TrainerPromptTest`, `TrainerResponseTest`, `AskTheTrainerTest`, `OpenAiTrainerTest` (one round trip).
- Commit: `feat(trainer): ask the trainer to suggest three goals (D126)`.

### Task 4 — D126: the cards on the form
- `EvaluatePlanViewModel`: `suggest()`, `pick(index)`; state `suggesting`, `suggestions`, `chosen`, `suggestFailure`.
- `EvaluatePlanScreen`: the outlined "Suggest goals" button above GOAL ("Suggesting…" while asked), its privacy line,
  its failure, and the cards.
- `TrainerWording.privacySuggest`; `GoalWording` card words.
- Tests: view model (fills goal, figures, sessions a week, leaves words; asking again replaces; failure leaves the form
  usable), render (button, Suggesting…, cards, Chosen, click calls back).
- Commit: `feat(trainer): suggested goals as cards that fill Plan ahead's form (D126)`.

### Task 5 — version
- `app/build.gradle.kts`: 0.79.0, versionCode 135.
- Full `:app:testDebugUnitTest` and `:app:lintDebug`.
- Commit: `0.79.0: suggested goals and a goal of more time each week (D126, D127)`.

## Not built here

As the spec: pace or speed goals, sleep or food goals, the workbench's distance and time goals, storing suggestions.
