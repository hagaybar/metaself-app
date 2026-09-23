package com.metaself.app.ui.screen.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.day.DayPartWording
import com.metaself.app.ui.day.DayTotalsWording
import com.metaself.app.ui.day.DayWording
import com.metaself.app.ui.screen.day.Chosen
import com.metaself.app.ui.screen.day.DayMeals
import com.metaself.app.ui.screen.day.EatenAtDialog
import com.metaself.app.ui.screen.day.MealNamingSheet
import com.metaself.app.ui.screen.day.UndoRow
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing
import java.time.LocalDate

/**
 * The day's record: every logging in full, and everything that can be done to one (D50).
 *
 * The reasoning for this screen existing: after logging, the log itself is rarely of interest, and
 * when it is looked at that is usually because something in it needs fixing — which deserves the
 * whole screen rather than a corner of another one. So recording and reviewing are two screens —
 * the day answers *how am I doing*, in one number, and this answers *what exactly did I write down,
 * and is it right*.
 *
 * It is reached from a part of the clock on the day (D51), one tap, and it opens at the logging that
 * part begins with.
 *
 * **The list is not re-implemented here.** It is the day's own [DayMeals], which draws each logging
 * with its time, a meal he built as one row that opens to its parts, and Edit and Delete on every
 * row. That list was written for the day and moved here whole rather than copied, because a second
 * version of it would be a second thing to keep in step with every rule about how a row reads.
 *
 * **The tappable time lives here now.** A part of the clock covers several loggings with several
 * times, so the day's row cannot be the control that sets an unknown one; here each logging is a row
 * again, so it can be (D33, D51). The colophon on the day says *"Open the day's record to set it"*,
 * and this screen is what makes that sentence true.
 *
 * **There is no date picker and no way to travel to another day, and that is a correctness
 * requirement rather than a simplification.** `EditEntry` resolves the row to correct by searching
 * the day view model's *currently selected* day, and pops straight back out when it finds nothing.
 * That works only because the pager's day and the view model's day are always the same — and a
 * picker on this screen is exactly what would break it: every "Edit" tap on a day the pager was not
 * on would silently bounce. The alternative, letting this screen drive the selected day, collides
 * with the invariant `DayPager` states in its own KDoc — the pager is the one owner of which day is
 * shown, and two owners is how a pager ends up drawing one date under another's heading. So a day is
 * chosen where days have always been chosen, and this screen shows the one it was reached from.
 *
 * **The actions are pinned to the bottom edge** (D50, #41): a bar that scrolls away on the screen
 * whose whole job is the list is a bar that is not there when it is wanted. It draws over the list
 * rather than beside it, and the list is given exactly its height back at the end so the last row is
 * never underneath it — measured from the bar as drawn, not from a number written down here.
 *
 * **[MetaSelfScreen] has no bottom-bar slot and this does not add one.** A parameter on the shared
 * scaffold would touch every screen in the app for the sake of one, and the room it reserves would
 * have to agree with the room already reserved for a floating button; this screen overlays its own
 * instead. The cost is that the bar sits inside the scaffold's screen padding rather than truly edge
 * to edge, which is the trade taken deliberately.
 */
