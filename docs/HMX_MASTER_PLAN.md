# HMX SYSTEM AGENT — COMPLETE MASTER DEVELOPMENT PLAN

> **Planning-only.** No implementation code. No file modifications. OAuth/Login/Signup = **FROZEN / ZERO TOUCH**.
> Source of truth: repository at `/` + 15 audit files in `docs/audit_*.md`. Distinctions: **VERIFIED** (read source), **INFERRED** (from audit), **PLANNED** (new), **UNKNOWN**, **NOT SUPPORTED**.

---

## 1. Executive Summary

ClosePaw is a working Android-first agent (946 files, 19 packages) with ReAct loop, Accessibility perception, 17+ tools, multi-LLM, Termux, Browser/CDP, Shizuku/VD, memory, skills, subagents. HMX is **evolution, not rewrite**: unify the brain, make every action capability-aware, risk-aware, environment-routed, verified, and recoverable, with live diagnostics and bounded autonomy — while **preserving** every useful subsystem.

21 phases (0–20) with gates, dependency graph, parallel tracks, 15 architecture diagrams, state machines, file-level impact map, risk register, and per-phase implementation prompts. First shippable milestone is **Phase 1: Diagnostics Foundation** (safe, reversible, unlocks all later observability).

---

## 2. Source / Audit Findings

**VERIFIED** via `read` + `bash ls`:

- Packages: `agent`, `app`, `auth`, `browser`, `debug`, `history`, `llm`, `memory`, `model`, `onboarding`, `perception`, `platform`, `protocol`, `session`, `termux`, `tool`, `trace`, `ui`, `util`
- Agent core: `Agent.kt:run()` loop, `AgentTurnRunner`, `TurnPlanningPhaseRunner`, `TurnExecutionPhaseRunner`, `ToolRouter` (VALIDATING→POLICY→EXECUTION→TERMINAL), `AgentSession` checkpoints
- Perception: `Perceptor.kt` traverses `AccessibilityNodeInfo`, multi-window, visibility ratio, dedup key, truncation via `interactiveKeepRatio`, `toPromptJson()`
- Tools: `ToolRegistry`, `ToolSpec`, `ToolRouter`, `PolicyEngine` with canonical ordering; BLOCKED is absolute floor; `ToolName` enum; handlers in `tool/impl/`
- LLM: `LLMClient` abstract (`chatWithTools`/`chatWithToolsStreaming` → `Flow<LLMStreamEvent>`), `DEFAULT_MODEL=glm-5`, rate-limit 5× exponential backoff, providers: OpenAI Responses, OpenRouter, Codex, Other, LFM local
- Policy: `PolicyEngine.kt:check()` pure, `AppTier` CAUTIOUS/NORMAL/BLOCKED, `ApprovalMode` ALWAYS_ASK/AUTO_APPROVE/SMART, session allowlist transient, browser_script special matrix
- Memory: `MemoryStore` markdown in `filesDir/memory/` (`user.md`/`device.md`/`apps/*.md`), 8KB/file, 2000 chars/entry, 5-rule `insertEntry`, `AtomicWrite`; **HIGH RISK: no redaction**
- Auth: `AuthStore` EncryptedSharedPreferences + MasterKey AES256_GCM, `generation` invalidation, `codexHeaders()` auto-refresh 5-min buffer, `OpenAIOAuth` PKCE S256, `localhost:1455` callback, `exchangeForApiKey`; **HIGH RISK: JWT parsed without verify, tokens decrypted in memory**
- Termux: `TermuxBridgeManager` health check 2s timeout, `TermuxCapabilitySnapshot`, `localhost:18422`
- VD/Shizuku: `VirtualDisplayPlatform`, `VdLifecycleArbiter`, `ShizukuClient` binder, transports for display/input/activity/shell
- Browser: `BrowserScriptTool`, `ChromeCdpClient`, `CdpTransport`, `ShizukuChromeDevtoolsBridge`
- Skills: `AgentSkillManager`, `AgentSkillCatalog`, `BundledAgentSkillInstaller`, YAML frontmatter
- Session: `SessionServices` DI container, `MainActivity` entry + overlay + permission gates
- Tests: unit + `androidTest` coverage for policy, perceptor, memory, auth; no single giant E2E harness

**INFERRED**: ReAct loop works but verification = action success (not state); recovery = retry only; no unified device state; no formal capability registry; no risk scoring; no environment router; diagnostics are Logcat-only.

**UNKNOWN / REQUIRES VERIFICATION**: OEM Accessibility divergence matrix; exact Termux bridge daemon stability across OEMs; real-world Shizuku adoption rate on target devices.

---

## 3. Current ClosePaw Architecture

```
MainActivity → AgentService → Agent → AgentTurnRunner
                                   ├─ TurnPlanningPhaseRunner (PromptBuilder + LLMClient + AppSkillRepository)
                                   └─ TurnExecutionPhaseRunner (ToolRouter → PolicyEngine → ToolSpec impls)
                                                        │
SessionServices (DI) ─── AccessibilityPlatform (Perceptor → ScreenSnapshot)
                    ├── TermuxBridgeManager ── TermuxRunCommandAdapter
                    ├── Browser: ChromeCdpClient/CdpTransport/ShizukuChromeDevtoolsBridge
                    ├── VirtualDisplayPlatform ← ShizukuClient/*Transport
                    ├── MemoryStore + MemoryRecaller
                    ├── AuthStore (EncryptedSharedPrefs) ← OpenAIOAuth PKCE :1455
                    └── AgentSkillManager / SubAgentRunner
```

Lifecycle: `USER GOAL → Agent.run() loop → planning (LLM tool calls) → PolicyEngine check → ToolRouter execute → Perceptor snapshot → compaction → repeat → completion/pause/stop`.

---

## 4. Current Capability Matrix

| Domain | Capability | State | Evidence |
|---|---|---|---|
| Understand goal | LLM prompt + tools | SUPPORTED | `PromptBuilder`, `LLMClient` |
| Plan multi-step | ReAct turns | SUPPORTED | `AgentTurnRunner` |
| Read UI | Accessibility tree | SUPPORTED | `Perceptor`, `ScreenSnapshot` |
| Screenshots | VD/Accessibility | PARTIALLY SUPPORTED | `VirtualDisplayScreenshotProcessor` |
| Tap/swipe/type/long-press | mobile actions | SUPPORTED | `tool/impl` actions |
| Open app / Intent | open_app tool | SUPPORTED | `ToolName.OPEN_APP` |
| Notifications | limited | ANDROID-LIMITED | permission-gated |
| Files | Termux + shell | REQUIRES_TERMUX | `TermuxBridgeManager` |
| Browser/CDP | JS bridge, CDP | REQUIRES_BROWSER_BRIDGE | `ChromeCdpClient` |
| Shizuku/VD | privileged, VD | REQUIRES_SHIZUKU | `ShizukuClient` |
| Memory | markdown store | SUPPORTED | `MemoryStore` |
| Skills | SKILL.md discovery | SUPPORTED | `AgentSkillManager` |
| Verify success | action success only | NOT SUPPORTED (gap) | no state verification |
| Recover/replan | retry only | PARTIALLY SUPPORTED | `TurnErrorClassifier` |
| Capability awareness | none formal | NOT SUPPORTED (gap) | → HMX CapabilityManager |
| Risk scoring | allow/deny only | PARTIALLY SUPPORTED | `PolicyEngine` only |
| Env routing | manual per tool | NOT SUPPORTED | → HMX EnvironmentRouter |
| Live diagnostics | Logcat only | NOT SUPPORTED | → HMX Diagnostics |
| Long-running resume | checkpoints only | PARTIALLY SUPPORTED | `AgentSession` checkpoints |

---

## 5. KEEP / MODIFY / EXTEND / ADVANCE / REPLACE / REMOVE / ADD — Master

**KEEP** — stable, preserve:
`Agent` run loop, `AgentTurnRunner`, `ToolRouter` state machine, `PolicyEngine` ordering + BLOCKED floor, `LLMClient` abstraction, `Perceptor` traversal, `AuthStore`/`OpenAIOAuth` (FROZEN), `ToolRegistry`/`ToolSpec`, `AgentSession` checkpoints, `SessionServices` DI, tests.

**MODIFY** — targeted change:
`MemoryStore` → add `SensitiveDataFilter` before `sanitizeContent`; `Perceptor` visibility + truncation params exposed via `DeviceState`; `AppClassifier` tier thresholds configurable; `AgentExecutionConfig` adds step/time/risk limits.

**EXTEND** — preserve + add around:
Skills → declare `capabilitiesRequired/toolsAllowed/riskLevel`; Subagents → isolated task + shared state contract; Browser tab mgmt; Termux workspace mgmt.

**ADVANCE** — more powerful/reliable/observable:
Perception (semantic selectors, `waitForElement`, stale-node handling), LLM context budgeting + sensitive filtering, Shizuku health, VD fallback, memory pipeline (retrieve→rank→filter→dedup→budget→inject).

**REPLACE** — none justified currently. No subsystem fundamentally cannot support HMX.

**REMOVE** — none. Prefer deprecate-after-proven-replacement.

**ADD** — new HMX:
`HmxDeviceState`+Provider, `CapabilityManager`, `RiskEngine` (around PolicyEngine), `EnvironmentRouter`, `VerificationEngine`, `RecoveryManager`, `HmxDiagnostics` (RuntimeLogger/LogBuffer/CrashHandler/DiagnosticOverlay/CrashReportStore/RuntimeEventBus), `MemoryManager`+`MemoryPolicy`, `WorkflowState`/`Checkpoint` engine, phase gates.

---

## 6. HMX Target Architecture

