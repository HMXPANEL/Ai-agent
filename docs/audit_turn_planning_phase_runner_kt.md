FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/agent/TurnPlanningPhaseRunner.kt
PACKAGE: ai.closepaw.agent

MAIN CLASSES:
- TurnPlanningPhaseRunner - Runs the planning phase of an agent turn (LLM call, tool selection)

MAIN FUNCTIONS:
- runPlanningPhase() - complete planning phase: model resolution, prompt building, LLM call, tool arbitration

DATA FIELDS:
- config: AgentExecutionConfig
- services: SessionServices
- eventDispatcher: AgentEventDispatcher
- trace: AgentTrace
- turnPolicyEngine: TurnToolPolicy
- compactor: Compactor? (optional)
- modelResolver: AgentModelResolver (lazy)

RESPONSIBILITY:
- Builds the complete LLM prompt for one turn
- Components: system prompt + app skill + recalled memory + activated skills + observation + history
- Calls LLM via Turn.runStreaming() or Turn.run()
- Performs tool call arbitration via TurnToolPolicy
- Handles context window exceeded via reactive compaction
- Emits events for thought updates, approval requirements

STATE OWNED:
- None directly; works with transient turn state

DEPENDENCIES (injected via constructor):
- config: AgentExecutionConfig
- services: SessionServices (provides toolRegistry, llmClient, modelCatalog, llmClientFactory, agentSkillManager, memoryRecaller, sessionState, appSkillRepository, policyEngine, appClassifier, platform, config, traceRecorder, recordingService, browserSessionManager, termuxSnapshot, userResponseChannel, memoryStore, memoryRecaller)
- eventDispatcher: AgentEventDispatcher
- trace: AgentTrace
- turnPolicyEngine: TurnToolPolicy
- compactor: Compactor? (optional)

NOTABLE DEPENDENCIES FROM services:
- services.llmClient / services.modelCatalog / services.llmClientFactory
- services.agentSkillManager (activateExplicitMentions, catalogPrompt)
- services.memoryRecaller.recall()
- services.appSkillRepository.load()
- services.historyManager
- services.sessionState (todos, scratchpad)
- services.config.perceptionConfig
- services.traceRecorder

DEPENDENTS:
- AgentTurnRunner.planningPhaseRunner delegates to this class
- AgentTurnRunner.executeTurn() calls runPlanningPhase()
- AgentSession downstream via Agent and AgentTurnRunner
- PromptBuilder builds the actual input items (collaborator)

THREAD/COROUTINE:
- runPlanningPhase() is a suspend function
- Uses modelResolver.resolve() which may involve model downloading
- Collects LLM streaming events within coroutine scope
- Uses delay() for post-action settle in execution phase (not planning)
- Runs on whatever coroutine context is active

INPUTS:
- turnId: String
- turnNumber: Int
- snapshot: ScreenSnapshot - current screen state
- currentPackageName: String? - foreground package
- warnings: List<String> - loop warnings, etc.

OUTPUTS:
- PlanningPhaseOutput containing:
  - turnResult: TurnResult (LLM response: text + tool calls + isComplete)
  - arbitration: ToolArbitrationResult (selected tool calls, hasCompletionTool, hasScreenAction, droppedToolCalls)

SIDE EFFECTS:
- Adds screen observation to history via ResponseItem.Message(SCREEN_OBSERVATION)
- Records LLM request trace via trace.llmRequest()
- Records LLM response trace via trace.llmResponse()
- Emits turn phase changed events (PLANNING)
- Emits status updates ("🧠 Thinking...")
- Emits message deltas via eventDispatcher.messageDelta()
- Emits tool call received events via eventDispatcher.toolCall()
- Emits thoughtUpdate events via eventDispatcher.thoughtUpdate()
- Emits arbitration warnings
- May emit approvalRequired events

PERSISTENCE:
- History items added via services.historyManager
- Trace recordings

NETWORK:
- LLM API calls via Turn.runStreaming() → LLM client
- Model resolution via services.modelCatalog and services.llmClientFactory
- May involve network for model download (local LFM)

ANDROID API:
- Screen snapshot via Perceptor.snapshot() or direct Accessibility tree
- Perception config via services.config.perceptionConfig

ERROR HANDLING:
- ContextWindowExceededException caught in AgentTurnRunner.executeTurn()
- Reactive compaction attempted via cap.forceCompactNow()
- If compaction fails or not wired, exception propagated
- Stream errors thrown as RuntimeException

SECURITY:
- Prompt content may contain sensitive screen information
- App skills loaded from asset directory - sandboxed
- Memory recall from persistent store - sanitized
- Tool call arbitration done by policy engine (already vetted)

TEST COVERAGE:
- TurnPlanningPhaseRunnerTest.kt
- AppSkillAssetIntegrityTest.kt
- AssetAppSkillRepositoryTest.kt
- ModelCatalogTest.kt
- ModelDiscoveryTest.kt

STATUS: CONFIRMED IMPLEMENTED
- Complete planning phase implementation
- Robust prompt construction with multiple sections (history, memory, skills, observation)
- Handles context window overflow via reactive compaction
- Proper tool arbitration and dropoff handling
- Well-integrated with all session services