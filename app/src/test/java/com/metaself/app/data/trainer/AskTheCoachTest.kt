package com.metaself.app.data.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.day.InMemoryMealRepository
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.letter.FoodTotals
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.time.Now
import com.metaself.app.data.weight.InMemoryWeightRepository
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.target.CurrentTarget
import com.metaself.app.domain.trainer.CoachAnswer
import com.metaself.app.domain.trainer.CoachLooks
import com.metaself.app.domain.trainer.CoachTurn
import com.metaself.app.domain.trainer.ConversationLine
import com.metaself.app.domain.trainer.Drawer
import com.metaself.app.domain.trainer.DrawerAsk
import com.metaself.app.domain.trainer.FactGroup
import com.metaself.app.domain.trainer.FactSource
import com.metaself.app.domain.trainer.LineKind
import com.metaself.app.domain.trainer.ReadLine
import com.metaself.app.domain.trainer.RememberedFact
import com.metaself.app.domain.trainer.SheetFact
import com.metaself.app.domain.trainer.SheetMadeBy
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.weight.WeightReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** D161: one reply, up to three looks, over the real file on in-memory stores. Every word and figure is invented. */
class AskTheCoachTest {

    private val model = FakeCoachModel()
    private val record = FakeMovementRecord()
    private val sheets = InMemorySheetStore()
    private val meals = InMemoryMealRepository()
    private val line = ConversationLine(LocalDateTime.of(2026, 9, 3, 15, 0), true, LineKind.MESSAGE, "Invented question.")
    private val today = TEST_EPOCH_DAY

    private fun date(day: Long) = LocalDate.ofEpochDay(day).toString()

    private suspend fun answer(coach: AskTheCoach = coach()) = coach.answer(line, listOf(line), emptyList(), exceptMessage = null)

    private fun coach() = askTheCoach(model, Now { NOW }, record = record, sheets = sheets, meals = meals)

    @Test
    fun `an answer on the first look reads nothing and asks once`() = runTest {
        model.says("Invented reply.")

        val answer = answer() as CoachAnswer.Answered

        assertThat(answer.text).isEqualTo("Invented reply.")
        assertThat(answer.opened).isEmpty()
        assertThat(model.asked).hasSize(1)
        assertThat(model.asked.single().look).isEqualTo(1)
        assertThat(model.asked.single().opened).isEmpty()
    }

    @Test
    fun `a drawer named is fetched and sent with the next request, and what was read is what is reported`() = runTest {
        record.earliest.value = today - 100
        record.days.value = listOf(HealthDay(today - 1, sleepMinutes = 420))
        model.opens(DrawerAsk("days", date(today - 6), date(today), ""))
        model.says("Invented reply.")

        val answer = answer() as CoachAnswer.Answered

        assertThat(model.asked.map { it.look }).containsExactly(1, 2).inOrder()
        assertThat(model.asked.last().opened.single().answer).contains("\"sleep_minutes\":420")
        assertThat(ReadLine.of(answer.opened)).isEqualTo("sleep and recovery, 7 days")
    }

    @Test
    fun `at most three requests, and the third's opening is ignored`() = runTest {
        model.opens(DrawerAsk("habits", "", "", ""))
        model.opens(DrawerAsk("plan", "", "", ""))
        model.looks += TrainerReply.Answered(CoachTurn(listOf(DrawerAsk("weight", date(today - 1), date(today), "")), "Invented final.", emptyList()), "a-model")

        val answer = answer() as CoachAnswer.Answered

        assertThat(model.asked).hasSize(CoachLooks.MAX)
        assertThat(model.asked.last().lastLook).isTrue()
        assertThat(answer.text).isEqualTo("Invented final.")
        assertThat(answer.opened.map { it.drawer }).containsExactly(Drawer.HABITS, Drawer.PLAN).inOrder()
    }

    @Test
    fun `a third reply with no words is a failure`() = runTest {
        model.opens(DrawerAsk("habits", "", "", ""))
        model.opens(DrawerAsk("plan", "", "", ""))
        model.opens(DrawerAsk("weight", date(today - 1), date(today), ""))

        val failed = answer() as CoachAnswer.Failed

        assertThat(failed.failure).isInstanceOf(EstimateResult.Unreadable::class.java)
        assertThat(model.asked).hasSize(CoachLooks.MAX)
    }

    @Test
    fun `a first reply with neither words nor drawers is a failure, and asks no more`() = runTest {
        model.says("   ")

        assertThat(answer()).isInstanceOf(CoachAnswer.Failed::class.java)
        assertThat(model.asked).hasSize(1)
    }

    @Test
    fun `a failed look fails the reply`() = runTest {
        model.opens(DrawerAsk("habits", "", "", ""))
        model.looks += TrainerReply.Failed(EstimateResult.CeilingReached)

        val failed = answer() as CoachAnswer.Failed

        assertThat(failed.failure).isEqualTo(EstimateResult.CeilingReached)
    }

