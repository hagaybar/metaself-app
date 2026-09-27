# Settings in six pages — Implementation Plan (D79)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** D79. The Settings screen becomes a list of six rows — Eating, Movement and health, Backups,
AI estimates, Food database, Recent problems — each with one status line, each opening its own page
that holds the controls that were in that part of the old single scroll. Nothing is added or removed;
every action, message, question and refusal behaves as now.

**Architecture:**

```
ui/settings/SettingsIndexWording.kt         pure: the six status lines                  JUnit 5
ui/screen/settings/SettingsPage.kt          the six pages: path + title                 (used by all)
ui/screen/settings/SettingsParts.kt         RefusedHere, HourPicker, ArchiveQuestion     (moved, now internal)
ui/screen/settings/EatingSettingsPage.kt    When you eat + Daily reminder               Robolectric (JUnit 4)
ui/screen/settings/MovementSettingsPage.kt  Movement lines + Connect + pointer          Robolectric
ui/screen/settings/BackupSettingsPage.kt    Keeping a copy / Google Drive / By hand      Robolectric
ui/screen/settings/AiSettingsPage.kt        key, model, ceiling, Test it                 Robolectric
ui/screen/settings/FoodDatabaseSettingsPage.kt  Open Food Facts account                   Robolectric
ui/screen/settings/ProblemsSettingsPage.kt  the problems list                            Robolectric
ui/screen/settings/SettingsScreen.kt        REWRITTEN: the index of six rows             Robolectric
ui/nav/SettingsGraph.kt                     nested graph "settings" + page routes        Robolectric (real NavHost)
ui/nav/MetaSelfNavHost.kt                   the graph wired; one view model per visit    compile + graph test
```

**One `SettingsViewModel`, scoped to the Settings nav graph.** The pages are real destinations inside
a nested navigation graph whose route is `settings` (a nested graph is Navigation's way of grouping
destinations; the graph itself gets a back-stack entry that lives while any of its pages is on the
stack). Every page asks for `hiltViewModel<SettingsViewModel>(graphEntry)`, so all seven destinations
share one instance.

Why this and not the codebase's existing sharing pattern: the codebase has no nav-graph-scoped view
model yet; the one way it shares a view model between destinations is to hoist it to the activity
(`dayViewModel`, `weightViewModel` are `MetaSelfNavHost` parameters). Activity scope would change the
view model's *lifetime*: today it is created on entering Settings and cleared on leaving it, so a
restore question, a Test it result, a Drive message or a pending consent screen do not survive leaving
Settings. At activity scope they would. The graph-scoped owner reproduces today's lifetime exactly,
which is the "behaviour does not change" rule. `hiltViewModel(viewModelStoreOwner)` is
hilt-navigation-compose 1.1.0's documented API; no new dependency.

**Decision:** the owner's, 2026-09-27 — D79 in
`docs/superpowers/specs/2026-09-27-settings-in-six-pages-design.md`.

**Tech Stack:** Kotlin, Compose (Material 3), Navigation Compose 2.7.7, Hilt navigation 1.1.0, JUnit 5 +
Truth, Robolectric (JUnit 4) for render and navigation tests. No new dependency.

**Red lines (stop and report if crossed):**

- **No behaviour change to any action.** `SettingsViewModel`'s logic is not edited; the one code change
  there is a field rename (Task 4) and comments.
- **No schema change.** `app/schemas` untouched.
- **No assertion weakened.** An existing render-test assertion that spanned two sections now on
  different pages is *moved*: the order bound becomes "on this page, after X" plus "not drawn on the
  other page".
- **Never `git add -A`; never bare `./gradlew`; never pipe a build whose result is reported.** Never
  stage `app/.settings/*` or `tools/__pycache__`.
- Anonymisation: every figure in a test is invented and round; no real hours, ratio, reminder time,
  username or model name. Model names in tests stay the invented ones already there.

## Design questions the spec does not answer — settled here

1. **Section headings that repeat their page's title are dropped**: "Movement" under the page
   "Movement and health", and "Recent problems" under the page of the same name (the top bar says it).
   All other headings stay. Backups gains two group headings, "Google Drive" and "By hand"; its first
   group keeps "Keeping a copy". The page titles are the spec's row names.
2. **The Movement pointer** "Detailed readings: see Backups" is shown when either thing that moved
   away would have been drawn on Movement: `driveOn || healthRecord.days > 0`.
