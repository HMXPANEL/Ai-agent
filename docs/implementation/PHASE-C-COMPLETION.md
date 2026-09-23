# Implementation Phase C — Completion Report

## Objective

Close the advertisement-time-only capability gap: execution-time re-check in ToolRouter, wired into session + subagent routers, with behavior tests. No second capability architecture.

## Gaps Investigated

G1 (confirmed missing), G2 (partial declarations — left as-is by design).

## Gaps Confirmed

ToolRouter had zero capability references; only prompt-time `getAvailable` filtering existed.

## Changes Implemented

1. **`ToolRouter`**: optional `capabilityManager: CapabilityManager? = null` (default null = legacy behavior for existing callers/tests). After cancellation pre-check, before EXECUTING: denies with `ToolCallResult.Cancelled("Capability unavailable at execution: ...")` when any `tool.requiredCapabilities` is not AVAILABLE on the live source. Runs after policy + approval, so it cannot bypass either; tools without requirements (incl. `browser_script`) unaffected.
2. **`SessionToolingBootstrapper.create`**: new optional `capabilityManager` param, forwarded to ToolRouter.
3. **`SessionServices.create`**: CapabilityManager (live `AndroidDeviceCapabilitySource`) now built BEFORE tooling bootstrap and passed in; the same instance feeds services + DeviceState (duplicate construction removed — single source of truth).
4. **`SubAgentRunner`**: child router inherits `parentServices.capabilityManager`.
5. **`ToolRouterCapabilityTest`** (6 tests): available→executes; missing→denied unexecuted with capability in reason; revoked-during-approval→denied (models Shizuku death/Termux kill mid-approval); DEGRADED→denied fail-closed; requirement-free tool executes under UNKNOWN; null manager preserves legacy.
6. Docs: `tools.md` CapabilityManager section now states two-point gating; `04` G1 closed by implementation.

## Files Changed

- `tool/ToolRouter.kt`, `session/SessionToolingBootstrapper.kt`, `session/SessionServices.kt`, `agent/subagent/SubAgentRunner.kt`
- `app/src/test/.../tool/ToolRouterCapabilityTest.kt` (new)
- `doc/main/infra/tools.md`

## Architecture Changes

Same capability system, extended to a second enforcement point (no parallel architecture). `DefaultBrowserScriptCapabilityGate` intentionally untouched (different purpose: per-call transport preflight).

## Tests Added

6. Existing ToolRouter tests unaffected (null-manager default).

## Tests Run

None locally (operator forbids local execution). CI gate on push. CI-watch notes: test policy paths depend on `ToolName` categories ("scratchpad" non-screen→Allow; "mobile_action" screen-changing→Ask in ALWAYS_ASK) — verified against `PolicyEngine.check` steps 1/6 and `ToolName.isScreenChanging` by reading source.

## Build Results

Pending CI.

## Security Impact

Positive: closes TOCTOU window where approved tools executed with dead capabilities. Fail-closed; no policy/approval bypass introduced.

## Performance Impact

One synchronous `snapshot()` + set scan per execution — negligible (map lookup; source is in-memory).

## Documentation Updated

`tools.md` (two-point gating). Audit `04` G1 status flips to implemented (noted here; frozen audit files untouched per R3).

## Remaining Issues

G2 (more declarations) left for product decision — BROWSER_CDP/SHIZUKU/VD declarations would change advertised toolsets; proposing, not imposing.

## Git Commit

`fix(capability): enforce execution-time capability checks`

## Git Push

Deferred to end of run.

## Status

COMPLETE (pending CI verdict on push)
