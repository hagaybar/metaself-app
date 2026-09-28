# Earlier weeks on the Movement screen — Implementation Plan (D83)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** D83. The Movement screen steps back and forward a week at a time with **‹** and **›** beside
the kicker, as far back as the stored record holds days and never past this week. A past week shows
all seven days newest first with no day open, its own headline and its own last-four-weeks line; "Log
a workout" needs an open day there.

**Architecture:**

```
domain/movement/MovementWeek.kt         of(..., monday): any week, not only this one     JUnit 5
ui/movement/MovementWeekWording.kt      kicker(monday, thisMonday): THIS / LAST / WEEK OF JUnit 5
data/health/WorkoutDao.kt               + observeEarliest() — a query, no schema change  CI (Room)
data/health/MovementRecord.kt           + observeEarliestDay(): min of the two tables    JUnit 5 (proxy DAOs)
ui/screen/movement/MovementUiState.kt   + canGoEarlier, canGoLater, logDay
ui/screen/movement/MovementViewModel.kt the shown week; earlierWeek(), laterWeek()        JUnit 5 + coroutines-test
ui/screen/movement/MovementScreen.kt    the arrows, the disabled button and its line      Robolectric (JUnit 4)
ui/nav/MetaSelfNavHost.kt               wires the two arrows
```

The view model holds which week is shown as a Monday, or null for "this week" — so a screen left open
over midnight into a new week still shows the current one. Everything the week reads is widened from
"Monday to today" to "Monday to the week's last day shown": today for this week, Sunday for a past
one. The earliest stored day is read through the same `MovementRecord` port, from
`HealthDayDao.observeEarliest()` (exists) and a new `WorkoutDao.observeEarliest()` — a `SELECT MIN`
query, which changes no table, column or index, so `app/schemas` is untouched and there is no
migration.

**Decision:** the owner's, 2026-09-28 — D83 in `docs/superpowers/specs/2026-09-28-earlier-weeks-design.md`,
amending D73–D75 (`2026-09-27-movement-screen-design.md`) and D76 (`2026-09-27-typing-in-a-workout-design.md`).

**Tech Stack:** Kotlin, Compose (Material 3), Hilt, Room (read only), kotlinx-coroutines-test, JUnit 5 +
Truth, Robolectric (JUnit 4) for render tests. No new dependency.

**Red lines (stop and report if crossed):**

- **No schema change.** `app/schemas` untouched; one new read-only DAO query, no entity, column or migration.
- **No figure is invented (D4).** A past week's headline, rows and four-week line follow the same
  rules as this week's: a missing figure is left out, never zero.
- **Nothing after this week is shown.** **›** is absent on the current week, and the view model refuses
  to step past it whatever calls it.
- **No control that does nothing without saying why:** a disabled "Log a workout" has the line "Open a
  day to log onto it".
- **D8:** the earliest-day read fails like any other read of the record: logged (kind `"movement"`),
  said on the screen, never thrown.
- **No swipe between weeks** (D83, last bullet).
- **Never `git add -A`; never bare `./gradlew`; never pipe a build whose result is reported.** Never
  stage `app/.settings/*` or `tools/__pycache__`. Anonymisation: every figure and date in a test is
  invented and built on `TEST_EPOCH_DAY`.

## Design questions the spec does not answer — settled here

1. **"Sunday first" means newest first.** A past week lists Sun, Sat … Mon — the same direction as the
   current week's today-first list, so the list reads the same way whichever week is shown.
2. **"Leaving the screen and coming back"** is leaving by Back (or the up arrow): the view model is
   scoped to the screen's navigation entry and is discarded, so the next visit starts on this week with
   today open. A trip out to the file picker or to another app and back is **not** leaving: the week
   shown stays, because the point of D83 is to see where a file's session landed (D82). A return on a
   new calendar day still goes to this week with today open, as it already did.
3. **› into the current week opens today**, as arriving on the screen does. **‹ or › onto a past week
   opens nothing** (the spec's "no day is open").
4. **"Log a workout" is disabled only on a past week with no day open.** On the current week with every
   day closed it still logs onto today, as D76 had it; the spec's parenthetical "(a past week with
   nothing opened)" is read as the case the rule is for. The view model enforces the same rule, so a
   press that reaches it anyway does nothing.
