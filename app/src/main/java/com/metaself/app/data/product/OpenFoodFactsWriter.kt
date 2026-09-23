package com.metaself.app.data.product

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.product.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What happened to a contribution.
 *
 * Three outcomes rather than one, because they mean different things to do: check the password, try
 * again later, or nothing at all. A single "could not send" would leave the owner guessing.
 */
sealed interface Contribution {

    data object Sent : Contribution

    /** The server answered and declined — almost always a wrong username or password. */
    data class Refused(val detail: String?) : Contribution

    data object Unreachable : Contribution
}

/**
 * Sending a product up to Open Food Facts.
 *
 * The local copy is always saved before this is called and never depends on it. A contribution is a
 * gift to other people; the owner's own use of the product must not hang on whether the gift
 * arrived.
 */
@Singleton
class OpenFoodFactsWriter @Inject constructor(
    private val problems: ProblemLog,
) {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun contribute(
        product: Product,
        credentials: OffCredentials,
    ): Contribution = withContext(Dispatchers.IO) {
        if (!credentials.isUsable) return@withContext Contribution.Refused("No account set")

        val form = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            ProductContribution.fieldsFor(product, credentials)
                .forEach { (name, value) -> addFormDataPart(name, value) }
        }.build()

        val body = runCatching {
            val request = Request.Builder()
                .url(ProductContribution.URL)
                .header("User-Agent", USER_AGENT)
                .post(form)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.onFailure { error ->
            problems.record(
                kind = "contribute",
                // The credentials are in the form, never in the log.
                detail = "send failed: ${error::class.java.simpleName} ${error.message}",
            )
        }.getOrNull() ?: return@withContext Contribution.Unreachable

        readOutcome(body)
    }

    /** Their reply is `{"status": 1, ...}` for accepted and `{"status": 0, ...}` for declined. */
    private fun readOutcome(body: String): Contribution {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return Contribution.Refused(null)
        val status = root["status"]?.jsonPrimitive?.content
        val detail = root["status_verbose"]?.jsonPrimitive?.content
        return if (status == "1") Contribution.Sent else Contribution.Refused(detail)
    }

    private companion object {
        const val TIMEOUT_SECONDS = 20L
        const val USER_AGENT = "MetaSelf/1.0 (personal single-user app; via GitHub hagaybar/metaself-app)"
    }
}
