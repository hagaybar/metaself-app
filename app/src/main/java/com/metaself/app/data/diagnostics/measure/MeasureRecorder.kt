package com.metaself.app.data.diagnostics.measure

import java.io.OutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * D152 (temporary): the app measuring itself, off unless the owner switches it on, for 72 hours.
 *
 * **Off, it costs one read of a volatile field per event**: [record] is inline, so its builder is never
 * made and nothing is allocated; no thread exists until the first event is recorded.
 *
 * **On, it never touches the app's database.** An event goes into an in-memory queue; one thread of
 * its own ("metaself-measure", a daemon at the lowest priority) encodes the queue to JSON lines and
 * appends them to the [sink] every [flushEveryMillis] or as soon as [batchSize] are waiting. It is never
 * one of the database's executors and never inside a transaction. More than [MAX_PENDING] waiting —
 * the writer stuck — and the newest are dropped and counted, never held.
 *
 * It turns itself off when [MeasureClock.wallMillis] reaches the end it was switched on until: the
 * check is made on each event while on, so a stale "on" costs one clock read and then nothing.
 */
class MeasureRecorder(
    private val sink: MeasureSink,
    val clock: MeasureClock = MeasureClock.SYSTEM,
    private val newWriter: () -> ScheduledExecutorService = ::writerThread,
    private val flushEveryMillis: Long = FLUSH_EVERY_MILLIS,
    private val batchSize: Int = BATCH,
) {

    @Volatile
    private var untilMillis = 0L

    /** Whether events are being recorded now. Off once the end has passed, without being told. */
    val isOn: Boolean
        get() {
            val until = untilMillis
            if (until == 0L) return false
            if (clock.wallMillis() >= until) {
                untilMillis = 0L
                return false
            }
            return true
        }

    /** Records from now until [untilMillis] (wall clock). */
    fun switchOn(untilMillis: Long) {
        this.untilMillis = untilMillis
        record("measuring") { put("until", untilMillis) }
    }

    fun switchOff() {
        if (isOn) record("stopped") {}
        untilMillis = 0L
    }

    /** One event of [type], its fields set by [fill] — called only when on. */
    inline fun record(type: String, fill: MeasureEvent.() -> Unit) {
        if (isOn) enqueue(MeasureEvent(type, clock.wallMillis()).apply(fill))
    }

    // --- App open: process start to the day drawn, once per process -----------------------------

    @Volatile private var activityCreatedElapsed = -1L

    /** The activity was first created at [elapsedMillis] since boot; later re-creations are not the open. */
    fun activityCreated(elapsedMillis: Long) {
        if (activityCreatedElapsed < 0) activityCreatedElapsed = elapsedMillis
    }

    /** The day drew its first data at [elapsedMillis] since boot; the process started at [processStartElapsedMillis]. */
    fun dayDrawn(elapsedMillis: Long, processStartElapsedMillis: Long) {
        if (appOpenSeen) return
        val activity = activityCreatedElapsed
        appOpened(elapsedMillis - processStartElapsedMillis, if (activity >= 0) elapsedMillis - activity else null)
    }

    @Volatile private var restored = false
    @Volatile private var appOpenSeen = false
    @Volatile private var pendingAppOpen: LongArray? = null

    /**
     * The day screen drew its first frame of data, [sinceProcessMillis] after the process started and
     * [sinceActivityMillis] after the activity was created (null when not known). Once per process. The
     * switch is read from storage as the app opens, so this may arrive first: it is held until [restored].
     */
    fun appOpened(sinceProcessMillis: Long, sinceActivityMillis: Long?) {
        if (appOpenSeen) return
        appOpenSeen = true
        val times = longArrayOf(sinceProcessMillis, sinceActivityMillis ?: -1)
        if (restored) recordAppOpen(times) else pendingAppOpen = times
    }

    /** The switch has been read on this open; an app open held for it is recorded now, if on. */
    fun restored() {
        restored = true
        pendingAppOpen?.let { pendingAppOpen = null; recordAppOpen(it) }
    }

    private fun recordAppOpen(times: LongArray) = record("app_open") {
        put("process_to_day_drawn_ms", times[0])
        if (times[1] >= 0) put("activity_to_day_drawn_ms", times[1])
    }

    // --- The queue and its writer ------------------------------------------------------------------

    private val queue = ConcurrentLinkedQueue<MeasureEvent>()
    private val pending = AtomicInteger()
    private val dropped = AtomicInteger()
    private val drainQueued = AtomicBoolean(false)

    @Volatile
    private var writer: ScheduledExecutorService? = null

    @PublishedApi
    internal fun enqueue(event: MeasureEvent) {
        if (pending.incrementAndGet() > MAX_PENDING) {
            pending.decrementAndGet()
            dropped.incrementAndGet()
            return
        }
        queue.add(event)
        val running = writer()
        if (pending.get() >= batchSize && drainQueued.compareAndSet(false, true)) {
            running.execute { drainQueued.set(false); drain() }
        }
    }

    private fun writer(): ScheduledExecutorService = writer ?: synchronized(this) {
        writer ?: newWriter().also { created ->
            created.scheduleWithFixedDelay({ drain() }, flushEveryMillis, flushEveryMillis, TimeUnit.MILLISECONDS)
            writer = created
        }
    }

    /** On the writer's thread only. A failure loses that batch and nothing else. */
    private fun drain() {
        runCatching {
            val lines = ArrayList<String>()
            while (true) {
                val event = queue.poll() ?: break
                pending.decrementAndGet()
                lines += MeasureJson.line(event)
            }
            val lost = dropped.getAndSet(0)
            if (lost > 0) lines += MeasureJson.line(MeasureEvent("dropped", clock.wallMillis()).apply { put("events", lost) })
            sink.append(lines)
        }
    }

    /** Everything recorded so far written, waiting for the writer. Never from the main thread. */
    fun flush() {
        writer?.submit { drain() }?.get(WAIT_SECONDS, TimeUnit.SECONDS)
    }

    /** Everything recorded so far, oldest first, onto [out] — on the writer's thread, after a flush. */
    fun copyTo(out: OutputStream) {
        val running = writer
        if (running == null) sink.copyTo(out) else running.submit { drain(); sink.copyTo(out) }.get(WAIT_SECONDS, TimeUnit.SECONDS)
    }

    companion object {
        const val FLUSH_EVERY_MILLIS = 3_000L
        const val BATCH = 200
        const val MAX_PENDING = 20_000
        const val WRITER_THREAD = "metaself-measure"
        private const val WAIT_SECONDS = 30L

        /** Never switched on: what a test, or anything built without measuring, is given. */
        val OFF = MeasureRecorder(MeasureSink.NONE)

        private fun writerThread(): ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, WRITER_THREAD).apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }
    }
}
