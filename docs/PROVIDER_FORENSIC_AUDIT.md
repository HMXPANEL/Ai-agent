# Provider Forensic Audit

**Scope:** complete AI/LLM provider system of the ClosePaw HMX Android app.
**Method:** read-only source trace. No code was modified, fixed, refactored, or committed for this audit.
**HEAD audited:** `7873a47` (repo `HMXPANEL/Ai-agent`, branch `master`, working tree clean).
**Verdict vocabulary:** `CONFIRMED` (source evidence) / `POTENTIAL` (mechanism exists, trigger unverified) /
`EXPECTED` (by design) / `NOT VERIFIED` (needs runtime proof) / `BLOCKED` (no hardware/logs).

Prior provider work already on master (context, not part of this audit's changes):
`25ebc1d` (ModelEntry.modelId to clients, streaming index guard), `e268662` (override compile fixes),
`c78fb06` (persisted selectedProvider, session guard, routing log, factory tripwire), plus test-fix commits.

---

## Executive Summary

1. Routing is **model-led, single-choke-point**: `SessionConfig.mainModel` → `ModelCatalog.resolve` →
   `entry.provider` → `LLMClientFactory.create` → client. Every runtime LLM call (main turns, planning,
   compaction, subagents) resolves through this path. **CONFIRMED.**
2. The UI "provider" (settings tab) is **ephemeral composable state** (`rememberSaveable`), never directly
   consumed at runtime. The persisted routing decision is (`selectedModel`, `selectedProvider`) in
   `SharedPreferences "agent_prefs"`, kept coherent by `AppSettingsState`. A tab/model divergence is the
   only mechanism consistent with "UI=OTHER but runtime=Codex". **CONFIRMED mechanism.**
3. The three historical observations are **three separate bugs**, not one:
   - **404 `gpt-5.5`:** catalog data referenced a nonexistent upstream model (`model_id: "gpt-5.5"`).
     Fixed by removal (`1208558`) + validation fallback. **CONFIRMED, resolved.**
   - **400 `gpt-5.4 … not supported when using Codex`:** Codex backend received the OpenAI-style id
     `gpt-5.4`. Codex catalog entries still carry OpenAI-style `model_id`s and the client still sends
     `entry.modelId` verbatim → recurrence is **POTENTIAL, NOT VERIFIED live**.
   - **`OpenAIInvalidDataException: index is not set`:** `ChatCompletionClient` streaming called
     `tcDelta.index()` (OpenAI SDK required-field accessor, throws when absent); Gemini's
     OpenAI-compatible endpoint omits `tool_calls[].index`. Guarded post-fix (try/catch → `0L`).
     Location **CONFIRMED**; live Gemini payload capture **BLOCKED**.
4. **No silent OTHER→CODEX path exists in the current tree.** Factory mapping forbids it, a tripwire
   throws on mismatch, the bootstrap guard throws on UI/model disagreement, and `CodexResponseClient`
   has exactly one instantiation site. **CONFIRMED by exhaustive search.**
5. Two integration gaps found: Phase-3 capability filtering is **not applied** to the LLM tool list
   (`getAvailable()` has zero callers), and multi-tool-call index collisions under the `0L` fallback
   would merge builders (**POTENTIAL**).

---

## Provider Architecture Map

```
LlmAuthSettingsPage (tabs: SIGN_IN / API_KEY / LOCAL; provider sub-tabs incl. OTHER)
  │  selectedProvider: rememberSaveable UI state (NOT persisted, NOT read at runtime)
  │  selectedModel ──onModelChange──▶ AppSettingsState.updateModel / updateProvider
  ▼
AppSettingsStore (SharedPreferences "agent_prefs", PLAIN) + AuthStore (ENCRYPTED prefs)
  │  selectedModel, selectedProvider, otherBaseUrl, otherModelId, api keys / OAuth
  ▼
MainActivity / AgentService / OnboardingDemoController → SessionConfig(mainModel, provider, llm.backendType)
  ▼
AgentSession.create → SessionServices.create → SessionLlmBootstrapper.create
  │  ensureRequiredCredentials → factory.create(mainModel) → guard → routing log line
  ▼
LLMClientFactory (per-session instance; cache keyed by model name + auth generation)
  │  OPENAI_API+RESPONSE → OpenAIResponseClient      → {base}/responses (SDK)
  │  OPENAI_API+CHAT     → ChatCompletionClient      → {base}/chat/completions (SDK)
  │  OPENAI_CODEX        → CodexResponseClient       → chatgpt.com/backend-api/codex/responses (raw OkHttp)
  │  OPENROUTER          → ChatCompletionClient      → openrouter.ai/api/v1 + /chat/completions
  │  OTHER               → ChatCompletionClient      → configured baseUrl + /chat/completions
  │  LOCAL_LFM           → refused (use LFMLLMClient directly; LOCAL backendType path)
  ▼
Turn (per-turn AgentModelResolver.resolve(config.modelName) → factory or session-client fallback)
  → chatWithTools / chatWithToolsStreaming(modelId = entry.modelId)
  ▼
Response parsers (SDK Responses events / SDK Chat deltas / CodexSseParser) → LLMToolCall
  → Turn.processResponse → ToolRegistry → handlers (capability filter NOT applied to LLM list — §23)
```

Key files:
- `app/src/main/kotlin/ai/closepaw/llm/` — 28 files: clients, factory, catalog, validators, loggers, interop.
- `app/src/main/kotlin/ai/closepaw/auth/` — 5 files: `AuthStore.kt`, `AuthCredential.kt`, `AuthErrors.kt`,
  `OpenAIOAuth.kt`, `OpenAiSignIn.kt` (read-only for this audit).
- Session: `session/SessionLlmBootstrapper.kt`, `session/SessionServices.kt`, `session/AgentSession.kt`,
  `session/ProviderRouting.kt`, `protocol/SessionConfig.kt`.
- UI/state: `ui/settings/LlmAuthSettingsPage.kt`, `app/AppSettingsState.kt`, `app/AppSettingsStore.kt`,
  `app/MainActivityModelValidation.kt`.
- Agent: `agent/Turn*.kt`, `agent/AgentModelResolver.kt`, `agent/HmxAgent.kt`,
  `agent/TaskOrchestrator.kt`, `agent/Planner.kt`, `agent/subagent/SubAgentRunner.kt`.

---

## Provider Selection Flow

`ui/settings/LlmAuthSettingsPage.kt`:
- Provider tab state: `var selectedProvider by rememberSaveable(derivedProvider, initialProvider)` (~L375).
  Initial value derives from the model's entry (`resolveProviderForTab`, L261–274); afterwards it is
  independent, ephemeral tab state. Tapping a tab changes **only** this variable. **CONFIRMED.**
- Model pickers call `onModelChange` → `settingsState::updateModel` (`MainActivityContent.kt:167`).
  SIGN_IN commits additionally run `canonicalizeMainModel` (`LlmAuthSettingsPage.kt:163-174`); API_KEY/OTHER
  tab content has no canonicalization on tab tap (deliberate per L162 comment). **CONFIRMED.**
- OTHER auto-flip: `LaunchedEffect` (~L510-531) calls `onModelChange("other-custom")` only when
  `shouldAutoFlipToOtherCustom` (L544-565) passes ALL of: tab==OTHER, non-blank key, valid URL, valid
  modelId, catalog contains `other-custom` with byte-identical normalized URL+modelId, model not already
  `other-custom`. Any unmet gate → silent no-op. **CONFIRMED.**
- Asynchrony (Q10): API-key persist is debounced 300 ms (`API_KEY_PERSIST_DEBOUNCE_MS`, L277) with
  single-flight mutex; OTHER URL/modelId persist via `launchDebouncedPersist`; catalog `invalidate()` and
  recomposition must complete before the flip gate can pass. A fast navigate-away can strand the tab on
  OTHER while `selectedModel` keeps its previous value. **CONFIRMED mechanism; live reproduction BLOCKED.**
- OAuth override (Q9): none found. `ensureRequiredCredentials` only throws `MissingCredential`; no code
  switches provider on credential presence (searched `oauth|credential|codex` across llm+session+app).
  **CONFIRMED absent.**
- Stale overwrite (Q11): `updateModel`/`load` reconcile via `coherentPair`; `updateProvider` reconciles
  the model; nothing else writes `selectedModel`. **CONFIRMED.**

## Persistence Flow

- Settings (model, provider, URLs, backend, flags): plain `SharedPreferences "agent_prefs"`
  (`AppSettingsStore.kt:39`, `prefs()` L94). New `KEY_PROVIDER="provider"`, nullable, unknown strings load
  as null (`AppSettingsStore.kt` provider block). **CONFIRMED.**
- Credentials: `EncryptedSharedPreferences` (`AuthStore.kt:45-49`); `AuthCredential` = `ApiKey(key)` |
  `OAuth(...)`; `CodexHeaders(accessToken, chatgptAccountId, email)` (`AuthCredential.kt:3-15`).
  Read-only note: auth is a **dependency** of routing only via `has()` / `requireApiKey()` /
  `codexHeaders()` / `generation()` (`AuthStore.kt:69-103`). **CONFIRMED.**
- Catalog: in-memory `StateFlow` in process-singleton `ModelCatalogRepositoryHolder`
  (`ModelCatalogRepository.kt:310-330`); rebuilt by `invalidate()` from seed + settings + discovery cache.
  **CONFIRMED.**
- SAVE → RESTART → LOAD → RUNTIME: `store.saveModel/saveProvider` → prefs → `AppSettingsState.load()` →
  `coherentPair` → `SessionConfig(mainModel, provider)` (`MainActivity.kt:713-716`) →
  `SessionLlmBootstrapper.create` reads holder catalog `.value` off-main. Stale risk (Q4): if the holder
  catalog predates an OTHER edit, `other-custom` is absent → pre-fix this silently fell back; post-fix
  OTHER-flavored keys are trusted and the bootstrapper raises `MissingCredential(OTHER)`. **CONFIRMED.**

## Session Configuration Flow

- `SessionConfig` (`protocol/SessionConfig.kt:12-47`) carries **both** `mainModel: String = "glm-5"` (L37)
  and `provider: LLMProvider? = null` (added `c78fb06`). Backend enum `LLMBackendType { OPENAI, LOCAL }`
  (L64-69) selects cloud-vs-local; it is orthogonal to the cloud provider. **CONFIRMED.**
- Disagreement semantics (`session/ProviderRouting.kt:27-41`): null skips; else
  `entry.provider != selectedProvider` → `IllegalStateException` naming both (no secrets). So
  `provider=OTHER, model=gpt-5.4-codex` **throws at session start** — never routes. **CONFIRMED.**
- Construction sites: `MainActivity.kt:713` (persisted pair), `AgentService.kt:381` (persisted pair;
  previously defaults-only — latent trap, now wired), `OnboardingDemoController.kt:90` (persisted pair),
  `SessionCheckpointCoordinator.kt:107` (`toSessionConfig`, provider=null → guard skipped on resume —
  documented resume semantics), `MobileActionDebugRunner.kt:139` (defaults → guard skipped). **CONFIRMED.**

## Model Catalog

`assets/llm_models.json` (current): 9 seed entries —

| key | provider | api | model_id |
|---|---|---|---|
| gpt-5.2 | OPENAI_API | response | gpt-5.2 |
| gpt-5.2-codex | OPENAI_CODEX | response | gpt-5.2 |
| glm-5 (DEFAULT) | OPENROUTER | chat | z-ai/glm-5 |
| minimax-m2.5 | OPENROUTER | chat | … |
| kimi-k2.5 | OPENROUTER | chat | … |
| qwen3-vl-235b… | OPENROUTER | chat | … |
| qwen3.5 | OPENROUTER | chat | … |
| gpt-5.4 | OPENAI_API | response | gpt-5.4 |
| gpt-5.4-codex | OPENAI_CODEX | response | gpt-5.4 |

Plus runtime overlays (`ModelCatalogRepository.load`, L154-161): synthesized `other-custom`
(provider OTHER, api CHAT, modelId/baseUrl from settings, both validated — L189-216) and discovered
`other:*`/openrouter entries scoped to the current base URL (L170-187). Overlay wins on name collision
(`withExtraEntries`, `ModelCatalog.kt:143-151`); `withBaseUrlOverrides` never overwrites an explicit
entry baseUrl (L125-132). **CONFIRMED.**

Resolution API (`ModelCatalog.kt:78-117`): `resolve` (throws `IllegalArgumentException` listing keys),
`resolveOrNull`, `contains`, `modelsFor`, `preferredModelFor` (first match), `defaultModel` (throws if
none). Straight map lookups — no provider rewriting. **CONFIRMED.**

## Model Resolution

Per-turn: `TurnPlanningPhaseRunner.kt:55` `modelResolver.resolve(config.modelName)` →
`AgentModelResolver.kt:30-60`: entry found → `factory.create(modelName)` (+ `entry.modelId`,
`entry.supportsVision`); entry missing/factory failure → **session-client fallback** with
`modelId = modelName` (the raw key). `SessionAgentRunner.kt:82` sets `modelName = config.mainModel`;
compactor (`:98`, `:287-310`) and `SubAgentRunner.kt:101` (`childModelName = parent mainModel`, L101;
child factory `create`, L162, else parent client) follow the same pattern. `Turn.kt:140,202`
thread the resolved `modelId` into `chatWithToolsStreaming(modelId=)`. **CONFIRMED.**

## LLMClientFactory

`llm/LLMClientFactory.kt:53-128` (`create` → cache → `build(entry)`), per-session instance,
`ConcurrentHashMap` keyed by model name + `AuthStore.generation` (stale entries cleaned inside the
per-key critical section, L60-86); `clientOverride` is test-only (`forTest`). Post-`build` tripwire
`checkClientMatchesEntry` throws on provider/client mismatch. **CONFIRMED.**

| Provider (+api) | Expected | Actual (`build`) | Correct? | Evidence |
|---|---|---|---|---|
| OTHER | ChatCompletionClient | ChatCompletionClient (hard-requires `entry.baseUrl`, else `MissingCredential(OTHER)`) | YES | Factory L111-122 |
| OPENAI_CODEX | CodexResponseClient | CodexResponseClient(entry + per-request `codexHeaders`) | YES | Factory L104-108 |
| OPENAI_API + RESPONSE | OpenAIResponseClient | OpenAIResponseClient | YES | Factory L97-100 |
| OPENAI_API + CHAT | ChatCompletionClient | ChatCompletionClient | YES | Factory L101-102 |
| OPENROUTER | ChatCompletionClient | ChatCompletionClient | YES | Factory L109-110 |
| LOCAL_LFM | LFMLLMClient | Refused (`IllegalStateException`, LOCAL backend path builds it) | YES (documented) | Factory L123-126 |

No `fallback/autoFlip/default/override/oauth` branch can force OTHER→CODEX (searched; only the
`MissingCredential` throw). **CONFIRMED.**

## Client Lifecycle/Caching

- Factory + cache live exactly one session (`SessionLlmBootstrapper.create` L45-50 builds new;
  `SessionServices.create` L140-150 consumes). Provider switch ⇒ new session ⇒ new factory ⇒ empty
  cache. **No cross-session reuse. CONFIRMED.**
- `parentServices.copy()` (`SessionServices.kt:341+`) defaults `llmClientFactory`/`modelCatalog` to the
  parent's — subagents share the factory intentionally; model is inherited (`childModelName`), so no
  divergence. **CONFIRMED.**
- `AgentSession.createWithServices` (external services) is **test-only** (4 test call sites, zero in
  main). `AgentService.runAgent` (defaults trap) has **zero callers** in main. **CONFIRMED dormant.**

## Provider → Client Matrix

| selectedProvider | mainModel example | client built | evidence |
|---|---|---|---|
| OTHER | other-custom / other:* | ChatCompletionClient | Factory L111-122; ProviderRoutingTest |
| OPENAI_CODEX | gpt-5.4-codex | CodexResponseClient | Factory L104-108 |
| OPENAI_API | gpt-5.4 / gpt-5.4-chat | OpenAIResponseClient / ChatCompletionClient | Factory L97-102 |
| OPENROUTER | glm-5 | ChatCompletionClient | Factory L109-110 |
| LOCAL_LFM | (backendType=LOCAL) | LFMLLMClient | Bootstrapper L~85-100 |

## Provider → Endpoint Matrix

| Client | Base URL source | Endpoint | Method | Auth |
|---|---|---|---|---|
| OpenAIResponseClient | provider default (api.openai.com) or override | `/responses` (SDK `client.responses().create/createStreaming`) | POST (SSE stream) | Bearer API key (SDK) |
| ChatCompletionClient | `entry.baseUrl` (OTHER synth normalized) / OpenRouter default / override; OTHER blank → throw, never SDK default | `/chat/completions` (SDK `client.chat().completions()…`, L83/145) | POST (SSE stream) | Bearer API key (SDK) |
| CodexResponseClient | const `https://chatgpt.com/backend-api/codex/responses` (internal const, `CodexResponseClient.kt`) | same, raw OkHttp, no body media type (backend rejects `charset=utf-8`) | POST (SSE stream) | Bearer accessToken + `originator: pi` + `OpenAI-Beta: responses=experimental` + optional `chatgpt-account-id` |
| LFMLLMClient | n/a (on-device Leap SDK) | n/a | n/a | none |

**Can OTHER reach the Codex URL?** Only via a `CodexResponseClient` built for an OTHER model — forbidden
by the factory mapping, rejected by the tripwire, and the class has exactly one instantiation site
(`LLMClientFactory.kt:106`). **CONFIRMED impossible in the current tree** (barring reflection).

## Request Formats

- Codex (`CodexRequestBuilder.kt:23-…`): JSON `{model, stream:true, store:false, instructions,
  input, tool_choice:"auto", parallel_tool_calls:true}`, **no `max_output_tokens`** (backend forbids;
  enforced by signature — `CodexResponseClientTest` pins it). Model arg = `this@modelId` = entry.modelId
  (`CodexResponseClient.kt:59,150`). **CONFIRMED.**
- Chat Completions (`ChatCompletionInterop.kt`): system prompt + converted input items + converted tools;
  `ChatModel.of(modelId)` with entry.modelId (`ChatCompletionClient.kt:buildParams`); optional
  `maxCompletionTokens`. **CONFIRMED.**
- Responses (`OpenAIResponseClient.kt:buildResponseParams`): instructions + input + tools,
  `ChatModel.of(modelId)` with entry.modelId. **CONFIRMED.**
- Wire model = `ModelEntry.modelId` on all three cloud paths; catalog key is never sent
  (post-`25ebc1d`). **CONFIRMED.**

## Response Formats

- OpenAI Responses streaming (`OpenAIResponseClient.kt:115-…`): SDK typed events — `isCreated`,
  `isOutputTextDelta` (`.delta()`), `isOutputItemDone`→`isFunctionCall` (`.callId()/.name()/.arguments()`),
  `isCompleted`. No index handling (item-based, not delta-based). **CONFIRMED.**
- Chat streaming (`ChatCompletionClient.kt:149-…`): `choice.delta()` → `content()` text;
  `toolCalls()` deltas accumulated per `index()` into `(callId, name, args)` builders; flushed on
  `finish_reason` stop/tool_calls; `length` → TransientException; `content_filter` → Failed event.
  **CONFIRMED.**
- Codex (`CodexSseParser.kt:71-113` + accumulator L122+): `response.created/output_text.delta/
  output_item.added/function_call_arguments.delta/output_item.done/done|completed/incomplete/failed/
  error`; JSON parsed with `opt*` defaults (safe); tool args accumulated per `output_index`.
  **CONFIRMED.**

## Streaming Parser

Assumption inventory: Chat path assumes each tool-call delta carries `index` (violated by Gemini —
see §15), `id`/`function.name` may arrive in later deltas (handled by backfill, L178-193),
`finish_reason` terminates (absent reason ⇒ builders never flush — **POTENTIAL** silent drop, no timeout
in parser; mitigated by `sawFinishReason`/completion checks elsewhere — NOT VERIFIED end-to-end).
`[DONE]`/framing is inside the OpenAI SDK stream iterator (SDK internals — **NOT VERIFIED**).
Codex path assumes `output_index` present (`optInt(...,0)` — safe default). Responses path assumes
`output_item.done` arrives for every function call (SDK contract — **NOT VERIFIED** beyond SDK).

## Tool Call Parser

`LLMToolCall(callId, name, arguments: JSON string)` → `Turn.processResponse` → `convertToToolCallRequest`
→ `ToolRegistry`/handlers. Normalization is per-client (see §Response Formats); no shared normalizer.
Edge behavior: missing index → `0L` fallback (Chat path); **two concurrent index-less tool calls would
share builder `0L` and merge args — POTENTIAL follow-on bug**, untested live. Missing/empty arguments:
`.orElse("")`-style defaults at builders; Codex `optString("delta","")`. `!!`/`first()`/`single()` in
parser paths: none found in the three streaming accumulators (searched). **CONFIRMED by inspection.**

## Error Handling

| Raw | Exception | UI/diagnostics |
|---|---|---|
| SDK rate-limit / 429 msg / timeout-msg / server-msg / conn-issue | `RateLimitException` / `TransientException` | retried (`CloudLlmRetry`, defaults MAX_RETRIES=5, backoff 1s→60s ×2 — `LLMClient.kt:28-32`); banner only on exhaustion |
| Codex 429/usage_limit | `RateLimitException` (friendly + reset minutes) | same |
| Codex 401/403 | `IllegalStateException("Token rejected")` | surfaced (non-retryable) |
| Codex 5xx | `TransientException` | retried |
| Other Codex HTTP | `RuntimeException("Codex API error: HTTP …")` | surfaced |
| HTTP 400/404 (OpenAI/OpenRouter/OTHER) | no status-specific branch → `RuntimeException` ("LLM error:" prefix per test) | surfaced, non-retryable, message-only — **no invalid-model actionability (POTENTIAL gap)** |
| Missing credential / unknown model / no OTHER URL | `MissingCredential(provider)` / `IllegalArgumentException` | deep-link banner (`findMissingCloudKeys`, `MainActivityModelValidation.kt:30-…) |
| Context overflow | `ContextWindowExceededException` → reactive `Compactor.forceCompactNow` (`Turn.kt:28`) | automatic |

## Fallback System

Exhaustive search (`fallback|autoFlip|default|override` in llm/session/app): **no silent OTHER→CODEX
fallback exists. CONFIRMED.** Present fallbacks, all loud or scoped:
1. `validateModelAgainstCatalog`: unknown **non-OTHER** key → `DEFAULT_MODEL` (glm-5/OPENROUTER) with
   provider synced + `Log.w` (`AppSettingsState.kt`). OTHER-flavored keys trusted.
2. Seed unreadable/corrupt → `FALLBACK_CATALOG_JSON` containing **only glm-5**
   (`ModelCatalogRepository.kt:291-299`) — **DESIGN LIMITATION**: a corrupt asset collapses the whole
   catalog to one OpenRouter model (loud-ish via Log.w, but drastic).
3. `AgentModelResolver` / compactor / subagent: unknown model → session client (same backend, no switch).
4. Compactor synthetic entry claims `OPENAI_API` (`SessionAgentRunner.kt:~300`, `SubAgentRunner.kt:~163`)
   but is only a `modelId` carrier and clients ignore the passed id — harmless today, misleading type.

## Agent Integration

`MainActivity` session (`:736-744`) / `observeExternalSession` → `AgentSession.submit(Op.UserInput)` →
`SessionAgentRunner` (`modelName = config.mainModel`, L82) → `Agent(config: AgentExecutionConfig…,
modelName)` → `AgentTurnRunner.executeTurn` (`Agent.kt:132`) → `TurnPlanningPhaseRunner` (resolve +
`Turn`) → `Turn.runStreaming` → `llmClient.chatWithToolsStreaming(modelId=)`. Provider/client selected
**only** in `SessionLlmBootstrapper` + per-turn `AgentModelResolver`; no other creation sites in main.
**CONFIRMED, no bypass.**

## Phase 2 Integration

`HmxAgent(executor, orchestrator=TaskOrchestrator(), …)` (`HmxAgent.kt:13-19`),
`TaskOrchestrator(planner=DefaultPlanner(), …)` (`TaskOrchestrator.kt:17-19`) hold **no LLM clients**;
`Agent : HmxAgentExecutor` (`Agent.kt:29`). All LLM access flows through the Turn layer above, which uses
the session client. Phase 2 uses the selected provider **by construction**. **CONFIRMED.**

## Phase 3 Integration

`ToolSpec.requiredCapabilities` (`ToolSpec.kt:26`) → `CapabilityManager.canUse` →
`ToolRegistry.getAvailable(manager)` (`ToolRegistry.kt:85-86`) — but `getAvailable` has **zero callers**,
and the LLM tool list is built by `generateResponsesApiTools` (`ToolRegistry.kt:141`), whose sole caller
is `Turn.kt:278` with only an `allowedToolNames` filter. **CONFIRMED: capability gating is NOT applied to
the tools the LLM sees.** Phase 3 does not (yet) affect the selected provider's tool list.

## Security

- `LlmLogger` (prompts, inputs, tool names+descriptions, tool **arguments**, outputs) is gated on
  `BuildConfig.DEBUG` (`LlmLogger.kt:8-19`, early return). Release builds emit none of it. **CONFIRMED.**
- Release logcat provider surface: factory `Log.d` (class/provider/api), bootstrapper `Log.i` routing
  line (provider/modelId/client/api/sanitized host — `ProviderRouting.kt:44-72`, userinfo/query/fragment
  stripped). No keys/tokens/headers. **CONFIRMED by inspection; runtime NOT VERIFIED.**
- `SensitiveDataFilter` (`util/…`) redacts `secret=`, `Bearer`, `Authorization`, token shapes — applied to
  diagnostics overlay/crash paths, not to LLM logs (which rely on the DEBUG gate instead). **CONFIRMED.**
- Credentials at rest encrypted (`AuthStore.kt:45-49`); settings prefs plain (no secrets stored there —
  keys live only in `AuthStore`). **CONFIRMED.**

## Test Coverage

223 test files. Gap matrix (required 27):

| # | Case | Status |
|---|---|---|
| 1 OTHER routing | COVERED (`ProviderRoutingTest`: OTHER→ChatCompletionClient) |
| 2 OTHER never Codex | COVERED (type negations + URL assertions) |
| 3 Codex routing | COVERED |
| 4 OpenAI routing | COVERED (RESPONSE→Responses, CHAT→Completions) |
| 5 OpenRouter routing | COVERED |
| 6 Local LFM | COVERED (factory refusal pins LFMLLMClient-direct rule; `LocalBackendTurnRoutingTest`, `LFMLLMClientTest`) |
| 7 provider switching | COVERED (OTHER↔CODEX same-factory; `AppSettingsProviderTest.updateProvider`) |
| 8 persisted provider | COVERED (store round-trip OTHER/CODEX/null/garbage; state reload) |
| 9 modelId vs key | COVERED (`ChatCompletionClientTest` round-trip asserts; resolver tests) |
| 10 Base URL | COVERED (effectiveBaseUrl; OTHER-missing-URL throws) |
| 11 missing tool-call index | COVERED? new fallback has **no direct unit test** — **MISSING** (only covered indirectly if at all) |
| 12 index present | COVERED (existing streaming tests) |
| 13 multiple tool calls | PARTIAL (accumulator tests exist for Codex; Chat-path multi-index collision under `0L` fallback **MISSING**) |
| 14 empty tool calls | PARTIAL |
| 15 missing content | PARTIAL (`orElse` defaults; no dedicated case found) |
| 16 missing choices | MISSING (dedicated) |
| 17 empty stream chunk | MISSING (dedicated) |
| 18 [DONE] | MISSING (SDK-owned; no pin) |
| 19 malformed JSON | COVERED (Codex parser `opt*`; seed-probe fallback) |
| 20 HTTP errors | COVERED (`OpenAIErrorClassifierTest`, Codex error tests, MissingCredential tests) |
| 21 timeout | COVERED (SocketTimeout retry test) |
| 22 network failure | COVERED (UnknownHost/connectivity classification) |
| 23 Gemini-compatible | MISSING (no Gemini-shaped fixture: index-less deltas) |
| 24 OpenRouter response | PARTIAL (client-type level; no OpenRouter-shaped payload fixture) |
| 25 Codex response | COVERED (`CodexSseParserTest`, `CodexResponseClientTest`, builder tests) |
| 26 API key redaction | PARTIAL (filter unit tests presumably; release-log absence NOT VERIFIED at runtime) |
| 27 OAuth redaction | PARTIAL (same as 26) |

## Real Device Evidence

**BLOCKED.** No hardware, no logcat, no user-provided runtime logs in this session. The canonical
routing line (`Routing: provider=… model=… client=… api=… baseUrl=…`, `SessionLlmBootstrapper`) exists
precisely to make the next device run decisive: it is emitted once per session bootstrap at `Log.i`.
Until a run shows it, the OTHER→Codex report remains a mechanism-confirmed but
**live-unreproduced** report. Mark everything device-dependent **NOT VERIFIED**.

## Confirmed Bugs (in current tree)

1. **Phase-3 tools not applied to LLM list** — `getAvailable()` zero callers; `Turn` uses
   `generateResponsesApiTools` (capability-blind). Severity: medium. `ToolRegistry.kt:85-86,141`,
   `Turn.kt:278`. (Documented; not fixed per audit scope.)
2. **Historical 404 `gpt-5.5`** — seed carried `model_id:"gpt-5.5"` for a nonexistent upstream model
   (proven via `git show 1208558^:…llm_models.json`). Resolved by removal + fallback. Severity: high
   (was). **Closed.**
3. **Pre-fix OTHER-selection erasure** (`updateModel` fallback persisted `glm-5`) — resolved in `c78fb06`
   via OTHER-flavor trust + coherent pair. **Closed.**

## Potential Bugs (mechanism present, trigger unverified)

1. **Codex 400 recurrence**: codex entries carry OpenAI-style ids (`gpt-5.4-codex`→`gpt-5.4`); client sends
   `entry.modelId` verbatim (`CodexResponseClient.kt:59,150`); observed 400 text quotes that exact id.
   If the Codex backend requires Codex-style identifiers, every Codex call 400s. **Needs one live call.**
2. **Index-`0L` multi-call merge**: two concurrent index-less tool deltas share builder `0L`
   (`ChatCompletionClient.kt:~172-176`) → merged/corrupted calls. **Needs Gemini multi-tool fixture.**
3. **HTTP 400/404 actionability**: classified to generic `RuntimeException`; UI shows message text only,
   no invalid-model remediation. Minor.
4. **Fallback seed collapse**: corrupt asset → single-model (glm-5) catalog → mass fallback. Minor/latent.

## Missing Tests

Dedicated: index-less Gemini delta fixture (#11, #23), multi-tool index collision (#13), missing
choices/chunk fixtures (#16, #17), `[DONE]` pin (#18), release-log secret-absence (#26, #27 runtime),
live endpoint assertions (all BLOCKED on device).

## Root Cause Analysis (primary bug)

Necessary and sufficient condition for "UI=OTHER but runtime=Codex": `selectedModel` is a Codex key at
session start while the tab shows OTHER. Three silent producers (R1 tab/model divergence — primary;
R2 selection erasure — closed in `c78fb06`; R3 no tab-tap reconciliation — by design). Single choke
point (`SessionLlmBootstrapper.create` → `factory.create(mainModel)`); no code path in the current tree
routes an OTHER model to Codex (factory map + tripwire + single instantiation site). The remaining
live question is which producer fired on the user's device — answerable only by the new routing log
line on the next device run.

## Recommended Fix Order

1. ~~Model-id plumbing, index guard, override signatures~~ (done: `25ebc1d`, `e268662`).
2. ~~Provider persistence/coherence/guard/log/tripwire~~ (done: `c78fb06` + test fixes).
3. **Device run first**: install APK → OTHER → "hi" → capture `Routing:` line. Decides everything below.
4. If Codex 400 recurs live → correct codex `model_id`s in seed (data fix, needs backend truth).
5. Add Gemini index-less + multi-tool fixtures; harden `0L` merge (e.g. per-chunk builder or id-keyed).
6. Wire `getAvailable(manager)` into `Turn.prepareRequest` (Phase 3 completion).
7. 400/404 remediation UX (invalid-model hint + deep-link).
8. Revisit tab-tap reconciliation UX only with device evidence.

## Authentication Zero-Touch Verification

- `git log -- app/src/main/kotlin/ai/closepaw/auth/`: sole commit `666f9ec` (baseline). All provider
  commits (`25ebc1d`, `e268662`, `c78fb06`, `7873a47`, `c202928`) touch zero auth files
  (`git diff --stat -- auth/` empty, re-verified this session).
- Auth read-only findings: routing depends on auth only through `has/requireApiKey/codexHeaders/
  generation`; no auth state influences provider *selection*. `codexHeaders` is suspend (per-request
  refresh); factory caches per auth generation so rotation/account-switch rebuilds clients.
- Untouched and to remain untouched: `auth/AuthStore.kt`, `auth/AuthCredential.kt`,
  `auth/AuthErrors.kt`, `auth/OpenAIOAuth.kt`, `auth/OpenAiSignIn.kt`, OAuth headers/endpoints,
  login/signup/account flows. No auth rewrite proposed.

## Final Verdict

1. **Why does selecting OTHER still call Codex?** The tab is not the selection; the persisted
   (`selectedModel`, `selectedProvider`) pair is. A stale Codex `selectedModel` under the OTHER tab
   routes Codex — via `SessionLlmBootstrapper.kt:56→factory.create(mainModel)`. Exact-line cause of the
   *routing* (model-led by design) vs the *divergence* (R1/R2/R3 above) are distinguished in §Root Cause.
2. **Where is provider selection lost/overridden?** Nowhere in the current tree: no overwrite path found
   (exhaustive search); historically in `updateModel`'s fallback (closed). Live divergence = tab/model
   skew at session start.
3. **Codex selected because of?** Model state (Codex key as `mainModel`). NOT provider state, NOT OAuth
   credential, NOT cache, NOT factory, NOT bootstrap, NOT fallback — each ruled out with evidence above.
4. **Does OTHER create ChatCompletionClient?** CONFIRMED (`LLMClientFactory.kt:111-122` + tests).
5. **Can OTHER instantiate CodexResponseClient?** CONFIRMED no (mapping + tripwire + single site `:106`).
6. **Why "index is not set" on Gemini?** `tcDelta.index()` (`ChatCompletionClient.kt:~173`) is an SDK
   required-field accessor; Gemini omits `tool_calls[].index`. Guarded post-fix.
7. **Catalog keys vs modelId on the wire?** modelId everywhere post-`25ebc1d` (three builders cited).
8. **Are model IDs provider-specific?** EXPECTED yes in practice (e.g. `z-ai/glm-5` vs `gpt-5.4`), but the
   catalog does not enforce namespaces; codex entries reuse OpenAI-style ids (see Potential Bug 1).
9. **Are mismatches detected?** CONFIRMED: session guard (`ProviderRouting.kt:27`) + factory tripwire
   (`LLMClientFactory.kt` bottom) + `MissingCredential` paths.
10. **Silent provider fallback?** CONFIRMED absent.
11. **Silent model fallback?** Only the loud (`Log.w`) unknown-key→`glm-5` recovery; OTHER-flavored never.
12. **Does Phase 2 use the selected provider?** CONFIRMED yes (no client holdings; Turn-layer only).
13. **Does Phase 3 use the selected provider?** Provider yes; capability filtering NO — `getAvailable`
    unwired (Confirmed Bug 1).
14. **CONFIRMED bugs?** Phase-3 list gap; historical 404 (closed); historical selection erasure (closed).
15. **POTENTIAL only?** Codex-400 recurrence; `0L` multi-call merge; 400/404 UX; seed-collapse.
16. **Safest fix order?** §Recommended Fix Order (device proof before any further change).
17. **Untouched?** All of `auth/` (5 files), OAuth endpoints/headers, login/signup, token handling —
    verified via git history; no rewrite proposed.
