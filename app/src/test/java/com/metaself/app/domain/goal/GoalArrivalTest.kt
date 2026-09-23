package com.metaself.app.domain.goal

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.profile.GoalArrivalCodec
import org.junit.jupiter.api.Test

class GoalArrivalTest {

    @Test
    fun `it is shown on the day it happened`() {
        assertThat(GoalArrival(75.0, epochDay = 20_699).isTodayS(20_699)).isTrue()
    }

    /** D14: the past never nags, and an achievement that keeps announcing itself is a reprimand. */
    @Test
    fun `and on no other day`() {
        val arrival = GoalArrival(75.0, epochDay = 20_699)

        assertThat(arrival.isTodayS(20_700)).isFalse()
        assertThat(arrival.isTodayS(20_698)).isFalse()
    }

    @Test
    fun `it survives a round trip through the store`() {
        val stored = GoalArrivalCodec.encode(GoalArrival(75.5, epochDay = 20_699))

        assertThat(GoalArrivalCodec.decode(stored)).isEqualTo(GoalArrival(75.5, 20_699))
    }

    @Test
    fun `a store with no arrival in it decodes to nothing rather than throwing`() {
        assertThat(GoalArrivalCodec.decode(emptyMap())).isNull()
        assertThat(GoalArrivalCodec.decode(mapOf(GoalArrivalCodec.KEY_TARGET_KG to "75"))).isNull()
    }
}
