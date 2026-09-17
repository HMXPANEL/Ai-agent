# PHASE 3 — Capability Manager + Tool Architecture: Status

- Objective: capability-gated tool selection — LLM is offered allowed ∩
  available tools; UNKNOWN denies by default; revocation never stays AVAILABLE.
- Implementation: `requiredCapabilities` declared on `MobileActionTool`,
  `SystemButtonTool` (`ACCESSIBILITY`) and `TermuxShellTool` (`TERMUX_SHELL`);
  `Turn.prepareRequest` filters allowlist ∩ `getAvailable(manager)` (wired via
  `TurnPlanningPhaseRunner` ← `SessionServices.capabilityManager` ← live
  `AndroidDeviceCapabilitySource`); BROWSER_CDP/SHIZUKU/VIRTUAL_DISPLAY/
  BACKGROUND_EXECUTION stay UNKNOWN (no probes yet — declared by no tool, so
  nothing else is filtered).
- Files changed: tool/impl (3), tool/AndroidDeviceCapabilitySource KDoc,
  tests (3 files + 1 new).
- Tests: Turn-level TEMPORARILY_UNAVAILABLE / REQUIRES_USER_APPROVAL /
  REQUIRES_PERMISSION / allowlist∩available; overlay revocation re-snapshot;
  declaration + registry end-to-end (plus pre-existing manager/source/registry
  suites).
- Build result: pending CI.
- GitHub Actions result: pending.
- Device validation: REQUIRED (real permission flips, Termux presence).
- Known limitations: accessibility treated as available while the service runs
  (live arbiter arrives Phase 4); no execution-time re-check (advertisement
  gate only); browser/CDP gating stays in `DefaultBrowserScriptCapabilityGate`.
- Next phase: Phase 4 (after CI green).
