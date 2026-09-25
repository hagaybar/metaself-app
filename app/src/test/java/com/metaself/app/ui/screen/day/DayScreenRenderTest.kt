package com.metaself.app.ui.screen.day

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.DayPart
import com.metaself.app.domain.day.PartOfTheClock
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.movement.ActivityEnergy
import com.metaself.app.domain.movement.DayMovement
import com.metaself.app.domain.movement.ExerciseSession
import com.metaself.app.domain.movement.MovementCredit
import com.metaself.app.domain.movement.MovementSource
import com.metaself.app.domain.movement.MovementToday
import com.metaself.app.domain.streak.Streak
import com.metaself.app.domain.window.DayMeasured
import com.metaself.app.domain.window.DayVerdict
import com.metaself.app.domain.window.DayWindow
import com.metaself.app.domain.window.EatingStretch
import com.metaself.app.domain.window.EatingWindow
import com.metaself.app.domain.window.MeasuredWindow
import com.metaself.app.domain.window.StretchVerdict
import com.metaself.app.domain.day.Remaining
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.day.aMeal
import com.metaself.app.domain.day.anItem
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.portion.Portions
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.target.DailyTargetCalculator
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.ComposeRender
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** JUnit 4 by necessity — Robolectric's runner is JUnit 4. */
@RunWith(RobolectricTestRunner::class)
class DayScreenRenderTest {

    private val render = ComposeRender()
    private val target = DailyTargetCalculator.of(aProfile(), TEST_YEAR)

    @After
    fun tearDown() = render.dispose()

    /**
     * DELIBERATELY CHANGED by D49. The answer used to be one string — "2,090 kcal left" — because a
     * ring draws one line of text in one face. A page does not: the figure is set in the display
     * face at 72 sp and the words beside it in the text face at 14, and two faces at two sizes
     * cannot come out of one string.
     *
     * So all three pieces are asserted rather than one, and none of them is loosened to "contains a
     * number": a page that drew the figure and lost the word it is a figure OF, or drew the words
     * and lost the figure, fails here exactly as it should.
     */
    @Test
    fun `an empty day says so and shows the whole target`() {
        val texts = draw(emptyList())
        assertThat(texts).contains("2,090")
        assertThat(texts).contains("kcal left")
        assertThat(texts).contains("of 2,090")
        assertThat(texts).contains("Nothing logged yet today.")
    }

    /**
     * The denominator says the target once, and the line above it says "kcal" once — the defect
     * D49 exists to fix. "1,240 kcal left of 2,090 kcal" wrapped on the owner's phone for no better
     * reason than saying the unit twice.
     */
    @Test
    fun `the day's answer never says kcal twice`() {
        val texts = draw(emptyList())

        assertThat(texts.count { it.contains("kcal") }).isEqualTo(1)
        assertThat(texts.none { it.contains("kcal") && it.contains("2,090") }).isTrue()
    }

    /**
     * A past day nobody wrote anything on has no figure at all (D4).
     *
     * The whole target is not a measurement of a fast nobody recorded, so there is no number to
     * draw and no denominator for it to be out of — the sentence takes the slot instead. A dash
     * would put a punctuation mark where the page promises its one answer.
     */
    @Test
    fun `a past day with nothing on it draws no figure and no denominator`() {
        val texts = draw(emptyList(), isToday = false)

        assertThat(texts).contains("Nothing logged")
        assertThat(texts).doesNotContain("2,090")
        assertThat(texts).doesNotContain("of 2,090")
    }

    /**
     * How far over, never a negative — and in two pieces since D49, like every other day.
     *
     * Both are asserted. The figure alone would pass on a page that had quietly stopped saying
     * which side of the target 210 was on, which is the one thing this test is for.
     */
    @Test
    fun `past the target it says how far over`() {
        val texts = draw(listOf(aMeal(items = listOf(anItem(kcal = 2300)))))
        assertThat(texts).contains("210")
        assertThat(texts).contains("kcal over")
    }

    /**
     * D8, and the reason the by-hand path is named for what it does: logging never depends on the
     * network, so typing the numbers stays one tap from the day and never sits behind a search. An
     * assertion on "Add something" could not tell the two paths apart.
     */
    @Test
    fun `typing the numbers is still one tap from the day`() {
        assertThat(draw(emptyList())).contains("Type the numbers")
    }

    /** For the case the owner already knows is new, describing stays one tap away too. */
    @Test
    fun `describing a meal is one tap from the day`() {
        assertThat(draw(emptyList())).contains("Describe a meal")
    }

    /** The search over the owner's own foods is the floating button now, not a quiet-row entry. */
    @Test
    fun `the day's main action is not duplicated in the quiet row`() {
        assertThat(draw(emptyList())).doesNotContain("Add something")
    }

    // --- the three ways in are buttons, not links (#69) ------------------------------------------

    /**
     * Three controls, three names, three destinations.
     *
     * They became outlined buttons rather than bare `TextButton`s, which is the kind of change that
     * can quietly re-point one of them at the wrong callback while the screen still looks right.
     * Each is pressed on its own and the other two are checked to have stayed still, so a crossed
     * wire fails here instead of on the phone.
     *
     * It is also the check #12 asks for on this row: a screen reader, and anything driving the app
     * without seeing it, can name each of the three and get that one. They already had three
     * distinct labels, so nothing was added — but nothing now guarantees that silently either.
     */
    @Test
    fun `each of the three ways in presses its own way in`() {
        var described = 0
        var scanned = 0
        var typed = 0
        val texts = draw(
            emptyList(),
            onDescribe = { described++ },
            onScan = { scanned++ },
            onAdd = { typed++ },
        )
        assertThat(texts).containsAtLeast("Describe a meal", "Scan", "Type the numbers")

        render.click("Describe a meal")
        assertThat(listOf(described, scanned, typed)).isEqualTo(listOf(1, 0, 0))

        render.click("Scan")
        assertThat(listOf(described, scanned, typed)).isEqualTo(listOf(1, 1, 0))

        render.click("Type the numbers")
        assertThat(listOf(described, scanned, typed)).isEqualTo(listOf(1, 1, 1))
    }

    /**
     * On a screen too narrow to hold all three, one of them moves down — none of them is cut off.
     *
     * **This is the reported "Type the…" overflow, and it is the fence #51 put here.** A plain
     * `Row` answers an overflow by drawing its last child past the edge, where it is clipped
     * mid-word and nothing — no test, no warning, no crash — says so; a `FlowRow` moves the whole
     * control to the next line instead. Nothing else distinguishes the two, so nothing but a
     * reading of where the controls landed can tell whether the wrapping layout is still there.
     * Turning this row back into a `Row` fails here.
     *
     * Both halves are asserted, because each alone passes on the broken layout: that the last
     * control has a different top says the row wrapped, and that no control reaches past the canvas
     * says it wrapped instead of overflowing.
     *
     * **The narrow screen is a resource qualifier, not an argument.** Robolectric lays out on the
     * device it was configured with, and the width `ComposeRender` measures the decor at does not
     * change that — the default device comes back 320 dp wide at a density of 1 whatever it is
     * handed. That is also why this test needs a width of its own: Robolectric has no real font, so
     * a label measures a fraction of what it takes on a phone, and three buttons that would fill a
     * real 320 dp screen sit inside a third of this one.
     */
    @Test
    @Config(qualifiers = "+w150dp")
    fun `the three ways in wrap onto another line rather than off the edge`() {
        draw(emptyList())

        assertThat(render.rightEdgeDp("Describe a meal")).isAtMost(render.canvasWidthDp)
        assertThat(render.rightEdgeDp("Scan")).isAtMost(render.canvasWidthDp)
        assertThat(render.rightEdgeDp("Type the numbers")).isAtMost(render.canvasWidthDp)

        assertThat(render.topDp("Type the numbers"))
            .isGreaterThan(render.topDp("Describe a meal"))
    }

