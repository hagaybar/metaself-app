package com.metaself.app.data.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.letter.FakeLetterStore
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.weight.InMemoryWeightRepository
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.trainer.FactGroup
import com.metaself.app.domain.trainer.FactSource
import com.metaself.app.domain.trainer.RewrittenFact
import com.metaself.app.domain.trainer.SheetFact
import com.metaself.app.domain.trainer.SheetMadeBy
import com.metaself.app.domain.trainer.TrainerReply
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * D150: the weekly rewrite, over in-memory stores. Now is 15:00 on [TEST_EPOCH_DAY], which the tests treat as the
 * Sunday being rewritten; every fact and word is invented.
 */
class RewriteTheSheetTest {

    private val store = FakeTrainerStore()
    private val trainer = FakeTrainer()
    private val messages = InMemoryConversationStore()
    private val sheets = InMemorySheetStore()
    private val writer = FakeSheetWriter()
    private val aboutMe = InMemoryAboutMeStore("  Invented note.  ")
    private var now = NOW

    @Test
    fun `with nothing said, nothing is asked`() = runTest {
        messages.addOwn("Invented question.", NOW - 9 * DAY)
        sheets.add(NOW - 8 * DAY, emptyList(), SheetMadeBy.REWRITE, "a-model")

        assertThat(rewrite().rewrite(SUNDAY)).isEqualTo(RewriteTheSheet.Outcome.NothingSaid)
        assertThat(writer.asked).isEmpty()
    }

    @Test
    fun `the first rewrite reads the whole conversation and stores a version made by the rewrite`() = runTest {
        val asked = messages.addOwn("Invented question.", NOW - 20 * DAY)
        messages.addReply(asked, "Invented answer.", "a-model", NOW - 20 * DAY + 1_000)
        writer.replies += TrainerReply.Answered(listOf(RewrittenFact(null, FactGroup.LIFE, "A new invented fact.", TEST_EPOCH_DAY - 20, FactSource.SAID)), "a-model")

        val outcome = rewrite().rewrite(SUNDAY)

        val request = writer.asked.single()
        assertThat(request.since).isNull()
        assertThat(request.facts).isEmpty()
        assertThat(request.aboutMe).isEqualTo("Invented note.")
        assertThat(request.today).isEqualTo(TEST_EPOCH_DAY)
        assertThat(request.conversation.map { it.text }).containsExactly("Invented question.", "Invented answer.").inOrder()
        val version = sheets.versions.value.single()
        assertThat(outcome).isEqualTo(RewriteTheSheet.Outcome.Written(version.id))
        assertThat(version.madeBy).isEqualTo(SheetMadeBy.REWRITE)
        assertThat(version.model).isEqualTo("a-model")
        assertThat(version.atMillis).isEqualTo(NOW)
        assertThat(version.facts).containsExactly(SheetFact(1, FactGroup.LIFE, "A new invented fact.", TEST_EPOCH_DAY - 20, FactSource.SAID))
    }

    @Test
    fun `a later rewrite reads only since the last, and sends current and removed facts, not dropped ones`() = runTest {
        val kept = SheetFact(1, FactGroup.BODY, "Kept.", TEST_EPOCH_DAY - 9, FactSource.SAID, edited = true)
        val removed = SheetFact(2, FactGroup.GOALS, "Removed by him.", TEST_EPOCH_DAY - 9, FactSource.SAID, droppedEpochDay = TEST_EPOCH_DAY - 2, removedByOwner = true)
        val dropped = SheetFact(3, FactGroup.LIFE, "Dropped by a rewrite.", TEST_EPOCH_DAY - 20, FactSource.NOTICED, droppedEpochDay = TEST_EPOCH_DAY - 7)
        messages.addOwn("Before the rewrite.", NOW - 8 * DAY)
        sheets.add(NOW - 7 * DAY, listOf(kept, dropped), SheetMadeBy.REWRITE, "a-model")
        sheets.add(NOW - 2 * DAY, listOf(kept, removed, dropped), SheetMadeBy.OWNER, null)
        messages.addOwn("After the rewrite.", NOW - DAY)
        writer.replies += TrainerReply.Answered(listOf(RewrittenFact(1, FactGroup.BODY, "Kept.", TEST_EPOCH_DAY - 9, FactSource.SAID)), "a-model")

        rewrite().rewrite(SUNDAY)

        val request = writer.asked.single()
        assertThat(request.since).isEqualTo(LocalDateTime.ofInstant(Instant.ofEpochMilli(NOW - 7 * DAY), ZoneId.systemDefault()))
        assertThat(request.conversation.map { it.text }).containsExactly("After the rewrite.")
        assertThat(request.facts).containsExactly(kept, removed).inOrder()
        val written = sheets.versions.value.last().facts
        assertThat(written.filter { it.current }).containsExactly(kept)
        assertThat(written.map { it.id }).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun `a rewrite undone by go back is read again`() = runTest {
        sheets.add(NOW - 14 * DAY, emptyList(), SheetMadeBy.REWRITE, "a-model")
        messages.addOwn("Read by the undone rewrite.", NOW - 10 * DAY)
        sheets.add(NOW - 7 * DAY, emptyList(), SheetMadeBy.REWRITE, "a-model")
        sheets.add(NOW - 6 * DAY, emptyList(), SheetMadeBy.OWNER, null, restoredFrom = 1)
        writer.replies += TrainerReply.Answered(emptyList(), "a-model")

        rewrite().rewrite(SUNDAY)

        assertThat(writer.asked.single().conversation.map { it.text }).containsExactly("Read by the undone rewrite.")
    }

    @Test
    fun `one rewrite a Sunday`() = runTest {
        messages.addOwn("Invented question.", NOW - 2 * HOUR)
        sheets.add(NOW - HOUR, emptyList(), SheetMadeBy.REWRITE, "a-model")

        assertThat(rewrite().rewrite(SUNDAY)).isEqualTo(RewriteTheSheet.Outcome.AlreadyDone)
        assertThat(writer.asked).isEmpty()
    }

    @Test
    fun `a failed rewrite stores nothing, and the next reads the longer span`() = runTest {
        messages.addOwn("First week.", NOW - 8 * DAY)
        writer.replies += TrainerReply.Failed(EstimateResult.Unreachable())

        assertThat(rewrite().rewrite(SUNDAY - 7)).isEqualTo(RewriteTheSheet.Outcome.Failed(EstimateResult.Unreachable()))
        assertThat(sheets.versions.value).isEmpty()

        messages.addOwn("Second week.", NOW - DAY)
        writer.replies += TrainerReply.Answered(emptyList(), "a-model")
        rewrite().rewrite(SUNDAY)

        assertThat(writer.asked.last().conversation.map { it.text }).containsExactly("First week.", "Second week.").inOrder()
    }

    private fun rewrite() = RewriteTheSheet(writer, sheets, talk(), aboutMe, Now { now })

    private fun talk() = TalkToTheTrainer(ask(), trainer, messages, store, FakeLetterStore(), InMemoryWeekNoteStore(), Now { now })

    private fun ask() = AskTheTrainer(
        FakeMovementRecord(), store, InMemoryWeightRepository(), FakeProfileRepository(aProfile()), trainer, aboutMe, FakeProgrammeStore(),
        Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }, Now { now }, CurrentYear { TEST_YEAR },
    )

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
        const val NOW = TEST_EPOCH_DAY * DAY + 15 * HOUR

        /** Treated as the Sunday being rewritten. */
        const val SUNDAY = TEST_EPOCH_DAY
    }
}
