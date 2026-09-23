package com.metaself.app.domain.target

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.profile.Sex
import org.junit.jupiter.api.Test

class RestingBurnTest {

    @Test
    fun `a man of eighty kilograms and one hundred and eighty centimetres at forty-six`() {
        // 10*80 + 6.25*180 - 5*46 + 5
        assertThat(RestingBurn.kcal(Sex.MALE, weightKg = 80.0, heightCm = 180, ageYears = 46))
            .isWithin(0.01).of(1700.0)
    }

    @Test
    fun `the same body as a woman burns 166 kcal less`() {
        val man = RestingBurn.kcal(Sex.MALE, 80.0, 180, 46)
        val woman = RestingBurn.kcal(Sex.FEMALE, 80.0, 180, 46)
        assertThat(man - woman).isWithin(0.01).of(166.0)
    }

    @Test
    fun `each year of age costs five kcal`() {
        val younger = RestingBurn.kcal(Sex.MALE, 80.0, 180, 40)
        val older = RestingBurn.kcal(Sex.MALE, 80.0, 180, 41)
        assertThat(younger - older).isWithin(0.01).of(5.0)
    }

    @Test
    fun `each kilogram adds ten kcal`() {
        val lighter = RestingBurn.kcal(Sex.MALE, 80.0, 180, 46)
        val heavier = RestingBurn.kcal(Sex.MALE, 81.0, 180, 46)
        assertThat(heavier - lighter).isWithin(0.01).of(10.0)
    }
}
