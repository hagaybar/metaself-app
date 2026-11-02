package com.metaself.app.domain.trainer

/** The trainer's asks (D86, D87, D93, D97, D121, D126, D161), in the order the workbench (D106) lists the first four. */
enum class TrainerPath {
    FEEDBACK,
    PLAN,
    EVALUATE,
    ADJUST,
    FILL_WEEK,
    NEXT_BLOCK,
    SUGGEST_GOALS,

    /** D161: a reply in the conversation, which opens the file. On the workbench (D163). */
    TALK;

    companion object {
        /**
         * D106, D163: the paths the workbench tries. Filling in a week, the next block (release 2's choice 14) and
         * suggesting goals (D126) are not on it.
         */
        val WORKBENCH: List<TrainerPath> = listOf(FEEDBACK, PLAN, EVALUATE, ADJUST, TALK)

        fun of(question: TrainerQuestion): TrainerPath = when (question) {
            is TrainerQuestion.Review -> FEEDBACK
            is TrainerQuestion.Plan -> PLAN
            is TrainerQuestion.Evaluate -> EVALUATE
            is TrainerQuestion.Adjust -> ADJUST
            is TrainerQuestion.FillWeek -> FILL_WEEK
            is TrainerQuestion.NextBlock -> NEXT_BLOCK
            is TrainerQuestion.SuggestGoals -> SUGGEST_GOALS
        }
    }
}
