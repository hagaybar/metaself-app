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
 * One line about a failure: its class, its message and the frame it was thrown from.
 *
 * Shared with the crash handler in `MetaSelfApp`, so a refusal and a crash read alike under Recent
 * problems.
 */
fun problemDetail(error: Throwable): String =
    "${error::class.java.name}: ${error.message} at ${error.stackTrace.firstOrNull()}"
