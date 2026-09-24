package com.metaself.app.sim

import com.metaself.app.ui.ComposeSession

/**
 * Carries one instruction out, and records being stuck as faithfully as succeeding. Shared by the
 * agent's walk ([WalkTheApp]) and the committed one ([RegressionWalkTest]), so the two cannot come to
 * drive the app differently.
 *
 * Every throwable is caught, not just the ones the session raises deliberately. A screen that throws
 * when touched is the most interesting thing a walk could possibly find, and letting it end the run
 * would lose the transcript that says what led there.
 */
fun Transcript.carryOut(step: Step, session: ComposeSession, world: World) {
    val outcome = try {
        when (step) {
            is Step.Press -> session.press(step.label)
            is Step.Hold -> session.hold(step.label)
            is Step.Type -> session.type(step.label, step.text)
            is Step.Look -> session.screen()
            // The phone's own back gesture, not the arrow in the title bar.
            //
            // Pressing a node labelled "Back" was wrong twice over: a screen without a visible arrow
            // recorded STUCK, which reads as "there is no way out of here", and a screen WITH one was
            // being tested for its arrow rather than for going back.
            is Step.Back -> world.goBack().let { session.screen() }
            is Step.Unreadable -> throw IllegalArgumentException(step.why)
        }
        Transcript.Outcome.Did
    } catch (landed: ComposeSession.LandedOutside) {
        Transcript.Outcome.LandedOutside(landed.message.orEmpty())
    } catch (failure: Throwable) {
        Transcript.Outcome.Stuck(failure.message ?: failure.toString())
    }
    record(
        step = step,
        outcome = outcome,
        screen = session.screen(),
        actions = session.actions().map { it.toString() },
        layers = session.layers(),
    )
}
