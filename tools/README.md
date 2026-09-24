# tools

Scripts carried in version control so they are not only on one machine.

- `scripts/gradlew-safe` — **the only sanctioned way to run Gradle here.** It holds a lock so at most
  one build runs at a time; concurrent Gradle invocations once exhausted the development box and
  locked its owner out of SSH. A global Claude Code hook blocks bare `./gradlew`.
- `scripts/ms-release` — the release APK build. R8 needs 4g and the box default is 2048m, so
  `assembleRelease` is not used directly.
- `scripts/claude-gradle-guard.sh` — the hook that enforces the above.
- `check-migration-4-5.py` — runs a migration's SQL against Python's `sqlite3` from the previous
  version's exported schema. Room's own validation still only happens in CI, so this narrows the gap
  rather than closing it. Copy the pattern for the next migration.
- `check-impossible-figures.py` — runs the on-open repair of issue #7 (clearing a food's number
  group that holds a figure the food form would refuse) against `sqlite3` from the latest exported
  schema, and reads the result back the way the app reads a food. CI's `RoomFoodRepositoryTest` is
  still the real check.
- `check-correct-keeps.py` — runs the food form's Save (D54: a group whose figures did not change
  gets no statement) against `sqlite3` from the latest exported schema. It plans each group from the
  food as the app READS it, not from its columns, and asserts what the app reads afterwards. CI's
  `RoomFoodRepositoryTest` is still the real check.
- `check-food-use.py` — runs a food page's *Where it's used* reads (D55 §3: how many logged rows
  point at a food, and which saved meals hold it) against `sqlite3` from the latest exported schema,
  through rows logged, deleted and moved by a join, and models whether the app reads the food at
  all. It resolves a statement held in a `const val`, as `check-correct-keeps.py` now does. That
  each answer is emitted again when the tables move is only checked by CI's `RoomFoodRepositoryTest`.

The copies that actually run live in `~/bin`. These are the source of truth for what they should
contain.
