package com.metaself.app.ui.screen.propose

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Where he lands after describing a meal (D58 §5.3, §12.8). Pure: JUnit 5. */
class DescribeLandingTest {

    @Test
    fun `logging lands on the day, whichever way he came in`() {
        assertThat(DescribeLanding.after(DescribedFrom.ADD_SOMETHING, EndChoice.LOG)).isEqualTo(Landing.BACK_TO_DAY)
        assertThat(DescribeLanding.after(DescribedFrom.ADD_SOMETHING, EndChoice.BOTH)).isEqualTo(Landing.BACK_TO_DAY)
        assertThat(DescribeLanding.after(DescribedFrom.MY_MEALS, EndChoice.LOG))
            .isEqualTo(Landing.BACK_PAST_MY_MEALS_TO_DAY)
        assertThat(DescribeLanding.after(DescribedFrom.MY_MEALS, EndChoice.BOTH))
            .isEqualTo(Landing.BACK_PAST_MY_MEALS_TO_DAY)
    }

    @Test
    fun `keeping without logging lands on My meals, whichever way he came in`() {
        assertThat(DescribeLanding.after(DescribedFrom.ADD_SOMETHING, EndChoice.KEEP)).isEqualTo(Landing.ON_TO_MY_MEALS)
        assertThat(DescribeLanding.after(DescribedFrom.MY_MEALS, EndChoice.KEEP)).isEqualTo(Landing.BACK_TO_MY_MEALS)
    }

    @Test
    fun `from My meals logging goes on today, from Add something on the day being looked at`() {
        assertThat(DescribeLanding.logsOnToday(DescribedFrom.MY_MEALS)).isTrue()
        assertThat(DescribeLanding.logsOnToday(DescribedFrom.ADD_SOMETHING)).isFalse()
    }

    @Test
    fun `the route's argument says which way he came in, and anything else is Add something`() {
        assertThat(DescribeLanding.fromArgument("meals")).isEqualTo(DescribedFrom.MY_MEALS)
        assertThat(DescribeLanding.fromArgument(null)).isEqualTo(DescribedFrom.ADD_SOMETHING)
        assertThat(DescribeLanding.fromArgument("anything")).isEqualTo(DescribedFrom.ADD_SOMETHING)
    }
}
