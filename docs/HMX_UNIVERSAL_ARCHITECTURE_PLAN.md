# HMX Universal Architecture Plan

**Status:** READ-ONLY audit + implementation plan. No source modified, nothing committed,
working tree preserved exactly. This document is the only artifact created.
**HEAD:** `7873a47` + 19 modified / 26 new uncommitted files (inventoried §4).
**Auth:** ZERO TOUCH throughout — auth findings are documentation-only (§28).
**Device context:** low-RAM Android; no hardware available in this session, so every
device claim is marked accordingly. Nothing below is accepted without the matrix in §31.

---

## 1. Executive Summary

HMX already has the right skeleton: a universal agent loop (perceive → plan → act →
observe → verify-ish), a provider-agnostic LLM interface, model-led provider routing with
fail-fast guards, and a capability/filter/diagnostics substrate. The core does **not**
need rewriting. What is missing is **proof at the edges**: writes are not exactly
verified, success can be claimed without evidence, persistence dies with the APK,
restore can surprise, capabilities don't reach the tool list effectively yet, and
perception is captured expensively and repeatedly.

The plan: keep the tree, harden the six seams (text verify, outcome gate, parser
generalization, backup/restore, capability wiring, perception budget), validate each on
a physical device, and only then extend (self-state tools, cross-app matrix). No Room
migration, no provider rewrite, no app-specific core, no new frameworks.

---

## 2. Current Architecture

```
Goal (text)
 → SessionAgentRunner (AgentExecutionConfig{modelName, structuredTask?})
 → Agent → AgentTurnRunner ─┬─ TurnPlanningPhaseRunner (AgentModelResolver → Turn → LLM)
                            └─ TurnExecutionPhaseRunner (ToolRouter → handlers → observation → history)
 → Tools: mobile_action (a11y), open_app (Intent), browser_script (CDP), system_button,
          complete_task, write_todos, delegation, local skills…
 → Platform: AccessibilityPlatform (nodes/gestures) | VirtualDisplay | Fake (tests)
 → Observation: captureScreen → Perceptor.toPromptJson → history → next turn
 → LLM: LLMClientFactory → {OpenAIResponseClient | ChatCompletionClient |
    CodexResponseClient | LFMLLMClient} → normalized LLMToolCall/LLMStreamEvent
 → Persistence: files/sessions/*.json + checkpoints; prefs in agent_prefs; secrets in auth_store
```

Single choke points (verified): provider routing = `SessionLlmBootstrapper` + factory;
tool execution = `ToolRouter`; completion = `decideTurnOutcome`; restore = `AgentSession.reload`.

---

## 3. Current Working Features

- Real-app control via Accessibility (tap/type/scroll/swipe, node + tap-focus fallbacks).
- Intent-based app launch (`OpenAppTool` → `platform.launchApp`, `OpenAppTool.kt:209`) — genuine multi-strategy precedent.
- Gemini/OTHER routing proven on device (ChatCompletionClient path).
- Model-led provider routing with session guard + factory tripwire + routing log line.
- Checkpoint save/resume (schema v2, IDLE_READY/CLOSED), UI chat list backed by files.
- Compaction, subagents (model-inheriting), skill system (bundled + user), memory recall per package.
- Approval flow in ToolRouter (timeout-cancel), diagnostics overlay/bus, trace recorder.
- 223 test files; CI runs unit tests + debug APK.

---

## 4. Current Uncommitted Changes

Assessment standard: KEEP (as-is) / MODIFY (needs change before merge) / POSTPONE (good idea,
wrong time). All are additive; none touch `auth/`.

