package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.movement.StepAccess
import com.metaself.app.domain.movement.ActivityEnergy
import com.metaself.app.domain.movement.DayMovement
import com.metaself.app.domain.movement.MovementToday
import com.metaself.app.ui.movement.MovementWording
import org.junit.jupiter.api.Test

class MovementWordingTest {

    private fun credit(steps: Int, cap: Int = 275) = MovementCredit.of(
        today = ActivityEnergy.of(DayMovement(epochDay = 1, steps = steps), weightKg = 80.0),
        normalEnergyKcal = ActivityEnergy.of(
            DayMovement(epochDay = 0, steps = 5_200),
            weightKg = 80.0,
        ).kcal,
        capKcal = cap,
    )

    @Test
    fun `it says what was earned`() {
        // 10,000 steps at 80 kg is 300 kcal, 144 above the usual 5,200-step day's 156, of which
        // three quarters is 108.
        assertThat(MovementWording.earned(credit(10_000))).isEqualTo("+108 kcal earned")
    }

    @Test
    fun `a day that earned nothing says nothing about calories`() {
        assertThat(MovementWording.earned(credit(4_000))).isNull()
        assertThat(MovementWording.earned(null)).isNull()
    }

    /**
     * D12a, the correction that added it: activity is not only an input to the arithmetic. The count is
     * shown every day, including the ordinary ones and the ones before a usual day is known.
     */
    @Test
    fun `the step count is shown whether or not it earned anything`() {
        assertThat(MovementWording.steps(today(3_100))).isEqualTo("3,100 steps")
        assertThat(MovementWording.steps(today(10_000))).isEqualTo("10,000 steps")
        assertThat(MovementWording.steps(today(2_000, normal = null))).isEqualTo("2,000 steps")
    }

    @Test
    fun `a busy day says how far past usual it is`() {
        assertThat(MovementWording.againstUsual(today(9_000)))
            .isEqualTo("3,800 more than your usual 5,200")
    }

    /**
     * Never a reproach. A quiet day states the usual and stops: a target that already assumes
     * normal movement is owed nothing by a quiet Tuesday.
     */
    @Test
    fun `a quiet day states the usual without complaining`() {
        val text = MovementWording.againstUsual(today(3_100))!!

        assertThat(text).isEqualTo("Your usual day is 5,200")
        assertThat(text.lowercase()).doesNotContain("behind")
        assertThat(text.lowercase()).doesNotContain("only")
    }

    @Test
    fun `before a usual day is known there is nothing to compare against`() {
        assertThat(MovementWording.againstUsual(today(4_000, normal = null))).isNull()
    }

    @Test
    fun `the cap is mentioned only when it actually bit`() {
        assertThat(MovementWording.capped(credit(25_000))).isNotNull()
        assertThat(MovementWording.capped(credit(10_000))).isNull()
    }

    @Test
    fun `nothing claims he burned a number`() {
        val everything = listOfNotNull(
            MovementWording.earned(credit(10_000)),
            MovementWording.capped(credit(25_000)),
            MovementWording.status(StepAccess.GRANTED, hasNormal = true, daysSoFar = 30),
        ).joinToString(" ").lowercase()

        listOf("you burned", "calories burned", "burn rate").forEach {
            assertThat(everything).doesNotContain(it)
        }
    }

    @Test
    fun `with no permission it says what allowing it would do`() {
        val text = MovementWording.status(StepAccess.NOT_PERMITTED, hasNormal = false, daysSoFar = 0)

        assertThat(text).contains("Allow MetaSelf to read your steps")
        assertThat(text).contains("ordinary day adds nothing")
    }

    @Test
    fun `while learning it says how far along it is`() {
        val text = MovementWording.status(StepAccess.GRANTED, hasNormal = false, daysSoFar = 4)

        assertThat(text).contains("4 of the 10 days")
        assertThat(text).contains("Nothing is added until it knows")
    }

    /**
     * "4 of 10 days" alone cannot be acted on: it does not say whether the app is failing to see a
     * month of history or whether the phone only began recording a few days ago. The date says which.
     */
    @Test
    fun `while learning it says how far back the data goes`() {
        val text = MovementWording.status(
            StepAccess.GRANTED,
            hasNormal = false,
            daysSoFar = 4,
            earliest = java.time.LocalDate.of(2026, 9, 1),
        )

        assertThat(text).contains("earliest being 2026-09-01")
    }

    /** Nothing at all is a different fact from not enough, and needs different words. */
    @Test
    fun `no steps at all says so rather than counting to zero`() {
        val text = MovementWording.status(StepAccess.GRANTED, hasNormal = false, daysSoFar = 0)

        assertThat(text).contains("no steps in Health Connect yet")
        assertThat(text).doesNotContain("0 of the")
    }

    @Test
    fun `with no health connect it says so plainly`() {
        assertThat(MovementWording.status(StepAccess.UNAVAILABLE, false, 0))
            .contains("not available on this phone")
    }

    /**
     * The step display used to VANISH on a day Health Connect had nothing for — which early in the
     * morning is the app appearing to have lost a feature rather than reporting a fact.
     */
    @Test
    fun `a day with no record yet says so instead of disappearing`() {
        val nothingYet = MovementToday(steps = 0, recorded = false, normalSteps = 5_200)

        assertThat(MovementWording.steps(nothingYet)).isEqualTo("No steps recorded yet today")
        // And it does not go on to compare nothing against a usual day.
        assertThat(MovementWording.againstUsual(nothingYet)).isNull()
    }

    /** Zero steps recorded is a different fact from nothing recorded, and reads differently. */
    @Test
    fun `a recorded zero is not the same as no record`() {
        assertThat(MovementWording.steps(today(0))).isEqualTo("0 steps")
    }

    /**
     * A session and its energy are two different records in Health Connect. Without this line a
     * swim shows on the day, changes no number, and says nothing about why.
     */
    @Test
    fun `it says whether the band reports the calories that make a swim count`() {
        assertThat(MovementWording.bandEnergy(daysWithEnergy = 0, daysSeen = 30))
            .contains("swimming and weights are earning nothing")

        assertThat(MovementWording.bandEnergy(daysWithEnergy = 12, daysSeen = 30))
            .contains("12 of the last 30 days")
    }

    @Test
    fun `with no history at all there is nothing to say about the band`() {
        assertThat(MovementWording.bandEnergy(daysWithEnergy = 0, daysSeen = 0)).isNull()
    }

    private fun today(steps: Int, normal: Int? = 5_200) =
        MovementToday(steps = steps, normalSteps = normal)
}
