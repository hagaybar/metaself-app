package com.metaself.app.ui

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import com.metaself.app.ui.theme.MetaSelfTheme
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/**
 * Renders a composable in Robolectric and returns every piece of text it drew.
 *
 * There is no Compose test rule here on purpose: this project's unit tests run on the JUnit 5
 * platform through the vintage engine, and `createComposeRule` expects JUnit 4's rule machinery.
 * Driving the activity directly is a few more lines and no framework argument.
 *
 * Both static text and the CONTENTS of text fields are returned. Compose exposes a field's value as
 * `EditableText` rather than `Text`, so a helper reading only the latter can see a form's labels and
 * not a single thing typed into it — which made every pre-filled value silently untestable.
 *
 * Call [dispose] in an `@After`, and know why it drains the main looper after destroying the
 * activity. Tearing down a composition posts work to the main looper (Compose's UI dispatcher
 * schedules its next frame there and marks itself "already scheduled"). If the test ends with that
 * work still queued, Robolectric's reset between tests throws it away, the flag stays set, and the
 * dispatcher never runs again in this JVM. Nothing here notices, because a render through this
 * helper never waits for idle; the damage surfaces only in a LATER test that uses
 * `createComposeRule` (a `ComposeSession`) and waits for idle, which then hangs and fails with
 * `AppNotIdleException` in a class that did nothing wrong. Draining the looper at teardown runs
 * that work while it still can.
 *
 * Rendering again with the same instance disposes the previous activity first, for the same
 * reason: an activity overwritten rather than destroyed would stay alive for the rest of the JVM.
 */
class ComposeRender {

    private var controller: ActivityController<ComponentActivity>? = null

    fun texts(
        heightPx: Int = PHONE_HEIGHT_PX,
        content: @Composable () -> Unit,
    ): List<String> {
        dispose()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            .also { controller = it }
            .get()
        activity.setContent { MetaSelfTheme { content() } }
        density = activity.resources.displayMetrics.density
        canvasWidthDp = (activity.resources.displayMetrics.widthPixels / density).toInt()

        val decor = activity.window.decorView
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(PHONE_WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        decor.layout(0, 0, PHONE_WIDTH_PX, heightPx)
        shadowOf(Looper.getMainLooper()).idle()

        lastNodes = semanticsNodes(decor)
        return lastNodes.flatMap { it.texts() }
    }

    /**
     * How far apart, vertically, the first nodes matching [firstPrefix] and [secondPrefix] are
     * after [content] has been laid out at phone size.
     *
     * This is the density measure that matters for a list: the pitch of one row, in the dp that a
     * real screen has a finite number of. Measuring a single node's own height would not catch the
     * padding and the buttons around it, which is where the space actually goes.
     */
    fun rowPitchDp(
        firstPrefix: String,
        secondPrefix: String,
        content: @Composable () -> Unit,
    ): Int {
        // Laid out on a canvas tall enough to place everything. A row's height is a property of the
        // row, not of what happens to fit in one screenful — and measuring on a phone-sized canvas
        // silently returned nonsense the moment the header grew past it: unplaced nodes report a
        // top of zero, so the "pitch" became the distance from the first item to nowhere.
        texts(heightPx = TALL_ENOUGH_FOR_ANYTHING, content = content)
        return kotlin.math.abs(topOf(secondPrefix) - topOf(firstPrefix)).toInt()
    }

    /**
     * Presses the button whose label starts with [prefix].
     *
     * Only useful AFTER [texts] has rendered something: like [rowPitchDp] and the private `topOf`,
     * this reads the nodes of the LAST render, and nothing else fills them. Clicking before
     * rendering reports "no node whose text starts with ..." — which reads like a missing button
     * and is really a missing render.
     *
     * A Material 3 button merges its label and its click action into one semantics node, so
     * matching on the visible words is enough to find the thing to press.
     */
    fun click(prefix: String) {
        val node = nodeStartingWith(prefix)
        node.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
            ?: error("the node whose text starts with \"$prefix\" has nothing to click")
    }

    /**
     * Presses the control whose spoken name is exactly [description].
     *
     * For a control that has no words on it, only a name for a screen reader — an icon button. Kept
     * apart from [click] rather than folded into it: [click] matches a prefix of VISIBLE text, and
     * letting it also match descriptions would let "Delete" silently find a bin icon named "Delete
     * Hummus" instead of the button a test meant. Exact, for the same reason.
     *
     * Reads the LAST render, so call [texts] first.
     */
    fun clickDescribed(description: String) {
        val node = lastNodes.firstOrNull { node ->
            description in node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        } ?: error("no node whose spoken name is \"$description\"")
        node.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
            ?: error("the node named \"$description\" has nothing to click")
    }

    /**
     * True when the node matching [firstPrefix] is drawn before the one matching [secondPrefix].
     *
     * Order, unlike [rowPitchDp]'s distance, is signed — which is the only thing that can say a
     * destructive button has been moved out of the path a thumb travels while searching.
     *
     * Deliberately NOT measured from `boundsInRoot`, the way [rowPitchDp] measures distance. Far
     * enough down a long scrolling screen this project's nodes come back unplaced, reporting a top
     * of zero, and a comparison of two zeros is a coin toss that passes half the time. Semantic
     * traversal order is the declaration order of two siblings in one column, which is exactly the
     * question being asked, and it survives whether the second one was laid out or not.
     *
     * Reads the LAST render, so call [texts] first.
     */
    fun isDrawnBefore(firstPrefix: String, secondPrefix: String): Boolean =
        indexOf(firstPrefix) < indexOf(secondPrefix)

    /**
     * Every piece of text on screen NOW, after whatever [click] set off has been drawn — without
     * rendering afresh, so state the composable was holding (a `remember`, a tapped offer) is
     * still there.
     *
     * A frame is let pass first: a click changes state, and the redraw it causes is posted for the
     * next frame rather than run on the spot.
     *
     * Reads the LAST render's activity, so call [texts] first.
     */
    fun textsAgain(): List<String> {
        val activity = controller?.get() ?: error("nothing has been rendered to read again")
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(FRAME_MS))
        lastNodes = semanticsNodes(activity.window.decorView)
        return lastNodes.flatMap { it.texts() }
    }