| Change | Tries to accomplish | Verdict |
|---|---|---|
| `TaskOutcomeState`, `TaskVerification`, `TaskVerificationTracker`, `TaskDiagnostics`, RuntimeEventBus additive fields | Outcome lifecycle + policy + cross-turn memory + typed diagnostics | KEEP |
| `TextVerification`, `FieldContent`, platform `readTextAt/readFocusedText`, performer hint guard | Exact write verification; kill "Messagehi" class | KEEP; MODIFY follow-up: `verifyTypedText` treats unreadable as legacy-pass — correct for VD/fakes, but needs a device-run confirmation that reads work on real WhatsApp fields |
| `TypeExecutor` verify+retry+router wiring | Strategy-switched retry, no false success | KEEP; MODIFY: `targetKey` uses coordinates (index-like identity — at odds with P15 semantics; acceptable short-term, replace with semantic key later) |
| `StrategyRouter` | Explicit strategy table, no-repeat rule | KEEP |
| `ToolCallDeltaAccumulator` + ChatCompletionClient wiring | Order-based index-less correlation | KEEP; MODIFY: add an explicit multi-chunk interleave test with SDK-shaped fixtures before merge (unit tests use hand-rolled deltas — good but one level removed) |
| `StructuredTask` parser | Intent/app/entities/content extraction | MODIFY before merge: regex-primary with `"whatsapp"` as a verb keyword is app-specific leakage (only WhatsApp mention in `main/`); keep the **struct**, change the **producer** to LLM-validated extraction with the parser as proposer/fallback |
| `OutcomeVerifier` | Package/field/bubble/recipient checks | KEEP; bubble+recipient heuristics require device validation (§31) |
| `HmxSelfState` + prompt-line wiring | Self answers from structured state | KEEP; watch prompt-token cost — gate to self-requests if bloat observed |
| `ChatBackup`, `ChatPersistenceManager`, `BackupMediaMirror`, `backup_rules.xml`, `allowBackup=true` | Uninstall survival | KEEP with MODIFICATIONS: wire `currentAppVersion()` (currently null stub); confirm `agent_prefs` carries no key material (verified: model/flags/URLs/ids only); SAF **import UI still missing** — manager+mirror mergeable, UI wiring is Phase 2 work |
| `AgentSession.reload` overrides + MainActivity wiring + snapshot `provider` | Follow-global restore + ChatRestore log | KEEP (semantics decision §20; legacy null-override path preserved) |
| `SessionServices` bus + capabilityManager fields | Diagnostics + live capabilities | KEEP (defaulted, additive) |
| `AndroidDeviceCapabilitySource` | Honest capability states | KEEP; UNKNOWN for CDP/Shizuku/VD/BG is honest fail-closed; no tool declares requirements yet → zero behavior change |
| Turn capability filter + planner wiring | Gate LLM tool list | KEEP (null-safe no-op until requirements declared) |
| `ExecutionPhaseResult` extensions + `decideTurnOutcome` gate + runner tracker | Block false success incl. cross-turn | KEEP; failure-completion path untouched; loops bounded by existing budgets |
| `AgentExecutionConfig.structuredTask` + parse-in-runner | Task context for verification | KEEP subject to parser verdict above |
| All new tests (10 files) | Pin the new contracts | KEEP |
| `docs/PROVIDER_FORENSIC_AUDIT.md` | Prior audit record | KEEP as history; do not rewrite |

---

## 5. Agent Core Analysis

`SessionAgentRunner` builds one `Agent` per task; `Agent` owns one `AgentTurnRunner`;
turns run plan→execute→decide. Budgets exist (`evalTurnBudget`, `MAX_RECOVERABLE_RETRIES=1`,
compaction-failure circuit breaker ×3 — `Agent.kt:32-33,103,169`). Strengths: single
executor/decision points, immutable-ish turn state, trace+events throughout. Gaps closed by
uncommitted work: completion gate (was advisory prompt text in `CompleteTaskTool`), cross-turn
memory (new tracker), structured context (new optional field). No core rewrite needed.

---

## 6. Universal Android Perception

Today: `AccessibilityPlatform.captureScreen()` → `ScreenSnapshot(elements)` →
`Perceptor.toPromptJson()` with `PerceptorFilterConfig` (maxElements=500, size/visibility
filters, keyboard filtering, row snapping). 19 capture-related call sites; post-action analysis
captures up to 3× per action (300/500/1000 ms settles). Hierarchy + optional screenshot;
`PerceptionElement` carries text/desc/hint/class/state/bounds/focus — sufficient for semantic
resolution without raw dumps. No app-specific branches found in perception code.

---

## 7. Universal Target Resolution

`TargetResolver`: ElementIndex > text(+index) > x/y; text matches merged text, then
description/hint; coordinate fallback flagged explicitly. Node finding prefers editable/actionable
semantics (`findNodeAtLocation` checks `isEditable` OR `ACTION_SET_TEXT`). Weaknesses (known):
index-first priority, coordinate keys, no re-resolution after navigation inside some paths.
Direction: keep resolver, add re-resolve-on-stale + semantic-first ordering as refinements —
not a new system.

