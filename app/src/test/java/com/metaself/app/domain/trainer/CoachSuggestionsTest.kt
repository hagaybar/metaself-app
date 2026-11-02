package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CoachSuggestionsTest {

    /** D158: with nothing waiting, the next thing to do and the question that shows what the box is for. */
    @Test
    fun `with nothing waiting, the next thing and the question`() {
        val home = TrainerHome(waiting = null, earlier = emptyList())

        assertThat(CoachSuggestions.of(home)).containsExactly(
            CoachSuggestion.Next(home.main),
            CoachSuggestion.AskAboutTheWeek,
        ).inOrder()
    }
}
