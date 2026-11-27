package dev.localmind.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.localmind.app.llm.LlamaCppLlmResponder
import dev.localmind.app.search.ModelRuntimeState
import dev.localmind.app.search.SearchDeepLink
import dev.localmind.app.search.SearchScope
import dev.localmind.app.search.SearchViewModel
import dev.localmind.app.search.SmallLlmResponse
import dev.localmind.app.widget.LocalMindSearchWidgetProvider

private val GeminiColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B192E),
    secondary = Color(0xFFC58AF9),
    background = Color(0xFF131314),
    surface = Color(0xFF1E1F20),
    surfaceVariant = Color(0xFF282A2C),
    onBackground = Color(0xFFE3E3E3),
    onSurface = Color(0xFFE3E3E3),
)

class MainActivity : ComponentActivity() {
    private lateinit var searchViewModel: SearchViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        searchViewModel = SearchViewModel(
            responder = LlamaCppLlmResponder(applicationContext),
        )
        searchViewModel.initializeModel()
        handleIncomingIntent(intent)
        setContent {
            MaterialTheme(colorScheme = GeminiColorScheme) {
                LocalMindScreen(viewModel = searchViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uriString = intent?.dataString ?: return
        val launchRequest = SearchDeepLink.parseUriString(uriString) ?: return
        searchViewModel.applyLaunchRequest(launchRequest)
    }
}

@Composable
fun LocalMindScreen(viewModel: SearchViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val searchFocusRequester = remember { FocusRequester() }
    val userResponses = remember(state.responses) {
        state.responses.filter { it.id != "welcome" }
    }

    LaunchedEffect(state.focusRequestCounter) {
        if (state.focusRequestCounter > 0) {
            searchFocusRequester.requestFocus()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            // Minimal Google / Gemini-style top header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "✦",
                        fontSize = 18.sp,
                        color = Color(0xFF8AB4F8),
                    )
                    Text(
                        text = "LocalMind",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                // Subtle model & widget pill
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, Color(0xFF2E3134)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (state.modelRuntimeState) {
                                            ModelRuntimeState.READY -> Color(0xFF81C995)
                                            ModelRuntimeState.INITIALIZING -> Color(0xFF8AB4F8)
                                            ModelRuntimeState.FALLBACK -> Color(0xFFFDD663)
                                        },
                                    ),
                            )
                            Text(
                                text = when (state.modelRuntimeState) {
                                    ModelRuntimeState.READY -> "SmolLM2"
                                    ModelRuntimeState.INITIALIZING -> "Loading LLM…"
                                    ModelRuntimeState.FALLBACK -> "Local LLM"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFBDC1C6),
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, Color(0xFF2E3134)),
                        modifier = Modifier.clickable {
                            val pinned = LocalMindSearchWidgetProvider.requestPinWidget(context)
                            viewModel.setStatusNotice(
                                if (pinned) {
                                    "Home screen widget prompt opened."
                                } else {
                                    "Add \"LocalMind Search\" from your launcher Widgets menu."
                                },
                            )
                        },
                    ) {
                        Text(
                            text = "+ Widget",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Hero greeting when no active searches yet (Gemini-style calm center feel)
            if (userResponses.isEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Search your local files",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Medium,
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF8AB4F8), Color(0xFFC58AF9)),
                        ),
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Search inside files or file names like VS Code, powered on-device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9AA0A6),
                )
                Spacer(Modifier.height(16.dp))
            }

            // Clean Google / Gemini Pill Search Bar
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Color(0xFF35373A)),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "⌕",
                        fontSize = 20.sp,
                        color = Color(0xFF9AA0A6),
                    )
                    Spacer(Modifier.width(12.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (state.query.isEmpty()) {
                            Text(
                                text = when (state.options.scope) {
                                    SearchScope.ALL -> "Search file names & inside files…"
                                    SearchScope.FILE_NAMES -> "Search file names…"
                                    SearchScope.INSIDE_FILES -> "Search inside files…"
                                },
                                color = Color(0xFF80868B),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = state.query,
                            onValueChange = viewModel::updateQuery,
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { viewModel.submitSearchAsync() },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                        )
                    }

                    if (state.query.isNotEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { viewModel.updateQuery("") },
                        ) {
                            Text(
                                text = "✕",
                                color = Color(0xFF9AA0A6),
                                fontSize = 14.sp,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }

                    // Circular Gemini-style submit button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.isGenerating) Color(0xFF2F3237) else Color(0xFF8AB4F8),
                            )
                            .clickable(enabled = !state.isGenerating) {
                                viewModel.submitSearchAsync()
                            },
                    ) {
                        if (state.isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF8AB4F8),
                            )
                        } else {
                            Text(
                                text = "↑",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0B192E),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Single sleek horizontal row of search scope + VS Code option pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchScope.entries.forEach { scope ->
                    MinimalOptionPill(
                        label = scope.label,
                        selected = state.options.scope == scope,
                        onClick = { viewModel.selectScope(scope) },
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(Color(0xFF2E3134)),
                )

                MinimalOptionPill(
                    label = "Aa",
                    selected = state.options.caseSensitive,
                    monospace = true,
                    onClick = viewModel::toggleCaseSensitive,
                )
                MinimalOptionPill(
                    label = "ab",
                    selected = state.options.wholeWord,
                    monospace = true,
                    onClick = viewModel::toggleWholeWord,
                )
                MinimalOptionPill(
                    label = ".*",
                    selected = state.options.useRegex,
                    monospace = true,
                    onClick = viewModel::toggleRegex,
                )
            }

            state.statusNotice?.let { notice ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = notice,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Clean prompt suggestions when empty, otherwise Gemini-style response thread
            if (userResponses.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SuggestionPill("Find *.kt files") {
                        viewModel.runWidgetPreset("*.kt", SearchScope.FILE_NAMES)
                    }
                    SuggestionPill("Search \"TODO\" inside files") {
                        viewModel.runWidgetPreset("TODO", SearchScope.INSIDE_FILES)
                    }
                }
                Spacer(Modifier.weight(1f))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Responses",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF9AA0A6),
                    )
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = viewModel::clearResponses)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(userResponses, key = { it.id }) { item ->
                        GeminiResponseItem(item)
                    }
                }
            }

            Text(
                text = "LocalMind · Offline-first on-device LLM",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF5F6368),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun MinimalOptionPill(
    label: String,
    selected: Boolean,
    monospace: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) Color(0xFF283548) else Color(0xFF1E1F20),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) Color(0xFF8AB4F8) else Color(0xFF2E3134),
        ),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) Color(0xFFD2E3FC) else Color(0xFFBDC1C6),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SuggestionPill(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF1E1F20),
        border = BorderStroke(1.dp, Color(0xFF2E3134)),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFBDC1C6),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun GeminiResponseItem(response: SmallLlmResponse) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (response.query.isNotEmpty()) {
            val flags = response.options.activeFlagLabels()
            val meta = buildString {
                append(response.options.scope.label)
                if (flags.isNotEmpty()) append(" · ${flags.joinToString(" · ")}")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF282A2C),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                        Text(
                            text = response.query,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF9AA0A6),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF202A3A)),
            ) {
                Text(
                    text = "✦",
                    fontSize = 13.sp,
                    color = Color(0xFF8AB4F8),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val modelLabel = buildString {
                    append(response.modelName)
                    if (response.inferenceMillis != null) {
                        append(" · ${response.inferenceMillis} ms")
                    }
                }
                Text(
                    text = modelLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8AB4F8),
                )
                Text(
                    text = response.answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF131314)
@Composable
private fun LocalMindScreenPreview() {
    val previewVm = remember {
        SearchViewModel().apply {
            updateQuery("MainActivity.kt")
            selectScope(SearchScope.FILE_NAMES)
            submitSearch()
        }
    }
    MaterialTheme(colorScheme = GeminiColorScheme) {
        LocalMindScreen(viewModel = previewVm)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF131314)
@Composable
private fun LocalMindEmptyPreview() {
    val previewVm = remember { SearchViewModel() }
    MaterialTheme(colorScheme = GeminiColorScheme) {
        LocalMindScreen(viewModel = previewVm)
    }
}
