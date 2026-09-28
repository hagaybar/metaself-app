package com.metaself.app

import android.content.Intent
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.inlineWorkoutText
import com.metaself.app.domain.movement.TcxRead
import com.metaself.app.domain.movement.TcxReader
import com.metaself.app.domain.movement.WorkoutFileRefusal
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Which launches carry a workout file shared to MetaSelf (D82), and — for a share with no attached
 * file — the inline text it carries instead. JUnit 4: Robolectric, for Android's own Intent.
 * `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class SharedWorkoutFileIntentTest {

    private fun share(type: String = "application/vnd.garmin.tcx+xml"): Intent = Intent(Intent.ACTION_SEND)
        .setType(type)
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(FILE))

    private fun textShare(type: String, text: String): Intent = Intent(Intent.ACTION_SEND)
        .setType(type)
        .putExtra(Intent.EXTRA_TEXT, text)

    @Test
    fun `a share carrying a file is that file`() {
        assertThat(sharedWorkoutFile(share())).isEqualTo(FILE)
    }

    @Test
    fun `an attached file shared as text slash plain is still that file`() {
        assertThat(sharedWorkoutFile(share(type = "text/plain"))).isEqualTo(FILE)
    }

    @Test
    fun `a text slash plain share with no attached file carries its text, wrapped for the file reader`() {
        val tcx = tcx(metres = 3_250, steps = 4_200)

        assertThat(sharedWorkoutFile(textShare("text/plain", tcx))).isEqualTo(inlineWorkoutText(tcx))
        // The wrapped text is genuinely a readable workout, not just any string.
        assertThat(TcxReader.read(tcx)).isInstanceOf(TcxRead.Read::class.java)
    }

    @Test
    fun `a text slash plain share of ordinary prose still carries the text, for the reader to refuse`() {
        val prose = "pick up milk on the way home"

        assertThat(sharedWorkoutFile(textShare("text/plain", prose))).isEqualTo(inlineWorkoutText(prose))
        // Extraction does not judge content; TcxReader does, with the existing plain-word refusal.
        assertThat(TcxReader.read(prose)).isEqualTo(TcxRead.Refused(WorkoutFileRefusal.NOT_XML))
    }

    @Test
    fun `the same share replayed by a launch from Recents is not shared again`() {
        val fromRecents = share().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertThat(sharedWorkoutFile(fromRecents)).isNull()
    }

    @Test
    fun `a plain launch, and a share with no file and no text, carry none`() {
        assertThat(sharedWorkoutFile(Intent(Intent.ACTION_MAIN))).isNull()
        assertThat(sharedWorkoutFile(Intent(Intent.ACTION_SEND))).isNull()
        assertThat(sharedWorkoutFile(null)).isNull()
    }

    /** An invented, minimal TCX file: one lap, the figures given. */
    private fun tcx(metres: Int, steps: Int) = """<?xml version="1.0" encoding="UTF-8"?>
        <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
          <Activities>
            <Activity Sport="Running">
              <Id>2026-09-03T10:00:00Z</Id>
              <Lap StartTime="2026-09-03T10:00:00Z">
                <TotalTimeSeconds>1800</TotalTimeSeconds>
                <DistanceMeters>$metres</DistanceMeters>
                <Steps>$steps</Steps>
              </Lap>
            </Activity>
          </Activities>
        </TrainingCenterDatabase>"""

    private companion object {
        const val FILE = "content://example/a.tcx"
    }
}
