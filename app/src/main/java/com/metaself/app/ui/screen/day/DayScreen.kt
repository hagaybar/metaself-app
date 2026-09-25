package com.metaself.app.ui.screen.day

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import java.time.ZoneId
import java.time.Instant
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimeInput
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalHapticFeedback
import com.metaself.app.ui.theme.Feel
import com.metaself.app.ui.theme.LocalMoves
import com.metaself.app.ui.theme.Motion
import com.metaself.app.ui.theme.givesUnderPress
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.DayPart
import com.metaself.app.domain.day.DayParts
import com.metaself.app.domain.day.DayProgress
import com.metaself.app.domain.day.PartOfTheClock
import com.metaself.app.ui.day.DayPartWording
import com.metaself.app.ui.day.DayTotalsWording
import com.metaself.app.ui.portion.portionWords
import com.metaself.app.ui.day.StepBar
import com.metaself.app.ui.day.StreakWording
import com.metaself.app.domain.streak.ConsistencyFigure
import com.metaself.app.domain.window.DayMeasured
import com.metaself.app.domain.window.DayWindow
import com.metaself.app.ui.day.MeasuredMark
import com.metaself.app.ui.day.WindowMark
import com.metaself.app.ui.movement.MovementWording
import com.metaself.app.ui.window.WindowWording
import com.metaself.app.ui.day.MacroBar
import com.metaself.app.ui.day.ProportionRule
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/**
 * What is left of today, and what has been eaten of it — as a page rather than a dashboard (D49).
 *
 * It reads down, in the order D49 sets out: the number the screen exists to answer, what that
 * number means, a hairline filled to the proportion of the day gone, the three macros as columns,
 * the day's walking with what it earned, any notices as notes in the margin, the ways in, the food,
 * and the small print at the foot. Fourteen things used to be drawn at one volume, so the screen
 * had to be read line by line instead of glanced at; nothing here is new information, and almost
 * nothing is in the place it was.
 *
 * **The date is not drawn here.** The title bar above this content already carries it, and D49's
 * kicker would be the same date twice on one screen.
 *
 * **Nothing on this page is coloured for being over target.** D14 has always held for past days,
 * and D48 extended the same reasoning to today: over target is a fact about the day, said in the
 * same voice as every other fact. Red is left to the two things the owner has to go and put right —
 * a refusal, and a bad field.
 *
 * **The record is no longer on this page (D49 item 8 as revised, D51).** The day used to draw every
 * item of every logging, with its figures beneath, which pushed the small print past the bottom of
 * the screen on an ordinary day — so the footnotes could only be reached by scrolling, and
 * scrolling took the one number this page exists to answer away with it. Recording something and
 * reviewing what you recorded are two different jobs: the day answers *how am I doing*, and the
 * record answers *what exactly did I write down, and is it right*. The second job is rare,
 * deliberate, and gets a screen of its own (D50). What is left here is one line per part of the
 * clock — four at most, five when something was written down on another day — and each line is a
 * door to that screen.
 *
 * So the per-item rows, the chosen-items bar, the Undo row, the hold-to-choose hint, the open and
 * close affordance and the "Changed for this day" line are all gone from this screen. They are not
 * gone from the app: every one of them is about a row of the record, and goes where the rows went.
 *
 * **Ten callbacks came off this signature with them** — correcting a row, deleting one, putting one
 * back, opening a meal he built, ticking rows, clearing the tick, making a meal out of them, and
 * setting a time that is not known. None of them is deleted from the app; they are the record
 * screen's, and it takes them when it is built. They are off THIS signature because a parameter
 * nothing reads is a promise the screen is not keeping.
 *
 * **What has NOT been touched is the state itself** — the chosen rows, whether choosing is going
 * on, a refusal, and the sheet that names a new meal all stand exactly as they were, because making
 * a meal is also reached from the screen that shows the model's answer (D46, #24), and because the
 * one view model behind this screen is the one the record screen will read.
 *
 * Not one stored number, calculation or provenance badge moved for any of it. D4 stands.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayScreenContent(
    state: DayUiState.Ready,
    onAdd: () -> Unit,
    onDescribe: () -> Unit,
    onScan: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissTargetChange: () -> Unit,
    onDismissJustLogged: () -> Unit,
    onDismissEncouragement: () -> Unit,
    onDismissRefusal: () -> Unit,
    modifier: Modifier = Modifier,
    // Defaulted for the reason every callback added after this screen was written is: every caller
    // written before D45 (issue #13) draws the day it always drew, and the day pager is the one
    // that wires it.
    onDismissFoodRetaught: () -> Unit = {},
    // Opening the day's record at one part of the clock (D50, D51). Defaulted for the reason the
    // two above are, and because the screen it opens is the next step: until it exists, a row is
    // still a real control and still says what it holds — it simply has nowhere to go yet.
    onOpenPart: (DayPart) -> Unit = {},
) = CompositionLocalProvider(LocalMoves provides Motion.moves(state.isToday, LocalMoves.current)) {
    // Today moves, the past is still (public issue #16): everything drawn for a past day is still,
    // whatever the system allows, and today moves only if the system allows motion at all.
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Section),
    ) {
        // The one day this is shown (D21). Above the day's own number, because it is the only
        // thing on the screen more important than what is left to eat today — and because it is
        // the announcement that that number has just changed by the whole size of the deficit.
        // Milestones share the arrival's one-day life. A milestone and an arrival cannot land
        // together: reaching the goal switches it to holding, which leaves no milestone to reach.
        (state.goalReached ?: state.milestoneReached)?.let { reached ->
            MarginNote(text = reached, style = MaterialTheme.typography.titleMedium)
        }

        // D49 items 2, 3 and 4: the number, what it means, and how much of the day has gone.
        DayAnswer(state = state)

        // D49 item 5. Three columns rather than three sentences stacked: one shape to glance at,
        // ranked a step below the number above them by size alone.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Section),
        ) {
            DayTotalsWording.macroBars(state.target, state.remaining).forEach { bar ->
                MacroBar(
                    kicker = bar.kicker,
                    figure = bar.figure,
                    fractionEaten = bar.fractionEaten,
                    overTarget = bar.overTarget,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // D49 item 6, asked for by name: the day's walking on its own line
        // directly beneath the macros and in their treatment, with what it earned beside it.
        // Activity is not only an input to the calorie arithmetic — it is a reason to go for a
        // walk (D12a) — and what it earned is what moved the figure at the top of this page (D9).
        state.movement?.let { walked ->
            StepBar(today = walked)

            MovementWording.capped(walked.credit)?.let { capped ->
                Text(
                    text = capped,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // D49 item 7: every notice below is a margin note — a rule down the leading edge with the
        // words beside it — rather than a card that interrupts the page. Not one sentence and not
        // one button changes; only the frame around them is gone.

        // One encouraging line, at most one a day, dismissible and never repeated.
        state.encouragement?.let { said ->
            MarginNote(
                text = said,
                dismissLabel = stringResource(R.string.today_got_it),
                onDismiss = onDismissEncouragement,
            )
        }

        // Decision D11: a number that moves under you without saying so is a number you stop
        // trusting. This stays until dismissed rather than fading, because a notice that
        // disappears while the phone is in a pocket has announced nothing.
        state.targetChangeNotice?.let { notice ->
            MarginNote(
                text = notice,
                dismissLabel = stringResource(R.string.day_target_changed_dismiss),
                onDismiss = onDismissTargetChange,
            )
        }

        // D45 (issue #13): a food's own stored figures changed because of what he just logged.
        // Under the target's notice rather than instead of it: the two are about different things
        // and each has its own Got it, because suppressing either would be the silence this closes.
        // It waits until dismissed for the same reason the one above does, and survives a swipe to
        // another day — a food is not a property of a day.
        state.foodRetaughtNotice?.let { notice ->
            MarginNote(
                text = notice,
                dismissLabel = stringResource(R.string.day_food_retaught_dismiss),
                onDismiss = onDismissFoodRetaught,
            )
        }

        // Saving used to return the owner here with nothing to say it had worked.
        state.justLogged?.let { logged ->
            MarginNote(
                text = logged,
                dismissLabel = stringResource(R.string.today_logged_dismiss),
                onDismiss = onDismissJustLogged,
            )
        }

        // A refusal is not a failure: it names the row that stands in the way, so he can drop that
        // one and try again. What he chose stays chosen for exactly that reason. Its edge is the
        // error colour, which nothing else on this page uses: a refusal is one of the two things in
        // the app the owner actually has to go and put right, and over target is not one of them.
        // An action that threw is a failure, and says so in the same place (ActionRefused).
        val sentence = state.refusal ?: state.failed?.let { stringResource(it.sentence) }
        sentence?.let { refusal ->
            MarginNote(
                text = refusal,
                accent = MaterialTheme.colorScheme.error,
                dismissLabel = stringResource(R.string.day_refusal_dismiss),
                onDismiss = onDismissRefusal,
            )
        }

        // Adding something starts from the owner's own foods, and that search is the floating
        // button, where Android puts a main action. Describing is what the search falls through to
        // when nothing matches — which is exactly when a new food is the right outcome, and is why
        // the path was turned around: nothing resolves a model proposal against food already on the
        // record, so every described meal makes fresh items. The three other ways in sit here:
        // describing on its own, the barcode, and typing the numbers, which must always be one tap
        // away and never behind a search (D8). See D28.
        // Wrapping, not a fixed row. At a larger system font the three labels are wider than the
        // screen, and a plain Row answers that by breaking a label apart mid-word rather than by
        // moving one down a line. Three buttons that must always be one tap away (D8) must survive
        // the font size the reader chose.
        //
        // Above the day's list rather than under it, which is where D49 item 9 expects them. The
        // colophon goes below the list because it is a footnote; these three are not, and a day of
        // several meals would put them below the fold — an action nobody scrolls to is an action
        // that is not one tap away.
        //
        // The wrapped lines are spaced as well as the controls on one line (#69). Until these were
        // buttons there was nothing to see between two rows of them; a border makes a row that
        // touches the row above it read as one block with a line through it.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
            verticalArrangement = Arrangement.spacedBy(Spacing.Related),
        ) {
            WayIn(stringResource(R.string.propose_describe_meal), onDescribe)
            WayIn(stringResource(R.string.scan_open), onScan)
            WayIn(stringResource(R.string.today_type_numbers), onAdd)
        }

        // **The chosen-items bar, the Undo row and the hold-to-choose hint are no longer drawn
        // here.** Every one of them is about a row of the record, and the record is no longer on
        // this screen: what is drawn below is four parts of the clock at most, each covering
        // several loggings. An Undo over a list that does not show what was deleted, and a hint
        // telling him to hold a row that is not there, would both be instructions about another
        // screen. They belong with the rows, and go to the record screen (D50) with them.
        //
        // **Nothing has been removed from the state.** `chosen`, `choosing`, `refusal` and the
        // naming sheet below all stay exactly as they were: making a meal is also reached from the
        // screen that shows the model's answer (D46, #24), and the sheet's own tests render it
        // directly, so neither would notice this path dying. Only the day's *drawing* has moved.

        // D49 item 8, as D51 revised it: the day's loggings as one line per part of the clock.
        if (state.meals.isEmpty()) {
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
            DayPartRows(meals = state.meals, onOpenPart = onOpenPart)
        }

        // D49 item 9: the small print, at the foot, after his food rather than in front of it.
        Colophon(state = state, onOpenSettings = onOpenSettings)
    }
}

/**
 * The day's one answer: the number, what it means, and how much of the day has gone (D49).
 *
 * Three pieces of one thought, so they are grouped tightly and the page's ordinary gap is left to
 * separate this block from what follows. The figure is the display face at 72 sp and nothing else
 * on the screen is within two steps of it; the line under it is ordinary prose, because it is read
 * once and then never again.
 *
 * **The figure is ink whether the day went over or not.** It used to turn red, which told the owner
 * off in the colour this app reserves for a refusal and a bad field — and going over a target is the
 * least alarming of the three (D14, D48). The words still say "over".
 *
 * **The line never says "kcal" twice**, which is the defect D49 exists to fix: the denominator lost
 * its unit, because the unit has already been said an inch to the left. Both halves refuse to wrap,
 * so what was two lines on a narrow phone is one line at any width.
 */
