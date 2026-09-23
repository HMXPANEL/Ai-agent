# ClosePaw Forensic Audit — 00 Master Audit

**Date:** 2026-09-23 · **HEAD:** `db9f1aa` (Phase 4 DeviceState) · **Scope:** `docs/` (26 files) + `doc/main|dev|release` (62 files) + `app/src` (681 Kotlin files) + Gradle/manifest/resources/tests
**Method:** read-only; every documentation claim cross-checked against source; execution paths traced definition → construction → registration → invocation → result. No code modified.

## 1. Executive summary

ClosePaw is an on-device Android AI agent (Kotlin, ReAct loop, BYO LLM) that drives the phone via AccessibilityService or a Shizuku-backed Virtual Display, with tools for UI action, shell, Termux, browser CDP automation, todos/scratchpad, memory, skills, and subagents. The repository is large, well-tested (240 unit + 27 instrumented test files), and CI-green through Phase 4.

**Headline verdict:** the core agent loop (perceive → plan → act → observe → verify) is **real and wired end-to-end**. LLM routing (model-led single choke point), tool routing (registry → policy → approval → execution), perception (Perceptor), action fallbacks, outcome verification gating, session lifecycle, memory, Termux bridge, browser CDP, Shizuku/VD, approval/policy, backup/restore, and log redaction are all **implemented, reachable, and tested**. The codebase is substantially healthier than its documentation suggests — but documentation is fragmented across three eras (`doc/main/*` spec corpus, `docs/audit_*.md` per-file audits, `docs/PHASE_*/HMX_*` plan/status reports) with known contradictions, and a handful of real gaps remain.

**Critical gaps (P0):**
1. `storage/BackupManager` (create/restore/list) has **zero direct unit tests** — only adjacent `ChatBackupTest` covers a different class. Restore path (checksum verify, atomic rename, rollback) is untested.
2. `okhttp:4.12.0` vs `mockwebserver:5.2.1` major-version skew in the test harness — verify no linkage issue.
3. `security-crypto:1.1.0-alpha06` (alpha) in the auth path (`AuthStore`); track stable.
4. `minSdk 31` + `compileSdk/targetSdk 36` + `suppressUnsupportedCompileSdk=35` — build requires current tooling; API-36 emulator images required for eval.

**Most important doc-vs-code findings:**
- Verification **is** in the runtime loop (`TurnExecutionPhaseRunner.verifyTurnOutcome` → `OutcomeVerifier.verifyTurn` → `decideTurnOutcome` gate blocks unverified `complete_task(success)` same-turn and cross-turn). Docs that describe "simple action success" are obsolete.
- Provider selection is **global** (`AppSettingsStore.selectedModel/selectedProvider` → `SessionConfig.mainModel`), not per-chat. Persisted per-chat model is restored into the global store. Any doc claiming per-chat routing is outdated.
- `getAvailable()` capability gating was unwired at forensic-audit time, then wired for 3 tools in Phase 3 (`MobileActionTool`/`SystemButtonTool`→ACCESSIBILITY, `TermuxShellTool`→TERMUX_SHELL). No execution-time re-check exists (advertisement-time only).
- Four `docs/audit_*.md` drift points are corrected by `docs/BASELINE.md` D-002/D-003/D-004/D-006 (browser-script path, LLM impl count, policy AUTO_APPROVE behavior, Termux `snapshot` signature). Trust BASELINE over the raw audits on those points.
- `doc/main/*` has at least 6 internal contradictions (`recentFullScreens` 3 vs 2, ApiKey states 10 vs 11, SessionState 5 vs 6, capsule 8 vs 9 modes, single-role vs Standalone/Planner/Executor, `onMainAppVisible` scope).

## 2. Repository architecture (actual, from source)

```
Chat UI / Capsule overlay / Settings / Onboarding
  ↓ Op (submit) / AgentEvent (observe)
SessionCoordinator → AgentSession → SessionAgentRunner → HmxAgent → Agent
  → AgentTurnRunner → TurnPlanningPhaseRunner → Turn → LLMClient → provider API
  → TurnExecutionPhaseRunner → ToolRouter → ToolRegistry/PolicyEngine → Tool impl
  → AndroidPlatform (AccessibilityPlatform | VirtualDisplayPlatform+Shizuku)
  → Perceptor → ScreenSnapshot → next turn
Memory (MemoryStore/MemoryRecaller) · History (HistoryManager/Compactor) ·
Trace (AgentTrace/RuntimeEventBus) · Checkpoint (SessionCheckpointCoordinator) ·
Backup (BackupManager/ClosePawStorage) · Browser CDP · Termux bridge
```

Single choke points (confirmed): provider = `SessionLlmBootstrapper` + `LLMClientFactory.create`; tools = `ToolRouter.execute`; completion = `AgentTurnRunner.decideTurnOutcome`; restore = `AgentSession.reload`. See `02-ARCHITECTURE-MAP.md`.

## 3. Current implementation state

