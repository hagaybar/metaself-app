package com.metaself.app.data.profile

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.profile.ActivityLevel
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.profile.Sex
import com.metaself.app.domain.profile.aProfile
import org.junit.jupiter.api.Test

class ProfileCodecTest {

    @Test
    fun `a profile survives a round trip unchanged`() {
        val profile = aProfile(
            heightCm = 175,
            birthYear = 1990,
            sex = Sex.MALE,
            weightKg = 90.0,
            activity = ActivityLevel.LIGHT,
            goal = Goal.lose(0.25),
            allowBelowFloor = true,
        )
        assertThat(ProfileCodec.decode(ProfileCodec.encode(profile))).isEqualTo(profile)
    }

    @Test
    fun `holding weight survives the round trip`() {
        val profile = aProfile(goal = Goal.hold())
        val decoded = ProfileCodec.decode(ProfileCodec.encode(profile))
        assertThat(decoded?.goal?.direction).isEqualTo(GoalDirection.HOLD)
        assertThat(decoded?.goal?.kgPerWeek).isEqualTo(0.0)
    }

    @Test
    fun `an empty store decodes to no profile rather than a crash`() {
        assertThat(ProfileCodec.decode(emptyMap())).isNull()
    }

    @Test
    fun `a half-written store decodes to no profile`() {
        val partial = ProfileCodec.encode(aProfile()) - ProfileCodec.KEY_WEIGHT_KG
        assertThat(ProfileCodec.decode(partial)).isNull()
    }

    @Test
    fun `a value this version cannot parse decodes to no profile`() {
        val corrupt = ProfileCodec.encode(aProfile()) +
            (ProfileCodec.KEY_ACTIVITY to "TELEPORTING")
        assertThat(ProfileCodec.decode(corrupt)).isNull()
    }

    @Test
    fun `a decimal weight is stored without depending on the phone's locale`() {
        val encoded = ProfileCodec.encode(aProfile(weightKg = 80.5))
        assertThat(encoded[ProfileCodec.KEY_WEIGHT_KG]).isEqualTo("80.5")
    }

    @Test
    fun `a target weight survives a round trip`() {
        val stored = ProfileCodec.encode(aProfile(goal = Goal.lose(0.5, targetKg = 75.0)))

        assertThat(ProfileCodec.decode(stored)!!.goal.targetKg).isEqualTo(75.0)
    }

    /**
     * Every profile written before targets existed. Refusing to decode one would offer the owner
     * setup again, which is indistinguishable from having lost his data.
     */
    @Test
    fun `a profile stored before there were targets still decodes`() {
        val old = ProfileCodec.encode(aProfile(goal = Goal.lose(0.5)))
            .filterKeys { it != ProfileCodec.KEY_GOAL_TARGET_KG }

        val decoded = ProfileCodec.decode(old)

        assertThat(decoded).isNotNull()
        assertThat(decoded!!.goal.targetKg).isNull()
    }

    /**
     * The store clears only the keys the codec writes. If a cleared target were simply omitted, the
     * old one would stay in the store and the app would go on aiming at a weight he had removed.
     */
    @Test
    fun `clearing a target writes the key rather than omitting it`() {
        val stored = ProfileCodec.encode(aProfile(goal = Goal.lose(0.5)))

        assertThat(stored).containsKey(ProfileCodec.KEY_GOAL_TARGET_KG)
        assertThat(stored[ProfileCodec.KEY_GOAL_TARGET_KG]).isEmpty()
    }
}
