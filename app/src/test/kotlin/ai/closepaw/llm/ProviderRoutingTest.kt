package ai.closepaw.llm

import ai.closepaw.auth.AuthStore
import ai.closepaw.auth.CodexHeaders
import ai.closepaw.auth.MissingCredential
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Provider-routing invariants: the UI-selected provider must reach the
 * correct [LLMClient] implementation and endpoint — never silently another
 * backend (notably: OTHER must never reach Codex).
 *
 * Client TYPE determines the wire path (URL, headers, body shape), so
 * asserting the built type pins the endpoint:
 * - [ChatCompletionClient] → provider base URL, plain API-key auth
 * - [CodexResponseClient] → chatgpt.com/backend-api/codex/responses + OAuth
 * - [OpenAIResponseClient] → OpenAI Responses API
 */
class ProviderRoutingTest {

    private val catalogJson = """
        {
          "other-custom": {
            "display_name": "Other",
            "provider": "OTHER",
            "api": "chat",
            "model_id": "gemini-2.5-flash",
            "base_url": "https://other.example.com/v1"
          },
          "gpt-5.4": {
            "display_name": "GPT-5.4",
            "provider": "OPENAI_API",
            "api": "response",
            "model_id": "gpt-5.4"
          },
          "gpt-5.4-chat": {
            "display_name": "GPT-5.4 (Chat)",
            "provider": "OPENAI_API",
            "api": "chat",
            "model_id": "gpt-5.4"
          },
          "gpt-5.4-codex": {
            "display_name": "GPT-5.4 (ChatGPT sign-in)",
            "provider": "OPENAI_CODEX",
            "api": "response",
            "model_id": "gpt-5.4"
          },
          "glm-5": {
            "display_name": "GLM-5",
            "provider": "OPENROUTER",
            "api": "chat",
            "model_id": "z-ai/glm-5"
          },
          "local-x": {
            "display_name": "Local",
            "provider": "LOCAL_LFM",
            "api": "chat",
            "model_id": "local-x",
            "context_window": 8000
          }
        }
    """.trimIndent()

    private val catalog = ModelCatalog.fromJson(catalogJson)

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun mockStore(): AuthStore {
        val store = mockk<AuthStore>(relaxed = true)
        every { store.generation(any()) } returns 0L
        every { store.requireApiKey(any()) } returns "test-key"
        coEvery { store.codexHeaders(any()) } returns
            CodexHeaders(
                accessToken = "acc-token",
                chatgptAccountId = "acct-test",
                email = "user@example.com",
            )
        return store
    }

    private fun factory(): LLMClientFactory = LLMClientFactory(catalog, mockStore())

    @Test
    fun `OTHER builds ChatCompletionClient`() {
        val client = factory().create("other-custom")

        assertThat(client).isInstanceOf(ChatCompletionClient::class.java)
    }

    @Test
    fun `OTHER never builds CodexResponseClient`() {
        val client = factory().create("other-custom")

        assertThat(client).isNotInstanceOf(CodexResponseClient::class.java)
        assertThat(client).isNotInstanceOf(OpenAIResponseClient::class.java)
    }

    @Test
    fun `OTHER uses the configured base URL, not the Codex endpoint`() {
        val entry = catalog.resolve("other-custom")

        assertThat(entry.provider).isEqualTo(LLMProvider.OTHER)
        assertThat(entry.effectiveBaseUrl).isEqualTo("https://other.example.com/v1")
        assertThat(entry.effectiveBaseUrl).doesNotContain("chatgpt.com")
    }

    @Test
    fun `OTHER without a base URL fails loudly instead of misrouting`() {
        val noUrlCatalog = ModelCatalog.fromJson(
            """
            {
              "other-custom": {
                "display_name": "Other",
                "provider": "OTHER",
                "api": "chat",
                "model_id": "gemini-2.5-flash"
              }
            }
            """.trimIndent()
        )
        val factory = LLMClientFactory(noUrlCatalog, mockStore())

        assertThrows(MissingCredential::class.java) { factory.create("other-custom") }
    }

