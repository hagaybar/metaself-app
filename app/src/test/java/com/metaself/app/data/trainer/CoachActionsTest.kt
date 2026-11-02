package com.metaself.app.data.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.weight.InMemoryWeightRepository
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.trainer.FactGroup
import com.metaself.app.domain.trainer.FactSource
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.Proposal
import com.metaself.app.domain.trainer.ProposalKind
import com.metaself.app.domain.trainer.ProposalState
import com.metaself.app.domain.trainer.SheetFact
import com.metaself.app.domain.trainer.SheetMadeBy
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.WeeksPlan
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** D164: nothing changes until Confirm, and a card the record has moved past changes nothing. Every word is invented. */
class CoachActionsTest {

    private val messages = InMemoryConversationStore()
    private val programmes = FakeProgrammeStore()
    private val sheets = InMemorySheetStore()
    private val trainer = FakeTrainer()
    private val today = Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }
    private val now = Now { TEST_EPOCH_DAY * 86_400_000L }
    private val ask = AskTheTrainer(
        FakeMovementRecord(), FakeTrainerStore(), InMemoryWeightRepository(), FakeProfileRepository(aProfile()), trainer,
        InMemoryAboutMeStore(), programmes, today, now, CurrentYear { TEST_YEAR }, sheets = sheets,
    )
    private val actions = CoachActions(ask, messages, programmes, sheets, today, now)

    private suspend fun reply(proposal: Proposal): Long {
        val own = messages.addOwn("Invented.", 1, "talk:1")
        return messages.addReply(own, "Invented reply.", "a-model", 2, "", proposal)
    }

    private suspend fun proposal(id: Long): Proposal = messages.message(id)!!.proposal!!

    private suspend fun running(): Long {
        val id = programmes.add(PLAN)
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        return id
    }

    @Test
    fun `a prepared plan change leaves the running plan until Confirm`() = runTest {
        val plan = running()
        trainer.adjustments += TrainerReply.Answered(REST, "a-model")
        val id = reply(Proposal(ProposalKind.CHANGE_PLAN, words = "Invented words."))

        assertThat(actions.prepare(id)).isNull()

        assertThat(proposal(id).state).isEqualTo(ProposalState.READY)
        assertThat(proposal(id).title).isEqualTo("Invented new")
        assertThat(programmes.running()!!.id).isEqualTo(plan)

        actions.confirm(id)

        assertThat(proposal(id).state).isEqualTo(ProposalState.CONFIRMED)
        assertThat(programmes.running()!!.id).isEqualTo(proposal(id).programmeId)
    }

    @Test
    fun `Not now leaves the running plan`() = runTest {
        val plan = running()
        trainer.adjustments += TrainerReply.Answered(REST, "a-model")
        val id = reply(Proposal(ProposalKind.CHANGE_PLAN, words = "Invented words."))
        actions.prepare(id)

        actions.decline(id)

        assertThat(proposal(id).state).isEqualTo(ProposalState.DECLINED)
        assertThat(programmes.running()!!.id).isEqualTo(plan)
        // Over: a later Confirm does nothing.
        actions.confirm(id)
        assertThat(programmes.running()!!.id).isEqualTo(plan)
    }

    @Test
    fun `a job that does not answer leaves the card preparing, and preparing again works`() = runTest {
        running()
        trainer.adjustments += TrainerReply.Failed(EstimateResult.NoKey)
        trainer.adjustments += TrainerReply.Answered(REST, "a-model")
        val id = reply(Proposal(ProposalKind.CHANGE_PLAN, words = "Invented words."))

        assertThat(actions.prepare(id)).isEqualTo(EstimateResult.NoKey)
        assertThat(proposal(id).state).isEqualTo(ProposalState.PREPARING)

        assertThat(actions.prepare(id)).isNull()
        assertThat(proposal(id).state).isEqualTo(ProposalState.READY)
    }

    @Test
    fun `with no plan running a plan change no longer applies`() = runTest {
        val id = reply(Proposal(ProposalKind.CHANGE_PLAN, words = "Invented words."))

        actions.prepare(id)

        assertThat(proposal(id).state).isEqualTo(ProposalState.OUT_OF_DATE)
        assertThat(trainer.asked).isEmpty()
    }

    @Test
    fun `a change prepared for a plan since stopped changes nothing`() = runTest {
        val plan = running()
        trainer.adjustments += TrainerReply.Answered(REST, "a-model")
        val id = reply(Proposal(ProposalKind.CHANGE_PLAN, words = "Invented words."))
        actions.prepare(id)
        programmes.stop(plan, TEST_EPOCH_DAY)

        actions.confirm(id)

        assertThat(proposal(id).state).isEqualTo(ProposalState.OUT_OF_DATE)
        assertThat(programmes.running()).isNull()
    }

    @Test
    fun `stopping stops the plan it was prepared for, and no other`() = runTest {
        val plan = running()
        val id = reply(Proposal(ProposalKind.STOP_PLAN))
        actions.prepare(id)
        assertThat(programmes.running()!!.id).isEqualTo(plan)

        actions.confirm(id)
        assertThat(programmes.running()).isNull()

        val second = running()
        val old = reply(Proposal(ProposalKind.STOP_PLAN, ProposalState.READY, programmeId = plan))
        actions.confirm(old)
        assertThat(proposal(old).state).isEqualTo(ProposalState.OUT_OF_DATE)
        assertThat(programmes.running()!!.id).isEqualTo(second)
    }

    @Test
    fun `a new plan that cannot be asked for is said to be unreadable, and asks nothing`() = runTest {
        val id = reply(Proposal(ProposalKind.NEW_PLAN, goal = "not a goal", perWeek = 3))

        actions.prepare(id)

        assertThat(proposal(id).kind).isEqualTo(ProposalKind.UNREADABLE)
        assertThat(trainer.asked).isEmpty()
    }

    @Test
    fun `a fact goes on the sheet only on Confirm, marked as noticed`() = runTest {
        val id = reply(Proposal(ProposalKind.ADD_FACT, group = FactGroup.LIFE.name, text = "An invented fact."))

        actions.prepare(id)
        assertThat(sheets.newest()).isNull()

        actions.confirm(id)
        assertThat(sheets.newest()!!.current.single().text).isEqualTo("An invented fact.")
        assertThat(sheets.newest()!!.current.single().source).isEqualTo(FactSource.NOTICED)
    }

    @Test
    fun `a change or removal names a fact on the sheet, or no longer applies`() = runTest {
        sheets.add(1, listOf(SheetFact(1, FactGroup.LIFE, "An invented fact.", TEST_EPOCH_DAY, FactSource.NOTICED)), SheetMadeBy.REWRITE, "a-model")

        val missing = reply(Proposal(ProposalKind.REMOVE_FACT, fact = "Another invented fact."))
        actions.prepare(missing)
        assertThat(proposal(missing).state).isEqualTo(ProposalState.OUT_OF_DATE)

        val change = reply(Proposal(ProposalKind.CHANGE_FACT, fact = "an invented fact.", text = "A corrected fact."))
        actions.prepare(change)
        actions.confirm(change)
        assertThat(sheets.newest()!!.current.single().text).isEqualTo("A corrected fact.")

        val remove = reply(Proposal(ProposalKind.REMOVE_FACT, fact = "A corrected fact."))
        actions.prepare(remove)
        actions.confirm(remove)
        assertThat(sheets.newest()!!.current).isEmpty()
    }

    @Test
    fun `Confirm twice does the change once`() = runTest {
        val id = reply(Proposal(ProposalKind.ADD_FACT, group = FactGroup.LIFE.name, text = "An invented fact."))
        actions.prepare(id)

        actions.confirm(id)
        val versions = sheets.newest()!!.id
        actions.confirm(id)

        assertThat(sheets.newest()!!.id).isEqualTo(versions)
    }

    private companion object {
        val MONDAY = TEST_EPOCH_DAY - 3
        val WALK = PlannedSession(WorkoutKind.WALK, 20, PlannedEffort.EASY, "Invented walk")
        val PLAN = Programme(0, 1_000, ProgrammeAsk(2, 2), null, WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(WALK, WALK)) }, "Invented reason."), "a-model")
        val REST = WeeksPlan("Invented new", listOf(PlanWeek("lighter", listOf(WALK)), PlanWeek("w", listOf(WALK))), "Invented why.")
    }
}
