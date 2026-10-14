package com.metaself.app.data.diagnostics.measure

/**
 * D152: the first line of the downloaded diagnostics file — what the lines after it were measured on.
 * Pure, so what is written is what a test reads. Counts and sizes only: the row count of each table,
 * never a row.
 */
object DiagnosticsHeader {

    /**
     * @param hoursMeasured of the latest switching-on, to its end or to now; null if never switched on.
     * @param tableRows each table's row count, by table name.
     * @param databaseBytes the database file and its write-ahead log together.
     */
    fun line(
        appVersion: String,
        versionCode: Int,
        phoneModel: String,
        androidVersion: String,
        androidSdk: Int,
        hoursMeasured: Double?,
        measuredFromMillis: Long?,
        measuredUntilMillis: Long?,
        tableRows: Map<String, Long>,
        databaseBytes: Long,
        writtenAtMillis: Long,
    ): String = MeasureJson.objectLine(
        linkedMapOf(
            "type" to "diagnostics",
            "app_version" to appVersion,
            "version_code" to versionCode,
            "phone_model" to phoneModel,
            "android_version" to androidVersion,
            "android_sdk" to androidSdk,
            "hours_measured" to hoursMeasured?.let { Math.round(it * 10) / 10.0 },
            "measured_from" to measuredFromMillis,
            "measured_until" to measuredUntilMillis,
            "database_bytes" to databaseBytes,
            "table_rows" to tableRows.toSortedMap(),
            "written_at" to writtenAtMillis,
        ),
    )

    /** The name the file is offered under, for [isoDate] (yyyy-mm-dd). */
    fun fileName(isoDate: String): String = "metaself-diagnostics-$isoDate.jsonl"
}