5. **"Open a day to log onto it" sits directly above the day rows**, not beside the floating button:
   the floating button draws over the list, and a second line there would cover the foot of the page
   that `MetaSelfScreen` reserves exactly 88 dp for. Above the rows it points at what to tap.
6. **The earliest week is the Monday-based week of the earliest `health_days` row or workout row**,
   hidden workouts and uncounted walks included — the spec says "as far as the stored record holds
   days". Meals do not extend it. With an empty record, **‹** is absent.
7. **‹ and › are drawn one each side of the kicker**, 48 dp icon buttons (Material's `IconButton`
   minimum). An absent arrow leaves a 48 dp gap so the kicker does not jump sideways as weeks change.
   Auto-mirrored arrow icons, as the back arrow is, for right-to-left.
8. **Amended during build: a week or day outside today's year says its year**, in the kicker and a
   day's heading — "WEEK OF MON 16 JUN 2025", "Sun 22 Jun 2025" — judged date by date, so a week
   across New Year gives the year only to last year's days. The plan's first pass, and the spec's own
   examples, had none; the risk that flagged (a week over a year back reading the same as one a year
   later) is what this closes.
9. **The disabled button keeps its place and its words**, greyed, and declares itself disabled to a
   screen reader. Material 3's `ExtendedFloatingActionButton` has no `enabled` parameter in this
   Compose version, so the disabled state is set on its semantics and its click is swallowed.

## The shared box — read before any build

Another project builds on this machine. `~/bin/gradlew-safe` and `~/bin/ms-release` share one lock; a
finished build's daemon lingers up to ten minutes. **Before every Gradle command run `free -m`;** under
about 4000 MB available (6000 MB before `ms-release`), wait and check again. Never `gradlew --stop`.
One build at a time.

```
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "<pattern>" > /tmp/ms-weeks.log 2>&1; echo "exit $?"
```

SQLite classes skip locally (ten: the eight `CLAUDE.md` names plus `HealthRecordDaoTest` and
`HealthRecordStoreTest`); report them as skipped.

---

### Task 1: Any week, as numbers

**Files:**
- Modify: `app/src/main/java/com/metaself/app/domain/movement/MovementWeek.kt`
- Modify: `app/src/test/java/com/metaself/app/domain/movement/MovementWeekTest.kt`
- Modify: `app/src/test/java/com/metaself/app/ui/movement/MovementWeekWordingTest.kt` (constructor gains `thisMonday`)

- [ ] **Step 1: Failing tests** (append to `MovementWeekTest`; 20,689 is Monday 24 August, 20,695 Sunday 30 August):

