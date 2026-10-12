package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.letter.LetterTexts
import com.metaself.app.domain.movement.WorkoutKind
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * D146, D147: the thread, in time order, and the window a reply reads — 14 days, the newest 24,000 characters, an
 * item cut by the cap left out whole. Now is 15:00 on [TEST_EPOCH_DAY], UTC. Every word is invented.
 */
class ConversationTest {

    @Test
    fun `the thread interleaves messages, feedback and letters by time`() {
        val letter = ThreadItem.Letter(TEST_EPOCH_DAY - 10, NOW - 3 * HOUR, LETTER)
        val feedback = ThreadItem.AfterSession(4, NOW - 2 * HOUR, WorkoutKind.RUN, 60, NOTE_FEEDBACK)
        val asked = said(1, NOW - HOUR, owner = true)
        val replied = said(2, NOW - HOUR + 1_000, owner = false)
        val older = said(3, NOW - 4 * HOUR, owner = true)

        val thread = Conversation.thread(listOf(asked, replied, older), listOf(feedback), listOf(letter))

        assertThat(thread).containsExactly(ThreadItem.Said(older), letter, feedback, ThreadItem.Said(asked), ThreadItem.Said(replied)).inOrder()
    }

    @Test
    fun `two messages at the same moment keep their order of writing`() {
        val first = said(5, NOW, owner = true)
        val second = said(6, NOW, owner = false)

        assertThat(Conversation.thread(listOf(second, first), emptyList(), emptyList()))
            .containsExactly(ThreadItem.Said(first), ThreadItem.Said(second)).inOrder()
    }

    @Test
    fun `the window holds the last 14 days, oldest first`() {
        val old = ThreadItem.Said(said(1, NOW - 14 * DAY - 1, owner = true))
        val edge = ThreadItem.Said(said(2, NOW - 14 * DAY + 1, owner = true))
        val recent = ThreadItem.Said(said(3, NOW - HOUR, owner = true))

        assertThat(Conversation.window(listOf(old, edge, recent), NOW)).containsExactly(edge, recent).inOrder()
    }

    @Test
    fun `over the cap the oldest go, and an item the cap cuts is left out whole with all before it`() {
        // 10,000 + 10,000 + 5,000 is over 24,000: the oldest long one would be cut, so it goes — and so does the
        // short one before it, which would fit on its own.
        val short = ThreadItem.Said(said(1, NOW - 5 * HOUR, owner = true, text = "a".repeat(100)))
        val cut = ThreadItem.Said(said(2, NOW - 4 * HOUR, owner = false, text = "b".repeat(5_000)))
        val middle = ThreadItem.Said(said(3, NOW - 3 * HOUR, owner = true, text = "c".repeat(10_000)))
        val newest = ThreadItem.Said(said(4, NOW - 2 * HOUR, owner = false, text = "d".repeat(10_000)))

        assertThat(Conversation.window(listOf(short, cut, middle, newest), NOW)).containsExactly(middle, newest).inOrder()
    }

    @Test
    fun `exactly the cap is kept`() {
        val a = ThreadItem.Said(said(1, NOW - 2 * HOUR, owner = true, text = "a".repeat(14_000)))
        val b = ThreadItem.Said(said(2, NOW - HOUR, owner = false, text = "b".repeat(10_000)))

        assertThat(Conversation.window(listOf(a, b), NOW)).containsExactly(a, b).inOrder()
    }

    @Test
    fun `feedback and letters count their words toward the cap`() {
        val letter = ThreadItem.Letter(TEST_EPOCH_DAY - 7, NOW - 2 * HOUR, LETTER)
        val feedback = ThreadItem.AfterSession(4, NOW - HOUR, WorkoutKind.WALK, 30, NOTE_FEEDBACK)

        assertThat(Conversation.text(letter)).isEqualTo(
            listOf("Invented headline.", "Invented recognition.", "Invented paying off.", "Invented look after.", "Invented rests on.", "Invented next week.", "Invented close.")
                .joinToString("\n\n"),
        )
        assertThat(Conversation.text(feedback)).isEqualTo("Invented headline.\n\nInvented note.")
        val fourParts = NOTE_FEEDBACK.copy(note = null, againstPlan = "Invented plan.", numbers = "", nextTime = "Invented next.", thisWeek = "Invented week.")
        assertThat(Conversation.text(feedback.copy(feedback = fourParts)))
            .isEqualTo("Invented headline.\n\nInvented plan.\n\nInvented next.\n\nInvented week.")
        val sixParts = LetterTexts.SixParts("H.", "E.", "P.", "L.", "N.", "C.")
        assertThat(Conversation.text(letter.copy(texts = sixParts))).isEqualTo("H.\n\nE.\n\nP.\n\nL.\n\nN.\n\nC.")
    }

    @Test
    fun `a line carries the time, who said it and what it is about`() {
        val line = Conversation.line(ThreadItem.AfterSession(4, NOW, WorkoutKind.RUN, 60, NOTE_FEEDBACK), ZoneOffset.UTC)

        assertThat(line.at).isEqualTo(LocalDateTime.of(2026, 9, 3, 15, 0))
        assertThat(line.fromOwner).isFalse()
        assertThat(line.kind).isEqualTo(LineKind.SESSION_FEEDBACK)
        assertThat(line.sessionKind).isEqualTo(WorkoutKind.RUN)
        assertThat(line.sessionMinutes).isEqualTo(60)

        val letter = Conversation.line(ThreadItem.Letter(TEST_EPOCH_DAY - 3, NOW, LETTER), ZoneOffset.UTC)
        assertThat(letter.kind).isEqualTo(LineKind.LETTER)
        assertThat(letter.letterWeek).isEqualTo(TEST_EPOCH_DAY - 3)

        val his = Conversation.line(ThreadItem.Said(said(1, NOW, owner = true)), ZoneOffset.UTC)
        assertThat(his.fromOwner).isTrue()
        assertThat(his.kind).isEqualTo(LineKind.MESSAGE)
        assertThat(his.text).isEqualTo("Invented words.")
    }

    @Test
    fun `a message is cut to two thousand characters, never through an emoji`() {
        assertThat(Conversation.cut("a".repeat(2_500))).hasLength(2_000)
        assertThat(Conversation.cut("a".repeat(1_999) + "😀")).isEqualTo("a".repeat(1_999))
        assertThat(Conversation.cut("short")).isEqualTo("short")
    }

    private fun said(id: Long, at: Long, owner: Boolean, text: String = "Invented words.") =
        TrainerMessage(id, at, owner, text, if (owner) null else "a-model", answered = !owner, replyTo = null)

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
        const val NOW = TEST_EPOCH_DAY * DAY + 15 * HOUR
        val NOTE_FEEDBACK = Feedback("Invented headline.", "", "", "", "", PlanFollowed.YES, note = "Invented note.")
        val LETTER = LetterTexts.Coach(
            "Invented headline.", "Invented recognition.", "Invented paying off.", "Invented look after.", "Invented rests on.",
            "Invented next week.", "Invented close.",
        )
    }
}
