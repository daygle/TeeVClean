package com.teevclean.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

val Ink = Color(0xFF101311)
val Panel = Color(0xFF191E1A)
val PanelLight = Color(0xFF232B25)
val Lime = Color(0xFFB7F35B)
val Muted = Color(0xFF9BA79C)

@Composable
fun Sidebar(selected: Screen, onSelect: (Screen) -> Unit) {
    Column(Modifier.width(210.dp).fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Lime), contentAlignment = Alignment.Center) { Text("✓", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp)); Text("TeeV", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("Clean", color = Lime, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(62.dp))
        Screen.entries
            .filter { it != Screen.SETTINGS && it != Screen.DUPLICATES && it != Screen.BREAKDOWN }
            .forEach { screen -> NavItem(screen, selected, onSelect) }
        Spacer(Modifier.weight(1f))
        NavItem(Screen.SETTINGS, selected, onSelect)
        Text(stringResource(R.string.version_footer, "1.0"), color = Muted, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 18.dp, top = 20.dp))
    }
}

@Composable
fun NavItem(screen: Screen, selected: Screen, onSelect: (Screen) -> Unit) {
    val active = screen == selected
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isFocused) PanelLight.copy(alpha = 0.8f) else if (active) PanelLight else Color.Transparent)
            .border(
                if (isFocused) 2.dp else if (active) 1.dp else 0.dp,
                if (isFocused) Lime else if (active) Lime.copy(alpha = .35f) else Color.Transparent,
                RoundedCornerShape(14.dp)
            )
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onSelect(screen)
                    true
                } else false
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onSelect(screen) }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (active) Lime else Color.Transparent)
        )
        Icon(
            imageVector = screen.icon,
            contentDescription = stringResource(screen.labelRes),
            tint = if (isFocused || active) Color.White else Muted,
            modifier = Modifier.padding(start = 14.dp).size(18.dp)
        )
        Text(
            stringResource(screen.labelRes),
            color = if (active || isFocused) Color.White else Muted,
            fontSize = 15.sp,
            fontWeight = if (active || isFocused) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        )
    }
}

@Composable
fun ResultRow(title: String, subtitle: String, amount: String, safe: Boolean) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(22.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Muted, fontSize = 14.sp) }; Text(amount, color = if (safe) Lime else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun ActionRow(title: String, subtitle: String, action: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isFocused) PanelLight else Panel)
            .border(
                if (isFocused) 2.dp else 0.dp,
                if (isFocused) Lime else Color.Transparent,
                RoundedCornerShape(18.dp),
            )
            .padding(22.dp)
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick()
                    true
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 14.sp)
        }
        Text(action, color = Lime, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(14.dp))
        Text("›", color = Muted, fontSize = 26.sp)
    }
}

@Composable
fun SelectableChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val base = Modifier
        .clip(RoundedCornerShape(10.dp))
        .background(if (!enabled) Panel else if (selected) Lime else PanelLight)
        .border(
            if (isFocused) 2.dp else 0.dp,
            if (isFocused) Color.White else Color.Transparent,
            RoundedCornerShape(10.dp),
        )
    val interactive = if (enabled) {
        base
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick()
                    true
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    } else {
        base
    }
    Box(interactive.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            color = if (selected && enabled) Ink else if (enabled) Color.White else Muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun SelectableFileRow(title: String, subtitle: String, amount: String, selected: Boolean, onToggle: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isFocused) PanelLight else Panel)
            .border(
                if (isFocused || selected) 2.dp else 0.dp,
                if (selected) Lime else if (isFocused) Color.White else Color.Transparent,
                RoundedCornerShape(18.dp),
            )
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onToggle()
                    true
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(if (selected) Lime else PanelLight),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Text("✓", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, color = Muted, fontSize = 13.sp, maxLines = 1)
        }
        Text(amount, color = Lime, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FeatureCard(
    title: String,
    subtitle: String,
    badge: String,
    amount: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isFocused) PanelLight else Panel)
            .border(
                if (isFocused) 2.dp else 0.dp,
                if (isFocused) Lime else Color.Transparent,
                RoundedCornerShape(18.dp)
            )
            .padding(20.dp)
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick()
                    true
                } else false
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF29352A)), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = title, tint = Lime, modifier = Modifier.size(22.dp)) }; Spacer(Modifier.width(18.dp)); Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Muted, fontSize = 13.sp) }; Text(amount, color = Lime, fontSize = 17.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(22.dp)); Text("›", color = Muted, fontSize = 28.sp)
    }
}

