package dev.localmind.app

import dev.localmind.app.search.LocalMindPromptBuilder
import dev.localmind.app.search.SearchDeepLink
import dev.localmind.app.search.SearchOptions
import dev.localmind.app.search.SearchScope
import dev.localmind.app.search.SearchViewModel
import dev.localmind.app.search.TinyLocalLlmResponder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUnitTests {

    @Test
    fun tinyLlmReportsSearchNotYetSupportedForInsideFiles() {
        val responder = TinyLocalLlmResponder()
        val response = responder.respondToSearch(
            query = "fn main",
            options = SearchOptions(
                scope = SearchScope.INSIDE_FILES,
                caseSensitive = true,
                wholeWord = false,
                useRegex = false,
            ),
        )

        assertFalse(response.searchSupported)
        assertTrue(response.answer.contains("not yet supported", ignoreCase = true))
        assertTrue(response.answer.contains("inside files", ignoreCase = true))
        assertTrue(response.answer.contains("Match Case (Aa)"))
        assertEquals("fn main", response.query)
    }

    @Test
    fun tinyLlmReportsSearchNotYetSupportedForFileNames() {
        val responder = TinyLocalLlmResponder()
        val response = responder.respondToSearch(
            query = "ContentView.swift",
            options = SearchOptions(scope = SearchScope.FILE_NAMES),
            triggeredFromWidget = true,
        )

        assertFalse(response.searchSupported)
        assertTrue(response.triggeredFromWidget)
        assertTrue(response.answer.contains("[Search Widget]"))
        assertTrue(response.answer.contains("file names", ignoreCase = true))
        assertTrue(response.answer.contains("not yet supported", ignoreCase = true))
    }

    @Test
    fun promptBuilderFormatsChatMlPromptAndStripsStopTokens() {
        val prompt = LocalMindPromptBuilder.buildChatMlPrompt(
            query = "MainActivity.kt",
            options = SearchOptions(scope = SearchScope.FILE_NAMES, caseSensitive = true),
            triggeredFromWidget = false,
        )
        assertTrue(prompt.contains("<|im_start|>system"))
        assertTrue(prompt.contains("not yet supported"))
        assertTrue(prompt.contains("MainActivity.kt"))
        assertTrue(prompt.contains("file names"))
        assertTrue(prompt.endsWith("<|im_start|>assistant\n"))

        val cleaned = LocalMindPromptBuilder.cleanAssistantOutput("  Searching is not yet supported.<|im_end|>  ")
        assertEquals("Searching is not yet supported.", cleaned)
    }

    @Test
    fun deepLinkRoundTripPreservesScopeAndQuery() {
        val uri = SearchDeepLink.buildUriString(
            scope = SearchScope.FILE_NAMES,
            focus = true,
            query = "notes 2026.md",
            autoSubmit = true,
            fromWidget = true,
        )
        val parsed = SearchDeepLink.parseUriString(uri)
        assertNotNull(parsed)
        assertEquals(SearchScope.FILE_NAMES, parsed!!.scope)
        assertEquals("notes 2026.md", parsed.query)
        assertTrue(parsed.focusSearchLine)
        assertTrue(parsed.autoSubmit)
        assertTrue(parsed.fromWidget)
    }

    @Test
    fun deepLinkRejectsUnrelatedUri() {
        assertNull(SearchDeepLink.parseUriString("https://example.com/search"))
        assertNull(SearchDeepLink.parseUriString(""))
    }

    @Test
    fun viewModelWidgetPresetPopulatesQueryAndPrependsSmallLlmAnswer() {
        val vm = SearchViewModel()
        vm.toggleCaseSensitive()
        vm.toggleRegex()
        vm.runWidgetPreset("TODO", SearchScope.INSIDE_FILES)

        val state = vm.uiState.value
        assertEquals("TODO", state.query)
        assertEquals(SearchScope.INSIDE_FILES, state.options.scope)
        assertTrue(state.options.caseSensitive)
        assertTrue(state.options.useRegex)
        assertEquals(1, state.focusRequestCounter)

        val latest = state.responses.first()
        assertFalse(latest.searchSupported)
        assertTrue(latest.triggeredFromWidget)
        assertTrue(latest.answer.contains("not yet supported", ignoreCase = true))
    }

    @Test
    fun viewModelAppliesDeepLinkLaunchRequest() {
        val vm = SearchViewModel()
        val request = SearchDeepLink.parseUriString(
            "localmind://search?scope=contents&focus=true&fromWidget=true",
        )!!
        vm.applyLaunchRequest(request)

        val state = vm.uiState.value
        assertEquals(SearchScope.INSIDE_FILES, state.options.scope)
        assertEquals(1, state.focusRequestCounter)
        assertTrue(state.statusNotice?.contains("Home Search Widget") == true)
    }
}
