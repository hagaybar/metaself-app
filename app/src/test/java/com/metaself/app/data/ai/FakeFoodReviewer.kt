package com.metaself.app.data.ai

import com.metaself.app.domain.ai.FoodReview
import com.metaself.app.domain.ai.FoodReviewer
import com.metaself.app.domain.ai.ReviewRequest
import com.metaself.app.domain.ai.ReviewResult
import kotlinx.coroutines.CompletableDeferred

/**
 * A [FoodReviewer] for view-model tests: it records every request and answers [answer].
 *
 * Set [gate] to hold the answer back until the test completes it — the way to test what happens
 * while a review is still out (typing in a group, closing the editor, opening another food).
 */
class FakeFoodReviewer(
    var answer: ReviewResult = ReviewResult.Proposed(FoodReview(null, null, null, emptyList())),
) : FoodReviewer {

    val requests = mutableListOf<ReviewRequest>()

    var gate: CompletableDeferred<Unit>? = null

    override suspend fun review(request: ReviewRequest): ReviewResult {
        requests += request
        gate?.await()
        return answer
    }
}
