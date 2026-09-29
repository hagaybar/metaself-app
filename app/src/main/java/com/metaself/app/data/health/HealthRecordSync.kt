package com.metaself.app.data.health

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.lifecycle.AppForeground
import com.metaself.app.data.time.Now
import com.metaself.app.domain.health.HealthKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copying the health record, once per open and on refresh (D67). Every decision lives here; the
 * reading and the writing are behind [HealthSource] and [HealthStore].
 *
 * One open runs in two phases:
 * - **A.** Every granted kind, in [HealthKind] order, takes a changes token if it has none, or drains
 *   the changes since its token. Token-taking always happens for every granted kind; draining stops
 *   once the budget is spent.
 * - **B.** Kinds still catching up take one week each in turn, newest first, until all are done or
 *   the budget is spent, so no kind waits behind another's history.
 *
 * After phase A, a synced workout that still lacks its distance or calories, on a day whose distance
 * or calorie readings phase A's changes just brought, is asked for that figure again
 * ([fillSessionGaps]), at most [SESSION_ASKS_PER_OPEN] workouts per open.
 *
 * Days touched are summarised after phase A and after each turn of phase B, with totals asked over
 * runs of at most [TOTALS_DAYS] days that cover them, one call per run (uncounted). **Accepted:** a
 * crash between a saved bookmark and its summarise leaves that day's summary stale until the day
 * changes again.
 *
 * **Accepted:** the count of empty weeks restarts each open, so a history spread thinly across opens
 * may not stop by that rule; it ends by [FURTHEST_DAYS] at worst.
 *
 * Before both, once, the first open on which older history may be read re-opens the catch-ups (D72);
 * see [reopenForHistory].
 *
 * **Only in the foreground** ([AppForeground]): Health Connect refuses reads from the background, so a
 * copy asked for there does nothing, and one under way is cancelled when the app leaves. A read refused
 * for the background ([BackgroundReadRefused]) stops the pass without a log line, and the next
 * foreground copy rechecks the recent record ([recheckRecent]).
 *
 * **Nothing here throws upwards (D8).** A failure — an exception or an error from the client — is a
 * problem-log entry, and the next open carries on from the last bookmark saved. Log dates are in the
 * phone's own zone.
 */
