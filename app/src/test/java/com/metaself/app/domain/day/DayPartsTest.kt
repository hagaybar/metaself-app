package com.metaself.app.domain.day

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The day's loggings, gathered into the four fixed parts of the clock (D51).
 *
 * The rule asked for is one that can be run in the head: the hour a thing was eaten is known, so
 * which row holds it is known — no arithmetic, no dependence on what else he logged that day, and no
 * dependence on the order he logged it in. That is why the gap rule this replaces had to go: its
 * row count was unbounded and locating anything meant remembering every logging's time and
 * computing the gaps between them.
 *
 * **These are parts of the clock, not names for meals.** "Morning" is true by definition of the
 * hour; "Breakfast" would be a claim about what the eating was — an inference printed as a fact,
 * which is D4's defect, and wrong on exactly the days an eating window puts a first meal at eleven.
 * There is no Breakfast, Lunch or Dinner anywhere in this app and this file must not introduce one.
 *
 * **Nothing here is stored.** The grouping is computed from each [Meal]'s `loggedAtMillis` read in
 * the day's zone, so it cannot drift out of step with the record and no migration is owed.
 *
 * **The parts come out in clock order, Night first**, though D51's table lists Night last. Night is
 * 00:00–05:59: on the calendar day the screen is showing, it is chronologically first, and putting
 * it last would be the one row where later reads higher — a 02:00 logging sitting below a 23:00 one.
 * The day's meals are already drawn in time order, and rows running 00:00 to 23:59 keep the rule
 * legible on the screen it is printed on. The single exception is the untimed row, which is last
 * precisely because it has no clock reading to be ordered by.
 */
class DayPartsTest {

    /**
     * A fixed offset rather than a named place, because no test here is about a place.
     *
     * It must NOT be UTC: the last test in this file proves the part is read in the day's own zone
     * rather than the machine's, and it can only prove that by reading one instant in two zones
     * that disagree. Five hours west is far enough to move 13:00 across the Midday/Evening
     * boundary, and a fixed offset has no daylight saving to make the answer depend on the month.
     */
    private val zone: ZoneId = ZoneId.of("UTC-05:00")

    /** The day every meal here is filed under, taken from the shared fixture so the two agree. */
    private val day: LocalDate = LocalDate.ofEpochDay(TEST_EPOCH_DAY)

