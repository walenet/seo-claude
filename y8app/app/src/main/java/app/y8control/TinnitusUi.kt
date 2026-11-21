package app.y8control

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Tipo di acufene percepito */
enum class TinType(val label: String) {
    NONE("Nessuno"),
    FIXED("Fischio fisso"),
    WARBLE("Fischio che varia (tipo modem)"),
    HISS("Fruscio / soffio"),
    HUM("Ronzio basso"),
    PULSE("Pulsante (col battito)"),
}

/** Profilo dell'acufene di un orecchio (0 = sinistro, 1 = destro) */
data class TinProfile(
    val type: TinType = TinType.NONE,
    val freq: Double = 4000.0,
    val modRate: Double = 4.0,
    val modDepth: Double = 0.08,
    val hops: Boolean = true,
    val levelDb: Double = -50.0,
) {
    /** Suono che riproduce l'acufene (per il confronto) */
    fun imitation(): SoundEngine.Voice = when (type) {
        TinType.WARBLE -> SoundEngine.Voice(true, SoundEngine.Kind.TONE, freq, levelDb, modRate, modDepth, hops)
        TinType.HISS -> SoundEngine.Voice(true, SoundEngine.Kind.NARROW, freq, levelDb, widthOct = 1.0)
        TinType.HUM -> SoundEngine.Voice(true, SoundEngine.Kind.TONE, freq.coerceAtMost(500.0), levelDb)
        else -> SoundEngine.Voice(true, SoundEngine.Kind.TONE, freq, levelDb)
    }

    /** Contromisura consigliata in base al tipo */
    fun relief(): SoundEngine.Voice {
        val lvl = (levelDb - 4).coerceIn(-75.0, -15.0)   // poco sotto il volume percepito: "punto di fusione"
        return when (type) {
            TinType.FIXED -> SoundEngine.Voice(true, SoundEngine.Kind.NOTCHED, freq, lvl, widthOct = 1.0)
            // banda di rumore larga quanto la variazione, che "segue" il modem in modo lento
            TinType.WARBLE -> SoundEngine.Voice(true, SoundEngine.Kind.NARROW, freq, lvl,
                widthOct = (1.0 + 4 * modDepth).coerceAtMost(2.5))
            TinType.HISS -> SoundEngine.Voice(true, SoundEngine.Kind.PINK, freq, lvl)
            TinType.HUM -> SoundEngine.Voice(true, SoundEngine.Kind.BROWN, freq, lvl)
            TinType.PULSE -> SoundEngine.Voice(true, SoundEngine.Kind.OCEAN, freq, lvl)
            TinType.NONE -> SoundEngine.Voice(false)
        }
    }

    fun reliefWhy(): String = when (type) {
        TinType.FIXED -> "Rumore rosa con un \"buco\" largo un'ottava centrato sul fischio (terapia a tacca)."
        TinType.WARBLE -> "Banda di rumore centrata sulla zona dove il fischio oscilla: lo \"assorbe\" senza coprirlo."
        TinType.HISS -> "Rumore rosa morbido: si fonde con il soffio."
        TinType.HUM -> "Rumore marrone (grave): copre i ronzii bassi."
        TinType.PULSE -> "Onde del mare. Ma un acufene pulsante va fatto vedere da un medico."
        TinType.NONE -> "Nessun suono su questo lato."
    }
}

object TinPrefs {
    private fun sp(c: Context) = c.getSharedPreferences("tinnitus", Context.MODE_PRIVATE)
    fun profile(c: Context, ear: Int): TinProfile {
        val s = sp(c)
        val t = TinType.entries.getOrElse(s.getInt("t$ear", 0)) { TinType.NONE }
        return TinProfile(t, s.getFloat("f$ear", 4000f).toDouble(), s.getFloat("r$ear", 4f).toDouble(),
            s.getFloat("d$ear", 0.08f).toDouble(), s.getBoolean("h$ear", true), s.getFloat("l$ear", -50f).toDouble())
    }
    fun save(c: Context, ear: Int, p: TinProfile) = sp(c).edit()
        .putInt("t$ear", p.type.ordinal).putFloat("f$ear", p.freq.toFloat()).putFloat("r$ear", p.modRate.toFloat())
        .putFloat("d$ear", p.modDepth.toFloat()).putBoolean("h$ear", p.hops).putFloat("l$ear", p.levelDb.toFloat()).apply()
    fun audiogram(c: Context): String = sp(c).getString("audiogram", "") ?: ""
    fun setAudiogram(c: Context, s: String) = sp(c).edit().putString("audiogram", s).apply()
}