| Area | Status | Evidence |
|---|---|---|
| Agent loop | Implemented, reachable, tested | `Agent.kt:59`, `AgentTurnRunner.kt:74`, 39 agent tests |
| LLM routing (5 providers, 4 clients) | Implemented, reachable, tested | `LLMClientFactory.kt:53`, 22 llm tests |
| Tool routing + policy + approval | Implemented, reachable, tested | `ToolRouter.kt:64`, `PolicyEngine.kt`, 38 tool tests |
| Perception | Implemented, reachable, tested | `Perceptor.kt:27/54`, 3 perception tests |
| Action fallbacks | Implemented, reachable, tested | `tool/action/*Executor`, 10 action tests |
| Verification gate | Implemented, reachable, tested | `TurnExecutionPhaseRunner.kt:85-114`, `OutcomeVerifier.kt`, gate+tracker+parser tests |
| Session lifecycle | Implemented, reachable, tested | `AgentSession.kt`, `SessionCoordinator.kt`, 17 session tests |
| Memory | Implemented, reachable, tested | `MemoryStore.kt`, `MemoryRecaller.kt`, 2 tests |
| Browser CDP | Implemented, reachable, tested | `BrowserSessionManager.kt`, 29 browser tests |
| Termux bridge | Implemented, reachable, tested | `TermuxBridgeManager.kt`, 3 tests + bridge py |
| Shizuku/VD | Implemented, reachable, tested | `VirtualDisplayPlatform.kt`, 7 VD tests |
| Backup/restore | Implemented, reachable, **BackupManager untested** | `BackupManager.kt`, `ChatBackupTest` (different class) |
| Security (EncryptedSharedPrefs, redaction, policy) | Implemented, reachable, tested | `AuthStore.kt`, `SensitiveDataFilterTest`, redactor tests |
| UI (chat/capsule/settings/onboarding) | Implemented, reachable, tested | 34 ui unit + 23 qa/settings instrumented tests |
| Capability gating | Partial (3 tools, no exec-time re-check) | `PHASE_3_STATUS.md`, `CapabilityManager` |
| DeviceState (Phase 4) | Implemented, tests, CI pending | `device/HmxDeviceState*`, 1 test file (7 tests) |
| HmxAgent/Planner/Orchestrator (Phase 2) | Implemented (`useLegacyPath=true`), tested | `Planner.kt`, `TaskOrchestrator.kt`, 10 tests |

## 4. Major findings

1. **Verification is real.** Not a stub: `verifyTurnOutcome` runs every actuating turn, VERIFY lines enter history (model sees them), `TaskVerificationTracker` carries unresolved actuation cross-turn, `decideTurnOutcome` converts unverified `complete_task(success)` into recoverable Error. (Sec 9 of `02`, Sec "Verification" of `03`.)
2. **Provider model is global.** `AppSettingsStore` → `SessionConfig.mainModel` → `AgentModelResolver` → factory. Subagents inherit. No per-chat routing. (`PROVIDER_FORENSIC_AUDIT.md` verdict confirmed against `SessionLlmBootstrapper.kt`, `AgentModelResolver.kt`.)
3. **Tool availability is 3-stage** (bootstrapper 9 + services 2 + runner 2 = up to 13 of 14 canonical names) filtered by allowlist ∩ registry ∩ capabilities at prompt time (`Turn.prepareRequest`). No permanently orphan tool; gating makes `browser_script` (pref default off), `activate_skill` (empty catalog), `termux_shell` (snapshot unavailable) absent in practice.
4. **Capability gating is advertisement-time only.** Phase-3 wiring covers 3 tools; BROWSER_CDP/SHIZUKU/VD/BG stay UNKNOWN; zero execution-time re-check. Fail-closed design (UNKNOWN = unavailable) is correct but narrow.
5. **Storage story weakened mid-Phase-2.** Plan wanted MediaStore user-visible `ClosePaw/`; status reports app-external root with MediaStore "unavailable via SDK". Uninstall-survival claim needs re-verification.
6. **Auth boundary holds.** Every phase doc asserts empty auth diff; forensic audit verifies via git log; `AuthStore` throws (no silent fallback) on Keystore failure; credentials excluded from backups by design.
7. **Device evidence is the universal gap.** All phase reports mark real-device validation BLOCKED/REQUIRED. CI-green ≠ device-proven (Shizuku binder, VD lifecycle, wireless-ADB pairing, Termux on OEM ROMs, demo-task E2E).

## 5. Documentation health

- `docs/` (26 files): 15 per-file audits (4 with known drift, corrected by BASELINE) + BASELINE + forensic provider audit + HMX plans + phase reports/status. Status: mostly COMPLETE as historical record; `audit_llm_client_kt`/`audit_policy_engine_kt`/`audit_browser_script_kt`/`audit_termux_bridge_manager_kt` NEED_UPDATE on the 4 drift points (or a pointer to BASELINE).
- `doc/main/` (53 files): the normative spec corpus. NEED_UPDATE: 6 internal contradictions (§7 of `03`); `session.md` stale role paragraph; `planning.md`/`runtime.md`/`turn_prompt_anatomy.md` `recentFullScreens` disagreement; `README.md` code-tree SessionState count.
- Root: `README.md` COMPLETE (minor: verify `shell` one-command claim vs `TermuxShellTool` full-bash distinction); `SECURITY.md`/`PRIVACY_POLICY.md` COMPLETE; `AGENTS.md` stub → `CLAUDE.md` COMPLETE.

