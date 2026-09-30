package com.metaself.app.ui.screen.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerPath
import com.metaself.app.domain.trainer.Wish
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.screen.trainer.PlanSessionViewModel
import com.metaself.app.ui.trainer.TrainerWording
import com.metaself.app.ui.trainer.WorkbenchWording
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * D106's page drawn: what is on it and in which order, and that the copy buttons call back. Nothing about
 * size (`CLAUDE.md`). Every word and file name is invented. JUnit 4 because Robolectric's runner is.
 */
@RunWith(RobolectricTestRunner::class)
class TrainerWorkbenchPageRenderTest {

    private val render = ComposeRender()
    private val copied = mutableListOf<String>()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `the four paths, then which instructions will be sent, then Send`() {
        val texts = page(TrainerWorkbenchViewModel.State(loaded = true))

        // The title bar is its own node, read after the content, so it is checked apart from the order.
        assertThat(texts).contains("Test the trainer's instructions")
        assertThat(texts).containsAtLeast(
            WorkbenchWording.INTRO,
            "Feedback on a session", "Plan my next session", "Review and plan the weeks ahead", "Change the rest of my plan",
            "Will send: the app's own instructions", WorkbenchWording.LOAD, WorkbenchWording.SAVE_APP_OWN,
            WorkbenchWording.SEND, WorkbenchWording.PRIVACY,
        ).inOrder()
        assertThat(texts).contains(WorkbenchWording.NO_SESSIONS)
        assertThat(texts).doesNotContain(WorkbenchWording.USE_APP_OWN)
    }

    @Test
    fun `a record not read on opening says so above the paths, and only then`() {
        val unread = page(TrainerWorkbenchViewModel.State(loaded = true, unreadable = true))

        assertThat(unread).containsAtLeast(WorkbenchWording.RECORD_NOT_READ, "Feedback on a session").inOrder()
        assertThat(page(TrainerWorkbenchViewModel.State(loaded = true))).doesNotContain(WorkbenchWording.RECORD_NOT_READ)
    }

    @Test
    fun `a loaded file is named, and the way back to the app's own is offered`() {
        val texts = page(TrainerWorkbenchViewModel.State(loaded = true, fileName = "invented.txt"))

        assertThat(texts).containsAtLeast("Will send: invented.txt", WorkbenchWording.USE_APP_OWN).inOrder()
    }

    @Test
    fun `changing the plan with none running says so, and Send is off`() {
        val texts = page(TrainerWorkbenchViewModel.State(loaded = true, path = TrainerPath.ADJUST, planRuns = false))

        assertThat(texts).contains("No plan is running.")
        assertThat(render.isEnabled(WorkbenchWording.SEND)).isFalse()
    }

    @Test
    fun `the reply is shown as written, and both copies call back`() {
        val texts = page(TrainerWorkbenchViewModel.State(loaded = true, path = TrainerPath.PLAN, reply = "Invented reply.", sent = "{}"))

        assertThat(texts).containsAtLeast("Invented reply.", WorkbenchWording.COPY_REPLY, WorkbenchWording.COPY_SENT).inOrder()
        render.click(WorkbenchWording.COPY_REPLY)
        render.click(WorkbenchWording.COPY_SENT)
        assertThat(copied).containsExactly("reply", "sent").inOrder()
    }

    @Test
    fun `a failure is worded as the trainer's, with no reply to copy`() {
        val texts = page(TrainerWorkbenchViewModel.State(loaded = true, path = TrainerPath.PLAN, failure = EstimateResult.NoKey))

        assertThat(texts).contains(TrainerWording.failure(EstimateResult.NoKey))
        assertThat(texts).containsNoneOf(WorkbenchWording.COPY_REPLY, WorkbenchWording.COPY_SENT)
    }

    @Test
    fun `before the record is read, neither the empty session list nor the missing plan is claimed`() {
        assertThat(page(TrainerWorkbenchViewModel.State())).doesNotContain(WorkbenchWording.NO_SESSIONS)
        assertThat(page(TrainerWorkbenchViewModel.State(path = TrainerPath.ADJUST))).doesNotContain(WorkbenchWording.NO_PLAN)
    }

    @Test
    fun `changing the plan asks under the Change-the-plan screen's own label`() {
        val texts = page(TrainerWorkbenchViewModel.State(loaded = true, path = TrainerPath.ADJUST, planRuns = true))

        assertThat(texts).contains("What should change?")
        assertThat(texts).doesNotContain(WorkbenchWording.NO_PLAN)
    }

    @Test
    fun `the plan path draws its four row labels, and Send is on once all four are answered`() {
        val answered = PlanSessionViewModel.Form(PlanActivity.RUN, TimeAvailable.MIN_30, Feeling.NORMAL, Wish.EASY)
        val texts = page(TrainerWorkbenchViewModel.State(loaded = true, path = TrainerPath.PLAN, plan = answered))

        assertThat(texts).containsAtLeast("WHAT TO TEST", "WHAT", "TIME I HAVE", "HOW I FEEL", "TODAY I WANT").inOrder()
        assertThat(render.isEnabled(WorkbenchWording.SEND)).isTrue()
    }

    @Test
    fun `the weeks-ahead path draws its two row labels`() {
        val texts = page(TrainerWorkbenchViewModel.State(loaded = true, path = TrainerPath.EVALUATE))

        assertThat(texts).containsAtLeast("HOW MANY WEEKS", "SESSIONS A WEEK I CAN MANAGE").inOrder()
    }

    @Test
    fun `while sending, the button says so and is off`() {
        val answered = PlanSessionViewModel.Form(PlanActivity.RUN, TimeAvailable.MIN_30, Feeling.NORMAL, Wish.EASY)
        val texts = page(TrainerWorkbenchViewModel.State(loaded = true, path = TrainerPath.PLAN, plan = answered, sending = true))

        assertThat(texts).contains(WorkbenchWording.SENDING)
        assertThat(texts).doesNotContain(WorkbenchWording.SEND)
        assertThat(render.isEnabled(WorkbenchWording.SENDING)).isFalse()
    }

    @Test
    fun `a failure after sending offers what was sent, and no reply to copy`() {
        val texts = page(
            TrainerWorkbenchViewModel.State(loaded = true, path = TrainerPath.PLAN, failure = EstimateResult.NoKey, sent = "{}"),
        )

        assertThat(texts).contains(WorkbenchWording.COPY_SENT)
        assertThat(texts).doesNotContain(WorkbenchWording.COPY_REPLY)
    }

    @Test
    fun `a file message and a notice are drawn`() {
        val texts = page(
            TrainerWorkbenchViewModel.State(
                loaded = true, fileMessage = WorkbenchWording.COULD_NOT_READ, notice = WorkbenchWording.SESSION_GONE,
            ),
        )

        assertThat(texts).containsAtLeast(WorkbenchWording.COULD_NOT_READ, WorkbenchWording.SESSION_GONE).inOrder()
    }

    private fun page(state: TrainerWorkbenchViewModel.State): List<String> = render.texts(heightPx = TALL) {
        TrainerWorkbenchPage(
            state = state,
            onPath = {}, onSession = {}, onPlan = {}, onEvaluate = {}, onAdjustWords = {},
            onLoad = {}, onUseAppOwn = {}, onSaveAppOwn = {}, onSend = {},
            onCopyReply = { copied += "reply" }, onCopySent = { copied += "sent" },
            onBack = {},
        )
    }

    private companion object {
        const val TALL = 20_000
    }
}
