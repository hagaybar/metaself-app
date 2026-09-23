package com.metaself.app.data.product

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.product.Product
import com.metaself.app.domain.product.ProductField
import com.metaself.app.domain.product.ProductForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What came back from a scan, so the screen can say something true about each case. */
sealed interface Lookup {

    data class Found(val product: Product, val fromThisPhone: Boolean) : Lookup

    /**
     * The database has the packet but not every figure (D40, issue #30): the label form, filled
     * with what it has, and which figures he must copy off the packet. Nothing has been saved.
     */
    data class Incomplete(val form: ProductForm, val missing: List<ProductField>) : Lookup

    /** The database answered, and has never heard of this barcode. */
    data object Unknown : Lookup

    /** No answer at all: no signal, a timeout, the service down. Different from Unknown. */
    data object Unreachable : Lookup
}

/**
 * Finding a product: this phone first, the world second.
 *
 * The local table is asked every time and the network only on a miss, so a packet scanned once is a
 * packet that works with no signal for ever afterwards (D8). Every successful lookup is written
 * back, which is what makes that true without anybody having to think about caching.
 *
 * An incomplete one is not (D40, issue #30). A packet saved with a figure the database left out
 * would need a number in that place, and any number put there is one the label never stated; and
 * once on the phone it would be found there for ever and the database never asked again. So the
 * lookup hands back the filled form instead, and the packet reaches the phone only when he saves
 * it with the gap copied off the packet.
 *
 * A failure is never an exception reaching the screen. "Never heard of it" and "could not ask" are
 * different facts and the owner is told which, because one means type it in and the other means try
 * again in a minute.
 */
@Singleton
class ProductRepository internal constructor(
    private val products: ProductDao,
    private val problems: ProblemLog,
    /**
     * Where a reply comes from, for a test to hand one in; null is the real network. The lookup
     * could not be tested at all while the request was sealed inside it, which is how an invented
     * zero reached the phone unnoticed.
     */
    private val fetchReply: ((String) -> String?)?,
) {

    @Inject
    constructor(products: ProductDao, problems: ProblemLog) : this(products, problems, null)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    suspend fun lookUp(barcode: String, nowMillis: Long): Lookup = withContext(Dispatchers.IO) {
        if (barcode.isBlank()) return@withContext Lookup.Unknown

        products.find(barcode)?.toDomain()?.let {
            return@withContext Lookup.Found(it, fromThisPhone = true)
        }

        val body = (fetchReply ?: ::fetch)(barcode) ?: return@withContext Lookup.Unreachable

        when (val reply = OpenFoodFacts.read(barcode, body)) {
            is OpenFoodFacts.Reply.Usable -> {
                products.save(reply.product.toEntity(nowMillis))
                Lookup.Found(reply.product, fromThisPhone = false)
            }
            // Returned before any save, so the next scan misses on the phone and asks again.
            is OpenFoodFacts.Reply.Incomplete -> Lookup.Incomplete(reply.form, reply.missing)
            OpenFoodFacts.Reply.Unusable -> Lookup.Unknown
        }
    }

    /**
     * Keep a product the owner typed in himself.
     *
     * Saved before any attempt to contribute it, and never conditional on one. A contribution is a
     * gift to other people; his own use of the packet must not hang on whether the gift arrived.
     */
    suspend fun remember(product: Product, nowMillis: Long) = withContext(Dispatchers.IO) {
        products.save(product.toEntity(nowMillis))
    }

    private fun fetch(barcode: String): String? = runCatching {
        val request = Request.Builder()
            .url(OpenFoodFacts.urlFor(barcode))
            // Open Food Facts asks every application to name itself and leave a way to be reached.
            // It is a volunteer database and that is a small thing to give it.
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }.onFailure { error ->
        // Broad on purpose: a lookup that cannot happen must never be able to crash a log entry.
        // The same reasoning as the model seam, and for the same reason it was needed there.
        problems.record(
            kind = "barcode",
            detail = "lookup failed: ${error::class.java.simpleName} ${error.message}",
        )
    }.getOrNull()

    private companion object {
        const val TIMEOUT_SECONDS = 10L
        const val USER_AGENT = "MetaSelf/1.0 (personal single-user app; via GitHub hagaybar/metaself-app)"
    }
}
