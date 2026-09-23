# Missing Features — 04 (documented/planned but not implemented)

Reason policy: evidence only (TODO/FIXME, feature flags, stubs, missing registration/permission/dependency/UI entry, git history). Otherwise "Reason unknown from repository evidence."

| # | Feature (as documented) | Status | Evidence | Why missing |
|---|---|---|---|---|
| 1 | Execution-time capability re-check | NOT IMPLEMENTED | Phase-3 STATUS "Limitations: no execution-time re-check"; no call site re-checks `CapabilityManager` inside `ToolRouter.execute` | Deferred by design; reason: Phase 3 scoped to advertisement-time (STATUS says so) |
| 2 | Per-tool capability declarations beyond 3 tools | NOT IMPLEMENTED | Only `MobileActionTool`/`SystemButtonTool`/`TermuxShellTool` declare; BROWSER_CDP/SHIZUKU/VD/BG remain UNKNOWN | Deferred; browser gating lives in `DefaultBrowserScriptCapabilityGate` instead |
| 3 | `DeviceStateProvider` battery/network fields | NOT IMPLEMENTED | Phase-4 STATUS "battery/network fields deferred" | Deferred by design |
| 4 | Memory redaction/encryption | NOT IMPLEMENTED | `audit_memory_storemd_kt.md` flags HIGH RISK (no redaction/encryption); no filter call in `MemoryStore` write path | Unknown from repository evidence (no TODO found) |
| 5 | MediaStore user-visible `ClosePaw/` dir (Phase-2 plan) | NOT IMPLEMENTED (fallback shipped) | `PHASE_2_STATUS.md` "MediaStore shared-dir approach is unavailable via SDK" | Dependency/platform limitation per STATUS |
| 6 | `TermuxShellTool` `/v1/cancel` | NOT IMPLEMENTED | `termux_shell.md`: "no `/v1/cancel` (TCP disconnect kills pgid)" | By design (documented) |
| 7 | Real-device validation of Phases 1–4 | NOT DONE | All phase reports: "device run blocked/pending" | Environment limitation (no device in CI); not a code gap |
| 8 | `OAuthCodexValidator` wired into sign-in | NOT CONNECTED | `OpenAiSignIn.kt:75` explicitly skips validation; validator has no prod caller | Intentional skip per code comment; rationale not in docs — unknown |
| 9 | `protocol/` unit tests | NO TESTS | No `protocol/` test dir | Low-risk pure data; reason unknown from repository evidence |
| 10 | `storage/BackupManager` direct tests | NO TESTS | No `BackupManagerTest`; only `ChatBackupTest` (different class) | Unknown from repository evidence — P0 |
| 11 | Sustained perf benchmark | NO TESTS | No 50-turn profile in `app/src/test` or `eval/` | Unknown from repository evidence |

NOT missing (commonly misread): verification-in-loop (implemented), per-chat provider routing (never the design; global is correct), `ask_user` registration (backfilled by runner), Gemini/Anthropic native clients (OTHER+discovery is the design; only UI labels mention them).
