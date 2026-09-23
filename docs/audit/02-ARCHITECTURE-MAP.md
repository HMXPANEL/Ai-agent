# Real Architecture Map — 02 (derived from source)

All paths under `app/src/main/kotlin/ai/closepaw/`. Verified at HEAD `db9f1aa`.

## 1. Entry points

| Entry | File | Role |
|---|---|---|
| `MainActivity` | `app/MainActivity.kt:73` | ComponentActivity; hosts chat/onboarding/settings; OAuth deep link `closepaw://oauth-complete` |
| `AgentService` | `app/AgentService.kt:37` | AccessibilityService; owns `serviceScope` (Main+SupervisorJob), event fan-out to overlay/chat |
| `VirtualDisplayViewerActivity` | `ui/viewer/VirtualDisplayViewerActivity.kt:39` | Full-screen VD preview + touch forwarding |
| `ShizukuProvider` | Manifest-registered | Shizuku binder provider |

Manifest (`app/src/main/AndroidManifest.xml`): INTERNET, SYSTEM_ALERT_WINDOW, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, RUN_COMMAND, Shizuku API_V23, RECORD_AUDIO.

## 2. The real agent loop

```
ChatViewModel/MainActivity
 → SessionCoordinator.submit/createAndSubmit (session/SessionCoordinator.kt:70,117)
 → AgentSession.submit(Op) (session/AgentSession.kt:337) → startTask (:456)
 → SessionAgentRunner.start (session/SessionAgentRunner.kt:65)
    builds AgentExecutionConfig (:87), Compactor, Agent; wraps in HmxAgent
 → HmxAgent.run → Agent.run (agent/HmxAgent.kt:28 → agent/Agent.kt:59)
    loop: maybeCompact → executeTurn → delay → repeat
 → AgentTurnRunner.executeTurn (agent/AgentTurnRunner.kt:74)
    → capturePreTurnSnapshot (platform.captureScreen + Perceptor.snapshot)
    → TurnPlanningPhaseRunner.runPlanningPhase (agent/TurnPlanningPhaseRunner.kt:45)
       → AgentModelResolver.resolve → Turn → PromptBuilder.buildInputItems
       → Turn.runStreaming → LLMClient.chatWithToolsStreaming
       → TurnToolPolicy.arbitrate → PlanningPhaseOutput
    → TurnExecutionPhaseRunner.executeActions (agent/TurnExecutionPhaseRunner.kt:38)
       → ToolRouter.execute per call → Tool impl → AndroidPlatform
       → verifyTurnOutcome (:105) → OutcomeVerifier.verifyTurn
    → decideTurnOutcome (AgentTurnRunner.kt:230 + AgentRuntimeTypes.kt:103)
       → TurnOutcome Continue | Complete | Error | Cancelled
```

## 3. Subsystem map (entry / owner / interfaces / impls / instantiation / callers / state / failure / tests / docs)

### Agent
Entry `Agent.run`; owner `SessionAgentRunner`. Interfaces `HmxAgentExecutor`, `Planner`, `SubAgentRunner`. Impls `Agent`, `DefaultPlanner` (`useLegacyPath=true`), `IsolatedSubAgentRunner`, `HmxAgent`, `TaskOrchestrator`. Instantiated per task in `SessionAgentRunner.start`. State: turnCount, pauseState, stopRequested, recoverableRetry, compaction breaker (MAX_RECOVERABLE_RETRIES=1, MAX_CONSECUTIVE=3). Failure: recoverable retry ×1, compaction breaker ×3, eval budget → Error. Tests: 39 files (`AgentRunLoopTest`, `AgentErrorRecoveryTest`, …). Docs: `agent/loop.md`, `overview.md`, `planning.md`, `audit_agent_kt.md`.

### LLM
Entry `LLMClientFactory.create(modelName)` (`llm/LLMClientFactory.kt:53`). Owner `SessionLlmBootstrapper`. Interface `LLMClient` (`chatWithTools`, `chatWithToolsStreaming` → `Flow<LLMStreamEvent>`). Impls: `OpenAIResponseClient`, `ChatCompletionClient`, `CodexResponseClient`, `LFMLLMClient`. Catalog `ModelCatalog` + `ModelCatalogRepository` (seed `assets/llm_models.json`, 9 rows; fallback single `glm-5`). Auth `AuthStore` (EncryptedSharedPreferences, per-provider, generation-guarded). Per-turn resolve `AgentModelResolver.resolve` (miss → session client fallback, vision=false). Retry: non-stream `CloudLlmRetry`, stream `CloudStreamRetryRunner/Policy` (no retry after partial output; context-window fail-fast). No cross-provider fallback. OAuth `OpenAIOAuth` + `OpenAiSignIn` (PKCE, localhost:1455, 2-min); `OAuthCodexValidator` dead (no prod caller). Path: `SessionConfig.mainModel` → bootstrapper → factory → client → OkHttp/Leap → SSE parse. Tests: 22 files. Docs: `infra/llm.md`, `PROVIDER_FORENSIC_AUDIT.md`, `audit_llm_client_kt.md` (drift D-003), `audit_auth_store_kt.md`, `audit_openai_oauth_kt.md`.

