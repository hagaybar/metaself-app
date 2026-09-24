package com.metaself.app.sim

import com.metaself.app.ui.ComposeSession

/**
 * What the screen said, step by step, written for something else to read.
 *
 * Two readers, with opposite needs, and the format serves both. The walking agent reads the tail of
 * it to decide what to do next, so the latest screen has to be findable without parsing. The
 * reviewing agent reads the whole thing hunting for the moment the walk went wrong, so nothing may
 * be summarised away — an instruction that failed is as much a part of the record as one that
 * worked, and rather more interesting.
 *
 * **It records no geometry, and says so at the top** (public issue #6, item 5). Robolectric draws on a
 * 320 dp canvas with no real font, so a label measures about a pixel per character: recorded bounds
 * would be precise numbers about a layout no phone draws, and a walk would reason from them as if they
 * were real. What it does record is the one spatial fact this harness knows for certain — which
 * WINDOW each line is in: the screen, or a menu, dialog or sheet open over it, and which of those a
 * finger can reach. Order within a window is the screen's reading order, not a position.
 */
class Transcript {

    private val entries = mutableListOf<Entry>()

    /** One instruction and what became of it. */
    data class Entry(
        val step: Step,
        val outcome: Outcome,
        val screen: List<String>,
        val actions: List<String>,
        /** The screen window by window; empty when the caller had only the flat list. */
        val layers: List<ComposeSession.Layer> = emptyList(),
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

    fun record(
        step: Step,
        outcome: Outcome,
        screen: List<String>,
        actions: List<String>,
        layers: List<ComposeSession.Layer> = emptyList(),
    ) {
        entries += Entry(step, outcome, screen, actions, layers)
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
                "screen below, not as a step marked STUCK. " +
                "${notAsAskedCount - stuckCount} landed on a menu, dialog or sheet open over what " +
                "they named, and touched nothing beneath it.",
        )
        appendLine()
        appendLine(NO_GEOMETRY)
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
            } else if (entry.layers.size <= 1) {
                entry.screen.forEach { appendLine("> $it") }
            } else {
                entry.layers.forEachIndexed { at, layer ->
                    val where = if (at == 0) "On ${layer.kind}" else "Open over it, ${layer.kind}"
                    val reach = if (layer.reachable) "" else " — covered: a finger cannot reach this"
                    if (at > 0) appendLine()
                    appendLine("$where$reach:")
                    appendLine()
                    layer.lines.forEach { appendLine("> $it") }
                }
            }
            appendLine()
            appendLine("Can be touched here: " + entry.actions.joinToString("; ").ifEmpty { "nothing" })
            appendLine()
        }
    }

    companion object {
        /** Said once, at the top, so a reader never mistakes the order of lines for a layout. */
        const val NO_GEOMETRY =
            "No positions or sizes are recorded, deliberately: this is drawn on a stand-in canvas with " +
                "no real font, so where things are and how big they are here is not where they are on " +
                "a phone. Do not conclude that something is off screen, overlapping, cut off or too " +
                "small from this transcript. What is recorded is which window each line is in — the " +
                "screen, or a menu, dialog or sheet open over it — and what a finger can reach."
    }
}
