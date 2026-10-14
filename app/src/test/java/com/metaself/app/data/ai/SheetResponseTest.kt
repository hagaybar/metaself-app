package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.trainer.FactGroup
import com.metaself.app.domain.trainer.FactSource
import com.metaself.app.domain.trainer.RewrittenFact
import com.metaself.app.domain.trainer.SheetFact
import com.metaself.app.domain.trainer.TrainerReply
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** D150: the rewrite's answer read on the phone, and the sheet's stored shape. Every fact is invented. */
class SheetResponseTest {

    @Test
    fun `a sheet reads, with a continued id, a new fact and an unreadable since left to the phone`() {
        val reply = SheetResponse.parse(
            body("""{"facts":[{"id":3,"group":"body","text":" Invented. ","since":"2026-09-01","source":"they_said"},""" +
                """{"id":null,"group":"life","text":"Also invented.","since":"soon","source":"you_noticed"}]}"""),
            "a-model",
        )

        assertThat(reply).isEqualTo(
            TrainerReply.Answered(
                listOf(
                    RewrittenFact(3, FactGroup.BODY, "Invented.", LocalDate.of(2026, 9, 1).toEpochDay(), FactSource.SAID),
                    RewrittenFact(null, FactGroup.LIFE, "Also invented.", null, FactSource.NOTICED),
                ),
                "a-model",
            ),
        )
    }

    @Test
    fun `an empty sheet is an answer`() {
        assertThat(SheetResponse.parse(body("""{"facts":[]}"""), "a-model")).isEqualTo(TrainerReply.Answered(emptyList<RewrittenFact>(), "a-model"))
    }

    @Test
    fun `an unknown group or source, a missing field or a quoted id is unreadable`() {
        listOf(
            """{"facts":[{"id":null,"group":"mood","text":"x","since":"2026-09-01","source":"they_said"}]}""",
            """{"facts":[{"id":null,"group":"body","text":"x","since":"2026-09-01","source":"heard"}]}""",
            """{"facts":[{"id":null,"group":"body","since":"2026-09-01","source":"they_said"}]}""",
            """{"facts":[{"id":"3","group":"body","text":"x","since":"2026-09-01","source":"they_said"}]}""",
            """{"sheet":[]}""",
            "not json",
        ).forEach { content ->
            val reply = SheetResponse.parse(body(content), "a-model")
            assertThat(reply).isInstanceOf(TrainerReply.Failed::class.java)
            assertThat((reply as TrainerReply.Failed).failure).isInstanceOf(EstimateResult.Unreadable::class.java)
        }
    }

    @Test
    fun `the stored shape round-trips, dropped and removed facts included`() {
        val facts = listOf(
            SheetFact(1, FactGroup.TRAINING, "Invented.", TEST_EPOCH_DAY - 3, FactSource.NOTICED),
            SheetFact(2, FactGroup.GOALS, "His words.", TEST_EPOCH_DAY - 2, FactSource.SAID, edited = true, droppedEpochDay = TEST_EPOCH_DAY, removedByOwner = true),
        )

        assertThat(SheetResponse.read(SheetResponse.encode(facts))).isEqualTo(facts)
        assertThat(SheetResponse.read("not json")).isNull()
        assertThat(SheetResponse.read("""[{"id":1,"group":"MOOD","text":"x","since":1,"source":"SAID"}]""")).isNull()
    }

    private fun body(content: String) = """{"choices":[{"message":{"content":${JsonPrimitive(content)}}}]}"""
}
