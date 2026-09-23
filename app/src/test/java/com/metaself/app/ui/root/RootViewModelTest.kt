package com.metaself.app.ui.root

import com.metaself.app.data.day.DeletedEntry
import com.metaself.app.data.day.DetachedRow
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.day.MealRepository
import com.metaself.app.data.time.Today
import com.metaself.app.data.weight.WeightRepository
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.weight.WeightReading
import java.time.LocalDate
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.target.CurrentTarget
import com.metaself.app.domain.target.TargetRevision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `with nothing stored, the app needs setting up`() = runTest {
        val viewModel = viewModel(FakeProfileRepository(null))
        assertThat(viewModel.state.first { it !is RootUiState.Loading })
            .isInstanceOf(RootUiState.NeedsSetup::class.java)
    }

    @Test
    fun `with a profile stored, the target is ready`() = runTest {
        val viewModel = viewModel(FakeProfileRepository(aProfile()))
        val ready = viewModel.state.first { it is RootUiState.Ready } as RootUiState.Ready
        assertThat(ready.target.kcal).isEqualTo(2090)
    }

    @Test
    fun `the profile screen's target follows the revision, like the day screen's`() = runTest {
        val repository = FakeProfileRepository(
            aProfile(weightKg = 80.0),
            initialRevision = TargetRevision(20_699L, trendKg = 74.0, kcal = 0, previousKcal = null),
        )
        val viewModel = viewModel(repository)

        val ready = viewModel.state.first { it is RootUiState.Ready } as RootUiState.Ready

        assertThat(ready.weightUsedKg).isEqualTo(74.0)
        assertThat(ready.targetFollowsTrend).isTrue()
        assertThat(ready.target.kcal).isEqualTo(
            CurrentTarget.of(aProfile(weightKg = 80.0), repository.storedRevision, TEST_YEAR).kcal,
        )
    }

    @Test
    fun `with no revision the target still comes from the weight that was typed`() = runTest {
        val viewModel = viewModel(FakeProfileRepository(aProfile()))

        val ready = viewModel.state.first { it is RootUiState.Ready } as RootUiState.Ready

        assertThat(ready.targetFollowsTrend).isFalse()
        assertThat(ready.weightUsedKg).isEqualTo(80.0)
    }

    @Test
    fun `saving a profile moves the app off the setup screen`() = runTest {
        val repository = FakeProfileRepository(null)
        val viewModel = viewModel(repository)

        viewModel.save(aProfile())

        val ready = viewModel.state.first { it is RootUiState.Ready } as RootUiState.Ready
        assertThat(ready.profile).isEqualTo(aProfile())
    }

    @Test
    fun `overruling the floor stores the decision, not just the screen state`() = runTest {
        val repository = FakeProfileRepository(aProfile())
        val viewModel = viewModel(repository)

        viewModel.state.first { it is RootUiState.Ready }
        viewModel.allowBelowFloor()
        // The write happens in a coroutine on the test dispatcher, which only runs when asked.
        advanceUntilIdle()

        assertThat(repository.profile.first()?.allowBelowFloor).isTrue()
    }

    
    /**
     * The root reads meals and weights now, to show what a day has actually cost against what the
     * formula predicted (D25). Empty stores mean it has nothing to measure, which is the right
     * answer for every test here.
     */
    private fun viewModel(profiles: FakeProfileRepository) = RootViewModel(
        repository = profiles,
        meals = EmptyMeals(),
        weights = EmptyWeights(),
        today = Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) },
        currentYear = CurrentYear { TEST_YEAR },
    )

    private class EmptyMeals : MealRepository {
        override fun observeDay(epochDay: Long): Flow<List<Meal>> = MutableStateFlow(emptyList())
        override fun observeLoggedDays(): Flow<Set<Long>> = MutableStateFlow(emptySet())
        override suspend fun kcalByDaySince(fromEpochDay: Long): Map<Long, Int> = emptyMap()
        override suspend fun log(meal: Meal): List<Long> = emptyList()
        override suspend fun updateItem(item: FoodItem) = Unit
        override suspend fun deleteItem(itemId: Long): DeletedEntry? = null
        override suspend fun restore(entry: DeletedEntry) = Unit
        override suspend fun setEatenAt(mealId: Long, atMillis: Long) = Unit
        override suspend fun rowsWithNoFood(): List<DetachedRow> = emptyList()
        override suspend fun attachRow(itemId: Long, foodId: Long) = Unit
        override suspend fun gatherIntoSavedMeal(
            epochDay: Long,
            itemIds: List<Long>,
            savedMealId: Long,
        ) = Unit
    }

    private class EmptyWeights : WeightRepository {
        override val readings: Flow<List<WeightReading>> = MutableStateFlow(emptyList())
        override suspend fun log(reading: WeightReading) = Unit
        override suspend fun delete(epochDay: Long) = Unit
    }
}