```
                         HMX SYSTEM AGENT
                                │
                         TASK ORCHESTRATOR
                                │
            ┌───────────────────┼───────────────────┐
            ▼                   ▼                   ▼
         PLANNER         CAPABILITY MANAGER   HmxDeviceState
            │                   │                   │
            └─────────┬─────────┴─────────┬─────────┘
                      ▼                   ▼
                RISK ENGINE  ──→  POLICY ENGINE (KEEP)
                      │
                ENVIRONMENT ROUTER
                      │
      ┌───────────────┼───────────────┬───────────────┐
      ▼               ▼               ▼               ▼
 ANDROID_UI       TERMUX        BROWSER_CDP      SHIZUKU/VD
      │               │               │               │
      └───────────────┴───────┬───────┴───────────────┘
                              ▼
                     EXECUTE → OBSERVE → VERIFY STATE
                              │            │
                         SUCCESS      FAILURE → CLASSIFY → RECOVER → REPLAN → EXECUTE AGAIN

CROSS-CUTTING: HmxDiagnostics | MemoryManager | Skills | Security | Observability
FOUNDATION (FROZEN): OAuth / Login / Signup / AuthStore / OpenAIOAuth — ZERO TOUCH
```

---

## 7. New HMX Subsystems (detailed)

### 7.1 Unified HmxAgent Core
Single brain for chat/voice/automation. `HmxAgent` wraps `Agent`; `Task Orchestrator` owns lifecycle, delegates to `Planner`→`CapabilityManager`→`Risk/Policy`→`EnvironmentRouter`→`Execution`→`Observation`→`Verification`→`Recovery`→`Replanning`. No duplicate agent logic.

### 7.2 Device State — `HmxDeviceState` + `DeviceStateProvider`
Fields: battery/charging, RAM/storage, network/Wi-Fi/mobile, screen size/orientation/state, foreground app, Accessibility/overlay/keyboard/notification/mic/camera permissions, Termux/Shizuku/Browser/VD availability + health, current session/task/environment. Each field = AVAILABLE/UNAVAILABLE/UNKNOWN/DEGRADED. Poll + event-driven, cached 2s, never assumed.

### 7.3 Capability Manager
`CapabilityManager` answers "what can/can't I do, what do I need, what's alternative?" States: AVAILABLE/UNAVAILABLE/DEGRADED/REQUIRES_PERMISSION/REQUIRES_USER_APPROVAL/TEMPORARILY_UNAVAILABLE. Sources: `DeviceState`, `PolicyEngine` tier, permission checks, bridge health.

### 7.4 Risk Engine (around PolicyEngine)
Policy: "is allowed?" Risk: "how consequential?" Levels LOW/MEDIUM/HIGH/CRITICAL (destructive, file delete, system change, financial, comms, sensitive info, auth, irreversible, privileged, shell, browser, app sensitivity, intent confidence). Decision ALLOW/ASK/DENY. Stricter as autonomy grows.

### 7.5 Environment Router
`EnvironmentRouter` selects ANDROID_UI/ANDROID_API/ACCESSIBILITY/TERMUX/BROWSER_CDP/SHIZUKU/VIRTUAL_DISPLAY by capability, reliability, permissions, task type, performance, risk, fore/background, visibility, health. Priority is **capability-aware**, not fixed.

### 7.6 Verification Engine
Never `action success == task success`. States VERIFIED/FAILED/UNCERTAIN. Checks desired state: tap→screen reached, exit 0→file exists, text entered→saved, click→expected state. Pluggable verifiers per tool.

### 7.7 Recovery Engine — `RecoveryManager`
Strategies: retry, re-observe, refresh, back, reopen app, restart service, reinit bridge, change selector/coords/action/tool/environment/params, replan, ask user, abort safely. Guards: retry≤3, loop detection, duplicate-action detection, destructive-action protection, timeout, escalation, safe abort.

### 7.8 Live Runtime + Crash Diagnostics — `HmxDiagnostics`
`RuntimeLogger → LogBuffer(50, rotation 100/5min) → DiagnosticEvent → CrashHandler → DiagnosticOverlay → CrashReportStore + RuntimeEventBus`. Bounded, crash-safe (logging never throws), sensitive-data filtered, includes timestamp/app version/Android/device/task/state/screen/app/tool/last success/failure/exception/stack/recent log/diagnostic ID. Actions: COPY LOG / SAVE REPORT / CLOSE / RESTART where safe. Separate RUNTIME LOG / CRASH REPORT / ANR / AGENT FAILURE / TOOL FAILURE / RECOVERY EVENT.

### 7.9 Memory System — `MemoryManager`
Layers: WORKING (turn), EPISODIC (session snapshots), SEMANTIC (generalized), PROCEDURAL (skill patterns), USER/DEVICE/APP (persisted markdown). Pipeline: retrieve→rank→filter→dedup→budget→inject. `SensitiveDataFilter`+`MemoryPolicy` (passwords/OTP/keys/tokens/financial/private-msg/auth secrets redacted; budgets TOTAL_BUDGET 3000 / DISPLAY_TRUNCATE 600).

### 7.10 Accessibility Advancement
Keep tree; add: `findByText/desc/resId/class/bounds`, `waitForElement/screenChange`, `assertElement`, `scrollToElement`, semantic selectors, stale-node handling, OEM divergence handling, multi-window, FLAG_SECURE, service-death, gesture failure.

### 7.11–7.14 Termux / Shizuku / Browser / Skills
Preserve; add workspace/streaming/cancellation, Binder health + privileged ops, reliable CDP session + tab/page/DOM/JS/verification, skills declare capabilities/tools/risk/permissions/versions/fallback and never bypass safety.

### 7.15 Long-Running Workflow Engine
`WorkflowState`/`Checkpoint`/`TaskState`/`ExecutionState`, persistence, interruption, process-death resume, background, waiting. Not early — after verification/recovery/risk.

---

## 8. Phase-by-Phase Roadmap

> For every phase: **AUTH / OAUTH / LOGIN / SIGNUP: ZERO TOUCH**. No phase touches `app/src/main/kotlin/ai/closepaw/auth/*`, `AuthStoreHolder`, login/signup UI/nav.

### PHASE 0 — Repository / Audit / Baseline

**Objective:** Freeze truth. Verify audit vs repo, establish baseline metrics.

**Why now:** All later phases depend on correct classification.

**Current components:** All packages listed in §2; 15 audit files in `docs/`.

**KEEP/MODIFY/EXTEND/ADVANCE/REPLACE/REMOVE/ADD:** KEEP all; ADD `docs/BASELINE.md` (commit hash, file counts, test counts).

**Architecture/Data/Control flow:** INPUT repo+audits → PROCESS diff audit claims vs `ls`/`read` → OUTPUT `BASELINE.md` + discrepancy log.

**Dependencies:** none.

**Parallel work:** none (gate).

**Files/packages:** `docs/BASELINE.md` ADD; `docs/audit_*.md` KEEP.

**New interfaces:** `BaselineReport` (commit, counts, discrepancies).

**Failure/recovery:** audit mismatch → file issue, re-audit that file.

**Security/privacy/perf/battery/Android:** none.

**Testing:** UNIT none; REGRESSION snapshot file list + test count.

**Completion:** `BASELINE.md` committed, 0 unresolved VERIFIED vs repo mismatches.

**Deliverables:** `BASELINE.md`, discrepancy log.

**Risks/rollback:** low; delete `BASELINE.md`.

**OAuth:** ZERO TOUCH.

---

### PHASE 1 — Regression Safety + Diagnostics Foundation

**Objective:** Make future work observable and reversible. Introduce crash-safe diagnostics + regression harness.

**Why now:** Without it, later changes are blind.

**Current:** Logcat only, no `HmxDiagnostics`, tests exist but no failure injection harness.

**KEEP:** existing `Log` calls, tests. **ADD:** `HmxDiagnostics`, `RuntimeLogger`, `LogBuffer(50)`, `CrashHandler`, `DiagnosticOverlay`, `CrashReportStore`, `RuntimeEventBus`, `SensitiveDataFilter` (for logs).

**Architecture:** `Agent → RuntimeLogger → LogBuffer → CrashHandler → Overlay/Store`; `Thread.setDefaultUncaughtExceptionHandler` chain; bounded queue, `try/catch` around every log call.

**Data flow:** event → filter → buffer → (crash? → overlay+store) : (normal → ring).

**Control:** `SessionServices` creates `HmxDiagnostics`; `Agent` emits events; `CrashHandler` is app-scoped singleton.

**Deps:** Phase 0. **Parallel:** none.

**Files:** `util/HmxDiagnostics.kt` ADD, `util/RuntimeLogger.kt` ADD, `util/CrashHandler.kt` ADD, `ui/DiagnosticOverlay.kt` ADD, `trace/RuntimeEventBus.kt` ADD; `AppSettingsStore` MODIFY (add `diagnosticsEnabled`).

**Interfaces:** `HmxDiagnostics(log, buffer, report)`, `RuntimeLogger.log(level, tag, msg)`, `LogBuffer.push/pop/snapshot`, `CrashReport(id, ts, version, device, task, state, screen, tool, lastSuccess, lastFailure, exception, stack, recentLog)`.

**Failures:** logger throws → swallowed; OOM from buffer → drop oldest; filter misses secret → post-filter scan. Recovery: degraded mode, disable diagnostics.

**Security:** filter secrets at source; store encrypted via EncryptedSharedPrefs or private file.

**Privacy:** redact OTP/tokens before buffer.

**Perf:** <1ms per log, <200KB RAM.

**Battery:** none.

**Android:** overlay needs `SYSTEM_ALERT_WINDOW`; fallback to notification.

**Testing:** UNIT LogBuffer bounds; INTEGRATION crash → overlay appears; INSTRUMENTED ANR; FAILURE injection: logger exception must not crash app; REGRESSION existing tests green.

**Completion:** overlay shows on test crash, COPY LOG works, 50-entry bound verified, no existing test broken.

**Deliverables:** diagnostics module + tests + overlay screenshot.

**Risks:** overlay permission denial → fallback. Rollback: feature-flag off, remove `HmxDiagnostics` wiring.

**OAuth:** ZERO TOUCH.

---

### PHASE 2 — Unified Agent Core

**Objective:** One brain. Introduce `HmxAgent` + `TaskOrchestrator` wrapping existing `Agent`.

