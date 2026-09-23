# Document Registry — 01

**Scope:** all 88 markdown files: `docs/` (26) + `doc/` (62) + roots (README, SECURITY, PRIVACY_POLICY, AGENTS, CLAUDE). Each row: purpose, type, status, related code.

Status legend: COMPLETE (accurate as historical/spec record) · NEED_UPDATE (known drift) · CONTRADICTORY (conflicts with another doc) · OBSOLETE (superseded) · EXPERIMENTAL (unproven path).

## docs/ (26)

| File | Purpose | Type | Status | Related code |
|---|---|---|---|---|
| `docs/BASELINE.md` | Phase-0 read-only verification baseline; 6 discrepancies D-001–D-006 | Audit | COMPLETE (normative corrections record) | All `audit_*.md` + source at 2026-09-03 |
| `docs/HMX_MASTER_PLAN.md` | 21-phase evolution plan, gates, prompts | Planning | COMPLETE (plan record) | `SessionServices`, `ToolRouter`, `MemoryStore` |
| `docs/HMX_UNIVERSAL_ARCHITECTURE_PLAN.md` | Read-only audit + 6-seam hardening plan P0–P5 | Architecture | COMPLETE | Choke points: `SessionLlmBootstrapper`, `ToolRouter`, `decideTurnOutcome`, `AgentSession.reload` |
| `docs/PHASE_1_REPORT.md` | Diagnostics foundation validation | Implementation | COMPLETE | `LogBuffer`, `SensitiveDataFilter`, `RuntimeLogger`, `RuntimeEventBus`, `CrashHandler` |
| `docs/PHASE_2_REPORT.md` | Unified agent core (HmxAgent/Planner/Orchestrator) | Implementation | COMPLETE | `HmxAgent.kt`, `Planner.kt`, `TaskOrchestrator.kt` |
| `docs/PHASE_2_STATUS.md` | Phase-2 repair status, CI green, storage migration | Operations | COMPLETE (note: MediaStore limitation supersedes impl-audit) | `ClosePawStorage`, `BackupManager` |
| `docs/PHASE_2_IMPLEMENTATION_AUDIT.md` | Pre-impl persistence gap audit | Audit | COMPLETE (plan-time; storage decision superseded by STATUS) | `MemoryStore`, `SessionStorage`, checkpoints |
| `docs/PHASE_3_REPORT.md` | Capability Manager implementation | Implementation | COMPLETE | `Capability.kt`, `CapabilityManager.kt` |
| `docs/PHASE_3_STATUS.md` | Capability-gated selection accepted, 3 tools wired | Operations | COMPLETE (current for capability scope) | `MobileActionTool`, `SystemButtonTool`, `TermuxShellTool` |
| `docs/PHASE_4_STATUS.md` | DeviceState provider + wiring | Operations | COMPLETE (CI pending noted) | `device/HmxDeviceState*` |
| `docs/PROVIDER_FORENSIC_AUDIT.md` | Full LLM routing forensic audit; model-led verdict | Audit | COMPLETE (normative for routing) | `SessionLlmBootstrapper`, `LLMClientFactory`, `ModelCatalog` |
| `docs/audit_agent_kt.md` | `agent/Agent.kt` audit | Audit | COMPLETE | `agent/Agent.kt` |
| `docs/audit_agent_turn_runner_kt.md` | `AgentTurnRunner.kt` audit | Audit | COMPLETE | `agent/AgentTurnRunner.kt` |
| `docs/audit_auth_store_kt.md` | `AuthStore.kt` audit | Audit | COMPLETE | `auth/AuthStore.kt` |
| `docs/audit_browser_script_kt.md` | Browser scripting audit | Audit | NEED_UPDATE (path drift; see BASELINE D-002: actual `tool/impl/BrowserScriptTool.kt`) | `tool/impl/BrowserScriptTool.kt`, `browser/script/*` |
| `docs/audit_llm_client_kt.md` | `LLMClient.kt` audit | Audit | NEED_UPDATE ("two implementations" claim; see BASELINE D-003: 4 clients exist) | `llm/OpenAIResponseClient`, `ChatCompletionClient`, `CodexResponseClient`, `LFMLLMClient` |
| `docs/audit_memory_storemd_kt.md` | `MemoryStore.kt` audit | Audit | COMPLETE | `memory/MemoryStore.kt` |
| `docs/audit_openai_oauth_kt.md` | `OpenAIOAuth.kt` audit | Audit | COMPLETE | `auth/OpenAIOAuth.kt` |
| `docs/audit_perceptor_kt.md` | `Perceptor.kt` audit | Audit | COMPLETE | `perception/Perceptor.kt` |
| `docs/audit_policy_engine_kt.md` | `PolicyEngine.kt` audit | Audit | NEED_UPDATE (browser_script AUTO_APPROVE claim; see BASELINE D-004: code returns Allow) | `tool/PolicyEngine.kt` |
| `docs/audit_shizuku_vd_kt.md` | Shizuku/VD audit | Audit | COMPLETE (with BASELINE downgrade to INFERRED for binder health) | `platform/virtualdisplay/*` |
| `docs/audit_termux_bridge_manager_kt.md` | `TermuxBridgeManager.kt` audit | Audit | NEED_UPDATE (`snapshot(enabled)` suspend claim; see BASELINE D-006: synchronous) | `termux/TermuxBridgeManager.kt` |
| `docs/audit_tool_router_kt.md` | `ToolRouter.kt` audit | Audit | COMPLETE | `tool/ToolRouter.kt` |
| `docs/audit_turn_execution_phase_runner_kt.md` | Execution runner audit | Audit | COMPLETE | `agent/TurnExecutionPhaseRunner.kt` |
| `docs/audit_turn_planning_phase_runner_kt.md` | Planning runner audit | Audit | COMPLETE | `agent/TurnPlanningPhaseRunner.kt` |
| `docs/audit_virtualdisplay_platform_kt.md` | VD platform audit | Audit | COMPLETE (INFERRED lifecycle per BASELINE) | `platform/virtualdisplay/VirtualDisplayPlatform.kt` |

