package com.metaself.app.ui.root

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metaself.app.ui.nav.MetaSelfNavHost
import com.metaself.app.ui.target.RevisionWording
import com.metaself.app.ui.screen.home.HomeScreen
import com.metaself.app.ui.screen.setup.SetupFormState
import com.metaself.app.ui.screen.setup.SetupScreen

/**
 * Picks the screen from the stored state.
 *
 * No navigation library: there are two destinations and the choice between them is a fact about the
 * data, not a back stack. Editing is the third state, and system back leaves it — which is what
 * [BackHandler] is for.
 */
@Composable
fun MetaSelfRoot(
    versionName: String,
    versionCode: Int,
    viewModel: RootViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentYear = viewModel.year

    var editing by remember { mutableStateOf(false) }
    var showingProfile by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf(SetupFormState()) }
    var showErrors by remember { mutableStateOf(false) }

    when (val current = state) {
        // A page, not a bare arc. Unframed, the indicator was drawn against whatever was behind
        // it — on the first frame of a cold start, the window's own background — and pinned to the
        // top-left corner of the window rather than centred in anything.
        RootUiState.Loading -> Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        RootUiState.NeedsSetup -> SetupScreen(
            state = form,
            currentYear = currentYear,
            showErrors = showErrors,
            onChange = { form = it },
            onSave = {
                val profile = form.toProfile(currentYear)
                if (profile == null) showErrors = true else viewModel.save(profile)
            },
            onCancel = null,
        )

        is RootUiState.Ready -> when {
            editing -> {
                BackHandler {
                    editing = false
                    showErrors = false
                }
                SetupScreen(
                    state = form,
                    currentYear = currentYear,
                    showErrors = showErrors,
                    onChange = { form = it },
                    onSave = {
                        val profile = form.toProfile(currentYear)
                        if (profile == null) {
                            showErrors = true
                        } else {
                            viewModel.save(profile)
                            editing = false
                            showErrors = false
                        }
                    },
                    onCancel = {
                        editing = false
                        showErrors = false
                    },
                )
            }

            showingProfile -> {
                BackHandler { showingProfile = false }
                HomeScreen(
                    profile = current.profile,
                    target = current.target,
                    versionName = versionName,
                    versionCode = versionCode,
                    measuredBurn = current.measuredBurn,
                    burnAdjustmentKcal = current.burnAdjustmentKcal,
                    daysLoggedRecently = current.daysLoggedRecently,
                    weightUsedLine = RevisionWording.weightUsed(
                        weightKg = current.weightUsedKg,
                        fromTrend = current.targetFollowsTrend,
                    ),
                    onEdit = {
                        form = SetupFormState.from(current.profile)
                        showErrors = false
                        editing = true
                    },
                    onAllowBelowFloor = viewModel::allowBelowFloor,
                    onForgetBurnAdjustment = viewModel::forgetBurnAdjustment,
                    onBack = { showingProfile = false },
                )
            }

            else -> MetaSelfNavHost(onEditProfile = { showingProfile = true })
        }
    }
}
