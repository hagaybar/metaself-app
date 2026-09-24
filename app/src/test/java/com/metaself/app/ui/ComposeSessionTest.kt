package com.metaself.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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

    // --- A press is a touch (public issue #6, item 1) --------------------------------------------

    @Test
    fun `a press on a control behind an open menu does not reach it, and closes the menu`() {
        session.start { BehindAMenu() }
        session.press("Open the menu")
        assertThat(session.screen()).contains("Rename")

        val failure = runCatching { session.press("Behind") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(ComposeSession.LandedOutside::class.java)
        assertThat(session.screen()).contains("Behind pressed 0 times")
        assertThat(session.screen()).doesNotContain("Rename")
    }

    @Test
    fun `with a menu open, only what is in the menu can be touched`() {
        session.start { BehindAMenu() }
        session.press("Open the menu")

        assertThat(session.actions().map { it.label }).containsExactly("Rename")
        assertThat(session.layers().map { it.kind to it.reachable })
            .containsExactly("the screen" to false, "a menu" to true).inOrder()
    }

    @Test
    fun `a control inside the open menu is pressed as usual`() {
        session.start { BehindAMenu() }
        session.press("Open the menu")

        val after = session.press("Rename")

        assertThat(after).contains("Renamed")
        assertThat(after).doesNotContain("Rename")
    }

    @Test
    fun `a press beneath an open dialog closes the dialog and reaches nothing`() {
        session.start { BehindADialog() }
        session.press("Open the dialog")
        assertThat(session.screen()).contains("Are you sure?")

        val failure = runCatching { session.press("Behind") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(ComposeSession.LandedOutside::class.java)
        assertThat(session.screen()).contains("Behind pressed 0 times")
        assertThat(session.screen()).doesNotContain("Are you sure?")
    }

    @Test
    fun `a press beneath an open sheet lands on its scrim, which closes it`() {
        session.start { BehindASheet() }
        session.press("Open the sheet")
        assertThat(session.screen()).contains("On the sheet")

        val failure = runCatching { session.press("Behind") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(ComposeSession.LandedOutside::class.java)
        assertThat(session.screen()).contains("Behind pressed 0 times")
        assertThat(session.screen()).doesNotContain("On the sheet")
    }

    @Test
    fun `a switched-off button ignores the finger, and is listed as switched off`() {
        session.start { SwitchedOff() }

        val after = session.press("Save")

        assertThat(after).contains("pressed: nothing")
        assertThat(session.actions().single { it.label == "Save" }.toString()).contains("SWITCHED OFF")
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

@Composable
private fun BehindAMenu() {
    var behind by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf(false) }
    var renamed by remember { mutableStateOf(false) }
    Column {
        Text("Behind pressed $behind times")
        if (renamed) Text("Renamed")
        TextButton(onClick = { behind++ }) { Text("Behind") }
        TextButton(onClick = { open = true }) { Text("Open the menu") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    renamed = true
                    open = false
                },
            )
        }
    }
}

@Composable
private fun BehindADialog() {
    var behind by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf(false) }
    Column {
        Text("Behind pressed $behind times")
        TextButton(onClick = { behind++ }) { Text("Behind") }
        TextButton(onClick = { open = true }) { Text("Open the dialog") }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Yes") } },
            text = { Text("Are you sure?") },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BehindASheet() {
    var behind by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf(false) }
    Column {
        Text("Behind pressed $behind times")
        TextButton(onClick = { behind++ }) { Text("Behind") }
        TextButton(onClick = { open = true }) { Text("Open the sheet") }
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) { Text("On the sheet") }
    }
}

@Composable
private fun SwitchedOff() {
    var pressed by remember { mutableStateOf("pressed: nothing") }
    Column {
        Button(onClick = { pressed = "pressed: Save" }, enabled = false) { Text("Save") }
        Text(pressed)
    }
}
