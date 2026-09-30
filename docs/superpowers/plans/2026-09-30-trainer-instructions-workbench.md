# The trainer's instructions workbench — Implementation Plan (D106, D107)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** D106 — a testing page, Settings → last entry "Test the trainer's instructions": pick one of the
trainer's four paths and its inputs, optionally load instructions from a text file (replacing the whole
system message, in memory for this visit), Send the real request built from the real record with no reply
shape imposed, and read the reply as plain text, with Copy reply and Copy what was sent. It stores
nothing. D107 — every planned tick sent to the trainer carries `"of_weeks"` (the plan's length) beside
`"week"`, and the shared instructions say so. Version 0.71.0.

**Decision:** the owner's, 2026-09-29 — `docs/superpowers/specs/2026-09-29-trainer-instructions-workbench-design.md`.

**Architecture:**

```
domain/trainer/Programme.kt             PlannedTick gains ofWeeks (D107)                                  Task 1
domain/trainer/PlanProgress.kt, TrainerHome.kt   fill ofWeeks from the plan's week count                  Task 1
data/ai/TrainerPrompt.kt                "of_weeks" in plannedTick; one clause in COMMON (D107)            Task 1
                                        instructions(path), userMessage(request), workbenchBody(...)     Task 2
data/ai/ChatRequest.kt                  plainBody: the same assembly, no response_format                  Task 2
domain/trainer/TrainerPath.kt           the four paths, and which a question belongs to                   Task 2
data/trainer/AskTheTrainer.kt           question builders + request() made public and shared             Task 3
domain/trainer/Workbench.kt             WorkbenchSender port, WorkbenchReply                              Task 4
data/ai/OpenAiWorkbench.kt              the sender over the shared OpenAiCall                             Task 4
data/trainer/TrainerWorkbench.kt        inputs → the real request → sender; reads only                    Task 5
data/trainer/InstructionFiles.kt        read/write/name a picked document, uris as strings                Task 6
di/AiModule.kt, di/DataModule.kt        bindings                                                          Tasks 4, 6
ui/trainer/WorkbenchWording.kt          every sentence on the page                                        Task 7
ui/screen/settings/TrainerWorkbenchViewModel.kt   state, load/save/send                                   Task 7 (JUnit 5)
ui/screen/settings/TrainerWorkbenchPage.kt        the page; reuses the plan form's ChoiceRow              Task 8 (Robolectric)
ui/screen/settings/SettingsPage.kt, SettingsScreen.kt, ui/settings/SettingsIndexWording.kt,
ui/nav/SettingsDestination.kt, res/values/strings.xml   the seventh Settings entry, pickers, clipboard    Task 9
privacy.html, app/build.gradle.kts      of_weeks and the page named; 0.71.0 / 127                         Task 10
```

**Tech Stack:** Kotlin 1.9.22, kotlinx.serialization, OkHttp via the shared `OpenAiCall`, Compose Material 3,
Hilt, JUnit 5 + Truth, `okhttp3.mockwebserver`, Robolectric (JUnit 4) for the render test.

**Red lines (stop and report if crossed):**

- **The workbench writes nothing to the trainer's stores or the programmes' store.** No `putReview`,
  `addPlan`, `keep`, `unkeep`, `add`, `keep*`, `stop`, `confirm`. Task 5's test fails if it does. (The
  shared `OpenAiCall` still counts the request against the day's ceiling and may remember the profile
  that answered — see design choice 3.)
- **The question builders are shared, not copied.** `AskTheTrainer`'s real asks and the workbench call the
  same `planQuestion` / `feedbackQuestion` / `evaluateQuestion` / `adjustQuestion` / `request`.
- **No `response_format` and no schema instruction in a workbench body.** Real bodies are byte-for-byte
  unchanged except for D107's `of_weeks` and its clause.
- **Anonymisation:** every figure, word and file name in a test is invented; build on `TEST_EPOCH_DAY` and
  `aProfile()`. No real instructions text, no real reply.
- **Never `git add -A`; never stage `tools/__pycache__`; never bare `./gradlew`; never pipe a build whose
  result is reported.** No push.

## Design choices the spec leaves open — settled here

1. **`of_weeks` lives on `PlannedTick`** (`PlannedTick(week, ofWeeks, session)`), filled from
   `progress.weeks.size` where a tick is made (`PlanProgress.tickOf`, `PlanCard.Running.next`) —
   `PlanProgress.of` builds one `WeekProgress` per `plan.weeks` entry, so that is the plan's length. No
   default value: a forgotten call site must not compile. The clause goes in **COMMON**, as the spec says.
2. **Paths are a new enum `TrainerPath`** (FEEDBACK, PLAN, EVALUATE, ADJUST — the spec's order). The
   system text per path is `TrainerPrompt.instructions(path)`; the four real bodies now take it from
   there (`TrainerPath.of(request.question)`), so "save the app's instructions" is the same string the
   real ask sends, not a copy.
3. **The workbench calls `OpenAiCall.remember` on an answer.** Verified: `remember` writes only when the
   profile that answered differs from the one remembered. The workbench sends no `response_format`, so a
   retry can only change `temperature` / `reasoning_effort` (a `response_format` refusal cannot happen);
   the `strictFormat` it carries is the one already remembered or the first guess, which the next real
   call would use anyway. Remembering saves the next workbench or real call the same refusal and retry.
4. **The reply text is the message content** out of the provider's envelope (`Outcome.Body.text` is the
   raw HTTP body). `TrainerResponse.content` is made `internal` and reused. An envelope with no content
   is `EstimateResult.Unreadable`, worded by the existing `TrainerWording.failure`.
5. **Files go through a new small port `InstructionFiles`** (uris as strings, plus the document's display
   name), not `BackupFiles`: `BackupFiles` takes `android.net.Uri`, which would force the view-model test
   onto Robolectric; and the page needs the file's name for its "Will send" line.
6. **"Use the app's own instructions"** appears once a file is loaded, to go back to the baseline without
   leaving the page. Not in the spec's list, not excluded by it; one button.
7. **A blank or unreadable file is refused** ("The file could not be read, or is empty. Nothing changed."),
   rather than sending an empty system message.
8. **Changing the path clears the last reply** so a reply is never shown under a path that did not ask it.
9. **The page's own privacy line** under Send: what the chosen path sends, under the named instructions,
   one of today's AI requests, nothing kept. The number of requests is not shown (it would need the AI
   settings store on this page).
