package com.metaself.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.AnnotatedString
import com.metaself.app.ui.theme.MetaSelfTheme

/**
 * One composition, kept alive, driven the way a person drives a phone.
 *
 * [ComposeRender] is the right tool for asserting on a single drawing and the wrong one for a
 * session: every `texts()` call builds a fresh activity, so the second look throws away whatever the
 * first look created, and `click` leaves the node list pointing at the render from BEFORE the press.
 * Nothing in this project had ever pressed a button and then read what the screen said next.
 *
 * **Why this one takes Compose's own test rule when nothing else in the suite does.** Driving a live
 * composition by hand does not work, and fails in a way that looks like success: advancing
 * Robolectric's looper recomposes the ROOT content lambda, so a counter written inline into
 * `setContent` updates and the harness looks finished. Every real screen keeps its state a level
 * down, in a child composable, and a child's invalidated scope is only recomposed by Compose's own
 * frame clock. Measured, not assumed — an inline counter updated and the identical counter moved
 * into a named function did not, with everything else held the same.
 *
 * So this uses `createComposeRule`, which brings JUnit 4 rule machinery with it. That is the
 * project's existing exception rather than a new one: JUnit 4 ONLY where a framework demands it,
 * which is Robolectric and Compose. A test using this class is therefore a JUnit 4 test and its
 * `@Test` must come from `org.junit`, never `org.junit.jupiter.api` — the two annotations look
 * identical at the call site and mixing them produces a test that silently never runs.
 */
class ComposeSession(private val compose: ComposeContentTestRule) {

    /** Renders [content] and returns what it says. Call once per session. */
    fun start(content: @Composable () -> Unit): List<String> {
        compose.setContent { MetaSelfTheme { content() } }
        compose.waitForIdle()
        return screen()
    }

    /** What the screen says now: text, what is typed into fields, and what a screen reader hears. */
    fun screen(): List<String> = nodes().flatMap { it.readableText() }

    /**
     * Presses the thing whose label is [label], and returns what the screen says afterwards.
     *
     * Throws [NothingSaysThat] when no node matches — which the driver records as being stuck rather
     * than as a crash, because that is what being stuck looks like from the inside.
     */
    fun press(label: String): List<String> = act(label, SemanticsActions.OnClick, "press") { node ->
        node.config[SemanticsActions.OnClick].action?.invoke()
    }

    /** Holds [label] down. This is how choosing begins on the food list and on a day. */
    fun hold(label: String): List<String> =
        act(label, SemanticsActions.OnLongClick, "hold") { node ->
            node.config[SemanticsActions.OnLongClick].action?.invoke()
        }

    /**
     * Types [text] into the field identified by [label].
     *
     * A field is found by its label, its placeholder or what is already in it — whichever the screen
     * happens to expose — because that is all a person has to go on. `SetText` replaces the
     * contents, which is what typing into a field a person has selected does.
     */
    fun type(label: String, text: String): List<String> =
        act(label, SemanticsActions.SetText, "type into") { node ->
            node.config[SemanticsActions.SetText].action?.invoke(AnnotatedString(text))
        }

    /**
     * Every label on screen that can be pressed, held or typed into.
     *
     * This is the driver's answer to "what are my options here", and the only thing standing between
     * an agent and guessing. A screen that offers nothing actionable is itself a finding.
     */
    fun actions(): List<Action> = nodes().mapNotNull { node ->
        val kinds = buildList {
            if (node.config.getOrNull(SemanticsActions.OnClick) != null) add("press")
            if (node.config.getOrNull(SemanticsActions.OnLongClick) != null) add("hold")
            if (node.config.getOrNull(SemanticsActions.SetText) != null) add("type into")
        }
        if (kinds.isEmpty()) return@mapNotNull null
        val on = node.switchedOn()
        val label = node.readableText().firstOrNull { it.isNotBlank() }
            // A control whose only words are empty is a real thing a person meets: a text field with
            // nothing in it and no placeholder. Naming it by what it can do at least lets the walk
            // reach it, and the emptiness is itself worth reporting.
            ?: UNLABELLED
        Action(label = label, kinds = kinds, switchedOn = on)
    }

