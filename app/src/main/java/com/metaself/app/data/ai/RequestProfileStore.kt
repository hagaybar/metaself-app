package com.metaself.app.data.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/** The request profiles that worked, remembered per model name (D57 §5). */
interface RequestProfileStore {

    /** The profile remembered for [model], exactly as settings hold the name, or null. */
    fun profileFor(model: String): Flow<RequestProfile?>

    /**
     * Remember [profile] as working for [model], leaving every other name's in place — and this
     * name's deep level, which only a final analysis writes (D58 §12.5).
     */
    suspend fun remember(model: String, profile: RequestProfile)

    /** The thinking a conversation's final analysis worked with on [model], or null when none is known. */
    fun deepFor(model: String): Flow<DeepLevel?>

    /**
     * Remember that a final analysis worked on [model] sent [effort] — null when it worked only
     * with none. Never changes the everyday profile; creates one from the first guess when the name
     * has none, so the entry stays a valid one (D58 §12.5).
     */
    suspend fun rememberDeep(model: String, effort: String?)
}

/**
 * What a conversation's final analysis sends as `reasoning_effort` on one model (D58 §8.5).
 * [effort] null means it worked with none sent.
 */
data class DeepLevel(val effort: String?)

/**
 * Every remembered profile in one entry of the settings store, as a JSON object from model name to
 * profile — a Preferences DataStore, not the database, so no schema change.
 *
 * Each write is one [edit], which DataStore runs one at a time on the stored value: two calls
 * learning at once cannot lose a third name's profile, and the last working profile written for a
 * name stands. A value that cannot be read counts as nothing remembered, so it is relearned.
 */
class DataStoreRequestProfileStore(private val store: DataStore<Preferences>) : RequestProfileStore {

    override fun profileFor(model: String): Flow<RequestProfile?> = store.data
        .map { preferences ->
            val entry = all(preferences)[model] as? JsonObject
            entry?.let(RequestProfile::fromJson)
        }
        .distinctUntilChanged()

    override suspend fun remember(model: String, profile: RequestProfile) {
        store.edit { preferences ->
            val all = all(preferences)
            // The deep level stays as it was found, in this same edit (D58 §12.5).
            val deep = (all[model] as? JsonObject)?.get(DEEP)
            val entry = JsonObject(profile.toJson() + listOfNotNull(deep?.let { DEEP to it }))
            preferences[KEY] = replaced(all, model, entry).toString()
        }
    }

    override fun deepFor(model: String): Flow<DeepLevel?> = store.data
        .map { preferences ->
            val deep = (all(preferences)[model] as? JsonObject)?.get(DEEP) ?: return@map null
            when {
                deep is JsonNull -> DeepLevel(null)
                deep is JsonPrimitive && deep.isString -> DeepLevel(deep.content)
                else -> null
            }
        }
        .distinctUntilChanged()

    override suspend fun rememberDeep(model: String, effort: String?) {
        store.edit { preferences ->
            val all = all(preferences)
            val everyday = (all[model] as? JsonObject)?.takeIf { RequestProfile.fromJson(it) != null }
                ?: RequestProfile.guess(model).toJson()
            val entry = JsonObject(everyday + (DEEP to (effort?.let { JsonPrimitive(it) } ?: JsonNull)))
            preferences[KEY] = replaced(all, model, entry).toString()
        }
    }

    private fun replaced(all: JsonObject, model: String, entry: JsonObject): JsonObject = buildJsonObject {
        all.forEach { (name, kept) -> if (name != model) put(name, kept) }
        put(model, entry)
    }

    private fun all(preferences: Preferences): JsonObject = preferences[KEY]
        ?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        ?: JsonObject(emptyMap())

    private companion object {
        val KEY = stringPreferencesKey("ai_request_profiles")
        const val DEEP = "deep_reasoning_effort"
    }
}