---

## 8. Universal Action Execution

Executors per action (tap/type/scroll/…) over `AndroidPlatform.performAction(UIAction)`; intent
path exists (`launchApp`); gesture injector; VD mode disables tap-focus (correct). Strategy
selection was implicit per-executor; uncommitted `StrategyRouter` makes it explicit for type
with a generic registry for the rest. No `ANDROID_API`-beyond-intents exists yet — correctly
scoped as future per-capability work, not a gap to fill blindly.

---

## 9. Text Input Architecture

Chain: `mobile_action(type)` → validate (`input_text` required, `clear` default false) →
`TypeExecutor` → `SetTextOnNodeAt` → tap→`SetTextOnFocused` → `NodeActionPerformer.setTextOnNode`
(APPEND semantics when `clear=false`) → weak contains-check → success. No IME injection
(`performEnterKey` is separate). Root cause of "Messagehi" (§Q3/Q4): placeholder exposed via
`node.text` with `isShowingHintText==false` → treated as existing content → glued. Uncommitted
work fixes both layers (hint-equality guard in performer; exact re-read + clear-escalated retry
+ fail-closed in executor). Remaining risk: unreadable-field legacy-pass — device-gated.

---

## 10. Screen Reading Performance

Cost drivers: full-tree capture per action × up to 3 post-action captures; full 500-element JSON
into every planning prompt; screenshots repeated alongside trees. Existing levers:
`PerceptorFilterConfig` budgets, keyboard filtering, `UiChangeDetector` compare. Plan (§35):
(1) measure first (add capture/serialize timings to diagnostics); (2) reuse the pre-turn snapshot
across same-turn captures when fingerprint unchanged; (3) shrink prompt trees (collapse
non-interactive subtrees already partially done via `interactiveKeepRatio`); (4) screenshots
on-demand (verification/visual questions) rather than default; (5) NEVER cache across actions
without fingerprint check (stale-node risk dominates). Low-RAM rule: bound everything by count
and bytes, never by hope.

---

## 11. Task Understanding

Current uncommitted `StructuredTaskParser` is regex-primary (verb list + `on/in/via` + quoted
payload + capitalized spans). Verdict: **useful struct, wrong producer**. Regex must stay a
proposer, never the authority: it misparses ellipsized speech, non-English orderings, and embeds
app-specific triggers (`"whatsapp"` as verb). Target: model fills/validates the struct during
planning (it already has goal + app list via `getInstalledApps`); deterministic parser remains
as fallback + test fixture. Keep all eight task classes; resolve app labels against installed
apps at execution, never from a hard-coded list.

---

## 12. Task Verification

Target states map cleanly onto the new `TaskOutcomeState` + `TaskVerification` policy +
`OutcomeVerifier` checks. Rules to lock: tool success ≠ task success (gate); fresh observation
only (never planning snapshot); typed-but-unsent ≠ sent (non-editable bubble rule); package
checks exact-match; unverifiable ⇒ `OUTCOME_NOT_VERIFIED`, never pass-by-default — except the
documented unreadable-field legacy path, which must stay visible in trails. Informational turns
complete freely.

---

## 13. Recovery / Replanning

Existing: retry-with-longer-settle captures, tap-focus fallback, error→recoverable→continue,
compaction circuit breaker, turn budgets. Add (small): strategy-exhaustion stop (router null ⇒
fail, never loop — already the design), re-resolve-then-retry on stale targets, and record
failed (target,strategy) pairs per task to avoid cross-turn repeats. No new framework.

---

## 14. HMX Self-Understanding

Today: none structural — the model sees HMX UI as opaque pixels/tree like any app. Uncommitted
`HmxSelfState` (settings/session/capabilities/screen summary + prompt line) is the right shape:
read-only, sourced from existing truths, no second source. Still needed: read-only self answers
first (model/provider/chats/diagnostics/last-failure from state, verified against UI), and only
later self-actions (switch provider/model = persist + verify persisted value; open settings =
intent). Self-actions must reuse the same verify-then-claim pipeline as third-party tasks.

---

## 15. Universal App Support

The loop is already app-agnostic (package comes from the OS, targets from semantics, launch via
Intent). Only app-specific main-code found: the parser verb keyword (§11 — to remove). Skills
(`agent/cognition/skills/…`, bundled installer) are correctly positioned as optional extensions;
keep them out of core control flow. No per-app core branches will be accepted.

