package com.metaself.app.ui.screen.manager

import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.google.common.truth.Truth.assertThat
import com.metaself.app.R
import com.metaself.app.data.food.aFood
import com.metaself.app.data.food.aPer100g
import com.metaself.app.data.food.aPerUnit
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.domain.food.SavedMeal
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.screen.foods.FoodsUiState
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The one screen the food list and the meals now live on, a tab each.
 *
 * Compose, so JUnit 4 — `org.junit.Test`, never `org.junit.jupiter.api.Test`. The two annotations
 * look identical at the call site and the wrong one produces a test that silently never runs.
 */
@RunWith(RobolectricTestRunner::class)
class ManagerScreenRenderTest {

    private val render = ComposeRender()

    /** Which tab the last tap asked for, or null if nothing asked. */
    private var switchedTo: ManagerTab? = null

    /** Which meal the last tap opened the builder on, or null if nothing did. */
    private var openedMealId: Long? = null

    private var builderStarted = false

    @After
    fun tearDown() = render.dispose()

    /** An action that threw is said where refusals are, with the same way to take it down. */
    @Test
    fun `a food action that failed says so in the refusal slot`() {
        val texts = draw(
            tab = ManagerTab.FOODS,
            foods = FoodsUiState(foods = listOf(hummus), failed = ActionRefused.NOTHING_CHANGED),
        )

        assertThat(texts).contains(
            "That didn't work, and nothing was changed. " +
                "What went wrong is under Settings → Recent problems.",
        )
        assertThat(texts).contains("All right")
    }

    /** A list that could not be read is not a list with nothing in it. */
    @Test
    fun `a meals list that could not be read says so, not that none is built`() {
        val texts = draw(
            tab = ManagerTab.MEALS,
            meals = MealsUiState(failed = ActionRefused.COULD_NOT_OPEN),
        )

        assertThat(texts).contains(
            "That couldn't be opened. What went wrong is under Settings → Recent problems.",
        )
        assertThat(texts.none { it.startsWith("Nothing built yet") }).isTrue()
    }

    @Test
    fun `both lists are named and one is in front`() {
        val texts = draw(tab = ManagerTab.FOODS)

        assertThat(texts).contains("My foods")
        assertThat(texts).contains("My meals")
        // In front means drawn: the foods list is there and the meals list is not.
        assertThat(texts).contains("Hummus")
        assertThat(texts).doesNotContain("Vegetable salad")
    }

    @Test
    fun `the label of the other list switches to it`() {
        draw(tab = ManagerTab.FOODS)

        render.click("My meals")

        assertThat(switchedTo).isEqualTo(ManagerTab.MEALS)
    }

    @Test
    fun `the foods tab draws the food list`() {
        val texts = draw(tab = ManagerTab.FOODS)

        assertThat(texts).contains("Hummus")
        assertThat(texts).contains("120 kcal per 100 g")
    }

    @Test
    fun `the meals tab draws what he has built`() {
        val texts = draw(tab = ManagerTab.MEALS)

        assertThat(texts).contains("Vegetable salad")
        // What it comes to, and what is in it as one quiet line beneath the name.
        assertThat(texts.any { it.contains("135 kcal") }).isTrue()
        assertThat(texts.any { it.contains("Cucumber") && it.contains("Olive oil") }).isTrue()
    }

    /** Looking after a meal happens through the builder that already exists. */
    @Test
    fun `a meal opens the builder on itself`() {
        draw(tab = ManagerTab.MEALS)

        render.click("Vegetable salad")

        assertThat(openedMealId).isEqualTo(1L)
    }

    @Test
    fun `with nothing built the meals tab offers the builder rather than an empty space`() {
        val texts = draw(tab = ManagerTab.MEALS, meals = MealsUiState())

        assertThat(texts.any { it.startsWith("Nothing built yet") }).isTrue()
        assertThat(texts).contains("Build a meal")

        render.click("Build a meal")
        assertThat(builderStarted).isTrue()
    }