**Why now:** Capability/risk/routing need single choke point.

**Current:** `Agent`, `AgentTurnRunner`, `AgentSession`.

**KEEP:** `Agent` internals. **MODIFY:** `Agent.kt` to delegate lifecycle to `TaskOrchestrator` (thin wrapper, no logic move yet). **ADD:** `HmxAgent`, `TaskOrchestrator`, `Planner` interface.

**Architecture:** `HmxAgent → TaskOrchestrator → (Planner, CapabilityManager stub, Risk stub, EnvRouter stub, Execution, Observation, Verification stub, Recovery stub)`.

**Data:** `UserGoal → TaskOrchestrator.plan() → Execution → Observation → Verification → done/recover`.

**Control:** `MainActivity`/`AgentService` call `HmxAgent.run()` instead of `Agent.run()` directly; `HmxAgent` owns `Agent` instance.

**Deps:** Phase 1 (diagnostics for orchestration events).

**Files:** `agent/HmxAgent.kt` ADD, `agent/TaskOrchestrator.kt` ADD, `agent/Planner.kt` ADD; `agent/Agent.kt` MODIFY (delegate); `session/SessionServices.kt` MODIFY (provide orchestrator).

**Interfaces:** `HmxAgent.run(goal): Result`, `TaskOrchestrator.orchestrate(task)`, `Planner.plan(goal, deviceState, capabilities): Plan`.

**Failures:** orchestrator crash → fallback to legacy `Agent.run()` path. Loop detection stub.

**Security/privacy:** no new surface.

**Perf:** +1 indirection, negligible.

**Testing:** UNIT orchestrator delegates; INTEGRATION legacy path still works; REGRESSION all agent tests green.

**Completion:** `HmxAgent` runs existing task end-to-end (regression), diagnostics emit orchestrator events.

**Risks:** behavioral drift → keep `Agent` untouched internally; rollback by flipping `MainActivity` back to direct `Agent`.

**OAuth:** ZERO TOUCH.

---

### PHASE 3 — Capability Manager + Tool Architecture

**Objective:** Agent knows "what can I do?" Formalize `CapabilityManager` + tool registry health.

**Why now:** Env router + risk need capability truth.

**Current:** `ToolRegistry`, `ToolSpec`, no unified capability view.

**KEEP:** `ToolRegistry`. **ADVANCE:** `ToolSpec` adds `requiredCapabilities: Set<Capability>`. **ADD:** `CapabilityManager`, `Capability` enum, `CapabilityState`.

**Architecture:** `DeviceStateProvider → CapabilityManager → EnvironmentRouter`; `ToolRegistry` queries manager before exposing tools.

**Data:** `DeviceState → CapabilityManager.resolve() → Map<Capability, CapabilityState> → filtered ToolSpec list`.

**Deps:** Phase 2, Phase 4 can parallel start.

**Files:** `tool/CapabilityManager.kt` ADD, `tool/Capability.kt` ADD, `tool/CapabilityState.kt` ADD; `tool/ToolSpec.kt` MODIFY; `platform/*` MODIFY to report health.

**Interfaces:** `CapabilityManager.snapshot(): Map<Capability, CapabilityState>`, `isAvailable(cap)`, `alternatives(cap)`.

**Failures:** unknown capability → UNKNOWN (not crash); permission revoked mid-task → TEMPORARILY_UNAVAILABLE + recovery.

**Security:** capability checks are authorization-adjacent — deny by default on UNKNOWN.

**Testing:** UNIT state transitions; INTEGRATION tool list filtered; INSTRUMENTED permission revoke mid-session.

**Completion:** `snapshot()` covers all 7 envs + permissions, tool exposure matches snapshot, tests for REVOKED case.

**OAuth:** ZERO TOUCH.

---

### PHASE 4 — Device State

**Objective:** Ship `HmxDeviceState` + provider with AVAILABLE/UNAVAILABLE/UNKNOWN/DEGRADED per field.

**Why now:** Capability Manager needs it; can build in parallel with Phase 3 after Phase 2.

**Current:** scattered checks (`TermuxBridgeManager.healthCheck`, Shizuku binder, `AppSettingsStore`).

**KEEP:** existing health checks. **ADD:** `HmxDeviceState`, `DeviceStateProvider`, `DeviceStateField`.

**Architecture:** `DeviceStateProvider` polls + listens (battery `BatteryManager`, network `ConnectivityManager`, permissions `checkSelfPermission`, bridge health) → caches 2s → `HmxDeviceState`.

**Data:** sensors → provider → `HmxDeviceState` → CapabilityManager/Planner.

**Deps:** Phase 2. **Parallel:** with Phase 3.

**Files:** `platform/DeviceStateProvider.kt` ADD, `model/HmxDeviceState.kt` ADD; `termux/*`, `platform/virtualdisplay/*`, `browser/*` MODIFY to expose health via provider.

**Interfaces:** `DeviceStateProvider.current(): HmxDeviceState`, `observe(): Flow<HmxDeviceState>`.

**Failures:** provider throws → return UNKNOWN for that field; never crash caller.

**Security:** no location/exact identifiers; battery/network only.

**Privacy:** no PII.

**Perf:** cache 2s, lazy permission checks.

**Battery:** no wakelocks; passive listeners.

**Android:** `BATTERY_STATS` not needed; `ACCESS_NETWORK_STATE` already granted.

**Testing:** UNIT field states; INTEGRATION provider on emulator vs real device; FAILURE field unavailable → UNKNOWN.

**Completion:** all fields in §6.2 reported correctly on 2 devices, UNKNOWN handled.

**OAuth:** ZERO TOUCH.

---

### PHASE 5 — Perception + Accessibility Advancement

**Objective:** Robust element targeting, wait/assert, OEM handling — keep `Perceptor` architecture.

**Why now:** Flaky perception = flaky everything.

**Current:** `Perceptor`, `PerceptorFilterConfig`, `ScreenSnapshot`, `LoopDetectionPolicy`.

**KEEP:** traversal. **ADVANCE:** add `findBy*`, `waitForElement(timeout)`, `waitForScreenChange`, `assertElement`, `scrollToElement`, semantic selector `Selector(text|desc|resId|class|bounds)`, stale-node retry, FLAG_SECURE handling, service-death recovery.

**Architecture:** `Perceptor` + `AccessibilityAdvancement` layer; `NodeActionPerformer` uses selector fallback chain.

**Data:** `ScreenSnapshot → SelectorEngine.find(selector) → ranked candidates → action`.

**Deps:** Phase 4 (screen/orientation from DeviceState).

**Files:** `perception/Selector.kt` ADD, `perception/WaitPolicy.kt` ADD, `perception/ElementFinder.kt` ADD; `perception/Perceptor.kt` MODIFY (expose helpers); `platform/*` MODIFY (stale handling).

**Failures:** element missing → wait then UNCERTAIN; service death → rebind + re-observe; FLAG_SECURE → report UNAVAILABLE.

**Security:** never log node text without filter.

**Perf:** cache snapshot 500ms; dedup already.

**Testing:** UNIT selector ranking; INSTRUMENTED wait/FLAG_SECURE/service-death; REAL DEVICE 3 OEMs.

**Completion:** `waitForElement` <2s p95, stale retry verified, OEM smoke on 2 OEMs.

**OAuth:** ZERO TOUCH.

---

### PHASE 6 — Verification Engine

**Objective:** `VerificationEngine` — desired-state checks, VERIFIED/FAILED/UNCERTAIN, never `exit 0 == success`.

**Why now:** Must verify before recovery can be meaningful.

**Current:** `ScreenSnapshot` comparison ad-hoc.

**ADD:** `VerificationEngine`, `Verifier<T>`, `VerificationState`.

**Architecture:** `ToolRouter` result → `VerificationEngine.verify(desiredState, observation)` → state.

**Data:** `(desired, snapshot, toolResult) → Verifier → VERIFIED/FAILED/UNCERTAIN`.

**Deps:** Phase 5 (observation). Parallel with Phase 7 start after.

**Files:** `agent/verification/VerificationEngine.kt` ADD, `agent/verification/Verifier.kt` ADD; `tool/*` MODIFY to declare `desiredState`.

**Failures:** verifier timeout → UNCERTAIN → recovery asks user.

**Testing:** UNIT verifiers; INTEGRATION tap→screen reached vs not.

**Completion:** 5 verifiers (tap, type, open_app, shell, browser) achieve >95% correct VERIFIED/FAILED on regression set.

**OAuth:** ZERO TOUCH.

---

### PHASE 7 — Recovery Engine

**Objective:** `RecoveryManager` with bounded strategies + loop/duplicate/destructive guards.

**Why now:** Verification without recovery is dead-end.

**Current:** retry only via `TurnErrorClassifier`.

**KEEP:** classifier. **ADD:** `RecoveryManager`, `RecoveryStrategy`, loop detection, escalation.

**Architecture:** `Verification FAILED/UNCERTAIN → RecoveryManager.classify() → strategy → (retry|re-observe|back|reopen|change selector/tool/env|replan|ask|abort)`.

**Data:** `failure + history + device + capability → strategy → action`.

**Deps:** Phase 6.

**Files:** `agent/recovery/RecoveryManager.kt` ADD; `agent/cognition/policy/*` MODIFY (integrate).

**Failures:** infinite loop → abort after 3; destructive recovery blocked.

**Testing:** UNIT strategy selection; FAILURE injection duplicate-action + loop; INTEGRATION recovery actually fixes missing-element case.

**Completion:** loop detection triggers ≤3 repeats, destructive guard blocks, no infinite recovery in stress test (50 failures).

**OAuth:** ZERO TOUCH.

---

### PHASE 8 — Risk Engine + Policy Integration

**Objective:** `RiskEngine` around `PolicyEngine`; Risk LOW/MED/HIGH/CRITICAL → ALLOW/ASK/DENY.

**Why now:** Autonomy must get safer, not looser.

**Current:** `PolicyEngine` Allow/Deny/AskUser + `AppTier`.

