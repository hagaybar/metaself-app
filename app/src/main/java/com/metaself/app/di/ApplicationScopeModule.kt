package com.metaself.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * A scope that lives as long as the process: for work a screen starts that must finish even if the
 * screen is left — a paid trainer request and the storing of its answer (D86, D87). Never for work
 * started without a tap (D84).
 *
 * A supervisor, so one failed job does not cancel the others; Default, because every call made in it
 * is main-safe and moves its own blocking work to IO.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object ApplicationScopeModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
