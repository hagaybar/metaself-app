package com.metaself.app.sim

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metaself.app.data.ai.FakeFoodReviewer
import com.metaself.app.data.backup.BackupOutcome
import com.metaself.app.data.backup.DailyBackup
import com.metaself.app.data.day.InMemoryMealRepository
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.CountingClock
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.LoggedFoods
import com.metaself.app.data.movement.StepAccess
import com.metaself.app.data.movement.StepSource
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.time.CurrentHour
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.weight.InMemoryWeightRepository
import com.metaself.app.domain.movement.DayMovement
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.ui.food.ReviewActions
import com.metaself.app.ui.nav.FoodPageExit
import com.metaself.app.ui.nav.TakeFromFoodPage
import com.metaself.app.ui.screen.foods.FoodsViewModel
import com.metaself.app.ui.screen.food.FoodPageScreen
import com.metaself.app.ui.screen.food.FoodPageViewModel
import com.metaself.app.ui.screen.manager.ManagerScreen
import com.metaself.app.ui.screen.manager.ManagerViewModel
import com.metaself.app.ui.screen.manager.MealsViewModel
import com.metaself.app.ui.screen.day.DayPager
import com.metaself.app.ui.screen.entry.EntryEditorScreen
import com.metaself.app.ui.screen.entry.EntryFormState
import com.metaself.app.ui.screen.day.DayUiState
import com.metaself.app.ui.screen.day.DayViewModel
import com.metaself.app.ui.screen.mealbuilder.MealBuilderScreen
import com.metaself.app.ui.screen.mealbuilder.MealBuilderViewModel
import com.metaself.app.ui.screen.record.RecordScreen
import com.metaself.app.ui.screen.record.RecordUiState
import com.metaself.app.ui.screen.repeat.RepeatScreen
import com.metaself.app.ui.screen.repeat.RepeatViewModel
import java.time.LocalDate

/**
 * Everything the walked app stores, in memory, for one walk.
 *
 * Held outside the composition so that what the walk created survives being looked at, and so the
 * review can read the finished state without going through a screen.
 */
class World(
    val foods: FakeFoodRepository = FakeFoodRepository(),
    /**
     * Resolves foods out of [foods] rather than a map somebody had to prime — see
     * [SimSavedMealRepository], which exists because the shared fake's silent drop of an unknown
     * food made the walk report that a meal can never have anything put into it.
     */
    val savedMeals: SimSavedMealRepository = SimSavedMealRepository(foods),
    /**
     * Given both lists, because a day's rows are LABELLED from them: what each food is called now,
     * and what the meal a logging came from is called now. See [InMemoryMealRepository] — omitting
     * them made the walk report that a logged meal leaves no trace of itself on the day.
     */
    val dayMeals: InMemoryMealRepository = InMemoryMealRepository(
        foods = foods,
        mealTitles = savedMeals.observeAllNames(),
    ),
    val weights: InMemoryWeightRepository = InMemoryWeightRepository(),
    val profiles: FakeProfileRepository = FakeProfileRepository(aProfile()),
    /**
     * The one clock every stand-in and view model is stamped from, so "most recent first" compares
     * like with like, as on a phone.
     *
     * It starts at a fixed moment and counts one millisecond per reading, so nothing in a transcript
     * changes between runs for want of a clock and no two edits tie by accident. A fixed clock made
     * every edit tie, and the lists then fell back to their id order.
     *
     * **One thing it cannot reach.** The day stamps a logging with `System.currentTimeMillis()`
     * directly (`DayViewModel.writeMeal`), not with this clock, and Robolectric does not intercept
     * that call. So in a walk every logging carries the real time of the run, which is later than
     * every edit stamped here: a food or a meal logged in a walk stays above one edited after it,
     * where on a phone the edit would bring the other to the top.
     */
    val now: Now = CountingClock(FIXED_MOMENT),
    val today: Today = Today { FIXED_DAY },
    /**
     * Where the walk begins.
     *
     * The day is where a person actually opens this app, and the front door to the two lists is an
     * item in its menu — so starting there walks the way in as well as what it leads to. Starting at
     * the manager is the shorter walk, for exercising the lists themselves without the journey.
     */
    val start: Where = Where.Today,
) {
    /**
     * The phone's back gesture, wired up by [SimulatedApp] when it draws.
     *
     * Separate from the back arrow in the title bar ON PURPOSE. Driving back by pressing a node
     * labelled "Back" tested the arrow rather than the gesture, and recorded a screen without a
     * visible arrow as somewhere with no way out of it.
     */
    var goBack: () -> Unit = {}
        internal set

    init {
        // One clock and one database: each store reads what the real queries join against.
        foods.onClock(now).linkedTo(rows = dayMeals, meals = savedMeals)
        savedMeals.onClock(now).linkedTo(dayMeals.observeLatestLoggingOfSavedMeals())
    }

    val loggedFoods = LoggedFoods(foods)

    /** Nothing is backed up during a walk, and nothing counts steps. */
    val backups = object : DailyBackup {
        override suspend fun runIfDue(today: LocalDate, nowMillis: Long): BackupOutcome? = null
    }
    val steps = object : StepSource {
        override suspend fun access() = StepAccess.UNAVAILABLE
        override suspend fun history(from: LocalDate, to: LocalDate) = emptyList<DayMovement>()
    }

    private companion object {
        /** 2026-09-17T09:00:00Z. Any fixed instant would do; this one is simply fixed. */
        const val FIXED_MOMENT = 1_789_722_000_000L
        val FIXED_DAY: LocalDate = LocalDate.of(2026, 9, 17)
    }
}

