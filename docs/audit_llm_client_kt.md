FILE: /mnt/sdcard/AIProjects/closepaw-main/app/src/main/kotlin/ai/closepaw/llm/LLMClient.kt
PACKAGE: ai.closepaw.llm

MAIN CLASSES:
- LLMClient - Abstract base class for LLM clients (cloud and local)

SEALED CLASSES / DATA CLASSES:
- LLMStreamEvent - streaming events (Created, TextDelta, ToolCallDone, Completed, Failed)
- ResponsesResult - non-streaming result (textContent, toolCalls, responseId)
- LLMToolCall - individual LLM tool call (callId, name, arguments)
- RateLimitException - rate limited API
- TransientException - transient errors for retry

COMpanion OBJECT CONSTANTS:
- TAG = "LLMClient"
- DEFAULT_MODEL = "glm-5" (note: seems like a LiquidAI model, not OpenAI)
- MAX_RETRIES = 5
- INITIAL_BACKOFF_MS = 1000L
- MAX_BACKOFF_MS = 60000L
- BACKOFF_MULTIPLIER = 2.0

ABSTRACT METHODS:
- chatWithTools() - non-streaming LLM call with tool/function calling
- chatWithToolsStreaming() - streaming LLM call returning Flow<LLMStreamEvent>
- isReady() - check if client ready (defaults to true)
- cleanup() - cleanup resources

RESPONSE CLASSES:
- ResponsesResult(textContent, toolCalls, responseId)
- LLMToolCall(callId, name, arguments)
- LLMStreamEvent sealed interface with: Created, TextDelta, ToolCallDone, Completed, Failed

RESPONSIBILITY:
- Abstract base defining the LLM client interface
- Uses OpenAI Responses API types (FunctionTool, ResponseInputItem) as input
- Minimizes caller changes by reusing OpenAI types
- Two concrete implementations: OpenAIResponseClient, LFMLLMClient
- Streaming via Flow<LLMStreamEvent> with custom events (OpenAI ResponseStreamEvent can't be constructed outside SDK)

STATE OWNERSHIP:
- None in base class; implementations manage their own state

DEPENDENCIES:
- com.openai.models.responses.FunctionTool
- com.openai.models.responses.ResponseInputItem
- kotlinx.coroutines.flow.Flow
- org.json.JSONArray, JSONObject

NOTABLE DEPENDENCIES:
- DEFAULT_MODEL = "glm-5" unusual (LiquidAI model, not OpenAI default)
- Rate limit configuration shared across all providers (MAX_RETRIES=5, exponential backoff)
- Streaming abstraction because OpenAI ResponseStreamEvent can't be constructed outside SDK
- LLMToolCall data class for tool call correlation

THREAD/COROUTINE:
- chatWithTools() is suspend fun
- chatWithToolsStreaming() returns Flow<LLMStreamEvent>
- Rate limit backoff calculations are pure math
- No explicit dispatcher usage in base class; implementations choose

INPUTS:
- systemPrompt: String - system/developer instructions
- inputItems: List<ResponseInputItem> - conversation history
- tools: List<FunctionTool> - tool definitions for function calling
- model: String = "glm-5" (override default)
- maxOutputTokens: Long? - optional output cap

OUTPUTS:
- ResponsesResult with textContent, toolCalls, responseId

SIDE EFFECTS:
- Network I/O (in implementations)
- Stream processing (in streaming variant)
- Error creation (RateLimitException, TransientException)

PERSISTENCE:
- None in base class

NETWORK:
- All LLM communication happens in implementations
- Cloud: OpenAI Responses API, Chat Completions
- Local: Leap SDK inference
- Streaming via SSE or custom flow

ANDROID API:
- None directly in base class

ERROR HANDLING:
- RateLimitException with retryAfterMs
- TransientException with cause
- Implementations handle retries, timeouts, cancellations

TEST COVERAGE:
- LFMLLMClientConversionTest.kt
- LFMLLMClientTest.kt
- OpenAIResponseClientTest.kt
- OpenAIErrorClassifierTest.kt
- ModelCatalogTest.kt
- ModelDiscoveryTest.kt
- ContextWindowClassifierTest.kt
- CodexResponseClientTest.kt
- CodexRequestBuilderTest.kt
- CodeSseParserTest.kt
- CloudStreamRetryPolicyTest.kt
- CloudStreamRetryRunnerTest.kt
- ChatCompletionClientTest.kt
- CodexSseParserTest.kt
- OtherBaseUrlValidatorTest.kt
- ToolParameterExtractorTest.kt

STATUS: CONFIRMED IMPLEMENTED
- Well-designed abstract base class
- Proper streaming abstraction for cross-provider compatibility
- Rate limit config shared across providers
- Multiple concrete implementations (OpenAI, Local LFM, Codex, OpenRouter, Other)
- Comprehensive test coverage across all providers
- TYPE safe tool call representation (LLMToolCall data class)