package com.kitsumed.shizucallrecorder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.n2k02.updater.N2K02UpdateScreen

/**
 * Host configuration for the shared updater. The status card, actions, progress,
 * retry and install-permission UI are owned by n2k02updater.
 */
@Composable
fun UpdateCard(modifier: Modifier = Modifier) {
    val experimental by LiveUpdater.experimentalEnabled.collectAsState()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ListItem(
            headlineContent = { Text("Experimental updates") },
            supportingContent = {
                Text("Offer signed APKs from successful pull request builds. Test builds may have bugs.")
            },
            trailingContent = {
                Switch(
                    checked = experimental,
                    onCheckedChange = LiveUpdater::setExperimentalEnabled,
                )
            },
            modifier = Modifier.clickable {
                LiveUpdater.setExperimentalEnabled(!experimental)
            },
        )
        HorizontalDivider()
        N2K02UpdateScreen(
            controller = LiveUpdater.controller,
            installGuard = LiveUpdater.installGuard,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** Version, build and scrcpy server details. */
@Composable
fun AppDetailsDialog(appName: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Application: $appName")
                Text("App version: ${LiveUpdater.installedVersionName}")
                Text("Build number: ${BuildConfig.VERSION_CODE}")
                Text("scrcpy server: ${ScrcpyConfig.SCRCPY_VERSION}")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
