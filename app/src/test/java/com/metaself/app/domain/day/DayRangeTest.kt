package com.metaself.app.domain.day

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DayRangeTest {

    private val range = DayRange(today = TEST_EPOCH_DAY)

    @Test
    fun `today sits at the anchor page`() {
        assertThat(range.pageOf(TEST_EPOCH_DAY)).isEqualTo(range.todayPage)
    }

    @Test
    fun `the page after today is tomorrow`() {
        assertThat(range.dayAt(range.todayPage + 1)).isEqualTo(TEST_EPOCH_DAY + 1)
    }

    @Test
    fun `the page before today is yesterday`() {
        assertThat(range.dayAt(range.todayPage - 1)).isEqualTo(TEST_EPOCH_DAY - 1)
    }

    @Test
    fun `a page and a day are the same thing counted from different places`() {
        val someDay = TEST_EPOCH_DAY - 47
        assertThat(range.dayAt(range.pageOf(someDay))).isEqualTo(someDay)
    }

    @Test
    fun `the range reaches five years back and one year forward`() {
        assertThat(range.dayAt(0)).isEqualTo(TEST_EPOCH_DAY - DayRange.DAYS_BACK)
        assertThat(range.dayAt(range.pageCount - 1)).isEqualTo(TEST_EPOCH_DAY + DayRange.DAYS_FORWARD)
    }

    @Test
    fun `a day outside the range is clamped to its edge rather than crashing the pager`() {
        val farPast = TEST_EPOCH_DAY - 10_000
        assertThat(range.pageOf(farPast)).isEqualTo(0)

        val farFuture = TEST_EPOCH_DAY + 10_000
        assertThat(range.pageOf(farFuture)).isEqualTo(range.pageCount - 1)
    }

    @Test
    fun `every page holds a day and every day in range holds a page`() {
        assertThat(range.pageCount).isEqualTo(DayRange.DAYS_BACK + DayRange.DAYS_FORWARD + 1)
    }
}
