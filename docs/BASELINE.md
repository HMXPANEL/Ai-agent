# HMX System Agent — Repository Baseline

Baseline date: 2026-09-03 (Asia/Kolkata). This is a Phase 0 read-only verification; no application source, XML, Gradle, dependency, or auth file was changed.

## 1. Repository

- Commit/hash: **UNKNOWN**. Both canonical paths (`/mnt/sdcard/AIProjects/closepaw-main` and its `/storage/emulated/0/...` alias) are not recognised as a Git worktree; no `.git` entry is present in the resolved directory.
- Repository state: **UNKNOWN**. `git status`, `git diff --stat`, and `git diff -- auth/` cannot run outside a Git worktree.
- Android modules: one application module, `:app` (`settings.gradle.kts`). Namespace and application ID: `ai.closepaw`.
- Build baseline: Android/Kotlin/Compose application; compile/target SDK 36, min SDK 31, JVM 17. Main manifest declares the launcher `MainActivity`, accessibility `AgentService`, Termux, Shizuku, overlay, internet, and microphone permissions.
- Important configuration: `settings.gradle.kts`, root and app `build.gradle.kts`, `gradle.properties`, `app/src/main/AndroidManifest.xml`, `app/proguard-rules.pro`, `gradle/wrapper/`.

## 2. Package/File Baseline

- Top-level Kotlin packages: **19** — `agent`, `app`, `auth`, `browser`, `debug`, `history`, `llm`, `memory`, `model`, `onboarding`, `perception`, `platform`, `protocol`, `session`, `termux`, `tool`, `trace`, `ui`, `util`.
- Main Kotlin files: **376**; main XML files: **10**; AIDL files: **2**; main assets: **27**; main relevant Kotlin/XML/AIDL/asset files: **415**.
- All files under `app/src`: **677**. Files reported by `rg --files` for the available checkout: **923**. The latter is the reproducible whole-checkout count used here; it does not include hidden/unlisted files.
- Main source roots: `app/src/main/kotlin`, `app/src/main/res`, `app/src/main/assets`, `app/src/main/aidl`; tests are in `app/src/test` and `app/src/androidTest`.
- Important implementation areas: agent loop/turn phases under `agent/`; tools/policy under `tool/`; accessibility and virtual display under `perception/` and `platform/`; Termux under `termux/`; CDP/browser scripting under `browser/`; session wiring under `session/`; persistent memory under `memory/`; trace recording under `trace/`.

## 3. Test Baseline

- JVM unit-test Kotlin files: **210**; test classes named `*Test`: **205**; `@Test` annotations: **2,057**.
- Instrumentation-test Kotlin files: **27**; test classes named `*Test`: **25**; `@Test` annotations: **111**.
- Locations: `app/src/test/kotlin/ai/closepaw/**` and `app/src/androidTest/kotlin/ai/closepaw/**`, including agent, browser/CDP, LLM, memory, perception, platform/virtualdisplay, session, Termux, tools, trace, UI, and QA coverage.
- Execution: **NOT RUN**. `./gradlew test` was intentionally not run because this Phase 0 instruction permits only `docs/BASELINE.md` to change, while the configured Gradle build has source-resource copy work (`preBuild`) and can create build/generated artifacts. Existing test sources are present and structurally discoverable; compilation/execution remains **UNKNOWN**.

## 4. Audit Verification

All 15 `docs/audit_*.md` files were read. Auth implementation was intentionally not inspected; only frozen auth-file existence was checked, in accordance with the zero-touch rule.

