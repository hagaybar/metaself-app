package com.metaself.app.ui.screen.propose

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.MealEstimator
import com.metaself.app.ui.ComposeSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Describing a meal with no key saved, out to settings and back (public issue #11).
 *
 * The words must still be in the box on return, and two different things keep them: the view model,
 * which holds what was asked and lives as long as the describe screen's place on the back stack, and
 * the field itself, which holds anything typed since and is saved with that place while settings is
 * on top. Either one missing loses words, so this goes through a real `NavHost` — Navigation's own
 * back stack, not a list standing in for one — with the screen wired the way `MetaSelfNavHost`
 * wires it. The settings end is a stand-in: what is under test is the coming back.
 *
 * JUnit 4, because Compose's rule demands it: `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class DescribeWithoutKeySessionTest {

    @get:Rule
    val compose = createComposeRule()

    private val session by lazy { ComposeSession(compose) }

    @Test
    fun `no key offers a way to settings, and the words are still there on the way back`() {
        session.start { DescribeThenSettings() }

        session.type(FIELD, "a bowl of soup")
        val failed = session.press("Work it out")
        assertThat(failed.any { it.startsWith("No API key yet") }).isTrue()

        // Typed after the failure, so the field's own saved state is what has to carry it: the view
        // model only holds what was last asked.
        session.type(FIELD, "a bowl of soup, about 300 g")
        val inSettings = session.press(ADD_KEY)
        assertThat(inSettings).contains(SETTINGS)

        val back = session.press("Back")

        assertThat(back).contains("a bowl of soup, about 300 g")
        // The complaint went with him, so a key saved in settings is not contradicted on return.
        assertThat(back.none { it.startsWith("No API key yet") }).isTrue()
        assertThat(back).doesNotContain(ADD_KEY)
    }

    @Test
    fun `a failure that is not the key offers no way to settings`() {
        session.start { DescribeThenSettings(result = EstimateResult.Unreachable) }

        session.type(FIELD, "a bowl of soup")
        val failed = session.press("Work it out")

        assertThat(failed.any { it.startsWith("Could not reach the model") }).isTrue()
        assertThat(failed).doesNotContain(ADD_KEY)
    }

    @Composable
    private fun DescribeThenSettings(result: EstimateResult = EstimateResult.NoKey) {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = "describe") {
            composable("describe") {
                // Scoped to this entry, exactly as `hiltViewModel()` scopes it in the app.
                val viewModel = viewModel { ProposalViewModel(Answers(result), ProblemLog.NONE) }
                val state by viewModel.state.collectAsStateWithLifecycle()
                ProposalScreen(
                    state = state,
                    description = viewModel.description,
                    onDescribe = viewModel::describe,
                    onSetAmount = { _, _ -> },
                    onStep = { _, _ -> },
                    onRemove = {},
                    onTellItMore = {},
                    onSave = {},
                    onTypeItMyself = {},
                    onAddKey = {
                        viewModel.leaveToAddKey()
                        nav.navigate("settings")
                    },
                    onCancel = { nav.popBackStack() },
                    onKeepAsMeal = {},
                    onNameMeal = {},
                    onGiveUpNaming = {},
                    onKeepingDone = {},
                    chosenRows = emptyList(),
                    isToday = true,
                    refusal = null,
                )
            }
            composable("settings") {
                Text(SETTINGS)
                TextButton(onClick = { nav.popBackStack() }) { Text("Back") }
            }
        }
    }

    private class Answers(private val result: EstimateResult) : MealEstimator {
        override suspend fun estimate(description: String, moreDetail: String?) = result
    }

    private companion object {
        /** `R.string.propose_field`, as the phone draws it. */
        const val FIELD = "What did you eat?"

        /** `R.string.propose_add_key`, as the phone draws it. */
        const val ADD_KEY = "Add a key in settings"

        const val SETTINGS = "Settings, standing in"
    }
}
