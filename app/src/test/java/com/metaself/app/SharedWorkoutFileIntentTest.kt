package com.metaself.app

import android.content.Intent
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Which launches carry a workout file shared to MetaSelf (D82). JUnit 4: Robolectric, for Android's
 * own Intent. `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class SharedWorkoutFileIntentTest {

    private fun share(): Intent = Intent(Intent.ACTION_SEND)
        .setType("application/vnd.garmin.tcx+xml")
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(FILE))

    @Test
    fun `a share carrying a file is that file`() {
        assertThat(sharedWorkoutFile(share())).isEqualTo(FILE)
    }

    @Test
    fun `the same share replayed by a launch from Recents is not shared again`() {
        val fromRecents = share().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertThat(sharedWorkoutFile(fromRecents)).isNull()
    }

    @Test
    fun `a plain launch, and a share with no file, carry none`() {
        assertThat(sharedWorkoutFile(Intent(Intent.ACTION_MAIN))).isNull()
        assertThat(sharedWorkoutFile(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "words"))).isNull()
        assertThat(sharedWorkoutFile(null)).isNull()
    }

    private companion object {
        const val FILE = "content://example/a.tcx"
    }
}
