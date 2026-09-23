package com.metaself.app.ui.screen.day

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.DayRange
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import org.junit.jupiter.api.Test

/**
 * JUnit 5 and deliberately small.
 *
 * Driving a real pager under Robolectric proves little that `DayRangeTest` does not already prove
 * about the arithmetic, and costs a slow and flaky test. What is worth pinning is that the pager is
 * built on the same range the arithmetic uses, and opens on today. Whether the SWIPE feels right is
 * not something any test here can answer — that is the owner's, on a phone.
 */
class DayPagerRenderTest {

    @Test
    fun `the pager opens on today`() {
        val range = DayRange(today = TEST_EPOCH_DAY)
        assertThat(range.dayAt(range.todayPage)).isEqualTo(TEST_EPOCH_DAY)
    }

    @Test
    fun `a swipe of one page is a day`() {
        val range = DayRange(today = TEST_EPOCH_DAY)
        assertThat(range.dayAt(range.todayPage + 1) - range.dayAt(range.todayPage)).isEqualTo(1)
    }
}