    /**
     * Where the node matching [prefix] ends, horizontally, in dp.
     *
     * For asking whether a control was laid out inside the space it was given. A layout that places
     * something past its own right edge does not fail, does not warn and does not move: it draws the
     * control clipped, which on a button is a label cut off mid-word. Nothing but a bounds reading
     * can see that happen.
     *
     * Reads the LAST render, so call [texts] first.
     */
    fun rightEdgeDp(prefix: String): Int =
        (nodeStartingWith(prefix).boundsInRoot.right / density).toInt()

    /**
     * Whether the control whose label starts with [prefix] can be pressed.
     *
     * Asked of the semantics, not of the colour: a render here cannot say how a control looks, only
     * whether it declares itself switched off.
     *
     * Reads the LAST render, so call [texts] first.
     */
    fun isEnabled(prefix: String): Boolean =
        !nodeStartingWith(prefix).config.contains(SemanticsProperties.Disabled)

    /**
     * How far down the node matching [prefix] starts, in dp.
     *
     * Two controls with the same top are on the same line; different tops mean one of them wrapped.
     * That is the only way to ask a wrapping layout whether it actually wrapped.
     *
     * Reads the LAST render, so call [texts] first.
     */
    fun topDp(prefix: String): Int =
        (nodeStartingWith(prefix).boundsInRoot.top / density).toInt()

    /**
     * How wide, in dp, the last render was really laid out — which is the device Robolectric was
     * given and not the width [texts] asks the decor for. See the note on `PHONE_WIDTH_PX`.
     *
     * A test that asks whether anything was drawn past the edge has to compare against this and not
     * against a number of its own, or it is asserting about a screen that was never rendered.
     */
    var canvasWidthDp: Int = 0
        private set

    private var density: Float = 1f

    private fun indexOf(prefix: String): Int =
        lastNodes.indexOf(nodeStartingWith(prefix))

    private fun topOf(prefix: String): Float = nodeStartingWith(prefix).boundsInRoot.top

    private fun nodeStartingWith(prefix: String): SemanticsNode = lastNodes.firstOrNull { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty()
            .any { it.text.startsWith(prefix) }
    } ?: error("no node whose text starts with \"$prefix\"")

    private var lastNodes: List<SemanticsNode> = emptyList()

    fun dispose() {
        controller?.pause()?.stop()?.destroy()
        controller = null
        // Not optional: see the class comment. Without it, work queued by the teardown is discarded
        // at the test boundary and a later test waiting for Compose to go idle waits forever.
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun SemanticsNode.texts(): List<String> =
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            listOfNotNull(config.getOrNull(SemanticsProperties.EditableText)?.text) +
            // From the design pass on, half of what this app says to a screen reader is in
            // descriptions rather than text — the back arrow, for one. A helper that cannot see
            // them cannot test accessibility at all.
            config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

    private fun semanticsNodes(view: View): List<SemanticsNode> = composeRoots(view).flatMap {
        it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true, skipDeactivatedNodes = true)
    }

    private fun composeRoots(view: View): List<ViewRootForTest> = when {
        view is ViewRootForTest -> listOf(view)
        view is ViewGroup -> (0 until view.childCount).flatMap { composeRoots(view.getChildAt(it)) }
        else -> emptyList()
    }

    private companion object {
        /**
         * **The width asked for here is not the width anything is laid out at.** Robolectric hands
         * the activity the device it has configured, and `decor.measure` cannot widen it: measured
         * at this value, the decor comes back 320 px on the default device, which is also where its
         * density of 1 comes from. A test that needs another width says so with a resource
         * qualifier — `@Config(qualifiers = "+w150dp")` — and gets it. This constant is left as it
         * was because every measurement in this project's render tests was taken through it.
         */
        const val PHONE_WIDTH_PX = 1080
        const val PHONE_HEIGHT_PX = 1920

        /** For measuring, not for looking: tall enough that nothing goes unplaced. */
        const val TALL_ENOUGH_FOR_ANYTHING = 20_000

        /** Long enough for one frame to come round, at any refresh rate a phone has. */
        const val FRAME_MS = 100L
    }
}
