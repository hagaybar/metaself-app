package com.metaself.app.ui.screen.record

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.day.aMeal
import com.metaself.app.domain.day.anItem
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.portion.Portions
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.screen.day.DayMeals
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * **THE RECORD SCREEN (D50).**
 *
 * Most of this file was written for the day screen and moved here when D51 took the record off the
 * day: the day now draws one line per part of the clock, and every row, figure, tick, time and
 * delete it used to draw belongs to this screen. The tests were moved rather than deleted because a
 * test deleted for the thing having moved is coverage silently lost — and because **six of them were
 * passing vacuously** the moment the day stopped drawing rows. An assertion that no badge is drawn,
 * that no origin line appears, that nothing says a meal was changed, that no tick is on any row,
 * that no meal-making action is offered, and that nothing is estimated or repeated, all went on
 * passing against a screen that drew none of those things at all, and nothing would have reported
 * it. Against this screen they are real again.
 *
 * Every one of them now renders the real screen through [record], where they were parked against the
 * bare list. **Three could not travel as they were, and were rewritten here rather than dropped:**
 * - *The Undo is drawn above the list.* It inverts: this screen pins its actions to the BOTTOM edge,
 *   so what is asserted is the opposite order (`the way back from a deletion is pinned below the
 *   list`).
 * - *Nothing offers an undo until something has been deleted*, and *with nothing chosen there is no
 *   meal to make.* Both are about a CONDITION a screen applies, which the bare list could not apply.
 *   They are conditions this screen does apply, and are asserted against it.
 * - *Pressing "Make a meal from these 2" clears a refusal already answered.* That was the day's own
 *   wiring between the action and the dismissal; it is this screen's wiring now, and is tested here.
 *
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4.
 */
