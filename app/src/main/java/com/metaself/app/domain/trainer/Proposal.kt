package com.metaself.app.domain.trainer

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** D164: the only changes the coach can propose: the plan, its goal and the sheet. Stored by name. */
@Serializable
enum class ProposalKind {
    CHANGE_PLAN, NEW_PLAN, STOP_PLAN, FILL_WEEK, NEXT_BLOCK, ADD_FACT, CHANGE_FACT, REMOVE_FACT,

    /** The coach named a change this app could not read: said on the card, so a reply promising one is not left bare. */
    UNREADABLE,
}

/**
 * D164. [PREPARING]: the phone has not finished the job, or was stopped before it did. [READY]: waiting for Confirm.
 * [OUT_OF_DATE]: the record moved on, so it can no longer be done. Stored by name.
 */
@Serializable
enum class ProposalState { PREPARING, READY, CONFIRMED, DECLINED, OUT_OF_DATE }

/**
 * D164: the change a reply names and how far it has got, stored with the reply. Nothing the app holds changes until
 * Confirm, apart from an offered plan version, which is what Keep on the plan page reads too.
 *
 * @property words what the plan's job is told to change (CHANGE_PLAN) or asked for (NEW_PLAN).
 * @property goal NEW_PLAN's goal, in the JSON form plans store theirs.
 * @property perWeek NEW_PLAN's sessions a week.
 * @property group ADD_FACT's group, as [FactGroup]'s name.
 * @property fact the current fact CHANGE_FACT or REMOVE_FACT is about, in its own words.
 * @property text the new words of ADD_FACT or CHANGE_FACT.
 * @property programmeId the plan version prepared, or the plan STOP_PLAN stops.
 * @property title the plan's title; [why] and [says] its reason and the coach's stand on the goal, shown on the card.
 * @property startEpochDay the Monday a NEW_PLAN starts on; [replaces] says it replaces a running plan from then.
 */
@Serializable
data class Proposal(
    val kind: ProposalKind,
    val state: ProposalState = ProposalState.PREPARING,
    val words: String = "",
    val goal: String? = null,
    val perWeek: Int? = null,
    val group: String? = null,
    val fact: String = "",
    val text: String = "",
    val programmeId: Long? = null,
    val title: String = "",
    val why: String = "",
    val says: String = "",
    val startEpochDay: Long? = null,
    val replaces: Boolean = false,
)

/** D164. Pure. */
object Proposals {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(proposal: Proposal): String = json.encodeToString(proposal)

    /** Null when it does not read, as from a later version: the reply then shows no card. */
    fun decode(stored: String?): Proposal? = stored?.let { runCatching { json.decodeFromString<Proposal>(it) }.getOrNull() }

    /** What the coach is told it proposed and how that ended, after its reply's words, so it is not asked for twice. */
    fun note(proposal: Proposal): String? {
        val what = when (proposal.kind) {
            ProposalKind.CHANGE_PLAN -> "a change to the plan"
            ProposalKind.NEW_PLAN -> "a new plan"
            ProposalKind.STOP_PLAN -> "stopping the plan"
            ProposalKind.FILL_WEEK -> "filling in the next week"
            ProposalKind.NEXT_BLOCK -> "planning the next block"
            ProposalKind.ADD_FACT, ProposalKind.CHANGE_FACT, ProposalKind.REMOVE_FACT -> "a change to the sheet"
            ProposalKind.UNREADABLE -> return null
        }
        val ended = when (proposal.state) {
            ProposalState.PREPARING, ProposalState.READY -> "they have not answered"
            ProposalState.CONFIRMED -> "they confirmed it and it is done"
            ProposalState.DECLINED -> "they said not now"
            ProposalState.OUT_OF_DATE -> "it no longer applied, and nothing changed"
        }
        return "(You proposed $what: $ended.)"
    }
}
