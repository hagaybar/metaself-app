package com.metaself.app.data.trainer

import com.metaself.app.data.time.Now
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.Conversation
import com.metaself.app.domain.trainer.SheetMadeBy
import com.metaself.app.domain.trainer.SheetRewriteRequest
import com.metaself.app.domain.trainer.SheetWriter
import com.metaself.app.domain.trainer.Sheets
import com.metaself.app.domain.trainer.ThreadItem
import com.metaself.app.domain.trainer.TrainerReply
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * D150: the weekly rewrite of the sheet, run by the Sunday letter's worker after the letter. One request, from the
 * sheet as it is, the owner's note and the conversation since the last successful rewrite (feedback and letters in
 * their place, the newest [Sheets.REWRITE_CHARS] characters); its answer becomes a new version made by the rewrite.
 * A failure stores nothing — the sheet stays, and next Sunday's rewrite reads the longer span. Reads and writes throw;
 * the worker logs them by kind. Nothing here logs: the writer logs a failed ask by its kind.
 */
class RewriteTheSheet @Inject constructor(
    private val writer: SheetWriter,
    private val sheets: SheetStore,
    private val talk: TalkToTheTrainer,
    private val aboutMe: AboutMeStore,
    private val now: Now,
) {
    sealed interface Outcome {
        /** A rewrite was already made on or after this Sunday: one a week. */
        data object AlreadyDone : Outcome

        /** The owner has said nothing since the last rewrite: nothing is asked. */
        data object NothingSaid : Outcome

        data class Written(val versionId: Long) : Outcome

        /** Nothing stored; the sheet stays as it was. */
        data class Failed(val failure: EstimateResult) : Outcome
    }

    /** The rewrite for the week ending on [sundayEpochDay]. */
    suspend fun rewrite(sundayEpochDay: Long): Outcome {
        val zone = ZoneId.systemDefault()
        val versions = sheets.observe().first()
        val newestRewrite = Sheets.newestRewriteAt(versions)
        if (newestRewrite != null && day(newestRewrite, zone) >= sundayEpochDay) return Outcome.AlreadyDone
        val since = Sheets.lastRewriteAt(versions)
        val span = Sheets.span(talk.observeThread().first(), since)
        if (span.none { it is ThreadItem.Said && it.message.fromOwner }) return Outcome.NothingSaid
        val today = day(now(), zone)
        val previous = versions.maxByOrNull { it.id }?.facts.orEmpty()
        val request = SheetRewriteRequest(
            today = today,
            // The current facts and those he removed that are still shown; a fact a rewrite dropped is not sent.
            facts = previous.filter { it.current || (it.removedByOwner && Sheets.stillShown(it.droppedEpochDay!!, today)) },
            aboutMe = aboutMe.note.first().trim().ifEmpty { null },
            since = since?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) },
            conversation = span.map { Conversation.line(it, zone) },
        )
        return when (val reply = writer.rewrite(request)) {
            is TrainerReply.Failed -> Outcome.Failed(reply.failure)
            is TrainerReply.Answered -> Outcome.Written(
                sheets.add(now(), Sheets.afterRewrite(previous, reply.value, today), SheetMadeBy.REWRITE, reply.model),
            )
        }
    }

    private fun day(millis: Long, zone: ZoneId): Long = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()
}