@Composable
fun StorageCard(storage: StorageSummary, onClean: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).padding(27.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(stringResource(R.string.storage_health_label), color = Lime, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Spacer(Modifier.height(10.dp)); Text(formatBytes(storage.used), color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold); Text(stringResource(R.string.used_of_total, formatBytes(storage.used), formatBytes(storage.total)), color = Muted, fontSize = 15.sp) }
            Column(horizontalAlignment = Alignment.End) { 
                Text(formatBytes(storage.free), color = Lime, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.available_space_label), color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                TvButton(onClick = onClean) { Text(stringResource(R.string.review_safe_cleanup)) } 
            }
        }
        Spacer(Modifier.height(20.dp)); Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF303930))) { Box(Modifier.fillMaxWidth(storage.fraction).fillMaxHeight().background(if (storage.fraction > .85f) Color(0xFFFFB74D) else Lime)) }
    }
}

@Composable
fun SettingsScreen(
    scheduleSummary: String,
    customFolders: List<String>,
    history: CleanupHistory,
    appVersion: String,
    onEditSchedule: () -> Unit,
    onRemoveFolder: (String) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Text(stringResource(R.string.settings), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(20.dp)) {
                Text(stringResource(R.string.settings_cleanup_schedule), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(scheduleSummary, color = Muted, modifier = Modifier.weight(1f))
                    TvButton(onClick = onEditSchedule) {
                        Text(stringResource(R.string.schedule_change))
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(20.dp)) {
                Text(stringResource(R.string.settings_history), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                val lastRun = if (history.lastRun == 0L) {
                    stringResource(R.string.history_never)
                } else {
                    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(history.lastRun)
                }
                Text(stringResource(R.string.history_last_run, lastRun), color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.history_total_freed, formatBytes(history.totalFreedBytes), history.totalItems), color = Muted, fontSize = 14.sp)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(20.dp)) {
                Text(stringResource(R.string.settings_scanned_folders), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                if (customFolders.isEmpty()) {
                    Text(stringResource(R.string.settings_no_folders), color = Muted)
                } else {
                    customFolders.forEach { folder ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(folder, color = Muted, modifier = Modifier.weight(1f), maxLines = 1)
                            IconButton(onClick = { onRemoveFolder(folder) }) {
                                Icon(Icons.Outlined.Delete, stringResource(R.string.remove_folder), tint = Color.Red)
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(20.dp)) {
                Text(stringResource(R.string.settings_about), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.about_version, appVersion), color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_privacy_policy), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.privacy_desc), color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun LoadingOverlay() {
    Box(Modifier.fillMaxWidth().height(4.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Lime, trackColor = Color.Transparent)
    }
}

@Composable
fun PermissionRationale(title: String, description: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(description, color = Muted, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        TvButton(onClick = onClick) { Text(stringResource(R.string.enable)) }
    }
}

@Composable
fun TvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (isFocused) Lime else PanelLight,
            contentColor = if (isFocused) Ink else Color.White
        ),
        modifier = modifier
            .border(
                if (isFocused) 2.dp else 0.dp,
                if (isFocused) Color.White.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick()
                    true
                } else false
            },
        shape = RoundedCornerShape(8.dp)
    ) {
        content()
    }
}

@Composable
fun TvTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    TextButton(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            contentColor = if (isFocused) Lime else Muted
        ),
        modifier = modifier
            .background(if (isFocused) Color.White.copy(alpha = 0.05f) else Color.Transparent, RoundedCornerShape(8.dp))
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.key == Key.DirectionCenter || it.key == Key.Enter)) {
                    onClick()
                    true
                } else false
            }
    ) {
        content()
    }
}

fun formatBytes(bytes: Long): String { if (bytes < 1024) return "$bytes B"; val units = arrayOf("KB", "MB", "GB", "TB"); var value = bytes.toDouble(); var index = -1; while (value >= 1024 && index < units.lastIndex) { value /= 1024; index++ }; return String.format(Locale.US, "%.1f %s", value, units[index]) }
