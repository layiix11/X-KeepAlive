package app.xkeepalive.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.xkeepalive.apps.InstalledApp
import app.xkeepalive.core.Blocker
import app.xkeepalive.core.Phase
import app.xkeepalive.core.PhaseLabels
import app.xkeepalive.core.ProcessOutcome
import app.xkeepalive.data.EventEntry
import app.xkeepalive.data.SessionUi
import app.xkeepalive.data.UserSettings
import app.xkeepalive.ui.KeyValue
import app.xkeepalive.ui.LogoMark
import app.xkeepalive.ui.SectionCard
import app.xkeepalive.ui.StatusChip
import app.xkeepalive.ui.StatusDot
import app.xkeepalive.ui.eventTitle
import app.xkeepalive.ui.formatEventTime
import app.xkeepalive.ui.theme.Accent
import app.xkeepalive.ui.theme.Danger
import app.xkeepalive.ui.theme.Mist
import app.xkeepalive.ui.theme.Ok
import app.xkeepalive.ui.theme.Warning

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: UserSettings,
    session: SessionUi,
    events: List<EventEntry>,
    apps: List<InstalledApp>,
    appsLoading: Boolean,
    onToggle: (Boolean) -> Unit,
    onSelect: (InstalledApp) -> Unit,
    onRefreshApps: () -> Unit,
    onRequestShizuku: () -> Unit,
    onOpenShizuku: () -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val headline = when (session.blocker) {
        Blocker.PERMISSIONS -> "Permissions missing"
        Blocker.NO_GAME_SELECTED -> "No game selected"
        Blocker.NONE -> PhaseLabels.english(session.phase)
    }
    val accent = when {
        session.blocker != Blocker.NONE -> Warning
        session.phase == Phase.BACKGROUND_MANAGEMENT_ACTIVE -> Ok
        session.phase == Phase.GAME_FOREGROUND || session.phase == Phase.GAME_RETURNED -> Accent
        session.phase == Phase.INACTIVE -> Mist
        else -> Warning
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LogoMark()
            Column {
                Text("X-KeepAlive", style = MaterialTheme.typography.headlineMedium)
                Text("Background app manager", color = Mist, style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionCard {
            Text("Selected game", style = MaterialTheme.typography.titleMedium)
            if (settings.selectedPackage.isBlank()) {
                Text("Nothing selected. Choose an installed game.", color = Mist)
            } else {
                Text(settings.selectedLabel.ifBlank { settings.selectedPackage }, fontWeight = FontWeight.SemiBold)
                Text(settings.selectedPackage, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = {
                pickerOpen = true
                onRefreshApps()
            }) { Text("Choose game") }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (settings.automationEnabled) "Monitoring on" else "Monitoring off", style = MaterialTheme.typography.titleMedium)
                    Text("Turn it on once. Closing this screen does not stop it.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = settings.automationEnabled, onCheckedChange = onToggle)
            }
            Button(
                onClick = { onToggle(!settings.automationEnabled) },
                modifier = Modifier.fillMaxWidth(),
                colors = if (settings.automationEnabled) {
                    ButtonDefaults.buttonColors(containerColor = Danger, contentColor = MaterialTheme.colorScheme.onPrimary)
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(if (settings.automationEnabled) "Stop service" else "Start service")
            }
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(accent, pulsing = session.serviceRunning)
                AnimatedContent(targetState = headline, label = "phase") { value ->
                    Text(value, style = MaterialTheme.typography.titleLarge, color = accent)
                }
            }
            Text(
                "A running service does not mean the game stays in memory.",
                style = MaterialTheme.typography.bodySmall,
            )
            session.limitNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(
                    if (session.serviceRunning) "Service running" else "Service stopped",
                    if (session.serviceRunning) Ok else Mist,
                )
                if (!session.shizuku.ready) StatusChip("ADB connection unavailable", Danger)
                if (!session.permissions.usageAccess) StatusChip("Permissions missing", Danger)
                if (session.outcome != ProcessOutcome.NOT_CHECKED) {
                    StatusChip(outcomeLabel(session.outcome), accent)
                }
            }
            if (session.observedPids.isNotEmpty()) {
                KeyValue("Current PID", session.observedPids.sorted().joinToString(", "))
            } else if (session.serviceRunning) {
                KeyValue("Current PID", "reading")
            }
            session.pidChange?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            session.foregroundPackage?.let { KeyValue("In foreground", it) }
        }

        SectionCard {
            Text("Shizuku", style = MaterialTheme.typography.titleMedium)
            Text(session.shizuku.message, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (session.shizuku.binderAlive && !session.shizuku.permissionGranted) {
                    Button(onClick = onRequestShizuku) { Text("Authorize") }
                }
                TextButton(onClick = onOpenShizuku) { Text("Open Shizuku") }
            }
        }

        session.policy?.let { report ->
            SectionCard {
                Text(
                    if (report.anyVerified) "Policy verified" else "Policy not verified",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (report.anyVerified) Ok else Warning,
                )
                Text("${report.mode} · ${report.packageName}", style = MaterialTheme.typography.bodySmall)
                report.operations.take(6).forEach { op ->
                    Text(
                        "${if (op.verified) "OK" else "NO"}  ${op.name}",
                        color = if (op.verified) Ok else Warning,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(op.detail, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (events.isNotEmpty()) {
            SectionCard {
                Text("Recent events", style = MaterialTheme.typography.titleMedium)
                events.takeLast(3).asReversed().forEach { entry ->
                    Text(formatEventTime(entry.timeMs) + "  " + eventTitle(entry.kind), fontWeight = FontWeight.SemiBold)
                    Text(entry.message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (pickerOpen) {
        AppPicker(
            apps = apps,
            loading = appsLoading,
            onDismiss = { pickerOpen = false },
            onSelect = {
                onSelect(it)
                pickerOpen = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AppPicker(
    apps: List<InstalledApp>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (InstalledApp) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var gamesOnly by remember { mutableStateOf(true) }
    val filtered = apps.filter { app ->
        (!gamesOnly || app.isGame) &&
            (query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true))
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Installed apps", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search") },
            )
            FilterChip(
                selected = gamesOnly,
                onClick = { gamesOnly = !gamesOnly },
                label = { Text(if (gamesOnly) "Games only" else "All apps") },
            )
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            filtered.take(80).forEach { app ->
                TextButton(onClick = { onSelect(app) }, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(app.label + if (app.isGame) "  · game" else "")
                        Text(app.packageName, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (!loading && filtered.isEmpty()) {
                Text("No results. Turn off Games only: not every game declares that category.", color = Mist)
            }
        }
    }
}

private fun outcomeLabel(outcome: ProcessOutcome): String = when (outcome) {
    ProcessOutcome.NOT_CHECKED -> "Process not checked"
    ProcessOutcome.UNKNOWN_NO_PRIVILEGE -> "PID not readable"
    ProcessOutcome.STILL_IN_MEMORY -> "Process still in memory"
    ProcessOutcome.TERMINATED -> "Process terminated by Android"
    ProcessOutcome.RESTARTED_NEW_PID -> "Process recreated"
    ProcessOutcome.NOT_RUNNING -> "Process not running"
}