```kotlin
    /** D83: a past week is all seven days, newest first — Sunday down to Monday. */
    @Test
    fun `a past week is its seven days, Sunday first`() {
        val week = MovementWeek.of(today, emptyList(), emptyList(), emptyMap(), monday = 20_689)

        assertThat(week.monday).isEqualTo(20_689L)
        assertThat(week.thisMonday).isEqualTo(monday)
        assertThat(week.isCurrent).isFalse()
        assertThat(week.days.map { it.epochDay }).containsExactly(
            20_695L, 20_694L, 20_693L, 20_692L, 20_691L, 20_690L, 20_689L,
        ).inOrder()
    }

    @Test
    fun `this week is current, and is the default`() {
        val week = MovementWeek.of(today, emptyList(), emptyList(), emptyMap())

        assertThat(week.isCurrent).isTrue()
        assertThat(week.thisMonday).isEqualTo(monday)
    }

    /** D83: the headline is the week shown — its Sunday counts, the Monday after does not. */
    @Test
    fun `a past week's headline is that week's`() {
        val days = listOf(
            day(20_688, distanceM = 9_000, activeKcal = 900), // Sunday 23 August: the week before
            day(20_689, distanceM = 3_000, activeKcal = 200),
            day(20_695, distanceM = 2_000, activeKcal = 400),
            day(20_696, distanceM = 9_000, activeKcal = 900), // Monday 31 August: this week
        )
        val workouts = listOf(workout(20_695, minutes = 40), workout(20_696, minutes = 60))

        val week = MovementWeek.of(today, days, workouts, emptyMap(), monday = 20_689)

        assertThat(week.distanceM).isEqualTo(5_000)
        assertThat(week.averageActiveKcal).isEqualTo(300)
        assertThat(week.workoutCount).isEqualTo(1)
        assertThat(week.workoutMinutes).isEqualTo(40)
        assertThat(week.days.first().workouts.map { it.durationMinutes }).containsExactly(40)
    }

    /** D83: "the last four weeks" is relative to the week shown. */
    @Test
    fun `a past week's last four weeks are the four before it`() {
        val days = listOf(
            day(20_689, distanceM = 1_000), // the week shown: the headline, not the line
            day(20_682, distanceM = 4_000), // 17 August
            day(20_661, distanceM = 2_000), // 27 July, four back
            day(20_654, distanceM = 99_000), // 20 July, five back: not on the line
        )

        assertThat(MovementWeek.of(today, days, emptyList(), emptyMap(), monday = 20_689).previousWeeksM)
            .containsExactly(4_000, null, null, 2_000).inOrder()
    }

    @Test
    fun `a week after this one, or a day that is not a Monday, is refused`() {
        assertThrows<IllegalArgumentException> { MovementWeek.of(today, emptyList(), emptyList(), emptyMap(), monday = 20_703) }
        assertThrows<IllegalArgumentException> { MovementWeek.of(today, emptyList(), emptyList(), emptyMap(), monday = 20_690) }
    }
```

(import `org.junit.jupiter.api.assertThrows`.)

- [ ] **Step 2: Run, expect compile failure** (`monday`, `thisMonday`, `isCurrent` unknown):
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.movement.MovementWeekTest" > /tmp/ms-weeks.log 2>&1; echo "exit $?"`

- [ ] **Step 3: Implement.** In `MovementWeek`: add `val thisMonday: Long` after `monday`, and
  `val isCurrent: Boolean get() = monday == thisMonday`. `of` gains `monday: Long = mondayOf(today)`:

```kotlin
            val thisMonday = mondayOf(today)
            require(mondayOf(monday) == monday) { "not a Monday: $monday" }
            require(monday <= thisMonday) { "a week after this one: $monday" }
            val last = if (monday == thisMonday) today else monday + 6
```

  and every `monday..today` / `today downTo monday` becomes `monday..last` / `last downTo monday`;
  `val week = weekOf(monday)`. KDoc: days are "newest first: today back to Monday this week, Sunday
  back to Monday a past one"; the `days`/`workouts`/`mealsByDay` params name "the week shown".
  Wording test's constructor call gets `thisMonday = 20_696`.

- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit** `MovementWeek.kt`, `MovementWeekTest.kt`, `MovementWeekWordingTest.kt`:
  `feat: the Movement week can be any past week (D83)`.

### Task 2: The kicker names the week

**Files:** `ui/movement/MovementWeekWording.kt`, `MovementWeekWordingTest.kt`

- [ ] **Step 1: Failing test** (replace the kicker test):

```kotlin
    /** D83: this week, last week, and further back. */
    @Test
    fun `the kicker names the week and its Monday, in capitals`() {
        assertThat(MovementWeekWording.kicker(20_696, thisMonday = 20_696)).isEqualTo("THIS WEEK · FROM MON 31 AUG")
        assertThat(MovementWeekWording.kicker(20_689, thisMonday = 20_696)).isEqualTo("LAST WEEK · FROM MON 24 AUG")
        assertThat(MovementWeekWording.kicker(20_682, thisMonday = 20_696)).isEqualTo("WEEK OF MON 17 AUG")
    }
```

  ("Open a day to log onto it" is a string resource, `movement_open_a_day`, like the screen's other
  fixed words; the render test in Task 5 checks it.)

- [ ] **Step 2: Run, expect compile failure.**
- [ ] **Step 3: Implement:**

