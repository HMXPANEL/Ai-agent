FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/agent/AgentTurnRunner.kt
PACKAGE: ai.closepaw.agent

MAIN CLASSES:
- AgentTurnRunner - Executes one full agent turn and returns the next-loop decision

INTERNAL DATA CLASSES:
- PreTurnContext - snapshot, currentPackageName, securityWarnings
- PreparedTurn - nextState, warnings

MAIN FUNCTIONS:
- executeTurn() - runs one complete agent turn (perception → planning → action → observation)
- capturePreTurnSnapshot() - captures screen and current package
- prepareTurn() - advances navigation state, detects loops, builds warnings
- decideTurnOutcome() - determines turn outcome (Continue, Complete, Error, Cancelled)
- handleTurnFailure() - classifies and wraps turn errors

DATA FIELDS:
- config: AgentExecutionConfig
- services: SessionServices
- eventDispatcher: AgentEventDispatcher
- cancellationSignal: CompletableDeferred<AgentStopReason>
- stopRequested: AtomicBoolean
- trace: AgentTrace
- turnPolicyEngine: TurnToolPolicy
- compactor: Compactor? (optional)
- loopDetectionPolicy: LoopDetectionPolicy (lazy)
- planningPhaseRunner: TurnPlanningPhaseRunner (lazy)
- executionPhaseRunner: TurnExecutionPhaseRunner (lazy)

RESPONSIBILITY:
- Executes one complete ReAct iteration per call
- Pipeline: 1) Perception (capture screen), 2) Thinking (planning phase → LLM), 3) Action (execute tools), 4) Observation (collect results)
- Manages turn state transitions and outcome determination
- Handles cancellation and error recovery

STATE OWNED:
- nextState: TurnRunnerState (passed in/out)
- navigationState via state.navigationState

DEPENDENCIES (injected via constructor):
- config: AgentExecutionConfig
- services: SessionServices (provides platform, history, LLM, policy, etc.)
- eventDispatcher: AgentEventDispatcher
- cancellationSignal: CompletableDeferred<AgentStopReason>
- stopRequested: AtomicBoolean
- trace: AgentTrace
- turnPolicyEngine: TurnToolPolicy
- compactor: Compactor? (optional for production, used in eval)

DEPENDENTS:
- Agent.kt creates AgentTurnRunner and calls executeTurn()
- AgentSession uses AgentTurnRunner indirectly through Agent
- TurnPlanningPhaseRunner.runPlanningPhase() is called from executeTurn()
- TurnExecutionPhaseRunner.executeActions() is called from executeTurn()
- AgentTurnRunner.capturePreTurnSnapshot() calls services.platform.captureScreen()

THREAD/COROUTINE:
- executeTurn() is a suspend function
- Uses try/catch/finally with CancellationException handling
- delay() for UI settle delays in Agent.run() (not in executeTurn itself)
- All operations on whichever coroutine context is active
- withContext(Dispatchers.Main) in AccessibilityPlatform for screen capture

INPUTS:
- turnId: String - unique turn identifier
- turnNumber: Int - 1-based turn counter
- state: TurnRunnerState - cross-turn state including navigationState

OUTPUTS:
- TurnExecutionResult with outcome: TurnOutcome and nextState: TurnRunnerState
- Emitted events via eventDispatcher (turnStarted, turnPhaseChanged, turnCompleted)
- Trace records via trace

SIDE EFFECTS:
- Captures screen via AccessibilityPlatform.captureScreen()
- Builds LLM prompt via TurnPlanningPhaseRunner
- Executes tool calls via TurnExecutionPhaseRunner
- Records history items
- Emits turn completion events
- May emit loop detection warnings

PERSISTENCE:
- History via services.historyManager
- Trace via trace
- Screen observations recorded

NETWORK:
- Indirect: LLM calls occur within TurnPlanningPhaseRunner → Turn( runStreaming )
- LLM HTTP calls are handled by LLMClient (OpenAIResponseClient, etc.)

ANDROID API:
- AccessibilityService.captureScreen() via services.platform
- Package name retrieval via services.platform.getCurrentPackageName()
- Screen snapshot model classes

ERROR HANDLING:
- Catches CancellationException (rethrows, don't treat as turn error)
- Catches generic Exception → handleTurnFailure() → TurnOutcome.Error
- Loop detection warnings are advisory (LLM decides)
- Finally block always emits turnCompleted and trace.turnCompleted

SECURITY:
- No direct security concerns; delegates to policy engine
- Screen capture respects BLOCKED app masking via appClassifier
- Tool execution routed through PolicyEngine checks

TEST COVERAGE:
- TurnExecutionPhaseRunnerTest.kt
- TurnPlanningPhaseRunnerTest.kt
- TurnErrorClassifierTest.kt
- TurnOutcomeDecisionTest.kt
- TurnReactiveCompactionTest.kt
- TurnToolFilteringTest.kt

STATUS: CONFIRMED IMPLEMENTED
- Core turn execution engine, fully functional
- Clear separation of planning and execution phases
- Proper error handling and cancellation support
- Well-integrated with all supporting subsystems
- Test coverage exists for all major code paths