/** Where the walk currently is. A stack, because Back is a real way out and often the only one. */
sealed interface Where {
    /** The day: where the app opens, and where a person starts. */
    data object Today : Where

    /** "Add something" — the logging screen, which holds both lists as tabs of its own. */
    data object AddSomething : Where

    /** The manual form: a name and the numbers, typed in, logged straight onto the day. */
    data object TypingTheNumbers : Where

    /**
     * The foods-and-meals manager. [joinFrom] is the food a page's *Join with a duplicate* opened it
     * picking a duplicate for, when there was no list beneath the page (D55 §5) — the route's
     * `foods?joinFrom=`.
     */
    data class Manager(val joinFrom: Long? = null) : Where

    /** One food's own page (D55), as a row of the list and "Give this a portion" open it. */
    data class FoodPage(val foodId: Long) : Where

    /** [mealId] zero means a meal that does not exist yet, exactly as the real route means it. */
    data class BuildingMeal(val mealId: Long, val foodIds: List<Long> = emptyList()) : Where

    /**
     * The day's record (D50), reached from a part of the clock on the day.
     *
     * [openAtMealId] is the logging that part begins with, exactly as the real route carries it.
     */
    data class Record(val epochDay: Long, val openAtMealId: Long?) : Where
}

/**
 * The app, as far as a walk can tell, with fakes underneath.
 *
 * **This is a second copy of what `MetaSelfNavHost` does, and that is a real cost.** It can drift
 * from the app and then prove something about a route the app does not have. Three things hold it
 * honest: the callbacks below are copied verbatim from the nav host, the screens and view models are
 * the app's own rather than stand-ins, and the fidelity of this file is an explicit target of the
 * adversarial review rather than an afterthought.
 *
 * The nav host itself cannot be driven: ten of its twelve `hiltViewModel()` calls sit inside
 * `composable { }` blocks with nothing to override, and adding `hilt-android-testing` to reach them
 * would be a larger change to the build than the thing being tested.
 */