3. **The archive question asked from the Drive controls** (formerly asked from Movement) is drawn in
   the Google Drive group; the one offered after a restore stays in By hand, as now. The view-model
   flag `archiveInMovement` is renamed `archiveFromDrive` — a rename only; the three view-model tests
   that name "Movement" are renamed with it.
4. **A refusal (`RefusedHere`) stays exactly where its part put it:** BACKUP's at the end of the
   Backups page (the end of By hand, as today at the end of the backup area), WINDOW and REMINDER on
   Eating, KEY/MODEL/CEILING/TEST on AI estimates, OFF_ACCOUNT on Food database.
5. **"Opened at the key"** (the describe screen's "Add a key in settings") now navigates straight to
   the AI estimates page, where the key is the first section, so the scroll-into-view machinery
   (`BringIntoViewRequester`, `openAtKey`) is removed. Back from there returns to the describe screen
   as today, because the index is never put on the stack.
6. **Status lines** (all pure, in `SettingsIndexWording`; examples here invented):
   - Eating: `"10:00–18:00 · reminder at 21:00"`, `"14/10 · reminder off"`,
     `"No hours set · reminder off"`. Hours use an en dash; a ratio is `WindowWording.ratio`.
   - Movement and health: `UNAVAILABLE` → `"Health Connect not available"`; `NOT_PERMITTED` → `"Off"`;
     `GRANTED` → `"On"`, plus `" · health record N days"` when N > 0 (`"1 day"` singular).
   - Backups: folder and Drive → `"Daily to a folder and to Drive"`; folder only →
     `"Daily to a folder"`; Drive only → `"Daily to Drive"`; neither → `"Not set up"`. With a folder
     and a last copy date, `" · last copy 3 Sep"` (`d MMM`, `Locale.US`). The date is the folder's, so
     it is not shown without a folder.
   - AI estimates: `"Key saved · up to 20 a day"` / `"No key yet"`.
   - Food database: `"Open Food Facts · signed in"` / `"Not signed in"` (signed in = a password is
     saved, the same test the page uses).
   - Recent problems: `"3 recent"` / `"None"`.
7. **Opening a page and pressing back are guarded** the way the food list is: a row opens its page
   only while the index is the resumed entry (`ifResumed`), and a page's back arrow pops only itself
   (`popFrom`), so a quick double tap cannot stack two pages or pop the index with the page.
8. **The three reads Settings does on opening** (`refreshSteps`, `refreshWindow`,
   `refreshHealthRecord`) run when each Settings destination opens, not once per visit — the index's
   status lines need them and a page opened directly (at the key) never passes the index. They are
   reads that record their own failure; nothing is written. **This is the one timing difference.**
9. **Google's consent screen** is observed on every Settings destination, but launched only by the
   one that is RESUMED, so a transition between two pages cannot show it twice.
10. **Activity-result launchers** (Android's "start another screen and get an answer back" hooks —
    like a callback registered before the call) are registered on the page whose control launches
    them: notifications on Eating, Health Connect on Movement, folder/export/restore on Backups.
11. **The day screen's window marks** (the ring and the ratio mark, which open window settings) go
    straight to the Eating page (`Destination.Settings.atWindow`). "When you eat" was the first thing
    on the single Settings page, so the marks landed on it; landing on the index would add a tap.
    `DayPager` gains `onOpenWindowSettings`, which it hands to `DayScreen` as its `onOpenSettings`
    (the marks are the day screen's only use of it); the top-right menu still opens the index.
    Found during Task 6, not in the first draft of this plan.

## The shared box — read before any build

Another project builds on this machine. `~/bin/gradlew-safe` and `~/bin/ms-release` share one lock; a
finished build's daemon lingers up to ten minutes. **Before every Gradle command run `free -m`;** under
about 4000 MB available (6000 MB before `ms-release`), wait and check again. Never `gradlew --stop`.
One build at a time.

```
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "<pattern>" > /tmp/ms-settings.log 2>&1; echo "exit $?"
```

SQLite classes skip locally (ten: the eight `CLAUDE.md` names plus `HealthRecordDaoTest` and
`HealthRecordStoreTest`); report them as skipped.

---

### Task 1: The six status lines

**Files:**
- Create: `app/src/main/java/com/metaself/app/ui/settings/SettingsIndexWording.kt`
- Test: `app/src/test/java/com/metaself/app/ui/settings/SettingsIndexWordingTest.kt`

- [ ] **Step 1: Write the failing test** (JUnit 5, `org.junit.jupiter.api.Test`)

```kotlin
package com.metaself.app.ui.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.movement.StepAccess
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.reminder.Reminder
import com.metaself.app.domain.window.EatingWindow
import com.metaself.app.domain.window.MeasuredWindow
import com.metaself.app.domain.window.WindowRule
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** Every figure here is invented and round. */
class SettingsIndexWordingTest {
    private val today = LocalDate.ofEpochDay(TEST_EPOCH_DAY.toLong())

    @Test fun `eating names the hours and the reminder time`() {
        val rule = WindowRule.Fixed(EatingWindow(10, 18, TEST_EPOCH_DAY.toLong()))
        assertThat(SettingsIndexWording.eating(rule, Reminder(enabled = true, hour = 21, minute = 0)))
            .isEqualTo("10:00–18:00 · reminder at 21:00")
    }
    @Test fun `eating names a ratio by its figure`() {
        val rule = WindowRule.Measured(MeasuredWindow(14), fromEpochDay = TEST_EPOCH_DAY.toLong())
        assertThat(SettingsIndexWording.eating(rule, Reminder(enabled = false)))
            .isEqualTo("14/10 · reminder off")
    }
    @Test fun `eating with nothing set says so`() {
        assertThat(SettingsIndexWording.eating(null, Reminder(enabled = false)))
            .isEqualTo("No hours set · reminder off")
    }
    @Test fun `movement is on, with how far the health record reaches`() {
        assertThat(SettingsIndexWording.movement(StepAccess.GRANTED, healthDays = 30))
            .isEqualTo("On · health record 30 days")
        assertThat(SettingsIndexWording.movement(StepAccess.GRANTED, healthDays = 1))
            .isEqualTo("On · health record 1 day")
        assertThat(SettingsIndexWording.movement(StepAccess.GRANTED, healthDays = 0)).isEqualTo("On")
    }
    @Test fun `movement off, or not available`() {
        assertThat(SettingsIndexWording.movement(StepAccess.NOT_PERMITTED, healthDays = 30)).isEqualTo("Off")
        assertThat(SettingsIndexWording.movement(StepAccess.UNAVAILABLE, healthDays = 0))
            .isEqualTo("Health Connect not available")
    }
    @Test fun `backups say where and when the last copy was`() {
        assertThat(SettingsIndexWording.backups(hasFolder = true, driveOn = true, lastBackup = today))
            .isEqualTo("Daily to a folder and to Drive · last copy 3 Sep")
        assertThat(SettingsIndexWording.backups(hasFolder = true, driveOn = false, lastBackup = null))
            .isEqualTo("Daily to a folder")
    }
    @Test fun `Drive alone has no folder date to show`() {
        assertThat(SettingsIndexWording.backups(hasFolder = false, driveOn = true, lastBackup = today))
            .isEqualTo("Daily to Drive")
    }
    @Test fun `no backup is not set up`() {
        assertThat(SettingsIndexWording.backups(hasFolder = false, driveOn = false, lastBackup = null))
            .isEqualTo("Not set up")
    }
    @Test fun `AI estimates says whether a key is saved, and the ceiling`() {
        assertThat(SettingsIndexWording.ai(hasKey = true, ceiling = 20)).isEqualTo("Key saved · up to 20 a day")
        assertThat(SettingsIndexWording.ai(hasKey = false, ceiling = 20)).isEqualTo("No key yet")
    }
    @Test fun `food database says whether an account is signed in`() {
        assertThat(SettingsIndexWording.foodDatabase(signedIn = true)).isEqualTo("Open Food Facts · signed in")
        assertThat(SettingsIndexWording.foodDatabase(signedIn = false)).isEqualTo("Not signed in")
    }
    @Test fun `problems are counted`() {
        assertThat(SettingsIndexWording.problems(3)).isEqualTo("3 recent")
        assertThat(SettingsIndexWording.problems(0)).isEqualTo("None")
    }
}
```

- [ ] **Step 2: Run it; expect a compile failure** (`SettingsIndexWording` unresolved)

`~/bin/gradlew-safe :app:testDebugUnitTest --tests "*SettingsIndexWordingTest" > /tmp/ms-settings.log 2>&1; echo "exit $?"`

- [ ] **Step 3: Implement**

```kotlin
package com.metaself.app.ui.settings

import com.metaself.app.data.movement.StepAccess
import com.metaself.app.domain.reminder.Reminder
import com.metaself.app.domain.window.WindowRule
import com.metaself.app.ui.day.ReminderWording
import com.metaself.app.ui.window.WindowWording
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The one status line under each row of the Settings index (D79). Counts and states, never
 * reassurance — the rule the backup wording keeps: "Daily to a folder", not "You're protected".
 */
object SettingsIndexWording {

    fun eating(rule: WindowRule?, reminder: Reminder): String {
        val hours = when (rule) {
            null -> "No hours set"
            is WindowRule.Fixed -> String.format(
                Locale.US, "%02d:00–%02d:00", rule.window.startHour, rule.window.endHour,
            )
            is WindowRule.Measured -> WindowWording.ratio(rule.window)
        }
        val reminded = if (reminder.enabled) "reminder at ${ReminderWording.clock(reminder)}" else "reminder off"
        return "$hours · $reminded"
    }

    fun movement(access: StepAccess, healthDays: Int): String = when (access) {
        StepAccess.UNAVAILABLE -> "Health Connect not available"
        StepAccess.NOT_PERMITTED -> "Off"
        StepAccess.GRANTED -> when {
            healthDays <= 0 -> "On"
            healthDays == 1 -> "On · health record 1 day"
            else -> "On · health record $healthDays days"
        }
    }

    /** The date is the folder copy's, so it is said only with a folder. */
    fun backups(hasFolder: Boolean, driveOn: Boolean, lastBackup: LocalDate?): String {
        val where = when {
            hasFolder && driveOn -> "Daily to a folder and to Drive"
            hasFolder -> "Daily to a folder"
            driveOn -> "Daily to Drive"
            else -> return "Not set up"
        }
        val last = lastBackup?.takeIf { hasFolder } ?: return where
        return "$where · last copy ${last.format(DAY)}"
    }

    fun ai(hasKey: Boolean, ceiling: Int): String =
        if (hasKey) "Key saved · up to $ceiling a day" else "No key yet"

    fun foodDatabase(signedIn: Boolean): String =
        if (signedIn) "Open Food Facts · signed in" else "Not signed in"

    fun problems(count: Int): String = if (count == 0) "None" else "$count recent"

    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)
}
```

- [ ] **Step 4: Run; expect PASS (11 tests).**
- [ ] **Step 5: Commit** `git add` the two files; message `settings: the six status lines (D79)`.

---

### Task 2: The pages as routes in a nested graph

**Files:**
- Create: `app/src/main/java/com/metaself/app/ui/screen/settings/SettingsPage.kt`
- Create: `app/src/main/java/com/metaself/app/ui/nav/SettingsGraph.kt`
- Modify: `app/src/main/java/com/metaself/app/ui/nav/MetaSelfNavHost.kt` (`Destination.Settings` only)
- Test: `app/src/test/java/com/metaself/app/ui/nav/SettingsGraphSessionTest.kt` (Robolectric, JUnit 4)
- Test: `app/src/test/java/com/metaself/app/ui/nav/MetaSelfNavHostRenderTest.kt` (JUnit 5)

- [ ] **Step 1: Write the failing tests.**

In `MetaSelfNavHostRenderTest` add:

```kotlin
    @Test
    fun `every Settings page has its own route, under the Settings graph`() {
        val routes = SettingsPage.entries.map(Destination.Settings::page) + Destination.Settings.index
        assertThat(routes).containsNoDuplicates()
        assertThat(routes.all { it.startsWith("settings/") }).isTrue()
    }

```

`SettingsGraphSessionTest` draws a real `NavHost` whose Settings part is built by the production
`settingsGraph` with stand-in page content (the real pages need Hilt), and a plain `ViewModel` probe
fetched from the graph entry exactly as the host fetches `SettingsViewModel`:

```kotlin
@RunWith(RobolectricTestRunner::class)
class SettingsGraphSessionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var nav: NavHostController
    private val probes = mutableListOf<Probe>()
    class Probe : ViewModel()

    @Test fun `the menu's route opens the index`()                     // navigate(Settings.route) → current "settings/index"
    @Test fun `a row opens its page and back returns to the index`()   // for every page
    @Test fun `a page opened directly, back goes straight back to where he was`() // day → page(AI) → back → day, no settings entry left
    @Test fun `the index and a page share one view model`()            // probe identical on index and page
    @Test fun `leaving Settings and coming back is a fresh view model`()
    @Test fun `two quick taps on a row open its page once`()           // frame clock held, as OneTapOnePageSessionTest
}
```

(full code in the test file; the Graph composable: `NavHost(start = "day")` with `composable("day")`
holding "Open settings" and "Open at key" buttons, plus `settingsGraph(nav) { page, here, graph -> … }`
drawing `Text(page?.name ?: "INDEX")`, a button per page calling `nav.openSettingsPage(here, page)`,
and a "Back" button calling `nav.popFrom(here)`; each composition records
`viewModel<Probe>(viewModelStoreOwner = graph)`.)

- [ ] **Step 2: Run; expect compile failure** (`SettingsPage`, `Destination.Settings.page`, `settingsGraph`).

- [ ] **Step 3: Implement.**

`SettingsPage.kt`:

```kotlin
/** The six pages of Settings (D79), in the index's order. [path] is the route's last part. */
enum class SettingsPage(val path: String, @StringRes val title: Int) {
    EATING("eating", R.string.settings_page_eating),
    MOVEMENT("movement", R.string.settings_page_movement),
    BACKUPS("backups", R.string.settings_page_backups),
    AI("ai", R.string.settings_page_ai),
    FOOD_DATABASE("food-database", R.string.settings_page_food),
    PROBLEMS("problems", R.string.settings_problems_title),
}
```

strings.xml, beside the other `settings_` strings: `settings_page_eating` "Eating",
`settings_page_movement` "Movement and health", `settings_page_backups` "Backups",
`settings_page_ai` "AI estimates", `settings_page_food` "Food database".

`Destination.Settings` becomes:

```kotlin
    /** Settings is a nested graph (D79): an index of six rows, and a page for each. */
    data object Settings : Destination("settings") {
        const val index: String = "settings/index"
        fun page(page: SettingsPage): String = "settings/${page.path}"
        /** Scrolled to the key: where the describe screen's "Add a key in settings" goes. */
        val atKey: String = "settings?at=key"   // becomes page(SettingsPage.AI) in Task 6
    }
```

`SettingsGraph.kt`:

```kotlin
/**
 * Settings as a nested graph (D79): the index at [Destination.Settings.index], one destination per
 * [SettingsPage]. [content] is handed the page (null for the index), the entry being drawn, and the
 * graph's own entry — the owner every page takes the one SettingsViewModel from, so it lives exactly
 * as long as a visit to Settings, as it did when Settings was one destination.
 */
fun NavGraphBuilder.settingsGraph(
    nav: NavController,
    content: @Composable (page: SettingsPage?, here: NavBackStackEntry, graph: NavBackStackEntry) -> Unit,
) {
    navigation(startDestination = Destination.Settings.index, route = Destination.Settings.route) {
        composable(Destination.Settings.index) { here -> content(null, here, graphOf(nav, here)) }
        SettingsPage.entries.forEach { page ->
            composable(Destination.Settings.page(page)) { here -> content(page, here, graphOf(nav, here)) }
        }
    }
}

@Composable
private fun graphOf(nav: NavController, here: NavBackStackEntry): NavBackStackEntry =
    remember(here) { nav.getBackStackEntry(Destination.Settings.route) }

/** Opens [page] from the index, only while the index is in front (see [ifResumed]). */
fun NavController.openSettingsPage(here: NavBackStackEntry, page: SettingsPage) {
    here.ifResumed { navigate(Destination.Settings.page(page)) }
}
```

The host keeps registering the old single `settings?at={at}` destination until Task 6 replaces it, so
in this task `atKey` keeps its old value `"settings?at=key"`; Task 6 changes it to `page(SettingsPage.AI)`
together with its test (moved there from this task), so no commit points a caller at an unregistered
route.

- [ ] **Step 4: Run both test classes; expect PASS.**
- [ ] **Step 5: Commit** `nav: Settings pages as routes in a nested graph (D79)`.

---

### Task 3: Shared parts, and the Eating, Movement, Food database and Recent problems pages

**Files:**
- Create: `app/src/main/java/com/metaself/app/ui/screen/settings/SettingsParts.kt`
- Create: `EatingSettingsPage.kt`, `MovementSettingsPage.kt`, `FoodDatabaseSettingsPage.kt`,
  `ProblemsSettingsPage.kt` (all in `ui/screen/settings/`)
- Modify: `SettingsScreen.kt` (helpers moved out)
- Modify tests: `WindowSettingsRenderTest.kt` → draws `EatingSettingsPage`;
  `SettingsHistoryRenderTest.kt` → draws `MovementSettingsPage`
- Test: `app/src/test/java/com/metaself/app/ui/screen/settings/SettingsPagesRenderTest.kt`

- [ ] **Step 1: Move the three helpers.** Cut `RefusedHere`, `HourPicker`, `ArchiveQuestion` and the
  constants `DEFAULT_START`, `DEFAULT_END`, `DEFAULT_FASTING`, `RATIOS` from `SettingsScreen.kt`
  (lines 890–972 at commit `1d03847`) into `SettingsParts.kt`, changing `private` to `internal`,
  bodies and KDoc verbatim.

- [ ] **Step 2: Write the failing tests.** Re-point `WindowSettingsRenderTest.draw` and
  `SettingsHistoryRenderTest.draw` at `EatingSettingsPage(...)` / `MovementSettingsPage(...)` with only
  the callbacks those pages take; every assertion unchanged. New `SettingsPagesRenderTest`:

  - Eating page: in order "When you eat", "Set the hours", "Keep tab on this", "Set a ratio",
    "Save this ratio", "Daily reminder"; with the reminder on, "Send it now, to check it arrives".
    Title bar "Eating".
  - Movement page: the status line (`MovementWording.status` for the state), Connect when not
    permitted; **no** "Bring back detailed readings from Drive"; the pointer
    "Detailed readings: see Backups" when Drive is on, and not when Drive is off and the record is empty.
  - Food database page: "Open Food Facts account", "Save the account"; with a password saved,
    "Forget it". Title bar "Food database".
  - Problems page: with none, "Nothing has gone wrong." and no "Copy them"; with two invented lines,
    both lines, "Copy them", "Clear them". Title bar "Recent problems", drawn once.
  - Refusals: WINDOW and REMINDER sentences drawn on Eating; OFF_ACCOUNT on Food database.

- [ ] **Step 3: Run; expect compile failure** (pages unresolved).

- [ ] **Step 4: Implement the four pages.** Each is `MetaSelfScreen(title = stringResource(page.title),
  onBack = onBack) { … }` whose body is the old section code moved **verbatim**:

  - `EatingSettingsPage(state, onSetWindow, onSetRatio, onClearWindow, onSetReminder, onSendReminderNow,
    onDismissFailure, onBack, modifier)`: the "when to eat" column (old lines 132–266), a
    `HorizontalDivider()`, the reminder column (old lines 580–647).
  - `MovementSettingsPage(state, onConnectSteps, onBack, modifier)`: the steps column (old lines
    270–354) **minus** its title `Text` (settled question 1), **minus** the detailed-backup line, the
    Bring back button, the Movement-drawn archive question and the archive message (they move to
    Backups in Task 4), **plus**, before Connect, the pointer
    `if (state.driveOn || state.healthRecord.days > 0) Text(stringResource(R.string.settings_movement_readings_pointer), bodySmall, onSurfaceVariant)`.
    New string `settings_movement_readings_pointer` = "Detailed readings: see Backups".
  - `FoodDatabaseSettingsPage(state, onSaveOffAccount, onClearOffAccount, onDismissFailure, onBack,
    modifier)`: the OFF column (old lines 358–417) with its `typedOffUser`/`typedOffPassword` state.
  - `ProblemsSettingsPage(state, onCopyProblems, onClearProblems, onBack, modifier)`: the problems
    column (old lines 855–886) minus its title `Text` (settled question 1).

  `SettingsScreen.kt` is not otherwise touched in this task (its sections are removed in Task 6).

- [ ] **Step 5: Run the settings tests; expect PASS.**
- [ ] **Step 6: Commit** `settings: Eating, Movement, Food database and Recent problems pages (D79)`.

---

### Task 4: The Backups page, with Drive's "Bring back detailed readings"

**Files:**
- Create: `ui/screen/settings/BackupSettingsPage.kt`
- Modify: `SettingsUiState.kt`, `SettingsViewModel.kt` (rename `archiveInMovement` → `archiveFromDrive`;
  comments), `SettingsViewModelTest.kt` (the two assertions and three test names)
- Modify: `SettingsRefusalRenderTest.kt` (backup case → Backups page)
- Test: `ui/screen/settings/BackupSettingsRenderTest.kt`

- [ ] **Step 1: Write the failing tests.** `BackupSettingsRenderTest`:
  - Headings in order "Keeping a copy", "Google Drive", "By hand"; "Pick a folder" before
    "Google Drive"; the Drive switch line before "By hand"; "Save to a file" and "Restore from a file"
    after "By hand". Title bar "Backups".
  - Drive on: "Copy to Drive now" and "Bring back detailed readings from Drive" in the Drive group
    (after "Google Drive", before "By hand"). Drive off: neither.
  - A health record with days: the detailed-backup line (`HealthRecordWording.detailedBackup`) in the
    Drive group.
  - `pendingArchive` with `archiveFromDrive = true`: the question and "Bring them back" before
    "By hand"; with `archiveFromDrive = false`: after "Restore from a file". Drawn once either way.
  - `archiveMessage`: drawn in the Drive group with "Got it".
  - Pressing "Bring back detailed readings from Drive" calls `onOfferArchive`.

  `SettingsRefusalRenderTest`'s backup case becomes: on `BackupSettingsPage`, the sentence is drawn
  once, after "Keeping a copy" and after "Restore from a file" (the end of the page, where BACKUP's
  refusal has always been drawn), and **not** drawn on `EatingSettingsPage` (the reminder's page) — the
  moved form of "before the reminder". "All right" still drawn.

  In `SettingsViewModelTest`, the assertions on `archiveInMovement` become `archiveFromDrive`; the
  test names "asking from Movement …" become "asking from the Drive controls …" and "the offer after a
  restore is not put in Movement" becomes "the offer after a restore is not put with the Drive controls".

- [ ] **Step 2: Run; expect compile failure.**

- [ ] **Step 3: Implement.** Rename the state field and the view model's private flow (and its KDoc:
  "asked for from the Drive controls on Backups"); no other view-model line changes.
  `BackupSettingsPage(state, onPickBackupFolder, onForgetBackupFolder, onBackUpNow, onSetDrive,
  onDriveNow, onOfferArchive, onConfirmArchive, onCancelArchive, onDismissArchiveMessage, onExport,
  onRestore, onConfirmRestore, onCancelRestore, onDismissBackupMessage, onDismissFailure, onBack,
  modifier)`, body, three columns separated by `HorizontalDivider()`:
  1. **Keeping a copy** — old lines 423–466 (title, note, folder status, folder buttons), then
     `automaticBackupMessage` (old 497–499) and `KEY_NOT_INCLUDED` (old 501–505).
  2. **Google Drive** — new heading `settings_drive_title` "Google Drive" (titleMedium); the switch row
     (old 468–485), Copy to Drive now (old 487–491), `driveMessage` (old 493–495); then, moved from
     Movement verbatim: the detailed-backup line (old 319–325), the Bring back button (old 327–335),
     the archive question when `archiveFromDrive` (old 336–338), the archive message (old 339–347).
  3. **By hand** — new heading `settings_by_hand_title` "By hand" (titleMedium); old lines 511–576
     verbatim (Save/Restore, restore question, archive after restore when `!archiveFromDrive`, backup
     message, `RefusedHere(BACKUP)`).

- [ ] **Step 4: Run the settings tests; expect PASS.**
- [ ] **Step 5: Commit** `settings: the Backups page; Drive's bring-back moves beside Drive (D79)`.

---

### Task 5: The AI estimates page

**Files:**
- Create: `ui/screen/settings/AiSettingsPage.kt`
- Modify tests: `SettingsModelSessionTest.kt`, `SettingsTestItRenderTest.kt`, `SettingsAtKeySessionTest.kt`,
  `SettingsRefusalRenderTest.kt` (key case)

- [ ] **Step 1: Re-point the tests.** Model session and Test it draw `AiSettingsPage`; every
  assertion kept. Test it's "before Recent problems" becomes: after the result, drawn once, and not
  drawn on `ProblemsSettingsPage`. The key refusal keeps "after the key title, before Model".
  `SettingsAtKeySessionTest`: "opened for the key, the whole key section is on screen" draws the AI
  page (key title and Save the key `assertIsDisplayed`); "opened from the menu …" becomes "opened from
  the menu, it opens at the index, where the key is not" — draws `SettingsScreen` (the index, Task 6)
  and asserts `KEY_TITLE` does not exist. (This one test is written here and goes green in Task 6.)
- [ ] **Step 2: Run; expect compile failure.**
- [ ] **Step 3: Implement** `AiSettingsPage(state, onSaveKey, onClearKey, onSetModel, onSetCeiling,
  onTest, onDismissFailure, onBack, modifier)`: the `typedKey`, model and ceiling state (old lines
  109–121 minus the OFF lines), then old lines 651–851 verbatim **minus** the
  `BringIntoViewRequester`, its modifier, `broughtToKey` and the `LaunchedEffect` (settled question 5).
- [ ] **Step 4: Run** (the index test excluded until Task 6); expect PASS.
- [ ] **Step 5: Commit** `settings: the AI estimates page (D79)`.

---

### Task 6: The index, and the host wired to the graph

**Files:**
- Rewrite: `ui/screen/settings/SettingsScreen.kt`
- Create: `ui/nav/SettingsDestination.kt` (the destination body below, out of the host's long file)
- Modify: `ui/nav/MetaSelfNavHost.kt`, `ui/screen/day/DayPager.kt`, `sim/SimulatedApp.kt` (test)
- Test: `ui/screen/settings/SettingsIndexRenderTest.kt`

- [ ] **Step 1: Write the failing tests.** In `MetaSelfNavHostRenderTest` (moved from Task 2):

```kotlin
    @Test
    fun `opened for the key, Settings is the AI estimates page`() {
        assertThat(Destination.Settings.atKey).isEqualTo(Destination.Settings.page(SettingsPage.AI))
    }
```

`SettingsIndexRenderTest` (ComposeRender):
  - Six titles in order: Eating, Movement and health, Backups, AI estimates, Food database, Recent
    problems; each immediately followed by its status line from `SettingsIndexWording` for an invented
    state (hours 10–18, reminder 21:00, GRANTED with 30 days, folder + Drive with a date, key with
    ceiling 20, signed in, two problems).
  - Pressing each title calls `onOpen` with that page.
  - No control of any page is drawn on the index ("Save the key", "Pick a folder", "Keep tab on this").
- [ ] **Step 2: Run; expect failure** (old `SettingsScreen` signature).
- [ ] **Step 3: Implement.** `SettingsScreen(state, onOpen: (SettingsPage) -> Unit, onBack, modifier)`:
  `MetaSelfScreen(title = settings_title, onBack)` with one row per `SettingsPage.entries`: a
  `Column(Modifier.fillMaxWidth().clickable { onOpen(page) }.padding(vertical = Spacing.Tight))` with
  the title (`titleMedium`) and the status (`bodyMedium`, `MetaSelfInk.two`), a `HorizontalDivider`
  between rows. Status via a private `statusOf(page, state)` calling `SettingsIndexWording`.
  `Destination.Settings.atKey` becomes `page(SettingsPage.AI)`. In `MetaSelfNavHost`, replace the `composable(Destination.Settings.route + "?at={at}")` block with
  `settingsGraph(navController) { page, here, graph -> SettingsDestination(...) }` where the body:
  - `val settingsViewModel: SettingsViewModel = hiltViewModel(graph)`;
  - the refresh `LaunchedEffect(Unit)` as before (settled question 8);
  - the consent launcher, and `LaunchedEffect(consent, resumed)` launching only when
    `here.lifecycle.currentStateAsState().value == RESUMED` (settled question 9);
  - `when (page)` → index (`onOpen = { navController.openSettingsPage(here, it) }`) or the page, each
    with its own launchers moved verbatim from the old block (settled question 10), and
    `onBack = { navController.popFrom(here) }`.
  - Remove `settings_steps_title` from strings.xml if nothing else uses it.
  - The window marks (settled question 11): test first in `MetaSelfNavHostRenderTest` —
    `Destination.Settings.atWindow == page(SettingsPage.EATING)` — then `atWindow`, the `DayPager`
    parameter, the host's `onOpenWindowSettings`, and `SimulatedApp`'s `onOpenWindowSettings = {}`.
  - `DestinationRouteTest`'s "settings can be opened at the key" now expects `"settings/ai"`.
- [ ] **Step 4: Run all settings and nav tests; expect PASS.**
- [ ] **Step 5: Commit** `settings: the index of six rows, wired as a nested graph (D79)`.

---

### Task 7: Suite, lint, assemble

- [ ] Whole suite: `~/bin/gradlew-safe :app:testDebugUnitTest > /tmp/ms-settings.log 2>&1; echo "exit $?"`;
  count tests/failures/skips from `app/build/test-results/testDebugUnitTest/*.xml`; skipped classes must
  be exactly the ten SQLite ones.
- [ ] `~/bin/gradlew-safe :app:lintDebug` exit 0; `~/bin/gradlew-safe :app:assembleDebug` exit 0.
- [ ] No version bump, no push, no release.

## Self-review (2026-09-27)

- Spec rows and page contents: Eating (Task 3), Movement (3), Backups incl. the moved bring-back and
  pointer (4, 3), AI (5), Food database (3), Recent problems (3); index with status lines (1, 6);
  entry points: the menu → index, the describe screen → AI page (2, 6); "messages where their action
  is" (settled 4); status lines are counts and states (1).
- Not in the spec and not built: no new setting, no search, no reordering by use.
- Known difference: the three opening reads run per destination (settled 8); an unsaved typed model
  name is forgotten on leaving the AI page for the index (it was forgotten on leaving Settings before;
  now a page is left more often).
