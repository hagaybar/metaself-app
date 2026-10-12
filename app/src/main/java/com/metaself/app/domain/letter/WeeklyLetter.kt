package com.metaself.app.domain.letter

import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.BodyFacts
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.GoalFacts
import com.metaself.app.domain.trainer.GoalView
import com.metaself.app.domain.trainer.PlanGoal
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.SessionFacts
import com.metaself.app.domain.trainer.WeekLedger

/**
 * A letter's texts, all non-empty. Advice, never a measurement (D4). A letter written now is [Coach] (D140);
 * one stored before it keeps D102's [SixParts] and is drawn as it was.
 */
sealed interface LetterTexts {
    val headline: String

    /** The one thing the letter asked to watch: [Coach.lookAfter], or D102's look_at. */
    val lookAfter: String
    val nextWeek: String
    val close: String

    /** D140: the coach's letter. */
    data class Coach(
        override val headline: String,
        val recognition: String,
        val payingOff: String,
        override val lookAfter: String,
        val restsOn: String,
        override val nextWeek: String,
        override val close: String,
    ) : LetterTexts

    /** D102: the six texts of a letter written before D140. */
    data class SixParts(
        override val headline: String,
        val effort: String,
        val progress: String,
        val lookAt: String,
        override val nextWeek: String,
        override val close: String,
    ) : LetterTexts {
        override val lookAfter: String get() = lookAt
    }
}

/** A stored letter (D104). [bandDataUntil] is the last copy's time when the band's data may be behind (D99). */
data class WeeklyLetter(
    val id: Long = 0,
    val weekMonday: Long,
    val createdAtMillis: Long,
    val figures: LetterFigures,
    val texts: LetterTexts,
    val model: String,
    val bandDataUntil: Long? = null,
    val readAtMillis: Long? = null,
)

/** D141: one of the week's answers to "How did it go?" (D85) that has words, with its session's day and kind. */
data class SessionWords(val epochDay: Long, val kind: WorkoutKind, val felt: Felt?, val words: String)

/** D141: the words given with a plan request (D93, D124), or with an adjustment (D97) when [adjustment]. */
data class PlanRequestWords(val epochDay: Long, val adjustment: Boolean, val words: String)

/** D141: an earlier letter's one thing to look after (or D102's look_at) and its next week, with its week. */
data class PastLetter(val weekMonday: Long, val lookAfter: String, val nextWeek: String)

/**
 * Everything one letter request holds (D101, amended by D111 and D141) — built fresh from the record. There
 * is deliberately no field for a meal, a food, an amount, a time of eating, a single weigh-in or raw readings;
 * `LetterPromptTest` fails if one is added. The owner's words go only in [sessionWords], [planWords] and
 * [aboutMe] — never on [sessions].
 *
 * @property ledger the week's ledger (D110), counted to its Sunday.
 * @property sessions this week's sessions as D84 sends them, with `words`, `felt` and `plan` always null.
 * @property planWeek this week of the weekly plan, when one ran.
 * @property days the 35 day rows (D141), oldest first.
 * @property patterns D142's, each null when it rests on too few.
 * @property sessionWords this week's answers to "How did it go?" that have words.
 * @property planWords the words given with plan requests and adjustments in the last five weeks.
 * @property planGoal the goal of the plan that ran this week (D119, D127), when one ran with a goal.
 * @property goalView the trainer's stand on that goal (D129), stored with the plan's evaluation.
 * @property lastLetters the last three letters before this one, newest first.
 */
data class LetterRequest(
    val figures: LetterFigures,
    val ledger: WeekLedger,
    val sessions: List<SessionFacts>,
    val aboutMe: String?,
    val goal: GoalFacts?,
    val body: BodyFacts?,
    val planTitle: String?,
    val planWeek: PlanWeek?,
    val days: List<LetterDay>,
    val patterns: LetterPatterns,
    val sessionWords: List<SessionWords>,
    val planWords: List<PlanRequestWords>,
    val planGoal: PlanGoal?,
    val goalView: GoalView?,
    val lastLetters: List<PastLetter>,
) {
    init {
        require(sessions.all { it.words == null && it.felt == null && it.plan == null }) {
            "the letter never sends the owner's words on a session (D101)"
        }
        require(lastLetters.size <= LAST_LETTERS) { "at most $LAST_LETTERS earlier letters" }
    }

    companion object {
        /** D141: how many earlier letters are sent. */
        const val LAST_LETTERS = 3
    }
}
