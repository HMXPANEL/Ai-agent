package ai.closepaw.app

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ai.closepaw.llm.LLMProvider
import ai.closepaw.llm.ModelCatalog
import ai.closepaw.llm.ModelCatalogRepository
import ai.closepaw.llm.ModelCatalogRepositoryHolder
import ai.closepaw.protocol.ApprovalMode
import ai.closepaw.protocol.LLMBackendType
import ai.closepaw.protocol.PlatformMode
import ai.closepaw.ui.settings.LocalModelOption

class AppSettingsState(
    private val store: AppSettingsStore,
    private val appContext: Context,
    /**
     * Optional hook fired after `otherBaseUrl` / `otherModelId` writes. Lets the
     * caller invalidate the process-wide [ai.closepaw.llm.ModelCatalogRepository]
     * so the synthesized `other-custom` entry reflects the new settings. Pulled
     * out as a constructor parameter so plain JVM tests can substitute it
     * without holding a real Android Context.
     */
    private val onOtherSettingsChanged: () -> Unit = {},
) {
    companion object {
        private const val TAG = "AppSettingsState"

        /**
         * Production wiring: build a state that invalidates the app-singleton
         * [ai.closepaw.llm.ModelCatalogRepository] whenever the OTHER settings
         * change. Done as a factory so production callers don't repeat the
         * holder lookup and tests can use the bare constructor.
         */
        fun create(context: Context): AppSettingsState {
            val appContext = context.applicationContext
            return AppSettingsState(
                store = AppSettingsStore(appContext),
                appContext = appContext,
                onOtherSettingsChanged = {
                    ModelCatalogRepositoryHolder.get(appContext).invalidate()
                },
            )
        }
    }

    var selectedModel by mutableStateOf(AppSettingsStore.DEFAULT_MODEL)
        private set
    /**
     * Explicitly selected provider, kept coherent with [selectedModel].
     * Null when never explicitly chosen — callers then derive from the model.
     * The session bootstrap refuses to silently route when the persisted
     * provider and the model's catalog provider disagree.
     */
    var selectedProvider by mutableStateOf<LLMProvider?>(null)
        private set
    var debugMode by mutableStateOf(AppSettingsStore.DEFAULT_DEBUG_MODE)
        private set
    var perceptionMode by mutableStateOf(AppSettingsStore.DEFAULT_PERCEPTION_MODE)
        private set
    var llmBackend by mutableStateOf(AppSettingsStore.DEFAULT_LLM_BACKEND)
        private set
    var localModel by mutableStateOf<LocalModelOption>(AppSettingsStore.DEFAULT_LOCAL_MODEL)
        private set
    var platformMode by mutableStateOf(AppSettingsStore.DEFAULT_PLATFORM_MODE)
        private set
    var traceEnabled by mutableStateOf(AppSettingsStore.DEFAULT_TRACE_ENABLED)
        private set
    var browserScriptEnabled by mutableStateOf(AppSettingsStore.DEFAULT_BROWSER_SCRIPT_ENABLED)
        private set
    var approvalMode by mutableStateOf(AppSettingsStore.DEFAULT_APPROVAL_MODE)
        private set

    /** Base URL override for OPENAI provider (set from intent / Settings; persisted). */
    var openaiBaseUrl by mutableStateOf("")
        private set

    /** Base URL for the user-configured OTHER provider (persisted). */
    var otherBaseUrl by mutableStateOf("")
        private set

    /** Model id for the user-configured OTHER provider (persisted). */
    var otherModelId by mutableStateOf("")
        private set

    fun load() {
        val settings = store.load()
        val (model, provider) = coherentPair(settings.selectedModel, settings.selectedProvider)
        selectedModel = model
        selectedProvider = provider
        debugMode = settings.debugMode
        perceptionMode = settings.perceptionMode
        llmBackend = settings.llmBackend
        localModel = settings.localModel
        platformMode = settings.platformMode
        traceEnabled = settings.traceEnabled
        browserScriptEnabled = settings.browserScriptEnabled
        openaiBaseUrl = settings.openaiBaseUrl
        otherBaseUrl = settings.otherBaseUrl
        otherModelId = settings.otherModelId
        approvalMode = settings.approvalMode

        Log.d(
                TAG,
                "Settings loaded: backend=$llmBackend, model=$selectedModel, localModel=${localModel.id}, debugMode=$debugMode, perceptionMode=$perceptionMode, platformMode=$platformMode"
        )
    }

    fun updateBackend(backend: LLMBackendType) {
        llmBackend = backend
        store.saveBackend(backend)
    }

    fun updateModel(model: String) {
        val (coherentModel, coherentProvider) = coherentPair(model, selectedProvider)
        selectedModel = coherentModel
        selectedProvider = coherentProvider
        store.saveModel(coherentModel)
        store.saveProvider(coherentProvider)
    }

    /**
     * Explicit provider switch. Reconciles the model so the two can never
     * silently disagree: the model moves to the provider's preferred entry.
     * For OTHER without a valid catalog entry yet, the model moves to
     * `other-custom` optimistically — validation keeps OTHER-flavored keys
     * and the session bootstrap surfaces incomplete config as
     * MissingCredential(OTHER) instead of routing elsewhere.
     *
     * Deliberately NOT wired to provider-tab taps (peeking at a tab must not
     * destroy the current model selection); it backs explicit switches,
     * intent/deep-link flows, and tests.
     */
    fun updateProvider(provider: LLMProvider) {
        val catalog = currentCatalogOrNull()
        val currentProvider = providerOf(selectedModel, catalog)
        if (currentProvider == provider) {
            selectedProvider = provider
            store.saveProvider(provider)
            return
        }
        val target =
            catalog?.preferredModelFor(provider)?.name
                ?: if (provider == LLMProvider.OTHER) {
                    ModelCatalogRepository.OTHER_CUSTOM_NAME
                } else {
                    null
                }
        if (target != null) {
            val (model, derived) = coherentPair(target, provider)
            selectedModel = model
            // Target was chosen for `provider`; force the explicit choice so the
            // two can never silently disagree (coherentPair only differs here on
            // racy catalog staleness — loud, and the session guard stays armed).
            if (derived != provider) {
                Log.w(TAG, "Provider $provider has no coherent model yet; keeping $model")
            }
            selectedProvider = provider
            store.saveModel(model)
            store.saveProvider(provider)
        } else {
            Log.w(TAG, "No catalog entry for provider $provider; keeping model $selectedModel")
            selectedProvider = provider
            store.saveProvider(provider)
        }
    }

    fun updateLocalModel(model: LocalModelOption) {
        localModel = model
        store.saveLocalModel(model)
    }

    fun updateOpenaiBaseUrl(url: String) {
        openaiBaseUrl = url
        store.saveOpenaiBaseUrl(url)
    }

    fun updateOtherBaseUrl(url: String) {
        otherBaseUrl = url
        store.saveOtherBaseUrl(url)
        onOtherSettingsChanged()
    }

    fun updateOtherModelId(modelId: String) {
        otherModelId = modelId
        store.saveOtherModelId(modelId)
        onOtherSettingsChanged()
    }

    fun updateDebugMode(value: Boolean) {
        debugMode = value
        store.saveDebugMode(value)
    }

    fun updateTraceEnabled(value: Boolean) {
        traceEnabled = value
        store.saveTraceEnabled(value)
    }

    fun updateBrowserScriptEnabled(value: Boolean) {
        browserScriptEnabled = value
        store.saveBrowserScriptEnabled(value)
    }

    fun updatePerceptionMode(value: String) {
        perceptionMode = value
        store.savePerceptionMode(value)
    }

    fun updatePlatformMode(value: PlatformMode) {
        platformMode = value
        store.savePlatformMode(value)
    }

    fun updateApprovalMode(value: ApprovalMode) {
        approvalMode = value
        store.saveApprovalMode(value)
    }

    /**
     * Reconcile a (model, provider) pair so the two can never silently disagree.
     * Model wins on genuine conflict; OTHER-flavored keys are always trusted
     * (their absence means "incomplete OTHER config", which the session
     * bootstrap reports as MissingCredential(OTHER) — never a silent reroute).
     */
    private fun coherentPair(
        storedModel: String,
        storedProvider: LLMProvider?
    ): Pair<String, LLMProvider?> {
        val model = validateModelAgainstCatalog(storedModel)
        if (model != storedModel) {
            // Genuine fallback (removed/unknown seed key, e.g. retired models):
            // move the provider WITH the model, loudly — never leave a stale
            // provider pointing at a different backend than the model routes to.
            val fallbackProvider = providerOf(model, currentCatalogOrNull())
            Log.w(TAG, "Model '$storedModel' unknown; falling back to '$model' ($fallbackProvider)")
            return model to fallbackProvider
        }
        val entryProvider = providerOf(model, currentCatalogOrNull())
        // Keep an explicit stored choice only when it agrees with the entry;
        // otherwise the entry (what the session will actually route by) wins.
        return model to (storedProvider?.takeIf { it == entryProvider } ?: entryProvider)
    }

    private fun providerOf(model: String, catalog: ModelCatalog?): LLMProvider? {
        if (isOtherFlavored(model)) return LLMProvider.OTHER
        return catalog?.resolveOrNull(model)?.provider
    }

    private fun isOtherFlavored(model: String): Boolean =
        model == ModelCatalogRepository.OTHER_CUSTOM_NAME ||
            model.startsWith(ModelCatalogRepository.OTHER_DISCOVERED_PREFIX)

    private fun currentCatalogOrNull(): ModelCatalog? =
        try {
            ModelCatalogRepositoryHolder.get(appContext).catalog.value
        } catch (e: Exception) {
            null
        }

    private fun validateModelAgainstCatalog(model: String): String {
        // OTHER-flavored keys are trusted even when transiently absent: absence
        // means incomplete OTHER config, not a wrong selection. Rewriting them
        // to the default would silently switch providers AND persist it.
        if (isOtherFlavored(model)) return model
        val catalog = currentCatalogOrNull() ?: return model
        return if (catalog.contains(model)) model else AppSettingsStore.DEFAULT_MODEL
    }
}