    @Test
    fun `OPENAI_CODEX builds CodexResponseClient`() {
        val client = factory().create("gpt-5.4-codex")

        assertThat(client).isInstanceOf(CodexResponseClient::class.java)
    }

    @Test
    fun `CODEX entry does not use the OTHER base URL`() {
        val entry = catalog.resolve("gpt-5.4-codex")

        assertThat(entry.provider).isEqualTo(LLMProvider.OPENAI_CODEX)
        assertThat(entry.effectiveBaseUrl ?: "").doesNotContain("other.example.com")
    }

    @Test
    fun `OPENAI_API response entry builds OpenAIResponseClient`() {
        val client = factory().create("gpt-5.4")

        assertThat(client).isInstanceOf(OpenAIResponseClient::class.java)
        assertThat(client).isNotInstanceOf(CodexResponseClient::class.java)
    }

    @Test
    fun `OPENAI_API chat entry builds ChatCompletionClient`() {
        val client = factory().create("gpt-5.4-chat")

        assertThat(client).isInstanceOf(ChatCompletionClient::class.java)
        assertThat(client).isNotInstanceOf(CodexResponseClient::class.java)
    }

    @Test
    fun `OPENROUTER builds ChatCompletionClient`() {
        val client = factory().create("glm-5")

        assertThat(client).isInstanceOf(ChatCompletionClient::class.java)
        assertThat(client).isNotInstanceOf(CodexResponseClient::class.java)
    }

    @Test
    fun `LOCAL_LFM is refused with directions to LFMLLMClient`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            factory().create("local-x")
        }
        assertThat(ex.message).contains("LFMLLMClient")
    }

    @Test
    fun `provider switch OTHER to CODEX routes each model to its own client`() {
        val factory = factory()

        assertThat(factory.create("other-custom")).isInstanceOf(ChatCompletionClient::class.java)
        assertThat(factory.create("gpt-5.4-codex")).isInstanceOf(CodexResponseClient::class.java)
    }

    @Test
    fun `provider switch CODEX to OTHER routes each model to its own client`() {
        val factory = factory()

        assertThat(factory.create("gpt-5.4-codex")).isInstanceOf(CodexResponseClient::class.java)
        val backToOther = factory.create("other-custom")
        assertThat(backToOther).isInstanceOf(ChatCompletionClient::class.java)
        assertThat(backToOther).isNotInstanceOf(CodexResponseClient::class.java)
    }

    @Test
    fun `tripwire accepts every correct provider client pairing`() {
        val headers = CodexHeaders("acc", "acct", "u@e.c")
        checkClientMatchesEntry(
            catalog.resolve("other-custom"),
            ChatCompletionClient(catalog.resolve("other-custom"), "k")
        )
        checkClientMatchesEntry(
            catalog.resolve("gpt-5.4"),
            OpenAIResponseClient(catalog.resolve("gpt-5.4"), "k")
        )
        checkClientMatchesEntry(
            catalog.resolve("gpt-5.4-chat"),
            ChatCompletionClient(catalog.resolve("gpt-5.4-chat"), "k")
        )
        checkClientMatchesEntry(
            catalog.resolve("gpt-5.4-codex"),
            CodexResponseClient(catalog.resolve("gpt-5.4-codex")) { headers }
        )
        // No throw = pass.
    }

    @Test
    fun `tripwire rejects OTHER entry paired with Codex client`() {
        val codexClient = CodexResponseClient(catalog.resolve("gpt-5.4-codex")) {
            CodexHeaders("acc", "acct", "u@e.c")
        }

        val ex = assertThrows(IllegalStateException::class.java) {
            checkClientMatchesEntry(catalog.resolve("other-custom"), codexClient)
        }
        assertThat(ex.message).contains("other-custom")
    }

    @Test
    fun `tripwire rejects CODEX entry paired with Chat client`() {
        val chatClient = ChatCompletionClient(catalog.resolve("other-custom"), "k")

        val ex = assertThrows(IllegalStateException::class.java) {
            checkClientMatchesEntry(catalog.resolve("gpt-5.4-codex"), chatClient)
        }
        assertThat(ex.message).contains("gpt-5.4-codex")
    }
}