    // --- the day is four parts of the clock, one line each (D49 item 8 as revised, D51) ----------

    /**
     * One line per part of the clock, each saying **its name and its hours**.
     *
     * The hours are on the row because that is the owner's requirement rather than decoration: the
     * rule he locates a thing by has to be readable on the screen it governs. He knows when he ate
     * something, so he knows which line holds it — no arithmetic, and no dependence on what else he
     * logged.
     *
     * **They end at :59.** An earlier draft wrote the friendlier "12:00–18:00", which is ambiguous
     * at exactly the boundary the row exists to make unambiguous: 18:00 is Evening. A range printed
     * to make a rule followable must not need a footnote — so that is asserted here, not assumed.
     */
    @Test
    fun `the day draws one line per part of the clock, with its name and its hours`() {
        val texts = draw(
            listOf(
                aMeal(
                    id = 1,
                    epochDay = shownDay,
                    loggedAtMillis = at(shownDay, 8),
                    items = listOf(anItem(id = 10, name = "Porridge")),
                ),
                aMeal(
                    id = 2,
                    epochDay = shownDay,
                    loggedAtMillis = at(shownDay, 13),
                    items = listOf(anItem(id = 11, name = "Shawarma")),
                ),
            ),
        )

        assertThat(texts).contains("Morning \u00b7 06:00\u201311:59")
        assertThat(texts).contains("Midday \u00b7 12:00\u201317:59")
        assertThat(texts.none { it.contains("12:00\u201318:00") }).isTrue()
    }

    /**
     * A part with nothing in it is absent, never present and empty.
     *
     * An empty row is a row he has to read and discard, and four of them on a quiet day is the
     * dashboard D49 just finished dismantling. It is also what makes an ordinary day two or three
     * lines rather than always four.
     */
    @Test
    fun `a part of the clock with nothing in it is not drawn at all`() {
        val texts = draw(
            listOf(
                aMeal(
                    id = 1,
                    epochDay = shownDay,
                    loggedAtMillis = at(shownDay, 8),
                    items = listOf(anItem(id = 10, name = "Porridge")),
                ),
            ),
        )

        assertThat(texts.any { it.startsWith("Morning") }).isTrue()
        assertThat(texts.none { it.startsWith("Night") }).isTrue()
        assertThat(texts.none { it.startsWith("Midday") }).isTrue()
        assertThat(texts.none { it.startsWith("Evening") }).isTrue()
    }

    /**
     * A row says what is in it from what is already on the record, and says more than one as a
     * COUNT — never as an invented label.
     *
     * "Breakfast" would be a claim about what the eating was, which is D4's defect: an inference
     * printed as a fact, and wrong on exactly the days his eating window puts a first meal at
     * eleven. There is no Breakfast, Lunch or Dinner anywhere in this app.
     */
    @Test
    fun `a row names the first thing in it and counts the rest`() {
        val texts = draw(
            listOf(
                aMeal(
                    id = 1,
                    epochDay = shownDay,
                    loggedAtMillis = at(shownDay, 8),
                    items = listOf(
                        anItem(id = 10, name = "Porridge", kcal = 180),
                        anItem(id = 11, name = "Coffee", kcal = 20),
                    ),
                ),
            ),
        )

        assertThat(texts).contains("Porridge")
        assertThat(texts.any { it.contains("2 things") }).isTrue()
        assertThat(texts.none { it.contains("Breakfast") }).isTrue()
        // The SECOND thing's name, which is the one that proves the items are not being listed.
        // Asserting the FIRST one is absent would contradict the line above: when a logging has no
        // name of its own, its first food's name IS the row's label.
        assertThat(texts).doesNotContain("Coffee")
    }

    /**
     * The row's total is summed from the loggings' own rows, like every other total in this app.
     *
     * There is no `Meal.kcal`, and a meal definition's stored total is the wrong number for
     * anything logged adjusted — a row reading that would disagree with the figure at the top of
     * this very page on exactly the days he changed something.
     */
    @Test
    fun `a row says what its part came to`() {
        val texts = draw(
            listOf(
                aMeal(
                    id = 1,
                    epochDay = shownDay,
                    loggedAtMillis = at(shownDay, 8),
                    items = listOf(anItem(id = 10, name = "Porridge", kcal = 180)),
                ),
                aMeal(
                    id = 2,
                    epochDay = shownDay,
                    loggedAtMillis = at(shownDay, 9),
                    items = listOf(anItem(id = 11, name = "Coffee", kcal = 20)),
                ),
            ),
        )

        assertThat(texts).contains("200 kcal")
    }

    /**
     * **Nothing of a logging's detail is on the day any more**, which is the whole of what D49's
     * revision bought: the page stops growing with what he ate and starts growing with when.
     *
     * A real fence, not a vacuous one — the day is drawn with a logging whose figures, provenance
     * and controls all exist and would have been printed before.
     */
    @Test
    fun `the day draws no item figures and no row controls`() {
        val texts = draw(
            listOf(
                aMeal(
                    id = 1,
                    epochDay = shownDay,
                    loggedAtMillis = at(shownDay, 8),
                    items = listOf(
                        anItem(id = 10, name = "Hummus", kcal = 180, proteinG = 6, carbsG = 12, fatG = 12),
                    ),
                ),
            ),
        )

        assertThat(texts).doesNotContain("180 kcal \u00b7 P 6 \u00b7 C 12 \u00b7 F 12")
        assertThat(texts).doesNotContain("Edit")
        assertThat(texts).doesNotContain("Delete")
        // The tappable time went with them: a row covering several loggings with several times
        // cannot be the control that sets one of them (D51).
        assertThat(texts).doesNotContain("--:--")
        assertThat(texts.none { it.startsWith("Eaten at") }).isTrue()
    }

    /**
     * A logging written down on another day belongs to no part of the clock, and gets the last row.
     *
     * This is the one case that exceeds D51's cap of four, and it is deliberate: it is the only row
     * asking to be fixed, and the colophon sends him to it. Filed by its stored hour it would land
     * in a part it may have nothing to do with — this one was written at 08:00 the next morning,
     * which would read as Morning, and he ate it he does not know when.
     */
    @Test
    fun `a logging with no honest time gets the last row, and that row has no hours`() {
        val texts = draw(
            listOf(
                aMeal(
                    id = 1,
                    epochDay = shownDay,
                    loggedAtMillis = at(shownDay, 8),
                    items = listOf(anItem(id = 10, name = "Porridge")),
                ),
                aMeal(
                    id = 2,
                    epochDay = shownDay,
                    loggedAtMillis = at(shownDay + 1, 8, 0),
                    items = listOf(anItem(id = 11, name = "Cottage cheese")),
                ),
            ),
        )

        assertThat(texts).contains("Time not known")
        assertThat(texts.none { it.startsWith("Time not known \u00b7") }).isTrue()
        assertThat(render.isDrawnBefore("Morning", "Time not known")).isTrue()
    }

