package com.metaself.app.ui.screen.home

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.profile.ActivityLevel
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.Profile
import com.metaself.app.domain.profile.Sex
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.target.DailyTargetCalculator
import com.metaself.app.domain.target.MeasuredBurn
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** JUnit 4 by necessity — Robolectric's runner is JUnit 4. Everything pure here is JUnit 5. */
@RunWith(RobolectricTestRunner::class)
class HomeScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `draws the daily target`() {
        assertThat(drawWith(aProfile())).contains("Your daily target: 2,090 kcal")
    }

    @Test
    fun `draws every step of the arithmetic that produced it`() {
        val texts = drawWith(aProfile())
        assertThat(texts).contains("Resting burn: 1,700 kcal")
        assertThat(texts).contains("A normal day: 2,640 kcal")
        assertThat(texts).contains("To lose 0.5 kg a week: −550 kcal")
    }

    @Test
    fun `draws the three macros`() {
        val texts = drawWith(aProfile())
        assertThat(texts).contains("Protein: 145 g")
        assertThat(texts).contains("Fat: 65 g")
        assertThat(texts).contains("Carbohydrate: 230 g")
    }

    @Test
    fun `still says which build this is`() {
        assertThat(drawWith(aProfile())).contains("v0.1.0-rc1 (1)")
    }

    @Test
    fun `offers to overrule a floor it had to apply`() {
        assertThat(drawWith(steepLoss())).contains("Use the number I asked for anyway")
    }

    @Test
    fun `does not offer to overrule a floor it never reached`() {
        assertThat(drawWith(aProfile())).doesNotContain("Use the number I asked for anyway")
    }

    private fun steepLoss(): Profile = aProfile(
        sex = Sex.FEMALE,
        weightKg = 65.0,
        heightCm = 165,
        birthYear = 1986,
        activity = ActivityLevel.SEDENTARY,
        goal = Goal.lose(1.0),
    )

    @Test
    fun `it says the target came from the weight the owner entered, when it did`() {
        assertThat(drawWith(aProfile(), "Worked out from the weight you entered, 80.0 kg"))
            .contains("Worked out from the weight you entered, 80.0 kg")
    }

    @Test
    fun `it says the target came from the trend, when it did`() {
        assertThat(drawWith(aProfile(), "Worked out from your weight trend, 79.2 kg"))
            .contains("Worked out from your weight trend, 79.2 kg")
    }

    private fun drawWith(
        profile: Profile,
        weightUsedLine: String = "Worked out from the weight you entered, 80.0 kg",
        measuredBurn: MeasuredBurn? = null,
        burnAdjustmentKcal: Int = 0,
        daysLoggedRecently: Int = 0,
    ): List<String> = render.texts {
        HomeScreen(
            profile = profile,
            target = DailyTargetCalculator.of(profile, TEST_YEAR),
            versionName = "0.1.0-rc1",
            versionCode = 1,
            measuredBurn = measuredBurn,
            burnAdjustmentKcal = burnAdjustmentKcal,
            daysLoggedRecently = daysLoggedRecently,
            weightUsedLine = weightUsedLine,
            onEdit = {},
            onAllowBelowFloor = {},
            onForgetBurnAdjustment = {},
        )
    }

    /** D25: what actually happened, against what the formula predicted. */
    @Test
    fun `with nothing measured yet it says so`() {
        assertThat(drawWith(aProfile()).any { it.contains("Not measured yet") }).isTrue()
    }

    /** The working is off by default; the numbers are what he came for. */
    @Test
    fun `the working is offered rather than shown`() {
        val texts = drawWith(aProfile())

        assertThat(texts.any { it.contains("Show how these are worked out") }).isTrue()
    }

    @Test
    fun `a measurement shows its arithmetic, not only its conclusion`() {
        val texts = drawWith(
            aProfile(),
            measuredBurn = MeasuredBurn(
                days = 28,
                daysLogged = 26,
                averageLoggedKcal = 2_150,
                trendChangeKg = -1.2,
                fromStoresKcalPerDay = 330,
                measuredKcal = 2_480,
                formulaKcal = 2_640,
            ),
            burnAdjustmentKcal = -100,
        )

        // The conclusion is the heading and is always visible; the sum behind it waits behind the
        // button, which is where it was asked for.
        assertThat(texts.any { it.contains("costing about 2480 kcal") }).isTrue()
        assertThat(texts.any { it.contains("Against the formula") }).isTrue()
        assertThat(texts.any { it.contains("2150 kcal a day") }).isFalse()
    }

    /**
     * The one assumption the arithmetic cannot check lives in a HEADING, so that collapsing the
     * working cannot hide it (D25a).
     */
    @Test
    fun `the assumption survives the working being hidden`() {
        assertThat(
            drawWith(aProfile()).any { it.contains("what you logged is what you ate") },
        ).isTrue()
    }
}
