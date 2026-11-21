package app.y8control

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = dynamicDarkColorScheme(this)) {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }
}

private val BT_PERMS = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.POST_NOTIFICATIONS,
)

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.width(4.dp))
            content()
        }
    }
}

@Composable
fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label) }
    else OutlinedButton(onClick = onClick) { Text(label) }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@SuppressLint("BatteryLife")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Screen() {
    val ctx = LocalContext.current
    val s by Headset.state.collectAsState()
    val logs by Headset.log.collectAsState()
    val hearing by HearingAid.running.collectAsState()
    val idle by AutoOff.idleMinutes.collectAsState()
    val connected = s.conn == Conn.CONNECTED

    var permsOk by remember {
        mutableStateOf(BT_PERMS.take(2).all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED })
    }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        permsOk = r[Manifest.permission.BLUETOOTH_CONNECT] == true && r[Manifest.permission.BLUETOOTH_SCAN] == true
        if (permsOk) HeadsetService.start(ctx)
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startHearing(ctx) else Headset.log("Permesso microfono negato")
    }
    LaunchedEffect(Unit) {
        if (permsOk) HeadsetService.start(ctx) else btLauncher.launch(BT_PERMS)
    }

    LazyColumn(Modifier.fillMaxSize().systemBarsPadding()) {
        item {
            Text("Y8 Control", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
        }

        if (!permsOk) item {
            Section("Permessi") {
                Text("Serve il permesso \"Dispositivi nelle vicinanze\" per parlare con le cuffie.")
                Button(onClick = { btLauncher.launch(BT_PERMS) }) { Text("Concedi") }
            }
        }

        item {
            Section("Stato") {
                Text("${s.conn.label}${s.name?.let { " · $it" } ?: ""}")
                if (connected) {
                    Text("Batteria: ${s.battery?.let { "$it%" } ?: "–"}   Firmware: ${s.firmware ?: "–"}")
                    Text("Modello: ${s.variant.label}", fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Headset.refreshAll() }, enabled = connected) { Text("Aggiorna") }
                    OutlinedButton(onClick = { Headset.reconnect() }) { Text("Riconnetti") }
                }
                val pm = ctx.getSystemService(PowerManager::class.java)
                if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                    TextButton(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + ctx.packageName)))
                    }) { Text("⚠ Consenti l'uso in background (necessario per auto-off)") }
                }
            }
        }

        item {
            Section("Cancellazione rumore") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Presets.ANC.forEachIndexed { i, n -> Choice(n, s.anc == i) { Headset.setAnc(i) } }
                }
            }
        }

        item { AdvancedAncSection(connected) }

        item {
            Section("Modalità rapide") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        Headset.setAnc(1); Headset.applyMine(Presets.MINE[0]); Prefs.autoOff = true
                    }) { Text("✈ Viaggio") }
                    OutlinedButton(onClick = { Headset.setGame(true); Headset.setAnc(0) }) { Text("🎮 Gaming") }
                    OutlinedButton(onClick = { Headset.setAnc(2); Headset.applyMine(Presets.MINE[3]) }) { Text("💼 Ufficio") }
                    OutlinedButton(onClick = { Headset.setLdac(true); Headset.setAnc(1); Headset.applyMine(Presets.MINE[2]) }) { Text("🎧 Hi-Fi") }
                    OutlinedButton(onClick = { Headset.setAnc(1); Headset.applyMine(Presets.MINE[4]) }) { Text("🌙 Notte") }
                }
            }
        }

        item { EqSection(s) }

        item {
            Section("Connessione") {
                SwitchRow("Hi-Res (LDAC)", s.ldac == true, "Qualità massima. Disattiva multipoint e game mode") { Headset.setLdac(it) }
                SwitchRow("Game mode", s.game == true, "Meno ritardo audio") { Headset.setGame(it) }
                SwitchRow("Doppia connessione", s.multipoint == true, "Due dispositivi insieme") { Headset.setMultipoint(it) }
            }
        }

        item {
            Section("Autospegnimento intelligente") {
                var on by remember { mutableStateOf(Prefs.autoOff) }
                var min by remember { mutableFloatStateOf(Prefs.autoOffMin.toFloat()) }
                SwitchRow("Spegni se non le uso", on,
                    "Conta solo il tempo senza audio dal telefono" + if (on && connected) " · inattive da $idle min" else "") {
                    on = it; Prefs.autoOff = it
                }
                Text("Dopo ${min.roundToInt()} minuti di silenzio")
                Slider(value = min, onValueChange = { min = it }, valueRange = 5f..120f, steps = 22,
                    onValueChangeFinished = { Prefs.autoOffMin = min.roundToInt() })
                Text("Timer fisso (come l'app originale)" + (s.timerMin?.takeIf { it > 0 }?.let { ": $it min" } ?: ""), fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 120).forEach { m -> OutlinedButton(onClick = { Headset.shutdownIn(m); Headset.send("TA") }) { Text("$m'") } }
                    OutlinedButton(onClick = { Headset.cancelShutdown(); Headset.send("TA") }) { Text("Annulla") }
                }
            }
        }

        item {
            Section("👂 Apparecchio acustico") {
                Text("Amplifica le voci nelle cuffie. Con il canale chiamata il ritardo è minimo " +
                    "(la musica si mette in pausa). Cambiare canale o microfono: spegni e riaccendi.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                var lowLat by remember { mutableStateOf(Prefs.haLowLatency) }
                SwitchRow("Attivo", hearing, "Inizia con volume basso") { on ->
                    if (on) {
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                            startHearing(ctx) else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        HearingAid.stop()
                        HeadsetService.start(ctx, HeadsetService.ACTION_REFRESH_FG)
                    }
                }
                var gain by remember { mutableFloatStateOf(HearingAid.gainDb) }
                var voice by remember { mutableFloatStateOf(HearingAid.voiceDb) }
                var nr by remember { mutableStateOf(HearingAid.noiseReduction) }
                var hp by remember { mutableStateOf(HearingAid.cutRumble) }
                Text("Amplificazione: +${gain.roundToInt()} dB")
                Slider(value = gain, onValueChange = { gain = it; HearingAid.gainDb = it; HearingAid.paramsChanged() },
                    valueRange = 0f..30f, onValueChangeFinished = { Prefs.saveHearing() })
                Text("Chiarezza voci: +${voice.roundToInt()} dB")
                Slider(value = voice, onValueChange = { voice = it; HearingAid.voiceDb = it; HearingAid.paramsChanged() },
                    valueRange = 0f..15f, onValueChangeFinished = { Prefs.saveHearing() })
                SwitchRow("Riduzione rumore di fondo", nr) {
                    nr = it; HearingAid.noiseReduction = it; HearingAid.paramsChanged(); Prefs.saveHearing()
                }
                SwitchRow("Taglia rombo (vento, traffico)", hp) {
                    hp = it; HearingAid.cutRumble = it; HearingAid.paramsChanged(); Prefs.saveHearing()
                }
                var call by remember { mutableStateOf(HearingAid.callMode) }
                var hsMic by remember { mutableStateOf(HearingAid.headsetMic) }
                SwitchRow("Canale chiamata (bassa latenza)", call, "Consigliato. Qualità voce, ritardo ~30-60 ms") {
                    call = it; HearingAid.callMode = it; Prefs.saveHearing()
                }
                if (call) SwitchRow("Microfono delle cuffie", hsMic, "Capta all'altezza delle orecchie. Se fischia, abbassa il volume o usa il telefono") {
                    hsMic = it; HearingAid.headsetMic = it; Prefs.saveHearing()
                }
                if (!call) SwitchRow("Riduci ritardo (game mode automatica)", lowLat) { lowLat = it; Prefs.haLowLatency = it }
            }
        }

        item {
            Section("Musica") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Headset.track(false) }) { Text("⏮") }
                    OutlinedButton(onClick = { Headset.play(true) }) { Text("▶") }
                    OutlinedButton(onClick = { Headset.play(false) }) { Text("⏸") }
                    OutlinedButton(onClick = { Headset.track(true) }) { Text("⏭") }
                }
            }
        }

        item { ToolsSection(logs) }
    }
}

