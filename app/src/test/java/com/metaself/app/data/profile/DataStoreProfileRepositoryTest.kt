package com.metaself.app.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.goal.GoalArrival
import com.metaself.app.domain.milestone.Milestone
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.target.TargetRevision
import com.metaself.app.domain.window.EatingWindow
import com.metaself.app.domain.window.MeasuredWindow
import com.metaself.app.domain.window.WindowRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DataStoreProfileRepositoryTest {

    @Test
    fun `an untouched store holds no profile`(@TempDir dir: File) = runTest {
        val repository = DataStoreProfileRepository(storeIn(dir))
        assertThat(repository.profile.first()).isNull()
    }

    @Test
    fun `a saved profile reads back`(@TempDir dir: File) = runTest {
        val repository = DataStoreProfileRepository(storeIn(dir))
        val profile = aProfile(weightKg = 90.0, goal = Goal.lose(0.25))

        repository.save(profile)

        assertThat(repository.profile.first()).isEqualTo(profile)
    }

    @Test
    fun `saving again replaces what was there`(@TempDir dir: File) = runTest {
        val repository = DataStoreProfileRepository(storeIn(dir))
        repository.save(aProfile(weightKg = 90.0))
        repository.save(aProfile(weightKg = 85.0))

        assertThat(repository.profile.first()?.weightKg).isEqualTo(85.0)
    }

    @Test
    fun `with nothing stored there is no revision`(@TempDir dir: File) = runTest {
        val repository = DataStoreProfileRepository(storeIn(dir))
        assertThat(repository.revision.first()).isNull()
    }

    @Test
    fun `a saved revision reads back`(@TempDir dir: File) = runTest {
        val repository = DataStoreProfileRepository(storeIn(dir))
        val revision = TargetRevision(20_699L, trendKg = 79.2, kcal = 2050, previousKcal = 2090)

        repository.saveRevision(revision)

        assertThat(repository.revision.first()).isEqualTo(revision)
    }

    @Test
    fun `saving the profile does not destroy the revision`(@TempDir dir: File) = runTest {
        // The profile write used to clear the whole store, which would have taken the revision with
        // it — the target would quietly have stopped following the weight trend after any profile
        // edit.
        val repository = DataStoreProfileRepository(storeIn(dir))
        repository.saveRevision(TargetRevision(20_699L, 79.2, 2050, 2090))

        repository.save(aProfile(weightKg = 81.0))

        assertThat(repository.revision.first()).isNotNull()
        assertThat(repository.profile.first()?.weightKg).isEqualTo(81.0)
    }

    @Test
    fun `a new revision has not been seen`(@TempDir dir: File) = runTest {
        val repository = DataStoreProfileRepository(storeIn(dir))
        repository.saveRevision(TargetRevision(20_699L, 79.2, 2050, 2090))

        assertThat(repository.revisionSeen.first()).isFalse()
    }

    @Test
    fun `a notice stays dismissed until the next revision`(@TempDir dir: File) = runTest {
        val repository = DataStoreProfileRepository(storeIn(dir))
        repository.saveRevision(TargetRevision(20_699L, 79.2, 2050, 2090))
        repository.markRevisionSeen()
        assertThat(repository.revisionSeen.first()).isTrue()

        repository.saveRevision(TargetRevision(20_706L, 78.6, 2020, 2050))

        assertThat(repository.revisionSeen.first()).isFalse()
    }

    /**
     * The store clears only the keys the codec writes. A target omitted rather than blanked would
     * survive being removed, and the app would go on aiming at a weight the owner had cleared.
     */
    @Test
    fun `clearing a goal weight really clears it`(@TempDir dir: File) = runTest {
        val repository = DataStoreProfileRepository(storeIn(dir))
        repository.save(aProfile(goal = Goal.lose(0.5, targetKg = 75.0)))

        repository.save(aProfile(goal = Goal.lose(0.5)))

        assertThat(repository.profile.first()!!.goal.targetKg).isNull()
    }

    @Test
    fun `with nothing stored nobody has arrived anywhere`(@TempDir dir: File) = runTest {
        assertThat(DataStoreProfileRepository(storeIn(dir)).arrival.first()).isNull()
    }

    @Test
    fun `an arrival reads back`(@TempDir dir: File) = runTest {
        val repository = DataStoreProfileRepository(storeIn(dir))

        repository.saveArrival(GoalArrival(targetKg = 75.0, epochDay = 20_699))

        assertThat(repository.arrival.first()).isEqualTo(GoalArrival(75.0, 20_699))
    }

    /** The same hazard the revision had: a profile edit must not wipe the arrival beside it. */
    @Test
    fun `saving the profile does not destroy the arrival`(@TempDir dir: File) = runTest {
        val repository = DataStoreProfileRepository(storeIn(dir))
        repository.saveArrival(GoalArrival(targetKg = 75.0, epochDay = 20_699))

        repository.save(aProfile(weightKg = 77.0))

        assertThat(repository.arrival.first()).isEqualTo(GoalArrival(75.0, 20_699))
    }

    @Test
    fun `nothing has been celebrated in an untouched store`(@TempDir dir: File) = runTest {
        assertThat(DataStoreProfileRepository(storeIn(dir)).milestones.first()).isEmpty()
    }

    @Test
    fun `a recorded milestone reads back`(@TempDir dir: File) = runTest {
        val repository = DataStoreProfileRepository(storeIn(dir))

        repository.recordMilestones(mapOf(Milestone.firstKg to 20_699L))

        assertThat(repository.milestones.first()).containsExactly(Milestone.firstKg, 20_699L)
    }

    /** The same hazard the revision and the arrival had: a profile edit must not wipe these. */
    @Test
    fun `saving the profile does not destroy the milestones`(@TempDir dir: File) = runTest {
        val repository = DataStoreProfileRepository(storeIn(dir))
        repository.recordMilestones(mapOf(Milestone.firstKg to 20_699L))

        repository.save(aProfile(weightKg = 77.0))

        assertThat(repository.milestones.first()).containsExactly(Milestone.firstKg, 20_699L)
    }

    // --- the eating window, of either kind ---------------------------------------------------

    @Test
    fun `a ratio window reads back`(@TempDir dir: File) = runTest {
        val repository = DataStoreProfileRepository(storeIn(dir))
        val rule = WindowRule.Measured(MeasuredWindow(fastingHours = 16), fromEpochDay = 20_707)

        repository.addWindowRule(rule)

        assertThat(repository.windowRules.first()).containsExactly(rule)
    }

    /**
     * The one-rule-per-starting-day rule, now spanning both kinds.
     *
     * Choosing the other kind today is the owner saying which one he is keeping, not the start of a
     * history: it replaces today's rule rather than stacking a second one on the same day.
     */
    @Test
    fun `setting a window twice on one day is a correction, not a history`(
        @TempDir dir: File,
    ) = runTest {
        val repository = DataStoreProfileRepository(storeIn(dir))
        val ratio = WindowRule.Measured(MeasuredWindow(fastingHours = 16), fromEpochDay = 20_707)

        repository.addWindowRule(WindowRule.Fixed(EatingWindow(6, 20, fromEpochDay = 20_707)))
        repository.addWindowRule(ratio)

        assertThat(repository.windowRules.first()).containsExactly(ratio)
    }

    /** Yesterday's rule keeps governing the days it governed. Nothing reaches backwards. */
    @Test
    fun `a rule set today leaves an older one in place`(@TempDir dir: File) = runTest {
        val repository = DataStoreProfileRepository(storeIn(dir))
        val older = WindowRule.Fixed(EatingWindow(6, 20, fromEpochDay = 20_677))
        val today = WindowRule.Measured(MeasuredWindow(fastingHours = 16), fromEpochDay = 20_707)

        repository.addWindowRule(older)
        repository.addWindowRule(today)

        assertThat(repository.windowRules.first()).containsExactly(older, today).inOrder()
    }

    @Test
    fun `clearing removes both kinds`(@TempDir dir: File) = runTest {
        val repository = DataStoreProfileRepository(storeIn(dir))
        repository.addWindowRule(WindowRule.Fixed(EatingWindow(6, 20, fromEpochDay = 20_677)))
        repository.addWindowRule(
            WindowRule.Measured(MeasuredWindow(fastingHours = 16), fromEpochDay = 20_707),
        )

        repository.clearWindowRules()

        assertThat(repository.windowRules.first()).isEmpty()
    }

    private fun storeIn(dir: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(dir, "profile.preferences_pb") }
}
