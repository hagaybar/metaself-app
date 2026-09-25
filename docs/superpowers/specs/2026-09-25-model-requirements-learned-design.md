# The app learns what a model accepts — D57

> Decided 2026-09-25 by the owner. A model named in Settings that refuses a request's settings used
> to fail on every call until a new version of the app was built for it. From D57 the app reads the
> provider's refusal, adjusts the request, and remembers what worked, per model name.
>
> Every model name, error text and figure below is invented to illustrate the rule beside it; the
> error bodies are shaped like the provider's real ones.

---

## Why there is a decision here at all

- **The model is a setting precisely because names move faster than releases** (`AiSettings`). But
  models differ in what a request may carry, and until now the only way to learn that was a new
  build: 0.50.0 added a name-prefix rule (`ModelParams`) after a reasoning model refused
  `temperature: 0` on every call.
- **A prefix rule is a guess about names that do not exist yet.** The provider says exactly what it
  refused, in a structured error object. Reading it is cheaper than guessing.

---

## D57 — Every request is built from a per-model profile; a refusal teaches the profile; a working profile is remembered

### 1. The profile

What a request carries besides what is asked, and nothing else:

| Part | Values |
|---|---|
| temperature | `0` sent, or omitted |
| `reasoning_effort` | omitted, or one of `none`, `minimal`, `low`, `medium`, `high` |
| reply format | `json_schema` with `strict: true`, or `json_object` with the schema written into the instructions |

**No token limit is part of it, because no request sends one.** A refusal naming `max_tokens` or
`max_completion_tokens` therefore has nothing to fix and is shown as it came (§3).

One place (`ChatRequest`) builds every request — the estimate, the review and Settings' *Test it* —
from the messages, the schema and the profile. `EstimatePrompt` and `ReviewPrompt` keep deciding what
is asked; they no longer decide how it is sent.

### 2. The first guess

A model with nothing remembered starts from the 0.50.0 rule, unchanged: names beginning `gpt-5`,
`gpt-6`, `o1`, `o3` or `o4` get no temperature and `low` reasoning; every other name gets
temperature 0 and no reasoning setting. Both start with the strict schema.

### 3. What a refusal teaches

Only an HTTP **400** is read. Its body's `error` object is parsed for `type`, `code`, `param` and
`message`. **Structured fields decide**; the message is read only where they are silent — to find the
parameter when `param` is null, and to read a list of accepted values (*"Supported values are:
'low', 'medium', and 'high'."*).

| Refused | Next profile(s), first untried wins |
|---|---|
| `temperature` (sent) | omit it, and send `reasoning_effort` (kept if set, else `low`) |
| `reasoning_effort` as a parameter (`unsupported_parameter`, or no value named) | omit it and restore temperature 0; failing that, omit both |
| `reasoning_effort` value, with a list | the lowest accepted value at or above `low`; if none, the highest below it |
| `reasoning_effort` value, no list | the next value up from the one sent (`low` → `medium` → `high`) |
| `response_format` (while strict) | `json_object`, with the schema written into the system message |
| anything else — a token limit, a key, a quota, an unknown code | nothing: the refusal is shown exactly as before D57 |

The reply format's fallback loses nothing that matters: every reply is already checked field by field
by the strict parsers (`EstimateResponse`, `ReviewResponse`), which is what drops or refuses a
malformed answer today.

**Bounds.** At most **three** learning retries follow one call's first request. A profile already
tried in this call is never sent again. When the bounds, a repeat or an unknown refusal stop the
learning, the last refusal is shown as it came.

### 4. Counting against the day's ceiling

**Every request that reaches the provider is counted**, a refused one included — as before D57, a
refusal still cost a request. So one call can spend up to four of the day's allowance, once per model
name, until the profile is remembered. A retry is not sent when the allowance is spent; the refusal
that preceded it is shown instead. The ceiling exists to bound a runaway; counting what learning
really sends keeps it honest.

### 5. Remembering

- **Only a profile that worked is stored**, on the first success, keyed by the model name exactly as
  Settings holds it. A stored profile is used as sent on every later call; the guess is not consulted.
- All remembered profiles live in one entry of the settings store (a Preferences DataStore, not the
  database — no schema change): a JSON object from model name to profile, for example
  `{"gpt-6-luna":{"temperature":false,"reasoning_effort":"low","format":"strict"}}`. An entry that
  cannot be read counts as nothing remembered and is relearned.
- **Changing the model** uses that name's remembered profile, or the guess. Saving a model, or
  *Use the default*, leaves every other name's profile in place.
- **A remembered profile that is refused later** (the provider changed the model) is relearned from
  where it stands, by §3, and the new working profile replaces it.
- **Two calls at once for a new model** (a description and a review) may both learn; each writes one
  working profile in a single atomic edit, and the last write stands. Both are working, so either is
  correct.
- Remembered profiles are not in the backup: they are relearned in at most four requests, and a
  restore that replaces the settings store drops them like any other setting it replaces.

### 6. What the owner sees

- **Nothing new, when the model works.** When it would have refused, the answer now comes — a few
  seconds later on its first call, since each learning retry is a request.
- **Settings → Test it** says, under its usual line, what it now sends for the saved model, in one
  plain line:
  - *"gpt-6-luna works as sent."* when the first guess worked;
  - *"gpt-6-luna works: no temperature, medium thinking, strict format."* when something was learned
    (or was learned earlier). The parts read *temperature 0* / *no temperature*; *low thinking* and
    so on, *thinking off* for `none`, nothing when no reasoning setting is sent; *strict format* /
    *format in the instructions*.
  - Nothing more when the test failed: the failure says what it said before.

### 7. Privacy and offline use

**Nothing new is sent to the provider.** A retry carries the same words as the request it replaces,
with only the profile's parameters changed — or, in the `json_object` fallback, the reply's schema
moved into the instructions; the schema is the app's own text. D16's promise (only the description
leaves) and D54's (only the food under review leaves) are unchanged. Nothing about learning is sent
anywhere else, and the problem log is written exactly as before. Offline-first is unaffected: the
app makes no call it did not make before, except the bounded retries of §3.