@Singleton
class HealthRecordSync(
    private val source: HealthSource,
    private val store: HealthStore,
    private val problems: ProblemLog,
    private val now: Now,
    private val zone: () -> ZoneId,
    private val foreground: AppForeground = AppForeground.ALWAYS,
) : HealthRecordCopier, BackgroundHealthCopy {

    @Inject
    constructor(source: HealthSource, store: HealthStore, problems: ProblemLog, now: Now, foreground: AppForeground) :
        this(source, store, problems, now, { ZoneId.systemDefault() }, foreground)

    private val running = Mutex()

    /**
     * The days touched so far by the pass under way, kept here (rather than a local in [copy]) so a
     * pass stopped by a background refusal, or by the app leaving mid-copy, still leaves them readable:
     * [oweRecheck] reads its earliest day for the marker. An outside cancellation of [copyNow] itself
     * (not taken for either of those) is never read this way — it propagates before [oweRecheck] would
     * run. Reset at the start of every [copy]; only [running]'s owner ever touches it.
     */
    private var touchedThisPass = mutableSetOf<Long>()

    /**
     * Only in the foreground: Health Connect refuses a read from the background. The pass runs as a
     * child that is cancelled the moment the app leaves the foreground; the bookmarks it already saved
     * stand, since each is saved only after its own slice or page is stored. A pass stopped that way,
     * or by a [BackgroundReadRefused], is not a failure and logs nothing; it leaves the next foreground
     * copy a recheck of the recent record ([recheckRecent]). A cancellation from anywhere else is
     * passed on, as before.
     */
    override suspend fun copyNow() {
        if (!foreground.isForeground.value) return
        if (!running.tryLock()) return
        try {
            var left = false
            val refused = coroutineScope {
                val pass = async { pass() }
                val watch = launch {
                    foreground.isForeground.first { !it }
                    left = true
                    pass.cancel()
                }
                pass.join()
                watch.cancel()
                // A pass cancelled from elsewhere rethrows its cancellation from await.
                if (left) true else pass.await() == PassEnd.REFUSED
            }
            if (refused) oweRecheck()
        } finally {
            running.unlock()
        }
    }

    /**
     * D99: one pass with no foreground gate — for the weekly letter, whose caller has checked that the
     * background-read permission is held. Shares [running] with [copyNow], so the two never overlap: a
     * copy already under way makes this one return false at once. A refusal is handled as [copyNow]
     * handles it (no log line, a recheck owed); a failure is already logged by [pass].
     */
    override suspend fun copyInBackground(): Boolean {
        if (!running.tryLock()) return false
        return try {
            val end = pass()
            if (end == PassEnd.REFUSED) oweRecheck()
            end == PassEnd.DONE
        } finally {
            running.unlock()
        }
    }

    /** How a pass ended. */
    private enum class PassEnd { DONE, REFUSED, FAILED }

    /** One pass; [PassEnd.REFUSED] when Health Connect refused it for being in the background. Never throws else. */
    private suspend fun pass(): PassEnd = try {
        copy()
        PassEnd.DONE
    } catch (refused: BackgroundReadRefused) {
        PassEnd.REFUSED
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        note("copying stopped: ${failure::class.java.simpleName} ${failure.message}")
        PassEnd.FAILED
    }

    /**
     * The marker is set owed, together with the earliest day this pass left touched but not yet
     * summarised ([touchedThisPass]), if any — so [recheckRecent] re-totals from there rather than only
     * the last few days.
     */
    private suspend fun oweRecheck() {
        try {
            store.setRecentRecheckDue(true, touchedThisPass.minOrNull())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            note("recheck not recorded: ${failure::class.java.simpleName} ${failure.message}")
        }
    }

    private suspend fun copy() {
        touchedThisPass = mutableSetOf()
        val nowMillis = now()
        val granted = source.grantedKinds()
        if (granted.isEmpty()) return
        reopenForHistory(nowMillis)
        val recheck = recheckDue()
        val recheckFrom = if (recheck) recheckFromDayOrNull() else null

        val budget = Budget(READS_PER_OPEN)
        val touched = touchedThisPass
        val arrived = mutableMapOf<HealthKind, MutableSet<Long>>()
        val catching = mutableListOf<CatchUp>()

        for (kind in HealthKind.entries.filter { it in granted }) {
            try {
                val mark = bringUp(kind, nowMillis, budget, touched, arrived)
                if (!mark.catchUpDone) catching += CatchUp(kind, mark)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                passOnRefusal(failure)
                note("$kind: ${failure::class.java.simpleName} ${failure.message}")
            }
        }
        val asked = fillSessionGaps(arrived, granted)
        var capLimited = false
        if (recheck) {
            capLimited = recheckRecent(granted, asked, nowMillis, touched, recheckFrom)
        }
        summarise(touched, nowMillis)
        if (recheck && !capLimited) doneRechecking()

        while (catching.isNotEmpty() && !budget.spent) {
            for (turn in catching.toList()) {
                if (!budget.take()) break
                try {
                    slice(turn, nowMillis, touched)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    passOnRefusal(failure)
                    note("${turn.kind}: ${failure::class.java.simpleName} ${failure.message}")
                    turn.finished = true
                }
                if (turn.finished) catching -= turn
            }
            summarise(touched, nowMillis)
        }
    }

    /**
     * D72. The first open on which history older than [WINDOW_DAYS] days may be read, every catch-up
     * is set going again, so it carries on further back; then the marker is written, and later opens
     * skip this (without asking Health Connect).
     *
     * A cursor within the last [WINDOW_DAYS] days is kept. Every other cursor — however far back it
     * already got, even one that sits at [FURTHEST_DAYS] — is brought forward to that edge: before the
     * permission, the weeks past the history limit may have come back empty rather than refused, so a
     * catch-up could walk all the way to [FURTHEST_DAYS] on nothing but empty weeks without ever really
     * seeing the record. It would normally stop after [EMPTY_SLICES_TO_STOP] such weeks in a row, but
     * that count resets every open, and with all of [HealthKind]'s kinds sharing one open's read budget,
     * a single kind rarely gets [EMPTY_SLICES_TO_STOP] turns before the budget runs out — so a cursor at
     * the bound proves nothing about what is really there, and gets no exemption. Re-reading the weeks
     * between the edge and the limit costs reads, not rows: a record read again replaces its own.
     *
     * **Accepted:** a failure after some bookmarks are saved but before the marker is written is logged,
     * and the next open does this again — which can bring a cursor that has moved past the edge back to
     * it once more. Costs reads, not rows.
     */
    private suspend fun reopenForHistory(nowMillis: Long) {
        try {
            if (store.historyActedOn()) return
            val available = source.historyAvailable()
            if (!available || !source.historyGranted(available)) return
            val edge = nowMillis - WINDOW_DAYS * DAY
            for (kind in HealthKind.entries) {
                val mark = store.bookmark(kind) ?: continue
                val cursor = mark.catchUpCursorMillis ?: continue
                val reopened = mark.copy(catchUpCursorMillis = maxOf(cursor, edge), catchUpDone = false)
                if (reopened != mark) store.saveBookmark(reopened)
            }
            store.markHistoryActedOn()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            passOnRefusal(failure)
            note("older history: ${failure::class.java.simpleName} ${failure.message}")
        }
    }

    /**
     * A workout's distance and calories are Health Connect's totals over its time AT THE MOMENT IT IS
     * READ, and a session is read again only when it changes. A writing app that stores a session
     * before the distance or calories of that stretch (interval data synced in a later batch) left the
     * session without them for good (D81's investigation). So after phase A, a visible, counted synced
     * workout that still lacks a figure is asked for it again — bounded three ways:
     *
     * - **Only where that figure's readings just arrived.** Distance is asked only of a workout on a
     *   day ([arrived]) whose DISTANCE readings phase A's changes just brought, or on the day before
     *   (a workout running past midnight has its later readings filed on the next day); calories
     *   likewise with ACTIVE_KCAL. A day touched only by other kinds, by an expired token's re-read of
     *   the window, or by a catch-up week (read just now, sessions and all) is not a reason to ask.
     * - **Only a figure that is missing AND allowed**, one aggregation call each. A walk that does not
     *   count (D81) is not a gap at all ([HealthStore.sessionGaps]).
     * - **At most [SESSION_ASKS_PER_OPEN] workouts per open**, newest first. The rest are logged as
     *   not asked; each is asked on a later open only if its day receives that figure's readings again.
     *
     * A figure that comes back is stored; a failure is logged and the next workout is still asked (D8).
     * A cancellation is not a failure and is passed on; nor is a [BackgroundReadRefused].
     *
     * Returns the ids of the workouts asked, which [recheckRecent] counts against the same cap.
     */
    private suspend fun fillSessionGaps(arrived: Map<HealthKind, Set<Long>>, granted: Set<HealthKind>): Set<Long> {
        if (HealthKind.EXERCISE !in granted) return emptySet()
        val distanceDays = if (HealthKind.DISTANCE in granted) arrived[HealthKind.DISTANCE].orEmpty() else emptySet()
        val energyDays = if (HealthKind.ACTIVE_KCAL in granted) arrived[HealthKind.ACTIVE_KCAL].orEmpty() else emptySet()
        if (distanceDays.isEmpty() && energyDays.isEmpty()) return emptySet()
        fun near(days: Set<Long>, day: Long) = day in days || day + 1 in days
        val lookIn = (distanceDays + energyDays).flatMapTo(mutableSetOf()) { listOf(it - 1, it) }
        val gaps = try {
            store.sessionGaps(lookIn)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            passOnRefusal(failure)
            note("workout totals: ${failure::class.java.simpleName} ${failure.message}")
            return emptySet()
        }
        val asks = gaps.sortedByDescending { it.startMillis }.mapNotNull { gap ->
            val distance = gap.needsDistance && near(distanceDays, gap.epochDay)
            val energy = gap.needsEnergy && near(energyDays, gap.epochDay)
            if (distance || energy) Triple(gap, distance, energy) else null
        }
        val taken = asks.take(SESSION_ASKS_PER_OPEN)
        for ((gap, askDistance, askEnergy) in taken) askAgain(gap, askDistance, askEnergy)
        if (asks.size > SESSION_ASKS_PER_OPEN) {
            note("workout totals: ${asks.size - SESSION_ASKS_PER_OPEN} more not asked this open")
        }
        return taken.mapTo(mutableSetOf()) { it.first.id }
    }

    /** One workout asked again; a figure that comes back is stored, a failure logged (D8). */
    private suspend fun askAgain(gap: SessionGap, distance: Boolean, energy: Boolean) {
        try {
            val totals = source.sessionTotals(gap.startMillis, gap.endMillis, distance, energy)
            if (totals.distanceM != null || totals.energyKcal != null) store.fillSessionTotals(gap.id, totals)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            passOnRefusal(failure)
            note("workout totals: ${failure::class.java.simpleName} ${failure.message}")
        }
    }

    /** Whether a pass stopped by the background left a recheck owed; a store that cannot say owes none. */
    private suspend fun recheckDue(): Boolean = try {
        store.recentRecheckDue()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        passOnRefusal(failure)
        note("recheck: ${failure::class.java.simpleName} ${failure.message}")
        false
    }

    /** [HealthStore.recheckFromDay], read only when a recheck is due; a store that cannot say owes none extra. */
    private suspend fun recheckFromDayOrNull(): Long? = try {
        store.recheckFromDay()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        passOnRefusal(failure)
        note("recheck: ${failure::class.java.simpleName} ${failure.message}")
        null
    }

    /**
     * The second look a pass stopped by the background leaves owed (see [HealthStore.recentRecheckDue]).
     * Before this fix a refused aggregate was logged and stored as "no figure", so a workout could keep
     * a missing distance or calories and a day stale totals; now a refusal stops the pass before either
     * is stored, but a pass cut short still leaves days unsummarised.
     *
     * - **Gaps:** every visible synced workout of the last [WINDOW_DAYS] days still missing a figure
     *   whose readings are allowed is asked again, newest first — within [SESSION_ASKS_PER_OPEN], which
     *   the ordinary asks of this open ([asked]) have already drawn on. Those not reached are not logged.
     * - **Totals:** every day from [fromDay] through today joins the days to summarise ([summarise]
     *   then splits that run into calls of at most [TOTALS_DAYS] days) — [fromDay] is the earliest day
     *   the marker named ([HealthStore.recheckFromDay]), or, with none, the last [RECHECK_DAYS] days.
     *
     * The marker is cleared once the summarise after it has run ([doneRechecking]) — but only when
     * [SESSION_ASKS_PER_OPEN] was not the reason some gaps went unasked: this return value tells [copy]
     * to keep the marker owed instead, so a later open reaches the rest.
     *
     * @return whether the per-open cap, not a lack of gaps, was why some were not asked again.
     */
    private suspend fun recheckRecent(
        granted: Set<HealthKind>,
        asked: Set<Long>,
        nowMillis: Long,
        touched: MutableSet<Long>,
        fromDay: Long?,
    ): Boolean {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone()).toLocalDate().toEpochDay()
        val from = fromDay?.coerceAtMost(today) ?: (today - RECHECK_DAYS + 1)
        touched += from..today
        if (HealthKind.EXERCISE !in granted) return false
        val distance = HealthKind.DISTANCE in granted
        val energy = HealthKind.ACTIVE_KCAL in granted
        if (!distance && !energy) return false
        val gaps = try {
            store.sessionGaps(((today - WINDOW_DAYS)..today).toSet())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            passOnRefusal(failure)
            note("workout totals: ${failure::class.java.simpleName} ${failure.message}")
            return false
        }
        val toAsk = gaps.asSequence()
            .filter { it.id !in asked }
            .sortedByDescending { it.startMillis }
            .map { gap -> Triple(gap, gap.needsDistance && distance, gap.needsEnergy && energy) }
            .filter { (_, d, e) -> d || e }
            .toList()
        val remaining = (SESSION_ASKS_PER_OPEN - asked.size).coerceAtLeast(0)
        toAsk.take(remaining).forEach { (gap, d, e) -> askAgain(gap, d, e) }
        return toAsk.size > remaining
    }

    private suspend fun doneRechecking() {
        try {
            store.setRecentRecheckDue(false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            passOnRefusal(failure)
            note("recheck: ${failure::class.java.simpleName} ${failure.message}")
        }
    }

    /**
     * Phase A for one kind: a token if it has none (uncounted), otherwise its changes (counted). The
     * days a page of changes brought are added to [touched], and, for the kinds a workout's missing
     * figures come from, to [arrived] under the kind.
     */
    private suspend fun bringUp(
        kind: HealthKind,
        nowMillis: Long,
        budget: Budget,
        touched: MutableSet<Long>,
        arrived: MutableMap<HealthKind, MutableSet<Long>>,
    ): HealthSyncEntity {
        val mark = store.bookmark(kind)
        if (mark?.changesToken != null) return drainChanges(kind, mark, nowMillis, budget, touched, arrived)

        val fresh = HealthSyncEntity(
            kind = kind.name,
            changesToken = source.changesToken(kind),
            tokenAtMillis = nowMillis,
            catchUpCursorMillis = mark?.catchUpCursorMillis ?: nowMillis,
            catchUpDone = mark?.catchUpDone ?: false,
        )
        store.saveBookmark(fresh)
        return fresh
    }

    /**
     * An expired token takes its fresh token FIRST, then re-reads the window: a change made during the
     * re-read is then after the new token, not lost between the two. The bookmark is saved last.
     */
    private suspend fun drainChanges(
        kind: HealthKind,
        start: HealthSyncEntity,
        nowMillis: Long,
        budget: Budget,
        touched: MutableSet<Long>,
        arrived: MutableMap<HealthKind, MutableSet<Long>>,
    ): HealthSyncEntity {
        var mark = start
        while (budget.take()) {
            val page = source.changes(kind, mark.changesToken!!)
            if (page.expired) {
                val token = source.changesToken(kind)
                val from = nowMillis - WINDOW_DAYS * DAY
                touched += store.replaceWindow(kind, from, nowMillis, source.readWindow(kind, from, nowMillis))
                mark = mark.copy(changesToken = token, tokenAtMillis = nowMillis)
                store.saveBookmark(mark)
                return mark
            }
            val days = store.apply(page.upserts, page.deletedIds)
            touched += days
            if (kind in GAP_KINDS) arrived.getOrPut(kind) { mutableSetOf() } += days
            mark = mark.copy(changesToken = page.nextToken, tokenAtMillis = nowMillis)
            store.saveBookmark(mark)
            if (!page.hasMore) break
        }
        return mark
    }

    private class CatchUp(val kind: HealthKind, var mark: HealthSyncEntity) {
        var emptyInARow = 0
        var finished = false
    }

    /**
     * One week of catch-up for one kind. A refusal of a week older than [WINDOW_DAYS] is the history
     * limit (rule 5) unless the failure is [transient]; any other refusal stops the kind for this open
     * and keeps its cursor. The end of a catch-up is logged with its date either way, so the history
     * limit is visible whether Health Connect refuses or returns nothing: after empty weeks, the date
     * is where the empty run began.
     */
    private suspend fun slice(turn: CatchUp, nowMillis: Long, touched: MutableSet<Long>) {
        val kind = turn.kind
        val to = turn.mark.catchUpCursorMillis ?: nowMillis
        val from = to - SLICE_DAYS * DAY
        val records = try {
            source.readWindow(kind, from, to)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (refused: Exception) {
            // The background is neither the history limit nor a failure: stop the pass, save nothing.
            passOnRefusal(refused)
            val reason = "${refused::class.java.simpleName} ${refused.message}"
            if (to <= nowMillis - WINDOW_DAYS * DAY && !transient(refused)) {
                note("$kind: reading before ${dateOf(to)} was refused ($reason); taken as the history limit")
                turn.mark = turn.mark.copy(catchUpDone = true)
                store.saveBookmark(turn.mark)
            } else {
                note("$kind: reading before ${dateOf(to)} failed ($reason); will try again")
            }
            turn.finished = true
            return
        }
        touched += store.apply(records, emptyList())
        turn.emptyInARow = if (records.isEmpty()) turn.emptyInARow + 1 else 0
        val emptyRun = turn.emptyInARow >= EMPTY_SLICES_TO_STOP
        val done = emptyRun || from <= nowMillis - FURTHEST_DAYS * DAY
        turn.mark = turn.mark.copy(catchUpCursorMillis = from, catchUpDone = done)
        store.saveBookmark(turn.mark)
        if (done) {
            val nothingBefore = if (emptyRun) from + turn.emptyInARow * SLICE_DAYS * DAY else from
            note("$kind: nothing before ${dateOf(nothingBefore)}; catch-up finished")
            turn.finished = true
        }
    }

    /**
     * Summarises the days touched since the last summarise. They are cut into runs, each starting at a
     * touched day and reaching at most [TOTALS_DAYS] days on, and each run gets its own totals call, so
     * a scattered set of days is never one call over months. A run whose call throws is summarised with
     * every metric failed, which keeps its stored totals; the other runs are not affected.
     *
     * A run is removed from [touched] only once it is actually summarised — never upfront — so a
     * [BackgroundReadRefused] partway through (which stops the pass, see [pass]) leaves the runs not yet
     * reached still in [touched] (kept as [touchedThisPass]), for [oweRecheck] to read.
     */
    private suspend fun summarise(touched: MutableSet<Long>, nowMillis: Long) {
        if (touched.isEmpty()) return
        val days = touched.sorted()
        for (run in runsOf(days)) {
            val totals = try {
                source.dayTotals(run.first(), run.last())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                passOnRefusal(failure)
                note("totals: ${failure::class.java.simpleName} ${failure.message}")
                TotalsResult.ALL_FAILED
            }
            store.summarise(run.toSet(), totals, nowMillis)
            touched -= run.toSet()
        }
    }

    private fun runsOf(sortedDays: List<Long>): List<List<Long>> {
        val runs = mutableListOf<MutableList<Long>>()
        for (day in sortedDays) {
            val current = runs.lastOrNull()
            if (current != null && day < current.first() + TOTALS_DAYS) current += day else runs += mutableListOf(day)
        }
        return runs
    }

    /**
     * A failure that says "not now" rather than "never": an I/O failure, a [SecurityException] (Health
     * Connect throws this for reads made while the app is in the background rather than for a
     * genuinely refused permission; treating it as final would end a catch-up over nothing — the
     * history limit still shows up as empty weeks, which the "catch-up finished" line records), the
     * binder's RemoteException or a subclass of it (matched by name, so pure tests need no Android
     * class), or an IllegalStateException whose message says "rate limit", "rate-limit", "ratelimit" or
     * "quota" in any case — not merely "rate", which is inside ordinary words.
     */
    private fun transient(failure: Exception): Boolean =
        failure is IOException ||
            failure is SecurityException ||
            generateSequence<Class<*>>(failure.javaClass) { it.superclass }.any { it.name == REMOTE_EXCEPTION } ||
            (failure is IllegalStateException && failure.message.let { message ->
                message != null && RATE_LIMITED.any { message.contains(it, ignoreCase = true) }
            })

    /** A [BackgroundReadRefused] is not a failure to log: it stops the pass, from wherever it came. */
    private fun passOnRefusal(failure: Exception) {
        if (failure is BackgroundReadRefused) throw failure
    }

    /**
     * The problem log writes a file (D8); `copyNow` is called from the caller's own dispatcher — Main,
     * in the app — so the write is moved off it here rather than left to block the UI thread.
     */
    private suspend fun note(detail: String) = withContext(Dispatchers.IO) {
        problems.record(kind = "health", detail = detail)
    }

    private fun dateOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate()

    private class Budget(private var left: Int) {
        val spent: Boolean get() = left <= 0
        fun take(): Boolean = if (left > 0) { left--; true } else false
    }

    companion object {
        private const val DAY = 86_400_000L
        private const val REMOTE_EXCEPTION = "android.os.RemoteException"
        private val RATE_LIMITED = listOf("rate limit", "rate-limit", "ratelimit", "quota")

        /** The kinds whose arrival may fill a workout's missing figure: its distance, its calories. */
        private val GAP_KINDS = setOf(HealthKind.DISTANCE, HealthKind.ACTIVE_KCAL)

        /**
         * A choice, not a measured quota: at most this many workouts are asked again for a missing
         * figure in one open (each ask is one or two aggregation calls, outside [READS_PER_OPEN]).
         */
        const val SESSION_ASKS_PER_OPEN = 10

        /**
         * A choice, not a measurement: after a pass stopped by the background, this many days back
         * from today (today included) have their totals asked again ([recheckRecent]).
         */
        const val RECHECK_DAYS = 3L

        /** A choice: a totals call covers at most this many consecutive days. */
        const val TOTALS_DAYS = 30L

        /** A choice: a week is a readable slice for any kind. */
        const val SLICE_DAYS = 7L

        /** A choice, matching the documented 30-day life of a changes token; not a measured quota. */
        const val WINDOW_DAYS = 30L

        /** A choice: eight empty weeks in a row means the record has begun. */
        const val EMPTY_SLICES_TO_STOP = 8

        /** A choice: two years bounds the catch-up on a phone that has nothing. */
        const val FURTHEST_DAYS = 730L

        /**
         * A choice that keeps one open cheap. Health Connect's real quota is not measured here.
         *
         * Counted: each page of changes and each catch-up week. **Not counted:** taking a token, the
         * totals calls (one per run of up to [TOTALS_DAYS] touched days), the re-read of the window
         * after an expired token, each workout's two aggregate calls made while it is read, and the
         * calls asking again for a workout's missing figures after phase A — those have their own
         * bound, at most [SESSION_ASKS_PER_OPEN] workouts of one or two calls each, and only on days
         * whose distance or calorie readings phase A just brought ([fillSessionGaps]).
         */
        const val READS_PER_OPEN = 60
    }
}
