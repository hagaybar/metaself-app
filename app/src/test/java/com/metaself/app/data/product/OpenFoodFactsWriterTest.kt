package com.metaself.app.data.product

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.scan.ContributeWording
import org.junit.jupiter.api.Test

class OpenFoodFactsWriterTest {

    /** Three failures told apart, because they mean three different things to do next. */
    @Test
    fun `a sent contribution says it is in the database`() {
        assertThat(ContributeWording.result(Contribution.Sent)).contains("in the food database now")
    }

    @Test
    fun `an unreachable database says the local copy is safe`() {
        val text = ContributeWording.result(Contribution.Unreachable)

        assertThat(text).contains("Could not reach")
        assertThat(text).contains("saved here either way")
    }

    @Test
    fun `a refusal points at the likely cause and keeps the local copy`() {
        val text = ContributeWording.result(Contribution.Refused("no valid user"))

        assertThat(text).contains("username or password")
        assertThat(text).contains("saved here either way")
        assertThat(text).contains("no valid user")
    }

    @Test
    fun `a refusal with no explanation still says something useful`() {
        assertThat(ContributeWording.result(Contribution.Refused(null)))
            .contains("would not accept it")
    }

    /** With no account there is no button, and the line explains why rather than failing later. */
    @Test
    fun `with no account it says so and says the local copy still works`() {
        assertThat(ContributeWording.NO_ACCOUNT).contains("Settings")
        assertThat(ContributeWording.NO_ACCOUNT).contains("works without one")
    }

    /** The rule this whole step exists to obey (D24, D4). */
    @Test
    fun `the form says plainly that nothing estimated goes up`() {
        assertThat(ContributeWording.ONLY_THE_LABEL).contains("from the package itself")
        assertThat(ContributeWording.ONLY_THE_LABEL).contains("Nothing estimated")
    }
}