    /** A label on screen, what can be done to it, and whether it is currently on. */
    data class Action(
        val label: String,
        val kinds: List<String>,
        /** Null when this is not the kind of control that is either on or off. */
        val switchedOn: Boolean? = null,
    ) {
        /** How the transcript prints it. */
        override fun toString(): String = buildString {
            append("\"").append(label).append("\" (").append(kinds.joinToString("/"))
            switchedOn?.let { append(if (it) ", ON" else ", OFF") }
            append(")")
        }
    }

    /** No node on this screen matched. Being stuck, as the driver sees it. */
    class NothingSaysThat(message: String) : RuntimeException(message)

    /**
     * Two nodes answer to the same words, so which one an instruction meant is unknowable.
     *
     * Matching on a prefix and silently taking the first hit is how a transcript becomes a lie: the
     * walk records pressing one thing and the app was pressed somewhere else. An ambiguous
     * instruction is refused rather than resolved.
     */
    class MoreThanOneSaysThat(message: String) : RuntimeException(message)

    private fun act(
        label: String,
        action: SemanticsPropertyKey<*>,
        verb: String,
        perform: (SemanticsNode) -> Unit,
    ): List<String> {
        perform(node(label, action, verb))
        compose.waitForIdle()
        return screen()
    }

    /**
     * Finds the one node answering to [label] that can take [action].
     *
     * Exact matches win outright: a screen holding both "Save" and "Save this ratio" must let an
     * instruction reach the shorter one. Only when nothing matches exactly does this fall back to a
     * prefix, and a prefix matching two things is refused rather than guessed at.
     */
    private fun node(label: String, action: SemanticsPropertyKey<*>, verb: String): SemanticsNode {
        val wanted = label.trim()
        val able = nodes().filter { it.config.getOrNull(action) != null }
        val exact = able.filter { node -> node.readableText().any { it.trim() == wanted } }
        val candidates = exact.ifEmpty {
            if (wanted == UNLABELLED) {
                able.filter { node -> node.readableText().none { it.isNotBlank() } }
            } else {
                able.filter { node -> node.readableText().any { it.trim().startsWith(wanted) } }
            }
        }
        return when (candidates.size) {
            1 -> candidates.single()
            0 -> throw NothingSaysThat(
                "nothing on this screen can be ${verb}d as \"$label\". What is here: " +
                    actions().joinToString("; ") {
                        "\"${it.label}\" (${it.kinds.joinToString("/")})"
                    },
            )
            else -> throw MoreThanOneSaysThat(
                "${candidates.size} things answer to \"$label\": " +
                    candidates.joinToString("; ") {
                        "\"" + it.readableText().joinToString(" / ") + "\""
                    },
            )
        }
    }

    /**
     * Every node on screen.
     *
     * `atLeastOneRootRequired = false` so that reading a screen which has drawn nothing at all
     * reports an empty screen rather than throwing. A blank screen is a finding, not an error.
     */
    private fun nodes(): List<SemanticsNode> =
        compose.onAllNodes(SemanticsMatcher("anything") { true })
            .fetchSemanticsNodes(atLeastOneRootRequired = false)

    /**
     * Text, what is typed into a field, and what is said to a screen reader.
     *
     * The last of those matters more here than in a one-shot render: half of what this app offers a
     * finger is an icon whose only words are its description — the back arrow, the overflow menu,
     * the floating button that starts a log.
     */
    private fun SemanticsNode.readableText(): List<String> =
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            listOfNotNull(config.getOrNull(SemanticsProperties.EditableText)?.text) +
            config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

    /**
     * Whether this control is currently on, when it is the kind of control that can be.
     *
     * Without this the walk reported that "Show hidden" and "Only know a portion" say nothing about
     * whether they are on or off. They are `FilterChip`s and they say so perfectly well — through
     * `Selected`, which a reader of text alone never sees. A screen reader does see it, so leaving it
     * out was the harness mistaking its own blindness for the app's silence.
     *
     * What this does NOT rescue is a control that distinguishes its states by colour only. Those stay
     * invisible here, and rightly: they are invisible to a screen reader too.
     */
    private fun SemanticsNode.switchedOn(): Boolean? =
        config.getOrNull(SemanticsProperties.Selected)
            ?: config.getOrNull(SemanticsProperties.ToggleableState)?.let {
                it == androidx.compose.ui.state.ToggleableState.On
            }

    companion object {
        /** What the driver calls a control that shows no words at all. */
        const val UNLABELLED = "(unlabelled)"
    }
}
