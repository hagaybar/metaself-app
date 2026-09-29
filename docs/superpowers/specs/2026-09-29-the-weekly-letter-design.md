# The weekly letter — D99 to D104

> Written 2026-09-29 and approved the same day. Once a week, on Sunday evening, the trainer writes the
> owner a short letter about the week that is ending — food, weight and movement together, set against the
> weeks before it — and the phone says it has arrived. It is the trainer's first use that runs without a
> tap, and the first that sends anything about what was eaten; the owner agreed to both, in the limited
> forms below. **Every figure in an example is invented.**

## What was chosen, and against what

- **Sunday evening**, not Monday morning: a moment to look back before the new week. The letter covers
  Monday to Sunday of the week that is ending.
- **A letter and a box of figures**, not a letter alone (harder to scan) or a dashboard with a few lines
  (less of a letter).
- **Written automatically, with a notification** — not a notice asking the owner to open the app first.
- **Band data copied first, in the background**, with Health Connect's background-read permission; not
  the record as last copied, which could miss the weekend's sessions.
- **Android's background-job library (WorkManager)**, not the alarm the meal reminder uses: an alarm
  gives the app seconds, and a letter needs a copy and an AI answer that can take a minute.
- **The phone counts; the model writes.** Every figure in the letter and the box is computed on the
  phone. A stronger model is not needed.

## D99 — When, and whether, a letter is written (amends D84)

- **Sunday at 20:00 by default.** Settings → the AI page gains **Weekly letter**: a switch (on by
  default) and the time, 18:00 to 23:00 in whole hours. The day is always Sunday.
- **Written without a tap.** D84 says a trainer request is sent only when the owner taps; the weekly
  letter is the one exception, once a week, while the switch is on.
- **A quiet week gets no letter.** When the week holds no meal, no weigh-in and no counted session,
  nothing is sent and nothing is shown (D14: the past never nags).
- **One letter per week.** A week that already has one is never written again, whatever runs twice.
- **Before writing**, the job copies band data from Health Connect as the app does in the foreground,
  when the background-read permission is held. **The permission is asked once**, from the Weekly letter
  setting, with the reason: "so Sunday's letter includes sessions from today". Declined, or not offered
  by the phone, the letter is written from the record as last copied and its figures note under the box
  says "Band data up to <day and time of the last copy>".
- **Retries.** No network, a provider error or an unreadable answer: the job is retried by WorkManager,
  with growing gaps, until Monday 12:00. After that the notification says "Your weekly letter couldn't be
  written — tap to try again"; the tap opens Weekly letters, where **Write it now** asks once (a tap). A
  missing key, a refusal or the day's ceiling reached (D57) goes straight to that notification.
- It counts as one request against the day's ceiling, as every AI request does.

## D100 — What the phone counts

For the week (Monday–Sunday) and for **each of the four weeks before it**, separately:

- **Food**, over the days that hold a meal: calories, protein, carbs and fat a day (the day totals, averaged),
  the daily calorie target that applied, and **days logged** out of 7.
- **Weight**: the change of the smoothed trend across the week (never a single weigh-in), and the goal's
  weekly rate. A week whose trend does not rest on a weigh-in at most 14 days old has none (as D89).
- **Movement**: counted sessions (visible, counted, a combined session once — D74, D81, D92), their
  minutes, distance, how they felt where reviewed (counts only), active energy a day and steps a day
  (D74's figures).
- **The weekly plan**, when one ran during the week: its planned and ticked sessions that week (D95), and
  whether it ended.

The **4-week average** in the box is the mean of those four weeks, each figure over the weeks that have
it; "—" when none has.

## D101 — What is sent (amends D16 and D84)

One request carries:

- the figures of D100, this week and the four before, each week separately;
- this week's sessions as D84 sends them (kind, minutes, distance, energy, heart rate, zones, steps,
  their sources) — **without the owner's words on them**;
- the owner's note (D90), the goal's direction and weekly rate, age, sex and height;
- the weekly plan's title and this week's planned sessions, when one ran;
- **last week's letter's "for next week" line**, so the letters stay consistent.

**Food leaves the phone only as weekly averages of daily totals and a count of days logged — never a
meal, a food's name, an amount or a time of eating.** Never sent either: a single weigh-in, sleep, raw
readings, names, device or app names.

## D102 — What the letter says, and how

Asked for as JSON with six texts, all required and non-empty:

| Part | Heading shown |
|---|---|
| headline | (the title) |
| effort | WHAT YOU PUT IN |
| progress | WHERE IT'S TAKING YOU |
| look_at | ONE THING TO LOOK AT |
| next_week | FOR NEXT WEEK |
| close | (one line, set apart) |

**The tone is written into the instructions, not left to chance.** The owner asked for recognition of
effort, and for encouragement without blame when a week goes badly — a trainer, not a new friend:

- Name the **effort** behind a result, not only the result ("you made room for it on the days it wasn't
  convenient"), in the second person, plainly.
- Compare with his own earlier weeks, never with other people or ideals.
- **One** thing to look at, framed as information or a next step, never as a failure; a gap in logging is
  missing information, not a fault. Say when a figure rests on few days and cannot say much.
- Never shame, never exaggerate, no exclamation marks, no emoji. Every figure mentioned must be one given.
- The close is one warm sentence about the week or the person, not a slogan.
- D87's safety rule stands: nothing medical is diagnosed.

## D103 — Where it is seen

- **The notification** (a channel of its own, "Weekly letter", so it can be silenced without silencing
  the meal reminder — amends D15): "Your week is in" and the headline. It opens the letter.
- **The day screen** shows a card "YOUR WEEK IS IN · <dates>", the headline and **Read it**, above
  today's meals, until the letter has been opened.
- **Weekly letters**, from the Trainer screen: every letter, newest first, by week and headline.
- **The letter page**: the dates, the headline, "From the AI trainer · advice, not a measurement. The
  figures were counted on your phone." (D4), the four parts under their headings, the close, then the box
  — rows Calories a day, Target, Protein a day, Days logged, Weight trend, Sessions, Distance, Steps a day,
  and Weekly plan when one ran — with **This week** and **4-week avg** columns, and the note under it
  (which days the food figures cover; the band-data line of D99 when it applies).

## D104 — What is stored

Room version 11 adds `weekly_letters`: id, weekMonday (unique), createdAtMillis, the figures (JSON: this
week and the four before, as counted), the letter (JSON, D102's shape), the model's name, bandDataUntil
(nullable), and readAtMillis (nullable). It goes into the backup, whose format becomes **9**; formats 1–8
still read (no letters), and a restore replaces. The setting (switch, hour) is stored with the AI
settings and restored as they are.

## Not built here

- A letter on another day, or monthly letters.
- Replying to the letter; a chat.
- Charts in the letter.
- Anything using sleep.