    /** It has not finished reading: its words are not yet its answer. */
    @Test
    fun `a look with both words and a drawer opens, on any look but the last`() = runTest {
        model.looks += TrainerReply.Answered(CoachTurn(listOf(DrawerAsk("habits", "", "", "")), "Invented early.", emptyList()), "a-model")
        model.says("Invented final.")

        assertThat((answer() as CoachAnswer.Answered).text).isEqualTo("Invented final.")
    }

    @Test
    fun `asking again for what is already open fetches nothing again`() = runTest {
        model.opens(DrawerAsk("habits", "", "", ""))
        model.opens(DrawerAsk("habits", "", "", ""))
        model.says("Invented final.")

        answer()

        assertThat(model.asked.last().opened).hasSize(1)
    }

    @Test
    fun `an unknown drawer and bad dates are told to the model, are not read, and the reply goes on`() = runTest {
        model.opens(DrawerAsk("astrology", "", "", ""), DrawerAsk("days", "soon", "later", ""))
        model.says("Invented reply.")

        val answer = answer() as CoachAnswer.Answered

        val told = model.asked.last().opened.map { it.answer }
        assertThat(told[0]).contains("no drawer called")
        assertThat(told[1]).contains("not a date")
        assertThat(answer.opened).isEmpty()
    }

    @Test
    fun `a drawer that cannot be read is told to the model, and the reply goes on`() = runTest {
        record.earliest.value = today - 3
        val failing = object : MovementRecord by record {
            override fun observeDays(from: Long, to: Long): Flow<List<HealthDay>> = flow { error("invented read failure") }
        }
        model.opens(DrawerAsk("days", date(today - 1), date(today), ""))
        model.says("Invented reply.")

        val answer = answer(askTheCoach(model, Now { NOW }, record = failing)) as CoachAnswer.Answered

        assertThat(model.asked.last().opened.single().answer).contains("could not be read")
        assertThat(answer.opened).isEmpty()
    }

    @Test
    fun `no more drawers are opened at one look than the cap, and it says so`() = runTest {
        model.opens(
            DrawerAsk("habits", "", "", ""),
            DrawerAsk("plan", "", "", ""),
            DrawerAsk("conversations", "", "", "a"),
            DrawerAsk("conversations", "", "", "b"),
            DrawerAsk("conversations", "", "", "c"),
        )
        model.says("Invented reply.")

        answer()

        assertThat(model.asked.last().opened.last().answer).contains("only 4 drawers")
    }

    /** Refused for the cap, not read: it can be asked for on the next look. */
    @Test
    fun `a drawer refused for the cap can be asked for again`() = runTest {
        val five = DrawerAsk("conversations", "", "", "e")
        model.opens(
            DrawerAsk("conversations", "", "", "a"),
            DrawerAsk("conversations", "", "", "b"),
            DrawerAsk("conversations", "", "", "c"),
            DrawerAsk("conversations", "", "", "d"),
            five,
        )
        model.opens(five)
        model.says("Invented reply.")

        answer()

        assertThat(model.asked.last().opened.last().answer).contains("searched_for")
    }

    @Test
    fun `what to remember is gathered from every look, once`() = runTest {
        val fact = RememberedFact("life", "Invented fact.")
        model.looks += TrainerReply.Answered(CoachTurn(listOf(DrawerAsk("habits", "", "", "")), "", listOf(fact)), "a-model")
        model.says("Invented reply.", remember = listOf(fact, RememberedFact("body", "Another invented fact.")))

        assertThat((answer() as CoachAnswer.Answered).remember).containsExactly(fact, RememberedFact("body", "Another invented fact.")).inOrder()
    }

    @Test
    fun `the front page is the app's own floor and target, the whole sheet, and how far back the record goes`() = runTest {
        val facts = List(40) { SheetFact(it.toLong(), FactGroup.BODY, "Invented fact $it.", today - 1, FactSource.SAID) }
        sheets.add(NOW - HOUR, facts, SheetMadeBy.REWRITE, "a-model")
        record.earliest.value = today - 70
        meals.log(Meal(epochDay = today - 50, loggedAtMillis = (today - 50) * DAY, items = listOf(FoodItem(name = "Invented", kcal = 1, proteinG = 0, carbsG = 0, fatG = 0, source = Source.TYPED))))
        val weights = InMemoryWeightRepository(listOf(WeightReading(today - 20, 80.0)))
        model.says("Invented reply.")

        answer(askTheCoach(model, Now { NOW }, record = record, sheets = sheets, meals = meals, weights = weights, profiles = FakeProfileRepository(aProfile())))

        val page = model.asked.single().frontPage
        val target = CurrentTarget.of(aProfile(), null, TEST_YEAR, 0)
        assertThat(page.floorKcal).isEqualTo(target.floorKcal)
        assertThat(page.targetKcal).isEqualTo(target.kcal)
        assertThat(page.sheet).hasSize(40)
        assertThat(page.recordBegins.movement).isEqualTo(today - 70)
        assertThat(page.recordBegins.food).isEqualTo(today - 50)
        assertThat(page.recordBegins.weight).isEqualTo(today - 20)
        assertThat(page.body!!.heightCm).isEqualTo(aProfile().heightCm)
        assertThat(page.today).isEqualTo(today)
    }

