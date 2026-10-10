package com.metaself.app.ui.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.PlanStanding
import com.metaself.app.domain.trainer.PlanTile
import com.metaself.app.domain.trainer.PlanTimeline
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.TileState
import com.metaself.app.domain.trainer.TimelinePlace
import com.metaself.app.domain.trainer.TimelineWeek
import org.junit.jupiter.api.Test

/**
 * What the plan screen says (D117). A four-week plan from Monday 24 August 2026; today is Thursday 3 September
 * ([TEST_EPOCH_DAY]). Every figure is invented.
 */
class PlanTimelineWordingTest {

    private val monday = TEST_EPOCH_DAY - 3
    private val start = monday - 7
    private val walk30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")
    private val walk45 = PlannedSession(WorkoutKind.WALK, 45, PlannedEffort.STEADY, "Invented line")
    private val run20 = PlannedSession(WorkoutKind.RUN, 20, PlannedEffort.PUSH, "Invented line")

    @Test
    fun `the subtitle's four forms`() {
        assertThat(PlanTimelineWording.subtitle(timeline(start, PlanStanding.InWeek(2))))
            .isEqualTo("4 weeks · 24 Aug – 20 Sep · you're in week 2")
        assertThat(PlanTimelineWording.subtitle(timeline(monday + 7, PlanStanding.Before(monday + 7))))
            .isEqualTo("4 weeks · 7 Sep – 4 Oct · starts Mon 7 Sep")
        assertThat(PlanTimelineWording.subtitle(timeline(start, PlanStanding.Ended(start + 27))))
            .isEqualTo("4 weeks · 24 Aug – 20 Sep · ended Sun 20 Sep")
        assertThat(PlanTimelineWording.subtitle(timeline(start, PlanStanding.Stopped(monday + 1))))
            .isEqualTo("4 weeks · 24 Aug – 20 Sep · stopped Tue 1 Sep")
    }

    @Test
    fun `a past week's line says its own sessions only when there are some`() {
        assertThat(PlanTimelineWording.pastLine(TimelinePlace.Past(2, 2, 0))).isEqualTo("2 of 2 planned")
        assertThat(PlanTimelineWording.pastLine(TimelinePlace.Past(2, 2, 4))).isEqualTo("2 of 2 planned · plus 4 of your own")
        assertThat(PlanTimelineWording.pastLine(TimelinePlace.Past(0, 3, 1))).isEqualTo("0 of 3 planned · plus 1 of your own")
    }

    @Test
    fun `a week ahead's sessions on one line`() {
        assertThat(PlanTimelineWording.aheadLine(listOf(walk30, walk45, run20))).isEqualTo("Easy 30′ · Steady 45′ · Push 20′")
    }

    @Test
    fun `a tile's two lines`() {
        assertThat(PlanTimelineWording.tileTitle(walk30)).isEqualTo("Easy 30′")
        assertThat(PlanTimelineWording.tileLine(PlanTile(walk30, TileState.DONE, monday, 30))).isEqualTo("Done Mon")
        assertThat(PlanTimelineWording.tileLine(PlanTile(walk30, TileState.DONE_SHORT, monday + 2, 25))).isEqualTo("Done Wed · 25 min")
        assertThat(PlanTimelineWording.tileLine(PlanTile(walk30, TileState.OPEN))).isEqualTo("Open")
    }

    @Test
    fun `a week's heading, and where you stood`() {
        assertThat(PlanTimelineWording.weekHeading(TimelineWeek(2, "Add a run", TimelinePlace.Ahead(emptyList())))).isEqualTo("Week 2 · Add a run")
        assertThat(PlanTimelineWording.weekHeading(TimelineWeek(3, " ", TimelinePlace.Ahead(emptyList())))).isEqualTo("Week 3")
        assertThat(PlanTimelineWording.THIS_WEEK).isEqualTo(" — this week")
        assertThat(PlanTimelineWording.stood(monday - 1)).isEqualTo("WHERE YOU STOOD · SUN 30 AUG")
    }

    private fun timeline(start: Long, standing: PlanStanding) =
        PlanTimeline("Invented plan", "Invented reason.", start, standing, List(4) { TimelineWeek(it + 1, "", TimelinePlace.Ahead(emptyList())) })
}