**KEEP:** `PolicyEngine` unchanged. **ADD:** `RiskEngine`, `RiskLevel`, `RiskAssessment`.

**Architecture:** `Tool call → CapabilityManager → RiskEngine.assess() → PolicyEngine.check() → merged decision (stricter wins)`.

**Data:** `tool+params+appTier+device → RiskLevel → decision`.

**Deps:** Phase 3,7.

**Files:** `tool/RiskEngine.kt` ADD, `tool/RiskLevel.kt` ADD; `tool/PolicyEngine.kt` KEEP (no edit) — integration in `ToolRouter` MODIFY.

**Security:** destructive/file delete/system/financial/comm/sensitive/irreversible/privileged/shell/browser → CRITICAL → ASK or DENY; BLOCKED always DENY.

**Testing:** UNIT risk scoring; INTEGRATION policy+risk stricter-wins; SECURITY suite.

**Completion:** risk matrix covers §6.4 checklist, BLOCKED still absolute, CRITICAL requires explicit approval.

**OAuth:** ZERO TOUCH.

---

### PHASE 9 — Memory Architecture

**Objective:** Layered memory + pipeline + sensitive filter.

**Why now:** Memory is currently unbounded redaction-free markdown.

**Current:** `MemoryStore` 5-rule append, `MemoryRecaller`.

**MODIFY:** `MemoryStore` to call `SensitiveDataFilter` before write. **ADD:** `MemoryManager`, `MemoryPolicy`, `SensitiveDataFilter`, `MemoryPipeline` (retrieve→rank→filter→dedup→budget→inject).

**Architecture:** `MemoryRecaller → MemoryManager.pipeline(totalBudget=3000, displayTruncate=600) → prompt injection`.

**Data:** `recall USER+DEVICE+APP → rank → filter secrets → dedup → budget → inject`.

**Deps:** Phase 1 (filter for logs), Phase 8 (risk for memory policy).

**Files:** `memory/MemoryManager.kt` ADD, `memory/SensitiveDataFilter.kt` ADD, `memory/MemoryPolicy.kt` ADD; `memory/MemoryStore.kt` MODIFY (single filter call); `memory/MemoryRecaller.kt` MODIFY (pipeline).

**Security/privacy:** passwords/OTP/tokens/financial/private-msg never persisted; filter at write + recall; budget prevents giant histories in prompt.

**Testing:** UNIT filter redaction; INTEGRATION pipeline respects budget; REGRESSION existing memory tests.

**Completion:** secret written → redacted/rejected (test), pipeline budget enforced, prompt injection ≤3000 chars.

**OAuth:** ZERO TOUCH (tokens never go through MemoryStore — enforced by filter + code review).

---

### PHASE 10 — Environment Router

**Objective:** `EnvironmentRouter` selects best env per task from ANDROID_UI/TERMUX/BROWSER_CDP/SHIZUKU/VD.

**Why now:** Needs capability + risk + device state.

**Current:** per-tool manual selection.

**ADD:** `EnvironmentRouter`, `EnvironmentHealth`.

**Architecture:** `task + DeviceState + Capability snapshot + Risk → Router.rank() → health check → select → fallback`.

**Deps:** Phase 3,4,8.

**Files:** `platform/EnvironmentRouter.kt` ADD; `platform/*` MODIFY (health probes).

**Failures:** env dies mid-task → re-route + recovery.

**Testing:** UNIT ranking; INTEGRATION Termux down → falls back to UI; INSTRUMENTED VD unavailable.

**Completion:** router selects correct env in 5 scenario tests, fallback verified.

**OAuth:** ZERO TOUCH.

---

### PHASE 11 — Termux / Linux Advancement

**Objective:** Workspace, streaming, cancellation, Android/Linux coordination — keep bridge.

**Why now:** Router can now leverage richer Termux.

**Current:** `TermuxBridgeManager`, `TermuxRunCommandAdapter`, bridge `localhost:18422`.

**KEEP:** bridge daemon. **ADVANCE:** `WorkspaceManager`, streaming output, `CancellationToken`, git/patch/edit/search helpers — incremental, prioritize HMX use cases (file ops, search, edit).

**Files:** `termux/WorkspaceManager.kt` ADD, `termux/StreamingCommand.kt` ADD; `termux/*` MODIFY.

**Testing:** UNIT workspace; INTEGRATION long-running + cancel; FAILURE bridge death → recovery.

**OAuth:** ZERO TOUCH.

---

### PHASE 12 — Shizuku + Virtual Display

**Objective:** Capability detection, Binder health, privileged ops, VD background execution + fallback when unavailable (never assumed).

**Current:** `ShizukuClient`, `*Transport`, `VirtualDisplayPlatform`, `VdLifecycleArbiter`.

**KEEP:** transports. **ADVANCE:** `ShizukuHealthMonitor`, `VdLifecycleArbiter` with fallback (no Shizuku → UI mode).

**Files:** `platform/virtualdisplay/ShizukuHealthMonitor.kt` ADD; `platform/virtualdisplay/*` MODIFY.

**Testing:** UNIT Binder death; INSTRUMENTED Shizuku absent → graceful degraded UI path.

**OAuth:** ZERO TOUCH.

---

### PHASE 13 — Browser / CDP Advancement

**Objective:** Reliable CDP session, tab/page/DOM/JS, verification, timeouts, recovery.

**Current:** `ChromeCdpClient`, `CdpTransport`, `BrowserScriptTool`.

**KEEP:** CDP client. **ADVANCE:** `BrowserSessionManager` reliability, `TabManager`, JS `Verifier`.

**Files:** `browser/*` MODIFY + `browser/TabManager.kt` ADD.

**Testing:** UNIT session lifecycle; INTEGRATION JS execution + verify.

**OAuth:** ZERO TOUCH.

---

### PHASE 14 — Skills + Subagents

**Objective:** Skills declare capabilities/tools/risk/permissions/versions/fallback; subagents isolated with shared-state contract; never bypass safety.

**Current:** `AgentSkillManager`, `SubAgentRunner`.

**EXTEND:** `AgentSkillEntry` adds declarations; `SubAgentRunner` enforces `ToolRouter`+`RiskEngine` per subagent.

**Files:** `agent/definition/AgentRoleDef.kt` MODIFY, `agent/subagent/*` MODIFY, `agent/cognition/skills/*` MODIFY.

**Testing:** UNIT skill activation respects risk; INTEGRATION subagent cannot escalate.

**OAuth:** ZERO TOUCH.

---

### PHASE 15 — Autonomous Workflow Engine

**Objective:** Multi-step workflows, waiting, persistence, interruption, process-death resume via `WorkflowState`/`Checkpoint`.

**Why later:** Needs verification/recovery/risk.

**ADD:** `WorkflowState`, `Checkpoint`, `WorkflowEngine`.

**Deps:** Phase 6,7,8,10.

**Testing:** UNIT checkpoint; INTEGRATION kill process → resume.

**OAuth:** ZERO TOUCH.

---

### PHASE 16 — Long-Running Tasks + Resume

**Objective:** Background execution, state checkpoints, real resume across service restart.

**ADD:** `TaskState`, `ExecutionState`, `ResumeManager`.

**Deps:** Phase 15.

**Testing:** INSTRUMENTED 30-min workflow with interruption.

**OAuth:** ZERO TOUCH.

---

### PHASE 17 — Security Hardening

**Objective:** Unified audit: Policy+Risk+Capability+tool/skill perms; shell/file/system/privileged/browser/comm/secrets/memory/logs/screenshots/clipboard/notifications.

**Why now:** Before production QA.

**No new features** — harden, review, pen-test checklist.

**Deliverable:** security review doc + fixes (non-auth).

**OAuth:** ZERO TOUCH (explicitly excluded from hardening scope).

---

### PHASE 18 — Performance + Battery Optimization

**Objective:** Android-first: low RAM/CPU, thermal, background restrictions. Optimize screenshots, tree processing, LLM calls, memory retrieval, logs, polling, serialization, large contexts.

**Metrics:** perception <200ms p95, LLM call <2s median, memory <5MB, battery <5%/day typical, no wakelock leaks.

**OAuth:** ZERO TOUCH.

---

### PHASE 19 — Production Reliability + Real Device QA

**Objective:** Real-device, OEM, regression, failure-injection, battery, long-running, crash-recovery tests. Preserve existing tests.

**Deliverable:** QA report + OEM matrix (≥3 OEMs, 2 Android versions).

**OAuth:** ZERO TOUCH.

---

### PHASE 20 — Final HMX Integration / Release Readiness

**Objective:** All phases integrated, M9 acceptance, zero-touch verified, release checklist.

**Completion:** checklist §25 all green, 0 P0 risks open.

**OAuth:** ZERO TOUCH — final compliance report.

---

## 9. Phase Dependency Graph

```
Phase 0
  ↓
Phase 1
  ↓
Phase 2
  ↓
 ┌────┴────┐
Phase 3  Phase 4  (parallel)
 └────┬────┘
      ↓
   Phase 5
      ↓
   Phase 6
      ↓
   Phase 7
      ↓
   Phase 8
      ↓
   Phase 9
      ↓
  Phase 10
      ↓
 ┌────┼────┐
Phase11 Phase12 Phase13  (11∥12 can parallel; 13 after 10)
 └────┼────┘
      ↓
  Phase 14
      ↓
  Phase 15
      ↓
  Phase 16
      ↓
  Phase 17 ─┐
            ├─ can overlap
  Phase 18 ─┘
      ↓
  Phase 19
      ↓
  Phase 20

Also parallel: Phase 3∥4; Phase 11∥12; Phase 13∥14; Phase 17∥18
Strictly sequential otherwise.
```

---

## 10. Architecture Diagrams

**1. Current ClosePaw**
```
MainActivity → AgentService → Agent.run() → AgentTurnRunner → TurnPlanning → LLMClient → ToolRouter → PolicyEngine → ToolSpec impls
                                                              TurnExecution → Perception(Perceptor) → ScreenSnapshot
SessionServices → AccessibilityPlatform / Termux / Browser(CDP) / VD+Shizuku / MemoryStore / AuthStore
```

