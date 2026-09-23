package com.metaself.app.data.backup

import com.metaself.app.domain.backup.Backup
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * The backup file's format, as a pure function both ways.
 *
 * Indented on purpose. This is the one file the owner is expected to be able to open and read, and
 * a single line of dense JSON is not that.
 *
 * **A number that is not finite is written as null, and never stops the export** (issue #7). JSON
 * has no way to write one and the encoder throws on it; until 0.32.6 (D42) a food form and the
 * amount boxes took "Infinity", so a single such value made every export and every daily copy fail.
 * A food's number group holding one is already written as null by [BackupFoods.toBackup], which
 * keeps the rest of the food; this catches whatever else could hold one. On the way back, a null
 * where the file's shape wants a number with a default reads as that default — an amount of 0, which
 * is what "no amount" already means. Every finite number is written exactly as before.
 */
object BackupCodec {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    /** Only to hold a non-finite number long enough to replace it; never used to print. */
    private val lenient = Json(json) { allowSpecialFloatingPointValues = true }

    fun encode(backup: Backup): String {
        val tree = lenient.encodeToJsonElement(Backup.serializer(), backup)
        return json.encodeToString(JsonElement.serializer(), tree.withoutNonFinite())
    }

    /**
     * Null for anything that cannot be read whole — rubbish, a truncated file, or one written by a
     * later version of the app. No partial restore: a half-restored record is worse than a failed
     * one, because a failure is visible and a half is not.
     */
    fun decode(text: String): Backup? {
        val backup = runCatching { json.decodeFromString(Backup.serializer(), text) }.getOrNull()
            ?: return null
        return backup.takeIf { it.version in 1..Backup.CURRENT_VERSION }
    }

    private fun JsonElement.withoutNonFinite(): JsonElement = when (this) {
        is JsonObject -> JsonObject(mapValues { (_, value) -> value.withoutNonFinite() })
        is JsonArray -> JsonArray(map { it.withoutNonFinite() })
        is JsonPrimitive -> if (isNonFinite()) JsonNull else this
    }

    private fun JsonPrimitive.isNonFinite(): Boolean =
        !isString && doubleOrNull?.isFinite() == false
}
