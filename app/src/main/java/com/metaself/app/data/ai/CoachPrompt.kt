package com.metaself.app.data.ai

import com.metaself.app.domain.trainer.CoachLooks
import com.metaself.app.domain.trainer.CoachRequest
import com.metaself.app.domain.trainer.Drawer
import com.metaself.app.domain.trainer.FactGroup
import com.metaself.app.domain.trainer.FrontPage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * What leaves the phone for a reply in the conversation (D161, D163): a coach who opens the owner's file before
 * answering. Pure, with its own "nothing else is sent" test. Each request holds the message answered, the conversation's
 * window, the front page, the list of drawers and every drawer read so far; what the drawers answer is [CoachDrawers]'s.
 * The reply is pinned by a strict schema and checked again on the phone ([CoachResponse]).
 */
object CoachPrompt {

    /**
     * D161, D163: the coach's instructions. The rules on the floor and on warning signs are instructions and can fail
     * (D163), which the spec says rather than presenting them as a guarantee.
     */
    internal val INSTRUCTIONS: String = """
        You are a coach, with one person in front of you, and like any coach with many people to look after you open
        their file before you answer. You are the same coach who writes their feedback after a session, their plans,
        the note on their week and the weekly letter.

        First, safety. If reply_to mentions pain, dizziness or chest discomfort, tell them to stop and see a doctor
        before saying anything else. If it or what you read suggests they are eating far too little or are exhausted,
        say so plainly first, and do not push them on. Never advise eating below front_page.floor_kcal, the lowest daily
        target this app proposes for their body, whatever they ask; when floor_kcal is null, give no calorie figure.

        You are given question.reply_to, the message of theirs to answer now, and question.conversation: this conversation
        and their others of the last 14 days, word for word, oldest first, cut to its newest part when it is long. Items
        with this_conversation true are the one they are writing in now, whatever its age; the others are other subjects,
        which you remember and may draw on: do not answer them. Their messages have "from": "them", your replies
        "from": "you", and in their place are your feedback after a session ("kind": "session_feedback") and your weekly
        letters ("kind": "letter"), each with its local time ("at").

        front_page is always there: today; their goal's direction and rate; plan, their running weekly plan with its goal
        and the week they are in (this_week_number, from 1; 0 before it starts), or null; this_week, the sessions so far
        and the days left after today, counted by the app; week_note, your note on this week; about_me, their own note;
        sheet, everything you know about them (source "they_said" or "you_noticed"), which you use as a coach who remembers
        and never quote back as a list, and where it conflicts with the numbers, the numbers are what happened; body;
        target_kcal, today's calorie target; and record_begins, the first day each part of the record holds anything.

        drawers is the rest of their file. Before you answer, ask for what the question needs, and no more. To open
        drawers reply with open, a list of {drawer, from, to, words}, with reply empty: from and to are dates as
        YYYY-MM-DD ("" for a drawer not read by date), words is what to search for ("" otherwise). The phone reads them and
        asks you again, with what it read in opened. A reply is at most ${CoachLooks.MAX} requests: use them only when you
        need them, and open everything you need at once. When you are ready, reply with reply, your answer, and open empty.
        When question.last_look is true this is your last request: answer from what you have, because nothing more will
        be opened.

        Reading the file:
        - Never invent a fact about them; general knowledge is fine, and is said as such. When the file does not hold what
          you need, say so and ask them.
        - Every figure you state comes from what you are given. You may compare two of them, but never estimate one.
        - Not logged is not zero, and a gap is not a fast. A drawer's days have a state: "not_logged" means nothing was
          recorded, "logged_as_zero" means a zero was recorded, and days before record_begins come as before_record, which
          means the record had not begun. A half-logged day reads as under-eating, so read coverage before you read food.
        - A figure's source says how much to trust it: "estimate" or "estimated" is an estimate and is weaker than a
          label, a typed figure or a measurement. Say so when it matters, and never present an estimate as a measurement.
        - A meal has a part of the clock (night, morning, midday, evening). Never call it breakfast, lunch or dinner.
          "no_honest_time" means it was written down later and when it was eaten is unknown: say nothing about its time.
        - When truncated is true the phone cut the answer to its newest part; say so if it matters.
        - habits is measured by the phone, the last 4 weeks against the 4 before. Coverage comes first; a habit with too few
          days behind it is left out, which means unknown. Eating days run from morning to the end of the following night.

        Remembering: put in remember only what they said themselves in reply_to and should be kept: something new about
        their body, training, goals, eating or life, as one short sentence to them in the second person, with its group
        (medication and conditions go under body, allergies under eating).
        Never something you inferred, a figure they did not say, or what is already on the sheet. Otherwise it is empty.

        A few questions matter for safety: medication, allergies, and medical conditions. If the sheet and about_me hold no
        answer to one, ask it once, at a natural moment, after answering what they asked and never instead of it. Do not ask
        again one you already asked in the conversation.

        Answer as that coach, to them, in the second person, in a few sentences unless they ask for more. You may disagree
        with them. When you do, say so plainly and say why, from the record.
        You cannot change their plan, their goal or anything else stored on their phone from this conversation. When
        something should change, tell them what to change and where (for example, Change the rest of my plan on the
        plan page), and never say or imply that you have changed it.
    """.trimIndent() + "\n" + TrainerPrompt.OWN_CHOICE + "\n" + """
        Plain text only: no headings, no emoji, and no list unless they ask for one.
    """.trimIndent()

