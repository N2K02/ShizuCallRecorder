package com.kitsumed.shizucallrecorder

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.kitsumed.shizucallrecorder.integrations.scrcpy.ScrcpyConfig

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * Shows when an update is available, downloading or ready to install; hidden otherwise.
 * Put it near the top of the main screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateCard(modifier: Modifier = Modifier) {
    val state by AppUpdater.state.collectAsState()
    var diagnosticsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state) { if (state !is AppUpdater.State.Failed) diagnosticsVisible = false }
    val activity = LocalContext.current.activity()
    // "You're up to date" (after a manual check) shows for a few seconds, then goes away.
    LaunchedEffect(state) {
        if (state is AppUpdater.State.UpToDate) {
            delay(5_000)
            if (AppUpdater.state.value is AppUpdater.State.UpToDate) AppUpdater.dismiss()
        }
    }
    val visible = state is AppUpdater.State.UpToDate || state is AppUpdater.State.Checking || state is AppUpdater.State.Available || state is AppUpdater.State.Downloading ||
        state is AppUpdater.State.NeedsPermission || state is AppUpdater.State.Ready ||
        state is AppUpdater.State.Failed
    AnimatedVisibility(visible = visible, modifier = modifier) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (state is AppUpdater.State.Failed) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (val s = state) {
                    is AppUpdater.State.Checking -> {
                        Title("Checking for updates…")
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Contacting GitHub for the latest release.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is AppUpdater.State.UpToDate -> {
                        Title("You're up to date")
                        Text(
                            "Version ${AppUpdater.installedVersionName} is the latest.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    is AppUpdater.State.Available -> {
                        Title("Update available: ${s.release.versionName}")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { diagnosticsVisible = false; AppUpdater.update(s.release) }) { Text("Update now") }
                            TextButton(onClick = { AppUpdater.dismiss() }) { Text("Later") }
                            TextButton(onClick = { AppUpdater.skip(s.release) }) { Text("Skip this version") }
                        }
                    }
                    is AppUpdater.State.Downloading -> {
                        Title("Downloading ${s.release.versionName}…")
                        val p = s.progress
                        if (p == null) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                            Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { AppUpdater.dismiss() }) { Text("Cancel") }
                    }
                    is AppUpdater.State.NeedsPermission -> {
                        Title("One more step")
                        Text(
                            "Android needs your OK to let this app install its own updates. " +
                                "Turn on \"Allow from this source\", then come back.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { activity?.let { AppUpdater.openInstallPermission(it) } }) { Text("Open settings") }
                            TextButton(onClick = { AppUpdater.dismiss() }) { Text("Later") }
                        }
                    }
                    is AppUpdater.State.Ready -> {
                        Title("${s.release.versionName} is ready to install")
                        Text(
                            "Android's installer opens. If Play Protect asks, you can scan the app or choose to install anyway.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { AppUpdater.install(s.release) }) { Text("Install") }
                            TextButton(onClick = { AppUpdater.dismiss() }) { Text("Later") }
                        }
                    }
                    is AppUpdater.State.Failed -> {
                        Title("Update problem")
                        Text(s.message, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { diagnosticsVisible = !diagnosticsVisible }) {
                            Text(if (diagnosticsVisible) "Hide details" else "Show details")
                        }
                        if (diagnosticsVisible) {
                            Text(
                                s.diagnostics.ifBlank { "No additional diagnostic details were captured." },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(onClick = {
                                val clipboard = activity?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Update diagnostics", s.diagnostics))
                            }) { Text("Copy diagnostics") }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            s.release?.let { r -> Button(onClick = { diagnosticsVisible = false; AppUpdater.update(r) }) { Text("Try again") } }
                            TextButton(onClick = { activity?.let { AppUpdater.openReleasePage(it, s.release) } }) {
                                Text("Open release page")
                            }
                            TextButton(onClick = { AppUpdater.dismiss() }) { Text("Close") }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

/** Version, build and scrcpy server of this build (it was shown by tapping the old footer). */
@Composable
fun AppDetailsDialog(appName: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Application: $appName")
                Text("App version: ${AppUpdater.installedVersionName}")
                Text("Build number: ${BuildConfig.VERSION_CODE}")
                Text("scrcpy server: ${ScrcpyConfig.SCRCPY_VERSION}")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * Dedicated update page. The update card remains visible at the top while checking,
 * downloading, waiting for install permission, or reporting an update error.
 */
@Composable
fun UpdatesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val experimentalEnabled by LiveUpdater.experimentalEnabled.collectAsState()
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back to settings") }
        Text("About & updates", style = MaterialTheme.typography.headlineMedium)
        androidx.compose.material3.ListItem(
            headlineContent = { Text("ShizuCallRecorder") },
            supportingContent = { Text("Version " + AppUpdater.installedVersionName) },
        )
        androidx.compose.material3.ListItem(
            headlineContent = { Text("Experimental updates") },
            supportingContent = { Text("Offer signed APKs from successful pull request builds. Test builds may have bugs.") },
            trailingContent = {
                androidx.compose.material3.Switch(
                    checked = experimentalEnabled,
                    onCheckedChange = LiveUpdater::setExperimentalEnabled,
                )
            },
        )
        androidx.compose.material3.HorizontalDivider()
        dev.n2k02.updater.N2K02UpdateScreen(
            controller = LiveUpdater.controller,
            installGuard = LiveUpdater.installGuard,
        )
    }
}