**2. HMX Target**
```
HmxAgent → TaskOrchestrator → Planner → CapabilityManager → RiskEngine → PolicyEngine → EnvironmentRouter
  → {ANDROID_UI, TERMUX, BROWSER_CDP, SHIZUKU/VD} → EXECUTE → OBSERVE → VERIFY → RECOVERY → REPLAN
Cross: Diagnostics | MemoryManager | Skills | Subagents
Frozen: OAuth/Login/Signup
```

**3. Unified Agent**
```
Voice/Chat/Automation triggers → same HmxAgent core (no duplication)
```

**4. Agent Lifecycle**
```
USER GOAL → UNDERSTAND → PLAN → CAPABILITY CHECK → RISK/POLICY → ENV SELECT → EXECUTE → OBSERVE → VERIFY → SUCCESS? → YES→COMPLETE
                                                                                                          NO→CLASSIFY→RECOVER→REPLAN→EXECUTE AGAIN
```

**5. Capability System**
```
DeviceStateProvider → CapabilityManager → snapshot: AVAILABLE/UNAVAILABLE/DEGRADED/REQUIRES_PERMISSION/REQUIRES_USER_APPROVAL/TEMPORARILY_UNAVAILABLE
  → filtered ToolSpecs + alternatives
```

**6. Device State System**
```
Sensors/BatteryManager/ConnectivityManager/PackageManager/BridgeHealth → DeviceStateProvider (cache 2s) → HmxDeviceState
```

**7. Risk + Policy**
```
Tool call → CapabilityManager → RiskEngine(LOW/MED/HIGH/CRITICAL) → PolicyEngine(Allow/Deny/AskUser) → merged stricter-wins → ALLOW/ASK/DENY
```

**8. Environment Routing**
```
Task + DeviceState + Capability + Risk → EnvironmentRouter.rank() → health check → select → fallback chain (e.g., Shizuku→Accessibility→Termux)
```

**9. Verification**
```
(action success) != (task success). DesiredState + Observation → Verifier → VERIFIED/FAILED/UNCERTAIN
```

**10. Recovery**
```
FAILED → classify → {retry, re-observe, back, reopen, change selector/tool/env, replan, ask user, abort} with retry≤3 + loop detection
```

**11. Memory**
```
retrieve → rank → filter(secrets) → dedup → budget(3000/600) → inject → LLM
```

**12. Diagnostics**
```
Agent → RuntimeLogger → LogBuffer(50, rotation 100/5min) → CrashHandler → DiagnosticOverlay + CrashReportStore
                                         ↘ RuntimeEventBus
```

**13. Crash Flow**
```
UncaughtException → CrashHandler (never throws) → Capture report + recentLog → Overlay(COPY/SAVE/CLOSE/RESTART) + persist
ANR → watchdog → same path
```

**14. Long-Running Workflow**
```
WorkflowState → Checkpoint (persist) → TaskState/ExecutionState → interruption/process death → ResumeManager → rehydrate → continue
```

**15. Phase Dependency Graph** — see §9 ASCII.

---

## 11. State Machines

**AgentState:** `IDLE → PLANNING → EXECUTING → VERIFYING → (VERIFIED→COMPLETE | FAILED→RECOVERING) → REPLAN → EXECUTING … → ABORT/COMPLETE`. Invalid: `EXECUTING→COMPLETE` without `VERIFYING`.

**TaskState:** `PENDING → RUNNING → PAUSED → RESUMING → SUCCEEDED | FAILED | ABORTED`. `PAUSED` requires `pauseConfirmed`.

**ToolState:** `VALIDATING → POLICY_CHECK → EXECUTION → TERMINAL(Succeeded/Failed/Cancelled)` (from `ToolCallState`).

**CapabilityState:** `UNKNOWN → AVAILABLE ↔ DEGRADED ↔ TEMPORARILY_UNAVAILABLE → UNAVAILABLE`; `REQUIRES_PERMISSION/APPROVAL` are terminal until granted.

**VerificationState:** `PENDING → VERIFIED | FAILED | UNCERTAIN`; `UNCERTAIN` triggers `ASK_USER` or `RECOVER`.

**RecoveryState:** `NONE → RETRY(≤3) → REPLAN → SWITCH_TOOL → SWITCH_ENV → ASK_USER → ABORT_SAFE`. Destructive actions skip `RETRY`.

**EnvironmentState:** `SELECTED → HEALTH_CHECK → OPERATIONAL ↔ DEGRADED → UNAVAILABLE → SWITCHING`.

**DiagnosticState:** `IDLE → LOGGING → CRASHED → OVERLAY_SHOWN → (COPY|SAVE|CLOSE|RESTART) → IDLE`.

**WorkflowState:** `CREATED → RUNNING → CHECKPOINTED → PAUSED → RESUMED → COMPLETED | FAILED | ABORTED`; `CHECKPOINTED` survives process death.

---

## 12. Diagnostics / Crash Architecture

```
HmxDiagnostics
 ├── RuntimeLogger      — try/catch around every log, bounded queue 50
 ├── LogBuffer         — ring, rotation 100 entries / 5 min, drop-oldest on OOM
 ├── RuntimeEventBus   — lightweight, in-memory, batch emit
 ├── CrashHandler      — Thread.setDefaultUncaughtExceptionHandler chain, never throws
 ├── ANR Diagnostics   — watchdog + ANR file watcher
 ├── DiagnosticOverlay — SYSTEM_ALERT_WINDOW or notification fallback, shows report
 └── CrashReportStore  — private file, rotation 10 reports, sensitive-filtered

Report: { diagnosticId, timestamp, appVersion, androidVersion, deviceModel, currentTask, agentState, screenPackage, currentTool, lastSuccess, lastFailure, exception, stackTrace, recentLog[50], component }

Thread safety: LogBuffer synchronized; CrashHandler re-entrant guard.
Bounded memory: 50 entries × ~200 chars ≈ 10KB + 10 reports × 20KB ≈ 210KB max.
Log retention: ring + rotation; persisted only on crash/failure.
Crash-time: overlay uses bare Activity, no dependency on broken components; handler copies buffer before any allocation.
ANR: FileObserver on /data/anr, plus heartbeat watchdog 10s.
Sensitive filtering: `SensitiveDataFilter` at logger entry (regex for `password|otp|token|key|secret` + JWT pattern) → `[REDACTED]`.
Performance: <1ms/log, no I/O off-crash; persist is async low priority.
Persistence: private `filesDir/diagnostics/` JSON, 10 files max.
UI: COPY LOG (clipboard), SAVE REPORT (share), CLOSE, RESTART where safe (re-init `HmxAgent`).
Recovery interaction: `RecoveryManager` emits `RECOVERY EVENT` → logger; crash report links to last recovery attempt.
```

---

## 13. Memory Architecture

Layered (§6.9) + pipeline. `MemoryStore` markdown kept; `SensitiveDataFilter` is single choke point. `MemoryPolicy`: per-entry 2000 chars, per-file 8KB, total inject 3000, display 600. `retrieve` pulls USER+DEVICE+APP; `rank` by recency+relevance; `filter` redacts; `dedup` by hash; `budget` truncates; `inject` builds prompt-visible section. Secrets never stored; working memory cleared per task; episodic bounded per session.

---

## 14. Security Architecture

`PolicyEngine (Allow/Deny/Ask) + RiskEngine (LOW..CRITICAL→ALLOW/ASK/DENY) + CapabilityManager + Tool/Skill perms`. Analysis per vector: shell (CRITICAL by default, whitelist safe), file delete (system→DENY, user→ASK), system settings (NORMAL only, BLOCKED absolute), privileged (REQUIRES_SHIZUKU+approval), browser (verified session, BLOCKED→DENY), comm (LLM only), secrets (AuthStore encrypted, MemoryStore filtered, logs filtered), screenshots/UI text/clipboard/notifications (tier-aware, no blind auto-allow). **Autonomy ≠ auto-allow** — §6.4 thresholds enforce ASK/DENY as autonomy grows.

---

## 15. Testing Strategy

Global: UNIT (pure logic), INTEGRATION (two subsystems), INSTRUMENTATION (Accessibility/overlay/ANR/VD), UI (chat/overlay/permission flows — limited non-sensitive), REGRESSION (existing tests must stay green), FAILURE injection (service death, Binder death, bridge down, FLAG_SECURE, no network, OOM), REAL DEVICE (≥3 OEMs, 2 Android versions, Termux/Shizuku matrix), PERFORMANCE (perception <200ms, LLM <2s), BATTERY (5%/day), LONG-RUNNING (30-min with kill), CRASH recovery (overlay appears, COPY works).

Per phase gates in §8 `Testing` lines; never delete useful existing tests.

---

## 16. Performance / Battery Strategy

Constraints: low RAM, CPU limits, battery, thermal throttling, background restrictions, process death, network instability. Optimize: screenshots (reuse tree, limit size), tree processing (dedup+spatial sort already; cache 500ms), LLM (bounded context, cache repetitive, streaming), memory retrieval (rank/budget), logs (bounded 50, no I/O), polling (events not polls; 2s cache), background (foreground service with notification when running, respect OOM killer), serialization (minimal JSON/proto), large contexts (hard token caps). Every phase declares perf impact; measure before/after.

---

## 17. File-Level Master Impact Map