    @Test
    fun `with no profile there is no floor and no target`() = runTest {
        model.says("Invented reply.")

        answer(askTheCoach(model, Now { NOW }, profiles = FakeProfileRepository(null)))

        assertThat(model.asked.single().frontPage.floorKcal).isNull()
        assertThat(model.asked.single().frontPage.targetKcal).isNull()
    }

    @Test
    fun `the food and meals drawers read the record`() = runTest {
        meals.log(Meal(epochDay = today - 1, loggedAtMillis = (today - 1) * DAY + 8 * HOUR, items = listOf(FoodItem(name = "Invented porridge", kcal = 300, proteinG = 10, carbsG = 50, fatG = 5, source = Source.TYPED))))
        val food = FoodTotals { _, _ -> mapOf(today - 1 to DayTotals(300, 10, 50, 5)) }
        model.opens(DrawerAsk("meals", date(today - 1), date(today), ""), DrawerAsk("food_days", date(today - 1), date(today), ""))
        model.says("Invented reply.")

        val answer = answer(askTheCoach(model, Now { NOW }, meals = meals, food = food)) as CoachAnswer.Answered

        assertThat(model.asked.last().opened[0].answer).contains("Invented porridge")
        assertThat(model.asked.last().opened[1].answer).contains("\"kcal\":300")
        assertThat(ReadLine.of(answer.opened)).isEqualTo("meals, 2 days · daily food totals, 2 days")
    }

    @Test
    fun `words about food with no food read send the phone to the record, and the coach answers again from it`() = runTest {
        meals.log(Meal(epochDay = today - 20, loggedAtMillis = (today - 20) * DAY + 8 * HOUR, items = listOf(FoodItem(name = "Invented porridge", kcal = 300, proteinG = 10, carbsG = 50, fatG = 5, source = Source.TYPED))))
        model.says("Your intake has been too low.")
        model.says("Only some of those days are logged, so I cannot say.")

        val answer = answer() as CoachAnswer.Answered

        assertThat(answer.text).isEqualTo("Only some of those days are logged, so I cannot say.")
        assertThat(model.asked.map { it.foodCheck }).containsExactly(false, true).inOrder()
        val check = model.asked.last().opened.single().ask
        assertThat(check).isEqualTo(DrawerAsk("food_days", date(today - 14), date(today - 1), ""))
        assertThat(answer.foodUnchecked).isFalse()
        assertThat(ReadLine.of(answer.opened, answer.foodUnchecked)).isEqualTo("daily food totals, 14 days")
    }

    @Test
    fun `a reply the model marks as about food is checked though it has none of the words`() = runTest {
        model.saysAboutFood("Invented reply.")
        model.says("Another invented reply.")

        answer()

        assertThat(model.asked).hasSize(2)
        assertThat(model.asked.last().foodCheck).isTrue()
    }

    @Test
    fun `words about food after food was read are the answer, with no further request`() = runTest {
        model.opens(DrawerAsk("habits", "", "", ""))
        model.says("Your intake has been too low.")

        val answer = answer() as CoachAnswer.Answered

        assertThat(answer.text).isEqualTo("Your intake has been too low.")
        assertThat(model.asked).hasSize(2)
        assertThat(answer.foodUnchecked).isFalse()
    }

    @Test
    fun `a reply with no food in it takes no more requests`() = runTest {
        model.says("Keep the walk easy.")

        answer()

        assertThat(model.asked).hasSize(1)
    }

    @Test
    fun `the phone checks once, and a second reply that still has no food read says so`() = runTest {
        val food = FoodTotals { _, _ -> error("invented read failure") }
        model.says("Your intake has been too low.")
        model.says("Your intake is still too low.")

        val answer = answer(askTheCoach(model, Now { NOW }, meals = meals, food = food)) as CoachAnswer.Answered

        assertThat(model.asked).hasSize(2)
        assertThat(answer.foodUnchecked).isTrue()
        assertThat(ReadLine.of(answer.opened, answer.foodUnchecked)).isEqualTo("the front page · food not checked")
    }

    @Test
    fun `on the last look nothing more can be read, the reply stands, and the Read line says food was not checked`() = runTest {
        model.opens(DrawerAsk("plan", "", "", ""))
        model.opens(DrawerAsk("days", date(today - 1), date(today - 1), ""))
        model.says("Your intake has been too low.")

        val answer = answer() as CoachAnswer.Answered

        assertThat(model.asked).hasSize(CoachLooks.MAX)
        assertThat(answer.foodUnchecked).isTrue()
        assertThat(ReadLine.of(answer.opened, answer.foodUnchecked)).endsWith("· food not checked")
    }

    @Test
    fun `reading the file changes nothing`() = runTest {
        model.opens(DrawerAsk("habits", "", "", ""))
        model.says("Invented reply.", remember = listOf(RememberedFact("life", "Invented fact.")))

        answer()

        assertThat(sheets.observe().first()).isEmpty()
    }

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
        const val NOW = TEST_EPOCH_DAY * DAY + 15 * HOUR
    }
}
