package com.metaself.app.data.ai

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.letter.LetterReply
import com.metaself.app.domain.letter.LetterRequest
import com.metaself.app.domain.letter.LetterWriter
import okhttp3.OkHttpClient

/**
 * The one [LetterWriter] (D101): [LetterPrompt] out, [LetterResponse] back, over the shared [OpenAiCall]
 * — the key, the day's ceiling, the counting and the failures are the estimator's and the trainer's.
 * Nothing here retries; the weekly job decides whether to ask again (D99).
 */
class OpenAiLetterWriter(
    keys: ApiKeyStore,
    settings: AiSettingsStore,
    client: OkHttpClient,
    profiles: RequestProfileStore,
    private val problems: ProblemLog = ProblemLog.NONE,
    baseUrl: String = OpenAiCall.OPENAI_URL,
) : LetterWriter {

    private val call = OpenAiCall(keys, settings, client, profiles, baseUrl)

    override suspend fun write(request: LetterRequest): LetterReply =
        when (val outcome = call.send(build = { model, profile -> LetterPrompt.body(model, request, profile) })) {
            is OpenAiCall.Outcome.Body -> when (val parsed = LetterResponse.parse(outcome.text, outcome.model)) {
                is LetterResponse.Parsed.Written -> {
                    call.remember(outcome)
                    LetterReply.Written(parsed.texts, parsed.model)
                }
                is LetterResponse.Parsed.Failed -> LetterReply.Failed(parsed.failure).also { recorded(it.failure, null) }
            }
            is OpenAiCall.Outcome.Failed -> LetterReply.Failed(outcome.failure).also { recorded(it.failure, outcome.status) }
        }

    /** By kind only: the request holds the owner's note and the figures, and the answer may quote them. */
    private fun recorded(failure: EstimateResult, status: Int?) {
        when (failure) {
            is EstimateResult.Refused -> problems.record(
                "letter refused",
                if (status != null) "the provider answered $status" else "the call could not be made",
            )
            is EstimateResult.Unreadable -> problems.record("letter unreadable", "the reply was not in the shape this app asked for")
            is EstimateResult.Unreachable -> problems.record("letter unreachable", "no answer")
            else -> Unit
        }
    }
}
