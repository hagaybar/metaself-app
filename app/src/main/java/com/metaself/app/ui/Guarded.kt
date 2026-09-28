package com.metaself.app.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.R
import com.metaself.app.data.diagnostics.ProblemLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * What a screen says when something it started threw instead of finishing.
 *
 * Three sentences, because only one of them is true for any given action: an action that writes in
 * one transaction changed nothing when it failed; one that writes in several may have partly
 * happened; and one that only reads to open something wrote nothing but also opened nothing.
 */
enum class ActionRefused(@StringRes val sentence: Int) {
    NOTHING_CHANGED(R.string.action_refused_nothing_changed),
    MAYBE_PARTIAL(R.string.action_refused_maybe_partial),
    COULD_NOT_OPEN(R.string.action_refused_could_not_open),
}

/**
 * Launch a screen's action so that a failure is said out loud instead of taking the app down.
 *
 * Any [Exception] out of [block] is written to [problems] as `"refused"`, in the shape the
 * process-wide crash handler writes, and handed to [onRefused] — which is where the screen puts up
 * its sentence. [CancellationException] is let through, because it is how the scope ends and not a
 * failure. An [Error] is not caught either: running out of memory is not something a sentence
 * recovers from, and it still reaches the crash handler.
 *
 * A problem log that cannot write does not stop the sentence: being told is the point.
 */
fun ViewModel.guarded(
    problems: ProblemLog,
    onRefused: (Throwable) -> Unit,
    block: suspend CoroutineScope.() -> Unit,
): Job = viewModelScope.launch {
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        runCatching { problems.record(kind = "refused", detail = problemDetail(failure)) }
        onRefused(failure)
    }
}

/**
 * One line about a failure: its class, its message and the frame it was thrown from. For a refusal,
 * which the app survives; a crash gets [crashDetail].
 */
fun problemDetail(error: Throwable): String =
    "${error::class.java.name}: ${error.message} at ${error.stackTrace.firstOrNull()}"

/**
 * A crash, in enough detail to find the line: the throwable, then its suppressed failures and its
 * causes — at most [CRASH_THROWABLES] in all, none twice (a cause chain can loop). Each is
 * "Class: message", its first [CRASH_FIRST_FRAMES] frames, then up to [CRASH_APP_FRAMES] of the app's
 * own frames from the rest, then "… n more" for those left out. One line, parts joined with " | ";
 * each frame is `StackTraceElement.toString()`, the form R8's retrace reads back.
 *
 * The frame only [problemDetail] gives was not enough: a crash inside Compose's runtime is thrown
 * from a library frame that says nothing about which screen caused it.
 */
fun crashDetail(error: Throwable): String {
    val parts = mutableListOf<String>()
    val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
    val pending = ArrayDeque<Pair<String, Throwable>>().apply { add("" to error) }
    while (pending.isNotEmpty()) {
        val (label, next) = pending.removeFirst()
        if (!seen.add(next)) continue
        if (seen.size > CRASH_THROWABLES) {
            parts += "… more causes not shown"
            break
        }
        parts += label + "${next::class.java.name}: ${next.message}"
        parts += framesOf(next)
        // Suppressed first, then the cause, as the JVM prints them.
        next.suppressed.forEach { pending.add("Suppressed: " to it) }
        next.cause?.let { pending.add("Caused by: " to it) }
    }
    return parts.joinToString(" | ").replace(Regex("\\s*\n\\s*"), " ")
}

private fun framesOf(error: Throwable): List<String> {
    val frames = error.stackTrace
    val first = frames.take(CRASH_FIRST_FRAMES)
    val own = frames.drop(CRASH_FIRST_FRAMES).filter { it.className.startsWith(APP_PACKAGE) }.take(CRASH_APP_FRAMES)
    val left = frames.size - first.size - own.size
    return (first + own).map { "at $it" } + if (left > 0) listOf("… $left more") else emptyList()
}

/** A choice: how many throwables of one crash are said. */
private const val CRASH_THROWABLES = 5

/** A choice: the frames said from the top of each throwable, whoever's they are. */
private const val CRASH_FIRST_FRAMES = 3

/** A choice: the app's own frames said from the rest of each throwable. */
private const val CRASH_APP_FRAMES = 6

private const val APP_PACKAGE = "com.metaself."
