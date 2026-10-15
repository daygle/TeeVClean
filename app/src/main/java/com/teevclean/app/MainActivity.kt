package com.teevclean.app

import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.net.Uri
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.key
import java.io.File
import android.os.Process
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import java.text.DateFormat
import androidx.compose.runtime.mutableIntStateOf
import java.util.Calendar
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.widthIn
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val viewModel: TeeVViewModel by viewModels()

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Some pickers don't offer a persistable grant; the folder would stop working after a restart.
            Toast.makeText(this, R.string.folder_grant_failed, Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        viewModel.addCustomFolder(uri.toString())
    }

    /** Free space just before the clear-all-caches prompt opened, to report what it freed. */
    private var freeBeforeCacheClear = 0L

    private val clearCachesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.onSystemCachesCleared(freeBeforeCacheClear) { freed ->
                Toast.makeText(this, getString(R.string.app_caches_cleared, formatBytes(freed)), Toast.LENGTH_LONG).show()
            }
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
            // Dialogs take their colours from the Material theme, so it must be the app's dark one.
            TeeVTheme {
                TeeVCleanApp(
                    viewModel,
                    onRequestStorageAccess = {
                        if (viewModel.hasFullStorageAccess()) {
                            Toast.makeText(this, R.string.storage_full_access_granted, Toast.LENGTH_SHORT).show()
                        } else {
                            pickFolder()
                        }
                    },
                    // Duplicate and temp-file scans only cover picked folders, so adding one is always allowed.
                    onAddFolder = ::pickFolder,
                    onClearAllCaches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ::clearAllAppCaches else null,
                )
            }
        }
    }

    private fun pickFolder() {
        try {
            folderPicker.launch(null)
        } catch (_: ActivityNotFoundException) {
            // Many Android TVs ship without the system folder picker. With all-files access
            // TeeVClean can browse shared storage itself; without it, ask for that access first.
            if (viewModel.hasFullStorageAccess()) {
                viewModel.folderBrowserOpen = true
                return
            }
            Toast.makeText(this, R.string.folder_picker_needs_access, Toast.LENGTH_LONG).show()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                openAllFilesAccessSettings()
            } else {
                storagePermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    /**
     * Opens Android's own "clear all app caches" confirmation (Android 11+). Android only offers
     * it to apps holding all-files access, so that is requested first when missing.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun clearAllAppCaches() {
        if (!Environment.isExternalStorageManager()) {
            Toast.makeText(this, R.string.clear_caches_needs_access, Toast.LENGTH_LONG).show()
            openAllFilesAccessSettings()
            return
        }
        freeBeforeCacheClear = viewModel.freeBytes()
        try {
            clearCachesLauncher.launch(Intent(StorageManager.ACTION_CLEAR_APP_CACHE))
        } catch (_: Exception) {
            // ActivityNotFoundException on TVs without the screen; SecurityException if access was just revoked.
            Toast.makeText(this, R.string.clear_caches_unavailable, Toast.LENGTH_LONG).show()
            viewModel.openStorageManager()
        }
    }

    private fun openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = "package:$packageName".toUri()
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
                val genericIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                try {
                    startActivity(genericIntent)
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.feature_not_supported, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun TeeVCleanApp(
    viewModel: TeeVViewModel,
    onRequestStorageAccess: () -> Unit,
    onAddFolder: () -> Unit,
    onClearAllCaches: (() -> Unit)?,
) {
    var showCleanup by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }
    var showSweep by remember { mutableStateOf(false) }
    var cleanupResult by remember { mutableStateOf<CleanupResult?>(null) }
    val context = LocalContext.current
    var lastBackPress by remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val contentFocus = remember { FocusRequester() }
    // The sidebar item for the current screen; Back and D-pad Left from the content land here.
    val sidebarFocus = remember { FocusRequester() }
    // Whether D-pad focus was last in the content pane (rather than the sidebar).
    val focusWasInContent = remember { mutableStateOf(false) }
    // Whether anything in the app (sidebar or content) currently has focus.
    val appHasFocus = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // When the focused item disappears (a card that opened another screen, Back, a deleted
    // row), Compose drops focus and the next D-pad press would jump to the top-left menu item.
    // Put focus back into the content instead, once the new content is laid out.
    val restoreContentFocus = {
        scope.launch {
            withFrameNanos { }
            // Moving from the content to the sidebar (D-pad Left, Back) briefly leaves neither
            // with focus. Only restore if focus is still lost a frame later, or it would be
            // pulled straight back out of the sidebar.
            if (!appHasFocus.value && focusWasInContent.value) contentFocus.tryRequestFocus()
        }
    }

    val restoreFocusToContentTop = {
        scope.launch {
            withFrameNanos { }
            contentFocus.tryRequestFocus()
        }
    }

    // Fires on first composition too, which performs the initial load.
    DisposableEffect(lifecycleOwner) {
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
        if (focusWasInContent.value) {
            // Back from the content returns to the menu first, like D-pad Left.
            sidebarFocus.tryRequestFocus()
        } else if (viewModel.currentScreen != Screen.OVERVIEW) {
            viewModel.currentScreen = Screen.OVERVIEW
        } else {
            val now = SystemClock.elapsedRealtime()
            if (lastBackPress != 0L && now - lastBackPress < 2000) {
                (context as? android.app.Activity)?.finish()
            } else {
                lastBackPress = now
                Toast.makeText(context, R.string.back_to_exit, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showCleanup) {
        ReviewCleanupDialog(
            hasFolders = viewModel.customFolders.isNotEmpty(),
            hasStorageAccess = viewModel.hasFullStorageAccess(),
            onDismiss = { showCleanup = false },
            onRequireFolders = {
                showCleanup = false
                onAddFolder()
            },
            onRequireStorage = {
                showCleanup = false
                onRequestStorageAccess()
            },
        ) { options ->
            // Closed straight away: a device-wide sweep can take a while, and a second press
            // must not start a second run.
            showCleanup = false
            Toast.makeText(context, R.string.cleaning_in_progress, Toast.LENGTH_SHORT).show()
            viewModel.cleanNow(options) { result -> cleanupResult = result }
            if (options.appCaches && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                onClearAllCaches?.invoke()
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
    if (viewModel.folderBrowserOpen) {
        FolderBrowserDialog(
            onDismiss = { viewModel.folderBrowserOpen = false },
            onPick = { folder ->
                viewModel.folderBrowserOpen = false
                viewModel.addCustomFolder(Uri.fromFile(folder).toString())
            },
        )
    }
    if (showSchedule) {
        ScheduleDialog(
            current = viewModel.cleanupFrequency,
            options = viewModel.scheduleOptions,
            minuteOfDay = viewModel.scheduleMinuteOfDay,
            hasFolders = viewModel.customFolders.isNotEmpty(),
            hasStorageAccess = viewModel.hasFullStorageAccess(),
            onDismiss = { showSchedule = false },
            onSave = { frequency, options, minuteOfDay ->
                viewModel.setSchedule(frequency, options, minuteOfDay)
                showSchedule = false
            },
        )
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            // Android TV safe area: 48dp left/right and 27dp top/bottom stay clear of overscan.
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 27.dp)
                    .onFocusChanged {
                        appHasFocus.value = it.hasFocus
                        if (!it.hasFocus && focusWasInContent.value) restoreContentFocus()
                    }
            ) {
                Box(Modifier.onFocusChanged { if (it.hasFocus) focusWasInContent.value = false }) {
                    Sidebar(viewModel.currentScreen, sidebarFocus) { viewModel.currentScreen = it }
                }
                Spacer(Modifier.width(32.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .onFocusChanged { if (it.hasFocus) focusWasInContent.value = true }
                        .focusRequester(contentFocus)
                        // D-pad Right from the sidebar would land on whatever item is level with
                        // the focused menu entry (e.g. "System Uptime" from "Device Health").
                        // Cancel that move and request the content instead, which enters it at
                        // its first item.
                        .focusProperties {
                            enter = { direction ->
                                if (direction == FocusDirection.Right) {
                                    restoreFocusToContentTop()
                                    FocusRequester.Cancel
                                } else {
                                    FocusRequester.Default
                                }
                            }
                        }
                        .focusGroup()
                ) {
                    when (viewModel.currentScreen) {
                        Screen.OVERVIEW -> Dashboard(viewModel.storageSummary, viewModel.apps, viewModel.largeFiles, viewModel.cleanupHistory, { showCleanup = true }) { viewModel.currentScreen = it }
                        Screen.CLEAN -> CleanupScreen(
                            onSweep = { showSweep = true },
                            onFindDuplicates = { viewModel.currentScreen = Screen.DUPLICATES },
                            onFreeUpSpace = { viewModel.openStorageManager() },
                            onClearAllCaches = onClearAllCaches,
                            onNavigate = { viewModel.currentScreen = it },
                        )
                        Screen.LARGE -> {
                            if (viewModel.customFolders.isEmpty() && viewModel.largeFiles.isEmpty() && !viewModel.hasFullStorageAccess()) {
                                PermissionRationale(
                                    stringResource(R.string.large_files),
                                    stringResource(R.string.storage_access_rationale),
                                    onRequestStorageAccess,
                                    fallbackLabel = stringResource(R.string.settings),
                                    onFallback = { viewModel.currentScreen = Screen.SETTINGS }
                                )
                            } else {
                                LargeFilesScreen(
                                    files = viewModel.largeFiles,
                                    filter = viewModel.fileFilter,
                                    onUpdateFilter = { viewModel.updateFileFilter(it) },
                                    onDeleteMany = { selected -> viewModel.deleteFiles(selected) { result -> cleanupResult = result } },
                                )
                            }
                        }
                        Screen.APPS -> ApplicationsScreen(
                            apps = viewModel.apps,
                            hasUsageAccess = hasUsageStatsPermission(context),
                            onEnableUsage = { openUsageAccessSettings(context) },
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
                        Screen.HISTORY -> CleanupHistoryScreen(
                            entries = viewModel.cleanupLog,
                            onClear = { viewModel.clearCleanupLog() },
                        )
                        Screen.BREAKDOWN -> StorageBreakdownScreen(
                            breakdown = viewModel.storageBreakdown,
                            isLoading = viewModel.isLoadingBreakdown,
                            onLoad = { viewModel.loadBreakdown() },
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            scheduleSummary = scheduleStatusText(viewModel.cleanupFrequency, viewModel.scheduleOptions, viewModel.scheduleMinuteOfDay),
                            scheduleTimes = scheduleTimesText(viewModel.cleanupFrequency, viewModel.lastAutomaticRun, viewModel.nextScheduledRun),
                            customFolders = viewModel.customFolders,
                            hasUsageAccess = hasUsageStatsPermission(context),
                            hasStorageAccess = hasStorageAccessPermission(context, viewModel),
                            appVersion = BuildConfig.VERSION_NAME,
                            onEditSchedule = { showSchedule = true },
                            onEnableUsage = { openUsageAccessSettings(context) },
                            onEnableStorage = onRequestStorageAccess,
                            onAddFolder = onAddFolder,
                            onRemoveFolder = { viewModel.removeCustomFolder(it) },
                        )
                    }
                    // Overlays the top edge so content doesn't shift while refreshing.
                    if (viewModel.isRefreshing) {
                        LoadingOverlay(Modifier.align(Alignment.TopCenter).offset(y = (-12).dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Dashboard(storage: StorageSummary, apps: List<AppSummary>, files: List<FileSummary>, history: CleanupHistory, onClean: () -> Unit, onNavigate: (Screen) -> Unit) {
    ScreenScaffold(stringResource(R.string.greeting), stringResource(R.string.sub_greeting)) {
        ScreenList(spacing = 18.dp) {
            item { StorageCard(storage, history, onClean) }
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
                    stringResource(R.string.history_title),
                    stringResource(R.string.history_card_desc),
                    "Log",
                    "View",
                    Screen.HISTORY.icon
                ) { onNavigate(Screen.HISTORY) }
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
}

@Composable
private fun CleanupScreen(
    onSweep: () -> Unit,
    onFindDuplicates: () -> Unit,
    onFreeUpSpace: () -> Unit,
    onClearAllCaches: (() -> Unit)?,
    onNavigate: (Screen) -> Unit,
) {
    ScreenScaffold(stringResource(R.string.cleanup), stringResource(R.string.cleanup_desc)) {
        ScreenList(spacing = 14.dp) {
            if (onClearAllCaches != null) {
                item {
                    ActionRow(
                        stringResource(R.string.all_app_caches),
                        stringResource(R.string.all_app_caches_desc),
                        stringResource(R.string.action_clear),
                        onClick = onClearAllCaches,
                    )
                }
            } else {
                // Before Android 11 there is no clear-all screen, so per-app info pages are the
                // only way to clear other apps' caches.
                item {
                    ActionRow(
                        stringResource(R.string.other_app_caches),
                        stringResource(R.string.other_app_caches_desc),
                        stringResource(R.string.action_open),
                    ) { onNavigate(Screen.APPS) }
                }
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
                    stringResource(R.string.free_up_space_desc),
                    stringResource(R.string.action_open),
                    onClick = onFreeUpSpace,
                )
            }
            item {
                ActionRow(
                    stringResource(R.string.history_title),
                    stringResource(R.string.history_card_desc),
                    stringResource(R.string.action_open),
                ) { onNavigate(Screen.HISTORY) }
            }
        }
    }
}

@Composable
private fun LargeFilesScreen(
    files: List<FileSummary>,
    filter: FileFilter,
    onUpdateFilter: (FileFilter) -> Unit,
    onDeleteMany: (List<FileSummary>) -> Unit,
) {
    var activeFilters by remember { mutableStateOf(setOf(0, 1, 2, 3)) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showConfirm by remember { mutableStateOf(false) }
    var showFilterSettings by remember { mutableStateOf(false) }

    val largeBytes = filter.minSize

    val filteredFiles = remember(files, activeFilters, filter) {
        val now = System.currentTimeMillis()
        files.filter { file ->
            (0 in activeFilters && filter.isLarge(file.size)) ||
                (1 in activeFilters && filter.isOld(file.modified, now)) ||
                (2 in activeFilters && filter.isListedInstaller(file.name)) ||
                (3 in activeFilters && file.alreadyInstalled)
        }
    }

    // Selection outside the current chips is ignored, so hidden files are never deleted.
    val selectedFiles = filteredFiles.filter { it.uri in selected }
    val selectedBytes = selectedFiles.sumOf { it.size }
    val allSelected = selectedFiles.size == filteredFiles.size

    if (showFilterSettings) {
        FilterDialog(
            current = filter,
            onDismiss = { showFilterSettings = false },
            onSave = {
                onUpdateFilter(it)
                showFilterSettings = false
            }
        )
    }

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
            dismissButton = { TvTextButton(modifier = Modifier.focusRequester(rememberInitialFocus()), onClick = { showConfirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    ScreenScaffold(
        stringResource(R.string.large_files),
        stringResource(R.string.large_files_desc),
        actions = { TvButton(onClick = { showFilterSettings = true }) { Text(stringResource(R.string.filter_settings)) } },
    ) {
        // Chips scroll sideways (D-pad focus follows) so selection controls always keep their place.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val filterOptions = mutableListOf(
                    (if (largeBytes >= 1024 * 1024 * 1024) "${largeBytes / (1024 * 1024 * 1024)} GB+" else "${largeBytes / (1024 * 1024)} MB+") to 0,
                    "${filter.minAgeDays} Days+" to 1
                )
                if (filter.showInstallers) {
                    filterOptions.add(stringResource(R.string.filter_installers) to 2)
                }
                if (files.any { it.alreadyInstalled }) {
                    filterOptions.add(stringResource(R.string.filter_already_installed) to 3)
                }

                filterOptions.forEach { (label, index) ->
                    SelectableChip(
                        label = label,
                        selected = index in activeFilters,
                        onClick = {
                            activeFilters = if (index in activeFilters) activeFilters - index else activeFilters + index
                        }
                    )
                }
            }
            if (filteredFiles.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                TvTextButton(onClick = {
                    selected = if (allSelected) emptySet() else filteredFiles.mapTo(mutableSetOf()) { it.uri }
                }) { Text(stringResource(if (allSelected) R.string.clear_selection else R.string.select_all)) }
            }
            if (selectedFiles.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                TvButton(onClick = { showConfirm = true }) {
                    Text(stringResource(R.string.delete_selected, selectedFiles.size))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        ScreenList(spacing = 10.dp) {
            if (filteredFiles.isEmpty()) {
                item { Text(stringResource(R.string.no_large_files), color = Muted, fontSize = 15.sp) }
            } else {
                items(filteredFiles, key = { it.uri }) { file ->
                    val subtitle = if (file.alreadyInstalled) stringResource(R.string.installed_suffix, file.path) else file.path
                    SelectableFileRow(file.name, subtitle, formatBytes(file.size), file.uri in selected) {
                        selected = if (file.uri in selected) selected - file.uri else selected + file.uri
                    }
                }
            }
            item { Text(stringResource(R.string.folder_access_disclaimer), color = Muted, fontSize = 13.sp) }
        }
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
    val rarelyUsed = remember(apps) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RARELY_USED_DAYS)
        apps.filter { it.lastUsed in 1L until cutoff }.take(6)
    }

    ScreenScaffold(stringResource(R.string.apps), stringResource(R.string.app_review_desc)) {
        ScreenList(spacing = 10.dp) {
            item { Text(stringResource(R.string.app_review_cache_hint), color = Muted, fontSize = 13.sp) }
            if (!hasUsageAccess) {
                item {
                    ActionRow(
                        stringResource(R.string.usage_hint_title),
                        stringResource(R.string.usage_hint_desc),
                        stringResource(R.string.enable),
                        onClick = onEnableUsage,
                    )
                }
            }
            if (rarelyUsed.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.rarely_used_title), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.rarely_used_desc), color = Muted, fontSize = 13.sp)
                }
                items(rarelyUsed, key = { "rare:${it.packageName}" }) { app ->
                    AppRow(
                        title = app.label,
                        subtitle = lastUsedText(app.lastUsed),
                        subtitleColor = Color(0xFFFFB74D),
                        size = formatBytes(app.size),
                        action = stringResource(R.string.uninstall),
                        background = Color(0xFF2A211C),
                    ) { onUninstall(app.packageName) }
                }
            }
            item {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.all_apps_title), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            items(apps, key = { it.packageName }) { app ->
                AppRow(
                    title = app.label,
                    subtitle = "${app.packageName} • ${lastUsedText(app.lastUsed)}",
                    subtitleColor = Muted,
                    size = formatBytes(app.size),
                    action = stringResource(R.string.app_info_btn),
                    background = Panel,
                ) { onOpenInfo(app.packageName) }
            }
        }
    }
}

/**
 * One app in the list. The whole row is a single focus stop that runs [onClick]; a separate
 * button inside a focusable row would make every app take two D-pad presses to pass.
 */
@Composable
private fun AppRow(
    title: String,
    subtitle: String,
    subtitleColor: Color,
    size: String,
    action: String,
    background: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) PanelLight else background)
            .border(if (isFocused) 2.dp else 0.dp, if (isFocused) Lime else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = subtitleColor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(16.dp))
        Text(size, color = Lime, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(16.dp))
        Text(action, color = if (isFocused) Color.White else Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text("›", color = Muted, fontSize = 24.sp)
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
            dismissButton = { TvTextButton(modifier = Modifier.focusRequester(rememberInitialFocus()), onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    ScreenScaffold(
        stringResource(R.string.duplicates),
        stringResource(R.string.duplicates_desc),
        actions = { TvButton(onClick = onScan) { Text(stringResource(R.string.rescan)) } },
    ) {
        when {
            isScanning -> Text(stringResource(R.string.scanning), color = Lime, fontSize = 15.sp)
            groups.isEmpty() -> Text(stringResource(R.string.no_duplicates), color = Muted, fontSize = 15.sp)
            else -> ScreenList {
                items(groups, key = { it.files.first().uri }) { group ->
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
                                overflow = TextOverflow.Ellipsis,
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
    }
}

@Composable
private fun StorageBreakdownScreen(
    breakdown: StorageBreakdown?,
    isLoading: Boolean,
    onLoad: () -> Unit,
) {
    LaunchedEffect(Unit) { if (breakdown == null && !isLoading) onLoad() }

    ScreenScaffold(
        stringResource(R.string.storage_breakdown),
        stringResource(R.string.storage_breakdown_desc),
        actions = { TvButton(onClick = onLoad) { Text(stringResource(R.string.rescan)) } },
    ) {
        if (isLoading || breakdown == null) {
            Text(stringResource(R.string.scanning), color = Lime, fontSize = 15.sp)
        } else {
            BreakdownCard(breakdown)
        }
    }
}

@Composable
private fun BreakdownCard(breakdown: StorageBreakdown) {
    val orange = Color(0xFFFFB74D)
    val gray = Color(0xFF303930)
    val total = breakdown.totalBytes.coerceAtLeast(1)
    val appsFrac = breakdown.appsBytes.toFloat() / total
    val sysFrac = breakdown.systemAndOtherBytes.toFloat() / total
    val freeFrac = breakdown.freeBytes.toFloat() / total

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
    ScreenScaffold(stringResource(R.string.device_health), stringResource(R.string.device_health_desc)) {
        ScreenList(spacing = 10.dp) {
            item { ResultRow(stringResource(R.string.storage_pressure), if (storage.fraction > .85) stringResource(R.string.storage_pressure_low) else stringResource(R.string.storage_pressure_healthy), "${(storage.fraction * 100).toInt()}% Used", storage.fraction <= .85f) }
            item { ResultRow(stringResource(R.string.network_label), if (network) stringResource(R.string.network_connected) else stringResource(R.string.network_offline), if (network) stringResource(R.string.connected) else stringResource(R.string.offline), network) }
            item { ResultRow(stringResource(R.string.system_uptime), stringResource(R.string.system_uptime_desc), formatDuration(SystemClock.elapsedRealtime()), true) }
            item { ResultRow(stringResource(R.string.device_model), Build.MANUFACTURER, Build.MODEL, true) }
            item { ResultRow(stringResource(R.string.android_version), stringResource(R.string.android_version_desc), "Android ${Build.VERSION.RELEASE}", true) }
            item { Text(stringResource(R.string.thermal_disclaimer), color = Muted, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun CleanupHistoryScreen(entries: List<CleanupLogEntry>, onClear: () -> Unit) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    ScreenScaffold(stringResource(R.string.history_title), stringResource(R.string.history_desc)) {
        ScreenList(spacing = 10.dp) {
            if (entries.isEmpty()) {
                item { Text(stringResource(R.string.history_empty), color = Muted, fontSize = 15.sp) }
            }
            items(entries) { entry ->
                val source = stringResource(if (entry.automatic) R.string.history_automatic else R.string.history_manual)
                val what = entry.kinds.map { stringResource(it.labelRes()) }.joinToString(", ")
                val count = pluralStringResource(R.plurals.history_items, entry.itemsRemoved, entry.itemsRemoved)
                ResultRow(
                    title = dateFormat.format(entry.time),
                    // Android's own cache clear doesn't report a count, so none is shown for it.
                    subtitle = listOfNotNull(
                        source,
                        stringResource(R.string.history_incomplete).takeIf { !entry.complete },
                        what.ifEmpty { null },
                        count.takeIf { CleanupKind.APP_CACHES !in entry.kinds },
                    ).joinToString("  ·  "),
                    amount = formatBytes(entry.freedBytes),
                    safe = true,
                )
            }
            // Last, so landing on the list from the menu can't put focus on it.
            if (entries.isNotEmpty()) {
                item { TvTextButton(onClick = onClear) { Text(stringResource(R.string.history_clear)) } }
            }
        }
    }
}

private fun CleanupKind.labelRes(): Int = when (this) {
    CleanupKind.OWN_CACHE -> R.string.this_app_cache
    CleanupKind.APP_CACHES -> R.string.all_app_caches
    CleanupKind.SWEEP_FOLDERS -> R.string.schedule_clean_sweep
    CleanupKind.SWEEP_DEVICE -> R.string.schedule_clean_device_sweep
    CleanupKind.INSTALLERS -> R.string.schedule_clean_installed_apks
    CleanupKind.TRASH -> R.string.schedule_clean_trash
    CleanupKind.FILES -> R.string.large_files
    CleanupKind.DUPLICATES -> R.string.duplicates
}

/** "Last run: … · Next run: about …" for the Settings card; null while no schedule is set. */
@Composable
private fun scheduleTimesText(frequency: CleanupFrequency, lastRun: Long, nextRun: Long?): String? {
    if (frequency == CleanupFrequency.OFF) return null
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val last = if (lastRun > 0) format.format(lastRun) else stringResource(R.string.history_never)
    val next = nextRun?.let { stringResource(R.string.schedule_next_run, format.format(it)) }
        ?: stringResource(R.string.schedule_next_run_unknown)
    return stringResource(R.string.schedule_last_run, last) + "  ·  " + next
}

/** A time of day (minutes after midnight) in the TV's own short format, e.g. "3:00 AM" or "03:00". */
private fun formatMinuteOfDay(minuteOfDay: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
    }
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(calendar.time)
}

@Composable
private fun scheduleStatusText(frequency: CleanupFrequency, options: ScheduleOptions, minuteOfDay: Int): String {
    val time = formatMinuteOfDay(minuteOfDay)
    val base = when (frequency) {
        CleanupFrequency.OFF -> stringResource(R.string.freq_off)
        CleanupFrequency.DAILY -> stringResource(R.string.schedule_at, stringResource(R.string.freq_daily), time)
        CleanupFrequency.WEEKLY -> stringResource(R.string.schedule_at, stringResource(R.string.freq_weekly), time)
        CleanupFrequency.MONTHLY -> stringResource(R.string.schedule_at, stringResource(R.string.freq_monthly), time)
    }
    val extras = options.extrasCount
    return if (frequency != CleanupFrequency.OFF && extras > 0) {
        pluralStringResource(R.plurals.schedule_with_extras, extras, base, extras)
    } else {
        base
    }
}

@Composable
private fun ScheduleDialog(
    current: CleanupFrequency,
    options: ScheduleOptions,
    minuteOfDay: Int,
    hasFolders: Boolean,
    hasStorageAccess: Boolean,
    onDismiss: () -> Unit,
    onSave: (CleanupFrequency, ScheduleOptions, Int) -> Unit,
) {
    var frequency by remember { mutableStateOf(current) }
    var time by remember { mutableIntStateOf(minuteOfDay) }
    var selected by remember { mutableStateOf(options) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    // The explanation at the bottom has nothing focusable, so D-pad focus alone never scrolls
    // to it; reaching Save brings it into view. (Not Cancel: it takes focus on open, and the
    // dialog should open at the top.)
    val revealBottom = Modifier.onFocusChanged {
        if (it.isFocused) scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_title)) },
        text = {
            Column(Modifier.verticalScroll(scrollState)) {
                Text(stringResource(R.string.schedule_frequency_label), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectableChip(stringResource(R.string.freq_off), frequency == CleanupFrequency.OFF) { frequency = CleanupFrequency.OFF }
                    SelectableChip(stringResource(R.string.freq_daily), frequency == CleanupFrequency.DAILY) { frequency = CleanupFrequency.DAILY }
                    SelectableChip(stringResource(R.string.freq_weekly), frequency == CleanupFrequency.WEEKLY) { frequency = CleanupFrequency.WEEKLY }
                    SelectableChip(stringResource(R.string.freq_monthly), frequency == CleanupFrequency.MONTHLY) { frequency = CleanupFrequency.MONTHLY }
                }
                // Time and extras only matter when something is scheduled; hiding them keeps "Off" simple.
                if (frequency != CleanupFrequency.OFF) {
                    Spacer(Modifier.height(20.dp))
                    Text(stringResource(R.string.schedule_time_label), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    // Steppers rather than a clock-face picker: easy to work with a D-pad.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectableChip("◀", selected = false) { time = ScheduleTiming.step(time, -1) }
                        Text(
                            formatMinuteOfDay(time),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(min = 110.dp),
                        )
                        SelectableChip("▶", selected = false) { time = ScheduleTiming.step(time, 1) }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(stringResource(R.string.schedule_also_clean), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    CleanupOptionsList(selected, hasFolders, hasStorageAccess) { selected = it }
                }
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.schedule_footer), color = Muted, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TvButton(
                modifier = revealBottom,
                // Options that can't run (missing folder or access) are saved as off.
                onClick = { onSave(frequency, selected.availableOnly(hasFolders, hasStorageAccess), time) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TvTextButton(modifier = Modifier.focusRequester(rememberInitialFocus()), onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Overview's Review Clean-up: runs clean-up now with all items selected by default and fully interactive toggles.
 */
@Composable
private fun ReviewCleanupDialog(
    hasFolders: Boolean,
    hasStorageAccess: Boolean,
    onDismiss: () -> Unit,
    onRequireFolders: () -> Unit,
    onRequireStorage: () -> Unit,
    onClean: (ScheduleOptions) -> Unit,
) {
    val hasAppCaches = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    var selected by remember {
        mutableStateOf(
            ScheduleOptions(
                appCaches = hasAppCaches,
                sweepFolders = true,
                sweepDevice = true,
                installedApks = true,
                trash = TRASH_SUPPORTED,
            )
        )
    }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    // As in the schedule dialog: focusing Clean Now scrolls the footer into view.
    val revealBottom = Modifier.onFocusChanged {
        if (it.isFocused) scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.review_cleanup)) },
        text = {
            Column(Modifier.verticalScroll(scrollState)) {
                CleanupOptionsList(selected, hasFolders, hasStorageAccess, showAppCaches = true, allAvailable = true) { selected = it }
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.review_footer), color = Muted, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TvButton(
                modifier = revealBottom,
                onClick = {
                    if (selected.sweepFolders && !hasFolders) {
                        onDismiss()
                        onRequireFolders()
                    } else if ((selected.sweepDevice || selected.trash || selected.appCaches) && !hasStorageAccess) {
                        onDismiss()
                        onRequireStorage()
                    } else {
                        onClean(selected.availableOnly(hasFolders, hasStorageAccess, hasAppCaches))
                    }
                },
            ) { Text(stringResource(R.string.clean_now)) }
        },
        // Cancel takes focus on open, so a stray OK press deletes nothing.
        dismissButton = { TvTextButton(modifier = Modifier.focusRequester(rememberInitialFocus()), onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The cleanup options list, plus a hint when some can't run. */
@Composable
private fun CleanupOptionsList(
    selected: ScheduleOptions,
    hasFolders: Boolean,
    hasStorageAccess: Boolean,
    showAppCaches: Boolean = false,
    allAvailable: Boolean = false,
    onChange: (ScheduleOptions) -> Unit,
) {
    val av = allAvailable || hasStorageAccess
    val avFolders = allAvailable || hasFolders
    val avInstallers = allAvailable || hasStorageAccess || hasFolders
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showAppCaches && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ScheduleToggle(stringResource(R.string.all_app_caches), selected.appCaches, available = av) {
                onChange(selected.copy(appCaches = !selected.appCaches))
            }
        }
        ScheduleToggle(stringResource(R.string.schedule_clean_sweep), selected.sweepFolders, available = avFolders) {
            onChange(selected.copy(sweepFolders = !selected.sweepFolders))
        }
        ScheduleToggle(stringResource(R.string.schedule_clean_device_sweep), selected.sweepDevice, available = av) {
            onChange(selected.copy(sweepDevice = !selected.sweepDevice))
        }
        ScheduleToggle(stringResource(R.string.schedule_clean_installed_apks), selected.installedApks, available = avInstallers) {
            onChange(selected.copy(installedApks = !selected.installedApks))
        }
        if (TRASH_SUPPORTED) {
            ScheduleToggle(stringResource(R.string.schedule_clean_trash), selected.trash, available = av) {
                onChange(selected.copy(trash = !selected.trash))
            }
        }
    }
    if (!allAvailable && (!hasFolders || !hasStorageAccess || (showAppCaches && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R))) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.schedule_unavailable_hint), color = Muted, fontSize = 13.sp)
    }
}

/** Android's trash only exists from Android 11. */
private val TRASH_SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

/** These options with the ones that can't run here turned off. */
private fun ScheduleOptions.availableOnly(hasFolders: Boolean, hasStorageAccess: Boolean, hasAppCaches: Boolean = false) = ScheduleOptions(
    appCaches = appCaches && hasStorageAccess && hasAppCaches,
    sweepFolders = sweepFolders && hasFolders,
    sweepDevice = sweepDevice && hasStorageAccess,
    installedApks = installedApks && (hasStorageAccess || hasFolders),
    trash = trash && hasStorageAccess && TRASH_SUPPORTED,
)

/**
 * Folder picker over shared storage for TVs without the system one; needs all-files access.
 * Hidden folders and Android/ (other apps' private data) are left out. Focus moves to the first
 * row whenever the folder changes, since the focused row disappears with the old listing.
 */
@Composable
private fun FolderBrowserDialog(onDismiss: () -> Unit, onPick: (File) -> Unit) {
    val root = remember { Environment.getExternalStorageDirectory() }
    var current by remember { mutableStateOf(root) }
    val folders = remember(current) {
        current.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") && !(current == root && it.name.equals("Android", ignoreCase = true)) }
            .sortedBy { it.name.lowercase() }
    }
    val canGoUp = current != root
    val firstFocus = key(current) { rememberInitialFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_browser_title)) },
        text = {
            Column {
                Text(folderDisplayName(Uri.fromFile(current).toString()), color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (canGoUp) {
                        item(key = "up") {
                            FolderRow("‹  " + stringResource(R.string.folder_browser_up), Modifier.focusRequester(firstFocus)) {
                                current = current.parentFile ?: root
                            }
                        }
                    }
                    items(folders, key = { it.path }) { folder ->
                        val modifier = if (!canGoUp && folder == folders.first()) Modifier.focusRequester(firstFocus) else Modifier
                        FolderRow(folder.name, modifier) { current = folder }
                    }
                    if (folders.isEmpty()) {
                        item(key = "empty") { Text(stringResource(R.string.folder_browser_empty), color = Muted, fontSize = 14.sp) }
                    }
                }
            }
        },
        confirmButton = {
            // With nothing to list at the top level, start on the button instead.
            val modifier = if (!canGoUp && folders.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier
            TvButton(modifier = modifier, onClick = { onPick(current) }) { Text(stringResource(R.string.folder_browser_use)) }
        },
        dismissButton = { TvTextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun FolderRow(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Text(
        label,
        color = if (focused) Ink else Color.White,
        fontSize = 16.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Lime else PanelLight)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** An on/off option in the schedule dialog; a tick marks it as on, since chip colour alone is easy to miss. */
@Composable
private fun ScheduleToggle(label: String, on: Boolean, available: Boolean, onFlip: () -> Unit) {
    val active = on && available
    SelectableChip(if (active) "✓  $label" else label, selected = active, enabled = available, onClick = onFlip)
}

@Composable
private fun SweepDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.temp_and_logs)) },
        text = { Text(stringResource(R.string.confirm_sweep_desc)) },
        confirmButton = { TvButton(onClick = onConfirm) { Text(stringResource(R.string.action_sweep)) } },
        dismissButton = { TvTextButton(modifier = Modifier.focusRequester(rememberInitialFocus()), onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun FilterDialog(
    current: FileFilter,
    onDismiss: () -> Unit,
    onSave: (FileFilter) -> Unit,
) {
    var filter by remember { mutableStateOf(current) }
    val sizeOptions = listOf(
        50L * 1024 * 1024 to stringResource(R.string.filter_size_unit_mb, 50),
        100L * 1024 * 1024 to stringResource(R.string.filter_size_unit_mb, 100),
        500L * 1024 * 1024 to stringResource(R.string.filter_size_unit_mb, 500),
        1L * 1024 * 1024 * 1024 to stringResource(R.string.filter_size_unit_gb, 1),
        2L * 1024 * 1024 * 1024 to stringResource(R.string.filter_size_unit_gb, 2)
    )
    val ageOptions = listOf(
        7 to pluralStringResource(R.plurals.filter_age_unit_days, 7, 7),
        14 to pluralStringResource(R.plurals.filter_age_unit_days, 14, 14),
        30 to pluralStringResource(R.plurals.filter_age_unit_days, 30, 30),
        90 to pluralStringResource(R.plurals.filter_age_unit_days, 90, 90)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_settings), color = Muted) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.filter_min_size), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sizeOptions.forEach { (size, label) ->
                        SelectableChip(label, filter.minSize == size) { filter = filter.copy(minSize = size) }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.filter_min_age), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ageOptions.forEach { (days, label) ->
                        SelectableChip(label, filter.minAgeDays == days) { filter = filter.copy(minAgeDays = days) }
                    }
                }
                Spacer(Modifier.height(18.dp))
                SelectableChip(
                    stringResource(R.string.filter_show_installers),
                    selected = filter.showInstallers,
                ) { filter = filter.copy(showInstallers = !filter.showInstallers) }
            }
        },
        confirmButton = { TvButton(onClick = { onSave(filter) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TvTextButton(modifier = Modifier.focusRequester(rememberInitialFocus()), onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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
        confirmButton = { TvButton(modifier = Modifier.focusRequester(rememberInitialFocus()), onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

private const val RARELY_USED_DAYS = 90L

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
    // checkOpNoThrow was deprecated in API 29 for unsafeCheckOpNoThrow, then restored in API 36
    // (which deprecated unsafeCheckOpNoThrow); it behaves the same on every API level.
    val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    // MODE_DEFAULT defers to the regular permission check (used by some OEM settings screens).
    return if (mode == AppOpsManager.MODE_DEFAULT) {
        context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
    } else {
        mode == AppOpsManager.MODE_ALLOWED
    }
}

private fun openUsageAccessSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.usage_settings_unavailable, Toast.LENGTH_LONG).show()
    }
}

private fun hasStorageAccessPermission(context: Context, viewModel: TeeVViewModel): Boolean {
    return context.contentResolver.persistedUriPermissions.isNotEmpty() || viewModel.hasFullStorageAccess()
}

@Composable
private fun lastUsedText(timestamp: Long): String =
    if (timestamp == 0L) {
        stringResource(R.string.last_used_unavailable)
    } else {
        stringResource(R.string.last_used_at, DateFormat.getDateInstance(DateFormat.MEDIUM).format(timestamp))
    }

