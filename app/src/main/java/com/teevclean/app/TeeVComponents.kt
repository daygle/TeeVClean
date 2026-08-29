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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
        Screen.entries.forEach { screen -> NavItem(screen, selected, onSelect) }
        Spacer(Modifier.weight(1f))
        val settingsInteractionSource = remember { MutableInteractionSource() }
        val isSettingsFocused by settingsInteractionSource.collectIsFocusedAsState()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSettingsFocused) PanelLight else Color.Transparent)
                .border(
                    if (isSettingsFocused) 2.dp else 0.dp,
                    if (isSettingsFocused) Lime else Color.Transparent,
                    RoundedCornerShape(14.dp)
                )
                .focusable(interactionSource = settingsInteractionSource),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Settings, null, tint = if (isSettingsFocused) Color.White else Muted, modifier = Modifier.padding(18.dp).size(18.dp)); Text("Settings", color = if (isSettingsFocused) Color.White else Muted, fontSize = 15.sp)
        }
        Text("TV Cleaner  •  v1.0", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 18.dp, top = 20.dp))
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
            .focusable(interactionSource = interactionSource)
            .clickable(onClick = { onSelect(screen) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(if (active) Lime else Color.Transparent))
        Text(screen.label, color = if (active || isFocused) Color.White else Muted, fontSize = 15.sp, fontWeight = if (active || isFocused) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
    }
}

@Composable
fun ResultRow(title: String, subtitle: String, amount: String, safe: Boolean) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(22.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Muted, fontSize = 14.sp) }; Text(amount, color = if (safe) Lime else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun FeatureCard(title: String, subtitle: String, badge: String, amount: String) {
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
            .focusable(interactionSource = interactionSource),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF29352A)), contentAlignment = Alignment.Center) { Text(badge.take(1), color = Lime, fontSize = 20.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(18.dp)); Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Muted, fontSize = 13.sp) }; Text(amount, color = Lime, fontSize = 17.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(22.dp)); Text("›", color = Muted, fontSize = 28.sp)
    }
}

@Composable
fun StorageCard(storage: StorageSummary, onClean: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).padding(27.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Storage Health", color = Lime, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Spacer(Modifier.height(10.dp)); Text(formatBytes(storage.used), color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold); Text("of ${formatBytes(storage.total)} used", color = Muted, fontSize = 15.sp) }
            Column(horizontalAlignment = Alignment.End) { Text(formatBytes(storage.free), color = Lime, fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("available space", color = Muted, fontSize = 13.sp); Spacer(Modifier.height(14.dp)); Button(onClick = onClean) { Text("Review Safe Cleanup") } }
        }
        Spacer(Modifier.height(20.dp)); Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF303930))) { Box(Modifier.fillMaxWidth(storage.fraction).fillMaxHeight().background(if (storage.fraction > .85f) Color(0xFFFFB74D) else Lime)) }
    }
}

fun formatBytes(bytes: Long): String { if (bytes < 1024) return "$bytes B"; val units = arrayOf("KB", "MB", "GB", "TB"); var value = bytes.toDouble(); var index = -1; while (value >= 1024 && index < units.lastIndex) { value /= 1024; index++ }; return String.format(Locale.US, "%.1f %s", value, units[index]) }