| File / Package | Current Role | Classification | Phase | Reason |
|---|---|---|---|---|
| `agent/Agent.kt` | run loop | KEEP→MODIFY | 2 | delegate to `TaskOrchestrator` |
| `agent/AgentTurnRunner.kt` | turn loop | KEEP | 2 | wrapped by orchestrator |
| `agent/TurnPlanningPhaseRunner.kt` | planning | KEEP | 2 | add capability/risk context |
| `agent/TurnExecutionPhaseRunner.kt` | execution | KEEP | 7 | integrate verification/recovery |
| `agent/cognition/prompt/PromptBuilder.kt` | LLM prompt | MODIFY | 9 | budget+memory pipeline |
| `agent/cognition/policy/*` | loop/policy | MODIFY | 7 | RecoveryManager integration |
| `agent/definition/*` | role defs | EXTEND | 14 | skill declarations |
| `agent/subagent/SubAgentRunner.kt` | subagent | EXTEND | 14 | safety enforcement |
| `tool/PolicyEngine.kt` | policy | KEEP | 8 | frozen, wrapped by RiskEngine |
| `tool/ToolRouter.kt` | tool lifecycle | MODIFY | 3,8,10 | capability+risk+env routing |
| `tool/ToolSpec.kt` | tool contract | MODIFY | 3 | `requiredCapabilities` |
| `tool/ToolRegistry.kt` | registry | KEEP | 3 | filtered by CapabilityManager |
| `perception/Perceptor.kt` | tree traversal | KEEP→ADVANCE | 5 | selectors/wait |
| `platform/*` | Accessibility/VD | KEEP→ADVANCE | 5,12 | robust targeting, health |
| `platform/virtualdisplay/*` | VD + Shizuku | EXTEND | 12 | health monitor + fallback |
| `termux/*` | bridge | KEEP→ADVANCE | 11 | workspace/streaming |
| `browser/cdp/*`, `browser/script/*` | CDP | KEEP→ADVANCE | 13 | reliable session |
| `memory/MemoryStore.kt` | markdown store | MODIFY | 9 | `SensitiveDataFilter` |
| `memory/MemoryRecaller.kt` | recall | MODIFY | 9 | pipeline |
| `auth/*` | OAuth/tokens | KEEP (FROZEN) | — | **ZERO TOUCH** |
| `app/AuthStoreHolder.kt` | auth holder | KEEP (FROZEN) | — | **ZERO TOUCH** |
| `app/MainActivity*`, `app/AgentService*` | entry/service | MODIFY | 1,2 | diagnostics + HmxAgent wiring |
| `session/SessionServices.kt` | DI | MODIFY | 1,2,4 | provide diagnostics/orchestrator/device state |
| `util/*` (new) | diagnostics | ADD | 1 | HmxDiagnostics suite |
| `agent/verification/*` (new) | verification | ADD | 6 | VerificationEngine |
| `agent/recovery/*` (new) | recovery | ADD | 7 | RecoveryManager |
| `platform/EnvironmentRouter.kt` (new) | routing | ADD | 10 | env selection |
| `model/HmxDeviceState.kt` (new) | device state | ADD | 4 | state model |

---

## 18. Master Risk Register

| Risk | Severity | Probability | Phase | Mitigation |
|---|---|---|---|---|
| Dual-agent fragmentation | HIGH | MEDIUM | 2 | single `HmxAgent` choke point |
| Zero test coverage for new | HIGH | HIGH | 1–20 | tests from Phase 1, preserve existing |
| Accessibility OEM divergence | MEDIUM | HIGH | 5 | robust selectors, OEM matrix, FLAG_SECURE fallback |
| LLM timeout/malformed | MEDIUM | HIGH | 2,15 | retry+replan, bounded context, local fallback |
| Firebase coupling | LOW | LOW | 2 | isolate behind interface, no direct calls |
| Memory fragmentation / large context | MEDIUM | MEDIUM | 9 | hard budgets, rank/truncate |
| Background execution killed | HIGH | HIGH | 16,18 | foreground service + checkpoint resume |
| OEM kills service | MEDIUM | HIGH | 5,19 | rebind, persistent notification, VD fallback |
| Shizuku unavailable | MEDIUM | HIGH | 12 | fallback to UI, never assume |
| Termux bridge failure | MEDIUM | MEDIUM | 11 | health check + reinit + alternate env |
| Browser/CDP failure | LOW | MEDIUM | 13 | timeouts, recovery, verification |
| Crash handling itself crashes | CRITICAL | LOW | 1 | crash-safe logger, bounded, no allocation in handler |
| Sensitive data exposure | CRITICAL | HIGH | 9,17 | filter at source + store + log |
| Excessive autonomy / loops | CRITICAL | MEDIUM | 7,16 | step/time/retry/risk limits, loop detection, safe abort |
| Infinite recovery loops | HIGH | MEDIUM | 7 | ≤3 retries, escalation |
| Config corruption | MEDIUM | LOW | 0 | defensive parse, defaults |
| Battery drain from polling | MEDIUM | MEDIUM | 18 | event-driven, 2s cache, no wakelock |

---

## 19. Milestones

| Milestone | Phase | Acceptance |
|---|---|---|
| M0 Baseline stable | 0 | `BASELINE.md` + 0 VERIFIED mismatches |
| M1 Diagnostics + tests | 1 | overlay on test crash, 50-bound, tests green |
| M2 Unified Agent | 2 | `HmxAgent` regression pass, orchestrator events |
| M3 Capability-aware | 3–4 | `snapshot()` correct, tool list filtered |
| M4 Verified actions | 6 | 5 verifiers >95% on regression set |
| M5 Self-recovering | 7 | loop detection ≤3, destructive guard, stress 50 failures no infinite loop |
| M6 Multi-environment | 10–13 | router 5 scenarios + fallback, Termux/Shizuku/Browser advancing |
| M7 Bounded autonomy | 8,16 | risk matrix + step/time/risk limits enforced |
| M8 Long-running | 15–16 | 30-min workflow survives kill → resume |
| M9 Production-ready | 20 | §25 checklist all green, QA report, 0 P0 risks |

---

## 20. Phase Gates

```
PHASE START → PRECONDITIONS VERIFIED (deps, baseline, device) → IMPLEMENTATION → TESTS (unit+integration+instrumented per §8)
  → REAL DEVICE VALIDATION (if UI/perception/VD) → REGRESSION CHECK (existing tests green) → SECURITY CHECK (secrets, tiers)
  → PERFORMANCE CHECK (budgets) → PHASE ACCEPTED → NEXT PHASE
If any gate fails: STOP → DIAGNOSE → FIX → RETEST → ACCEPT (no blind continue).
```

---

## 21. Recommended Implementation Order

Strictly §9 order; parallel tracks: 3∥4, 11∥12, 13∥14, 17∥18. Deviations require written justification (better dependency found during Phase 0 re-audit).

Priority: P0 correctness/safety/observability (0–2) → P1 reliability/testability/capability (3–5) → P2 execution power (6–10) → P3 autonomy/polish (11–20). Never prioritize flashy autonomy over reliability.

---

## 22. Recommended First Implementation Step

**Phase 1: Regression Safety + Diagnostics Foundation.**

1. **Why:** Unlocks observability for every later phase; safe + reversible (feature-flag); no auth touch; proves test harness.
2. **Existing component:** `util` + `trace/RuntimeEventBus` + `AppSettingsStore` + `AgentService` (add handler, no auth change).
3. **New component:** `HmxDiagnostics` + `RuntimeLogger` + `LogBuffer(50)` + `CrashHandler` + `DiagnosticOverlay` + `CrashReportStore` + `SensitiveDataFilter`.
4. **First test (unit):** `LogBufferTest` — push 60 entries → size==50, oldest dropped, order preserved; `SensitiveDataFilter` redacts `password: foo` → `[REDACTED]`.
5. **First real-device test:** Trigger `NullPointerException` in debug build → overlay appears within 1s → COPY LOG copies 50 recent entries → SAVE REPORT writes JSON → CLOSE dismisses → no crash from logger itself.
6. **First milestone:** M1.
7. **Explicitly NOT touched:** `auth/*`, `AuthStoreHolder`, `OpenAIOAuth`, login/signup UI/nav, `PolicyEngine` logic, LLM providers.

Do not start Phase 2 until Phase 1 gate passes.

---

## 23. Per-Phase Implementation Prompts

> Each prompt below is copy-paste for a future AI coding agent. Agent must read it verbatim.

### PHASE 0 IMPLEMENTATION PROMPT

```
OBJECTIVE: Establish BASELINE.md and verify audit vs repo.
SCOPE: Read repo + docs/audit_*.md, diff claims vs reality, write docs/BASELINE.md.
OUT OF SCOPE: Any code, any auth change, any behavior.
FILES TO INSPECT: all docs/audit_*.md, app/src/main/kotlin/ai/closepaw/**, build.gradle.kts, settings.gradle.kts
FILES EXPECTED TO CHANGE: docs/BASELINE.md (ADD)
FILES THAT MUST NOT CHANGE: app/src/main/kotlin/ai/closepaw/auth/**, any .kt/.xml/.gradle
ARCHITECTURE: INPUT repo+audits → PROCESS ls/read diff → OUTPUT BASELINE.md + discrepancy log
STEPS: 1) ls packages 2) read each audited file header 3) compare classification 4) write BASELINE.md (commit hash, file counts, test counts, discrepancies) 5) list UNKNOWN
TESTS: snapshot file list, test count
VALIDATION: 0 unresolved VERIFIED mismatches
FAILURE HANDLING: mismatch → re-audit that file, mark UNKNOWN
ROLLBACK: delete BASELINE.md
ACCEPTANCE: BASELINE.md committed, discrepancy log 0 VERIFIED gaps
FINAL REPORT: changed files, test results, limitations, open UNKNOWNs
STOP IF: repo not readable
AUTH ZERO TOUCH: enforced — no auth file read beyond existence check
```

### PHASE 1 IMPLEMENTATION PROMPT

