package com.teevclean.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.app.AppOpsManager
import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {
    private val viewModel: TeeVViewModel by viewModels()

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.addCustomFolder(it)
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            viewModel.refreshData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TeeVCleanApp(
                viewModel,
                onPickFolder = {
                    try {
                        folderPicker.launch(null)
                    } catch (_: ActivityNotFoundException) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            openAllFilesAccessSettings()
                        } else {
                            storagePermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }
                }
            )
        }
    }

    private fun openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
                val genericIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                try {
                    startActivity(genericIntent)
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.feature_not_supported, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

@Composable
fun TeeVCleanApp(viewModel: TeeVViewModel, onPickFolder: () -> Unit) {
    var showCleanup by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }
    var showSweep by remember { mutableStateOf(false) }
    var cleanupResult by remember { mutableStateOf<CleanupResult?>(null) }
    val context = LocalContext.current
    var lastBackPress by remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler {
        if (viewModel.currentScreen != Screen.OVERVIEW) {
            viewModel.currentScreen = Screen.OVERVIEW
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPress < 2000) {
                (context as? android.app.Activity)?.finish()
            } else {
                lastBackPress = now
                android.widget.Toast.makeText(context, R.string.back_to_exit, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showCleanup) {
        CleanupDialog(viewModel.cacheSize, onDismiss = { showCleanup = false }) {
            viewModel.clearCache { result ->
                showCleanup = false
                cleanupResult = result
            }
        }
    }
    if (showSweep) {
        SweepDialog(onDismiss = { showSweep = false }) {
            viewModel.sweepJunk { result ->
                showSweep = false
                cleanupResult = result
            }
        }
    }
    cleanupResult?.let { result ->
        CleanupResultDialog(result) { cleanupResult = null }
    }
    if (showSchedule) {
        ScheduleDialog(
            current = viewModel.cleanupFrequency,
            sweepEnabled = viewModel.scheduleSweepEnabled,
            hasFolders = viewModel.customFolders.isNotEmpty(),
            onDismiss = { showSchedule = false },
            onSave = { frequency, includeSweep ->
                viewModel.setSchedule(frequency, includeSweep)
                showSchedule = false
            },
        )
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
                            Screen.OVERVIEW -> Dashboard(viewModel.storageSummary, viewModel.apps, viewModel.largeFiles, viewModel.cleanupHistory, { showCleanup = true }) { viewModel.currentScreen = it }
                            Screen.CLEAN -> CleanupScreen(
                                cacheSize = viewModel.cacheSize,
                                onReview = { showCleanup = true },
                                onSweep = { showSweep = true },
                                onFindDuplicates = { viewModel.currentScreen = Screen.DUPLICATES },
                                onFreeUpSpace = { viewModel.openStorageManager() },
                                onNavigate = { viewModel.currentScreen = it },
                            )
                            Screen.LARGE -> {
                                if (viewModel.customFolders.isEmpty() && viewModel.largeFiles.isEmpty() && !viewModel.hasFullStorageAccess()) {
                                    PermissionRationale(
                                        stringResource(R.string.large_files),
                                        stringResource(R.string.storage_access_rationale),
                                        onPickFolder,
                                        fallbackLabel = stringResource(R.string.settings),
                                        onFallback = { viewModel.currentScreen = Screen.SETTINGS }
                                    )
                                } else {
                                    LargeFilesScreen(
                                        files = viewModel.largeFiles,
                                        onPickFolder = onPickFolder,
                                        onDeleteMany = { selected -> viewModel.deleteFiles(selected) { result -> cleanupResult = result } },
                                    )
                                }
                            }
                            Screen.APPS -> ApplicationsScreen(
                                apps = viewModel.apps,
                                hasUsageAccess = hasUsageStatsPermission(context),
                                onEnableUsage = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    } catch (_: ActivityNotFoundException) {
                                        Toast.makeText(context, R.string.feature_not_supported, Toast.LENGTH_LONG).show()
                                    }
                                },
                                onOpenInfo = { viewModel.openAppInfo(it) },
                                onUninstall = { viewModel.uninstallApp(it) },
                            )
                            Screen.DUPLICATES -> DuplicatesScreen(
                                groups = viewModel.duplicateGroups,
                                isScanning = viewModel.isScanningDuplicates,
                                onScan = { viewModel.findDuplicates() },
                                onDeleteExtras = { extras -> viewModel.deleteDuplicates(extras) { result -> cleanupResult = result } },
                            )
                            Screen.HEALTH -> HealthScreen(context, viewModel.storageSummary)
                            Screen.BREAKDOWN -> StorageBreakdownScreen(
                                breakdown = viewModel.storageBreakdown,
                                isLoading = viewModel.isLoadingBreakdown,
                                onLoad = { viewModel.loadBreakdown() },
                            )
                            Screen.SETTINGS -> SettingsScreen(
                                scheduleSummary = scheduleStatusText(viewModel.cleanupFrequency, viewModel.scheduleSweepEnabled),
                                customFolders = viewModel.customFolders,
                                history = viewModel.cleanupHistory,
                                hasUsageAccess = hasUsageStatsPermission(context),
                                hasStorageAccess = hasStorageAccessPermission(context, viewModel),
                                appVersion = "1.0",
                                onEditSchedule = { showSchedule = true },
                                onEnableUsage = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    } catch (_: ActivityNotFoundException) {
                                        Toast.makeText(context, R.string.feature_not_supported, Toast.LENGTH_LONG).show()
                                    }
                                },
                                onEnableStorage = onPickFolder,
                                onRemoveFolder = { viewModel.removeCustomFolder(it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dashboard(storage: StorageSummary, apps: List<AppSummary>, files: List<FileSummary>, history: CleanupHistory, onClean: () -> Unit, onNavigate: (Screen) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Text(stringResource(R.string.greeting), color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(stringResource(R.string.sub_greeting), color = Muted, fontSize = 16.sp)
            Spacer(Modifier.height(28.dp))
            StorageCard(storage, history, onClean)
        }
        item { Text(stringResource(R.string.safe_tools_title), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
        item {
            FeatureCard(
                stringResource(R.string.storage_breakdown),
                stringResource(R.string.storage_breakdown_desc),
                "Storage",
                "View",
Screen.BREAKDOWN.icon
            ) { onNavigate(Screen.BREAKDOWN) }
        }
        item {
            FeatureCard(
                stringResource(R.string.large_files),
                stringResource(R.string.feature_large_files_desc),
                "Files",
                if (files.isEmpty()) "Scan" else formatBytes(files.sumOf { it.size }),
Screen.LARGE.icon
            ) { onNavigate(Screen.LARGE) }
        }
        item {
            FeatureCard(
                stringResource(R.string.apps),
                pluralStringResource(R.plurals.feature_app_review_desc, apps.size, apps.size),
                "Apps",
                "Guided",
Screen.APPS.icon
            ) { onNavigate(Screen.APPS) }
        }
        item {
            FeatureCard(
                stringResource(R.string.device_health),
                stringResource(R.string.feature_device_health_desc),
                "Health",
                "Check",
Screen.HEALTH.icon
            ) { onNavigate(Screen.HEALTH) }
        }
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
private fun CleanupScreen(
    cacheSize: Long,
    onReview: () -> Unit,
    onSweep: () -> Unit,
    onFindDuplicates: () -> Unit,
    onFreeUpSpace: () -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Text(stringResource(R.string.cleanup), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.cleanup_desc), color = Muted, fontSize = 16.sp)
        }
        item {
            ActionRow(
                stringResource(R.string.this_app_cache),
                stringResource(R.string.this_app_cache_desc),
                formatBytes(cacheSize),
                onClick = onReview
            )
        }
        item {
            ActionRow(
                stringResource(R.string.other_app_caches),
                stringResource(R.string.other_app_caches_desc),
                stringResource(R.string.action_open),
            ) { onNavigate(Screen.APPS) }
        }
        item {
            ActionRow(
                stringResource(R.string.downloads_and_media),
                stringResource(R.string.downloads_and_media_desc),
                stringResource(R.string.action_review),
            ) { onNavigate(Screen.LARGE) }
        }
        item {
            ActionRow(
                stringResource(R.string.temp_and_logs),
                stringResource(R.string.temp_and_logs_desc),
                stringResource(R.string.action_sweep),
                onClick = onSweep,
            )
        }
        item {
            ActionRow(
                stringResource(R.string.duplicates),
                stringResource(R.string.duplicates_desc),
                stringResource(R.string.action_scan),
                onClick = onFindDuplicates,
            )
        }
        item {
            ActionRow(
                stringResource(R.string.free_up_space),
                stringResource(R.string.other_app_caches_desc),
                stringResource(R.string.action_open),
                onClick = onFreeUpSpace,
            )
        }
    }
}

@Composable
private fun LargeFilesScreen(
    files: List<FileSummary>,
    onPickFolder: () -> Unit,
    onDeleteMany: (List<FileSummary>) -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showConfirm by remember { mutableStateOf(false) }
    // Drop selections whose files are no longer present (e.g. after a delete + rescan).
    val selectedFiles = files.filter { it.uri in selected }
    val selectedBytes = selectedFiles.sumOf { it.size }

    if (showConfirm && selectedFiles.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.confirm_delete_files)) },
            text = { Text(pluralStringResource(R.plurals.confirm_delete_files_desc, selectedFiles.size, selectedFiles.size, formatBytes(selectedBytes))) },
            confirmButton = {
                TvButton(onClick = {
                    onDeleteMany(selectedFiles)
                    selected = emptySet()
                    showConfirm = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TvTextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.large_files), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.large_files_desc), color = Muted, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(stringResource(R.string.filter_over_500mb), stringResource(R.string.filter_older_30days), stringResource(R.string.filter_installers)).forEach {
                Text(it, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Lime).padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }
        if (files.isEmpty()) {
            Text(stringResource(R.string.no_large_files), color = Muted, fontSize = 15.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvTextButton(onClick = {
                    selected = if (selected.size == files.size) emptySet() else files.asSequence().map { it.uri }.toSet()
                }) { Text(if (selected.size == files.size) stringResource(R.string.clear_selection) else stringResource(R.string.select_all)) }
                Spacer(Modifier.weight(1f))
                if (selectedFiles.isNotEmpty()) {
                    TvButton(onClick = { showConfirm = true }) {
                        Text(stringResource(R.string.delete_selected, selectedFiles.size))
                    }
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(files) { file ->
                    SelectableFileRow(file.name, file.path, formatBytes(file.size), file.uri in selected) {
                        selected = if (file.uri in selected) selected - file.uri else selected + file.uri
                    }
                }
            }
        }
        TvButton(onClick = onPickFolder) { Text(stringResource(R.string.choose_folder_to_scan)) }
        Text(stringResource(R.string.folder_access_disclaimer), color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun ApplicationsScreen(
    apps: List<AppSummary>,
    hasUsageAccess: Boolean,
    onEnableUsage: () -> Unit,
    onOpenInfo: (String) -> Unit,
    onUninstall: (String) -> Unit,
) {
    val rarelyUsedCutoff = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
    val rarelyUsed = apps.filter { it.lastUsed in 1L until rarelyUsedCutoff }

    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.apps), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.app_review_desc), color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_review_cache_hint), color = Muted, fontSize = 13.sp)
        if (!hasUsageAccess) {
            Spacer(Modifier.height(14.dp))
            ActionRow(
                stringResource(R.string.usage_hint_title),
                stringResource(R.string.usage_hint_desc),
                stringResource(R.string.enable),
                onClick = onEnableUsage,
            )
        }
        if (rarelyUsed.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.rarely_used_title), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.rarely_used_desc), color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            rarelyUsed.take(6).forEach { app ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A211C)).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(lastUsedText(app.lastUsed), color = Color(0xFFFFB74D), fontSize = 12.sp)
                    }
                    Text(formatBytes(app.size), color = Lime, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(16.dp))
                    TvButton(onClick = { onUninstall(app.packageName) }) { Text(stringResource(R.string.uninstall)) }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.all_apps_title), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(apps) { app ->
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
                    TvTextButton(onClick = { onOpenInfo(app.packageName) }) { Text(stringResource(R.string.app_info_btn)) }
                }
            }
        }
    }
}

