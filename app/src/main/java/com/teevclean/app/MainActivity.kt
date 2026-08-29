package com.teevclean.app

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.app.AppOpsManager
import android.content.pm.PackageManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
            Column {
                if (viewModel.isRefreshing) {
                    LoadingOverlay()
                }
                Row(Modifier.fillMaxSize().padding(44.dp)) {
                    Sidebar(viewModel.currentScreen) { viewModel.currentScreen = it }
                    Spacer(Modifier.width(38.dp))
                    Box(Modifier.weight(1f)) {
                        when (viewModel.currentScreen) {
                            Screen.OVERVIEW -> Dashboard(viewModel.storageSummary, viewModel.apps, viewModel.largeFiles) { showCleanup = true }
                            Screen.CLEAN -> CleanupScreen(viewModel.cacheSize, onReview = { showCleanup = true }, onSchedule = { showSchedule = true }, scheduleEnabled = viewModel.scheduleEnabled)
                            Screen.LARGE -> {
                                if (viewModel.customFolders.isEmpty() && viewModel.largeFiles.isEmpty()) {
                                    PermissionRationale(
                                        stringResource(R.string.large_files),
                                        stringResource(R.string.storage_access_rationale),
                                        onPickFolder
                                    )
                                } else {
                                    LargeFilesScreen(viewModel.largeFiles, onPickFolder)
                                }
                            }
                            Screen.APPS -> {
                                if (!hasUsageStatsPermission(context)) {
                                    PermissionRationale(
                                        stringResource(R.string.permission_required),
                                        stringResource(R.string.usage_access_rationale)
                                    ) {
                                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    }
                                } else {
                                    AppReviewScreen(viewModel.apps) { viewModel.openAppInfo(it) }
                                }
                            }
                            Screen.HEALTH -> HealthScreen(context, viewModel.storageSummary)
                            Screen.SETTINGS -> SettingsScreen(
                                viewModel.scheduleEnabled,
                                viewModel.customFolders,
                                { viewModel.toggleSchedule(it) },
                                { viewModel.removeCustomFolder(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dashboard(storage: StorageSummary, apps: List<AppSummary>, files: List<FileSummary>, onClean: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Text(stringResource(R.string.greeting), color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(stringResource(R.string.sub_greeting), color = Muted, fontSize = 16.sp)
            Spacer(Modifier.height(28.dp))
            StorageCard(storage, onClean)
        }
        item { Text(stringResource(R.string.safe_tools_title), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
        item { FeatureCard(stringResource(R.string.large_files), stringResource(R.string.feature_large_files_desc), "Files", if (files.isEmpty()) "Scan" else formatBytes(files.sumOf { it.size })) }
        item { FeatureCard(stringResource(R.string.app_review), pluralStringResource(R.plurals.feature_app_review_desc, apps.size, apps.size), "Apps", "Guided") }
        item { FeatureCard(stringResource(R.string.device_health), stringResource(R.string.feature_device_health_desc), "Health", "Check") }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF253020)).padding(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✦", color = Lime, fontSize = 28.sp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.privacy_title), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(stringResource(R.string.privacy_desc), color = Color(0xFFC4D1C2), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun CleanupScreen(cacheSize: Long, onReview: () -> Unit, onSchedule: () -> Unit, scheduleEnabled: Boolean) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.safe_cleanup), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.safe_cleanup_desc), color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))
        ResultRow(stringResource(R.string.this_app_cache), stringResource(R.string.this_app_cache_desc), formatBytes(cacheSize), true)
        ResultRow(stringResource(R.string.other_app_caches), stringResource(R.string.other_app_caches_desc), "Guided", false)
        ResultRow(stringResource(R.string.downloads_and_media), stringResource(R.string.downloads_and_media_desc), "User choice", false)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onReview) { Text(stringResource(R.string.review_cleanup_plan)) }
        TextButton(onClick = onSchedule) {
            Text(if (scheduleEnabled) stringResource(R.string.scheduled_cleanup_weekly) else stringResource(R.string.schedule_weekly_cleanup))
        }
    }
}

@Composable
private fun LargeFilesScreen(files: List<FileSummary>, onPickFolder: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.large_files), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.large_files_desc), color = Muted, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(stringResource(R.string.filter_over_500mb), stringResource(R.string.filter_older_30days), stringResource(R.string.filter_downloads)).forEach {
                Text(it, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Lime).padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }
        if (files.isEmpty()) {
            Text(stringResource(R.string.no_large_files), color = Muted, fontSize = 15.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(files.take(20)) { file ->
                    ResultRow(file.name, file.path, formatBytes(file.size), false)
                }
            }
        }
        Button(onClick = onPickFolder) { Text(stringResource(R.string.choose_folder_to_scan)) }
        Text(stringResource(R.string.folder_access_disclaimer), color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun AppReviewScreen(apps: List<AppSummary>, onOpenInfo: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.app_review), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.app_review_desc), color = Muted, fontSize = 16.sp)
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
                    TextButton(onClick = { onOpenInfo(app.packageName) }) { Text(stringResource(R.string.app_info_btn)) }
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
        Text(stringResource(R.string.device_health), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.device_health_desc), color = Muted, fontSize = 16.sp)
        ResultRow(stringResource(R.string.storage_pressure), if (storage.fraction > .85) stringResource(R.string.storage_pressure_low) else stringResource(R.string.storage_pressure_healthy), "${(storage.fraction * 100).toInt()}% used", storage.fraction <= .85f)
        ResultRow(stringResource(R.string.network_label), if (network) stringResource(R.string.network_connected) else stringResource(R.string.network_offline), if (network) stringResource(R.string.connected) else stringResource(R.string.offline), network)
        ResultRow(stringResource(R.string.system_uptime), stringResource(R.string.system_uptime_desc), formatDuration(SystemClock.elapsedRealtime()), true)
        ResultRow(stringResource(R.string.android_version), "${Build.MANUFACTURER} ${Build.MODEL}", "Android ${Build.VERSION.RELEASE}", true)
        Text(stringResource(R.string.thermal_disclaimer), color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun ScheduleDialog(enabled: Boolean, onDismiss: () -> Unit, onSave: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scheduled_cleanup_title)) },
        text = { Text(if (enabled) stringResource(R.string.scheduled_cleanup_enabled_desc) else stringResource(R.string.scheduled_cleanup_disabled_desc)) },
        confirmButton = { Button(onClick = { onSave(!enabled) }) { Text(if (enabled) stringResource(R.string.turn_off) else stringResource(R.string.enable)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun CleanupDialog(cacheSize: Long, onDismiss: () -> Unit, onCleaned: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_safe_cleanup)) },
        text = { Text(stringResource(R.string.confirm_cleanup_desc, formatBytes(cacheSize))) },
        confirmButton = { Button(onClick = onCleaned) { Text(stringResource(R.string.remove_cache)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

@Composable
private fun lastUsedText(timestamp: Long): String =
    if (timestamp == 0L) stringResource(R.string.last_used_unavailable) else stringResource(R.string.last_used_at, java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(timestamp))

private fun formatDuration(milliseconds: Long): String {
    val hours = milliseconds.coerceAtLeast(0L) / 3_600_000
    val days = hours / 24
    return if (days > 0) "$days days, ${hours % 24} hours" else "$hours hours"
}
