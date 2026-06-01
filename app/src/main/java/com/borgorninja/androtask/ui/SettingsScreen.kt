package com.borgorninja.androtask.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.borgorninja.androtask.AppLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark, titleContentColor = OnBg),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnBg)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Permissions
            SettingsCard(title = "Permissions", icon = Icons.Default.Security) {
                Text(
                    "Required for recording and replaying gestures.",
                    style = MaterialTheme.typography.bodySmall, color = OnBgVariant
                )
                Spacer(Modifier.height(8.dp))
                SettingsButton("Accessibility Service", Icons.Default.Accessibility) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                Spacer(Modifier.height(6.dp))
                SettingsButton("Draw Over Other Apps", Icons.Default.Layers) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }
            }

            // Debug / Logs
            SettingsCard(title = "Debug", icon = Icons.Default.BugReport) {
                val entryCount = remember { AppLogger.entries().size }
                Text(
                    "$entryCount log entries recorded this session.",
                    style = MaterialTheme.typography.bodySmall, color = OnBgVariant
                )
                Spacer(Modifier.height(8.dp))
                SettingsButton("View Debug Log", Icons.Default.Terminal) {
                    navController.navigate("logs")
                }
            }

            // About
            SettingsCard(title = "About", icon = Icons.Default.Info) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("AndroTask", style = MaterialTheme.typography.bodyMedium, color = OnBg)
                    Text("v1.2.0", style = MaterialTheme.typography.bodyMedium, color = Green400)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "No-root macro recorder using Android Accessibility Services.",
                    style = MaterialTheme.typography.bodySmall, color = OnBgVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, null, tint = Green400, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = OnBg)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors   = ButtonDefaults.outlinedButtonColors(contentColor = OnBg),
        border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