    /**
     * Every row is a real control, and a screen reader can tell four of them apart (#12).
     *
     * The agent walk could not tell apart eight fields that shared four names. Four lines
     * that each read "a meal, some calories" would be exactly that defect again, so what is heard
     * names the part, what is in it and what it came to.
     */
    @Test
    fun `each row is a control, named by its part`() {
        var opened: DayPart? = null
        val texts = draw(
            listOf(
                aMeal(
                    id = 1,
                    epochDay = shownDay,
                    loggedAtMillis = at(shownDay, 8),
                    items = listOf(anItem(id = 10, name = "Porridge", kcal = 180)),
                ),
            ),
            onOpenPart = { opened = it },
        )

        assertThat(texts).contains(
            "Morning \u00b7 06:00\u201311:59: Porridge, 180 kcal. Open the day's record.",
        )

        render.click("Morning")
        assertThat(opened?.part).isEqualTo(PartOfTheClock.MORNING)
    }

    @Test
    fun `an empty past day says nothing was logged, in the past tense`() {
        assertThat(draw(emptyList(), isToday = false))
            .contains("Nothing was logged on this day.")
    }

    @Test
    fun `an empty today says nothing is logged yet`() {
        assertThat(draw(emptyList(), isToday = true))
            .contains("Nothing logged yet today.")
    }

    @Test
    fun `a past day over its target is stated, never coloured`() {
        // Decision D14. A colour on a day the owner cannot change is a reproach nobody can act on.
        // The wording still says 'over'; only the warning colour is withheld, which a render test
        // cannot see.
        //
        // Since D49 the statement is two nodes rather than one, so the pin is re-expressed on BOTH
        // of them: the figure, and the word that says which side of the target it falls. Asserting
        // only the figure would let a page state 210 and say nothing about it and still pass, which
        // is precisely the reading this test exists to forbid.
        val texts = draw(listOf(aMeal(items = listOf(anItem(kcal = 2300)))), isToday = false)
        assertThat(texts).contains("210")
        assertThat(texts).contains("kcal over")
    }

    /**
     * The page is read down, and this is the whole of what D49 changed that no wording test can see.
     *
     * The number first, because the screen exists to answer one question. The macros next, because
     * they qualify that answer. The food after them. **The colophon last** — the window's tally and
     * how consistently he has logged used to sit between him and his food, in the middle of the
     * page, at the same volume as everything else; they are facts about the record rather than
     * about what he is deciding now, and nothing but their position can say so.
     *
     * The prefixes are chosen to be unique in this particular day, and each of the four is
     * justified, because `isDrawnBefore` takes the FIRST node whose text starts with the prefix and
     * a prefix that matches something earlier silently compares the wrong pair of nodes.
     *
     * - **"1,910"** — 2,090 less the 180 eaten, this day's remainder, printed once. A bare "2,090"
     *   would not do: it also begins "of 2,090".
     * - **"105 g"** — the protein column's figure, 145 g of target less the 40 g in the omelette.
     *   The three macro figures on this day are "105 g", "180 g" and "40 g", and no other node
     *   begins with it; the food's own line begins "180 kcal".
     * - **"Omelette"** — the one food on the day, and since D51 the label of the part of the clock
     *   it was logged in: a logging with no name of its own lends the row its first food's name.
     *   Still unique, and still the thing that has to sit between the macros and the colophon.
     * - **"12 days in a row"** — printed by the colophon's streak line and by nothing else.
     *
     * **"105 g" replaces "P", which was not a justified prefix at all.** "P" is one character,
     * matched with `startsWith` against every node on the page, so the macro kicker won only
     * because nothing drawn above it happened to begin with a capital P. A food named "Porridge", a
     * notice, or a button would have taken the match and the test would have gone on passing while
     * comparing something else entirely.
     */
    @Test
    fun `the page is read in order - the number, the macros, the food, then the colophon`() {
        draw(
            listOf(aMeal(id = 1, items = listOf(anItem(id = 10, name = "Omelette", kcal = 180)))),
            streak = Streak(currentDays = 12, lifetimeDays = 45, daysInLast30 = 22),
        )

        // 2,090 less the 180 eaten, drawn alone in the display face, then the protein column.
        assertThat(render.isDrawnBefore("1,910", "105 g")).isTrue()
        assertThat(render.isDrawnBefore("105 g", "Omelette")).isTrue()
        assertThat(render.isDrawnBefore("Omelette", "12 days in a row")).isTrue()
    }

    @Test
    fun `a target change the owner has not seen is announced, with a way to dismiss it`() {
        val texts = draw(emptyList(), notice = "Your daily target has changed from 2,090 to 2,050 kcal.")

        assertThat(texts).contains("Your daily target has changed from 2,090 to 2,050 kcal.")
        assertThat(texts).contains("Got it")
    }

    @Test
    fun `a day with nothing to announce announces nothing`() {
        val texts = draw(emptyList())

        assertThat(texts).doesNotContain("Got it")
    }

    /**
     * **The day never offers an Undo at all**, whatever has just been deleted.
     *
     * This used to say "not until something has been deleted", and it was moved and rewritten
     * rather than left: nothing is deleted from the day any more, because the rows are not on it
     * (D51). So the conditional version has no condition left to test here, and is owed to the
     * record screen, which is where a delete and its way back both live now (D50). What is left is
     * a real fence: an Undo on this screen would be a way back from something this screen cannot do.
     */
    @Test
    fun `the day never offers an undo, because nothing is deleted here`() {
        assertThat(draw(listOf(aMeal(items = listOf(anItem(name = "Porridge"))))))
            .doesNotContain("Undo")
    }

    private companion object {
        /** A sheet shorter than twenty parts need, so without scrolling the button would fall below it. */
        const val SHORT_SHEET_DP = 400

        // The fence on how tall a LOGGED row may be, and its measured pitch, moved to
        // `DayRecordRenderTest` with the list they measure. They are facts about the record's rows,
        // which this screen no longer draws. **The day's new part-of-the-clock row has no pitch
        // fence**, and if it is ever given one that is a new constant with a new number printed
        // from a new render — never 83 carried over from a different row.

        /** The naming sheet's sentence before a name is typed, today and on a past day (D37). */
        const val UNNAMED_TODAY =
            "Today will show these as one row, under the name you give it. Not one number you " +
                "logged changes, and you can open the row to see the parts."
        const val UNNAMED_PAST =
            "That day will show these as one row, under the name you give it. Not one number you " +
                "logged changes, and you can open the row to see the parts."
    }

    /** D52: one figure on the day, not three. The other two are on the record screen. */
    @Test
    fun `the day carries the run and only the run`() {
        val texts = draw(
            meals = emptyList(),
            streak = Streak(currentDays = 12, lifetimeDays = 45, daysInLast30 = 22),
        )

        assertThat(texts).contains("12 days in a row")
        assertThat(texts.any { it.contains("days logged") }).isFalse()
        assertThat(texts.any { it.contains("of the last 30") }).isFalse()
    }

    /** D14: a run that has ended is simply not mentioned; the month speaks instead (D52). */
    @Test
    fun `a run that has ended is not printed as a zero`() {
        val texts = draw(
            meals = emptyList(),
            streak = Streak(currentDays = 0, lifetimeDays = 45, daysInLast30 = 3),
        )

        assertThat(texts.any { it.contains("in a row") }).isFalse()
        assertThat(texts).contains("3 of the last 30 days")
        assertThat(texts.any { it.contains("days logged") }).isFalse()
    }

    @Test
    fun `before anything is logged the counts say nothing at all`() {
        val texts = draw(meals = emptyList())

        assertThat(texts.any { it.contains("in a row") }).isFalse()
        assertThat(texts.any { it.contains("days logged") }).isFalse()
        assertThat(texts.any { it.contains("of the last 30") }).isFalse()
    }

