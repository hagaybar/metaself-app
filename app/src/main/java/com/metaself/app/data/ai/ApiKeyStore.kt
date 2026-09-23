package com.metaself.app.data.ai

import kotlinx.coroutines.flow.Flow

/**
 * Where the owner's API key lives.
 *
 * An interface because the real implementation needs the Android keystore, which Robolectric on the
 * development machine cannot provide. **Nothing in this app's test suite exercises the encrypted
 * store.** Every test that touches a key uses a fake, and the real one is verified exactly once —
 * by the owner, pressing Test in settings.
 *
 * That is why step 7 has a checkpoint in the middle rather than one verification at the end.
 */
interface ApiKeyStore {

    val key: Flow<String?>

    suspend fun save(key: String)

    suspend fun clear()
}
