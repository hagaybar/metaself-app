package com.metaself.app.sim

/**
 * What the screen said, step by step, written for something else to read.
 *
 * Two readers, with opposite needs, and the format serves both. The walking agent reads the tail of
 * it to decide what to do next, so the latest screen has to be findable without parsing. The
 * reviewing agent reads the whole thing hunting for the moment the walk went wrong, so nothing may
 * be summarised away — an instruction that failed is as much a part of the record as one that
 * worked, and rather more interesting.
 */
class Transcript {

    private val entries = mutableListOf<Entry>()

    /** One instruction and what became of it. */
    data class Entry(
        val step: Step,
        val outcome: Outcome,
        val screen: List<String>,
        val actions: List<String>,
    )

    sealed interface Outcome {
        /** The instruction was carried out. */
        data object Did : Outcome

        /**
         * The app would not, or could not, do it.
         *
         * This is the finding this whole exercise exists to collect, so it carries the app's own
         * words rather than a summary of them.
         */
        data class Stuck(val why: String) : Outcome

        /**
         * The finger came down, but on a menu, dialog or sheet open over what was named — so the
         * window on top answered, as it would on a phone, and nothing beneath it was touched.
         */
        data class LandedOutside(val why: String) : Outcome
    }

    fun record(step: Step, outcome: Outcome, screen: List<String>, actions: List<String>) {
        entries += Entry(step, outcome, screen, actions)
    }

    val stuckCount: Int get() = entries.count { it.outcome is Outcome.Stuck }

    /** Steps that did not do what they said: stuck, or landed on something open over the target. */
    val notAsAskedCount: Int get() = entries.count { it.outcome !is Outcome.Did }

    /** Every step and what became of it, for a check that reads the walk rather than its rendering. */
    val steps: List<Entry> get() = entries.toList()

    fun render(title: String): String = buildString {
        appendLine("# $title")
        appendLine()
        appendLine(
            "${entries.size} steps. ${stuckCount} did not happen — which means the instruction found " +
                "nothing to act on, NOT that the app refused. A real refusal appears as words on the " +
                "screen below, not as a step marked STUCK.",
        )
        appendLine()
        entries.forEachIndexed { index, entry ->
            appendLine("## Step ${index + 1} — `${entry.step.line}`")
            appendLine()
            when (val outcome = entry.outcome) {
                is Outcome.Did -> appendLine("Done.")
                is Outcome.Stuck -> {
                    // Deliberately not "the app refused". Nothing here reached the app at all: the
                    // instruction named something the screen does not offer, or named two things at
                    // once. Calling that a refusal is how a transcript starts accusing the app of
                    // the harness's own limits.
                    appendLine("**DID NOT HAPPEN** (the instruction found nothing to act on): ${outcome.why}")
                }
                is Outcome.LandedOutside ->
                    appendLine("**LANDED ON WHAT WAS OPEN OVER IT**: ${outcome.why}")
            }
            appendLine()
            appendLine("Screen now:")
            appendLine()
            if (entry.screen.isEmpty()) {
                appendLine("> (the screen said nothing at all)")
            } else {
                entry.screen.forEach { appendLine("> $it") }
            }
            appendLine()
            appendLine("Can be touched here: " + entry.actions.joinToString("; ").ifEmpty { "nothing" })
            appendLine()
        }
    }
}
