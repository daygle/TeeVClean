package com.teevclean.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF101311)
private val Panel = Color(0xFF191E1A)
private val PanelLight = Color(0xFF232B25)
private val Lime = Color(0xFFB7F35B)
private val Muted = Color(0xFF9BA79C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TeeVCleanApp() }
    }
}

private data class CleanFeature(val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val amount: String)

@Composable
fun TeeVCleanApp() {
    var selected by remember { mutableIntStateOf(0) }
    val features = listOf(
        CleanFeature("Quick clean", "Remove safe temporary files", Icons.Outlined.CleaningServices, "1.8 GB"),
        CleanFeature("Large files", "Find space-hungry media", Icons.Outlined.FolderOpen, "4.2 GB"),
        CleanFeature("App review", "Unused apps and cache guidance", Icons.Outlined.Apps, "12 apps"),
        CleanFeature("Device health", "Storage, network and thermal checks", Icons.Outlined.HealthAndSafety, "Good")
    )
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
            Row(modifier = Modifier.fillMaxSize().padding(44.dp)) {
                Sidebar(selected) { selected = it }
                Spacer(Modifier.width(38.dp))
                if (selected == 0) Dashboard(features) else FeatureDetail(features[selected - 1])
            }
        }
    }
}

@Composable
private fun Sidebar(selected: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.width(210.dp).fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Lime), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.DeleteSweep, null, tint = Ink, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("TeeV", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(" clean", color = Lime, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(62.dp))
        listOf("Overview", "Quick clean", "Large files", "App review", "Device health").forEachIndexed { index, label ->
            NavItem(label, index, selected, onSelect)
        }
        Spacer(Modifier.weight(1f))
        NavItem("Settings", 9, selected, onSelect, Icons.Outlined.Settings)
        Text("TV CLEANER  •  v1.0", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 18.dp, top = 20.dp))
    }
}

@Composable
private fun NavItem(label: String, index: Int, selected: Int, onSelect: (Int) -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val active = selected == index
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(if (active) PanelLight else Color.Transparent).border(if (active) 1.dp else 0.dp, if (active) Lime.copy(alpha = .35f) else Color.Transparent, RoundedCornerShape(14.dp)).focusable(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(if (active) Lime else Color.Transparent))
        Text(label, color = if (active) Color.White else Muted, fontSize = 15.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
    }
}

@Composable
private fun Dashboard(features: List<CleanFeature>) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Text("Good evening, ready to tidy up?", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text("A calmer, cleaner TV starts here.", color = Muted, fontSize = 16.sp)
            Spacer(Modifier.height(28.dp))
            StorageCard()
        }
        item { Text("Tools for a healthier TV", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
        items(features) { FeatureCard(it) }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF253020)).padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("✦", color = Lime, fontSize = 28.sp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Your privacy comes first", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("TeeV Clean only removes files you approve. No fake RAM boosts.", color = Color(0xFFC4D1C2), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun StorageCard() {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).padding(27.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("STORAGE HEALTH", color = Lime, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(10.dp))
            Text("18.6 GB", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
            Text("of 32 GB used", color = Muted, fontSize = 15.sp)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF303930))) {
                Box(Modifier.fillMaxWidth(.58f).fillMaxHeight().background(Lime))
            }
        }
        Spacer(Modifier.width(55.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("13.4 GB", color = Lime, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("available space", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Text("Last scan  •  Today, 8:42 PM", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FeatureCard(feature: CleanFeature) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(20.dp).focusable(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF29352A)), contentAlignment = Alignment.Center) { Icon(feature.icon, null, tint = Lime, modifier = Modifier.size(27.dp)) }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) { Text(feature.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(feature.subtitle, color = Muted, fontSize = 13.sp) }
        Text(feature.amount, color = Lime, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(22.dp))
        Text("›", color = Muted, fontSize = 28.sp)
    }
}

@Composable
private fun FeatureDetail(feature: CleanFeature) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(feature.title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(feature.subtitle, color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).padding(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(feature.icon, null, tint = Lime, modifier = Modifier.size(55.dp))
            Spacer(Modifier.width(24.dp))
            Column {
                Text(feature.amount, color = Lime, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Ready to scan safely", color = Color.White, fontSize = 16.sp)
                Text("You review every item before anything is removed.", color = Muted, fontSize = 14.sp)
            }
        }
        Text("Press OK to begin", color = Lime, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
