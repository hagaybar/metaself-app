package com.metaself.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The session driver, proved against composables small enough to leave no doubt what happened.
 *
 * Every one of these does something no test in this project could do before: press a button and read
 * what the screen says NEXT, type into a field, hold a row down, and refuse an instruction that two
 * things answer to.
 *
 * The helper composables are deliberately NAMED functions rather than written inline. That is the
 * case that matters: a state change inside a child composable is the one a hand-rolled driver
 * silently fails to see, so testing the inline case alone would have passed while the real screens —
 * every one of which is a named function — stayed frozen.
 *
 * JUnit 4 throughout, because [ComposeSession] needs Compose's rule. `org.junit.Test`, never
 * `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class ComposeSessionTest {

    @get:Rule
    val compose = createComposeRule()

    private val session by lazy { ComposeSession(compose) }

    @Test
    fun `reads the screen again after pressing, which is the whole point`() {
        session.start { Counter() }
        assertThat(session.screen()).contains("Pressed 0 times")

        val after = session.press("Press me")

        assertThat(after).contains("Pressed 1 times")
        assertThat(after).doesNotContain("Pressed 0 times")
    }

    @Test
    fun `keeps its place across several presses`() {
        session.start { Counter() }
        session.press("Press me")
        session.press("Press me")

        assertThat(session.screen()).contains("Pressed 2 times")
    }

    @Test
    fun `types into a field and reads back what is in it`() {
        session.start { Form() }

        val after = session.type(ComposeSession.UNLABELLED, "Greek salad")

        assertThat(after).contains("Greek salad")
    }

    @Test
    fun `holds a row down, which is how choosing begins on the food list and on a day`() {
        session.start { Holdable() }
        assertThat(session.screen()).contains("nothing picked")

        val after = session.hold("Cucumber")

        assertThat(after).contains("1 chosen")
    }

    @Test
    fun `an exact label wins over a longer one that starts the same way`() {
        session.start { TwoSaves() }

        session.press("Save")

        // Reaching the shorter button at all is the assertion: a prefix match taking the first hit
        // would have pressed "Save this ratio" and the walk would have recorded a lie.
        assertThat(session.screen()).contains("pressed: Save")
    }

    @Test
    fun `refuses an instruction two things answer to rather than guessing`() {
        session.start { TwoAdds() }

        val failure = runCatching { session.press("Add a foo") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(ComposeSession.MoreThanOneSaysThat::class.java)
        assertThat(failure).hasMessageThat().contains("2 things answer")
    }

    @Test
    fun `being stuck says what is actually on the screen`() {
        session.start { Counter() }

        val failure = runCatching { session.press("Build a meal") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(ComposeSession.NothingSaysThat::class.java)
        assertThat(failure).hasMessageThat().contains("Build a meal")
        assertThat(failure).hasMessageThat().contains("Press me")
    }

    @Test
    fun `lists what can be done here, so the walk has options rather than guesses`() {
        session.start { Form() }

        assertThat(session.actions().flatMap { it.kinds }).contains("type into")
    }
}

@Composable
private fun Counter() {
    var count by remember { mutableStateOf(0) }
    Column {
        Text("Pressed $count times")
        TextButton(onClick = { count++ }) { Text("Press me") }
    }
}

@Composable
private fun Form() {
    var value by remember { mutableStateOf("") }
    Column {
        Text("Name this meal")
        BasicTextField(value = value, onValueChange = { value = it })
    }
}

/** Shaped like the real thing: a row pressed to open it and held to start choosing. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Holdable() {
    var chosen by remember { mutableStateOf(false) }
    Column {
        Text(if (chosen) "1 chosen" else "nothing picked")
        Text(
            text = "Cucumber",
            modifier = Modifier.combinedClickable(onLongClick = { chosen = true }, onClick = {}),
        )
    }
}

@Composable
private fun TwoSaves() {
    var pressed by remember { mutableStateOf("pressed: nothing") }
    Column {
        TextButton(onClick = { pressed = "pressed: the long one" }) { Text("Save this ratio") }
        TextButton(onClick = { pressed = "pressed: Save" }) { Text("Save") }
        Text(pressed)
    }
}

@Composable
private fun TwoAdds() {
    Column {
        TextButton(onClick = {}) { Text("Add a food") }
        TextButton(onClick = {}) { Text("Add a food to the meal") }
    }
}
