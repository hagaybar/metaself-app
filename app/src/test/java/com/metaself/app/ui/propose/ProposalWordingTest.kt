package com.metaself.app.ui.propose

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import org.junit.jupiter.api.Test

/** The sentence for a model that could not be reached. Pure, so JUnit 5. */
class ProposalWordingTest {

    @Test
    fun `unreachable says so, as it always did`() {
        assertThat(ProposalWording.failure(EstimateResult.Unreachable()))
            .isEqualTo("Could not reach the model. Type the numbers instead — your words are still here.")
    }

    /** D57: the connection was lost while a refusal was being answered; the refusal is still said. */
    @Test
    fun `unreachable after a refusal says the refusal too`() {
        val words = "Unsupported value: 'temperature' does not support 0 with this model."

        assertThat(ProposalWording.failure(EstimateResult.Unreachable(afterRefusal = words))).isEqualTo(
            "Could not reach the model. Before that, the provider refused: $words " +
                "Type the numbers instead — your words are still here.",
        )
    }
}
