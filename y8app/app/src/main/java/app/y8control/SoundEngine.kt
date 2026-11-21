package app.y8control

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Generatore a due voci indipendenti (orecchio sinistro e destro) per test e sollievo dall'acufene.
 * Ogni voce ha il suo tipo di suono, frequenza, volume e modulazione.
 */
object SoundEngine {
    enum class Kind(val label: String) {
        TONE("Tono"),
        NARROW("Rumore stretto (sulla frequenza)"),
        WHITE("Rumore bianco"),
        PINK("Rumore rosa"),
        BROWN("Rumore marrone"),
        OCEAN("Onde del mare"),
        RAIN("Pioggia"),
        NOTCHED("Rumore a tacca (terapia)"),
    }

    /** Parametri di una voce. modDepth = variazione di frequenza (0..0.5 = ±50%), hops = a salti (tipo modem) */
    data class Voice(
        val on: Boolean = false,
        val kind: Kind = Kind.PINK,
        val freq: Double = 4000.0,
        val levelDb: Double = -40.0,
        val modRate: Double = 0.0,
        val modDepth: Double = 0.0,
        val hops: Boolean = false,
        val widthOct: Double = 1.0,
        val pulse: Boolean = false,
    )

    const val MAX_DB = -8.0

    @Volatile var left = Voice()
    @Volatile var right = Voice()
    @Volatile var stopAtMillis = 0L

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing
    private var thread: Thread? = null
    @Volatile private var stop = false

    fun set(l: Voice, r: Voice) { left = l; right = r }

    fun start() {
        if (_playing.value) return
        if (HearingAid.running.value) HearingAid.stop()
        stop = false
        _playing.value = true
        thread = Thread({ loop() }, "sound").also { it.start() }
    }

    fun stop() {
        stop = true
        thread?.join(400)
        thread = null
        _playing.value = false
    }

