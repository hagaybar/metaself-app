package com.metaself.app.data.ai

/**
 * What to send instead, after a refusal (D57 §3). Pure.
 *
 * One rule per parameter the app sends. A refusal of the app's own schema, and anything else — a
 * token limit, which the app never sends, a key, a quota, a code this does not know — has no fix,
 * and the refusal is shown as it came.
 */
object RequestFix {

    /** The most retries one call makes to learn (D57 §3). */
    const val MAX_RETRIES = 3

    /** `reasoning_effort`'s values, least thinking first. */
    private val EFFORTS = listOf("none", "minimal", "low", "medium", "high")

    /** What every everyday request wants when a value is refused (D57 §3). */
    const val EVERYDAY = "low"

    /** What a conversation's final analysis wants (D58 §8.4). */
    const val DEEP = "high"

    /**
     * The profiles worth trying after [refusal] of a request sent as [sent], best first; empty when
     * the refusal is not one a different profile can answer. The caller skips any it has already
     * tried in this call.
     *
     * [wanted] is the thinking the call is aiming at: [EVERYDAY] for every request but a
     * conversation's final analysis, which aims at [DEEP] (D58 §8.4, amending D57 §3).
     */
    fun candidates(
        sent: RequestProfile,
        refusal: ProviderRefusal,
        wanted: String = EVERYDAY,
    ): List<RequestProfile> =
        when (refusal.parameter) {
            "temperature" -> if (sent.temperature) {
                listOf(
                    sent.copy(temperature = false, reasoningEffort = sent.reasoningEffort ?: "low"),
                    sent.copy(temperature = false),
                )
            } else {
                emptyList()
            }

            "reasoning_effort" -> effortCandidates(sent, refusal, wanted)

            "response_format" -> if (sent.strictFormat && refusesTheFormat(refusal)) {
                listOf(sent.copy(strictFormat = false))
            } else {
                emptyList()
            }

            else -> emptyList()
        }.distinct().filter { it != sent }

    private fun effortCandidates(
        sent: RequestProfile,
        refusal: ProviderRefusal,
        wanted: String,
    ): List<RequestProfile> {
        val effort = sent.reasoningEffort ?: return emptyList()
        return if (refusesTheValue(effort, refusal)) {
            nextEfforts(effort, refusal.supportedValues, wanted).map { sent.copy(reasoningEffort = it) }
        } else {
            // The parameter itself is refused: no reasoning setting, so temperature 0 again —
            // failing that, neither.
            listOf(
                sent.copy(reasoningEffort = null, temperature = true),
                sent.copy(reasoningEffort = null),
            )
        }
    }

    /**
     * Whether the model refuses the strict format itself — *"'response_format' of type
     * 'json_schema' is not supported with this model"* — rather than the app's schema in it.
     *
     * A refusal of the schema (*"Invalid schema for response_format …"*, `invalid_json_schema`) is
     * the app's own mistake, true of every model: learning `json_object` from it would be remembered
     * for the model and never undone, so it is shown as a refusal instead.
     */
    private fun refusesTheFormat(refusal: ProviderRefusal): Boolean {
        val message = refusal.message.orEmpty().lowercase()
        if (refusal.code == "invalid_json_schema" || "invalid schema" in message) return false
        val unsupported = "not supported" in message || "unsupported" in message ||
            refusal.code == "unsupported_value" || refusal.code == "unsupported_parameter"
        return unsupported && "json_schema" in message
    }

    /** Whether the value sent is what is refused, rather than the parameter. */
    private fun refusesTheValue(effort: String, refusal: ProviderRefusal): Boolean = when (refusal.code) {
        "unsupported_value" -> true
        "unsupported_parameter" -> false
        else -> refusal.supportedValues.isNotEmpty() || refusal.message.orEmpty().contains("'$effort'")
    }

    /**
     * With a list: the lowest accepted value at or above [wanted], else the highest below it.
     * Without one: the values above the one sent, in order — or, aiming at [DEEP], the values below
     * it, most first, since there is nothing above `high` (D58 §8.4).
     */
    private fun nextEfforts(sent: String, supported: List<String>, wanted: String): List<String> {
        val aim = EFFORTS.indexOf(wanted).takeIf { it >= 0 } ?: EFFORTS.indexOf(EVERYDAY)
        if (supported.isEmpty()) {
            val at = EFFORTS.indexOf(sent)
            if (at < 0) return emptyList()
            return if (wanted == DEEP) EFFORTS.take(at).reversed() else EFFORTS.drop(at + 1)
        }
        val accepted = supported.filter { it in EFFORTS && it != sent }
        val atOrAbove = accepted.filter { EFFORTS.indexOf(it) >= aim }.sortedBy { EFFORTS.indexOf(it) }
        val below = accepted.filter { EFFORTS.indexOf(it) < aim }.sortedByDescending { EFFORTS.indexOf(it) }
        return atOrAbove + below
    }
}
