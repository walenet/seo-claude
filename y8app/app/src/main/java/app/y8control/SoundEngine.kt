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
import kotlin.random.Random

/**
 * Generatore di suoni per test dell'udito e sollievo dall'acufene.
 * Stereo, con volume indipendente per orecchio. Livello massimo limitato per sicurezza.
 */
object SoundEngine {
    enum class Kind(val label: String) {
        TONE("Tono puro"),
        NARROW("Rumore stretto (sulla frequenza)"),
        WHITE("Rumore bianco"),
        PINK("Rumore rosa"),
        BROWN("Rumore marrone"),
        OCEAN("Onde del mare"),
        RAIN("Pioggia"),
        NOTCHED("Rumore rosa a tacca (terapia)"),
    }

    @Volatile var kind = Kind.PINK
    @Volatile var freq = 4000.0          // Hz: tono, rumore stretto, centro della tacca
    @Volatile var levelDb = -35.0        // dBFS
    @Volatile var left = 1.0             // 0..1
    @Volatile var right = 1.0
    @Volatile var pulse = false          // tono a impulsi (test)
    @Volatile private var version = 0
    const val MAX_DB = -8.0

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing
    private var thread: Thread? = null
    @Volatile private var stop = false
    @Volatile var stopAtMillis = 0L      // timer (0 = nessuno)

    fun changed() { version++ }

    fun start() {
        if (_playing.value) { changed(); return }
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
            val w = 2 * PI * f / fs; val al = sin(w) / (2 * q); val c = cos(w)
            set(al, 0.0, -al, 1 + al, -2 * c, 1 - al)
        }
        fun notch(fs: Double, f: Double, q: Double) {
            val w = 2 * PI * f / fs; val al = sin(w) / (2 * q); val c = cos(w)
            set(1.0, -2 * c, 1.0, 1 + al, -2 * c, 1 - al)
        }
        fun lowPass(fs: Double, f: Double, q: Double = 0.707) {
            val w = 2 * PI * f / fs; val al = sin(w) / (2 * q); val c = cos(w)
            set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al)
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
        val buf = FloatArray(n * 2)
        val rnd = Random(System.nanoTime())
        var phase = 0.0
        var t = 0L
        // rumore rosa (Paul Kellet)
        var p0 = 0.0; var p1 = 0.0; var p2 = 0.0; var p3 = 0.0; var p4 = 0.0; var p5 = 0.0; var p6 = 0.0
        var brown = 0.0
        val bp1 = Bq(); val bp2 = Bq()
        val n1 = Bq(); val n2 = Bq(); val n3 = Bq(); val n4 = Bq()
        val lp = Bq(); val lpRain = Bq()
        var seen = -1
        var gL = 0.0; var gR = 0.0   // guadagni correnti (rampa senza click)
        var oceanPhase = 0.0
        try {
            while (!stop) {
                if (stopAtMillis > 0 && System.currentTimeMillis() > stopAtMillis) break
                if (seen != version) {
                    seen = version
                    val f = freq.coerceIn(100.0, 16000.0)
                    bp1.bandPass(fs.toDouble(), f, 5.0); bp2.bandPass(fs.toDouble(), f, 5.0)
                    // tacca di circa 1 ottava centrata sulla frequenza
                    n1.notch(fs.toDouble(), f / 1.26, 3.0); n2.notch(fs.toDouble(), f, 2.0)
                    n3.notch(fs.toDouble(), f * 1.26, 3.0); n4.notch(fs.toDouble(), f, 4.0)
                    lp.lowPass(fs.toDouble(), 900.0); lpRain.lowPass(fs.toDouble(), 7000.0)
                }
                val k = kind
                val lvl = 10.0.pow(levelDb.coerceAtMost(MAX_DB) / 20.0)
                val tL = lvl * left.coerceIn(0.0, 1.0)
                val tR = lvl * right.coerceIn(0.0, 1.0)
                val dPh = 2 * PI * freq / fs
                for (i in 0 until n) {
                    val white = rnd.nextDouble() * 2 - 1
                    p0 = 0.99886 * p0 + white * 0.0555179; p1 = 0.99332 * p1 + white * 0.0750759
                    p2 = 0.96900 * p2 + white * 0.1538520; p3 = 0.86650 * p3 + white * 0.3104856
                    p4 = 0.55000 * p4 + white * 0.5329522; p5 = -0.7616 * p5 - white * 0.0168980
                    val pink = (p0 + p1 + p2 + p3 + p4 + p5 + p6 + white * 0.5362) * 0.11
                    p6 = white * 0.115926
                    brown = (brown + 0.02 * white) / 1.02
                    var x = when (k) {
                        Kind.TONE -> sin(phase)
                        Kind.NARROW -> bp2.p(bp1.p(white)) * 4.0
                        Kind.WHITE -> white * 0.5
                        Kind.PINK -> pink
                        Kind.BROWN -> brown * 3.5
                        Kind.OCEAN -> {
                            val swell = 0.25 + 0.75 * (0.5 + 0.5 * sin(oceanPhase))
                            lp.p(pink) * 2.2 * swell + pink * 0.15 * swell
                        }
                        Kind.RAIN -> lpRain.p(white) * 0.35 * (if (rnd.nextDouble() < 0.0008) 3.0 else 1.0)
                        Kind.NOTCHED -> n4.p(n3.p(n2.p(n1.p(pink))))
                    }
                    if (pulse && k == Kind.TONE) {
                        val ms = (t * 1000 / fs) % 800
                        val env = when {
                            ms < 20 -> ms / 20.0
                            ms < 380 -> 1.0
                            ms < 400 -> (400 - ms) / 20.0
                            else -> 0.0
                        }
                        x *= env
                    }
                    phase += dPh; if (phase > 2 * PI) phase -= 2 * PI
                    oceanPhase += 2 * PI * 0.09 / fs
                    t++
                    gL += (tL - gL) * 0.0005; gR += (tR - gR) * 0.0005
                    buf[2 * i] = (x * gL).toFloat().coerceIn(-1f, 1f)
                    buf[2 * i + 1] = (x * gR).toFloat().coerceIn(-1f, 1f)
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