| Area | Audit Claim | Repository Reality | Status |
|---|---|---|---|
| Agent core (`audit_agent_kt.md`) | `Agent.run()` owns the ReAct lifecycle, pause/resume/stop, compaction, retry, memory fallback, and trace events. | `agent/Agent.kt` contains those lifecycle controls, compaction handling, one recoverable retry, `AgentTurnRunner`, and `AgentTrace`. Named unit tests exist. | VERIFIED |
| Turn runner (`audit_agent_turn_runner_kt.md`) | One turn performs perception, planning, execution, observation, and outcome/error handling. | `AgentTurnRunner.executeTurn()` captures a screen, calls the planning and execution phase runners, and returns `TurnExecutionResult`; cancellation/error handling is present. | VERIFIED |
| Planning (`audit_turn_planning_phase_runner_kt.md`) | Planning builds context, calls an LLM, applies arbitration, and records trace/history. | `TurnPlanningPhaseRunner` exists with `runPlanningPhase()` and the stated session dependencies. Its runtime quality was not device/network exercised. | VERIFIED |
| Execution (`audit_turn_execution_phase_runner_kt.md`) | Selected calls go through `ToolRouter`, emit history/trace/UI events, and capture post-action observation. | `TurnExecutionPhaseRunner.executeActions()` and the cited routing/capture integration exist. Formal desired-state verification is absent. | VERIFIED |
| Tool router/registry/spec (`audit_tool_router_kt.md`) | Router implements validation → policy → approval → execution → terminal cleanup, with cancellation/TOCTOU handling. | `ToolRouter`, `ToolCallState`, `ToolRegistry`, and `ToolSpec` exist with that lifecycle. No HMX capability/risk/environment layer exists. | VERIFIED |
| Policy (`audit_policy_engine_kt.md`) | `PolicyEngine.check()` preserves BLOCKED as an absolute floor and applies canonical ordering. | `PolicyEngine.check()` has the documented non-screen/escape/BLOCKED/browser/session/mode ordering. One browser-script AUTO_APPROVE statement in the audit is contradicted; see D-004. | VERIFIED |
| Perception (`audit_perceptor_kt.md`) | Perceptor traverses accessibility trees, supports multi-root input, deduplication, visibility/truncation, and prompt JSON. | `Perceptor.snapshot()` overloads and `toPromptJson()` implement these behaviours; `PerceptorFilterConfig`/internals and tests are present. | VERIFIED |
| LLM (`audit_llm_client_kt.md`) | `LLMClient` is an abstract streaming/tool-call interface with `glm-5` default and retry constants. | `LLMClient` provides `chatWithTools`, `chatWithToolsStreaming`, `DEFAULT_MODEL = glm-5`, and `MAX_RETRIES = 5`. The audit understates the implementation count; see D-003. | VERIFIED |
| Memory (`audit_memory_storemd_kt.md`) | Markdown memory is scoped under internal files, bounded, atomically written, and lacks dedicated sensitive-data filtering. | `MemoryStore` and `MemoryRecaller` exist with the stated file model and limits. No `SensitiveDataFilter`/`MemoryManager` file exists. | VERIFIED |
| Termux (`audit_termux_bridge_manager_kt.md`) | Bridge readiness and capability snapshot are captured during `SessionServices` creation with a 2-second timeout. | `TermuxBridgeManager.ensureReadyForSession()` and `SessionServices.TERMUX_HEALTH_CHECK_TIMEOUT_MS = 2_000L` exist; snapshot capability gating exists. The audit's `snapshot` suspension signature is incorrect; see D-006. | VERIFIED |
| Browser/CDP (`audit_browser_script_kt.md`) | Browser scripting has a session manager, CDP bridge, validation, timeout/cancellation, gating, and trace support. | `browser/script/BrowserSessionManager.kt`, CDP/Shizuku bridge files, and `tool/impl/BrowserScriptTool.kt` provide these components. The audit's tool file/package path is incorrect; see D-002. | VERIFIED |
| Shizuku (`audit_shizuku_vd_kt.md`) | Shizuku client and display/input/activity transports support privileged virtual-display operations. | `ShizukuClient`, runtime gateway, proxy provider, and cited transports exist. Real-device binder health/reconnection behaviour was not exercised. | INFERRED |
| Virtual display (`audit_virtualdisplay_platform_kt.md`) | `VirtualDisplayPlatform` plus `VdLifecycleArbiter` supports an end-to-end VD lifecycle. | Both classes and supporting capture/input/surface files exist. Full device lifecycle/fallback behaviour is not proven by this read-only review. | INFERRED |
| Skills/subagents | Skill catalog/manager and subagent runner are available. | `AgentSkillManager`, catalog/frontmatter/installer files and `SubAgentRunner` exist; HMX declaration/isolation extensions do not. | VERIFIED |
| Session/app structure | `SessionServices` is the DI/session assembly point; `MainActivity` and `AgentService` are entry/service components. | `SessionServices.kt`, `MainActivity.kt`, and `AgentService.kt` exist and contain the stated roles. | VERIFIED |
| Diagnostics/current logging | Current diagnostics are Logcat-only. | Android `Log` calls are extensive, but optional file-based `TraceRecorderFactory`/`FileTraceRecorder`, `AgentTrace`, and trace redaction also exist. No HMX diagnostics suite exists. | NOT SUPPORTED |
| Auth (`audit_auth_store_kt.md`, `audit_openai_oauth_kt.md`) | Frozen auth/OAuth implementation and related behaviour are described. | `auth/*.kt` and `app/AuthStoreHolder.kt` exist. Their implementation details were deliberately not read and thus behavioural claims cannot be re-verified in Phase 0. | UNKNOWN |
| Tests | Unit and Android-test coverage exists across the described subsystems. | 210 JVM and 27 instrumentation Kotlin test files are present. Results are unknown because tests were not run. | VERIFIED |