@Composable
private fun DuplicatesScreen(
    groups: List<DuplicateGroup>,
    isScanning: Boolean,
    onScan: () -> Unit,
    onDeleteExtras: (List<FileSummary>) -> Unit,
) {
    var pending by remember { mutableStateOf<DuplicateGroup?>(null) }
    LaunchedEffect(Unit) { if (groups.isEmpty() && !isScanning) onScan() }

    pending?.let { group ->
        val extras = group.files.drop(1)
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.confirm_delete_dupes)) },
            text = { Text(pluralStringResource(R.plurals.confirm_delete_dupes_desc, extras.size, extras.size, formatBytes(group.reclaimableBytes))) },
            confirmButton = {
                TvButton(onClick = {
                    onDeleteExtras(extras)
                    pending = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TvTextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.duplicates), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.duplicates_desc), color = Muted, fontSize = 16.sp)
        when {
            isScanning -> Text(stringResource(R.string.scanning), color = Lime, fontSize = 15.sp)
            groups.isEmpty() -> Text(stringResource(R.string.no_duplicates), color = Muted, fontSize = 15.sp)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                items(groups) { group ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(20.dp)) {
                        Text(
                            pluralStringResource(R.plurals.duplicate_group, group.files.size, group.files.size, formatBytes(group.sizeEach)),
                            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        group.files.forEachIndexed { index, file ->
                            Text(
                                (if (index == 0) "★ " else "• ") + file.name + "  -  " + file.path,
                                color = if (index == 0) Lime else Muted,
                                fontSize = 13.sp,
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        TvButton(onClick = { pending = group }) {
                            Text(pluralStringResource(R.plurals.delete_extras, group.files.size - 1, group.files.size - 1))
                        }
                    }
                }
            }
        }
        TvButton(onClick = onScan) { Text(stringResource(R.string.rescan)) }
    }
}

