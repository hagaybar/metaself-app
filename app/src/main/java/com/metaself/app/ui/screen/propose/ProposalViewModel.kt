package com.metaself.app.ui.screen.propose

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.FoodRepository
import com.metaself.app.data.food.MealKeeper
import com.metaself.app.data.food.ToLog
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.Asked
import com.metaself.app.domain.ai.MealConversation
import com.metaself.app.domain.ai.MealConversationAsker
import com.metaself.app.domain.ai.MealEstimator
import com.metaself.app.domain.ai.Next
import com.metaself.app.domain.ai.StepResult
import com.metaself.app.domain.portion.Portions
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.food.MealWording
import com.metaself.app.ui.guarded
import com.metaself.app.ui.propose.ProposalWording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Describing a meal — in a short conversation when the model needs one (D58) — correcting what
 * came back, and accepting it.
 *
 * *Work it out* sends one request. When the meal needs no question it is the estimate, as before;
 * otherwise the model offers questions, and each answer below the offered number asks for the next
 * one — one request each — until the final analysis. Which request comes next is
 * [MealConversation]'s decision; this sends it and shows what came. **Nothing is stored before the
 * owner's end choice, and the conversation never is.**
 *
 * **One live request** (D58 §12.1): each carries a generation, and a reply to a request that has
 * since been superseded — by Back, by leaving — is dropped unread. Back while waiting cancels it.
 *
 * **Nothing here takes the app down.** The estimator reports its own failures as results; anything
 * it throws instead is caught by [guarded], written to the problem log, and leaves the owner where
 * any other failure does — describing, with his words kept. Asking stores nothing, so the sentence
 * says nothing was changed.
 */