    /** "0 of the last 30 days" is the same reproach as "0 days in a row" (D52). */
    @Test
    fun `a quiet month prints no figure at all`() {
        val texts = draw(
            meals = emptyList(),
            streak = Streak(currentDays = 0, lifetimeDays = 45, daysInLast30 = 0),
        )

        assertThat(texts.any { it.contains("of the last 30") }).isFalse()
        assertThat(texts.any { it.contains("in a row") }).isFalse()
    }

    /**
     * A milestone reached today is set as one: the figure on its own, in the display face, and its
     * words beside it (D52). Only the text is assertable here — the size and the teal are not.
     */
    @Test
    fun `a milestone reached today sets its figure apart from its words`() {
        val texts = draw(
            meals = emptyList(),
            streak = Streak(currentDays = 30, lifetimeDays = 45, daysInLast30 = 30),
            loggedToday = true,
        )

        assertThat(texts).contains("30")
        assertThat(texts).contains("days in a row")
        assertThat(texts).doesNotContain("30 days in a row")
    }

    /** The weekly congratulation said it; the figure does not say it again (D52). */
    @Test
    fun `a milestone the congratulation already said is plain`() {
        val texts = draw(
            meals = emptyList(),
            streak = Streak(currentDays = 7, lifetimeDays = 45, daysInLast30 = 20),
            loggedToday = true,
            weeklyCongratulationToday = true,
        )

        assertThat(texts).contains("7 days in a row")
        assertThat(texts).doesNotContain("days in a row")
    }

    /**
     * D12a, the correction that added it: the count is shown every day. Hiding it until it earned
     * calories made the app's only view of his effort conditional on that effort being unusual.
     */
    @Test
    fun `a quiet day still shows its steps`() {
        val texts = draw(
            meals = emptyList(),
            movement = MovementToday(steps = 3_100, normalSteps = 5_200),
        )

        assertThat(texts).contains("3,100 steps")
        assertThat(texts).contains("Your usual day is 5,200")
        assertThat(texts.any { it.contains("kcal earned") }).isFalse()
    }

    @Test
    fun `a busy day shows the steps, the surplus and what it earned`() {
        val busy = MovementToday(
            steps = 10_000,
            normalSteps = 5_200,
            credit = MovementCredit.of(
                today = ActivityEnergy.of(DayMovement(1, steps = 10_000), weightKg = 80.0),
                normalEnergyKcal = ActivityEnergy.of(
                    DayMovement(0, steps = 5_200),
                    weightKg = 80.0,
                ).kcal,
                capKcal = 275,
            ),
        )

        val texts = draw(meals = emptyList(), movement = busy)

        assertThat(texts).contains("10,000 steps")
        assertThat(texts).contains("4,800 more than your usual 5,200")
        // 10,000 steps at 80 kg is 300 kcal, 144 above the usual day's 156, three quarters of
        // which is 108.
        assertThat(texts).contains("+108 kcal earned")
    }

    /**
     * The KNOWN GAP of milestone 1 §6, closed: on a day the band's figure decided the credit, the
     * line beneath the count speaks in that figure, not in steps.
     */
    @Test
    fun `a band-driven day explains itself in movement energy`() {
        val swam = MovementToday(
            steps = 900,
            normalSteps = 5_200,
            energy = ActivityEnergy(580, MovementSource.ACTIVE_CALORIES),
            normalEnergyKcal = 400,
            sessions = listOf(ExerciseSession("Swimming", 45)),
        )

        val texts = draw(meals = emptyList(), movement = swam)

        assertThat(texts).contains("900 steps")
        assertThat(texts).contains("Swimming · 45 min")
        assertThat(texts).contains("180 kcal more movement than your usual 400")
        assertThat(texts).doesNotContain("Your usual day is 5,200")
    }

    @Test
    fun `with no steps to read the day screen says nothing about walking`() {
        val texts = draw(meals = emptyList())

        assertThat(texts.any { it.contains("steps") }).isFalse()
    }

    /**
     * What this answers: the sentence is long and not needed. The mark says the same
     * thing in the width of a word, and carries the count that was asked for.
     */
    @Test
    fun `a kept window is a mark and a number, not a sentence`() {
        val texts = draw(
            meals = emptyList(),
            windowOpenNow = true,
            verdict = fixed(),
            windowKept = 10,
            windowJudged = 14,
        )

        assertThat(texts).contains("10/14")
        assertThat(texts.any { it.contains("Inside your") }).isFalse()
    }

    /**
     * A past day whose fixed hours are no longer the rule in force: the tally must survive it.
     *
     * Set the hours, then switch to a ratio, then page back. What the day itself contributes is its
     * days-kept count, and that must survive being looked at under another kind of rule: it is that
     * day's only way into window settings. Gating the ring on today's rule and the measured mark on
     * the shown day's used to leave this day with neither mark and no tally at all.
     *
     * NULL rather than shut, deliberately (design §4, corrected 2026-09-17). A ratio in force now
     * hands the ring no state at all: a stretch is open or shut too, but a ring filled by whether
     * eating is going on right now, drawn beside a day judged by the hours on a clock, is a mark
     * about a different thing from the one the tally beside it was scored by. The view model
     * withholds it, so the row here is the count by itself.
     */
    @Test
    fun `a past fixed day under a ratio today keeps its tally`() {
        val texts = draw(
            meals = emptyList(),
            isToday = false,
            windowOpenNow = null,
            verdict = fixed(),
            windowKept = 10,
            windowJudged = 14,
        )

        assertThat(texts).contains("10/14")
        assertThat(texts.count { it == "10/14" }).isEqualTo(1)
        assertThat(texts.any { it.contains("done by") }).isFalse()
    }

    /**
     * A measured day from before the fixed hours in force now draws no tally at all.
     *
     * That rule's count is about the days it governed, and this day was not one of them. It once
     * drew the count twice here (the mark's and the ring's); then once; and on the owner's phone,
     * 2026-09-18, a tally on a day before its rule began read as a count of something that had not
     * started. The view model now hands such a day no counts, and the screen draws what it is given.
     */
    @Test
    fun `a past measured day under fixed hours today draws no tally`() {
        val texts = draw(
            meals = emptyList(),
            isToday = false,
            windowOpenNow = true,
            verdict = measured(),
        )

        assertThat(texts.any { it.contains(" of ") && it.contains("Kept") }).isFalse()
        assertThat(texts.any { it.contains("/") }).isFalse()
    }

    @Test
    fun `with no window there is no mark at all`() {
        assertThat(draw(meals = emptyList()).any { it.contains("/") }).isFalse()
    }

    /**
     * A day no rule ever governed, while the window is open right now.
     *
     * Reachable, and not rare: the pager goes back five years, so any day before the first rule's
     * `fromEpochDay` has a null verdict while today's fixed window is perfectly open. The ring was
     * gated on `windowOpenNow` alone, which is a fact about NOW, so it drew over a day the app had
     * no verdict for — and with a tally already earned it printed that count beside it, as though
     * the day had been judged. A null verdict means no rule governed the shown day; drawing any
     * mark there was the defect.
     *
     * The tally is non-zero on purpose: at 0/0 both marks are silent for a second reason of their
     * own, so a day with no history could not tell the two gatings apart.
     */
    @Test
    fun `a day before every rule draws no mark, even with the window open now`() {
        val texts = draw(
            meals = emptyList(),
            isToday = false,
            windowOpenNow = true,
            verdict = null,
            windowKept = 10,
            windowJudged = 14,
        )

        assertThat(texts).doesNotContain("10/14")
        assertThat(texts.any { it.contains("/") }).isFalse()
        assertThat(texts.any { it.contains("done by") }).isFalse()
    }