```
OBJECTIVE: Ship HmxDiagnostics (RuntimeLogger, LogBuffer 50, CrashHandler, DiagnosticOverlay, CrashReportStore, RuntimeEventBus, SensitiveDataFilter) behind flag.
SCOPE: diagnostics only; wire into SessionServices + AgentService; no auth.
OUT OF SCOPE: Agent core, Policy, LLM, Memory, Env, Termux/Shizuku/Browser changes, any auth.
FILES TO INSPECT: app/src/main/kotlin/ai/closepaw/session/SessionServices.kt, app/AppSettingsStore.kt, app/AgentService.kt, util/*, trace/RuntimeEventBus.kt, docs/BASELINE.md
EXPECTED TO CHANGE: util/HmxDiagnostics.kt ADD, util/RuntimeLogger.kt ADD, util/LogBuffer.kt ADD, util/CrashHandler.kt ADD, util/SensitiveDataFilter.kt ADD, ui/DiagnosticOverlay.kt ADD, trace/RuntimeEventBus.kt MODIFY, session/SessionServices.kt MODIFY (provide diagnostics), app/AppSettingsStore.kt MODIFY (diagnosticsEnabled flag)
MUST NOT CHANGE: auth/**, AuthStoreHolder, PolicyEngine.kt, LLMClient.kt, Perceptor.kt
ARCHITECTURE: Agent→RuntimeLogger→LogBuffer(50, rotation 100/5min)→CrashHandler→Overlay/Store; handler never throws; filter at entry
STEPS: 1) LogBuffer ring 2) SensitiveDataFilter 3) RuntimeLogger (try/catch) 4) CrashHandler chaining default handler 5) Overlay (SYSTEM_ALERT_WINDOW + notification fallback) 6) Store rotation 10 7) wire + flag 8) emit 3 pilot events (Agent started, Tool, Error)
TESTS: UNIT LogBuffer bounds + filter, INTEGRATION crash→overlay, INSTRUMENTED ANR, FAILURE logger throws must not crash, REGRESSION existing tests
VALIDATION: overlay appears <1s on test crash, COPY LOG works, 50 bound, no regression
FAILURE: logger exception swallowed, OOM drops oldest
ROLLBACK: flag off, remove wiring
ACCEPTANCE: M1 — overlay on test crash, COPY/SAVE/CLOSE, 50-bound verified, 0 regressions
REPORT: changed files, test results (pass/fail), known limitations (overlay needs permission), next phase readiness
STOP IF: existing tests break
AUTH ZERO TOUCH: verify no auth file modified (git diff -- auth/)
```

### PHASE 2 IMPLEMENTATION PROMPT

```
OBJECTIVE: Introduce HmxAgent + TaskOrchestrator wrapping Agent (thin delegation).
SCOPE: unified brain choke point; no logic move from Agent internals.
OUT OF SCOPE: Capability/Risk/Verification/Recovery, any auth, LLM/policy changes
INSPECT: agent/Agent.kt, agent/AgentTurnRunner.kt, session/SessionServices.kt, app/AgentService.kt
CHANGE: agent/HmxAgent.kt ADD, agent/TaskOrchestrator.kt ADD, agent/Planner.kt ADD (interface), agent/Agent.kt MODIFY (delegate), session/SessionServices.kt MODIFY, app/AgentService.kt MODIFY (call HmxAgent)
MUST NOT: auth/**, PolicyEngine.kt, Perceptor.kt
ARCH: HmxAgent owns Agent instance; orchestrator emits diagnostics events
STEPS: 1) HmxAgent.run() delegates 2) TaskOrchestrator skeleton 3) Planner interface 4) wire 5) flag fallback to legacy Agent.run()
TESTS: UNIT orchestrator delegates, INTEGRATION legacy path still works, REGRESSION agent tests
ACCEPTANCE: HmxAgent runs existing task end-to-end, diagnostics show orchestrator events, fallback works
AUTH ZERO TOUCH: git diff auth/ must be empty
```

### PHASE 3 IMPLEMENTATION PROMPT

```
OBJECTIVE: CapabilityManager + CapabilityState + Tool requiredCapabilities.
SCOPE: DeviceState stub ok, real provider comes Phase 4.
INSPECT: tool/*, platform/*, agent/TaskOrchestrator.kt
CHANGE: tool/CapabilityManager.kt ADD, tool/Capability.kt ADD, tool/CapabilityState.kt ADD, tool/ToolSpec.kt MODIFY, platform/* MODIFY (report health)
MUST NOT: auth/**
ARCH: DeviceState → Manager → filtered ToolSpecs + alternatives
TESTS: UNIT state transitions, INTEGRATION tool filtering, INSTRUMENTED permission revoke
ACCEPTANCE: snapshot covers 7 envs, tool exposure matches, REVOKED case handled
AUTH ZERO TOUCH
```

### PHASE 4 IMPLEMENTATION PROMPT

```
OBJECTIVE: HmxDeviceState + DeviceStateProvider (AVAILABLE/UNAVAILABLE/UNKNOWN/DEGRADED per field, cache 2s).
INSPECT: platform/*, termux/*, browser/*, app/AppSettingsStore.kt
CHANGE: platform/DeviceStateProvider.kt ADD, model/HmxDeviceState.kt ADD, termux/*+platform/virtualdisplay/*+browser/* MODIFY (expose health)
MUST NOT: auth/**
TESTS: UNIT field states, INTEGRATION emulator vs real device, FAILURE unavailable→UNKNOWN
ACCEPTANCE: all §6.2 fields correct on 2 devices
AUTH ZERO TOUCH
```

### PHASE 5 IMPLEMENTATION PROMPT

```
OBJECTIVE: Perception advancement — Selector, ElementFinder, WaitPolicy, scrollTo, stale handling, FLAG_SECURE, service-death.
INSPECT: perception/*, platform/*, tool/action/*
CHANGE: perception/Selector.kt ADD, perception/ElementFinder.kt ADD, perception/WaitPolicy.kt ADD, perception/Perceptor.kt MODIFY, platform/* MODIFY
MUST NOT: auth/**
TESTS: UNIT selector ranking, INSTRUMENTED wait/FLAG_SECURE/service-death, REAL DEVICE 3 OEMs
ACCEPTANCE: waitForElement p95 <2s, stale retry, 2-OEM smoke
AUTH ZERO TOUCH
```

### PHASE 6 IMPLEMENTATION PROMPT

```
OBJECTIVE: VerificationEngine VERIFIED/FAILED/UNCERTAIN — desired state, never action success.
INSPECT: agent/verification/* (new), tool/*, perception/*
CHANGE: agent/verification/VerificationEngine.kt ADD, agent/verification/Verifier.kt ADD, tool/* MODIFY (declare desiredState)
MUST NOT: auth/**
TESTS: UNIT verifiers, INTEGRATION tap→screen reached vs not
ACCEPTANCE: 5 verifiers >95% on regression set
AUTH ZERO TOUCH
```

### PHASE 7 IMPLEMENTATION PROMPT

```
OBJECTIVE: RecoveryManager with retry≤3, loop detection, destructive guard, escalation, safe abort.
INSPECT: agent/recovery/*, agent/cognition/policy/*, agent/verification/*
CHANGE: agent/recovery/RecoveryManager.kt ADD, agent/cognition/policy/* MODIFY
MUST NOT: auth/**
TESTS: UNIT strategy, FAILURE duplicate+loop, INTEGRATION fixes missing-element
ACCEPTANCE: loop ≤3, destructive blocked, 50-failure stress no infinite loop
AUTH ZERO TOUCH
```

### PHASE 8 IMPLEMENTATION PROMPT

```
OBJECTIVE: RiskEngine around PolicyEngine (KEEP PolicyEngine file untouched).
INSPECT: tool/PolicyEngine.kt (read only), tool/*, platform/*, agent/recovery/*
CHANGE: tool/RiskEngine.kt ADD, tool/RiskLevel.kt ADD, tool/ToolRouter.kt MODIFY (merged stricter-wins)
MUST NOT: tool/PolicyEngine.kt (no edit), auth/**
TESTS: UNIT risk scoring, INTEGRATION policy+risk stricter-wins, SECURITY suite
ACCEPTANCE: §6.4 matrix, BLOCKED absolute, CRITICAL requires approval
AUTH ZERO TOUCH — explicitly verify PolicyEngine.kt diff empty
```

### PHASE 9 IMPLEMENTATION PROMPT

```
OBJECTIVE: MemoryManager + SensitiveDataFilter + MemoryPolicy + pipeline retrieve→rank→filter→dedup→budget→inject (3000/600).
INSPECT: memory/*
CHANGE: memory/MemoryManager.kt ADD, memory/SensitiveDataFilter.kt ADD, memory/MemoryPolicy.kt ADD, memory/MemoryStore.kt MODIFY (single filter call), memory/MemoryRecaller.kt MODIFY
MUST NOT: auth/** (tokens never through MemoryStore)
TESTS: UNIT filter, INTEGRATION budget, REGRESSION memory tests
ACCEPTANCE: secret→redacted/rejected, pipeline ≤3000 chars
AUTH ZERO TOUCH — confirm no auth token in memory tests
```

### PHASE 10 IMPLEMENTATION PROMPT

```
OBJECTIVE: EnvironmentRouter ranking + health check + fallback.
INSPECT: platform/*, tool/CapabilityManager.kt, model/HmxDeviceState.kt
CHANGE: platform/EnvironmentRouter.kt ADD, platform/* MODIFY (health)
MUST NOT: auth/**
TESTS: UNIT ranking, INTEGRATION Termux down fallback, INSTRUMENTED VD unavailable
ACCEPTANCE: 5 scenarios correct, fallback verified
AUTH ZERO TOUCH
```

### PHASE 11 IMPLEMENTATION PROMPT

```
OBJECTIVE: Termux advancement — WorkspaceManager, streaming, cancellation, file/git/edit helpers (incremental).
INSPECT: termux/*
CHANGE: termux/WorkspaceManager.kt ADD, termux/StreamingCommand.kt ADD, termux/* MODIFY
MUST NOT: auth/**
TESTS: UNIT workspace, INTEGRATION long-running+cancel, FAILURE bridge death
ACCEPTANCE: workspace + cancel verified, bridge death recovery
AUTH ZERO TOUCH
```

### PHASE 12 IMPLEMENTATION PROMPT

