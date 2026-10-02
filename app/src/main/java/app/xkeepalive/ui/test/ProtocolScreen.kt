package app.xkeepalive.ui.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.xkeepalive.data.EventEntry
import app.xkeepalive.ui.SectionCard
import app.xkeepalive.ui.formatEventTime
import app.xkeepalive.ui.theme.Mist
import app.xkeepalive.viewmodel.TestRecord

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProtocolScreen(
    tests: List<EventEntry>,
    onSave: (TestRecord) -> Unit,
) {
    var wait by remember { mutableStateOf("1 minute") }
    var foreground by remember { mutableStateOf("Home") }
    var process by remember { mutableStateOf("Not checked") }
    var service by remember { mutableStateOf("Still running") }
    var shizuku by remember { mutableStateOf("Not used") }
    var notes by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Verification", style = MaterialTheme.typography.headlineMedium)
        Text(
            "You record these results on the phone after playing. The app does not invent an outcome.",
            color = Mist,
        )
        SectionCard {
            Text("Sequence", style = MaterialTheme.typography.titleMedium)
            Text(
                "Open the game, start X-KeepAlive, go Home, open WhatsApp or Chrome, wait, then return to the game. Note whether the process is still in memory and whether the service or Shizuku stopped. Repeat at 1, 5, and 10 minutes, and with different foreground apps. Repeat after closing Shizuku and after a reboot.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Choice("Wait", listOf("1 minute", "5 minutes", "10 minutes"), wait) { wait = it }
        Choice("Foreground app", listOf("Home", "WhatsApp", "Chrome", "Other"), foreground) { foreground = it }
        Choice(
            "Game process",
            listOf("Still in memory", "Terminated by Android", "Not readable"),
            process,
        ) { process = it }
        Choice("X-KeepAlive service", listOf("Still running", "Terminated by Android"), service) { service = it }
        Choice("Shizuku", listOf("Available", "Stopped", "Not used"), shizuku) { shizuku = it }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it.take(300) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Notes") },
            minLines = 2,
        )
        Button(
            onClick = {
                onSave(TestRecord(wait, foreground, process, service, shizuku, notes.trim()))
                notes = ""
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save this result") }
        if (tests.isNotEmpty()) {
            Text("Saved results", style = MaterialTheme.typography.titleMedium)
            tests.asReversed().take(20).forEach { entry ->
                SectionCard {
                    Text(formatEventTime(entry.timeMs), style = MaterialTheme.typography.bodySmall)
                    Text(entry.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choice(title: String, values: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(value) })
            }
        }
    }
}