private val EAR = listOf("Sinistro", "Destro")
private const val F_MIN = 125.0
private const val F_MAX = 15000.0
private fun posToFreq(p: Float) = F_MIN * (F_MAX / F_MIN).pow(p.toDouble())
private fun freqToPos(f: Double) = (ln(f / F_MIN) / ln(F_MAX / F_MIN)).toFloat().coerceIn(0f, 1f)
fun fmtHz(f: Double) = if (f >= 1000) "%.1f kHz".format(f / 1000) else "${f.roundToInt()} Hz"

private fun hint(t: String) = t
@Composable private fun Hint(t: String) = Text(hint(t), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

private fun summary(p: TinProfile): String = when (p.type) {
    TinType.NONE -> "nessun acufene"
    TinType.WARBLE -> "${p.type.label}, ${fmtHz(p.freq)} ±${(p.modDepth * 100).roundToInt()}% a ${"%.1f".format(p.modRate)}/s" +
        if (p.hops) " (a salti)" else " (ondulato)"
    else -> "${p.type.label}, ${fmtHz(p.freq)}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TestSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playing by SoundEngine.playing.collectAsState()

    Section("🧪 Riconosci il tuo acufene e test dell'udito") {
        Hint("Non è un esame medico. Stanza silenziosa, anti-rumore acceso, volume del telefono a metà.")

        // ---------- profilo per orecchio ----------
        Text("1 · Ricostruisci il suono, orecchio per orecchio", fontWeight = FontWeight.SemiBold)
        var ear by remember { mutableIntStateOf(0) }
        var prof by remember(ear) { mutableStateOf(TinPrefs.profile(ctx, ear)) }
        var other by remember(ear) { mutableStateOf(TinPrefs.profile(ctx, 1 - ear)) }
        var compare by remember { mutableStateOf(false) }

        fun push(p: TinProfile) {
            prof = p
            val me = p.imitation()
            val them = if (compare) other.imitation() else SoundEngine.Voice(false)
            if (ear == 0) SoundEngine.set(me, them) else SoundEngine.set(them, me)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EAR.forEachIndexed { i, n -> Choice("Orecchio $n", ear == i) { ear = i } }
        }
        Text("Tipo di suono", fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TinType.entries.forEach { t -> Choice(t.label, prof.type == t) { push(prof.copy(type = t)) } }
        }
        if (prof.type == TinType.PULSE)
            Text("⚠ Un acufene pulsante (a ritmo col cuore) va sempre fatto vedere da un medico: può avere cause vascolari curabili.",
                fontSize = 13.sp, color = Color(0xFFFFB74D))
        if (prof.type != TinType.NONE) {
            Text("Altezza: ${fmtHz(prof.freq)}")
            Slider(value = freqToPos(prof.freq), onValueChange = { push(prof.copy(freq = posToFreq(it))) })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { push(prof.copy(freq = (prof.freq / 2).coerceAtLeast(F_MIN))) }) { Text("÷2 ottava") }
                OutlinedButton(onClick = { push(prof.copy(freq = prof.freq / 1.06)) }) { Text("−") }
                OutlinedButton(onClick = { push(prof.copy(freq = prof.freq * 1.06)) }) { Text("+") }
                OutlinedButton(onClick = { push(prof.copy(freq = (prof.freq * 2).coerceAtMost(F_MAX))) }) { Text("×2") }
            }
            if (prof.type == TinType.WARBLE) {
                Text("Velocità della variazione: ${"%.1f".format(prof.modRate)} volte/s")
                Slider(value = prof.modRate.toFloat(), onValueChange = { push(prof.copy(modRate = it.toDouble())) }, valueRange = 0.3f..25f)
                Text("Ampiezza della variazione: ±${(prof.modDepth * 100).roundToInt()}%")
                Slider(value = prof.modDepth.toFloat(), onValueChange = { push(prof.copy(modDepth = it.toDouble())) }, valueRange = 0.01f..0.4f)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Choice("A salti (modem)", prof.hops) { push(prof.copy(hops = true)) }
                    Choice("Ondulato (sirena)", !prof.hops) { push(prof.copy(hops = false)) }
                }
            }
            Text("Volume: alzalo finché è forte quanto il tuo acufene (${prof.levelDb.roundToInt()} dB)")
            Slider(value = prof.levelDb.toFloat(), onValueChange = { push(prof.copy(levelDb = it.toDouble())) }, valueRange = -85f..-20f)
            SwitchRow("Suona anche l'altro orecchio (confronto)", compare) { compare = it; push(prof) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (playing) SoundEngine.stop() else { SoundEngine.stopAtMillis = 0; push(prof); SoundEngine.start() }
                }) { Text(if (playing) "■ Stop" else "▶ Ascolta") }
                OutlinedButton(onClick = {
                    TinPrefs.save(ctx, ear, prof)
                    Headset.log("Profilo acufene ${EAR[ear]} salvato: ${summary(prof)}")
                }) { Text("💾 Salva profilo") }
            }
            Hint("Ci si confonde facilmente di un'ottava: prova ÷2 e ×2 e tieni quello più simile.")
        }
        Text("Salvati → SX: ${summary(TinPrefs.profile(ctx, 0))}\n              DX: ${summary(TinPrefs.profile(ctx, 1))}", fontSize = 13.sp)

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
                    job?.cancel(); SoundEngine.stop(); running = false; status = "Test interrotto"
                } else {
                    results.clear(); running = true
                    job = scope.launch {
                        SoundEngine.stopAtMillis = 0
                        for (e in 0..1) for (f in freqs) {
                            var lvl = -80.0
                            heard = false
                            fun apply() {
                                val v = SoundEngine.Voice(true, SoundEngine.Kind.TONE, f, lvl, pulse = true)
                                if (e == 0) SoundEngine.set(v, SoundEngine.Voice(false)) else SoundEngine.set(SoundEngine.Voice(false), v)
                            }
                            apply(); SoundEngine.start()
                            while (!heard && lvl < -10.0) {
                                status = "${EAR[e]} · ${fmtHz(f)} · ${lvl.roundToInt()} dB"
                                delay(1600)
                                if (!heard) { lvl += 5; apply() }
                            }
                            results["$e-${f.roundToInt()}"] = if (heard) lvl else 0.0
                            SoundEngine.set(SoundEngine.Voice(false), SoundEngine.Voice(false))
                            delay(700 + (0..900).random().toLong())
                        }
                        SoundEngine.stop()
                        val txt = buildString {
                            append("Soglie (dB rel., più basso = senti meglio):\n")
                            for (f in freqs) {
                                fun v(e: Int) = results["$e-${f.roundToInt()}"]?.let { if (it == 0.0) "n.s." else "${it.roundToInt()}" } ?: "-"
                                append("${fmtHz(f)}:  SX ${v(0)}   DX ${v(1)}\n")
                            }
                        }
                        status = txt; TinPrefs.setAudiogram(ctx, txt); running = false
                    }
                }
            }) { Text(if (running) "■ Interrompi" else "Inizia test") }
            Button(onClick = { heard = true }, enabled = running) { Text("Lo sento!") }
        }
        Text(status, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TinnitusSection() {
    val ctx = LocalContext.current
    val playing by SoundEngine.playing.collectAsState()
    Section("🌿 Sollievo acufene su misura") {
        Hint("Ogni orecchio riceve la contromisura adatta al suo acufene, in contemporanea. Volume basso: il suono " +
            "deve fondersi con il fischio, non coprirlo. Non è una cura, ma a molte persone dà sollievo.")
        val profiles = remember { mutableStateListOf(TinPrefs.profile(ctx, 0), TinPrefs.profile(ctx, 1)) }
        val voices = remember { mutableStateMapOf(0 to profiles[0].relief(), 1 to profiles[1].relief()) }
        var timer by remember { mutableIntStateOf(30) }

        fun pushAll() { SoundEngine.set(voices[0] ?: SoundEngine.Voice(), voices[1] ?: SoundEngine.Voice()) }

        OutlinedButton(onClick = {
            profiles[0] = TinPrefs.profile(ctx, 0); profiles[1] = TinPrefs.profile(ctx, 1)
            voices[0] = profiles[0].relief(); voices[1] = profiles[1].relief(); pushAll()
        }) { Text("↻ Usa i profili salvati") }

        for (e in 0..1) {
            val p = profiles[e]
            val v = voices[e] ?: SoundEngine.Voice()
            Text("Orecchio ${EAR[e]} · ${summary(p)}", fontWeight = FontWeight.SemiBold)
            Hint("Consigliato: ${p.reliefWhy()}")
            SwitchRow("Suono attivo su questo lato", v.on) { voices[e] = v.copy(on = it); pushAll() }
            if (v.on) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SoundEngine.Kind.entries.filter { it != SoundEngine.Kind.TONE }.forEach { k ->
                        Choice(k.label, v.kind == k) { voices[e] = v.copy(kind = k); pushAll() }
                    }
                }
                if (v.kind == SoundEngine.Kind.NARROW || v.kind == SoundEngine.Kind.NOTCHED) {
                    Text("Larghezza banda: ${"%.1f".format(v.widthOct)} ottave · centro ${fmtHz(v.freq)}", fontSize = 13.sp)
                    Slider(value = v.widthOct.toFloat(), onValueChange = { voices[e] = v.copy(widthOct = it.toDouble()); pushAll() },
                        valueRange = 0.3f..3f)
                }
                Text("Volume: ${v.levelDb.roundToInt()} dB")
                Slider(value = v.levelDb.toFloat(), onValueChange = { voices[e] = v.copy(levelDb = it.toDouble()); pushAll() },
                    valueRange = -80f..-15f)
            }
        }

        Text("Timer", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "Nessuno", 15 to "15 min", 30 to "30 min", 60 to "1 ora", 120 to "2 ore").forEach { (m, l) ->
                Choice(l, timer == m) { timer = m }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                if (playing) SoundEngine.stop() else {
                    pushAll()
                    SoundEngine.stopAtMillis = if (timer > 0) System.currentTimeMillis() + timer * 60_000L else 0
                    SoundEngine.start()
                }
            }) { Text(if (playing) "■ Stop" else "▶ Avvia") }
            OutlinedButton(onClick = {
                // inibizione residua: 60 s di rumore stretto sulla frequenza di ciascun lato, poi silenzio
                fun ri(p: TinProfile) = if (p.type == TinType.NONE) SoundEngine.Voice(false)
                    else SoundEngine.Voice(true, SoundEngine.Kind.NARROW, p.freq, (p.levelDb + 10).coerceAtMost(-15.0), widthOct = 0.5)
                SoundEngine.set(ri(profiles[0]), ri(profiles[1]))
                SoundEngine.stopAtMillis = System.currentTimeMillis() + 60_000
                SoundEngine.start()
                Headset.log("Inibizione residua 60 s: poi ascolta se i fischi sono più deboli e per quanto")
            }) { Text("Inibizione 60 s") }
        }
        Hint("Inibizione residua: dopo 60 s di rumore sulla frequenza del fischio, in alcune persone l'acufene si " +
            "attenua per secondi o minuti. Se succede su un lato, la terapia sonora su quel lato ha buone probabilità di aiutare.")

        Text("Consigli utili", fontWeight = FontWeight.SemiBold)
        listOf(
            "Acufene solo da un lato, comparso da poco o pulsante: fai una visita da un otorino senza aspettare.",
            "Perdita d'udito improvvisa: è un'urgenza, vai da un otorino o al pronto soccorso entro 24–48 ore.",
            "Tieni il volume basso: usa l'anti-rumore così non devi alzare la musica.",
            "Evita il silenzio totale, soprattutto la sera: un suono di fondo leggero aiuta il cervello a ignorare il fischio.",
            "Stress, poco sonno, caffeina, alcol e nicotina spesso lo peggiorano.",
            "Le terapie con più prove sono la terapia cognitivo-comportamentale (CBT) e la TRT con un audiologo.",
            "Se c'è un calo d'udito, un apparecchio acustico vero spesso riduce anche l'acufene.",
        ).forEach { Text("• $it", fontSize = 13.sp) }
    }
}
