package com.metaself.app.ui

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
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
 *
 * **A press is a touch** (public issue #6, item 1). Presses and holds go through Compose's own touch
 * injection, so hit-testing applies: a disabled button ignores the finger, and whatever is drawn on
 * top of a control receives the tap instead of it. That injection goes straight into the window the
 * control is in, though, and so on its own it still reached THROUGH an open menu or dialog — measured:
 * a press on a button behind an open `DropdownMenu` reached the button. The phone's window manager is
 * what stops that, so this class stands in for it:
 *
 * - While a touch-modal window is open (a menu, a dialog, a sheet — a window without
 *   `FLAG_NOT_FOCUSABLE` or `FLAG_NOT_TOUCH_MODAL`), only what is in it, or in a window above it, can
 *   be reached, and only that is listed by [actions].
 * - An instruction naming something beneath it is carried out as the tap a finger would make there:
 *   delivered to the open window as a touch outside it — which a menu or a dialog answers by closing
 *   — or, when the open window covers the whole screen (a sheet's scrim), at the control's own place
 *   on it. Nothing beneath is pressed, and [LandedOutside] says so.
 *
 * Which window is on top is read from the window manager's own list, in the order windows were added.
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
     * What the screen says, window by window, bottom first: the app's own screen, then each menu,
     * dialog or sheet open over it, and whether a finger can reach it.
     */
    fun layers(): List<Layer> {
        val order = windowOrder()
        val reachableFrom = topModal(order)
        return nodes().groupBy { windowOf(it) }
            .entries
            .sortedBy { (window, _) -> order.indexOf(window) }
            .map { (window, nodes) ->
                Layer(
                    kind = kindOf(window, nodes, isFirst = order.indexOf(window) == 0),
                    lines = nodes.flatMap { it.readableText() },
                    reachable = order.indexOf(window) >= reachableFrom,
                )
            }
    }

    /** One window's worth of the screen. [kind] is what a person would call it. */
    data class Layer(val kind: String, val lines: List<String>, val reachable: Boolean)

    /**
     * Presses the thing whose label is [label], and returns what the screen says afterwards.
     *
     * Throws [NothingSaysThat] when no node matches — which the driver records as being stuck rather
     * than as a crash, because that is what being stuck looks like from the inside.
     */
    fun press(label: String): List<String> = act(label, SemanticsActions.OnClick, "press") { node ->
        touch(node).performTouchInput { click() }
    }

    /** Holds [label] down. This is how choosing begins on the food list and on a day. */
    fun hold(label: String): List<String> =
        act(label, SemanticsActions.OnLongClick, "hold") { node ->
            touch(node).performTouchInput { longClick() }
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
            if (node.config.getOrNull(SemanticsProperties.Disabled) != null) {
                throw SwitchedOff("\"$label\" is switched off; nothing can be typed into it")
            }
            interaction(node).performTextReplacement(text)
        }

    /**
     * Every label on screen that can be pressed, held or typed into.
     *
     * This is the driver's answer to "what are my options here", and the only thing standing between
     * an agent and guessing. A screen that offers nothing actionable is itself a finding.
     */
    fun actions(): List<Action> = reachableNodes().mapNotNull { node ->
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
        Action(
            label = label,
            kinds = kinds,
            switchedOn = on,
            enabled = node.config.getOrNull(SemanticsProperties.Disabled) == null,
        )
    }

    /** A label on screen, what can be done to it, and whether it is currently on. */
    data class Action(
        val label: String,
        val kinds: List<String>,
        /** Null when this is not the kind of control that is either on or off. */
        val switchedOn: Boolean? = null,
        /**
         * False for a control drawn but switched off. A finger on it does nothing, so the walk has to
         * be told — before this, a press reached a disabled button's action anyway.
         */
        val enabled: Boolean = true,
    ) {
        /** How the transcript prints it. */
        override fun toString(): String = buildString {
            append("\"").append(label).append("\" (").append(kinds.joinToString("/"))
            switchedOn?.let { append(if (it) ", ON" else ", OFF") }
            if (!enabled) append(", SWITCHED OFF")
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

    /**
     * The control named is beneath an open menu, dialog or sheet. The tap was made — it landed on
     * the open window, outside what it offers, and that window answered as it does on a phone — but
     * nothing beneath was pressed.
     */
    class LandedOutside(message: String) : RuntimeException(message)

    /** A field that is drawn but switched off. */
    class SwitchedOff(message: String) : RuntimeException(message)

    private fun act(
        label: String,
        action: SemanticsPropertyKey<*>,
        verb: String,
        perform: (SemanticsNode) -> Unit,
    ): List<String> {
        val order = windowOrder()
        val reachableFrom = topModal(order)
        val target = node(label, action, verb)
        val window = windowOf(target)
        if (order.indexOf(window) < reachableFrom) {
            val over = order[reachableFrom]
            val kind = kindOf(over, nodes().filter { windowOf(it) == over }, isFirst = false)
            tapOutside(over, target)
            compose.waitForIdle()
            throw LandedOutside(
                "\"$label\" is beneath $kind that is open over it. The finger landed on $kind, outside " +
                    "what it offers, as it would on a phone; nothing beneath it was ${past(verb)}.",
            )
        }
        perform(target)
        compose.waitForIdle()
        return screen()
    }

    /**
     * The node, brought on screen the way a person brings it: scrolled to, when something it sits in
     * scrolls. A control that stays off screen cannot be touched, and saying so is better than a tap
     * that lands on nothing and reads as the app ignoring it.
     */
    private fun touch(node: SemanticsNode): SemanticsNodeInteraction {
        val interaction = interaction(node)
        val scrolls = generateSequence(node.parent) { it.parent }
            .any { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
        if (scrolls) interaction.performScrollTo()
        val bounds = interaction.fetchSemanticsNode().boundsInRoot
        if (bounds.width <= 0f || bounds.height <= 0f) {
            throw NothingSaysThat(
                "\"${node.readableText().firstOrNull() ?: UNLABELLED}\" is not on the screen, and nothing " +
                    "scrolls to it",
            )
        }
        return interaction
    }

    private fun past(verb: String): String = when (verb) {
        "press" -> "pressed"
        "hold" -> "held"
        "type into" -> "typed into"
        else -> verb
    }

    private fun interaction(node: SemanticsNode): SemanticsNodeInteraction =
        compose.onNode(SemanticsMatcher("the node answering to what was asked") { it.id == node.id })

    // --- Windows: what the phone's window manager decides and a test rule does not ------------------

    /** Every window on screen, in the order they were added — the last is on top. */
    @Suppress("UNCHECKED_CAST")
    private fun windowOrder(): List<View> {
        val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        val views = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global)
        return (views as List<View>).toList()
    }

    /** The index of the topmost window that takes every touch on the screen, or 0 when none does. */
    private fun topModal(order: List<View>): Int =
        order.indices.lastOrNull { at -> order[at].isTouchModal() && order[at].isShown } ?: 0

    private fun View.isTouchModal(): Boolean {
        val flags = (layoutParams as? WindowManager.LayoutParams)?.flags ?: return true
        return flags and (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL) == 0
    }

    private fun windowOf(node: SemanticsNode): View {
        val root = generateSequence(node) { it.parent }.last()
        return (root.root as ViewRootForTest).view.rootView
    }

    private fun kindOf(window: View, nodes: List<SemanticsNode>, isFirst: Boolean): String = when {
        isFirst -> "the screen"
        nodes.any { it.config.getOrNull(SemanticsProperties.IsDialog) != null } -> "a dialog"
        window.javaClass.simpleName.contains("BottomSheet") -> "a sheet"
        nodes.any { it.config.getOrNull(SemanticsProperties.IsPopup) != null } -> "a menu"
        else -> "a window"
    }

    /**
     * A finger coming down where [target] is drawn, on [over], which covers it.
     *
     * A window drawn over only part of the screen — a menu, a dialog — is touched outside itself, and
     * is sent the touch at a place well outside it, as the window manager sends it to a touch-modal
     * window. One that covers the whole screen (a sheet's scrim) is touched at the target's own place,
     * which is where the finger lands on it. Robolectric's window geometry is not the phone's (a
     * dialog here is drawn at the corner, not centred), so this deliberately does not decide by
     * where a partial window is drawn: whatever is beneath one is treated as outside it.
     */
    private fun tapOutside(over: View, target: SemanticsNode) {
        val screen = windowOrder().first()
        val coversAll = over.width >= screen.width && over.height >= screen.height
        val (x, y) = if (coversAll) {
            target.boundsInRoot.center.let { it.x to it.y }
        } else {
            FAR_OUTSIDE to FAR_OUTSIDE
        }
        compose.runOnUiThread {
            val at = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEachIndexed { step, action ->
                val event = MotionEvent.obtain(at, at + step * TAP_MILLIS, action, x, y, 0)
                over.dispatchTouchEvent(event)
                event.recycle()
            }
        }
    }

    /** Every node a finger can reach now: those in the topmost touch-modal window and above it. */
    private fun reachableNodes(): List<SemanticsNode> {
        val order = windowOrder()
        val from = topModal(order)
        return nodes().filter { order.indexOf(windowOf(it)) >= from }
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
        fun answering(pool: List<SemanticsNode>): List<SemanticsNode> {
            val able = pool.filter { it.config.getOrNull(action) != null }
            val exact = able.filter { node -> node.readableText().any { it.trim() == wanted } }
            return exact.ifEmpty {
                if (wanted == UNLABELLED) {
                    able.filter { node -> node.readableText().none { it.isNotBlank() } }
                } else {
                    able.filter { node -> node.readableText().any { it.trim().startsWith(wanted) } }
                }
            }
        }
        // What a finger can reach answers first: with a dialog open, "Delete" is the dialog's, not
        // the one on the screen beneath it. Only when nothing reachable answers is what lies beneath
        // looked at — and a tap aimed there lands on the open window instead (see [act]).
        val candidates = answering(reachableNodes()).ifEmpty { answering(nodes()) }
        return when (candidates.size) {
            1 -> candidates.single()
            0 -> throw NothingSaysThat(
                "nothing on this screen can be ${past(verb)} as \"$label\". What is here: " +
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

        /** Far enough outside any window to be outside it, past any touch slop. */
        private const val FAR_OUTSIDE = -10_000f

        /** How long the finger stays down for a tap. */
        private const val TAP_MILLIS = 50L
    }
}
