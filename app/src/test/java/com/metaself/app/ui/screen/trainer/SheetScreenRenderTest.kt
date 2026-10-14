package com.metaself.app.ui.screen.trainer

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.trainer.FactGroup
import com.metaself.app.domain.trainer.FactSource
import com.metaself.app.domain.trainer.SheetFact
import com.metaself.app.domain.trainer.SheetMadeBy
import com.metaself.app.domain.trainer.SheetVersion
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.trainer.SheetWording
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4.
 *
 * Which words What I know about you draws (D149, D150), in which order, and that each door calls back. Not provable
 * here (CLAUDE.md, Testing): the strike-through, a card's colour. Every fact is invented.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "+h8000dp")
class SheetScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `before any rewrite it says what will appear and when`() {
        val texts = draw(SheetViewModel.State(loaded = true, today = TEST_EPOCH_DAY))

        assertThat(texts).contains(SheetWording.TITLE)
        assertThat(texts).containsAtLeast(SheetWording.HEADER, SheetWording.PRIVACY, SheetWording.EMPTY, SheetWording.FULL_CONVERSATION).inOrder()
        assertThat(texts).doesNotContain(SheetWording.GO_BACK)
    }

    /** The groups in the page's order, each fact with its line; then No longer true; then the footer. */
    @Test
    fun `the groups, each fact with where it came from, then no longer true, then the footer`() {
        val texts = draw(state(listOf(FIRST, SECOND)))

        val order = listOf(
            SheetWording.HEADER, "BODY", "An invented ache.", "Since Sat 29 Aug · you said",
            "GOALS IN YOUR WORDS", "An invented goal.", "Since Mon 31 Aug · I noticed",
            "NO LONGER TRUE", "An invented old fact.", "24 Aug – 2 Sep · dropped Wed 2 Sep",
            "Removed by him.", "24 Aug – 1 Sep · you removed it Tue 1 Sep",
        )
        order.zipWithNext().forEach { (first, second) -> assertWithMessage("$first before $second").that(render.isDrawnBefore(first, second)).isTrue() }
        assertThat(texts).containsNoneOf("HOW YOU LIKE TO TRAIN", "LIFE", "Dropped long ago.", SheetWording.EMPTY)
        assertThat(texts.any { it.startsWith("Last rewritten Thu 3 Sep, ") }).isTrue()
    }

    @Test
    fun `edit and remove call back with the fact, and the footer opens the conversation`() {
        var edited: Long? = null
        var removed: Long? = null
        var opened = 0
        draw(state(listOf(FIRST)), onEdit = { edited = it }, onRemove = { removed = it }, onConversation = { opened++ })

        render.click(SheetWording.EDIT)
        render.click(SheetWording.REMOVE)
        render.click(SheetWording.FULL_CONVERSATION)

        assertThat(edited).isEqualTo(1L)
        assertThat(removed).isEqualTo(1L)
        assertThat(opened).isEqualTo(1)
    }

    /** D150: Go back is offered while the newest version is a rewrite with one before it. */
    @Test
    fun `go back is offered only for the newest rewrite`() {
        var asked = 0
        val texts = draw(state(listOf(FIRST, SECOND)), onGoBack = { asked++ })
        assertThat(texts).contains(SheetWording.GO_BACK)
        render.click(SheetWording.GO_BACK)
        assertThat(asked).isEqualTo(1)

        assertThat(draw(state(listOf(FIRST)))).doesNotContain(SheetWording.GO_BACK)
        assertThat(draw(state(listOf(FIRST, SECOND, SECOND.copy(id = 3, madeBy = SheetMadeBy.OWNER, model = null))))).doesNotContain(SheetWording.GO_BACK)
    }

    @Test
    fun `a sheet with nothing current says so, and an unreadable one says why`() {
        val dropped = SheetVersion(1, NOW, listOf(OLD), SheetMadeBy.REWRITE, "a-model")
        assertThat(draw(state(listOf(dropped)))).containsAtLeast(SheetWording.NONE_NOW, "NO LONGER TRUE")
        assertThat(draw(SheetViewModel.State(unreadable = true))).contains(SheetWording.UNREADABLE)
    }

    private fun draw(
        state: SheetViewModel.State,
        onEdit: (Long) -> Unit = {},
        onRemove: (Long) -> Unit = {},
        onGoBack: () -> Unit = {},
        onConversation: () -> Unit = {},
    ): List<String> = render.texts {
        SheetScreen(
            state = state, onBack = {}, onEdit = onEdit, onEditText = {}, onSaveEdit = {}, onCancelEdit = {}, onRemove = onRemove,
            onConfirmRemove = {}, onCancelRemove = {}, onGoBack = onGoBack, onConfirmGoBack = {}, onCancelGoBack = {},
            onConversation = onConversation,
        )
    }

    private fun state(versions: List<SheetVersion>) = SheetViewModel.State(versions = versions, today = TEST_EPOCH_DAY, loaded = true)

    private companion object {
        const val NOW = TEST_EPOCH_DAY * 86_400_000L + 12 * 3_600_000L
        val OLD = SheetFact(9, FactGroup.LIFE, "An invented old fact.", TEST_EPOCH_DAY - 10, FactSource.SAID, droppedEpochDay = TEST_EPOCH_DAY - 1)
        val FIRST = SheetVersion(
            1, NOW - 86_400_000L * 7,
            listOf(SheetFact(1, FactGroup.BODY, "An invented ache.", TEST_EPOCH_DAY - 5, FactSource.SAID)),
            SheetMadeBy.REWRITE, "a-model",
        )

        /** The newest rewrite: the ache kept, a goal added, an old fact dropped, one removed by him, one dropped long ago. */
        val SECOND = SheetVersion(
            2, NOW,
            listOf(
                SheetFact(1, FactGroup.BODY, "An invented ache.", TEST_EPOCH_DAY - 5, FactSource.SAID),
                SheetFact(2, FactGroup.GOALS, "An invented goal.", TEST_EPOCH_DAY - 3, FactSource.NOTICED),
                OLD,
                SheetFact(10, FactGroup.LIFE, "Removed by him.", TEST_EPOCH_DAY - 10, FactSource.SAID, droppedEpochDay = TEST_EPOCH_DAY - 2, removedByOwner = true),
                SheetFact(11, FactGroup.LIFE, "Dropped long ago.", TEST_EPOCH_DAY - 60, FactSource.SAID, droppedEpochDay = TEST_EPOCH_DAY - 30),
            ),
            SheetMadeBy.REWRITE, "a-model",
        )
    }
}
