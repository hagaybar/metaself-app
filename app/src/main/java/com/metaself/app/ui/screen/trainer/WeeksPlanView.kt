package com.metaself.app.ui.screen.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.metaself.app.R
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.PlanProgress
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.ProgrammeWording
import com.metaself.app.ui.trainer.TrainerWording

/**
 * D4: "From the AI trainer · advice, not a measurement". Drawn once, by each page, above everything the
 * trainer wrote — the evaluation as well as the plan — never inside [WeeksPlanView], which comes second.
 */
@Composable
internal fun FromTrainerLabel() {
    Text(
        TrainerWording.FROM_TRAINER,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** D94: where you stand — the headline and its parts; "since last time" only when there is one. */
@Composable
internal fun EvaluationView(evaluation: Evaluation) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        Text(
            stringResource(R.string.weeks_where),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(evaluation.headline, style = MaterialTheme.typography.titleLarge)
        LabelledPart(R.string.weeks_going_well, evaluation.goingWell)
        LabelledPart(R.string.weeks_to_work_on, evaluation.toWorkOn)
        if (evaluation.sinceLast.isNotBlank()) LabelledPart(R.string.weeks_since_last, evaluation.sinceLast)
    }
}

@Composable
private fun LabelledPart(label: Int, text: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * D94, D95: a plan's heading, dates, weeks and why, under a [FromTrainerLabel] its caller draws first. With [progress], each session says whether it is done
 * (the running plan); without, it says what it is (an answer not yet kept).
 */
@Composable
internal fun WeeksPlanView(plan: WeeksPlan, ask: ProgrammeAsk, start: Long, progress: PlanProgress?) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        Text(
            ProgrammeWording.planHeading(ask),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(plan.title, style = MaterialTheme.typography.titleLarge)
        Text(ProgrammeWording.span(start, ask.weeks), style = MaterialTheme.typography.bodyMedium)
        plan.weeks.forEachIndexed { index, week ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    Text(ProgrammeWording.weekTitle(index + 1, week.focus), style = MaterialTheme.typography.titleSmall)
                    val ticks = progress?.weeks?.getOrNull(index)?.ticks
                    week.sessions.forEachIndexed { at, session ->
                        val tick = ticks?.getOrNull(at)
                        Text(
                            if (tick != null) ProgrammeWording.tickTitle(tick) else ProgrammeWording.plannedTitle(session),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            tick?.by?.let(ProgrammeWording::tickLine) ?: session.what,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Column {
            Text(stringResource(R.string.weeks_why), style = MaterialTheme.typography.titleSmall)
            Text(plan.why, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
