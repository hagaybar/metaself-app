package com.metaself.app.data.day

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.day.aMeal
import com.metaself.app.domain.day.anItem
import org.junit.jupiter.api.Test

class DayMappingTest {

    @Test
    fun `a meal survives the round trip`() {
        val meal = aMeal(items = listOf(anItem(name = "Hummus", kcal = 180)))

        val stored = meal.toEntities()
        val readBack = MealWithItems(stored.first, stored.second).toDomain()

        assertThat(readBack?.items?.single()?.name).isEqualTo("Hummus")
        assertThat(readBack?.items?.single()?.kcal).isEqualTo(180)
        assertThat(readBack?.epochDay).isEqualTo(meal.epochDay)
    }

    @Test
    fun `an estimate keeps its confidence through storage`() {
        val meal = aMeal(
            items = listOf(anItem(source = Source.AI_ESTIMATE, confidence = Confidence.LOW)),
        )

        val stored = meal.toEntities()
        val readBack = MealWithItems(stored.first, stored.second).toDomain()

        assertThat(readBack?.items?.single()?.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(readBack?.items?.single()?.confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun `a source this version does not know is shown, not dropped and not relabelled`() {
        val row = MealWithItems(
            meal = MealEntity(id = 1, epochDay = 20_699L, loggedAtMillis = 1_000, note = null),
            items = listOf(
                FoodItemEntity(
                    id = 1,
                    mealId = 1,
                    name = "Something from the future",
                    portion = null,
                    kcal = 400,
                    proteinG = 10,
                    carbsG = 20,
                    fatG = 15,
                    source = "BARCODE_SCAN",
                    confidence = null,
                ),
            ),
        )

        val item = row.toDomain()?.items?.single()

        assertThat(item?.source).isEqualTo(Source.UNRECOGNISED)
        assertThat(item?.kcal).isEqualTo(400)
    }

    @Test
    fun `a stored typed item carrying a confidence is read as unrecognised rather than rejected`() {
        // The domain forbids this combination, so it can only arrive from a corrupted or future
        // store. It must not crash the day, and it must not be presented as a plain typed number.
        val row = MealWithItems(
            meal = MealEntity(id = 1, epochDay = 20_699L, loggedAtMillis = 1_000, note = null),
            items = listOf(
                FoodItemEntity(
                    id = 1,
                    mealId = 1,
                    name = "Impossible",
                    portion = null,
                    kcal = 100,
                    proteinG = 0,
                    carbsG = 0,
                    fatG = 0,
                    source = "TYPED",
                    confidence = "HIGH",
                ),
            ),
        )

        val item = row.toDomain()?.items?.single()

        assertThat(item?.source).isEqualTo(Source.UNRECOGNISED)
        assertThat(item?.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `a meal whose every item is unreadable is dropped rather than shown empty`() {
        val row = MealWithItems(
            meal = MealEntity(id = 1, epochDay = 20_699L, loggedAtMillis = 1_000, note = null),
            items = emptyList(),
        )

        assertThat(row.toDomain()).isNull()
    }

    @Test
    fun `a new meal is stored with no id, so the database assigns one`() {
        val (mealEntity, itemEntities) = aMeal().toEntities()
        assertThat(mealEntity.id).isEqualTo(0)
        assertThat(itemEntities.single().id).isEqualTo(0)
    }
}
