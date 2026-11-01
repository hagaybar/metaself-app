package com.metaself.app.domain.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.food.aFood
import com.metaself.app.data.food.aPer100g
import com.metaself.app.data.food.aPerUnit
import com.metaself.app.domain.day.Source
import org.junit.jupiter.api.Test

/** Which foods and meals get a tile on Add something (D154), and what a question opens holding (D155). */
class QuickAddTest {

    // Every figure here is invented, and chosen to keep the sums round.
    private val bar = aFood("Bar", FoodFacts(perUnit = aPerUnit("bar", 200.0))).copy(id = 1)
    private val rice = aFood("Rice", FoodFacts(per100g = aPer100g(100.0))).copy(id = 2)

    private var nextMeal = 1L

    /** Logged [times] times, each as a meal of one row. */
    private fun logged(food: Food, amount: Double, unit: String, times: Int = 3, at: Long = 1_000): List<LoggedRow> =
        List(times) { LoggedRow(nextMeal++, null, food.id, amount, unit, at + it) }

    private fun tiles(vararg rows: List<LoggedRow>, foods: List<Food> = listOf(bar, rice), meals: List<SavedMeal> = emptyList()) =
        QuickAdd.tiles(foods, meals, rows.toList().flatten())

    @Test
    fun `three loggings earn a tile and two do not`() {
        assertThat(tiles(logged(bar, 1.0, "bar", times = 3)).map { it.name }).containsExactly("Bar")
        assertThat(tiles(logged(bar, 1.0, "bar", times = 2))).isEmpty()
    }

    @Test
    fun `a tile writes the row the food's figures give now, carrying the food's source`() {
        val row = tiles(logged(bar, 2.0, "bar")).single().items.single()

        assertThat(row.kcal).isEqualTo(400)
        assertThat(row.portion).isEqualTo("2 bar")
        assertThat(row.foodId).isEqualTo(bar.id)
        assertThat(row.source).isEqualTo(Source.TYPED)
    }

    @Test
    fun `a weighed food's tile counts in grams`() {
        val tile = tiles(logged(rice, 150.0, "g")).single()

        assertThat(tile.usual).isEqualTo(QuickAmount(150.0, CountedAs.GRAMS))
        assertThat(tile.kcal).isEqualTo(150)
    }

    @Test
    fun `the most common amount wins over the latest`() {
        val tile = tiles(logged(bar, 1.0, "bar", times = 3, at = 1_000), logged(bar, 2.0, "bar", times = 1, at = 9_000))

        assertThat(tile.single().usual?.amount).isEqualTo(1.0)
    }

    @Test
    fun `between two equally common amounts the more recent wins`() {
        val tile = tiles(logged(bar, 1.0, "bar", times = 2, at = 1_000), logged(bar, 2.0, "bar", times = 2, at = 9_000))

        assertThat(tile.single().usual?.amount).isEqualTo(2.0)
    }

    @Test
    fun `a food whose amount varies gets no tile`() {
        val rows = logged(rice, 100.0, "g", times = 1) + logged(rice, 150.0, "g", times = 1) + logged(rice, 200.0, "g", times = 1)

        assertThat(tiles(rows)).isEmpty()
    }

    @Test
    fun `a row of a many-row meal does not count for the food`() {
        val rows = List(3) { index ->
            listOf(
                LoggedRow(100L + index, null, bar.id, 1.0, "bar", 1_000),
                LoggedRow(100L + index, null, rice.id, 100.0, "g", 1_000),
            )
        }.flatten()

        assertThat(tiles(rows)).isEmpty()
    }

    @Test
    fun `a part of a built meal does not become a favourite through that meal`() {
        val rows = List(3) { LoggedRow(100L + it, savedMealId = 7, foodId = bar.id, amount = 1.0, unit = "bar", loggedAtMillis = 1_000) }

        assertThat(tiles(rows)).isEmpty()
    }

    @Test
    fun `a food no longer offered gets no tile`() {
        assertThat(tiles(logged(bar, 1.0, "bar"), foods = listOf(rice))).isEmpty()
    }

    @Test
    fun `an amount in a unit the food can no longer be counted in gets no tile`() {
        assertThat(tiles(logged(bar, 1.0, "slice"))).isEmpty()
    }

    @Test
    fun `an amount past its ceiling gets no tile`() {
        assertThat(tiles(logged(rice, 9_000.0, "g"))).isEmpty()
    }

    @Test
    fun `a built meal counts once per logging whatever its parts, and logs as defined`() {
        val meal = SavedMeal(
            id = 7,
            name = "Lunch box",
            components = listOf(
                MealComponent(1, bar, 1.0, CountedAs.UNITS, position = 0),
                MealComponent(2, rice, 100.0, CountedAs.GRAMS, position = 1),
            ),
        )
        // What was actually logged differed from the definition, and that must make no difference.
        val threeTimes = List(3) { index ->
            listOf(
                LoggedRow(100L + index, 7, bar.id, 5.0, "bar", 1_000),
                LoggedRow(100L + index, 7, rice.id, 900.0, "g", 1_000),
            )
        }.flatten()
        val twice = threeTimes.take(4)

        val tile = tiles(threeTimes, meals = listOf(meal)).single()

        assertThat(tile.savedMealId).isEqualTo(7)
        assertThat(tile.items.map { it.portion }).containsExactly("1 bar", "100 g").inOrder()
        assertThat(tile.kcal).isEqualTo(300)
        assertThat(tiles(twice, meals = listOf(meal))).isEmpty()
    }

    @Test
    fun `past eight the most logged stay, and they are shown by name`() {
        val foods = (1L..9L).map { aFood("Food ${10 - it}", FoodFacts(perUnit = aPerUnit("one", 100.0))).copy(id = it) }
        // Food id 1 ("Food 9") is the least logged, so it is the one that goes.
        val rows = foods.flatMap { logged(it, 1.0, "one", times = 2 + it.id.toInt()) }

        val names = QuickAdd.tiles(foods, emptyList(), rows).map { it.name }

        assertThat(names).containsExactly(
            "Food 1", "Food 2", "Food 3", "Food 4", "Food 5", "Food 6", "Food 7", "Food 8",
        ).inOrder()
    }

    @Test
    fun `the question opens at the tile's amount, not at the last one`() {
        val tiles = tiles(logged(bar, 1.0, "bar"), logged(bar, 2.0, "bar", times = 1))
        val last = LastAmount(bar.id, 2.0, "bar", 9_000)

        assertThat(QuickAdd.amountToOffer(bar, tiles, last)).isEqualTo(QuickAmount(1.0, CountedAs.UNITS))
    }

    @Test
    fun `with no tile the question opens at the last amount`() {
        val last = LastAmount(rice.id, 180.0, "g", 9_000)

        assertThat(QuickAdd.amountToOffer(rice, emptyList(), last)).isEqualTo(QuickAmount(180.0, CountedAs.GRAMS))
    }

    @Test
    fun `with no history, or a unit it has lost, the question opens empty`() {
        assertThat(QuickAdd.amountToOffer(bar, emptyList(), null)).isNull()
        assertThat(QuickAdd.amountToOffer(bar, emptyList(), LastAmount(bar.id, 1.0, "slice", 9_000))).isNull()
    }
}
