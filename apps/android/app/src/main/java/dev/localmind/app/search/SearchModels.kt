package dev.localmind.app.search

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class SearchScope(val wireValue: String, val label: String) {
    ALL("all", "All"),
    FILE_NAMES("filenames", "File Names"),
    INSIDE_FILES("contents", "Inside Files");

    companion object {
        fun fromWire(value: String?): SearchScope =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: ALL
    }
}

data class SearchOptions(
    val scope: SearchScope = SearchScope.ALL,
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = false,
    val useRegex: Boolean = false,
) {
    fun activeFlagLabels(): List<String> = buildList {
        if (caseSensitive) add("Match Case (Aa)")
        if (wholeWord) add("Whole Word (ab)")
        if (useRegex) add("Regex (.*)")
    }
}

data class SmallLlmResponse(
    val id: String,
    val modelName: String,
    val query: String,
    val options: SearchOptions,
    val answer: String,
    val searchSupported: Boolean,
    val triggeredFromWidget: Boolean = false,
    val inferenceMillis: Long? = null,
    val ranNativeLlm: Boolean = false,
)

object LocalMindPromptBuilder {
    fun scopeDescription(scope: SearchScope): String = when (scope) {
        SearchScope.ALL -> "file names and inside files"
        SearchScope.FILE_NAMES -> "file names"
        SearchScope.INSIDE_FILES -> "inside files"
    }

    fun buildChatMlPrompt(
        query: String,
        options: SearchOptions,
        triggeredFromWidget: Boolean,
    ): String {
        val trimmed = query.trim().ifEmpty { "(empty query)" }
        val scopeDesc = scopeDescription(options.scope)
        val flags = options.activeFlagLabels().ifEmpty { listOf("none") }.joinToString(", ")
        val source = if (triggeredFromWidget) "home search widget" else "search line"

        return buildString {
            append("<|im_start|>system\n")
            append("You are LocalMind, a small local assistant inside a VS Code-style file search app. ")
            append("Searching files is not yet supported in this version. ")
            append("Answer concisely in 1 or 2 sentences stating that searching is not yet supported.<|im_end|>\n")
            append("<|im_start|>user\n")
            append("User searched for \"$trimmed\" in $scopeDesc via $source (options: $flags). ")
            append("Tell the user that searching is not yet supported.<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
    }

    fun cleanAssistantOutput(raw: String): String {
        return raw
            .replace("<|im_end|>", "")
            .replace("<|im_start|>", "")
            .replace("<|endoftext|>", "")
            .trim()
    }
}

interface SmallLlmResponder {
    val modelName: String
    val isNativeRuntime: Boolean get() = false

    suspend fun ensureLoaded(): Result<Unit> = Result.success(Unit)

    suspend fun respondToSearchAsync(
        query: String,
        options: SearchOptions,
        triggeredFromWidget: Boolean = false,
    ): SmallLlmResponse = respondToSearch(query, options, triggeredFromWidget)

    fun respondToSearch(
        query: String,
        options: SearchOptions,
        triggeredFromWidget: Boolean = false,
    ): SmallLlmResponse
}

/**
 * Deterministic fallback responder used in JVM unit tests and if the native GGUF model fails to load.
 */
class TinyLocalLlmResponder(
    override val modelName: String = "LocalMind-TinyLLM (fallback stub)",
) : SmallLlmResponder {

    override fun respondToSearch(
        query: String,
        options: SearchOptions,
        triggeredFromWidget: Boolean,
    ): SmallLlmResponse {
        val trimmed = query.trim()
        val scopeDescription = LocalMindPromptBuilder.scopeDescription(options.scope)
        val flags = options.activeFlagLabels()
        val flagSuffix = if (flags.isEmpty()) "" else " with ${flags.joinToString(", ")}"
        val sourcePrefix = if (triggeredFromWidget) "[Search Widget] " else ""

        val answer = if (trimmed.isEmpty()) {
            "${sourcePrefix}Small LLM ($modelName): Searching is not yet supported. " +
                "Type a file name or code/text snippet in the search line to preview $scopeDescription search."
        } else {
            "${sourcePrefix}Small LLM ($modelName): Searching for \"$trimmed\" in $scopeDescription$flagSuffix " +
                "is not yet supported in this version. Local indexing and VS Code-style search execution will be added next."
        }

        return SmallLlmResponse(
            id = "${System.nanoTime()}-${trimmed.hashCode()}",
            modelName = modelName,
            query = trimmed,
            options = options,
            answer = answer,
            searchSupported = false,
            triggeredFromWidget = triggeredFromWidget,
            ranNativeLlm = false,
        )
    }
}

data class SearchLaunchRequest(
    val query: String = "",
    val scope: SearchScope = SearchScope.ALL,
    val focusSearchLine: Boolean = false,
    val autoSubmit: Boolean = false,
    val fromWidget: Boolean = false,
)

object SearchDeepLink {
    const val SCHEME = "localmind"
    const val HOST = "search"

    fun buildUriString(
        scope: SearchScope = SearchScope.ALL,
        focus: Boolean = true,
        query: String = "",
        autoSubmit: Boolean = false,
        fromWidget: Boolean = true,
    ): String {
        val params = mutableListOf(
            "scope=${scope.wireValue}",
            "focus=$focus",
            "fromWidget=$fromWidget",
        )
        if (query.isNotBlank()) {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
            params += "q=$encoded"
        }
        if (autoSubmit) {
            params += "submit=true"
        }
        return "$SCHEME://$HOST?${params.joinToString("&")}"
    }

    fun parseUriString(uriString: String?): SearchLaunchRequest? {
        if (uriString.isNullOrBlank()) return null
        val schemeIdx = uriString.indexOf("://")
        if (schemeIdx <= 0) return null
        val scheme = uriString.substring(0, schemeIdx)
        if (!scheme.equals(SCHEME, ignoreCase = true)) return null

        val rest = uriString.substring(schemeIdx + 3)
        val qIdx = rest.indexOf('?')
        val host = (if (qIdx >= 0) rest.substring(0, qIdx) else rest).trimEnd('/')
        if (!host.equals(HOST, ignoreCase = true)) return null

        val queryPairs = if (qIdx >= 0) rest.substring(qIdx + 1) else ""
        val params = queryPairs.split('&')
            .filter { it.isNotBlank() }
            .associate { pair ->
                val eq = pair.indexOf('=')
                if (eq >= 0) {
                    val k = pair.substring(0, eq)
                    val v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8)
                    k to v
                } else {
                    pair to ""
                }
            }

        return SearchLaunchRequest(
            query = params["q"].orEmpty(),
            scope = SearchScope.fromWire(params["scope"]),
            focusSearchLine = params["focus"]?.toBooleanStrictOrNull() ?: true,
            autoSubmit = params["submit"]?.toBooleanStrictOrNull() ?: false,
            fromWidget = params["fromWidget"]?.toBooleanStrictOrNull() ?: true,
        )
    }
}
