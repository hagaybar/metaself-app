package com.metaself.app.data.diagnostics.measure

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** D152: lines are valid JSON objects, and SQL keeps its placeholders and constants only. */
class MeasureJsonTest {

    @Test
    fun `placeholders and constant names stay, any other quoted literal goes`() {
        val sql = "SELECT * FROM workouts WHERE source = 'SYNCED' AND kind = 'RUN' AND title = 'Pizza with ham' " +
            "AND note = 'it''s' AND id = ? AND x = '42'"

        val scrubbed = MeasureJson.scrubSql(sql)

        assertThat(scrubbed).isEqualTo(
            "SELECT * FROM workouts WHERE source = 'SYNCED' AND kind = 'RUN' AND title = ? AND note = ? AND id = ? AND x = ?",
        )
    }

    @Test
    fun `whitespace collapses and a very long statement is cut`() {
        assertThat(MeasureJson.scrubSql("SELECT\n  1\n")).isEqualTo("SELECT 1")
        assertThat(MeasureJson.scrubSql("SELECT " + "a, ".repeat(500) + "b").length).isEqualTo(MeasureJson.MAX_SQL + 1)
    }

    @Test
    fun `an event is one line, strings escaped, durations in milliseconds`() {
        val event = MeasureEvent("transaction", 1_000).apply {
            put("thread", "a \"quoted\"\nname")
            putMillis("held_ms", 12_345_678)
            put("committed", true)
            putSql("sql", "UPDATE foods SET name = 'Toast' WHERE id = ?")
            putStatements("statements", linkedMapOf("DELETE FROM t WHERE id = ?" to longArrayOf(3, 3_000_000, 2_000_000)))
        }

        val line = MeasureJson.line(event)

        assertThat(line).doesNotContain("\n")
        assertThat(line).isEqualTo(
            "{\"type\":\"transaction\",\"at\":1000,\"thread\":\"a \\\"quoted\\\"\\nname\",\"held_ms\":12.35," +
                "\"committed\":true,\"sql\":\"UPDATE foods SET name = ? WHERE id = ?\"," +
                "\"statements\":[{\"sql\":\"DELETE FROM t WHERE id = ?\",\"count\":3,\"total_ms\":3.00,\"longest_ms\":2.00}]}",
        )
    }
}
