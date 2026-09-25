package com.metaself.app.data.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * What a request carries besides what is asked, for one model (D57 §1).
 *
 * Models differ in what they accept: a reasoning model refuses `temperature` unless its reasoning is
 * off, some accept only some `reasoning_effort` values, and a model may not take a strict JSON
 * schema. The app starts from [guess] and learns the rest from the provider's refusals
 * ([RequestFix]); a profile that worked is remembered per model name ([RequestProfileStore]).
 *
 * No token limit is part of a profile, because no request sends one.
 *
 * @property temperature `temperature: 0` is sent when true, nothing when false.
 * @property reasoningEffort sent as `reasoning_effort` when not null.
 * @property strictFormat the reply is pinned with a strict `json_schema` when true; when false it is
 *   asked for as a `json_object`, with the schema written into the instructions ([ChatRequest]).
 */
data class RequestProfile(
    val temperature: Boolean,
    val reasoningEffort: String?,
    val strictFormat: Boolean = true,
) {

    /** The profile as it is remembered (D57 §5). */
    fun toJson(): JsonObject = buildJsonObject {
        put("temperature", temperature)
        reasoningEffort?.let { put("reasoning_effort", it) }
        put("format", if (strictFormat) FORMAT_STRICT else FORMAT_IN_INSTRUCTIONS)
    }

    /**
     * What the request sends, in the owner's words, for Settings' Test it (D57 §6): for example
     * *no temperature, low thinking, strict format*.
     */
    fun describe(): String = listOfNotNull(
        if (temperature) "temperature 0" else "no temperature",
        reasoningEffort?.let { if (it == "none") "thinking off" else "$it thinking" },
        if (strictFormat) "strict format" else "format in the instructions",
    ).joinToString(", ")

    companion object {
        /** What the app always sent. */
        val DETERMINISTIC = RequestProfile(temperature = true, reasoningEffort = null)

        /** A reasoning model: no temperature, which it refuses, and a little reasoning. */
        val REASONING = RequestProfile(temperature = false, reasoningEffort = "low")

        private val REASONING_FAMILIES = listOf("gpt-5", "gpt-6", "o1", "o3", "o4")
        private const val FORMAT_STRICT = "strict"
        private const val FORMAT_IN_INSTRUCTIONS = "instructions"

        /**
         * The first guess for [model], the name as settings hold it, when nothing is remembered for
         * it (D57 §2) — the rule 0.50.0 shipped.
         *
         * The GPT-5 and GPT-6 families, and o1, o3 and o4, are reasoning models: sent no temperature
         * and a low `reasoning_effort`. Every other name keeps what the app always sent,
         * `temperature: 0`, since a name this rule does not know cannot be assumed to take anything
         * else. A wrong guess costs a refusal and a retry, not a failed call.
         */
        fun guess(model: String): RequestProfile {
            val name = model.trim().lowercase()
            return if (REASONING_FAMILIES.any { name.startsWith(it) }) REASONING else DETERMINISTIC
        }

        /** A remembered profile, or null when [json] is not one — which is then relearned. */
        fun fromJson(json: JsonObject): RequestProfile? = runCatching {
            val temperature = json["temperature"]?.jsonPrimitive?.booleanOrNull ?: return null
            val effort = json["reasoning_effort"]?.let { element ->
                (element as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: return null
            }
            val strict = when (json["format"]?.jsonPrimitive?.contentOrNull) {
                FORMAT_STRICT -> true
                FORMAT_IN_INSTRUCTIONS -> false
                else -> return null
            }
            RequestProfile(temperature, effort, strict)
        }.getOrNull()
    }
}
