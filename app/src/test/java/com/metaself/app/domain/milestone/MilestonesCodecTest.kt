package com.metaself.app.domain.milestone

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.profile.MilestonesCodec
import org.junit.jupiter.api.Test

class MilestonesCodecTest {

    @Test
    fun `what has been celebrated survives a round trip`() {
        val reached = mapOf(
            Milestone.firstKg to 20_699L,
            Milestone.everyFifth(5) to 20_730L,
        )

        assertThat(MilestonesCodec.decode(MilestonesCodec.encode(reached))).isEqualTo(reached)
    }

    @Test
    fun `a store with nothing in it has celebrated nothing`() {
        assertThat(MilestonesCodec.decode(null)).isEmpty()
        assertThat(MilestonesCodec.decode("")).isEmpty()
        assertThat(MilestonesCodec.decode("   ")).isEmpty()
    }

    /**
     * A half-written entry costs one repeated celebration. Refusing to decode the whole string
     * would cost every one of them at once.
     */
    @Test
    fun `an unreadable entry is dropped and the rest survives`() {
        val decoded = MilestonesCodec.decode("FIRST_KG:20699,rubbish,HALFWAY:notaday,LAST_KG:20800")

        assertThat(decoded).containsExactly(Milestone.firstKg, 20_699L, Milestone.lastKg, 20_800L)
    }

    /** It is meant to be legible in a backup, which is why it is names and not numbers. */
    @Test
    fun `it is written as words a person could read`() {
        val encoded = MilestonesCodec.encode(mapOf(Milestone.firstKg to 20_699L))

        assertThat(encoded).isEqualTo("FIRST_KG:20699")
    }
}
