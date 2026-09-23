package com.metaself.app.data.ai

import com.metaself.app.data.secret.SecretStore
import kotlinx.coroutines.flow.Flow

/**
 * The owner's API key, over the shared encrypted store.
 *
 * The encryption itself moved to [SecretStore] when a second credential arrived — the Open Food
 * Facts account — because two classes doing the same untestable thing to the same file is two
 * chances to get it wrong. This class keeps its own interface so that nothing above it noticed.
 */
class EncryptedApiKeyStore(private val secrets: SecretStore) : ApiKeyStore {

    override val key: Flow<String?> = secrets.watch(SecretStore.OPENAI_KEY)

    override suspend fun save(key: String) = secrets.save(SecretStore.OPENAI_KEY, key)

    override suspend fun clear() = secrets.clear(SecretStore.OPENAI_KEY)
}