    private class Bq {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
        var z1 = 0.0; var z2 = 0.0
        fun p(x: Double): Double { val y = b0 * x + z1; z1 = b1 * x - a1 * y + z2; z2 = b2 * x - a2 * y; return y }
        private fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
            b0 = nb0 / na0; b1 = nb1 / na0; b2 = nb2 / na0; a1 = na1 / na0; a2 = na2 / na0
        }
        fun bandPass(fs: Double, f: Double, q: Double) {
            val w = 2 * PI * f.coerceIn(50.0, fs * 0.45) / fs; val al = sin(w) / (2 * q); val c = cos(w)
            set(al, 0.0, -al, 1 + al, -2 * c, 1 - al)
        }
        fun notch(fs: Double, f: Double, q: Double) {
            val w = 2 * PI * f.coerceIn(50.0, fs * 0.45) / fs; val al = sin(w) / (2 * q); val c = cos(w)
            set(1.0, -2 * c, 1.0, 1 + al, -2 * c, 1 - al)
        }
        fun lowPass(fs: Double, f: Double, q: Double = 0.707) {
            val w = 2 * PI * f / fs; val al = sin(w) / (2 * q); val c = cos(w)
            set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al)
        }
    }

    /** Stato di sintesi di una voce */
    private class Synth(seed: Long) {
        val rnd = Random(seed)
        var phase = 0.0
        var modPhase = 0.0
        var hopTarget = 0.0; var hopCur = 0.0; var hopTimer = 0.0
        var p0 = 0.0; var p1 = 0.0; var p2 = 0.0; var p3 = 0.0; var p4 = 0.0; var p5 = 0.0; var p6 = 0.0
        var brown = 0.0
        val bp1 = Bq(); val bp2 = Bq()
        val n1 = Bq(); val n2 = Bq(); val n3 = Bq(); val n4 = Bq()
        val lp = Bq(); val lpRain = Bq()
        var cfg: Voice? = null
        var cfgFreq = 0.0
        var gain = 0.0
        var oceanPhase = rnd.nextDouble() * 6.0
        var t = 0L

        fun configure(v: Voice, f: Double, fs: Double) {
            val w = v.widthOct.coerceIn(0.2, 4.0)
            val q = sqrt(2.0.pow(w)) / (2.0.pow(w) - 1)  // Q per larghezza in ottave
            bp1.bandPass(fs, f, q); bp2.bandPass(fs, f, q)
            val spread = 2.0.pow(w / 3)
            n1.notch(fs, f / spread, 3.0); n2.notch(fs, f, 2.0); n3.notch(fs, f * spread, 3.0); n4.notch(fs, f, 4.0)
            lp.lowPass(fs, 900.0); lpRain.lowPass(fs, 7000.0)
            cfgFreq = f
        }

        /** frequenza istantanea con modulazione (sinusoidale o a salti) */
        fun modFreq(v: Voice, fs: Double): Double {
            if (v.modRate <= 0 || v.modDepth <= 0) return v.freq
            return if (v.hops) {
                hopTimer -= 1.0 / fs
                if (hopTimer <= 0) {
                    hopTimer = 1.0 / v.modRate * (0.5 + rnd.nextDouble())
                    hopTarget = (rnd.nextDouble() * 2 - 1) * v.modDepth
                }
                hopCur += (hopTarget - hopCur) * 0.01
                v.freq * (1 + hopCur)
            } else {
                modPhase += 2 * PI * v.modRate / fs
                if (modPhase > 2 * PI) modPhase -= 2 * PI
                v.freq * (1 + v.modDepth * sin(modPhase))
            }
        }

        fun block(v: Voice, out: DoubleArray, fs: Double) {
            if (cfg != v) { cfg = v; configure(v, v.freq, fs) }
            val target = if (v.on) 10.0.pow(v.levelDb.coerceAtMost(MAX_DB) / 20.0) else 0.0
            val modulatedNoise = (v.kind == Kind.NARROW || v.kind == Kind.NOTCHED) && v.modRate > 0 && v.modDepth > 0
            for (i in out.indices) {
                val f = modFreq(v, fs)
                if (modulatedNoise && i % 64 == 0) configure(v, f, fs)
                val white = rnd.nextDouble() * 2 - 1
                p0 = 0.99886 * p0 + white * 0.0555179; p1 = 0.99332 * p1 + white * 0.0750759
                p2 = 0.96900 * p2 + white * 0.1538520; p3 = 0.86650 * p3 + white * 0.3104856
                p4 = 0.55000 * p4 + white * 0.5329522; p5 = -0.7616 * p5 - white * 0.0168980
                val pink = (p0 + p1 + p2 + p3 + p4 + p5 + p6 + white * 0.5362) * 0.11
                p6 = white * 0.115926
                brown = (brown + 0.02 * white) / 1.02
                var x = when (v.kind) {
                    Kind.TONE -> { phase += 2 * PI * f / fs; if (phase > 2 * PI) phase -= 2 * PI; sin(phase) }
                    Kind.NARROW -> bp2.p(bp1.p(white)) * (2.0 + 2.0 / v.widthOct.coerceAtLeast(0.25))
                    Kind.WHITE -> white * 0.5
                    Kind.PINK -> pink
                    Kind.BROWN -> brown * 3.5
                    Kind.OCEAN -> {
                        oceanPhase += 2 * PI * 0.09 / fs
                        val swell = 0.25 + 0.75 * (0.5 + 0.5 * sin(oceanPhase))
                        (lp.p(pink) * 2.2 + pink * 0.15) * swell
                    }
                    Kind.RAIN -> lpRain.p(white) * 0.35 * (if (rnd.nextDouble() < 0.0008) 3.0 else 1.0)
                    Kind.NOTCHED -> n4.p(n3.p(n2.p(n1.p(pink))))
                }
                if (v.pulse) {
                    val ms = (t * 1000 / fs.toLong()) % 800
                    x *= when {
                        ms < 20 -> ms / 20.0
                        ms < 380 -> 1.0
                        ms < 400 -> (400 - ms) / 20.0
                        else -> 0.0
                    }
                }
                t++
                gain += (target - gain) * 0.0005
                out[i] = x * gain
            }
        }
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val fs = 48000
        val n = 512
        val minBuf = AudioTrack.getMinBufferSize(fs, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(fs).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
            .setBufferSizeInBytes(maxOf(minBuf, n * 8 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
        val sl = Synth(System.nanoTime()); val sr = Synth(System.nanoTime() + 7919)
        val l = DoubleArray(n); val r = DoubleArray(n)
        val buf = FloatArray(n * 2)
        try {
            while (!stop) {
                if (stopAtMillis > 0 && System.currentTimeMillis() > stopAtMillis) break
                sl.block(left, l, fs.toDouble())
                sr.block(right, r, fs.toDouble())
                for (i in 0 until n) {
                    buf[2 * i] = l[i].toFloat().coerceIn(-1f, 1f)
                    buf[2 * i + 1] = r[i].toFloat().coerceIn(-1f, 1f)
                }
                track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
            }
        } catch (e: Exception) {
            Headset.log("Suoni: errore ${e.message}")
        } finally {
            try { track.stop() } catch (_: Exception) {}
            track.release()
            _playing.value = false
            stopAtMillis = 0
        }
    }
}