## doc/main/ (53)

| File | Purpose | Type | Status | Related code |
|---|---|---|---|---|
| `doc/main/README.md` | Navigation hub + code tree | Reference | NEED_UPDATE (SessionState 5 vs 6 states) | All packages |
| `doc/main/data_schemas.md` | 5 schema redundancies catalog | Design | CONTRADICTORY (ApiKey states 10 vs 11 elsewhere) | `AppSettingsStore`, `AgentSession`, `WriteTodosTool` |
| `doc/main/error_handling.md` | Failure-pattern catalog | Design | COMPLETE | `SessionCheckpointCoordinator`, `AgentService`, `SessionServices` |
| `doc/main/agent/overview.md` | Agent design principles + wiring | Architecture | COMPLETE | `agent/*`, `cognition/*`, `definition/*` |
| `doc/main/agent/loop.md` | ReAct turn loop, compaction, budgets | Specification | COMPLETE | `Agent.kt`, `Turn.kt`, `TurnToolPolicy`, `LoopDetectionPolicy` |
| `doc/main/agent/planning.md` | Todos/scratchpad/history planning | Specification | CONTRADICTORY (`recentFullScreens` 3 vs `runtime.md` 2) | `TodoState`, `ScratchpadState`, `HistoryManager` |
| `doc/main/agent/memory.md` | Two-layer memory spec | Specification | COMPLETE | `MemoryStore`, `MemoryRecaller`, `MemorySchema` |
| `doc/main/agent/multiagent.md` | Unified subagent model | Architecture | COMPLETE | `SubAgentRunner`, `DelegateTaskTool` |
| `doc/main/agent/turn_prompt_anatomy.md` | Per-turn prompt assembly order | Specification | CONTRADICTORY (`recentFullScreens` 3 vs 2) | `PromptBuilder`, `TurnPlanningPhaseRunner` |
| `doc/main/agent/agent_skills.md` | App vs Agent skill kinds | Specification | COMPLETE | `AgentSkillManager`, `ActivateSkillTool` |
| `doc/main/app/settings.md` | Settings + AuthStore spec | Specification | COMPLETE | `AppSettingsStore`, `AuthStore`, settings UI |
| `doc/main/app/termux_shell.md` | Termux bridge full spec | Specification | COMPLETE | `TermuxBridgeManager`, `closepaw_bridge.py` |
| `doc/main/app/history/overview.md` | 3-layer history overview | Architecture | COMPLETE | `HistoryManager`, `SessionRecordingService` |
| `doc/main/app/history/models.md` | Record model catalog | Reference | COMPLETE | `history/model/*` |
| `doc/main/app/history/persistence.md` | Disk persistence paths | Implementation | COMPLETE | `SessionHistoryManager`, `SessionStorage` |
| `doc/main/app/history/runtime.md` | Runtime history + compaction | Specification | CONTRADICTORY (`recentFullScreens` 2 vs 3) | `HistoryManager`, `Compactor` |
| `doc/main/eval/eval.md` | AndroidWorld eval bridge | Testing | COMPLETE | `eval/*.py` |
| `doc/main/infra/browser.md` | CDP automation spec | Specification | COMPLETE | `browser/cdp/*`, `browser/script/*` |
| `doc/main/infra/llm.md` | LLM client/provider spec | Specification | COMPLETE | `llm/*` |
| `doc/main/infra/perception.md` | Perception ownership + masking | Specification | COMPLETE | `Perceptor`, `TargetResolver` |
| `doc/main/infra/platform.md` | Platform abstraction spec | Specification | COMPLETE | `AccessibilityPlatform`, `VirtualDisplayPlatform` |
| `doc/main/infra/session.md` | Session lifecycle spec | Specification | NEED_UPDATE (stale Standalone/Planner/Executor roles para) | `AgentSession`, `SessionCoordinator` |
| `doc/main/infra/tools.md` | Tool lifecycle + policy matrix | Specification | COMPLETE | `ToolRouter`, `PolicyEngine`, `AppClassifier` |
| `doc/main/infra/virtual_display.md` | VD orchestrator spec | Specification | COMPLETE | `platform/virtualdisplay/*` |
| `doc/main/infra/tool/mobile_action.md` | Screen-tool contract + verify timing | Specification | COMPLETE | `MobileActionTool`, `*Executor` |
| `doc/main/infra/tool/click_transport_experiment.md` | 2026-03-06 click transport experiment | Experimental | COMPLETE (experiment record) | `ActionPriorityOrder` |
| `doc/main/protocol/overview.md` | Op/Event protocol overview | Specification | COMPLETE | `protocol/Op.kt`, `AgentEvent.kt` |
| `doc/main/protocol/events.md` | Event catalog | Reference | COMPLETE | `protocol/*Events.kt` |
| `doc/main/protocol/config.md` | SessionConfig reference | Reference | COMPLETE | `protocol/SessionConfig.kt` |
| `doc/main/state_machines/README.md` | FSM index | Reference | COMPLETE | All state machines |
| `doc/main/state_machines/agent_run_loop.md` | Agent loop FSM | Specification | COMPLETE | `Agent.kt` |
| `doc/main/state_machines/tool_call.md` | ToolCallState FSM | Specification | COMPLETE | `ToolRouter.kt` |
| `doc/main/state_machines/llm_retry.md` | Retry policy FSM | Specification | COMPLETE | `CloudStreamRetryPolicy`, `CloudLlmRetry` |
| `doc/main/state_machines/local_model_loading.md` | Leap loading FSM | Specification | COMPLETE | `LFMLLMClient` |
| `doc/main/state_machines/session_state.md` | SessionState FSM | Specification | COMPLETE | `AgentSession.kt` |
| `doc/main/state_machines/session_coordinator.md` | Coordinator FSM | Specification | COMPLETE | `SessionCoordinator.kt` |
| `doc/main/state_machines/onboarding_wizard.md` | Wizard FSM | Specification | COMPLETE | `onboarding/*` |
| `doc/main/state_machines/onboarding_step_states.md` | 3-hierarchy rationale | ADR | CONTRADICTORY (23 vs 24 count drift) | `OnboardingStepState` hierarchies |
| `doc/main/state_machines/onboarding_apikey_step.md` | ApiKey step FSM | Specification | CONTRADICTORY (11 states vs `data_schemas` 10) | `OnboardingSteps` |
| `doc/main/state_machines/onboarding_demo_step.md` | Demo step FSM | Specification | COMPLETE | `OnboardingDemoController` |
| `doc/main/state_machines/onboarding_permission_step.md` | Permission step FSM | Specification | COMPLETE | `PermissionStateMonitor` |
| `doc/main/ui/capsule/architecture.md` | Capsule architecture | Architecture | COMPLETE | `CapsuleStateHolder`, `SmartCapsuleSurface` |
| `doc/main/ui/capsule/state_machine.md` | Capsule FSM (9 states) | Specification | CONTRADICTORY (9 vs `overlay.md` 8 modes) | `CapsuleMode.kt` |
| `doc/main/ui/capsule/user_flows.md` | Capsule flows | Specification | NEED_UPDATE (`onMainAppVisible` scope vs `overlay.md`) | `CapsuleBinding` |
| `doc/main/ui/capsule/voice.md` | Voice input spec | Specification | COMPLETE | `capsule/voice/*` |
| `doc/main/ui/overlay.md` | Overlay hosts + glow + visualizer | Specification | CONTRADICTORY (8 modes vs capsule 9) | `overlay/compose/*` |
| `doc/main/ui/user_interaction.md` | Chat-first interaction | Design | COMPLETE | `chat/*`, `capsule/*` |
| `doc/main/ui/tech_design.md` | Compose stack + file map | Architecture | COMPLETE | `ui/*` |
| `doc/main/ui/style.md` | D1 visual baseline | Design | COMPLETE | `ui/theme/*` |
| `doc/main/ui/session/state_machine.md` | Session↔Task formal FSM | Specification | COMPLETE | `AgentSession.kt` |
| `doc/main/ui/session/user_flows.md` | Session user flows | Specification | COMPLETE | `ChatViewModel`, `SessionHistoryManager` |

