package com.metaself.app.ui.screen.day

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metaself.app.R
import com.metaself.app.domain.day.DayRange
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.day.DayWording
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The day, swipeable, with a date picker behind its heading.
 *
 * `HorizontalPager` rather than a hand-written drag: the day's list scrolls vertically, and a
 * hand-written horizontal gesture loses the diagonal case — the swipe that is mostly sideways and a
 * little bit down. The pager is built for pages that scroll inside themselves.
 *
 * The pager is the source of truth for which day is shown; the view model is told, never asked. Two
 * things owning "which day" is how a pager ends up drawing one date under another's heading.
 */
@Composable
fun DayPager(
    viewModel: DayViewModel,
    onOpenProfile: () -> Unit,
    /**
     * Open the day's record (D50) — for one day, at one logging.
     *
     * The day is passed rather than left to be read from the view model, because the pager is the
     * source of truth for which day is shown and this is a door opened FROM a page. The logging is
     * the one the tapped part of the clock begins with, which is what "it opens at the meal that
     * was tapped" means once a line covers several loggings rather than one (D51).
     */
    onOpenRecord: (Long, Long) -> Unit,
    onAdd: () -> Unit,
    onDescribe: () -> Unit,
    onRepeat: () -> Unit,
    onScan: () -> Unit,
    onOpenWeight: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenManager: () -> Unit,
) {
    // Today's sentence under a ratio changes with the clock alone — "next meal from 12:00" becomes
    // "your 14 hours were up" at 12:00, and "Good morning." goes at noon — so the moment it is said
    // at is read again every time this screen comes to the front (D32). So is the calendar day.
    LifecycleResumeEffect(viewModel) {
        viewModel.lookedAt()
        onPauseOrDispose { }
    }

    // The pages are built around one "today", and a new date rebuilds them around the new one,
    // landing on it. Kept alive overnight, the pager used to open the morning on YESTERDAY under
    // the heading "Today", and filed breakfast there. Whatever page was open before midnight is let
    // go: a new day has begun, and today is where it begins.
    val todayEpochDay by viewModel.calendarToday.collectAsStateWithLifecycle()
    key(todayEpochDay) {
        DayPagerOn(
            todayEpochDay = todayEpochDay,
            viewModel = viewModel,
            onOpenProfile = onOpenProfile,
            onOpenRecord = onOpenRecord,
            onAdd = onAdd,
            onDescribe = onDescribe,
            onRepeat = onRepeat,
            onScan = onScan,
            onOpenWeight = onOpenWeight,
            onOpenSettings = onOpenSettings,
            onOpenManager = onOpenManager,
        )
    }
}