@Composable
fun RecordScreen(
    state: RecordUiState,
    /** Whether anything deleted here is still waiting to be put back. The view model counts. */
    canUndo: Boolean,
    onBack: () -> Unit,
    onToggleMeal: (Long) -> Unit,
    onEditItem: (FoodItem) -> Unit,
    onDeleteItem: (FoodItem) -> Unit,
    onUndoDelete: () -> Unit,
    onSetEatenAt: (Meal, Int, Int) -> Unit,
    onBeginChoosing: (Long) -> Unit,
    onToggleChosen: (Long) -> Unit,
    onChooseMeal: (Meal) -> Unit,
    onChooseAll: () -> Unit,
    onClearChoosing: () -> Unit,
    onDeleteChosen: () -> Unit,
    onMakeMealFromChosen: (String) -> Unit,
    onDismissRefusal: () -> Unit,
    /** The logging the record was opened at, if it was opened at one. See [Loggings]. */
    openAtMealId: Long? = null,
) {
    // Which logging's time is being set (D33). Here rather than in the view model for the reason the
    // meal name below is: it exists only while the picker is open, and nothing else reads it.
    var timing by remember { mutableStateOf<Meal?>(null) }

    // The name being typed lives here rather than in the view model: it exists only while the sheet
    // is open, nothing else reads it, and it is gone the moment the meal is made or abandoned.
    var naming by remember { mutableStateOf(false) }
    var mealName by remember { mutableStateOf("") }

    // The sheet closes when the meal is MADE, not when the button is pressed. Whether it was made is
    // the view model's answer and it arrives after the tap; closing on the tap threw the typed name
    // away before anything knew whether it had worked, so a refused name — "you already have a meal
    // called that" most of all — had to be typed again from nothing. Making the meal empties the
    // choice, so choosing ending is the signal, and it covers the other way out too: clearing the
    // choice from behind the sheet leaves no rows for a meal, so the sheet has nothing to name. A
    // refusal leaves the choice standing, which is exactly why it leaves the sheet standing.
    LaunchedEffect(state.choosing) {
        if (!state.choosing) {
            naming = false
            mealName = ""
        }
    }

    MetaSelfScreen(
        title = stringResource(R.string.record_title),
        onBack = onBack,
        // The content scrolls inside this screen, because the actions must not scroll with it. A
        // bar inside the shared scaffold's own scroller would slide off the bottom of a long day,
        // which is the whole of #41.
        scrolls = false,
    ) {
        val scroll = rememberScrollState()
        val density = LocalDensity.current
        // How much room the bar takes, read from the bar rather than guessed, so the last row of the
        // list always clears it whatever the reader's font size does to its buttons.
        var barHeightPx by remember { mutableStateOf(0) }
        // Where the top of the scrolling window is on the screen. Read BEFORE the scroll modifier
        // in the chain, so it is the window rather than the content: the content's own top slides
        // away as soon as anything is scrolled, and a distance measured from a moving origin is not
        // a distance. [Loggings] needs it to work out how far down its content a logging sits.
        var viewportTop by remember { mutableStateOf(0f) }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { viewportTop = it.positionInRoot().y }
                    .verticalScroll(scroll)
                    .padding(bottom = with(density) { barHeightPx.toDp() }),
                verticalArrangement = Arrangement.spacedBy(Spacing.Section),
            ) {
                RecordHeader(state = state)

                // Why nothing was made, above the list it is about. Its colour is the error one,
                // which nothing else on this screen uses: a refusal is one of the two things in the
                // app the owner actually has to go and put right (D48). What he chose stays chosen,
                // so it is a next step rather than a dead end.
                state.refusal?.let { why ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = why,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onDismissRefusal) {
                            Text(stringResource(R.string.day_refusal_dismiss))
                        }
                    }
                }

                if (state.nothingLogged) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        text = stringResource(
                            if (state.isToday) {
                                R.string.today_nothing_logged
                            } else {
                                R.string.day_nothing_logged_past
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    // A gesture nothing on screen mentions is a gesture nobody finds. Said once,
                    // quietly, and only while there is nothing to say about a choice already made.
                    // ABOVE the list, where it is read before the rows it is about: after the list
                    // it was below the fold on any day long enough to need it (#12).
                    if (!state.choosing) {
                        Text(
                            text = stringResource(R.string.record_hold_to_choose),
                            style = MaterialTheme.typography.bodySmall,
                            color = MetaSelfInk.two,
                        )
                    }

                    Loggings(
                        state = state,
                        scroll = scroll,
                        viewportTop = viewportTop,
                        openAtMealId = openAtMealId,
                        onTime = { timing = it },
                        onToggleMeal = onToggleMeal,
                        onEditItem = onEditItem,
                        onDeleteItem = onDeleteItem,
                        onBeginChoosing = onBeginChoosing,
                        onToggleChosen = onToggleChosen,
                        onChooseMeal = onChooseMeal,
                    )
                }
            }

            if (canUndo || state.choosing) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { barHeightPx = it.height },
                    // Lifted off the list rather than ruled off it, so the words underneath are
                    // plainly behind the bar rather than part of it.
                    tonalElevation = 3.dp,
                ) {
                    RecordActions(
                        count = state.chosen.size,
                        canUndo = canUndo,
                        onUndoDelete = onUndoDelete,
                        onChooseAll = onChooseAll,
                        onClearChoosing = onClearChoosing,
                        onMakeMeal = {
                            // A refusal from a previous attempt has been read by now, and leaving
                            // it up would put an old sentence under a new name.
                            onDismissRefusal()
                            naming = true
                        },
                        onDeleteChosen = onDeleteChosen,
                    )
                }
            }
        }
    }

    timing?.let { meal ->
        EatenAtDialog(
            meal = meal,
            onSave = { hour, minute ->
                onSetEatenAt(meal, hour, minute)
                timing = null
            },
            onDismiss = { timing = null },
        )
    }

    if (naming) {
        MealNamingSheet(
            items = state.chosenRows,
            isToday = state.isToday,
            name = mealName,
            refusal = state.refusal,
            onNameChange = { mealName = it },
            onConfirm = { onMakeMealFromChosen(mealName) },
            onCancel = {
                naming = false
                mealName = ""
            },
        )
    }
}

/**
 * What the day came to: the total recorded, and how many things.
 *
 * **Not the target and not what is left.** This screen is about the record, not about progress —
 * which is why it is handed [RecordUiState] and not the day's own state, where the target would have
 * been sitting there waiting to be drawn.
 *
 * The date is a kicker over it, in D49's treatment. The day screen does not draw one because its
 * title bar already carries the date; this screen's title bar says what the screen is instead, so
 * without this there would be nothing saying which day is being read.
 *
 * The figure and the words beside it are two pieces of text and never one string: a Hebrew name or
 * word in the same string as a Latin figure gets reordered by the bidirectional algorithm, and a
 * day's list has already shown a calorie count in front of the food it belonged to.
 *
 * A day with nothing on it says so rather than drawing a zero, exactly as the day does. "0 kcal" is
 * a figure, and a figure is an answer; nothing was written down, which is not the same answer.
 */
