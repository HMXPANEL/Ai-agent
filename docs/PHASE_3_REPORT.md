# PHASE 3 — Capability Manager + Tool Architecture: Implementation Report

- Auth diff: `git diff -- app/src/main/kotlin/ai/closepaw/auth/` = empty. ZERO TOUCH holds.
- `ToolRegistry` existing behavior unchanged (`getAll`/`register`/`contains` intact).

## Added

| File | Responsibility |
|---|---|
| `tool/Capability.kt` | 7 capabilities: ACCESSIBILITY, OVERLAY, TERMUX_SHELL, BROWSER_CDP, SHIZUKU, VIRTUAL_DISPLAY, BACKGROUND_EXECUTION |
| `tool/CapabilityState.kt` | AVAILABLE/UNAVAILABLE/UNKNOWN/DEGRADED/REQUIRES_PERMISSION/REQUIRES_USER_APPROVAL/TEMPORARILY_UNAVAILABLE; only AVAILABLE = usable (fail closed) |
| `tool/CapabilityManager.kt` | `snapshot()` (never throws; failing source → empty), `stateOf()` (defaults UNKNOWN), `isAvailable()` (== AVAILABLE only), `canUse()` (all-required), `alternatives()` (available fallbacks only); `DeviceCapabilitySource` fun-interface + `UnknownDeviceCapabilities` stub (Phase 4 replaces it) |

## Modified

- `tool/ToolSpec.kt`: added `requiredCapabilities` with `emptySet()` default — all 17
  existing tools compile and behave exactly as before (no filtering until Phase 4 data).
- `tool/ToolRegistry.kt`: added `getAvailable(manager)`; additive only.

## Deliberately NOT built

Real DeviceStateProvider (P4), per-tool capability declarations (with P4/P10 data),
RecoveryManager (P7).

## Tests (JVM unit)

- `CapabilityManagerTest` (6): AVAILABLE-only-usable across all 7 states; unreported → UNKNOWN;
  stub → all closed; failing source → empty, no throw; `canUse` conjunction; alternatives filtered.
- `ToolRegistryFilteringTest` (2): available/unknown mix filters correctly; UNKNOWN fails closed while `getAll` unchanged.
- Existing tool tests untouched; execution via CI.

## Gate

- Manager/Capability/CapabilityState exist; ToolSpec declares `requiredCapabilities`;
  registry filters via `getAvailable`; snapshot covers 7 capabilities; UNKNOWN fails closed;
  revocation → non-AVAILABLE → excluded (TEMPORARILY_UNAVAILABLE handled as not-usable;
  live revocation events arrive with P4 provider); auth untouched. Real-device: BLOCKED, tracked.
