package com.casmirx.cx

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku

val Ink = Color(0xFF000000)
val Panel = Color(0xFF0B0D12)
val CxRed = Color(0xFFFF2D4A)
val CxBlue = Color(0xFF2D7BFF)
val Dim = Color(0xFF8A93A6)

data class Level(val name: String, val tag: String, val ratio: Double, val extreme: Boolean = false)

val LEVELS = listOf(
    Level("16:10", "light", 1.60),
    Level("3:2", "mild", 1.50),
    Level("4:3", "classic", 1.3333),
    Level("5:4", "strong", 1.25),
    Level("1:1", "ultra", 1.00, extreme = true),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Panel, primary = CxRed, secondary = CxBlue)) {
                Surface(Modifier.fillMaxSize(), color = Ink) { CXScreen() }
            }
        }
    }
}

@Composable
fun CXScreen() {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var stretchOn by remember { mutableStateOf(false) }
    var autoOn by remember { mutableStateOf(Prefs.auto(ctx)) }
    var extra by remember { mutableStateOf(Prefs.extra(ctx)) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var ready by remember { mutableStateOf(Shell.ready()) }
    var native by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var nativeDpi by remember { mutableStateOf<Int?>(null) }
    var status by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(LEVELS.find { kotlin.math.abs(it.ratio - Prefs.ratio(ctx)) < 0.001 } ?: LEVELS[2]) }
    var cw by remember { mutableStateOf("") }
    var ch by remember { mutableStateOf("") }
    var dpi by remember { mutableStateOf("") }
    var countdown by remember { mutableStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var pendingReset by remember { mutableStateOf("wm size reset") }

    fun refresh() = scope.launch(Dispatchers.IO) {
        if (Shell.ready()) { native = Shell.nativeSize(); nativeDpi = Shell.nativeDensity(); stretchOn = Shell.isStretched() }
    }

    DisposableEffect(Unit) {
        val onBinder = Shizuku.OnBinderReceivedListener { ready = Shell.ready(); refresh() }
        val onDead = Shizuku.OnBinderDeadListener { ready = false }
        val onPerm = Shizuku.OnRequestPermissionResultListener { _, _ -> ready = Shell.ready(); refresh() }
        Shizuku.addBinderReceivedListenerSticky(onBinder)
        Shizuku.addBinderDeadListener(onDead)
        Shizuku.addRequestPermissionResultListener(onPerm)
        onDispose {
            Shizuku.removeBinderReceivedListener(onBinder)
            Shizuku.removeBinderDeadListener(onDead)
            Shizuku.removeRequestPermissionResultListener(onPerm)
        }
    }
    LaunchedEffect(ready) { if (ready) refresh() }

    // Apply a change, then give 15 seconds to confirm. Shell-side watchdog is the backup.
    fun applyWithRevert(cmd: String, reset: String) {
        job?.cancel()
        pendingReset = reset
        job = scope.launch {
            withContext(Dispatchers.IO) { Shell.armWatchdog(reset); Shell.run(cmd) }
            for (i in 15 downTo 1) { countdown = i; delay(1000) }
            withContext(Dispatchers.IO) { Shell.run(reset) }
            countdown = 0
            status = "Reverted"
        }
    }

    fun applySize(w: Int, h: Int) {
        val n = native ?: return
        if (w < 480 || h < 480 || w > n.first || h > n.second) {
            status = "Out of safe range: width 480-${n.first}, height 480-${n.second}"; return
        }
        applyWithRevert("wm size ${w}x${h}", "wm size reset")
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("C", fontSize = 40.sp, fontWeight = FontWeight.Black, color = CxRed, fontFamily = FontFamily.Monospace)
            Text("X", fontSize = 40.sp, fontWeight = FontWeight.Black, color = CxBlue, fontFamily = FontFamily.Monospace)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (ready) CxBlue else CxRed))
            Text(if (ready) "Shizuku connected" else "Shizuku not connected", color = Dim, fontSize = 13.sp)
        }
        if (!ready) {
            Button(
                onClick = { try { if (Shizuku.pingBinder()) Shizuku.requestPermission(0) else status = "Start Shizuku first" } catch (e: Throwable) { status = "Start Shizuku first" } },
                colors = ButtonDefaults.buttonColors(containerColor = CxBlue)
            ) { Text("Grant access") }
        }

        Card("Stretch") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(if (stretchOn) "ON" else "OFF", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (stretchOn) CxRed else Dim, fontFamily = FontFamily.Monospace)
                    Text("Level ${selected.name}", color = Dim, fontSize = 12.sp)
                }
                Switch(
                    checked = stretchOn, enabled = ready && native != null,
                    onCheckedChange = { on ->
                        scope.launch(Dispatchers.IO) {
                            if (on) Shell.applyRatio(selected.ratio) else Shell.reset()
                            stretchOn = Shell.isStretched()
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = CxRed, uncheckedTrackColor = Panel, checkedThumbColor = Color.White)
                )
            }
        }

        Card("Auto mode") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Stretch when CODM opens", color = Color.White, fontSize = 14.sp)
                    Text("Normal again when you leave", color = Dim, fontSize = 12.sp)
                }
                Switch(
                    checked = autoOn, enabled = ready,
                    onCheckedChange = { on ->
                        autoOn = on; Prefs.setAuto(ctx, on)
                        if (on) {
                            if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                            AutoService.start(ctx)
                        } else AutoService.stop(ctx)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = CxBlue, uncheckedTrackColor = Panel, checkedThumbColor = Color.White)
                )
            }
            Field(extra, { extra = it }, "Extra game package (optional)", Modifier.fillMaxWidth(), digitsOnly = false)
            OutlinedButton(onClick = { Prefs.setExtra(ctx, extra); status = "Saved" }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, CxBlue)) { Text("Save package", color = Color.White) }
        }

        Card("Display") {
            Text("Native  ${native?.let { "${it.first}x${it.second}" } ?: "-"}   ${nativeDpi?.let { "${it}dpi" } ?: ""}", color = Dim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }

        Card("Stretch level") {
            LEVELS.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { lv ->
                        val on = selected == lv
                        val c = if (lv.extreme) CxRed else CxBlue
                        OutlinedButton(
                            onClick = {
                                selected = lv; Prefs.setRatio(ctx, lv.ratio)
                                if (stretchOn) scope.launch(Dispatchers.IO) { Shell.applyRatio(lv.ratio) }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (on) c else Color(0xFF222733)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (on) c.copy(alpha = 0.15f) else Color.Transparent, contentColor = Color.White)
                        ) { Text("${lv.name}  ·  ${lv.tag}", fontSize = 13.sp) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Card("Custom size") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(cw, { cw = it }, "Width", Modifier.weight(1f))
                Field(ch, { ch = it }, "Height", Modifier.weight(1f))
            }
            OutlinedButton(
                enabled = ready,
                onClick = { val w = cw.toIntOrNull(); val h = ch.toIntOrNull(); if (w != null && h != null) applySize(w, h) else status = "Enter width and height" },
                modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, CxBlue)
            ) { Text("Apply custom", color = Color.White) }
        }

        Card("Density (optional)") {
            Field(dpi, { dpi = it }, "DPI (e.g. 420)", Modifier.fillMaxWidth())
            OutlinedButton(
                enabled = ready,
                onClick = {
                    val d = dpi.toIntOrNull()
                    if (d == null || d < 240 || d > 640) status = "DPI must be 240-640"
                    else applyWithRevert("wm density $d", "wm density reset")
                },
                modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, CxBlue)
            ) { Text("Apply density", color = Color.White) }
        }

        Button(
            enabled = ready,
            onClick = {
                job?.cancel(); countdown = 0
                scope.launch(Dispatchers.IO) { Shell.run("wm size reset"); Shell.run("wm density reset"); stretchOn = false }
                status = "Reset to native"
            },
            modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = CxBlue)
        ) { Text("Reset to native") }

        if (status.isNotEmpty()) Text(status, color = Dim, fontSize = 12.sp)
    }

    if (countdown > 0) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = Panel,
            title = { Text("Keep this setting?", color = Color.White) },
            text = { Text("Reverting in $countdown s", color = Dim) },
            confirmButton = {
                TextButton(onClick = {
                    job?.cancel(); countdown = 0
                    scope.launch(Dispatchers.IO) { Shell.keep() }
                    status = "Kept"
                }) { Text("Keep", color = CxBlue) }
            },
            dismissButton = {
                TextButton(onClick = {
                    job?.cancel(); countdown = 0
                    scope.launch(Dispatchers.IO) { Shell.run(pendingReset) }
                    status = "Reverted"
                }) { Text("Revert", color = CxRed) }
            }
        )
    }
}

@Composable
fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title.uppercase(), color = Dim, fontSize = 11.sp, letterSpacing = 2.sp)
        content()
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier, digitsOnly: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(if (digitsOnly) it.filter(Char::isDigit) else it) },
        label = { Text(label) }, singleLine = true, modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CxBlue, unfocusedBorderColor = Color(0xFF222733))
    )
}