## 5. Classification Verification

| Component | Classification | Evidence | Status |
|---|---|---|---|
| `Agent` / `AgentTurnRunner` / phases | KEEP → MODIFY | Current ReAct loop and phase split exist and are tested; no `HmxAgent`/`TaskOrchestrator` exists. | VERIFIED |
| `ToolRouter` | MODIFY | Current central policy-driven routing lifecycle exists; no capability/risk/environment routing files exist. | VERIFIED |
| `PolicyEngine` | KEEP | Existing canonical ordering and BLOCKED floor are present; `RiskEngine` is absent. | VERIFIED |
| `ToolRegistry` / `ToolSpec` | KEEP / MODIFY | Registry/spec exist; `requiredCapabilities` is absent. | VERIFIED |
| `Perceptor` | KEEP → ADVANCE | Existing traversal/configuration is substantial; selectors, waits, and stale-node recovery files are absent. | VERIFIED |
| `LLMClient` / prompt | KEEP / ADVANCE | Streaming tool-call interface and multiple providers exist; HMX context-budget/sensitive filter additions are absent. | VERIFIED |
| `SessionServices` | MODIFY | Existing DI/session composition exists; HMX diagnostics/orchestrator/device-state providers are absent. | VERIFIED |
| Memory | MODIFY | `MemoryStore`/`MemoryRecaller` exist; `MemoryManager`, `MemoryPolicy`, and dedicated filter are absent. | VERIFIED |
| Termux | KEEP → ADVANCE | Bridge readiness and snapshot gating exist; `WorkspaceManager`/streaming-command additions are absent. | VERIFIED |
| Browser/CDP | KEEP → ADVANCE | CDP session/bridge/runtime are present; `TabManager` is absent. | VERIFIED |
| Shizuku / virtual display | EXTEND / ADVANCE | Current transports and lifecycle arbiter exist; `ShizukuHealthMonitor` and explicit HMX fallback addition are absent. | VERIFIED |
| Skills / subagents | EXTEND | Existing skill and subagent implementations exist; HMX declarations and per-subagent risk/router enforcement are absent. | VERIFIED |
| Device/capability/risk/environment/verification/recovery/workflow | ADD / MISSING | `HmxDeviceState`, `DeviceStateProvider`, `CapabilityManager`, `RiskEngine`, `EnvironmentRouter`, `VerificationEngine`, `RecoveryManager`, and workflow/resume files are absent. | VERIFIED |
| Diagnostics | ADD | Existing Logcat and trace facilities exist, but no `HmxDiagnostics`, runtime logger, log buffer, crash handler, or diagnostic overlay file exists. | VERIFIED |
| REPLACE / REMOVE | None | Current subsystems remain present; no repository evidence requires a Phase 0 replacement/removal decision. | VERIFIED |
| Auth / login / signup | KEEP (FROZEN) | Auth files and `AuthStoreHolder` exist; no implementation inspection or modification was performed. | VERIFIED |
| Existing tests | KEEP | Broad JVM/instrumentation test source structure exists. | VERIFIED |

