package com.metaself.app.domain.window

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The ratio itself: how it is read, and what it refuses to be.
 *
 * **This file used to prove a great deal more.** It held the calendar-day rule — `WindowRules.
 * measure()`, which took the first and last timed minute between midnight and 23:59 and asked
 * whether that span fitted the eating half. That rule was removed on 2026-09-17, not amended: a
 * ratio is a statement about hours, so the measured window judges eating stretches bounded by the
 * fast instead, and midnight has nothing to do with it (design §2, §3). Its tests went with it
 * rather than being reworded, because a test of a rule that no longer exists is a claim about the
 * app that is no longer true. What replaced them is `EatingStretchTest` and the stretch half of
 * `WindowRulesTest`.
 *
 * What stayed is what was never about days: the reading of the ratio, and the one thing it insists
 * on. Both are pinned in the domain rather than only on the settings screen, because reading the
 * first number as the eating hours would silently mean the opposite of what the owner typed.
 */
class MeasuredWindowTest {

    /** Sixteen hours fasting, eight eating — the ratio is written fasting first. */
    private val sixteenEight = MeasuredWindow(fastingHours = 16)

    /**
     * "16/8" means sixteen hours fasting everywhere else in the world, and if the app reads the
     * first number as the eating hours it silently means the opposite of what the owner typed.
     */
    @Test
    fun `sixteen eight is sixteen hours fasting and eight eating`() {
        assertThat(sixteenEight.fastingHours).isEqualTo(16)
        assertThat(sixteenEight.eatingHours).isEqualTo(8)
        assertThat(MeasuredWindow(fastingHours = 12).eatingHours).isEqualTo(12)
        assertThat(MeasuredWindow(fastingHours = 20).eatingHours).isEqualTo(4)
    }

    @Test
    fun `a ratio must leave some of the day for each half`() {
        assertThrows<IllegalArgumentException> { MeasuredWindow(fastingHours = 0) }
        assertThrows<IllegalArgumentException> { MeasuredWindow(fastingHours = 24) }

        assertThat(MeasuredWindow(fastingHours = 1).eatingHours).isEqualTo(23)
        assertThat(MeasuredWindow(fastingHours = 23).eatingHours).isEqualTo(1)
    }
}
