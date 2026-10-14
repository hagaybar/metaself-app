package com.metaself.app.data.diagnostics.measure

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** D152: the downloaded file's first line. Every figure invented. */
class DiagnosticsHeaderTest {

    @Test
    fun `the header names the app, the phone, the hours measured and each table's row count`() {
        val line = DiagnosticsHeader.line(
            appVersion = "0.87.0",
            versionCode = 145,
            phoneModel = "Example Phone",
            androidVersion = "14",
            androidSdk = 34,
            hoursMeasured = 71.96,
            measuredFromMillis = 1_000,
            measuredUntilMillis = 2_000,
            tableRows = mapOf("workouts" to 12L, "health_readings" to 3_400L),
            databaseBytes = 8_192,
            writtenAtMillis = 3_000,
        )

        assertThat(line).isEqualTo(
            "{\"type\":\"diagnostics\",\"app_version\":\"0.87.0\",\"version_code\":145,\"phone_model\":\"Example Phone\"," +
                "\"android_version\":\"14\",\"android_sdk\":34,\"hours_measured\":72.0,\"measured_from\":1000," +
                "\"measured_until\":2000,\"database_bytes\":8192," +
                "\"table_rows\":{\"health_readings\":3400,\"workouts\":12},\"written_at\":3000}",
        )
    }

    @Test
    fun `never measured, the hours are null`() {
        val line = DiagnosticsHeader.line("0.87.0", 145, "Example Phone", "14", 34, null, null, null, emptyMap(), 0, 0)

        assertThat(line).contains("\"hours_measured\":null")
    }

    @Test
    fun `the file is offered under the day's date`() {
        assertThat(DiagnosticsHeader.fileName("2026-09-03")).isEqualTo("metaself-diagnostics-2026-09-03.jsonl")
    }
}
