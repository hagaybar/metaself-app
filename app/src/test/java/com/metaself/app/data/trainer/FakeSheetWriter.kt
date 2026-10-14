package com.metaself.app.data.trainer

import com.metaself.app.domain.trainer.RewrittenFact
import com.metaself.app.domain.trainer.SheetRewriteRequest
import com.metaself.app.domain.trainer.SheetWriter
import com.metaself.app.domain.trainer.TrainerReply

/** A [SheetWriter] answering from its script, in order; every request kept in [asked]. Nothing scripted fails the test. */
class FakeSheetWriter : SheetWriter {
    val replies = mutableListOf<TrainerReply<List<RewrittenFact>>>()
    val asked = mutableListOf<SheetRewriteRequest>()

    override suspend fun rewrite(request: SheetRewriteRequest): TrainerReply<List<RewrittenFact>> {
        asked += request
        return replies.removeAt(0)
    }
}