@Composable
private fun DayAnswer(state: DayUiState.Ready) {
    val amount = DayTotalsWording.amount(
        remaining = state.remaining,
        isToday = state.isToday,
        nothingLogged = state.nothingLogged,
    )
    val unit = DayTotalsWording.unit(
        remaining = state.remaining,
        isToday = state.isToday,
        nothingLogged = state.nothingLogged,
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Related),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            // Null on a past day nobody wrote anything on: that day has no number, and a dash in
            // the slot the page reserves for its one answer reads as a figure that failed to load
            // rather than as a day with no record (D4).
            amount?.let { figure ->
                // On today a new figure rolls in from below as the old one leaves upward (#16),
                // rather than one number being swapped for another. Only whole figures are ever
                // drawn — never a count through the values between — because each of those would
                // be a number the day never had (D4). Still on a past day and with animations off.
                if (LocalMoves.current) {
                    AnimatedContent(
                        targetState = figure,
                        transitionSpec = { rollUp() },
                        label = "day figure",
                    ) { shown -> DayFigure(shown) }
                } else {
                    DayFigure(figure)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    softWrap = false,
                )
                // Beside a figure and nowhere else. "Nothing logged of 2,090" would hand a
                // denominator to a day with no numerator, which is the reading D4 refuses.
                if (amount != null) {
                    Text(
                        text = DayTotalsWording.ofTarget(state.target),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }

        // What the ring carried, at 2 dp instead of 200. The arithmetic is unchanged and still the
        // domain's: a ring is a picture of a number, and so is a rule.
        ProportionRule(
            fraction = DayProgress.eatenFraction(
                target = state.target.kcal,
                eatenKcal = state.target.kcal - state.remaining.kcal,
            ),
            thickness = 2.dp,
            fill = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** The day's one number, in the display face. */
@Composable
private fun DayFigure(figure: String) {
    Text(
        text = figure,
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
    )
}

/**
 * The new figure up from below, the old one up and away, both fading, over [Motion.SETTLE_MILLIS]
 * with Material's standard easing and no overshoot. Clipped to the figure's own line, so the roll
 * happens inside it rather than over the words above and below.
 */
private fun AnimatedContentTransitionScope<String>.rollUp(): ContentTransform {
    val slide = tween<IntOffset>(durationMillis = Motion.SETTLE_MILLIS, easing = Motion.Easing)
    val fade = tween<Float>(durationMillis = Motion.SETTLE_MILLIS, easing = Motion.Easing)
    return (slideInVertically(slide) { height -> height } + fadeIn(fade))
        .togetherWith(slideOutVertically(slide) { height -> -height } + fadeOut(fade))
        .using(
            SizeTransform(clip = true) { _, _ ->
                tween(durationMillis = Motion.SETTLE_MILLIS, easing = Motion.Easing)
            },
        )
}

/**
 * One of the three other ways to log: describing a meal, the barcode, or typing the numbers (#69).
 *
 * **Outlined, and ranked below the floating button on purpose.** D28 says the way in is the owner's
 * own foods — the search behind "Add something" — and that describing is what that search falls
 * through to when none of them is it. So these three are secondary. But D8 says typing the numbers
 * is always one tap from the day and never behind a search, so they cannot be folded into a menu or
 * a sheet either. Secondary, and never more than one tap away: that is a border and no fill.
 *
 * **Not filled, and not green.** D48 makes colour punctuation — it appears about twice on a page —
 * and three filled buttons in one row would spend the whole page's budget on the least important
 * thing on it. The border is `outline`, which the palette defines as the edge of something
 * interactive; the label takes the ink ladder's top step, where Material would have given it the
 * brand green. They were bare `TextButton`s before this, which read as three links in a line of
 * prose rather than as the doors into the thing the app exists to do.
 *
 * **The 48 dp floor is the touch target**, the same one the day's own rows take. Material's button
 * is 40 dp tall by default, which is short of it — the thing under the finger grew, the words on it
 * did not.
 *
 * **The label is never truncated and is not allowed to be.** Nothing here sets `maxLines` or an
 * overflow, so a label too wide for its line wraps inside the button and the button grows. The row
 * around it wraps first: `FlowRow` moves a whole control to the next line rather than squeezing it,
 * which is what #51 put there and what the render test at a narrow screen holds it to.
 */
@Composable
private fun WayIn(label: String, onClick: () -> Unit) {
    // Gives under the finger on today, as the day's rows do (#16).
    val press = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .givesUnderPress(press),
        interactionSource = press,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(label)
    }
}

/**
 * A notice, as a note in the margin: a 2 dp rule down the leading edge and the words beside it.
 *
 * Every announcement on this screen used to be a card, a coloured sentence or a block with a button
 * under it, each drawn at its own volume — so the page was interrupted several times before it got
 * to the food. The edge says "this is an aside" in the width of a line, and the prose steps down to
 * the ink ladder's second step, which exists for exactly this: text that is read rather than
 * scanned.
 *
 * The edge is drawn as a box in a row rather than painted at a fixed x, so it is on the LEADING
 * side in both directions and will still be on the correct side the day the app speaks Hebrew (#9).
 *
 * [dismissLabel] and [onDismiss] travel together: a note nobody can put away is a note that has to
 * be scrolled past for ever, and a button with no words on it is not a control. The one note with
 * neither is the arrival at a goal, which is shown on exactly one day and takes itself away.
 */
@Composable
private fun MarginNote(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(accent),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
        ) {
            Text(text = text, style = style, color = MetaSelfInk.two)

            if (dismissLabel != null && onDismiss != null) {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                ) {
                    Text(dismissLabel)
                }
            }
        }
    }
}

