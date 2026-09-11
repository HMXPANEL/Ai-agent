package ai.closepaw.session

import ai.closepaw.history.model.ConversationConfigSnapshot
import ai.closepaw.llm.LLMProvider
import ai.closepaw.protocol.LLMBackendType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * P9: provider survives the checkpoint round-trip; unknown/legacy values
 * degrade to null (guard skipped) instead of crashing or misrouting.
 */
class ReloadProviderMappingTest {

    private fun snapshot(provider: String?) = ConversationConfigSnapshot(
        mainModel = "other-custom",
        perceptionMode = "accessibility_only",
        platformMode = "ACCESSIBILITY",
        provider = provider,
    )

    @Test
    fun `snapshot provider maps to config provider`() {
        val config = snapshot("OTHER").toSessionConfig()

        assertThat(config.provider).isEqualTo(LLMProvider.OTHER)
        assertThat(config.mainModel).isEqualTo("other-custom")
    }

    @Test
    fun `codex snapshot maps to codex config`() {
        val config = snapshot("OPENAI_CODEX").toSessionConfig()

        assertThat(config.provider).isEqualTo(LLMProvider.OPENAI_CODEX)
    }

    @Test
    fun `unknown provider string maps to null`() {
        val config = snapshot("NOPE").toSessionConfig()

        assertThat(config.provider).isNull()
    }

    @Test
    fun `legacy snapshot without provider maps to null`() {
        val config = snapshot(null).toSessionConfig()

        assertThat(config.provider).isNull()
    }

    @Test
    fun `config snapshot round-trips provider name`() {
        val config = ai.closepaw.protocol.SessionConfig(
            mainModel = "glm-5",
            provider = LLMProvider.OPENROUTER,
            llm = ai.closepaw.protocol.SessionLlmConfig(backendType = LLMBackendType.OPENAI),
        )

        val snap = config.toConfigSnapshot()

        assertThat(snap.provider).isEqualTo("OPENROUTER")
        assertThat(snap.toSessionConfig().provider).isEqualTo(LLMProvider.OPENROUTER)
    }
}
