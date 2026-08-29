package com.teevclean.app

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import java.io.File
import java.util.Locale

private val Ink = Color(0xFF101311)
private val Panel = Color(0xFF191E1A)
private val PanelLight = Color(0xFF232B25)
private val Lime = Color(0xFFB7F35B)
private val Muted = Color(0xFF9BA79C)

class MainActivity : ComponentActivity() {
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TeeVCleanApp(onPickFolder = { folderPicker.launch(null) }) }
    }
}

private data class StorageSummary(val used: Long, val total: Long) {
    val free: Long get() = (total - used).coerceAtLeast(0)
    val fraction: Float get() = if (total == 0L) 0f else (used.toFloat() / total).coerceIn(0f, 1f)
}

private data class AppSummary(val label: String, val packageName: String, val size: Long, val lastUsed: Long)
private data class FileSummary(val name: String, val path: String, val size: Long, val modified: Long)

private enum class Screen(val label: String) {
    OVERVIEW("Overview"), CLEAN("Safe cleanup"), LARGE("Large files"), APPS("App review"), HEALTH("Device health")
}

@Composable
fun TeeVCleanApp(onPickFolder: () -> Unit) {
    var selected by remember { mutableStateOf(Screen.OVERVIEW) }
    var showCleanup by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val storage = remember { readStorage() }
    val apps = remember { loadApps(context) }
    val files = remember { scanFiles() }

    if (showCleanup) CleanupDialog { showCleanup = false }

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Row(Modifier.fillMaxSize().padding(44.dp)) {
                Sidebar(selected) { selected = it }
                Spacer(Modifier.width(38.dp))
                when (selected) {
                    Screen.OVERVIEW -> Dashboard(storage, apps, files) { showCleanup = true }
                    Screen.CLEAN -> CleanupScreen { showCleanup = true }
                    Screen.LARGE -> LargeFilesScreen(files, onPickFolder)
                    Screen.APPS -> AppReviewScreen(apps, context)
                    Screen.HEALTH -> HealthScreen(context, storage)
                }
            }
        }
    }
}

@Composable
private fun Sidebar(selected: Screen, onSelect: (Screen) -> Unit) {
    Column(Modifier.width(210.dp).fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Lime), contentAlignment = Alignment.Center) { Text("✓", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp)); Text("TeeV", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold); Text(" clean", color = Lime, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(62.dp))
        Screen.entries.forEach { screen -> NavItem(screen, selected, onSelect) }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).focusable(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Settings, null, tint = Muted, modifier = Modifier.padding(18.dp).size(18.dp)); Text("Settings", color = Muted, fontSize = 15.sp)
        }
        Text("TV CLEANER  •  v1.0", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 18.dp, top = 20.dp))
    }
}

@Composable
private fun NavItem(screen: Screen, selected: Screen, onSelect: (Screen) -> Unit) {
    val active = screen == selected
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(if (active) PanelLight else Color.Transparent).border(if (active) 1.dp else 0.dp, if (active) Lime.copy(alpha = .35f) else Color.Transparent, RoundedCornerShape(14.dp)).focusable(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(if (active) Lime else Color.Transparent))
        Text(screen.label, color = if (active) Color.White else Muted, fontSize = 15.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
    }
}

@Composable
private fun Dashboard(storage: StorageSummary, apps: List<AppSummary>, files: List<FileSummary>, onClean: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Text("Good evening, ready to tidy up?", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp)); Text("A calmer, cleaner TV starts here.", color = Muted, fontSize = 16.sp); Spacer(Modifier.height(28.dp)); StorageCard(storage, onClean)
        }
        item { Text("Safe tools for a healthier TV", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
        item { FeatureCard("Large files", "Review downloads and media before deleting", "Files", if (files.isEmpty()) "Scan" else formatBytes(files.sumOf { it.size })) }
        item { FeatureCard("App review", "${apps.size} apps; review size and open Android app info", "Apps", "Guided") }
        item { FeatureCard("Device health", "Storage pressure, network, uptime and system details", "Health", "Check") }
        item { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF253020)).padding(22.dp), verticalAlignment = Alignment.CenterVertically) { Text("✦", color = Lime, fontSize = 28.sp); Spacer(Modifier.width(16.dp)); Column { Text("Your privacy comes first", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp); Text("No silent deletion and no fake RAM boosts. You approve every cleanup action.", color = Color(0xFFC4D1C2), fontSize = 13.sp) } } }
    }
}

