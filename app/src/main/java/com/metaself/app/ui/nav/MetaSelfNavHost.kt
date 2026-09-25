package com.metaself.app.ui.nav

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.res.stringResource
import com.metaself.app.R
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.ui.day.DayWording
import com.metaself.app.ui.food.ReviewActions
import com.metaself.app.ui.weight.WeightWording
import com.metaself.app.ui.screen.day.DayPager
import com.metaself.app.ui.screen.day.DayUiState
import com.metaself.app.ui.screen.day.DayViewModel
import com.metaself.app.ui.screen.entry.EntryEditorScreen
import com.metaself.app.ui.screen.entry.EntryFormState
import com.metaself.app.ui.screen.settings.SettingsScreen
import com.metaself.app.ui.screen.propose.ConversationActions
import com.metaself.app.ui.screen.propose.KeepOnlyActions
import com.metaself.app.ui.screen.propose.ProposalScreen
import com.metaself.app.ui.screen.record.RecordScreen
import com.metaself.app.ui.screen.record.RecordUiState
import com.metaself.app.ui.screen.mealbuilder.MealBuilderScreen
import com.metaself.app.ui.screen.mealbuilder.MealBuilderViewModel
import com.metaself.app.ui.screen.foods.FoodsViewModel
import com.metaself.app.ui.screen.food.FoodPageScreen
import com.metaself.app.ui.screen.food.FoodPageViewModel
import com.metaself.app.ui.screen.manager.ManagerScreen
import com.metaself.app.ui.screen.manager.ManagerViewModel
import com.metaself.app.ui.screen.manager.MealsViewModel
import com.metaself.app.ui.screen.repeat.RepeatScreen
import com.metaself.app.ui.screen.repeat.RepeatViewModel
import com.metaself.app.ui.screen.propose.ProposalViewModel
import androidx.health.connect.client.PermissionController
import com.metaself.app.data.movement.HealthConnectSteps
import com.metaself.app.ui.screen.scan.ScanScreen
import com.metaself.app.ui.screen.scan.ScanViewModel
import com.metaself.app.ui.screen.settings.SettingsViewModel
import java.time.LocalDate
import com.metaself.app.ui.screen.weight.WeightChartScreen
import com.metaself.app.ui.screen.weight.WeightEditorScreen
import com.metaself.app.ui.screen.weight.WeightFormState
import com.metaself.app.ui.screen.weight.WeightScreen
import com.metaself.app.ui.screen.weight.WeightViewModel

/**
 * The places this host can be.
 *
 * The profile is not one of them: it is the root's business, reached through `onEditProfile` (and
 * its editor through `onEditGoal`), for the same reason step 2 gave — it is chosen by data rather
 * than pushed onto a stack.
 */
sealed class Destination(val route: String) {
    data object Today : Destination("today")
    data object AddEntry : Destination("entry/add") {
        /** Carrying the words the owner already typed, so a failure never loses them. */
        fun withName(name: String): String =
            if (name.isBlank()) route else "entry/add?name=" + Uri.encode(name)
    }
    data object Weight : Destination("weight")
    data object Settings : Destination("settings") {
        /** Scrolled to the key: where the describe screen's "Add a key in settings" goes. */
        val atKey: String = "settings?at=key"
    }
    data object Describe : Destination("meal/describe") {
        /** Carrying the words already typed into the search, so a miss costs a tap, not a retype. */
        fun withWords(text: String): String =
            if (text.isBlank()) route else "meal/describe?text=" + Uri.encode(text)
    }
    data object Repeat : Destination("meal/repeat")
    data object Foods : Destination("foods") {
        /**
         * The pattern the list is registered under: the bare route, and a food to join from (D55
         * §5). A String, as [Record]'s logging is: `NavType` has no nullable `Long`.
         */
        const val registered: String = "foods?joinFrom={joinFrom}"

        /** The list opened picking a duplicate for one food, from a page with no list beneath it. */
        fun joiningFrom(foodId: Long): String = "foods?joinFrom=$foodId"

        /** True for a back stack entry that is the list, as the back stack names it: by [registered]. */
        fun isTheList(route: String?): Boolean = route == registered
    }

    /**
     * One food's own page (D55), for one food. The id is a required part of the path, as the
     * record's day is: a page of no particular food is not a thing that can be drawn. Where a row
     * in My foods and "Give this a portion" go.
     */
    data object Food : Destination("food/{foodId}") {
        fun of(foodId: Long): String = "food/$foodId"
    }
    data object BuildMeal : Destination("meal/build/{mealId}") {
        /** Zero means a meal that does not exist yet: he is starting one. */
        fun of(mealId: Long): String = "meal/build/$mealId"