@RunWith(RobolectricTestRunner::class)
class RecordScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    // --- what the day came to (D50 item 1) ------------------------------------------------------

    /**
     * The top line is the total recorded and how many things — **and it is not the target, and not
     * what is left**. This screen is about the record; the day is about progress, and the whole
     * reason the two were split is that answering both on one page is what made the day a dashboard.
     *
     * The target of this profile is 2,090, which is the figure the day would have drawn, so a screen
     * that had quietly been handed the day's state whole would fail here.
     */
    @Test
    fun `the top line says what the day came to, and says nothing about the target`() {
        val texts = record(
            listOf(
                aMeal(id = 1, items = listOf(anItem(id = 10, name = "Porridge", kcal = 180))),
                aMeal(id = 2, items = listOf(anItem(id = 11, name = "Coffee", kcal = 60))),
            ),
        )

        assertThat(texts).contains("240 kcal")
        assertThat(texts).contains("2 things")
        assertThat(texts.none { it.contains("2,090") }).isTrue()
        assertThat(texts.none { it.contains("left") }).isTrue()
    }

    /**
     * A meal he built is ONE thing, because it is one row that opens to its parts — the same rule
     * the day's own rows count by. Two rules for the word would disagree the first time he built a
     * meal, and this screen would say five things where the day said one.
     */
    @Test
    fun `a meal he built counts as one thing, not as its parts`() {
        val texts = record(
            listOf(
                aMeal(
                    id = 1,
                    items = listOf(anItem(id = 10, name = "Cucumber"), anItem(id = 11, name = "Olive oil")),
                    savedMealId = 1,
                    savedMealName = "Vegetable salad",
                ),
            ),
        )

        assertThat(texts).contains("1 thing")
    }

    /** The day it is the record of, so a screen reached from yesterday says which day it read. */
    @Test
    fun `the record names the day it was opened for`() {
        val texts = record(
            listOf(aMeal(id = 1, epochDay = shownDay, items = listOf(anItem(id = 10)))),
            todayEpochDay = shownDay + 1,
            isToday = false,
        )

        assertThat(texts).contains("Yesterday")
    }

    /**
     * **No date picker and no travel between days** (D50, amended before any code).
     *
     * This is not tidiness. `EditEntry` resolves the row to correct by searching the day view
     * model's currently selected day and pops out when it finds nothing, so a record screen showing
     * any other day would bounce every correction tap — which is the one thing this screen exists
     * for. The day's own picker is the two words below; neither may appear here.
     */
    @Test
    fun `the record offers no way to travel to another day`() {
        val texts = record(listOf(aMeal(id = 1, items = listOf(anItem(id = 10)))))

        assertThat(texts).doesNotContain("Show it")
        assertThat(texts).doesNotContain("Cancel")
    }

    // --- the loggings, item by item -------------------------------------------------------------

    @Test
    fun `a logged item is listed with what it held`() {
        val texts = record(
            listOf(
                aMeal(
                    items = listOf(
                        anItem(name = "Hummus", kcal = 180, proteinG = 6, carbsG = 12, fatG = 12),
                    ),
                ),
            ),
        )
        // The name and its numbers are two pieces of text, never one string: a Hebrew name in the
        // same string as a Latin figure gets reordered by the bidirectional algorithm, which drags
        // the calorie count in front of the food it belongs to.
        assertThat(texts).contains("Hummus")
        assertThat(texts).contains("180 kcal · P 6 · C 12 · F 12")
    }

    /** VACUOUS ON THE DAY once the rows went: the day draws no badge for anything. */
    @Test
    fun `a typed item wears no badge`() {
        val texts = record(listOf(aMeal(items = listOf(anItem(source = Source.TYPED)))))
        assertThat(texts.none { it.startsWith("Estimated") }).isTrue()
    }

    /**
     * D7a: provenance belongs where a number is ACCEPTED, not under every row of the record. The
     * source is still on the record and still on the proposal screen; this list is for seeing what
     * he ate, not for auditing it.
     *
     * VACUOUS ON THE DAY once the rows went.
     */
    @Test
    fun `the list does not print where each number came from`() {
        val estimate = anItem(source = Source.AI_ESTIMATE, confidence = Confidence.LOW)
        val texts = record(listOf(aMeal(items = listOf(estimate))))

        assertThat(texts.any { it.startsWith("Estimated") }).isFalse()
        assertThat(texts.any { it.contains("Repeated") }).isFalse()
    }

    @Test
    fun `a day of several items still shows every one of them`() {
        // R6: one item filled a card and two nearly filled the screen. A tracker on which a day's
        // meals do not fit is failing at the thing it exists to do.
        val meals = (1..6).map { n ->
            aMeal(id = n.toLong(), items = listOf(anItem(id = 100L + n, name = "Item $n", kcal = 100)))
        }
        val texts = record(meals)
        (1..6).forEach { n ->
            assertThat(texts).contains("Item $n")
        }
    }

    // --- a meal he built ------------------------------------------------------------------------

    /**
     * The salad is one thing he ate, not five entries to read past. Before this it was flattened
     * into its parts and the name he gave it appeared nowhere.
     */
    @Test
    fun `a meal he built shows as one row with the name he gave it`() {
        val texts = record(
            listOf(
                aMeal(
                    items = listOf(
                        anItem(name = "Cucumber", kcal = 16),
                        anItem(name = "Olive oil", kcal = 119),
                    ),
                    savedMealId = 1,
                    savedMealName = "Vegetable salad",
                ),
            ),
        )

        assertThat(texts).contains("Vegetable salad")
        // Closed by default: its parts are behind the row rather than spread across the list.
        assertThat(texts).doesNotContain("Cucumber")
        assertThat(texts.any { it.startsWith("135 kcal") }).isTrue()
    }

    /** A title with a number beside it can be decided about; a bare title has to be opened. */
    @Test
    fun `a collapsed meal says how much is under it`() {
        val texts = record(
            listOf(
                aMeal(
                    items = listOf(anItem(name = "Cucumber"), anItem(name = "Olive oil")),
                    savedMealId = 1,
                    savedMealName = "Vegetable salad",
                ),
            ),
        )

        assertThat(texts.any { it.contains("2 things") }).isTrue()
    }

    /**
     * **Everything else stays flat**, which is the ordinary case. Typed, described, scanned or
     * one food on its own have no name, and inventing one for a row is what this app stopped doing
     * when the derived meal list went.
     */
    @Test
    fun `anything not logged from a meal he built still shows its items`() {
        val texts = record(listOf(aMeal(items = listOf(anItem(name = "Hummus", kcal = 180)))))

        assertThat(texts).contains("Hummus")
    }

    /**
     * The only thing the adjusted flag is for. It is never counted, never aggregated and never
     * turned into an offer to change the meal: nothing learns from what he does.
     */
    @Test
    fun `a meal changed for the day says so`() {
        val texts = record(
            listOf(
                aMeal(
                    items = listOf(anItem(name = "Cucumber", kcal = 16)),
                    savedMealId = 1,
                    savedMealName = "Vegetable salad",
                    savedMealAdjusted = true,
                ),
            ),
        )

        assertThat(texts).contains("Changed for this day")
    }

    /** VACUOUS ON THE DAY once the rows went: the day draws no such line for any meal. */
    @Test
    fun `a meal logged as it was built says nothing about being changed`() {
        val texts = record(
            listOf(
                aMeal(
                    items = listOf(anItem(name = "Cucumber", kcal = 16)),
                    savedMealId = 1,
                    savedMealName = "Vegetable salad",
                ),
            ),
        )

        assertThat(texts).doesNotContain("Changed for this day")
    }

    /**
     * Opening it is how he gets at the rows, because the record is rows: the title is a label over
     * them, and a button deleting "the salad" would be deleting two of them on the strength of one.
     *
     * Which meals are open is held by the view model rather than inside the row, which is what lets
     * this be tested at all — state a screen keeps to itself cannot be put into a known position.
     */
    @Test
    fun `an opened meal shows its parts, each editable on its own`() {
        val meal = aMeal(
            id = 7,
            items = listOf(anItem(name = "Cucumber", kcal = 16), anItem(name = "Olive oil", kcal = 119)),
            savedMealId = 1,
            savedMealName = "Vegetable salad",
        )

        val closed = record(listOf(meal))
        assertThat(closed).doesNotContain("Cucumber")

        val opened = record(listOf(meal), openMeals = setOf(7))

        assertThat(opened).contains("Cucumber")
        assertThat(opened).contains("Olive oil")
        assertThat(opened).contains("Edit")
        assertThat(opened).contains("Delete")
    }

    // --- how much room a row costs --------------------------------------------------------------

    @Test
    fun `a logged row stays as short as it was`() {
        // R6: an item filled a large card and two nearly filled the screen.
        //
        // Measured on the LIST ALONE. Measuring inside a whole screen returned nonsense: under
        // Robolectric, nodes below a certain point come back with no layout bounds at all, so a
        // pitch measured against one of them is the distance from a real row to nowhere. It read
        // as 61 when the screen was short and 448 when it was not, and neither was a row's height.
        //
        // **Unchanged in the move, deliberately.** It measures two item rows through `DayMeals`,
        // and this screen draws that same composable, so both the fence and the measured figure
        // keep their exact meaning here. The DAY's new row is a different row with a different
        // shape; if it ever wants a fence of its own, that is a new constant with a new number
        // printed from a new render, never this one carried over.
        val meals = (1..3).map { n ->
            aMeal(
                id = n.toLong(),
                epochDay = shownDay,
                loggedAtMillis = at(shownDay, 8 + n),
                items = listOf(anItem(id = 100L + n, name = "Item $n", kcal = 100)),
            )
        }

        val pitch = render.rowPitchDp("Item 1", "Item 2") {
            DayMeals(meals = meals, onEditItem = {}, onDeleteItem = {})
        }

        // The ceiling, which is the fence: a row may not creep back towards being a card.
        assertThat(pitch).isAtMost(MAX_ROW_PITCH)

        // And the measurement itself, which nothing pinned until it was written down.
        //
        // The figure 83 dp was carried into D49 with no assertion holding it, so it was a
        // remembered number in a project that has now shipped the same defect twice. It is 83 —
        // printed from this very render, at PHONE_WIDTH_PX, with the list's own meal rows — and a
        // band of four dp either side is what a font metric may legitimately move it by.
        //
        // This is deliberately a floor as well as a ceiling. The ceiling alone cannot see the row
        // getting SHORTER, and a row that suddenly costs 40 dp has lost a line — the time, the
        // figures, or the name — which is a defect that would otherwise pass as an improvement.
        assertThat(pitch).isAtLeast(ROW_PITCH_DP - ROW_PITCH_TOLERANCE)
        assertThat(pitch).isAtMost(ROW_PITCH_DP + ROW_PITCH_TOLERANCE)
    }

    // --- correcting and deleting ----------------------------------------------------------------

    /**
     * The way to the entry editor, which this screen is now the only one that has.
     *
     * The day drew it until D51 took the rows off the day; between that change and this screen
     * there was no reachable way to correct a logging at all.
     */
    @Test
    fun `each logged item can be corrected`() {
        assertThat(record(listOf(aMeal(items = listOf(anItem()))))).contains("Edit")
    }

    /**
     * The fence around D36: a whole food or a whole meal asks before it goes, but one entry does
     * not. It is cheap to put back exactly (Undo, #25), and asking before every row would slow the
     * screen down for nothing. One press, one delete — nothing stands between them.
     */
    @Test
    fun `deleting an entry happens at once, with no question`() {
        var deleted = 0
        record(listOf(aMeal(items = listOf(anItem(name = "Hummus")))), onDeleteItem = { deleted++ })

        render.click("Delete")

        assertThat(deleted).isEqualTo(1)
    }

    /** The way back from a delete says what happened and offers the one word that reverses it. */
    @Test
    fun `after a deletion there is a way back`() {
        var undone = 0
        val texts = record(
            listOf(aMeal(id = 1, items = listOf(anItem(id = 10, name = "Porridge")))),
            canUndo = true,
            onUndoDelete = { undone++ },
        )

        assertThat(texts).contains("Deleted")
        assertThat(texts).contains("Undo")

        render.click("Undo")
        assertThat(undone).isEqualTo(1)
    }

    /**
     * **REWRITTEN, NOT MOVED, and it inverts.** On the day this asserted the Undo was drawn ABOVE
     * the list: a day of several meals put it below the fold, and an Undo nobody can see is a delete
     * that cannot be undone. This screen answers the same objection the other way — the actions are
     * pinned to the bottom edge (D50, #41), where they cannot go below the fold at all — so the
     * order is now the opposite one, and copying the old assertion would have failed for the right
     * reason at the wrong time.
     */
    @Test
    fun `the way back from a deletion is pinned below the list, not above it`() {
        record(
            listOf(aMeal(id = 1, items = listOf(anItem(id = 10, name = "Porridge")))),
            canUndo = true,
        )

        assertThat(render.isDrawnBefore("Porridge", "Deleted")).isTrue()
    }

    /**
     * **REWRITTEN AGAINST A CONDITION THIS SCREEN APPLIES.** Parked against the bare list it could
     * not be asserted at all: whether the bar is drawn is not the list's decision. It is this
     * screen's, and it is asserted with a logging on screen that could have been deleted.
     */
    @Test
    fun `nothing offers an undo until something has been deleted`() {
        val texts = record(listOf(aMeal(items = listOf(anItem(name = "Porridge")))))

        assertThat(texts).doesNotContain("Undo")
        assertThat(texts).doesNotContain("Deleted")
    }

    // --- when each logging was eaten (D33) ------------------------------------------------------

    /**
     * Each logging shows when it was eaten, named so that two times are never two identical
     * controls.
     *
     * D33. A meal logged on its own day shows its time; one written down on a later day shows
     * "--:--", because its stored moment is the moment of writing, on another date. What a screen
     * reader hears names the meal too — the agent walk could not tell apart fields that shared a
     * name (issue #12).
     *
     * **This is why the tappable time had to leave the day.** A part of the clock covers several
     * loggings with several times, and one row cannot be the control that sets one of them (D51).
     * Here each logging is a row again, so it can be — and the colophon on the day now says so:
     * "Open the day's record to set it."
     */
    @Test
    fun `each meal shows when it was eaten, and one written down later shows it is not known`() {
        val texts = record(
            listOf(
                aMeal(
                    id = 1,
                    epochDay = shownDay,
                    loggedAtMillis = at(shownDay, 9, 30),
                    items = listOf(anItem(id = 11, name = "Omelette")),
                ),
                aMeal(
                    id = 2,
                    epochDay = shownDay,
                    loggedAtMillis = at(shownDay + 1, 8, 0),
                    items = listOf(anItem(id = 12, name = "Cottage cheese")),
                ),
            ),
        )

        assertThat(texts).contains("09:30")
        assertThat(texts).contains("Eaten at 09:30: Omelette. Tap to change the time.")
        assertThat(texts).contains("--:--")
        assertThat(texts).contains("Time not known: Cottage cheese. Tap to set it.")
        // The later-written meal's stored moment is 08:00 the NEXT day, and must not be shown as
        // a time on this one.
        assertThat(texts).doesNotContain("08:00")
    }

    // --- a plain tap on a row, and the hint about holding one (public issue #12) --------------

    /**
     * A tap on a row opens it to be corrected, exactly as its Edit does.
     *
     * The row lit up under a tap and then did nothing, unless rows were already being chosen — a
     * touch that answers with a ripple and no result reads as a broken screen. Correcting is what
     * this screen is for (D50), so that is what the tap means; Edit stays, for anyone who looks for
     * a word rather than a row.
     */
    @Test
    fun `a tap on a row opens it to be corrected, as its Edit does`() {
        val hummus = anItem(id = 10, name = "Hummus")
        val opened = mutableListOf<FoodItem>()
        record(listOf(aMeal(id = 1, items = listOf(hummus))), onEditItem = { opened += it })

        render.click("Hummus")

        assertThat(opened).containsExactly(hummus)
    }

    /** While choosing, the same tap ticks the row instead, and opens nothing. */
    @Test
    fun `while choosing, a tap on a row ticks it and opens nothing`() {
        val opened = mutableListOf<FoodItem>()
        val ticked = mutableListOf<Long>()
        record(
            listOf(
                aMeal(
                    id = 1,
                    items = listOf(anItem(id = 10, name = "Eggs"), anItem(id = 11, name = "Tomato")),
                ),
            ),
            chosen = setOf(10L),
            onEditItem = { opened += it },
            onToggleChosen = { ticked += it },
        )

        render.click("Tomato")

        assertThat(ticked).containsExactly(11L)
        assertThat(opened).isEmpty()
    }

    /**
     * The hint on how choosing starts is read BEFORE the rows it is about. Printed after the whole
     * list, it was below the fold on any day long enough to need it.
     */
    @Test
    fun `the hint on holding a row is drawn above the list, not after it`() {
        record(
            listOf(
                aMeal(id = 1, items = listOf(anItem(id = 10, name = "Porridge"))),
                aMeal(id = 2, items = listOf(anItem(id = 11, name = "Coffee"))),
            ),
        )

        assertThat(render.isDrawnBefore("Hold anything here", "Porridge")).isTrue()
    }

    /**
     * With one row ticked, the bar says how to tick more. The hint about holding is gone by then —
     * it has been acted on — and without a line in its place nothing on screen says the next tap
     * adds to the choice.
     */
    @Test
    fun `with one row chosen the bar says how to add more, and with two it does not`() {
        val meals = listOf(fourRows())

        assertThat(record(meals, chosen = setOf(1L))).contains("Tap anything else to add it.")
        assertThat(record(meals, chosen = setOf(1L, 2L))).doesNotContain("Tap anything else to add it.")
    }

    // --- choosing rows, and what they become (design §3.5, D50) ---------------------------------

    /**
     * A tick is the whole of what says a row is in the choice, so it is named for a screen reader:
     * nothing else on the row says whether it is in or out.
     */
    @Test
    fun `a tick is drawn against a chosen row`() {
        val texts = record(
            listOf(
                aMeal(
                    items = listOf(
                        anItem(id = 1, name = "Eggs", kcal = 155),
                        anItem(id = 2, name = "Tomato", kcal = 27),
                    ),
                ),
            ),
            chosen = setOf(1L),
        )

        assertThat(texts).contains("Chosen")
        assertThat(texts).contains("Not chosen")
    }

    /**
     * With nothing chosen, no row wears a tick.
     *
     * VACUOUS ON THE DAY once the rows went.
     */
    @Test
    fun `with nothing chosen no row wears a tick`() {
        val texts = record(listOf(aMeal(items = listOf(anItem(id = 1, name = "Eggs")))))

        assertThat(texts).doesNotContain("Chosen")
        assertThat(texts).doesNotContain("Not chosen")
    }

    /**
     * **REWRITTEN AGAINST A CONDITION THIS SCREEN APPLIES**, for the reason the Undo one above was:
     * whether the bar exists is the screen's decision and the bare list could not make it.
     *
     * "Make a meal from", not "…these": the one-form reads "this one", and an absence test narrower
     * than every form the action can take would pass with the action drawn.
     */
    @Test
    fun `with nothing chosen there is no meal to make, and nothing to delete in one go`() {
        val texts = record(listOf(aMeal(items = listOf(anItem(id = 1, name = "Eggs")))))

        assertThat(texts.none { it.startsWith("Make a meal from") }).isTrue()
        assertThat(texts.none { it.startsWith("Delete these") }).isTrue()
        assertThat(texts).doesNotContain("All")
    }

    /**
     * The action carries the number, because "Make a meal" on its own does not say whether the four
     * he thinks are ticked are the four that are.
     */
    @Test
    fun `the action says how many rows the meal would be made from`() {
        val texts = record(listOf(fourRows()), chosen = setOf(1L, 2L, 3L, 4L))

        assertThat(texts).contains("Make a meal from these 4")
    }

    /**
     * The action is offered from a single row, so "these 1" is reachable. The count is still
     * carried — in words, "this one" — through a plural resource rather than a test for 1 in code,
     * because Hebrew (#9) has more plural forms than English and will translate the same resource.
     */
    @Test
    fun `with one row chosen the action reads naturally`() {
        val texts = record(
            listOf(aMeal(items = listOf(anItem(id = 1, name = "Eggs")))),
            chosen = setOf(1L),
        )

        assertThat(texts).contains("Make a meal from this one")
        assertThat(texts.none { it.contains("these 1") }).isTrue()
    }

    /**
     * **REWRITTEN HERE, because the wiring it is about moved with the action.** On the day this was
     * the screen's own wiring between pressing the action and dismissing a refusal already read;
     * the day no longer has either. Without it, reopening the sheet puts an old sentence under a new
     * name — and it was reachable and untested once before, because every callback the render helper
     * passed was a no-op and nothing would have noticed the call going missing.
     */
    @Test
    fun `making a meal from what is chosen clears a refusal already answered`() {
        var dismissed = 0

        record(
            listOf(
                aMeal(
                    items = listOf(
                        anItem(id = 1, name = "Eggs"),
                        anItem(id = 2, name = "Tomato"),
                    ),
                ),
            ),
            chosen = setOf(1L, 2L),
            refusal = "You already have a meal called “Vegetable salad”.",
            onDismissRefusal = { dismissed++ },
        )

        render.click("Make a meal from these 2")

        assertThat(dismissed).isEqualTo(1)
    }

    /**
     * "All" is back where what it ticks is on the screen it is pressed from (D50).
     *
     * It was in the day's title bar, over a screen that had no rows to tick and no way to start
     * choosing — a button that could never appear and would have acted on nothing if it had.
     */
    @Test
    fun `All ticks every row, from the bar over the choice`() {
        var chosenAll = 0
        record(
            listOf(fourRows()),
            chosen = setOf(1L),
            onChooseAll = { chosenAll++ },
        )

        render.click("All")

        assertThat(chosenAll).isEqualTo(1)
    }

    /**
     * A whole selection goes in one act (#50), and the button says how much it is about to take.
     *
     * Its own words rather than the row's plain "Delete": two controls under one name is what the
     * agent walk could not tell apart (#12), and the count belongs on the button that
     * acts on it.
     */
    @Test
    fun `everything chosen can be deleted in one go`() {
        var deletedChosen = 0
        val texts = record(
            listOf(fourRows()),
            chosen = setOf(1L, 2L),
            onDeleteChosen = { deletedChosen++ },
        )

        assertThat(texts).contains("Delete these 2")

        render.click("Delete these 2")

        assertThat(deletedChosen).isEqualTo(1)
    }

    /**
     * Ticking a meal he built takes the whole meal (D50, #50).
     *
     * Its parts are behind the row, so its kicker is the only thing on screen that stands for them.
     * Without this the only way to remove a built meal in one act would be to open it first and tick
     * its parts one at a time, which is the thing #50 asked to stop doing.
     */
    @Test
    fun `ticking a meal he built takes the whole meal`() {
        var taken: Meal? = null
        val salad = aMeal(
            id = 7,
            items = listOf(anItem(id = 1, name = "Cucumber"), anItem(id = 2, name = "Olive oil")),
            savedMealId = 1,
            savedMealName = "Vegetable salad",
        )

        record(
            listOf(salad, aMeal(id = 8, items = listOf(anItem(id = 3, name = "Eggs")))),
            chosen = setOf(3L),
            onChooseMeal = { taken = it },
        )

        render.click("Vegetable salad")

        assertThat(taken?.id).isEqualTo(7L)
    }

    // --- "2 portion" on a logged row (D37) ------------------------------------------------------

    /**
     * The row's stored words say "2 portion", written once by whatever logged it. The plural is
     * chosen when the row is drawn, from its amount and unit, so every row already logged reads
     * right too — nothing stored is rewritten.
     *
     * These five are the whole of D37's rendered coverage. Deleted rather than moved, D37 would
     * have lost its only render-level fence.
     */
    @Test
    fun `two portions read as two portions`() {
        val texts = record(listOf(aMeal(items = listOf(portions(2.0)))))

        assertThat(texts.any { it.endsWith("· 2 portions") }).isTrue()
        assertThat(texts.none { it.endsWith("· 2 portion") }).isTrue()
    }

    @Test
    fun `one portion reads as one portion`() {
        val texts = record(listOf(aMeal(items = listOf(portions(1.0)))))

        assertThat(texts.any { it.endsWith("· 1 portion") }).isTrue()
        assertThat(texts.none { it.contains("1 portions") }).isTrue()
    }

    /** Repeat accepts any amount he types, so a part-portion is a real row and takes the plural. */
    @Test
    fun `a part-portion reads as portions`() {
        val texts = record(listOf(aMeal(items = listOf(portions(1.5)))))

        assertThat(texts.any { it.endsWith("· 1.5 portions") }).isTrue()
    }

    /**
     * Only the app's own word is pluralised. "Slice" came from the model; the app does not know
     * another word's plural and does not invent one.
     */
    @Test
    fun `a unit the app did not name is drawn as it was written`() {
        val slice = anItem(
            name = "Pizza",
            portion = "2 slice",
            portionAmount = 2.0,
            portionUnit = "slice",
            kcal = 570,
        )

        val texts = record(listOf(aMeal(items = listOf(slice))))

        assertThat(texts.any { it.endsWith("· 2 slice") }).isTrue()
        assertThat(texts.none { it.contains("2 slices") }).isTrue()
    }

    /**
     * The row hands its stored words to the shared rule, not just its numbers: words that say more
     * than the amount and unit are what the row says, and are drawn as stored (D5), though the unit
     * is the app's own "portion".
     */
    @Test
    fun `a portion row whose words say more is drawn as it was written`() {
        val saysMore = anItem(
            id = 1,
            name = "Leftover stew",
            portion = "2 portion (large)",
            portionAmount = 2.0,
            portionUnit = FoodFacts.PORTION,
        )

        val texts = record(listOf(aMeal(items = listOf(saysMore))))

        assertThat(texts.any { it.endsWith("· 2 portion (large)") }).isTrue()
        assertThat(texts.none { it.contains("2 portions") }).isTrue()
    }

    // --- the fixtures ---------------------------------------------------------------------------

    /**
     * A row logged in the app's own "portion", its words stored by the same call every writer of a
     * row uses — so "2 portion" here is exactly what the phone's rows carry.
     */
    private fun portions(amount: Double): FoodItem = anItem(
        id = 1,
        name = "Leftover stew",
        portion = Portions.words(amount, FoodFacts.PORTION),
        portionAmount = amount,
        portionUnit = FoodFacts.PORTION,
    )

    /** One logging of four rows, so a choice of four is a choice a screen could really hold. */
    private fun fourRows(): Meal = aMeal(
        id = 1,
        items = (1..4).map { n -> anItem(id = n.toLong(), name = "Row $n", kcal = 100) },
    )

    private val shownDay = 20_699L

    /** The zone the moments here are built in, which is the one the list reads them back in. */
    private val zone: ZoneId = ZoneId.systemDefault()

    private fun at(epochDay: Long, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(LocalDate.ofEpochDay(epochDay), LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    /**
     * The record screen, drawn as the app draws it.
     *
     * Every test above goes through this one call, which is what the parked version of this file was
     * built for: when the screen existed, one helper changed and no test did.
     */
    private fun record(
        meals: List<Meal>,
        openMeals: Set<Long> = emptySet(),
        chosen: Set<Long> = emptySet(),
        refusal: String? = null,
        canUndo: Boolean = false,
        isToday: Boolean = true,
        todayEpochDay: Long = shownDay,
        onEditItem: (FoodItem) -> Unit = {},
        onDeleteItem: (FoodItem) -> Unit = {},
        onUndoDelete: () -> Unit = {},
        onToggleChosen: (Long) -> Unit = {},
        onChooseMeal: (Meal) -> Unit = {},
        onChooseAll: () -> Unit = {},
        onDeleteChosen: () -> Unit = {},
        onDismissRefusal: () -> Unit = {},
    ): List<String> = render.texts {
        RecordScreen(
            state = RecordUiState(
                epochDay = shownDay,
                todayEpochDay = todayEpochDay,
                isToday = isToday,
                meals = meals,
                openMeals = openMeals,
                chosen = chosen,
                refusal = refusal,
            ),
            canUndo = canUndo,
            onBack = {},
            onToggleMeal = {},
            onEditItem = onEditItem,
            onDeleteItem = onDeleteItem,
            onUndoDelete = onUndoDelete,
            onSetEatenAt = { _, _, _ -> },
            onBeginChoosing = {},
            onToggleChosen = onToggleChosen,
            onChooseMeal = onChooseMeal,
            onChooseAll = onChooseAll,
            onClearChoosing = {},
            onDeleteChosen = onDeleteChosen,
            onMakeMealFromChosen = {},
            onDismissRefusal = onDismissRefusal,
        )
    }

    private companion object {
        /**
         * The most a logged row may cost, vertically.
         *
         * Twice what a plain typed item currently needs, which leaves room for the extra line an
         * estimate's confidence takes without letting a card creep back in.
         */
        const val MAX_ROW_PITCH = 120

        /**
         * What a logged row actually costs, measured rather than remembered.
         *
         * Taken from the render in `a logged row stays as short as it was`, at phone width, on the
         * list's own meal rows with the time column each one leads with (D33). It is a fact about
         * this layout, so a change to the layout is expected to change it — and to change it on
         * purpose, in the same commit, with the new number measured the same way.
         */
        const val ROW_PITCH_DP = 83

        /** A font metric may move the pitch by a dp or two; a lost line moves it by tens. */
        const val ROW_PITCH_TOLERANCE = 4
    }
}
