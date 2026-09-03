FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/agent/Agent.kt
PACKAGE: ai.closepaw.agent

MAIN CLASSES:
- Agent - Public entry point for running a single ReAct agent session

MAIN FUNCTIONS:
- run() - suspend function that executes the main agent loop
- pause() - requests agent pause, returns Deferred that completes when paused
- resume() - resumes a paused agent
- stop() - requests agent stop
- shouldContinue() - checks if agent should continue running

DATA FIELDS (Agent class):
- turnCount: Int - tracks turn number
- pauseState: MutableStateFlow<Boolean> - pause control
- pauseConfirmed: CompletableDeferred<Unit>? - pause confirmation
- stopRequested: AtomicBoolean - stop flag
- lifecycleMutex: Mutex - lifecycle mutex
- eventDispatcher: AgentEventDispatcher - event emission
- trace: AgentTrace - trace recording
- turnRunner: AgentTurnRunner - turn execution

RESPONSIBILITY:
- Orchestrates the complete agent session lifecycle
- Manages turn loop, pauses, retries, and completion
- Handles auto-compaction of history
- Manages eval turn budget safety net
- Auto-retains app memory on task failure
- Coordinates between compactor, policy, and turn execution

STATE OWNED:
- turnCount, pauseState, pauseConfirmed, stopRequested, lastKnownPackage
- turnRunner state via TurnRunnerState

DEPENDENCIES (injected via constructor):
- config: AgentExecutionConfig
- services: SessionServices
- compactor: Compactor
- eventEmitter: suspend (AgentEvent) -> Unit
- cancellationSignal: CompletableDeferred<AgentStopReason>

DEPENDENTS:
- AgentSession.submit() calls Agent.run()
- AgentSession.handleAgentComplete() processes AgentStopReason
- MainActivity.handleIntent() can trigger goal submission
- SessionCoordinator orchestrates session lifecycle

THREAD/COROUTINE:
- run() is a suspend function executed within coroutine scope
- Uses delay() for UI settle delays
- Uses AtomicBoolean for thread-safe stop/pause flags
- Uses Mutex for lifecycle mutex protection
- Event dispatcher emits on whatever coroutine context is active

INPUTS:
- config.goal: String - the user's task/goals
- config.sessionId: SessionId - session identifier
- turn events from eventDispatcher
- compactor outcomes
- LLM responses via turnRunner

OUTPUTS:
- AgentStopReason - final result (GoalAchieved, TaskImpossible, Error, UserRequested)
- Emitted events via eventDispatcher
- Trace records via trace

SIDE EFFECTS:
- Logs agent lifecycle events
- Emits AgentEvent flow events
- Records trace entries
- Auto-compacts history
- Appends operational notes to memory store
- Handles pause/resume lifecycle

PERSISTENCE:
- Memory operations via services.memoryStore
- History management via services.historyManager
- Trace recording via trace

NETWORK:
- None directly; network occurs through LLM client within turn execution

ANDROID API:
- AccessibilityService interaction via SessionServices.platform
- UI events via eventDispatcher
- Toast messages for user feedback

ERROR HANDLING:
- try/finally block ensures pauseConfirmed completion
- Handles CancellationException specially (rethrows)
- General Exception handler calls handleTurnFailure()
- MAX_RECOVERABLE_RETRIES = 1 for recoverable errors
- MAX_CONSECUTIVE_COMPACTION_FAILURES = 3 circuit breaker

SECURITY:
- No direct security handling; delegates to policy engine and platform
- Pause/resume state protected by Mutex
- stopRequested AtomicBoolean for cancellation

TEST COVERAGE:
- Not directly tested in isolation; tested through AgentSession and Turn execution tests
- Test files: AgentRunLoopTest, AgentErrorRecoveryTest, AgentTraceObservabilityTest

STATUS: CONFIRMED IMPLEMENTED
- Core agent orchestration class, fully implemented and wired into SessionServices
- Well-integrated with turn execution, compaction, and policy systems
- State management is correct and follows the established patterns