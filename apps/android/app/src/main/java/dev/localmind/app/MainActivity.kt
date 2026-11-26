package dev.localmind.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { LocalMindScreen() } }
}

@Composable
fun LocalMindScreen() {
    var query by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(false) }
    var messages by remember { mutableStateOf(listOf("LocalMind is on-device. Connect a local model runtime to enable chat.")) }
    Scaffold(topBar = { TopAppBar(title = { Text("LocalMind") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !searchMode, onClick = { searchMode = false }, label = { Text("Chat") })
                FilterChip(selected = searchMode, onClick = { searchMode = true }, label = { Text("Search files") })
            }
            if (searchMode) {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search device files") })
                Button(onClick = { messages = listOf("Search core is scaffolded. Grant platform file access and connect Rust FFI to search the index.") }) { Text("Search locally") }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(messages) { Card(shape = RoundedCornerShape(16.dp)) { Text(it, Modifier.padding(14.dp)) } } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Ask your local AI…") })
                    Button(onClick = { if (input.isNotBlank()) { messages = messages + "You: $input" + "AI: Model runtime is not connected yet."; input = "" } }) { Text("Send") }
                }
            }
            Text("Private · Offline-first", style = MaterialTheme.typography.labelSmall)
        }
    }
}
