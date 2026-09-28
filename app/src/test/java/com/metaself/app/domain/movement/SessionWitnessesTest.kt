package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * D92: overlapping workouts are witnesses of one session. Every figure and time is invented; a time is
 * minutes after an invented base ([at]).
 */
class SessionWitnessesTest {

    private fun combine(vararg workouts: Workout, splits: Set<SessionSplit> = emptySet()) =
        SessionWitnesses.combine(workouts.toList(), splits)

    // --- Which copies are one session -------------------------------------------------------------

    @Test
    fun `a session wholly inside a longer one is one session with both as witnesses`() {
        val sessions = combine(aSyncedWorkout(id = 1, from = 0, minutes = 60), aSyncedWorkout(id = 2, from = 10, minutes = 20))

        assertThat(sessions).hasSize(1)
        assertThat(sessions.single().witnessIds).containsExactly(1L, 2L).inOrder()
        assertThat(sessions.single().alsoRecordedBy).isEqualTo(1)
    }

    @Test
    fun `an overlap of exactly half the shorter session is one session`() {
        // 0–60 and 40–80: 20 minutes shared, the shorter is 40.
        val sessions = combine(aSyncedWorkout(id = 1, from = 0, minutes = 60), aSyncedWorkout(id = 2, from = 40, minutes = 40))

        assertThat(sessions).hasSize(1)
    }

    @Test
    fun `an overlap just under half the shorter session leaves two sessions`() {
        // 0–60 and 41–81: 19 minutes shared, the shorter is 40.
        val sessions = combine(aSyncedWorkout(id = 1, from = 0, minutes = 60), aSyncedWorkout(id = 2, from = 41, minutes = 40))

        assertThat(sessions.map { it.id }).containsExactly(1L, 2L).inOrder()
        assertThat(sessions.all { it.witnesses.isEmpty() }).isTrue()
    }

    @Test
    fun `sessions that only touch end to start are two`() {
        val sessions = combine(aSyncedWorkout(id = 1, from = 0, minutes = 30), aSyncedWorkout(id = 2, from = 30, minutes = 30))

        assertThat(sessions).hasSize(2)
    }