```kotlin
    fun kicker(monday: Long, thisMonday: Long): String {
        val date = LocalDate.ofEpochDay(monday).format(SHORT_DATE).uppercase(Locale.US)
        return when (monday) {
            thisMonday -> "THIS WEEK · FROM $date"
            thisMonday - 7 -> "LAST WEEK · FROM $date"
            else -> "WEEK OF $date"
        }
    }
```

  `dayHeading`'s KDoc: "No year: the design writes none (plan design question 8)."
  Screen's call becomes `kicker(week.monday, week.thisMonday)`.

- [ ] **Step 4: PASS.** **Step 5: Commit** `feat: the Movement kicker names this, last or an earlier week (D83)`.

### Task 3: The earliest stored day

**Files:** `data/health/WorkoutDao.kt`, `data/health/MovementRecord.kt`,
`app/src/test/java/com/metaself/app/data/health/MovementRecordTest.kt`,
`MovementViewModelTest.kt` (its fakes implement the new method).

- [ ] **Step 1: Failing test** (in `MovementRecordTest`, over proxy DAOs):

```kotlin
    /** D83: as far back as the record holds days — the earlier of the two tables; none with neither. */
    @Test
    fun `the earliest day is the earlier of the first summary and the first workout`() = runTest {
        val healthEarliest = MutableStateFlow<Long?>(20_689)
        val workoutEarliest = MutableStateFlow<Long?>(20_682)
        val record = RoomMovementRecord(
            only { if (it == "observeEarliest") healthEarliest else null },
            only { if (it == "observeEarliest") workoutEarliest else null },
            FakeWalkChoices(),
        )

        assertThat(record.observeEarliestDay().first()).isEqualTo(20_682L)
        workoutEarliest.value = null
        assertThat(record.observeEarliestDay().first()).isEqualTo(20_689L)
        healthEarliest.value = null
        assertThat(record.observeEarliestDay().first()).isNull()
    }
```

- [ ] **Step 2: Run, expect compile failure.**
- [ ] **Step 3: Implement.** `WorkoutDao`:

```kotlin
    /** The first day holding any workout, hidden ones included; null with none (D83's lower bound). */
    @Query("SELECT MIN(epochDay) FROM workouts")
    fun observeEarliest(): Flow<Long?>
```

  `MovementRecord`:

```kotlin
    /**
     * The first day the record holds anything for — a daily summary or a workout, hidden or not —
     * or null when it holds nothing. How far back the screen can go (D83).
     */
    fun observeEarliestDay(): Flow<Long?>
```

  `RoomMovementRecord`:

```kotlin
    override fun observeEarliestDay(): Flow<Long?> =
        combine(days.observeEarliest(), workouts.observeEarliest()) { a, b -> listOfNotNull(a, b).minOrNull() }
```

  The view-model test's `FakeRecord` gains `val earliest = MutableStateFlow<Long?>(null)` and
  `override fun observeEarliestDay() = earliest`; its two anonymous broken records answer `flowOf(null)`.
  The class KDoc's "no new query (the plan's red line)" becomes "one query of its own, the earliest
  workout (D83); no schema change".

- [ ] **Step 4: PASS.** **Step 5: Commit** `feat: the Movement record says its earliest day (D83)`.

### Task 4: The view model steps between weeks

**Files:** `MovementUiState.kt`, `MovementViewModel.kt`, `MovementViewModelTest.kt`.

- [ ] **Step 1: Failing tests** (append; `record.earliest.value = 20_682` puts the earliest record in the
  week of 17 August, two weeks back):

