package com.metaself.app.ui.screen.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.trainer.ConversationSummary
import com.metaself.app.domain.trainer.ThreadItem
import com.metaself.app.domain.trainer.TrainerHome
import com.metaself.app.domain.trainer.TrainerMessage
import com.metaself.app.domain.trainer.WeekNote
import com.metaself.app.domain.trainer.WeekNoteSource
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CoachScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    /** D157: the note, the box, the suggestions, the latest conversations and the way to everything else. */
    @Test
    fun `the coach screen has the note, the box, suggestions, conversations and Everything else`() {
        var opened: String? = null
        var everything = 0
        var all = 0
        val texts = draw(summaries(5), onConversation = { opened = it }, onEverythingElse = { everything++ }, onAll = { all++ })

        assertThat(texts).containsAtLeast("Invented note.", "Reply ›", "How is my week going?", "All conversations", "Everything else")
        // D157: three, the latest, of the five.
        assertThat(texts).containsAtLeast("Invented subject 0", "Invented subject 1", "Invented subject 2")
        assertThat(texts).doesNotContain("Invented subject 3")

        render.click("Invented subject 1")
        assertThat(opened).isEqualTo("talk:1")
        render.click("Everything else")
        assertThat(everything).isEqualTo(1)
        render.click("All conversations")
        assertThat(all).isEqualTo(1)
    }

    /** D158: the question fills the box; it sends nothing. */
    @Test
    fun `the suggested question fills the box and sends nothing`() {
        var filled = 0
        var sent = 0
        draw(emptyList(), onAsk = { filled++ }, onSend = { sent++ })

        render.click("How is my week going?")

        assertThat(filled).isEqualTo(1)
        assertThat(sent).isEqualTo(0)
    }

    /** D157: with no conversations there is nothing to list, and no way to an empty list. */
    @Test
    fun `no conversations, no All conversations`() {
        val texts = draw(emptyList())
        assertThat(texts).doesNotContain("All conversations")
        assertThat(texts).contains("Everything else")
    }

    private fun summaries(count: Int): List<ConversationSummary> = (0 until count).map { index ->
        val message = TrainerMessage(index + 1L, 1_000L + index, true, "Invented subject $index", null, true, conversation = "talk:$index")
        ConversationSummary("talk:$index", ThreadItem.Said(message), message.text, message.atMillis, "Invented words.", true, false)
    }

    private fun draw(
        conversations: List<ConversationSummary>,
        onConversation: (String) -> Unit = {},
        onEverythingElse: () -> Unit = {},
        onAll: () -> Unit = {},
        onAsk: () -> Unit = {},
        onSend: () -> Unit = {},
    ): List<String> = render.texts(heightPx = 4_000) {
        run {
            CoachScreen(
                trainer = TrainerViewModel.State(
                    home = TrainerHome(waiting = null, earlier = emptyList()),
                    today = TEST_EPOCH_DAY,
                    note = WeekNote("Invented note.", 1_000, TEST_EPOCH_DAY, WeekNoteSource.TRAINER),
                ),
                coach = CoachViewModel.State(conversations = conversations, loaded = true),
                onBack = {},
                onEdit = {},
                onSend = onSend,
                onAskAboutTheWeek = onAsk,
                onReview = {},
                onPlan = {},
                onPlanAhead = {},
                onSeePlan = {},
                onReplyToNote = {},
                onConversation = onConversation,
                onAllConversations = onAll,
                onEverythingElse = onEverythingElse,
            )
        }
    }
}
