package com.metaself.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.metaself.app.data.ai.AiSettingsStore
import com.metaself.app.data.ai.AiTimeouts
import com.metaself.app.data.ai.ApiKeyStore
import com.metaself.app.data.ai.DataStoreAiSettingsStore
import com.metaself.app.data.ai.DataStoreRequestProfileStore
import com.metaself.app.data.ai.EncryptedApiKeyStore
import com.metaself.app.data.ai.OpenAiFoodReviewer
import com.metaself.app.data.ai.OpenAiMealConversation
import com.metaself.app.data.ai.OpenAiMealEstimator
import com.metaself.app.data.ai.RequestProfileStore
import com.metaself.app.data.diagnostics.FileProblemLog
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.time.Today
import com.metaself.app.domain.ai.FoodReviewer
import com.metaself.app.domain.ai.MealConversationAsker
import com.metaself.app.domain.ai.MealEstimator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import com.metaself.app.data.secret.SecretStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
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
     * Every request to the model, apart from a conversation's final analysis (D58 §8.6), may take a
     * minute: to connect, to be written, for the answer to begin, and in all.
     *
     * The read timeout is the one that matters. Set only the call's, OkHttp's own ten-second read
     * timeout stayed in force, and a model that thinks before its first byte — a reasoning model
     * does — failed as unreachable while the call still had time left. That cost a paid request and
     * gave nothing, which is exactly what a generous timeout exists to prevent.
     */
    @Provides
    @Singleton
    fun provideHttpClient(): OkHttpClient = AiTimeouts.everyday(OkHttpClient.Builder()).build()

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

    /** What each model accepts, learned from its refusals (D57), beside the model's name. */
    @Provides
    @Singleton
    fun provideRequestProfileStore(store: DataStore<Preferences>): RequestProfileStore =
        DataStoreRequestProfileStore(store)

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
        profiles: RequestProfileStore,
        problems: ProblemLog,
    ): MealEstimator = OpenAiMealEstimator(keys, settings, client, profiles, problems)

    /** A conversation about a meal (D58): the same key, ceiling, client, profiles and log. */
    @Provides
    @Singleton
    fun provideMealConversationAsker(
        keys: ApiKeyStore,
        settings: AiSettingsStore,
        client: OkHttpClient,
        profiles: RequestProfileStore,
        problems: ProblemLog,
    ): MealConversationAsker = OpenAiMealConversation(keys, settings, client, profiles, problems)

    /** A food's review (D54): the same key, ceiling, client, profiles and log as the estimator. */
    @Provides
    @Singleton
    fun provideFoodReviewer(
        keys: ApiKeyStore,
        settings: AiSettingsStore,
        client: OkHttpClient,
        profiles: RequestProfileStore,
        problems: ProblemLog,
    ): FoodReviewer = OpenAiFoodReviewer(keys, settings, client, profiles, problems)
}