@Composable
private fun StorageBreakdownScreen(
    breakdown: StorageBreakdown?,
    isLoading: Boolean,
    onLoad: () -> Unit,
) {
    LaunchedEffect(Unit) { if (breakdown == null && !isLoading) onLoad() }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.storage_breakdown), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.storage_breakdown_desc), color = Muted, fontSize = 16.sp)
        if (isLoading || breakdown == null) {
            Text(stringResource(R.string.scanning), color = Lime, fontSize = 15.sp)
        } else {
            val orange = Color(0xFFFFB74D)
            val gray = Color(0xFF303930)
            val total = breakdown.totalBytes.coerceAtLeast(1)
            val appsFrac = breakdown.appsBytes.toFloat() / total
            val sysFrac = breakdown.systemAndOtherBytes.toFloat() / total
            val freeFrac = breakdown.freeBytes.toFloat() / total

            Text(
                stringResource(R.string.used_of_total, formatBytes(breakdown.usedBytes), formatBytes(breakdown.totalBytes)),
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            )
            Row(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(6.dp)).background(gray)) {
                if (appsFrac > 0f) Box(Modifier.weight(appsFrac).fillMaxHeight().background(Lime))
                if (sysFrac > 0f) Box(Modifier.weight(sysFrac).fillMaxHeight().background(orange))
                if (freeFrac > 0f) Box(Modifier.weight(freeFrac).fillMaxHeight())
            }
            if (breakdown.appDataKnown) {
                BreakdownRow(stringResource(R.string.breakdown_apps), formatBytes(breakdown.appsBytes), Lime)
                BreakdownRow(stringResource(R.string.breakdown_app_cache), formatBytes(breakdown.appCacheBytes), Muted, indent = true)
            }
            BreakdownRow(stringResource(R.string.breakdown_system_other), formatBytes(breakdown.systemAndOtherBytes), orange)
            BreakdownRow(stringResource(R.string.breakdown_free), formatBytes(breakdown.freeBytes), gray)
            if (!breakdown.appDataKnown) {
                Text(stringResource(R.string.breakdown_needs_usage), color = Muted, fontSize = 13.sp)
            }
        }
        TvButton(onClick = onLoad) { Text(stringResource(R.string.rescan)) }
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, swatch: Color, indent: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (indent) Spacer(Modifier.width(20.dp))
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(swatch))
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (indent) Muted else Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HealthScreen(context: Context, storage: StorageSummary) {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val network = manager?.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(stringResource(R.string.device_health), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.device_health_desc), color = Muted, fontSize = 16.sp)
            Spacer(Modifier.height(14.dp))
        }
        item { ResultRow(stringResource(R.string.storage_pressure), if (storage.fraction > .85) stringResource(R.string.storage_pressure_low) else stringResource(R.string.storage_pressure_healthy), "${(storage.fraction * 100).toInt()}% used", storage.fraction <= .85f) }
        item { ResultRow(stringResource(R.string.network_label), if (network) stringResource(R.string.network_connected) else stringResource(R.string.network_offline), if (network) stringResource(R.string.connected) else stringResource(R.string.offline), network) }
        item { ResultRow(stringResource(R.string.system_uptime), stringResource(R.string.system_uptime_desc), formatDuration(SystemClock.elapsedRealtime()), true) }
        item { ResultRow(stringResource(R.string.device_model), "${Build.MANUFACTURER}", Build.MODEL, true) }
        item { ResultRow(stringResource(R.string.android_version), stringResource(R.string.android_version_desc), "Android ${Build.VERSION.RELEASE}", true) }
        item {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.thermal_disclaimer), color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun scheduleStatusText(frequency: CleanupFrequency, sweepEnabled: Boolean): String {
    val base = when (frequency) {
        CleanupFrequency.OFF -> stringResource(R.string.freq_off)
        CleanupFrequency.DAILY -> stringResource(R.string.freq_daily)
        CleanupFrequency.WEEKLY -> stringResource(R.string.freq_weekly)
        CleanupFrequency.MONTHLY -> stringResource(R.string.freq_monthly)
    }
    return if (frequency != CleanupFrequency.OFF && sweepEnabled) {
        stringResource(R.string.schedule_with_sweep, base)
    } else {
        base
    }
}