## doc/dev + doc/release + misc (9 + roots)

| File | Purpose | Type | Status | Related code |
|---|---|---|---|---|
| `doc/dev/development.md` | Debug vs Release, commands | Operations | COMPLETE | Gradle build types |
| `doc/dev/visual_debug_guide.md` | Visual debug workflow | Operations | COMPLETE | `scripts/debug-run.sh` |
| `doc/dev/scroll_visualizer_plan_codex.md` + `..._review_codex.md` | Scroll visualizer plan/review | Planning | COMPLETE (historical) | `ScrollVisualizationGeometry` |
| `doc/release/signing.md` | Release signing | Operations | COMPLETE | `signingConfigs.release` |
| `doc/release/play-store/README.md` + `a11y-declaration.md` + `data-safety-answers.md` | Play listing + declarations | Operations | COMPLETE | Manifest, privacy policy |
| `doc/release/privacy/index.md` | Privacy policy mirror | Security | COMPLETE | `AuthStore`, backup rules |
| `README.md` (root) | Product overview | Reference | COMPLETE (minor shell-claim nuance) | All |
| `SECURITY.md` (root) | Disclosure policy | Security | COMPLETE | — |
| `PRIVACY_POLICY.md` (root) | Privacy policy | Security | COMPLETE | `AuthStore`, `AgentService` |
| `AGENTS.md` / `CLAUDE.md` / `GEMINI.md` | Contributor pointers | Reference | COMPLETE | `scripts/*`, `eval/*` |