    @Test
    fun `one encouraging line is shown, and can be dismissed`() {
        val texts = draw(meals = emptyList(), encouragement = "Good to see you back.")

        assertThat(texts).contains("Good to see you back.")
        assertThat(texts).contains("Got it")
    }

    /**
     * Today draws the one sentence the view model chose, and nothing else about the open stretch.
     *
     * The stretch is open on today, which on a past day would draw the still-open line. On today
     * the one sentence (D32) has already said the one thing there is to say, so it is withheld.
     * Which sentence to say is the view model's and `WindowWording`'s decision — every situation is
     * a row of `MeasuredWindowSentencesTest` — and the screen draws it verbatim.
     */
    @Test
    fun `today draws the one sentence it is given, and no still-open line`() {
        val now = at(shownDay, 10)
        val texts = draw(
            meals = listOf(aMeal()),
            verdict = measured(
                stretches = listOf(
                    stretch(fromHour = 9, fromMinute = 35, inputs = 1, nowMillis = now),
                ),
                nowMillis = now,
            ),
            ratioNow = "Good morning. If you're keeping your window, last meal by 19:35.",
        )

        assertThat(texts).contains("Good morning. If you're keeping your window, last meal by 19:35.")
        assertThat(texts.any { it.startsWith("Still open") }).isFalse()
    }


    /** D14: the span and what the ratio allows, flatly, with nothing said about him. */
    @Test
    fun `a broken stretch states the span it ran to`() {
        val now = at(shownDay + 1, 12)
        val texts = draw(
            meals = listOf(aMeal()),
            isToday = false,
            verdict = measured(
                stretches = listOf(
                    stretch(
                        fromHour = 9,
                        fromMinute = 30,
                        toHour = 21,
                        toMinute = 10,
                        inputs = 4,
                        nowMillis = now,
                    ),
                ),
                nowMillis = now,
            ),
            windowKept = 10,
            windowJudged = 14,
            ratioTally = "Kept 10 of 14 stretches since 3 Sep",
        )

        assertThat(texts).contains("You ate over 11h 40m; your ratio allows 10h.")
    }

    /** A kept stretch is silent: the mark beside it already says so. */
        @Test
    fun `a kept stretch draws the tally and no sentence at all`() {
        val now = at(shownDay + 1, 12)
        val texts = draw(
            meals = listOf(aMeal()),
            isToday = false,
            verdict = measured(
                stretches = listOf(
                    stretch(fromHour = 9, fromMinute = 30, toHour = 17, inputs = 3, nowMillis = now),
                ),
                nowMillis = now,
            ),
            windowKept = 10,
            windowJudged = 14,
            ratioTally = "Kept 10 of 14 stretches since 3 Sep",
        )

        assertThat(texts).contains("Kept 10 of 14 stretches since 3 Sep")
        assertThat(texts.any { it.contains("You ate over") }).isFalse()
        assertThat(texts.any { it.contains("If you're keeping") }).isFalse()
        assertThat(texts.any { it.contains("Still open") }).isFalse()
    }

    /**
     * A stretch still open on a day already past says so, and says why.
     *
     * New on screen, and the honest consequence of counting hours: last night has no verdict until
     * the fast completes, and the app says that rather than guessing at one.
     */
    @Test
    fun `a stretch still open on a day already past says so`() {
        // Before the closing time, so the "done by" line is withheld by the day being over rather
        // than by the deadline having passed.
        val now = at(shownDay + 1, 4)
        val texts = draw(
            meals = listOf(aMeal()),
            isToday = false,
            verdict = measured(
                stretches = listOf(
                    stretch(fromHour = 20, toHour = 23, inputs = 2, nowMillis = now),
                ),
                nowMillis = now,
            ),
            windowKept = 10,
            windowJudged = 14,
            ratioTally = "Kept 10 of 14 stretches since 3 Sep",
        )

        assertThat(texts).contains("Still open — nothing is judged until you have fasted 14 hours.")
        assertThat(texts.any { it.contains("If you're keeping") }).isFalse()
    }

    /**
     * A day the owner ate on, but only inside a stretch that began the day before.
     *
     * It is not a broken day and not a kept one, and the window says nothing whatever about it. The
     * tally still stands, because that is a fact about the record and that day's only way into
     * window settings.
     */
        @Test
    fun `a day with no stretch of its own says nothing about the window`() {
        val texts = draw(
            meals = listOf(aMeal()),
            isToday = false,
            verdict = measured(stretches = emptyList()),
            windowKept = 10,
            windowJudged = 14,
            ratioTally = "Kept 10 of 14 stretches since 3 Sep",
        )

        assertThat(texts).contains("Kept 10 of 14 stretches since 3 Sep")
        assertThat(texts.any { it.contains("If you're keeping") }).isFalse()
        assertThat(texts.any { it.contains("You ate over") }).isFalse()
        assertThat(texts.any { it.contains("Still open") }).isFalse()
    }


    /**
     * Today says how long the stretch open now has been running, with no stretch of its own to
     * hang it on (design §3.0a).
     *
     * The case the review found, drawn: a ratio whose fast is never reached is ONE stretch that
     * never closes, so today begins nothing, the mark draws nothing, the tally is 0 of 0 — and
     * before this the screen was completely silent for exactly the person failing to keep it.
     *
     * The sentence is NOT hung off the day's verdict, which is why it survives that verdict being
     * empty. It is a fact about this moment rather than about the day on screen.
     */
    @Test
    fun `today says how long the open stretch has been running`() {
        val texts = draw(
            meals = listOf(aMeal()),
            verdict = measured(stretches = emptyList()),
            ratioNow = "You've eaten for 720h 0m — next meal from 00:00 tomorrow if you're keeping your fast.",
        )

        assertThat(texts)
            .contains("You've eaten for 720h 0m — next meal from 00:00 tomorrow if you're keeping your fast.")
        assertThat(texts.any { it.contains("last meal by") }).isFalse()
        assertThat(texts.any { it.contains("You ate over") }).isFalse()
        assertThat(texts.any { it.startsWith("Still open") }).isFalse()
    }

    /**
     * A stretch that BEGAN today and has run past its eating hours says ONE thing.
     *
     * DELIBERATELY CHANGED, 2026-09-17. This drew both the live line and the still-open line, and
     * this test pinned that. Design §3.0a allows one sentence about one open stretch: the live line
     * replaces the closing time rather than joining it, so on today it stands alone and the
     * still-open line is withheld exactly as the closing time is. A duration passes no judgement —
     * nobody reads "you have been eating for 13h 0m" as a verdict — so the second sentence explaining
     * that nothing has been judged is not owed.
     *
     * The still-open line is untouched on a day already over, where the live line is not said at
     * all because it is about now — `a stretch still open on a day already past says so` is that
     * day, and it still expects the sentence.
     */
    @Test
    fun `a stretch of today past its eating hours says how long, and nothing else`() {
        val now = at(shownDay, 23)
        val texts = draw(
            meals = listOf(aMeal()),
            verdict = measured(
                stretches = listOf(stretch(fromHour = 9, toHour = 22, inputs = 4, nowMillis = now)),
                nowMillis = now,
            ),
            ratioNow = "You've eaten for 13h 0m — next meal from 12:00 tomorrow if you're keeping your fast.",
        )

        assertThat(texts)
            .contains("You've eaten for 13h 0m — next meal from 12:00 tomorrow if you're keeping your fast.")
        assertThat(texts.any { it.startsWith("Still open") }).isFalse()
        assertThat(texts.any { it.contains("last meal by") }).isFalse()
    }

