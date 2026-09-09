package ai.closepaw.app

import ai.closepaw.auth.FakeSharedPreferences
import ai.closepaw.llm.LLMProvider
import ai.closepaw.llm.ModelCatalogRepository
import ai.closepaw.llm.ModelCatalogRepositoryHolder
import android.content.Context
import android.content.res.AssetManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Selected-provider persistence + model/provider coherence.
 *
 * Regression cover for the OTHER→Codex misroute class:
 * - an OTHER selection must survive validation, persistence, and reload —
 *   never silently rewritten to the default model (and never to a Codex key);
 * - provider and model must stay coherent across updateModel/updateProvider.
 */
class AppSettingsProviderTest {

    private lateinit var context: Context
    private lateinit var prefs: FakeSharedPreferences

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        val seedBytes = File("src/main/assets/llm_models.json").readBytes()
        val assetManager = mockk<AssetManager>()
        every { assetManager.open(any()) } answers {
            ByteArrayInputStream(seedBytes)
        }
        context = mockk(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { context.assets } returns assetManager
        every { context.applicationContext } returns context
        val repo = ModelCatalogRepository(
            context = context,
            settingsStore = AppSettingsStore(context),
            discoveryCache = mockk(relaxed = true),
        )
        ModelCatalogRepositoryHolder.setForTest(repo)
    }

    @After
    fun tearDown() {
        ModelCatalogRepositoryHolder.resetForTest()
        unmockkAll()
    }

    private fun state() = AppSettingsState(
        store = AppSettingsStore(context),
        appContext = context,
        onOtherSettingsChanged = {
            ModelCatalogRepositoryHolder.get(context).invalidate()
        },
    )

    private fun withValidOtherConfig() {
        val s = state()
        s.updateOtherBaseUrl("https://other.example.com/v1")
        s.updateOtherModelId("gen-x")
    }

    @Test
    fun `provider OTHER round-trips through store`() {
        val store = AppSettingsStore(context)

        store.saveProvider(LLMProvider.OTHER)

        assertThat(store.load().selectedProvider).isEqualTo(LLMProvider.OTHER)
    }

    @Test
    fun `provider CODEX round-trips through store`() {
        val store = AppSettingsStore(context)

        store.saveProvider(LLMProvider.OPENAI_CODEX)

        assertThat(store.load().selectedProvider).isEqualTo(LLMProvider.OPENAI_CODEX)
    }

    @Test
    fun `provider defaults to null and clears back to null`() {
        val store = AppSettingsStore(context)

        assertThat(store.load().selectedProvider).isNull()
        store.saveProvider(LLMProvider.OTHER)
        store.saveProvider(null)

        assertThat(store.load().selectedProvider).isNull()
    }

    @Test
    fun `unknown provider string loads as null instead of crashing`() {
        prefs.edit().putString("provider", "NO_SUCH_PROVIDER").apply()

        assertThat(AppSettingsStore(context).load().selectedProvider).isNull()
    }

    @Test
    fun `updateModel codex syncs provider`() {
        val s = state()
        s.load()

        s.updateModel("gpt-5.4-codex")

        assertThat(s.selectedModel).isEqualTo("gpt-5.4-codex")
        assertThat(s.selectedProvider).isEqualTo(LLMProvider.OPENAI_CODEX)
    }

    @Test
    fun `updateModel other-custom with valid OTHER config is kept`() {
        withValidOtherConfig()
        val s = state()
        s.load()

        s.updateModel(ModelCatalogRepository.OTHER_CUSTOM_NAME)

        assertThat(s.selectedModel).isEqualTo(ModelCatalogRepository.OTHER_CUSTOM_NAME)
        assertThat(s.selectedProvider).isEqualTo(LLMProvider.OTHER)
    }

    @Test
    fun `updateModel other-custom with blank OTHER config is kept, never defaulted`() {
        // THE R2 regression: a transiently-absent synth row must not rewrite
        // (and persist!) the default model — that silently switches providers.
        val s = state()
        s.load()

        s.updateModel(ModelCatalogRepository.OTHER_CUSTOM_NAME)

        assertThat(s.selectedModel).isEqualTo(ModelCatalogRepository.OTHER_CUSTOM_NAME)
        assertThat(s.selectedModel).isNotEqualTo(AppSettingsStore.DEFAULT_MODEL)
        assertThat(s.selectedProvider).isEqualTo(LLMProvider.OTHER)
        // …and the kept value — not the default — is what persisted.
        assertThat(AppSettingsStore(context).load().selectedModel)
            .isEqualTo(ModelCatalogRepository.OTHER_CUSTOM_NAME)
    }

    @Test
    fun `unknown model falls back to default with synced provider`() {
        val s = state()
        s.load()

        s.updateModel("retired-model-xyz")

        assertThat(s.selectedModel).isEqualTo(AppSettingsStore.DEFAULT_MODEL)
        // Seed default glm-5 is OPENROUTER: provider moves WITH the model.
        assertThat(s.selectedProvider).isEqualTo(LLMProvider.OPENROUTER)
    }

    @Test
    fun `persisted OTHER pair survives reload`() {
        withValidOtherConfig()
        val first = state()
        first.load()
        first.updateModel(ModelCatalogRepository.OTHER_CUSTOM_NAME)

        val second = state()
        second.load()

        assertThat(second.selectedModel).isEqualTo(ModelCatalogRepository.OTHER_CUSTOM_NAME)
        assertThat(second.selectedProvider).isEqualTo(LLMProvider.OTHER)
    }

    @Test
    fun `persisted CODEX pair survives reload`() {
        val first = state()
        first.load()
        first.updateModel("gpt-5.4-codex")

        val second = state()
        second.load()

        assertThat(second.selectedModel).isEqualTo("gpt-5.4-codex")
        assertThat(second.selectedProvider).isEqualTo(LLMProvider.OPENAI_CODEX)
    }

    @Test
    fun `updateProvider OTHER reconciles model to other-custom`() {
        withValidOtherConfig()
        val s = state()
        s.load()
        s.updateModel("gpt-5.4-codex")

        s.updateProvider(LLMProvider.OTHER)

        assertThat(s.selectedProvider).isEqualTo(LLMProvider.OTHER)
        assertThat(s.selectedModel).isEqualTo(ModelCatalogRepository.OTHER_CUSTOM_NAME)
    }

    @Test
    fun `updateProvider OTHER without valid config keeps optimistic other-custom`() {
        val s = state()
        s.load()
        s.updateModel("gpt-5.4-codex")

        s.updateProvider(LLMProvider.OTHER)

        // No valid synth yet: optimistic key (kept by validation) + OTHER.
        // Session bootstrap reports incomplete config as MissingCredential(OTHER).
        assertThat(s.selectedProvider).isEqualTo(LLMProvider.OTHER)
        assertThat(s.selectedModel).isEqualTo(ModelCatalogRepository.OTHER_CUSTOM_NAME)
    }

    @Test
    fun `updateProvider CODEX reconciles model to a codex entry`() {
        val s = state()
        s.load()
        s.updateModel("glm-5")

        s.updateProvider(LLMProvider.OPENAI_CODEX)

        assertThat(s.selectedProvider).isEqualTo(LLMProvider.OPENAI_CODEX)
        val entry = ModelCatalogRepositoryHolder.get(context)
            .catalog.value.resolveOrNull(s.selectedModel)
        assertThat(entry?.provider).isEqualTo(LLMProvider.OPENAI_CODEX)
    }
}
