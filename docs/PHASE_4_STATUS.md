# PHASE 4 — Device State: Status

- Objective: `HmxDeviceState` + provider with per-field availability; bounded
  event-driven snapshots; orchestration use; stale/refresh tests.
- Implementation: new `device/` package — `HmxDeviceState` (timestamp,
  foregroundPackage, accessibilityAvailable, overlayGranted, capabilities,
  display, sessionPhase, per-field states; `isStale`), `HmxDeviceStateProvider`
  (`current`/`refresh`/`observe`/`onExternalEvent`), probe-injected
  `AndroidHmxDeviceStateProvider` (2s cache, every probe failure degrades its
  field, never throws); wired into `SessionServices` (live platform/context
  probes) and refreshed once per task in `SessionAgentRunner.start`.
- Files changed: device/ (2 new), session/SessionServices.kt,
  session/SessionAgentRunner.kt, 1 new test file.
- Tests: 7 provider tests (fresh values, cache hit, stale re-read, refresh
  emit, event invalidation, probe-failure degradation, field mapping).
- Build result: pending CI.
- GitHub Actions result: pending.
- Device validation: REQUIRED (real foreground app, overlay flips).
- Known limitations: no polling (by design); accessibility probe reads service
  binding; battery/network fields deferred (no requirement in orchestration yet).
- Next phase: Phase 5 (after CI green).
