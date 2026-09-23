package com.metaself.app.ui.screen.scan

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.product.Lookup
import com.metaself.app.data.product.OffCredentials
import com.metaself.app.data.product.OpenFoodFactsWriter
import com.metaself.app.data.product.ProductDao
import com.metaself.app.data.product.ProductEntity
import com.metaself.app.data.product.ProductRepository
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.product.Product
import com.metaself.app.domain.product.ProductField
import com.metaself.app.domain.product.ProductForm
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.RecordingProblemLog
import com.metaself.app.ui.scan.ScanWording
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * What a scan means, decided away from the camera.
 *
 * Nothing here touches a camera or a network. The camera cannot be tested on this machine at all, so
 * everything that could be moved out of it was.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private val bamba = Product(
        barcode = "7290000066318",
        name = "במבה",
        brand = "אסם",
        kcalPer100g = 535.0,
        proteinPer100g = 17.0,
        carbsPer100g = 49.0,
        fatPer100g = 30.0,
        servingSizeG = 80.0,
    )

    @Test
    fun `the amount opens at the package's own serving`() {
        assertThat(ScanUiState.Found(bamba, false, "80").gramsOrNull).isEqualTo(80.0)
    }

    @Test
    fun `an amount that is not a number logs nothing`() {
        assertThat(ScanUiState.Found(bamba, false, "a lot").gramsOrNull).isNull()
    }

    @Test
    fun `an amount of nothing logs nothing`() {
        assertThat(ScanUiState.Found(bamba, false, "0").gramsOrNull).isNull()
        assertThat(ScanUiState.Found(bamba, false, "").gramsOrNull).isNull()
    }

    @Test
    fun `the label is stated as the label states it`() {
        assertThat(ScanWording.per100g(bamba)).isEqualTo("535 kcal · P 17 · C 49 · F 30 per 100 g")
    }

    /** D9: he is agreeing to a number, so the arithmetic happens in front of him. */
    @Test
    fun `what will be logged is worked out on screen`() {
        assertThat(ScanWording.forAmount(bamba, 80.0)).isEqualTo("428 kcal · P 14 · C 39 · F 24")
    }

    @Test
    fun `an amount that makes no sense produces no total to agree to`() {
        assertThat(ScanWording.forAmount(bamba, null)).isNull()
        assertThat(ScanWording.forAmount(bamba, 0.0)).isNull()
    }

    /**
     * The crash the issue names: the "what will be logged" line rounded a not-a-number while the
     * screen was drawing. No such product reaches the screen since D39 (issue #31); if one did, the
     * line would simply offer no total.
     */
    @Test
    fun `a product whose figure is not a number gives no total, and does not throw`() {
        assertThat(ScanWording.forAmount(bamba.copy(fatPer100g = Double.NaN), 80.0)).isNull()
    }

    /** A product already on the phone needs no network, which is what matters where there is none. */
    @Test
    fun `it says whether anything was sent`() {
        assertThat(ScanWording.origin(fromThisPhone = true)).contains("Nothing was sent")
        assertThat(ScanWording.origin(fromThisPhone = false)).contains("Open Food Facts")
    }

    /** "Never heard of it" and "could not ask" mean different things to do next. */
    @Test
    fun `an unknown product and an unreachable database are told apart`() {
        assertThat(ScanWording.notFound(couldNotAsk = false)).contains("not in the food database")
        assertThat(ScanWording.notFound(couldNotAsk = true)).contains("Could not reach")
    }

    @Test
    fun `what gets logged is marked as coming from a label`() {
        val item = bamba.toFoodItem(80.0)!!

        assertThat(item.source).isEqualTo(Source.LABEL)
        assertThat(item.confidence).isNull()
        assertThat(item.name).isEqualTo("במבה")
    }

    // --- A packet the database has without every figure opens the label form (D40, #30) ---

    private val withoutFat = ProductForm.prefilled(
        barcode = "7290000066318",
        name = "במבה",
        brand = "אסם",
        kcalPer100g = 535.0,
        proteinPer100g = 17.0,
        carbsPer100g = 49.0,
        fatPer100g = null,
        servingSizeG = 80.0,
    )

    /**
     * Not the found screen: the typed label form, filled with what the database had, and carrying
     * which figures it lacked so the screen can say so. Nothing is saved until he saves.
     */
    @Test
    fun `a packet with a missing figure opens the label form, filled`() {
        val state = scanStateFor(
            "7290000066318",
            Lookup.Incomplete(withoutFat, listOf(ProductField.FAT)),
        )

        assertThat(state).isEqualTo(
            ScanUiState.Adding(
                withoutFat,
                showErrors = false,
                databaseLacks = listOf(ProductField.FAT),
            ),
        )
    }

    /** The mapping moved out of the view model to be testable; the three old cases did not move. */
    @Test
    fun `found, unknown and unreachable land where they did`() {
        val barcode = "7290000066318"

        assertThat(scanStateFor(barcode, Lookup.Found(bamba, fromThisPhone = false)))
            .isEqualTo(ScanUiState.Found(bamba, false, "80"))
        assertThat(scanStateFor(barcode, Lookup.Found(bamba, fromThisPhone = true)))
            .isEqualTo(ScanUiState.Found(bamba, true, "80"))
        assertThat(scanStateFor(barcode, Lookup.Unknown))
            .isEqualTo(ScanUiState.NotFound(barcode, couldNotAsk = false))
        assertThat(scanStateFor(barcode, Lookup.Unreachable))
            .isEqualTo(ScanUiState.NotFound(barcode, couldNotAsk = true))
    }

    /**
     * The sentence describes the database, which his typing does not change, so it stays; the
     * errors clear once he types, as they always have. Rebuilding the state on each keystroke — how
     * typing worked before — would have dropped the sentence at the first digit.
     *
     * A known gap: this pins [ScanUiState.Adding.typed], not the view model's `setForm`, which only
     * delegates to it. The view model cannot be built in a unit test (it needs the phone's secret
     * store), so that delegation is checked by reading it. Were `setForm` to go back to building a
     * fresh `Adding(form)`, the sentence would vanish at the first keystroke and no test here
     * would fail.
     */
    @Test
    fun `typing keeps the sentence and clears the errors`() {
        val typed = withoutFat.copy(fatPer100g = "3")

        val before = ScanUiState.Adding(
            withoutFat,
            showErrors = true,
            databaseLacks = listOf(ProductField.FAT),
        )

        val state = before.typed(typed)

        assertThat(state.databaseLacks).containsExactly(ProductField.FAT)
        assertThat(state.showErrors).isFalse()
        assertThat(state.form).isEqualTo(typed)
    }

    // --- The grams box has a ceiling, and Log it never closes silently (D42, issue #32) ---

    /**
     * "Infinity" closed the screen with nothing logged; "1e300" logged a 2,147,483,647-kcal row.
     * Both, and anything past 5000 g, are now no amount to log — and, unlike a blank, a zero or a
     * word, they are too much, which is what the box says out loud. The quiet cases stay quiet:
     * "0" is a stage of typing "0.5", and a refusal flashing under his fingers helps nobody.
     */
    @Test
    fun `the grams box refuses Infinity, 1e300 and past 5000, and says so`() {
        listOf("Infinity", "1e300", "5000.5").forEach { typed ->
            val found = ScanUiState.Found(bamba, false, typed)

            assertThat(found.gramsOrNull).isNull()
            assertThat(found.gramsTooMuch).isTrue()
        }

        val atTheCeiling = ScanUiState.Found(bamba, false, "5000")
        // The ceiling the line under the box names is the one the box judged against.
        assertThat(atTheCeiling.most).isEqualTo(5_000.0)
        assertThat(atTheCeiling.gramsOrNull).isEqualTo(5_000.0)
        assertThat(atTheCeiling.gramsTooMuch).isFalse()

        listOf("", "0", "a lot").forEach { typed ->
            val found = ScanUiState.Found(bamba, false, typed)

            assertThat(found.gramsOrNull).isNull()
            assertThat(found.gramsTooMuch).isFalse()
        }
    }

    /**
     * The decision the nav host acts on, moved into the state where it can be tested: what Log it
     * would put on the day, and — when that is nothing — why, so the screen stays and says so
     * instead of closing as if it had worked. The reason clears once he types.
     *
     * Both "Nothing was logged" lines are last-line defences, not reachable by ordinary taps: Log it
     * is disabled while the amount is not one to log, and no door lets an unbelievable packet reach
     * this screen (D39, D42). They exist so that if either guard ever slips, the screen stays and
     * says why rather than closing as if it had worked.
     *
     * A known gap, as for [ScanUiState.Adding.typed]: the view model's `save()` and the nav host's
     * pop-only-when-logged wiring are covered by review, not by a test. The view model cannot be
     * built in a unit test, and no test draws the nav host's scan route.
     */
    @Test
    fun `Log it with nothing to log stays and says why`() {
        val absurdPacket = ScanUiState.Found(bamba.copy(kcalPer100g = 1e12), false, "80")
        assertThat(absurdPacket.toLog()).isNull()
        assertThat(absurdPacket.refusedSave().nothingLogged).isEqualTo(NothingLogged.PACKET)

        val absurdAmount = ScanUiState.Found(bamba, false, "Infinity")
        assertThat(absurdAmount.toLog()).isNull()
        assertThat(absurdAmount.refusedSave().nothingLogged).isEqualTo(NothingLogged.AMOUNT)

        val usual = ScanUiState.Found(bamba, false, "80")
        assertThat(usual.nothingLogged).isNull()
        val row = usual.toLog()!!
        assertThat(row.kcal).isEqualTo(428)
        assertThat(row.portionAmount).isEqualTo(80.0)
        assertThat(row.source).isEqualTo(Source.LABEL)

        val typedAgain = absurdAmount.refusedSave().typed("90")
        assertThat(typedAgain.nothingLogged).isNull()
        assertThat(typedAgain.grams).isEqualTo("90")
        assertThat(typedAgain.gramsOrNull).isEqualTo(90.0)
    }

    // --- When an action throws -----------------------------------------------------------------------
    //
    // The phone's own table failing, not a miss: a miss is an answer the lookup already gives. An
    // exception that got past the guard would fail each of these on its own — `runTest` reports a
    // coroutine's uncaught exception when it ends. The table is read and written off the main thread,
    // so these wait for the answer rather than advancing a clock.

    @Test
    fun `a lookup that throws goes back to the camera and says it could not be opened`() =
        runTest(dispatcher) {
            val table = FailingTable(reads = true)
            val problems = RecordingProblemLog()
            val viewModel = scanning(table, problems)

            viewModel.onBarcodeRead(bamba.barcode)

            assertThat(viewModel.failed.first { it != null }).isEqualTo(ActionRefused.COULD_NOT_OPEN)
            assertThat(viewModel.state.value).isEqualTo(ScanUiState.Looking)
            assertThat(problems.recorded.single().kind).isEqualTo("refused")
            assertThat(problems.recorded.single().detail).contains("disk full")
        }

    /** The same packet is still in front of the lens; without this it would fail many times a second. */
    @Test
    fun `while a failed lookup is on screen the camera's reads are ignored`() = runTest(dispatcher) {
        val table = FailingTable(reads = true)
        val viewModel = scanning(table, RecordingProblemLog())

        viewModel.onBarcodeRead(bamba.barcode)
        viewModel.failed.first { it != null }
        viewModel.onBarcodeRead(bamba.barcode)
        viewModel.onBarcodeRead(bamba.barcode)
        advanceUntilIdle()

        assertThat(table.finds).isEqualTo(1)
        assertThat(viewModel.state.value).isEqualTo(ScanUiState.Looking)

        table.reads = false
        viewModel.dismissFailure()
        viewModel.onBarcodeRead(bamba.barcode)
        viewModel.state.first { it is ScanUiState.NotFound }
        assertThat(table.finds).isEqualTo(2)
        assertThat(viewModel.failed.value).isNull()
    }

    @Test
    fun `a typed packet that cannot be saved keeps the form and says nothing was changed`() =
        runTest(dispatcher) {
            val problems = RecordingProblemLog()
            val viewModel = scanning(FailingTable(writes = true), problems)

            viewModel.onBarcodeRead("0000000000017")
            viewModel.state.first { it is ScanUiState.NotFound }
            viewModel.addByHand()
            viewModel.setForm(typedForm("0000000000017"))
            viewModel.saveTypedProduct()

            assertThat(viewModel.failed.first { it != null }).isEqualTo(ActionRefused.NOTHING_CHANGED)
            assertThat((viewModel.state.value as ScanUiState.Adding).form.name).isEqualTo("Crackers")
            assertThat(problems.recorded.single().kind).isEqualTo("refused")
        }

    /** Nothing is sent when it throws, and the button comes back so it can be pressed again. */
    @Test
    fun `a contribution that throws gives the button back and says nothing was changed`() =
        runTest(dispatcher) {
            var accountReadable = true
            val problems = RecordingProblemLog()
            val viewModel = scanning(
                FailingTable(),
                problems,
                credentials = {
                    check(accountReadable) { "disk full" }
                    OffCredentials("someone", "secret")
                },
            )

            viewModel.onBarcodeRead("0000000000017")
            viewModel.state.first { it is ScanUiState.NotFound }
            viewModel.addByHand()
            viewModel.setForm(typedForm("0000000000017"))
            viewModel.saveTypedProduct()
            viewModel.state.first { it is ScanUiState.Found }

            accountReadable = false
            viewModel.contribute()
            advanceUntilIdle()

            assertThat(viewModel.failed.value).isEqualTo(ActionRefused.NOTHING_CHANGED)
            assertThat((viewModel.state.value as ScanUiState.Found).contributing).isFalse()
            assertThat(problems.recorded.single().kind).isEqualTo("refused")
        }

    private fun typedForm(barcode: String) = ProductForm(
        barcode = barcode,
        name = "Crackers",
        kcalPer100g = "400",
        proteinPer100g = "10",
        carbsPer100g = "70",
        fatPer100g = "10",
    )

    private fun scanning(
        table: ProductDao,
        problems: RecordingProblemLog,
        credentials: () -> OffCredentials = { OffCredentials("", "") },
    ) = ScanViewModel(
        // No network: a miss on the phone is answered "never heard of it".
        products = ProductRepository(table, ProblemLog.NONE, fetchReply = { """{"status":0}""" }),
        writer = OpenFoodFactsWriter(ProblemLog.NONE),
        credentials = credentials,
        problems = problems,
    )

    /** The phone's own packet table, empty, throwing where a test says to. */
    private class FailingTable(
        var reads: Boolean = false,
        var writes: Boolean = false,
    ) : ProductDao {
        var finds = 0
        private val saved = mutableMapOf<String, ProductEntity>()

        override suspend fun find(barcode: String): ProductEntity? {
            finds++
            check(!reads) { "disk full" }
            return saved[barcode]
        }

        override suspend fun save(product: ProductEntity) {
            check(!writes) { "disk full" }
            saved[product.barcode] = product
        }
    }
}