@Composable
private fun RecordHeader(state: RecordUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(
            text = DayWording.label(
                date = LocalDate.ofEpochDay(state.epochDay),
                today = LocalDate.ofEpochDay(state.todayEpochDay),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!state.nothingLogged) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = DayTotalsWording.itemsTotal(state.items),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    softWrap = false,
                )
                // Counted the way the day's own rows count, through the one rule that already says
                // what a thing is on this list: a meal he built is ONE thing, because it is one row
                // that opens to its parts, and everything else counts the foods it holds. Two rules
                // for the same word would disagree the first time he built a meal.
                val things = DayPartWording.size(state.meals)
                Text(
                    text = pluralStringResource(R.plurals.record_things, things, things),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The loggings themselves, and the one piece of machinery this screen adds to them: opening at the
 * logging it was reached from (D50).
 *
 * Each logging is drawn through [DayMeals] one at a time rather than the whole list in one call, so
 * that the one being opened at has a box of its own to report where it ended up. [DayMeals] lays its
 * meals out in a column with no gap between them, so a column of one-meal lists is the same drawing.
 *
 * **The place to scroll to is measured, not indexed.** This is a plain scrolling column rather than
 * a lazy list — the record of one day is small, and a lazy list would drop rows out of the semantics
 * tree that this app's tests read — so there is no item index to jump to. The offset is the
 * logging's distance from the top of the scrolling content: where it is now, less where the viewport
 * is now, plus how far the viewport has already been scrolled.
 *
 * **It happens once.** After the screen has opened where it was asked to, where it scrolls is his.
 */
@Composable
private fun Loggings(
    state: RecordUiState,
    scroll: ScrollState,
    /** Where the top of the scrolling window is, measured outside the scroll. See its call site. */
    viewportTop: Float,
    openAtMealId: Long?,
    onTime: (Meal) -> Unit,
    onToggleMeal: (Long) -> Unit,
    onEditItem: (FoodItem) -> Unit,
    onDeleteItem: (FoodItem) -> Unit,
    onBeginChoosing: (Long) -> Unit,
    onToggleChosen: (Long) -> Unit,
    onChooseMeal: (Meal) -> Unit,
) {
    var opened by remember(openAtMealId) { mutableStateOf(openAtMealId == null) }
    var openAt by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(openAt) { openAt?.let { scroll.scrollTo(it) } }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        state.meals.forEach { meal ->
            Box(
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    if (!opened && meal.id == openAtMealId) {
                        opened = true
                        openAt = (scroll.value + coordinates.positionInRoot().y - viewportTop)
                            .toInt()
                            .coerceAtLeast(0)
                    }
                },
            ) {
                DayMeals(
                    meals = listOf(meal),
                    onTime = onTime,
                    openMeals = state.openMeals,
                    onToggleMeal = onToggleMeal,
                    onEditItem = onEditItem,
                    onDeleteItem = onDeleteItem,
                    chosen = state.chosen,
                    onBeginChoosing = onBeginChoosing,
                    onToggleChosen = onToggleChosen,
                    onChooseMeal = onChooseMeal,
                )
            }
        }
    }
}

/**
 * The bar over a choice, and the way back from a delete (D50, #41, #37).
 *
 * Both are drawn from one place because both are about rows that have just been acted on, and
 * because two bars rising from the same edge would fight over it.
 *
 * The pieces are the day's own — [UndoRow] and [Chosen] — kept rather than rewritten, so what they
 * say and the coverage they carry travel with them. **Where they sit is this screen's decision and
 * it inverts**: on the day the Undo was drawn ABOVE the list, because a day of several meals put it
 * below the fold and an Undo nobody can see is a delete that cannot be undone. Pinned to the bottom
 * edge it cannot go below the fold at all, which answers the same objection more simply.
 */
@Composable
private fun RecordActions(
    count: Int,
    canUndo: Boolean,
    onUndoDelete: () -> Unit,
    onChooseAll: () -> Unit,
    onClearChoosing: () -> Unit,
    onMakeMeal: () -> Unit,
    onDeleteChosen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Related),
        verticalArrangement = Arrangement.spacedBy(Spacing.Related),
    ) {
        if (canUndo) {
            UndoRow(onUndo = onUndoDelete)
        }
        if (count > 0) {
            Chosen(
                count = count,
                onClear = onClearChoosing,
                onMakeMeal = onMakeMeal,
                // "All" comes back here, where what it ticks is on the screen it is pressed from.
                // In the day's title bar it hung over a screen with no rows to tick.
                onChooseAll = onChooseAll,
                onDelete = onDeleteChosen,
            )
        }
    }
}