@Composable
private fun scheduleButtonLabel(frequency: CleanupFrequency, sweepEnabled: Boolean): String =
    if (frequency == CleanupFrequency.OFF) {
        stringResource(R.string.schedule_cta)
    } else {
        stringResource(R.string.schedule_status, scheduleStatusText(frequency, sweepEnabled))
    }

@Composable
private fun ScheduleDialog(
    current: CleanupFrequency,
    sweepEnabled: Boolean,
    hasFolders: Boolean,
    onDismiss: () -> Unit,
    onSave: (CleanupFrequency, Boolean) -> Unit,
) {
    var frequency by remember { mutableStateOf(current) }
    var sweep by remember { mutableStateOf(sweepEnabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_title), color = Muted) },
        text = {
            Column {
                Text(stringResource(R.string.schedule_desc), color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.schedule_frequency_label), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectableChip(stringResource(R.string.freq_off), frequency == CleanupFrequency.OFF) { frequency = CleanupFrequency.OFF }
                    SelectableChip(stringResource(R.string.freq_daily), frequency == CleanupFrequency.DAILY) { frequency = CleanupFrequency.DAILY }
                    SelectableChip(stringResource(R.string.freq_weekly), frequency == CleanupFrequency.WEEKLY) { frequency = CleanupFrequency.WEEKLY }
                    SelectableChip(stringResource(R.string.freq_monthly), frequency == CleanupFrequency.MONTHLY) { frequency = CleanupFrequency.MONTHLY }
                }
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.schedule_items_label), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.schedule_clean_own_cache), color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                SelectableChip(
                    stringResource(R.string.schedule_clean_sweep),
                    selected = sweep && hasFolders,
                    enabled = hasFolders,
                ) { sweep = !sweep }
                if (!hasFolders) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.schedule_sweep_needs_folder), color = Muted, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TvButton(onClick = { onSave(frequency, sweep && hasFolders) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TvTextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CleanupDialog(cacheSize: Long, onDismiss: () -> Unit, onCleaned: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_cleanup)) },
        text = { Text(stringResource(R.string.confirm_cleanup_desc, formatBytes(cacheSize))) },
        confirmButton = { TvButton(onClick = onCleaned) { Text(stringResource(R.string.remove_cache)) } },
        dismissButton = { TvTextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun SweepDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.temp_and_logs)) },
        text = { Text(stringResource(R.string.confirm_sweep_desc)) },
        confirmButton = { TvButton(onClick = onConfirm) { Text(stringResource(R.string.action_sweep)) } },
        dismissButton = { TvTextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CleanupResultDialog(result: CleanupResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cleanup_complete)) },
        text = {
            if (result.itemsRemoved == 0) {
                Text(stringResource(R.string.cleanup_nothing))
            } else {
                Text(pluralStringResource(R.plurals.cleanup_freed, result.itemsRemoved, formatBytes(result.freedBytes), result.itemsRemoved))
            }
        },
        confirmButton = { TvButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
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

private fun hasStorageAccessPermission(context: Context, viewModel: TeeVViewModel): Boolean {
    return context.contentResolver.persistedUriPermissions.isNotEmpty() || viewModel.hasFullStorageAccess()
}

@Composable
private fun lastUsedText(timestamp: Long): String =
    if (timestamp == 0L) stringResource(R.string.last_used_unavailable) else stringResource(R.string.last_used_at, java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(timestamp))

