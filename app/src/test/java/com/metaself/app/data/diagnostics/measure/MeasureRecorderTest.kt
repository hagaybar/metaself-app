package com.metaself.app.data.diagnostics.measure

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File

/** D152: the recorder — off costs nothing, on writes on its own thread, stops itself after 72 hours. */
class MeasureRecorderTest {

    private val clock = FakeMeasureClock()
    private val sink = RecordingSink()
    private var writersMade = 0

    private fun recorder(batch: Int = MeasureRecorder.BATCH) = MeasureRecorder(
        sink = sink,
        clock = clock,
        newWriter = { writersMade++; aWriterWithoutTimer()() },
        batchSize = batch,
    )

    @Test
    fun `off, an event is never built, no thread is made and nothing is written`() {
        val recorder = recorder()
        var built = 0

        repeat(1_000) { recorder.record("sql") { built++ } }
        recorder.flush()

        assertThat(built).isEqualTo(0)
        assertThat(writersMade).isEqualTo(0)
        assertThat(sink.lines).isEmpty()
    }

    @Test
    fun `off, nothing reaches the file on disk`(@TempDir dir: File) {
        val recorder = MeasureRecorder(MeasureFile(File(dir, "measure")), clock)

        recorder.record("sql") { put("ms", 1) }
        recorder.appOpened(1_000, 500)
        recorder.restored()
        recorder.flush()

        assertThat(File(dir, "measure").exists()).isFalse()
    }

    @Test
    fun `on, events are written by the writer's own thread, not the caller's`() {
        val recorder = recorder()
        recorder.switchOn(clock.wall + MeasureWindow.LENGTH_MILLIS)

        recorder.record("screen") { put("screen", "day") }
        assertThat(sink.lines).isEmpty()
        recorder.flush()

        assertThat(sink.lines.last()).contains("\"type\":\"screen\"")
        assertThat(sink.lines.last()).contains("\"screen\":\"day\"")
        assertThat(sink.threads.toSet()).containsExactly(MeasureRecorder.WRITER_THREAD)
        assertThat(writersMade).isEqualTo(1)
    }

    @Test
    fun `a full batch is written without waiting for the timer`() {
        val recorder = recorder(batch = 5)
        recorder.switchOn(clock.wall + MeasureWindow.LENGTH_MILLIS)

        repeat(10) { recorder.record("sql") { put("n", it) } }
        // The batch went to the writer thread; flush only waits for it.
        recorder.flush()

        assertThat(sink.lines.count { it.contains("\"type\":\"sql\"") }).isEqualTo(10)
    }

    @Test
    fun `it turns itself off when the 72 hours have passed`() {
        val recorder = recorder()
        val start = clock.wall
        recorder.switchOn(MeasureWindow.endOf(start))

        clock.wall = start + MeasureWindow.LENGTH_MILLIS - 1
        assertThat(recorder.isOn).isTrue()
        recorder.record("sql") { put("last", true) }

        clock.wall = start + MeasureWindow.LENGTH_MILLIS
        assertThat(recorder.isOn).isFalse()
        recorder.record("sql") { put("late", true) }
        // Off for good: the clock going back does not bring it back.
        clock.wall = start
        assertThat(recorder.isOn).isFalse()
        recorder.flush()

        assertThat(sink.lines.joinToString()).contains("\"last\":true")
        assertThat(sink.lines.joinToString()).doesNotContain("late")
    }

    @Test
    fun `the app open waits for the switch to be read, and is recorded once`() {
        val recorder = recorder()
        recorder.appOpened(sinceProcessMillis = 1_200, sinceActivityMillis = 700)
        recorder.switchOn(clock.wall + MeasureWindow.LENGTH_MILLIS)
        recorder.restored()
        recorder.appOpened(sinceProcessMillis = 9_999, sinceActivityMillis = 9_999)
        recorder.flush()

        val opens = sink.lines.filter { it.contains("app_open") }
        assertThat(opens).hasSize(1)
        assertThat(opens.single()).contains("\"process_to_day_drawn_ms\":1200")
        assertThat(opens.single()).contains("\"activity_to_day_drawn_ms\":700")
    }

    @Test
    fun `the file is downloaded oldest line first, with everything recorded so far`(@TempDir dir: File) {
        val recorder = MeasureRecorder(MeasureFile(dir), clock, aWriterWithoutTimer())
        recorder.switchOn(clock.wall + MeasureWindow.LENGTH_MILLIS)
        recorder.record("one") {}
        recorder.record("two") {}

        val out = ByteArrayOutputStream().also { recorder.copyTo(it) }.toString()

        assertThat(out.indexOf("\"type\":\"one\"")).isLessThan(out.indexOf("\"type\":\"two\""))
    }
}
