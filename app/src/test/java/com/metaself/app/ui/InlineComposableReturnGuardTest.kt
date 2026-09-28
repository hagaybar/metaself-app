package com.metaself.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * No labelled early return out of an inline layout composable's content in the app's source.
 *
 * `Column`, `Row`, `Box` and the rest are inline composables. With the Compose compiler this project
 * builds with (1.5.9), a `return@Column` taken after the block had already emitted something closed
 * one group too many, and the next recomposition that took a different path through the block
 * crashed on a slot-table index below zero. The Movement screen's workout-file lines did exactly that
 * and crashed on every import. Branch with `if`/`when` instead.
 *
 * A source scan rather than a render test because the crash needs a particular sequence of states to
 * show, and a new block written the same way would carry its own sequence.
 *
 * **What this cannot catch:** a bare, non-local `return` written directly inside such a lambda —
 * one with no `@Label` at all, closing over the enclosing function rather than the composable's own
 * block — matches nothing here, since the regex looks for a label.
 */
class InlineComposableReturnGuardTest {

    private val labelled =
        Regex("""return@(Column|Row|Box|Card|Surface|FlowRow|FlowColumn|LazyColumn|Scaffold|ModalBottomSheet|Layout|key)\b""")

    @Test
    fun `no source returns early out of an inline layout composable`() {
        val root = File("src/main/java")
        assertThat(root.isDirectory).isTrue()

        val hits = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> labelled.containsMatchIn(line) }
                    .map { (i, line) -> "${file.path}:${i + 1}: ${line.trim()}" }
            }
            .toList()

        assertThat(hits).isEmpty()
    }

    @Test
    fun `the pattern catches the shape it guards against`() {
        assertThat(labelled.containsMatchIn("        if (file == null) return@Column")).isTrue()
        assertThat(labelled.containsMatchIn("    return@LazyColumn")).isTrue()
        assertThat(labelled.containsMatchIn("    return@Layout")).isTrue()
        assertThat(labelled.containsMatchIn("    return@key")).isTrue()
        assertThat(labelled.containsMatchIn("    return@FlowColumn")).isTrue()
        assertThat(labelled.containsMatchIn("    return@forEach")).isFalse()
        assertThat(labelled.containsMatchIn("    return@Columns")).isFalse()
        assertThat(labelled.containsMatchIn("    return@Keys")).isFalse()
    }
}