---

## 16. Provider Architecture

Model-led, single choke point, five providers (OTHER/OPENAI_API/OPENAI_CODEX/OPENROUTER/LOCAL_LFM),
per-entry `api` selects Responses vs Chat Completions. Verified working on device for OTHER/Gemini.
No changes proposed — this section exists to forbid them: provider selection stays independent
from Android control (§33).

---

## 17. Provider Adapter Design

Already the de-facto shape: `LLMClient` interface → per-provider client → normalized
`LLMToolCall`/`LLMStreamEvent` → agent core. Provider layer owns base URL, model id, auth
dependency, wire format, streaming, index quirks, errors, retry. Keep. Only open item: codex
entries carry OpenAI-style ids — a **data** question for backend truth, not an adapter redesign.

---

## 18. Model Catalog Design

Seed JSON + `other-custom` synth (validated URL/modelId) + discovered entries scoped by current
base URL; overlay-wins merging; `withBaseUrlOverrides` never clobbers explicit entry URLs.
Resolution is total-map lookup with loud failures. No redesign; data edits (ids/entries) only
with backend evidence.

---

## 19. Provider Persistence

`agent_prefs`: selectedModel + selectedProvider (nullable, garbage-tolerant) + backend + URLs/ids.
Coherence rule (in-tree): model wins loudly on conflict; OTHER-flavored keys trusted; fallback
moves provider with model + `Log.w`. AuthStore untouched and unlinked (keys only). Semantics:
global selection is the default; per-chat pinning does NOT exist as a feature — see §20.

---

## 20. Existing Chat Restoration

Cause of Codex-on-restore (traced, not guessed): checkpoints saved `mainModel` without provider;
reopen rebuilt config from snapshot while global selection had moved on — so an old Codex chat
resumed Codex. Product semantics decided: **history is provider-agnostic; runtime follows current
global selection** (uncommitted `reload` overrides + `ChatRestore` log line with
saved/provider/model vs resolved). Rationale: no per-chat pin UI exists, so "pinning" was an
accident, not a feature; silent Codex billing is the worst outcome. Legacy null-override path
preserved. Requires device confirmation that reopened chats log the expected resolved pair.

---

## 21. Chat History Persistence

Stores today: `files/sessions/session-*.json` (full `SessionRecord`: messages, tool blocks,
screen-state paths, summary, metadata) + `context-*.json` checkpoints; UI list via
`SessionHistoryManager`. Disappearance cause: everything durable is app-private +
`allowBackup="false"` + no export feature (verified). Uncommitted `ChatPersistenceManager`
(export/verify/restore/reconcile over streams, atomic renames, rollback-on-interrupt) +
`ChatBackup` envelope (version, SHA-256, dup/corrupt guards) is the right architecture over the
existing store — **no Room migration** (unnecessary risk/size; JSON store works and must be
migrated losslessly, never replaced).

---

## 22. Backup / Restore

Two mechanisms, both secret-free by construction: (1) Auto Backup via `backup_rules.xml`
(sessions + agent_prefs in; `auth_store`, skills, diagnostics, cache out) — flipped
`allowBackup` needs release sign-off; (2) user-visible Downloads mirror + SAF import for
explicit control. Reconcile rule: union by sessionId, newer `lastUpdated` wins, ties keep live —
never overwrite newer, never delete. Missing piece (postponed, not skipped): settings-screen
Export/Import buttons + restore-chooser UI. Auto-restore runs only when the store is empty.

---

## 23. Capability Manager

`CapabilityManager.canUse` (UNKNOWN deny) + `ToolRegistry.getAvailable` existed but unwired —
uncommitted work wires a live `AndroidDeviceCapabilitySource` (real overlay check, real Termux
mapping, honest UNKNOWN elsewhere) through `SessionServices` into `Turn.prepareRequest`. Zero
behavior change today (no tool declares requirements) — the mechanism is armed, not the policy.
Rollout rule: declare requirements per tool ONLY with device proof that the source state matches
reality; never gate working tools on UNKNOWN probes.

---

## 24. Tool Registry

Registry + schemas + `generateResponsesApiTools(allowlist ∩ capabilities)` (as wired). Keep the
`strict:false` rationale documented (optional params). No structural change needed.

