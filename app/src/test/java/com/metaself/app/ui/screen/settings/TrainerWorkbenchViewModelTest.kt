package com.metaself.app.ui.screen.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.ai.TrainerPrompt
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.AskTheTrainer
import com.metaself.app.data.trainer.FakeProgrammeStore
import com.metaself.app.data.trainer.FakeTrainer
import com.metaself.app.data.trainer.FakeTrainerStore
import com.metaself.app.data.trainer.InMemoryAboutMeStore
import com.metaself.app.data.trainer.InstructionFiles
import com.metaself.app.data.trainer.RecordingWorkbenchSender
import com.metaself.app.data.trainer.TrainerWorkbench
import com.metaself.app.data.weight.InMemoryWeightRepository
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerPath
import com.metaself.app.domain.trainer.WorkbenchReply
import com.metaself.app.domain.trainer.Wish
import com.metaself.app.ui.screen.trainer.EvaluatePlanViewModel
import com.metaself.app.ui.screen.trainer.PlanSessionViewModel
import com.metaself.app.ui.trainer.WorkbenchWording
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** D106's page state. Every word and file name is invented. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainerWorkbenchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sender = RecordingWorkbenchSender()
    private val files = Files()
    private val today = Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the app's own instructions are sent until a file is loaded, then the file's, and back again`() = runTest {
        val vm = opened()
        vm.pickPath(TrainerPath.PLAN)
        vm.changePlan(FORM)
        assertThat(vm.state.value.fileName).isNull()

        vm.send(); advanceUntilIdle()
        vm.load("content://invented/one"); advanceUntilIdle()
        vm.send(); advanceUntilIdle()
        vm.useAppOwn()
        vm.send(); advanceUntilIdle()

        assertThat(sender.sent.map { it.first }).containsExactly(
            TrainerPrompt.instructions(TrainerPath.PLAN), LOADED, TrainerPrompt.instructions(TrainerPath.PLAN),
        ).inOrder()
    }

    @Test
    fun `a loaded file is named on the page`() = runTest {
        val vm = opened()

        vm.load("content://invented/one"); advanceUntilIdle()

        assertThat(vm.state.value.fileName).isEqualTo("invented.txt")
    }

    @Test
    fun `an empty or unreadable file is refused and nothing changes`() = runTest {
        val vm = opened()
        files.text = "   "

        vm.load("content://invented/one"); advanceUntilIdle()

        assertThat(vm.state.value.fileName).isNull()
        assertThat(vm.state.value.fileMessage).isEqualTo(WorkbenchWording.COULD_NOT_READ)
    }

    @Test
    fun `the reply and the body sent are shown as they came`() = runTest {
        sender.reply = WorkbenchReply.Answered("Invented reply.", "{\"invented\":1}")
        val vm = opened()
        vm.pickPath(TrainerPath.PLAN)
        vm.changePlan(FORM)

        vm.send(); advanceUntilIdle()

        assertThat(vm.state.value.reply).isEqualTo("Invented reply.")
        assertThat(vm.state.value.sent).isEqualTo("{\"invented\":1}")
        assertThat(vm.state.value.sending).isFalse()
    }

    @Test
    fun `a failed call keeps its failure for the trainer's own wording`() = runTest {
        sender.reply = WorkbenchReply.Failed(EstimateResult.NoKey, sent = null)
        val vm = opened()
        vm.pickPath(TrainerPath.PLAN)
        vm.changePlan(FORM)

        vm.send(); advanceUntilIdle()

        assertThat(vm.state.value.failure).isEqualTo(EstimateResult.NoKey)
        assertThat(vm.state.value.reply).isNull()
        assertThat(vm.state.value.sent).isNull()
    }

    @Test
    fun `each path can be sent only with its inputs, and adjusting only while a plan runs`() = runTest {
        val vm = opened()

        assertThat(vm.state.value.canSend).isFalse() // feedback, no session picked
        vm.pickPath(TrainerPath.PLAN)
        assertThat(vm.state.value.canSend).isFalse()
        vm.changePlan(FORM)
        assertThat(vm.state.value.canSend).isTrue()
        vm.pickPath(TrainerPath.ADJUST)
        assertThat(vm.state.value.planRuns).isFalse()
        assertThat(vm.state.value.canSend).isFalse()
    }

    @Test
    fun `changing the path clears the last reply`() = runTest {
        val vm = opened()
        vm.pickPath(TrainerPath.PLAN)
        vm.changePlan(FORM)
        vm.send(); advanceUntilIdle()

        vm.pickPath(TrainerPath.EVALUATE)

        assertThat(vm.state.value.reply).isNull()
        assertThat(vm.state.value.sent).isNull()
    }

    @Test
    fun `a reply that arrives after the path changed is not shown under the new path`() = runTest {
        val vm = opened()
        vm.pickPath(TrainerPath.PLAN)
        vm.changePlan(FORM)

        vm.send()
        vm.pickPath(TrainerPath.EVALUATE)
        advanceUntilIdle()

        assertThat(vm.state.value.reply).isNull()
        assertThat(vm.state.value.sent).isNull()
        assertThat(vm.state.value.sending).isFalse()
    }

    @Test
    fun `the review path opens on the real screen's own preset`() = runTest {
        val vm = opened()

        vm.pickPath(TrainerPath.EVALUATE)

        assertThat(vm.state.value.evaluate).isEqualTo(EvaluatePlanViewModel.PRESET)
        assertThat(vm.state.value.canSend).isTrue()
    }

    @Test
    fun `saving writes the chosen path's instructions, exactly`() = runTest {
        val vm = opened()
        vm.pickPath(TrainerPath.EVALUATE)

        vm.saveAppInstructionsTo("content://invented/out"); advanceUntilIdle()

        assertThat(files.written).containsExactly("content://invented/out" to TrainerPrompt.instructions(TrainerPath.EVALUATE))
        assertThat(vm.state.value.fileMessage).isEqualTo(WorkbenchWording.SAVED)
    }

    private fun kotlinx.coroutines.test.TestScope.opened(): TrainerWorkbenchViewModel {
        val record = FakeMovementRecord()
        val store = FakeTrainerStore()
        val ask = AskTheTrainer(
            record, store, InMemoryWeightRepository(), FakeProfileRepository(aProfile()), FakeTrainer(), InMemoryAboutMeStore(),
            FakeProgrammeStore(), today, Now { TEST_EPOCH_DAY * 86_400_000L }, CurrentYear { TEST_YEAR },
        )
        return TrainerWorkbenchViewModel(TrainerWorkbench(ask, store, record, today, sender), files, today)
            .also { advanceUntilIdle() }
    }

    private class Files : InstructionFiles {
        var text: String? = LOADED
        val written = mutableListOf<Pair<String, String>>()
        override suspend fun read(uri: String): String? = text
        override suspend fun write(uri: String, text: String): Boolean {
            written += uri to text
            return true
        }
        override suspend fun nameOf(uri: String): String = "invented.txt"
    }

    private companion object {
        const val LOADED = "Invented instructions: a short note from a coach."
        val FORM = PlanSessionViewModel.Form(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.NOT_SURE)
    }
}