    /**
     * **This tab looks after a meal; it does not eat one.** Logging stays on "Add something", where
     * he is when he is eating — a manager that also logged would make the two jobs one screen
     * again, which is the mistake this screen exists to correct.
     */
    @Test
    fun `nothing on the meals tab logs anything`() {
        val texts = draw(tab = ManagerTab.MEALS)

        assertThat(texts.any { it.startsWith("Log") }).isFalse()
        assertThat(texts.any { it.startsWith("Adjust") }).isFalse()
        assertThat(texts.any { it.contains("Log it") }).isFalse()
    }

    /**
     * The same honesty the food editor already shows: changing a meal here is a change from now on,
     * and the days it is already on keep the numbers they were logged with.
     */
    @Test
    fun `the meals tab says plainly that a change does not reach backwards`() {
        val texts = draw(tab = ManagerTab.MEALS)

        assertThat(texts.any { it.contains("keep the numbers they were logged with") }).isTrue()
    }

    /**
     * The strip of tab labels is chrome: it belongs to the title bar, not to the list.
     *
     * Drawn with the content it was indented by the screen's margins, pushed down by the page's top
     * padding, and scrolled away with the foods. Only the second of the three is visible from here:
     * a render reports where a node was laid out, not how wide the strip's background runs nor what
     * happens when the list is scrolled. So the control below draws the SAME strip the old way and
     * the assertion is relative — no measurement to go stale, and it fails the moment the strip is
     * put back among the content.
     */
    @Test
    fun `the tabs sit against the title bar, not on top of the list`() {
        val asChrome = render.rowPitchDp("Foods & meals", "My foods") { manager(ManagerTab.FOODS) }
        render.dispose()
        val asContent = render.rowPitchDp("Foods & meals", "My foods") { tabsAmongTheContent() }

        assertThat(asChrome).isLessThan(asContent)
    }

    /** The tab strip as it was first written: emitted as the first thing in the padded column. */
    @Composable
    private fun tabsAmongTheContent() {
        MetaSelfScreen(title = stringResource(R.string.manager_title), onBack = {}) {
            TabRow(selectedTabIndex = 0) {
                Tab(
                    selected = true,
                    onClick = {},
                    text = { Text(stringResource(R.string.manager_tab_foods)) },
                )
                Tab(
                    selected = false,
                    onClick = {},
                    text = { Text(stringResource(R.string.manager_tab_meals)) },
                )
            }
            Text("Hummus")
        }
    }

    private val hummus = aFood("Hummus", FoodFacts(per100g = aPer100g(120.0))).copy(id = 7)
    private val cucumber = aFood("Cucumber", FoodFacts(per100g = aPer100g(16.0))).copy(id = 1)
    private val oil = aFood("Olive oil", FoodFacts(perUnit = aPerUnit("spoon", 119.0))).copy(id = 2)

    /** A salad he built: two foods, counted the way each of them knows. 135 kcal in all. */
    private val salad = SavedMeal(
        id = 1,
        name = "Vegetable salad",
        components = listOf(
            MealComponent(10, cucumber, 100.0, CountedAs.GRAMS, position = 0),
            MealComponent(11, oil, 1.0, CountedAs.UNITS, position = 1),
        ),
    )

    private fun draw(
        tab: ManagerTab,
        foods: FoodsUiState = FoodsUiState(foods = listOf(hummus)),
        meals: MealsUiState = MealsUiState(meals = listOf(salad)),
    ): List<String> = render.texts { manager(tab, foods, meals) }

    @Composable
    private fun manager(
        tab: ManagerTab,
        foods: FoodsUiState = FoodsUiState(foods = listOf(hummus)),
        meals: MealsUiState = MealsUiState(meals = listOf(salad)),
    ) {
        ManagerScreen(
            tab = tab,
            onShowTab = { switchedTo = it },
            foods = foods,
            onSearch = {},
            onShowOnlyPortions = {},
            onShowHidden = {},
            onOpen = {},
            onMergeInto = {},
            onConfirmMerging = {},
            onCancelMerging = {},
            onDismissRefusal = {},
            onShowAgain = {},
            onDismissHidden = {},
            onBeginChoosing = {},
            onToggleChosen = {},
            onClearChoosing = {},
            onMakeMeal = {},
            onJoinChosen = {},
            meals = meals,
            onBuildMeal = { builderStarted = true },
            onEditMeal = { openedMealId = it },
            onDismissMealsFailure = {},
            onBack = {},
        )
    }
}