        /**
         * Carrying the foods he chose in the list, so they arrive waiting for an amount.
         *
         * Plain commas rather than `Uri.encode`: the ids are digits, so there is nothing to escape,
         * and a route a person can read is a route a person can debug. With nothing chosen this is
         * the bare route, which is where the plain "Build a meal" button still lands.
         */
        fun of(mealId: Long, foodIds: List<Long>): String =
            if (foodIds.isEmpty()) of(mealId) else of(mealId) + "?foods=" + foodIds.joinToString(",")
    }
    data object Scan : Destination("meal/scan")
    data object LogWeight : Destination("weight/log")
    data object WeightChart : Destination("weight/chart")
    data object EditWeight : Destination("weight/edit/{epochDay}") {
        fun of(epochDay: Long): String = "weight/edit/$epochDay"
    }
    data object EditEntry : Destination("entry/edit/{itemId}") {
        fun of(itemId: Long): String = "entry/edit/$itemId"
    }
    /**
     * The day's record (D50), for one day, optionally opened at one logging.
     *
     * **The logging is a String, and that is not a slip.** `NavType` has no nullable `Long` — there
     * is `LongType` and nothing that can carry "no id" — so an optional id travels as text and is
     * read back with `toLongOrNull`. [BuildMeal] hit the same wall first and declares its meal the
     * same way; two different answers to one problem in one file is how the second one rots.
     *
     * The day itself is a required part of the path rather than an optional argument: a record of no
     * particular day is not a thing this screen can draw.
     */
    data object Record : Destination("day/record/{epochDay}") {
        fun of(epochDay: Long, openAtMealId: Long? = null): String =
            if (openAtMealId == null) {
                "day/record/$epochDay"
            } else {
                "day/record/$epochDay?mealId=$openAtMealId"
            }
    }

    companion object {
        val start: Destination = Today
    }
}

/**
 * Describe a meal, as the back stack knows it: the bare route plus its optional argument.
 *
 * Written once because it is asked two different questions — what to register, and whether that
 * screen is still the one on top when the naming sheet says it has finished. The second only works
 * against the pattern the stack holds, which is this whole string and not the bare route.
 */
private val describeRoute = Destination.Describe.route + "?text={text}"

/**
 * The day, and the item editor that serves both adding and correcting.
 *
 * The day view model is hoisted here rather than fetched inside each destination. `hiltViewModel()`
 * inside a `composable` block is scoped to that back stack entry, so the editor would get its own
 * instance, log to a default day, and be maddening to debug.
 */
