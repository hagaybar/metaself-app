package com.metaself.app.data.diagnostics.measure

/**
 * D152 (temporary): the two clocks a measurement needs. [wallMillis] says when, for the reader;
 * [nanos] says how long, and never goes backwards.
 */
interface MeasureClock {
    fun wallMillis(): Long
    fun nanos(): Long

    companion object {
        val SYSTEM: MeasureClock = object : MeasureClock {
            override fun wallMillis(): Long = System.currentTimeMillis()
            override fun nanos(): Long = System.nanoTime()
        }
    }
}

/**
 * One line of the measuring file: a [type] and fields, each a timing, a count, a name the app gives
 * a phase, or the app's own SQL text. **Never a value from the record** (D152): no food name, weight,
 * heart rate or message is ever put here, and SQL arrives with its placeholders, never its arguments.
 *
 * Built only when measuring is on ([MeasureRecorder.record]); encoded on the writer's thread.
 */
class MeasureEvent(val type: String, val atMillis: Long) {

    internal val fields = ArrayList<Any?>(12)

    fun put(key: String, value: Long) { fields.add(key); fields.add(value) }
    fun put(key: String, value: Int) { fields.add(key); fields.add(value) }
    fun put(key: String, value: Boolean) { fields.add(key); fields.add(value) }
    fun put(key: String, value: String?) { fields.add(key); fields.add(value) }

    /** Nanoseconds as milliseconds with two decimals: enough to rank, short to write. */
    fun putMillis(key: String, nanos: Long) { fields.add(key); fields.add(Millis(nanos)) }

    /** The app's SQL text, scrubbed ([MeasureJson.scrubSql]) when written, on the writer's thread. */
    fun putSql(key: String, sql: String) { fields.add(key); fields.add(Sql(sql)) }

    /** A transaction's statements: text → count, total and longest nanoseconds. Copied, as the map is the caller's. */
    fun putStatements(key: String, statements: Map<String, LongArray>) {
        fields.add(key)
        fields.add(statements.map { (sql, times) -> Statement(sql, times[0], times[1], times[2]) })
    }

    internal class Millis(val nanos: Long)
    internal class Sql(val text: String)
    internal class Statement(val sql: String, val count: Long, val totalNanos: Long, val longestNanos: Long)
}
