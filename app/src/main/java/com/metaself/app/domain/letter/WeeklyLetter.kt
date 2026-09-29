package com.metaself.app.domain.letter

import com.metaself.app.domain.trainer.BodyFacts
import com.metaself.app.domain.trainer.GoalFacts
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.SessionFacts

/** The six texts of a letter (D102), all non-empty. Advice, never a measurement (D4). */
data class LetterTexts(
    val headline: String,
    val effort: String,
    val progress: String,
    val lookAt: String,
    val nextWeek: String,
    val close: String,
)

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

/**
 * Everything one letter request holds (D101) — built fresh from the record. There is deliberately no
 * field for a meal, a food, an amount, a time of eating, a single weigh-in, sleep or the owner's words;
 * `LetterPromptTest` fails if one is added.
 *
 * @property sessions this week's sessions as D84 sends them, with `words`, `felt` and `plan` always null.
 * @property lastNextWeek last week's letter's "for next week" line, when there is one.
 * @property planWeek this week of the weekly plan, when one ran.
 */
data class LetterRequest(
    val figures: LetterFigures,
    val sessions: List<SessionFacts>,
    val aboutMe: String?,
    val goal: GoalFacts?,
    val body: BodyFacts?,
    val planTitle: String?,
    val planWeek: PlanWeek?,
    val lastNextWeek: String?,
) {
    init {
        require(sessions.all { it.words == null && it.felt == null && it.plan == null }) {
            "the letter never sends the owner's words on a session (D101)"
        }
    }
}
