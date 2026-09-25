package com.metaself.app.data.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/** The request profiles that worked, remembered per model name (D57 §5). */
interface RequestProfileStore {

    /** The profile remembered for [model], exactly as settings hold the name, or null. */
    fun profileFor(model: String): Flow<RequestProfile?>

    /** Remember [profile] as working for [model], leaving every other name's in place. */
    suspend fun remember(model: String, profile: RequestProfile)
}

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
            val updated = buildJsonObject {
                all(preferences).forEach { (name, entry) -> if (name != model) put(name, entry) }
                put(model, profile.toJson())
            }
            preferences[KEY] = updated.toString()
        }
    }

    private fun all(preferences: Preferences): JsonObject = preferences[KEY]
        ?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        ?: JsonObject(emptyMap())

    private companion object {
        val KEY = stringPreferencesKey("ai_request_profiles")
    }
}