Full registry: `01-DOCUMENT-REGISTRY.md`. Mismatches: `03-DOCUMENTATION-VS-CODE.md`. Missing: `04-MISSING-FEATURES.md`. Undocumented: `05-UNDOCUMENTED-FEATURES.md`.

## 6. Architecture health

Sound: single choke points, fail-closed policy/capability, TOCTOU re-check on approval, atomic writes (memory temp+rename, backup staged+rename), bounded buffers (`LogBuffer` 50, `RuntimeEventBus` 64, crash reports 10/64KB), bounded retries (agent recoverable ×1, compaction breaker ×3, LLM ×5 no-retry-after-partial). Debt: no execution-time capability re-check; `OAuthCodexValidator` dead; `Turn.run()` non-stream uncalled in loop; `LOCAL_LFM` factory throw by design (document); `MainActivity.kt:919` `runBlocking` on calling thread (inspect); `Thread.sleep` on binder/pairing threads instead of `delay`. Details: `06-TECHNICAL-DEBT.md`.

## 7. Test coverage

240 unit + 27 instrumented files. Every major subsystem has behavior-level tests **except** `storage/BackupManager` (zero direct tests), `protocol/` (no test dir — pure data, low risk), `debug/` (no test dir — dev tooling). Naming traps: `tools/`→`tool/` (38 tests), `actions/`→`tool/action/` (10), `verification/`→`agent/TaskVerification*` + `OutcomeVerifier` + `tool/action/TextVerification` (all covered), `security/`→ scattered (redactor, filter, classifier, privacy-gate, shell blocklist), `backup/`→`history/ChatBackupTest` (different class than `storage/BackupManager`). No sustained perf benchmark (50-turn screenshot+tree profile). Matrix: `07-TEST-COVERAGE.md`.

## 8. Build constraints

AGP 8.9.1 · Kotlin 2.3.0 · Gradle 8.11.1 · Java 17 · compile/target 36, min 31 · Compose BOM 2024.12.01 · R8+shrink in release · env-keystore signing · unit-test heap 2g · `jvmargs -Xmx4096m`. Flags: core-ktx pin 1.12.0 vs Leap-transitive 1.17.0 (stale pin); okhttp 4.12.0 vs mockwebserver 5.2.1 (verify); alpha security-crypto; `suppressUnsupportedCompileSdk=35`. minSdk 31 excludes Android <12 by design. Details: `08-BUILD-AND-RESOURCE-AUDIT.md`.

## 9. Security findings

Trust boundary User → LLM → tool call → Android → external app is gated by PolicyEngine (BLOCKED absolute, browser_script always-ask in SMART, session allowlist, TOCTOU re-check) + approval UX (60s timeout, package validation, DENIED never mutates allowlist) + verification gate + log redaction (`SensitiveDataFilter` in logger/store/overlay) + backup credential exclusion + Termux workspace jail + CDP per-session relay tokens. Residual risks: tokens decrypted in memory (documented in audits); JWT parsed without signature verification (documented); Termux bridge localhost without token (loopback reliance); no execution-time capability re-check (TOCTOU window post-approval is re-checked for package but not capability). Details: `09-SECURITY-AUDIT.md`.

## 10. Performance findings

Critical: per-turn screenshot (scale+JPEG+disk+Base64) and full a11y-tree snapshot+`toPromptJson` every turn. High: LLM context growth + compaction round-trips; PixelCopy→ImageReader fallback holding surfaces in VD mode. Medium: coroutine fan-out (2 outliers), checkpoint JSON, JPEG capacity alloc. Low: reactive flows, bounded retries. No profiler benchmark exists. Details: `08-BUILD-AND-RESOURCE-AUDIT.md`.

## 11. Implementation queue (summary)

- **P0:** BackupManager direct tests; okhttp/mockwebserver skew check; security-crypto alpha tracking; API-36 build/eval environment pin.
- **P1:** execution-time capability re-check; `recentFullScreens`/state-count doc contradictions; storage uninstall-survival re-verification; `runBlocking` + `Thread.sleep` outliers; Termux localhost token or documented loopback rationale.
- **P2:** remove or wire `OAuthCodexValidator`; document `Turn.run()` non-stream role; align core-ktx pin; `protocol/` + `debug/` minimal tests.
- **P3:** documentation completion plan (`10-DOCUMENTATION-COMPLETION-PLAN.md`).
- **P4:** perf benchmark; battery/network DeviceState fields; per-tool capability declarations beyond 3 tools.

---

*Self-check: all 88 markdown files read (26 docs/ + 62 doc/ + roots); all major execution paths traced (agent, LLM, tools, perception, actions, verification, session, memory, browser, Termux, Shizuku, VD, security, backup, build, tests); contradictions recorded; unknowns marked; no code changed.*
