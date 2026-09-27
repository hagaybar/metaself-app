package com.metaself.app.data.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * An app's name for "What the band sends" (D80): the phone's label, or the package name. JUnit 4
 * because Robolectric's runner is.
 */
@RunWith(RobolectricTestRunner::class)
class AppLabelsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `an app the phone does not know is shown by its package name`() {
        assertThat(PackageManagerAppLabels(context).labelOf("com.example.band")).isEqualTo("com.example.band")
    }

    @Test
    fun `an app the phone knows is shown by its label`() {
        val label = PackageManagerAppLabels(context).labelOf(context.packageName)

        assertThat(label).isNotEmpty()
        assertThat(label).isNotEqualTo(context.packageName)
    }
}
