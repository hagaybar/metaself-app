package com.metaself.app.data.trainer

import com.metaself.app.data.ai.TrainerResponse
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.trainer.FactGroup
import com.metaself.app.domain.trainer.FactSource
import com.metaself.app.domain.trainer.GoalBounds
import com.metaself.app.domain.trainer.PlanAheadAsk
import com.metaself.app.domain.trainer.PlanShown
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeCalendar
import com.metaself.app.domain.trainer.Proposal
import com.metaself.app.domain.trainer.ProposalKind
import com.metaself.app.domain.trainer.ProposalState
import com.metaself.app.domain.trainer.SheetFact
import com.metaself.app.domain.trainer.SheetMadeBy
import com.metaself.app.domain.trainer.Sheets
import javax.inject.Inject

/**
 * D164: what happens to the change a reply names. The phone runs the app's existing jobs with their existing checks:
 * [prepare] gets it ready, and **nothing the owner relies on changes until [confirm]**. An AI job prepares a plan once,
 * as part of the reply he asked for; filling a week and the next block keep their answer at once on the plan page, so
 * here they run only on Confirm. There is no path here to the calorie target, a food record or a weight (D163).
 * Writes throw; the screen catches them. A returned failure is why the AI's job did not answer, and changes nothing.
 */
class CoachActions @Inject constructor(
    private val ask: AskTheTrainer,
    private val messages: ConversationStore,
    private val programmes: ProgrammeStore,
    private val sheets: SheetStore,
    private val today: Today,
    private val now: Now,
) {

    /** The change named by the reply to the owner's message [messageId], if it has one. */
    suspend fun prepareReplyTo(messageId: Long): EstimateResult? {
        val reply = messages.replyTo(messageId) ?: return null
        return prepare(reply.id)
    }

    suspend fun prepare(replyId: Long): EstimateResult? {
        val proposal = proposalOf(replyId)?.takeIf { it.state == ProposalState.PREPARING } ?: return null
        var failure: EstimateResult? = null
        val after = when (proposal.kind) {
            ProposalKind.CHANGE_PLAN -> when (val outcome = ask.adjust(proposal.words)) {
                is AskTheTrainer.Adjusted.Offered -> proposal.ready(outcome.programme)
                is AskTheTrainer.Adjusted.Failed -> proposal.also { failure = outcome.failure }
                AskTheTrainer.Adjusted.NotRunning -> proposal.outOfDate()
            }
            ProposalKind.NEW_PLAN -> newPlan(proposal) { failure = it }
            ProposalKind.STOP_PLAN -> ask.running()?.let { proposal.ready(it.programme) } ?: proposal.outOfDate()
            ProposalKind.FILL_WEEK -> if (ask.fillQuestion() != null) proposal.copy(state = ProposalState.READY) else proposal.outOfDate()
            ProposalKind.NEXT_BLOCK -> if (ask.nextBlockQuestion() != null) proposal.copy(state = ProposalState.READY) else proposal.outOfDate()
            ProposalKind.ADD_FACT -> proposal.copy(state = ProposalState.READY)
            ProposalKind.CHANGE_FACT, ProposalKind.REMOVE_FACT ->
                if (factNamed(proposal.fact) != null) proposal.copy(state = ProposalState.READY) else proposal.outOfDate()
            ProposalKind.UNREADABLE -> proposal.copy(state = ProposalState.READY)
        }
        if (after != proposal) messages.setProposal(replyId, after)
        return failure
    }

    private suspend fun newPlan(proposal: Proposal, failed: (EstimateResult) -> Unit): Proposal {
        val day = today().toEpochDay()
        val planAsk = runCatching {
            val goal = requireNotNull(GoalBounds.checked(TrainerResponse.goalOf(requireNotNull(proposal.goal)), day))
            PlanAheadAsk(goal, requireNotNull(proposal.perWeek), proposal.words)
        }.getOrNull() ?: return proposal.copy(kind = ProposalKind.UNREADABLE, state = ProposalState.READY)
        val outcome = try {
            ask.planAhead(planAsk)
        } catch (refused: IllegalArgumentException) {
            // The goal or the sessions a week are outside what a plan may be.
            return proposal.copy(kind = ProposalKind.UNREADABLE, state = ProposalState.READY)
        }
        return when (outcome) {
            is AskTheTrainer.Evaluated.Offered -> {
                val replacing = ask.replacingFrom()
                proposal.ready(outcome.programme).copy(
                    says = outcome.programme.goalView?.says.orEmpty(),
                    startEpochDay = replacing ?: ProgrammeCalendar.startFor(day),
                    replaces = replacing != null,
                )
            }
            is AskTheTrainer.Evaluated.Failed -> proposal.also { failed(outcome.failure) }
        }
    }

    /** Does the change. Only a READY proposal, once; one the record has moved past becomes OUT_OF_DATE and changes nothing. */
    suspend fun confirm(replyId: Long): EstimateResult? {
        val proposal = proposalOf(replyId)?.takeIf { it.state == ProposalState.READY } ?: return null
        var failure: EstimateResult? = null
        val after = try {
            when (proposal.kind) {
                ProposalKind.CHANGE_PLAN -> {
                    ask.keepAdjusted(programme(proposal))
                    proposal.confirmed()
                }
                ProposalKind.NEW_PLAN -> proposal.confirmed().copy(startEpochDay = ask.keepProgramme(requireNotNull(proposal.programmeId)))
                ProposalKind.STOP_PLAN ->
                    if (ask.running()?.programme?.id == proposal.programmeId) {
                        ask.stop(requireNotNull(proposal.programmeId))
                        proposal.confirmed()
                    } else {
                        proposal.outOfDate()
                    }
                ProposalKind.FILL_WEEK -> filled(proposal, ask.fillWeek()) { failure = it }
                ProposalKind.NEXT_BLOCK -> filled(proposal, ask.nextBlock()) { failure = it }
                ProposalKind.ADD_FACT -> addFact(proposal)
                ProposalKind.CHANGE_FACT -> onFact(proposal) { facts, fact, day -> Sheets.edit(facts, fact.id, proposal.text, day) }
                ProposalKind.REMOVE_FACT -> onFact(proposal) { facts, fact, day -> Sheets.remove(facts, fact.id, day) }
                ProposalKind.UNREADABLE -> proposal
            }
        } catch (refused: IllegalStateException) {
            proposal.outOfDate()
        } catch (refused: IllegalArgumentException) {
            proposal.outOfDate()
        }
        if (after != proposal) messages.setProposal(replyId, after)
        return failure
    }

    suspend fun decline(replyId: Long) {
        val proposal = proposalOf(replyId)?.takeIf { it.state == ProposalState.READY } ?: return
        messages.setProposal(replyId, proposal.copy(state = ProposalState.DECLINED))
    }

    /** The prepared plan's weeks, for See the weeks. Reads only; null when there is nothing to show. */
    suspend fun weeks(replyId: Long): PlanShown? {
        val proposal = proposalOf(replyId)?.takeIf { it.state == ProposalState.READY } ?: return null
        if (proposal.kind != ProposalKind.CHANGE_PLAN && proposal.kind != ProposalKind.NEW_PLAN) return null
        val start = proposal.startEpochDay ?: ask.running()?.programme?.startEpochDay ?: MovementWeek.mondayOf(today().toEpochDay())
        return ask.planShownOf(programme(proposal), start)
    }

    private fun filled(proposal: Proposal, outcome: AskTheTrainer.Filled, failed: (EstimateResult) -> Unit): Proposal = when (outcome) {
        is AskTheTrainer.Filled.Done -> proposal.confirmed().copy(title = outcome.programme.plan.title, why = outcome.why)
        is AskTheTrainer.Filled.Failed -> proposal.also { failed(outcome.failure) }
        AskTheTrainer.Filled.NotOffered -> proposal.outOfDate()
    }

    /** "I noticed", which is what it is: he agreed to it, he did not say it (D4). */
    private suspend fun addFact(proposal: Proposal): Proposal {
        val group = FactGroup.entries.first { it.name == proposal.group }
        val previous = sheets.newest()?.facts.orEmpty()
        val after = Sheets.remembered(previous, listOf(group to proposal.text), today().toEpochDay(), FactSource.NOTICED)
        if (after === previous) return proposal.outOfDate()
        sheets.add(now(), after, SheetMadeBy.CONVERSATION, null)
        return proposal.confirmed()
    }

    private suspend fun onFact(proposal: Proposal, change: (List<SheetFact>, SheetFact, Long) -> List<SheetFact>): Proposal {
        val fact = factNamed(proposal.fact) ?: return proposal.outOfDate()
        sheets.add(now(), change(sheets.newest()?.facts.orEmpty(), fact, today().toEpochDay()), SheetMadeBy.OWNER, null)
        return proposal.confirmed()
    }

    private suspend fun factNamed(words: String): SheetFact? =
        sheets.newest()?.current?.firstOrNull { it.text.trim().equals(words.trim(), ignoreCase = true) }

    private suspend fun programme(proposal: Proposal): Programme =
        checkNotNull(programmes.all().firstOrNull { it.id == proposal.programmeId }) { "the version prepared is gone" }

    private suspend fun proposalOf(replyId: Long): Proposal? = messages.message(replyId)?.proposal

    private fun Proposal.ready(programme: Programme) =
        copy(state = ProposalState.READY, programmeId = programme.id, title = programme.plan.title, why = programme.plan.why)

    private fun Proposal.confirmed() = copy(state = ProposalState.CONFIRMED)

    private fun Proposal.outOfDate() = copy(state = ProposalState.OUT_OF_DATE)
}