```kotlin
    /** D83: ‹ is offered only while there is an earlier week holding something; › never on this week. */
    @Test
    fun `this week offers the week before only when the record goes back that far`() = runTest {
        val record = FakeRecord()
        val model = viewModel(record)
        val job = launch { model.state.collect {} }
        advanceUntilIdle()

        assertThat(model.state.value.canGoEarlier).isFalse()
        assertThat(model.state.value.canGoLater).isFalse()

        record.earliest.value = 20_698 // this week: nothing earlier
        advanceUntilIdle()
        assertThat(model.state.value.canGoEarlier).isFalse()

        record.earliest.value = 20_695 // Sunday 30 August: last week
        advanceUntilIdle()
        assertThat(model.state.value.canGoEarlier).isTrue()
        job.cancel()
    }

    @Test
    fun `stepping back shows the week before with no day open, and reads that week`() = runTest {
        val record = FakeRecord().apply { earliest.value = 20_682 }
        val model = viewModel(record)
        val job = launch { model.state.collect {} }
        advanceUntilIdle()

        model.earlierWeek()
        advanceUntilIdle()

        val state = model.state.value
        assertThat(state.week!!.monday).isEqualTo(20_689L)
        assertThat(state.week!!.days.first().epochDay).isEqualTo(20_695L)
        assertThat(state.openDay).isNull()
        assertThat(state.canGoEarlier).isTrue()
        assertThat(state.canGoLater).isTrue()
        assertThat(record.daysAsked.last()).isEqualTo(20_661L to 20_695L)
        assertThat(record.workoutsAsked.last()).isEqualTo(20_689L to 20_695L)
        job.cancel()
    }

    @Test
    fun `at the earliest week there is no stepping further back`() = runTest {
        val record = FakeRecord().apply { earliest.value = 20_682 }
        val model = viewModel(record)
        val job = launch { model.state.collect {} }
        advanceUntilIdle()

        model.earlierWeek(); advanceUntilIdle()
        model.earlierWeek(); advanceUntilIdle()
        assertThat(model.state.value.week!!.monday).isEqualTo(20_682L)
        assertThat(model.state.value.canGoEarlier).isFalse()

        model.earlierWeek(); advanceUntilIdle()
        assertThat(model.state.value.week!!.monday).isEqualTo(20_682L)
        job.cancel()
    }

    @Test
    fun `stepping forward into this week opens today, and there is no week after it`() = runTest {
        val record = FakeRecord().apply { earliest.value = 20_682 }
        val model = viewModel(record)
        val job = launch { model.state.collect {} }
        advanceUntilIdle()

        model.earlierWeek(); advanceUntilIdle()
        model.earlierWeek(); advanceUntilIdle()
        model.laterWeek(); advanceUntilIdle()
        assertThat(model.state.value.week!!.monday).isEqualTo(20_689L)
        assertThat(model.state.value.openDay).isNull()

        model.laterWeek(); advanceUntilIdle()
        assertThat(model.state.value.week!!.isCurrent).isTrue()
        assertThat(model.state.value.openDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(model.state.value.canGoLater).isFalse()

        model.laterWeek(); advanceUntilIdle()
        assertThat(model.state.value.week!!.monday).isEqualTo(20_696L)
        job.cancel()
    }

    /** D83: coming back on a new day is the current week again, with today open. */
    @Test
    fun `back on the screen on a new day, a past week gives way to this week`() = runTest {
        val record = FakeRecord().apply { earliest.value = 20_682 }
        val model = viewModel(record)
        val job = launch { model.state.collect {} }
        advanceUntilIdle()
        model.earlierWeek(); advanceUntilIdle()

        date = LocalDate.ofEpochDay(TEST_EPOCH_DAY + 1)
        model.lookedAt(); advanceUntilIdle()

        assertThat(model.state.value.week!!.isCurrent).isTrue()
        assertThat(model.state.value.openDay).isEqualTo(TEST_EPOCH_DAY + 1)
        job.cancel()
    }

    /** Plan design question 2: back from the file picker on the same day, the week shown stays. */
    @Test
    fun `back on the screen the same day, a past week stays`() = runTest {
        val record = FakeRecord().apply { earliest.value = 20_682 }
        val model = viewModel(record)
        val job = launch { model.state.collect {} }
        advanceUntilIdle()
        model.earlierWeek(); advanceUntilIdle()

        model.lookedAt(); advanceUntilIdle()

        assertThat(model.state.value.week!!.monday).isEqualTo(20_689L)
        job.cancel()
    }

    /** D83: a past week with nothing open has nowhere to log onto; opening a day gives it one. */
    @Test
    fun `on a past week Log a workout needs an open day, and then logs onto it`() = runTest {
        val record = FakeRecord().apply { earliest.value = 20_682 }
        val model = viewModel(record)
        val job = launch { model.state.collect {} }
        advanceUntilIdle()
        model.earlierWeek(); advanceUntilIdle()

        assertThat(model.state.value.logDay).isNull()
        model.logWorkout(); advanceUntilIdle()
        assertThat(model.state.value.sheet).isNull()

        model.toggle(20_692); advanceUntilIdle()
        assertThat(model.state.value.logDay).isEqualTo(20_692L)
        model.logWorkout(); advanceUntilIdle()
        assertThat(model.state.value.sheet!!.epochDay).isEqualTo(20_692L)
        job.cancel()
    }

    /** Plan design question 4: on this week, every day closed still logs onto today (D76). */
    @Test
    fun `on this week with every day closed, the log day is today`() = runTest {
        val model = viewModel()
        val job = launch { model.state.collect {} }
        advanceUntilIdle()

        model.toggle(TEST_EPOCH_DAY); advanceUntilIdle()

        assertThat(model.state.value.openDay).isNull()
        assertThat(model.state.value.logDay).isEqualTo(TEST_EPOCH_DAY)
        job.cancel()
    }

    /** D8: the earliest day is part of the read; its failure is said like any other. */
    @Test
    fun `an earliest day that cannot be read is said and logged`() = runTest {
        val problems = RecordingProblemLog()
        val broken = object : MovementRecord {
            override fun observeDays(from: Long, to: Long): Flow<List<HealthDay>> = flowOf(emptyList())
            override fun observeWorkouts(from: Long, to: Long): Flow<List<Workout>> = flowOf(emptyList())
            override fun observeEarliestDay(): Flow<Long?> = flow { throw IllegalStateException("disk full") }
        }
        val model = viewModel(record = broken, problems = problems)

        assertThat(model.state.first { it.unreadable }.canGoEarlier).isFalse()
        assertThat(problems.recorded.single().kind).isEqualTo("movement")
    }
```