    @Test
    fun `grouping is transitive, so a chain of overlaps is one session`() {
        // A 0–40 and C 60–100 do not overlap; B 20–80 overlaps each by half of itself or more of them.
        val sessions = combine(
            aSyncedWorkout(id = 1, from = 0, minutes = 40),
            aSyncedWorkout(id = 2, from = 20, minutes = 60),
            aSyncedWorkout(id = 3, from = 60, minutes = 40),
        )

        assertThat(sessions).hasSize(1)
        assertThat(sessions.single().witnessIds).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun `a pair the owner split stays two sessions whatever their overlap`() {
        val sessions = combine(
            aSyncedWorkout(id = 1, from = 0, minutes = 60),
            aSyncedWorkout(id = 2, from = 0, minutes = 60),
            splits = setOf(SessionSplit.of(2, 1)),
        )

        assertThat(sessions.map { it.id }).containsExactly(1L, 2L).inOrder()
    }

    @Test
    fun `a third witness overlapping both halves of a split never joins them again`() {
        val sessions = combine(
            aSyncedWorkout(id = 1, from = 0, minutes = 60),
            aSyncedWorkout(id = 2, from = 0, minutes = 60),
            aSyncedWorkout(id = 3, from = 10, minutes = 40),
            splits = setOf(SessionSplit.of(1, 2)),
        )

        assertThat(sessions).hasSize(2)
        // Pairs are joined in id order: 1 with 3 comes before 2 with 3.
        assertThat(sessions.first { 3L in it.witnessIds }.witnessIds).containsExactly(1L, 3L)
        assertThat(sessions.first { 2L in it.witnessIds }.witnesses).isEmpty()
    }

    @Test
    fun `a hidden workout is never a witness and passes through as it is`() {
        val hidden = aSyncedWorkout(id = 2, from = 0, minutes = 60, hidden = true)

        val sessions = combine(aSyncedWorkout(id = 1, from = 0, minutes = 60), hidden)

        assertThat(sessions).hasSize(2)
        assertThat(sessions).contains(hidden)
    }

    @Test
    fun `a walk that does not count is never a witness and passes through as it is`() {
        val uncounted = aSyncedWorkout(id = 2, from = 0, minutes = 60, counted = false)

        val sessions = combine(aSyncedWorkout(id = 1, from = 0, minutes = 60), uncounted)

        assertThat(sessions).hasSize(2)
        assertThat(sessions).contains(uncounted)
    }

    @Test
    fun `a session recorded once comes back exactly as given`() {
        val alone = aSyncedWorkout(id = 1, from = 0, minutes = 60, distanceM = 5_000)

        assertThat(combine(alone)).containsExactly(alone)
        assertThat(alone.witnessIds).containsExactly(1L)
        assertThat(alone.alsoRecordedBy).isEqualTo(0)
        assertThat(alone.asStored).isSameInstanceAs(alone)
    }

    @Test
    fun `sessions come back in the order they started`() {
        val sessions = combine(
            aSyncedWorkout(id = 1, from = 200, minutes = 30),
            aSyncedWorkout(id = 2, from = 0, minutes = 30),
            aSyncedWorkout(id = 3, from = 100, minutes = 30),
        )

        assertThat(sessions.map { it.id }).containsExactly(2L, 3L, 1L).inOrder()
    }

    @Test
    fun `a zero-minute session is a witness only when its moment lies inside the other`() {
        val inside = combine(aSyncedWorkout(id = 1, from = 0, minutes = 60), aSyncedWorkout(id = 2, from = 30, minutes = 0))
        val outside = combine(aSyncedWorkout(id = 1, from = 0, minutes = 60), aSyncedWorkout(id = 2, from = 60, minutes = 0))

        assertThat(inside).hasSize(1)
        assertThat(outside).hasSize(2)
    }

    @Test
    fun `a split is stored lower id first`() {
        assertThat(SessionSplit.of(9, 4)).isEqualTo(SessionSplit(4, 9))
    }

    // --- Which witness leads ----------------------------------------------------------------------

    private fun typed(id: Long, from: Int = 0, minutes: Int = 60) =
        aTypedWorkout(id = id, startedAtMillis = at(from), minutes = minutes)

    private fun fromFile(id: Long, from: Int = 0, minutes: Int = 60, distanceM: Int? = null) =
        typed(id, from, minutes).copy(
            kind = WorkoutKind.WALK, distanceM = distanceM,
            distanceSource = WorkoutFigureSource.FILE, energyKcal = null, energySource = EnergySource.NONE,
        )

    @Test
    fun `the lead is a synced session with heart rate, then synced, then from a file, then typed`() {
        val session = combine(
            typed(id = 3, minutes = 90),
            fromFile(id = 2, minutes = 90),
            aSyncedWorkout(id = 1, from = 0, minutes = 90),
            aSyncedWorkout(id = 4, from = 10, minutes = 50, avgHeartRate = 120),
        ).single()

        assertThat(session.witnessIds).containsExactly(4L, 1L, 2L, 3L).inOrder()
        assertThat(session.id).isEqualTo(4L)
        assertThat(session.startedAtMillis).isEqualTo(at(10))
        assertThat(session.durationMinutes).isEqualTo(50)
        assertThat(session.source).isEqualTo(WorkoutSource.SYNCED)
    }

    @Test
    fun `between two of the same standing the longer leads, then the lower id`() {
        val longer = combine(aSyncedWorkout(id = 1, minutes = 40), aSyncedWorkout(id = 2, minutes = 60)).single()
        val same = combine(aSyncedWorkout(id = 5, minutes = 60), aSyncedWorkout(id = 2, minutes = 60)).single()

        assertThat(longer.id).isEqualTo(2L)
        assertThat(same.id).isEqualTo(2L)
    }

    @Test
    fun `a combined session is visible and counted`() {
        val session = combine(aSyncedWorkout(id = 1), typed(id = 2)).single()

        assertThat(session.hidden).isFalse()
        assertThat(session.counted).isTrue()
    }

    // --- Each figure from the witness that knows it best ------------------------------------------

    @Test
    fun `the kind is the lead's unless it is other, then the first specific one with its title`() {
        val lead = aSyncedWorkout(id = 1, kind = WorkoutKind.OTHER, title = "Workout")
        val run = aSyncedWorkout(id = 2, minutes = 50, kind = WorkoutKind.RUN, title = "Running")

        val fromOther = combine(lead, run).single()
        val fromUnknown = combine(lead.copy(kind = WorkoutKind.UNRECOGNISED), run).single()
        val specific = combine(lead.copy(kind = WorkoutKind.WALK, title = "Walking"), run).single()

        assertThat(fromOther.kind).isEqualTo(WorkoutKind.RUN)
        assertThat(fromOther.title).isEqualTo("Running")
        assertThat(fromUnknown.kind).isEqualTo(WorkoutKind.RUN)
        assertThat(specific.kind).isEqualTo(WorkoutKind.WALK)
        assertThat(specific.title).isEqualTo("Walking")
    }

    @Test
    fun `the distance the owner typed comes first, marked as typed`() {
        val session = combine(
            aSyncedWorkout(id = 1, distanceM = 3_100, ownDistance = true),
            fromFile(id = 2, distanceM = 3_400),
            typed(id = 3).copy(distanceM = 3_000),
        ).single()

        assertThat(session.distanceM).isEqualTo(3_000)
        assertThat(session.distanceSource).isEqualTo(WorkoutFigureSource.TYPED)
    }

    @Test
    fun `a file's distance comes before any app's`() {
        val session = combine(aSyncedWorkout(id = 1, distanceM = 3_100, ownDistance = true), fromFile(id = 2, distanceM = 3_200)).single()

        assertThat(session.distanceM).isEqualTo(3_200)
        assertThat(session.distanceSource).isEqualTo(WorkoutFigureSource.FILE)
    }

    @Test
    fun `a witness whose own app recorded distance comes before the total over the session`() {
        val session = combine(
            aSyncedWorkout(id = 1, distanceM = 3_000, avgHeartRate = 120),
            aSyncedWorkout(id = 2, distanceM = 3_100, ownDistance = true),
        ).single()

        assertThat(session.id).isEqualTo(1L)
        assertThat(session.distanceM).isEqualTo(3_100)
        assertThat(session.distanceSource).isNull()
    }

    @Test
    fun `with only totals the lead's comes first, and any witness's when the lead has none`() {
        val leadHas = combine(aSyncedWorkout(id = 1, distanceM = 3_000), aSyncedWorkout(id = 2, minutes = 50, distanceM = 3_100)).single()
        val otherHas = combine(aSyncedWorkout(id = 1), aSyncedWorkout(id = 2, minutes = 50, distanceM = 3_100)).single()
        val none = combine(aSyncedWorkout(id = 1), typed(id = 2)).single()

        assertThat(leadHas.distanceM).isEqualTo(3_000)
        assertThat(otherHas.distanceM).isEqualTo(3_100)
        assertThat(none.distanceM).isNull()
        assertThat(none.distanceSource).isNull()
    }

    @Test
    fun `steps are a file's first, else any witness's`() {
        val file = combine(
            aSyncedWorkout(id = 1, steps = 3_000, stepsSource = WorkoutFigureSource.TYPED),
            aSyncedWorkout(id = 2, minutes = 50, steps = 4_000, stepsSource = WorkoutFigureSource.FILE),
        ).single()
        val any = combine(aSyncedWorkout(id = 1), aSyncedWorkout(id = 2, minutes = 50, steps = 3_000, stepsSource = WorkoutFigureSource.TYPED)).single()

        assertThat(file.steps).isEqualTo(4_000)
        assertThat(file.stepsSource).isEqualTo(WorkoutFigureSource.FILE)
        assertThat(any.steps).isEqualTo(3_000)
        assertThat(any.stepsSource).isEqualTo(WorkoutFigureSource.TYPED)
    }

    @Test
    fun `the heart rate is the lead's, all of it from that one witness`() {
        val lead = aSyncedWorkout(id = 1, avgHeartRate = 120)
            .copy(maxHeartRate = 150, zoneSeconds = listOf(600, 1200, 1200, 600, 0), zoneMaxSource = "ESTIMATED")
        val other = aSyncedWorkout(id = 2, minutes = 50, avgHeartRate = 110).copy(maxHeartRate = 160)

        val session = combine(lead, other).single()

        assertThat(session.avgHeartRate).isEqualTo(120)
        assertThat(session.maxHeartRate).isEqualTo(150)
        assertThat(session.zoneSeconds).containsExactly(600, 1200, 1200, 600, 0).inOrder()
        assertThat(session.zoneMaxSource).isEqualTo("ESTIMATED")
    }

    @Test
    fun `with no heart rate on the lead it is the first witness's that has one`() {
        val lead = aSyncedWorkout(id = 1)
        val other = fromFile(id = 2).copy(avgHeartRate = 110, maxHeartRate = 130, zoneMaxSource = "ESTIMATED")

        val session = combine(lead, other).single()

        assertThat(session.id).isEqualTo(1L)
        assertThat(session.avgHeartRate).isEqualTo(110)
        assertThat(session.maxHeartRate).isEqualTo(130)
    }

    @Test
    fun `calories are the band's, then a file's, then typed, then an estimate, each with its source`() {
        val band = aSyncedWorkout(id = 1, energyKcal = 300, energySource = EnergySource.BAND)
        val none = aSyncedWorkout(id = 1)
        val file = fromFile(id = 2).copy(energyKcal = 320, energySource = EnergySource.FILE)
        val typedKcal = typed(id = 3).copy(energyKcal = 280, energySource = EnergySource.TYPED)
        val estimate = typed(id = 4)

        assertThat(combine(estimate, typedKcal, file, band).single().let { it.energyKcal to it.energySource })
            .isEqualTo(300 to EnergySource.BAND)
        assertThat(combine(estimate, typedKcal, file, none).single().let { it.energyKcal to it.energySource })
            .isEqualTo(320 to EnergySource.FILE)
        assertThat(combine(estimate, typedKcal, none).single().let { it.energyKcal to it.energySource })
            .isEqualTo(280 to EnergySource.TYPED)
        assertThat(combine(estimate, none).single().let { it.energyKcal to it.energySource })
            .isEqualTo(150 to EnergySource.MET_ESTIMATE)
        assertThat(combine(none, aSyncedWorkout(id = 5, minutes = 50)).single().let { it.energyKcal to it.energySource })
            .isEqualTo(null to EnergySource.NONE)
    }

    @Test
    fun `the felt effort and the note come from the typed witness when the lead has none`() {
        val session = combine(aSyncedWorkout(id = 1), typed(id = 2).copy(note = "a note")).single()

        assertThat(session.effort).isEqualTo(Effort.MODERATE)
        assertThat(session.note).isEqualTo("a note")
    }

    // --- Disagreement ------------------------------------------------------------------------------

    @Test
    fun `distances more than fifteen percent apart are both shown, with who said the other`() {
        val session = combine(fromFile(id = 2, distanceM = 3_400), aSyncedWorkout(id = 1, distanceM = 2_800)).single()

        assertThat(session.distanceM).isEqualTo(3_400)
        assertThat(session.otherDistance).isEqualTo(OtherDistance(2_800, DistanceWitness.APP))
    }

    @Test
    fun `distances exactly fifteen percent apart are not`() {
        val session = combine(fromFile(id = 2, distanceM = 4_000), aSyncedWorkout(id = 1, distanceM = 3_400)).single()

        assertThat(session.otherDistance).isNull()
    }

    @Test
    fun `with several disagreeing the widest gap is shown, and a typed or file figure says so`() {
        val widest = combine(
            typed(id = 3).copy(distanceM = 4_000),
            aSyncedWorkout(id = 1, distanceM = 3_000),
            fromFile(id = 2, distanceM = 2_000),
        ).single()

        assertThat(widest.distanceM).isEqualTo(4_000)
        assertThat(widest.otherDistance).isEqualTo(OtherDistance(2_000, DistanceWitness.FILE))
        val againstTyped = combine(fromFile(id = 2, distanceM = 4_000), typed(id = 3).copy(distanceM = 3_000)).single()
        assertThat(againstTyped.distanceM).isEqualTo(3_000)
        assertThat(againstTyped.otherDistance).isEqualTo(OtherDistance(4_000, DistanceWitness.FILE))
    }

    @Test
    fun `a second typed distance that disagrees is said as typed`() {
        val session = combine(typed(id = 3).copy(distanceM = 4_000), typed(id = 4, minutes = 40).copy(distanceM = 3_000)).single()

        assertThat(session.distanceM).isEqualTo(4_000)
        assertThat(session.distanceSource).isEqualTo(WorkoutFigureSource.TYPED)
        assertThat(session.otherDistance).isEqualTo(OtherDistance(3_000, DistanceWitness.TYPED))
    }

    // --- Pace --------------------------------------------------------------------------------------

    @Test
    fun `a distance from another witness is paced over that witness's minutes`() {
        // The lead is 60 minutes with no distance; the file's 5 km took 50 minutes: 10:00 a km.
        val session = combine(aSyncedWorkout(id = 1, minutes = 60, kind = WorkoutKind.RUN), fromFile(id = 2, minutes = 50, distanceM = 5_000)).single()

        assertThat(session.durationMinutes).isEqualTo(60)
        assertThat(session.paceSecondsPerKm).isEqualTo(600)
    }

    @Test
    fun `the lead's own distance is paced over its own minutes`() {
        val session = combine(aSyncedWorkout(id = 1, minutes = 50, distanceM = 5_000), aSyncedWorkout(id = 2, minutes = 40)).single()

        assertThat(session.paceSecondsPerKm).isEqualTo(600)
        assertThat(session.distanceMinutes).isNull()
    }

    // --- Put back together ---------------------------------------------------------------------

    @Test
    fun `sessions split from an overlapping partner carry the split, so it can be undone`() {
        val split = SessionSplit(1, 2)
        val sessions = combine(aSyncedWorkout(id = 1), aSyncedWorkout(id = 2, minutes = 50), splits = setOf(split))

        assertThat(sessions.map { it.splits }).containsExactly(listOf(split), listOf(split))
    }

    @Test
    fun `a split between sessions that no longer overlap is not carried, and one still apart is carried by both sides`() {
        val apart = combine(aSyncedWorkout(id = 1, from = 0, minutes = 30), aSyncedWorkout(id = 2, from = 100, minutes = 30), splits = setOf(SessionSplit(1, 2)))
        val chained = combine(
            aSyncedWorkout(id = 1),
            aSyncedWorkout(id = 2, minutes = 50),
            aSyncedWorkout(id = 3, minutes = 40),
            splits = setOf(SessionSplit(1, 2)),
        )

        assertThat(apart.flatMap { it.splits }).isEmpty()
        assertThat(chained.single { 2L in it.witnessIds }.splits).containsExactly(SessionSplit(1, 2))
        assertThat(chained.single { 1L in it.witnessIds }.splits).containsExactly(SessionSplit(1, 2))
    }

    // --- A typed witness, splitting and hiding ---------------------------------------------------

    @Test
    fun `a typed witness under a synced lead stays as it was stored`() {
        val lead = aSyncedWorkout(id = 1, distanceM = 3_100)
        val mine = typed(id = 2).copy(distanceM = 3_000)

        val session = combine(lead, mine).single()

        assertThat(session.asStored).isEqualTo(lead)
        assertThat(session.witnesses).containsExactly(lead, mine).inOrder()
        assertThat(session.distanceM).isEqualTo(3_000)
    }

    @Test
    fun `splitting a session parts the lead from each other witness`() {
        val session = combine(aSyncedWorkout(id = 5), aSyncedWorkout(id = 2, minutes = 50), typed(id = 9)).single()

        assertThat(SessionWitnesses.splitsOf(session)).containsExactly(SessionSplit(2, 5), SessionSplit(5, 9))
        assertThat(SessionWitnesses.splitsOf(aSyncedWorkout(id = 1))).isEmpty()
    }

    @Test
    fun `hiding a session hides every synced witness and no typed one`() {
        val session = combine(aSyncedWorkout(id = 5), aSyncedWorkout(id = 2, minutes = 50), typed(id = 9)).single()

        assertThat(SessionWitnesses.toHide(session)).containsExactly(5L, 2L)
        assertThat(SessionWitnesses.toHide(aSyncedWorkout(id = 1))).containsExactly(1L)
        assertThat(SessionWitnesses.toHide(typed(id = 3))).isEmpty()
    }
}