---

## 25. Tool-Call Streaming

Per-client parsers (Responses item events; Chat delta builder map; Codex opt*-based accumulator).
Uncommitted `ToolCallDeltaAccumulator` replaces the Chat inline map with indexed + occurrence-order
unindexed correlation — correct direction (kills the merge-all-to-0 bug class), Gemini-compatible
by construction for the single-call case. Pre-merge requirements: SDK-shaped multi-chunk
interleave test, drain-order parity note (insertion order preserved — matches old iteration
semantics), malformed-argument policy (currently verbatim accumulation; JSON validation happens
downstream at execution — acceptable, documented).

---

## 26. Diagnostics

`RuntimeEventBus` (bounded 64) + new `TaskDiagnostics` lifecycle (11 event types) + provider/model/
verification/action/result fields (additive) + routing log line + ChatRestore line. Emission points:
orchestrator (task lifecycle), turn loop (action + verification), executor trails. Secret policy:
ids/names/hosts only; full bodies stay behind the DEBUG-gated `LlmLogger`. Remaining: attach
capture/serialize timings (§10) and strategy selections.

---

## 27. Memory Architecture

`MemoryStore` (filesDir markdown) + `MemoryRecaller.recall(package)` injected into planning prompts
+ auto-retained operational notes on failure (`Agent.kt:145`). Correct shape (per-app, bounded,
user-visible dir). No change proposed; consider per-task scoping only if prompt bloat is measured.

---

## 28. Security

Auth zero-touch: `auth/` (AuthStore, credentials, OAuth, sign-in) is excluded from this program;
routing depends on it only via `has/requireApiKey/codexHeaders/generation`. Verified: secrets in
encrypted prefs; backups exclude `auth_store`; logs carry no keys (release paths inspected;
runtime confirmation still owed to §31). Guardrails to keep: no credential in prompts/logs/
backups/traces; approval timeouts; BLOCKED-app masking (`maskIfBlocked`); no bypass of Android
permission boundaries; Shizuku/Termux behind their own gates.

---

## 29. Performance

Low-RAM rules: keep full-tree captures but budget them (measure → reuse-on-same-fingerprint →
shrink prompt JSON → screenshots on demand); keep 500-element cap and revisit downward with data;
bounded event bus/ring buffers already the pattern — extend it to any new per-turn allocations
(trails, verification lines are small and bounded); no new background services; no Room; no extra
processes. Cold-start cost of restore-if-empty is one directory listing — negligible.

---

## 30. Testing Strategy

Keep the established seams: pure-logic JVM tests (policies, parsers, accumulators, reconcile,
guards), fake-platform executor tests (scripted reads/actions), JSON catalog fixtures, TemporaryFolder
storage tests, mockk AuthStore. Add per phase: Gemini-shaped streaming fixtures, multi-app fake
trees (message app, browser, settings, dialog, scroller), interrupted-restore (read-only dir),
exhaustion/loops (strategy router), prompt-line golden assertions. No device in CI — device matrix
is manual (§31) with log-line acceptance.

---

## 31. Real Device Validation Matrix

Each row needs the stated log/observable evidence — "should work" is not a result.

| # | Case | Steps | Accept evidence |
|---|---|---|---|
| 1 | New chat, Gemini | send hi-like task | `Routing: provider=OTHER … client=ChatCompletionClient` + task completes |
| 2 | Existing chat after provider switch | switch → reopen old chat | `ChatRestore:` shows saved≠resolved, runtime = current selection |
| 3 | WhatsApp send | "send hi to Aditya Maurya" | input `=hi` exactly; bubble `=hi`; report success only after; trail shows verify lines |
| 4 | Typing adversarial | hint-like fields, existing text | mismatch → retry → exact or OUTCOME_NOT_VERIFIED, never false success |
| 5 | Uninstall/reinstall | export → uninstall → install → auto-restore | every conversation/message present; counts match export report |
| 6 | Corrupt/duplicate restore | damaged file; import twice | rejected / idempotent; live data intact |
| 7 | Provider matrix | each configured provider, one task | correct client + endpoint per provider; no cross-routing |
| 8 | Capability honesty | airplane/permission-denied states | gated tools hidden; working tools unaffected |
| 9 | Self tasks | "which model", "open settings", "show chats" | correct answers/actions from state, UI-verified |
| 10 | Cross-app spot | browser search, settings toggle, file open | per-app verify lines pass; no app-specific core changes needed |
| 11 | Log hygiene | logcat across 1–10 | no keys/tokens/bodies beyond DEBUG expectations |

