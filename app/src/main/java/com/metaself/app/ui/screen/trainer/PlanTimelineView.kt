package com.metaself.app.ui.screen.trainer

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.trainer.DatedEvaluation
import com.metaself.app.domain.trainer.MainAction
import com.metaself.app.domain.trainer.PlanShown
import com.metaself.app.domain.trainer.PlanTile
import com.metaself.app.domain.trainer.PlanTimeline
import com.metaself.app.domain.trainer.TileState
import com.metaself.app.domain.trainer.TimelinePlace
import com.metaself.app.domain.trainer.TimelineWeek
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.PlanTimelineWording
import com.metaself.app.ui.trainer.TrainerWeekWording
import com.metaself.app.ui.trainer.TrainerWording

/**
 * D4: "From the AI trainer · advice, not a measurement". Drawn once, by each page, above everything the
 * trainer wrote — the evaluation as well as the plan — never inside [PlanShownView], which comes second.
 */
@Composable
internal fun FromTrainerLabel() {
    Text(
        TrainerWording.FROM_TRAINER,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The timeline's node column; the gap between it and the week. */
private val NODE = 28.dp
private val NODE_GAP = 14.dp

/** Below a week's text, before the next week's node; as the approved mockup. */
private val WEEK_GAP = 18.dp
private const val TILES_PER_ROW = 3

/**
 * D117 items 1–3, D118: the plan's title in the display face and where the owner is in it, the timeline, and
 * where he stood — the evaluation's headline, its full text folded under "Read the trainer's full evaluation",
 * closed by default; neither when there is no evaluation. Drawn into the caller's column, after its
 * [FromTrainerLabel] and before its buttons.
 */
@Composable
internal fun PlanShownView(shown: PlanShown) {
    val timeline = shown.timeline
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(timeline.title, style = MaterialTheme.typography.headlineMedium)
        Text(PlanTimelineWording.subtitle(timeline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Timeline(timeline)
    shown.evaluation?.let { Stood(it, timeline.why) }
}

/** D117 item 2: one row a week — its node and the line to the next, then its heading and its line or tiles. */
@Composable
private fun Timeline(timeline: PlanTimeline) {
    Column(Modifier.fillMaxWidth()) {
        timeline.weeks.forEachIndexed { index, week ->
            val last = index == timeline.weeks.lastIndex
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(NODE_GAP)) {
                Column(Modifier.width(NODE).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Node(week.place)
                    if (!last) {
                        val line = if (week.place is TimelinePlace.Past) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        Box(Modifier.width(2.dp).weight(1f).background(line))
                    }
                }
                Column(
                    Modifier.weight(1f).padding(bottom = if (last) 0.dp else WEEK_GAP),
                    verticalArrangement = Arrangement.spacedBy(if (week.place is TimelinePlace.ThisWeek) 10.dp else 2.dp),
                ) {
                    WeekHeading(week)
                    when (val place = week.place) {
                        is TimelinePlace.Past -> WeekLine(PlanTimelineWording.pastLine(place))
                        is TimelinePlace.ThisWeek -> Tiles(place.tiles)
                        is TimelinePlace.Ahead -> WeekLine(PlanTimelineWording.aheadLine(place.sessions))
                    }
                }
            }
        }
    }
}

/** A past week: filled, with a tick; this week: a ring; a week ahead: a small grey dot. */
@Composable
private fun Node(place: TimelinePlace) {
    val green = MaterialTheme.colorScheme.primary
    when (place) {
        is TimelinePlace.Past -> Box(Modifier.size(NODE).background(green, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
        is TimelinePlace.ThisWeek -> Box(
            Modifier.size(NODE).background(MaterialTheme.colorScheme.background, CircleShape).border(3.dp, green, CircleShape),
        )
        is TimelinePlace.Ahead -> Box(Modifier.padding(4.dp).size(20.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
    }
}

/** "Week 2 · Add a run", then " — this week" in the lighter ink; a week ahead is all in the lighter ink. */
@Composable
private fun WeekHeading(week: TimelineWeek) {
    val lighter = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        buildAnnotatedString {
            append(PlanTimelineWording.weekHeading(week))
            if (week.place is TimelinePlace.ThisWeek) {
                withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = lighter)) { append(PlanTimelineWording.THIS_WEEK) }
            }
        },
        style = MaterialTheme.typography.titleSmall,
        color = if (week.place is TimelinePlace.Ahead) lighter else Color.Unspecified,
    )
}

@Composable
private fun WeekLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** This week's planned sessions, three to a row, as the Trainer page draws planned sessions (D114). */
@Composable
private fun Tiles(tiles: List<PlanTile>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        tiles.chunked(TILES_PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                row.forEach { Tile(it, Modifier.weight(1f)) }
                repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Done (short or not): filled in the dark green, its text `onPrimary` (5.00:1 light, 7.29:1 dark — InkLadderTest).
 * Open: the strip's dashed outline, its text in ink, as the strip's "40′" is.
 */
@Composable
private fun Tile(tile: PlanTile, modifier: Modifier) {
    val open = tile.state == TileState.OPEN
    val shape = RoundedCornerShape(12.dp)
    val surface = if (open) {
        Modifier.dashedOutline(MaterialTheme.colorScheme.primary, corner = 12.dp)
    } else {
        Modifier.background(MaterialTheme.colorScheme.primary, shape)
    }
    val ink = if (open) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
    Column(modifier.then(surface).padding(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(PlanTimelineWording.tileTitle(tile.planned), style = MaterialTheme.typography.titleSmall, color = ink)
        Text(
            PlanTimelineWording.tileLine(tile),
            style = MaterialTheme.typography.bodySmall,
            color = if (open) MaterialTheme.colorScheme.onSurfaceVariant else ink,
        )
    }
}

/**
 * D117 item 3: "WHERE YOU STOOD" and its date, the headline, and the rest folded — going well, to work on,
 * since last time (when there is one) and the plan's why. The fold's words are ink (the green on a card is
 * 3.86:1, under the floor); its arrow carries the green.
 */
@Composable
private fun Stood(dated: DatedEvaluation, why: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    val evaluation = dated.evaluation
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Text(PlanTimelineWording.stood(dated.epochDay), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(evaluation.headline, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { open = !open },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Tight),
            ) {
                Text(
                    if (open) PlanTimelineWording.HIDE_FULL else PlanTimelineWording.READ_FULL,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (open) {
                LabelledPart(R.string.weeks_going_well, evaluation.goingWell)
                LabelledPart(R.string.weeks_to_work_on, evaluation.toWorkOn)
                if (evaluation.sinceLast.isNotBlank()) LabelledPart(R.string.weeks_since_last, evaluation.sinceLast)
                LabelledPart(R.string.weeks_why, why)
            }
        }
    }
}

@Composable
private fun LabelledPart(@StringRes label: Int, text: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * D117 item 4: a plan ended or stopped has one filled "Evaluate me and plan again"; a running one, Adjust the
 * plan (outlined) beside the Trainer page's main action (D114).
 */
@Composable
internal fun PlanButtons(shown: PlanShown, onAdjust: () -> Unit, onPlan: () -> Unit, onEvaluate: () -> Unit) {
    val running = shown.running
    when {
        shown.timeline.over -> Button(onClick = onEvaluate, modifier = Modifier.fillMaxWidth()) { Text(PlanTimelineWording.EVALUATE_AGAIN) }
        running != null -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            OutlinedButton(onClick = onAdjust, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.trainer_adjust_plan), textAlign = TextAlign.Center)
            }
            Button(onClick = onPlan, modifier = Modifier.weight(1f)) {
                val main = MainAction.session(running.next)
                Text(
                    if (main is MainAction.PlanOpen) TrainerWeekWording.planOpen(main.kind) else stringResource(R.string.trainer_plan_next),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