10. **privacy.html changes** (Task 10): D107 sends one new figure (the plan's length), and the page sends
    the owner's own file text as instructions. One clause and one sentence. README unchanged.

## The shared box — read before any build

Another project builds on this machine. `~/bin/gradlew-safe` holds the one lock. **Before every Gradle
command run `free -m`;** under about 4000 MB available, wait and check again. One Gradle command at a time,
never in parallel, never `gradle --stop`. Always:

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "<pattern>" > /tmp/ms-bench.log 2>&1; echo "exit $?"
```

Read the log (`tail -40 /tmp/ms-bench.log`) — never pipe the build itself.

---

### Task 1: The plan's length on every planned tick (D107)

**Files:**
- Modify: `app/src/main/java/com/metaself/app/domain/trainer/Programme.kt:75-76`
- Modify: `app/src/main/java/com/metaself/app/domain/trainer/PlanProgress.kt` (`tickOf`)
- Modify: `app/src/main/java/com/metaself/app/domain/trainer/TrainerHome.kt` (`PlanCard.Running.next`)
- Modify: `app/src/main/java/com/metaself/app/data/ai/TrainerPrompt.kt` (`COMMON`, `plannedTick`)
- Test: `app/src/test/java/com/metaself/app/data/ai/TrainerPromptTest.kt`, and every test constructing `PlannedTick`

- [ ] **Step 1: Write the failing tests.** In `TrainerPromptTest.kt`, in
  `` `a plan question and a review carry the weekly plan's session, or null` `` change the tick and the key assertion:

```kotlin
        val tick = PlannedTick(2, 4, PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Steady walk"))
```
```kotlin
        assertThat(question.getValue("planned").jsonObject.keys).containsExactly("week", "of_weeks", "kind", "minutes", "effort", "what")
        assertThat(question.getValue("planned").jsonObject.getValue("week").jsonPrimitive.int).isEqualTo(2)
        assertThat(question.getValue("planned").jsonObject.getValue("of_weeks").jsonPrimitive.int).isEqualTo(4)
```

  In `` `a review's own weekly-plan tick is sent too, with its keys and week number` ``:

```kotlin
        val tick = PlannedTick(3, 4, PlannedSession(WorkoutKind.RUN, 25, PlannedEffort.PUSH, "Push run"))
```
```kotlin
        assertThat(question.getValue("planned").jsonObject.keys).containsExactly("week", "of_weeks", "kind", "minutes", "effort", "what")
        assertThat(question.getValue("planned").jsonObject.getValue("week").jsonPrimitive.int).isEqualTo(3)
        assertThat(question.getValue("planned").jsonObject.getValue("of_weeks").jsonPrimitive.int).isEqualTo(4)
```

  And add:

```kotlin
    /** D107: the shared instructions say what of_weeks is, so any instructions can say "the first week of two". */
    @Test
    fun `every path's instructions say of_weeks is the plan's length`() {
        listOf(
            TrainerPrompt.planBody("a-model", planRequest()),
            TrainerPrompt.feedbackBody("a-model", reviewRequest()),
            TrainerPrompt.evaluateBody("a-model", evaluateRequest()),
            TrainerPrompt.adjustBody("a-model", adjustRequest()),
        ).forEach { body ->
            assertThat(systemContent(body)).contains("of_weeks is how many weeks that plan has")
        }
    }
```

- [ ] **Step 2: Update every other `PlannedTick(` in tests to the three-argument form.** Every fixture plan in
  these files is two weeks long (checked: `List(2) { PlanWeek(...) }` or two `PlanWeek`s), so the length is 2:

```bash
cd /home/ubuntu/projects/metaself-app
sed -i -E 's/PlannedTick\(([0-9]+), /PlannedTick(\1, 2, /g' \
  app/src/test/java/com/metaself/app/data/trainer/AskTheTrainerTest.kt \
  app/src/test/java/com/metaself/app/domain/trainer/TrainerHomeTest.kt \
  app/src/test/java/com/metaself/app/domain/trainer/PlanProgressTest.kt \
  app/src/test/java/com/metaself/app/ui/screen/trainer/PlanSessionViewModelTest.kt \
  app/src/test/java/com/metaself/app/ui/screen/trainer/ReviewSessionViewModelTest.kt \
  app/src/test/java/com/metaself/app/ui/screen/trainer/ReviewSessionScreenRenderTest.kt \
  app/src/test/java/com/metaself/app/ui/screen/trainer/PlanSessionScreenRenderTest.kt \
  app/src/test/java/com/metaself/app/ui/trainer/ProgrammeWordingTest.kt
grep -rn "PlannedTick(" app/src/test app/src/main
```

  Expected: every hit has three arguments except the two `main` constructions changed in Step 4 and the
  data class. `PlanProgressTest`'s `tickOf(1)` expectation becomes `PlannedTick(2, 2, easyWalk)` — its
  `plan` has two weeks, so this asserts the length is filled.

- [ ] **Step 3: Run to see them fail to compile.** `free -m`; then
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.ai.TrainerPromptTest" > /tmp/ms-bench-1.log 2>&1; echo "exit $?"`.
  Expected: exit non-zero, "No value passed for parameter 'session'" / too many arguments.

- [ ] **Step 4: Implement.** `Programme.kt`:

```kotlin
/** A planned session with its week, counted from 1 (D96), and how many weeks its plan has (D107). */
data class PlannedTick(val week: Int, val ofWeeks: Int, val session: PlannedSession)
```

  `PlanProgress.kt`, `tickOf`:

```kotlin
    fun tickOf(workoutId: Long): PlannedTick? = weeks.firstNotNullOfOrNull { week ->
        week.ticks.firstOrNull { it.by?.id == workoutId }?.let { PlannedTick(week.index + 1, weeks.size, it.planned) }
    }
```

  `TrainerHome.kt`, `PlanCard.Running.next`:

```kotlin
                progress.weeks[weekIndex].next?.let { PlannedTick(weekIndex + 1, progress.weeks.size, it) }
```

  `TrainerPrompt.kt`, `plannedTick`:

```kotlin
    private fun plannedTick(tick: PlannedTick): JsonObject = buildJsonObject {
        put("week", tick.week)
        put("of_weeks", tick.ofWeeks)
        plannedJson(tick.session).forEach { (name, value) -> put(name, value) }
    }
```

  `TrainerPrompt.kt`, `COMMON` — after the paragraph ending `never count sessions yourself.`, insert a
  paragraph (keep the blank lines around it, and the `trimIndent()` indentation):

```
        When the question gives planned, its week is the week of their weekly plan it falls in, counted
        from 1, and of_weeks is how many weeks that plan has.
```

- [ ] **Step 5: Run the trainer tests.** `free -m`; then
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.ai.TrainerPromptTest" --tests "com.metaself.app.data.trainer.AskTheTrainerTest" --tests "com.metaself.app.domain.trainer.*" --tests "com.metaself.app.ui.screen.trainer.*" --tests "com.metaself.app.ui.trainer.*" > /tmp/ms-bench-1.log 2>&1; echo "exit $?"`.
  Expected: exit 0.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/metaself/app/domain/trainer/Programme.kt \
  app/src/main/java/com/metaself/app/domain/trainer/PlanProgress.kt \
  app/src/main/java/com/metaself/app/domain/trainer/TrainerHome.kt \
  app/src/main/java/com/metaself/app/data/ai/TrainerPrompt.kt \
  app/src/test/java/com/metaself/app/data/ai/TrainerPromptTest.kt \
  app/src/test/java/com/metaself/app/data/trainer/AskTheTrainerTest.kt \
  app/src/test/java/com/metaself/app/domain/trainer/TrainerHomeTest.kt \
  app/src/test/java/com/metaself/app/domain/trainer/PlanProgressTest.kt \
  app/src/test/java/com/metaself/app/ui/screen/trainer/PlanSessionViewModelTest.kt \
  app/src/test/java/com/metaself/app/ui/screen/trainer/ReviewSessionViewModelTest.kt \
  app/src/test/java/com/metaself/app/ui/screen/trainer/ReviewSessionScreenRenderTest.kt \
  app/src/test/java/com/metaself/app/ui/screen/trainer/PlanSessionScreenRenderTest.kt \
  app/src/test/java/com/metaself/app/ui/trainer/ProgrammeWordingTest.kt
git commit -m "feat(trainer): a planned tick carries the plan's length as of_weeks (D107)"
```

---

### Task 2: One system text per path, the user message, and a body with no reply shape

**Files:**
- Create: `app/src/main/java/com/metaself/app/domain/trainer/TrainerPath.kt`
- Modify: `app/src/main/java/com/metaself/app/data/ai/ChatRequest.kt`
- Modify: `app/src/main/java/com/metaself/app/data/ai/TrainerPrompt.kt`
- Test: `app/src/test/java/com/metaself/app/data/ai/TrainerPromptTest.kt`

- [ ] **Step 1: Write the failing tests** (append to `TrainerPromptTest`; add imports
  `com.metaself.app.domain.trainer.TrainerPath`):

```kotlin
    /** D106: "save the app's instructions" is the system message each real body sends, not a copy of it. */
    @Test
    fun `each path's instructions are exactly the system message its body sends`() {
        val strict = RequestProfile.DETERMINISTIC
        assertThat(systemContent(TrainerPrompt.planBody("a-model", planRequest(), strict))).isEqualTo(TrainerPrompt.instructions(TrainerPath.PLAN))
        assertThat(systemContent(TrainerPrompt.feedbackBody("a-model", reviewRequest(), strict))).isEqualTo(TrainerPrompt.instructions(TrainerPath.FEEDBACK))
        assertThat(systemContent(TrainerPrompt.evaluateBody("a-model", evaluateRequest(), strict))).isEqualTo(TrainerPrompt.instructions(TrainerPath.EVALUATE))
        assertThat(systemContent(TrainerPrompt.adjustBody("a-model", adjustRequest(), strict))).isEqualTo(TrainerPrompt.instructions(TrainerPath.ADJUST))
    }

    @Test
    fun `each body's user message is userMessage of its request`() {
        listOf(
            planRequest() to TrainerPrompt.planBody("a-model", planRequest()),
            reviewRequest() to TrainerPrompt.feedbackBody("a-model", reviewRequest()),
            evaluateRequest() to TrainerPrompt.evaluateBody("a-model", evaluateRequest()),
            adjustRequest() to TrainerPrompt.adjustBody("a-model", adjustRequest()),
        ).forEach { (request, body) ->
            assertThat(userContent(Json.parseToJsonElement(body).jsonObject)).isEqualTo(TrainerPrompt.userMessage(request))
        }
    }

    /** D106: the loaded text is the whole system message; the reply is not pinned in any way. */
    @Test
    fun `a workbench body has the given text as its only system message, the real user message, and no reply shape`() {
        val body = Json.parseToJsonElement(
            TrainerPrompt.workbenchBody("a-model", LOADED, planRequest(), RequestProfile.REASONING),
        ).jsonObject

        val sent = messages(body)
        assertThat(sent.map { it.getValue("role").jsonPrimitive.content }).containsExactly("system", "user").inOrder()
        assertThat(sent[0].getValue("content").jsonPrimitive.content).isEqualTo(LOADED)
        assertThat(sent[1].getValue("content").jsonPrimitive.content).isEqualTo(TrainerPrompt.userMessage(planRequest()))
        assertThat(body.keys).containsExactly("model", "reasoning_effort", "messages").inOrder()
        assertThat(body.getValue("reasoning_effort").jsonPrimitive.content).isEqualTo("low")
    }

    @Test
    fun `a workbench body keeps the model's settings, and adds no schema even for a model without the strict format`() {
        val deterministic = Json.parseToJsonElement(
            TrainerPrompt.workbenchBody("a-model", LOADED, reviewRequest(), RequestProfile.DETERMINISTIC),
        ).jsonObject
        val loose = TrainerPrompt.workbenchBody("a-model", LOADED, reviewRequest(), RequestProfile.REASONING.copy(strictFormat = false))

        assertThat(deterministic.keys).containsExactly("model", "temperature", "messages").inOrder()
        assertThat(deterministic.getValue("temperature").jsonPrimitive.int).isEqualTo(0)
        assertThat(systemContent(loose)).isEqualTo(LOADED)
        assertThat(Json.parseToJsonElement(loose).jsonObject.keys).doesNotContain("response_format")
    }
```

  and in the companion object: `const val LOADED = "Invented instructions: a short note from a coach."`

- [ ] **Step 2: Run to see them fail.** `free -m`; `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.ai.TrainerPromptTest" > /tmp/ms-bench-2.log 2>&1; echo "exit $?"`.
  Expected: compile failure (`TrainerPath`, `instructions`, `userMessage`, `workbenchBody` unresolved).

- [ ] **Step 3: Create `TrainerPath.kt`.**

```kotlin
package com.metaself.app.domain.trainer

/** The trainer's four asks (D86, D87, D93, D97), in the order the workbench (D106) lists them. */
enum class TrainerPath {
    FEEDBACK,
    PLAN,
    EVALUATE,
    ADJUST;

    companion object {
        fun of(question: TrainerQuestion): TrainerPath = when (question) {
            is TrainerQuestion.Review -> FEEDBACK
            is TrainerQuestion.Plan -> PLAN
            is TrainerQuestion.Evaluate -> EVALUATE
            is TrainerQuestion.Adjust -> ADJUST
        }
    }
}
```

- [ ] **Step 4: `ChatRequest.kt` — one assembly, two bodies.** Replace the `return buildJsonObject { … }.toString()`
  at the end of `body` with a call to a private `assemble`, and add `plainBody`. Add
  `import kotlinx.serialization.json.JsonObjectBuilder`.

```kotlin
        return assemble(model, profile, sent) {
            putJsonObject("response_format") {
                if (profile.strictFormat) {
                    put("type", "json_schema")
                    putJsonObject("json_schema") {
                        put("name", schemaName)
                        put("strict", true)
                        put("schema", schema)
                    }
                } else {
                    put("type", "json_object")
                }
            }
        }
    }

    /**
     * D106: the same request with no reply shape — no `response_format`, and nothing added to any
     * message. How it is sent is still the model's [RequestProfile].
     */
    fun plainBody(model: String, profile: RequestProfile, messages: List<Message>): String =
        assemble(model, profile, messages) {}

    /** The one place the parts every request shares are put in order: model, settings, messages, then [rest]. */
    private fun assemble(
        model: String,
        profile: RequestProfile,
        messages: List<Message>,
        rest: JsonObjectBuilder.() -> Unit,
    ): String = buildJsonObject {
        put("model", model)
        if (profile.temperature) put("temperature", 0)
        profile.reasoningEffort?.let { put("reasoning_effort", it) }
        putJsonArray("messages") {
            messages.forEach { message ->
                add(
                    buildJsonObject {
                        put("role", message.role)
                        put("content", message.content)
                    },
                )
            }
        }
        rest()
    }.toString()
```

  Key order is unchanged (`model`, `temperature`, `reasoning_effort`, `messages`, `response_format`), so
  every pinned body test stays green.

- [ ] **Step 5: `TrainerPrompt.kt`.** Add `import com.metaself.app.domain.trainer.TrainerPath`. Replace
  `messages(task, request)` and make the four bodies call `messages(request)`
  (`messages(PLAN, request)` → `messages(request)`, likewise FEEDBACK, EVALUATE, ADJUST):

```kotlin
    /**
     * D106: the whole system message [path] sends today — the shared part and the path's own, joined as
     * every body joins them. The workbench saves this to a file as the starting point.
     */
    fun instructions(path: TrainerPath): String = COMMON + "\n\n" + when (path) {
        TrainerPath.FEEDBACK -> FEEDBACK
        TrainerPath.PLAN -> PLAN
        TrainerPath.EVALUATE -> EVALUATE
        TrainerPath.ADJUST -> ADJUST
    }

    /** The user message every body sends: [request], serialised. Shared with the workbench (D106). */
    fun userMessage(request: TrainerRequest): String = user(request).toString()

    /**
     * D106: the workbench's body — [system] as the only system message, the real user message, the
     * model's own settings — and no reply shape: no `response_format`, no schema instruction.
     */
    fun workbenchBody(
        model: String,
        system: String,
        request: TrainerRequest,
        profile: RequestProfile = RequestProfile.guess(model),
    ): String = ChatRequest.plainBody(
        model,
        profile,
        listOf(ChatRequest.Message("system", system), ChatRequest.Message("user", userMessage(request))),
    )

    private fun messages(request: TrainerRequest) = listOf(
        ChatRequest.Message("system", instructions(TrainerPath.of(request.question))),
        ChatRequest.Message("user", userMessage(request)),
    )
```

  Each `…Body` keeps its `require(request.question is …)` first, so the path from the question is the
  body's own.

- [ ] **Step 6: Run the AI tests.** `free -m`; `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.ai.*" > /tmp/ms-bench-2.log 2>&1; echo "exit $?"`. Expected: exit 0.

- [ ] **Step 7: Commit.**

```bash
git add app/src/main/java/com/metaself/app/domain/trainer/TrainerPath.kt \
  app/src/main/java/com/metaself/app/data/ai/ChatRequest.kt \
  app/src/main/java/com/metaself/app/data/ai/TrainerPrompt.kt \
  app/src/test/java/com/metaself/app/data/ai/TrainerPromptTest.kt
git commit -m "feat(trainer): each path's instructions, the user message and an unshaped body, from one place (D106)"
```

---

### Task 3: The question builders, shared (no behaviour change)

**Files:**
- Modify: `app/src/main/java/com/metaself/app/data/trainer/AskTheTrainer.kt`
- Test: `app/src/test/java/com/metaself/app/data/trainer/AskTheTrainerTest.kt` (unchanged; must stay green)

- [ ] **Step 1: Refactor.** Add `import com.metaself.app.domain.movement.Workout`. Replace `suggest`,
  the feedback half of `save`, `evaluate`, `adjust`, `request` and the companion as below; everything else
  in the class is unchanged.

```kotlin
    suspend fun suggest(answers: PlanAnswers): Suggested =
        when (val reply = trainer.suggest(request(planQuestion(answers)))) {
            is TrainerReply.Failed -> Suggested.Failed(reply.failure)
            is TrainerReply.Answered -> {
                val plan = TrainerPlan(0, now(), answers, reply.value, reply.model, kept = false)
                Suggested.Planned(plan.copy(id = store.addPlan(plan)))
            }
        }
```

  In `save`, from `val plan = planId?.let …` to the end:

```kotlin
        val question = feedbackQuestion(workout, saved)
        return when (val reply = trainer.feedback(request(question, exceptWorkoutId = workoutId))) {
            is TrainerReply.Failed -> Reviewed.NoFeedback(saved, reply.failure)
            is TrainerReply.Answered -> {
                // With no plan sent there is nothing to have followed, whatever the model judged
                // (design question 9); a planId whose plan is gone sends none either.
                val feedback = if (question.session.plan == null) reply.value.copy(followed = PlanFollowed.NO_PLAN) else reply.value
                val answered = saved.copy(feedback = feedback, feedbackAtMillis = now(), model = reply.model)
                store.putReview(answered)
                if (planId != null) store.unkeep(planId)
                Reviewed.WithFeedback(answered)
            }
        }
    }
```

  (`question.session.plan` is null exactly when the old `plan` was: `session(workout, review, plan?.plan)`.)

```kotlin
    /** D93: one ask; the answer is stored OFFERED. The plan would start on the Monday keeping it today gives (D94). */
    suspend fun evaluate(ask: ProgrammeAsk): Evaluated =
        when (val reply = trainer.evaluate(request(evaluateQuestion(ask)))) {
            is TrainerReply.Failed -> Evaluated.Failed(reply.failure)
            is TrainerReply.Answered -> {
                val programme = Programme(0, now(), ask, reply.value.evaluation, reply.value.plan, reply.model)
                Evaluated.Offered(programme.copy(id = programmes.add(programme)))
            }
        }
```

```kotlin
    suspend fun adjust(words: String): Adjusted {
        val running = running() ?: return Adjusted.NotRunning
        val programme = running.programme
        val question = adjustQuestion(running, words)
        return when (val reply = trainer.adjust(request(question))) {
            is TrainerReply.Failed -> Adjusted.Failed(reply.failure)
            is TrainerReply.Answered -> {
                val rest = reply.value
                val thisWeek = PlanWeek(rest.weeks.first().focus, question.tickedThisWeek + rest.weeks.first().sessions)
                val composed = WeeksPlan(rest.title, programme.plan.weeks.take(question.weekIndex) + thisWeek + rest.weeks.drop(1), rest.why)
                val version = Programme(
                    id = 0, createdAtMillis = now(), ask = programme.ask.copy(words = question.words), evaluation = null,
                    plan = composed, model = reply.model, replacesId = programme.id,
                )
                Adjusted.Offered(version.copy(id = programmes.add(version)))
            }
        }
    }
```

  The builders — put them just above `lastEvaluation`:

```kotlin
    // The question builders. The asks above and the workbench (D106) build their questions here and only
    // here, so the workbench's request is the real one by construction. Each reads only.

    /** D86, D96: the plan form's answers, with the running plan's next session. */
    suspend fun planQuestion(answers: PlanAnswers): TrainerQuestion.Plan = TrainerQuestion.Plan(answers, nextPlanned())

    /** D87, D96: [workout] with [review], the single-session plan it names, and the planned session it ticked. */
    suspend fun feedbackQuestion(workout: Workout, review: TrainerReview): TrainerQuestion.Review {
        val plan = review.planId?.let { store.plans(listOf(it))[it] }
        return TrainerRequest.reviewQuestion(workout, review, plan).copy(planned = plannedTickOf(workout.id))
    }

    /** D93, D94, D98: the form, the Monday keeping it today gives, and the last kept evaluation. */
    suspend fun evaluateQuestion(ask: ProgrammeAsk): TrainerQuestion.Evaluate {
        val day = today().toEpochDay()
        return TrainerQuestion.Evaluate(ask, ProgrammeCalendar.startFor(day), lastEvaluation(day))
    }

    /** D97, D105: the running plan's rest and how each week went; null when no plan runs. */
    suspend fun adjustQuestion(words: String): TrainerQuestion.Adjust? = running()?.let { adjustQuestion(it, words) }

    private fun adjustQuestion(running: PlanCard.Running, words: String): TrainerQuestion.Adjust {
        val programme = running.programme
        val index = running.weekIndex.coerceAtLeast(0)
        val week = running.progress.weeks[index]
        val ticked = week.ticks.filter { it.by != null }.map { it.planned }
        return TrainerQuestion.Adjust(
            ask = programme.ask,
            startEpochDay = requireNotNull(programme.startEpochDay),
            plan = programme.plan,
            weekIndex = index,
            doneByWeek = running.progress.weeks.take(index).map { it.done },
            tickedThisWeek = ticked,
            thisWeekMax = week.planned - ticked.size,
            words = words.trim(),
            howItWent = running.progress.weeks.take(index + 1).map { it.outcome() },
        )
    }
```

  `request` becomes public with a default, and the constant public:

```kotlin
    /** Every ask's request (D84), fresh from the stored record. Reads only; shared with the workbench (D106). */
    suspend fun request(question: TrainerQuestion, exceptWorkoutId: Long = NO_WORKOUT): TrainerRequest {
        // … body unchanged …
    }

    companion object {
        /**
         * No workout's id, and no review's either: workouts are numbered from 1, and a review without
         * a workout is restored under -1, -2, … (D88), whose feedback must still count as earlier.
         */
        const val NO_WORKOUT = Long.MIN_VALUE
    }
```

- [ ] **Step 2: Run.** `free -m`; `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.trainer.*" --tests "com.metaself.app.ui.screen.trainer.*" > /tmp/ms-bench-3.log 2>&1; echo "exit $?"`.
  Expected: exit 0, no test changed.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/java/com/metaself/app/data/trainer/AskTheTrainer.kt
git commit -m "refactor(trainer): the asks' question builders and request are public, for the workbench (D106)"
```

---

### Task 4: The sender — one call, no reply shape

**Files:**
- Create: `app/src/main/java/com/metaself/app/domain/trainer/Workbench.kt`
- Create: `app/src/main/java/com/metaself/app/data/ai/OpenAiWorkbench.kt`
- Modify: `app/src/main/java/com/metaself/app/data/ai/TrainerResponse.kt` (`content`: `private` → `internal`)
- Modify: `app/src/main/java/com/metaself/app/di/AiModule.kt`
- Test: `app/src/test/java/com/metaself/app/data/ai/OpenAiWorkbenchTest.kt`

- [ ] **Step 1: Create the port** (`Workbench.kt`) so the test compiles against it:

```kotlin
package com.metaself.app.domain.trainer

import com.metaself.app.domain.ai.EstimateResult

/**
 * D106: what one workbench call came to. [sent] is the request body verbatim — the last one sent, when
 * the call learned and sent again (D57); null when nothing was sent (no key, the day's allowance spent).
 */
sealed interface WorkbenchReply {
    data class Answered(val text: String, val sent: String) : WorkbenchReply
    data class Failed(val failure: EstimateResult, val sent: String?) : WorkbenchReply
}

/** D106: sends one trainer request under [system] as its whole instructions, with no reply shape. */
fun interface WorkbenchSender {
    suspend fun send(system: String, request: TrainerRequest): WorkbenchReply
}
```

- [ ] **Step 2: Write the failing test** `OpenAiWorkbenchTest.kt`:

```kotlin
package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.Rhythm
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.WeekFacts
import com.metaself.app.domain.trainer.WorkbenchReply
import com.metaself.app.domain.trainer.Wish
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** D106 over a local server; no real network call. Every word is invented. */
class OpenAiWorkbenchTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `the reply is the model's text as written, and the body sent is handed back verbatim`() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":${JsonPrimitive(PROSE)}}}]}"""))
        val settings = FakeSettings()

        val reply = workbench(settings = settings).send(SYSTEM, REQUEST) as WorkbenchReply.Answered

        val body = server.takeRequest().body.readUtf8()
        assertThat(reply.text).isEqualTo(PROSE)
        assertThat(reply.sent).isEqualTo(body)
        assertThat(Json.parseToJsonElement(body).jsonObject.keys).doesNotContain("response_format")
        assertThat(body).isEqualTo(TrainerPrompt.workbenchBody(AiSettings().model, SYSTEM, REQUEST, RequestProfile.guess(AiSettings().model)))
        assertThat(settings.calls).isEqualTo(1)
    }

    @Test
    fun `with no key nothing is sent, and the failure is the call's own`() = runTest {
        val reply = workbench(key = null).send(SYSTEM, REQUEST)

        assertThat(reply).isEqualTo(WorkbenchReply.Failed(EstimateResult.NoKey, sent = null))
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `a refusal is the provider's words, with what was sent`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Invented refusal."}}"""))

        val reply = workbench().send(SYSTEM, REQUEST) as WorkbenchReply.Failed

        assertThat(reply.failure).isEqualTo(EstimateResult.Refused("Invented refusal."))
        assertThat(reply.sent).isNotNull()
    }

    @Test
    fun `an answer with no message in it is unreadable, not a crash`() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[]}"""))

        val reply = workbench().send(SYSTEM, REQUEST) as WorkbenchReply.Failed

        assertThat(reply.failure).isInstanceOf(EstimateResult.Unreadable::class.java)
    }

    private fun workbench(key: String? = "a-key", settings: FakeSettings = FakeSettings()) = OpenAiWorkbench(
        keys = FakeKeys(key),
        settings = settings,
        client = OkHttpClient(),
        profiles = FakeRequestProfileStore(),
        baseUrl = server.url("/v1/chat/completions").toString(),
    )

    private class FakeKeys(value: String?) : ApiKeyStore {
        override val key: Flow<String?> = MutableStateFlow(value)
        override suspend fun save(key: String) = Unit
        override suspend fun clear() = Unit
    }

    private class FakeSettings : AiSettingsStore {
        private val state = MutableStateFlow(AiSettings())
        var calls = 0
            private set
        override val settings: Flow<AiSettings> = state
        override suspend fun setModel(model: String) = Unit
        override suspend fun setDailyCeiling(ceiling: Int) = Unit
        override suspend fun recordCall() {
            calls++
            state.value = state.value.copy(usedToday = state.value.usedToday + 1)
        }
    }

    private companion object {
        const val SYSTEM = "Invented instructions."
        const val PROSE = "Invented reply, in prose, not JSON."
        val REQUEST = TrainerRequest(
            question = TrainerQuestion.Plan(PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.NOT_SURE)),
            today = TEST_EPOCH_DAY,
            aboutMe = null,
            sessions = emptyList(),
            weeks = (0 until 6).map { back ->
                WeekFacts(monday = TEST_EPOCH_DAY - 3 - 7L * back, distanceM = null, averageActiveKcal = null, sessions = 0, current = back == 0)
            },
            months = emptyList(),
            weight = null,
            goal = null,
            body = null,
            thisWeek = Rhythm(sessionsSoFar = 0, daysLeft = 3),
            earlierFeedback = emptyList(),
        )
    }
}
```

- [ ] **Step 3: Run to see it fail.** `free -m`; `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.ai.OpenAiWorkbenchTest" > /tmp/ms-bench-4.log 2>&1; echo "exit $?"`. Expected: compile failure, `OpenAiWorkbench` unresolved.

- [ ] **Step 4: Implement.** In `TrainerResponse.kt` change `private fun content(body: String)` to
  `internal fun content(body: String)`. Create `OpenAiWorkbench.kt`:

```kotlin
package com.metaself.app.data.ai

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.WorkbenchReply
import com.metaself.app.domain.trainer.WorkbenchSender
import okhttp3.OkHttpClient

/**
 * The workbench's sender (D106): [TrainerPrompt.workbenchBody] over the shared [OpenAiCall] — the key, the
 * day's ceiling, the counting, the failures and the learning are the trainer's. The reply is the model's
 * text as written; nothing is parsed or checked, and nothing is stored but what [OpenAiCall] itself keeps
 * (the day's count, and the profile that answered — design choice 3 of the plan).
 *
 * The base URL is a parameter so a test can point it at a local server. **No test makes a real network call.**
 */
class OpenAiWorkbench(
    keys: ApiKeyStore,
    settings: AiSettingsStore,
    client: OkHttpClient,
    profiles: RequestProfileStore,
    baseUrl: String = OpenAiCall.OPENAI_URL,
) : WorkbenchSender {

    private val call = OpenAiCall(keys, settings, client, profiles, baseUrl)

    override suspend fun send(system: String, request: TrainerRequest): WorkbenchReply {
        var sent: String? = null
        val outcome = call.send { model, profile -> TrainerPrompt.workbenchBody(model, system, request, profile).also { sent = it } }
        return when (outcome) {
            is OpenAiCall.Outcome.Failed -> WorkbenchReply.Failed(outcome.failure, sent)
            is OpenAiCall.Outcome.Body -> {
                val text = TrainerResponse.content(outcome.text)
                if (text == null) {
                    WorkbenchReply.Failed(EstimateResult.Unreadable(NO_MESSAGE, outcome.text), sent)
                } else {
                    call.remember(outcome)
                    WorkbenchReply.Answered(text, checkNotNull(sent))
                }
            }
        }
    }

    private companion object {
        const val NO_MESSAGE = "the answer held no message"
    }
}
```

  In `AiModule.kt`, beside `provideTrainer` (add imports `com.metaself.app.data.ai.OpenAiWorkbench`,
  `com.metaself.app.domain.trainer.WorkbenchSender`):

```kotlin
    /** The trainer's instructions workbench (D106): the same key, ceiling, client and profiles as the trainer. */
    @Provides
    @Singleton
    fun provideWorkbenchSender(
        keys: ApiKeyStore,
        settings: AiSettingsStore,
        client: OkHttpClient,
        profiles: RequestProfileStore,
    ): WorkbenchSender = OpenAiWorkbench(keys, settings, client, profiles)
```

- [ ] **Step 5: Run.** Same command as Step 3, plus `--tests "com.metaself.app.data.ai.TrainerResponseTest"`. Expected: exit 0.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/metaself/app/domain/trainer/Workbench.kt \
  app/src/main/java/com/metaself/app/data/ai/OpenAiWorkbench.kt \
  app/src/main/java/com/metaself/app/data/ai/TrainerResponse.kt \
  app/src/main/java/com/metaself/app/di/AiModule.kt \
  app/src/test/java/com/metaself/app/data/ai/OpenAiWorkbenchTest.kt
git commit -m "feat(trainer): the workbench's sender, over the shared call, with no reply shape (D106)"
```

---

### Task 5: The workbench — the real request, and nothing written

**Files:**
- Create: `app/src/main/java/com/metaself/app/data/trainer/TrainerWorkbench.kt`
- Create: `app/src/test/java/com/metaself/app/data/trainer/RecordingWorkbenchSender.kt`
- Test: `app/src/test/java/com/metaself/app/data/trainer/TrainerWorkbenchTest.kt`

- [ ] **Step 1: The recording sender** (shared with Task 7's test):

```kotlin
package com.metaself.app.data.trainer

import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.WorkbenchReply
import com.metaself.app.domain.trainer.WorkbenchSender

/** Keeps every (instructions, request) it is given, and answers [reply]. */
class RecordingWorkbenchSender(var reply: WorkbenchReply = WorkbenchReply.Answered("Invented reply.", "{\"invented\":true}")) : WorkbenchSender {
    val sent = mutableListOf<Pair<String, TrainerRequest>>()

    override suspend fun send(system: String, request: TrainerRequest): WorkbenchReply {
        sent += system to request
        return reply
    }
}
```

- [ ] **Step 2: Write the failing test** `TrainerWorkbenchTest.kt`:

```kotlin
package com.metaself.app.data.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.ai.TrainerPrompt
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.weight.InMemoryWeightRepository
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.EvaluationAndPlan
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlanStep
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.PlannedTick
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.domain.trainer.Wish
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * D106. For each path the workbench's request equals the one the real ask sends for the same inputs and
 * record — built by the same builders, so this cannot pass by coincidence — and a run writes nothing.
 * Today is [TEST_EPOCH_DAY]; every figure and word is invented.
 */
class TrainerWorkbenchTest {

    private val record = FakeMovementRecord()
    private val store = FakeTrainerStore()
    private val trainer = FakeTrainer()
    private val programmes = FakeProgrammeStore()
    private val sender = RecordingWorkbenchSender()
    private val ask = AskTheTrainer(
        record, store, InMemoryWeightRepository(), FakeProfileRepository(aProfile()), trainer, InMemoryAboutMeStore(), programmes,
        Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }, Now { NOW }, CurrentYear { TEST_YEAR },
    )
    private val workbench = TrainerWorkbench(ask, store, record, Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }, sender)

    @BeforeEach
    fun setUp() {
        store.workouts.value = listOf(walk(id = 1, day = TEST_EPOCH_DAY))
        record.workouts.value = store.workouts.value
    }

    @Test
    fun `feedback's request is the real one, from the stored review, which is not saved again`() = runTest {
        val planId = storedReview()
        planRunning()

        val bench = workbench.request(TrainerWorkbench.Inputs.Feedback(1))!!
        trainer.feedback += TrainerReply.Answered(FEEDBACK, "a-model")
        ask.save(1, Felt.HARD, "Invented words.", planId, withFeedback = true)

        assertThat(bench).isEqualTo(trainer.asked.single())
        assertThat(TrainerPrompt.userMessage(bench)).isEqualTo(TrainerPrompt.userMessage(trainer.asked.single()))
        assertThat((bench.question as TrainerQuestion.Review).planned).isEqualTo(PlannedTick(1, 2, WALK_30))
    }

    @Test
    fun `the plan form's request is the real one, with the next planned session`() = runTest {
        planRunning()

        val bench = workbench.request(TrainerWorkbench.Inputs.Plan(ANSWERS))
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")
        ask.suggest(ANSWERS)

        assertThat(bench).isEqualTo(trainer.asked.single())
    }

    @Test
    fun `the evaluation's request is the real one, with the start and the last evaluation`() = runTest {
        planRunning()
        val form = ProgrammeAsk(2, 2, "Invented words.")

        val bench = workbench.request(TrainerWorkbench.Inputs.Evaluate(form))
        trainer.evaluations += TrainerReply.Answered(EvaluationAndPlan(EVALUATION, WEEKS), "a-model")
        ask.evaluate(form)

        assertThat(bench).isEqualTo(trainer.asked.single())
    }

    @Test
    fun `the adjustment's request is the real one`() = runTest {
        planRunning()

        val bench = workbench.request(TrainerWorkbench.Inputs.Adjust(" Invented words. "))
        trainer.adjustments += TrainerReply.Answered(WEEKS, "a-model")
        ask.adjust(" Invented words. ")

        assertThat(bench).isEqualTo(trainer.asked.single())
    }

    @Test
    fun `with no plan running, adjusting sends nothing and says so`() = runTest {
        assertThat(workbench.send(TrainerWorkbench.Inputs.Adjust("Invented."), SYSTEM)).isEqualTo(TrainerWorkbench.Run.NotRunning)
        assertThat(sender.sent).isEmpty()
    }

    @Test
    fun `a session no longer in the record sends nothing`() = runTest {
        assertThat(workbench.send(TrainerWorkbench.Inputs.Feedback(99), SYSTEM)).isEqualTo(TrainerWorkbench.Run.SessionGone)
        assertThat(sender.sent).isEmpty()
    }

    /** Every write of both stores throws from here on; a workbench write would fail the test. */
    @Test
    fun `a run on every path writes nothing, and sends the given instructions with the real request`() = runTest {
        storedReview()
        planRunning()
        val plans = store.plans.value
        val reviews = store.reviews.value
        val rows = programmes.rows.value
        val confirmations = programmes.confirmationRows.value
        store.failing = setOf("addPlan", "keep", "unkeep", "putReview")
        programmes.failing = true

        val inputs = listOf(
            TrainerWorkbench.Inputs.Feedback(1),
            TrainerWorkbench.Inputs.Plan(ANSWERS),
            TrainerWorkbench.Inputs.Evaluate(ProgrammeAsk(2, 2)),
            TrainerWorkbench.Inputs.Adjust("Invented words."),
        )
        inputs.forEach { assertThat(workbench.send(it, SYSTEM)).isInstanceOf(TrainerWorkbench.Run.Replied::class.java) }

        assertThat(store.plans.value).isEqualTo(plans)
        assertThat(store.reviews.value).isEqualTo(reviews)
        assertThat(programmes.rows.value).isEqualTo(rows)
        assertThat(programmes.confirmationRows.value).isEqualTo(confirmations)
        assertThat(sender.sent.map { it.first }).containsExactly(SYSTEM, SYSTEM, SYSTEM, SYSTEM)
        assertThat(sender.sent[1].second).isEqualTo(workbench.request(TrainerWorkbench.Inputs.Plan(ANSWERS)))
    }

    @Test
    fun `the sessions offered are the visible, counted ones of the 42 days, newest first`() = runTest {
        record.workouts.value = listOf(
            walk(id = 1, day = TEST_EPOCH_DAY),
            walk(id = 2, day = TEST_EPOCH_DAY - 41),
            walk(id = 3, day = TEST_EPOCH_DAY - 42),
            walk(id = 4, day = TEST_EPOCH_DAY - 1).copy(hidden = true),
            walk(id = 5, day = TEST_EPOCH_DAY - 2).copy(counted = false),
        )

        assertThat(workbench.sessions().map { it.id }).containsExactly(1L, 2L).inOrder()
    }

    @Test
    fun `the app's instructions are the prompt's own`() {
        TrainerPath.entries.forEach { assertThat(workbench.appInstructions(it)).isEqualTo(TrainerPrompt.instructions(it)) }
    }

    /** A stored review of session 1 naming a stored single-session plan; returns the plan's id. */
    private suspend fun storedReview(): Long {
        val planId = store.addPlan(TrainerPlan(0, NOW - HOUR, ANSWERS, PLAN, "a-model", kept = false))
        store.putReview(TrainerReview(workoutId = 1, planId = planId, felt = Felt.HARD, words = "Invented words."))
        return planId
    }

    /** A two-week plan, evaluated on a Sunday at noon and kept to run from this week's Monday. */
    private suspend fun planRunning() {
        val id = programmes.add(Programme(0, (MONDAY - 15) * DAY + 12 * HOUR, ProgrammeAsk(2, 2), EVALUATION, WEEKS, "a-model"))
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
    }

    private fun walk(id: Long, day: Long) = Workout(
        id = id, epochDay = day, startedAtMillis = day * DAY + 8 * HOUR, durationMinutes = 40,
        kind = WorkoutKind.WALK, title = null, distanceM = 4_000, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
        const val NOW = TEST_EPOCH_DAY * DAY + 15 * HOUR
        const val SYSTEM = "Invented instructions."
        val MONDAY = TEST_EPOCH_DAY - 3
        val ANSWERS = PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.NOT_SURE, "Invented words.")
        val PLAN = SessionPlan(
            "Steady walk",
            listOf(PlanStep(0, 10, "Warm up", "easy pace"), PlanStep(10, 35, "Walk", "zone 2"), PlanStep(35, 45, "Cool down", "")),
            "Invented reason.",
        )
        val FEEDBACK = Feedback("Invented headline.", "Invented.", "Invented.", "Invented.", "Invented.", PlanFollowed.YES)
        val WALK_30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Easy walk")
        val WEEKS = WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(WALK_30, WALK_30)) }, "Invented reason.")
        val EVALUATION = Evaluation("Invented headline.", "Invented.", "Invented.", "")
    }
}
```

  Add `import com.metaself.app.domain.trainer.TrainerPath`.

- [ ] **Step 3: Run to see it fail.** `free -m`; `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.trainer.TrainerWorkbenchTest" > /tmp/ms-bench-5.log 2>&1; echo "exit $?"`. Expected: compile failure, `TrainerWorkbench` unresolved.

- [ ] **Step 4: Implement** `TrainerWorkbench.kt`:

```kotlin
package com.metaself.app.data.trainer

import com.metaself.app.data.ai.TrainerPrompt
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.time.Today
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.TrainerPath
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.WorkbenchReply
import com.metaself.app.domain.trainer.WorkbenchSender
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * D106: the trainer's four paths, tried under other instructions on the real record. The request is the
 * real ask's — built by [AskTheTrainer]'s own question builders and request — and this class **only
 * reads**: no review is saved first, no answer is stored, kept, ticked or offered. Only when the page asks.
 */
class TrainerWorkbench @Inject constructor(
    private val ask: AskTheTrainer,
    private val store: TrainerStore,
    private val record: MovementRecord,
    private val today: Today,
    private val sender: WorkbenchSender,
) {

    /** What each path's real screen asks. */
    sealed interface Inputs {
        data class Feedback(val workoutId: Long) : Inputs
        data class Plan(val answers: PlanAnswers) : Inputs
        data class Evaluate(val ask: ProgrammeAsk) : Inputs
        data class Adjust(val words: String) : Inputs
    }

    sealed interface Run {
        data class Replied(val reply: WorkbenchReply) : Run

        /** Adjust with no plan running: nothing was sent. */
        data object NotRunning : Run

        /** The session left the record: nothing was sent. */
        data object SessionGone : Run
    }

    /** The sessions feedback can be asked about: the visible, counted ones of the 42 days a request carries, newest first. */
    suspend fun sessions(): List<Workout> {
        val day = today().toEpochDay()
        return record.observeWorkouts(TrainerRequest.firstDay(day), day).first()
            .filter { !it.hidden && it.counted }
            .sortedByDescending { it.startedAtMillis }
    }

    suspend fun planRuns(): Boolean = ask.running() != null

    /** The system message [path] sends today, exactly. */
    fun appInstructions(path: TrainerPath): String = TrainerPrompt.instructions(path)

    /** The request the real ask would send for [inputs]; null when adjusting with no plan, or the session is gone. */
    suspend fun request(inputs: Inputs): TrainerRequest? = when (inputs) {
        is Inputs.Feedback -> store.workout(inputs.workoutId)?.let { workout ->
            // The stored review supplies felt, words and its plan; it is never saved first, as the real ask does.
            val review = store.reviewOf(workout.id) ?: TrainerReview(workoutId = workout.id, planId = null, felt = null, words = null)
            ask.request(ask.feedbackQuestion(workout, review), exceptWorkoutId = workout.id)
        }
        is Inputs.Plan -> ask.request(ask.planQuestion(inputs.answers))
        is Inputs.Evaluate -> ask.request(ask.evaluateQuestion(inputs.ask))
        is Inputs.Adjust -> ask.adjustQuestion(inputs.words)?.let { ask.request(it) }
    }

    /** Sends [inputs]' real request with [system] as the whole system message. Throws only as the stores' reads do. */
    suspend fun send(inputs: Inputs, system: String): Run {
        val request = request(inputs) ?: return if (inputs is Inputs.Adjust) Run.NotRunning else Run.SessionGone
        return Run.Replied(sender.send(system, request))
    }
}
```

- [ ] **Step 5: Run.** Same command as Step 3. Expected: exit 0. If an equality fails, print both requests'
  `question` — a difference means a builder is not shared; fix the sharing, never the test.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/metaself/app/data/trainer/TrainerWorkbench.kt \
  app/src/test/java/com/metaself/app/data/trainer/RecordingWorkbenchSender.kt \
  app/src/test/java/com/metaself/app/data/trainer/TrainerWorkbenchTest.kt
git commit -m "feat(trainer): the workbench builds the real request and writes nothing (D106)"
```

---

### Task 6: Instruction files

**Files:**
- Create: `app/src/main/java/com/metaself/app/data/trainer/InstructionFiles.kt`
- Modify: `app/src/main/java/com/metaself/app/di/DataModule.kt`

No unit test: the implementation is three ContentResolver calls, exercised on the phone; the view model's
test uses a fake of the interface.

- [ ] **Step 1: Create.**

```kotlin
package com.metaself.app.data.trainer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * D106: the instructions file picked with Android's document picker, read or written as plain text — so a
 * file on Drive works, and the app needs no storage permission. Uris are strings so the page's view model
 * is tested without Android. Nothing here keeps a copy.
 */
interface InstructionFiles {
    suspend fun read(uri: String): String?
    suspend fun write(uri: String, text: String): Boolean
    suspend fun nameOf(uri: String): String?
}

@Singleton
class ContentResolverInstructionFiles @Inject constructor(
    @ApplicationContext private val context: Context,
) : InstructionFiles {

    override suspend fun read(uri: String): String? = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes().decodeToString() } }.getOrNull()
    }

    /** Truncated first ("wt"), as the backup export is, so a shorter text leaves no tail behind. */
    override suspend fun write(uri: String, text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(Uri.parse(uri), "wt")?.use { it.write(text.toByteArray()) } != null
        }.getOrDefault(false)
    }

    override suspend fun nameOf(uri: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
    }
}
```

- [ ] **Step 2: Bind it** in `DataModule.kt` beside `provideBackupFiles` (imports
  `com.metaself.app.data.trainer.InstructionFiles`, `com.metaself.app.data.trainer.ContentResolverInstructionFiles`):

```kotlin
    @Provides
    @Singleton
    fun provideInstructionFiles(files: ContentResolverInstructionFiles): InstructionFiles = files
```

- [ ] **Step 3: Compile.** `free -m`; `~/bin/gradlew-safe :app:compileDebugKotlin > /tmp/ms-bench-6.log 2>&1; echo "exit $?"`. Expected: exit 0.

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/com/metaself/app/data/trainer/InstructionFiles.kt app/src/main/java/com/metaself/app/di/DataModule.kt
git commit -m "feat(trainer): read, write and name a picked instructions file (D106)"
```

---

### Task 7: Wording and the page's view model

**Files:**
- Create: `app/src/main/java/com/metaself/app/ui/trainer/WorkbenchWording.kt`
- Create: `app/src/main/java/com/metaself/app/ui/screen/settings/TrainerWorkbenchViewModel.kt`
- Test: `app/src/test/java/com/metaself/app/ui/screen/settings/TrainerWorkbenchViewModelTest.kt`

- [ ] **Step 1: Wording** (no logic worth a test beyond the view model's):

```kotlin
package com.metaself.app.ui.trainer

import com.metaself.app.domain.trainer.TrainerPath

/** Every sentence on "Test the trainer's instructions" (D106). */
object WorkbenchWording {
    const val INTRO = "A testing tool: try other instructions on your real record. Nothing here is stored, " +
        "and the trainer's own screens are unchanged."
    const val APP_OWN = "the app's own instructions"
    const val LOAD = "Load instructions from a file"
    const val USE_APP_OWN = "Use the app's own instructions"
    const val SAVE_APP_OWN = "Save the app's instructions to a file"
    const val SEND = "Send"
    const val SENDING = "Sending…"
    const val PRIVACY = "On Send, what the chosen path sends goes to OpenAI with your key, under the instructions " +
        "named above. One of today's AI requests. Nothing is kept."
    const val COPY_REPLY = "Copy reply"
    const val COPY_SENT = "Copy what was sent"
    const val NO_PLAN = "No plan is running."
    const val NO_SESSIONS = "No sessions in the last 42 days."
    const val SESSION_GONE = "That session is no longer in the record."
    const val RECORD_UNREADABLE = "The record could not be read. Nothing was sent."
    const val COULD_NOT_READ = "The file could not be read, or is empty. Nothing changed."
    const val UNNAMED = "the file you picked"
    const val SAVED = "Saved."
    const val COULD_NOT_WRITE = "The file could not be written."

    fun path(path: TrainerPath): String = when (path) {
        TrainerPath.FEEDBACK -> "Feedback on a session"
        TrainerPath.PLAN -> "Plan my next session"
        TrainerPath.EVALUATE -> "Review and plan the weeks ahead"
        TrainerPath.ADJUST -> "Change the rest of my plan"
    }

    /** The line that always says which instructions Send will use. */
    fun willSend(fileName: String?): String = "Will send: ${fileName ?: APP_OWN}"

    /** The suggested name when saving the app's instructions for [path]. */
    fun fileName(path: TrainerPath): String = "trainer-instructions-${path.name.lowercase()}.txt"
}
```

- [ ] **Step 2: Write the failing test** `TrainerWorkbenchViewModelTest.kt` (JUnit 5):

```kotlin
package com.metaself.app.ui.screen.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.ai.TrainerPrompt
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.AskTheTrainer
import com.metaself.app.data.trainer.FakeProgrammeStore
import com.metaself.app.data.trainer.FakeTrainer
import com.metaself.app.data.trainer.FakeTrainerStore
import com.metaself.app.data.trainer.InMemoryAboutMeStore
import com.metaself.app.data.trainer.InstructionFiles
import com.metaself.app.data.trainer.RecordingWorkbenchSender
import com.metaself.app.data.trainer.TrainerWorkbench
import com.metaself.app.data.weight.InMemoryWeightRepository
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerPath
import com.metaself.app.domain.trainer.WorkbenchReply
import com.metaself.app.domain.trainer.Wish
import com.metaself.app.ui.screen.trainer.PlanSessionViewModel
import com.metaself.app.ui.trainer.WorkbenchWording
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** D106's page state. Every word and file name is invented. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainerWorkbenchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sender = RecordingWorkbenchSender()
    private val files = Files()
    private val today = Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the app's own instructions are sent until a file is loaded, then the file's, and back again`() = runTest {
        val vm = opened()
        vm.pickPath(TrainerPath.PLAN)
        vm.changePlan(FORM)
        assertThat(vm.state.value.fileName).isNull()

        vm.send(); advanceUntilIdle()
        vm.load("content://invented/one"); advanceUntilIdle()
        vm.send(); advanceUntilIdle()
        vm.useAppOwn()
        vm.send(); advanceUntilIdle()

        assertThat(sender.sent.map { it.first }).containsExactly(
            TrainerPrompt.instructions(TrainerPath.PLAN), LOADED, TrainerPrompt.instructions(TrainerPath.PLAN),
        ).inOrder()
    }

    @Test
    fun `a loaded file is named on the page`() = runTest {
        val vm = opened()

        vm.load("content://invented/one"); advanceUntilIdle()

        assertThat(vm.state.value.fileName).isEqualTo("invented.txt")
    }

    @Test
    fun `an empty or unreadable file is refused and nothing changes`() = runTest {
        val vm = opened()
        files.text = "   "

        vm.load("content://invented/one"); advanceUntilIdle()

        assertThat(vm.state.value.fileName).isNull()
        assertThat(vm.state.value.fileMessage).isEqualTo(WorkbenchWording.COULD_NOT_READ)
    }

    @Test
    fun `the reply and the body sent are shown as they came`() = runTest {
        sender.reply = WorkbenchReply.Answered("Invented reply.", "{\"invented\":1}")
        val vm = opened()
        vm.pickPath(TrainerPath.PLAN)
        vm.changePlan(FORM)

        vm.send(); advanceUntilIdle()

        assertThat(vm.state.value.reply).isEqualTo("Invented reply.")
        assertThat(vm.state.value.sent).isEqualTo("{\"invented\":1}")
        assertThat(vm.state.value.sending).isFalse()
    }

    @Test
    fun `a failed call keeps its failure for the trainer's own wording`() = runTest {
        sender.reply = WorkbenchReply.Failed(EstimateResult.NoKey, sent = null)
        val vm = opened()
        vm.pickPath(TrainerPath.PLAN)
        vm.changePlan(FORM)

        vm.send(); advanceUntilIdle()

        assertThat(vm.state.value.failure).isEqualTo(EstimateResult.NoKey)
        assertThat(vm.state.value.reply).isNull()
        assertThat(vm.state.value.sent).isNull()
    }

    @Test
    fun `each path can be sent only with its inputs, and adjusting only while a plan runs`() = runTest {
        val vm = opened()

        assertThat(vm.state.value.canSend).isFalse() // feedback, no session picked
        vm.pickPath(TrainerPath.PLAN)
        assertThat(vm.state.value.canSend).isFalse()
        vm.changePlan(FORM)
        assertThat(vm.state.value.canSend).isTrue()
        vm.pickPath(TrainerPath.ADJUST)
        assertThat(vm.state.value.planRuns).isFalse()
        assertThat(vm.state.value.canSend).isFalse()
    }

    @Test
    fun `saving writes the chosen path's instructions, exactly`() = runTest {
        val vm = opened()
        vm.pickPath(TrainerPath.EVALUATE)

        vm.saveAppInstructionsTo("content://invented/out"); advanceUntilIdle()

        assertThat(files.written).containsExactly("content://invented/out" to TrainerPrompt.instructions(TrainerPath.EVALUATE))
        assertThat(vm.state.value.fileMessage).isEqualTo(WorkbenchWording.SAVED)
    }

    private fun kotlinx.coroutines.test.TestScope.opened(): TrainerWorkbenchViewModel {
        val record = FakeMovementRecord()
        val store = FakeTrainerStore()
        val ask = AskTheTrainer(
            record, store, InMemoryWeightRepository(), FakeProfileRepository(aProfile()), FakeTrainer(), InMemoryAboutMeStore(),
            FakeProgrammeStore(), today, Now { TEST_EPOCH_DAY * 86_400_000L }, CurrentYear { TEST_YEAR },
        )
        return TrainerWorkbenchViewModel(TrainerWorkbench(ask, store, record, today, sender), files, today)
            .also { advanceUntilIdle() }
    }

    private class Files : InstructionFiles {
        var text: String? = LOADED
        val written = mutableListOf<Pair<String, String>>()
        override suspend fun read(uri: String): String? = text
        override suspend fun write(uri: String, text: String): Boolean {
            written += uri to text
            return true
        }
        override suspend fun nameOf(uri: String): String = "invented.txt"
    }

    private companion object {
        const val LOADED = "Invented instructions: a short note from a coach."
        val FORM = PlanSessionViewModel.Form(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.NOT_SURE)
    }
}
```

- [ ] **Step 3: Run to see it fail.** `free -m`; `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.screen.settings.TrainerWorkbenchViewModelTest" > /tmp/ms-bench-7.log 2>&1; echo "exit $?"`. Expected: compile failure.

- [ ] **Step 4: Implement** `TrainerWorkbenchViewModel.kt`:

```kotlin
package com.metaself.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.InstructionFiles
import com.metaself.app.data.trainer.TrainerWorkbench
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.trainer.TrainerPath
import com.metaself.app.domain.trainer.WorkbenchReply
import com.metaself.app.ui.screen.trainer.EvaluatePlanViewModel
import com.metaself.app.ui.screen.trainer.PlanSessionViewModel
import com.metaself.app.ui.trainer.WorkbenchWording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Test the trainer's instructions" (D106), on the page's own back-stack entry: the loaded text lives in
 * [instructions] and goes when the page is left. Nothing is stored; the forms are the trainer screens' own.
 */
@HiltViewModel
class TrainerWorkbenchViewModel @Inject constructor(
    private val workbench: TrainerWorkbench,
    private val files: InstructionFiles,
    private val today: Today,
) : ViewModel() {

    /**
     * @property fileName the loaded file's name; null sends the app's own instructions.
     * @property sent the last request body, verbatim, for Copy what was sent.
     * @property notice a sentence for a run that sent nothing (no plan, session gone, record unreadable).
     */
    data class State(
        val loaded: Boolean = false,
        val today: Long = 0,
        val path: TrainerPath = TrainerPath.FEEDBACK,
        val sessions: List<Workout> = emptyList(),
        val workoutId: Long? = null,
        val plan: PlanSessionViewModel.Form = PlanSessionViewModel.Form(),
        val evaluate: EvaluatePlanViewModel.Form = EvaluatePlanViewModel.Form(),
        val adjustWords: String = "",
        val planRuns: Boolean = false,
        val fileName: String? = null,
        val fileMessage: String? = null,
        val sending: Boolean = false,
        val reply: String? = null,
        val sent: String? = null,
        val failure: EstimateResult? = null,
        val notice: String? = null,
    ) {
        val inputs: TrainerWorkbench.Inputs?
            get() = when (path) {
                TrainerPath.FEEDBACK -> workoutId?.let { TrainerWorkbench.Inputs.Feedback(it) }
                TrainerPath.PLAN -> plan.answers()?.let { TrainerWorkbench.Inputs.Plan(it) }
                TrainerPath.EVALUATE -> evaluate.ask()?.let { TrainerWorkbench.Inputs.Evaluate(it) }
                TrainerPath.ADJUST -> if (planRuns) TrainerWorkbench.Inputs.Adjust(adjustWords) else null
            }

        val canSend: Boolean get() = loaded && !sending && inputs != null
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** The loaded file's text, for this visit only; null sends the app's own. */
    private var instructions: String? = null

    init {
        viewModelScope.launch { read() }
    }

    private suspend fun read() {
        val sessions = orNull { workbench.sessions() }.orEmpty()
        val runs = orNull { workbench.planRuns() } ?: false
        _state.update { it.copy(loaded = true, today = today().toEpochDay(), sessions = sessions, planRuns = runs) }
    }

    /** A new path clears the last reply, so a reply is never shown under a path that did not ask it. */
    fun pickPath(path: TrainerPath) = _state.update {
        it.copy(path = path, reply = null, sent = null, failure = null, notice = null)
    }

    fun pickSession(workoutId: Long) = _state.update { it.copy(workoutId = workoutId) }
    fun changePlan(form: PlanSessionViewModel.Form) = _state.update { it.copy(plan = form) }
    fun changeEvaluate(form: EvaluatePlanViewModel.Form) = _state.update { it.copy(evaluate = form) }
    fun changeAdjustWords(words: String) = _state.update { it.copy(adjustWords = words) }

    fun load(uri: String) {
        viewModelScope.launch {
            val text = files.read(uri)
            if (text.isNullOrBlank()) {
                _state.update { it.copy(fileMessage = WorkbenchWording.COULD_NOT_READ) }
                return@launch
            }
            instructions = text
            val name = files.nameOf(uri) ?: WorkbenchWording.UNNAMED
            _state.update { it.copy(fileName = name, fileMessage = null) }
        }
    }

    fun useAppOwn() {
        instructions = null
        _state.update { it.copy(fileName = null, fileMessage = null) }
    }

    fun saveAppInstructionsTo(uri: String) {
        val text = workbench.appInstructions(_state.value.path)
        viewModelScope.launch {
            val written = files.write(uri, text)
            _state.update { it.copy(fileMessage = if (written) WorkbenchWording.SAVED else WorkbenchWording.COULD_NOT_WRITE) }
        }
    }

    fun send() {
        val current = _state.value
        if (!current.canSend) return
        val inputs = current.inputs ?: return
        val system = instructions ?: workbench.appInstructions(current.path)
        _state.update { it.copy(sending = true, reply = null, sent = null, failure = null, notice = null) }
        viewModelScope.launch {
            val run = orNull { workbench.send(inputs, system) }
            _state.update { s ->
                when (run) {
                    is TrainerWorkbench.Run.Replied -> when (val reply = run.reply) {
                        is WorkbenchReply.Answered -> s.copy(sending = false, reply = reply.text, sent = reply.sent)
                        is WorkbenchReply.Failed -> s.copy(sending = false, failure = reply.failure, sent = reply.sent)
                    }
                    TrainerWorkbench.Run.NotRunning -> s.copy(sending = false, planRuns = false, notice = WorkbenchWording.NO_PLAN)
                    TrainerWorkbench.Run.SessionGone -> s.copy(sending = false, notice = WorkbenchWording.SESSION_GONE)
                    null -> s.copy(sending = false, notice = WorkbenchWording.RECORD_UNREADABLE)
                }
            }
        }
    }

    /** A store's read that throws is null here; cancellation is never swallowed. */
    private suspend fun <T> orNull(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (unreadable: Exception) {
        null
    }
}
```

- [ ] **Step 5: Run.** Same as Step 3. Expected: exit 0.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/metaself/app/ui/trainer/WorkbenchWording.kt \
  app/src/main/java/com/metaself/app/ui/screen/settings/TrainerWorkbenchViewModel.kt \
  app/src/test/java/com/metaself/app/ui/screen/settings/TrainerWorkbenchViewModelTest.kt
git commit -m "feat(trainer): the workbench page's state — path, inputs, file, send (D106)"
```

---

### Task 8: The page

**Files:**
- Modify: `app/src/main/java/com/metaself/app/ui/screen/trainer/PlanSessionScreen.kt` (`private fun <T> ChoiceRow` → `internal fun <T> ChoiceRow`)
- Create: `app/src/main/java/com/metaself/app/ui/screen/settings/TrainerWorkbenchPage.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/metaself/app/ui/screen/settings/TrainerWorkbenchPageRenderTest.kt`

- [ ] **Step 1: Strings** (beside the other `settings_page_*`):

```xml
    <string name="settings_page_trainer_instructions">Test the trainer\'s instructions</string>
    <string name="workbench_row_path">What to test</string>
    <string name="workbench_row_session">Session</string>
```

- [ ] **Step 2: Write the failing render test** (JUnit 4, Robolectric):

```kotlin
package com.metaself.app.ui.screen.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.TrainerPath
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.trainer.TrainerWording
import com.metaself.app.ui.trainer.WorkbenchWording
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * D106's page drawn: what is on it and in which order, and that the copy buttons call back. Nothing about
 * size (`CLAUDE.md`). Every word and file name is invented. JUnit 4 because Robolectric's runner is.
 */
@RunWith(RobolectricTestRunner::class)
class TrainerWorkbenchPageRenderTest {

    private val render = ComposeRender()
    private val copied = mutableListOf<String>()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `the four paths, then which instructions will be sent, then Send`() {
        val texts = page(TrainerWorkbenchViewModel.State(loaded = true))

        assertThat(texts).containsAtLeast(
            "Test the trainer's instructions",
            "Feedback on a session", "Plan my next session", "Review and plan the weeks ahead", "Change the rest of my plan",
            "Will send: the app's own instructions", WorkbenchWording.LOAD, WorkbenchWording.SAVE_APP_OWN,
            WorkbenchWording.SEND, WorkbenchWording.PRIVACY,
        ).inOrder()
        assertThat(texts).contains(WorkbenchWording.NO_SESSIONS)
        assertThat(texts).doesNotContain(WorkbenchWording.USE_APP_OWN)
    }

    @Test
    fun `a loaded file is named, and the way back to the app's own is offered`() {
        val texts = page(TrainerWorkbenchViewModel.State(loaded = true, fileName = "invented.txt"))

        assertThat(texts).containsAtLeast("Will send: invented.txt", WorkbenchWording.USE_APP_OWN).inOrder()
    }

    @Test
    fun `changing the plan with none running says so, and Send is off`() {
        val texts = page(TrainerWorkbenchViewModel.State(loaded = true, path = TrainerPath.ADJUST, planRuns = false))

        assertThat(texts).contains("No plan is running.")
        assertThat(render.isEnabled(WorkbenchWording.SEND)).isFalse()
    }

    @Test
    fun `the reply is shown as written, and both copies call back`() {
        val texts = page(TrainerWorkbenchViewModel.State(loaded = true, path = TrainerPath.PLAN, reply = "Invented reply.", sent = "{}"))

        assertThat(texts).containsAtLeast("Invented reply.", WorkbenchWording.COPY_REPLY, WorkbenchWording.COPY_SENT).inOrder()
        render.click(WorkbenchWording.COPY_REPLY)
        render.click(WorkbenchWording.COPY_SENT)
        assertThat(copied).containsExactly("reply", "sent").inOrder()
    }

    @Test
    fun `a failure is worded as the trainer's, with no reply to copy`() {
        val texts = page(TrainerWorkbenchViewModel.State(loaded = true, path = TrainerPath.PLAN, failure = EstimateResult.NoKey))

        assertThat(texts).contains(TrainerWording.failure(EstimateResult.NoKey))
        assertThat(texts).containsNoneOf(WorkbenchWording.COPY_REPLY, WorkbenchWording.COPY_SENT)
    }

    private fun page(state: TrainerWorkbenchViewModel.State): List<String> = render.texts(heightPx = TALL) {
        TrainerWorkbenchPage(
            state = state,
            onPath = {}, onSession = {}, onPlan = {}, onEvaluate = {}, onAdjustWords = {},
            onLoad = {}, onUseAppOwn = {}, onSaveAppOwn = {}, onSend = {},
            onCopyReply = { copied += "reply" }, onCopySent = { copied += "sent" },
            onBack = {},
        )
    }

    private companion object {
        const val TALL = 20_000
    }
}
```

- [ ] **Step 3: Run to see it fail.** `free -m`; `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.screen.settings.TrainerWorkbenchPageRenderTest" > /tmp/ms-bench-8.log 2>&1; echo "exit $?"`. Expected: compile failure.

- [ ] **Step 4: Implement.** In `PlanSessionScreen.kt` make `ChoiceRow` `internal`. Create `TrainerWorkbenchPage.kt`:

```kotlin
package com.metaself.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.metaself.app.R
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerPath
import com.metaself.app.domain.trainer.Wish
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.screen.trainer.ChoiceRow
import com.metaself.app.ui.screen.trainer.EvaluatePlanViewModel
import com.metaself.app.ui.screen.trainer.PlanSessionViewModel
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.TrainerWording
import com.metaself.app.ui.trainer.WorkbenchWording
import java.time.ZoneId

/**
 * "Test the trainer's instructions" (D106): the path and its inputs, the instructions, Send, the reply.
 * Plain on purpose — a testing tool. The pickers and the clipboard are the destination's, so this stays a
 * pure composable. Branches only; no early return out of an inline composable.
 */
@Composable
fun TrainerWorkbenchPage(
    state: TrainerWorkbenchViewModel.State,
    onPath: (TrainerPath) -> Unit,
    onSession: (Long) -> Unit,
    onPlan: (PlanSessionViewModel.Form) -> Unit,
    onEvaluate: (EvaluatePlanViewModel.Form) -> Unit,
    onAdjustWords: (String) -> Unit,
    onLoad: () -> Unit,
    onUseAppOwn: () -> Unit,
    onSaveAppOwn: () -> Unit,
    onSend: () -> Unit,
    onCopyReply: () -> Unit,
    onCopySent: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(title = stringResource(R.string.settings_page_trainer_instructions), modifier = modifier, onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
            Text(WorkbenchWording.INTRO, style = MaterialTheme.typography.bodyMedium)
            val open = !state.sending
            ChoiceRow(R.string.workbench_row_path, TrainerPath.entries, state.path, WorkbenchWording::path, open, onPath)
            PathInputs(state, open, onSession, onPlan, onEvaluate, onAdjustWords)

            Text(WorkbenchWording.willSend(state.fileName), style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = onLoad, enabled = open, modifier = Modifier.fillMaxWidth()) { Text(WorkbenchWording.LOAD) }
            if (state.fileName != null) {
                OutlinedButton(onClick = onUseAppOwn, enabled = open, modifier = Modifier.fillMaxWidth()) { Text(WorkbenchWording.USE_APP_OWN) }
            }
            OutlinedButton(onClick = onSaveAppOwn, modifier = Modifier.fillMaxWidth()) { Text(WorkbenchWording.SAVE_APP_OWN) }
            state.fileMessage?.let { Text(it) }

            Button(onClick = onSend, enabled = state.canSend, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.sending) WorkbenchWording.SENDING else WorkbenchWording.SEND)
            }
            Text(WorkbenchWording.PRIVACY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.failure?.let { Text(TrainerWording.failure(it), color = MaterialTheme.colorScheme.error) }
            state.reply?.let { reply ->
                SelectionContainer { Text(reply) }
                OutlinedButton(onClick = onCopyReply, modifier = Modifier.fillMaxWidth()) { Text(WorkbenchWording.COPY_REPLY) }
            }
            if (state.sent != null) {
                OutlinedButton(onClick = onCopySent, modifier = Modifier.fillMaxWidth()) { Text(WorkbenchWording.COPY_SENT) }
            }
        }
    }
}

/** Each path's inputs, as its real screen asks them. */
@Composable
private fun PathInputs(
    state: TrainerWorkbenchViewModel.State,
    open: Boolean,
    onSession: (Long) -> Unit,
    onPlan: (PlanSessionViewModel.Form) -> Unit,
    onEvaluate: (EvaluatePlanViewModel.Form) -> Unit,
    onAdjustWords: (String) -> Unit,
) {
    when (state.path) {
        TrainerPath.FEEDBACK -> if (state.sessions.isEmpty()) {
            Text(WorkbenchWording.NO_SESSIONS)
        } else {
            val zone = ZoneId.systemDefault()
            ChoiceRow(
                R.string.workbench_row_session, state.sessions, state.sessions.firstOrNull { it.id == state.workoutId },
                { TrainerWording.sessionTitle(it, state.today, zone) }, open,
            ) { onSession(it.id) }
        }

        TrainerPath.PLAN -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
            val form = state.plan
            ChoiceRow(R.string.plan_row_what, PlanActivity.entries, form.activity, TrainerWording::activity, open) { onPlan(form.copy(activity = it)) }
            ChoiceRow(R.string.plan_row_time, TimeAvailable.entries, form.time, TrainerWording::time, open) { onPlan(form.copy(time = it)) }
            ChoiceRow(R.string.plan_row_feel, Feeling.entries, form.feeling, TrainerWording::feeling, open) { onPlan(form.copy(feeling = it)) }
            ChoiceRow(R.string.plan_row_want, Wish.entries, form.wish, TrainerWording::wish, open) { onPlan(form.copy(wish = it)) }
            Words(form.words, open) { onPlan(form.copy(words = it)) }
        }

        TrainerPath.EVALUATE -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
            val form = state.evaluate
            ChoiceRow(R.string.weeks_row_weeks, ProgrammeAsk.WEEKS, form.weeks, { "$it weeks" }, open) { onEvaluate(form.copy(weeks = it)) }
            ChoiceRow(R.string.weeks_row_per_week, ProgrammeAsk.PER_WEEK.toList(), form.perWeek, { "$it" }, open) { onEvaluate(form.copy(perWeek = it)) }
            Words(form.words, open) { onEvaluate(form.copy(words = it)) }
        }

        TrainerPath.ADJUST -> if (state.planRuns) {
            Words(state.adjustWords, open, onAdjustWords)
        } else {
            Text(WorkbenchWording.NO_PLAN)
        }
    }
}

@Composable
private fun Words(words: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = words,
        onValueChange = onChange,
        enabled = enabled,
        label = { Text(stringResource(R.string.plan_words)) },
        minLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}
```

  **Check the prefix collision:** `render.isEnabled("Send")` and `click` match the first node *starting
  with* the prefix. "Send" is drawn before the privacy line, which starts "On Send", and "Sending…" only
  appears while sending — so "Send" finds the button.

- [ ] **Step 5: Run.** Same as Step 3, plus `--tests "com.metaself.app.ui.screen.trainer.*"` (ChoiceRow's
  visibility change). Expected: exit 0.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/metaself/app/ui/screen/trainer/PlanSessionScreen.kt \
  app/src/main/java/com/metaself/app/ui/screen/settings/TrainerWorkbenchPage.kt \
  app/src/main/res/values/strings.xml \
  app/src/test/java/com/metaself/app/ui/screen/settings/TrainerWorkbenchPageRenderTest.kt
git commit -m "feat(trainer): the instructions workbench page (D106)"
```

---

### Task 9: The seventh Settings entry, the pickers and the clipboard

**Files:**
- Modify: `app/src/main/java/com/metaself/app/ui/screen/settings/SettingsPage.kt`
- Modify: `app/src/main/java/com/metaself/app/ui/screen/settings/SettingsScreen.kt` (KDoc "six rows" → "seven rows")
- Modify: `app/src/main/java/com/metaself/app/ui/settings/SettingsIndexWording.kt`
- Modify: `app/src/main/java/com/metaself/app/ui/nav/SettingsDestination.kt`
- Test: `app/src/test/java/com/metaself/app/ui/settings/SettingsIndexWordingTest.kt`, `app/src/test/java/com/metaself/app/ui/screen/settings/SettingsIndexRenderTest.kt`

- [ ] **Step 1: Update the tests first.** `SettingsIndexWordingTest`: rename
  `` `the other four rows show as soon as the page has loaded` `` to
  `` `the other rows show as soon as the page has loaded` ``, its KDoc to "Backups, AI, Food database,
  Problems and the trainer's instructions need only [SettingsUiState.loaded].", and add:

```kotlin
        assertThat(SettingsIndexWording.statusOf(SettingsPage.TRAINER_INSTRUCTIONS, state)).isEqualTo("A testing tool · stores nothing")
```

  `SettingsIndexRenderTest`: rename `` `six rows, in order, …` `` to `` `seven rows, in order, …` ``; append
  `"Test the trainer's instructions" to SettingsIndexWording.TRAINER_INSTRUCTIONS` to its `rows`; append
  `"A testing tool · stores nothing"` to the `containsAtLeast` in `` `the status lines read as the wording says` ``;
  append `"Test the trainer's instructions"` to the click list in `` `each row opens its page` ``.

- [ ] **Step 2: Run to see them fail.** `free -m`; `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.settings.SettingsIndexWordingTest" --tests "com.metaself.app.ui.screen.settings.SettingsIndexRenderTest" > /tmp/ms-bench-9.log 2>&1; echo "exit $?"`. Expected: compile failure.

- [ ] **Step 3: Implement.** `SettingsPage.kt` — KDoc "The seven pages of Settings (D79); the last, a testing
  tool (D106)" and the new last entry:

```kotlin
    PROBLEMS("problems", R.string.settings_problems_title),
    TRAINER_INSTRUCTIONS("trainer-instructions", R.string.settings_page_trainer_instructions),
```

  `SettingsIndexWording.kt`:

```kotlin
    /** D106: nothing to count; what the page is, and that it keeps nothing. */
    const val TRAINER_INSTRUCTIONS = "A testing tool · stores nothing"
```

  and in `statusOf`: `SettingsPage.TRAINER_INSTRUCTIONS -> TRAINER_INSTRUCTIONS`.

  `SettingsDestination.kt` — a new branch after `PROBLEMS` (imports:
  `com.metaself.app.ui.screen.settings.TrainerWorkbenchPage`,
  `com.metaself.app.ui.screen.settings.TrainerWorkbenchViewModel`, `com.metaself.app.ui.trainer.WorkbenchWording`):

```kotlin
        SettingsPage.TRAINER_INSTRUCTIONS -> {
            // D106: its own view model on this entry, so a loaded file goes when the page is left.
            val workbench: TrainerWorkbenchViewModel = hiltViewModel(here)
            val state by workbench.state.collectAsStateWithLifecycle()
            val clipboard = LocalClipboardManager.current
            // Android's own document picker, plain text, so a file on Drive works.
            val chooseInstructions = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri -> uri?.let { workbench.load(it.toString()) } }
            val chooseWhereToSave = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("text/plain"),
            ) { uri -> uri?.let { workbench.saveAppInstructionsTo(it.toString()) } }
            TrainerWorkbenchPage(
                state = state,
                onPath = workbench::pickPath,
                onSession = workbench::pickSession,
                onPlan = workbench::changePlan,
                onEvaluate = workbench::changeEvaluate,
                onAdjustWords = workbench::changeAdjustWords,
                onLoad = { chooseInstructions.launch(arrayOf("text/plain", "*/*")) },
                onUseAppOwn = workbench::useAppOwn,
                onSaveAppOwn = { chooseWhereToSave.launch(WorkbenchWording.fileName(state.path)) },
                onSend = workbench::send,
                onCopyReply = { state.reply?.let { clipboard.setText(AnnotatedString(it)) } },
                onCopySent = { state.sent?.let { clipboard.setText(AnnotatedString(it)) } },
                onBack = onBack,
            )
        }
```

- [ ] **Step 4: Run the Settings and nav tests.** `free -m`; `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.settings.*" --tests "com.metaself.app.ui.screen.settings.*" --tests "com.metaself.app.ui.nav.*" > /tmp/ms-bench-9.log 2>&1; echo "exit $?"`. Expected: exit 0.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/metaself/app/ui/screen/settings/SettingsPage.kt \
  app/src/main/java/com/metaself/app/ui/screen/settings/SettingsScreen.kt \
  app/src/main/java/com/metaself/app/ui/settings/SettingsIndexWording.kt \
  app/src/main/java/com/metaself/app/ui/nav/SettingsDestination.kt \
  app/src/test/java/com/metaself/app/ui/settings/SettingsIndexWordingTest.kt \
  app/src/test/java/com/metaself/app/ui/screen/settings/SettingsIndexRenderTest.kt
git commit -m "feat(settings): Test the trainer's instructions, the last entry (D106)"
```

---

### Task 10: The privacy page, 0.71.0, the suite and the build

**Files:**
- Modify: `privacy.html`
- Modify: `app/build.gradle.kts:24,35`

- [ ] **Step 1: privacy.html.** In the "Your training record, to OpenAI, when you ask the trainer" item,
  change `the planned session you are planning or were given feedback on;` to
  `the planned session you are planning or were given feedback on, with its week and how many weeks the plan has;`.
  At the end of that `<li>`, after `The same list is shown on screen beside the button.`, add:

```html
  Settings → <em>Test the trainer's instructions</em> sends the same request, when you press Send, with
  instructions you load from a file in place of the app's own; it stores nothing.
```

- [ ] **Step 2: Version.** `versionCode = 126` → `127`; `versionName = "0.70.0"` → `"0.71.0"`.

- [ ] **Step 3: The suite, lint, the build** — one at a time, `free -m` before each:

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest > /tmp/ms-bench-10a.log 2>&1; echo "exit $?"
free -m
~/bin/gradlew-safe :app:lintDebug > /tmp/ms-bench-10b.log 2>&1; echo "exit $?"
free -m
~/bin/gradlew-safe :app:assembleDebug > /tmp/ms-bench-10c.log 2>&1; echo "exit $?"
```

  Expected: exit 0 each. In the suite's results, exactly the ten classes `CLAUDE.md` names are skipped
  (Room), nothing else: `grep -l 'skipped="[1-9]' app/build/test-results/testDebugUnitTest/*.xml`.

- [ ] **Step 4: Commit.**

```bash
git add privacy.html app/build.gradle.kts
git commit -m "0.71.0: the trainer's instructions workbench (D106, D107)"
```

- [ ] **Step 5: Phone checks to hand the owner** (the release build is the controller's, `~/bin/ms-release`):
  Settings → last entry opens; each path sends and shows a reply; Save the app's instructions writes a
  `.txt` that opens on the phone; Load from a Drive file names it on the "Will send" line; Copy reply and
  Copy what was sent paste; a real feedback screen afterwards shows no new feedback and the Trainer card
  is unchanged.

---

## Self-review against the spec

- Path picker with each path's inputs — Tasks 7, 8 (feedback: 42 days' visible counted sessions, Task 5
  `sessions()`; stored review supplies felt/words/plan, no review saved, Task 5; plan: five answers, next
  planned session via `planQuestion`; evaluate: weeks, per week, words, start and last evaluation via
  `evaluateQuestion`; adjust: words, only while a plan runs, "No plan is running.").
- Load from a file, in memory for the visit, whole system message replaced — Tasks 6, 7, 9; Task 2 test.
- Save the app's instructions (the exact system text) — Tasks 2, 7, 9.
- The "Will send" line — Tasks 7, 8.
- Send; reply as plain text; Copy reply; Copy what was sent (verbatim body) — Tasks 4, 7, 8, 9.
- The model's settings apply; no `response_format`, no schema instruction — Tasks 2, 4.
- Failures in the existing wording, no retry of its own — Task 8 (`TrainerWording.failure`), Task 4.
- Stores nothing; never runs in the background; real screens unchanged — Task 5 test; only a tap sends.
- Checks: per-path user message equals the real one (Task 5, via shared builders, Task 3); a run writes
  nothing (Task 5); the body has the loaded text as the only system message and no `response_format`
  (Task 2).
- D107 in real requests too, with one clause in the shared instructions — Task 1.
- Version, privacy page — Task 10.
