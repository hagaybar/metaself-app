# The Drive copy says what it sent, and three small fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development or
> superpowers:executing-plans. Test first for every behaviour change.

**Goal:** four fixes found in the Settings screen, as 0.57.1.

1. **The month archive never fails silently.** `HealthArchive.writeOutOfDate` returns 0 without a word
   when no month is out of date or Drive gives no token, so the owner could not tell why no month file
   appeared. It returns an outcome instead — `ArchiveWrite` with: months written; months failed;
   `NothingDue`; `NoDrive` (no token); `ListingFailed` — and every non-written outcome that is not
   "nothing due" is logged as kind `"drive"`. After **"Copy to Drive now"** (`SettingsViewModel.driveNow`)
   succeeds, the Drive line gains one sentence from `HealthRecordWording` (tested):
   - written N → "Also sent N month(s) of detailed readings."
   - nothing due → "The detailed readings in Drive were already up to date."
   - no Drive / listing failed / some months failed → "The detailed readings could not be sent this
     time; Recent problems says why."
   The archive is no longer run in a fire-and-forget `quietly` after the line is set: the line is set
   once, after both (busy while it runs if the page has a busy state for Drive; otherwise leave as is).
2. **The band-calories line could count more days with calories than days seen.** `SettingsViewModel.refreshSteps`
   counts `daysWithEnergy` over the whole history (today included) but `daysSoFar` over the days before
   today. Count both over the same days (before today). Test with a fake StepSource (invented days): every day before today and today
   itself with calories → the count equals the days seen.
3. **The "20/4" ratio chip wraps into a vertical column** in Settings → When you eat → Set a ratio.
   Five chips do not fit one row on a phone. Let them wrap onto a second line (FlowRow, or two rows),
   each label on one line (`maxLines = 1, softWrap = false`). Render test at `@Config(qualifiers = "+w320dp")`
   or narrower asserting relative geometry (a chip's height does not exceed a single chip's height by a
   factor that would mean vertical text; or all five labels present with one line each) — CLAUDE.md
   bounds what Robolectric can assert; say what the test can and cannot prove.
4. **Crash: "UNIQUE constraint failed: food_names.nameKey, food_names.brandKey"**, found in the
   problem log (the version that wrote it is unknown).
   Use superpowers:systematic-debugging: find every insert into `food_names` (FoodDao, RoomFoodRepository,
   merges, renames, aliases, saved meals, restore) and whether any path can still insert a
   (nameKey, brandKey) that exists. If one can, write the failing test (Robolectric store tests are
   CI-only — prefer a pure test of the decision if the logic allows), fix it, and say which path. If
   none can on current main, say so with the evidence (which commit closed it) and change nothing.
5. `.gitignore`: add the root `/build/` directory the new AGP creates.

**Red lines:** no schema change; D8 (nothing throws upwards); anonymisation (no food eaten, no figure
of the owner's); shared box (free -m before every Gradle command; gradlew-safe only; one build at a
time); never `git add -A`. Version 0.57.1 (112) in the last commit.
