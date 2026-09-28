package com.metaself.app.data.health

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.metaself.app.data.time.Now
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Which writing apps' walks do not count as workouts (D81), keyed by package. No app is named in code:
 * the owner switches an app off on "What the band sends", and its package is what is stored.
 */
interface WalkChoices {

    /** The packages whose WALK sessions are left out. Empty until one is switched off. */
    val uncounted: Flow<Set<String>>

    suspend fun setCounted(origin: String, counted: Boolean)
}

/**
 * The choice in the preferences DataStore the profile, the AI settings and the reminder share — one
 * key, a set of package names. Only a switched-off app is stored, so switching it on again forgets it.
 */
class DataStoreWalkChoices @Inject constructor(
    private val store: DataStore<Preferences>,
) : WalkChoices {

    override val uncounted: Flow<Set<String>> = store.data.map { it[KEY] ?: emptySet() }

    override suspend fun setCounted(origin: String, counted: Boolean) {
        store.edit { preferences ->
            val now = preferences[KEY] ?: emptySet()
            val next = if (counted) now - origin else now + origin
            if (next.isEmpty()) preferences.remove(KEY) else preferences[KEY] = next
        }
    }

    private companion object {
        val KEY = stringSetPreferencesKey("walks_not_counted")
    }
}

/** Switching an app's walks on or off, and bringing the stored summaries into line with it. */
fun interface WalkSwitch {
    suspend fun set(origin: String, counted: Boolean)
}

/**
 * Stores the choice, then summarises again every day holding one of that app's synced walks — all of
 * them, not only the band page's 30 days — so each day's workout count agrees with the choice before
 * the next copy. No Health Connect: every total is passed as failed, which keeps the one stored
 * (`RoomHealthStore.keepingStored`), and each workout's heart-rate figures are worked out again from
 * the stored readings.
 *
 * The store and the recount run as one step: under [NonCancellable], so leaving the page after the
 * choice is stored cannot skip its recount, and under a [Mutex], so two quick switches run one after
 * the other and the summaries end agreeing with the last choice stored.
 *
 * **Accepted:** a failure between the stored choice and the summaries leaves those days' counts as
 * they were until each day is next summarised; the caller logs it.
 */
class RecountingWalkSwitch(
    private val choices: WalkChoices,
    private val walkDays: suspend (origin: String) -> List<Long>,
    private val summarise: suspend (days: Set<Long>, totals: TotalsResult) -> Unit,
) : WalkSwitch {

    @Inject
    constructor(choices: WalkChoices, workouts: WorkoutDao, store: HealthStore, now: Now) : this(
        choices,
        workouts::syncedWalkDays,
        { days, totals -> store.summarise(days, totals, now()) },
    )

    private val oneAtATime = Mutex()

    override suspend fun set(origin: String, counted: Boolean) {
        withContext(NonCancellable) {
            oneAtATime.withLock {
                choices.setCounted(origin, counted)
                val days = walkDays(origin).toSet()
                if (days.isNotEmpty()) summarise(days, TotalsResult.ALL_FAILED)
            }
        }
    }
}