@Composable
fun SimulatedApp(world: World) {
    val stack = remember { mutableStateListOf(world.start) }
    // What the nav host keeps for each entry on its back stack and this shell must keep too, by the
    // entry's place in the stack: the list's view models and the saved state a page's result is left
    // in, and every `rememberSaveable` — the list's scroll among them — so Back finds the list where
    // it was (D55 §1). Dropped as the entry leaves the stack, as the nav host drops them.
    val lists = remember { mutableMapOf<Int, ManagerEntry>() }
    val saveable = rememberSaveableStateHolder()
    val leave: () -> Unit = {
        val top = stack.lastIndex
        lists.remove(top)
        saveable.removeState(saveKey(top, stack[top]))
        stack.removeAt(top)
    }
    val goBack: () -> Unit = { if (stack.size > 1) leave() }
    world.goBack = goBack

    // One day view model for the whole walk, exactly as the app keeps one for the whole session:
    // the logging screen logs THROUGH it, so a second one would log onto a day nobody is looking at.
    val dayViewModel = remember {
        DayViewModel(
            profiles = world.profiles,
            meals = world.dayMeals,
            weights = world.weights,
            savedMeals = world.savedMeals,
            foods = world.foods,
            today = world.today,
            now = world.now,
            currentYear = CurrentYear { world.today().year },
            automaticBackup = world.backups,
            steps = world.steps,
            currentHour = CurrentHour { NINE_IN_THE_MORNING },
            loggedFoods = world.loggedFoods,
            problems = ProblemLog.NONE,
        )
    }

    val at = stack.lastIndex
    saveable.SaveableStateProvider(saveKey(at, stack[at])) {
        when (val here = stack.last()) {
            is Where.Today -> TodayHere(dayViewModel, stack)
            is Where.AddSomething -> AddSomethingHere(world, dayViewModel, stack, goBack)
            is Where.TypingTheNumbers -> TypingTheNumbersHere(dayViewModel, goBack)
            is Where.Manager ->
                ManagerHere(lists.getOrPut(at) { ManagerEntry(world, here.joinFrom) }, stack, goBack)
            is Where.FoodPage -> FoodPageHere(
                world = world,
                here = here,
                // The entry beneath, as the nav host reads `previousBackStackEntry`.
                listBelow = lists[at - 1]?.takeIf { stack.getOrNull(at - 1) is Where.Manager },
                goBack = goBack,
                replaceWith = { where ->
                    leave()
                    stack.add(where)
                },
            )
            is Where.BuildingMeal -> BuildingMealHere(world, here, goBack)
            is Where.Record -> RecordHere(dayViewModel, here, goBack)
        }
    }
}

/**
 * What one entry's saved state is kept under: its place in the stack AND what it is, so a page
 * replaced by the list in the same place (D55 §5) starts from nothing rather than from the page's.
 */
private fun saveKey(at: Int, where: Where): String = "$at $where"

/**
 * One manager on the stack: its three view models, held for as long as it is on the stack as the nav
 * host's back stack entry holds them, and [results] — the entry's own saved state, where a food's page
 * leaves what it has to tell the list (`NavBackStackEntry.savedStateHandle`).
 *
 * [joinFrom] reaches the list's view model the way the route's argument does, in its own saved state.
 */
private class ManagerEntry(world: World, joinFrom: Long?) {
    val manager = ManagerViewModel()
    val foods = FoodsViewModel(
        world.foods,
        ProblemLog.NONE,
        SavedStateHandle(joinFrom?.let { mapOf(FoodsViewModel.JOIN_FROM to it.toString()) } ?: emptyMap()),
    )
    val meals = MealsViewModel(world.savedMeals, ProblemLog.NONE)
    val results = SavedStateHandle()
}

/**
 * The manual entry form, which is what "Type the numbers" opens.
 *
 * Its form state lives in a `remember` here rather than in a view model, exactly as it does in
 * `MetaSelfNavHost` — this is one of only two places in the app where a screen keeps its own.
 */
@Composable
private fun TypingTheNumbersHere(dayViewModel: DayViewModel, goBack: () -> Unit) {
    var form by remember { mutableStateOf(EntryFormState()) }
    var showErrors by remember { mutableStateOf(false) }

    EntryEditorScreen(
        state = form,
        showErrors = showErrors,
        isEdit = false,
        onChange = { form = it },
        onSave = {
            val item = form.toItem()
            if (item == null) {
                showErrors = true
            } else {
                dayViewModel.log(item)
                goBack()
            }
        },
        onCancel = goBack,
    )
}

/** The day. Its top-right menu is the front door to the two lists. */
@Composable
private fun TodayHere(dayViewModel: DayViewModel, stack: MutableList<Where>) {
    DayPager(
        viewModel = dayViewModel,
        onOpenProfile = {},
        // Every part of the clock is a door to the record (D50, D51), opening at the logging that
        // part begins with. Copied from the nav host, as every callback in this file is.
        onOpenRecord = { epochDay, mealId -> stack.add(Where.Record(epochDay, mealId)) },
        // Three different buttons, three different screens. Wiring "Type the numbers" to the same
        // place as the repeat list was a defect of this shell, and the walk duly reported the app for
        // it — the exact failure mode the spec warns a second copy of the wiring can produce.
        onAdd = { stack.add(Where.TypingTheNumbers) },
        onDescribe = {},
        onRepeat = { stack.add(Where.AddSomething) },
        onScan = {},
        onOpenWeight = {},
        onOpenSettings = {},
        onOpenManager = { stack.add(Where.Manager()) },
    )
}

