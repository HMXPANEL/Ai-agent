# Documentation Completion Plan — 10 (exact order)

Do not implement code in this phase. Correct docs first; each step lists files + acceptance.

## Order

### Step 1 — Point raw audits at BASELINE (30 min)
Files: `docs/audit_browser_script_kt.md`, `audit_llm_client_kt.md`, `audit_policy_engine_kt.md`, `audit_termux_bridge_manager_kt.md`.
Action: prepend pointer header to `docs/BASELINE.md` D-002/D-003/D-004/D-006 (or correct the 4 claims in place).
Accept: no reader can take the drifted claim at face value.

### Step 2 — Fix `doc/main/*` contradictions (1–2 h)
1. `recentFullScreens`: read `HistoryConfig.kt`, set all three (`planning.md`, `runtime.md`, `turn_prompt_anatomy.md`) to the source value.
2. ApiKey states: count `ApiKeyStepState` subtypes; fix `data_schemas.md` (10) vs `onboarding_apikey_step.md` (11).
3. Sealed counts: fix `onboarding_step_states.md` 23/24 drift.
4. `README.md` tree: add TakeoverPending (6 states per `SessionState.kt`).
5. Capsule 8 vs 9: clarify Hidden-counting in `ui/overlay.md` + `ui/capsule/state_machine.md`.
6. `session.md`: delete stale Standalone/Planner/Executor paragraph.
7. `onMainAppVisible`: reconcile `capsule/user_flows.md` vs `overlay.md` against `MainActivity`.
Accept: `grep` for each disputed value returns one consistent answer.

### Step 3 — Document the undocumented systems (2–4 h)
New or extended specs (see `05`): capability system, DeviceState, HmxAgent/Planner/Orchestrator, ClosePawStorage/auto-backup, cross-turn verification tracker, text-verification states, interop/delta-accumulator, StrategyRouter, relay token + artifact cap, dual browser gates, routing log line, app_skills inventory.
Accept: every `05` row has a doc home or a "keep undocumented" rationale (only `InsecureSslConfig` qualifies).

### Step 4 — Clarify shell duality + provider globality in user docs (30 min)
`README.md`: distinguish `shell` (one-shot toybox) vs `termux_shell` (full bash). Any per-chat routing language → global. Mark Phase-2 MediaStore plan superseded by STATUS.
Accept: new user cannot confuse the two shells or expect per-chat models.

### Step 5 — Test-plan docs for P0 gaps (30 min)
Record in `doc/main/eval/eval.md` or new `doc/main/testing/`: BackupManager test plan, okhttp/mockwebserver verification, security-crypto tracking, API-36 env pin.
Accept: each P0 has an owner + test path.

## Source-truth resolutions (verified Phase 0, 2026-09-24 — binding for Phase 8)

| Term | Truth | Source |
|---|---|---|
| `HistoryConfig.recentFullScreens` default | **2** | `history/HistoryConfig.kt:6` |
| `SessionState` variants | **6** (Created/Running/Idle/TakeoverPending/Paused/Shutdown) | `protocol/SessionState.kt` |
| `ApiKeyStepState` subtypes | **11** (incl. `OAuthFinishing`) | `onboarding/OnboardingState.kt:55-68` |
| `CapsuleMode` subtypes | **9** (incl. Hidden) | `ui/overlay/model/CapsuleMode.kt` |
| Provider routing | **global** (`AppSettingsStore` → `SessionConfig.mainModel`) | `AgentModelResolver.kt`, `SessionLlmBootstrapper.kt` |
| Verification | **gating, same-turn + cross-turn** | `TurnExecutionPhaseRunner.kt:85-114`, `TaskVerificationTracker.kt` |
| Capability gating | **advertisement-time only, 3 tools** | `PHASE_3_STATUS.md`, `ToolRegistry.getAvailable` |

## Implementation queue (for after docs)

- **P0:** BackupManager direct tests (`create/restore/list/rollback`, tamper, missing sidecar, interrupted-rollback); okhttp 5.x vs mockwebserver compat check; security-crypto stable tracking; API-36 build/eval env pin. Evidence/affected files in `00` §§7–8, `07`, `08`.
- **P1:** execution-time capability re-check; doc contradictions (Step 2); uninstall-survival re-verification; `runBlocking`/`Thread.sleep` outliers; Termux loopback rationale/token; release `INSECURE_SSL_FOR_EVAL=false` confirmation.
- **P2:** `OAuthCodexValidator` wire-or-delete; `Turn.run()` role note; core-ktx pin; `protocol/`+`debug/` minimal tests; legacy secure-prefs wipe.
- **P3:** Steps 1–5 above.
- **P4:** perf benchmark; battery/network DeviceState; per-tool capabilities beyond 3.