    /**
     * The same admission as ever, now from the stretch walk's count — **and it says WHERE**.
     *
     * The sentence used to end "Tap its time to set it", which was true while the day drew one row
     * per logging with its time on it. D51 took that away: a part of the clock holds several
     * loggings with several times, so there is no single time on the day to tap. The control moves
     * to the record screen (D50), and the one instruction on this page has to point at it rather
     * than at a row that no longer exists. Asserted, not left to the wording's own test, because
     * this is the screen the sentence is read on.
     */
    @Test
    fun `a meal with no trustworthy hour is admitted to, and says where to fix it`() {
        val now = at(shownDay, 12)
        val texts = draw(
            meals = listOf(aMeal()),
            verdict = measured(
                stretches = listOf(stretch(fromHour = 9, toHour = 11, inputs = 2, nowMillis = now)),
                nowMillis = now,
                untimed = 1,
            ),
        )

        assertThat(texts.any { it.contains("written down on another day") }).isTrue()
        assertThat(texts.any { it.contains("Open the day's record to set it.") }).isTrue()
        assertThat(texts.none { it.contains("Tap its time") }).isTrue()
    }

    /** Nothing before the day's first input: no line, and no ring either. */
        @Test
    fun `with nothing to say and nothing counted, the measured window draws nothing`() {
        val texts = draw(meals = emptyList(), verdict = measured())

        assertThat(texts.any { it.contains("If you're keeping") }).isFalse()
        assertThat(texts.any { it.contains("Kept ") }).isFalse()
        assertThat(texts.any { it.contains("/") }).isFalse()
    }

    /**
     * The tally is drawn on today before anything is eaten, too.
     *
     * DELIBERATELY CHANGED by D32. The mark used to draw nothing at all on today before the first
     * input, tally included, because the only thing it had to say was a closing time. The owner
     * asked for a marker of how many stretches were kept since the ratio was set, and that is a fact
     * about the record on any day at any hour. This test pinned the opposite until then.
     */
    @Test
    fun `a tally already earned is drawn on today before the first input too`() {
        val texts = draw(
            meals = emptyList(),
            verdict = measured(),
            windowKept = 10,
            windowJudged = 14,
            ratioTally = "Kept 10 of 14 stretches since 3 Sep",
        )

        assertThat(texts).contains("Kept 10 of 14 stretches since 3 Sep")
    }

    /**
     * The mirror of the test above: a day that is OVER keeps its tally.
     *
     * Today's silence is about today. A past day has been measured, so the days-kept count stands
     * exactly as it does on a past fixed-governed day — and it is also the only tap target into
     * window settings that day has. There is no closing time left to state, so that line is gone.
     */
        @Test
    fun `a past measured day still shows the tally, and no sentence about now`() {
        val texts = draw(
            meals = emptyList(),
            isToday = false,
            verdict = measured(),
            windowKept = 10,
            windowJudged = 14,
            ratioTally = "Kept 10 of 14 stretches since 3 Sep",
        )

        assertThat(texts).contains("Kept 10 of 14 stretches since 3 Sep")
        assertThat(texts.any { it.contains("If you're keeping") }).isFalse()
    }

    /** The same count, in the same shape, whichever kind of window produced it. */
        @Test
    fun `the tally is shown in words for a measured window`() {
        val now = at(shownDay, 20)
        val texts = draw(
            meals = listOf(aMeal()),
            verdict = measured(
                stretches = listOf(stretch(fromHour = 9, toHour = 16, inputs = 2, nowMillis = now)),
                nowMillis = now,
            ),
            windowKept = 10,
            windowJudged = 14,
            ratioTally = "Kept 10 of 14 stretches since 3 Sep",
        )

        assertThat(texts).contains("Kept 10 of 14 stretches since 3 Sep")
        assertThat(texts).doesNotContain("10/14")
    }

    // --- Choosing rows, and naming what they become (design §3.5) ----------------------------------

    /**
     * **The day never offers the meal-making action, and never draws a tick**, chosen or not.
     *
     * Rewritten rather than left where it was. It used to say "with nothing chosen", which was a
     * statement about a condition; there is no condition here any more, because rows are chosen
     * where rows are shown and that is the record screen (D50, D51). The conditional form is owed
     * there. What is asserted here is the day's own contract — drawn with rows ticked, so it is
     * the state that would have produced the action, and the day still says nothing about it.
     */
    @Test
    fun `the day offers no meal to make and draws no tick, even with rows chosen`() {
        val texts = draw(
            listOf(
                aMeal(
                    items = listOf(
                        anItem(id = 1, name = "Eggs"),
                        anItem(id = 2, name = "Tomato"),
                    ),
                ),
            ),
            chosen = setOf(1L, 2L),
        )

        // "Make a meal from", not "…these": the one-form reads "this one", and an absence test
        // narrower than every form the action can take would pass with the action drawn.
        assertThat(texts.none { it.startsWith("Make a meal from") }).isTrue()
        assertThat(texts).doesNotContain("Chosen")
        assertThat(texts).doesNotContain("Not chosen")
    }

    // --- "2 portion", on the sheet that names a meal (D37) --------------------------------------

    /** The sheet draws the same row's numbers, and must not say something the day does not. */
    @Test
    fun `the naming sheet reads portions the same way`() {
        val texts = render.texts {
            MealNameSheet(
                items = listOf(portions(2.0)),
                isToday = true,
                name = "Stew dinner",
                refusal = null,
                onNameChange = {},
                onConfirm = {},
                onCancel = {},
            )
        }

        assertThat(texts.any { it.endsWith("· 2 portions") }).isTrue()
        assertThat(texts.none { it.endsWith("· 2 portion") }).isTrue()
    }

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

    /**
     * A described meal can have many parts, and the sheet's button once sat below all of them: on a
     * phone, a long list pushed "Make the meal" off the screen with no way to scroll to it. The list
     * now scrolls between the name and the buttons, which stay in view. A short box stands in for the
     * phone's height; only relative geometry is asserted (`CLAUDE.md`).
     */
    @Test
    @Config(qualifiers = "+h640dp")
    fun `a long list of parts scrolls, and the naming sheet's button stays in view`() {
        render.texts {
            Box(Modifier.height(SHORT_SHEET_DP.dp)) {
                MealNameSheet(
                    items = (1..20).map { n ->
                        anItem(id = n.toLong(), name = "Part $n", portion = "50 g", portionAmount = 50.0, portionUnit = "g", kcal = 20)
                    },
                    isToday = true,
                    name = "Counter salad",
                    refusal = null,
                    onNameChange = {},
                    onConfirm = {},
                    onCancel = {},
                )
            }
        }

        // Placed below the title — a node pushed out of the window reports no bounds, read as 0, so
        // "above the sheet's foot" alone would pass for a button that is not there at all.
        val title = render.topDp("Name this meal")
        assertThat(render.topDp("Make the meal")).isIn(com.google.common.collect.Range.open(title, SHORT_SHEET_DP))
        assertThat(render.topDp("Not now")).isIn(com.google.common.collect.Range.open(title, SHORT_SHEET_DP))
    }

