package com.metaself.app.domain.movement

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.StringReader
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

/**
 * Reads a TCX workout file into the session's totals (D82). Pure: no Android type, only the
 * `javax.xml` DOM API, so it is tested on the JVM. The parser behind that API is not the same one: the
 * JVM's is Xerces, Android ships its own. Nothing here relies on what only one of them does; the
 * DOCTYPE refusal below is made before either parser sees the text.
 *
 * **Tolerant.** Elements are matched by local name, so any namespace or prefix is read the same. Per
 * lap, and summed: `TotalTimeSeconds`, `DistanceMeters`, `Calories`, `Steps` (not TCX; a band's app
 * adds it, and it is looked for anywhere under the lap but its track, extensions included), and heart
 * rate — the standard `AverageHeartRateBpm/Value`, or `HeartRateBpm` directly under the lap holding
 * either a `Value` or a plain number, as a band's app writes it — averaged by each lap's time. Only
 * a lap's own children are its totals, so a trackpoint's figures are never read. A lap figure that
 * does not parse is left out of its sum. Only the first activity is read.
 *
 * **Refused whole**, with a [WorkoutFileRefusal], when it is not XML (a DOCTYPE counts as not XML:
 * nothing is ever expanded or fetched), holds no activity, has no start, no lap time, or nothing a
 * session could gain. A document nested deeper than [MAX_DEPTH] elements is not a workout file, and
 * is refused as not XML before it is walked; a parser that runs out of stack or memory on one is a
 * refusal too, never a crash.
 */
object TcxReader {

    /** Far deeper than a TCX file goes: its deepest figure, a trackpoint's Extensions/TPX/Speed, is nine down. */
    const val MAX_DEPTH = 64

    fun read(text: String): TcxRead = try {
        readText(text)
    } catch (_: StackOverflowError) {
        refused(WorkoutFileRefusal.NOT_XML)
    } catch (_: OutOfMemoryError) {
        refused(WorkoutFileRefusal.NOT_XML)
    }

    private fun readText(text: String): TcxRead {
        val clean = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        if (clean.isEmpty() || clean.contains("<!DOCTYPE", ignoreCase = true)) return refused(WorkoutFileRefusal.NOT_XML)
        val root = parse(clean) ?: return refused(WorkoutFileRefusal.NOT_XML)
        if (deeperThan(root, MAX_DEPTH)) return refused(WorkoutFileRefusal.NOT_XML)

        val activity = descendants(root).firstOrNull { it.local == "Activity" }
            ?: return refused(WorkoutFileRefusal.NO_WORKOUT)
        val laps = children(activity).filter { it.local == "Lap" }
        val start = (children(activity).firstOrNull { it.local == "Id" }?.textContent?.let(::start))
            ?: laps.firstOrNull()?.getAttribute("StartTime")?.let(::start)
            ?: return refused(WorkoutFileRefusal.NO_START)

        val figures = laps.map(::lap)
        val seconds = figures.mapNotNull { it.seconds }.filter { it > 0 }.sum()
        if (seconds <= 0.0) return refused(WorkoutFileRefusal.NO_DURATION)

        // A total of zero is nothing to add: a session gains no measurement from it.
        val distance = figures.mapNotNull { it.metres }.sum().takeIf { it > 0 }
        val kcal = figures.mapNotNull { it.kcal }.sum().roundToInt().takeIf { it > 0 }
        val steps = figures.mapNotNull { it.steps }.sum().roundToInt().takeIf { it > 0 }
        if (distance == null && kcal == null && steps == null) return refused(WorkoutFileRefusal.NOTHING_TO_ADD)

        val timed = figures.filter { it.bpm != null && (it.seconds ?: 0.0) > 0 }
        val heartRate = timed.takeIf { it.isNotEmpty() }
            ?.let { laps -> laps.sumOf { it.bpm!! * it.seconds!! } / laps.sumOf { it.seconds!! } }
            ?.roundToInt()

        return TcxRead.Read(
            FileWorkout(
                writtenAt = start.first,
                instant = start.second?.toInstant(),
                seconds = seconds.roundToInt(),
                distanceM = distance,
                kcal = kcal,
                steps = steps,
                avgHeartRate = heartRate,
                sport = activity.getAttribute("Sport").takeIf { it.isNotBlank() },
            ),
        )
    }

    private class Lap(val seconds: Double?, val metres: Double?, val kcal: Double?, val steps: Double?, val bpm: Double?)

    private fun lap(lap: Element): Lap {
        val own = children(lap)
        fun figure(name: String) = own.firstOrNull { it.local == name }?.let { number(it.textContent) }
        val heart = own.firstOrNull { it.local == "AverageHeartRateBpm" || it.local == "HeartRateBpm" }
            ?.let { element -> children(element).firstOrNull { it.local == "Value" }?.textContent ?: element.textContent }
            ?.let(::number)
        val steps = withoutTrack(lap).firstOrNull { it.local == "Steps" }?.let { number(it.textContent) }
        return Lap(figure("TotalTimeSeconds"), figure("DistanceMeters"), figure("Calories"), steps, heart)
    }

    /** The wall clock as written, and the offset time when one was given. */
    private fun start(text: String): Pair<LocalDateTime, OffsetDateTime?>? {
        val written = text.trim().replace(' ', 'T')
        return try {
            val withOffset = OffsetDateTime.parse(written)
            withOffset.toLocalDateTime() to withOffset
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(written) to null
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    private fun number(text: String?): Double? = text?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }

    private fun parse(text: String): Element? = try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val builder = factory.newDocumentBuilder()
        builder.setErrorHandler(Quiet)
        builder.parse(InputSource(StringReader(text))).documentElement
    } catch (_: Exception) {
        null
    }

    /** Stops at the first error without printing it: an unreadable file is a refusal, not noise. */
    private object Quiet : ErrorHandler {
        override fun warning(exception: SAXParseException) = Unit
        override fun error(exception: SAXParseException) = throw exception
        override fun fatalError(exception: SAXParseException) = throw exception
    }

    /** Walked with a list, not recursion, so the depth being measured cannot overflow the stack. */
    private fun deeperThan(root: Element, limit: Int): Boolean {
        val waiting = ArrayDeque<Pair<Node, Int>>()
        waiting.addLast(root to 1)
        while (waiting.isNotEmpty()) {
            val (node, depth) = waiting.removeLast()
            if (depth > limit) return true
            var child = node.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) waiting.addLast(child to depth + 1)
                child = child.nextSibling
            }
        }
        return false
    }

    private val Element.local: String get() = localName ?: nodeName.substringAfter(':')

    private fun children(element: Element): List<Element> =
        (0 until element.childNodes.length).map { element.childNodes.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE }
            .map { it as Element }

    private fun descendants(element: Element): Sequence<Element> = sequence {
        for (child in children(element)) {
            yield(child)
            yieldAll(descendants(child))
        }
    }

    /** Every element under [lap] except those inside its track. */
    private fun withoutTrack(lap: Element): Sequence<Element> = sequence {
        for (child in children(lap)) {
            if (child.local == "Track") continue
            yield(child)
            yieldAll(withoutTrack(child))
        }
    }

    private fun refused(reason: WorkoutFileRefusal) = TcxRead.Refused(reason)
}
