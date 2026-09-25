# The app learns what a model accepts — Implementation Plan

**Goal:** a model named in Settings that refuses a request's settings is answered anyway: the app
reads the provider's 400 refusal, adjusts the request (at most three retries), and remembers the
profile that worked, per model name, so a new model needs no new version.

**Decision:** the owner's, 2026-09-25. Recorded as D57 in
`docs/superpowers/specs/2026-09-25-model-requirements-learned-design.md`. No public issue exists.

**Red lines (stop and report if crossed):**

- **Nothing new is sent.** `EstimatePromptTest`'s and `ReviewPromptTest`'s *nothing else is sent*
  tests pass unchanged in what they forbid.
- **No database change.** Profiles live in the Preferences DataStore beside the model name.
- **Every request that reaches the provider is counted** against the day's ceiling; no retry is sent
  with the allowance spent.
- **An unknown refusal is shown exactly as before**, in the provider's words.

## Steps

1. **The profile and the one request builder** (`data/ai/RequestProfile.kt`, `ChatRequest.kt`).
   `RequestProfile(temperature, reasoningEffort, strictFormat)`; `RequestProfile.guess(model)` is the
   0.50.0 prefix rule, moved from `ModelParams` (deleted; its tests move to `RequestProfileTest`).
   `ChatRequest.body(model, profile, messages, schemaName, schema)` writes the parameters and either
   the strict `json_schema` format or `json_object` with the schema appended to the first system
   message. `EstimatePrompt.requestBody` and `ReviewPrompt.requestBody` take a profile (default: the
   guess) and build through it. Tests: both formats; the guess; the prompts' existing tests green.
2. **The refusal reader and the fix** (`ProviderRefusal.kt`, `RequestFix.kt`), pure, JUnit 5.
   Parse `error.{type,code,param,message}`; param from the message only when absent; supported values
   from the message. `RequestFix.candidates(profile, refusal)` per the spec's table. Tests: realistic
   bodies — temperature, a `reasoning_effort` value with a list, `reasoning_effort` unsupported,
   `max_tokens`, `response_format`, a key refusal, a body that is not JSON.
3. **The remembered profiles** (`RequestProfileStore` + `DataStoreRequestProfileStore`): one
   JSON-encoded preferences entry, name → profile, each write one atomic `edit`. Tests: round trip;
   two names kept apart; an unreadable entry reads as nothing; the model's own settings untouched.
4. **The learning call** (`OpenAiCall.send`): stored profile or guess; on 400 pick the first untried
   candidate, up to three retries, each counted, none sent past the ceiling; store on success when it
   differs from what is stored. The builder lambda receives the model and the profile. Tests with a
   local server: refused twice then answered — the stored profile, three requests counted, the
   bodies sent; a stored profile used as sent; a repeat stops; the bound; an unknown refusal as
   before; the ceiling mid-learning.
5. **Wiring:** `AiModule` provides the store and hands it to the estimator and the reviewer.
6. **Settings → Test it** reports the saved model's profile in one line
   (`SettingsUiState.testLearned`), from `RequestProfile.describe`. Tests: the wording (JUnit 5), the
   view model after a success, and a render test that the line is drawn under the result.
7. **Verify** `:app:testDebugUnitTest` (exactly the eight database classes skip) and `:app:lintDebug`;
   bump to 0.51.0; push the branch. No PR.

## Not built

- No token-limit field in the profile: no request sends one (spec §1).
- Profiles are not in the backup (spec §5).
