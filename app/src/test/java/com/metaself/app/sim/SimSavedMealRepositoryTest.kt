package com.metaself.app.sim

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.day.InMemoryMealRepository
import com.metaself.app.data.food.CountingClock
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.domain.day.aMeal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The walk's saved meals order as `SavedMealDao.observeOffered` does: the later of the last change
 * and the last logging, newest first, then id (public issue #6, item 3). Meal names are invented.
 */
class SimSavedMealRepositoryTest {

    @Test
    fun `meals made in one moment come newest id first`() = runTest {
        val sameMoment = com.metaself.app.data.time.Now { 5 }
        val meals = SimSavedMealRepository(FakeFoodRepository(), now = sameMoment)
        meals.create("Lunch box")
        meals.create("Picnic plate")

        assertThat(meals.observeOffered().first().map { it.name })
            .containsExactly("Picnic plate", "Lunch box").inOrder()
    }

    @Test
    fun `a meal logged since comes above one edited before`() = runTest {
        val day = InMemoryMealRepository()
        val meals = SimSavedMealRepository(FakeFoodRepository(), now = CountingClock(10))
            .linkedTo(day.observeLatestLoggingOfSavedMeals())
        meals.create("Lunch box")
        meals.create("Picnic plate")

        day.log(aMeal(loggedAtMillis = 50, savedMealId = 1))

        assertThat(meals.observeOffered().first().map { it.name })
            .containsExactly("Lunch box", "Picnic plate").inOrder()
    }
}
