# Quick add — D154, D155, D156

Logging something the owner logs again and again takes a search, a pick, a change of unit, an amount and a
press. This makes it two presses, with no new way in: **Add something** opens with up to eight tiles above
the lists, and a tile logs.

Every figure in this document's rules is a chosen starting point, not a measurement.

## D154 — Tiles at the top of Add something

- Up to **8** tiles, on both tabs, above everything else in the list area. Hidden while anything is typed in
  the search, because typing means he is looking for something else. No tiles qualify: no section.
- **No new button on Today.** One way in, then either a tile or the search.
- **Drawn as tiles, two across, unlike the rows below.** A tile logs and a row opens. Public issue #21 removed
  a row that logged because it looked like a row that opens; these must not look alike.
- **What qualifies is worked out from the record on every read.** Nothing is stored, so there is no schema
  change and no second truth to drift, and backup and restore are untouched.
  - The window is the last **30** days of the record, by the day a meal is on.
  - A food counts a logging only when it was **logged on its own**: a row in a meal of one row, not from a
    saved meal. So a part of a built meal never becomes a favourite through that meal.
  - A saved meal counts each logging of it once, whatever its parts. Whether a logging was adjusted is not
    read: that flag is never counted, as its column's comment says.
  - At least **3** loggings in the window.
  - A food also needs **one amount that makes up at least half** of its loggings in the window. A food whose
    amount varies would log a wrong number on one press, so it stays in the search (D155 still helps it).
  - Hidden, deleted or joined-away foods and meals are not offered.
  - A food whose usual amount can no longer be costed in the unit it was logged in, or is past its ceiling
    (D42), gets no tile. A meal none of whose parts can be costed gets no tile.
- **Usage decides which tiles appear; they are shown in order of name.** A tile moves only when the set
  changes, so its place can be learned. When more than 8 qualify, the most logged win, the most recent
  breaking a tie.
- **A tile says what it will write:** the name, the amount, and the calories at the food's or meal's current
  figures.

**The amount a tile logs** is the most common amount in the window, the most recent breaking a tie. Not the
last amount: one larger helping must not change what every later press writes.

**A tile on a food computes its row exactly as Log it does**, through `Logging.log`, so the row carries the
food's source and confidence (D4). **A tile on a meal logs it as defined**, through
`SavedMeals.toLoggableItems`, with `savedMealAdjusted = false`. It is never the last adjusted version.

**One press, one row.** The press closes the screen; a second press on the same screen does nothing, so a
press that seems not to have worked cannot write twice. The line on Today is set before the write starts.

**The day.** A tile logs on the day being looked at, as every other way in here does. When that is not today,
the section's heading names the day.

## D155 — Amounts are remembered in the search

Picking a food opens its amount question filled in: the tile's amount if it has a tile, otherwise the amount
of the last time it was logged on its own, each in the unit it was logged in. Log it is one press away. If the
unit is one the food can no longer be costed in, the question opens empty, as it does for a food with no
history. Only the Add something foods tab does this; the meal builder and adding to a meal for one day do not.

**Amends D37 and D30.** Both said the amount is the one thing the app will not fill in, because a default
saved unchanged cannot afterwards be told from a number he typed. The owner reversed this. What is filled in
is his own earlier entry, visible before the press. A logged amount has never recorded its own source (D53),
so nothing the record claims changes, and the calories keep their source and confidence (D4).

**Amends the note under public issue #21 in one respect only.** A row still never logs. Tiles do, and are not
rows.

## D156 — Undo after a quick add

The line on Today after a tile says what was written — food, amount, calories — with **Undo** beside its
dismissal. Undo removes exactly the rows that press wrote, by their ids, and the meal with its last row. An
Undo pressed before the write has finished waits for it. A write that fails offers no Undo.

It goes away with the line: dismissed, on moving to another day, or replaced by another logging's line.

Only tiles have it. The other ways in ask first, and a tile is the one that writes on a single press.

## Not built

- A home-screen widget.
- Pinning or removing a tile by hand. Usage decides until that proves wrong.
- Choosing tiles by time of day.
- Undo on the other ways of logging.
- Filling amounts in the meal builder.

## What a render test cannot show

Tile wrapping at real width and with real fonts cannot be asserted here (see CLAUDE.md, Testing), so how eight
tiles with long names look is a check on the phone.
