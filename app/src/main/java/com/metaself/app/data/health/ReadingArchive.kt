package com.metaself.app.data.health

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * One month of raw readings as a file in the owner's Drive (D71). Pure: rows in, bytes out.
 *
 * gzip-compressed JSON, indented like the daily file, so that unzipped it is something a person can
 * open and check. **Named so that the daily backup's pruning can never match it** — the daily rule
 * requires a name ending in `.json`, and this ends in `.json.gz` (`ReadingArchiveTest` pins it).
 */
object ReadingArchive {

    const val VERSION = 1
    private const val PREFIX = "metaself-readings-"
    private const val SUFFIX = ".json.gz"

    @Serializable
    data class File(
        val version: Int = VERSION,
        val month: String,
        val readings: List<Row> = emptyList(),
    )

    /** One row, written whole. */
    @Serializable
    data class Row(
        val kind: String,
        @SerialName("start") val startMillis: Long,
        @SerialName("end") val endMillis: Long? = null,
        val value: Double,
        val unit: String,
        val origin: String,
        @SerialName("record_id") val recordId: String,
        @SerialName("sample_index") val sampleIndex: Int = 0,
        @SerialName("epoch_day") val epochDay: Long,
    )

    /** What [decode] gives back: the month and its rows, ids zeroed. */
    data class Month(val month: String, val readings: List<HealthReadingEntity>)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun fileName(month: String): String = "$PREFIX$month$SUFFIX"

    fun monthOf(fileName: String): String? =
        fileName.takeIf { it.startsWith(PREFIX) && it.endsWith(SUFFIX) }
            ?.removePrefix(PREFIX)?.removeSuffix(SUFFIX)
            ?.takeIf { Regex("""\d{4}-\d{2}""").matches(it) }

    fun encode(month: String, rows: List<HealthReadingEntity>): ByteArray =
        ByteArrayOutputStream().also { encodeTo(it, month, rows) }.toByteArray()

    /**
     * Streamed through gzip as it is serialised, so a month's text is never held whole beside its
     * compressed bytes. [out] is finished but not closed: the GZIP stream (and the Deflater holding
     * its native resources) is closed here, through a wrapper that swallows that close so the
     * caller's own stream stays open for whatever it writes next.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun encodeTo(out: OutputStream, month: String, rows: List<HealthReadingEntity>) {
        GZIPOutputStream(NonClosing(out)).use { gzip ->
            json.encodeToStream(File.serializer(), File(month = month, readings = rows.map { it.toRow() }), gzip)
        }
    }

    /** [out] with `close()` swallowed, so closing a stream built on it never closes [out] itself. */
    private class NonClosing(private val out: OutputStream) : OutputStream() {
        override fun write(b: Int) = out.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun flush() = out.flush()
        override fun close() = Unit
    }

    /** For tests: gzip of UTF-8 text. */
    fun encodeRaw(text: String): ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }.toByteArray()

    /**
     * Null for anything that cannot be read whole, or a later version. No partial month. Streamed
     * out of gzip as it is parsed, so the unzipped text is never held whole.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun decode(bytes: ByteArray): Month? = runCatching {
        val file = GZIPInputStream(bytes.inputStream()).use { json.decodeFromStream(File.serializer(), it) }
        if (file.version !in 1..VERSION) return null
        Month(file.month, file.readings.map { it.toEntity() })
    }.getOrNull()

    private fun HealthReadingEntity.toRow() =
        Row(kind, startMillis, endMillis, value, unit, origin, recordId, sampleIndex, epochDay)

    private fun Row.toEntity() = HealthReadingEntity(
        kind = kind, startMillis = startMillis, endMillis = endMillis, value = value, unit = unit,
        origin = origin, recordId = recordId, sampleIndex = sampleIndex, epochDay = epochDay,
    )
}
