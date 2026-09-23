package com.metaself.app.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.profile.Profile
import com.metaself.app.domain.target.DailyTarget
import com.metaself.app.domain.target.MeasuredBurn
import com.metaself.app.ui.target.BurnWording
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.VersionMarker
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.target.ExplanationLine
import com.metaself.app.ui.target.TargetWording

/**
 * The daily target, and the arithmetic that produced it.
 *
 * Every step is on the same screen as the answer rather than behind a "why?" link, because decision
 * D9's point is that a number you cannot argue with is a number you end up ignoring. There is
 * nothing else here yet: the day's log arrives in step 3.
 */
@Composable
fun HomeScreen(
    profile: Profile,
    target: DailyTarget,
    versionName: String,
    versionCode: Int,
    weightUsedLine: String,
    measuredBurn: MeasuredBurn?,
    burnAdjustmentKcal: Int,
    daysLoggedRecently: Int,
    onEdit: () -> Unit,
    onAllowBelowFloor: () -> Unit,
    onForgetBurnAdjustment: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showingWorking by rememberSaveable { mutableStateOf(false) }

    MetaSelfScreen(
        title = stringResource(R.string.today_profile),
        modifier = modifier,
        onBack = onBack,
    ) {
        // Off by default. The numbers are what he came for; the arithmetic behind them matters when
        // one of them looks wrong, which is not most of the time.
        TextButton(
            onClick = { showingWorking = !showingWorking },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        ) {
            Text(
                stringResource(
                    if (showingWorking) R.string.home_hide_working else R.string.home_show_working,
                ),
            )
        }

        // Which weight the arithmetic below was actually built on. Without this the target and the
        // weight on the profile can disagree with nothing to explain why.
        Text(
            text = weightUsedLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Section(title = stringResource(R.string.home_arithmetic_title)) {
            TargetWording.arithmetic(target, profile).forEach { Explanation(it, showingWorking) }
        }

        // What the formula predicted, against what actually happened (D25). Shown here because this
        // is the screen that already explains where the target came from.
        Section(title = stringResource(R.string.home_measured_title)) {
            BurnWording.lines(
                measured = measuredBurn,
                standingKcal = burnAdjustmentKcal,
                daysLoggedRecently = daysLoggedRecently,
            ).forEach { Explanation(it, showingWorking) }

            // The correction can walk a long way over weeks. Nothing else puts it back.
            if (burnAdjustmentKcal != 0) {
                TextButton(onClick = onForgetBurnAdjustment) {
                    Text(stringResource(R.string.home_forget_correction))
                }
            }
        }

        if (target.floorApplied) {
            TextButton(onClick = onAllowBelowFloor) {
                Text(stringResource(R.string.home_floor_override))
            }
        }

        Section(title = stringResource(R.string.home_macros_title)) {
            TargetWording.macros(target).forEach { Explanation(it, showingWorking) }
        }

        Button(onClick = onEdit) { Text(stringResource(R.string.home_edit)) }

        Text(
            text = VersionMarker.line(versionName, versionCode),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Related),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Explanation(line: ExplanationLine, showDetail: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
        ) {
            Text(text = line.heading, style = MaterialTheme.typography.titleSmall)
            // The heading is the number; the detail is why. The why is worth having and is not
            // worth reading every time, so it waits behind the button at the top of the screen.
            if (showDetail) {
                Text(text = line.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
