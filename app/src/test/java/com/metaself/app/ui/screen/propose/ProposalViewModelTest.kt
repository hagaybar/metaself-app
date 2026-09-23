package com.metaself.app.ui.screen.propose

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.FoodRepository
import com.metaself.app.data.food.LoggedFoods
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.MealEstimator
import com.metaself.app.domain.ai.ProposedItem
import com.metaself.app.domain.ai.aBun
import com.metaself.app.domain.ai.aModelPita
import com.metaself.app.domain.ai.aProposal
import com.metaself.app.domain.ai.aProposedItem
import com.metaself.app.domain.amount.Per
import com.metaself.app.domain.amount.Rate
import com.metaself.app.domain.amount.Worth
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodMatch
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.PerUnit
import com.metaself.app.domain.food.Provenance
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.RecordingProblemLog
import com.metaself.app.ui.propose.ProposalWording
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProposalViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a described meal becomes rows, one per component`() = runTest {
        val viewModel =
            ProposalViewModel(
                FakeEstimator(EstimateResult.Proposed(aProposal())),
                ProblemLog.NONE,
                FakeFoodRepository(),
            )

        viewModel.describe("risotto with mozzarella")
        advanceUntilIdle()

        val proposed = viewModel.state.value as ProposalUiState.Proposed
        assertThat(proposed.rows).hasSize(2)
        // The burger's 250 per 100 g at 200 g, and one bun at 150 (D53 §1).
        assertThat(proposed.totalKcal).isEqualTo(650)
    }

    // --- The typed amount (D53 §1, §6) -----------------------------------------------------------

    @Test
    fun `typing an amount changes that row's total and nothing else`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.setAmount(0, "150")

        val proposed = viewModel.state.value as ProposalUiState.Proposed
        val burger = proposed.rows[0].item.numbers!!
        assertThat(listOf(burger.kcal, burger.proteinG, burger.carbsG, burger.fatG))
            .containsExactly(375, 27, 0, 30).inOrder()
        assertThat(proposed.rows[1].item.numbers!!.kcal).isEqualTo(150)
        assertThat(proposed.totalKcal).isEqualTo(525)
    }

    /** No scaling of a scaled item: the worth is the worth until he types over it. */
    @Test
    fun `the worth survives any amount`() = runTest {
        val viewModel = proposedViewModel()
        val before = (viewModel.state.value as ProposalUiState.Proposed).rows[0].item.worth

        viewModel.setAmount(0, "150")
        viewModel.setAmount(0, "")
        viewModel.setAmount(0, "300")

        val row = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        assertThat(row.item.worth).isEqualTo(before)
        assertThat(row.item.numbers!!.kcal).isEqualTo(750)
    }

    @Test
    fun `a blank amount blocks saving and names the row`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.setAmount(0, "")

        val proposed = viewModel.state.value as ProposalUiState.Proposed
        assertThat(proposed.blockedBy).isEqualTo(0)
        assertThat(viewModel.accepted()).isEmpty()
    }

    /** The model's 6000 g arrives kept, and the box refuses it as if he had typed it (D53 §2). */
    @Test
    fun `an amount past the ceiling blocks saving until it is changed`() = runTest {
        val viewModel = proposedViewModelOf(aProposedItem(amount = 6000.0))

        val proposed = viewModel.state.value as ProposalUiState.Proposed
        assertThat(proposed.rows[0].item.amountTooMuch).isTrue()
        assertThat(proposed.blockedBy).isEqualTo(0)
        assertThat(viewModel.accepted()).isEmpty()

        viewModel.setAmount(0, "200")
        assertThat(viewModel.accepted()).hasSize(1)
    }

    @Test
    fun `plus and minus step a counted row by one and never below one`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.step(1, -1)
        assertThat(amountOf(viewModel, 1)).isEqualTo("1")

        viewModel.step(1, +1)
        assertThat(amountOf(viewModel, 1)).isEqualTo("2")
        assertThat((viewModel.state.value as ProposalUiState.Proposed).rows[1].item.numbers!!.kcal)
            .isEqualTo(300)
    }

    /** A typed half is his, and kept: − does nothing when a step would go below one (D53 §6). */
    @Test
    fun `a typed half is kept, and stepping from it keeps the half`() = runTest {
        val viewModel = proposedViewModel()
        viewModel.setAmount(1, "0.5")

        viewModel.step(1, -1)
        assertThat(amountOf(viewModel, 1)).isEqualTo("0.5")

        viewModel.step(1, +1)
        assertThat(amountOf(viewModel, 1)).isEqualTo("1.5")
    }

    @Test
    fun `plus and minus do nothing to grams`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.step(0, +1)
        viewModel.step(0, -1)

        assertThat(amountOf(viewModel, 0)).isEqualTo("200")
    }

    @Test
    fun `plus and minus do nothing to millilitres`() = runTest {
        val viewModel =
            proposedViewModelOf(aProposedItem(name = "Orange juice", amount = 330.0, unit = "ml"))

        viewModel.step(0, +1)

        assertThat(amountOf(viewModel, 0)).isEqualTo("330")
    }

    /** An estimate times his amount is still an estimate (D53 §3). */
    @Test
    fun `accepted rows are estimates with the model's confidence at the typed amount`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.setAmount(0, "150")
        val accepted = viewModel.accepted().map { it.item }

        assertThat(accepted).hasSize(2)
        assertThat(accepted.all { it.source == Source.AI_ESTIMATE }).isTrue()
        assertThat(accepted[0].confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(accepted[0].kcal).isEqualTo(375)
        assertThat(accepted[0].portion).isEqualTo("150 g")
        assertThat(accepted[0].portionAmount).isEqualTo(150.0)
        assertThat(accepted[1].portion).isEqualTo("1 bun (sesame, toasted)")
        assertThat(accepted.all { it.foodId == null }).isTrue()
    }

    @Test
    fun `each row keeps the model's answer beside it`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.setAmount(0, "150")

        val row = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        assertThat(row.estimate).isEqualTo(aProposedItem())
        assertThat(row.item.worth).isInstanceOf(Worth.Estimated::class.java)
    }

    // --- The worth, typed over (D53 §1, §3) -----------------------------------------------------

    @Test
    fun `the worth boxes open holding the worth as a person would type it`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.openWorth(0)

        val boxes = (viewModel.state.value as ProposalUiState.Proposed).rows[0].editingWorth!!
        assertThat(boxes.typed).containsExactly("250", "18", "0", "20").inOrder()
    }

    /** The source belongs to the row: one figure typed makes all four his (D53 §3, D44's cost). */
    @Test
    fun `changing one figure of the worth makes the row typed`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.openWorth(0)
        viewModel.setWorthBox(0, WorthFigure.KCAL, "240")

        val row = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        assertThat(row.item.worth).isInstanceOf(Worth.Typed::class.java)
        val numbers = row.numbers!!
        assertThat(numbers.source).isEqualTo(Source.TYPED)
        assertThat(numbers.confidence).isNull()
        assertThat(numbers.kcal).isEqualTo(480)
        assertThat(numbers.proteinG).isEqualTo(36)
    }

    /** Compared as numbers: "250.0" is the figure it opened with, and so is "250,0". */
    @Test
    fun `retyping the same figure changes nothing`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.openWorth(0)
        viewModel.setWorthBox(0, WorthFigure.KCAL, "250.0")
        viewModel.setWorthBox(0, WorthFigure.PROTEIN, "18,0")

        val numbers = (viewModel.state.value as ProposalUiState.Proposed).rows[0].numbers!!
        assertThat(numbers.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(numbers.confidence).isEqualTo(Confidence.MEDIUM)
    }

    /** Typed away and typed back: the row is the model's again, not a typed copy of it. */
    @Test
    fun `typing a figure back to what it opened with returns the row to its source`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.openWorth(0)
        viewModel.setWorthBox(0, WorthFigure.KCAL, "240")
        viewModel.setWorthBox(0, WorthFigure.KCAL, "250")

        val row = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        assertThat(row.item.worth).isEqualTo(row.estimate.toItemToLog().worth)
    }

    @Test
    fun `a worth past its ceiling blocks the row and says the ceiling`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.openWorth(0)
        viewModel.setWorthBox(0, WorthFigure.KCAL, "1001")

        val proposed = viewModel.state.value as ProposalUiState.Proposed
        assertThat(proposed.rows[0].editingWorth!!.refused).isTrue()
        assertThat(proposed.rows[0].numbers).isNull()
        assertThat(proposed.blockedBy).isEqualTo(0)
        assertThat(proposed.totalKcal).isEqualTo(150)
        assertThat(viewModel.accepted()).isEmpty()
        assertThat(ProposalWording.worthRefused(proposed.rows[0].editingWorth!!.per, "g"))
            .isEqualTo(
                "All four per 100 g (at most 1000 kcal, and 110 g of protein, " +
                    "carbohydrate or fat).",
            )
    }

    /** A piece's figures have the per-one ceiling: 1001 kcal is a believable bun, 5001 is not. */
    @Test
    fun `a piece's worth is judged against the per-one ceiling`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.openWorth(1)
        viewModel.setWorthBox(1, WorthFigure.KCAL, "1001")
        assertThat((viewModel.state.value as ProposalUiState.Proposed).blockedBy).isNull()

        viewModel.setWorthBox(1, WorthFigure.KCAL, "5001")
        val proposed = viewModel.state.value as ProposalUiState.Proposed
        assertThat(proposed.blockedBy).isEqualTo(1)
        assertThat(ProposalWording.worthRefused(proposed.rows[1].editingWorth!!.per, "bun"))
            .isEqualTo(
                "All four per bun (at most 5000 kcal, and 500 g of protein, carbohydrate or fat).",
            )
    }

    @Test
    fun `a blank worth box blocks the row as a blank amount does`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.openWorth(0)
        viewModel.setWorthBox(0, WorthFigure.FAT, "")

        assertThat((viewModel.state.value as ProposalUiState.Proposed).blockedBy).isEqualTo(0)
        assertThat(viewModel.accepted()).isEmpty()
    }

    @Test
    fun `the amount and the worth move independently`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.openWorth(0)
        viewModel.setWorthBox(0, WorthFigure.KCAL, "240")
        viewModel.setAmount(0, "150")

        val row = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        assertThat(row.item.amountText).isEqualTo("150")
        assertThat(row.item.rateLine!!.nutrients.kcal).isEqualTo(240.0)
        assertThat(row.numbers!!.kcal).isEqualTo(360)
    }

    /** The boxes close on what he typed; while one is refused they stay, with the sentence. */
    @Test
    fun `closing the boxes keeps what was typed, and a refused box keeps them open`() = runTest {
        val viewModel = proposedViewModel()
        viewModel.openWorth(0)
        viewModel.setWorthBox(0, WorthFigure.KCAL, "1001")

        viewModel.closeWorth(0)
        assertThat((viewModel.state.value as ProposalUiState.Proposed).rows[0].editingWorth)
            .isNotNull()

        viewModel.setWorthBox(0, WorthFigure.KCAL, "240")
        viewModel.closeWorth(0)
        val row = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        assertThat(row.editingWorth).isNull()
        assertThat(row.numbers!!.kcal).isEqualTo(480)
    }

    /** Typed decimals are kept: the worth is a food's kind of figure, rounded once when logged. */
    @Test
    fun `a typed worth keeps its decimals until the row is logged`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.openWorth(0)
        viewModel.setWorthBox(0, WorthFigure.PROTEIN, "18.4")

        val row = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        assertThat(row.item.rateLine!!.nutrients.proteinG).isEqualTo(18.4)
        // 18.4 per 100 g at 200 g is 36.8, rounded once to 37.
        assertThat(row.numbers!!.proteinG).isEqualTo(37)
    }

    // --- His own foods (D53 §4, §5) --------------------------------------------------------------

    /** The spec's Pita: his, known per pita at 250 kcal, typed. The model said 165. */
    @Test
    fun `an exact match takes his food's figures and keeps the described amount`() = runTest {
        val foods = FakeFoodRepository(listOf(hisPita()))
        val pita = foods.current.single()
        val viewModel = proposedViewModelOf(aModelPita(), foods = foods)

        val row = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        assertThat(row.match).isEqualTo(FoodMatch.Exact(pita))
        assertThat(row.item.worth).isInstanceOf(Worth.YourFood::class.java)
        assertThat(row.item.amountText).isEqualTo("1")
        assertThat(row.item.foodId).isEqualTo(pita.id)
        val numbers = row.numbers!!
        assertThat(listOf(numbers.kcal, numbers.proteinG, numbers.carbsG, numbers.fatG))
            .containsExactly(250, 8, 50, 1).inOrder()
        assertThat(numbers.source).isEqualTo(Source.TYPED)
        assertThat(viewModel.accepted().single().item.foodId).isEqualTo(pita.id)
    }

    @Test
    fun `one tap uses the estimate and one tap goes back`() = runTest {
        val foods = FakeFoodRepository(listOf(hisPita()))
        val viewModel = proposedViewModelOf(aModelPita(), foods = foods)

        viewModel.useEstimate(0)
        var row = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        assertThat(row.numbers!!.kcal).isEqualTo(165)
        assertThat(row.numbers!!.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(row.numbers!!.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(row.item.foodId).isNull()

        viewModel.useYourFood(0)
        row = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        assertThat(row.numbers!!.kcal).isEqualTo(250)
        assertThat(row.item.foodId).isEqualTo(foods.current.single().id)
    }

    /** An amount he typed is his, whichever worth it is multiplied by. */
    @Test
    fun `switching between his food and the estimate keeps a typed amount`() = runTest {
        val viewModel =
            proposedViewModelOf(aModelPita(), foods = FakeFoodRepository(listOf(hisPita())))

        viewModel.setAmount(0, "2")
        viewModel.useEstimate(0)
        assertThat((viewModel.state.value as ProposalUiState.Proposed).rows[0].numbers!!.kcal)
            .isEqualTo(330)

        viewModel.useYourFood(0)
        assertThat((viewModel.state.value as ProposalUiState.Proposed).rows[0].numbers!!.kcal)
            .isEqualTo(500)
    }

    /**
     * The spec's Hamburger bun (§5): his is known per 100 g at 270 kcal, the model said 1 bun at
     * 150. A bun cannot be costed from a per-100 g figure, so the estimate stays until he switches
     * to grams — and then the box is empty, because a number put there would look like his.
     */
    @Test
    fun `his food counted in another unit leaves the estimate and offers the switch`() = runTest {
        val foods = FakeFoodRepository(listOf(hisBun()))
        val bun = foods.current.single()
        val viewModel = proposedViewModelOf(aBun(), foods = foods)

        var row = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        assertThat(row.match).isEqualTo(FoodMatch.Exact(bun))
        assertThat(row.item.worth).isInstanceOf(Worth.Estimated::class.java)
        assertThat(row.numbers!!.kcal).isEqualTo(150)

        viewModel.countInFoodUnit(0)
        var proposed = viewModel.state.value as ProposalUiState.Proposed
        row = proposed.rows[0]
        assertThat(row.item.unit).isEqualTo("g")
        assertThat(row.item.amountText).isEmpty()
        assertThat(proposed.blockedBy).isEqualTo(0)
        assertThat(viewModel.accepted()).isEmpty()

        viewModel.setAmount(0, "60")
        row = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        val numbers = row.numbers!!
        // 270 × 0.6 = 162; 9 × 0.6 = 5.4 → 5; 50 × 0.6 = 30; 4 × 0.6 = 2.4 → 2.
        assertThat(listOf(numbers.kcal, numbers.proteinG, numbers.carbsG, numbers.fatG))
            .containsExactly(162, 5, 30, 2).inOrder()
        assertThat(numbers.source).isEqualTo(Source.TYPED)
        assertThat(row.item.foodId).isEqualTo(bun.id)

        // The estimate returns with its own unit and amount.
        viewModel.useEstimate(0)
        proposed = viewModel.state.value as ProposalUiState.Proposed
        row = proposed.rows[0]
        assertThat(row.item.unit).isEqualTo("bun")
        assertThat(row.item.amountText).isEqualTo("1")
        assertThat(row.numbers!!.kcal).isEqualTo(150)
        assertThat(row.item.foodId).isNull()
    }

    @Test
    fun `a close match is offered, never applied by itself`() = runTest {
        val foods = FakeFoodRepository(listOf(aGreekYoghurt()))
        val greek = foods.current.single()
        val viewModel = proposedViewModelOf(
            aProposedItem(name = "Yoghurt", amount = 150.0, unit = "g"),
            foods = foods,
        )

        var row = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        assertThat(row.match).isEqualTo(FoodMatch.Close(greek))
        assertThat(row.item.worth).isInstanceOf(Worth.Estimated::class.java)
        assertThat(row.item.foodId).isNull()
        assertThat(row.item.name).isEqualTo("Yoghurt")

        viewModel.useYourFood(0)
        row = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        assertThat(row.item.name).isEqualTo("Greek yoghurt")
        assertThat(row.item.foodId).isEqualTo(greek.id)
        assertThat(row.item.worth).isInstanceOf(Worth.YourFood::class.java)

        // Taken back, it is the model's item again, under the model's name.
        viewModel.useEstimate(0)
        row = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        assertThat(row.item.name).isEqualTo("Yoghurt")
        assertThat(row.item.foodId).isNull()
    }

    @Test
    fun `a hidden food is not matched`() = runTest {
        val viewModel = proposedViewModelOf(
            aModelPita(),
            foods = FakeFoodRepository(listOf(hisPita().copy(hidden = true))),
        )

        val row = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        assertThat(row.match).isEqualTo(FoodMatch.None)
        assertThat(row.numbers!!.kcal).isEqualTo(165)
    }

    /** Typing over his food's worth changes only this entry, and the entry stays on his food. */
    @Test
    fun `a worth typed over his food keeps the food and still offers the estimate`() = runTest {
        val foods = FakeFoodRepository(listOf(hisPita()))
        val viewModel = proposedViewModelOf(aModelPita(), foods = foods)

        viewModel.openWorth(0)
        viewModel.setWorthBox(0, WorthFigure.KCAL, "240")

        val row = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        assertThat(row.item.worth).isInstanceOf(Worth.Typed::class.java)
        assertThat(row.item.foodId).isEqualTo(foods.current.single().id)
        assertThat(row.numbers!!.kcal).isEqualTo(240)
        // His food itself is not touched by typing here.
        assertThat(foods.current.single().facts).isEqualTo(hisPita().facts)

        viewModel.useEstimate(0)
        val back = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        assertThat(back.numbers!!.kcal).isEqualTo(165)
        assertThat(back.editingWorth).isNull()
    }

    /**
     * His food's worth line prints whole numbers, as a row would log them; the boxes open on the
     * food's own figures. Opened on the line's, a 0.5 g fat he left alone would be saved as 1 g —
     * typed, as his — because he changed the calories.
     */
    @Test
    fun `a figure left alone over his food keeps the food's own decimals`() = runTest {
        val halfGramFat = hisPita().copy(
            facts = FoodFacts(perUnit = PerUnit("pita", Nutrients(250.0, 8.0, 50.0, 0.5), typed)),
        )
        val viewModel =
            proposedViewModelOf(aModelPita(), foods = FakeFoodRepository(listOf(halfGramFat)))

        viewModel.openWorth(0)
        val boxes = (viewModel.state.value as ProposalUiState.Proposed).rows[0].editingWorth!!
        assertThat(boxes.typed).containsExactly("250", "8", "50", "0.5").inOrder()

        viewModel.setWorthBox(0, WorthFigure.KCAL, "240")
        viewModel.setAmount(0, "4")

        val row = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        assertThat(row.item.worth)
            .isEqualTo(Worth.Typed(Rate(Nutrients(240.0, 8.0, 50.0, 0.5), Per.ONE)))
        // Four pitas at 0.5 g is 2 g of fat; at a rounded 1 g it would have been 4.
        assertThat(row.numbers!!.fatG).isEqualTo(2)
    }

    // --- What a saved row teaches its food (D53 §3) ----------------------------------------------

    /** The estimate's own worth goes with the row, unrounded, for the food it lands on. */
    @Test
    fun `a row on the estimate hands over its worth for its food to learn`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.setAmount(0, "150")
        val burger = viewModel.accepted()[0]

        assertThat(burger.taught!!.per100g!!.nutrients).isEqualTo(Nutrients(250.0, 18.0, 0.0, 20.0))
        assertThat(burger.taught!!.per100g!!.provenance.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(burger.brand).isNull()
    }

    /** His food's figures, or his typing over them: the food is changed in My foods, not here. */
    @Test
    fun `a row on his food hands over nothing to teach`() = runTest {
        val viewModel =
            proposedViewModelOf(aModelPita(), foods = FakeFoodRepository(listOf(hisPita())))
        assertThat(viewModel.accepted().single().taught).isNull()

        viewModel.openWorth(0)
        viewModel.setWorthBox(0, WorthFigure.KCAL, "240")
        assertThat(viewModel.accepted().single().taught).isNull()
    }

    /**
     * A branded food is only ever a close match (identity is name and brand; the model names none).
     * Taken, and unable to cost a glass, the row stays the estimate under the food's name — and so
     * it must carry the food's brand, or saving it would make an unbranded food of the same name
     * beside his. With the brand, it lands on his food as the §5 rows do, and teaches it what a
     * glass is worth as an estimate, beside the per-100 g he typed.
     */
    @Test
    fun `a branded close match taken but not counted in its unit keeps the food's brand`() = runTest {
        val foods = FakeFoodRepository(listOf(hisOatDrink()))
        val oat = foods.current.single()
        val viewModel = proposedViewModelOf(aGlassOfOatDrink(), foods = foods)
        assertThat((viewModel.state.value as ProposalUiState.Proposed).rows[0].match)
            .isEqualTo(FoodMatch.Close(oat))
        assertThat(viewModel.accepted().single().brand).isNull()

        viewModel.useYourFood(0)

        val row = (viewModel.state.value as ProposalUiState.Proposed).rows[0]
        assertThat(row.item.foodId).isNull()
        assertThat(row.item.worth).isInstanceOf(Worth.Estimated::class.java)
        val toLog = viewModel.accepted().single()
        assertThat(toLog.item.name).isEqualTo("Oat drink")
        assertThat(toLog.brand).isEqualTo("Acme Oats")

        val attached = LoggedFoods(foods).attach(viewModel.accepted())
        assertThat(foods.current).hasSize(1)
        assertThat(attached.item.foodId).isEqualTo(oat.id)
        assertThat(foods.current.single().facts.per100g).isEqualTo(oat.facts.per100g)
        assertThat(foods.current.single().facts.perUnit!!.unitName).isEqualTo("glass")
    }

    /** Taken back to the estimate, a close match is the model's item again, brand and all. */
    @Test
    fun `a close match taken and then given back carries no brand`() = runTest {
        val foods = FakeFoodRepository(listOf(hisOatDrink()))
        val viewModel = proposedViewModelOf(
            aProposedItem(
                name = "Oat",
                amount = 250.0,
                unit = "g",
                rate = Rate(Nutrients(45.0, 1.0, 6.5, 1.5), Per.HUNDRED),
            ),
            foods = foods,
        )

        viewModel.useYourFood(0)
        assertThat((viewModel.state.value as ProposalUiState.Proposed).rows[0].onYourFood).isTrue()
        viewModel.useEstimate(0)

        val toLog = viewModel.accepted().single()
        assertThat(toLog.item.name).isEqualTo("Oat")
        assertThat(toLog.brand).isNull()
    }

    /** D16: matching happens on the phone, after the reply — nothing is read while it is asked. */
    @Test
    fun `no food is read before the answer arrives`() = runTest {
        val answer = CompletableDeferred<EstimateResult>()
        val estimator = object : MealEstimator {
            override suspend fun estimate(description: String, moreDetail: String?) = answer.await()
        }
        val foods = CountingFoods(FakeFoodRepository(listOf(hisPita())))
        val viewModel = ProposalViewModel(estimator, ProblemLog.NONE, foods)

        viewModel.describe("a pita")
        advanceUntilIdle()
        assertThat(viewModel.state.value).isEqualTo(ProposalUiState.Waiting)
        assertThat(foods.reads).isEqualTo(0)

        answer.complete(EstimateResult.Proposed(aProposal(items = listOf(aModelPita()))))
        advanceUntilIdle()
        assertThat(foods.reads).isEqualTo(1)
    }

    @Test
    fun `nothing about his foods reaches the estimator`() = runTest {
        val estimator =
            FakeEstimator(EstimateResult.Proposed(aProposal(items = listOf(aModelPita()))))
        val viewModel =
            ProposalViewModel(estimator, ProblemLog.NONE, FakeFoodRepository(listOf(hisPita())))

        viewModel.describe("a pita with hummus")
        advanceUntilIdle()

        assertThat(estimator.lastDescription).isEqualTo("a pita with hummus")
        assertThat(estimator.lastDetail).isNull()
    }

    @Test
    fun `a row can be removed, and removing the last one starts over`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.remove(1)
        assertThat((viewModel.state.value as ProposalUiState.Proposed).rows).hasSize(1)

        viewModel.remove(0)
        assertThat(viewModel.state.value).isInstanceOf(ProposalUiState.Describing::class.java)
    }

    @Test
    fun `a failure keeps the owner's words and says what went wrong`() = runTest {
        val viewModel =
            ProposalViewModel(
                FakeEstimator(EstimateResult.Unreachable),
                ProblemLog.NONE,
                FakeFoodRepository(),
            )

        viewModel.describe("risotto with mozzarella")
        advanceUntilIdle()

        val describing = viewModel.state.value as ProposalUiState.Describing
        assertThat(describing.failure).contains("Could not reach")
        assertThat(viewModel.description).isEqualTo("risotto with mozzarella")
    }

    /**
     * No amount even when asked twice: nothing is proposed, and the owner is told which items and
     * how to give amounts himself (D34). His words stay, so saying the amount costs one sentence.
     */
    @Test
    fun `a reply that would not give amounts says which, and how to give them`() = runTest {
        val viewModel =
            ProposalViewModel(
                FakeEstimator(EstimateResult.AmountMissing(listOf("Stew"))),
                ProblemLog.NONE,
                FakeFoodRepository(),
            )

        viewModel.describe("stew")
        advanceUntilIdle()

        val failure = (viewModel.state.value as ProposalUiState.Describing).failure
        assertThat(failure).contains("Stew")
        assertThat(failure).contains("Say the amounts in your description")
        assertThat(viewModel.description).isEqualTo("stew")
    }

    @Test
    fun `no key says so, and does not pretend the network failed`() = runTest {
        val viewModel = ProposalViewModel(
            FakeEstimator(EstimateResult.NoKey),
            ProblemLog.NONE,
            FakeFoodRepository(),
        )

        viewModel.describe("risotto")
        advanceUntilIdle()

        assertThat((viewModel.state.value as ProposalUiState.Describing).failure)
            .contains("No API key")
    }

    /**
     * Public issue #11: the only failure the screen offers a way to settings for is the missing key.
     */
    @Test
    fun `no key offers the way to the key, and keeps the words`() = runTest {
        val viewModel = ProposalViewModel(
            FakeEstimator(EstimateResult.NoKey),
            ProblemLog.NONE,
            FakeFoodRepository(),
        )

        viewModel.describe("risotto")
        advanceUntilIdle()

        assertThat((viewModel.state.value as ProposalUiState.Describing).needsKey).isTrue()
        assertThat(viewModel.description).isEqualTo("risotto")
    }

    @Test
    fun `a failure that is not the key offers no way to it`() = runTest {
        val viewModel =
            ProposalViewModel(
                FakeEstimator(EstimateResult.Unreachable),
                ProblemLog.NONE,
                FakeFoodRepository(),
            )

        viewModel.describe("risotto")
        advanceUntilIdle()

        assertThat((viewModel.state.value as ProposalUiState.Describing).needsKey).isFalse()
    }

    /**
     * Going to add the key takes the complaint down with it: coming back with a key saved to a
     * screen still saying there is none would be the screen contradicting settings.
     */
    @Test
    fun `leaving to add the key clears the complaint and keeps the words`() = runTest {
        val viewModel = ProposalViewModel(
            FakeEstimator(EstimateResult.NoKey),
            ProblemLog.NONE,
            FakeFoodRepository(),
        )
        viewModel.describe("risotto")
        advanceUntilIdle()

        viewModel.leaveToAddKey()

        assertThat(viewModel.state.value).isEqualTo(ProposalUiState.Describing())
        assertThat(viewModel.description).isEqualTo("risotto")
    }

    @Test
    fun `telling it more asks again with both the original and the addition`() = runTest {
        val estimator = FakeEstimator(EstimateResult.Proposed(aProposal()))
        val viewModel = ProposalViewModel(estimator, ProblemLog.NONE, FakeFoodRepository())
        viewModel.describe("risotto with mozzarella")
        advanceUntilIdle()

        viewModel.tellItMore("small bowl, half the cheese")
        advanceUntilIdle()

        assertThat(estimator.lastDescription).isEqualTo("risotto with mozzarella")
        assertThat(estimator.lastDetail).isEqualTo("small bowl, half the cheese")
    }

    @Test
    fun `an empty description asks nothing at all`() = runTest {
        val estimator = FakeEstimator(EstimateResult.Proposed(aProposal()))

        ProposalViewModel(estimator, ProblemLog.NONE, FakeFoodRepository()).describe("   ")
        advanceUntilIdle()

        assertThat(estimator.calls).isEqualTo(0)
    }

    @Test
    fun `words carried in from the search start the description already filled`() = runTest {
        val viewModel = ProposalViewModel(
            FakeEstimator(EstimateResult.Proposed(aProposal())),
            ProblemLog.NONE,
            FakeFoodRepository(),
            SavedStateHandle(mapOf("text" to "shakshuka")),
        )

        assertThat(viewModel.description).isEqualTo("shakshuka")
        assertThat(viewModel.state.value).isInstanceOf(ProposalUiState.Describing::class.java)
    }

    /**
     * D8, and the most important assertion in this file: arriving with the box filled is not the
     * same as asking. The model is asked when the owner presses the button, never on his behalf.
     */
    @Test
    fun `carried words do not ask the model by themselves`() = runTest {
        val estimator = FakeEstimator(EstimateResult.Proposed(aProposal()))

        ProposalViewModel(
            estimator,
            ProblemLog.NONE,
            FakeFoodRepository(),
            SavedStateHandle(mapOf("text" to "shakshuka")),
        )
        advanceUntilIdle()

        assertThat(estimator.calls).isEqualTo(0)
    }

    /** Guards every call site above, which constructs the view model with no handle at all. */
    @Test
    fun `no carried words leaves the description empty`() = runTest {
        val estimator = FakeEstimator(EstimateResult.Proposed(aProposal()))

        val viewModel =
            ProposalViewModel(estimator, ProblemLog.NONE, FakeFoodRepository(), SavedStateHandle())
        advanceUntilIdle()

        assertThat(viewModel.description).isEqualTo("")
        assertThat(estimator.calls).isEqualTo(0)
    }

    /** The seed is read once, at construction, so starting over cannot resurrect it. */
    @Test
    fun `starting over clears words that were carried in`() = runTest {
        val viewModel = ProposalViewModel(
            FakeEstimator(EstimateResult.Proposed(aProposal())),
            ProblemLog.NONE,
            FakeFoodRepository(),
            SavedStateHandle(mapOf("text" to "shakshuka")),
        )

        viewModel.startOver()

        assertThat(viewModel.description).isEqualTo("")
        assertThat(viewModel.state.value).isInstanceOf(ProposalUiState.Describing::class.java)
    }

    /**
     * Starting over leaves nothing that could be accepted a second time (D46, issue #24).
     *
     * Both ways off this screen end the same way, and must: accepting plainly starts over, and so
     * does keeping what was accepted as a named meal. Left standing, the answer would still be on
     * the screen when he came back to it — with rows already on the day — and one more press would
     * log every one of them again.
     */
    @Test
    fun `starting over leaves nothing that could be accepted again`() = runTest {
        val viewModel =
            ProposalViewModel(
                FakeEstimator(EstimateResult.Proposed(aProposal())),
                ProblemLog.NONE,
                FakeFoodRepository(),
            )
        viewModel.describe("risotto with mozzarella")
        advanceUntilIdle()
        assertThat(viewModel.accepted()).isNotEmpty()

        viewModel.startOver()

        assertThat(viewModel.accepted()).isEmpty()
        assertThat(viewModel.state.value).isInstanceOf(ProposalUiState.Describing::class.java)
    }

    @Test
    fun `telling it more works from words that were carried in`() = runTest {
        val estimator = FakeEstimator(EstimateResult.Proposed(aProposal()))
        val viewModel =
            ProposalViewModel(
                estimator,
                ProblemLog.NONE,
                FakeFoodRepository(),
                SavedStateHandle(mapOf("text" to "shakshuka")),
            )

        viewModel.describe("shakshuka")
        advanceUntilIdle()
        viewModel.tellItMore("with two eggs")
        advanceUntilIdle()

        assertThat(estimator.lastDescription).isEqualTo("shakshuka")
        assertThat(estimator.lastDetail).isEqualTo("with two eggs")
    }

    /**
     * The estimator reports its own failures as results, so this is the case it did not foresee. The
     * owner is left where any failure leaves him — describing, his words kept — and it is written
     * down. An exception that escaped would fail this on its own, because `runTest` reports it.
     */
    @Test
    fun `an estimator that throws leaves his words and says nothing was changed`() = runTest {
        val throwing = object : MealEstimator {
            override suspend fun estimate(description: String, moreDetail: String?): EstimateResult =
                throw IllegalStateException("unexpected reply")
        }
        val problems = RecordingProblemLog()
        val viewModel = ProposalViewModel(throwing, problems, FakeFoodRepository())

        viewModel.describe("risotto")
        advanceUntilIdle()

        val state = viewModel.state.value as ProposalUiState.Describing
        assertThat(state.refused).isEqualTo(ActionRefused.NOTHING_CHANGED)
        assertThat(viewModel.description).isEqualTo("risotto")
        assertThat(problems.recorded.single().kind).isEqualTo("refused")
    }

    private fun proposedViewModel(): ProposalViewModel = proposedViewModelOf(aProposedItem(), aBun())

    private fun proposedViewModelOf(
        vararg items: ProposedItem,
        foods: FoodRepository = FakeFoodRepository(),
    ): ProposalViewModel {
        val viewModel = ProposalViewModel(
            FakeEstimator(EstimateResult.Proposed(aProposal(items = items.toList()))),
            ProblemLog.NONE,
            foods,
        )
        viewModel.describe("a burger in a bun")
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    private val typed = Provenance(Source.TYPED, null, setAtMillis = 0)

    /** The spec's invented Pita: per pita, 250 kcal · P 8 · C 50 · F 1, typed. */
    private fun hisPita() = Food(
        name = "Pita",
        facts = FoodFacts(perUnit = PerUnit("pita", Nutrients(250.0, 8.0, 50.0, 1.0), typed)),
    )

    /** The spec's invented Hamburger bun: per 100 g only, 270 kcal · P 9 · C 50 · F 4, typed. */
    private fun hisBun() = Food(
        name = "Hamburger bun",
        facts = FoodFacts(per100g = PerHundredGrams(Nutrients(270.0, 9.0, 50.0, 4.0), typed)),
    )

    /** Any figures: it is only ever the close match for the model's "Yoghurt". */
    private fun aGreekYoghurt() = Food(
        name = "Greek yoghurt",
        facts = FoodFacts(per100g = PerHundredGrams(Nutrients(97.0, 9.0, 4.0, 5.0), typed)),
    )

    /** Invented: a branded oat drink he knows per 100 g only, typed — never per glass. */
    private fun hisOatDrink() = Food(
        name = "Oat drink",
        brand = "Acme Oats",
        facts = FoodFacts(per100g = PerHundredGrams(Nutrients(45.0, 1.0, 6.5, 1.5), typed)),
    )

    /** The model's glass of it, per glass. */
    private fun aGlassOfOatDrink() = aProposedItem(
        name = "Oat drink",
        amount = 1.0,
        unit = "glass",
        rate = Rate(Nutrients(120.0, 3.0, 16.0, 5.0), Per.ONE),
    )

    /** Counts every time the foods on offer are read. */
    private class CountingFoods(private val inner: FakeFoodRepository) : FoodRepository by inner {
        var reads = 0
            private set

        override fun observeOffered(): Flow<List<Food>> =
            inner.observeOffered().onStart { reads++ }
    }

    private fun amountOf(viewModel: ProposalViewModel, index: Int): String =
        (viewModel.state.value as ProposalUiState.Proposed).rows[index].item.amountText

    private class FakeEstimator(private val result: EstimateResult) : MealEstimator {
        var calls = 0
            private set
        var lastDescription: String? = null
            private set
        var lastDetail: String? = null
            private set

        override suspend fun estimate(description: String, moreDetail: String?): EstimateResult {
            calls++
            lastDescription = description
            lastDetail = moreDetail
            return result
        }
    }
}
