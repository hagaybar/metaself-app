package com.metaself.app.data.diagnostics.measure

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteProgram
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import android.database.Cursor
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/**
 * D152: the timed database. Room's database is faked by a proxy that answers only what the wrapper
 * asks of it, so this runs on the JVM. Every value is invented; [SENTINEL] stands for a value from the
 * record and must never reach the file.
 */
class MeasuringDatabaseTest {

    private val clock = FakeMeasureClock()
    private val sink = RecordingSink()
    private val recorder = MeasureRecorder(sink, clock, aWriterWithoutTimer())
    private val calls = mutableListOf<String>()
    private var depth = 0
    private val bound = mutableListOf<Any?>()

    private val statement: SupportSQLiteStatement = proxy { name, args ->
        calls += name
        if (name.startsWith("execute")) clock.nano += 2_000_000
        when (name) {
            "bindString", "bindLong" -> { bound += args!![1]; Unit }
            "executeInsert" -> 7L
            "executeUpdateDelete" -> 1
            else -> Unit
        }
    }

    private val cursor: Cursor = proxy { name, _ -> calls += "cursor.$name"; if (name == "moveToNext") false else Unit }

    private val framework: SupportSQLiteDatabase = proxy { name, _ ->
        calls += name
        when (name) {
            "compileStatement" -> statement
            "inTransaction" -> depth > 0
            "beginTransaction", "beginTransactionNonExclusive" -> { clock.nano += 5_000_000; depth++; Unit }
            "endTransaction" -> { depth--; Unit }
            "query" -> { clock.nano += 1_000_000; cursor }
            else -> Unit
        }
    }

    private val helper: SupportSQLiteOpenHelper = proxy { name, _ ->
        when (name) {
            "getWritableDatabase", "getReadableDatabase" -> framework
            else -> Unit
        }
    }

    private val database = MeasuringOpenHelper(helper, recorder).writableDatabase

    private fun on() = recorder.switchOn(clock.wall + MeasureWindow.LENGTH_MILLIS)

    private fun written(): String {
        recorder.flush()
        return sink.lines.joinToString("\n")
    }

    @Test
    fun `a bound value never reaches the file, the statement's text with its placeholder does`() {
        on()
        val insert = database.compileStatement("INSERT INTO foods (name) VALUES (?)")
        insert.bindString(1, SENTINEL)
        insert.executeInsert()
        database.execSQL("UPDATE foods SET name = ? WHERE id = ?", arrayOf<Any?>(SENTINEL, 3))
        database.query(SimpleQuery("SELECT * FROM foods WHERE name = ?", SENTINEL)).close()

        val file = written()
        assertThat(bound).containsExactly(SENTINEL)
        assertThat(file).doesNotContain(SENTINEL)
        assertThat(file).contains("\"sql\":\"INSERT INTO foods (name) VALUES (?)\"")
        assertThat(file).contains("\"sql\":\"UPDATE foods SET name = ? WHERE id = ?\"")
        assertThat(file).contains("\"sql\":\"SELECT * FROM foods WHERE name = ?\"")
    }

    @Test
    fun `a transaction records its wait, how long it was held and the statements inside it`() {
        on()
        database.beginTransaction()
        val delete = database.compileStatement("DELETE FROM health_readings WHERE recordId = ?")
        repeat(3) { delete.bindString(1, SENTINEL); delete.executeUpdateDelete() }
        // A nested transaction is part of the outer one.
        database.beginTransactionNonExclusive()
        database.endTransaction()
        database.setTransactionSuccessful()
        database.endTransaction()

        val file = written()
        val line = file.lines().single { it.contains("\"type\":\"transaction\"") }
        assertThat(line).contains("\"wait_ms\":5.00")
        assertThat(line).contains("\"committed\":true")
        assertThat(line).contains("{\"sql\":\"DELETE FROM health_readings WHERE recordId = ?\",\"count\":3,\"total_ms\":6.00,\"longest_ms\":2.00}")
        // 3 statements of 2 ms, and the nested begin's 5 ms, inside.
        assertThat(line).contains("\"held_ms\":11.00")
        assertThat(file).doesNotContain(SENTINEL)
        // Inside a transaction, statements are summed there, not written one by one.
        assertThat(file.lines().none { it.contains("\"type\":\"sql\"") }).isTrue()
    }

    @Test
    fun `off, every call goes straight through and nothing is recorded`() {
        val insert = database.compileStatement("INSERT INTO foods (name) VALUES (?)")
        insert.bindString(1, SENTINEL)
        insert.executeInsert()
        database.beginTransaction()
        database.execSQL("DELETE FROM foods")
        database.endTransaction()
        val read = database.query("SELECT 1")

        assertThat(read).isSameInstanceAs(cursor)
        assertThat(calls).containsAtLeast("compileStatement", "executeInsert", "beginTransaction", "execSQL", "endTransaction", "query")
        assertThat(written()).isEmpty()
    }

    @Test
    fun `Room is handed the same wrapper each time it asks`() {
        val again = MeasuringOpenHelper(helper, recorder)

        assertThat(again.writableDatabase).isSameInstanceAs(again.writableDatabase)
    }

    private class SimpleQuery(override val sql: String, private val arg: String) : SupportSQLiteQuery {
        override val argCount: Int = 1
        override fun bindTo(statement: SupportSQLiteProgram) = statement.bindString(1, arg)
    }

    private inline fun <reified T> proxy(crossinline answer: (String, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            answer(method.name, args)
        } as T

    private companion object {
        const val SENTINEL = "SENTINEL-VALUE-FROM-THE-RECORD"
    }
}
