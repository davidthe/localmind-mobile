package dev.localmind.app.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ModelRuntimeState(val label: String) {
    INITIALIZING("Loading SmolLM2-135M on-device…"),
    READY("SmolLM2-135M Running On-Device"),
    FALLBACK("Fallback Responder"),
}

data class SearchUiState(
    val query: String = "",
    val options: SearchOptions = SearchOptions(),
    val responses: List<SmallLlmResponse> = emptyList(),
    val focusRequestCounter: Int = 0,
    val statusNotice: String? = null,
    val isGenerating: Boolean = false,
    val modelRuntimeState: ModelRuntimeState = ModelRuntimeState.FALLBACK,
    val modelDetails: String = "",
)

class SearchViewModel(
    private val responder: SmallLlmResponder = TinyLocalLlmResponder(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val _uiState = MutableStateFlow(
        SearchUiState(
            modelRuntimeState = if (responder.isNativeRuntime) {
                ModelRuntimeState.INITIALIZING
            } else {
                ModelRuntimeState.FALLBACK
            },
            modelDetails = responder.modelName,
            responses = listOf(
                SmallLlmResponse(
                    id = "welcome",
                    modelName = responder.modelName,
                    query = "",
                    options = SearchOptions(),
                    answer = "LocalMind is ready. Type a query in the search line or use a search widget option (File Names / Inside Files) to run the local small LLM.",
                    searchSupported = false,
                    ranNativeLlm = responder.isNativeRuntime,
                ),
            ),
        ),
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun initializeModel() {
        if (!responder.isNativeRuntime) return
        scope.launch {
            _uiState.update { it.copy(modelRuntimeState = ModelRuntimeState.INITIALIZING) }
            val result = responder.ensureLoaded()
            _uiState.update { state ->
                if (result.isSuccess) {
                    state.copy(
                        modelRuntimeState = ModelRuntimeState.READY,
                        modelDetails = responder.modelName,
                    )
                } else {
                    state.copy(
                        modelRuntimeState = ModelRuntimeState.FALLBACK,
                        statusNotice = "Native LLM load warning: ${result.exceptionOrNull()?.message ?: "using fallback"}",
                    )
                }
            }
        }
    }

    fun updateQuery(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
    }

    fun selectScope(scope: SearchScope) {
        _uiState.update { state ->
            state.copy(options = state.options.copy(scope = scope))
        }
    }

    fun toggleCaseSensitive() {
        _uiState.update { state ->
            state.copy(options = state.options.copy(caseSensitive = !state.options.caseSensitive))
        }
    }

    fun toggleWholeWord() {
        _uiState.update { state ->
            state.copy(options = state.options.copy(wholeWord = !state.options.wholeWord))
        }
    }

    fun toggleRegex() {
        _uiState.update { state ->
            state.copy(options = state.options.copy(useRegex = !state.options.useRegex))
        }
    }

    /**
     * Synchronous helper for unit tests; for native async LLM inference use [submitSearchAsync].
     */
    fun submitSearch(triggeredFromWidget: Boolean = false) {
        val snapshot = _uiState.value
        val response = responder.respondToSearch(
            query = snapshot.query,
            options = snapshot.options,
            triggeredFromWidget = triggeredFromWidget,
        )
        _uiState.update { state ->
            state.copy(
                responses = listOf(response) + state.responses,
                statusNotice = null,
            )
        }
    }

    fun submitSearchAsync(triggeredFromWidget: Boolean = false) {
        val snapshot = _uiState.value
        if (!responder.isNativeRuntime) {
            submitSearch(triggeredFromWidget)
            return
        }
        scope.launch {
            _uiState.update { it.copy(isGenerating = true, statusNotice = null) }
            val response = responder.respondToSearchAsync(
                query = snapshot.query,
                options = snapshot.options,
                triggeredFromWidget = triggeredFromWidget,
            )
            _uiState.update { state ->
                state.copy(
                    isGenerating = false,
                    modelRuntimeState = if (response.ranNativeLlm) {
                        ModelRuntimeState.READY
                    } else {
                        state.modelRuntimeState
                    },
                    responses = listOf(response) + state.responses,
                )
            }
        }
    }

    fun runWidgetPreset(presetQuery: String, scope: SearchScope) {
        _uiState.update { state ->
            state.copy(
                query = presetQuery,
                options = state.options.copy(scope = scope),
                focusRequestCounter = state.focusRequestCounter + 1,
            )
        }
        submitSearchAsync(triggeredFromWidget = true)
    }

    fun applyLaunchRequest(request: SearchLaunchRequest) {
        _uiState.update { state ->
            state.copy(
                query = if (request.query.isNotEmpty()) request.query else state.query,
                options = state.options.copy(scope = request.scope),
                focusRequestCounter = if (request.focusSearchLine) {
                    state.focusRequestCounter + 1
                } else {
                    state.focusRequestCounter
                },
                statusNotice = if (request.fromWidget) {
                    "Opened from Home Search Widget (${request.scope.label})"
                } else {
                    null
                },
            )
        }
        if (request.autoSubmit || request.query.isNotBlank()) {
            submitSearchAsync(triggeredFromWidget = request.fromWidget)
        }
    }

    fun setStatusNotice(message: String) {
        _uiState.update { it.copy(statusNotice = message) }
    }

    fun clearResponses() {
        _uiState.update { it.copy(responses = emptyList(), statusNotice = null) }
    }
}
