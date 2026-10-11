package com.metaself.app.domain.trainer

/** The trainer's asks (D86, D87, D93, D97, D121), in the order the workbench (D106) lists the first four. */
enum class TrainerPath {
    FEEDBACK,
    PLAN,
    EVALUATE,
    ADJUST,
    FILL_WEEK,
    NEXT_BLOCK;

    companion object {
        /** D106: the paths the workbench tries. Filling in a week and the next block are not on it (release 2's choice 14). */
        val WORKBENCH: List<TrainerPath> = listOf(FEEDBACK, PLAN, EVALUATE, ADJUST)

        fun of(question: TrainerQuestion): TrainerPath = when (question) {
            is TrainerQuestion.Review -> FEEDBACK
            is TrainerQuestion.Plan -> PLAN
            is TrainerQuestion.Evaluate -> EVALUATE
            is TrainerQuestion.Adjust -> ADJUST
            is TrainerQuestion.FillWeek -> FILL_WEEK
            is TrainerQuestion.NextBlock -> NEXT_BLOCK
        }
    }
}
