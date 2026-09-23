# Documentation vs Code — 03

Verdict scale: MATCH · PARTIAL · MISMATCH · MISSING · OBSOLETE · UNKNOWN. Evidence at HEAD `db9f1aa`.

## A. Doc → code verification (major claims)

| # | Document claim | Implementation | Path status | Verdict |
|---|---|---|---|---|
| 1 | "Agent verifies task completion after every action" | `TurnExecutionPhaseRunner.verifyTurnOutcome:105` → `OutcomeVerifier.verifyTurn` every actuating turn + gate in `decideTurnOutcome` | defined→instantiated→called→consumed (VERIFY lines in history, tracker cross-turn) | MATCH (docs saying "simple action success" are OBSOLETE) |
| 2 | "Provider selected per chat" (older docs) | Global: `AppSettingsStore.selectedModel/Provider` → `SessionConfig.mainModel` → `AgentModelResolver`; per-chat value restored into global | Per-chat routing absent | MISMATCH — global is current; `PROVIDER_FORENSIC_AUDIT.md` normative |
| 3 | "Capability gating filters tools" | `getAvailable(manager)` wired for 3 tools (Phase 3); advertisement-time only; no exec-time re-check | Partially connected | PARTIAL |
| 4 | `audit_llm_client_kt` "two implementations" | 4 clients: OpenAIResponse, ChatCompletion, CodexResponse, LFMLLM | Doc undercounts | MISMATCH (see BASELINE D-003) |
| 5 | `audit_policy_engine_kt` "browser_script requires approval even in AUTO_APPROVE" | Code returns Allow in AUTO_APPROVE | Doc overstates | MISMATCH (see BASELINE D-004) |
| 6 | `audit_browser_script_kt` path `browser/script/BrowserScriptTool.kt` | Actual `tool/impl/BrowserScriptTool.kt` | Wrong path | MISMATCH (see BASELINE D-002) |
| 7 | `audit_termux_bridge_manager_kt` "`snapshot(enabled)` suspend" | Synchronous | Signature drift | MISMATCH (see BASELINE D-006) |
| 8 | "Session persists across reinstall" (Phase-2 plan MediaStore) | App-external root; MediaStore "unavailable via SDK" per STATUS | Weakened | PARTIAL — re-verify uninstall survival |
| 9 | "`shell` = one toybox command, no pipes" (README) | `ShellTool`: blocklist + metachar reject; `TermuxShellTool`: full bash via bridge | Both exist; README describes `shell` only | MATCH (clarify two shells in docs) |
| 10 | "Memory redaction/encryption" (implied durable-memory safety) | `MemoryStore` has NO redaction/encryption (flagged HIGH RISK in own audit) | Absent | MISSING |
| 11 | `Turn.run()` non-stream in loop | Only prod caller is `Compactor`; loop uses `runStreaming` | Defined, reachable (compaction), uncalled in turn path | PARTIAL (document role) |
| 12 | `OAuthCodexValidator` validates sign-in | No prod caller; `OpenAiSignIn` skips it | Dead | MISMATCH (dead code, doc implies live) |
| 13 | "Auth diff empty every phase" | Forensic verifies via git log; `AuthStore` throws on Keystore failure; creds excluded from backup | Holds | MATCH |

## B. Code contradictions (interface vs impl, flag vs consumer, test vs impl)

1. `LLMClientFactory` vs `LOCAL_LFM`: factory throws `IllegalStateException` for LOCAL by design; local instantiated directly in bootstrapper. Not a bug — document.
2. `ToolName` has 14 canonical names; `DefaultRoleDef` allowlist declares 13 (+`termux_shell` auto-appended). Consistent; `ask_user` backfilled by runner. No orphan.
3. `Capability.requiredCapabilities` defaults empty → no behavior change except 3 tools. BROWSER_CDP/SHIZUKU/VD/BG gating lives in `DefaultBrowserScriptCapabilityGate`, not the capability system. Two gating mechanisms coexist — unify or document.
4. `SessionState` sealed set includes TakeoverPending; `doc/main/README.md` tree omits it. Doc stale.
5. `HistoryConfig.recentFullScreens` default disputed (2 vs 3). Runtime truth must be read from `HistoryConfig.kt` and one doc fixed.
6. `OnboardingStepState` counts: 23 vs 24; ApiKey 10 vs 11 (`OAuthFinishing`). Count from source, fix docs.
7. `storage/BackupManager` vs `history/ChatBackup`: two backup-adjacent classes; only the latter tested. Flag for P0 tests.
8. `okhttp 4.12.0` vs `mockwebserver 5.2.1`: major skew. Verify linkage.
9. `core-ktx` direct pin 1.12.0 vs transitive 1.17.0. Stale pin; align.

## C. Documentation contradictions (doc vs doc)

| A | B | Source truth | Resolution |
|---|---|---|---|
| `planning.md` recentFullScreens 3 | `runtime.md` 2 | Read `HistoryConfig.kt` | Fix losing doc |
| `data_schemas.md` ApiKey 10 states | `onboarding_apikey_step.md` 11 | Count `ApiKeyStepState` subtypes | Fix losing doc |
| `onboarding_step_states.md` "23-case" | same file "24 members" | Count sealed subtypes | Fix number |
| `README.md` tree: 5 SessionStates | `protocol/overview.md`: 6 | `SessionState.kt:33-50` | Add TakeoverPending to tree |
| `ui/capsule/*`: 9 modes | `ui/overlay.md`: 8 modes | `CapsuleMode.kt` (Hidden counted differently) | Clarify counting |
| `session.md`: Standalone/Planner/Executor roles | unified single-role docs | `AgentRoleDef` (single Default) | Remove stale para |
| `capsule/user_flows.md`: onMainAppVisible 4 call sites | `overlay.md`: onResume/onStop | `MainActivity` source | Fix scope |
| Impl-audit: MediaStore `ClosePaw/` | STATUS: app-external, MediaStore unavailable | STATUS (later) | Mark audit superseded |
| Raw audits (4 drift points) | BASELINE D-002/3/4/6 | Source at Phase-0 | Trust BASELINE |
