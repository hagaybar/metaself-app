package com.metaself.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.metaself.app.data.food.FoodDao
import com.metaself.app.data.food.FoodRepository
import com.metaself.app.data.food.RoomFoodRepository
import com.metaself.app.data.food.RoomSavedMealRepository
import com.metaself.app.data.food.RoomMealKeeper
import com.metaself.app.data.food.MealKeeper
import com.metaself.app.data.food.LoggedFoods
import com.metaself.app.data.food.SavedMealDao
import com.metaself.app.data.food.SavedMealRepository
import com.metaself.app.data.day.MealDao
import com.metaself.app.data.health.HealthBookkeepingDao
import com.metaself.app.data.health.HealthDayDao
import com.metaself.app.data.health.HealthReadingDao
import com.metaself.app.data.health.MovementCorrectionDao
import com.metaself.app.data.health.SleepDao
import com.metaself.app.data.health.WorkoutDao
import com.metaself.app.data.movement.HealthConnectSteps
import com.metaself.app.data.movement.StepSource
import com.metaself.app.data.day.MealRepository
import com.metaself.app.data.day.MIGRATION_4_5
import com.metaself.app.data.day.MIGRATION_5_6
import com.metaself.app.data.day.MetaSelfDatabase
import com.metaself.app.data.day.RoomMealRepository
import com.metaself.app.data.weight.RoomWeightRepository
import com.metaself.app.data.weight.WeightDao
import com.metaself.app.data.weight.WeightRepository
import com.metaself.app.data.backup.AutomaticBackup
import com.metaself.app.data.backup.BackupFiles
import com.metaself.app.data.backup.DailyBackup
import com.metaself.app.data.backup.ContentResolverBackupFiles
import com.metaself.app.data.backup.DataStoreSettingsSnapshot
import com.metaself.app.data.backup.SettingsSnapshot
import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.data.day.RoomDatabaseTransaction
import com.metaself.app.data.product.ProductDao
import com.metaself.app.data.profile.DataStoreProfileRepository
import com.metaself.app.data.reminder.AlarmReminderScheduler
import com.metaself.app.data.reminder.AndroidReminderNotifier
import com.metaself.app.data.reminder.ReminderNotifier
import com.metaself.app.data.reminder.DataStoreReminderStore
import com.metaself.app.data.reminder.ReminderScheduler
import com.metaself.app.data.reminder.ReminderStore
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.CurrentHour
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Today
import com.metaself.app.data.time.asCurrentYear
import com.metaself.app.data.time.systemHour
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import javax.inject.Singleton

