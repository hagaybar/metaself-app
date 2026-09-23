package com.metaself.app.data.diagnostics

/**
 * One thing that went wrong, as the owner would read it back to whoever is fixing it.
 *
 * @property whenMillis wall-clock, because "yesterday evening" is how he will describe it.
 * @property kind a short category — "crash", "estimate refused" — so a list is skimmable.
 * @property detail what actually happened. **Never the meal description and never the key:** the
 *   whole point of this log is that it can be copied and sent to somebody, and a log that is not
 *   safe to share is a log nobody shares.
 */
data class Problem(
    val whenMillis: Long,
    val kind: String,
    val detail: String,
)

/**
 * The last few things that went wrong, kept on this phone.
 *
 * **It is never sent anywhere.** Decision D16 forbids analytics and third parties, and a crash
 * reporting service is both. This is the same information, kept where the owner can read it and
 * decide for himself whether to pass it on.
 */
interface ProblemLog {

    /** Newest first, because the one that just happened is the one being looked for. */
    fun recent(): List<Problem>

    fun record(kind: String, detail: String)

    fun clear()

    companion object {
        /** For anything that has no interest in recording — tests, mostly. */
        val NONE: ProblemLog = object : ProblemLog {
            override fun recent(): List<Problem> = emptyList()
            override fun record(kind: String, detail: String) = Unit
            override fun clear() = Unit
        }
    }
}
