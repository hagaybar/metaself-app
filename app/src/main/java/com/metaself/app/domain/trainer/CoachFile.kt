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
data class CoachTurn(val open: List<DrawerAsk>, val reply: String, val remember: List<RememberedFact>, val propose: Proposal? = null, val aboutFood: Boolean = false)

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
 * @property foodCheck the last answer spoke of food unread, and the phone opened it for this request ([FoodTalk]).
 */
data class CoachRequest(
    val replyTo: ConversationLine,
    val conversation: List<ConversationLine>,
    val frontPage: FrontPage,
    val opened: List<OpenedDrawer>,
    val look: Int,
    val foodCheck: Boolean = false,
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
 * D163: a reply that speaks of what was eaten rests on the food record read for that reply, not on an old letter or note.
 * A half-logged week reads as under-eating, and the coach can object from it (D164). The model says so in about_food, and
 * [states] catches what it did not say, so that this is more than an instruction. These are words: a sentence they miss, on
 * a reply the model did not mark, goes unchecked.
 */
object FoodTalk {
    /** Each one tells how much of the record is logged. */
    val DRAWERS: Set<Drawer> = setOf(Drawer.FOOD_DAYS, Drawer.MEALS, Drawer.HABITS)

    /** What the phone opens itself: whole days ending yesterday, as the habits do (D162). A chosen size. */
    const val CHECK_DAYS = 14

    private val WORDS = Regex(
        "\\b(kcal|calories?|intake|protein|macros?|eaten|eating|ate|logged food|food average|under-?eat\\w*|under-?fuel\\w*)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun states(reply: String): Boolean = WORDS.containsMatchIn(reply)
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
        val foodUnchecked: Boolean = false,
    ) : CoachAnswer

    data class Failed(val failure: EstimateResult, override val last: CoachRequest) : CoachAnswer
}

/**
 * D163: the line under a coach's reply, made on the phone from what was really read and never from the AI's words.
 * Stored without its "Read:" ([com.metaself.app.domain.trainer.TrainerMessage.opened]); "" means nothing was opened.
 */
object ReadLine {

    /**
     * Each drawer once, in the order first read, its days the union of what was read of it. [foodUnchecked] says that the
     * reply speaks of food the phone could not read for it ([FoodTalk]).
     */
    fun of(reads: List<DrawerRead>, foodUnchecked: Boolean = false): String {
        val parts = reads.groupBy { it.drawer }.map { (drawer, same) ->
            val name = name(drawer)
            val days = same.mapNotNull { it.days }
            if (days.isEmpty()) name else "$name, " + days(days.flatten().toSet().size)
        }
        return when {
            !foodUnchecked -> parts
            parts.isEmpty() -> listOf("the front page", FOOD_UNCHECKED)
            else -> parts + FOOD_UNCHECKED
        }.joinToString(" · ")
    }

    private const val FOOD_UNCHECKED = "food not checked"

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