    private fun at(hour: Int, minute: Int = 0, onDate: LocalDate = day): Long =
        LocalDateTime.of(onDate, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    /**
     * A logging on the day, at a clock reading on that same day — which is what makes its time
     * honest, and so what puts it in a part at all (D33).
     */
    private fun loggedAt(hour: Int, minute: Int = 0, id: Long = 0, name: String = "Porridge"): Meal =
        aMeal(
            id = id,
            epochDay = TEST_EPOCH_DAY,
            loggedAtMillis = at(hour, minute),
            items = listOf(anItem(name = name)),
        )

    /** The part one logging alone lands in — the shape every boundary case below takes. */
    private fun partOf(hour: Int, minute: Int = 0): PartOfTheClock? =
        DayParts.of(listOf(loggedAt(hour, minute)), zone).single().part

    // --- the boundaries, every one of them stated in minutes ------------------------------------
    //
    // Written a minute either side rather than by the hour because an off-by-one here is invisible
    // in a screenshot and wrong for six hours a day: it would move the last logging of one part
    // into the next one, on exactly the entries whose time the owner is most likely to be checking.

    @Test
    fun `five fifty-nine is Night and six o'clock begins Morning`() {
        assertThat(partOf(5, 59)).isEqualTo(PartOfTheClock.NIGHT)
        assertThat(partOf(6, 0)).isEqualTo(PartOfTheClock.MORNING)
    }

    @Test
    fun `eleven fifty-nine is Morning and noon begins Midday`() {
        assertThat(partOf(11, 59)).isEqualTo(PartOfTheClock.MORNING)
        assertThat(partOf(12, 0)).isEqualTo(PartOfTheClock.MIDDAY)
    }

    @Test
    fun `five fifty-nine in the afternoon is Midday and six begins Evening`() {
        assertThat(partOf(17, 59)).isEqualTo(PartOfTheClock.MIDDAY)
        assertThat(partOf(18, 0)).isEqualTo(PartOfTheClock.EVENING)
    }

    @Test
    fun `the last minute of the day is Evening and midnight begins Night`() {
        assertThat(partOf(23, 59)).isEqualTo(PartOfTheClock.EVENING)
        assertThat(partOf(0, 0)).isEqualTo(PartOfTheClock.NIGHT)
    }

    /**
     * The four bands as D51 prints them, and that they tile the clock exactly.
     *
     * The hours are on the row itself — "Midday · 12:00–17:59" — so the rule is on the screen
     * rather than buried in the code, which means these numbers are shown to the owner and are not
     * an implementation detail. The tiling assertion is what makes "every logging falls into
     * exactly one" true by construction rather than by inspection: a gap would drop a logging
     * nowhere, an overlap would put one in two places.
     */
    @Test
    fun `the four bands are the ones D51 prints, and they tile the clock with no gap or overlap`() {
        assertThat(PartOfTheClock.NIGHT.firstHour).isEqualTo(0)
        assertThat(PartOfTheClock.NIGHT.lastHour).isEqualTo(5)
        assertThat(PartOfTheClock.MORNING.firstHour).isEqualTo(6)
        assertThat(PartOfTheClock.MORNING.lastHour).isEqualTo(11)
        assertThat(PartOfTheClock.MIDDAY.firstHour).isEqualTo(12)
        assertThat(PartOfTheClock.MIDDAY.lastHour).isEqualTo(17)
        assertThat(PartOfTheClock.EVENING.firstHour).isEqualTo(18)
        assertThat(PartOfTheClock.EVENING.lastHour).isEqualTo(23)

        val bands = PartOfTheClock.entries
        assertThat(bands.first().firstHour).isEqualTo(0)
        assertThat(bands.last().lastHour).isEqualTo(23)
        bands.zipWithNext { earlier, later ->
            assertThat(later.firstHour).isEqualTo(earlier.lastHour + 1)
        }
    }

    // --- what the list of rows is ---------------------------------------------------------------

    @Test
    fun `a day with nothing logged has no rows at all`() {
        assertThat(DayParts.of(emptyList(), zone)).isEmpty()
    }

    /**
     * Several loggings in one part stay one row, in time order.
     *
     * Fed out of order on purpose: the order must be computed from the clock, not inherited from
     * whatever order the store happened to hand over, or the row would re-order itself the next
     * time a query changed.
     */
    @Test
    fun `loggings in one part share its row, in time order`() {
        val parts = DayParts.of(
            listOf(
                loggedAt(hour = 13, minute = 30, id = 2, name = "Hummus"),
                loggedAt(hour = 12, minute = 5, id = 1, name = "Shawarma"),
                loggedAt(hour = 17, minute = 59, id = 3, name = "Apple"),
            ),
            zone,
        )

        assertThat(parts).hasSize(1)
        assertThat(parts.single().part).isEqualTo(PartOfTheClock.MIDDAY)
        assertThat(parts.single().meals.map { it.id }).containsExactly(1L, 2L, 3L).inOrder()
    }

    /**
     * A part with nothing in it is not drawn — absent, never present and empty.
     *
     * An empty row would be a row the owner has to read and discard, and four of them on a quiet
     * day is the dashboard D49 just finished dismantling. It is also why an ordinary day is two or
     * three rows rather than always four.
     */
    @Test
    fun `a part with nothing in it is absent rather than empty`() {
        val parts = DayParts.of(listOf(loggedAt(hour = 8), loggedAt(hour = 20)), zone)

        assertThat(parts.map { it.part })
            .containsExactly(PartOfTheClock.MORNING, PartOfTheClock.EVENING)
            .inOrder()
        assertThat(parts.none { it.meals.isEmpty() }).isTrue()
    }

    /**
     * A day that reaches all four parts gives four rows, in clock order, Night first.
     *
     * See this class's own note for why Night leads rather than trails: on the calendar day being
     * shown, 00:00–05:59 is the earliest thing on it, and every other row is ordered by the clock.
     */
    @Test
    fun `a day filling all four parts gives four rows in clock order, Night first`() {
        val parts = DayParts.of(
            listOf(
                loggedAt(hour = 19, id = 4),
                loggedAt(hour = 8, id = 2),
                loggedAt(hour = 2, id = 1),
                loggedAt(hour = 14, id = 3),
            ),
            zone,
        )

        assertThat(parts.map { it.part }).containsExactly(
            PartOfTheClock.NIGHT,
            PartOfTheClock.MORNING,
            PartOfTheClock.MIDDAY,
            PartOfTheClock.EVENING,
        ).inOrder()
        assertThat(parts.flatMap { group -> group.meals.map { it.id } })
            .containsExactly(1L, 2L, 3L, 4L)
            .inOrder()
    }

    // --- the one deliberate exception to "never more than four" ---------------------------------

    /**
     * A logging written down on another day belongs to no part of the clock, and gets its own row.
     *
     * Its stored moment is the moment of WRITING, on another date, so the clock reading it carries
     * is not a time on this day at all — the same date-against-day test `DayTotalsWording.eatenAt`
     * and the eating window already use. Filed by that stored hour it would land in a part it may
     * have nothing to do with: this one was written at 08:00 the next morning, which would read as
     * Morning, while when it was actually eaten is not known. Hiding it there would state a time
     * it was not eaten at, which is precisely what D33 refused.
     */
    @Test
    fun `a logging with no honest time belongs to no part and gets a row of its own`() {
        val writtenUp = aMeal(
            id = 9,
            epochDay = TEST_EPOCH_DAY,
            loggedAtMillis = at(hour = 8, minute = 5, onDate = day.plusDays(1)),
        )

        val parts = DayParts.of(listOf(writtenUp), zone)

        assertThat(parts).hasSize(1)
        assertThat(parts.single().part).isNull()
        assertThat(parts.single().meals.map { it.id }).containsExactly(9L)
    }

    /** The test is the date, not the direction: a moment before the day is no more honest. */
    @Test
    fun `a logging stored before the day it is filed under also has no honest time`() {
        val backdated = aMeal(
            id = 10,
            epochDay = TEST_EPOCH_DAY,
            loggedAtMillis = at(hour = 22, onDate = day.minusDays(1)),
        )

        val parts = DayParts.of(listOf(backdated), zone)

        assertThat(parts).hasSize(1)
        assertThat(parts.single().part).isNull()
    }

    /**
     * The untimed row is always last, after every part of the clock — five rows in all.
     *
     * This is the one case that exceeds D51's cap of four, and it is deliberate: it is the only row
     * that is asking to be fixed, and the colophon sends him to it. Sorting it in among the parts
     * by its stored hour would bury the one row he is being asked to act on.
     */
    @Test
    fun `the untimed row comes last, even on a day that already fills all four parts`() {
        val parts = DayParts.of(
            listOf(
                aMeal(
                    id = 9,
                    epochDay = TEST_EPOCH_DAY,
                    loggedAtMillis = at(hour = 8, minute = 5, onDate = day.plusDays(1)),
                ),
                loggedAt(hour = 2, id = 1),
                loggedAt(hour = 9, id = 2),
                loggedAt(hour = 15, id = 3),
                loggedAt(hour = 21, id = 4),
            ),
            zone,
        )

        assertThat(parts.map { it.part }).containsExactly(
            PartOfTheClock.NIGHT,
            PartOfTheClock.MORNING,
            PartOfTheClock.MIDDAY,
            PartOfTheClock.EVENING,
            null,
        ).inOrder()
        assertThat(parts.last().meals.map { it.id }).containsExactly(9L)
    }

    /**
     * More than one untimed logging shares that one row, in the order they were given.
     *
     * They have no clock reading to be sorted by, so there is nothing to sort them into: any order
     * imposed here would be invented. Keeping the order handed over is the only answer that states
     * nothing the record does not know.
     */
    @Test
    fun `several loggings with no honest time share the one last row`() {
        val first = aMeal(
            id = 11,
            epochDay = TEST_EPOCH_DAY,
            loggedAtMillis = at(hour = 7, onDate = day.plusDays(1)),
        )
        val second = aMeal(
            id = 12,
            epochDay = TEST_EPOCH_DAY,
            loggedAtMillis = at(hour = 9, onDate = day.plusDays(2)),
        )

        val parts = DayParts.of(listOf(first, second, loggedAt(hour = 13, id = 3)), zone)

        assertThat(parts.map { it.part })
            .containsExactly(PartOfTheClock.MIDDAY, null)
            .inOrder()
        assertThat(parts.last().meals.map { it.id }).containsExactly(11L, 12L).inOrder()
    }

    // --- nothing is lost and nothing is duplicated ----------------------------------------------

    /**
     * Every logging comes out exactly once, whatever it is.
     *
     * The grouping's whole job is to re-arrange the day, so a logging silently dropped or drawn
     * twice would be a wrong total on a row with no other symptom — and the day's own total, which
     * is computed elsewhere from the same meals, would then disagree with the rows beneath it.
     */
    @Test
    fun `every logging appears exactly once across the rows`() {
        val meals = listOf(
            loggedAt(hour = 1, id = 1),
            loggedAt(hour = 7, id = 2),
            loggedAt(hour = 7, minute = 30, id = 3),
            loggedAt(hour = 12, id = 4),
            loggedAt(hour = 23, minute = 59, id = 5),
            aMeal(
                id = 6,
                epochDay = TEST_EPOCH_DAY,
                loggedAtMillis = at(hour = 10, onDate = day.plusDays(1)),
            ),
        )

        val grouped = DayParts.of(meals, zone).flatMap { it.meals }

        assertThat(grouped.map { it.id }).containsExactly(1L, 2L, 3L, 4L, 5L, 6L)
        assertThat(grouped).hasSize(meals.size)
    }

    /**
     * The part is decided in the day's zone, not the machine's.
     *
     * One instant, read in two places, is two clock readings — and a row that said Midday on a
     * phone that had just flown somewhere would be the app disagreeing with the clock the owner is
     * reading it by. Both readings fall on the same calendar date here, so the honest-time test is
     * held still and only the hour differs: 13:00 five hours west of UTC is 18:00 in UTC.
     */
    @Test
    fun `the part is read in the day's own zone`() {
        val midAfternoonLocally = aMeal(
            id = 1,
            epochDay = TEST_EPOCH_DAY,
            loggedAtMillis = at(hour = 13),
        )

        assertThat(DayParts.of(listOf(midAfternoonLocally), zone).single().part)
            .isEqualTo(PartOfTheClock.MIDDAY)
        assertThat(DayParts.of(listOf(midAfternoonLocally), ZoneId.of("UTC")).single().part)
            .isEqualTo(PartOfTheClock.EVENING)
    }
}
