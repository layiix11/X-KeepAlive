package app.xkeepalive.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.xkeepalive.ui.SectionCard
import app.xkeepalive.ui.guideSections

@Composable
fun GuideScreen(
    onDeveloperOptions: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRequestShizuku: () -> Unit,
    onUsageAccess: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Setup", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Shizuku is optional. Without it, monitoring stays on, but no privileged command runs.",
            style = MaterialTheme.typography.bodyMedium,
        )
        RowActions(onDeveloperOptions, onOpenShizuku, onRequestShizuku, onUsageAccess)
        guideSections.forEach { section ->
            SectionCard {
                Text(section.title, style = MaterialTheme.typography.titleMedium)
                Text(section.body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun RowActions(
    onDeveloperOptions: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRequestShizuku: () -> Unit,
    onUsageAccess: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = onDeveloperOptions) { Text("Open developer options") }
        TextButton(onClick = onOpenShizuku) { Text("Open Shizuku") }
        TextButton(onClick = onRequestShizuku) { Text("Authorize Shizuku") }
        TextButton(onClick = onUsageAccess) { Text("Usage access") }
    }
}
