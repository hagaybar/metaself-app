package com.metaself.app.ui.nav

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4, and Robolectric is what makes `Uri.encode`
 * a real call rather than a stub. This module does not set `unitTests.isReturnDefaultValues`, so the
 * same assertions written as plain JUnit 5 would throw "Method encode in android.net.Uri not
 * mocked". They live here rather than in `MetaSelfNavHostRenderTest`, which is JUnit 5: putting a
 * runner on that file would stop its tests running at all.
 */
@RunWith(RobolectricTestRunner::class)
class DestinationRouteTest {

    @Test
    fun `the describe route carries the words already typed`() {
        assertThat(Destination.Describe.withWords("shakshuka"))
            .isEqualTo("meal/describe?text=shakshuka")
    }

    /**
     * The quiet row's describe button carries nothing, and must still land on the destination the
     * host registers under the bare route.
     */
    @Test
    fun `describing with nothing typed is the plain route`() {
        assertThat(Destination.Describe.withWords("")).isEqualTo(Destination.Describe.route)
        assertThat(Destination.Describe.withWords("   ")).isEqualTo(Destination.Describe.route)
    }

    @Test
    fun `words with spaces cannot break the route`() {
        val route = Destination.Describe.withWords("pita with hummus")

        assertThat(route).startsWith("meal/describe?text=")
        assertThat(route).doesNotContain(" ")
    }

    /** A name in a right-to-left script, which the route must carry unaltered. */
    @Test
    fun `a Hebrew name survives the route`() {
        val route = Destination.Describe.withWords("יוגורט")

        assertThat(Uri.decode(route.substringAfter("text="))).isEqualTo("יוגורט")
    }

    /**
     * The day's record (D50), for one day and opened at one logging.
     *
     * The logging is optional and the day is not, so the day is part of the path and the logging is
     * a query argument — and the bare route is what a record opened at nothing has to land on, since
     * the host registers one destination for both.
     */
    @Test
    fun `the record route carries the day, and the logging it opens at`() {
        assertThat(Destination.Record.of(20_699L, 7L)).isEqualTo("day/record/20699?mealId=7")
    }

    /** Opened at nothing, it is the bare route the host also registers. */
    @Test
    fun `a record opened at no particular logging is the plain route`() {
        assertThat(Destination.Record.of(20_699L)).isEqualTo("day/record/20699")
    }

    /**
     * The foods chosen in the list, carried to the builder the same way the describe screen carries
     * the words already typed: as an optional query argument on a route that is still reachable
     * bare. Plain commas rather than an encoded list, because the ids are digits and a route a
     * person can read is a route a person can debug.
     */
    @Test
    fun `a new meal can be started from foods already chosen`() {
        assertThat(Destination.BuildMeal.of(0, listOf(3L, 7L, 12L)))
            .isEqualTo("meal/build/0?foods=3,7,12")
    }

    /** "Give this a portion" opens the manager on that food, by the route the host registers. */
    @Test
    fun `the manager can be opened on one food`() {
        assertThat(Destination.Foods.editing(7L)).isEqualTo("foods?food=7")
    }

    /** "Add a key in settings" opens settings at the key, by the route the host registers. */
    @Test
    fun `settings can be opened at the key`() {
        assertThat(Destination.Settings.atKey).isEqualTo("settings?at=key")
    }

    /** The "Build a meal" button carries nothing, and must still land on the same destination. */
    @Test
    fun `a new meal with nothing chosen is the plain route`() {
        assertThat(Destination.BuildMeal.of(0, emptyList())).isEqualTo(Destination.BuildMeal.of(0))
    }

    /** Opening a meal he already built carries its foods no differently. */
    @Test
    fun `an existing meal keeps its own id when foods are carried`() {
        assertThat(Destination.BuildMeal.of(4, listOf(1L))).isEqualTo("meal/build/4?foods=1")
    }

    /**
     * Not a restatement of `the destinations have distinct routes` next door: that one never listed
     * the describe route. It matters now, because the describe destination is about to be registered
     * under `route + "?text={text}"` while still being navigated to by the bare route.
     */
    @Test
    fun `the describe route stays distinct from every other route`() {
        val routes = listOf(
            Destination.Today.route,
            Destination.AddEntry.route,
            Destination.EditEntry.route,
            Destination.Weight.route,
            Destination.Settings.route,
            Destination.Describe.route,
            Destination.Repeat.route,
            Destination.Scan.route,
            Destination.LogWeight.route,
            Destination.EditWeight.route,
            Destination.WeightChart.route,
        )

        assertThat(routes).containsNoDuplicates()
        assertThat(routes).contains(Destination.Describe.route)
    }
}