private fun startHearing(ctx: android.content.Context) {
    if (!HearingAid.callMode && Prefs.haLowLatency && Headset.state.value.conn == Conn.CONNECTED) Headset.setGame(true)
    HearingAid.start(ctx)
    HeadsetService.start(ctx, HeadsetService.ACTION_REFRESH_FG)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqSection(s: HeadsetState) {
    Section("Equalizzatore") {
        Text("I miei preset", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Presets.MINE.forEach { p -> OutlinedButton(onClick = { Headset.applyMine(p) }) { Text(p.name) } }
        }
        when (s.variant) {
            Variant.EIGHT_BAND -> {
                Text("Preset 1Mii", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Presets.FACTORY_8.forEach { p -> Choice(p.name, s.eqMode == p.index) { Headset.setEq8(p.index, p.gains) } }
                }
                Text("Personalizzato (8 bande, ±6 dB)", fontWeight = FontWeight.SemiBold)
                val g = remember(s.bands) {
                    mutableStateListOf<Float>().apply {
                        val f = (Headset.eightBandFactor * 10).toFloat()
                        addAll(if (s.bands.size == 8) s.bands.map { it / f } else List(8) { 0f })
                    }
                }
                g.forEachIndexed { i, v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("B${i + 1}", Modifier.width(36.dp))
                        Slider(value = v, onValueChange = { g[i] = (it * 2).roundToInt() / 2f }, valueRange = -6f..6f,
                            modifier = Modifier.weight(1f))
                        Text("%+.1f".format(v), Modifier.width(48.dp))
                    }
                }
                Button(onClick = { Headset.setEq8(0, g.map { it.toDouble() }) }) { Text("Applica") }
                var bt by remember { mutableStateOf(Headset.eightBandFactor < 0.75) }
                SwitchRow("Chip Bluetrum (scala EQ dimezzata)", bt,
                    "Attivalo se l'effetto dell'EQ ti sembra il doppio del previsto") {
                    bt = it; Headset.eightBandFactor = if (it) 0.5 else 1.0; Prefs.saveFactor()
                }
            }
            else -> {
                Text("Preset 1Mii", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Presets.FACTORY_10.forEachIndexed { i, n -> Choice(n, s.eqMode == i) { Headset.setFactoryEq10(i) } }
                }
                Text("Personalizzato (10 bande)", fontWeight = FontWeight.SemiBold)
                val g = remember(s.bands) {
                    mutableStateListOf<Int>().apply { addAll(if (s.bands.size == 10) s.bands else List(10) { 0 }) }
                }
                g.forEachIndexed { i, v ->
                    var cur by remember(v) { mutableIntStateOf(v) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Presets.FREQ_10[i], Modifier.width(40.dp))
                        Slider(value = cur.toFloat(), onValueChange = { cur = it.roundToInt() },
                            onValueChangeFinished = { g[i] = cur; Headset.setBand10(i, cur) },
                            valueRange = -8f..8f, steps = 15, modifier = Modifier.weight(1f))
                        Text("%+d".format(cur), Modifier.width(36.dp))
                    }
                }
                OutlinedButton(onClick = { Headset.send("SL") }) { Text("Azzera") }
                if (s.variant == Variant.UNKNOWN)
                    Text("Modello non ancora rilevato: premi Aggiorna quando sono connesse.", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ToolsSection(logs: List<String>) {
    Section("Strumenti (per test)") {
        var code by remember { mutableStateOf("GA") }
        var data by remember { mutableStateOf("00") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(code, { code = it.take(2).uppercase() }, Modifier.width(80.dp), label = { Text("Cmd") },
                singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            OutlinedTextField(data, { data = it }, Modifier.weight(1f), label = { Text("Dati hex") }, singleLine = true)
            Button(onClick = {
                if (code.length == 2) try { Headset.send(code, Proto.parseHex(data)) } catch (e: Exception) { Headset.log("Hex non valido") }
            }) { Text("Invia") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Headset.readNotifyChar() }) { Text("Leggi FAA2") }
            OutlinedButton(onClick = { Headset.refreshAll() }) { Text("Richiedi stato") }
        }
        Text("Registro", fontWeight = FontWeight.SemiBold)
        logs.take(40).forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
    }
}

@Composable
fun AdvancedAncSection(connected: Boolean) {
    val r by Rcsp.state.collectAsState()
    Section("ANC avanzato (sperimentale, RCSP JieLi)") {
        Text(
            when {
                !r.responded -> "Nessuna risposta dal canale JieLi finora."
                r.lastStatus != null && r.lastStatus != 0 -> "Le cuffie rispondono con errore ${r.lastStatus}: probabile autenticazione richiesta."
                r.modes.isEmpty() && r.current == null -> "Le cuffie rispondono ma non riportano livelli ANC."
                else -> "Livelli ANC disponibili ✔"
            }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Autenticazione: ${r.auth}", fontSize = 13.sp)
        r.current?.let { c ->
            Text("Attuale: ${Presets.ANC.getOrElse(c.mode) { "?" }} · livello ${c.leftCur}/${c.leftMax}")
        }
        val modes = if (r.modes.isNotEmpty()) r.modes.values.sortedBy { it.mode } else listOfNotNull(r.current)
        modes.filter { it.leftMax > 0 || it.rightMax > 0 }.forEach { m ->
            val max = maxOf(m.leftMax, m.rightMax)
            var lv by remember(m) { mutableFloatStateOf(maxOf(m.leftCur, m.rightCur).toFloat()) }
            Text("${Presets.ANC.getOrElse(m.mode) { "Modo ${m.mode}" }}: livello ${lv.roundToInt()} / $max",
                fontWeight = FontWeight.SemiBold)
            Slider(value = lv, onValueChange = { lv = it }, valueRange = 0f..max.toFloat(),
                onValueChangeFinished = { Rcsp.setNoise(m.mode, lv.roundToInt()) })
            if (m.mode == 1) Text("Al massimo può comparire fruscio o pressione: torna indietro se dà fastidio.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (r.attrs.isNotEmpty()) {
            Text("Funzioni riportate:", fontWeight = FontWeight.SemiBold)
            r.attrs.forEach { (k, v) -> Text("• ${Rcsp.ATTR_NAMES[k] ?: "attr $k"} ($k): $v", fontSize = 12.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Rcsp.startAuth() }, enabled = connected) { Text("Autentica") }
            OutlinedButton(onClick = { Rcsp.query() }, enabled = connected) { Text("Leggi") }
            OutlinedButton(onClick = { Rcsp.queryTargetInfo() }, enabled = connected) { Text("Info chip") }
        }
        var op by remember { mutableStateOf("07") }
        var par by remember { mutableStateOf("FF FFFFFFFF") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(op, { op = it.take(2) }, Modifier.width(72.dp), label = { Text("Op") }, singleLine = true)
            OutlinedTextField(par, { par = it }, Modifier.weight(1f), label = { Text("Parametri hex") }, singleLine = true)
            Button(onClick = {
                try { Rcsp.sendRaw(op.toInt(16), Proto.parseHex(par)) } catch (e: Exception) { Headset.log("Hex non valido") }
            }, enabled = connected) { Text("Invia") }
        }
    }
}