@Composable
fun MetaSelfNavHost(
    onEditProfile: () -> Unit,
    /** The profile editor, opened straight away: where the goal weight and weekly rate are set. */
    onEditGoal: () -> Unit,
    dayViewModel: DayViewModel = hiltViewModel(),
    weightViewModel: WeightViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Destination.start.route) {

        composable(Destination.Today.route) {
            DayPager(
                viewModel = dayViewModel,
                onOpenProfile = onEditProfile,
                // Correcting a row is no longer reached from the day: the day draws parts of the
                // clock (D51) and the rows live on the record screen (D50), which is where the way
                // to the editor goes. The editor itself, and its route below, are untouched.
                //
                // Every part of the clock is a door, and it opens at the logging that part begins
                // with — which is what "it opens at the meal that was tapped" comes to once the
                // day's line covers several loggings rather than one.
                onOpenRecord = { epochDay, openAtMealId ->
                    navController.navigate(Destination.Record.of(epochDay, openAtMealId))
                },
                onAdd = { navController.navigate(Destination.AddEntry.route) },
                onDescribe = { navController.navigate(Destination.Describe.route) },
                onRepeat = { navController.navigate(Destination.Repeat.route) },
                onScan = { navController.navigate(Destination.Scan.route) },
                onOpenWeight = { navController.navigate(Destination.Weight.route) },
                onOpenSettings = { navController.navigate(Destination.Settings.route) },
                onOpenManager = { navController.navigate(Destination.Foods.route) },
            )
        }

        composable(Destination.Weight.route) {
            val weightState by weightViewModel.state.collectAsStateWithLifecycle()
            val canUndoWeight by weightViewModel.canUndo.collectAsStateWithLifecycle()
            val weightFailed by weightViewModel.failed.collectAsStateWithLifecycle()
            val justLogged by weightViewModel.justLogged.collectAsStateWithLifecycle()
            WeightScreen(
                state = weightState,
                todayEpochDay = weightViewModel.todayEpochDay,
                justLogged = justLogged,
                onAdd = {
                    weightViewModel.forgetJustLogged()
                    navController.navigate(Destination.LogWeight.route)
                },
                onEdit = { reading ->
                    weightViewModel.forgetJustLogged()
                    navController.navigate(Destination.EditWeight.of(reading.epochDay))
                },
                onDelete = weightViewModel::delete,
                canUndo = canUndoWeight,
                onUndoDelete = weightViewModel::undoDelete,
                failed = weightFailed,
                onDismissFailure = weightViewModel::dismissFailure,
                onRange = weightViewModel::setRange,
                onOpenChart = { navController.navigate(Destination.WeightChart.route) },
                // The editor is the root's, like the profile (see `Destination`); the root keeps
                // this screen where it is underneath, so closing the editor comes back here.
                onChangeGoal = {
                    weightViewModel.forgetJustLogged()
                    onEditGoal()
                },
                onBack = {
                    weightViewModel.forgetJustLogged()
                    navController.popBackStack()
                },
            )
        }

        composable(Destination.WeightChart.route) {
            // The same view model the weight screen uses, so the two cannot disagree about which
            // range is showing.
            val weightState by weightViewModel.state.collectAsStateWithLifecycle()
            WeightChartScreen(
                state = weightState,
                todayEpochDay = weightViewModel.todayEpochDay,
                onRange = weightViewModel::setRange,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Destination.LogWeight.route) {
            val weightState by weightViewModel.state.collectAsStateWithLifecycle()
            val loggedTemplate = stringResource(R.string.weight_logged)
            WeightEditorScreen(
                initial = WeightFormState(epochDay = weightViewModel.todayEpochDay),
                todayEpochDay = weightViewModel.todayEpochDay,
                isEdit = false,
                existingDays = weightState.readings.map { it.epochDay }.toSet(),
                onSave = { reading ->
                    weightViewModel.log(
                        kg = reading.kg,
                        epochDay = reading.epochDay,
                        confirmation =
                            confirmation(loggedTemplate, reading, weightViewModel.todayEpochDay),
                    )
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(
            route = Destination.EditWeight.route,
            arguments = listOf(navArgument("epochDay") { type = NavType.LongType }),
        ) { entry ->
            val epochDay = entry.arguments?.getLong("epochDay") ?: return@composable
            val weightState by weightViewModel.state.collectAsStateWithLifecycle()
            val changedTemplate = stringResource(R.string.weight_updated)
            val existing = weightState.readings.firstOrNull { it.epochDay == epochDay }

            if (existing == null) {
                // Deleted from under the editor. Going back is the only honest thing to do.
                LaunchedEffect(epochDay) { navController.popBackStack() }
            } else {
                WeightEditorScreen(
                    initial = WeightFormState.from(existing),
                    todayEpochDay = weightViewModel.todayEpochDay,
                    isEdit = true,
                    existingDays = emptySet(),
                    onSave = { reading ->
                        weightViewModel.log(
                            kg = reading.kg,
                            epochDay = reading.epochDay,
                            confirmation = confirmation(
                                changedTemplate,
                                reading,
                                weightViewModel.todayEpochDay,
                            ),
                        )
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() },
                )
            }
        }

        // Registered with where to open as an optional argument and still reachable by the bare
        // route the menu uses, exactly as the food manager is.
        composable(
            route = Destination.Settings.route + "?at={at}",
            arguments = listOf(
                navArgument("at") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val clipboard = LocalClipboardManager.current
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()

            // From Android 13 a notification nobody permitted is a notification nobody sees. Asked
            // at the moment the owner turns the reminder on, which is the only moment it means
            // anything to him — and the setting is saved either way, so a refusal leaves a switch
            // that is on and silent rather than a switch that quietly turned itself off.
            //
            // Kept here rather than inside the settings screen so that the screen stays a pure
            // composable a render test can draw without an activity underneath it.
            val askForNotifications = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { }

            // Android's own document picker, so the app needs no storage permission and the owner
            // puts the file wherever he already keeps things rather than wherever the app decided.
            val chooseWhereToSave = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri -> uri?.let(settingsViewModel::exportTo) }

            val chooseWhatToRestore = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri -> uri?.let(settingsViewModel::offerRestoreFrom) }

            // A whole folder rather than a file, and a permission that survives a reboot. This is
            // what lets the copy be automatic with no Cloud project and no account (D26).
            // Health Connect issues its own permission screen rather than Android's. The result is
            // whatever the owner chose there, so the settings line is asked to look again rather
            // than assuming it got what it wanted.
            val askForSteps = rememberLauncherForActivityResult(
                PermissionController.createRequestPermissionResultContract(),
            ) { settingsViewModel.refreshSteps() }

            LaunchedEffect(Unit) {
                settingsViewModel.refreshSteps()
                settingsViewModel.refreshWindow()
            }

            // Google's consent screen, shown once. A view model cannot start an activity, so it
            // hands the screen up here; when it comes back, the write is simply tried again.
            val consent by settingsViewModel.consent.collectAsStateWithLifecycle()
            val showConsent = rememberLauncherForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult(),
            ) { result ->
                // Retried ONCE, and told it is a retry. Retrying blindly is what turned a
                // configuration problem into an endless account picker.
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    settingsViewModel.driveNow(afterConsent = true)
                } else {
                    settingsViewModel.consentDeclined()
                }
            }

            LaunchedEffect(consent) {
                consent?.let {
                    showConsent.launch(IntentSenderRequest.Builder(it).build())
                    settingsViewModel.consentShown()
                }
            }

            val chooseBackupFolder = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree(),
            ) { uri -> uri?.let(settingsViewModel::useBackupFolder) }
            SettingsScreen(
                state = settingsState,
                onSaveKey = settingsViewModel::saveKey,
                onClearKey = settingsViewModel::clearKey,
                onSetModel = settingsViewModel::setModel,
                onSetCeiling = settingsViewModel::setDailyCeiling,
                onTest = settingsViewModel::test,
                onSendReminderNow = settingsViewModel::sendReminderNow,
                onSaveOffAccount = settingsViewModel::saveOffAccount,
                onClearOffAccount = settingsViewModel::clearOffAccount,
                onSetWindow = settingsViewModel::setEatingWindow,
                onSetRatio = settingsViewModel::setMeasuredWindow,
                onClearWindow = settingsViewModel::clearEatingWindow,
                onConnectSteps = { askForSteps.launch(HealthConnectSteps.PERMISSIONS) },
                onPickBackupFolder = { chooseBackupFolder.launch(null) },
                onForgetBackupFolder = settingsViewModel::forgetBackupFolder,
                onBackUpNow = settingsViewModel::backUpNow,
                onSetDrive = settingsViewModel::setDriveBackup,
                onDriveNow = settingsViewModel::driveNow,
                onExport = { chooseWhereToSave.launch(backupFileName()) },
                onRestore = { chooseWhatToRestore.launch(arrayOf("application/json", "*/*")) },
                onConfirmRestore = settingsViewModel::confirmRestore,
                onCancelRestore = settingsViewModel::cancelRestore,
                onDismissBackupMessage = settingsViewModel::dismissBackupMessage,
                onSetReminder = { reminder ->
                    settingsViewModel.setReminder(reminder)
                    if (reminder.enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onCopyProblems = {
                    clipboard.setText(AnnotatedString(settingsViewModel.problemsAsText()))
                },
                onClearProblems = settingsViewModel::clearProblems,
                onDismissFailure = settingsViewModel::dismissFailure,
                onBack = { navController.popBackStack() },
                openAtKey = entry.arguments?.getString("at") == "key",
            )
        }

        composable(Destination.Scan.route) {
            val scanViewModel: ScanViewModel = hiltViewModel()
            val scanState by scanViewModel.state.collectAsStateWithLifecycle()
            val scanFailed by scanViewModel.failed.collectAsStateWithLifecycle()
            val context = LocalContext.current

            var cameraAllowed by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED,
                )
            }
            val askForCamera = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted -> cameraAllowed = granted }

            // Asked at the point of use rather than at startup: a camera permission requested by an
            // app that has not yet explained why it wants one is a permission that gets refused.
            LaunchedEffect(Unit) {
                if (!cameraAllowed) askForCamera.launch(Manifest.permission.CAMERA)
            }

            ScanScreen(
                state = scanState,
                hasCamera = cameraAllowed,
                onBarcode = scanViewModel::onBarcodeRead,
                onSetGrams = scanViewModel::setGrams,
                onAddByHand = scanViewModel::addByHand,
                onSetForm = scanViewModel::setForm,
                onSaveTyped = scanViewModel::saveTypedProduct,
                onContribute = scanViewModel::contribute,
                onSave = {
                    val item = scanViewModel.save()
                    val product = scanViewModel.scannedProduct()
                    // Leave only when a row went on the day. Leaving with nothing logged looked
                    // exactly like success (issue #32); staying, the screen says why (D42).
                    if (item != null && product != null) {
                        dayViewModel.logScanned(item, product)
                        navController.popBackStack()
                    }
                },
                onScanAgain = scanViewModel::scanAgain,
                // A miss costs one tap: straight into the description, carrying nothing, because a
                // miss has no product name to carry — Open Food Facts returned no product, and
                // ScanUiState.NotFound holds only the barcode.
                onDescribeInstead = {
                    navController.popBackStack()
                    navController.navigate(Destination.Describe.route)
                },
                onBack = { navController.popBackStack() },
                failed = scanFailed,
                onDismissFailure = scanViewModel::dismissFailure,
            )
        }

        composable(Destination.Repeat.route) { here ->
            val repeatViewModel: RepeatViewModel = hiltViewModel()
            val repeatable by repeatViewModel.state.collectAsStateWithLifecycle()

            RepeatScreen(
                state = repeatable,
                // Nothing here matched, so a new food is the right outcome. The search is popped
                // first: it has just said it has nothing, and leaving it behind the description
                // only makes the owner walk back through it.
                onDescribe = { words ->
                    navController.popBackStack()
                    navController.navigate(Destination.Describe.withWords(words))
                },
                onManageFoods = { navController.navigate(Destination.Foods.route) },
                // Straight to the food's own page (D55 §4). Back, or Save, comes back here with the
                // question still open: the food is observed, so the portion he gave it is on offer
                // when he returns, and a food hidden, deleted or joined away closes the question.
                // Once for a double tap: only while this screen is the one in front.
                onGivePortion = { foodId ->
                    here.ifResumed { navController.navigate(Destination.Food.of(foodId)) }
                },
                onBuildMeal = { navController.navigate(Destination.BuildMeal.of(0)) },
                onEditMeal = { mealId -> navController.navigate(Destination.BuildMeal.of(mealId)) },
                // Picking a food no longer logs it: it asks how much, because the food is one entry
                // now rather than one per morning and has no amount of its own to repeat.
                onPickFood = repeatViewModel::beginChoosing,
                onCountAs = repeatViewModel::countAs,
                onSetAmount = repeatViewModel::setAmount,
                onCancelChoosing = repeatViewModel::cancelChoosing,
                onLogChosen = {
                    repeatViewModel.chosen()?.let { dayViewModel.log(it) }
                    navController.popBackStack()
                },
                onShowTab = repeatViewModel::showTab,
                onSearch = repeatViewModel::search,
                onBeginAdjusting = repeatViewModel::beginAdjusting,
                onSetComponentAmount = repeatViewModel::setComponentAmount,
                onStepComponent = repeatViewModel::stepComponent,
                onRemoveComponent = repeatViewModel::removeComponent,
                onCancelAdjusting = repeatViewModel::cancelAdjusting,
                // The only way this screen logs a meal: a tap on its row opens it, and this is the
                // press that writes (public issue #21). Straight to the day being looked at. No
                // model, no network, no waiting.
                onLogAdjusted = {
                    repeatViewModel.adjusted()?.let(dayViewModel::logSavedMeal)
                    navController.popBackStack()
                },
                // The adjuster's own search and amount step. None of these writes anything: what
                // is put in joins today's rows, and reaches the record only through Log it above.
                onBeginAddingToMeal = repeatViewModel::beginAddingToMeal,
                onSearchToAdd = repeatViewModel::searchToAdd,
                onStopAddingToMeal = repeatViewModel::stopAddingToMeal,
                onPickToAdd = repeatViewModel::pickToAdd,
                onCountAddedAs = repeatViewModel::countAddedAs,
                onSetAddedAmount = repeatViewModel::setAddedAmount,
                onDropPicked = repeatViewModel::dropPicked,
                onPutItIn = repeatViewModel::putItIn,
                onBack = { navController.popBackStack() },
            )
        }

        // Registered with the chosen foods as an optional argument, and still reachable by the bare
        // route the "Build a meal" button navigates to — exactly as the describe destination is. The
        // block reads nothing from the entry: the view model picks the ids up through the back stack
        // entry's saved state, before its first composition, which is the only moment early enough
        // for the waiting rows to be drawn.
        // One registration serves both "meal/build/0" and "meal/build/0?foods=1,2", which is what
        // the optional argument is for.
        //
        // **Known coverage gap, stated rather than hidden**: nothing tests that it matches both.
        // There is no nav-host rendering harness in this repo — `MetaSelfNavHostRenderTest` is
        // deliberately JUnit 5 and renders nothing — and adding a Robolectric render of the whole
        // host to cover one route argument is the same trade step 1 refused for its menu item. The
        // route strings themselves are tested (`DestinationRouteTest`) and what the builder does
        // with the argument is tested (`MealBuilderViewModelTest`); only the matching in between is
        // checked by hand on the phone.
        composable(
            route = Destination.BuildMeal.route + "?foods={foods}",
            arguments = listOf(
                navArgument("mealId") { type = NavType.StringType },
                navArgument("foods") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            val builderViewModel: MealBuilderViewModel = hiltViewModel()
            val builderState by builderViewModel.state.collectAsStateWithLifecycle()

            MealBuilderScreen(
                state = builderState,
                onSetName = builderViewModel::setName,
                onName = builderViewModel::name,
                onRename = builderViewModel::rename,
                onSearch = builderViewModel::search,
                onBeginAdding = builderViewModel::beginAdding,
                onCountAs = builderViewModel::countAs,
                onSetAmount = builderViewModel::setAmount,
                onConfirmAdding = builderViewModel::confirmAdding,
                onCancelAdding = builderViewModel::cancelAdding,
                onSetPendingAmount = builderViewModel::setPendingAmount,
                onCountPendingAs = builderViewModel::countPendingAs,
                onConfirmPending = builderViewModel::confirmPending,
                onDropPending = builderViewModel::dropPending,
                onRemove = builderViewModel::remove,
                onMove = builderViewModel::move,
                onChangePart = builderViewModel::beginChanging,
                onChangeFood = builderViewModel::beginChangingFood,
                onBeginCreatingFood = builderViewModel::beginCreatingFood,
                onSetNewFood = builderViewModel::setNewFoodForm,
                onCreateFood = builderViewModel::createFood,
                onCancelCreatingFood = builderViewModel::cancelCreatingFood,
                newFoodReview = ReviewActions(
                    onReview = builderViewModel::reviewNewFood,
                    onPutBack = builderViewModel::putBackNewFood,
                    onAcceptAndSave = builderViewModel::acceptAndCreateFood,
                    onCancel = builderViewModel::cancelNewFoodReview,
                    onDismiss = builderViewModel::dismissNewFoodReview,
                ),
                // Left only once the meal is gone, so a delete that fails stays on screen to say so.
                // And only from this screen: the delete answers later, and Back may have been
                // pressed in between, when a pop would take the screen underneath with it.
                onDelete = {
                    val here = navController.currentBackStackEntry
                    builderViewModel.delete {
                        if (navController.currentBackStackEntry == here) navController.popBackStack()
                    }
                },
                onDismissRefusal = builderViewModel::dismissRefusal,
                onBack = { navController.popBackStack() },
            )
        }

        // The food list's own destination now holds both lists, a tab each. The route keeps its
        // name: it is where "Fix or join up your foods" has always gone, and that link still lands
        // on the food list because the manager opens on it.
        //
        // BACK GOES WHERE HE CAME FROM, not always to the day. The design first said "never into
        // the logging screen", written when the menu was to be the only way in; with the logging
        // screen's link kept, returning to the day would throw away a half-finished log he stepped
        // out of to fix a duplicate, and would be a back arrow that skips a screen. Design §3.1
        // corrected to match. From the menu this is the day anyway.
        //
        // Registered with a food to join from as an optional argument (D55 §5), still reachable by
        // the bare route, as the builder's foods are. What a food's page leaves for the list — a food
        // to join from, a food just hidden — arrives in this entry's own saved state and is carried
        // to the view model once (TakeFromFoodPage says why it has to be carried).
        composable(
            route = Destination.Foods.registered,
            arguments = listOf(
                navArgument(FoodsViewModel.JOIN_FROM) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { here ->
            val managerViewModel: ManagerViewModel = hiltViewModel()
            val tab by managerViewModel.tab.collectAsStateWithLifecycle()
            val foodsViewModel: FoodsViewModel = hiltViewModel()
            val foodsState by foodsViewModel.state.collectAsStateWithLifecycle()
            val mealsViewModel: MealsViewModel = hiltViewModel()
            val mealsState by mealsViewModel.state.collectAsStateWithLifecycle()

            TakeFromFoodPage(
                results = here.savedStateHandle,
                onJoinFrom = foodsViewModel::beginJoiningFrom,
                onHidden = foodsViewModel::sayHidden,
            )

            ManagerScreen(
                tab = tab,
                onShowTab = managerViewModel::showTab,
                foods = foodsState,
                onSearch = foodsViewModel::search,
                onShowOnlyPortions = foodsViewModel::showOnlyPortions,
                onShowHidden = foodsViewModel::showHidden,
                // A food opens its own page (D55). The list stays beneath it with its search, its
                // filters and its scroll, so Back finds it where it was. Once for a double tap: only
                // while the list is the screen in front.
                onOpen = { foodId ->
                    here.ifResumed {
                        foodsViewModel.openingAFood()
                        navController.navigate(Destination.Food.of(foodId))
                    }
                },
                onMergeInto = foodsViewModel::mergeInto,
                onConfirmMerging = foodsViewModel::confirmJoining,
                onCancelMerging = foodsViewModel::cancelMerging,
                onDismissRefusal = foodsViewModel::dismissRefusal,
                onShowAgain = foodsViewModel::showAgain,
                onDismissHidden = foodsViewModel::dismissHidden,
                onBeginChoosing = foodsViewModel::beginChoosing,
                onToggleChosen = foodsViewModel::toggleChosen,
                onClearChoosing = foodsViewModel::clearChoosing,
                // The builder opens on a new meal carrying the foods he chose, and the choice is
                // spent: it has become the thing he is now building, and a list still ticked behind
                // the builder would offer to make the same meal a second time.
                //
                // In the order he ticked them, which is what the builder says it receives. The
                // choice is a set that keeps its insertion order, so this hands it over as it
                // stands; sorting it here put them in order of id — the order the foods happen to
                // have been created in, which is nothing he can see.
                onMakeMeal = {
                    navController.navigate(
                        Destination.BuildMeal.of(0, foodsState.chosen.toList()),
                    )
                    foodsViewModel.clearChoosing()
                },
                // Joining hands off to the merge the screen already has rather than doing it here,
                // because a merge is not reversible and which of the two survives has to be on
                // screen before it happens. The older of the two is proposed as the survivor, and
                // he answers the one join question both ways in end on — Join them or Not now (D36).
                // Both ids, resolved by id and not out of the filtered list, and the choice
                // held until the join is done or refused — all of which is the view model's, where
                // it can be tested. A lambda here could only see what is on screen.
                onJoinChosen = foodsViewModel::joinChosen,
                meals = mealsState,
                onBuildMeal = { navController.navigate(Destination.BuildMeal.of(0)) },
                onEditMeal = { mealId -> navController.navigate(Destination.BuildMeal.of(mealId)) },
                onDismissMealsFailure = mealsViewModel::dismissFailure,
                onBack = { navController.popBackStack() },
            )
        }

        // One food's own page (D55). The page never navigates: it says why it is finished, once,
        // and this leaves it and says so back. Only from this page — the answer that closes it can
        // land after Back was pressed, when a pop would take the screen underneath with it (the
        // builder's guard).
        //
        // Where it goes is FoodPageExit's decision: back, leaving the list beneath a food to join
        // from or a food just hidden (§5, §6); or, for a join with no list beneath — he came from
        // Give this a portion — the list in its place, picking, so Back from there is Add something.
        composable(
            route = Destination.Food.route,
            arguments = listOf(navArgument(FoodPageViewModel.FOOD_ID) { type = NavType.LongType }),
        ) { here ->
            val pageViewModel: FoodPageViewModel = hiltViewModel()
            val page by pageViewModel.state.collectAsStateWithLifecycle()

            LaunchedEffect(page.closing) {
                val closing = page.closing ?: return@LaunchedEffect
                if (navController.currentBackStackEntry == here) {
                    val below = navController.previousBackStackEntry
                    val listBelow = Destination.Foods.isTheList(below?.destination?.route)
                    when (val exit = FoodPageExit.of(closing, listBelow)) {
                        is FoodPageExit.Back -> {
                            below?.let { entry -> exit.result?.leaveIn(entry.savedStateHandle) }
                            navController.popBackStack()
                        }
                        is FoodPageExit.ToList -> navController.navigate(exit.route) {
                            popUpTo(here.destination.id) { inclusive = true }
                        }
                    }
                }
                pageViewModel.closed()
            }

            FoodPageScreen(
                state = page,
                onSetForm = pageViewModel::setForm,
                onSave = pageViewModel::save,
                onHide = pageViewModel::hide,
                onUnhide = pageViewModel::unhide,
                // Asks, or refuses where he pressed it; only the question's Delete deletes (D36).
                onDelete = pageViewModel::askToDelete,
                onConfirmDeleting = pageViewModel::confirmDeleting,
                onCancelDeleting = pageViewModel::cancelDeleting,
                onBeginJoining = pageViewModel::beginJoining,
                onDismissRefusal = pageViewModel::dismissRefusal,
                review = ReviewActions(
                    onReview = pageViewModel::review,
                    onPutBack = pageViewModel::putBack,
                    onAcceptAndSave = pageViewModel::acceptAndSave,
                    onCancel = pageViewModel::cancelReview,
                    onDismiss = pageViewModel::dismissReview,
                ),
                // Leave it alone and the back arrow: nothing is saved but by Save (§2). A second
                // press pops nothing, rather than the screen beneath.
                onBack = { navController.popFrom(here) },
            )
        }

        // Registered with the optional argument but still reachable by the bare route, exactly as
        // the manual editor already is. The block reads nothing from the entry: the view model
        // picks the words up through the back stack entry's saved state, before its first
        // composition, which is the only moment early enough for the text field to show them.
        composable(
            route = describeRoute,
            arguments = listOf(
                navArgument("text") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            val proposeViewModel: ProposalViewModel = hiltViewModel()
            val proposeState by proposeViewModel.state.collectAsStateWithLifecycle()

            // The day's own answers about the rows it has just written, for the naming sheet drawn
            // over this screen (D46, issue #24). Read the way the day screen reads them, because it
            // is the day screen's sheet: the chosen rows in the day's order, whether the day being
            // looked at is today, and why nothing was made if it was refused.
            val dayState by dayViewModel.state.collectAsStateWithLifecycle()
            val ready = dayState as? DayUiState.Ready
            val chosenRows = ready?.let { day ->
                day.meals.flatMap { it.items }.filter { it.id in day.chosen }
            }.orEmpty()

            ProposalScreen(
                state = proposeState,
                description = proposeViewModel.description,
                onDescribe = proposeViewModel::describe,
                onSetAmount = proposeViewModel::setAmount,
                onStep = proposeViewModel::step,
                onOpenWorth = proposeViewModel::openWorth,
                onSetWorthBox = proposeViewModel::setWorthBox,
                onCloseWorth = proposeViewModel::closeWorth,
                onUseYourFood = proposeViewModel::useYourFood,
                onUseEstimate = proposeViewModel::useEstimate,
                onCountInFoodUnit = proposeViewModel::countInFoodUnit,
                onRemove = proposeViewModel::remove,
                onTellItMore = proposeViewModel::tellItMore,
                conversation = ConversationActions(
                    onAcceptQuestions = proposeViewModel::acceptQuestions,
                    onBestGuess = proposeViewModel::bestGuess,
                    onAnswer = proposeViewModel::answer,
                    onEnough = proposeViewModel::enough,
                    onStepBack = proposeViewModel::back,
                    onRetry = proposeViewModel::retry,
                    onBestGuessSoFar = proposeViewModel::bestGuessSoFar,
                ),
                fromMyMeals = false,
                keepOnly = KeepOnlyActions(
                    onOpen = proposeViewModel::openKeepOnly,
                    // Kept, and nothing logged: he goes to where the meal is (D58 §5.3).
                    onConfirm = { name ->
                        proposeViewModel.keepOnly(name) {
                            proposeViewModel.startOver()
                            navController.popBackStack()
                            navController.navigate(Destination.Foods.route)
                        }
                    },
                    onCancel = proposeViewModel::closeKeepOnly,
                    onLogInstead = {
                        dayViewModel.logMeal(proposeViewModel.accepted())
                        proposeViewModel.startOver()
                        navController.popBackStack()
                    },
                ),
                onSave = {
                    dayViewModel.logMeal(proposeViewModel.accepted())
                    proposeViewModel.startOver()
                    navController.popBackStack()
                },
                // Saved first and named afterwards (D46(b)): this writes the rows and leaves exactly
                // them chosen, and nothing about a meal is attempted until he confirms a name. The
                // answer is started over once the rows are on the day, so what is there cannot be
                // accepted a second time from a screen he comes back to — and not before, because a
                // write that throws leaves him the answer to try again, with the failure beside it.
                onKeepAsMeal = {
                    dayViewModel.logMealAndChoose(proposeViewModel.accepted()) {
                        proposeViewModel.startOver()
                    }
                },
                // The day's own act, unchanged: one set of rules about what a part is worth and
                // which rows can join, and one refusal when they cannot.
                onNameMeal = dayViewModel::makeMealFromChosen,
                // Not now: the rows stay logged, and the day stops offering them as a meal.
                onGiveUpNaming = dayViewModel::clearChoosing,
                // Made, or given up on — either way the rows are on the day and that is where he
                // goes. Popping before the sheet has closed would take it off the screen mid-name.
                //
                // Only while this screen is still the one on top. The pop comes from an effect, and
                // an effect can land after Back has already taken him off it; an unconditional pop
                // then takes the day with it and leaves him looking at nothing.
                onKeepingDone = {
                    if (navController.currentBackStackEntry?.destination?.route == describeRoute) {
                        navController.popBackStack()
                    }
                },
                chosenRows = chosenRows,
                isToday = ready?.isToday ?: true,
                // An action that threw goes in the refusal's place, as it does on the day.
                refusal = ready?.refusal ?: ready?.failed?.let { stringResource(it.sentence) },
                // Decision D8: whatever went wrong, the words are not lost — they arrive in the
                // manual editor as the item's name, ready to have numbers put beside them.
                onTypeItMyself = {
                    navController.popBackStack()
                    navController.navigate(
                        Destination.AddEntry.withName(proposeViewModel.description),
                    )
                },
                // Forward, not back: the describe screen stays on the stack underneath settings,
                // its view model with it, so Back from settings lands on his words (public
                // issue #11).
                onAddKey = {
                    proposeViewModel.leaveToAddKey()
                    navController.navigate(Destination.Settings.atKey)
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(
            route = Destination.AddEntry.route + "?name={name}",
            arguments = listOf(
                navArgument("name") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            EntryEditor(
                initial = EntryFormState(name = entry.arguments?.getString("name").orEmpty()),
                isEdit = false,
                onSave = { item -> dayViewModel.log(item) },
                onDone = { navController.popBackStack() },
            )
        }

        // The day's record (D50). Registered with the logging to open at as an optional argument and
        // still reachable by the bare route, exactly as the describe screen and the meal builder
        // are: one registration serves "day/record/20699" and "day/record/20699?mealId=7".
        composable(
            route = Destination.Record.route + "?mealId={mealId}",
            arguments = listOf(
                navArgument("epochDay") { type = NavType.LongType },
                // `NavType` has no nullable `Long`; see `Destination.Record`.
                navArgument("mealId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val epochDay = entry.arguments?.getLong("epochDay") ?: return@composable
            val openAtMealId = entry.arguments?.getString("mealId")?.toLongOrNull()
            val dayState by dayViewModel.state.collectAsStateWithLifecycle()
            val todayEpochDay by dayViewModel.calendarToday.collectAsStateWithLifecycle()
            val canUndo by dayViewModel.canUndo.collectAsStateWithLifecycle()
            val ready = dayState as? DayUiState.Ready

            when {
                // The day has not been read yet. Nothing is drawn and nothing is popped: the state
                // is a moment from arriving, and popping here would close the screen he just opened.
                ready == null -> Unit

                // The view model is on a different day from the one this screen was opened for, so
                // the rows it would draw are not the rows this route names — and every "Edit" on
                // them would bounce out of the editor, which resolves a row against the day the
                // view model has selected. Reachable only by the back stack being restored after
                // the process was killed, with the pager gone and the selected day back at today.
                // Going back is the only honest thing to do, exactly as the entry editor does when
                // the row it was opened on has gone.
                ready.epochDay != epochDay ->
                    LaunchedEffect(epochDay) { navController.popBackStack() }

                else -> RecordScreen(
                    state = RecordUiState.of(ready, todayEpochDay),
                    canUndo = canUndo,
                    openAtMealId = openAtMealId,
                    onToggleMeal = dayViewModel::toggleMeal,
                    // The editor that already exists, reached from here instead of from the day.
                    onEditItem = { item ->
                        navController.navigate(Destination.EditEntry.of(item.id))
                    },
                    onDeleteItem = dayViewModel::deleteItem,
                    onUndoDelete = dayViewModel::undoDelete,
                    onSetEatenAt = dayViewModel::setEatenAt,
                    onBeginChoosing = dayViewModel::beginChoosing,
                    onToggleChosen = dayViewModel::toggleChosen,
                    onChooseMeal = dayViewModel::chooseMeal,
                    onChooseAll = dayViewModel::chooseAll,
                    onClearChoosing = dayViewModel::clearChoosing,
                    onDeleteChosen = dayViewModel::deleteChosen,
                    onMakeMealFromChosen = dayViewModel::makeMealFromChosen,
                    onDismissRefusal = dayViewModel::dismissRefusal,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = Destination.EditEntry.route,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
        ) { entry ->
            val itemId = entry.arguments?.getLong("itemId") ?: return@composable
            val state by dayViewModel.state.collectAsStateWithLifecycle()
            val existing = (state as? DayUiState.Ready)
                ?.meals
                ?.flatMap { it.items }
                ?.firstOrNull { it.id == itemId }

            if (existing == null) {
                // The item was deleted from under the editor. Going back is the only honest thing
                // to do; showing an empty form would invite the owner to re-create it by accident.
                LaunchedEffect(itemId) { navController.popBackStack() }
            } else {
                EntryEditor(
                    initial = EntryFormState.from(existing),
                    isEdit = true,
                    // The row as it was goes with the correction, so the view model can tell a
                    // corrected figure (same food) from a new name (a different one) — issue #22.
                    onSave = { item -> dayViewModel.correctItem(existing, item) },
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}

/**
 * The item editor with its form state held for it.
 *
 * The same composable serves adding and correcting: the differences are what it starts with, what
 * is done with the result, and — since correcting an estimate is not starting from nothing — what
 * it is headed. All three are parameters.
 */
@Composable
private fun EntryEditor(
    initial: EntryFormState,
    isEdit: Boolean,
    onSave: (FoodItem) -> Unit,
    onDone: () -> Unit,
) {
    var form by remember { mutableStateOf(initial) }
    var showErrors by remember { mutableStateOf(false) }

    EntryEditorScreen(
        state = form,
        showErrors = showErrors,
        isEdit = isEdit,
        onChange = { form = it },
        onSave = {
            val item = form.toItem()
            if (item == null) {
                showErrors = true
            } else {
                onSave(item)
                onDone()
            }
        },
        onCancel = onDone,
    )
}

/**
 * "Logged 80.5 kg for Today." — what the weight screen says when the editor comes back.
 *
 * Takes the already-resolved template rather than reading it: this is called from a click handler,
 * which is not a composable context.
 */
private fun confirmation(
    template: String,
    reading: WeightReading,
    todayEpochDay: Long,
): String = String.format(
    template,
    WeightWording.reading(reading),
    DayWording.label(
        date = LocalDate.ofEpochDay(reading.epochDay),
        today = LocalDate.ofEpochDay(todayEpochDay),
    ),
)

/** "metaself-2026-09-04.json" — dated, so a folder of these sorts itself. */
private fun backupFileName(): String =
    "metaself-${LocalDate.now()}.json"
