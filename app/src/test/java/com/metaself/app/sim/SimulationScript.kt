package com.metaself.app.sim

/**
 * What the walking agent asks the app to do, parsed from plain lines of text.
 *
 * The agent writes a batch of these to a file and the run replays them. Batching is a concession to
 * this machine, where one run of the suite costs about half a minute: an agent that stopped to think
 * after every tap would spend its whole life waiting on Gradle. Thinking is batched; OBSERVING is
 * not — the transcript records the screen after every single step, so nothing is lost but the
 * agent's opportunity to change its mind mid-batch.
 */
sealed interface Step {

    /** The line as the agent wrote it, kept verbatim for the transcript. */
    val line: String

    data class Press(override val line: String, val label: String) : Step
    data class Hold(override val line: String, val label: String) : Step
    data class Type(override val line: String, val label: String, val text: String) : Step

    /** Read the screen without touching it. */
    data class Look(override val line: String) : Step

    /** The system back gesture, which is a real way out of a screen and often the only one. */
    data class Back(override val line: String) : Step

    /**
     * A line the agent wrote that is not an instruction at all.
     *
     * Kept rather than dropped: a malformed step means the agent believed it was doing something,
     * and a transcript that silently skips it would show a walk that never happened.
     */
    data class Unreadable(override val line: String, val why: String) : Step
}

/**
 * Turns the agent's file into steps.
 *
 * Blank lines and `#` comments are dropped — the agent is encouraged to leave notes to itself about
 * what it is trying, and those notes are not instructions.
 */
object SimulationScript {

    private const val TYPE_SEPARATOR = "|"

    fun parse(text: String): List<Step> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { parseLine(it) }
        .toList()

    private fun parseLine(line: String): Step {
        val verb = line.substringBefore(' ').lowercase()
        val rest = line.substringAfter(' ', missingDelimiterValue = "").trim()
        return when {
            verb == "look" -> Step.Look(line)
            verb == "back" -> Step.Back(line)
            verb == "press" && rest.isNotEmpty() -> Step.Press(line, rest)
            verb == "hold" && rest.isNotEmpty() -> Step.Hold(line, rest)
            verb == "type" -> parseType(line, rest)
            verb in setOf("press", "hold") -> Step.Unreadable(line, "$verb needs something to $verb")
            else -> Step.Unreadable(line, "\"$verb\" is not something this app can be asked to do")
        }
    }

    /**
     * `type <field> | <what to type>`.
     *
     * A bar rather than a space, because both halves are free text a person would actually write:
     * the field is identified by the words shown against it, and what is typed into it is a food's
     * name, which has spaces in it. Splitting on whitespace would make "Greek salad" two arguments.
     */
    private fun parseType(line: String, rest: String): Step = when {
        !rest.contains(TYPE_SEPARATOR) -> Step.Unreadable(
            line,
            "type needs a field and what to type, separated by \"$TYPE_SEPARATOR\"",
        )
        else -> Step.Type(
            line = line,
            label = rest.substringBefore(TYPE_SEPARATOR).trim(),
            text = rest.substringAfter(TYPE_SEPARATOR).trim(),
        )
    }
}