@Composable
private fun StorageCard(storage: StorageSummary, onClean: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).padding(27.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("STORAGE HEALTH", color = Lime, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Spacer(Modifier.height(10.dp)); Text(formatBytes(storage.used), color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold); Text("of ${formatBytes(storage.total)} used", color = Muted, fontSize = 15.sp) }
            Column(horizontalAlignment = Alignment.End) { Text(formatBytes(storage.free), color = Lime, fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("available space", color = Muted, fontSize = 13.sp); Spacer(Modifier.height(14.dp)); Button(onClick = onClean) { Text("Review safe cleanup") } }
        }
        Spacer(Modifier.height(20.dp)); Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF303930))) { Box(Modifier.fillMaxWidth(storage.fraction).fillMaxHeight().background(if (storage.fraction > .85f) Color(0xFFFFB74D) else Lime)) }
    }
}

@Composable
private fun FeatureCard(title: String, subtitle: String, badge: String, amount: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(20.dp).focusable(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF29352A)), contentAlignment = Alignment.Center) { Text(badge.take(1), color = Lime, fontSize = 20.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(18.dp)); Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Muted, fontSize = 13.sp) }; Text(amount, color = Lime, fontSize = 17.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(22.dp)); Text("›", color = Muted, fontSize = 28.sp)
    }
}

@Composable
private fun CleanupScreen(onReview: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Safe cleanup", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold); Text("Scan temporary files and review every item before removal.", color = Muted, fontSize = 16.sp); Spacer(Modifier.height(12.dp))
        ResultRow("This app's cache", "Safe to remove; app data and user files stay intact", formatBytes(cacheSize(LocalContext.current)), true)
        ResultRow("Other app caches", "Android requires each app's own info page", "Guided", false)
        ResultRow("Downloads and media", "Select files in the large-file review", "User choice", false)
        Spacer(Modifier.height(10.dp)); Button(onClick = onReview) { Text("Review cleanup plan") }
    }
}

