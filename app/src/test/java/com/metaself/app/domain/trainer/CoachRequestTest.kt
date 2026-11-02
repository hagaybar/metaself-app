package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

/** D161: the coach's request has its own fields, and the Read line comes only from what the phone read. */
class CoachRequestTest {

    /** Adding a field here is deciding to send something new: the disclosure has to say so too (D163). */
    @Test
    fun `a coach request holds exactly these fields`() {
        assertThat(fieldsOf(CoachRequest::class.java)).containsExactly("replyTo", "conversation", "frontPage", "opened", "look", "foodCheck")
        assertThat(fieldsOf(FrontPage::class.java)).containsExactly(
            "today", "goal", "plan", "thisWeek", "weekNote", "aboutMe", "sheet", "body", "floorKcal", "targetKcal", "recordBegins",
        )
        assertThat(fieldsOf(OpenedDrawer::class.java)).containsExactly("ask", "answer", "read")
    }

    @Test
    fun `a request answers the owner's message, within the looks a reply may take`() {
        val theirs = ConversationLine(LocalDateTime.of(2026, 9, 3, 8, 0), false, LineKind.MESSAGE, "Invented.")
        val mine = theirs.copy(fromOwner = true)
        val page = FrontPage(1, null, null, Rhythm(0, 0), null, null, null, null, null, null, RecordBegins(null, null, null, null))

        assertThrows<IllegalArgumentException> { CoachRequest(theirs, emptyList(), page, emptyList(), 1) }
        assertThrows<IllegalArgumentException> { CoachRequest(mine, emptyList(), page, emptyList(), CoachLooks.MAX + 1) }
        assertThat(CoachRequest(mine, emptyList(), page, emptyList(), CoachLooks.MAX - 1).lastLook).isFalse()
        assertThat(CoachRequest(mine, emptyList(), page, emptyList(), CoachLooks.MAX).lastLook).isTrue()
    }

    @Test
    fun `food words are recognised, and advice with none of them is not`() {
        listOf("Your intake was low", "below 2000 kcal", "You have been under-eating", "You ate little", "low on PROTEIN").forEach {
            assertThat(FoodTalk.states(it)).isTrue()
        }
        listOf("Keep the walk easy", "Rest your knee", "A later start", "A pelvic floor exercise").forEach {
            assertThat(FoodTalk.states(it)).isFalse()
        }
    }

    @Test
    fun `the Read line names each drawer once, with the days it covered in all, in the order first read`() {
        val line = ReadLine.of(
            listOf(
                DrawerRead(Drawer.DAYS, (1L..7L).toSet()),
                DrawerRead(Drawer.MEALS, setOf(5L)),
                DrawerRead(Drawer.HABITS),
                DrawerRead(Drawer.MEALS, setOf(5L, 6L, 7L)),
                DrawerRead(Drawer.CONVERSATIONS),
                DrawerRead(Drawer.PLAN),
            ),
        )

        assertThat(line).isEqualTo("sleep and recovery, 7 days · meals, 3 days · eating habits · past conversations · your plan")
        assertThat(ReadLine.of(listOf(DrawerRead(Drawer.WEIGHT, setOf(1L))))).isEqualTo("weight, 1 day")
        assertThat(ReadLine.of(emptyList())).isEmpty()
    }

    private fun fieldsOf(type: Class<*>): List<String> =
        type.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }
}
