package com.metaself.app.data.trainer

import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.WorkbenchReply
import com.metaself.app.domain.trainer.WorkbenchSender

/** Keeps every (instructions, request) it is given, and answers [reply]. */
class RecordingWorkbenchSender(
    var reply: WorkbenchReply = WorkbenchReply.Answered("Invented reply.", "{\"invented\":true}"),
) : WorkbenchSender {
    val sent = mutableListOf<Pair<String, TrainerRequest>>()

    override suspend fun send(system: String, request: TrainerRequest): WorkbenchReply {
        sent += system to request
        return reply
    }
}
