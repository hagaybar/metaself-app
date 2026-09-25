package com.metaself.app.data.ai

import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put

/**
 * The sampling parameters a request sends, chosen by the model's name from settings — one pure rule
 * for every request this app builds ([EstimatePrompt], [ReviewPrompt]).
 *
 * Reasoning models (the GPT-5 and GPT-6 families, and o1, o3, o4) refuse `temperature` unless their
 * reasoning is switched off, which not every one of them allows: a request carrying
 * `temperature: 0` fails on every call with *Unsupported value: 'temperature'*. They are sent no
 * temperature and a low `reasoning_effort` instead. Every other name keeps what the app always
 * sent, `temperature: 0`, since a name this rule does not know cannot be assumed to take anything
 * else.
 *
 * No token limit is sent to any model, so none has to leave room for reasoning.
 *
 * @property temperature sent when not null.
 * @property reasoningEffort sent as `reasoning_effort` when not null.
 */
data class ModelParams(val temperature: Int?, val reasoningEffort: String?) {

    /** Writes the parameters into a request being built. */
    fun into(request: JsonObjectBuilder) {
        temperature?.let { request.put("temperature", it) }
        reasoningEffort?.let { request.put("reasoning_effort", it) }
    }

    companion object {
        /** What the app always sent. */
        val DETERMINISTIC = ModelParams(temperature = 0, reasoningEffort = null)

        /** A reasoning model: no temperature, which it refuses, and a little reasoning. */
        val REASONING = ModelParams(temperature = null, reasoningEffort = "low")

        private val REASONING_FAMILIES = listOf("gpt-5", "gpt-6", "o1", "o3", "o4")

        /** The parameters for [model], the name as settings hold it. */
        fun of(model: String): ModelParams {
            val name = model.trim().lowercase()
            return if (REASONING_FAMILIES.any { name.startsWith(it) }) REASONING else DETERMINISTIC
        }
    }
}
