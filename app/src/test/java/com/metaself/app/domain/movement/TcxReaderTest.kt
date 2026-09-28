package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime

/**
 * Reading a TCX workout file (D82). Every file here is built by [tcx] from invented, round figures;
 * none is, or is copied from, a real export. The "band-style" file has the two quirks the spec
 * names — a plain number inside `HeartRateBpm`, and a `Steps` element TCX does not define — and a
 * wall-clock time written with a `Z`.
 */
class TcxReaderTest {

    @Test
    fun `a standard file gives its start, time, distance, calories and heart rate`() {
        val read = TcxReader.read(tcx(laps = listOf(standardLap(seconds = 1_800, metres = "5000.0", kcal = 300, bpm = 120))))

        val workout = (read as TcxRead.Read).workout
        assertThat(workout.writtenAt).isEqualTo(LocalDateTime.of(2026, 9, 3, 10, 0))
        assertThat(workout.instant).isEqualTo(Instant.parse("2026-09-03T10:00:00Z"))
        assertThat(workout.seconds).isEqualTo(1_800)
        assertThat(workout.distanceM).isEqualTo(5_000.0)
        assertThat(workout.kcal).isEqualTo(300)
        assertThat(workout.avgHeartRate).isEqualTo(120)
        assertThat(workout.steps).isNull()
        assertThat(workout.sport).isEqualTo("Running")
    }

    @Test
    fun `the band-style file gives its steps and its plain heart-rate number`() {
        val lap = """
            <Lap StartTime="2026-09-03T10:00:00Z">
              <TotalTimeSeconds>2400</TotalTimeSeconds>
              <DistanceMeters>3000</DistanceMeters>
              <Calories>200</Calories>
              <HeartRateBpm>110</HeartRateBpm>
              <Steps>4000</Steps>
            </Lap>
        """

        val workout = (TcxReader.read(tcx(sport = "Other", laps = listOf(lap))) as TcxRead.Read).workout

        assertThat(workout.steps).isEqualTo(4_000)
        assertThat(workout.avgHeartRate).isEqualTo(110)
        assertThat(workout.distanceM).isEqualTo(3_000.0)
        assertThat(workout.seconds).isEqualTo(2_400)
    }

    @Test
    fun `two laps are summed, and heart rate is averaged by each lap's time`() {
        val read = TcxReader.read(
            tcx(
                laps = listOf(
                    standardLap(seconds = 600, metres = "2000", kcal = 100, bpm = 100),
                    standardLap(seconds = 1_800, metres = "6000", kcal = 300, bpm = 140),
                ),
            ),
        )

        val workout = (read as TcxRead.Read).workout
        assertThat(workout.seconds).isEqualTo(2_400)
        assertThat(workout.distanceM).isEqualTo(8_000.0)
        assertThat(workout.kcal).isEqualTo(400)
        assertThat(workout.avgHeartRate).isEqualTo(130)
    }

    @Test
    fun `steps written inside a lap's extensions are read too`() {
        val lap = """
            <Lap><TotalTimeSeconds>600</TotalTimeSeconds>
              <Extensions><ns3:LX xmlns:ns3="urn:example:ext"><ns3:Steps>1000</ns3:Steps></ns3:LX></Extensions>
            </Lap>
        """

        assertThat((TcxReader.read(tcx(laps = listOf(lap))) as TcxRead.Read).workout.steps).isEqualTo(1_000)
    }

    @Test
    fun `a prefixed namespace reads the same as the default one`() {
        val text = """<?xml version="1.0"?>
            <tc:TrainingCenterDatabase xmlns:tc="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <tc:Activities><tc:Activity Sport="Biking"><tc:Id>2026-09-03T10:00:00Z</tc:Id>
                <tc:Lap><tc:TotalTimeSeconds>600</tc:TotalTimeSeconds><tc:DistanceMeters>4000</tc:DistanceMeters></tc:Lap>
              </tc:Activity></tc:Activities>
            </tc:TrainingCenterDatabase>"""

        val workout = (TcxReader.read(text) as TcxRead.Read).workout
        assertThat(workout.distanceM).isEqualTo(4_000.0)
        assertThat(workout.sport).isEqualTo("Biking")
    }

    @Test
    fun `a start with an offset gives its instant, one with none gives only the wall clock`() {
        val offset = TcxReader.read(tcx(id = "2026-09-03T10:00:00.000+03:00")) as TcxRead.Read
        val none = TcxReader.read(tcx(id = "2026-09-03T10:00:00")) as TcxRead.Read

        assertThat(offset.workout.writtenAt).isEqualTo(LocalDateTime.of(2026, 9, 3, 10, 0))
        assertThat(offset.workout.instant).isEqualTo(Instant.parse("2026-09-03T07:00:00Z"))
        assertThat(none.workout.writtenAt).isEqualTo(LocalDateTime.of(2026, 9, 3, 10, 0))
        assertThat(none.workout.instant).isNull()
    }

    @Test
    fun `with no Id, the first lap's start is used`() {
        val text = tcx(id = null, laps = listOf("""<Lap StartTime="2026-09-03T09:00:00Z"><TotalTimeSeconds>600</TotalTimeSeconds><Calories>50</Calories></Lap>"""))

        assertThat((TcxReader.read(text) as TcxRead.Read).workout.writtenAt).isEqualTo(LocalDateTime.of(2026, 9, 3, 9, 0))
    }

    @Test
    fun `trackpoints are ignored`() {
        val lap = """
            <Lap><TotalTimeSeconds>600</TotalTimeSeconds><DistanceMeters>2000</DistanceMeters>
              <Track><Trackpoint><Time>2026-09-03T10:00:00Z</Time><DistanceMeters>999999</DistanceMeters></Trackpoint></Track>
            </Lap>
        """

        assertThat((TcxReader.read(tcx(laps = listOf(lap))) as TcxRead.Read).workout.distanceM).isEqualTo(2_000.0)
    }

