package ai.closepaw.llm

import android.util.Log
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.responses.FunctionTool
import com.openai.models.responses.ResponseInputItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * LLM client using OpenAI Chat Completions API.
 *
 * Works with any OpenAI-compatible endpoint (OpenRouter, vLLM, etc.)
 * by setting [baseUrl] and [apiKey]. Accepts the same ResponseInputItem /
 * FunctionTool types as the callers produce and converts them internally
 * to Chat Completions types via [ChatCompletionInterop].
 *
 * Non-streaming: client.chat().completions().create()
 * Streaming:     client.chat().completions().createStreaming()
 */
class ChatCompletionClient(
    entry: ModelEntry,
    apiKey: String,
    baseUrl: String? = null
) : LLMClient() {

    companion object {
        private const val TAG = "ChatCompletionClient"
    }

    private val modelId: String = entry.modelId

    init {
        InsecureSslConfig.validateBaseUrl(baseUrl)
    }

    private val client: OpenAIClient = OpenAIOkHttpClient.builder()
        .apiKey(apiKey)
        .apply { baseUrl?.let { baseUrl(it) } }
        .apply {
            InsecureSslConfig.sslSocketFactory?.let { sslSocketFactory(it) }
            InsecureSslConfig.trustManager?.let { trustManager(it) }
        }
        .build()

    // ── Non-streaming ───────────────────────────────────────────────────

    override suspend fun chatWithTools(
        systemPrompt: String,
        inputItems: List<ResponseInputItem>,
        tools: List<FunctionTool>,
        modelId: String,
        maxOutputTokens: Long?,
    ): ResponsesResult = withContext(Dispatchers.IO) {
        CloudLlmRetry.executeWithRetry(
                tag = TAG,
                operationName = "chat-completions chatWithTools"
        ) {
            executeChatWithTools(systemPrompt, inputItems, tools, this@ChatCompletionClient.modelId, maxOutputTokens)
        }
    }

    private fun executeChatWithTools(
        systemPrompt: String,
        inputItems: List<ResponseInputItem>,
        tools: List<FunctionTool>,
        modelId: String,
        maxOutputTokens: Long?,
    ): ResponsesResult {
        Log.d(TAG, "Calling Chat Completions API with ${inputItems.size} input items, ${tools.size} tools")
        LlmLogger.logInput(TAG, systemPrompt, inputItems, tools)

        try {
            val params = buildParams(systemPrompt, inputItems, tools, modelId, maxOutputTokens)
            val response = client.chat().completions().create(params)

            val choice = response.choices().firstOrNull()
                ?: return ResponsesResult(textContent = null, toolCalls = emptyList(), responseId = response.id())

            val message = choice.message()
            val textContent = message.content().orElse(null)
            val toolCalls = message.toolCalls().orElse(emptyList())
                .filter { it.isFunction() }
                .map { tc ->
                    val func = tc.asFunction()
                    LLMToolCall(
                        callId = func.id(),
                        name = func.function().name(),
                        arguments = func.function().arguments()
                    )
                }

            val result = ResponsesResult(
                textContent = textContent,
                toolCalls = toolCalls,
                responseId = response.id()
            )
            Log.d(TAG, "Chat API result: ${result.textContent?.take(200)}, ${result.toolCalls.size} tool calls")
            LlmLogger.logOutput(TAG, result)
            return result
        } catch (e: Exception) {
            throw OpenAIErrorClassifier.classify(e)
        }
    }

    // ── Streaming ───────────────────────────────────────────────────────

    override fun chatWithToolsStreaming(
        systemPrompt: String,
        inputItems: List<ResponseInputItem>,
        tools: List<FunctionTool>,
        modelId: String
    ): Flow<LLMStreamEvent> = callbackFlow {
        Log.d(TAG, "Starting streaming Chat Completions with ${inputItems.size} input items")
        LlmLogger.logInput(TAG, systemPrompt, inputItems, tools)

        val activeStream = AtomicReference<AutoCloseable?>(null)

        val job = launch {
            val retryResult =
                streamWithRetry(
                    tag = TAG,
                    emitToFlow = { event -> trySend(event) }
                ) { attempt, emitter ->
                val verbose = LlmLogger.isVerboseEnabled
                val textAccumulator = if (verbose) StringBuilder() else null
                // Correlates streamed tool-call fragments. Indexed deltas key by
                // index; index-less deltas (some OpenAI-compatible providers omit
                // `index`) correlate by occurrence order so parallel calls stay
                // distinct instead of merging into one bucket.
                val toolCallAccumulator = ToolCallDeltaAccumulator()
                val completedToolCalls = if (verbose) mutableListOf<LLMToolCall>() else null
                var responseId: String? = null
                var sawFinishReason = false

                val params = buildParams(systemPrompt, inputItems, tools, this@ChatCompletionClient.modelId)
                Log.d(TAG, "Making streaming Chat API call (attempt $attempt)")

                withContext(Dispatchers.IO) {
                    val streamResponse = client.chat().completions().createStreaming(params)
                    activeStream.set(streamResponse)
                    try {
                        streamResponse.use { stream ->
                            stream.stream().forEach { chunk ->
                            if (responseId == null) {
                                responseId = chunk.id()
                                emitter.emit(LLMStreamEvent.Created(chunk.id()))
                            }

                            for (choice in chunk.choices()) {
                                val delta = choice.delta()

                                // Text content delta
                                delta.content().ifPresent { text ->
                                    if (text.isNotEmpty()) {
                                        textAccumulator?.append(text)
                                        emitter.emit(LLMStreamEvent.TextDelta(text))
                                    }
                                }

                                // Tool call deltas (streamed incrementally)
                                delta.toolCalls().ifPresent { calls ->
                                    var unindexedOrdinal = 0
                                    for (tcDelta in calls) {
                                        // Some OpenAI-compatible providers (e.g., Gemini)
                                        // omit the index field, which throws
                                        // OpenAIInvalidDataException on access.
                                        val idx: Long? = try {
                                            tcDelta.index()
                                        } catch (e: Exception) {
                                            null
                                        }
                                        val ordinal = if (idx == null) unindexedOrdinal++ else 0
                                        toolCallAccumulator.onDelta(
                                            index = idx,
                                            id = tcDelta.id().orElse(null),
                                            name = tcDelta.function().orElse(null)?.name()?.orElse(null),
                                            argsFragment = tcDelta.function().orElse(null)
                                                ?.arguments()?.orElse(null),
                                            chunkOrdinal = ordinal
                                        )
                                    }
                                }

                                // Emit completed tool calls when finish reason received
                                choice.finishReason().ifPresent { reason ->
                                    when (reason.toString()) {
                                        "stop", "tool_calls" -> {
                                            sawFinishReason = true
                                            for (toolCall in toolCallAccumulator.drain()) {
                                                completedToolCalls?.add(toolCall)
                                                emitter.emit(LLMStreamEvent.ToolCallDone(toolCall))
                                            }
                                        }
                                        "length" -> {
                                            throw TransientException("Response truncated (finish_reason=length)")
                                        }
                                        "content_filter" -> {
                                            sawFinishReason = true
                                            emitter.emit(LLMStreamEvent.Failed("Response blocked by content filter"))
                                        }
                                        else -> {
                                            sawFinishReason = true
                                        }
                                    }
                                }
                            }
                        }
                            }
                        } finally {
                            activeStream.set(null)
                        }
                    }

                    // Stream ended — require terminal completion
                    if (!sawFinishReason) {
                        throw TransientException("Stream ended without finish_reason")
                    }
                    if (verbose && textAccumulator != null && completedToolCalls != null) {
                        LlmLogger.logOutput(
                            TAG,
                            ResponsesResult(
                                textContent = textAccumulator.toString().takeIf { it.isNotEmpty() },
                                toolCalls = completedToolCalls,
                                responseId = responseId ?: "unknown"
                            )
                        )
                    }
                    emitter.emit(LLMStreamEvent.Completed)
                }

            retryResult.closeFlow(
                emitToFlow = { trySend(it) },
                closeFlow = { close() }
            )
        }

        awaitClose {
            activeStream.getAndSet(null)?.runCatching { close() }
            job.cancel()
            Log.d(TAG, "Streaming flow closed")
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun buildParams(
        systemPrompt: String,
        inputItems: List<ResponseInputItem>,
        tools: List<FunctionTool>,
        modelId: String,
        maxOutputTokens: Long? = null,
    ): ChatCompletionCreateParams {
        val messages = buildList {
            add(ChatCompletionInterop.systemMessage(systemPrompt))
            addAll(ChatCompletionInterop.convertInputItems(inputItems))
        }
        val chatTools = ChatCompletionInterop.convertTools(tools)

        val builder = ChatCompletionCreateParams.builder()
            .model(ChatModel.of(modelId))
            .messages(messages)
            .tools(chatTools)

        maxOutputTokens?.let { builder.maxCompletionTokens(it) }

        return builder.build()
    }

    override suspend fun cleanup() {
        Log.d(TAG, "Cleanup requested (no-op for cloud client)")
    }
}
