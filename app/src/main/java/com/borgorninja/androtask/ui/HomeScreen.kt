package com.borgorninja.androtask.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.borgorninja.androtask.FloatingOverlayService
import com.borgorninja.androtask.MacroAccessibilityService
import com.borgorninja.androtask.data.MacroWithSteps
import com.borgorninja.androtask.viewmodel.MacroViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, vm: MacroViewModel) {
    val context  = LocalContext.current
    val macrosWS by vm.macrosWithSteps.collectAsState()

    val accessOk  = isAccessibilityEnabled(context)
    val overlayOk = android.provider.Settings.canDrawOverlays(context)

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Text("AndroTask",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = Green400)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, "Settings", tint = OnBgVariant)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("editor/0") },
                containerColor = Green400,
                contentColor   = Color(0xFF003300)
            ) { Icon(Icons.Default.Add, "New macro") }
        }
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(
                top    = pad.calculateTopPadding() + 8.dp,
                bottom = pad.calculateBottomPadding() + 80.dp,
                start  = 14.dp, end = 14.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // Permission banner
            if (!accessOk || !overlayOk) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors   = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape    = RoundedCornerShape(12.dp),
                        onClick  = { navController.navigate("onboarding") }
                    ) {
                        Row(Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null,
                                tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(10.dp))
                            Text("Permissions needed — tap to set up",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null,
                                tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            if (macrosWS.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize().padding(top=80.dp),
                        contentAlignment = Alignment.TopCenter) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                               verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.RadioButtonChecked, null,
                                modifier = Modifier.size(56.dp), tint = OnBgVariant)
                            Text("No macros yet",
                                style = MaterialTheme.typography.titleMedium, color = OnBg)
                            Text("Tap + to record your first macro",
                                style = MaterialTheme.typography.bodySmall, color = OnBgVariant)
                        }
                    }
                }
            } else {
                items(macrosWS, key = { it.macro.id }) { mws ->
                    MacroCard(
                        mws      = mws,
                        onEdit   = { navController.navigate("editor/${mws.macro.id}") },
                        onPlay   = {
                            if (!accessOk) {
                                Toast.makeText(context,
                                    "Enable Accessibility Service first!", Toast.LENGTH_LONG).show()
                                navController.navigate("onboarding")
                                return@MacroCard
                            }
                            if (MacroAccessibilityService.instance == null) {
                                Toast.makeText(context,
                                    "Accessibility service not connected. Re-enable it in settings.",
                                    Toast.LENGTH_LONG).show()
                                return@MacroCard
                            }
                            vm.playMacro(mws.macro.id)
                            Toast.makeText(context, "▶ ${mws.macro.name}",
                                Toast.LENGTH_SHORT).show()
                        },
                        onDelete = { vm.deleteMacro(mws.macro.id) },
                        onToggle = { vm.setMacroEnabled(mws.macro.id, it) }
                    )
                }
            }
        }
    }
}

@Composable
fun MacroCard(
    mws: MacroWithSteps,
    onEdit: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val macro        = mws.macro
    var confirmDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = CardDark),
        onClick  = onEdit
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot
            Box(
                Modifier
                    .size(10.dp)
                    .then(Modifier.padding(end = 0.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (macro.isEnabled) Icons.Default.Circle else Icons.Default.RadioButtonUnchecked,
                    null,
                    tint     = if (macro.isEnabled) Green400 else OnBgVariant,
                    modifier = Modifier.size(10.dp)
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(macro.name,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (macro.isEnabled) OnBg else OnBgVariant)
                Text(
                    "${mws.steps.size} step${if (mws.steps.size == 1) "" else "s"}" +
                    (if (macro.loopCount > 1) "  ·  ×${macro.loopCount} loops" else ""),
                    style = MaterialTheme.typography.bodySmall, color = OnBgVariant
                )
            }

            // Enable/disable
            Switch(
                checked = macro.isEnabled, onCheckedChange = onToggle,
                colors  = SwitchDefaults.colors(
                    checkedTrackColor   = Green700,
                    uncheckedTrackColor = Color(0xFF333333)
                ),
                modifier = Modifier.padding(end = 4.dp)
            )

            // Play
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayCircle, "Play",
                    tint     = if (macro.isEnabled) Green400 else OnBgVariant,
                    modifier = Modifier.size(36.dp))
            }

            // Delete
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.DeleteOutline, "Delete",
                    tint = Color(0xFF666666), modifier = Modifier.size(20.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor   = CardDark,
            title = { Text("Delete \"${macro.name}\"?") },
            text  = { Text("This can't be undone.", color = OnBgVariant) },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirmDelete = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}
