package com.metaself.app.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * A hairline filled to a proportion — what the ring used to say, in a hundredth of its height (D49).
 *
 * The day's ring was 200 dp of circle around a headline that wrapped over its own stroke — measured
 * from `CalorieRing.RING_SIZE`, not remembered — so 2 dp of rule is a hundredth of it. The one
 * thing it carried that words did not is the proportion, and a rule carries that at 2 dp. The
 * macros beneath it carry theirs at 1, which is what ranks them below the day's own answer without
 * a second colour or a second size.
 *
 * Drawn as two boxes rather than a progress indicator: a progress indicator announces itself to a
 * screen reader as a bare percentage with no label, and at two of this rule's three call sites the
 * figure beside it has already said the same thing in words — the day's own rule sits under
 * "1,240 kcal left of 2,090", and the step rule under "3,100 steps" and "your usual 5,200".
 *
 * **[MacroBar] is the exception, and it is a small loss rather than none.** A macro's figure says
 * only what is left — "149 g" — and its target is printed nowhere on the day screen, so the
 * proportion eaten is not recoverable from any word a screen reader can reach. What was there
 * before was an unlabelled percentage from a progress indicator, which named neither the macro nor
 * the target, so what is gone is small; but it is gone, and this comment says so rather than
 * claiming the words always cover it.
 *
 * The track is [MaterialTheme.colorScheme.outlineVariant], the hairline step of the ink
 * ladder, which is the one colour in the palette that exists for exactly this and is never text.
 *
 * [fraction] is clamped, so a day 40% past its target draws a full rule rather than one that has
 * run off the page. That the day went over is said by the words above, not twice by the rule.
 */
@Composable
internal fun ProportionRule(
    fraction: Float,
    thickness: Dp,
    fill: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        val filled = fraction.coerceIn(0f, 1f)
        if (filled > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(filled)
                    .height(thickness)
                    .background(fill),
            )
        }
    }
}
