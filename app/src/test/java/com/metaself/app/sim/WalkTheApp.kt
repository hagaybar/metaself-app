package com.metaself.app.sim

import androidx.compose.ui.test.junit4.createComposeRule
import com.metaself.app.ui.ComposeSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Replays whatever the walking agent has asked for, and writes down what the app said back.
 *
 * The agent does not run inside this. It writes a batch of instructions to `simulation/commands.txt`,
 * runs this, reads the transcript, and writes the next batch. That loop is the whole design: one run
 * costs about half a minute on this box, so an agent thinking after every tap would spend its life
 * waiting on Gradle. Thinking is batched; OBSERVING is not — the screen is written down after every
 * single step.
 *
 * Run it with `--rerun`. The instructions file is not a declared input of the test task, so Gradle
 * will otherwise report the previous run as up to date and the walk will appear to have ignored
 * everything the agent just wrote.
 *
 * **It never fails and it never skips.** A walk that gets stuck is the finding, not an error, so
 * being stuck is recorded and the walk carries on to the next instruction. Skipping is worse than
 * failing here: CI fails the build on any skipped test, so a run with no instructions replays an
 * empty script rather than standing aside.
 */
@RunWith(RobolectricTestRunner::class)
class WalkTheApp {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `walks wherever the instructions lead`() {
        val world = World(start = startingPlace())
        val session = ComposeSession(compose)
        val transcript = Transcript()

        session.start { SimulatedApp(world) }
        transcript.record(
            step = Step.Look("(opened the app)"),
            outcome = Transcript.Outcome.Did,
            screen = session.screen(),
            actions = session.actions().map { it.toString() },
            layers = session.layers(),
        )

        SimulationScript.parse(commandsFile().takeIf { it.exists() }?.readText().orEmpty())
            .forEach { step -> transcript.carryOut(step, session, world) }

        outputDir().mkdirs()
        File(outputDir(), "transcript.md")
            .writeText(transcript.render("A walk through the foods and the meals"))
        File(outputDir(), "state.md").writeText(world.storedState())
    }

    private companion object {
        /**
         * Where the instructions live and where the record goes.
         *
         * The tests run with `app` as the working directory. The instructions are committed, so a
         * walk can be replayed exactly; the transcript is not, because it is output.
         */
        fun commandsFile() = File("../simulation/commands.txt")

        /**
         * Where this walk begins, from `simulation/start.txt`.
         *
         * Two entrances are worth walking and they are not the same walk. The DAY is where the app
         * actually opens and where the way in to the two lists has to be found before anything can be
         * done to them. The MANAGER is the shorter walk, for exercising the lists themselves without
         * the journey to them. Missing or unreadable means the day, because that is the honest one.
         */
        fun startingPlace(): Where {
            val asked = File("../simulation/start.txt").takeIf { it.exists() }?.readText()?.trim()
            return if (asked.equals("manager", ignoreCase = true)) Where.Manager() else Where.Today
        }

        fun outputDir() = File("build/simulation")
    }
}