    /**
     * The sheet's contents, drawn on their own.
     *
     * The bottom sheet itself puts its content in a window of its own, which this renderer — which
     * walks the activity's view tree — cannot see. The words are all in the content, so that is what
     * is asserted; the sheet around it is chrome, and is checked by hand on the phone with the rest
     * of the navigation.
     *
     * Nothing is typed here and nothing can be: the amounts are what was logged.
     */
    @Test
    fun `the naming sheet lists what is going in, with the amounts logged`() {
        val texts = render.texts {
            MealNameSheet(
                items = listOf(
                    anItem(
                        name = "Eggs",
                        portion = "2 eggs",
                        portionAmount = 2.0,
                        portionUnit = "egg",
                        kcal = 155,
                    ),
                    anItem(
                        name = "Tomato",
                        portion = "150 g",
                        portionAmount = 150.0,
                        portionUnit = "g",
                        kcal = 27,
                    ),
                ),
                isToday = true,
                name = "Shakshuka breakfast",
                refusal = null,
                onNameChange = {},
                onConfirm = {},
                onCancel = {},
            )
        }

        assertThat(texts).contains("Eggs")
        assertThat(texts).contains("Tomato")
        assertThat(texts.any { it.contains("2 eggs") }).isTrue()
        assertThat(texts.any { it.contains("150 g") }).isTrue()
        // And what the whole of it comes to, summed from the rows themselves.
        assertThat(texts.any { it.contains("182 kcal") }).isTrue()
    }

    /**
     * The one thing about this act that could surprise him: the day's four rows become one row. It is
     * said before it happens, together with the fact that no number he logged changes.
     */
    @Test
    fun `the sheet says the day will show these rows under the name he is giving them`() {
        val texts = render.texts {
            MealNameSheet(
                items = listOf(anItem(name = "Eggs", kcal = 155)),
                isToday = true,
                name = "Shakshuka breakfast",
                refusal = null,
                onNameChange = {},
                onConfirm = {},
                onCancel = {},
            )
        }

        assertThat(
            texts.any { it.contains("one row") && it.contains("Shakshuka breakfast") },
        ).isTrue()
        assertThat(texts.any { it.contains("Today will show these") }).isTrue()
    }

    /**
     * The same sentence, about the right day.
     *
     * Choosing is reachable on any day reached by swiping back, and telling a day in the past that
     * it is today would be saying something untrue immediately before the one irreversible act in
     * this feature.
     */
    @Test
    fun `on a past day the sheet says that day rather than today`() {
        val texts = render.texts {
            MealNameSheet(
                items = listOf(anItem(name = "Eggs", kcal = 155)),
                isToday = false,
                name = "Shakshuka breakfast",
                refusal = null,
                onNameChange = {},
                onConfirm = {},
                onCancel = {},
            )
        }

        // The whole sentence, name and all: the named past sentence had never been asserted with
        // its name in it, which is how a sentence with a hole in it could pass.
        assertThat(texts).contains(
            "That day will show these as one row called Shakshuka breakfast. Not one number you " +
                "logged changes, and you can open the row to see the parts.",
        )
        assertThat(texts.none { it.contains("Today will show these") }).isTrue()
    }

    // --- Before a name is typed (D37) -------------------------------------------------------------

    /**
     * The sheet opens with nothing in the field, and the named sentence then read "…as one row
     * called ." The fix is a sentence that needs no name, not a placeholder in one that does: "under
     * the name you give it" is true before anything is typed and says the one thing the named
     * sentence would — that a name is still to come.
     */
    @Test
    fun `before a name is typed, the sheet says what will happen without one — today`() {
        val texts = nameSheet(name = "", isToday = true)

        assertThat(texts).contains(UNNAMED_TODAY)
        assertThat(texts.none { it.contains("called .") || it.contains("called  ") }).isTrue()
    }

    @Test
    fun `before a name is typed, the sheet says it about the right day — a past day`() {
        val texts = nameSheet(name = "", isToday = false)

        assertThat(texts).contains(UNNAMED_PAST)
        assertThat(texts.none { it.contains("Today will show these") }).isTrue()
        assertThat(texts.none { it.contains("called .") || it.contains("called  ") }).isTrue()
    }

    /**
     * Spaces are not a name: the same test that keeps "Make the meal" from being pressed. Treating
     * them as one here would draw "called    ." while the button beneath says there is no name.
     */
    @Test
    fun `a name of only spaces is no name`() {
        val texts = nameSheet(name = "   ", isToday = true)

        assertThat(texts).contains(UNNAMED_TODAY)
        assertThat(texts.none { it.contains("called ") }).isTrue()
    }

    /**
     * A space typed before the name is not part of the name, and the sentence around it must not
     * show one: "called  Shakshuka" is the same hole in the sentence, only narrower. The field keeps
     * exactly what he typed; only the sentence reads it trimmed.
     */
    @Test
    fun `a name typed after a space draws no double space in the sentence`() {
        val texts = nameSheet(name = " Shakshuka breakfast", isToday = true)

        assertThat(texts).contains(
            "Today will show these as one row called Shakshuka breakfast. Not one number you " +
                "logged changes, and you can open the row to see the parts.",
        )
        assertThat(texts.none { it.contains("  ") }).isTrue()
    }

    private fun nameSheet(name: String, isToday: Boolean): List<String> = render.texts {
        MealNameSheet(
            items = listOf(anItem(name = "Eggs", kcal = 155)),
            isToday = isToday,
            name = name,
            refusal = null,
            onNameChange = {},
            onConfirm = {},
            onCancel = {},
        )
    }

    /**
     * A refusal reaches him where he is, which after confirming a name is the sheet.
     *
     * The sheet used to close on the tap and throw the name away, so a refused name had to be typed
     * again from nothing. Now the refusal is drawn in the sheet with what he typed still in the
     * field, which is what makes correcting it one edit rather than a fresh start.
     *
     * **What this test cannot reach**: the sheet stays open because [DayScreenContent] closes it
     * only when choosing ends, and that decision lives inside a `ModalBottomSheet` — a window of its
     * own that this renderer, which walks the activity's view tree, never sees. What is asserted is
     * the part that can be: given a refusal, the sheet's own content says it and keeps the name.
     */
    @Test
    fun `a refused name is said in the sheet, with what he typed still in the field`() {
        val texts = render.texts {
            MealNameSheet(
                items = listOf(anItem(name = "Eggs", kcal = 155)),
                isToday = true,
                name = "Vegetable salad",
                refusal = "You already have a meal called “Vegetable salad”.",
                onNameChange = {},
                onConfirm = {},
                onCancel = {},
            )
        }

        assertThat(texts.any { it.contains("You already have a meal called") }).isTrue()
        // Exactly the name and nothing else is the text field's own contents: every other line that
        // mentions the name is a sentence around it.
        assertThat(texts).contains("Vegetable salad")
    }

    /** Nothing has gone wrong yet, so nothing is said about anything going wrong. */
    @Test
    fun `with no refusal the sheet says nothing about one`() {
        val texts = render.texts {
            MealNameSheet(
                items = listOf(anItem(name = "Eggs", kcal = 155)),
                isToday = true,
                name = "Vegetable salad",
                refusal = null,
                onNameChange = {},
                onConfirm = {},
                onCancel = {},
            )
        }

        assertThat(texts.none { it.contains("You already have a meal called") }).isTrue()
    }

    /** A refusal names the row that stands in the way, so it is a next step and not a dead end. */
    @Test
    fun `a refusal is drawn with the row's own name in it`() {
        val texts = draw(
            listOf(
                aMeal(
                    items = listOf(
                        anItem(id = 1, name = "Eggs"),
                        anItem(id = 2, name = "Coffee with milk"),
                    ),
                ),
            ),
            chosen = setOf(1L, 2L),
            refusal = "Coffee with milk is not attached to a food yet.",
        )

        assertThat(texts.any { it.contains("Coffee with milk is not attached to a food yet.") })
            .isTrue()
    }

