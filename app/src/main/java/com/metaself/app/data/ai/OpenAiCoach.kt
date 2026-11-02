package com.metaself.app.data.ai

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.CoachModel
import com.metaself.app.domain.trainer.CoachRequest
import com.metaself.app.domain.trainer.CoachTurn
import com.metaself.app.domain.trainer.TrainerReply
import okhttp3.OkHttpClient

/**
 * The one [CoachModel] (D161): [CoachPrompt] out, [CoachResponse] back, over the shared [OpenAiCall] — so each look is
 * counted against the day's ceiling exactly as every other call. Nothing here retries a failed answer.
 */
class OpenAiCoach(
    keys: ApiKeyStore,
    settings: AiSettingsStore,
    client: OkHttpClient,
    profiles: RequestProfileStore,
    private val problems: ProblemLog = ProblemLog.NONE,
    baseUrl: String = OpenAiCall.OPENAI_URL,
) : CoachModel {

    private val call = OpenAiCall(keys, settings, client, profiles, baseUrl)

    override suspend fun look(request: CoachRequest, instructions: String?): TrainerReply<CoachTurn> =
        when (val outcome = call.send(build = { model, profile -> CoachPrompt.body(model, request, instructions, profile) })) {
            is OpenAiCall.Outcome.Body -> CoachResponse.parse(outcome.text, outcome.model).also { reply ->
                if (reply is TrainerReply.Answered) call.remember(outcome) else recorded((reply as TrainerReply.Failed).failure, null)
            }
            is OpenAiCall.Outcome.Failed -> TrainerReply.Failed(outcome.failure).also { recorded(it.failure, outcome.status) }
        }

    /** By kind only: the request holds the owner's words and his record, and the answer may quote them. */
    private fun recorded(failure: EstimateResult, status: Int?) {
        when (failure) {
            is EstimateResult.Refused -> problems.record(
                "coach refused",
                if (status != null) "the provider answered $status" else "the call could not be made",
            )
            is EstimateResult.Unreadable -> problems.record("coach unreadable", "the reply was not in the shape this app asked for")
            is EstimateResult.Unreachable -> problems.record("coach unreachable", "no answer")
            else -> Unit
        }
    }
}