/** The day pager, built around one calendar day as today. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DayPagerOn(
    todayEpochDay: Long,
    viewModel: DayViewModel,
    onOpenProfile: () -> Unit,
    onOpenRecord: (Long, Long) -> Unit,
    onAdd: () -> Unit,
    onDescribe: () -> Unit,
    onRepeat: () -> Unit,
    onScan: () -> Unit,
    onOpenWeight: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenManager: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val range = remember(todayEpochDay) { DayRange(today = todayEpochDay) }
    val pagerState = rememberPagerState(initialPage = range.todayPage) { range.pageCount }
    val scope = rememberCoroutineScope()

    var picking by remember { mutableStateOf(false) }
    var showingMenu by remember { mutableStateOf(false) }

    // The pager settles on a page; the view model is told which day that is.
    LaunchedEffect(pagerState, range) {
        snapshotFlow { pagerState.currentPage }
            .collect { page -> viewModel.showDay(range.dayAt(page)) }
    }

    // The bar is outside the pager, so the date stays put and only the day's content slides. A
    // title bar that slides sideways with the page is the sort of thing that reads as unfinished.
    MetaSelfScreen(
        title = DayWording.label(
            date = LocalDate.ofEpochDay(range.dayAt(pagerState.currentPage)),
            today = LocalDate.ofEpochDay(todayEpochDay),
        ),
        onTitleClick = { picking = true },
        scrolls = false,
        // The three ways off this screen used to be a row of links at the very bottom, where the
        // floating button drew straight over them and hid "Your numbers" entirely. In the bar they
        // cost no height at all. Words rather than icons: the core icon set has nothing that means
        // "weight", and a guessed pictogram is worse than a word.
        actions = {
            // **"All" is no longer here** — the button that ticked every row of the day at once
            // (design §4). It showed whenever rows were being chosen, and rows are not chosen on
            // this screen any more: the day draws parts of the clock (D51) and the record it opens
            // is where the rows, the ticks and the choice all live now (D50). A button in this bar
            // would sit over a day showing nothing it could act on — and nothing on the day can put
            // the screen into choosing in the first place, so it would never appear at all.
            //
            // It goes to the record screen's own action bar, beside the two things that can be done
            // with a choice, where what it selects is on the screen it is pressed from.
            // `DayViewModel.chooseAll` is untouched and still tested; only this way in has moved.
            IconButton(onClick = { showingMenu = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.today_more),
                )
            }
            DropdownMenu(expanded = showingMenu, onDismissRequest = { showingMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.weight_open)) },
                    onClick = {
                        showingMenu = false
                        onOpenWeight()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.today_profile)) },
                    onClick = {
                        showingMenu = false
                        onOpenProfile()
                    },
                )
                // Looking after the food list and the meals is a job of its own, done well after
                // the meal that produced the duplicate — so it belongs here rather than only behind
                // the button whose purpose is the thing done three times a day.
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.manager_open)) },
                    onClick = {
                        showingMenu = false
                        onOpenManager()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_open)) },
                    onClick = {
                        showingMenu = false
                        onOpenSettings()
                    },
                )
            }
        },
        hasFloatingButton = true,
        floatingActionButton = {
            // The main action opens the search over the owner's own foods, and describing is what
            // that search falls through to when none of them is it. See D28 and DayScreenContent.
            ExtendedFloatingActionButton(
                onClick = onRepeat,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.today_add)) },
            )
        },
    ) {
        // Pull down to read the steps again. Everything else on this screen is already live — the
        // meals, the weight, the target all come from flows — but the steps are a cross-process
        // read taken once per app open, so a walk taken WHILE the app is open never showed up
        // until it was closed and reopened.
        val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
        // Counted by the view model rather than flagged here. A boolean set beside each call could
        // not count: two deletes and one undo left it disagreeing with what was actually still
        // recoverable, in one direction or the other depending on the order.
        val pullState = rememberPullToRefreshState()

        if (pullState.isRefreshing) {
            LaunchedEffect(Unit) { viewModel.refresh() }
        }
        // Driven by the view model rather than by a timer, so the indicator disappears when there
        // is an answer rather than after a guess at how long one should take.
        LaunchedEffect(refreshing) {
            if (!refreshing && pullState.isRefreshing) pullState.endRefresh()
        }

        Box(modifier = Modifier.nestedScroll(pullState.nestedScrollConnection)) {
            HorizontalPager(state = pagerState) { page ->
            val day = range.dayAt(page)
            val current = state
            if (current is DayUiState.Ready && current.epochDay == day) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        // The pager scrolls itself, so the shared scaffold's room for the floating
                        // button does not reach in here.
                        .padding(bottom = FLOATING_BUTTON_ROOM),
                ) {
                    // Correcting a row, deleting one, undoing that, opening a meal he built,
                    // ticking rows and setting an unknown time are no longer wired here: they are
                    // about a row of the record, and the record has a screen of its own (D50, D51).
                    // The view model still exposes every one of them, and still has its tests —
                    // this is the one way IN that has gone, and the record screen is the new one.
                    DayScreenContent(
                        state = current,
                        // Every part of the clock is a door (D51), and it opens the record at the
                        // logging that part begins with. A part is never empty — `DayParts` draws
                        // no empty row — so there is always one to open at.
                        onOpenPart = { part -> onOpenRecord(day, part.meals.first().id) },
                        onAdd = onAdd,
                        onDescribe = onDescribe,
                        onScan = onScan,
                        onOpenSettings = onOpenSettings,
                        onDismissTargetChange = viewModel::dismissTargetChange,
                        onDismissFoodRetaught = viewModel::dismissFoodRetaught,
                        onDismissJustLogged = viewModel::dismissJustLogged,
                        onDismissEncouragement = viewModel::dismissEncouragement,
                        onDismissRefusal = viewModel::dismissRefusal,
                    )
                }
            }
            }

            PullToRefreshContainer(
                state = pullState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    if (picking) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(range.dayAt(pagerState.currentPage))
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toEpochDay()
                            picking = false
                            // Moving the pager tells the view model, as above: one owner of
                            // "which day", never two.
                            scope.launch { pagerState.scrollToPage(range.pageOf(picked)) }
                        }
                    },
                ) { Text(stringResource(R.string.day_picker_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) {
                    Text(stringResource(R.string.day_picker_dismiss))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Matches the room the shared scaffold leaves; the pager scrolls itself and must repeat it. */
private val FLOATING_BUTTON_ROOM = 88.dp