/**
 * The small print of the day, at the foot of the page (D49 item 9).
 *
 * The window's mark and tally, what the window has to say about the day, and how consistently he
 * has been logging. Every one of these used to sit between the number and his food, in the middle
 * of the page, at the same volume as everything else — so the screen had to be read past before it
 * could be read. They are facts about the record rather than about what he is deciding now, which
 * is what a colophon is for.
 *
 * **Not one sentence changes**, and neither does what any of them counts: [WindowWording],
 * [StreakWording] and the two marks print exactly what they printed before. What changed is where
 * the block sits and how loudly it is set. The one later exception is the streak, which since D52
 * prints one figure instead of three, and sets a milestone as one.
 *
 * The counts are the app's smallest type; the sentences are not. `labelSmall` is 11 sp, semibold
 * and letterspaced — a face for a caption of three words, and a face that turns a sentence like
 * "Still open — nothing is judged until you have fasted 14 hours." into something nobody reads. So
 * the figures take it and the prose stays at `bodySmall`, one step above.
 */
@Composable
private fun Colophon(
    state: DayUiState.Ready,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Related),
    ) {
        // The window as a mark rather than a sentence: the sentence was four times as long and said
        // the same thing. Exactly ONE mark is drawn, and which one follows the day on screen —
        // the verdict is derived from the shown day's rule, so branching on it is branching on the
        // kind of window that actually governed that day.
        //
        // It used to be two independent blocks: the ring gated on `windowOpenNow`, which is
        // derived from TODAY's rule, and the measured mark on the verdict, which is derived from
        // the shown day's. Set one kind, then the other, then page back a day and the two
        // disagreed — a past fixed day under a ratio today drew NEITHER mark (losing that day's
        // tally and its only way into window settings), and a past measured day under fixed hours
        // today drew BOTH, printing the identical tally twice, stacked.
        //
        // `windowOpenNow` stays exactly what it is — a fact about NOW — and only fills the ring in.
        when (state.verdict) {
            // The measured kind has a mark of its own rather than the ring: the ring is about
            // the hours he set, which is not what a ratio sets. On today, before the day's first
            // input, this draws nothing at all (D29); a past day keeps its days-kept tally.
            // Only the LAST stretch of a day can still be open — every earlier one was closed by
            // the fast that started the next — so that is the one with a closing time to announce.
            is DayMeasured -> MeasuredMark(
                // Today's one sentence, already chosen by the view model from the latest stretch
                // and the moment the screen was last brought to the front (D32).
                sentence = state.ratioNow,
                // The ratio's tally in words, on days the ratio governed. A measured day from before
                // the rule in force now has no tally: that rule's count is not about it.
                tally = state.ratioTally,
                onOpenSettings = onOpenSettings,
            )

            // The ring, filled or empty, and the day's tally beside it. `windowOpenNow` is
            // answered by the FIXED kind alone, so a day reached here while a RATIO is in force
            // now has no ring at all and draws its tally by itself.
            //
            // That is deliberate (design §4, corrected 2026-09-17). The ring is a fact about NOW
            // while the tally belongs to the day on screen, and a ring filled by whether eating is
            // going on right now, printed beside a day judged by the hours on a clock, describes a
            // different thing from the one the tally beside it was scored by — and sitting where
            // it sits, it reads as belonging to that day.
            is DayWindow -> WindowMark(
                open = state.windowOpenNow,
                daysKept = state.windowKept,
                daysJudged = state.windowJudged,
                onOpenSettings = onOpenSettings,
                // Non-null only while a RATIO governs today, whose count is in stretches.
                tally = state.ratioTally,
            )

            // No rule governed this day, so there is no mark of either kind.
            null -> Unit
        }

        // A meal outside it is still worth a line — but only when there was one, and only on the
        // day it happened. A past day states the same fact flatly (D14).
        WindowWording.outside(state.verdict as? DayWindow, state.isToday)?.let { window ->
            Text(
                text = window,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The measured kind's equivalents, said about each stretch the day BEGAN (design §3): the
        // span when one ran past the ratio, the admission that one is still open and so cannot be
        // judged yet, and the admission that a stretch of one input is not judged either way.
        //
        // Per stretch rather than per day, because a day can hold more than one: eating in the
        // morning and again after a long enough fast is two, and one of them being over the ratio
        // says nothing about the other. At most one of these three speaks about any one stretch.
        //
        // None of the three needs a moment handed to it: a span is a finished measurement, and the
        // still-open line is a past day's sentence only — on today the mark's one sentence (D32)
        // has already said the one thing there is to say about an open stretch.
        (state.verdict as? DayMeasured)?.let { measured ->
            measured.stretches.forEach { stretch ->
                WindowWording.span(stretch, measured.window)?.let { span ->
                    Text(
                        text = span,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WindowWording
                    .stillOpen(stretch, measured.window, state.isToday)
                    ?.let { stillOpen ->
                        Text(
                            text = stillOpen,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                // Prose, so `bodySmall` like the two sentences above it and NOT `labelSmall`.
                // This branch was demoted to the 11 sp semibold letterspaced face by mistake when
                // the colophon was built, which is exactly what this file's own comment above says
                // must not happen to a sentence.
                WindowWording.notJudged(stretch)?.let { notJudged ->
                    Text(
                        text = notJudged,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // The one line in the colophon that asks for something to be done — a meal's time is not
        // known, and tapping the "--:--" on the row sets it (D33). Prose, and actionable prose at
        // that, so `bodySmall`: it was demoted to `labelSmall` when the colophon was built, which
        // set the only instruction on the page in the smallest, tightest face in the app.
        WindowWording.untimed(state.verdict)?.let { untimed ->
            Text(
                text = untimed,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Counted from the record every time it is drawn, so a day filled in late repairs the run
        // by itself (D13). ONE figure, chosen by the record (D52): the run while there is one worth
        // naming, otherwise the month; nothing rather than a zero, because a zero here is a reproach
        // dressed as a statistic and the past does not nag (D14). The other two counts are on the
        // record screen, under "Your record".
        state.consistency?.let { figure ->
            if (figure is ConsistencyFigure.Run && figure.milestone) {
                RunMilestone(days = figure.days)
            } else {
                Text(
                    text = StreakWording.day(figure),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A run reaching 7, 30, 100 or 365 days, on the day it does (D52).
 *
 * Set as an achievement rather than as small print: the figure in the display face at the scale's
 * block-figure size, its words beside it in running text, both in the tertiary teal — the palette's
 * one family that means nothing else on this page. Two pieces rather than one string, as every
 * figure in the app is set beside its words. No emoji and nothing that moves.
 */
@Composable
private fun RunMilestone(days: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        Text(
            text = days.toString(),
            modifier = Modifier.alignByBaseline(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Text(
            text = StreakWording.runWords(days),
            modifier = Modifier.alignByBaseline(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/**
 * The way back from a delete: what happened, and the one word that undoes it (#37, #25).
 *
 * **No longer drawn on the day** — nothing is deleted there any more, because the rows are not
 * there any more (D51). It goes with them to the record screen (D50), which is the only screen that
 * can offer it honestly: an Undo over a list that does not show what was deleted says nothing about
 * what would come back.
 *
 * Kept whole rather than rewritten there, because it is the exact markup that was on the day and
 * its coverage travels with it. **Where it SITS is the record screen's own decision and inverts**:
 * on the day it was drawn above the list, because a day of several meals put it below the fold and
 * an Undo nobody can see is a delete that cannot be undone. The record screen pins its actions to
 * the bottom edge instead, which answers the same objection a different way.
 */
@Composable
internal fun UndoRow(onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
    ) {
        Text(
            text = stringResource(R.string.today_deleted),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onUndo) {
            Text(stringResource(R.string.today_undo))
        }
    }
}

/**
 * The count of what is chosen, and the one thing that can be done with it.
 *
 * **No longer drawn on the day**, for the reason [UndoRow] is not: rows are chosen where rows are
 * shown, and that is the record screen now (D50, D51). Unchanged otherwise, and its coverage moved
 * with it.
 *
 * The action carries the number — "Make a meal from these 4" — because "Make a meal" on its own does
 * not say whether the four he thinks are ticked are the four that are. At one it reads "this one":
 * the day offers a meal from a single row, and "these 1" was on screen. Chosen through a plural
 * resource, not a test for 1 in code, because Hebrew (#9) has more forms than English (D37).
 *
 * **[onChooseAll] and [onDelete] are the record screen's two additions**, and they are nullable
 * rather than defaulted no-ops so that a caller which cannot honestly offer them draws no button at
 * all: "All" ticks every row of the day and belongs where those rows are (it was in the day's title
 * bar and had nothing to act on there), and deleting everything ticked in one go is #50. A no-op
 * lambda would have drawn a button that did nothing.
 */
@Composable
internal fun Chosen(
    count: Int,
    onClear: () -> Unit,
    onMakeMeal: () -> Unit,
    onChooseAll: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.day_chosen, count.toString()),
                style = MaterialTheme.typography.titleMedium,
            )
            onChooseAll?.let { chooseAll ->
                TextButton(onClick = chooseAll) {
                    Text(stringResource(R.string.day_choose_all))
                }
            }
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.day_clear_choosing))
            }
        }
        // At one, the hint about holding has been acted on and is gone, and nothing else on screen
        // says the next tap adds to the choice (public issue #12). From two on the choice plainly
        // grows by tapping.
        if (count == 1) {
            Text(
                text = stringResource(R.string.record_tap_to_add),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
        }
        Button(onClick = onMakeMeal, modifier = Modifier.fillMaxWidth()) {
            Text(pluralStringResource(R.plurals.day_make_meal, count, count))
        }
        // Plain rather than coloured. D48 leaves red to the two things he has to go and put right —
        // a refusal and a bad field — and a delete he chose to press is neither.
        onDelete?.let { delete ->
            TextButton(onClick = delete, modifier = Modifier.fillMaxWidth()) {
                Text(pluralStringResource(R.plurals.record_delete_chosen, count, count))
            }
        }
    }
}

/**
 * The sheet itself: chrome around [MealNameSheet].
 *
 * Split in two because a modal sheet draws into a window of its own, which the render helper — which
 * walks the activity's view tree — cannot see. All the words are in the content, so the content is
 * what a test draws; the sheet around it is checked by hand on the phone with the rest of the
 * navigation.
 *
 * Visible outside this file because the screen showing the model's answer draws this same sheet to
 * name what it has just logged (D46, issue #24): one way of naming a meal, reached from two places.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MealNamingSheet(
    items: List<FoodItem>,
    isToday: Boolean,
    name: String,
    refusal: String?,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    keepOnly: Boolean = false,
    onLogInstead: (() -> Unit)? = null,
    busy: Boolean = false,
) {
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        MealNameSheet(
            items = items,
            isToday = isToday,
            name = name,
            refusal = refusal,
            onNameChange = onNameChange,
            onConfirm = onConfirm,
            onCancel = onCancel,
            keepOnly = keepOnly,
            onLogInstead = onLogInstead,
            busy = busy,
        )
    }
}

/**
 * Naming what is about to become a meal: the name, what is going in, and what it comes to.
 *
 * **Nothing is typed here but the name, and nothing can be.** The amounts are what was logged, which
 * is the whole reason this way in exists — a meal made out of a day has no amounts to ask for.
 *
 * It says the one thing about this act that could surprise him: the day's rows become one row. Said
 * before it happens, together with the fact that no number he logged changes.
 *
 * **It says it about the right day.** The sheet is reachable from any day reached by swiping back,
 * so [isToday] chooses between "Today will show these..." and "That day will show these...". An app
 * that told a past day it was today would be saying something untrue immediately before the one
 * irreversible act in this feature.
 *
 * **It never shows an empty name.** The sheet opens with nothing typed, and the named sentence then
 * read "…as one row called ." Until there is a name — by the same test that enables the button, so
 * spaces are not one — it says "under the name you give it", a sentence that needs no name rather
 * than a placeholder in one that does (D37). The name goes into the sentence trimmed: a space typed
 * before it is not part of it. The field keeps exactly what was typed.
 *
 * **A [refusal] is shown here rather than only behind the sheet**, because this is where he is when
 * it arrives: the name he typed is still in the field above it, and correcting it is one edit. The
 * day underneath says the same sentence for when he closes the sheet instead.
 */
@Composable
internal fun MealNameSheet(
    items: List<FoodItem>,
    isToday: Boolean,
    name: String,
    refusal: String?,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    // D58 §5.2: naming a described meal to keep in My meals, logging nothing. The rows are not on
    // any day, so the sentence says where they go instead, and a refusal about parts offers
    // [onLogInstead] beside it.
    keepOnly: Boolean = false,
    onLogInstead: (() -> Unit)? = null,
    busy: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Screen, vertical = Spacing.Related),
        verticalArrangement = Arrangement.spacedBy(Spacing.Related),
    ) {
        Text(
            text = stringResource(R.string.day_meal_name_title),
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.day_meal_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Everything between the name and the buttons scrolls, and only it: a described meal can
        // have many parts, and a list that grew past the screen once pushed the button off the
        // bottom with no way to reach it. The name stays above and the buttons below, in view.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.Related),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                items.forEach { item ->
                    Text(
                        text = DayTotalsWording.itemName(item),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val words = portionWords(item)
                    Text(
                        text = DayTotalsWording.itemNumbers(item, words),
                        style = MaterialTheme.typography.labelSmall,
                        // "≈" is read as a symbol's name, or not at all: said as "about" (D58 §12.9).
                        modifier = if (DayTotalsWording.amountEstimated(item, words)) {
                            Modifier.semantics {
                                contentDescription = DayTotalsWording.itemNumbersSpoken(item, words)
                            }
                        } else {
                            Modifier
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.day_meal_name_total,
                    DayTotalsWording.itemsTotal(items),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            val typed = name.trim()
            Text(
                text = when {
                    keepOnly && typed.isEmpty() -> stringResource(R.string.propose_keep_only_will_unnamed)
                    keepOnly -> stringResource(R.string.propose_keep_only_will, typed)
                    typed.isEmpty() && isToday -> stringResource(R.string.day_meal_will_group_unnamed)
                    typed.isEmpty() -> stringResource(R.string.day_meal_will_group_unnamed_past)
                    isToday -> stringResource(R.string.day_meal_will_group, typed)
                    else -> stringResource(R.string.day_meal_will_group_past, typed)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )

            // Why nothing was made, next to the name that caused it and above the button that would try
            // again. The day behind the sheet says the same thing, and cannot be read through it.
            refusal?.let { why ->
                Text(
                    text = why,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                onLogInstead?.let { logInstead ->
                    OutlinedButton(onClick = logInstead, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.propose_log_instead))
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.day_meal_name_cancel))
            }
            Button(onClick = onConfirm, enabled = name.isNotBlank() && !busy) {
                Text(stringResource(R.string.day_meal_name_confirm))
            }
        }
    }
}

/**
 * The day as four parts of the clock at most, one line each, and every line a door (D51).
 *
 * **What a row says**: the part **and its hours** — "Midday · 12:00–17:59" — so the rule the owner
 * locates a thing by is on the screen rather than buried in the code; what is in it, from what is
 * already on the record; and what it came to. Nothing else. The detail is behind the door.
 *
 * **The hours end at :59, never at the next part's first minute.** 18:00 is Evening, and a range
 * printed to make a rule followable must not need a footnote.
 *
 * **A row is a real control**, not a line of text that happens to respond: a tap target of the
 * usual 48, and what a screen reader hears names the part, so four rows are not four identical
 * ones (#12).
 *
 * **The zone is the machine's**, read here rather than carried on the state, which is what every
 * other clock reading on this screen already does — including the time drawn beside a logging
 * (D33). One instant read in two zones is two clock readings, and the one that matters is the clock
 * the owner is reading the screen by.
 *
 * **There is no tappable time here**, and there cannot be: a part of the day holds several
 * loggings with several times, so the control that sets an unknown one (D33) has no single row to
 * sit on. It goes to the record screen, where each logging is its own row again, and the colophon's
 * sentence now says so.
 */
@Composable
internal fun DayPartRows(
    meals: List<Meal>,
    modifier: Modifier = Modifier,
    onOpenPart: (DayPart) -> Unit = {},
) {
    val zone = ZoneId.systemDefault()
    val parts = remember(meals, zone) { DayParts.of(meals, zone) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        parts.forEach { part ->
            DayPartRow(part = part, onOpen = { onOpenPart(part) })
        }
    }
}

/** One part of the clock: what it is, what is in it, and what it came to. */
@Composable
private fun DayPartRow(part: DayPart, onOpen: () -> Unit) {
    val name = stringResource(
        when (part.part) {
            PartOfTheClock.NIGHT -> R.string.day_part_night
            PartOfTheClock.MORNING -> R.string.day_part_morning
            PartOfTheClock.MIDDAY -> R.string.day_part_midday
            PartOfTheClock.EVENING -> R.string.day_part_evening
            // The row for anything written down on another day, which belongs to no part of the
            // clock and so has no hours to print (D33).
            null -> R.string.day_part_untimed
        },
    )
    val heading = DayPartWording.heading(name, part.part)
    val what = DayPartWording.what(part.meals)
    val total = DayPartWording.total(part.meals)
    val size = DayPartWording.size(part.meals)
    val said = stringResource(R.string.day_part_description, heading, what, total)

    // The row gives a little under the finger on today (#16); the ripple answers on any day.
    val press = remember { MutableInteractionSource() }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .givesUnderPress(press)
                .clickable(
                    interactionSource = press,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onOpen,
                )
                // The usual 48 of touch. A row of three short lines already clears it; the floor is
                // here for the row that does not — one logging, with a name of one word.
                .heightIn(min = 48.dp)
                // One control, named once. Without the merge a screen reader reads a row as three
                // unrelated fragments and none of them says which part of the day it belongs to.
                .semantics(mergeDescendants = true) { contentDescription = said }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // The part and its hours as the kicker over the row, in the treatment D49 gives a
                // kicker: the small letterspaced face, one step down the ink ladder, because it
                // labels the line rather than being the line.
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleSmall,
                    color = MetaSelfInk.two,
                )
                // What is in it — a name, never an invented label. Separate from the figures for
                // the reason every other row in this app keeps them separate: a Hebrew name and a
                // Latin figure in one string get reordered, and the day's list once showed a
                // calorie count in front of the food it belonged to.
                Text(
                    text = what,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                // Said as a count, and only when there is more than one: at one the line above has
                // already named the only thing there is. Through a plural resource rather than a
                // test for 1 in code, because Hebrew has more forms than English (D37, #9).
                if (size > 1) {
                    Text(
                        text = pluralStringResource(R.plurals.day_part_things, size, size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = total,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
        HorizontalDivider()
    }
}

/**
 * The day, meal by meal.
 *
 * **No longer drawn on the day itself (D49 item 8 as revised, D51).** This is the record's list,
 * and it moves to the record screen (D50) whole — with the time beside each logging, the meal he
 * built opening to its parts, and each row editable and deletable on its own. It is kept here,
 * unchanged, because that screen draws exactly this and rewriting it there would be a second
 * version of the same list to keep in step.
 *
 * **A meal the owner built shows as one row with the name he gave it**, and opens to what is under
 * it. Everything else shows its items flat, as it always has — typed, described, scanned, or one
 * food on its own have no name, and inventing one for a row is exactly what this app stopped doing
 * when the derived meal list went.
 *
 * The title is read through the pointer to the meal, so renaming the salad retitles every day it
 * was ever eaten, and not one stored number moves for it.
 *
 * **Edit and delete stay on the items, not on the title.** The record is rows: the title is a label
 * over them, and a button that deleted "the salad" would be deleting five rows on the strength of a
 * label. Opening the row is how he gets at them.
 *
 * **D49 item 8 ranks this list by type and by nothing else.** A food's name is `bodyLarge` ink — the
 * thing on the list — its figures `bodySmall` on the caption step, and a meal he built takes the
 * `titleSmall` kicker D49 asks for, with a hairline running from the name across the rest of its
 * line. (The kicker was first built as the `titleMedium` of a section heading, which ranked a salad
 * level with a screen's own sections; that is corrected. D49 says the rule runs "to its time" and
 * it runs to the trailing edge instead — the time is the leading column on every row in this list
 * and is the tap target that sets an unknown one (D33), so it is not moved to suit a hairline. See
 * the comment at the call site.) What it does NOT do is move the calories out of
 * that figures line and set them at the end of the row, which is the rest of what D49 describes:
 * the line is one string on purpose (`DayTotalsWording.itemNumbers`), because a Hebrew name and a
 * Latin figure in one string get reordered, and splitting the total off would mean either printing
 * it twice or rewriting a sentence half the day's tests read verbatim. Worth doing on its own, not
 * worth smuggling into a layout change.
 */
@Composable
internal fun DayMeals(
    meals: List<Meal>,
    onTime: (Meal) -> Unit = {},
    openMeals: Set<Long> = emptySet(),
    onToggleMeal: (Long) -> Unit = {},
    onEditItem: (FoodItem) -> Unit,
    onDeleteItem: (FoodItem) -> Unit,
    modifier: Modifier = Modifier,
    chosen: Set<Long> = emptySet(),
    onBeginChoosing: (Long) -> Unit = {},
    onToggleChosen: (Long) -> Unit = {},
    /**
     * Take a whole meal he built into the choice, or out of it again (D50, #50).
     *
     * A meal he built is one row with its parts behind it, so its kicker is the only thing on
     * screen that stands for all of them — which is why ticking the kicker has to mean the rows.
     * Defaulted, because the choice itself is defaulted: a caller drawing the list with nothing
     * ticked and no way to tick is drawing a list, and this is one more thing it is not offering.
     */
    onChooseMeal: (Meal) -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        meals.forEach { meal ->
            val title = DayTotalsWording.mealTitle(meal)
            if (title == null) {
                meal.items.forEachIndexed { index, item ->
                    LoggedItem(
                        item = item,
                        // A meal's time is drawn once, on its first row: the rows carry no time of
                        // their own, and a salad eaten at 12:40 is not three things eaten at 12:40.
                        // The rows after it keep the column, so names stay in line.
                        leading = {
                            if (index == 0) {
                                EatenAt(meal = meal, onTime = onTime, enabled = chosen.isEmpty())
                            } else {
                                TimeColumn()
                            }
                        },
                        onEdit = onEditItem,
                        onDelete = onDeleteItem,
                        chosen = item.id in chosen,
                        choosing = chosen.isNotEmpty(),
                        onBeginChoosing = { onBeginChoosing(item.id) },
                        onToggleChosen = { onToggleChosen(item.id) },
                    )
                }
            } else {
                LoggedMeal(
                    meal = meal,
                    title = title,
                    onTime = onTime,
                    open = meal.id in openMeals,
                    onToggle = { onToggleMeal(meal.id) },
                    onEditItem = onEditItem,
                    onDeleteItem = onDeleteItem,
                    chosen = chosen,
                    onBeginChoosing = onBeginChoosing,
                    onToggleChosen = onToggleChosen,
                    onChooseMeal = onChooseMeal,
                )
            }
        }
    }
}

/**
 * One meal he built, logged: its name, what it came to, and what is under it.
 *
 * Closed by default, because the whole point of the row is that a salad is one thing he ate rather
 * than five entries to read past.
 *
 * **It says when it was changed that day**, which is the only thing the adjusted flag is for. It is
 * never counted, never aggregated, and never turned into an offer to change the meal: drop the oil
 * every day for a month and the salad still has oil in it.
 *
 * **Ticking the kicker takes the whole meal** (D50, #50). Held, it starts choosing with every one of
 * its rows in; tapped while choosing, it takes them all in or all out. That is the same way round as
 * a row — holding begins, tapping ticks — because a tap on this row already means something, and
 * choosing that began on a tap would turn opening a salad into the start of a meal. A meal he built
 * is the one row on this list that stands for rows he cannot see, so without this the only way to
 * remove one in a single act would be to open it first and tick its parts one by one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoggedMeal(
    meal: Meal,
    title: String,
    onTime: (Meal) -> Unit,
    open: Boolean,
    onToggle: () -> Unit,
    onEditItem: (FoodItem) -> Unit,
    onDeleteItem: (FoodItem) -> Unit,
    chosen: Set<Long> = emptySet(),
    onBeginChoosing: (Long) -> Unit = {},
    onToggleChosen: (Long) -> Unit = {},
    onChooseMeal: (Meal) -> Unit = {},
) {
    val choosing = chosen.isNotEmpty()
    // Whether the whole meal is in the choice, which is what its own tick says. Every row of it, not
    // some: a tick drawn for a meal with one row ticked out of five would be claiming four rows the
    // choice does not hold.
    val wholeMealChosen = meal.items.all { it.id in chosen }
    // A firm press when holding takes the meal in, a light tick when a tap does (#16): the hand
    // knows the choice changed before the eye finds the box.
    val haptics = LocalHapticFeedback.current
    val press = remember { MutableInteractionSource() }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .givesUnderPress(press)
                .combinedClickable(
                    interactionSource = press,
                    indication = LocalIndication.current,
                    onClick = {
                        if (choosing) {
                            haptics.performHapticFeedback(Feel.Tick)
                            onChooseMeal(meal)
                        } else {
                            onToggle()
                        }
                    },
                    onLongClick = {
                        haptics.performHapticFeedback(Feel.Thump)
                        onChooseMeal(meal)
                    },
                )
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
        ) {
            if (choosing) {
                // Named for a screen reader, as a row's tick is: it is the whole of what says the
                // meal is in the choice, and nothing else on the row says so.
                val tick = stringResource(
                    if (wholeMealChosen) R.string.day_is_chosen else R.string.day_not_chosen,
                )
                Checkbox(
                    checked = wholeMealChosen,
                    // The row is the target; two hit areas doing one thing is two things to get
                    // wrong.
                    onCheckedChange = null,
                    modifier = Modifier.semantics { contentDescription = tick },
                )
            }
            EatenAt(meal = meal, onTime = onTime, enabled = chosen.isEmpty())
            Column(modifier = Modifier.weight(1f)) {
                // Name and numbers on separate lines, never in one string: a Hebrew name beside a
                // Latin figure gets reordered, and the day's list once showed a calorie count
                // in front of the food it belonged to.
                //
                // D49 item 8: the name is a KICKER over the meal's parts — `titleSmall`, 13 sp
                // semibold and letterspaced — with a hairline running from it across the rest of
                // the line. It was a `titleMedium` section heading, which is the treatment a
                // screen's own sections take and ranked a salad level with one.
                //
                // **The rule runs to the trailing edge, not to the time, and D49 says the time.**
                // In this app the time is the LEADING column: every flat row draws it there so
                // that names line up down the day, and it is the tap target that sets an unknown
                // time (D33). Moving it to the line's end for meal rows alone would break that
                // column on exactly the rows that also have a name. So the rule is the same
                // editorial device with the time on the other side of it — kicker, hairline, and
                // the row's own control at the end — rather than the time being moved to suit a
                // hairline. Not uppercased: the upper-casing of a kicker is the caller's job, and
                // this string is a name the owner typed, which may be Hebrew (#9).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }
                Text(
                    text = DayTotalsWording.mealNumbers(meal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (meal.savedMealAdjusted) {
                    Text(
                        text = stringResource(R.string.day_meal_adjusted),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = if (open) {
                    stringResource(R.string.day_meal_close)
                } else {
                    stringResource(R.string.day_meal_open, DayTotalsWording.mealSize(meal))
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        HorizontalDivider()

        if (open) {
            // The parts of a meal he built can be chosen too, once the row is open: a title is a
            // label over rows, and the rows are still the record.
            meal.items.forEach { item ->
                LoggedItem(
                    item = item,
                    leading = { TimeColumn() },
                    onEdit = onEditItem,
                    onDelete = onDeleteItem,
                    chosen = item.id in chosen,
                    choosing = chosen.isNotEmpty(),
                    onBeginChoosing = { onBeginChoosing(item.id) },
                    onToggleChosen = { onToggleChosen(item.id) },
                )
            }
        }
    }
}

/**
 * One thing eaten.
 *
 * Holding it starts choosing; while choosing, a tap ticks it. That way round for the same reason the
 * foods list uses: a tap on a row already means something, and choosing that began on a tap would
 * turn every look at the record into the start of a meal.
 *
 * **What a plain tap means is what Edit means: it opens the row to be corrected**
 * (public issue #12). The row lit up under a tap and then did nothing, which reads as a broken
 * screen; correcting is what the record is for (D50), so that is the tap's answer. The pencil stays,
 * for whoever looks for a control, not a row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoggedItem(
    item: FoodItem,
    leading: (@Composable () -> Unit)? = null,
    onEdit: (FoodItem) -> Unit,
    onDelete: (FoodItem) -> Unit,
    chosen: Boolean = false,
    choosing: Boolean = false,
    onBeginChoosing: () -> Unit = {},
    onToggleChosen: () -> Unit = {},
) {
    // A firm press when holding starts choosing, a light tick when a tap ticks or unticks (#16).
    // An ordinary tap opens the row and is felt as nothing of its own.
    val haptics = LocalHapticFeedback.current
    // The row gives a little under the finger on today's record (#16); still on a past day's.
    val press = remember { MutableInteractionSource() }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .givesUnderPress(press)
                .combinedClickable(
                    interactionSource = press,
                    indication = LocalIndication.current,
                    onClick = {
                        if (choosing) {
                            haptics.performHapticFeedback(Feel.Tick)
                            onToggleChosen()
                        } else {
                            onEdit(item)
                        }
                    },
                    onLongClick = {
                        haptics.performHapticFeedback(Feel.Thump)
                        onBeginChoosing()
                    },
                )
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
        ) {
            if (choosing) {
                // Named for a screen reader: a tick is the whole of what this says, and nothing else
                // on the row says whether it is in the choice or not.
                val tick = stringResource(
                    if (chosen) R.string.day_is_chosen else R.string.day_not_chosen,
                )
                Checkbox(
                    checked = chosen,
                    // The row is the target. Two hit areas doing one thing is two things to get
                    // wrong.
                    onCheckedChange = null,
                    modifier = Modifier.semantics { contentDescription = tick },
                )
            }
            leading?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                // Name and numbers on separate lines, never in one string: a Hebrew name beside a
                // Latin figure gets reordered, and the day's list once showed a calorie count in
                // front of the food it belonged to.
                Text(
                    text = DayTotalsWording.itemName(item),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                val words = portionWords(item)
                Text(
                    text = DayTotalsWording.itemNumbers(item, words),
                    style = MaterialTheme.typography.bodySmall,
                    // "≈" is read as a symbol's name, or not at all: said as "about" (D58 §12.9).
                    modifier = if (DayTotalsWording.amountEstimated(item, words)) {
                        Modifier.semantics {
                            contentDescription = DayTotalsWording.itemNumbersSpoken(item, words)
                        }
                    } else {
                        Modifier
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Two icons, not two words (#14). The words cost the row more width than its figures
            // got. Each says aloud which row it acts on — "Delete Yoghurt", not "Delete" — so that
            // two rows' bins are two different controls to anything reading the screen (public
            // issue #3). Both on the caption step: neither is an error, and red is kept for the two
            // things that are (D48). An IconButton is a 48 dp target whatever its icon's size.
            val name = DayTotalsWording.itemName(item)
            IconButton(onClick = { onEdit(item) }) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.day_edit_item, name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onDelete(item) }) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.day_delete_item, name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
    }
}

/** How wide a meal's time is drawn, and the empty column beside the rows after its first. */
private val TIME_WIDTH = 48.dp

/**
 * When a meal was eaten — "09:30", or "--:--" when that is not known — and the way to change it (D33).
 *
 * Tapping it opens a picker. What a screen reader hears names the meal as well as the time, so two
 * meals at 12:00 are two different controls: the agent walk could not tell apart
 * eight fields that shared four names, and neither can anyone reading the screen rather than
 * seeing it (issue #12).
 */
@Composable
internal fun EatenAt(meal: Meal, onTime: (Meal) -> Unit, enabled: Boolean = true) {
    val zone = ZoneId.systemDefault()
    val time = DayTotalsWording.eatenAt(meal, zone)
    val said = DayTotalsWording.eatenAtDescription(meal, zone)
    Box(
        // The usual 48 of touch, however small the figure drawn in it: a near-miss otherwise lands
        // on the row, which opens the row's editor instead of the time. While rows are being chosen
        // for a meal the time is not a separate control at all, so a tap there ticks the row like
        // anywhere else.
        modifier = Modifier
            .width(TIME_WIDTH)
            .heightIn(min = 48.dp)
            .then(
                if (enabled) {
                    Modifier
                        .clickable(role = Role.Button) { onTime(meal) }
                        .semantics { contentDescription = said }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = time ?: stringResource(R.string.day_time_unknown),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            // One line whatever the phone's font size: "09:30" wrapping inside its column would read
            // as two figures.
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The time's column, empty: the rows after a meal's first keep their names in line with it. */
@Composable
private fun TimeColumn() {
    Spacer(modifier = Modifier.width(TIME_WIDTH))
}

/**
 * Setting when a meal was eaten: hours and minutes, typed (D33).
 *
 * `TimeInput` rather than the clock face, because he knows the time he means and a typed time is
 * one action where a dial is two. It opens on the meal's own time, or on noon when that is not
 * known — the case this exists for, something written down later onto an earlier day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EatenAtDialog(
    meal: Meal,
    onSave: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val known = DayTotalsWording.eatenAt(meal, zone) != null
    val at = Instant.ofEpochMilli(meal.loggedAtMillis).atZone(zone)
    val picker = rememberTimePickerState(
        initialHour = if (known) at.hour else NOON,
        initialMinute = if (known) at.minute else 0,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.day_time_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                Text(
                    text = meal.title ?: DayTotalsWording.itemName(meal.items.first()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TimeInput(state = picker)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(picker.hour, picker.minute) }) {
                Text(stringResource(R.string.day_time_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.day_time_cancel)) }
        },
    )
}

private const val NOON = 12
