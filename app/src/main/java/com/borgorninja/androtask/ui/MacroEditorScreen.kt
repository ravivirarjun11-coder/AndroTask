package com.borgorninja.androtask.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.borgorninja.androtask.FloatingOverlayService
import com.borgorninja.androtask.MacroAccessibilityService
import com.borgorninja.androtask.data.MacroStep
import com.borgorninja.androtask.data.StepType
import com.borgorninja.androtask.viewmodel.MacroViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroEditorScreen(navController: NavController, macroId: Long, vm: MacroViewModel) {
    val context      = LocalContext.current
    val currentMacro by vm.currentMacro.collectAsState()

    var name         by remember { mutableStateOf("") }
    var loopCount    by remember { mutableStateOf("1") }
    val steps        = remember { mutableStateListOf<MacroStep>() }
    var isRecording  by remember { mutableStateOf(false) }
    var initialized  by remember { mutableStateOf(false) }
    var editingStep  by remember { mutableStateOf<Pair<Int, MacroStep>?>(null) }

    LaunchedEffect(macroId) {
        initialized = false
        if (macroId != 0L) vm.loadMacro(macroId)
    }

    LaunchedEffect(currentMacro) {
        if (!initialized && macroId != 0L && currentMacro != null) {
            currentMacro!!.let { mws ->
                name      = mws.macro.name
                loopCount = if (mws.macro.loopCount < 1) "1" else mws.macro.loopCount.toString()
                steps.clear(); steps.addAll(mws.steps)
            }
            initialized = true
        }
    }

    // Poll recording state and sync step list
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            val svcRec = MacroAccessibilityService.isRecording
            if (isRecording != svcRec) isRecording = svcRec
            if (isRecording) {
                val captured = MacroAccessibilityService.recordedSteps.toList()
                if (captured.size != steps.size) {
                    steps.clear()
                    steps.addAll(captured.map { it.copy(macroId = macroId) })
                }
            }
        }
    }

    val accessOk = isAccessibilityEnabled(context)

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = { Text(if (macroId == 0L) "New Macro" else "Edit Macro",
                    fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark, titleContentColor = OnBg),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnBg)
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Macro name
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("Macro name") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Green400,
                    unfocusedBorderColor = Color(0xFF444444)
                )
            )

            // Loop count row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value         = loopCount,
                    onValueChange = { loopCount = it.filter { c -> c.isDigit() } },
                    label         = { Text("Loops") },
                    modifier      = Modifier.width(100.dp),
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Green400,
                        unfocusedBorderColor = Color(0xFF444444)
                    )
                )
                Text("times", style = MaterialTheme.typography.bodySmall, color = OnBgVariant)
            }

            // ── Big record button ──────────────────────────────────────────────
            Button(
                onClick = {
                    if (!accessOk) {
                        Toast.makeText(context, "Enable Accessibility Service first!",
                            Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (MacroAccessibilityService.instance == null) {
                        Toast.makeText(context,
                            "Accessibility service not connected. Re-enable it in settings.",
                            Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (isRecording) {
                        // Stop recording — service stops itself and bubble disappears
                        context.startService(
                            Intent(context, FloatingOverlayService::class.java)
                                .putExtra(FloatingOverlayService.CMD, FloatingOverlayService.CMD_STOP)
                        )
                    } else {
                        // Clear previous steps and start
                        MacroAccessibilityService.recordedSteps.clear()
                        steps.clear()
                        // Start the overlay service which shows the bubble + overlay
                        context.startService(
                            Intent(context, FloatingOverlayService::class.java)
                                .putExtra(FloatingOverlayService.CMD, FloatingOverlayService.CMD_START)
                        )
                        Toast.makeText(context,
                            "Recording started — perform your gestures, then tap the bubble to stop",
                            Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color(0xFFD32F2F) else Color(0xFF1B5E20)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    if (isRecording) Icons.Default.Stop
                    else Icons.Default.RadioButtonChecked,
                    null, modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isRecording) "Stop recording  (${steps.size} captured)"
                    else "Start recording",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (isRecording) {
                Text(
                    "Perform your gestures on any app — they're being captured",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnBgVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // ── Step list ──────────────────────────────────────────────────────
            if (steps.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recorded steps (${steps.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnBgVariant,
                        modifier = Modifier.weight(1f))
                    if (!isRecording) {
                        TextButton(onClick = { steps.clear() }) {
                            Text("Clear", color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                LazyColumn(
                    modifier             = Modifier.weight(1f),
                    verticalArrangement  = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(steps, key = { i, _ -> i }) { index, step ->
                        SimpleStepRow(
                            step    = step,
                            index   = index,
                            onEdit  = { editingStep = Pair(index, step) },
                            onDelete = {
                                steps.removeAt(index)
                            }
                        )
                    }
                }
            } else if (!isRecording) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No steps yet — press Start recording",
                        style = MaterialTheme.typography.bodySmall, color = OnBgVariant)
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            // ── Save button ────────────────────────────────────────────────────
            Button(
                onClick = {
                    val n = name.trim()
                    if (n.isEmpty()) {
                        Toast.makeText(context, "Enter a name", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (steps.isEmpty()) {
                        Toast.makeText(context, "Record at least one step",
                            Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val loops = loopCount.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    vm.saveMacro(
                        name            = n,
                        description     = "",
                        loopCount       = loops,
                        loopDelay       = 0L,
                        speedMultiplier = 1f,
                        steps           = steps.toList(),
                        macroId         = macroId
                    ) {
                        Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                },
                modifier  = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                enabled   = !isRecording && steps.isNotEmpty(),
                colors    = ButtonDefaults.buttonColors(containerColor = Green700),
                shape     = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("Save macro", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    editingStep?.let { (index, step) ->
        StepEditDialog(
            step      = step,
            onSave    = { updated ->
                steps[index] = updated.copy(macroId = macroId, stepIndex = index)
                editingStep  = null
            },
            onDismiss = { editingStep = null }
        )
    }
}

// ── Simple read-only step row ─────────────────────────────────────────────────
@Composable
fun SimpleStepRow(
    step: MacroStep, index: Int,
    onEdit: () -> Unit, onDelete: () -> Unit
) {
    val accent = stepTypeColor(step.type)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(8.dp),
        colors   = CardDefaults.cardColors(containerColor = CardDark),
        onClick  = onEdit
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color strip
            Box(
                Modifier
                    .width(3.dp).height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(10.dp))

            // Step number
            Text("${index + 1}.",
                style = MaterialTheme.typography.labelSmall,
                color = OnBgVariant,
                modifier = Modifier.width(24.dp))

            // Type badge
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    step.type.name.replace('_', ' ').lowercase()
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = accent
                )
            }
            Spacer(Modifier.width(8.dp))

            // Coords / info
            Text(
                when (step.type) {
                    StepType.SWIPE -> "(${step.x.toInt()},${step.y.toInt()})→(${step.x2.toInt()},${step.y2.toInt()})"
                    StepType.WAIT  -> "${step.duration}ms"
                    StepType.TYPE_TEXT -> "\"${step.text.take(20)}\""
                    StepType.BACK, StepType.HOME, StepType.RECENTS, StepType.NOTIFICATIONS,
                    StepType.VOLUME_UP, StepType.VOLUME_DOWN -> ""
                    else -> "(${step.x.toInt()}, ${step.y.toInt()})  ${step.duration}ms"
                },
                style = MaterialTheme.typography.bodySmall,
                color = OnBgVariant,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Remove",
                    modifier = Modifier.size(16.dp), tint = Color(0xFF666666))
            }
        }
    }
}
