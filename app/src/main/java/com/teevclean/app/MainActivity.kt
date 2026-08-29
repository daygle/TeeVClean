package com.teevclean.app

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val viewModel: TeeVViewModel by viewModels()

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TeeVCleanApp(viewModel, onPickFolder = { folderPicker.launch(null) })
        }
    }
}

@Composable
fun TeeVCleanApp(viewModel: TeeVViewModel, onPickFolder: () -> Unit) {
    var showCleanup by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showCleanup) {
        CleanupDialog(viewModel.cacheSize, onDismiss = { showCleanup = false }) {
            viewModel.clearCache { showCleanup = false }
        }
    }
    if (showSchedule) {
        ScheduleDialog(viewModel.scheduleEnabled, onDismiss = { showSchedule = false }) { enabled ->
            viewModel.toggleSchedule(enabled)
            showSchedule = false
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Row(Modifier.fillMaxSize().padding(44.dp)) {
                Sidebar(viewModel.currentScreen) { viewModel.currentScreen = it }
                Spacer(Modifier.width(38.dp))
                when (viewModel.currentScreen) {
                    Screen.OVERVIEW -> Dashboard(viewModel.storageSummary, viewModel.apps, viewModel.largeFiles) { showCleanup = true }
                    Screen.CLEAN -> CleanupScreen(viewModel.cacheSize, onReview = { showCleanup = true }, onSchedule = { showSchedule = true }, scheduleEnabled = viewModel.scheduleEnabled)
                    Screen.LARGE -> LargeFilesScreen(viewModel.largeFiles, onPickFolder)
                    Screen.APPS -> AppReviewScreen(viewModel.apps) { viewModel.openAppInfo(it) }
                    Screen.HEALTH -> HealthScreen(context, viewModel.storageSummary)
                }
            }
        }
    }
}

@Composable
private fun Dashboard(storage: StorageSummary, apps: List<AppSummary>, files: List<FileSummary>, onClean: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Text("Good evening, ready to tidy up?", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text("A calmer, cleaner TV starts here.", color = Muted, fontSize = 16.sp)
            Spacer(Modifier.height(28.dp))
            StorageCard(storage, onClean)
        }
        item { Text("Safe tools for a healthier TV", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
        item { FeatureCard("Large Files", "Review downloads and media before deleting", "Files", if (files.isEmpty()) "Scan" else formatBytes(files.sumOf { it.size })) }
        item { FeatureCard("App Review", "${apps.size} apps; review size and open Android app info", "Apps", "Guided") }
        item { FeatureCard("Device Health", "Storage pressure, network, uptime and system details", "Health", "Check") }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF253020)).padding(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✦", color = Lime, fontSize = 28.sp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Your privacy comes first", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("No silent deletion and no fake RAM boosts. You approve every cleanup action.", color = Color(0xFFC4D1C2), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun CleanupScreen(cacheSize: Long, onReview: () -> Unit, onSchedule: () -> Unit, scheduleEnabled: Boolean) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Safe Cleanup", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Scan temporary files and review every item before removal.", color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))
        ResultRow("This App's Cache", "Safe to remove; app data and user files stay intact", formatBytes(cacheSize), true)
        ResultRow("Other App Caches", "Android requires each app's own info page.", "Guided", false)
        ResultRow("Downloads and Media", "Select files in the large-file review.", "User choice", false)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onReview) { Text("Review Cleanup Plan") }
        TextButton(onClick = onSchedule) {
            Text(if (scheduleEnabled) "Scheduled Cleanup: Weekly" else "Schedule Weekly Cleanup")
        }
    }
}

@Composable
private fun LargeFilesScreen(files: List<FileSummary>, onPickFolder: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Large Files", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Review files in shared storage. Unknown files are never deleted automatically.", color = Muted, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Over 500 MB", "Older Than 30 Days", "Downloads").forEach {
                Text(it, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Lime).padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }
        if (files.isEmpty()) {
            Text("No large files were found in the accessible Downloads and media folders.", color = Muted, fontSize = 15.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(files.take(20)) { file ->
                    ResultRow(file.name, file.path, formatBytes(file.size), false)
                }
            }
        }
        Button(onClick = onPickFolder) { Text("Choose Folder to Scan") }
        Text("Folder access uses Android's Storage Access Framework. You stay in control of the location.", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun AppReviewScreen(apps: List<AppSummary>, onOpenInfo: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("App Review", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Apps are sorted by installed APK size. Last-used data may be unavailable without usage access.", color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(20.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(apps.take(15)) { app ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("${app.packageName} • ${lastUsedText(app.lastUsed)}", color = Muted, fontSize = 12.sp)
                    }
                    Text(formatBytes(app.size), color = Lime, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(16.dp))
                    TextButton(onClick = { onOpenInfo(app.packageName) }) { Text("App Info") }
                }
            }
        }
    }
}

@Composable
private fun HealthScreen(context: Context, storage: StorageSummary) {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val network = manager?.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Device Health", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Practical diagnostics, not RAM booster claims.", color = Muted, fontSize = 16.sp)
        ResultRow("Storage Pressure", if (storage.fraction > .85) "Low space — review large files." else "Healthy free-space margin.", "${(storage.fraction * 100).toInt()}% used", storage.fraction <= .85f)
        ResultRow("Network", if (network) "Internet connection detected." else "No active internet connection.", if (network) "Connected" else "Offline", network)
        ResultRow("System Uptime", "Time since the device last booted.", formatDuration(SystemClock.elapsedRealtime()), true)
        ResultRow("Android Version", "${Build.MANUFACTURER} ${Build.MODEL}", "Android ${Build.VERSION.RELEASE}", true)
        Text("Thermal readings vary by TV manufacturer and are not exposed on every device.", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun ScheduleDialog(enabled: Boolean, onDismiss: () -> Unit, onSave: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scheduled Cleanup") },
        text = { Text(if (enabled) "A weekly cleanup will clear only TeeVClean's own cache. Other apps, downloads, photos, and personal files are never touched." else "Schedule a weekly cleanup of TeeVClean's own cache. This does not delete user files or clear other apps' caches.") },
        confirmButton = { Button(onClick = { onSave(!enabled) }) { Text(if (enabled) "Turn Off" else "Enable") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CleanupDialog(cacheSize: Long, onDismiss: () -> Unit, onCleaned: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Safe Cleanup") },
        text = { Text("Only TeeVClean's own temporary cache will be removed. Photos, downloads, app data, and other apps remain untouched. Estimated space: ${formatBytes(cacheSize)}.") },
        confirmButton = { Button(onClick = onCleaned) { Text("Remove Cache") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun lastUsedText(timestamp: Long): String =
    if (timestamp == 0L) "Last used unavailable" else "Last used ${java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(timestamp)}"

private fun formatDuration(milliseconds: Long): String {
    val hours = milliseconds.coerceAtLeast(0L) / 3_600_000
    val days = hours / 24
    return if (days > 0) "$days days, ${hours % 24} hours" else "$hours hours"
}
