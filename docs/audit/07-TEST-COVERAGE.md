# Test Coverage — 07 (behavior-level)

**Counts (verified):** `app/src/test` 240 files · `app/src/androidTest` 27 files · total 267. (Phase-0 BASELINE cited 210+27; suite grew.)

## Matrix

| System | Implementation | Unit | Instrumented | Integration | Gap |
|---|---|---|---|---|---|
| Agent loop/turns/planning | Yes | Yes (39: `AgentRunLoopTest`, `Turn*Test`, `Planner*`, `SubAgent*`) | — | eval/ AndroidWorld bridge | None |
| LLM/routing/retry | Yes | Yes (22: `CodexSseParserTest`, `ToolCallDeltaAccumulatorTest`, discovery, routing) | — | — | None |
| Tool routing/policy/registry | Yes | Yes (38: `PolicyEngineTest`, registry, `ShellToolBlocklistTest`) | — | — | None |
| Actions/fallbacks | Yes | Yes (10: `Click/LongPress/Scroll/Swipe/TypeExecutorTest`, `TargetResolverTest`, `StrategyRouter`, `TextVerification`) | debug-run scripts | — | None |
| Verification | Yes | Yes (`OutcomeVerifierTest`, `TaskVerificationGateTest`, `TrackerTest`, `StructuredTaskParserTest`) | — | — | None |
| Perception | Yes | Yes (3: `PerceptorTest`, `PerceptorInternalsTest`, `ScreenSummaryTest`) | — | — | None |
| Session/checkpoint | Yes | Yes (17: `SessionCheckpointCoordinatorTest`, reload, routing, todos/scratchpad) | — | — | None |
| Memory | Yes | Yes (2: `MemoryStoreTest`, `MemoryRecallerTest`) | settings UI (MemoryFileEditor, MemorySettingsPage) | — | None |
| Browser/CDP | Yes | Yes (29: CDP client, ADB pairing/crypto incl. SPAKE2 KAT, script bridge) | Yes (3: pairing, script runner, real-device QA) | — | None |
| Termux | Yes | Yes (3: manager, adapter, bridge resource) | Yes (1: cleartext localhost) | — | None |
| Shizuku/VD | Yes | Yes (7 VD + shizuku transports/gateway under browser+platform) | — | — | Real-device binder exercise only |
| Security | Yes | Yes (scattered: `CognitionTraceRedactorSecurityTest`, `SensitiveDataFilterTest`, `AppClassifierSecurityTest`, `CapturePrivacyGateTest`, `ShellToolBlocklistTest`, `MainActivityIntentApplierSecurityTest`) | — | — | None structural |
| Backup/restore | Yes | **PARTIAL** (`ChatBackupTest` covers `history/ChatBackup`; **`storage/BackupManager` zero tests**) | — | — | **P0: BackupManager create/restore/list/rollback** |
| History/compaction | Yes | Yes (11: `CompactorTest`, buffers, storage, converters) | — | — | None |
| Trace/diagnostics | Yes | Yes (5: artifacts, redactor, recorder, bus) | — | — | None |
| Auth/OAuth | Yes | Yes (3: `AuthStoreTest`, `OpenAIOAuthTest`) | — | — | None |
| Onboarding | Yes | Yes (7: step states, validator, store, VM) | — | — | None |
| UI chat/capsule/settings | Yes | Yes (34: VM, reducer, handoff, capsule state, gates/mappers) | Yes (23: 19 qa + 4 settings) | — | None |
| DeviceState (Phase 4) | Yes | Yes (1 file, 7 tests) | — | CI pending | None |
| Capability (Phase 3) | Yes | Yes (8 tests per report) | — | — | Execution-time path untestable until implemented |
| protocol/ (pure data) | Yes | **No** | — | — | P2 (low risk) |
| debug/ tooling | Yes | **No** | — | — | P2 (dev-only) |
| eval/ harness | Yes | Yes (7 Python tests) | — | AndroidWorld runs | Device/emulator runs only |

## Notes

- Package-name traps when auditing: `tool/` (not `tools/`), `tool/action/` (not `actions/`), verification logic in `agent/` + `tool/action/` (no `verification/` dir), security tests scattered (no `security/` dir), backup split `history/ChatBackup` (tested) vs `storage/BackupManager` (untested).
- What tests actually verify (sampled): loop budgets and stop reasons; foreground/field/bubble/recipient verification incl. typed-but-unsent failure; policy deny/allow/ask matrix with BLOCKED escapes; target resolution incl. overlap edges; privacy masking for BLOCKED apps; shell blocklist; prompt-JSON canonicalization; compaction cut safety; backup export/verify round-trip + tamper rejection (for `ChatBackup`, not `BackupManager`); SPAKE2 KAT + pairing crypto; checkpoint state snapshots.
- No sustained memory/CPU benchmark exists.
