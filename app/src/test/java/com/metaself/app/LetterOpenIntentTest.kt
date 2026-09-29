package com.metaself.app

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.letter.LETTER_MONDAY
import com.metaself.app.data.letter.LetterNotifications
import com.metaself.app.data.letter.LetterOpen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Which launches a weekly letter notification started (D103, design question 16). JUnit 4: Robolectric,
 * for Android's own Intent. `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class LetterOpenIntentTest {

    @Test
    fun `the arrival's tap opens its letter`() {
        val tap = Intent().putExtra(LetterNotifications.EXTRA_WEEK, LETTER_MONDAY)

        assertThat(letterOpenOf(tap)).isEqualTo(LetterOpen.Letter(LETTER_MONDAY))
    }

    @Test
    fun `the failure's tap opens Weekly letters`() {
        val tap = Intent().putExtra(LetterNotifications.EXTRA_LIST, true)

        assertThat(letterOpenOf(tap)).isEqualTo(LetterOpen.List)
    }

    @Test
    fun `an ordinary launch opens neither`() {
        assertThat(letterOpenOf(Intent(Intent.ACTION_MAIN))).isNull()
        assertThat(letterOpenOf(null)).isNull()
    }

    @Test
    fun `the same tap replayed by a launch from Recents opens nothing again`() {
        val fromRecents = Intent().putExtra(LetterNotifications.EXTRA_WEEK, LETTER_MONDAY)
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertThat(letterOpenOf(fromRecents)).isNull()
    }
}
