package com.metaself.app.domain.trainer

import com.metaself.app.domain.ai.EstimateResult

/** D161: the fixed drawers of the owner's file, by the names the model is told. All read-only, all on the phone. */
enum class Drawer(val wire: String) {
    SESSIONS("sessions"),
    DAYS("days"),
    FOOD_DAYS("food_days"),
    MEALS("meals"),
    HABITS("habits"),
    WEIGHT("weight"),
    PLAN("plan"),
    CONVERSATIONS("conversations");

    companion object {
        fun named(wire: String): Drawer? = entries.firstOrNull { it.wire == wire }
    }
}

/** D161: one drawer the model named, as it named it: nothing here is trusted until the phone has read it. */
data class DrawerAsk(val drawer: String, val from: String, val to: String, val words: String)

/** D161: a fact the model says the owner said, as it named it; the phone checks the group and the caps. */
data class RememberedFact(val group: String, val text: String)

/**
 * D161: one answer in the structured form: drawers to [open], or the [reply]'s words; [remember] is what the owner
 * said that belongs on the sheet. Blank and empty are read as such; what they mean is decided by the look they came on.
 */
data class CoachTurn(val open: List<DrawerAsk>, val reply: String, val remember: List<RememberedFact>, val propose: Proposal? = null)

/**
 * D163: what the phone really read for one drawer, the only source of the Read line. [days] is the days it covered;
 * null for a drawer not read by days.
 */
data class DrawerRead(val drawer: Drawer, val days: Set<Long>? = null)

/**
 * D161: one drawer's answer as the next request sends it. [answer] is JSON text; [read] is null when nothing was
 * fetched (an unknown drawer, bad dates, a read that failed), which is what keeps it off the Read line.
 */
data class OpenedDrawer(val ask: DrawerAsk, val answer: String, val read: DrawerRead?)

/** D161: the first day each part of the record holds anything; null when it holds nothing. */
data class RecordBegins(val movement: Long?, val food: Long?, val weight: Long?, val talk: Long?)

/**
 * D161: the short front page every coach request carries, so the coach knows what exists and a missed injury is not
 * the price of an unopened drawer. [floorKcal] and [targetKcal] are the phone's own ([com.metaself.app.domain.target]),
 * null with no profile; [sheet] is the whole current sheet (it is capped, D149).
 */
data class FrontPage(
    val today: Long,
    val goal: GoalFacts?,
    val plan: TalkPlan?,
    val thisWeek: Rhythm,
    val weekNote: String?,
    val aboutMe: String?,
    val sheet: List<SheetFact>?,
    val body: BodyFacts?,
    val floorKcal: Int?,
    val targetKcal: Int?,
    val recordBegins: RecordBegins,
)

/**
 * Everything one coach request holds (D161): a request of its own, so [TrainerRequest] keeps no field for a meal on
 * the other paths (D84). `CoachRequestTest` fails if a field is added.
 *
 * @property replyTo the owner's message being answered.
 * @property conversation the window a reply reads ([Conversation.window]), this conversation marked.
 * @property opened every drawer read for this reply so far.
 * @property look which of [CoachLooks.MAX] requests this is, from 1.
 */
data class CoachRequest(
    val replyTo: ConversationLine,
    val conversation: List<ConversationLine>,
    val frontPage: FrontPage,
    val opened: List<OpenedDrawer>,
    val look: Int,
) {
    init {
        require(replyTo.fromOwner && replyTo.kind == LineKind.MESSAGE) { "a reply answers one of the owner's messages" }
        require(look in 1..CoachLooks.MAX) { "a reply is at most ${CoachLooks.MAX} requests, not $look" }
    }

    /** The last request of a reply: it says so, and anything it asks to open is ignored. */
    val lastLook: Boolean get() = look == CoachLooks.MAX
}

/** D161: the requests one reply may take. */
object CoachLooks {
    const val MAX = 3
}

/**
 * D161: asking the model once. [instructions] replaces the app's own (the workbench, D106), null sends the app's. An
 * implementation may resend on a rejected parameter (D57); it never retries a failed answer.
 */
fun interface CoachModel {
    suspend fun look(request: CoachRequest, instructions: String?): TrainerReply<CoachTurn>
}

/** What asking the coach for one reply came to. [last] is the last request sent, for the workbench's Copy what was sent. */
sealed interface CoachAnswer {
    val last: CoachRequest

    /** [opened] is what the Read line is made from ([ReadLine]); [remember] gathers every request's. */
    data class Answered(
        val text: String,
        val model: String,
        val opened: List<DrawerRead>,
        val remember: List<RememberedFact>,
        override val last: CoachRequest,
        val proposal: Proposal? = null,
    ) : CoachAnswer

    data class Failed(val failure: EstimateResult, override val last: CoachRequest) : CoachAnswer
}

/**
 * D163: the line under a coach's reply, made on the phone from what was really read and never from the AI's words.
 * Stored without its "Read:" ([com.metaself.app.domain.trainer.TrainerMessage.opened]); "" means nothing was opened.
 */
object ReadLine {

    /** Each drawer once, in the order first read, its days the union of what was read of it. */
    fun of(reads: List<DrawerRead>): String =
        reads.groupBy { it.drawer }.map { (drawer, same) ->
            val name = name(drawer)
            val days = same.mapNotNull { it.days }
            if (days.isEmpty()) name else "$name, " + days(days.flatten().toSet().size)
        }.joinToString(" · ")

    private fun days(count: Int): String = if (count == 1) "1 day" else "$count days"

    private fun name(drawer: Drawer): String = when (drawer) {
        Drawer.SESSIONS -> "sessions"
        Drawer.DAYS -> "sleep and recovery"
        Drawer.FOOD_DAYS -> "daily food totals"
        Drawer.MEALS -> "meals"
        Drawer.HABITS -> "eating habits"
        Drawer.WEIGHT -> "weight"
        Drawer.PLAN -> "your plan"
        Drawer.CONVERSATIONS -> "past conversations"
    }
}
