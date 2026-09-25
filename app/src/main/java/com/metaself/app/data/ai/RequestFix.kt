package com.metaself.app.data.ai

/**
 * What to send instead, after a refusal (D57 §3). Pure.
 *
 * One rule per parameter the app sends; anything else — a token limit, which the app never sends, a
 * key, a quota, a code this does not know — has no fix, and the refusal is shown as it came.
 */
object RequestFix {

    /** The most retries one call makes to learn (D57 §3). */
    const val MAX_RETRIES = 3

    /** `reasoning_effort`'s values, least thinking first. */
    private val EFFORTS = listOf("none", "minimal", "low", "medium", "high")
    private val LOW = EFFORTS.indexOf("low")

    /**
     * The profiles worth trying after [refusal] of a request sent as [sent], best first; empty when
     * the refusal is not one a different profile can answer. The caller skips any it has already
     * tried in this call.
     */
    fun candidates(sent: RequestProfile, refusal: ProviderRefusal): List<RequestProfile> =
        when (refusal.parameter) {
            "temperature" -> if (sent.temperature) {
                listOf(
                    sent.copy(temperature = false, reasoningEffort = sent.reasoningEffort ?: "low"),
                    sent.copy(temperature = false),
                )
            } else {
                emptyList()
            }

            "reasoning_effort" -> effortCandidates(sent, refusal)

            "response_format" -> if (sent.strictFormat) listOf(sent.copy(strictFormat = false)) else emptyList()

            else -> emptyList()
        }.distinct().filter { it != sent }

    private fun effortCandidates(sent: RequestProfile, refusal: ProviderRefusal): List<RequestProfile> {
        val effort = sent.reasoningEffort ?: return emptyList()
        return if (refusesTheValue(effort, refusal)) {
            nextEfforts(effort, refusal.supportedValues).map { sent.copy(reasoningEffort = it) }
        } else {
            // The parameter itself is refused: no reasoning setting, so temperature 0 again —
            // failing that, neither.
            listOf(
                sent.copy(reasoningEffort = null, temperature = true),
                sent.copy(reasoningEffort = null),
            )
        }
    }

    /** Whether the value sent is what is refused, rather than the parameter. */
    private fun refusesTheValue(effort: String, refusal: ProviderRefusal): Boolean = when (refusal.code) {
        "unsupported_value" -> true
        "unsupported_parameter" -> false
        else -> refusal.supportedValues.isNotEmpty() || refusal.message.orEmpty().contains("'$effort'")
    }

    /**
     * With a list: the lowest accepted value at or above `low`, else the highest below it. Without
     * one: the values above the one sent, in order.
     */
    private fun nextEfforts(sent: String, supported: List<String>): List<String> {
        if (supported.isEmpty()) {
            val at = EFFORTS.indexOf(sent)
            return if (at < 0) emptyList() else EFFORTS.drop(at + 1)
        }
        val accepted = supported.filter { it in EFFORTS && it != sent }
        val atOrAboveLow = accepted.filter { EFFORTS.indexOf(it) >= LOW }.sortedBy { EFFORTS.indexOf(it) }
        val belowLow = accepted.filter { EFFORTS.indexOf(it) < LOW }.sortedByDescending { EFFORTS.indexOf(it) }
        return atOrAboveLow + belowLow
    }
}
