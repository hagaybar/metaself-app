package com.metaself.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * What a crash leaves under Recent problems: enough to find the line, in the form R8's retrace reads
 * (each frame as `StackTraceElement.toString()`). Every frame here is invented.
 */
class CrashDetailTest {

    private fun frame(cls: String, method: String, line: Int) = StackTraceElement(cls, method, cls.substringAfterLast('.') + ".kt", line)

    private fun failure(message: String, vararg frames: StackTraceElement, cause: Throwable? = null) =
        IllegalStateException(message, cause).apply { stackTrace = arrayOf(*frames) }

    @Test
    fun `one throwable is its class and message, then its first three frames`() {
        val error = failure(
            "boom",
            frame("androidx.compose.runtime.SlotTable", "groupSize", 10),
            frame("androidx.compose.runtime.Composer", "end", 20),
            frame("androidx.compose.runtime.Recomposer", "run", 30),
        )

        assertThat(crashDetail(error)).isEqualTo(
            "java.lang.IllegalStateException: boom | " +
                "at androidx.compose.runtime.SlotTable.groupSize(SlotTable.kt:10) | " +
                "at androidx.compose.runtime.Composer.end(Composer.kt:20) | " +
                "at androidx.compose.runtime.Recomposer.run(Recomposer.kt:30)",
        )
    }

    @Test
    fun `past the first three, only the app's own frames are kept, six at most, and the rest counted`() {
        val library = (1..3).map { frame("androidx.lib.A", "f$it", it) }
        val mixed = (1..10).flatMap { listOf(frame("com.metaself.app.ui.Screen", "g$it", it), frame("android.os.Looper", "loop", it)) }
        val error = failure("boom", *(library + mixed).toTypedArray())

        val parts = crashDetail(error).split(" | ")

        assertThat(parts.first()).isEqualTo("java.lang.IllegalStateException: boom")
        assertThat(parts.drop(1).take(3)).containsExactlyElementsIn(library.map { "at $it" }).inOrder()
        assertThat(parts.drop(4).take(6)).containsExactlyElementsIn((1..6).map { "at com.metaself.app.ui.Screen.g$it(Screen.kt:$it)" }).inOrder()
        assertThat(parts.last()).isEqualTo("… 14 more")
        assertThat(parts).hasSize(1 + 3 + 6 + 1)
    }

    @Test
    fun `the causes follow, each said the same way`() {
        val root = IllegalArgumentException("index=-5").apply { stackTrace = arrayOf(frame("androidx.compose.runtime.SlotTable", "a", 1)) }
        val error = failure("wrapped", frame("com.metaself.app.MainActivity", "b", 2), cause = root)

        assertThat(crashDetail(error)).isEqualTo(
            "java.lang.IllegalStateException: wrapped | at com.metaself.app.MainActivity.b(MainActivity.kt:2) | " +
                "Caused by: java.lang.IllegalArgumentException: index=-5 | at androidx.compose.runtime.SlotTable.a(SlotTable.kt:1)",
        )
    }

    @Test
    fun `suppressed failures are said too`() {
        val error = failure("first", frame("com.metaself.app.A", "a", 1))
        error.addSuppressed(IllegalStateException("second").apply { stackTrace = arrayOf(frame("com.metaself.app.B", "b", 2)) })

        assertThat(crashDetail(error)).contains("Suppressed: java.lang.IllegalStateException: second | at com.metaself.app.B.b(B.kt:2)")
    }

    @Test
    fun `a chain is followed five deep at most, and a loop is not followed round`() {
        var error: Throwable = failure("0", frame("com.metaself.app.A", "a", 0))
        (1..8).forEach { error = failure("$it", frame("com.metaself.app.A", "a", it), cause = error) }

        val deep = crashDetail(error)

        assertThat(deep.split(" | ").count { it.startsWith("java.lang") || it.startsWith("Caused by") }).isEqualTo(5)
        assertThat(deep).endsWith("… more causes not shown")

        // Made without a cause, so the loop can be closed with initCause.
        val a = IllegalStateException("a").apply { stackTrace = arrayOf(frame("com.metaself.app.A", "a", 1)) }
        val b = failure("b", frame("com.metaself.app.B", "b", 2), cause = a)
        a.initCause(b)

        assertThat(crashDetail(b).split(" | ").count { it.startsWith("java.lang") || it.startsWith("Caused by") }).isEqualTo(2)
    }

    @Test
    fun `it is one line`() {
        val error = failure("line one\nline two", frame("com.metaself.app.A", "a", 1))

        assertThat(crashDetail(error)).doesNotContain("\n")
    }

    @Test
    fun `a refusal is still said in one short line`() {
        val error = failure("disk full", frame("com.metaself.app.A", "a", 1), frame("com.metaself.app.B", "b", 2))

        assertThat(problemDetail(error)).isEqualTo("java.lang.IllegalStateException: disk full at com.metaself.app.A.a(A.kt:1)")
    }
}
