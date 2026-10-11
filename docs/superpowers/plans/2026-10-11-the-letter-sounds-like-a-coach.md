# Plan — the letter sounds like a coach (D140–D143)

Spec: `docs/superpowers/specs/2026-10-11-the-letter-sounds-like-a-coach-design.md`. Branch
`the-letter-sounds-like-a-coach`, stacked on `a-file-waits-for-its-session`. Release 0.84.0 (versionCode 141).

## Task 1 — the day rows and the patterns (D141, D142), pure

- `domain/letter/LetterDays.kt`: `LetterDay` (date, planned and own minutes, the sessions' kinds, minutes
  and felt, steps, the night's sleep, calories and protein or null) and `LetterDays.of(sunday, …)` over the
  35 days to the letter's Sunday. Sessions by `WeekSessions` (D131); planned = the session ticked a planned
  one in its week, as `WeekLedgers.practiceOf` counts it (D110); felt by `SessionReviews` (D85).
- `domain/letter/LetterPatterns.kt`: the five patterns, each null unless every side rests on at least three.
- Tests (JUnit 5): planned/own split, unlogged food null, sleep per night; each pattern's counts, the median
  split, the at-least-three rule, logged days only for food.

## Task 2 — the letter's new shape (D140), parsing and storage

- `LetterTexts` becomes sealed: `Coach` (headline, recognition, paying_off, look_after, rests_on,
  next_week, close) and `SixParts` (D102's, for letters already stored). A reply must be `Coach`; a stored
  letter reads as either and is encoded back in its own shape.
- Tests: the new shape read strictly; an old stored letter still reads; both round-trip.

## Task 3 — what is sent and how it is asked (D141, D143)

- `LetterRequest` gains the day rows, the patterns, this week's "How did it go?" words, the plan-request
  words of the last five weeks, the plan's goal and the trainer's view of it, and the last three letters
  (look_after or look_at, next_week, their weeks); `lastNextWeek` goes, folded into the last letters.
- `LetterPrompt`: D143's instructions replace D102's; the schema asks for the seven texts and week_note.
- `WriteWeeklyLetter` assembles it; `LetterWording.privacyLetter` says the new list.
- Tests: the request's new parts; the "nothing else is sent" test to exactly the new list; the
  instructions' rules and banned phrasings.

## Task 4 — the letter page (D140)

- A `Coach` letter: headline, "From your trainer · counted on your phone", the tinted WHAT YOU DID THIS WEEK
  card (paragraphs), paying_off with its first sentence bold, ONE THING I WANT TO LOOK AFTER with "Rests on …"
  under it, the outlined FOR NEXT WEEK, the close, then "The week's figures ›" opening the box and its notes.
- A `SixParts` letter is drawn exactly as before.
- Render tests (JUnit 4, Robolectric): order and headings; rests_on under look_after; the figures closed
  and opening; an old letter as before.

## Task 5 — 0.84.0

versionName 0.84.0, versionCode 141, change-log entry at the top. `:app:testDebugUnitTest` and
`:app:lintDebug`; only the ten Room classes skip.

## Not built here

As the spec: no conversation, no other day than Sunday, no day rows on other trainer paths. No new
"write it now" for a week that already has a letter.