    private fun fixed(
        inside: Int = 0,
        outside: Int = 0,
        untimed: Int = 0,
    ): DayVerdict = DayWindow(
        window = EatingWindow(startHour = 6, endHour = 20, fromEpochDay = 20_699L),
        mealsInside = inside,
        mealsOutside = outside,
        mealsUntimed = untimed,
    )

    /** Fourteen hours fasting, ten eating — the ratio is written fasting first. */
    private val fourteenTen = MeasuredWindow(fastingHours = 14)

    /**
     * The zone the moments below are built in, and the one the verdict carries to the screen.
     *
     * The screen no longer reaches for the device's zone at the point of printing — it draws the
     * one on the verdict — so this could be any zone at all. It stays the machine's because the
     * rest of the file builds its days from `LocalDate` in the default zone, and one zone through
     * the whole file is one fewer thing to hold in the head.
     */
    private val zone: ZoneId = ZoneId.systemDefault()

    /**
     * A past fixed-hours day, paged back to while a RATIO governs today, draws the ratio's tally in
     * words — never as a bare fraction beside that day's ring.
     *
     * Found by review, 2026-09-18. The count is in stretches, and printed "10/14" beside a day ring
     * it read as days: the very kind of bare fraction D32 replaced.
     */
    @Test
    fun `a past fixed day under a ratio today draws the ratio's tally in words`() {
        val texts = draw(
            meals = emptyList(),
            isToday = false,
            verdict = fixed(),
            windowKept = 10,
            windowJudged = 14,
            ratioTally = "Kept 10 of 14 stretches since 3 Sep",
        )

        assertThat(texts).contains("Kept 10 of 14 stretches since 3 Sep")
        assertThat(texts).doesNotContain("10/14")
    }

    private val shownDay = 20_699L

    private fun at(epochDay: Long, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(LocalDate.ofEpochDay(epochDay), LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    /**
     * One stretch and what [nowMillis] makes of it, built through the domain's own verdict.
     *
     * `EatingStretch.at` is internal and the unit tests are the friend path CLAUDE.md describes, so
     * these are verdicts the app can really produce rather than hand-filled structures.
     */
    private fun stretch(
        fromHour: Int,
        fromMinute: Int = 0,
        toHour: Int = fromHour,
        toMinute: Int = fromMinute,
        onDay: Long = shownDay,
        endsOnDay: Long = onDay,
        inputs: Int = 2,
        nowMillis: Long,
    ): StretchVerdict = EatingStretch(
        startedAtMillis = at(onDay, fromHour, fromMinute),
        lastAtMillis = at(endsOnDay, toHour, toMinute),
        inputs = inputs,
        startedOnEpochDay = onDay,
    ).at(fourteenTen, nowMillis)

    /** A measured day: the stretches that BEGAN on it, and the meals that could not be timed. */
    private fun measured(
        stretches: List<StretchVerdict> = emptyList(),
        nowMillis: Long = at(shownDay, 12),
        untimed: Int = 0,
    ): DayVerdict = DayMeasured(
        window = fourteenTen,
        stretches = stretches,
        nowMillis = nowMillis,
        // The verdict carries the zone it was worked out in, and the screen draws its clock times
        // in that one rather than reading the machine's again — so what is asserted below is what
        // the screen prints, on any machine.
        zone = zone,
        mealsUntimed = untimed,
    )

    // --- issue #13 (D45): the line saying a food's own figures changed ---------------------------

    /** The sentence the wording produces for the owner's cucumber, as the day would draw it. */
    private val foodChanged =
        "“Cucumber” now counts 18 kcal per 100 g, where it counted 15, " +
            "because you have just logged it with different numbers."

    private val targetChanged =
        "Your daily target has changed from 2,090 to 2,050 kcal, because your weight trend is " +
            "now 79.2 kg."

    /**
     * It is drawn, and it waits: a notice that faded while the phone was in a pocket would have
     * announced nothing, so it goes only when he says he has read it.
     */
    @Test
    fun `the line about a food's figures is drawn and waits for its own Got it`() {
        var dismissed = false
        val texts = draw(
            emptyList(),
            foodNotice = foodChanged,
            onDismissFoodRetaught = { dismissed = true },
        )

        assertThat(texts).contains(foodChanged)
        assertThat(texts).contains("Got it")

        render.click("Got it")
        assertThat(dismissed).isTrue()
    }

    /** Two different things changed, so both are said, and each is dismissed on its own. */
    @Test
    fun `the target's notice and the food's are drawn together, each with its own button`() {
        val texts = draw(emptyList(), notice = targetChanged, foodNotice = foodChanged)

        assertThat(texts).contains(targetChanged)
        assertThat(texts).contains(foodChanged)
        assertThat(texts.filter { it == "Got it" }).hasSize(2)
    }

    /**
     * A log that failed lands here, where he returns to after logging: in the refusal's slot, with
     * the same way to take it down.
     */
    @Test
    fun `an action that failed says so where a refusal would, and can be put away`() {
        var dismissed = false
        val texts = draw(
            emptyList(),
            failed = ActionRefused.MAYBE_PARTIAL,
            onDismissRefusal = { dismissed = true },
        )

        assertThat(texts).contains(
            "That didn't finish, and may have only partly happened. " +
            "What went wrong is under Settings → Recent problems.",
        )
        render.click("All right")
        assertThat(dismissed).isTrue()
    }

    private fun draw(
        meals: List<Meal>,
        isToday: Boolean = true,
        notice: String? = null,
        foodNotice: String? = null,
        onDismissFoodRetaught: () -> Unit = {},
        streak: Streak = Streak(0, 0, 0),
        loggedToday: Boolean = false,
        weeklyCongratulationToday: Boolean = false,
        movement: MovementToday? = null,
        windowOpenNow: Boolean? = null,
        verdict: DayVerdict? = null,
        windowKept: Int = 0,
        windowJudged: Int = 0,
        ratioNow: String? = null,
        ratioTally: String? = null,
        encouragement: String? = null,
        chosen: Set<Long> = emptySet(),
        refusal: String? = null,
        failed: ActionRefused? = null,
        onDismissRefusal: () -> Unit = {},
        onOpenPart: (DayPart) -> Unit = {},
        onAdd: () -> Unit = {},
        onDescribe: () -> Unit = {},
        onScan: () -> Unit = {},
    ): List<String> = render.texts {
        DayScreenContent(
            state = DayUiState.Ready(
                epochDay = 20_699L,
                isToday = isToday,
                target = target,
                meals = meals,
                remaining = Remaining.of(target, DayTotals.of(meals)),
                targetChangeNotice = notice,
                foodRetaughtNotice = foodNotice,
                streak = streak,
                loggedToday = loggedToday,
                weeklyCongratulationToday = weeklyCongratulationToday,
                movement = movement,
                windowOpenNow = windowOpenNow,
                verdict = verdict,
                windowKept = windowKept,
                windowJudged = windowJudged,
                ratioNow = ratioNow,
                ratioTally = ratioTally,
                encouragement = encouragement,
                chosen = chosen,
                refusal = refusal,
                failed = failed,
            ),
            onAdd = onAdd,
            onDescribe = onDescribe,
            onScan = onScan,
            onOpenSettings = {},
            onDismissTargetChange = {},
            onDismissJustLogged = {},
            onDismissEncouragement = {},
            onDismissRefusal = onDismissRefusal,
            onDismissFoodRetaught = onDismissFoodRetaught,
            onOpenPart = onOpenPart,
        )
    }
}
