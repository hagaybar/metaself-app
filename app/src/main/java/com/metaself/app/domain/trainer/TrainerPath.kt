package com.metaself.app.domain.trainer

/** The trainer's four asks (D86, D87, D93, D97), in the order the workbench (D106) lists them. */
enum class TrainerPath {
    FEEDBACK,
    PLAN,
    EVALUATE,
    ADJUST;

    companion object {
        fun of(question: TrainerQuestion): TrainerPath = when (question) {
            is TrainerQuestion.Review -> FEEDBACK
            is TrainerQuestion.Plan -> PLAN
            is TrainerQuestion.Evaluate -> EVALUATE
            is TrainerQuestion.Adjust -> ADJUST
        }
    }
}
