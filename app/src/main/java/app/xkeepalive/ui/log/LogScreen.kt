package app.xkeepalive.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.xkeepalive.data.EventEntry
import app.xkeepalive.ui.SectionCard
import app.xkeepalive.ui.eventTitle
import app.xkeepalive.ui.formatEventTime
import app.xkeepalive.ui.theme.Mist

@Composable
fun LogScreen(events: List<EventEntry>, onClear: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val visible = events.asReversed().filter { entry ->
        query.isBlank() ||
            entry.kind.contains(query, true) ||
            entry.message.contains(query, true) ||
            eventTitle(entry.kind).contains(query, true)
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Log", style = MaterialTheme.typography.headlineMedium)
        Text("Stored only on this phone. A command is marked done only after its output was read.", color = Mist)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Filter") },
        )
        TextButton(onClick = onClear) { Text("Clear log") }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visible, key = { "${it.timeMs}-${it.kind}-${it.message.hashCode()}" }) { entry ->
                SectionCard {
                    Text(formatEventTime(entry.timeMs) + "  ·  " + eventTitle(entry.kind), fontWeight = FontWeight.SemiBold)
                    Text(entry.message, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (visible.isEmpty()) {
                item { Text("No events.", color = Mist) }
            }
        }
    }
}
