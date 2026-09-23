# Phase 5 — Completion Report

## Objective

Document actual Browser / Termux / Shizuku / Virtual Display integration paths, failure and fallback behavior, with no exaggeration.

## Documents Reviewed

`doc/main/infra/browser.md`, `doc/main/app/termux_shell.md`, `doc/main/infra/virtual_display.md`, `doc/main/infra/platform.md`, `docs/audit/02` (§§Browser/Termux/Shizuku/VD), `docs/audit/05` (#10, #11), `audit_browser_script_kt` (+Phase-0 pointer), `audit_shizuku_vd_kt`, `audit_virtualdisplay_platform_kt`, `audit_termux_bridge_manager_kt` (+Phase-0 pointer).

## Source Areas Reviewed

`tool/impl/BrowserScriptTool.kt`, `tool/impl/DefaultBrowserScriptCapabilityGate.kt`, `browser/script/BrowserSessionManager.kt:53-151,272-295` (lease/preflight/run/markBroken/artifact cap), `browser/script/BrowserScriptRunner.kt`, `browser/cdp/ChromeCdpClient.kt`, `browser/cdp/shizuku/ShizukuChromeDevtoolsBridge.kt:135-136`, `browser/cdp/RelayAuthToken.kt`, `browser/cdp/wireless/*`, `assets/agent_skills/browser-use/*`, `termux/TermuxBridgeManager.kt`, `termux/TermuxRunCommandAdapter.kt`, `tools/termux-bridge/closepaw_bridge.py:32-33,147,164-186,339,345` (workspace root/escape-400/busy-409/pgid-kill), `platform/PlatformFactory.kt:50-77` (VD→A11Y fallback), `platform/virtualdisplay/VirtualDisplayPlatform.kt` (start/stop/drain/Broken/cleanup), `platform/virtualdisplay/VdLifecycleArbiter.kt`, `platform/virtualdisplay/ShizukuClient.kt`.

## Changes Made

1. `doc/main/infra/virtual_display.md`: new "Requirements & Fallback" section (Shizuku mandatory; `PlatformFactory` falls back to AccessibilityPlatform + warning; binder-death → Broken; non-VD/non-CDP features Shizuku-free).
2. `doc/main/infra/browser.md`: dual-gate coexistence note (per-call `DefaultBrowserScriptCapabilityGate` is the effective block; `browser_script` declares no Phase-3 capabilities so `BROWSER_CDP` stays UNKNOWN).

## Documentation Corrections

- VD fallback path (previously code-only) now normative. Browser gating duality (previously split across phase reports) now stated in both `browser.md` and `tools.md`.

## Implemented Features Confirmed

CDP chain (tool → session manager → bridge → CDP client → runner; both transports Shizuku-mandatory); relay token + artifact cap + CAS enforcement; Termux probe→deploy→start→health→exec chain with jail/caps/timeout/pgid-kill (source-confirmed line refs above); VD create→operate→destroy with drain/broken/cleanup; shell input fallback; PixelCopy→ImageReader demotion.

## Missing / Deferred Features

No new gaps. `/v1/cancel` absent by design (documented); shell hardening beyond v1 deferred (documented); wireless-ADB self-pair remains Shizuku-dependent (documented).

## Contradictions Resolved

None in scope (browser/Termux/VD docs match source; AUTO_APPROVE Allow matrix already consistent with BASELINE D-004).

## Historical Documents Preserved

Yes — untouched.

## Tests / Validation Performed

No local builds per operator instruction. Static: zero source files in diff; bridge security claims re-verified at cited `.py` lines; factory fallback quoted from source; diff reviewed.

## Remaining Issues

Real-device validation (Shizuku binder health, VD lifecycle, wireless pairing, Termux on OEM ROMs) remains the universal unverified area — recorded in `04`/`07`, not misrepresented here.

## Git Commit

`docs(phase-5): document browser termux and shizuku`

## Git Push

BLOCKED — invalid credentials (see Phase 0). COMMITTED ONLY.

## Phase Status

COMPLETE
