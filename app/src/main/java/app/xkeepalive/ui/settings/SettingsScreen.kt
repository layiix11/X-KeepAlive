package app.xkeepalive.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.xkeepalive.BuildConfig
import app.xkeepalive.data.SessionUi
import app.xkeepalive.data.UserSettings
import app.xkeepalive.ui.KeyValue
import app.xkeepalive.ui.SectionCard
import app.xkeepalive.ui.theme.Mist

@Composable
fun SettingsScreen(
    settings: UserSettings,
    session: SessionUi,
    onRestore: (Boolean) -> Unit,
    onPoll: (Long) -> Unit,
    onUsage: () -> Unit,
    onOwnBattery: () -> Unit,
    onGamePower: () -> Unit,
    onProtocol: () -> Unit,
) {
    var poll by remember(settings.pollIntervalMs) { mutableFloatStateOf(settings.pollIntervalMs.toFloat()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        SectionCard {
            Text("Permissions", style = MaterialTheme.typography.titleMedium)
            KeyValue("Usage access", if (session.permissions.usageAccess) "granted" else "missing")
            KeyValue("Notifications", if (session.permissions.notifications) "granted" else "missing")
            KeyValue(
                "X-KeepAlive battery",
                if (session.permissions.ownBatteryUnrestricted) "unrestricted" else "optimized",
            )
            Button(onClick = onUsage) { Text("Usage access") }
            TextButton(onClick = onOwnBattery) { Text("X-KeepAlive battery") }
            TextButton(onClick = onGamePower) { Text("Selected game battery") }
        }
        SectionCard {
            Text("After reboot", style = MaterialTheme.typography.titleMedium)
            Text(
                "Restores only X-KeepAlive monitoring. Shizuku and wireless debugging stay off until you start them again.",
                style = MaterialTheme.typography.bodySmall,
            )
            Switch(checked = settings.restoreOnBoot, onCheckedChange = onRestore)
        }
        SectionCard {
            Text("Read interval: ${(poll / 1000f).let { "%.1f".format(it) }} s", style = MaterialTheme.typography.titleMedium)
            Text("With the screen off, the interval does not go below 8 seconds.", color = Mist, style = MaterialTheme.typography.bodySmall)
            Slider(
                value = poll,
                onValueChange = { poll = it },
                onValueChangeFinished = { onPoll(poll.toLong()) },
                valueRange = 1500f..5000f,
                steps = 6,
            )
        }
        Button(onClick = onProtocol, modifier = Modifier.fillMaxWidth()) {
            Text("On-device verification")
        }
        Text("X-KeepAlive ${BuildConfig.VERSION_NAME}", color = Mist, style = MaterialTheme.typography.bodySmall)
    }
}