/**
 * Where the app's one stored thing comes from.
 *
 * A single DataStore for the whole process: two instances over one file corrupt each other, and
 * DataStore throws rather than tolerating it.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    private const val PROFILE_STORE = "profile"
    private const val DATABASE_NAME = "metaself.db"

    @Provides
    @Singleton
    fun provideProfileDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile(PROFILE_STORE)
    }

    @Provides
    @Singleton
    fun provideProfileRepository(
        store: DataStore<Preferences>,
    ): ProfileRepository = DataStoreProfileRepository(store)

    /**
     * The one database for the process.
     *
     * No fallbackToDestructiveMigration. It is the line that turns a missing migration into
     * silently deleted food; a missing migration should stop the app and be fixed.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MetaSelfDatabase = Room.databaseBuilder(
        context,
        MetaSelfDatabase::class.java,
        DATABASE_NAME,
    ).addMigrations(
        MetaSelfDatabase.MIGRATION_1_2,
        MetaSelfDatabase.MIGRATION_2_3,
        MetaSelfDatabase.MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
    ).build()

    @Provides
    @Singleton
    fun provideReminderStore(
        store: DataStore<Preferences>,
    ): ReminderStore = DataStoreReminderStore(store)

    @Provides
    @Singleton
    fun provideStepSource(steps: HealthConnectSteps): StepSource = steps

    @Provides
    @Singleton
    fun provideDailyBackup(backup: AutomaticBackup): DailyBackup = backup

    @Provides
    @Singleton
    fun provideBackupFiles(
        files: ContentResolverBackupFiles,
    ): BackupFiles = files

    @Provides
    @Singleton
    fun provideDatabaseTransaction(
        transaction: RoomDatabaseTransaction,
    ): DatabaseTransaction = transaction

    /** Over the one DataStore above, which is what makes one snapshot cover every setting. */
    @Provides
    @Singleton
    fun provideSettingsSnapshot(
        snapshot: DataStoreSettingsSnapshot,
    ): SettingsSnapshot = snapshot

    @Provides
    @Singleton
    fun provideReminderNotifier(
        notifier: AndroidReminderNotifier,
    ): ReminderNotifier = notifier

    @Provides
    @Singleton
    fun provideReminderScheduler(
        scheduler: AlarmReminderScheduler,
    ): ReminderScheduler = scheduler

    @Provides
    fun provideProductDao(database: MetaSelfDatabase): ProductDao = database.productDao()

    @Provides
    fun provideMealDao(database: MetaSelfDatabase): MealDao = database.mealDao()

    @Provides
    fun provideFoodDao(database: MetaSelfDatabase): FoodDao = database.foodDao()

    @Provides
    fun provideSavedMealDao(database: MetaSelfDatabase): SavedMealDao = database.savedMealDao()

    @Provides
    fun provideWorkoutDao(database: MetaSelfDatabase): WorkoutDao = database.workoutDao()

    @Provides
    fun provideHealthReadingDao(database: MetaSelfDatabase): HealthReadingDao =
        database.healthReadingDao()

    @Provides
    fun provideSleepDao(database: MetaSelfDatabase): SleepDao = database.sleepDao()

    @Provides
    fun provideHealthDayDao(database: MetaSelfDatabase): HealthDayDao = database.healthDayDao()

    @Provides
    fun provideMovementCorrectionDao(database: MetaSelfDatabase): MovementCorrectionDao =
        database.movementCorrectionDao()

    @Provides
    fun provideHealthBookkeepingDao(database: MetaSelfDatabase): HealthBookkeepingDao =
        database.healthBookkeepingDao()

    @Provides
    @Singleton
    fun provideSavedMealRepository(
        database: MetaSelfDatabase,
        dao: SavedMealDao,
        foods: FoodDao,
        now: Now,
    ): SavedMealRepository = RoomSavedMealRepository(database, dao, foods, now)

    /** *Keep as a meal* without logging (D58 §5.2): foods, meal and parts in one transaction. */
    @Provides
    @Singleton
    fun provideMealKeeper(
        database: MetaSelfDatabase,
        loggedFoods: LoggedFoods,
        foods: FoodRepository,
        savedMeals: SavedMealRepository,
    ): MealKeeper = RoomMealKeeper(database, loggedFoods, foods, savedMeals)

    /**
     * The one door through which a food comes into existence.
     *
     * Bound to an interface for the same reason the meals are: what is behind it is expected to
     * change, and the screens should not have to.
     */
    @Provides
    @Singleton
    fun provideFoodRepository(
        database: MetaSelfDatabase,
        dao: FoodDao,
        now: Now,
    ): FoodRepository = RoomFoodRepository(database, dao, now)

    @Provides
    fun provideWeightDao(database: MetaSelfDatabase): WeightDao = database.weightDao()

    @Provides
    @Singleton
    fun provideWeightRepository(dao: WeightDao): WeightRepository = RoomWeightRepository(dao)

    @Provides
    @Singleton
    fun provideMealRepository(dao: MealDao): MealRepository = RoomMealRepository(dao)

    /** The only place in the running app that reads the calendar. */
    @Provides
    fun provideToday(): Today = Today { LocalDate.now() }

    /**
     * The moment, for the handful of records that are stamped rather than dated — when a food was
     * made, when one of its numbers came to be believed, when it was last edited.
     */
    @Provides
    fun provideNow(): Now = Now { System.currentTimeMillis() }

    /** Derived from [provideToday], so the app cannot hold two opinions about the date. */
    @Provides
    fun provideCurrentYear(today: Today): CurrentYear = today.asCurrentYear()

    @Provides
    fun provideCurrentHour(): CurrentHour = systemHour()
}