- [ ] **Step 2: Run, expect compile failure.**
- [ ] **Step 3: Implement.**

  `MovementUiState` gains `canGoEarlier: Boolean = false`, `canGoLater: Boolean = false`, and
  `logDay: Long? = null` — "the day Log a workout logs onto: the open day; today on this week when
  none is open; null on a past week with none open, when the button is disabled (D83)".

  `MovementViewModel`:

```kotlin
    /** The Monday of the week shown; null is this week, whichever week that is by now (D83). */
    private val shownMonday = MutableStateFlow<Long?>(null)

    private data class Read(val week: MovementWeek, val earliest: Long?)

    private val read: Flow<Read?> = combine(calendarToday, shownMonday) { day, monday -> day to monday }
        .flatMapLatest { (day, chosen) ->
            val monday = chosen ?: MovementWeek.mondayOf(day)
            val last = if (chosen == null) day else monday + 6
            combine(
                record.observeDays(monday - 7L * MovementWeek.PREVIOUS_WEEKS, last),
                record.observeWorkouts(monday, last),
                mealsOn((monday..last).toList()),
                record.observeEarliestDay(),
            ) { days, workouts, mealsByDay, earliest ->
                Read(MovementWeek.of(day, days, workouts, mealsByDay, monday), earliest)
            }
        }
        .catch { ... as before ... ; emit(null) }
```

  The state combine builds, from a `Read`:
  `canGoEarlier = earliest != null && MovementWeek.mondayOf(earliest) < week.monday`,
  `canGoLater = !week.isCurrent`, `logDay = open ?: week.days.first().epochDay.takeIf { week.isCurrent }`.

```kotlin
    /** ‹ (D83): the week before, with no day open — while the record goes back that far. */
    fun earlierWeek() {
        val current = state.value
        val week = current.week ?: return
        if (!current.canGoEarlier) return
        shownMonday.value = week.monday - 7
        openDay.value = null
    }

    /** › (D83): the week after; into this week opens today. Never past this week. */
    fun laterWeek() {
        val shown = shownMonday.value ?: return
        val next = shown + 7
        if (next >= MovementWeek.mondayOf(calendarToday.value)) {
            shownMonday.value = null
            openDay.value = calendarToday.value
        } else {
            shownMonday.value = next
            openDay.value = null
        }
    }
```

  `lookedAt()` on a new day also sets `shownMonday.value = null`. `logWorkout()` applies the same rule
  from its sources — `openDay.value ?: calendarToday.value.takeIf { shownMonday.value == null } ?: return`
  — not from `state.value`, which may not yet have caught up with the last toggle; `laterWeek()` reads
  `shownMonday` directly for the same reason. (Changed while building: the first version read
  `state.value.logDay` and would have judged a tap on a stale state.)

