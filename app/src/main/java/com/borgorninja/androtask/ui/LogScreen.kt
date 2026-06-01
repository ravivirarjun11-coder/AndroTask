package com.borgorninja.androtask.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.borgorninja.androtask.AppLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(navController: NavController) {
    val context    = LocalContext.current
    var entries    by remember { mutableStateOf(AppLogger.entries()) }
    var autoScroll by remember { mutableStateOf(true) }
    var filter     by remember { mutableStateOf("") }
    val listState  = rememberLazyListState()

    // Refresh every second
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            entries = AppLogger.entries()
        }
    }

    val shown = remember(entries, filter) {
        if (filter.isBlank()) entries
        else entries.filter {
            it.tag.contains(filter, ignoreCase = true) ||
            it.message.contains(filter, ignoreCase = true)
        }
    }

    LaunchedEffect(shown.size, autoScroll) {
        if (autoScroll && shown.isNotEmpty())
            listState.animateScrollToItem(shown.size - 1)
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Debug Log", fontWeight = FontWeight.SemiBold)
                        Text("${entries.size} entries", style = MaterialTheme.typography.labelSmall,
                            color = OnBgVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark, titleContentColor = OnBg),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnBg)
                    }
                },
                actions = {
                    // Auto-scroll toggle
                    IconButton(onClick = { autoScroll = !autoScroll }) {
                        Icon(
                            if (autoScroll) Icons.Default.VerticalAlignBottom
                            else Icons.Default.PauseCircle,
                            "Auto-scroll",
                            tint = if (autoScroll) Green400 else OnBgVariant
                        )
                    }
                    // Export
                    IconButton(onClick = { exportLog(context) }) {
                        Icon(Icons.Default.Share, "Export", tint = OnBgVariant)
                    }
                    // Clear
                    IconButton(onClick = { AppLogger.clear(); entries = emptyList() }) {
                        Icon(Icons.Default.DeleteSweep, "Clear", tint = OnBgVariant)
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {

            // Filter bar
            OutlinedTextField(
                value         = filter,
                onValueChange = { filter = it },
                placeholder   = { Text("Filter by tag or message…", fontSize = 13.sp) },
                modifier      = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                singleLine    = true,
                leadingIcon   = { Icon(Icons.Default.Search, null, modifier=Modifier.size(18.dp)) },
                trailingIcon  = if (filter.isNotEmpty()) ({
                    IconButton(onClick={filter=""}) {
                        Icon(Icons.Default.Close, null, modifier=Modifier.size(16.dp))
                    }
                }) else null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Green400,
                    unfocusedBorderColor = Color(0xFF333333)
                )
            )

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (entries.isEmpty()) "No log entries yet"
                        else "No entries match \"$filter\"",
                        color = OnBgVariant
                    )
                }
            } else {
                LazyColumn(
                    state           = listState,
                    modifier        = Modifier.fillMaxSize(),
                    contentPadding  = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(shown) { entry ->
                        LogRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: AppLogger.Entry) {
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val (bg, textColor) = when (entry.level) {
        AppLogger.Level.E -> Color(0xFF2A0000) to Color(0xFFFF8A80)
        AppLogger.Level.W -> Color(0xFF2A1A00) to Color(0xFFFFCC80)
        AppLogger.Level.I -> Color(0xFF0A1A0A) to Color(0xFFB9F6CA)
        AppLogger.Level.D -> Color.Transparent to OnBgVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            timeFmt.format(Date(entry.time)),
            fontFamily = FontFamily.Monospace,
            fontSize   = 11.sp, color = OnBgVariant,
            modifier   = Modifier.width(80.dp)
        )
        Spacer(Modifier.width(4.dp))

        // Level badge
        val (badgeBg, badgeFg) = when (entry.level) {
            AppLogger.Level.E -> Color(0xFFD32F2F) to Color.White
            AppLogger.Level.W -> Color(0xFFF57F17) to Color.Black
            AppLogger.Level.I -> Color(0xFF1B5E20) to Color.White
            AppLogger.Level.D -> Color(0xFF37474F) to Color.White
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(badgeBg)
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(entry.level.name, fontSize = 10.sp, color = badgeFg,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.width(4.dp))

        Text(
            "[${entry.tag}]",
            fontSize = 11.sp, color = Green400,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 60.dp, max = 90.dp)
        )
        Spacer(Modifier.width(4.dp))

        Text(
            entry.message,
            fontSize = 11.sp, color = textColor,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun exportLog(context: Context) {
    try {
        val content = AppLogger.export()
        val ts      = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file    = File(context.cacheDir, "androtask_log_$ts.txt")
        file.writeText(content)
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type      = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "AndroTask Debug Log $ts")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "Export log"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        AppLogger.e("LogScreen", "Export failed: ${e.message}")
    }
}