### Tools
Entry `ToolRouter.execute` (`tool/ToolRouter.kt:64`): VALIDATING→POLICY→AWAITING_APPROVAL→SCHEDULED→EXECUTING→terminal; 60s approval timeout; TOCTOU re-check; snapshot re-capture post-approval. Registry `ToolRegistry` (ConcurrentHashMap; `getAvailable(manager)` capability filter). 14 canonical `ToolName`s. Registration 3-stage: `SessionToolingBootstrapper` (9 + conditional `activate_skill`), `SessionServices.create` (+`RememberExperienceTool`, +`BrowserScriptTool` unless pref-excluded), `SessionAgentRunner` (+`DelegateTaskTool`, +`AskUserTool`). Final LLM list = allowlist ∩ registry ∩ capabilities (`Turn.prepareRequest`). Policy `PolicyEngine.check` (pure; BLOCKED absolute; browser_script SMART→Ask). Capability `CapabilityManager` (7 values; only AVAILABLE usable; 3 tools declare requirements). Tests: 38 + 10 action. Docs: `infra/tools.md`, `infram/tool/mobile_action.md`, `audit_tool_router_kt.md`, `audit_policy_engine_kt.md` (drift D-004).

### Perception
Entry `Perceptor.snapshot` (`perception/Perceptor.kt:27/54`, object, single-pass). Owner `AgentTurnRunner` (pre-turn) + `TurnExecutionPhaseRunner` (post-action). Config `PerceptorFilterConfig.DEFAULT` (maxElements 500, minSize 5px, visibility 0.10, keyboard filter, clipBounds). Pipeline: multi-root collect (overlay/IME excluded) → traverse w/ pool caps 2×max → dedup → enrich → truncation (80% interactive) → spatial sort → `toPromptJson`. Passwords masked. Model sees: history + working memory + recalled memory + app skill + activated skills + one `TurnObservation` (a11y JSON; image only if vision-capable). Tests: 3 files. Docs: `infra/perception.md`, `audit_perceptor_kt.md`.

### Actions
Entry `AndroidPlatform.performAction(UIAction)` (atomic; no internal fallback). Fallbacks orchestrated in `tool/action/`: Click NODE_CLICK→GESTURE_TAP; LongPress NODE_LONG_CLICK→GESTURE_LONG_PRESS; Scroll A11Y_SCROLL→GESTURE_SWIPE; swipe gesture-only; Type DIRECT_NODE_WRITE→TAP_FOCUS_WRITE→FOCUSED_WRITE (+clear-escalation). `PointActionExecutorCore` verifies via 300/500/1000ms post-capture + `UiChangeDetector` (a11y FNV-1a; pHash fallback; `Unverifiable` distinct). `[unverified]` surfaced, not silent. Platform: `NodeActionPerformer` vs `AccessibilityGestureInjector`; VD via `VirtualDisplayInputInjector` (+shell fallback). Tests: 10 action files. Docs: `infra/tool/mobile_action.md`, `infra/tool/click_transport_experiment.md`.

### Verification
Entry `TurnExecutionPhaseRunner.verifyTurnOutcome` (`:105`) → `OutcomeVerifier.verifyTurn` (`agent/OutcomeVerifier.kt:98`) = `TaskVerification.assess` (actuated iff mobile_action|browser_script; NOT_REQUIRED / REQUIRED_BUT_UNVERIFIED) + structured criteria (foreground-package, field-text, sent-message, recipient). VERIFY lines → history (model sees next turn) + `RuntimeEventBus` events. Gate `decideTurnOutcome`: unverified `complete_task(success)` → recoverable Error (same-turn + cross-turn via `TaskVerificationTracker`; only fresh VERIFIED clears). `CompleteTaskTool` itself inert; enforcement in the two gates. Tests: `OutcomeVerifierTest`, `TaskVerificationGateTest`, `TaskVerificationTrackerTest`, `StructuredTaskParserTest`, `TextVerification` tests. Docs: phase runners audits; HMX plans (Phase 6 motivation).

### Session
Entry `SessionCoordinator.submit` → `AgentSession.submit(Op)`. States Created→Running→TakeoverPending→Paused→Running→Idle→…→Shutdown. Hot Idle keeps platform/VD, releases runner; 5-min idle timeout; reacquire-fail ×3 → shutdown; checkpoint `IDLE_READY/CLOSED` schema v2 only; flush failures non-fatal. Takeover cooperative (Running only); Resume rejected in TakeoverPending. Tests: 17 files. Docs: `infra/session.md` (stale roles para), `protocol/overview.md`, state-machine docs.

