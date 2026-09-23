package com.metaself.app.ui.screen.scan

import com.metaself.app.data.product.Lookup
import com.metaself.app.domain.amount.BelievableAmount
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.product.Product
import com.metaself.app.domain.product.ProductField
import com.metaself.app.domain.product.ProductForm

/** What the scanning screen is showing. */
sealed interface ScanUiState {

    /** The camera is open and nothing has been read yet. */
    data object Looking : ScanUiState

    /** A barcode was read and is being resolved. */
    data object Resolving : ScanUiState

    /**
     * @property fromThisPhone true when it came from the local table, so the screen can say that it
     *   needed no network — which is the reassurance that matters in a supermarket basement.
     */
    data class Found(
        val product: Product,
        val fromThisPhone: Boolean,
        val grams: String,
        /** True when he has just typed this product in, which is when contributing is offered. */
        val justAdded: Boolean = false,
        /** False with no Open Food Facts account: no button at all beats one that always fails. */
        val canContribute: Boolean = false,
        val contributing: Boolean = false,
        val contributionMessage: String? = null,
        /**
         * Why the last Log it put nothing on the day, shown under the button; null until he presses
         * it and nothing could be logged, and again once he types.
         */
        val nothingLogged: NothingLogged? = null,
    ) : ScanUiState {

        /**
         * The ceiling on the grams box: 5000 g (D42). Here, once, so that the check below and the
         * line under the box that names it read the same number.
         */
        val most: Double get() = BelievableAmount.GRAMS

        /**
         * The amount to log: above nothing and not past [most] (D42, issue #32). The parse is the
         * one this box always had — a point, no comma.
         */
        val gramsOrNull: Double?
            get() = typedGrams?.takeIf { it > 0.0 && BelievableAmount.isBelievable(it, most) }

        /**
         * True only for a number past the ceiling — "Infinity", "1e300", 6000 — which is what the
         * box says out loud. A blank, a zero or a word stay quiet, with Log it simply off: "0" is a
         * stage of typing "0.5", and a refusal flashing under his fingers helps nobody.
         */
        val gramsTooMuch: Boolean
            get() = typedGrams?.let { BelievableAmount.isTooMuch(it, most) } == true

        private val typedGrams: Double? get() = grams.trim().toDoubleOrNull()

        /** What Log it would put on the day now, or null when there is nothing it can log. */
        fun toLog(): FoodItem? = gramsOrNull?.let(product::toFoodItem)

        /**
         * Log it was pressed and [toLog] was nothing: the screen stays and says why, rather than
         * close as if it had worked (issue #32). The amount when that is what is wrong; otherwise
         * the packet, whose figures no row can be made from.
         */
        fun refusedSave(): Found = copy(
            nothingLogged = if (gramsOrNull == null) NothingLogged.AMOUNT else NothingLogged.PACKET,
        )

        /** What he typed into the grams box. The last refusal's reason clears, as elsewhere. */
        fun typed(grams: String): Found = copy(grams = grams, nothingLogged = null)
    }

    /**
     * The label form: a packet the database has never heard of, typed in whole from Add it
     * yourself — or one it has without every figure, opened filled with what it has (D40).
     *
     * @property databaseLacks the figures the database left out, in the form's order, which the
     *   screen names above the form. Empty for a form he opened himself: a blank form has nothing
     *   to say about the database.
     */
    data class Adding(
        val form: ProductForm,
        val showErrors: Boolean = false,
        val databaseLacks: List<ProductField> = emptyList(),
    ) : ScanUiState {

        /**
         * What he typed. The errors clear once he types, as they always have; what the database
         * lacks stays, because his typing does not change the database.
         */
        fun typed(form: ProductForm): Adding = copy(form = form, showErrors = false)
    }

    /**
     * Read, but nothing known about it — or nothing reachable to ask.
     *
     * The two are told apart because they mean different things to do: one means type it in, the
     * other means try again in a minute.
     */
    data class NotFound(val barcode: String, val couldNotAsk: Boolean) : ScanUiState
}

/** Why Log it put nothing on the day (D42, issue #32). */
enum class NothingLogged {
    /** The amount in the box is not one the app can log. */
    AMOUNT,

    /** The amount is fine, but the packet's figures are not ones a row can be made from. */
    PACKET,
}

/**
 * Where a lookup lands on the screen. Out of the view model, which cannot be built in a unit test,
 * so that where each result goes — the pre-filled form above all (D40) — can be tested.
 */
fun scanStateFor(barcode: String, result: Lookup): ScanUiState = when (result) {
    is Lookup.Found -> ScanUiState.Found(
        product = result.product,
        fromThisPhone = result.fromThisPhone,
        grams = trimmed(result.product.suggestedGrams),
    )

    is Lookup.Incomplete -> ScanUiState.Adding(result.form, databaseLacks = result.missing)
    Lookup.Unknown -> ScanUiState.NotFound(barcode, couldNotAsk = false)
    Lookup.Unreachable -> ScanUiState.NotFound(barcode, couldNotAsk = true)
}

/** An amount as the box shows it: "80", not "80.0". */
internal fun trimmed(grams: Double): String =
    if (grams % 1.0 == 0.0) grams.toInt().toString() else grams.toString()
