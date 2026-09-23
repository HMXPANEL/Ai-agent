# Phase 2 — Completion Report

## Objective

Document the REAL tool-calling architecture: model selection → prompt → LLM → tool-call parsing → routing → execution → result → continuation; all providers/clients/retry; all 14 canonical tools with registration/policy/capability/tests.

## Documents Reviewed

`doc/main/infra/tools.md`, `doc/main/infra/llm.md`, `doc/main/infra/tool/mobile_action.md`, `docs/audit/02` (§§LLM/Tools), `docs/audit/03` (§B items 1–2), `docs/audit/05` (#3,4,6,11), `docs/PROVIDER_FORENSIC_AUDIT.md`.

## Source Areas Reviewed

`tool/ToolName.kt` (14 canonical + Unknown; screen-changing flags), `tool/ToolRegistry.kt`, `tool/ToolRouter.kt:64-329`, `tool/PolicyEngine.kt`, `tool/Capability*.kt`, `tool/AndroidDeviceCapabilitySource.kt`, `tool/impl/*` (14 impls), `tool/action/StrategyRouter.kt`, `session/SessionToolingBootstrapper.kt:62-129`, `session/SessionServices.kt:185-268`, `session/SessionAgentRunner.kt:77-199`, `agent/Turn.kt:73-341` (run/runStreaming/prepareRequest/convertToToolCallRequest/arbitration), `agent/TurnToolPolicy.kt`, `agent/AgentModelResolver.kt:21-60`, `agent/SubAgentRunner.kt:100`, `llm/LLMClient.kt`, `llm/LLMClientFactory.kt:53-124`, `llm/OpenAIResponseClient.kt`, `llm/ChatCompletionClient.kt`, `llm/CodexResponseClient.kt`, `llm/LFMLLMClient.kt`, `llm/ChatCompletionInterop.kt`, `llm/ToolCallDeltaAccumulator.kt`, `llm/LeapFunctionInterop.kt`, `llm/CloudLlmRetry.kt`, `llm/CloudStreamRetryRunner.kt`, `llm/CloudStreamRetryPolicy.kt`, `llm/OpenAIErrorClassifier.kt`, `session/SessionLlmBootstrapper.kt:28-109`.

## Changes Made

1. `doc/main/infra/tools.md`: added missing `activate_skill` row (conditional registration when catalog non-empty) — table now covers all 14 canonical tools.
2. `doc/main/infra/tools.md`: new `CapabilityManager (Phase 3)` subsection (7 values, fail-closed, 3 declaring tools, advertisement-time-only + no exec-time re-check, coexistence with `DefaultBrowserScriptCapabilityGate`).
3. `doc/main/infra/tools.md`: new three-layer distinction (LLM tool call vs ToolRouter execution vs platform operation) above the lifecycle diagram.
4. `doc/main/infra/llm.md`: Session Bootstrap extended with `AgentModelResolver` miss-fallback (session client, vision=false), no-cross-provider-fallback rule, subagent inheritance, global (not per-chat) routing.

## Documentation Corrections

- Phantom-tool risk closed for `activate_skill` (was implemented+registered but absent from the normative table).
- Capability system previously documented only in phase reports; now normative in `tools.md`.
- llm.md already correctly covered 4 clients, interop, local semantics, factory routing, retry — verified, no changes needed there beyond the resolver note.

## Implemented Features Confirmed

All 14 tools traced definition→registration→execution→policy→tests (3-stage wiring: bootstrapper 9+conditional skill; services +memory/browser; runner +delegate/ask_user). 5 providers / 4 clients / model-led routing confirmed. Streaming-only production path (`runStreaming`); non-stream `Turn.run()` used only by `Compactor`. No orphan tool. `OAuthCodexValidator` confirmed dead (documented in `04`, queued wire-or-delete P2).

## Missing / Deferred Features

No new gaps. Exec-time capability re-check still absent (P1, now normatively documented as absent).

## Contradictions Resolved

None in scope (tool/policy counts consistent; `isScreenChanging` set matches `tools.md` non-screen list + `ToolName.kt`).

## Historical Documents Preserved

Yes — untouched.

## Tests / Validation Performed

No local builds per operator instruction. Static: zero source files in diff; tool table cross-checked against `ToolName.kt` sealed set (14/14 + Unknown); registration sites re-read; diff reviewed.

## Remaining Issues

`LOCAL_LFM` factory-throw-by-design still only documented in code; one-line note queued for Phase 8 sweep.

## Git Commit

`docs(phase-2): document llm and tool calling`

## Git Push

BLOCKED — invalid credentials (see Phase 0). COMMITTED ONLY.

## Phase Status

COMPLETE
