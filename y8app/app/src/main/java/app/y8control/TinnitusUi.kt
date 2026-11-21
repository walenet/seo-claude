package app.y8control

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Valori salvati per il test e l'acufene */
object TinPrefs {
    private fun sp(c: Context) = c.getSharedPreferences("tinnitus", Context.MODE_PRIVATE)
    fun freq(c: Context, ear: Int) = sp(c).getFloat("f$ear", 4000f).toDouble()
    fun setFreq(c: Context, ear: Int, f: Double) = sp(c).edit().putFloat("f$ear", f.toFloat()).apply()
    fun ear(c: Context) = sp(c).getInt("ear", 0)
    fun setEar(c: Context, e: Int) = sp(c).edit().putInt("ear", e).apply()
    fun audiogram(c: Context): String = sp(c).getString("audiogram", "") ?: ""
    fun setAudiogram(c: Context, s: String) = sp(c).edit().putString("audiogram", s).apply()
}

private val EARS = listOf("Sinistra", "Destra", "Entrambe")

private fun applyEar(ear: Int) {
    SoundEngine.left = if (ear == 1) 0.0 else 1.0
    SoundEngine.right = if (ear == 0) 0.0 else 1.0
}

private const val F_MIN = 125.0
private const val F_MAX = 14000.0
private fun posToFreq(p: Float) = F_MIN * (F_MAX / F_MIN).pow(p.toDouble())
private fun freqToPos(f: Double) = (ln(f / F_MIN) / ln(F_MAX / F_MIN)).toFloat().coerceIn(0f, 1f)
private fun fmtHz(f: Double) = if (f >= 1000) "%.1f kHz".format(f / 1000) else "${f.roundToInt()} Hz"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TestSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playing by SoundEngine.playing.collectAsState()

    Section("🧪 Test: udito e frequenza dell'acufene") {
        Text("Non è un esame medico: i valori sono relativi a telefono e cuffie. Fai i test in un posto " +
            "silenzioso, con anti-rumore acceso e volume del telefono a metà.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // ---------- ricerca frequenza acufene ----------
        Text("1 · Trova la frequenza del tuo acufene", fontWeight = FontWeight.SemiBold)
        var ear by remember { mutableIntStateOf(TinPrefs.ear(ctx)) }
        var pos by remember { mutableFloatStateOf(freqToPos(TinPrefs.freq(ctx, if (ear == 1) 1 else 0))) }
        var vol by remember { mutableFloatStateOf(-45f) }
        var noisy by remember { mutableStateOf(false) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EARS.forEachIndexed { i, n -> Choice(n, ear == i) { ear = i; applyEar(i); TinPrefs.setEar(ctx, i) } }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Choice("Fischio (tono)", !noisy) { noisy = false; SoundEngine.kind = SoundEngine.Kind.TONE; SoundEngine.changed() }
            Choice("Fruscio (rumore)", noisy) { noisy = true; SoundEngine.kind = SoundEngine.Kind.NARROW; SoundEngine.changed() }
        }
        Text("Frequenza: ${fmtHz(posToFreq(pos))}")
        Slider(value = pos, onValueChange = { pos = it; SoundEngine.freq = posToFreq(it); SoundEngine.changed() })
        Text("Volume: ${vol.roundToInt()} dB")
        Slider(value = vol, onValueChange = { vol = it; SoundEngine.levelDb = it.toDouble() }, valueRange = -80f..-15f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (playing) SoundEngine.stop() else {
                    SoundEngine.kind = if (noisy) SoundEngine.Kind.NARROW else SoundEngine.Kind.TONE
                    SoundEngine.freq = posToFreq(pos); SoundEngine.levelDb = vol.toDouble()
                    SoundEngine.pulse = false; SoundEngine.stopAtMillis = 0; applyEar(ear); SoundEngine.start()
                }
            }) { Text(if (playing) "■ Stop" else "▶ Ascolta") }
            OutlinedButton(onClick = {
                val f = posToFreq(pos)
                if (ear != 1) TinPrefs.setFreq(ctx, 0, f)
                if (ear != 0) TinPrefs.setFreq(ctx, 1, f)
                Headset.log("Frequenza acufene salvata: ${fmtHz(f)} (${EARS[ear]})")
            }) { Text("Salva: è questa") }
        }
        Text("Consiglio: l'acufene è spesso confuso di un'ottava. Quando trovi il punto, prova anche " +
            "metà e doppio della frequenza e scegli quello più simile.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // ---------- test dell'udito ----------
        Text("2 · Test dell'udito per orecchio", fontWeight = FontWeight.SemiBold)
        val freqs = remember { listOf(250.0, 500.0, 1000.0, 2000.0, 3000.0, 4000.0, 6000.0, 8000.0, 10000.0, 12000.0) }
        val results = remember { mutableStateMapOf<String, Double>() }
        var status by remember { mutableStateOf(TinPrefs.audiogram(ctx).ifEmpty { "Premi Inizia e tocca \"Lo sento!\" appena senti il bip." }) }
        var heard by remember { mutableStateOf(false) }
        var job by remember { mutableStateOf<Job?>(null) }
        var running by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (running) {
                    job?.cancel(); SoundEngine.stop(); SoundEngine.pulse = false
                    running = false; status = "Test interrotto"
                } else {
                results.clear()
                running = true
                job = scope.launch {
                    SoundEngine.kind = SoundEngine.Kind.TONE; SoundEngine.pulse = true; SoundEngine.stopAtMillis = 0
                    for (e in 0..1) for (f in freqs) {
                        applyEar(e); SoundEngine.freq = f; SoundEngine.changed()
                        var lvl = -80.0
                        heard = false
                        SoundEngine.levelDb = lvl
                        SoundEngine.start()
                        while (!heard && lvl < -10.0) {
                            status = "${EARS[e]} · ${fmtHz(f)} · ${lvl.roundToInt()} dB"
                            delay(1600)
                            if (!heard) { lvl += 5; SoundEngine.levelDb = lvl }
                        }
                        results["$e-${f.roundToInt()}"] = if (heard) lvl else 0.0
                        SoundEngine.levelDb = -90.0
                        delay(700 + (0..900).random().toLong())  // pausa casuale: evita di "indovinare" il ritmo
                    }
                    SoundEngine.stop(); SoundEngine.pulse = false
                    val txt = buildString {
                        append("Soglie (dB rel., più basso = senti meglio):\n")
                        for (f in freqs) {
                            val l = results["0-${f.roundToInt()}"]; val r = results["1-${f.roundToInt()}"]
                            append("${fmtHz(f)}:  SX ${l?.let { if (it == 0.0) "non sentito" else "${it.roundToInt()}" } ?: "-"}" +
                                "   DX ${r?.let { if (it == 0.0) "non sentito" else "${it.roundToInt()}" } ?: "-"}\n")
                        }
                    }
                    status = txt
                    TinPrefs.setAudiogram(ctx, txt)
                    running = false
                }
                }
            }) { Text(if (running) "■ Interrompi" else "Inizia test") }
            Button(onClick = { heard = true }, enabled = running) { Text("Lo sento!") }
        }
        Text(status, fontSize = 13.sp)
        Text("Una differenza di 15–20 dB o più tra i due orecchi, o un calo marcato su una frequenza, " +
            "è un buon motivo per fare un esame audiometrico vero.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TinnitusSection() {
    val ctx = LocalContext.current
    val playing by SoundEngine.playing.collectAsState()
    Section("🌿 Sollievo acufene") {
        Text("Suoni di sollievo da usare a volume basso: devono confondersi con il fischio, non coprirlo del tutto. " +
            "Non curano l'acufene ma a molte persone danno sollievo, soprattutto la sera.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        var ear by remember { mutableIntStateOf(TinPrefs.ear(ctx)) }
        var kind by remember { mutableStateOf(SoundEngine.Kind.PINK) }
        var vol by remember { mutableFloatStateOf(-40f) }
        var bal by remember { mutableFloatStateOf(0f) }
        var timer by remember { mutableIntStateOf(30) }
        val f = remember(ear) { TinPrefs.freq(ctx, if (ear == 1) 1 else 0) }
        var freq by remember(f) { mutableDoubleStateOf(f) }

        Text("Orecchio", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EARS.forEachIndexed { i, n -> Choice(n, ear == i) { ear = i; TinPrefs.setEar(ctx, i) } }
        }
        if (ear == 2) {
            Text("Bilanciamento: ${if (bal < -0.05f) "più a sinistra" else if (bal > 0.05f) "più a destra" else "centrale"}")
            Slider(value = bal, onValueChange = {
                bal = it; SoundEngine.left = (1 - it).coerceAtMost(1f).toDouble(); SoundEngine.right = (1 + it).coerceAtMost(1f).toDouble()
            }, valueRange = -1f..1f)
        }
        Text("Suono", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SoundEngine.Kind.entries.filter { it != SoundEngine.Kind.TONE }.forEach { k ->
                Choice(k.label, kind == k) { kind = k; SoundEngine.kind = k; SoundEngine.changed() }
            }
        }
        if (kind == SoundEngine.Kind.NARROW || kind == SoundEngine.Kind.NOTCHED) {
            Text("Centrato sulla tua frequenza: ${fmtHz(freq)} (dal test)", fontSize = 13.sp)
            if (kind == SoundEngine.Kind.NOTCHED)
                Text("Terapia a tacca: rumore rilassante con un \"buco\" sulla frequenza del fischio. Si usa 1–2 ore " +
                    "al giorno per settimane; le prove scientifiche sono contrastanti.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Volume: ${vol.roundToInt()} dB")
        Slider(value = vol, onValueChange = { vol = it; SoundEngine.levelDb = it.toDouble() }, valueRange = -75f..-12f)
        Text("Timer", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "Nessuno", 15 to "15 min", 30 to "30 min", 60 to "1 ora", 120 to "2 ore").forEach { (m, l) ->
                Choice(l, timer == m) { timer = m }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                if (playing) SoundEngine.stop() else {
                    SoundEngine.kind = kind; SoundEngine.freq = freq; SoundEngine.levelDb = vol.toDouble()
                    SoundEngine.pulse = false
                    if (ear == 2) { SoundEngine.left = (1 - bal).coerceAtMost(1f).toDouble(); SoundEngine.right = (1 + bal).coerceAtMost(1f).toDouble() }
                    else applyEar(ear)
                    SoundEngine.stopAtMillis = if (timer > 0) System.currentTimeMillis() + timer * 60_000L else 0
                    SoundEngine.changed(); SoundEngine.start()
                }
            }) { Text(if (playing) "■ Stop" else "▶ Avvia") }
            OutlinedButton(onClick = {
                // inibizione residua: 60 s di rumore stretto sulla frequenza, poi silenzio
                SoundEngine.kind = SoundEngine.Kind.NARROW; SoundEngine.freq = freq
                SoundEngine.levelDb = (vol + 10f).toDouble(); SoundEngine.pulse = false
                applyEar(ear); SoundEngine.stopAtMillis = System.currentTimeMillis() + 60_000
                SoundEngine.changed(); SoundEngine.start()
                Headset.log("Inibizione residua: 60 s, poi ascolta se il fischio è più debole")
            }) { Text("Inibizione 60 s") }
        }
        Text("Inibizione residua: dopo 60 secondi di rumore sulla tua frequenza, in alcune persone il fischio " +
            "si attenua per secondi o minuti. Se succede, è un buon segno per la terapia sonora.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("Consigli utili", fontWeight = FontWeight.SemiBold)
        listOf(
            "Acufene solo da un lato, comparso da poco o pulsante (a tempo col battito): fai una visita da un otorino, senza aspettare.",
            "Perdita d'udito improvvisa: è un'urgenza, vai al pronto soccorso o da un otorino entro 24–48 ore.",
            "Tieni il volume basso: usa l'anti-rumore così non devi alzare la musica.",
            "Evita il silenzio totale, soprattutto la sera: un suono di fondo leggero aiuta il cervello a \"spegnere\" il fischio.",
            "Stress, poco sonno, caffeina, alcol e nicotina spesso lo peggiorano.",
            "Le terapie con più prove sono la terapia cognitivo-comportamentale (CBT) e la TRT (Tinnitus Retraining Therapy) con un audiologo.",
            "Se c'è un calo d'udito, un apparecchio acustico vero spesso riduce anche l'acufene.",
        ).forEach { Text("• $it", fontSize = 13.sp) }
    }
}
