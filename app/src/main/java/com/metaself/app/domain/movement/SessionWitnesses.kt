package com.metaself.app.domain.movement

import kotlin.math.max
import kotlin.math.min

/**
 * A pair of workouts the owner said are two sessions (D92): never grouped again, whatever their
 * overlap. Stored lower id first, so each pair has one spelling.
 */
data class SessionSplit(val firstId: Long, val secondId: Long) {
    init {
        require(firstId < secondId) { "a split is stored lower id first: $firstId, $secondId" }
    }

    companion object {
        fun of(a: Long, b: Long): SessionSplit = SessionSplit(min(a, b), max(a, b))
    }
}

/** Who gave a witness's distance, as the session's line says it (D92). */
enum class DistanceWitness { APP, FILE, TYPED }

/** A witness's distance that disagrees with the session's by more than [SessionWitnesses.DISAGREEMENT]. */
data class OtherDistance(val metres: Int, val saidBy: DistanceWitness)

/**
 * D92: overlapping workouts are witnesses of one session. Pure; worked out every time the record is
 * read, never written — every witness stays stored as it was.
 *
 * **Which copies are one session.** Two visible, counted workouts whose times overlap by at least half
 * of the shorter one; a zero-minute one only when its moment lies inside the other. Grouping is
 * transitive, as sleep's is (D68). A pair the owner split ([SessionSplit]) is never in one session:
 * pairs are joined in (lower id, higher id) order, and a join that would put a split pair together is
 * skipped — so a third workout overlapping both halves of a split joins the first it meets, never both.
 *
 * A hidden workout (hidden on purpose) and a walk that does not count (D81) witness nothing; they pass
 * through unchanged, as does a session recorded once.
 */
object SessionWitnesses {

    /** A choice, not a measurement: how far apart two witnesses' distances may be before both are shown. */
    const val DISAGREEMENT = 0.15

    /**
     * [workouts] as sessions, in the order they started (then by id). A session of several witnesses is
     * the lead's id, day, start and minutes, with [Workout.witnesses] holding every witness as stored.
     */
    fun combine(workouts: List<Workout>, splits: Collection<SessionSplit>): List<Workout> {
        val splitSet = splits.toSet()
        val (candidates, aside) = workouts.partition { !it.hidden && it.counted }
        val byStart = candidates.sortedWith(compareBy<Workout>({ it.startedAtMillis }, { it.id }))

        val edges = mutableListOf<Pair<Workout, Workout>>()
        byStart.forEachIndexed { i, a ->
            var j = i + 1
            while (j < byStart.size && byStart[j].startedAtMillis <= endOf(a)) {
                val b = byStart[j]
                if (overlaps(a, b)) edges += if (a.id < b.id) a to b else b to a
                j++
            }
        }
        edges.sortWith(compareBy<Pair<Workout, Workout>>({ it.first.id }, { it.second.id }))

        val groupOf = HashMap<Long, MutableSet<Workout>>()
        byStart.forEach { groupOf[it.id] = mutableSetOf(it) }
        edges.forEach { (a, b) ->
            val first = groupOf.getValue(a.id)
            val second = groupOf.getValue(b.id)
            if (first === second) return@forEach
            val apart = first.any { x -> second.any { y -> SessionSplit.of(x.id, y.id) in splitSet } }
            if (apart) return@forEach
            first.addAll(second)
            second.forEach { groupOf[it.id] = first }
        }

        val groups = mutableListOf<MutableSet<Workout>>()
        groupOf.values.forEach { group -> if (groups.none { it === group }) groups += group }
        val sessions = groups.map { group ->
            if (group.size == 1) group.single() else session(group.toList())
        }
        return (sessions + aside).sortedWith(compareBy<Workout>({ it.startedAtMillis }, { it.id }))
    }

    /** At least half of the shorter one shared; a zero-minute one only inside the other. */
    fun overlaps(a: Workout, b: Workout): Boolean {
        val shorter = min(lengthOf(a), lengthOf(b))
        if (shorter == 0L) {
            val (moment, other) = if (lengthOf(a) == 0L) a to b else b to a
            return moment.startedAtMillis >= other.startedAtMillis && moment.startedAtMillis < endOf(other)
        }
        val shared = min(endOf(a), endOf(b)) - max(a.startedAtMillis, b.startedAtMillis)
        return shared > 0 && shared * 2 >= shorter
    }

    /**
     * The lead first: a synced session carrying a heart-rate average; another synced session; a session
     * added from a file; a typed one. Ties go to the longer, then the lower id.
     */
    val LEAD_ORDER: Comparator<Workout> = compareBy<Workout> { rank(it) }
        .thenByDescending { it.durationMinutes }
        .thenBy { it.id }

    private fun rank(workout: Workout): Int = when {
        workout.source == WorkoutSource.SYNCED && workout.avgHeartRate != null -> 0
        workout.source == WorkoutSource.SYNCED -> 1
        workout.fromFile -> 2
        else -> 3
    }

    private fun session(group: List<Workout>): Workout {
        val ordered = group.sortedWith(LEAD_ORDER)
        val lead = ordered.first()
        return lead.copy(witnesses = ordered)
    }

    /** Whole minutes, as stored: there is no end column. */
    private fun lengthOf(workout: Workout): Long = workout.durationMinutes * MINUTE

    private fun endOf(workout: Workout): Long = workout.startedAtMillis + lengthOf(workout)

    private const val MINUTE = 60_000L
}