@HiltViewModel
class ProposalViewModel @Inject constructor(
    private val estimator: MealEstimator,
    private val problems: ProblemLog,
    private val foods: FoodRepository,
    savedState: SavedStateHandle = SavedStateHandle(),
    private val asker: MealConversationAsker,
    private val keeper: MealKeeper,
) : ViewModel() {

    private val _state = MutableStateFlow<ProposalUiState>(ProposalUiState.Describing())
    val state: StateFlow<ProposalUiState> = _state.asStateFlow()

    /**
     * The owner's words, kept so a failure never loses them (D8).
     *
     * Seeded here, at construction, from the words the food search carried in when nothing there
     * matched. It has to be here and not in a later call: this is a plain `var`, not something the
     * screen observes, and the text field reads it once when it is first composed. Filled in
     * afterwards it would change nothing on screen, and the owner would arrive at an empty box
     * having been told his words came with him.
     *
     * Carrying words in is not the same as asking. Nothing here calls the model — the owner presses
     * the button (D8).
     */
    var description: String = savedState.get<String>("text").orEmpty()
        private set

    /** The request in flight, cancelled by Back (D58 §12.1). */
    private var inFlight: Job? = null

    /** Bumped by every request and by Back: a reply for an older generation is dropped. */
    private var generation = 0L

    /** *Work it out*: the first request, which estimates the meal or offers questions (D58 §2.2). */
    fun describe(text: String) {
        description = text
        if (text.isBlank()) return
        // Back while waiting cancels, and returns to his words in the box (D58 §12.1).
        send(
            waiting = ProposalUiState.Waiting(returnTo = ProposalUiState.Describing()),
            onRefused = { ProposalUiState.Describing(refused = ActionRefused.NOTHING_CHANGED) },
        ) { mine ->
            when (val result = asker.open(text)) {
                is StepResult.Estimate -> shown(result.result, afterConversation = null)
                is StepResult.Failed -> described(result.failure)
                is StepResult.Ask -> {
                    val chat = MealConversation.open(text, result.question, result.planned, asker.remainingToday())
                    if (chat == null) described(EstimateResult.CeilingReached) else ProposalUiState.Offer(chat)
                }
                // A first reply is never *no more questions*; read as needing none, it is the best guess.
                StepResult.Enough -> null.also {
                    val next = MealConversation.bestGuess(asker.remainingToday())
                    if (mine == generation) go(next, ProposalUiState.Describing())
                }
            }
        }
    }

    /** *OK* on the offer: question one, already in hand — nothing is sent. */
    fun acceptQuestions() {
        val offer = _state.value as? ProposalUiState.Offer ?: return
        _state.value = ProposalUiState.Asking(offer.chat)
    }

    /** *Use your best guess* on the offer: the final analysis with no answers (D58 §12.2). */
    fun bestGuess() {
        val offer = _state.value as? ProposalUiState.Offer ?: return
        decide(offer) { MealConversation.bestGuess(it) }
    }

    /** He answered the question on screen — a tapped answer, or his *Other* words. */
    fun answer(text: String) {
        val asking = _state.value as? ProposalUiState.Asking ?: return
        if (text.isBlank()) return
        decide(asking) { MealConversation.answer(asking.chat, text, it) }
    }

    /** *That's enough, go ahead*. */
    fun enough() {
        val asking = _state.value as? ProposalUiState.Asking ?: return
        decide(asking) { MealConversation.enough(asking.chat, it) }
    }

    /**
     * *Try again*: the same request as the one that failed — decided again from the day's
     * allowance, so a retry never spends the request kept for the result, nor asks for more
     * thinking on the day's last one (D58 §7, §12.4).
     */
    fun retry() {
        val failed = _state.value as? ProposalUiState.ConversationFailed ?: return
        val again = failed.retry ?: return
        decide(failed) { remaining ->
            when (again) {
                is Next.Step -> MealConversation.beforeStep(again.chat, again.asked, remaining)
                is Next.Final -> MealConversation.beforeFinal(again.asked, remaining)
                is Next.Show, Next.Ceiling -> again
            }
        }
    }

    /** *Use your best guess with what you've said so far*, after a question step failed (D58 §9). */
    fun bestGuessSoFar() {
        val failed = _state.value as? ProposalUiState.ConversationFailed ?: return
        val asked = failed.bestGuessWith ?: return
        decide(failed) { MealConversation.bestGuessSoFar(asked, it) }
    }

    /**
     * Back, one stage (D58 §2.5, §12.1): a request in flight is cancelled; a question goes to the
     * one before it, the first to the offer, the offer to his words. False when there is nothing to
     * step back to, and the screen is left as it always was.
     */
    fun back(): Boolean {
        val current = _state.value
        val to: ProposalUiState = when (current) {
            is ProposalUiState.Waiting -> current.returnTo ?: return false
            is ProposalUiState.Asking -> MealConversation.back(current.chat)
                ?.let { ProposalUiState.Asking(it) }
                ?: ProposalUiState.Offer(current.chat)
            is ProposalUiState.Offer -> ProposalUiState.Describing()
            is ProposalUiState.ConversationFailed -> current.returnTo ?: ProposalUiState.Describing()
            is ProposalUiState.Describing, is ProposalUiState.Proposed -> return false
        }
        inFlight?.cancel()
        generation++
        _state.value = to
        return true
    }

    /**
     * Ask again with a sentence added.
     *
     * This is what handles what a typed amount cannot: the same bowl cooked richer. One extra call,
     * only when the owner decides the first answer was not close enough. It replaces every row.
     * After a conversation it re-runs the final analysis with the same answers (D58 §5.1).
     */
    fun tellItMore(extra: String) {
        if (description.isBlank() || extra.isBlank()) return
        val proposed = _state.value as? ProposalUiState.Proposed
        val asked = proposed?.afterConversation
        val kept = proposed
        send(
            waiting = ProposalUiState.Waiting(
                kind = if (asked != null) WaitingFor.RESULT else WaitingFor.ANSWER,
                // Back returns to the rows after a conversation, to his words otherwise (§12.1).
                returnTo = if (asked != null) kept else ProposalUiState.Describing(),
            ),
            onRefused = {
                kept?.copy(refused = ActionRefused.NOTHING_CHANGED)
                    ?: ProposalUiState.Describing(refused = ActionRefused.NOTHING_CHANGED)
            },
        ) { _ ->
            if (asked != null) {
                val deep = asker.remainingToday() >= 2
                shown(asker.finish(description, asked, moreDetail = extra, deep = deep), afterConversation = asked)
            } else {
                shown(estimator.estimate(description, extra), afterConversation = null)
            }
        }
    }

    /**
     * Decide the next request from the day's allowance, then send it; [from] is where Back returns
     * — or, from a failure, the stage the failure came from, so one Back always does (§12.1).
     */
    private fun decide(from: ProposalUiState, next: (remaining: Int) -> Next) {
        val back = (from as? ProposalUiState.ConversationFailed)?.returnTo ?: from
        // Taken synchronously, so a second tap in the same frame finds no question to answer.
        _state.value = ProposalUiState.Waiting(kind = WaitingFor.QUESTION, returnTo = back)
        val mine = ++generation
        inFlight = guarded(problems, onRefused = { if (mine == generation) _state.value = refusedAt(from) }) {
            val decided = next(asker.remainingToday())
            if (mine == generation) go(decided, returnTo = back)
        }
    }

    /** Send what [next] says, and show what came (D58 §2, §9). */
    private fun go(next: Next, returnTo: ProposalUiState?) {
        when (next) {
            is Next.Show -> _state.value = ProposalUiState.Asking(next.chat)
            Next.Ceiling -> _state.value = ProposalUiState.ConversationFailed(
                failure = ProposalWording.failure(EstimateResult.CeilingReached),
                asked = askedIn(returnTo),
                returnTo = returnTo,
            )
            is Next.Step -> send(
                waiting = ProposalUiState.Waiting(WaitingFor.QUESTION, returnTo),
                onRefused = { refusedAt(returnTo) },
            ) { mine ->
                when (val result = asker.next(description, next.asked, next.chat.cap)) {
                    // Onto the conversation as the request left it, later questions already dropped.
                    is StepResult.Ask ->
                        ProposalUiState.Asking(MealConversation.arrived(next.chat, result.question, result.planned))
                    StepResult.Enough -> null.also {
                        val final = MealConversation.beforeFinal(next.asked, asker.remainingToday())
                        if (mine == generation) go(final, returnTo)
                    }
                    is StepResult.Failed -> ProposalUiState.ConversationFailed(
                        failure = ProposalWording.failure(result.failure),
                        asked = next.asked,
                        answer = (result.failure as? EstimateResult.Unreadable)?.answer,
                        retry = next,
                        bestGuessWith = next.asked,
                        returnTo = returnTo,
                    )
                    // A step never estimates; read as unusable.
                    is StepResult.Estimate -> ProposalUiState.ConversationFailed(
                        failure = ProposalWording.failure(EstimateResult.Unreadable("not a question")),
                        asked = next.asked,
                        retry = next,
                        bestGuessWith = next.asked,
                        returnTo = returnTo,
                    )
                }
            }
            is Next.Final -> send(
                waiting = ProposalUiState.Waiting(WaitingFor.RESULT, returnTo, next.allowanceOnly),
                onRefused = { refusedAt(returnTo) },
            ) { _ ->
                when (val result = asker.finish(description, next.asked, deep = next.deep)) {
                    is EstimateResult.Proposed -> shown(result, afterConversation = next.asked)
                    else -> ProposalUiState.ConversationFailed(
                        failure = ProposalWording.failure(result),
                        asked = next.asked,
                        answer = (result as? EstimateResult.Unreadable)?.answer,
                        retry = next.takeUnless { result is EstimateResult.CeilingReached },
                        returnTo = returnTo,
                    )
                }
            }
        }
    }

    /**
     * One request: [waiting] shown at once, the reply shown if it is still wanted. [block] is given
     * its generation and returns the state to show, or null when it has handed on to another
     * request itself — which it does only while its generation is still the current one.
     */
    private fun send(
        waiting: ProposalUiState,
        onRefused: () -> ProposalUiState,
        block: suspend (mine: Long) -> ProposalUiState?,
    ) {
        _state.value = waiting
        val mine = ++generation
        inFlight = guarded(problems, onRefused = { if (mine == generation) _state.value = onRefused() }) {
            val shown = block(mine)
            if (shown != null && mine == generation) _state.value = shown
        }
    }

    /** A refusal said on the stage he was on, which is kept (D58 §12.3). */
    private fun refusedAt(stage: ProposalUiState?): ProposalUiState = when (stage) {
        is ProposalUiState.Asking -> stage.copy(refused = ActionRefused.NOTHING_CHANGED)
        is ProposalUiState.Offer -> stage.copy(refused = ActionRefused.NOTHING_CHANGED)
        is ProposalUiState.ConversationFailed -> stage
        else -> ProposalUiState.Describing(refused = ActionRefused.NOTHING_CHANGED)
    }

    private fun askedIn(stage: ProposalUiState?): List<Asked> = when (stage) {
        is ProposalUiState.Asking -> stage.chat.asked(stage.chat.answers.size.coerceAtMost(stage.chat.at))
        is ProposalUiState.ConversationFailed -> stage.asked
        else -> emptyList()
    }

    /** An answer on screen, or the failure it came to, with his words kept (D8). */
    private suspend fun shown(result: EstimateResult, afterConversation: List<Asked>?): ProposalUiState =
        when (result) {
            is EstimateResult.Proposed -> {
                // Only now, with the answer in: his foods are read on the phone, once, and
                // never go anywhere (D16, D53 §4). A food made while the answer is on screen is
                // not seen until he asks again.
                val offered = foods.observeOffered().first()
                ProposalUiState.Proposed(
                    rows = result.proposal.items.map { ProposalRow.of(it, offered) },
                    note = result.proposal.note,
                    dropped = result.proposal.dropped,
                    answer = result.proposal.answer,
                    afterConversation = afterConversation,
                )
            }
            else -> if (afterConversation != null) {
                ProposalUiState.ConversationFailed(
                    failure = ProposalWording.failure(result),
                    asked = afterConversation,
                    answer = (result as? EstimateResult.Unreadable)?.answer,
                )
            } else {
                described(result)
            }
        }

    private fun described(result: EstimateResult): ProposalUiState = ProposalUiState.Describing(
        failure = ProposalWording.failure(result),
        needsKey = result is EstimateResult.NoKey,
        answer = (result as? EstimateResult.Unreadable)?.answer,
    )

    /**
     * How much of it there was, as typed (D53 §1). Only the amount changes: the worth stays what it
     * was, and the total follows.
     */
    fun setAmount(index: Int, text: String) {
        updateRow(index) { row -> row.copy(item = row.item.copy(amountText = text)) }
    }

    /**
     * − and + on a counted row: one piece more or fewer (D53 §6).
     *
     * Only for a piece — a measured unit (grams, millilitres and the rest `Portions.isMass` names)
     * has only its box. Never below one: a step that would go there does nothing, so a typed 0.5 is
     * kept rather than rounded, and nought of something is an item to remove, not a count. A box
     * that holds no number steps from nothing, so + gives 1. The arithmetic is decimal, so 0.1 and
     * one make 1.1 and not 1.1000000000000001.
     */
    fun step(index: Int, by: Int) {
        updateRow(index) { row ->
            val item = row.item
            if (Portions.isMass(item.unit)) return@updateRow row
            val now = item.amountText.trim().replace(',', '.').ifEmpty { "0" }
                .toBigDecimalOrNull() ?: return@updateRow row
            val next = now + by.toBigDecimal()
            if (next < BigDecimal.ONE) return@updateRow row
            row.copy(item = item.copy(amountText = next.stripTrailingZeros().toPlainString()))
        }
    }

    /** *Change* under the worth line: the four boxes open, holding the worth (D53 §6). */
    fun openWorth(index: Int) {
        updateRow(index) { row ->
            if (row.editingWorth != null) return@updateRow row
            WorthBoxes.of(row.item)?.let { row.copy(editingWorth = it) } ?: row
        }
    }

    /**
     * One worth box typed into (D53 §1, §3). Only the worth changes, never the amount. While the
     * four make a worth the row takes it — his, once any figure differs from what the box opened
     * with; while one is blank or refused the row keeps its last worth and cannot be logged.
     * The food it is attached to, if any, stays: typing over his food's worth changes only this
     * entry.
     */
    fun setWorthBox(index: Int, figure: WorthFigure, text: String) {
        updateRow(index) { row ->
            val boxes = row.editingWorth?.with(figure, text) ?: return@updateRow row
            val worth = boxes.worth()
            row.copy(
                item = if (worth == null) row.item else row.item.copy(worth = worth),
                editingWorth = boxes,
            )
        }
    }

    /**
     * The boxes close on what was typed. Not while one is refused: closing would hide the one
     * sentence saying why the row cannot be saved.
     */
    fun closeWorth(index: Int) {
        updateRow(index) { row ->
            if (row.editingWorth?.refused == true) row else row.copy(editingWorth = null)
        }
    }

    /** *Use your …* — his own food in place of the estimate, on the terms of D53 §4 and §5. */
    fun useYourFood(index: Int) {
        updateRow(index) { it.usingYourFood() }
    }

    /** *Use the estimate* — the model's item again, with nothing lost either way (D53 §4). */
    fun useEstimate(index: Int) {
        updateRow(index) { it.usingEstimate() }
    }

    /** *Count it in …* — his food's own unit and worth, and an empty amount for him (D53 §5). */
    fun countInFoodUnit(index: Int) {
        updateRow(index) { it.countedInFoodUnit() }
    }

    /** Remove a row the model invented, or one the owner did not eat. */
    fun remove(index: Int) {
        val current = _state.value as? ProposalUiState.Proposed ?: return
        val kept = current.rows.filterIndexed { at, _ -> at != index }
        _state.value = if (kept.isEmpty()) {
            ProposalUiState.Describing()
        } else {
            current.copy(rows = kept)
        }
    }

    /**
     * What would be logged if the owner accepted it now — nothing at all while any row cannot be
     * logged, so that saving never quietly leaves one of the rows behind (D53 §6). Each row comes
     * with the worth its food is to learn, and the brand it is saved under (D53 §3, §4).
     */
    fun accepted(): List<ToLog> {
        val proposed = _state.value as? ProposalUiState.Proposed ?: return emptyList()
        if (proposed.blockedBy != null) return emptyList()
        return proposed.rows.mapNotNull { it.toLog() }
    }

    /**
     * He is going to settings to add the key the last answer said was missing (public issue #11).
     *
     * The complaint is taken down now rather than on his return, because nothing here can tell that
     * he saved one; left up, it would contradict settings the moment he had. The words stay — in
     * [description], and in the field, which keeps whatever he typed since — so coming back is one
     * press of "Work it out", and without a key that press simply says so again.
     */
    /** *Keep as a meal*: its naming sheet opens over the rows; nothing is written yet (D58 §5.2). */
    fun openKeepOnly() {
        val proposed = _state.value as? ProposalUiState.Proposed ?: return
        if (proposed.rows.size < 2 || proposed.blockedBy != null) return
        _state.value = proposed.copy(keeping = KeepOnly())
    }

    /** *Not now*: the sheet closes, the rows stay. */
    fun closeKeepOnly() {
        val proposed = _state.value as? ProposalUiState.Proposed ?: return
        if (proposed.keeping?.busy == true) return
        _state.value = proposed.copy(keeping = null)
    }

    /**
     * Keep the rows as a meal called [name], logging nothing (D58 §5.2, §12.7) — all or nothing.
     * [onKept] runs once it is kept, and is where the screen takes him to My meals.
     */
    fun keepOnly(name: String, onKept: () -> Unit) {
        val proposed = _state.value as? ProposalUiState.Proposed ?: return
        val sheet = proposed.keeping ?: return
        if (sheet.busy || name.isBlank()) return
        val rows = accepted()
        if (rows.size < 2) return
        _state.value = proposed.copy(keeping = KeepOnly(busy = true))
        guarded(problems, onRefused = { keeping { KeepOnly(refused = ActionRefused.NOTHING_CHANGED) } }) {
            when (val kept = keeper.keep(name, rows)) {
                is MealKeeper.Kept.Made -> {
                    keeping { null }
                    onKept()
                }
                is MealKeeper.Kept.NameTaken -> keeping { KeepOnly(refusal = MealWording.nameTaken(kept.name)) }
                is MealKeeper.Kept.Refused ->
                    keeping { KeepOnly(refusal = kept.why.joinToString("\n"), canLogInstead = true) }
            }
        }
    }

    private fun keeping(change: () -> KeepOnly?) {
        val proposed = _state.value as? ProposalUiState.Proposed ?: return
        _state.value = proposed.copy(keeping = change())
    }

    fun leaveToAddKey() {
        _state.value = ProposalUiState.Describing()
    }

    fun startOver() {
        inFlight?.cancel()
        generation++
        description = ""
        _state.value = ProposalUiState.Describing()
    }

    private fun updateRow(index: Int, change: (ProposalRow) -> ProposalRow) {
        val current = _state.value as? ProposalUiState.Proposed ?: return
        if (index !in current.rows.indices) return
        _state.value = current.copy(
            rows = current.rows.mapIndexed { at, row -> if (at == index) change(row) else row },
        )
    }
}
