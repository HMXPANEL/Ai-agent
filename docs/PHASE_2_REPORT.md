# PHASE 2 — Unified Agent Core: Implementation Report

- Auth diff: `git diff -- app/src/main/kotlin/ai/closepaw/auth/` = empty. ZERO TOUCH holds.
- Existing `Agent`/`AgentTurnRunner`/`AgentSession` internals: untouched except 4 `override`
  keywords + 1 interface clause in `Agent.kt` (zero behavior change).

## Added

| File | Responsibility |
|---|---|
| `agent/HmxAgentExecutor.kt` | Minimal run/pause/resume/stop handle; implemented by `Agent`, faked in tests |
| `agent/Planner.kt` | `HmxTaskInput`, `HmxPlan` (always `useLegacyPath=true` in Phase 2), `Planner`, `DefaultPlanner` |
| `agent/TaskOrchestrator.kt` | `orchestrate(input, execute)`: task_started → planning → execution events; planner failure → single legacy run; execute failure → `Error`; `execute` invoked at most once (no loop by construction); never throws for logging/event failures (`runCatching`) |
| `agent/HmxAgent.kt` | Owns executor + orchestrator; `run()` orchestrated; pause/resume/stop delegate directly (legacy path intact) |

## Modified

- `agent/Agent.kt`: `: HmxAgentExecutor` + `override` on run/pause/resume/stop. No logic change.
- `session/SessionAgentRunner.kt`: `start()` runs via `HmxAgent(executor, diagnostics.eventBus, goal, session/task)`; pause/stop/cancel still drive `Agent` directly.

## Deliberately NOT built (later phases)

CapabilityManager (P3 done separately), DeviceStateProvider (P4), RiskEngine (P8),
EnvironmentRouter (P10), VerificationEngine (P6), RecoveryManager (P7).

## Tests (JVM unit, fakes only — no Android deps)

- `HmxAgentTest` (4): delegation+event order; pause/resume/stop passthrough; planner-failure fallback runs once; executor throw → `Error`, runs once.
- `TaskOrchestratorTest` (4): happy path order; planner-failure fallback; execute-failure → `Error` with exactly 1 execution (no retry loop); null eventBus safe.
- `PlannerTest` (2): `DefaultPlanner` legacy plan; `HmxPlan.fallback`.
- Existing agent tests untouched; execution via CI (`:app:testDebugUnitTest`).

## Gate

- HmxAgent/TaskOrchestrator/Planner exist; Agent internals intact; legacy path works
  (same `Agent` instance, same call); fallback single-run verified by test; orchestration
  events emitted; auth untouched. Real-device run: BLOCKED (no hardware), tracked.
