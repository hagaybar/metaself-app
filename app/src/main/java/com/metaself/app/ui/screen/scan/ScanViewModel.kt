package com.metaself.app.ui.screen.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.product.OffCredentials
import com.metaself.app.data.product.OpenFoodFactsWriter
import com.metaself.app.data.product.ProductRepository
import com.metaself.app.data.secret.SecretStore
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.product.Product
import com.metaself.app.domain.product.ProductForm
import com.metaself.app.ui.scan.ContributeWording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A barcode, resolved.
 *
 * The camera reads continuously and will report the same barcode many times a second. Only the
 * first is acted on: [onBarcodeRead] does nothing once anything other than [ScanUiState.Looking] is
 * showing, which is what stops a scan turning into a burst of identical lookups.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val products: ProductRepository,
    private val writer: OpenFoodFactsWriter,
    private val secrets: SecretStore,
) : ViewModel() {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Looking)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    fun onBarcodeRead(barcode: String) {
        if (_state.value != ScanUiState.Looking) return
        if (barcode.isBlank()) return

        _state.value = ScanUiState.Resolving
        viewModelScope.launch {
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

        viewModelScope.launch {
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

    /** Send it up. Opt-in, per product, and it never touches the copy already saved here. */
    fun contribute() {
        val found = _state.value as? ScanUiState.Found ?: return
        if (found.contributing) return

        viewModelScope.launch {
            _state.value = found.copy(contributing = true, contributionMessage = null)
            val result = writer.contribute(found.product, credentials())
            _state.value = (_state.value as? ScanUiState.Found)?.copy(
                contributing = false,
                contributionMessage = ContributeWording.result(result),
            ) ?: return@launch
        }
    }

    private fun credentials(): OffCredentials = OffCredentials(
        username = secrets.read(SecretStore.OFF_USERNAME).orEmpty(),
        password = secrets.read(SecretStore.OFF_PASSWORD).orEmpty(),
    )

    /** Point the camera again — after a miss, or after reading the wrong packet. */
    fun scanAgain() {
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
