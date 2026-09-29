package com.metaself.app.data.letter

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.letter.LetterSetup.Asks
import org.junit.jupiter.api.Test

/** When the one-time note that sets the weekly letter up is shown, and what its Set up asks for (D99, D103). */
class LetterSetupTest {

    private fun asks(
        letterOn: Boolean = true,
        done: Boolean = false,
        notificationsNeeded: Boolean = true,
        notificationsAllowed: Boolean = false,
        backgroundOffered: Boolean = true,
        backgroundGranted: Boolean = false,
    ) = LetterSetup.asks(letterOn, done, notificationsNeeded, notificationsAllowed, backgroundOffered, backgroundGranted)

    @Test
    fun `with neither allowed, both are asked`() {
        assertThat(asks()).isEqualTo(Asks(notifications = true, background = true))
    }

    @Test
    fun `each ask stands alone when the other is settled or not offered`() {
        assertThat(asks(notificationsAllowed = true)).isEqualTo(Asks(notifications = false, background = true))
        assertThat(asks(notificationsNeeded = false)).isEqualTo(Asks(notifications = false, background = true))
        assertThat(asks(backgroundGranted = true)).isEqualTo(Asks(notifications = true, background = false))
        assertThat(asks(backgroundOffered = false)).isEqualTo(Asks(notifications = true, background = false))
    }

    @Test
    fun `quiet once both are settled`() {
        assertThat(asks(notificationsAllowed = true, backgroundGranted = true)).isNull()
        assertThat(asks(notificationsNeeded = false, backgroundOffered = false)).isNull()
    }

    @Test
    fun `quiet once put away or gone through, and while the letter is off`() {
        assertThat(asks(done = true)).isNull()
        assertThat(asks(letterOn = false)).isNull()
    }
}