---

## 32. Migration Strategy

No data migration required for: additive fields (defaults everywhere), new tables (none),
prompt-line additions, diagnostics fields. Required care: `ConversationConfigSnapshot.provider`
(default null → old snapshots read fine); backup envelope v1 with `ignoreUnknownKeys` forward
path; manifest flip is policy, not data. Rule: additive-only schema changes; any destructive
migration needs its own reviewed plan + backup-first precondition. Existing chats must open
unchanged (history path untouched).

---

## 33. Proposed Target Architecture

```
Goal → StructuredTask(proposed by parser, validated by LLM) → Planner/Orchestrator
 → App+Device State (snapshot summary + self-state) → CapabilityManager (live source)
 → TargetResolver (semantic, re-resolving) → StrategyRouter (no-repeat, bounded)
 → Executor (platform API) → Fresh Observation → OutcomeVerifier (criteria from task)
 → VERIFIED ? success : recover-limited / TASK_FAILED
Provider plane (independent): selection → catalog → factory → normalized tools.
Persistence plane (independent): sessions → checkpoints → versioned backup → restore.
```

This is the current architecture plus the six hardened seams — not a new system.

---

## 34. Dependency Graph

```
MainActivity/Service → AgentSession → SessionServices → {bootstrapper→factory→clients,
  tooling→registry/router, history, platform, capabilities, bus}
SessionAgentRunner → Agent → AgentTurnRunner → {planning (LLM+catalog+resolver),
  execution (router→executors→platform)} → decideTurnOutcome (policy+verification)
TypeExecutor → platform reads; OutcomeVerifier → platform+snapshot
ChatPersistenceManager → SessionStorage + AppSettingsStore + mirror
UI settings → AppSettingsState → store/prefs/catalog repo
```

Acyclic except history↔runner (append-only, safe). New edges (services→bus/capabilities,
runner→structured parse) follow existing directions.

---

## 35. Implementation Phases

- **Phase 0 — Stabilize (gate for everything):** review uncommitted diff as §4, CI green,
  keep/revert decisions recorded. No new behavior.
- **Phase 1 — Truth in acting:** text verify, outcome gate, tracker, accumulator fixtures,
  parser producer change (§11). Device rows 3–4.
- **Phase 2 — Truth in memory:** backup manager merge, export/import UI wiring, manifest
  policy sign-off, reinstall test (row 5), corruption tests (row 6).
- **Phase 3 — Truth in routing/restore:** device rows 1–2, 7; codex-id backend truth.
- **Phase 4 — Cost of seeing:** §10 instrumentation → budgets → on-demand screenshots.
- **Phase 5 — Self + matrix:** read-only self answers, then self-actions; cross-app rows 8–10.

---

## 36. Phase Gates

No phase starts until the previous gate is met: P0 CI green + decision log; P1 device rows 3–4
pass with logs; P2 row 5–6 pass with counts; P3 rows 1–2,7 pass; P4 measurements show
p95 prompt-bytes down without task-success regression; P5 matrix green. Any gate failure
returns to the phase — never forward.

---

## 37. Files To Keep

All new kernel/test files listed in the header inventory; `backup_rules.xml`;
`PROVIDER_FORENSIC_AUDIT.md` (history); every existing file outside §38.

---

## 38. Files To Modify

Only as sequenced: `StructuredTask.kt` (producer change); `TypeExecutor.kt`
(device follow-ups); `ChatPersistenceManager.kt` (`currentAppVersion` wiring);
`MainActivity.kt` (export/import UI wiring, Phase 2); `AndroidManifest.xml` policy only
(already flipped — needs release sign-off, no further edits); per-tool
`requiredCapabilities` declarations (Phase 3, one tool at a time with proof);
`PerceptorFilterConfig` budgets (Phase 4, measured). Everything else stays read-only
until its phase.

---

## 39. Files To Avoid

`auth/**` (absolute); provider clients/factory/catalog (done, proven — data-only changes
allowed with backend evidence); `ToolRouter` approval core; `SessionStorage` format;
`Agent.kt` loop structure; any file for stylistic refactors. No Room, no DI framework,
no new processes/services, no analytics SDKs.

