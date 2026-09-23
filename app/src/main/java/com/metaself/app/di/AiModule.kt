package com.metaself.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.metaself.app.data.ai.AiSettingsStore
import com.metaself.app.data.ai.ApiKeyStore
import com.metaself.app.data.ai.DataStoreAiSettingsStore
import com.metaself.app.data.ai.EncryptedApiKeyStore
import com.metaself.app.data.ai.OpenAiMealEstimator
import com.metaself.app.data.diagnostics.FileProblemLog
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.time.Today
import com.metaself.app.domain.ai.MealEstimator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import com.metaself.app.data.secret.SecretStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Everything the model seam needs.
 *
 * The HTTP client is a singleton because OkHttp's own guidance is that one client with one
 * connection pool is what you want, and because this app makes at most a few calls a day.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    /**
     * Forty-five seconds.
     *
     * Long, deliberately: a model can take twenty, and a timeout that fires first turns a slow
     * answer into a failure the owner has to redo — which costs him a second call and the app a
     * second charge.
     */
    private const val CALL_TIMEOUT_SECONDS = 45L

    @Provides
    @Singleton
    fun provideHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideSecretStore(@ApplicationContext context: Context): SecretStore =
        SecretStore(context)

    @Provides
    @Singleton
    fun provideApiKeyStore(secrets: SecretStore): ApiKeyStore = EncryptedApiKeyStore(secrets)

    @Provides
    @Singleton
    fun provideAiSettingsStore(
        store: DataStore<Preferences>,
        today: Today,
    ): AiSettingsStore = DataStoreAiSettingsStore(store, today)

    @Provides
    @Singleton
    fun provideProblemLog(@ApplicationContext context: Context): ProblemLog =
        FileProblemLog(java.io.File(context.filesDir, "problems.log"))

    @Provides
    @Singleton
    fun provideMealEstimator(
        keys: ApiKeyStore,
        settings: AiSettingsStore,
        client: OkHttpClient,
        problems: ProblemLog,
    ): MealEstimator = OpenAiMealEstimator(keys, settings, client, problems)
}
