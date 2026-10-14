package com.metaself.app.data.diagnostics.measure

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File

/** D152: the file is capped, and the oldest lines go first. */
class MeasureFileTest {

    @Test
    fun `the two halves together stay under the cap, keeping the newest lines`(@TempDir dir: File) {
        val cap = 2_000L
        val file = MeasureFile(dir, capBytes = cap)

        // Invented lines of 40 bytes each, numbered, in batches of 3.
        (0 until 300).chunked(3).forEach { batch ->
            file.append(batch.map { n -> "{\"n\":%05d,\"pad\":\"%s\"}".format(n, "x".repeat(14)) })
            assertThat(file.size()).isAtMost(cap)
        }
        val text = ByteArrayOutputStream().also { file.copyTo(it) }.toString()
        val numbers = Regex("\"n\":(\\d+)").findAll(text).map { it.groupValues[1].toInt() }.toList()

        assertThat(numbers.last()).isEqualTo(299)
        assertThat(numbers).isInStrictOrder()
        assertThat(numbers.first()).isGreaterThan(0)
        assertThat(text.lines().filter { it.isNotEmpty() }.all { it.startsWith("{") && it.endsWith("}") }).isTrue()
    }

    @Test
    fun `the real cap is five megabytes`() {
        assertThat(MeasureFile.CAP_BYTES).isEqualTo(5L * 1024 * 1024)
    }

    @Test
    fun `a batch bigger than half the cap keeps its newest lines`(@TempDir dir: File) {
        val file = MeasureFile(dir, capBytes = 200)

        file.append((0 until 50).map { "line-%02d".format(it) })

        val text = ByteArrayOutputStream().also { file.copyTo(it) }.toString()
        assertThat(file.size()).isAtMost(200L)
        assertThat(text).contains("line-49")
        assertThat(text).doesNotContain("line-00")
    }
}
