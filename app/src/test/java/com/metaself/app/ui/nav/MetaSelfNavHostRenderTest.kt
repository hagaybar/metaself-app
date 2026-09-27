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

    @Test
    fun `the Movement screen has its own route`() {
        assertThat(Destination.Movement.route).isEqualTo("movement")
    }

    /** Settings is a nested graph (D79): an index and six pages, each its own route. */
    @Test
    fun `every Settings page has its own route, under the Settings graph`() {
        val routes = SettingsPage.entries.map(Destination.Settings::page) + Destination.Settings.index

        assertThat(routes).hasSize(7)
        assertThat(routes).containsNoDuplicates()
        assertThat(routes.all { it.startsWith(Destination.Settings.route + "/") }).isTrue()
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
