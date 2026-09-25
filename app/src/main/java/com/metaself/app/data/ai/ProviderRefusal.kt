package com.metaself.app.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The provider's error object from a refused request, read for what it says was wrong (D57 §3).
 *
 * The structured fields decide. The message is read only where they are silent: [parameter] falls
 * back to the first request parameter the message quotes, or else names, and [supportedValues] is
 * read from the message's *"Supported values are: …"*, which no structured field carries.
 *
 * @property type the error's `type`, such as `invalid_request_error`.
 * @property code the error's `code`, such as `unsupported_parameter` or `unsupported_value`.
 * @property parameter the request parameter refused, from `param` or, failing that, the message.
 * @property message the provider's words, as they came.
 * @property supportedValues the values the message says are accepted, in its order; empty if none.
 */
data class ProviderRefusal(
    val type: String?,
    val code: String?,
    val parameter: String?,
    val message: String?,
    val supportedValues: List<String>,
) {

    companion object {
        /** The request parameters a refusal can name that the app knows of. */
        private val KNOWN_PARAMETERS = listOf(
            "temperature", "reasoning_effort", "response_format", "max_tokens", "max_completion_tokens",
        )

        private val QUOTED = Regex("""'([^']*)'""")
        private val SUPPORTED = Regex("""[Ss]upported values are:?\s*(.*)""")

        /** The refusal in [body], or null when the body holds no error object. Never throws. */
        fun parse(body: String): ProviderRefusal? {
            val error = runCatching {
                Json.parseToJsonElement(body).jsonObject["error"] as? JsonObject
            }.getOrNull() ?: return null

            val message = error.text("message")
            val parameter = error.text("param")?.takeIf { it.isNotBlank() }
                ?: message?.let(::parameterIn)
            return ProviderRefusal(
                type = error.text("type"),
                code = error.text("code"),
                parameter = parameter,
                message = message,
                supportedValues = message?.let(::supportedIn).orEmpty(),
            )
        }

        private fun JsonObject.text(name: String): String? =
            (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

        /**
         * The first known request parameter the message quotes or, failing that, the first it names
         * unquoted — *"Unrecognized request argument supplied: reasoning_effort"*.
         */
        private fun parameterIn(message: String): String? =
            QUOTED.findAll(message).map { it.groupValues[1] }.firstOrNull { it in KNOWN_PARAMETERS }
                ?: KNOWN_PARAMETERS
                    .mapNotNull { name -> wordIn(message, name)?.let { at -> at to name } }
                    .minByOrNull { it.first }?.second

        /** Where [name] stands in [message] as a whole word, or null. */
        private fun wordIn(message: String, name: String): Int? =
            Regex("""(?<![A-Za-z0-9_])${Regex.escape(name)}(?![A-Za-z0-9_])""").find(message)?.range?.first

        /** The quoted values after *"Supported values are:"*. */
        private fun supportedIn(message: String): List<String> {
            val list = SUPPORTED.find(message)?.groupValues?.get(1) ?: return emptyList()
            return QUOTED.findAll(list).map { it.groupValues[1] }.toList()
        }
    }
}
