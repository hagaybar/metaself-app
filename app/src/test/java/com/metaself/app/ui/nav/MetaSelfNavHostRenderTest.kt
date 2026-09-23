package com.metaself.app.ui.nav

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MetaSelfNavHostRenderTest {

    @Test
    fun `the destinations have distinct routes`() {
        val routes = listOf(
            Destination.Today.route,
            Destination.AddEntry.route,
            Destination.EditEntry.route,
            Destination.Weight.route,
        )
        assertThat(routes).containsNoDuplicates()
    }

    @Test
    fun `the edit route carries which item is being corrected`() {
        assertThat(Destination.EditEntry.route).contains("{itemId}")
        assertThat(Destination.EditEntry.of(itemId = 7)).isEqualTo("entry/edit/7")
    }

    @Test
    fun `the app starts on today`() {
        assertThat(Destination.start).isEqualTo(Destination.Today)
    }
}
