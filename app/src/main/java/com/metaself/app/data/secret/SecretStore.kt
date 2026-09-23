package com.metaself.app.data.secret

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Every secret this app holds, encrypted against the phone's hardware keystore.
 *
 * **Nothing tests this class.** It needs the Android keystore, which Robolectric on the development
 * machine cannot provide, so it is verified only by the owner pressing something in settings and
 * seeing it work. That is why it stays as short as it can be: untested lines are a risk worth
 * bounding, and this file now holds two credentials rather than one.
 *
 * The file is excluded from Android's backup (`res/xml/backup_rules.xml` and
 * `data_extraction_rules.xml`). A copy restored onto another phone would be undecryptable rubbish
 * anyway, and a credential should not travel even in a form nobody can read.
 */
class SecretStore(context: Context) {

    // The 1.0.0 API: an alias for a key the system holds, not the key itself. The newer MasterKey
    // builder is only in the alpha, and an alpha is not what a credential should sit on.
    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            FILE_NAME,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** One flow over the whole file, so a change to any secret is seen by everything watching. */
    private val state = MutableStateFlow(readAll())

    fun watch(name: String): Flow<String?> = state.map { it[name] }

    fun read(name: String): String? = state.value[name]

    suspend fun save(name: String, value: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(name, value.trim()).commit()
        state.value = readAll()
    }

    suspend fun clear(vararg names: String) = withContext(Dispatchers.IO) {
        prefs.edit().apply { names.forEach { remove(it) } }.commit()
        state.value = readAll()
    }

    private fun readAll(): Map<String, String> = NAMES
        .mapNotNull { name -> prefs.getString(name, null)?.takeIf { it.isNotBlank() }?.let { name to it } }
        .toMap()

    companion object {
        /** Named in `res/xml/backup_rules.xml` too. Change one and you must change the other. */
        const val FILE_NAME = "metaself_api_key"

        const val OPENAI_KEY = "openai"
        const val OFF_USERNAME = "off_username"
        const val OFF_PASSWORD = "off_password"

        private val NAMES = listOf(OPENAI_KEY, OFF_USERNAME, OFF_PASSWORD)
    }
}
