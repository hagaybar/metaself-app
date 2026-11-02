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

    /** D166: the newest letter takes a place, and what needs the owner still comes first. */
    @Test
    fun `the newest letter joins the next thing and the question`() {
        val home = TrainerHome(waiting = null, earlier = emptyList())
        val monday = 20_692L

        assertThat(CoachSuggestions.of(home, listOf(summary("talk:1"), summary(Conversation.letter(monday - 7)), summary(Conversation.letter(monday))))).containsExactly(
            CoachSuggestion.Next(home.main),
            CoachSuggestion.Letter(monday),
            CoachSuggestion.AskAboutTheWeek,
        ).inOrder()
    }

    private fun summary(key: String): ConversationSummary {
        val message = TrainerMessage(1L, 1_000L, true, "Invented words.", null, true, conversation = key)
        return ConversationSummary(key, ThreadItem.Said(message), message.text, message.atMillis, message.text, true, false)
    }
}