/**
 * The day's record: every logging in full, with the choice and the actions over it (D50).
 *
 * The same one day view model the day itself reads, as the app does — the record is a second view of
 * the day the pager has selected, never a second owner of which day that is.
 *
 * **Correcting a row is a dead end here**, as it is everywhere else in this shell: the entry editor
 * needs a route this harness does not carry. A walk that presses Edit and reports that nothing
 * happens is reporting a limit of the harness, not a defect in the app.
 */
@Composable
private fun RecordHere(dayViewModel: DayViewModel, here: Where.Record, goBack: () -> Unit) {
    val state by dayViewModel.state.collectAsStateWithLifecycle()
    val canUndo by dayViewModel.canUndo.collectAsStateWithLifecycle()
    val ready = state as? DayUiState.Ready ?: return
    if (ready.epochDay != here.epochDay) return

    RecordScreen(
        state = RecordUiState.of(ready, dayViewModel.calendarToday.value),
        canUndo = canUndo,
        openAtMealId = here.openAtMealId,
        onToggleMeal = dayViewModel::toggleMeal,
        onEditItem = {},
        onDeleteItem = dayViewModel::deleteItem,
        onUndoDelete = dayViewModel::undoDelete,
        onSetEatenAt = dayViewModel::setEatenAt,
        onBeginChoosing = dayViewModel::beginChoosing,
        onToggleChosen = dayViewModel::toggleChosen,
        onChooseMeal = dayViewModel::chooseMeal,
        onChooseAll = dayViewModel::chooseAll,
        onClearChoosing = dayViewModel::clearChoosing,
        onDeleteChosen = dayViewModel::deleteChosen,
        onMakeMealFromChosen = dayViewModel::makeMealFromChosen,
        onDismissRefusal = dayViewModel::dismissRefusal,
        onBack = goBack,
    )
}

/**
 * "Add something": the logging screen, with the food list and the meal list as its two tabs.
 *
 * Describing a meal in words, scanning a barcode and editing an entry are all dead ends here — they
 * need a language model, a camera and a screen this shell does not carry. A walk that reaches for one
 * gets nothing, and the transcript shows nothing happening, which is a limit of the harness and NOT a
 * defect in the app. The adversarial pass is told to treat it that way.
 */
@Composable
private fun AddSomethingHere(
    world: World,
    dayViewModel: DayViewModel,
    stack: MutableList<Where>,
    goBack: () -> Unit,
) {
    val repeatViewModel = remember { RepeatViewModel(world.foods, world.savedMeals) }
    val state by repeatViewModel.state.collectAsStateWithLifecycle()

    RepeatScreen(
        state = state,
        onDescribe = {},
        onManageFoods = { stack.add(Where.Manager()) },
        onGivePortion = { foodId -> stack.add(Where.FoodPage(foodId)) },
        onBuildMeal = { stack.add(Where.BuildingMeal(mealId = 0)) },
        onEditMeal = { mealId -> stack.add(Where.BuildingMeal(mealId = mealId)) },
        onPickFood = repeatViewModel::beginChoosing,
        onCountAs = repeatViewModel::countAs,
        onSetAmount = repeatViewModel::setAmount,
        onCancelChoosing = repeatViewModel::cancelChoosing,
        onLogChosen = {
            repeatViewModel.chosen()?.let { dayViewModel.log(it) }
            goBack()
        },
        onShowTab = repeatViewModel::showTab,
        onSearch = repeatViewModel::search,
        onBeginAdjusting = repeatViewModel::beginAdjusting,
        onSetComponentAmount = repeatViewModel::setComponentAmount,
        onStepComponent = repeatViewModel::stepComponent,
        onRemoveComponent = repeatViewModel::removeComponent,
        onCancelAdjusting = repeatViewModel::cancelAdjusting,
        onLogAdjusted = {
            repeatViewModel.adjusted()?.let(dayViewModel::logSavedMeal)
            goBack()
        },
        onBeginAddingToMeal = repeatViewModel::beginAddingToMeal,
        onSearchToAdd = repeatViewModel::searchToAdd,
        onStopAddingToMeal = repeatViewModel::stopAddingToMeal,
        onPickToAdd = repeatViewModel::pickToAdd,
        onCountAddedAs = repeatViewModel::countAddedAs,
        onSetAddedAmount = repeatViewModel::setAddedAmount,
        onDropPicked = repeatViewModel::dropPicked,
        onPutItIn = repeatViewModel::putItIn,
        onBack = goBack,
    )
}

