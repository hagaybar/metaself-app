package com.metaself.app.ui.day

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The day, as one circle.
 *
 * **No screen draws this any more.** D49 turned the day into a page and replaced the ring with a
 * 2 dp [ProportionRule]; the only thing that renders `CalorieRing` now is its own test. It is kept
 * rather than deleted while the new page is confirmed on the phone.
 *
 * The screen exists to answer one question — how much is left — and until now that answer was a line
 * of text the same size as every other line of text. A ring turns it into something read in half a
 * second.
 *
 * It reports and does not cheer: it shows a proportion, and says nothing about whether the
 * proportion is good. Over target it fills completely and changes shade, which is the same thing
 * the number already says, in the same tone.
 */
@Composable
fun CalorieRing(
    fractionEaten: Float,
    headline: String,
    caption: String,
    overTarget: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    // Over target is not an error, so it is not the error colour. Red in this app means a refusal or
    // a typo in a field — something the owner has to go and put right. Eating 180 calories more than
    // the target is neither: it is a fact about the day, and dressing it as a validation failure
    // made an ordinary Tuesday read like something broke. So it is the second colour of the
    // palette, the muted green-grey.
    //
    // **This comment used to say the opposite and is corrected here.** It said `secondary` had 91
    // uses across the app, most of them caption text, and concluded the hue announced nothing
    // because the eye had already seen it everywhere. That was true when it was written and is not
    // true now: D49's ink sweep moved every one of those captions onto the ladder, and `secondary`
    // is down to THREE uses in the whole of `app/src/main` — this arc, the macro rule's over-target
    // fill in `MacroBar.kt`, and the dot on the weight trend. All three are fills; none is a word.
    // So the reasoning inverts. The hue is now rare enough to read as a signal rather than as
    // background, which is better than what was argued for here, not worse. The full ring still
    // carries the message — a closed circle where every other day leaves a gap — and the colour now
    // genuinely reinforces it instead of merely failing to contradict it. The twin comment in
    // `MacroBar.kt` was corrected when the sweep ran; this one was missed.
    //
    // **Nothing on the day screen draws this composable any more.** D49 replaced the ring with a
    // 2 dp `ProportionRule`, so `CalorieRing` is referenced by `CalorieRingRenderTest` and by no
    // screen. The over-target colour below is therefore on nobody's screen at present; it is
    // kept, tested and honest so that the file does not rot before anyone decides its fate.
    val filled = if (overTarget) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Box(modifier = modifier.size(RING_SIZE), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(RING_SIZE)) {
            val stroke = STROKE.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)

            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            if (fractionEaten > 0f) {
                drawArc(
                    color = filled,
                    // From the top, clockwise: the direction a clock runs and a day passes.
                    startAngle = -90f,
                    sweepAngle = 360f * fractionEaten,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                // The headline is ink whether or not the day went over. It used to turn red, which
                // told the owner off in the colour this app otherwise reserves for a refusal or a
                // bad field — three meanings for one colour, and going over a target is the least
                // alarming of the three. The sentence still says "over"; only the accusation
                // is gone. The branch stays rather than collapsing to nothing because the two cases
                // are not the same request: over target asks for the page's ink explicitly, while
                // the ordinary case keeps inheriting whatever content colour it is drawn inside.
                color = if (overTarget) MaterialTheme.colorScheme.onBackground else Color.Unspecified,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val RING_SIZE = 200.dp
private val STROKE = 14.dp