### Memory
`MemoryStore` (`filesDir/memory/`: user.md/device.md/apps/<pkg>.md; 8KB/file, 2000 chars/entry; atomic temp+rename; `SaveResult` typed) + `MemoryRecaller.recall` (USER+DEVICE+APP → `## Recalled Memory` block) + `MemorySchema` scopes + `RememberExperienceTool` + `MemoryEditGate` (locks UI edits during session). Tests: 2 files. Docs: `agent/memory.md`, `audit_memory_storemd_kt.md`.

### Platform (Android)
Interface `AndroidPlatform`. `AccessibilityPlatform` (real screen; BLOCKED→empty snapshot; tree 3 attempts + conditional screenshot). `VirtualDisplayPlatform` (Shizuku display `closepaw_agent_display`; arbiter start/stop with drain; ImageReader↔live-preview; PixelCopy 3s + ImageReader fallback; shell input fallback; root-task cleanup on stop). `PlatformFactory` (VD null→A11Y fallback + warning). Tests: 5 platform + 7 VD. Docs: `infra/platform.md`, `infra/virtual_display.md`, `audit_virtualdisplay_platform_kt.md`, `audit_shizuku_vd_kt.md`.

### Browser
`BrowserScriptTool` → `BrowserSessionManager` (lease, preflight, run, markBroken rebuild, artifact cap) → `ShizukuChromeDevtoolsBridge` or wireless-ADB self-pair transport → `ChromeCdpClient` → OkHttp WS + per-session `RelayAuthToken` → `BrowserScriptRunner` (JS in hidden WebView; `page/input/tabs.js` from browser-use skill). Both transports require Shizuku. Tests: 29 files. Docs: `infra/browser.md`, `audit_browser_script_kt.md` (drift D-002).

### Termux
`TermuxBridgeManager` (probe→apt→deploy→start→health poll 127.0.0.1:18422) → `TermuxRunCommandAdapter` (RUN_COMMAND + PendingIntent) → `closepaw_bridge.py` (`/v1/health`, `/v1/exec`; workspace jail; 1MB cap; 120s timeout; 64KB output + spill; single-exec 409; pgid kill). Snapshot immutable at session creation; frozen across Hot Idle. Needs F-Droid Termux + Allow-external-apps. No `/v1/cancel`. Tests: 3 files. Docs: `app/termux_shell.md`, `audit_termux_bridge_manager_kt.md` (drift D-006).

### Shizuku
Required: VD (display/input/activity transports) + browser CDP. Optional: everything else. `ShizukuRuntimeGateway` ping/permission; UID-change re-consent; binder death → VD Broken + proxy clear. VD-without-Shizuku → A11Y fallback + warning. Tests: under `browser/cdp/shizuku/` (4) + `platform/virtualdisplay/Shizuku*` (2). Docs: `audit_shizuku_vd_kt.md`.

### Persistence / history / backup
`HistoryManager` (revision+CAS) + `Compactor` (threshold, safe cut, initial/update prompts) + `SessionRecordingService` (debounced 500ms, immediate on complete) + `SessionStorage` (`files/sessions/*.json`) + `SessionHistoryManager` + `ChatPersistenceManager` + `ChatBackup` (verify/reconcile) + `ClosePawStorage` + `BackupManager` (gzip + SHA-256 + sidecar meta; daily auto-backup; `restoreIfEmpty`; credentials excluded by design). Gap: `BackupManager` zero direct tests. Docs: `app/history/*`, `PHASE_2_*`.

### UI
`MainActivity` (chat/onboarding/settings) + `AgentService` overlay host + `VirtualDisplayViewerActivity`. Chat (`ChatScreen/ViewModel/Reducer/HistoryController`), capsule (`CapsuleStateHolder` 9 modes + `SmartCapsuleSurface` + voice), overlay (`Capsule/Island/Glow/Visualizer` hosts), settings (models/auth/behavior/skills/access/memory/backup/permissions), onboarding (6 steps, v2 prefs). Tests: 34 unit + 27 instrumented (qa 19, settings 4, browser 3, termux 1). Docs: `ui/*`, `state_machines/ui_*`.

### Cross-cutting
Trace/diagnostics: `AgentTrace`, `FileTraceRecorder`, `RuntimeEventBus` (64), `LogBuffer` (50), `CrashHandler`, `SensitiveDataFilter` (logger/store/overlay). Capability: `CapabilityManager` + `AndroidDeviceCapabilitySource`. DeviceState (Phase 4): `HmxDeviceState` + provider (2s cache, per-field degrade). HMX Phase 2: `HmxAgent/Planner/DefaultPlanner/TaskOrchestrator`.
