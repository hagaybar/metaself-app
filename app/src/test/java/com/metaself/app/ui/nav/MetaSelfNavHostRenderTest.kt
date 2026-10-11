package com.metaself.app.ui.nav

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.screen.settings.SettingsPage
import org.junit.jupiter.api.Test

class MetaSelfNavHostRenderTest {

    @Test
    fun `the destinations have distinct routes`() {
        val routes = listOf(
            Destination.Today.route,
            Destination.AddEntry.route,
            Destination.EditEntry.route,
            Destination.Weight.route,
            Destination.Movement.route,
        )
        assertThat(routes).containsNoDuplicates()
    }

    @Test
    fun `the edit route carries which item is being corrected`() {
        assertThat(Destination.EditEntry.route).contains("{itemId}")
        assertThat(Destination.EditEntry.of(itemId = 7)).isEqualTo("entry/edit/7")
    }

    /** D130, D132: Your movement on this week, or with the log-a-workout sheet open (the Trainer's button). */
    @Test
    fun `Your movement has its own route, and one that opens the sheet`() {
        assertThat(Destination.Movement.route).isEqualTo("movement?openLog={openLog}")
        assertThat(Destination.Movement.thisWeek()).isEqualTo("movement")
        assertThat(Destination.Movement.toLog()).isEqualTo("movement?openLog=true")
    }

    /** Settings is a nested graph (D79): an index and eight pages, About this app the eighth (D134), each its own route. */
    @Test
    fun `every Settings page has its own route, under the Settings graph`() {
        val routes = SettingsPage.entries.map(Destination.Settings::page) + Destination.Settings.index

        assertThat(routes).hasSize(9)
        assertThat(routes).containsNoDuplicates()
        assertThat(routes.all { it.startsWith(Destination.Settings.route + "/") }).isTrue()
    }

    /** D80: under the Settings graph, beside the Movement page it is reached from. */
    @Test
    fun `the band report has its own route under Settings`() {
        assertThat(Destination.Settings.bandReport).isEqualTo("settings/movement/band")
    }

    /** The describe screen's "Add a key in settings" lands on the page the key is on (D79). */
    @Test
    fun `opened for the key, Settings is the AI estimates page`() {
        assertThat(Destination.Settings.atKey).isEqualTo(Destination.Settings.page(SettingsPage.AI))
    }

    /**
     * The day's window marks open window settings. When Settings was one page, "When you eat" was
     * the first thing on it; now it is on the Eating page, and the marks go straight there (D79).
     */
    @Test
    fun `the window marks open Settings at the Eating page`() {
        assertThat(Destination.Settings.atWindow).isEqualTo(Destination.Settings.page(SettingsPage.EATING))
    }

    @Test
    fun `the app starts on today`() {
        assertThat(Destination.start).isEqualTo(Destination.Today)
    }
}
