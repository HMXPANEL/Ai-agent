package ai.closepaw.session

import ai.closepaw.llm.ApiType
import ai.closepaw.llm.LLMClient
import ai.closepaw.llm.LLMProvider
import ai.closepaw.llm.LLMStreamEvent
import ai.closepaw.llm.ModelEntry
import ai.closepaw.llm.ResponsesResult
import com.google.common.truth.Truth.assertThat
import com.openai.models.responses.FunctionTool
import com.openai.models.responses.ResponseInputItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Session-start provider guard + sanitized routing log.
 *
 * The guard is the last line of defense against the OTHER-tab/Codex-model
 * divergence: when the UI-selected provider and the model entry disagree,
 * bootstrap must throw — never silently send traffic to the wrong backend.
 */
class ProviderRoutingGuardTest {

    private fun entry(provider: LLMProvider, name: String = "m"): ModelEntry =
        ModelEntry(
            name = name,
            displayName = name,
            provider = provider,
            api = ApiType.CHAT,
            modelId = "model-id",
            contextWindow = 128000,
            baseUrl = "https://other.example.com/v1",
        )

    private class FakeClient : LLMClient() {
        override suspend fun chatWithTools(
            systemPrompt: String,
            inputItems: List<ResponseInputItem>,
            tools: List<FunctionTool>,
            modelId: String,
            maxOutputTokens: Long?,
        ): ResponsesResult = ResponsesResult(null, emptyList(), "r1")

        override fun chatWithToolsStreaming(
            systemPrompt: String,
            inputItems: List<ResponseInputItem>,
            tools: List<FunctionTool>,
            modelId: String,
        ): Flow<LLMStreamEvent> = emptyFlow()
    }

    @Test
    fun `matching provider and entry passes`() {
        // No throw = pass.
        checkProviderRouting(LLMProvider.OTHER, entry(LLMProvider.OTHER, "other-custom"))
        checkProviderRouting(LLMProvider.OPENAI_CODEX, entry(LLMProvider.OPENAI_CODEX, "gpt-5.4-codex"))
        checkProviderRouting(LLMProvider.OPENAI_API, entry(LLMProvider.OPENAI_API, "gpt-5.4"))
        checkProviderRouting(LLMProvider.OPENROUTER, entry(LLMProvider.OPENROUTER, "glm-5"))
    }

    @Test
    fun `OTHER selected with CODEX model throws`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            checkProviderRouting(LLMProvider.OTHER, entry(LLMProvider.OPENAI_CODEX, "gpt-5.4-codex"))
        }
        assertThat(ex.message).contains("OTHER")
        assertThat(ex.message).contains("OPENAI_CODEX")
        assertThat(ex.message).contains("gpt-5.4-codex")
    }

    @Test
    fun `CODEX selected with OTHER model throws`() {
        assertThrows(IllegalStateException::class.java) {
            checkProviderRouting(LLMProvider.OPENAI_CODEX, entry(LLMProvider.OTHER, "other-custom"))
        }
    }

    @Test
    fun `null provider skips the check`() {
        checkProviderRouting(null, entry(LLMProvider.OPENAI_CODEX, "gpt-5.4-codex"))
    }

    @Test
    fun `null entry skips the check`() {
        checkProviderRouting(LLMProvider.OTHER, null)
    }

    @Test
    fun `sanitizer keeps host and path, drops secrets`() {
        assertThat(sanitizeBaseUrlForLog("https://user:pass@other.example.com:8443/v1/?key=SECRET#frag"))
            .isEqualTo("other.example.com:8443/v1")
        assertThat(sanitizeBaseUrlForLog("https://other.example.com/v1/"))
            .isEqualTo("other.example.com/v1")
    }

    @Test
    fun `sanitizer handles blank and garbage`() {
        assertThat(sanitizeBaseUrlForLog(null)).isEqualTo("-")
        assertThat(sanitizeBaseUrlForLog("  ")).isEqualTo("-")
        assertThat(sanitizeBaseUrlForLog("::::")).isEqualTo("<unparseable>")
    }

    @Test
    fun `routing line names provider model client api host`() {
        val line = formatRoutingLine(
            provider = LLMProvider.OTHER,
            modelId = "gemini-2.5-flash",
            client = FakeClient(),
            api = ApiType.CHAT,
            baseUrl = "https://other.example.com/v1",
        )

        assertThat(line).contains("provider=OTHER")
        assertThat(line).contains("model=gemini-2.5-flash")
        assertThat(line).contains("client=FakeClient")
        assertThat(line).contains("api=CHAT")
        assertThat(line).contains("other.example.com/v1")
    }

    @Test
    fun `routing line never leaks query-string secrets`() {
        val line = formatRoutingLine(
            provider = LLMProvider.OTHER,
            modelId = "m",
            client = FakeClient(),
            api = ApiType.CHAT,
            baseUrl = "https://other.example.com/v1/?api_key=S3CR3T-KEY",
        )

        assertThat(line).doesNotContain("S3CR3T-KEY")
    }
}
