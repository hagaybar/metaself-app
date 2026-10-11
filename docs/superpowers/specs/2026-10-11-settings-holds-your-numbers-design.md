# Settings holds Your numbers, and says what changed — D133, D134

> Written 2026-10-11 and approved the same day, from the mockup "Settings, tidied". Amends D79 (Settings'
> pages) and Today's menu (D113, D130). **Every figure in an example is invented.**

## Why

Today's menu carries a page, Your numbers, that is set once and rarely revisited — the daily target and
how it was worked out. It belongs with the other things that are set once. And the version number sits
at the foot of that page, where nobody looks, with nothing saying what any version did.

## D133 — Your numbers is Settings' first row

- **Settings' index gains a first row, "Your numbers"**, with the status line "1,650 kcal a day · lose
  0.5 kg a week" (the target, and the goal as the page's goal line words it: "hold your weight" / "gain
  0.25 kg a week"). Tapping it opens Your numbers as it is today — the page's content, its toggle and
  "Change these" are unchanged; its back arrow returns to Settings.
- **Today's menu loses "Your numbers"**: Weight, Trainer, Foods & meals, Settings.
- The Weight page's "Change your goal" still opens the editor directly. First run is unchanged.
- **The version line leaves Your numbers** (D134 carries it).

## D134 — About this app: the version and what changed

- **Settings' index gains a last row, "About this app"**, with the status line "Version 0.81.0 · what
  changed".
- **Its page** shows "MetaSelf 0.81.0" in the display face, under it "Build 137 · installed version",
  then **WHAT CHANGED**: one entry per version, newest first, each a heading "0.81.0 · 11 Oct" and one
  plain sentence saying what the version did for the owner, in the words its release was announced in.
- **The log is part of the app** — a list in the code, back to 0.67.0. **Every version bump adds its
  entry**; a test fails when the app's version has no entry at the top of the log, so a release cannot
  ship without one.
- An entry says what changed on screen or in behaviour; never a decision number, an issue number or a
  code name.

## Not built here

Longer spans of training data (its own design, next); the weekly letter's rework.

## Tests

- D133: Settings' first row and its status line for lose / hold / gain; it opens Your numbers and back
  returns to Settings; Today's menu has no Your numbers; the version line is gone from Your numbers.
- D134: the last row's status line; the page's version, build and entries newest first; the log's first
  entry is the app's versionName (fails on a bump without an entry); entries are in strictly descending
  version order with no duplicates.
