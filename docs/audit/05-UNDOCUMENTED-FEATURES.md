# Undocumented Features — 05 (implemented but missing from docs)

| # | Implementation | Location | Docs gap | Action |
|---|---|---|---|---|
| 1 | `TaskVerificationTracker` cross-turn unresolved-actuation | `agent/TaskVerificationTracker.kt`, `AgentRuntimeTypes.kt:103-160` | `doc/main/*` verification story describes per-turn only; cross-turn hole-closing undocumented | Document in `agent/loop.md` |
| 2 | `TextVerification` typed-type verification (EXACT/MISMATCH/STILL_HINT) | `tool/action/TextVerification.kt`, `TypeExecutor.kt:364` | `mobile_action.md` covers verify timing but not text-verification states | Extend `mobile_action.md` |
| 3 | `ChatCompletionInterop` + `ToolCallDeltaAccumulator` index-less (Gemini-shape) assembly | `llm/ChatCompletionInterop.kt`, `llm/ToolCallDeltaAccumulator.kt` | `infra/llm.md` lists clients, not interop/delta-accumulation behavior | Extend `infra/llm.md` |
| 4 | `LeapFunctionInterop` + `LocalLlmSemantics` gaps (UUID callIds, no correlation, flattened content) | `llm/LeapFunctionInterop.kt` | Local semantics documented only in code (`LocalLlmSemantics:34`) | Extend `infra/llm.md` |
| 5 | `StrategyRouter` type-strategy cascade | `tool/action/StrategyRouter.kt:37` | `mobile_action.md` mentions executors, not the router decision order | Extend `mobile_action.md` |
| 6 | `Capability`/`CapabilityManager`/`AndroidDeviceCapabilitySource` system | `tool/Capability*.kt` | No `doc/main/*` spec (only phase reports) | New `doc/main/infra/capability.md` or extend `tools.md` |
| 7 | `HmxDeviceState` + provider (2s cache, per-field degrade) | `device/*` | No `doc/main/*` spec (only Phase-4 STATUS) | New short spec or extend `platform.md` |
| 8 | `HmxAgent`/`Planner`/`DefaultPlanner`/`TaskOrchestrator` | `agent/Hmx*.kt`, `Planner.kt`, `TaskOrchestrator.kt` | No `doc/main/*` spec (only Phase-2 REPORT) | Extend `agent/overview.md` |
| 9 | `ClosePawStorage` singleton + `StorageSchema` + auto-backup trigger | `storage/*`, `SessionCoordinator.kt:228-234` | No `doc/main/*` spec (only phase docs) | Extend history/persistence docs |
| 10 | `RelayAuthToken` per-session CDP auth + artifact byte cap | `browser/cdp/RelayAuthToken.kt`, `BrowserSessionManager.kt:100-108` | `infra/browser.md` covers transports, not token/cap | Extend `infra/browser.md` |
| 11 | `DefaultBrowserScriptCapabilityGate` as second gating mechanism | `tool/impl/DefaultBrowserScriptCapabilityGate.kt` | Undocumented alongside Capability system | Document both + relationship |
| 12 | `InsecureSslConfig` debug trust-all hook | Referenced in 3 clients | Correctly absent from user docs; code-only | No action (keep undocumented for users; note in dev docs) |
| 13 | `app_skills/` bundled per-package skills (17 packages) | `app/src/main/assets/app_skills/*` | `agent_skills.md` covers mechanism; per-app inventory undocumented | Generate inventory table |
| 14 | `BackupMediaMirror`, `WirelessAdb*` pairing stack details | `platform/*`, `browser/cdp/wireless/*` | Partially in `browser.md`; mirror unmentioned | Extend `browser.md` / `platform.md` |
| 15 | `SessionLlmBootstrapper` routing log line + fail-fast checks | `session/SessionLlmBootstrapper.kt:61-109` | Mentioned in forensic audit only | Reference from `infra/llm.md` |
