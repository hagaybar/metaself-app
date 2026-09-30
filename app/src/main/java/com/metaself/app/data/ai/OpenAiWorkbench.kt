package com.metaself.app.data.ai

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.WorkbenchReply
import com.metaself.app.domain.trainer.WorkbenchSender
import okhttp3.OkHttpClient

/**
 * The workbench's sender (D106): [TrainerPrompt.workbenchBody] over the shared [OpenAiCall] — the key, the
 * day's ceiling, the counting, the failures and the learning are the trainer's. The reply is the model's
 * text as written; nothing is parsed or checked, and nothing is stored but what [OpenAiCall] itself keeps
 * (the day's count, and the profile that answered — design choice 3 of the plan).
 *
 * The base URL is a parameter so a test can point it at a local server. **No test makes a real network call.**
 */
class OpenAiWorkbench(
    keys: ApiKeyStore,
    settings: AiSettingsStore,
    client: OkHttpClient,
    profiles: RequestProfileStore,
    baseUrl: String = OpenAiCall.OPENAI_URL,
) : WorkbenchSender {

    private val call = OpenAiCall(keys, settings, client, profiles, baseUrl)

    override suspend fun send(system: String, request: TrainerRequest): WorkbenchReply {
        var sent: String? = null
        val outcome = call.send { model, profile -> TrainerPrompt.workbenchBody(model, system, request, profile).also { sent = it } }
        return when (outcome) {
            is OpenAiCall.Outcome.Failed -> WorkbenchReply.Failed(outcome.failure, sent)
            is OpenAiCall.Outcome.Body -> {
                val text = TrainerResponse.content(outcome.text)
                if (text == null) {
                    WorkbenchReply.Failed(EstimateResult.Unreadable(NO_MESSAGE, outcome.text), sent)
                } else {
                    call.remember(outcome)
                    WorkbenchReply.Answered(text, checkNotNull(sent))
                }
            }
        }
    }

    private companion object {
        const val NO_MESSAGE = "the answer held no message"
    }
}