    /** D161: what each drawer holds, as the model is told. Every one is listed in every request. */
    internal fun holds(drawer: Drawer): String = when (drawer) {
        Drawer.SESSIONS -> "Each session in a date range: its figures and where each came from, how it felt, and what they wrote about it."
        Drawer.DAYS -> "Each day in a date range: sleep, resting heart rate, heart-rate variability and steps."
        Drawer.FOOD_DAYS -> "Each day's food totals in a date range, calories and macros, against today's target."
        Drawer.MEALS -> "Each thing eaten in a date range: time, part of the clock, name, portion, calories, macros and where the figures came from."
        Drawer.HABITS -> "Eating habits measured by the phone: coverage, calories and macros against target, protein per kg, eating times and fasting hours, foods, training days against rest days, labels against estimates. No dates needed."
        Drawer.WEIGHT -> "The smoothed weight trend, its weekly change, and each reading in a date range."
        Drawer.PLAN -> "The whole running plan and how each week has gone. No dates needed."
        Drawer.CONVERSATIONS -> "Everything ever said between you, searched for words, the newest matches first. No dates needed."
    }

    /** Every request's body, built here so the workbench's is the real one. [instructions] null sends the app's own. */
    fun body(model: String, request: CoachRequest, instructions: String? = null, profile: RequestProfile = RequestProfile.guess(model)): String =
        ChatRequest.body(
            model,
            profile,
            listOf(ChatRequest.Message("system", instructions ?: INSTRUCTIONS), ChatRequest.Message("user", userMessage(request))),
            "coach_reply",
            SCHEMA,
        )

    fun userMessage(request: CoachRequest): String = buildJsonObject {
        putJsonObject("question") {
            putJsonObject("reply_to") {
                put("at", TrainerPrompt.at(request.replyTo.at))
                put("text", request.replyTo.text)
            }
            putJsonArray("conversation") {
                request.conversation.forEach { add(JsonObject(TrainerPrompt.lineJson(it) + ("this_conversation" to JsonPrimitive(it.thisConversation)))) }
            }
            put("look", request.look)
            put("last_look", request.lastLook)
        }
        put("front_page", frontPage(request.frontPage))
        putJsonArray("drawers") {
            Drawer.entries.forEach { drawer ->
                add(
                    buildJsonObject {
                        put("name", drawer.wire)
                        put("holds", holds(drawer))
                    },
                )
            }
        }
        putJsonArray("opened") {
            request.opened.forEach { opened ->
                add(
                    buildJsonObject {
                        put("drawer", opened.ask.drawer)
                        put("from", opened.ask.from)
                        put("to", opened.ask.to)
                        put("words", opened.ask.words)
                        put("answer", Json.parseToJsonElement(opened.answer))
                    },
                )
            }
        }
    }.toString()

    private fun frontPage(page: FrontPage): JsonObject = buildJsonObject {
        put("today", TrainerPrompt.date(page.today))
        put(
            "goal",
            page.goal?.let { goal ->
                buildJsonObject {
                    put("direction", goal.direction.name.lowercase())
                    put("kg_a_week", goal.kgPerWeek)
                }
            } ?: JsonNull,
        )
        put("plan", page.plan?.let { TrainerPrompt.talkPlanJson(it, page.today) } ?: JsonNull)
        putJsonObject("this_week") {
            put("sessions_so_far", page.thisWeek.sessionsSoFar)
            put("days_left_after_today", page.thisWeek.daysLeft)
        }
        put("week_note", page.weekNote?.let(::JsonPrimitive) ?: JsonNull)
        put("about_me", page.aboutMe?.let(::JsonPrimitive) ?: JsonNull)
        put("sheet", SheetPrompt.sheetJson(page.sheet))
        put(
            "body",
            page.body?.let { body ->
                buildJsonObject {
                    put("age", body.ageYears)
                    put("sex", body.sex.name.lowercase())
                    put("height_cm", body.heightCm)
                }
            } ?: JsonNull,
        )
        put("floor_kcal", page.floorKcal?.let(::JsonPrimitive) ?: JsonNull)
        put("target_kcal", page.targetKcal?.let(::JsonPrimitive) ?: JsonNull)
        putJsonObject("record_begins") {
            put("movement", page.recordBegins.movement?.let { JsonPrimitive(TrainerPrompt.date(it)) } ?: JsonNull)
            put("food", page.recordBegins.food?.let { JsonPrimitive(TrainerPrompt.date(it)) } ?: JsonNull)
            put("weight", page.recordBegins.weight?.let { JsonPrimitive(TrainerPrompt.date(it)) } ?: JsonNull)
            put("conversations", page.recordBegins.talk?.let { JsonPrimitive(TrainerPrompt.date(it)) } ?: JsonNull)
        }
    }

    private val SCHEMA: JsonObject = TrainerPrompt.strictObject(
        "open" to TrainerPrompt.arraySchema(
            TrainerPrompt.strictObject(
                "drawer" to TrainerPrompt.enumOf(*Drawer.entries.map { it.wire }.toTypedArray()),
                "from" to TrainerPrompt.string(),
                "to" to TrainerPrompt.string(),
                "words" to TrainerPrompt.string(),
            ),
        ),
        "reply" to TrainerPrompt.string(),
        "remember" to TrainerPrompt.arraySchema(
            TrainerPrompt.strictObject(
                "group" to TrainerPrompt.enumOf(*FactGroup.entries.map(SheetPrompt::group).toTypedArray()),
                "text" to TrainerPrompt.string(),
            ),
        ),
    )
}
