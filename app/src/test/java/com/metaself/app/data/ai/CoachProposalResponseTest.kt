package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.trainer.CoachTurn
import com.metaself.app.domain.trainer.Proposal
import com.metaself.app.domain.trainer.ProposalKind
import com.metaself.app.domain.trainer.ProposalState
import com.metaself.app.domain.trainer.Proposals
import com.metaself.app.domain.trainer.TrainerReply
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test

/** D164: what the coach names is read here; nothing outside the plan and the sheet can be named. Every word is invented. */
class CoachProposalResponseTest {

    private fun named(propose: String): Proposal? {
        val content = """{"open":[],"reply":"Invented.","remember":[],"propose":$propose}"""
        val body = buildJsonObject {
            put("choices", buildJsonArray { add(buildJsonObject { put("message", buildJsonObject { put("content", JsonPrimitive(content)) }) }) })
        }.toString()
        return ((CoachResponse.parse(body, "a-model") as TrainerReply.Answered<CoachTurn>).value).propose
    }

    @Test
    fun `none, or nothing, proposes nothing`() {
        assertThat(named("""{"kind":"none"}""")).isNull()
        assertThat(named("null")).isNull()
    }

    @Test
    fun `each kind reads`() {
        assertThat(named("""{"kind":"change_plan","words":"Lighter."}""")).isEqualTo(Proposal(ProposalKind.CHANGE_PLAN, words = "Lighter."))
        assertThat(named("""{"kind":"stop_plan"}""")).isEqualTo(Proposal(ProposalKind.STOP_PLAN))
        assertThat(named("""{"kind":"fill_week"}""")).isEqualTo(Proposal(ProposalKind.FILL_WEEK))
        assertThat(named("""{"kind":"next_block"}""")).isEqualTo(Proposal(ProposalKind.NEXT_BLOCK))
        assertThat(named("""{"kind":"add_fact","group":"life","text":"A fact."}""")).isEqualTo(Proposal(ProposalKind.ADD_FACT, group = "LIFE", text = "A fact."))
        assertThat(named("""{"kind":"change_fact","fact":"A fact.","text":"Another."}""")).isEqualTo(Proposal(ProposalKind.CHANGE_FACT, fact = "A fact.", text = "Another."))
        assertThat(named("""{"kind":"remove_fact","fact":"A fact."}""")).isEqualTo(Proposal(ProposalKind.REMOVE_FACT, fact = "A fact."))
        val plan = named("""{"kind":"new_plan","words":"","goal":{"kind":"steady"},"per_week":3}""")!!
        assertThat(plan.kind).isEqualTo(ProposalKind.NEW_PLAN)
        assertThat(plan.perWeek).isEqualTo(3)
    }

    /** D163: there is no such kind, whatever the model answers. */
    @Test
    fun `a change to anything else is not a change the phone can make`() {
        listOf("change_calorie_target", "change_food", "change_weight").forEach { kind ->
            assertThat(named("""{"kind":"$kind","words":"Invented.","text":"Invented."}""")).isEqualTo(Proposal(ProposalKind.UNREADABLE))
        }
    }

    @Test
    fun `what lacks its parts is unreadable`() {
        assertThat(named("""{"kind":"change_plan","words":""}""")!!.kind).isEqualTo(ProposalKind.UNREADABLE)
        assertThat(named("""{"kind":"new_plan","goal":null,"per_week":3}""")!!.kind).isEqualTo(ProposalKind.UNREADABLE)
        assertThat(named("""{"kind":"add_fact","group":"none","text":"A fact."}""")!!.kind).isEqualTo(ProposalKind.UNREADABLE)
        assertThat(named("""{"kind":"remove_fact","fact":""}""")!!.kind).isEqualTo(ProposalKind.UNREADABLE)
    }

    @Test
    fun `a proposal survives storing, and what does not read shows no card`() {
        val proposal = Proposal(ProposalKind.NEW_PLAN, ProposalState.READY, words = "Invented.", goal = """{"kind":"steady"}""", perWeek = 3, programmeId = 7, startEpochDay = 10, replaces = true)
        assertThat(Proposals.decode(Proposals.encode(proposal))).isEqualTo(proposal)
        assertThat(Proposals.decode("""{"kind":"A_LATER_KIND"}""")).isNull()
        assertThat(Proposals.decode(null)).isNull()
    }

    @Test
    fun `the coach is told how an earlier proposal ended`() {
        assertThat(Proposals.note(Proposal(ProposalKind.STOP_PLAN, ProposalState.DECLINED))).isEqualTo("(You proposed stopping the plan: they said not now.)")
        assertThat(Proposals.note(Proposal(ProposalKind.UNREADABLE))).isNull()
    }
}