---

## 40. Risks

1. **Prompt-line + verification history bloat** — bounded, measurable; gate in P4.
2. **Capability rollout hides working tools** — mitigated by per-tool proof rule (§23).
3. **Unreadable-field legacy-pass masks real failures** — visible in trails; device-gated.
4. **Backup restores user does not expect** — only-when-empty rule + counts logged.
5. **Heuristic verifier false passes** (bubble/recipient) — device matrix is the control.
6. **Parser overreach** — contained by LLM-validated design (§11).
7. **Low-RAM pressure from captures** — budgets before features (§10).
8. **Scope creep into per-app skills** — skills stay optional extensions (§15).

---

## 41. Acceptance Criteria

1. Self-state questions answered from state (§14). 2. HMX UI navigated semantically.
3. NL requests parsed (validated struct). 4. Target apps identified via OS, not lists.
5. Accessible UI inspected without full dumps by default. 6. Strategy chosen per capability.
7. Actions executed via safest available mechanism. 8. Re-observation after every actuation.
9. Outcomes verified against criteria; unverifiable ⇒ not success. 10. Bounded recovery,
no blind repeats. 11. No false success (gate + matrix row 3). 12. Provider routing correct
(rows 1–2, 7). 13. Restore follows global selection with log proof (row 2). 14. Reinstall
recovery with counts (row 5). 15. Gemini path unregressed (row 1). 16. CI green + no
perf/task-success regression (P4 measures). 17. `git diff --name-only` shows zero `auth/`
paths — verified before every commit.

---

## 42. Recommended Implementation Order

Phase 0 → parser producer change + accumulator fixtures (small, de-risking) → device rows
3–4 → backup UI + manifest sign-off → device rows 5–6 → device rows 1–2, 7 (+ codex-id
truth) → perception budgets → self answers → self actions → cross-app matrix. Each step
lands behind its gate; anything failing returns, never compounds.

---

## Appendix A — Analysis Questions (explicit answers)

1. **Any app without hard-coding?** Yes — loop is package-driven and semantic; only violation
   found is the parser's `"whatsapp"` verb keyword (to remove).
2. **App-specific parts?** That keyword; bundled/user skills (correctly optional); bubble/recipient
   heuristics (app-agnostic shapes, device-gated).
3/4. **Typing cause / "Messagehi"?** Placeholder exposed as `node.text` with hint flag false +
   append-mode + contains-check. Exact chain in §9.
5. **What prevents false completion?** Uncommitted gate (same-turn + cross-turn tracker) + exact
   verification; previously only advisory prompt text.
6. **Verification weak spots?** Unreadable-field legacy-pass; heuristic bubble/recipient; SDK-owned
   `[DONE]`/framing unpinned.
7/8. **Slow reading / faster safely?** Full-tree × up-to-3 captures into every prompt; fix by
   measure → fingerprint-reuse → shrink JSON → on-demand screenshots. Never cache across actions
   unchecked.
9/10. **HMX self-understanding today / needed?** None structural today; need read-only state +
    prompt line (in tree) then verified self-actions.
11/12. **Multi-provider cleanly? Provider-specific vs universal?** Yes — factory+normalized events
    proven; wire format/index quirks stay per-client, agent sees normalized calls.
13. **Restore-Codex why?** Saved-model rebuild while global moved on (§20); fixed by follow-global
    + log (uncommitted), pending device proof.
14. **Persistence semantics?** Global default; no per-chat pin (no such feature exists); history
    always preserved.
15/16. **History loss / safest architecture?** App-private files + `allowBackup=false` + no export;
    safest = existing store + versioned checksummed backup + Auto Backup rules + reconcile (in tree).
17. **Backup auto-maintained?** Not yet: manager exists, mirror exists, auto-restore-if-empty wired;
    scheduled export + import UI still missing (Phase 2).
18. **CapabilityManager filters LLM list?** Now wired (uncommitted) but effectively no-op until
    tools declare requirements — by design, to avoid regressions.
19. **Index-less streaming?** Single-call safe (0L→ordinal design); multi-call needs the pending
    SDK-shaped fixture test before merge.
20. **Generalize before features?** Parser producer (§11) and perception budgets (§10) — everything
    else is ready behind gates.