@Composable
private fun ResultRow(title: String, subtitle: String, amount: String, safe: Boolean) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(22.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Muted, fontSize = 14.sp) }; Text(amount, color = if (safe) Lime else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun LargeFilesScreen(files: List<FileSummary>, onPickFolder: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Large files", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold); Text("Review files in shared storage. Unknown files are never deleted automatically.", color = Muted, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { listOf("Over 500 MB", "Older than 30 days", "Downloads").forEach { Text(it, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Lime).padding(horizontal = 18.dp, vertical = 10.dp)) } }
        if (files.isEmpty()) Text("No large files were found in the accessible Downloads and media folders.", color = Muted, fontSize = 15.sp) else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(files.take(10)) { file -> ResultRow(file.name, file.path, formatBytes(file.size), false) } }
        Button(onClick = onPickFolder) { Text("Choose folder to scan") }
        Text("Folder access uses Android's Storage Access Framework. You stay in control of the location.", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun AppReviewScreen(apps: List<AppSummary>, context: Context) {
    Column(Modifier.fillMaxSize()) {
        Text("App review", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("Apps are sorted by installed APK size. Last-used data may be unavailable without usage access.", color = Muted, fontSize = 16.sp); Spacer(Modifier.height(20.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(apps.take(12)) { app -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(app.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text("${app.packageName} • ${lastUsedText(app.lastUsed)}", color = Muted, fontSize = 12.sp) }; Text(formatBytes(app.size), color = Lime, fontWeight = FontWeight.Bold); Spacer(Modifier.width(16.dp)); TextButton(onClick = { openAppInfo(context, app.packageName) }) { Text("App info") } } } }
    }
}

@Composable
private fun HealthScreen(context: Context, storage: StorageSummary) {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val network = manager?.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Device health", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold); Text("Practical diagnostics, not RAM booster claims.", color = Muted, fontSize = 16.sp)
        ResultRow("Storage pressure", if (storage.fraction > .85) "Low space — review large files" else "Healthy free-space margin", "${(storage.fraction * 100).toInt()}% used", storage.fraction <= .85f)
        ResultRow("Network", if (network) "Internet connection detected" else "No active internet connection", if (network) "Connected" else "Offline", network)
        ResultRow("System uptime", "Time since the device last booted", formatDuration(SystemClock.elapsedRealtime()), true)
        ResultRow("Android version", "${Build.MANUFACTURER} ${Build.MODEL}", "Android ${Build.VERSION.RELEASE}", true)
        Text("Thermal readings vary by TV manufacturer and are not exposed on every device.", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun CleanupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Confirm safe cleanup") }, text = { Text("Only TeeV Clean's own temporary cache will be removed. Photos, downloads, app data, and other apps remain untouched. Estimated space: ${formatBytes(cacheSize(LocalContext.current))}.") }, confirmButton = { Button(onClick = { clearOwnCache(context); onDismiss() }) { Text("Remove cache") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private fun readStorage(): StorageSummary { val stat = StatFs(Environment.getDataDirectory().path); val total = stat.blockCountLong * stat.blockSizeLong; return StorageSummary(total - stat.availableBytes, total) }
private fun cacheSize(context: Context): Long = folderSize(context.cacheDir)
private fun clearOwnCache(context: Context) { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
private fun folderSize(file: File): Long = if (!file.exists()) 0L else if (file.isFile) file.length() else file.listFiles()?.sumOf { folderSize(it) } ?: 0L

private fun scanFiles(): List<FileSummary> {
    val roots = listOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES))
    val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
    return roots.flatMap { root -> root.listFiles()?.filter { it.isFile && (it.length() >= 500L * 1024 * 1024 || it.lastModified() < cutoff) }?.map { FileSummary(it.name, it.parent ?: "", it.length(), it.lastModified()) } ?: emptyList() }.sortedByDescending { it.size }
}

private fun loadApps(context: Context): List<AppSummary> {
    val usage = context.getSystemService(UsageStatsManager::class.java)
    val since = System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000
    val lastUsed = usage?.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, since, System.currentTimeMillis())?.associate { it.packageName to it.lastTimeUsed }.orEmpty()
    return context.packageManager.getInstalledApplications(0).filter { it.packageName != context.packageName }.map { app -> AppSummary(app.loadLabel(context.packageManager).toString(), app.packageName, File(app.sourceDir ?: "").length(), lastUsed[app.packageName] ?: 0L) }.sortedByDescending { it.size }
}

private fun openAppInfo(context: Context, packageName: String) { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
private fun lastUsedText(timestamp: Long): String = if (timestamp == 0L) "last used unavailable" else "last used ${java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(timestamp)}"
private fun formatBytes(bytes: Long): String { if (bytes < 1024) return "$bytes B"; val units = arrayOf("KB", "MB", "GB", "TB"); var value = bytes.toDouble(); var index = -1; while (value >= 1024 && index < units.lastIndex) { value /= 1024; index++ }; return String.format(Locale.US, "%.1f %s", value, units[index]) }
private fun formatDuration(milliseconds: Long): String { val hours = milliseconds / 3_600_000; val days = hours / 24; return if (days > 0) "$days days, ${hours % 24} hours" else "$hours hours" }
