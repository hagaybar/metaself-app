package com.metaself.app.ui.screen.scan

import androidx.lifecycle.ViewModel
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.product.OffCredentials
import com.metaself.app.data.product.OpenFoodFactsWriter
import com.metaself.app.data.product.ProductRepository
import com.metaself.app.data.secret.SecretStore
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.product.Product
import com.metaself.app.domain.product.ProductForm
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.guarded
import com.metaself.app.ui.scan.ContributeWording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * A barcode, resolved.
 *
 * The camera reads continuously and will report the same barcode many times a second. Only the
 * first is acted on: [onBarcodeRead] does nothing once anything other than [ScanUiState.Looking] is
 * showing, which is what stops a scan turning into a burst of identical lookups.
 *
 * **Nothing here takes the app down.** Every action goes through [guarded]; one that throws puts
 * [failed] at the top of the screen and is written to the problem log. The lookup and the contribution
 * already turn a missing network into an answer of their own, so what reaches the guard is the phone
 * itself failing — its own table, most likely.
 */
@HiltViewModel
class ScanViewModel internal constructor(
    private val products: ProductRepository,
    private val writer: OpenFoodFactsWriter,
    /**
     * The Open Food Facts account, read when it is needed. A function rather than the secret store,
     * for a test to hand one in: the store needs the phone's keystore, which a test does not have,
     * and that is what kept this view model out of its tests until now.
     */
    private val credentials: () -> OffCredentials,
    private val problems: ProblemLog,
) : ViewModel() {

    @Inject
    constructor(
        products: ProductRepository,
        writer: OpenFoodFactsWriter,
        secrets: SecretStore,
        problems: ProblemLog,
    ) : this(
        products,
        writer,
        {
            OffCredentials(
                username = secrets.read(SecretStore.OFF_USERNAME).orEmpty(),
                password = secrets.read(SecretStore.OFF_PASSWORD).orEmpty(),
            )
        },
        problems,
    )

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Looking)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private val _failed = MutableStateFlow<ActionRefused?>(null)

    /** The last action that threw rather than finishing, until he dismisses it or does another. */
    val failed: StateFlow<ActionRefused?> = _failed.asStateFlow()

    /**
     * Look a barcode up.
     *
     * A lookup that throws goes back to the camera with the failure above it, and the camera's
     * reads are ignored until he has dismissed it: the same packet is still in front of the lens,
     * and without that it would be read, fail and be written down again many times a second.
     */
    fun onBarcodeRead(barcode: String) {
        if (_state.value != ScanUiState.Looking) return
        if (_failed.value != null) return
        if (barcode.isBlank()) return

        _state.value = ScanUiState.Resolving
        act(ActionRefused.COULD_NOT_OPEN, onRefused = { _state.value = ScanUiState.Looking }) {
            _state.value = scanStateFor(barcode, products.lookUp(barcode, System.currentTimeMillis()))
        }
    }

    fun setGrams(grams: String) {
        val current = _state.value as? ScanUiState.Found ?: return
        _state.value = current.typed(grams)
    }

    /** Type in a packet the database has never heard of. */
    fun addByHand() {
        val barcode = (_state.value as? ScanUiState.NotFound)?.barcode ?: return
        _state.value = ScanUiState.Adding(ProductForm(barcode = barcode))
    }

    fun setForm(form: ProductForm) {
        val current = _state.value as? ScanUiState.Adding ?: return
        _state.value = current.typed(form)
    }

    /**
     * Keep what he typed, and only then offer to share it.
     *
     * Saved locally first and unconditionally: the packet works on this phone from now on whatever
     * happens next, including nothing.
     */
    fun saveTypedProduct() {
        val adding = _state.value as? ScanUiState.Adding ?: return
        val product = adding.form.toProduct()
        if (product == null) {
            _state.value = adding.copy(showErrors = true)
            return
        }

        // One write. The form stays as he typed it when it fails, so Save can simply be pressed again.
        act(ActionRefused.NOTHING_CHANGED) {
            products.remember(product, System.currentTimeMillis())
            _state.value = ScanUiState.Found(
                product = product,
                fromThisPhone = true,
                grams = trimmed(product.suggestedGrams),
                justAdded = true,
                canContribute = credentials().isUsable,
            )
        }
    }

    /**
     * Send it up. Opt-in, per product, and it never touches the copy already saved here.
     *
     * A failure to send is an answer the writer gives, not a throw; what can throw comes before the
     * request is made, so "nothing was changed" is true of it. The button comes back either way.
     */
    fun contribute() {
        val found = _state.value as? ScanUiState.Found ?: return
        if (found.contributing) return

        act(
            ActionRefused.NOTHING_CHANGED,
            onRefused = {
                _state.value = (_state.value as? ScanUiState.Found)?.copy(contributing = false)
                    ?: _state.value
            },
        ) {
            _state.value = found.copy(contributing = true, contributionMessage = null)
            val result = writer.contribute(found.product, credentials())
            _state.value = (_state.value as? ScanUiState.Found)?.copy(
                contributing = false,
                contributionMessage = ContributeWording.result(result),
            ) ?: return@act
        }
    }

    /** He has read the failure; take it down, and the camera listens again. */
    fun dismissFailure() {
        _failed.value = null
    }

    /**
     * Run one action under the guard. The last failure is let go of as the next action starts, so
     * what is on screen is always about the latest thing he did.
     */
    private fun act(how: ActionRefused, onRefused: () -> Unit = {}, block: suspend () -> Unit) {
        _failed.value = null
        guarded(problems, onRefused = {
            onRefused()
            _failed.value = how
        }) { block() }
    }

    /** Point the camera again — after a miss, or after reading the wrong packet. */
    fun scanAgain() {
        _failed.value = null
        _state.value = ScanUiState.Looking
    }

    /**
     * Log it: what goes on the day, or null — in which case the screen now says why, and the
     * caller must stay on it. Leaving with nothing logged looked exactly like success (issue #32).
     */
    fun save(): FoodItem? {
        val current = _state.value as? ScanUiState.Found ?: return null
        val item = current.toLog()
        if (item == null) _state.value = current.refusedSave()
        return item
    }

    /**
     * The packet the thing being logged came off.
     *
     * Carried into the log so the food is identified by its barcode and not only by whatever the
     * packet happens to be called — which is what makes a bar described in words last week and
     * scanned today one food rather than two.
     */
    fun scannedProduct(): Product? = (_state.value as? ScanUiState.Found)?.product
}
