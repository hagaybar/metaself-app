package com.metaself.app.data.diagnostics.measure

import android.database.Cursor
import android.database.sqlite.SQLiteTransactionListener
import android.os.CancellationSignal
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * D152 (temporary): the database Room is given, timed. Wraps the factory Room would use anyway
 * (`openHelperFactory`), and through it every statement and transaction the app runs:
 *
 * - a statement outside a transaction is one `sql` line: its text as compiled, with `?` for every
 *   argument (**bound arguments are never recorded**; [MeasureJson.scrubSql] also blanks any quoted
 *   literal that is not a constant name), how long it ran, and its thread. A query is timed from the
 *   call to its cursor's close, which is when Room has read its rows.
 * - a transaction is one `transaction` line: how long [beginTransaction] waited for the connection,
 *   how long it was then held, whether it committed, and the statements that ran inside it, each with
 *   its count, total and longest time. (Inside a transaction they are summed, not one line each: the
 *   copy runs thousands of inserts in one, and a line each would fill the 5 MB in an hour.)
 *
 * Off, every call goes straight to Room's own database after one read of the switch and of a counter
 * of transactions being measured; nothing is allocated except a statement's wrapper when Room first
 * compiles it (Room keeps compiled statements, so one compiled while off is still timed once on).
 *
 * Room's own statements — the invalidation tracker's reads and resets of its log table — pass through
 * here too and are recorded like any other. The recorder never calls the database, so nothing recurses.
 * Migrations and Room's open callbacks are given the framework's database directly, so they are not seen.
 */
class MeasuringOpenHelperFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory,
    private val recorder: MeasureRecorder,
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        MeasuringOpenHelper(delegate.create(configuration), recorder)
}

internal class MeasuringOpenHelper(
    private val delegate: SupportSQLiteOpenHelper,
    private val recorder: MeasureRecorder,
) : SupportSQLiteOpenHelper by delegate {

    private val transactions = Transactions()

    @Volatile
    private var cached: MeasuringDatabase? = null

    override val writableDatabase: SupportSQLiteDatabase get() = wrap(delegate.writableDatabase)

    override val readableDatabase: SupportSQLiteDatabase get() = wrap(delegate.readableDatabase)

    /** The same wrapper for the same database: Room asks for it on every call. */
    private fun wrap(database: SupportSQLiteDatabase): SupportSQLiteDatabase {
        val known = cached
        if (known != null && known.delegate === database) return known
        return MeasuringDatabase(database, recorder, transactions).also { cached = it }
    }
}

/** The transactions being measured, per thread. A transaction stays on the thread that began it. */
internal class Transactions {
    /** How many are open on any thread: zero lets an unmeasured call skip the thread-local read. */
    val open = AtomicInteger()
    private val serial = AtomicLong()
    private val current = ThreadLocal<OpenTransaction?>()

    fun current(): OpenTransaction? = if (open.get() == 0) null else current.get()

    fun start(mode: String, waitNanos: Long, beganNanos: Long): OpenTransaction =
        OpenTransaction(serial.incrementAndGet(), mode, waitNanos, beganNanos, Thread.currentThread().name).also {
            current.set(it)
            open.incrementAndGet()
        }

    fun finish() {
        current.remove()
        open.decrementAndGet()
    }
}

/** One measured transaction; touched only by its own thread. */
internal class OpenTransaction(
    val id: Long,
    val mode: String,
    val waitNanos: Long,
    val beganNanos: Long,
    val thread: String,
) {
    var committed = false

    /** Statement text → count, total nanoseconds, longest nanoseconds. */
    val statements = LinkedHashMap<String, LongArray>()
    var notListed = 0

    fun add(sql: String, nanos: Long) {
        val seen = statements[sql]
        if (seen == null) {
            if (statements.size >= MAX_LISTED) {
                notListed++
                return
            }
            statements[sql] = longArrayOf(1, nanos, nanos)
        } else {
            seen[0]++
            seen[1] += nanos
            if (nanos > seen[2]) seen[2] = nanos
        }
    }

    companion object {
        const val MAX_LISTED = 40
    }
}

