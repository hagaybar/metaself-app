package com.metaself.app.domain.movement

import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.abs
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
 * through unchanged, as does a session recorded once. A session split from one it still overlaps
 * carries that split ([Workout.splits]), so it can be put back together.
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
        // Splits between workouts that still overlap: what "Put back together" can remove.
        val standing = mutableSetOf<SessionSplit>()
        byStart.forEachIndexed { i, a ->
            var j = i + 1
            while (j < byStart.size && byStart[j].startedAtMillis <= endOf(a)) {
                val b = byStart[j]
                if (overlaps(a, b)) {
                    edges += if (a.id < b.id) a to b else b to a
                    SessionSplit.of(a.id, b.id).takeIf { it in splitSet }?.let { standing += it }
                }
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

        val groups = Collections.newSetFromMap(IdentityHashMap<MutableSet<Workout>, Boolean>())
        groups.addAll(groupOf.values)
        val sessions = groups.map { group ->
            val session = if (group.size == 1) group.single() else session(group.toList())
            val ids = group.mapTo(HashSet()) { it.id }
            val itsSplits = standing.filter { it.firstId in ids || it.secondId in ids }
                .sortedWith(compareBy<SessionSplit>({ it.firstId }, { it.secondId }))
            if (itsSplits.isEmpty()) session else session.copy(splits = itsSplits)
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

    /** The pairs "These are two sessions" stores for [session]: its lead against each other witness. */
    fun splitsOf(session: Workout): List<SessionSplit> =
        session.witnesses.drop(1).map { SessionSplit.of(session.id, it.id) }

    /**
     * What Hide on [session] hides: every synced witness. A typed witness is never hidden — it is
     * deleted, on its own line after a split. (No Hide button exists yet; this is the rule for it.)
     */
    fun toHide(session: Workout): List<Long> =
        session.witnesses.ifEmpty { listOf(session) }.filter { it.source == WorkoutSource.SYNCED }.map { it.id }

    /**
     * One session from [group]: the lead's identity — id, day, start, minutes, source — and each figure
     * from the witness that knows it best, with its source (D4, D69). Every witness is kept, as stored,
     * in [Workout.witnesses], lead first.
     */
    private fun session(group: List<Workout>): Workout {
        val ordered = group.sortedWith(LEAD_ORDER)
        val lead = ordered.first()
        val kindFrom = ordered.firstOrNull { it.kind.isSpecific } ?: lead
        val distanceFrom = ordered.firstOrNull(::typedDistance)
            ?: ordered.firstOrNull { it.distanceM != null && it.distanceSource == WorkoutFigureSource.FILE }
            ?: ordered.firstOrNull { it.distanceM != null && it.source == WorkoutSource.SYNCED && it.ownDistance }
            ?: ordered.firstOrNull { it.distanceM != null }
        val stepsFrom = ordered.firstOrNull { it.steps != null && it.stepsSource == WorkoutFigureSource.FILE }
            ?: ordered.firstOrNull { it.steps != null }
        val heartFrom = ordered.firstOrNull { it.avgHeartRate != null } ?: lead
        val energyFrom = ordered.filter { it.energyKcal != null && it.energySource in ENERGY_ORDER }
            .minByOrNull { ENERGY_ORDER.indexOf(it.energySource) }

        return lead.copy(
            kind = kindFrom.kind,
            title = kindFrom.title,
            distanceM = distanceFrom?.distanceM,
            distanceSource = distanceFrom?.let { if (typedDistance(it)) WorkoutFigureSource.TYPED else it.distanceSource },
            steps = stepsFrom?.steps,
            stepsSource = stepsFrom?.stepsSource,
            energyKcal = energyFrom?.energyKcal,
            energySource = energyFrom?.energySource ?: EnergySource.NONE,
            avgHeartRate = heartFrom.avgHeartRate,
            maxHeartRate = heartFrom.maxHeartRate,
            zoneSeconds = heartFrom.zoneSeconds,
            zoneMaxSource = heartFrom.zoneMaxSource,
            effort = ordered.firstNotNullOfOrNull { it.effort },
            note = ordered.firstNotNullOfOrNull { it.note },
            hidden = false,
            counted = true,
            witnesses = ordered,
            otherDistance = distanceFrom?.let { chosen -> otherDistance(chosen, ordered) },
            // Paced over the minutes the distance was measured in, never another witness's.
            distanceMinutes = distanceFrom?.takeIf { it !== lead }?.durationMinutes,
        )
    }

    /**
     * The widest-apart other distance, when it is more than [DISAGREEMENT] of the larger of the two;
     * the first in lead order on a tie.
     */
    private fun otherDistance(chosen: Workout, ordered: List<Workout>): OtherDistance? {
        val metres = chosen.distanceM ?: return null
        return ordered.asSequence()
            .filter { it !== chosen }
            .mapNotNull { witness -> witness.distanceM?.let { witness to it } }
            .filter { (_, other) -> disagree(metres, other) }
            .maxByOrNull { (_, other) -> abs(other - metres) }
            ?.let { (witness, other) -> OtherDistance(other, saidBy(witness)) }
    }

    /** More than 15 % of the larger apart, in whole numbers: 20 × gap > 3 × larger. */
    private fun disagree(a: Int, b: Int): Boolean = abs(a - b).toLong() * 20 > max(a, b).toLong() * 3

    private fun saidBy(witness: Workout): DistanceWitness = when {
        typedDistance(witness) -> DistanceWitness.TYPED
        witness.distanceSource == WorkoutFigureSource.FILE -> DistanceWitness.FILE
        else -> DistanceWitness.APP
    }

    /** A distance the owner typed: marked TYPED over a file's, or a typed workout's own (stored with no source). */
    private fun typedDistance(workout: Workout): Boolean = workout.distanceM != null &&
        (workout.distanceSource == WorkoutFigureSource.TYPED ||
            (workout.source == WorkoutSource.TYPED && workout.distanceSource == null))

    /** Band, file, typed, an estimate — the first available wins. */
    private val ENERGY_ORDER = listOf(EnergySource.BAND, EnergySource.FILE, EnergySource.TYPED, EnergySource.MET_ESTIMATE)

    /** Says what was done: neither "other" nor a kind this version does not know does. */
    private val WorkoutKind.isSpecific: Boolean
        get() = this != WorkoutKind.OTHER && this != WorkoutKind.UNRECOGNISED

    /** Whole minutes, as stored: there is no end column. */
    private fun lengthOf(workout: Workout): Long = workout.durationMinutes * MINUTE

    private fun endOf(workout: Workout): Long = workout.startedAtMillis + lengthOf(workout)

    private const val MINUTE = 60_000L
}
