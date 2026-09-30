package com.metaself.app.ui.screen.settings

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.ai.TrainerPrompt
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.health.MovementRecord
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
import com.metaself.app.data.trainer.ProgrammeStore
import com.metaself.app.data.trainer.RecordingWorkbenchSender
import com.metaself.app.data.trainer.TrainerWorkbench
import com.metaself.app.data.weight.InMemoryWeightRepository
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerPath
import com.metaself.app.domain.trainer.WorkbenchReply
import com.metaself.app.domain.trainer.WorkbenchSender
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.domain.trainer.Wish
import com.metaself.app.ui.screen.trainer.EvaluatePlanViewModel
import com.metaself.app.ui.screen.trainer.PlanSessionViewModel
import com.metaself.app.ui.RecordingProblemLog
import com.metaself.app.ui.trainer.WorkbenchWording
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
    private val problems = RecordingProblemLog()
    private val record = FakeMovementRecord()
    private val store = FakeTrainerStore()

    /** While set, a send is held after it reaches the sender, until the test completes it. */
    private var gate: CompletableDeferred<Unit>? = null
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
        assertThat(sender.sent).hasSize(1)
        assertThat(vm.state.value.reply).isNotNull()

        vm.pickPath(TrainerPath.EVALUATE)

        assertThat(vm.state.value.reply).isNull()
        assertThat(vm.state.value.sent).isNull()
    }

    @Test
    fun `a reply that arrives after the path changed is not shown under the new path`() = runTest {
        val vm = opened()
        vm.pickPath(TrainerPath.PLAN)
        vm.changePlan(FORM)
        gate = CompletableDeferred()

        vm.send(); advanceUntilIdle()
        assertThat(sender.sent).hasSize(1)
        assertThat(vm.state.value.sending).isTrue()
        vm.pickPath(TrainerPath.EVALUATE)
        gate!!.complete(Unit); advanceUntilIdle()

        assertThat(vm.state.value.reply).isNull()
        assertThat(vm.state.value.sent).isNull()
        assertThat(vm.state.value.sending).isFalse()
    }

    @Test
    fun `a reply asked under a path left and come back to is not shown either`() = runTest {
        val vm = opened()
        vm.pickPath(TrainerPath.PLAN)
        vm.changePlan(FORM)
        gate = CompletableDeferred()

        vm.send(); advanceUntilIdle()
        assertThat(sender.sent).hasSize(1)
        vm.pickPath(TrainerPath.EVALUATE)
        vm.pickPath(TrainerPath.PLAN)
        assertThat(vm.state.value.sending).isFalse()
        assertThat(vm.state.value.canSend).isTrue()
        gate!!.complete(Unit); advanceUntilIdle()

        assertThat(vm.state.value.reply).isNull()
        assertThat(vm.state.value.sent).isNull()
        assertThat(vm.state.value.sending).isFalse()
    }

    @Test
    fun `the will-send line and what is sent never part, whenever Send is pressed during a load`() = runTest {
        val vm = opened()
        vm.pickPath(TrainerPath.PLAN)
        vm.changePlan(FORM)
        files.nameGate = CompletableDeferred()

        vm.load("content://invented/one"); advanceUntilIdle() // read, and waiting on the name
        assertThat(vm.state.value.fileName).isNull()
        vm.send(); advanceUntilIdle()
        files.nameGate!!.complete(Unit); advanceUntilIdle()
        assertThat(vm.state.value.fileName).isEqualTo("invented.txt")
        vm.send(); advanceUntilIdle()

        assertThat(sender.sent.map { it.first }).containsExactly(TrainerPrompt.instructions(TrainerPath.PLAN), LOADED).inOrder()
    }

    @Test
    fun `using the app's own instructions during a load wins over the load`() = runTest {
        val vm = opened()
        vm.pickPath(TrainerPath.PLAN)
        vm.changePlan(FORM)
        files.nameGate = CompletableDeferred()

        vm.load("content://invented/one"); advanceUntilIdle()
        vm.useAppOwn()
        files.nameGate!!.complete(Unit); advanceUntilIdle()
        vm.send(); advanceUntilIdle()

        assertThat(vm.state.value.fileName).isNull()
        assertThat(sender.sent.map { it.first }).containsExactly(TrainerPrompt.instructions(TrainerPath.PLAN))
    }

    @Test
    fun `an unreadable file after a good one changes nothing`() = runTest {
        val vm = opened()
        vm.pickPath(TrainerPath.PLAN)
        vm.changePlan(FORM)
        vm.load("content://invented/one"); advanceUntilIdle()
        files.text = null

        vm.load("content://invented/two"); advanceUntilIdle()
        vm.send(); advanceUntilIdle()

        assertThat(vm.state.value.fileName).isEqualTo("invented.txt")
        assertThat(vm.state.value.fileMessage).isEqualTo(WorkbenchWording.COULD_NOT_READ)
        assertThat(sender.sent.map { it.first }).containsExactly(LOADED)
    }

    @Test
    fun `feedback sends the picked session's request`() = runTest {
        store.workouts.value = listOf(walk(id = 1))
        record.workouts.value = store.workouts.value
        val vm = opened()
        assertThat(vm.state.value.sessions.map { it.id }).containsExactly(1L)

        vm.pickSession(1)
        vm.send(); advanceUntilIdle()

        assertThat(sender.sent).hasSize(1)
        assertThat(sender.sent.single().first).isEqualTo(TrainerPrompt.instructions(TrainerPath.FEEDBACK))
        assertThat(vm.state.value.reply).isEqualTo("Invented reply.")
    }

    @Test
    fun `a session gone from the record sends nothing and says so`() = runTest {
        val vm = opened()

        vm.pickSession(99)
        vm.send(); advanceUntilIdle()

        assertThat(sender.sent).isEmpty()
        assertThat(vm.state.value.notice).isEqualTo(WorkbenchWording.SESSION_GONE)
        assertThat(vm.state.value.sending).isFalse()
    }

    @Test
    fun `adjusting when the plan stopped since opening sends nothing and says so`() = runTest {
        val programmes = FakeProgrammeStore()
        val id = planRunning(programmes)
        val vm = opened(programmes = programmes)
        assertThat(vm.state.value.planRuns).isTrue()
        programmes.stop(id, TEST_EPOCH_DAY)

        vm.pickPath(TrainerPath.ADJUST)
        vm.changeAdjustWords("Invented words.")
        vm.send(); advanceUntilIdle()

        assertThat(sender.sent).isEmpty()
        assertThat(vm.state.value.notice).isEqualTo(WorkbenchWording.NO_PLAN)
        assertThat(vm.state.value.planRuns).isFalse()
    }

    @Test
    fun `a record that cannot be read on Send sends nothing, says so, and is logged by kind`() = runTest {
        val vm = opened()
        store.failing = setOf("workout")

        vm.pickSession(1)
        vm.send(); advanceUntilIdle()

        assertThat(sender.sent).isEmpty()
        assertThat(vm.state.value.notice).isEqualTo(WorkbenchWording.RECORD_UNREADABLE)
        assertThat(vm.state.value.sending).isFalse()
        assertThat(problems.recorded.map { it.kind }).containsExactly(TrainerWorkbenchViewModel.PROBLEM_KIND)
    }

    @Test
    fun `sessions that cannot be read on opening are said, not shown as none`() = runTest {
        val vm = opened(record = object : MovementRecord by record {
            override fun observeWorkouts(from: Long, to: Long): Flow<List<Workout>> = flow { throw IllegalStateException("invented") }
        })

        assertThat(vm.state.value.loaded).isTrue()
        assertThat(vm.state.value.unreadable).isTrue()
        assertThat(problems.recorded.map { it.kind }).contains(TrainerWorkbenchViewModel.PROBLEM_KIND)
    }

    @Test
    fun `whether a plan runs, unread on opening, is said, not shown as no plan`() = runTest {
        val programmes = FakeProgrammeStore()
        val vm = opened(programmes = object : ProgrammeStore by programmes {
            override suspend fun running(): Programme? = throw IllegalStateException("invented")
        })

        assertThat(vm.state.value.unreadable).isTrue()
        assertThat(vm.state.value.planRuns).isFalse()
        assertThat(problems.recorded.map { it.kind }).containsExactly(TrainerWorkbenchViewModel.PROBLEM_KIND)
    }

    @Test
    fun `a record read on opening is not flagged`() = runTest {
        val vm = opened()

        assertThat(vm.state.value.unreadable).isFalse()
        assertThat(problems.recorded).isEmpty()
    }

    @Test
    fun `the weeks-ahead path opens on the real screen's own preset`() = runTest {
        val vm = opened()

        vm.pickPath(TrainerPath.EVALUATE)

        assertThat(vm.state.value.evaluate).isEqualTo(EvaluatePlanViewModel.PRESET)
        assertThat(vm.state.value.canSend).isTrue()
    }

    /**
     * Amends D106: the path and every input outlive Android closing the app in the background (the
     * file picker open, say); a loaded file's text does not, and the page returns to the app's own.
     */
    @Test
    fun `the path and the inputs survive the app being closed, and a loaded file does not`() = runTest {
        val saved = SavedStateHandle()
        val first = opened(savedState = saved)
        first.pickPath(TrainerPath.ADJUST)
        first.pickSession(7)
        first.changePlan(FORM.copy(words = "Invented plan words."))
        first.changeEvaluate(EvaluatePlanViewModel.Form(weeks = 2, perWeek = 1, words = "Invented ask."))
        first.changeAdjustWords("Invented change.")
        first.load("content://invented/one"); advanceUntilIdle()
        assertThat(first.state.value.fileName).isEqualTo("invented.txt")

        val again = opened(savedState = saved).state.value

        assertThat(again.path).isEqualTo(TrainerPath.ADJUST)
        assertThat(again.workoutId).isEqualTo(7L)
        assertThat(again.plan).isEqualTo(FORM.copy(words = "Invented plan words."))
        assertThat(again.evaluate).isEqualTo(EvaluatePlanViewModel.Form(weeks = 2, perWeek = 1, words = "Invented ask."))
        assertThat(again.adjustWords).isEqualTo("Invented change.")
        assertThat(again.fileName).isNull()
        assertThat(again.instructions).isNull()
    }

    /** A row the owner emptied stays empty: the preset is only where the page first opens. */
    @Test
    fun `an emptied row comes back empty, and a first opening is as before`() = runTest {
        val saved = SavedStateHandle()
        val fresh = opened(savedState = saved).state.value
        assertThat(fresh.path).isEqualTo(TrainerPath.FEEDBACK)
        assertThat(fresh.workoutId).isNull()
        assertThat(fresh.plan).isEqualTo(PlanSessionViewModel.Form())
        assertThat(fresh.evaluate).isEqualTo(EvaluatePlanViewModel.PRESET)
        assertThat(fresh.adjustWords).isEmpty()

        opened(savedState = saved).changeEvaluate(EvaluatePlanViewModel.Form(weeks = null, perWeek = 3))

        assertThat(opened(savedState = saved).state.value.evaluate).isEqualTo(EvaluatePlanViewModel.Form(weeks = null, perWeek = 3))
    }

    @Test
    fun `saving writes the chosen path's instructions, exactly`() = runTest {
        val vm = opened()
        vm.pickPath(TrainerPath.EVALUATE)

        vm.saveAppInstructionsTo("content://invented/out"); advanceUntilIdle()

        assertThat(files.written).containsExactly("content://invented/out" to TrainerPrompt.instructions(TrainerPath.EVALUATE))
        assertThat(vm.state.value.fileMessage).isEqualTo(WorkbenchWording.SAVED)
    }

    @Test
    fun `a save that fails says so`() = runTest {
        files.writes = false
        val vm = opened()

        vm.saveAppInstructionsTo("content://invented/out"); advanceUntilIdle()

        assertThat(vm.state.value.fileMessage).isEqualTo(WorkbenchWording.COULD_NOT_WRITE)
    }

    private fun TestScope.opened(
        record: MovementRecord = this@TrainerWorkbenchViewModelTest.record,
        programmes: ProgrammeStore = FakeProgrammeStore(),
        savedState: SavedStateHandle = SavedStateHandle(),
    ): TrainerWorkbenchViewModel {
        val ask = AskTheTrainer(
            record, store, InMemoryWeightRepository(), FakeProfileRepository(aProfile()), FakeTrainer(), InMemoryAboutMeStore(),
            programmes, today, Now { TEST_EPOCH_DAY * 86_400_000L }, CurrentYear { TEST_YEAR },
        )
        val held = WorkbenchSender { system, request -> sender.send(system, request).also { gate?.await() } }
        return TrainerWorkbenchViewModel(savedState, TrainerWorkbench(ask, store, record, today, held), files, today, problems)
            .also { advanceUntilIdle() }
    }

    /** A two-week plan, kept to run from this week's Monday, three days before today. Returns its id. */
    private suspend fun planRunning(programmes: FakeProgrammeStore): Long {
        val id = programmes.add(PROGRAMME)
        programmes.keep(id, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)
        return id
    }

    private class Files : InstructionFiles {
        var text: String? = LOADED
        var writes = true
        var nameGate: CompletableDeferred<Unit>? = null
        val written = mutableListOf<Pair<String, String>>()
        override suspend fun read(uri: String): String? = text
        override suspend fun write(uri: String, text: String): Boolean {
            written += uri to text
            return writes
        }
        override suspend fun nameOf(uri: String): String {
            nameGate?.await()
            return "invented.txt"
        }
    }

    private companion object {
        const val LOADED = "Invented instructions: a short note from a coach."
        val FORM = PlanSessionViewModel.Form(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.NOT_SURE)
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
        val WALK_30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Easy walk")

        val PROGRAMME = Programme(
            0, (TEST_EPOCH_DAY - 18) * DAY + 12 * HOUR, ProgrammeAsk(2, 2),
            Evaluation("Invented headline.", "Invented.", "Invented.", ""),
            WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(WALK_30, WALK_30)) }, "Invented reason."), "a-model",
        )

        fun walk(id: Long) = Workout(
            id = id, epochDay = TEST_EPOCH_DAY, startedAtMillis = TEST_EPOCH_DAY * DAY + 8 * HOUR, durationMinutes = 40,
            kind = WorkoutKind.WALK, title = null, distanceM = 4_000, energyKcal = null,
            energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
        )
    }
}
