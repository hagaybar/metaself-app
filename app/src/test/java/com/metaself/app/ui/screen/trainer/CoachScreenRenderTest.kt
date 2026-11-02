package com.metaself.app.ui.screen.trainer

import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.trainer.Conversation
import com.metaself.app.domain.trainer.ConversationSummary
import com.metaself.app.domain.trainer.ThreadItem
import com.metaself.app.domain.trainer.TrainerHome
import com.metaself.app.domain.trainer.TrainerMessage
import com.metaself.app.domain.trainer.WeekNote
import com.metaself.app.domain.trainer.WeekNoteSource
import com.metaself.app.ui.ComposeSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * D157, D166. The menu and the details of what is sent are drawn in windows of their own, which [ComposeSession] reads.
 * Every word is invented.
 *
 * JUnit 4, because Compose's rule demands it: `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class CoachScreenRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val session by lazy { ComposeSession(compose) }

    /** D166: the box, the note, the quick links and one line on what is sent; the rest is behind the menu. */
    @Test
    fun `the screen is the box, the note, the quick links and one line, and no list of conversations`() {
        val texts = draw(summaries(5))

        assertThat(texts).containsAtLeast("Invented note.", "Reply ›", "How am I doing this week?", TalkWordingLine)
        assertThat(texts).containsNoneOf("Invented subject 0", "All conversations", "Everything else")
        assertThat(texts.none { it.startsWith("Sends to OpenAI") }).isTrue()
    }

    @Test
    fun `the menu holds All conversations and Everything else`() {
        var all = 0
        var everything = 0
        draw(summaries(1), onAll = { all++ }, onEverythingElse = { everything++ })

        assertThat(session.press("More")).containsAtLeast("All conversations", "Everything else")
        session.press("All conversations")
        assertThat(all).isEqualTo(1)
        session.press("More")
        session.press("Everything else")
        assertThat(everything).isEqualTo(1)
    }

    /** D160: with no conversations there is no way to an empty list. */
    @Test
    fun `no conversations, no All conversations in the menu`() {
        draw(emptyList())
        val menu = session.press("More")
        assertThat(menu).doesNotContain("All conversations")
        assertThat(menu).contains("Everything else")
    }

    /** D84, D166: the whole of what leaves the phone is one tap from the line. */
    @Test
    fun `the line opens the whole of what is sent, and Close closes it`() {
        draw(emptyList())
        val details = session.press(TalkWordingLine)
        assertThat(details.any { it.startsWith("Sends to OpenAI, with your key: your message;") }).isTrue()

        val closed = session.press("Close")
        assertThat(closed.none { it.startsWith("Sends to OpenAI") }).isTrue()
    }

    /** D158: the question fills the box; it sends nothing. */
    @Test
    fun `the quick question fills the box and sends nothing`() {
        var filled = 0
        var sent = 0
        draw(emptyList(), onAsk = { filled++ }, onSend = { sent++ })

        session.press("How am I doing this week?")

        assertThat(filled).isEqualTo(1)
        assertThat(sent).isEqualTo(0)
    }

    /** D166: the letter's link opens the newest letter. */
    @Test
    fun `See your weekly letter opens the newest letter`() {
        var opened: Long? = null
        val letter = summary(Conversation.letter(20_692L), "Invented letter")
        draw(listOf(letter), onLetter = { opened = it })

        session.press("See your weekly letter")

        assertThat(opened).isEqualTo(20_692L)
    }

    /** D166: once there is something in the box, the links step aside. */
    @Test
    fun `words in the box hide the quick links`() {
        val texts = draw(emptyList(), draft = "Invented words")
        assertThat(texts).doesNotContain("How am I doing this week?")
    }

    private fun summaries(count: Int): List<ConversationSummary> = (0 until count).map { summary("talk:$it", "Invented subject $it") }

    private fun summary(key: String, words: String): ConversationSummary {
        val message = TrainerMessage(1L, 1_000L, true, words, null, true, conversation = key)
        return ConversationSummary(key, ThreadItem.Said(message), words, message.atMillis, "Invented words.", true, false)
    }

    private fun draw(
        conversations: List<ConversationSummary>,
        draft: String = "",
        onEverythingElse: () -> Unit = {},
        onAll: () -> Unit = {},
        onAsk: () -> Unit = {},
        onSend: () -> Unit = {},
        onLetter: (Long) -> Unit = {},
    ): List<String> = session.start {
        CoachScreen(
            trainer = TrainerViewModel.State(
                home = TrainerHome(waiting = null, earlier = emptyList()),
                today = TEST_EPOCH_DAY,
                note = WeekNote("Invented note.", 1_000, TEST_EPOCH_DAY, WeekNoteSource.TRAINER),
            ),
            coach = CoachViewModel.State(conversations = conversations, loaded = true, draft = draft),
            onBack = {},
            onEdit = {},
            onSend = onSend,
            onAskAboutTheWeek = onAsk,
            onReview = {},
            onPlan = {},
            onPlanAhead = {},
            onSeePlan = {},
            onLetter = onLetter,
            onReplyToNote = {},
            onAllConversations = onAll,
            onEverythingElse = onEverythingElse,
        )
    }

    private companion object {
        const val TalkWordingLine = "Sent to OpenAI with your key · What's sent ›"
    }
}
