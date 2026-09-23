# Implementation Gap Re-assessment — 00

**Date:** 2026-09-24 · **HEAD:** `478dd2f` (docs-complete) · **Method:** fresh source re-verification; old audit trusted only as a checklist.
**Rule applied:** doc-phase fixes (Phases 0–8) reclassified; only code/test/build gaps below.

## Verdict table

| # | Gap (from 04/06/07/08/09) | Re-verdict | Evidence (current HEAD) |
|---|---|---|---|
| G1 | Execution-time capability re-check absent | **CONFIRMED MISSING** | Zero `capability` references in `tool/ToolRouter.kt`; gating only via `ToolRegistry.getAvailable` at prompt time (`Turn.prepareRequest`) + `DefaultBrowserScriptCapabilityGate` per-call for browser |
| G2 | Per-tool capability declarations beyond 3 tools | **PARTIALLY IMPLEMENTED** | Only `MobileActionTool`/`SystemButtonTool` (ACCESSIBILITY) + `TermuxShellTool` (TERMUX_SHELL) declare; BROWSER_CDP/SHIZUKU/VD/BG undeclared |
| G3 | Battery/network DeviceState fields | **CONFIRMED MISSING** | Zero battery/network references in `device/`; STATUS documents deferral |
| G4 | Memory redaction/encryption | **CONFIRMED MISSING** | Zero redaction references in `memory/*` + `RememberExperienceTool`; `SensitiveDataFilter` applied only in logger/store/overlay/trace paths |
| G5 | MediaStore user-visible dir | **INTENTIONALLY DEFERRED** | `PHASE_2_STATUS.md` records SDK unavailability; app-external root shipped; needs device re-verification, not code |
| G6 | Termux `/v1/cancel` | **INTENTIONALLY DEFERRED** | Documented design (TCP-disconnect kill); no action |
| G7 | Device validation Phases 1–4 | **BLOCKED** (environment) | No device in this environment (see Phase H assessment); must not be faked |
| G8 | `OAuthCodexValidator` dead | **CONFIRMED (dead code)** | Zero references outside `auth/OpenAIOAuth.kt` definition; `OpenAiSignIn` skips it |
| G9 | `protocol/` tests absent | **CONFIRMED, low risk** | No `protocol/` test dir; pure data types |
| G10 | `storage/BackupManager` tests absent | **CONFIRMED MISSING (P0)** | `find -iname "*BackupManager*"` in both test trees: empty; only `history/ChatBackup` covered |
| G11 | Perf benchmark absent | **CONFIRMED MISSING (P3)** | No sustained profile in `app/src/test` or `eval/` |
| G12 | okhttp 4.12.0 vs mockwebserver 5.2.1 skew | **NEEDS INVESTIGATION** | Versions confirmed in `app/build.gradle.kts`; compat unproven |
| G13 | security-crypto 1.1.0-alpha06 | **NEEDS INVESTIGATION** | Confirmed in deps; stable-migration safety unproven |
| G14 | core-ktx pin 1.12.0 vs transitive 1.17.0 | **CONFIRMED (trivial)** | Stale pin; harmless, align opportunistically |
| G15 | `MainActivity.kt:919` `runBlocking` | **CONFIRMED (minor)** | Present; thread-context to assess before touching |
| G16 | `Thread.sleep` on binder/pairing threads (5 sites) | **CONFIRMED (minor)** | All on background threads; `OpenAIOAuth.kt:352` inside suspend retry — low risk |
| G17 | Termux bridge no auth token | **NEEDS INVESTIGATION** | Loopback-only confirmed; threat model to assess before adding token |
| G18 | Doc contradictions (7) | **ALREADY FIXED** | Fixed Phases 1/4/8; sweep clean |
| G19 | Undocumented systems (05) | **ALREADY FIXED** | All have doc homes (Phases 1–6); only `InsecureSslConfig` deliberately code-only |
| G20 | Release `INSECURE_SSL_FOR_EVAL` | **ALREADY FIXED (verified)** | `"false"` literal confirmed Phase 6 |
| G21 | Legacy `onboarding_secure_prefs` on disk | **CONFIRMED (trivial P2)** | `OnboardingStore.kt:79-81` comment; nothing reads it |
| G22 | `Turn.run()` non-stream role | **DOCUMENTED** | `llm.md` notes Compactor-only use; no code action needed |
| G23 | `LOCAL_LFM` factory throw | **DOCUMENTED (by design)** | `llm.md` notes direct construction; no action |

## Action queue (this run)

- **P0:** G10 (BackupManager behavior tests), G12 (skew verdict), G13 (crypto verdict)
- **P1:** G1 (exec-time capability check + tests), G3 (battery/network assess→implement), G4 (memory redaction assess→implement), G17 (Termux token verdict)
- **P2:** G8 (validator remove-or-document), G15/G16 (concurrency touch-ups if safe), G21 (legacy prefs wipe), G14 (pin align), G9 (protocol tests if cheap)
- **P3:** G11 (practical JVM-side measurements only)
- **Blocked:** G7 (device matrix, no faking), G5 (needs device)
