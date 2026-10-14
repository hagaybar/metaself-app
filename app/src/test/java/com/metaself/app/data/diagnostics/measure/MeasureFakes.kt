package com.metaself.app.data.diagnostics.measure

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.Callable
import java.util.concurrent.Delayed
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** A clock the test moves. Invented times. */
class FakeMeasureClock(var wall: Long = 1_000_000L, var nano: Long = 0L) : MeasureClock {
    override fun wallMillis(): Long = wall
    override fun nanos(): Long = nano
}

/** Lines appended, in memory, and the thread each batch was appended on. */
class RecordingSink : MeasureSink {
    val lines = mutableListOf<String>()
    val threads = mutableListOf<String>()

    @Synchronized
    override fun append(lines: List<String>) {
        if (lines.isEmpty()) return
        this.lines += lines
        threads += Thread.currentThread().name
    }

    @Synchronized
    override fun copyTo(out: OutputStream) {
        out.write(lines.joinToString("") { it + "\n" }.toByteArray())
    }

    fun text(): String = ByteArrayOutputStream().also { copyTo(it) }.toString()
}

/** A writer that runs nothing on its schedule: only [flush] and full batches drain, on a real thread. */
fun aWriterWithoutTimer(name: String = MeasureRecorder.WRITER_THREAD): () -> ScheduledExecutorService = {
    val real = Executors.newSingleThreadExecutor { Thread(it, name).apply { isDaemon = true } }
    object : ScheduledExecutorService by Executors.newSingleThreadScheduledExecutor() {
        override fun execute(command: Runnable) = real.execute(command)
        override fun <T> submit(task: Callable<T>) = real.submit(task)
        override fun submit(task: Runnable) = real.submit(task)
        override fun scheduleWithFixedDelay(command: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit): ScheduledFuture<*> =
            NeverRuns
    }
}

private object NeverRuns : ScheduledFuture<Unit> {
    override fun compareTo(other: Delayed?): Int = 0
    override fun getDelay(unit: TimeUnit): Long = Long.MAX_VALUE
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean = true
    override fun isCancelled(): Boolean = false
    override fun isDone(): Boolean = false
    override fun get() = Unit
    override fun get(timeout: Long, unit: TimeUnit) = Unit
}