```
OBJECTIVE: ShizukuHealthMonitor + VD fallback when unavailable.
INSPECT: platform/virtualdisplay/*
CHANGE: platform/virtualdisplay/ShizukuHealthMonitor.kt ADD, platform/virtualdisplay/* MODIFY
MUST NOT: auth/**
TESTS: UNIT Binder death, INSTRUMENTED Shizuku absent→degraded UI path
ACCEPTANCE: fallback graceful, no crash when Shizuku missing
AUTH ZERO TOUCH
```

### PHASE 13 IMPLEMENTATION PROMPT

```
OBJECTIVE: Browser reliability — BrowserSessionManager, TabManager, JS Verifier, timeouts.
INSPECT: browser/**, agent/verification/*
CHANGE: browser/TabManager.kt ADD, browser/* MODIFY
MUST NOT: auth/**
TESTS: UNIT session lifecycle, INTEGRATION JS+verify
ACCEPTANCE: session survives navigation, verifier correct
AUTH ZERO TOUCH
```

### PHASE 14 IMPLEMENTATION PROMPT

```
OBJECTIVE: Skills declare capabilities/tools/risk/permissions/versions/fallback; subagents enforce Router+Risk.
INSPECT: agent/definition/*, agent/subagent/*, agent/cognition/skills/*
CHANGE: agent/definition/AgentRoleDef.kt MODIFY, agent/subagent/* MODIFY, agent/cognition/skills/* MODIFY
MUST NOT: auth/**, PolicyEngine.kt
TESTS: UNIT skill respects risk, INTEGRATION subagent cannot escalate
ACCEPTANCE: skill declaration enforced, subagent blocked on CRITICAL
AUTH ZERO TOUCH
```

### PHASE 15 IMPLEMENTATION PROMPT

```
OBJECTIVE: WorkflowEngine + WorkflowState/Checkpoint for multi-step + waiting + persistence.
INSPECT: agent/*, session/*
CHANGE: agent/workflow/WorkflowEngine.kt ADD, agent/workflow/WorkflowState.kt ADD, agent/workflow/Checkpoint.kt ADD
MUST NOT: auth/**
TESTS: UNIT checkpoint, INTEGRATION kill→resume
ACCEPTANCE: workflow survives process death via checkpoint
AUTH ZERO TOUCH
```

### PHASE 16 IMPLEMENTATION PROMPT

```
OBJECTIVE: Long-running + ResumeManager + TaskState/ExecutionState + background execution.
INSPECT: agent/workflow/*, app/AgentService.kt
CHANGE: agent/workflow/ResumeManager.kt ADD, agent/workflow/TaskState.kt ADD
MUST NOT: auth/**
TESTS: INSTRUMENTED 30-min with interruption
ACCEPTANCE: resume after kill, no duplicate side effects
AUTH ZERO TOUCH
```

### PHASE 17 IMPLEMENTATION PROMPT

```
OBJECTIVE: Security hardening — Policy+Risk+Capability+tool/skill perms audit; fix non-auth findings.
SCOPE: no new features; pen-test checklist for shell/file/system/privileged/browser/comm/secrets/memory/logs/screenshots/clipboard/notifications
INSPECT: tool/*, memory/*, util/*, browser/*, termux/*, platform/*
CHANGE: fixes as needed (non-auth) + docs/SECURITY_REVIEW.md ADD
MUST NOT: auth/** (explicitly excluded from scope)
TESTS: SECURITY suite, manual review
ACCEPTANCE: review doc + 0 CRITICAL non-auth findings open
AUTH ZERO TOUCH — verify
```

### PHASE 18 IMPLEMENTATION PROMPT

```
OBJECTIVE: Perf/battery — optimize screenshots, tree, LLM, memory, logs, polling, serialization; measure.
INSPECT: perception/*, llm/*, memory/*, util/*, platform/*
CHANGE: targeted optimizations behind flags, docs/PERF_REPORT.md ADD
MUST NOT: auth/**
TESTS: PERFORMANCE perception <200ms p95, LLM <2s, memory <5MB, battery <5%/day
ACCEPTANCE: metrics met, no regression
AUTH ZERO TOUCH
```

### PHASE 19 IMPLEMENTATION PROMPT

```
OBJECTIVE: Production reliability + real-device QA — ≥3 OEMs × 2 Android versions × Termux/Shizuku matrix.
INSPECT: all
CHANGE: docs/QA_REPORT.md ADD, fixes for P0/P1 OEM bugs only
MUST NOT: auth/**, no big refactors
TESTS: REAL DEVICE matrix, OEM, FAILURE injection, battery, long-running, crash recovery
ACCEPTANCE: QA report green, 0 P0, ≤2 P1
AUTH ZERO TOUCH
```

### PHASE 20 IMPLEMENTATION PROMPT

```
OBJECTIVE: Final integration — verify M9, zero-touch, release checklist.
INSPECT: all + docs/*
CHANGE: docs/RELEASE_CHECKLIST.md ADD
MUST NOT: auth/**, no new code except P0 bug fixes
TESTS: full suite + real device smoke
ACCEPTANCE: §25 checklist all green, 0 P0 risks, zero-touch report clean
AUTH ZERO TOUCH — final compliance report
```

---

## 24. Zero-Touch OAuth/Login/Signup Report

| Phase | OAuth untouched | Login untouched | Signup untouched | Auth untouched | Token arch untouched | Callback untouched | Account mgmt untouched |
|---|---|---|---|---|---|---|---|
| 0 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 1 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 2 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 3 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 4 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 5 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 6 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 7 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 8 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 9 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 10 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 11 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 12 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 13 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 14 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 15 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 16 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 17 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ (explicitly excluded) |
| 18 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 19 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 20 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

Enforcement: `git diff -- app/src/main/kotlin/ai/closepaw/auth/ app/AuthStoreHolder.kt` must be empty per phase gate; any violation → STOP, revert.

---

## 25. Final Master Checklist

```
[✓] Repository actually analyzed (ls + read, not invented)
[✓] Audit findings incorporated (15 docs/audit_*.md)
[✓] Existing functionality preserved (KEEP classification)
[✓] No unnecessary rewrite (REPLACE=none)
[✓] Dual-agent problem addressed (unified HmxAgent, Phase 2)
[✓] LLM architecture addressed (bounded context, streaming, multi-provider kept)
[✓] Accessibility addressed (Phase 5 advancement, OEM/FLAG_SECURE)
[✓] Perception addressed (selectors, wait, stale, dedup kept)
[✓] Actions addressed (CapabilityManager filtered ToolSpecs)
[✓] PolicyEngine preserved (KEEP, RiskEngine wraps, Phase 8)
[✓] DeviceState added (Phase 4)
[✓] CapabilityManager added (Phase 3)
[✓] RiskEngine added (Phase 8, around PolicyEngine)
[✓] VerificationEngine added (Phase 6)
[✓] RecoveryEngine added (Phase 7, bounded)
[✓] EnvironmentRouter added (Phase 10)
[✓] Memory architecture addressed (Phase 9, layered + filter)
[✓] Termux addressed (Phase 11)
[✓] Shizuku addressed (Phase 12, fallback)
[✓] Browser/CDP addressed (Phase 13)
[✓] Skills addressed (Phase 14)
[✓] Subagents addressed (Phase 14, isolated + safe)
[✓] Live runtime logging added (Phase 1, bounded)
[✓] Crash diagnostics added (Phase 1, CrashHandler+Overlay+Store)
[✓] ANR diagnostics considered (Phase 1 watchdog)
[✓] Crash-safe logger design (never throws, bounded)
[✓] Sensitive-data filtering considered (Phase 1,9,12,17)
[✓] Testing strategy included (§15)
[✓] Real-device testing included (§15, Phase 19 ≥3 OEMs)
[✓] Performance included (§16, budgets per phase)
[✓] Battery included (§16, no wakelock, 2s cache)
[✓] Security included (§14, Policy+Risk+Capability)
[✓] Failure scenarios included (every phase)
[✓] Dependency graph included (§9)
[✓] State machines included (§11, 9 machines)
[✓] File-level impact included (§17)
[✓] Risk register included (§18)
[✓] Milestones included (§19, M0–M9)
[✓] Phase gates included (§20)
[✓] Per-phase implementation prompts included (§23, 21 prompts)
[✓] No implementation code generated (interfaces only)
[✓] No repository files modified (planning-only)
[✓] OAuth/Login/Signup untouched (every phase + §24)
```

---

## Appendix — Failure-First Coverage

Every major system answered "what happens when this fails?" in its phase `Failure Scenarios` + `Recovery`. Matrix:

Accessibility failure → wait+re-observe+fallback selector (5) → verification UNCERTAIN (6) → recovery re-observe/switch tool (7) → env router alternative (10)
App crash → re-observe → reopen app → replan (7)
Agent crash → CrashHandler report + checkpoint resume (1,15,16)
LLM timeout/malformed → retry+replan, bounded context (2,16)
Tool failure → verification FAILED → recovery switch tool/env (6,7,10)
Permission denial → Capability TEMPORARILY_UNAVAILABLE → recovery ASK_USER (3,4)
Termux unavailable/bridge death → DEGRADED → router fallback to UI (11,10)
Shizuku/Binder death → health monitor → fallback UI (12)
Browser/CDP failure → timeout+recovery+verification (13)
Network loss → UNKNOWN for network field, retry with backoff (4,10)
UI changed / element missing → waitForElement + scrollTo + alternate selector (5,7)
Screen locked → DeviceState screen=UNAVAILABLE → pause workflow, resume on unlock (4,16)
OEM kills service → rebind + notification + VD fallback (5,12,19)
Memory pressure / OOM → LogBuffer drops oldest, checkpoint persists (1,15)
Low battery → DeviceState DEGRADED → defer non-urgent, reduce polling (4,18)
Process death → Checkpoint resume (15,16)
Config corruption → defensive parse + defaults (0)
Storage unavailable → UNKNOWN, degrade to memory-only (4)
Unknown error → UNCERTAIN → recovery escalation → ask user → safe abort (7)

---

*End of HMX Master Plan. Next: review + approve → execute Phase 0→1 via its implementation prompt. Do not code before approval.*