- [ ] **Step 4: PASS** (whole `MovementViewModelTest`, the old tests included).
- [ ] **Step 5: Commit** `feat: the Movement view model steps between weeks (D83)`.

### Task 5: The screen — arrows, and a button that needs a day

**Files:** `MovementScreen.kt`, `res/values/strings.xml`, `ui/nav/MetaSelfNavHost.kt`,
`MovementScreenRenderTest.kt`.

Strings: `movement_week_earlier` "Previous week", `movement_week_later` "Next week",
`movement_open_a_day` "Open a day to log onto it".

- [ ] **Step 1: Failing render tests.** What they can prove: which arrows are in the tree, their spoken
  names, that each asks for its week, the kicker's words, a past week's rows and their order, that the
  button declares itself disabled and the line is drawn above the rows. What they cannot (CLAUDE.md):
  the arrows' 48 dp, the kicker staying put when an arrow is absent, the greyed look — phone checks.

```kotlin
    private val pastWeek = MovementWeek.of(
        today = TEST_EPOCH_DAY,
        days = listOf(HealthDay(epochDay = 20_695, distanceM = 3_000, activeKcal = 250, activeKcalSource = FigureSource.TOTAL)),
        workouts = emptyList(),
        mealsByDay = emptyMap(),
        monday = 20_689,
    )

    @Test
    fun `this week has Previous week when there is one, and never Next week`() {
        var earlier = 0
        draw(state = MovementUiState(week = week, openDay = TEST_EPOCH_DAY, canGoEarlier = true, logDay = TEST_EPOCH_DAY), onEarlierWeek = { earlier++ })

        assertThat(render.describedCount("Previous week")).isEqualTo(1)
        assertThat(render.describedCount("Next week")).isEqualTo(0)
        render.clickDescribed("Previous week")
        assertThat(earlier).isEqualTo(1)
    }

    @Test
    fun `at the earliest week there is no Previous week`() {
        draw(state = MovementUiState(week = pastWeek, canGoEarlier = false, canGoLater = true))

        assertThat(render.describedCount("Previous week")).isEqualTo(0)
        assertThat(render.describedCount("Next week")).isEqualTo(1)
    }

    @Test
    fun `a past week names itself, lists its seven days Sunday first, and steps forward`() {
        var later = 0
        val texts = draw(state = MovementUiState(week = pastWeek, canGoEarlier = true, canGoLater = true), onLaterWeek = { later++ })

        assertThat(texts).contains("LAST WEEK · FROM MON 24 AUG")
        assertThat(texts).contains("3.0 km")
        assertThat(render.isDrawnBefore("Sun 30 Aug", "Sat 29 Aug")).isTrue()
        assertThat(render.isDrawnBefore("Tue 25 Aug", "Mon 24 Aug")).isTrue()
        assertThat(texts.count { it == "nothing recorded" }).isEqualTo(6)
        render.clickDescribed("Next week")
        assertThat(later).isEqualTo(1)
    }

    @Test
    fun `a past week with no day open disables Log a workout and says why`() {
        var asked = 0
        val texts = draw(state = MovementUiState(week = pastWeek, logDay = null), onLogWorkout = { asked++ })

        assertThat(texts).contains("Open a day to log onto it")
        assertThat(render.isDrawnBefore("Open a day to log onto it", "Sun 30 Aug")).isTrue()
        assertThat(render.isEnabledDescribed("Log a workout")).isFalse()
        render.clickDescribed("Log a workout")
        assertThat(asked).isEqualTo(0)
    }

    @Test
    fun `with a day to log onto the button is enabled and there is no line`() {
        val texts = draw(state = MovementUiState(week = pastWeek, openDay = 20_692, logDay = 20_692))

        assertThat(texts).doesNotContain("Open a day to log onto it")
        assertThat(render.isEnabledDescribed("Log a workout")).isTrue()
    }
```

  The existing test `Log a workout is offered, and asks to log one` draws with the default state;
  `draw`'s default state gains `logDay = openDay ?: TEST_EPOCH_DAY` so it stays a current-week state.
  `draw` gains `onEarlierWeek`/`onLaterWeek` parameters passed through.
  `ComposeRender` gains `isEnabledDescribed(description)`: the floating button's drawn words never
  reach the semantics tree (only its spoken name does), so `isEnabled(prefix)`, which matches drawn
  text, cannot find it. (Found while building.)

