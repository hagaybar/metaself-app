package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.Confidence
import com.metaself.app.ui.portion.PortionWording
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

class EstimateResponseTest {

    @Test
    fun `a two-item reply becomes a two-item proposal`() {
        val result = EstimateResponse.parse(replyWith(RISOTTO_AND_MOZZARELLA))

        val proposal = (result as EstimateResult.Proposed).proposal
        assertThat(proposal.items).hasSize(2)
        assertThat(proposal.items[0].name).isEqualTo("Risotto")
        assertThat(proposal.items[0].kcal).isEqualTo(405)
        assertThat(proposal.items[1].name).isEqualTo("Mozzarella")
        assertThat(proposal.note).isEqualTo("Assumed a whole mozzarella ball.")
    }

    @Test
    fun `the amount and unit become a portion the owner can read`() {
        val result = EstimateResponse.parse(replyWith(RISOTTO_AND_MOZZARELLA))

        val first = (result as EstimateResult.Proposed).proposal.items.first()
        assertThat(first.portion).isEqualTo("280 g")
        assertThat(first.portionAmount).isWithin(0.01).of(280.0)
        assertThat(first.portionUnit).isEqualTo("g")
    }

    /**
     * An item with no amount makes the whole reply one that did not give amounts (D34).
     *
     * It used to be accepted as "amount not stated" and logged. A row with a unit and no amount then
     * could not join a meal, and read on the day exactly like one that could (issue #23).
     */
    @Test
    fun `an item with no amount is a reply without amounts, and says which`() {
        val vague = """{"note":"","items":[
            {"name":"Rice","amount":150,"unit":"g","kcal":200,"protein_g":4,"carbs_g":44,"fat_g":0,
             "confidence":"MEDIUM"},
            {"name":"Stew","amount":0,"unit":"g","kcal":400,"protein_g":20,"carbs_g":30,"fat_g":15,
             "confidence":"LOW"}]}"""

        val result = EstimateResponse.parse(replyWith(vague))

        assertThat(result).isEqualTo(EstimateResult.AmountMissing(listOf("Stew")))
    }

    /** A number without a unit says how many of nothing, which is not an amount either. */
    @Test
    fun `an item with a number and no unit is missing its amount too`() {
        val unitless = """{"note":"","items":[{"name":"Stew","amount":2,"unit":" ","kcal":400,
            "protein_g":20,"carbs_g":30,"fat_g":15,"confidence":"LOW"}]}"""

        assertThat(EstimateResponse.parse(replyWith(unitless)))
            .isEqualTo(EstimateResult.AmountMissing(listOf("Stew")))
    }

    /**
     * The proposal screen draws the plural only when the held words are exactly the app's own form
     * of the amount and unit (D37). Those words are written here, by a different call from the one
     * the rule compares against; if the two ever drifted apart, the model's "2 portion" would stay
     * singular on the proposal screen and nothing else would notice.
     */
    @Test
    fun `the model's own "portion" is in the form the plural recognises`() {
        val portions = """{"note":"","items":[
            {"name":"Stew","amount":2,"unit":"portion","kcal":600,"protein_g":40,"carbs_g":50,
             "fat_g":25,"confidence":"MEDIUM"},
            {"name":"Rice","amount":1.5,"unit":"portion","kcal":300,"protein_g":6,"carbs_g":66,
             "fat_g":1,"confidence":"MEDIUM"}]}"""

        val items = (EstimateResponse.parse(replyWith(portions)) as EstimateResult.Proposed)
            .proposal.items

        val counted = items.map {
            PortionWording.countedInPortions(it.portionAmount, it.portionUnit, it.portion)
        }
        assertThat(counted).containsExactly(2.0, 1.5).inOrder()
    }

    @Test
    fun `confidence is carried through`() {
        val result = EstimateResponse.parse(replyWith(RISOTTO_AND_MOZZARELLA))

        val items = (result as EstimateResult.Proposed).proposal.items
        assertThat(items[0].confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(items[1].confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `a reply with no items is unreadable, not an empty meal`() {
        val result = EstimateResponse.parse(replyWith("""{"note":"","items":[]}"""))

        assertThat(result).isInstanceOf(EstimateResult.Unreadable::class.java)
    }

    @Test
    fun `prose instead of JSON is unreadable and says so`() {
        val result = EstimateResponse.parse(replyWith("About 830 calories, I would guess."))

        assertThat(result).isInstanceOf(EstimateResult.Unreadable::class.java)
    }

    @Test
    fun `an item missing its calories is dropped rather than logged as zero`() {
        val partial = """{"note":"","items":[
            {"name":"Risotto","amount":280,"unit":"g","protein_g":9,"carbs_g":57,"fat_g":16,
             "confidence":"MEDIUM"},
            {"name":"Mozzarella","amount":100,"unit":"ball","kcal":280,"protein_g":18,"carbs_g":1,
             "fat_g":20,"confidence":"HIGH"}]}"""

        val result = EstimateResponse.parse(replyWith(partial))

        val proposal = (result as EstimateResult.Proposed).proposal
        assertThat(proposal.items).hasSize(1)
        assertThat(proposal.items.single().name).isEqualTo("Mozzarella")
    }

    @Test
    fun `a reply whose every item is unusable is unreadable, not an empty proposal`() {
        val useless = """{"note":"","items":[{"name":"Risotto","amount":280,"unit":"g"}]}"""

        assertThat(EstimateResponse.parse(replyWith(useless)))
            .isInstanceOf(EstimateResult.Unreadable::class.java)
    }

    @Test
    fun `an unknown confidence is read as low rather than refused`() {
        // Being told an estimate is "PROBABLY" should not lose the estimate. Reading it as LOW is
        // the safe direction: it shows a warning the owner can dismiss, rather than hiding doubt.
        val odd = """{"note":"","items":[{"name":"Stew","amount":300,"unit":"g","kcal":400,
            "protein_g":20,"carbs_g":30,"fat_g":15,"confidence":"PROBABLY"}]}"""

        val result = EstimateResponse.parse(replyWith(odd))

        assertThat((result as EstimateResult.Proposed).proposal.items.single().confidence)
            .isEqualTo(Confidence.LOW)
    }

    @Test
    fun `an empty body is unreadable`() {
        assertThat(EstimateResponse.parse("")).isInstanceOf(EstimateResult.Unreadable::class.java)
    }

    private fun replyWith(content: String): String =
        """{"choices":[{"message":{"content":${JsonPrimitive(content)}}}]}"""

    private companion object {
        const val RISOTTO_AND_MOZZARELLA = """{"note":"Assumed a whole mozzarella ball.",
            "items":[
              {"name":"Risotto","amount":280,"unit":"g","kcal":405,"protein_g":9,"carbs_g":57,
               "fat_g":16,"confidence":"MEDIUM"},
              {"name":"Mozzarella","amount":100,"unit":"ball","kcal":280,"protein_g":18,
               "carbs_g":1,"fat_g":20,"confidence":"HIGH"}]}"""
    }
}