## 6. Discrepancy Log

- **D-001**
  - File: `docs/HMX_MASTER_PLAN.md` §1
  - Audit claim: “946 files, 19 packages.”
  - Actual repository state: 19 top-level Kotlin packages is correct; this checkout reports 923 files via `rg --files`, 677 under `app/src`, and 415 main relevant source/resource files. No available count yields 946.
  - Severity: Medium (baseline inventory drift).
  - Resolution: Record reproducible counts here; do not modify the master plan in Phase 0.
  - VERIFIED: VERIFIED

- **D-002**
  - File: `docs/audit_browser_script_kt.md`
  - Audit claim: BrowserScriptTool is at `browser/script/BrowserScriptTool.kt` in `ai.closepaw.browser.script`.
  - Actual repository state: browser runtime is in `browser/script/`, but the agent-facing `BrowserScriptTool` is `app/src/main/kotlin/ai/closepaw/tool/impl/BrowserScriptTool.kt`, package `ai.closepaw.tool.impl`.
  - Severity: Low (documentation path/package drift).
  - Resolution: Preserve source; use the actual path in future work.
  - VERIFIED: VERIFIED

- **D-003**
  - File: `docs/audit_llm_client_kt.md`
  - Audit claim: “Two concrete implementations: OpenAIResponseClient, LFMLLMClient.”
  - Actual repository state: those classes exist, and `CodexResponseClient` plus `ChatCompletionClient` also extend the LLM abstraction.
  - Severity: Medium (provider inventory understated).
  - Resolution: Treat the broader provider set as baseline; no source change.
  - VERIFIED: VERIFIED

- **D-004**
  - File: `docs/audit_policy_engine_kt.md`
  - Audit claim: browser script “requires explicit approval even in AUTO_APPROVE mode.”
  - Actual repository state: `PolicyEngine.browserScriptDecision()` returns `Allow` for `AUTO_APPROVE`; it asks in `ALWAYS_ASK` and `SMART`.
  - Severity: High (security-policy documentation mismatch).
  - Resolution: Do not alter policy in Phase 0; resolve the documented policy intent before Phase 8 work.
  - VERIFIED: VERIFIED

- **D-005**
  - File: `docs/HMX_MASTER_PLAN.md` §2 / §4
  - Audit claim: diagnostics are “Logcat-only.”
  - Actual repository state: Logcat is used, but `trace/TraceRecorderFactory.kt` creates `FileTraceRecorder` when tracing is enabled; `AgentTrace` and trace redaction are also present.
  - Severity: Medium (existing observability understated).
  - Resolution: HMX diagnostics remains an additive plan, but Phase 1 must account for existing trace output.
  - VERIFIED: VERIFIED

- **D-006**
  - File: `docs/audit_termux_bridge_manager_kt.md`
  - Audit claim: `snapshot(enabled)` is suspend.
  - Actual repository state: `TermuxBridgeManager.snapshot(enabled)` is synchronous; readiness probing is suspend.
  - Severity: Low (signature drift).
  - Resolution: Use the actual API signature in future work.
  - VERIFIED: VERIFIED

No source discrepancies were repaired in this phase.

## 7. Unknowns

- Git commit/hash, worktree status, `git diff --stat`, and `git diff -- auth/` are unavailable because the supplied checkout is not a Git worktree.
- Test compilation/execution result is unknown; tests were intentionally not run to preserve the “only BASELINE.md changes” boundary.
- Real-device/OEM behaviour for accessibility, Termux bridge reliability, Shizuku binder lifecycle, virtual display lifecycle, and browser/CDP connectivity was not exercised.
- Auth/OAuth behavioural audit claims are unknown because their implementation was intentionally not read under the frozen-auth rule.
- A clean pre-existing repository state cannot be independently established without Git metadata.

## 8. Phase 1 Readiness

**NO.** The repository architecture and audit discrepancies have been recorded, and no implementation source was changed. However, the Phase 0 acceptance gate requires Git commit/status/diff confirmation, including an auth-specific diff, and this checkout has no usable Git worktree. Restore or provide the repository’s `.git` metadata, rerun the final Git checks, and re-accept this baseline before beginning Phase 1.