- [ ] **Step 2: Run, expect compile failure.**
- [ ] **Step 3: Implement.** `MovementScreen` gains `onEarlierWeek: () -> Unit = {}`,
  `onLaterWeek: () -> Unit = {}`. `Headline(week, canGoEarlier, canGoLater, onEarlier, onLater)`
  puts the kicker in a `Row(verticalAlignment = CenterVertically)`: `WeekArrow` or `Spacer(48.dp)`,
  the kicker with `weight(1f)` and centred text, `WeekArrow` or `Spacer(48.dp)`. `WeekArrow` is an
  `IconButton` holding `Icons.AutoMirrored.Filled.KeyboardArrowLeft`/`Right` with
  `contentDescription` from the string. Above the day rows: `if (state.logDay == null) Text(open_a_day)`.
  `LogWorkoutButton(enabled, onClick)`: when disabled, `containerColor = surfaceVariant`,
  `contentColor = onSurface.copy(alpha = 0.38f)`, `semantics { disabled() }`, `onClick = { if (enabled) onClick() }`.
  Nav host passes `onEarlierWeek = movementViewModel::earlierWeek`, `onLaterWeek = movementViewModel::laterWeek`.
  The screen's KDoc gains a D83 paragraph.

- [ ] **Step 4: PASS** (whole `MovementScreenRenderTest`).
- [ ] **Step 5: Commit** `feat: the Movement screen steps between weeks with two arrows (D83)`.

### Task 6: Version, whole suite, lint, debug build

- [ ] `app/build.gradle.kts`: `versionCode = 119`, `versionName = "0.63.0"`.
- [ ] `free -m`, then `~/bin/gradlew-safe :app:testDebugUnitTest > /tmp/ms-weeks.log 2>&1; echo "exit $?"` — exit 0;
  count tests, failures and skipped from `app/build/test-results/testDebugUnitTest/*.xml`; skipped
  classes are exactly the ten SQLite ones.
- [ ] `free -m`, `~/bin/gradlew-safe :app:lintDebug > /tmp/ms-weeks.log 2>&1; echo "exit $?"` — exit 0.
- [ ] `free -m`, `~/bin/gradlew-safe :app:assembleDebug > /tmp/ms-weeks.log 2>&1; echo "exit $?"` — exit 0.
- [ ] Commit `app/build.gradle.kts`: `0.63.0: earlier weeks on the Movement screen (D83)`. No push, no release.

## Phone checks this plan cannot make

1. ‹ and › are comfortable 48 dp targets and the kicker does not shift sideways when one disappears.
2. The greyed "Log a workout" reads as unavailable, and the line above the rows is noticed.
3. A session filled in from a file on an earlier day is found by stepping back to its week.

## Self-review (2026-09-28)

- Spec coverage: arrows with labels and absence rules (Tasks 4, 5); kicker three forms (Task 2); past
  week seven days newest first, none open, own headline, relative four-week line (Tasks 1, 4); earliest
  bound (Tasks 3, 4); return to current week (Task 4, design question 2); Log a workout disabled with
  line (Tasks 4, 5); no swipe (red lines).
- Names are consistent: `observeEarliestDay`, `canGoEarlier`, `canGoLater`, `logDay`, `earlierWeek`,
  `laterWeek`, `thisMonday`, `isCurrent`.