    @Test
    fun `a byte-order mark and leading blank lines are tolerated`() {
        assertThat(TcxReader.read("﻿\n\n" + tcx())).isInstanceOf(TcxRead.Read::class.java)
    }

    @Test
    fun `a figure that does not parse is left out, not fatal`() {
        val lap = "<Lap><TotalTimeSeconds>600</TotalTimeSeconds><DistanceMeters>lots</DistanceMeters><Calories>50</Calories></Lap>"

        val workout = (TcxReader.read(tcx(laps = listOf(lap))) as TcxRead.Read).workout
        assertThat(workout.distanceM).isNull()
        assertThat(workout.kcal).isEqualTo(50)
    }

    @Test
    fun `each unreadable file is refused whole with its reason`() {
        assertThat(TcxReader.read("not a file at all")).isEqualTo(TcxRead.Refused(WorkoutFileRefusal.NOT_XML))
        assertThat(TcxReader.read("")).isEqualTo(TcxRead.Refused(WorkoutFileRefusal.NOT_XML))
        assertThat(TcxReader.read("""<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e "e">]><x>&e;</x>"""))
            .isEqualTo(TcxRead.Refused(WorkoutFileRefusal.NOT_XML))
        assertThat(TcxReader.read("<gpx><trk/></gpx>")).isEqualTo(TcxRead.Refused(WorkoutFileRefusal.NO_WORKOUT))
        assertThat(TcxReader.read(tcx(id = null, laps = listOf("<Lap><TotalTimeSeconds>600</TotalTimeSeconds></Lap>"))))
            .isEqualTo(TcxRead.Refused(WorkoutFileRefusal.NO_START))
        assertThat(TcxReader.read(tcx(id = "yesterday-ish", laps = listOf("<Lap><TotalTimeSeconds>600</TotalTimeSeconds><Calories>5</Calories></Lap>"))))
            .isEqualTo(TcxRead.Refused(WorkoutFileRefusal.NO_START))
        assertThat(TcxReader.read(tcx(laps = emptyList()))).isEqualTo(TcxRead.Refused(WorkoutFileRefusal.NO_DURATION))
        assertThat(TcxReader.read(tcx(laps = listOf("<Lap><TotalTimeSeconds>0</TotalTimeSeconds><Calories>5</Calories></Lap>"))))
            .isEqualTo(TcxRead.Refused(WorkoutFileRefusal.NO_DURATION))
        assertThat(TcxReader.read(tcx(laps = listOf("<Lap><TotalTimeSeconds>600</TotalTimeSeconds><HeartRateBpm>100</HeartRateBpm></Lap>"))))
            .isEqualTo(TcxRead.Refused(WorkoutFileRefusal.NOTHING_TO_ADD))
    }

    /**
     * The lap sits four elements down (TrainingCenterDatabase, Activities, Activity, Lap), so [extra]
     * wrappers inside it make the document 4 + [extra] deep.
     */
    private fun lapWithNesting(extra: Int) =
        "<Lap><TotalTimeSeconds>600</TotalTimeSeconds><Calories>50</Calories>" +
            "<Extensions>".repeat(extra) + "</Extensions>".repeat(extra) + "</Lap>"

    @Test
    fun `a document nested deeper than any workout file is refused as not XML, one within it is read`() {
        assertThat(TcxReader.read(tcx(laps = listOf(lapWithNesting(TcxReader.MAX_DEPTH - 4)))))
            .isInstanceOf(TcxRead.Read::class.java)
        assertThat(TcxReader.read(tcx(laps = listOf(lapWithNesting(TcxReader.MAX_DEPTH - 3)))))
            .isEqualTo(TcxRead.Refused(WorkoutFileRefusal.NOT_XML))
    }

    /** A hostile nesting, a few hundred thousand deep: refused, and nothing thrown. */
    @Test
    fun `a document nested hundreds of thousands deep is refused, not a crash`() {
        val deep = "<a>".repeat(300_000) + "</a>".repeat(300_000)

        assertThat(TcxReader.read(deep)).isEqualTo(TcxRead.Refused(WorkoutFileRefusal.NOT_XML))
        assertThat(TcxReader.read(tcx(laps = listOf(lapWithNesting(300_000)))))
            .isEqualTo(TcxRead.Refused(WorkoutFileRefusal.NOT_XML))
    }

    private fun standardLap(seconds: Int, metres: String, kcal: Int, bpm: Int) = """
        <Lap StartTime="2026-09-03T10:00:00Z">
          <TotalTimeSeconds>$seconds.0</TotalTimeSeconds>
          <DistanceMeters>$metres</DistanceMeters>
          <Calories>$kcal</Calories>
          <AverageHeartRateBpm><Value>$bpm</Value></AverageHeartRateBpm>
          <Intensity>Active</Intensity>
        </Lap>
    """

    /** An invented file: the default namespace, one activity, [laps] as given. */
    private fun tcx(
        id: String? = "2026-09-03T10:00:00Z",
        sport: String = "Running",
        laps: List<String> = listOf(standardLap(seconds = 1_800, metres = "5000", kcal = 300, bpm = 120)),
    ) = """<?xml version="1.0" encoding="UTF-8"?>
        <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
          <Activities>
            <Activity Sport="$sport">
              ${id?.let { "<Id>$it</Id>" }.orEmpty()}
              ${laps.joinToString("\n")}
            </Activity>
          </Activities>
        </TrainingCenterDatabase>"""
}
