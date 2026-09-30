package com.metaself.app.domain.trainer

import com.metaself.app.domain.ai.EstimateResult

/**
 * D106: what one workbench call came to. [sent] is the last body built for sending, verbatim — the
 * later one when the call learned and sent again (D57); null when nothing was built (no key, the
 * day's allowance spent).
 */
sealed interface WorkbenchReply {
    data class Answered(val text: String, val sent: String) : WorkbenchReply
    data class Failed(val failure: EstimateResult, val sent: String?) : WorkbenchReply
}

/** D106: sends one trainer request under [system] as its whole instructions, with no reply shape. */
fun interface WorkbenchSender {
    suspend fun send(system: String, request: TrainerRequest): WorkbenchReply
}