@Composable
private fun ManagerHere(
    entry: ManagerEntry,
    stack: MutableList<Where>,
    goBack: () -> Unit,
) {
    val managerViewModel = entry.manager
    val foodsViewModel = entry.foods
    val mealsViewModel = entry.meals

    val tab by managerViewModel.tab.collectAsStateWithLifecycle()
    val foodsState by foodsViewModel.state.collectAsStateWithLifecycle()
    val mealsState by mealsViewModel.state.collectAsStateWithLifecycle()

    // Verbatim from MetaSelfNavHost: what a food's page left for the list, carried across once.
    TakeFromFoodPage(
        results = entry.results,
        onJoinFrom = foodsViewModel::beginJoiningFrom,
        onHidden = foodsViewModel::sayHidden,
    )

    ManagerScreen(
        tab = tab,
        onShowTab = managerViewModel::showTab,
        foods = foodsState,
        onSearch = foodsViewModel::search,
        onShowOnlyPortions = foodsViewModel::showOnlyPortions,
        onShowHidden = foodsViewModel::showHidden,
        onOpen = { foodId ->
            foodsViewModel.openingAFood()
            stack.add(Where.FoodPage(foodId))
        },
        onMergeInto = foodsViewModel::mergeInto,
        onConfirmMerging = foodsViewModel::confirmJoining,
        onCancelMerging = foodsViewModel::cancelMerging,
        onDismissRefusal = foodsViewModel::dismissRefusal,
        onShowAgain = foodsViewModel::showAgain,
        onDismissHidden = foodsViewModel::dismissHidden,
        onBeginChoosing = foodsViewModel::beginChoosing,
        onToggleChosen = foodsViewModel::toggleChosen,
        onClearChoosing = foodsViewModel::clearChoosing,
        // Verbatim from MetaSelfNavHost: the builder opens on a new meal carrying the foods he
        // chose, and the choice is spent — a list still ticked behind the builder would offer to
        // make the same meal a second time.
        onMakeMeal = {
            stack.add(Where.BuildingMeal(mealId = 0, foodIds = foodsState.chosen.toList()))
            foodsViewModel.clearChoosing()
        },
        onJoinChosen = foodsViewModel::joinChosen,
        meals = mealsState,
        onBuildMeal = { stack.add(Where.BuildingMeal(mealId = 0)) },
        onEditMeal = { mealId -> stack.add(Where.BuildingMeal(mealId = mealId)) },
        onDismissMealsFailure = mealsViewModel::dismissFailure,
        onBack = goBack,
    )
}

/**
 * One food's own page (D55). As the nav host does, it is left once it says it is closing, where
 * [FoodPageExit] says — back, leaving the list beneath what it has to be told, or replaced by the
 * list picking a duplicate — and told so.
 */
@Composable
private fun FoodPageHere(
    world: World,
    here: Where.FoodPage,
    listBelow: ManagerEntry?,
    goBack: () -> Unit,
    replaceWith: (Where) -> Unit,
) {
    val pageViewModel = remember(here) {
        FoodPageViewModel(
            foods = world.foods,
            now = world.now,
            problems = ProblemLog.NONE,
            reviewer = FakeFoodReviewer(),
            savedState = SavedStateHandle(mapOf(FoodPageViewModel.FOOD_ID to here.foodId)),
        )
    }
    val page by pageViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(page.closing) {
        val closing = page.closing ?: return@LaunchedEffect
        when (val exit = FoodPageExit.of(closing, listBelow = listBelow != null)) {
            is FoodPageExit.Back -> {
                listBelow?.let { list -> exit.result?.leaveIn(list.results) }
                goBack()
            }
            is FoodPageExit.ToList -> replaceWith(Where.Manager(joinFrom = exit.joinFrom))
        }
        pageViewModel.closed()
    }

    FoodPageScreen(
        state = page,
        onSetForm = pageViewModel::setForm,
        onSave = pageViewModel::save,
        onHide = pageViewModel::hide,
        onUnhide = pageViewModel::unhide,
        onDelete = pageViewModel::askToDelete,
        onConfirmDeleting = pageViewModel::confirmDeleting,
        onCancelDeleting = pageViewModel::cancelDeleting,
        onBeginJoining = pageViewModel::beginJoining,
        onDismissRefusal = pageViewModel::dismissRefusal,
        review = ReviewActions(
            onReview = pageViewModel::review,
            onApply = pageViewModel::applyReview,
            onUndo = pageViewModel::undoReview,
            onDismiss = pageViewModel::dismissReview,
        ),
        onBack = goBack,
    )
}

