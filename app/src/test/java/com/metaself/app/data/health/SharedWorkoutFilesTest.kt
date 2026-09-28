package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** The waiting shared file (D82). The Uris are invented. */
class SharedWorkoutFilesTest {

    @Test
    fun `a shared file is handed out once, and a later share replaces an untaken one`() {
        val shared = SharedWorkoutFiles()
        assertThat(shared.take()).isNull()

        shared.offer("content://example/a.tcx")
        shared.offer("content://example/b.tcx")

        assertThat(shared.pending.value).isEqualTo("content://example/b.tcx")
        assertThat(shared.take()).isEqualTo("content://example/b.tcx")
        assertThat(shared.take()).isNull()
        assertThat(shared.pending.value).isNull()
    }
}
