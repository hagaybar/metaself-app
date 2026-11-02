package com.metaself.app.data.trainer

import com.metaself.app.data.day.InMemoryMealRepository
import com.metaself.app.data.day.MealRepository
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.letter.FoodTotals
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.weight.InMemoryWeightRepository
import com.metaself.app.data.weight.WeightRepository
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.trainer.CoachModel
import com.metaself.app.domain.trainer.CoachRequest
import com.metaself.app.domain.trainer.CoachTurn
import com.metaself.app.domain.trainer.DrawerAsk
import com.metaself.app.domain.trainer.RememberedFact
import com.metaself.app.domain.trainer.TrainerReply
import java.time.LocalDate

/** [CoachModel] answering from a list, in order, and keeping what it was asked. Every word is the caller's invention. */
class FakeCoachModel : CoachModel {
    val looks = mutableListOf<TrainerReply<CoachTurn>>()
    val asked = mutableListOf<CoachRequest>()
    val instructions = mutableListOf<String?>()

    /** The next look answers in [text]. */
    fun says(text: String, remember: List<RememberedFact> = emptyList(), model: String = "a-model") {
        looks += TrainerReply.Answered(CoachTurn(emptyList(), text, remember), model)
    }

    /** The next look opens [asks], with no words. */
    fun opens(vararg asks: DrawerAsk) {
        looks += TrainerReply.Answered(CoachTurn(asks.toList(), "", emptyList()), "a-model")
    }

    override suspend fun look(request: CoachRequest, instructions: String?): TrainerReply<CoachTurn> {
        asked += request
        this.instructions += instructions
        return looks.removeAt(0)
    }
}

/** The real loop and the real file, over in-memory stores, each replaceable. Today is [TEST_EPOCH_DAY]. */
fun askTheCoach(
    model: CoachModel,
    now: Now,
    record: MovementRecord = FakeMovementRecord(),
    store: TrainerStore = FakeTrainerStore(),
    programmes: ProgrammeStore = FakeProgrammeStore(),
    weights: WeightRepository = InMemoryWeightRepository(),
    profiles: ProfileRepository = FakeProfileRepository(aProfile()),
    sheets: SheetStore = InMemorySheetStore(),
    weekNotes: WeekNoteStore = InMemoryWeekNoteStore(),
    meals: MealRepository = InMemoryMealRepository(),
    food: FoodTotals = FoodTotals { _, _ -> emptyMap() },
    aboutMe: AboutMeStore = InMemoryAboutMeStore(),
): AskTheCoach {
    val today = Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }
    val year = CurrentYear { TEST_YEAR }
    val ask = AskTheTrainer(record, store, weights, profiles, FakeTrainer(), aboutMe, programmes, today, now, year, sheets = sheets)
    return AskTheCoach(CoachFile(record, store, weights, profiles, ask, aboutMe, sheets, weekNotes, meals, food, today, now, year), model)
}