internal class MeasuringDatabase(
    val delegate: SupportSQLiteDatabase,
    internal val recorder: MeasureRecorder,
    internal val transactions: Transactions,
) : SupportSQLiteDatabase by delegate {

    // --- Statements ------------------------------------------------------------------------------

    override fun compileStatement(sql: String): SupportSQLiteStatement =
        MeasuringStatement(delegate.compileStatement(sql), sql, this)

    override fun execSQL(sql: String) = timed(sql) { delegate.execSQL(sql) }

    override fun execSQL(sql: String, bindArgs: Array<out Any?>) = timed(sql) { delegate.execSQL(sql, bindArgs) }

    override fun query(query: String): Cursor = queried(query) { delegate.query(query) }

    override fun query(query: String, bindArgs: Array<out Any?>): Cursor = queried(query) { delegate.query(query, bindArgs) }

    override fun query(query: SupportSQLiteQuery): Cursor = queried(query.sql) { delegate.query(query) }

    override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor =
        queried(query.sql) { delegate.query(query, cancellationSignal) }

    // Java default methods are not delegated by `by`: said explicitly, so Room reaches the framework's.
    override val isExecPerConnectionSQLSupported: Boolean get() = delegate.isExecPerConnectionSQLSupported

    override fun execPerConnectionSQL(sql: String, bindArgs: Array<out Any?>?) = delegate.execPerConnectionSQL(sql, bindArgs)

    /** Runs [run]; when measuring, records how long it took as [sql]'s, inside its transaction or alone. */
    inline fun <T> timed(sql: String, run: () -> T): T {
        val transaction = transactions.current()
        if (transaction == null && !recorder.isOn) return run()
        val start = recorder.clock.nanos()
        try {
            return run()
        } finally {
            ran(sql, recorder.clock.nanos() - start, transaction)
        }
    }

    private inline fun queried(sql: String, run: () -> Cursor): Cursor {
        val transaction = transactions.current()
        if (transaction == null && !recorder.isOn) return run()
        val start = recorder.clock.nanos()
        val cursor = try {
            run()
        } catch (failure: Throwable) {
            ran(sql, recorder.clock.nanos() - start, transaction)
            throw failure
        }
        return MeasuringCursor(cursor) { ran(sql, recorder.clock.nanos() - start, transaction) }
    }

    fun ran(sql: String, nanos: Long, transaction: OpenTransaction?) {
        if (transaction != null) {
            transaction.add(sql, nanos)
        } else {
            recorder.record("sql") {
                putSql("sql", sql)
                putMillis("ms", nanos)
                put("thread", Thread.currentThread().name)
            }
        }
    }

    // --- Transactions ----------------------------------------------------------------------------

    override fun beginTransaction() = begin("exclusive") { delegate.beginTransaction() }

    override fun beginTransactionNonExclusive() = begin("immediate") { delegate.beginTransactionNonExclusive() }

    override fun beginTransactionWithListener(transactionListener: SQLiteTransactionListener) =
        begin("exclusive") { delegate.beginTransactionWithListener(transactionListener) }

    override fun beginTransactionWithListenerNonExclusive(transactionListener: SQLiteTransactionListener) =
        begin("immediate") { delegate.beginTransactionWithListenerNonExclusive(transactionListener) }

    /**
     * Only the outermost transaction on a thread is measured; a nested one is part of it. The wait is
     * the time inside the framework's begin, which is where a second writer waits for the connection.
     */
    private inline fun begin(mode: String, run: () -> Unit) {
        if (!recorder.isOn) return run()
        val outermost = transactions.current() == null && !delegate.inTransaction()
        val start = recorder.clock.nanos()
        run()
        if (outermost) {
            val began = recorder.clock.nanos()
            transactions.start(mode, began - start, began)
        }
    }

    override fun setTransactionSuccessful() {
        delegate.setTransactionSuccessful()
        transactions.current()?.committed = true
    }

    override fun endTransaction() {
        val transaction = transactions.current()
        try {
            delegate.endTransaction()
        } finally {
            if (transaction != null && !delegate.inTransaction()) {
                val ended = recorder.clock.nanos()
                transactions.finish()
                recorder.record("transaction") {
                    put("id", transaction.id)
                    put("mode", transaction.mode)
                    put("thread", transaction.thread)
                    putMillis("wait_ms", transaction.waitNanos)
                    putMillis("held_ms", ended - transaction.beganNanos)
                    put("committed", transaction.committed)
                    putStatements("statements", transaction.statements)
                    if (transaction.notListed > 0) put("statements_not_listed", transaction.notListed)
                }
            }
        }
    }
}

/** A statement Room compiled: executions timed, bindings passed through and never seen. */
internal class MeasuringStatement(
    private val delegate: SupportSQLiteStatement,
    private val sql: String,
    private val database: MeasuringDatabase,
) : SupportSQLiteStatement by delegate {
    override fun execute() = database.timed(sql) { delegate.execute() }
    override fun executeUpdateDelete(): Int = database.timed(sql) { delegate.executeUpdateDelete() }
    override fun executeInsert(): Long = database.timed(sql) { delegate.executeInsert() }
    override fun simpleQueryForLong(): Long = database.timed(sql) { delegate.simpleQueryForLong() }
    override fun simpleQueryForString(): String? = database.timed(sql) { delegate.simpleQueryForString() }
}

/** A query's cursor; its close ends the query's time. */
internal class MeasuringCursor(
    private val delegate: Cursor,
    private val onClose: () -> Unit,
) : Cursor by delegate {
    private var closed = false

    override fun close() {
        try {
            delegate.close()
        } finally {
            if (!closed) {
                closed = true
                onClose()
            }
        }
    }
}
