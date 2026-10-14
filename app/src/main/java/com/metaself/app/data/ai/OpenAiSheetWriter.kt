package com.metaself.app.data.ai

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.RewrittenFact
import com.metaself.app.domain.trainer.SheetRewriteRequest
import com.metaself.app.domain.trainer.SheetWriter
import com.metaself.app.domain.trainer.TrainerReply
import okhttp3.OkHttpClient

/**
 * The one [SheetWriter] (D150): [SheetPrompt] out, [SheetResponse] back, over the shared [OpenAiCall] — the key, the
 * day's ceiling, the counting and the failures are the trainer's. Nothing here retries; a failed rewrite leaves the
 * sheet, and next Sunday's reads the longer span.
 */
class OpenAiSheetWriter(
    keys: ApiKeyStore,
    settings: AiSettingsStore,
    client: OkHttpClient,
    profiles: RequestProfileStore,
    private val problems: ProblemLog = ProblemLog.NONE,
    baseUrl: String = OpenAiCall.OPENAI_URL,
) : SheetWriter {

    private val call = OpenAiCall(keys, settings, client, profiles, baseUrl)

    override suspend fun rewrite(request: SheetRewriteRequest): TrainerReply<List<RewrittenFact>> =
        when (val outcome = call.send(build = { model, profile -> SheetPrompt.body(model, request, profile) })) {
            is OpenAiCall.Outcome.Body -> SheetResponse.parse(outcome.text, outcome.model).also { reply ->
                if (reply is TrainerReply.Answered) call.remember(outcome) else recorded((reply as TrainerReply.Failed).failure, null)
            }
            is OpenAiCall.Outcome.Failed -> TrainerReply.Failed(outcome.failure).also { recorded(it.failure, outcome.status) }
        }

    /** By kind only: the request holds the conversation word for word, and the answer may quote it. */
    private fun recorded(failure: EstimateResult, status: Int?) {
        when (failure) {
            is EstimateResult.Refused -> problems.record(
                "sheet refused",
                if (status != null) "the provider answered $status" else "the call could not be made",
            )
            is EstimateResult.Unreadable -> problems.record("sheet unreadable", "the reply was not in the shape this app asked for")
            is EstimateResult.Unreachable -> problems.record("sheet unreachable", "no answer")
            else -> Unit
        }
    }
}