@Composable
private fun BuildingMealHere(world: World, here: Where.BuildingMeal, goBack: () -> Unit) {
    val builderViewModel = remember(here) {
        MealBuilderViewModel(
            meals = world.savedMeals,
            foods = world.foods,
            now = world.now,
            problems = ProblemLog.NONE,
            reviewer = FakeFoodReviewer(),
            savedState = savedStateFor(here),
        )
    }
    val state by builderViewModel.state.collectAsStateWithLifecycle()

    MealBuilderScreen(
        state = state,
        onSetName = builderViewModel::setName,
        onName = builderViewModel::name,
        onRename = builderViewModel::rename,
        onSearch = builderViewModel::search,
        onBeginAdding = builderViewModel::beginAdding,
        onCountAs = builderViewModel::countAs,
        onSetAmount = builderViewModel::setAmount,
        onConfirmAdding = builderViewModel::confirmAdding,
        onCancelAdding = builderViewModel::cancelAdding,
        onSetPendingAmount = builderViewModel::setPendingAmount,
        onCountPendingAs = builderViewModel::countPendingAs,
        onConfirmPending = builderViewModel::confirmPending,
        onDropPending = builderViewModel::dropPending,
        onRemove = builderViewModel::remove,
        onMove = builderViewModel::move,
        onChangePart = builderViewModel::beginChanging,
        onChangeFood = builderViewModel::beginChangingFood,
        onBeginCreatingFood = builderViewModel::beginCreatingFood,
        onSetNewFood = builderViewModel::setNewFoodForm,
        onCreateFood = builderViewModel::createFood,
        onCancelCreatingFood = builderViewModel::cancelCreatingFood,
        newFoodReview = ReviewActions(
            onReview = builderViewModel::reviewNewFood,
            onApply = builderViewModel::applyNewFoodReview,
            onUndo = builderViewModel::undoNewFoodReview,
            onDismiss = builderViewModel::dismissNewFoodReview,
        ),
        // Gone back from once the meal is gone, as the nav host does.
        onDelete = { builderViewModel.delete(onDeleted = goBack) },
        onDismissRefusal = builderViewModel::dismissRefusal,
        onBack = goBack,
    )
}

/**
 * The route's arguments, as the real route carries them.
 *
 * Strings, and `mealId` always present, because that is what `MealBuilderViewModel` reads out of the
 * back stack entry. Getting this wrong would not fail loudly — it would quietly open a new meal
 * every time and the walk would never be able to edit one.
 */
private const val NINE_IN_THE_MORNING = 9

private fun savedStateFor(here: Where.BuildingMeal) = SavedStateHandle(
    buildMap<String, Any?> {
        put("mealId", here.mealId.toString())
        if (here.foodIds.isNotEmpty()) put("foods", here.foodIds.joinToString(","))
    },
)

/**
 * What a walk left behind, read straight out of storage rather than off a screen.
 *
 * Deliberately not through the UI: the second thing the review attacks is whether what was stored is
 * what the walk believed it stored, and a screen is exactly the wrong witness for that.
 */
fun World.storedState(): String = buildString {
    appendLine("## Foods")
    appendLine()
    if (foods.current.isEmpty()) {
        appendLine("(none)")
    } else {
        foods.current.forEach { appendLine("- $it") }
    }
    appendLine()
    appendLine("## Saved meals")
    appendLine()
    if (savedMeals.current.isEmpty()) {
        appendLine("(none)")
    } else {
        savedMeals.current.forEach { appendLine("- $it") }
    }
}
