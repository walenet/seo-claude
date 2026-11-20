package app.y8control

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * "Apparecchio acustico": microfono del telefono → elaborazione → cuffie.
 * Catena: passa-alto (taglia rombo) → enfasi voce 2.5 kHz → guadagno → riduttore rumore (expander) → limitatore.
 */
object HearingAid {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    @Volatile var gainDb = 10f        // amplificazione generale
    @Volatile var voiceDb = 6f        // enfasi delle voci
    @Volatile var noiseReduction = true
    @Volatile var cutRumble = true
    @Volatile private var paramsVersion = 0

    fun paramsChanged() { paramsVersion++ }

    private var thread: Thread? = null
    @Volatile private var stopFlag = false

    fun start(ctx: Context) {
        if (_running.value) return
        stopFlag = false
        _running.value = true
        thread = Thread({ loop(ctx.applicationContext) }, "hearing-aid").also { it.start() }
        Headset.log("Apparecchio acustico avviato")
    }

    fun stop() {
        stopFlag = true
        thread?.join(500)
        thread = null
        _running.value = false
    }

    private class Biquad {
        var b0 = 1f; var b1 = 0f; var b2 = 0f; var a1 = 0f; var a2 = 0f
        private var z1 = 0f; private var z2 = 0f
        fun process(x: Float): Float {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
        fun bypass() { b0 = 1f; b1 = 0f; b2 = 0f; a1 = 0f; a2 = 0f }
        private fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
            b0 = (nb0 / na0).toFloat(); b1 = (nb1 / na0).toFloat(); b2 = (nb2 / na0).toFloat()
            a1 = (na1 / na0).toFloat(); a2 = (na2 / na0).toFloat()
        }
        fun highPass(fs: Double, f: Double, q: Double = 0.707) {
            val w = 2 * PI * f / fs; val al = sin(w) / (2 * q); val c = cos(w)
            set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al)
        }
        fun peak(fs: Double, f: Double, db: Double, q: Double = 0.9) {
            val a = 10.0.pow(db / 40); val w = 2 * PI * f / fs; val al = sin(w) / (2 * q); val c = cos(w)
            set(1 + al * a, -2 * c, 1 - al * a, 1 + al / a, -2 * c, 1 - al / a)
        }
    }

    @SuppressLint("MissingPermission")
    private fun loop(ctx: Context) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val fs = 48000
        val am = ctx.getSystemService(AudioManager::class.java)
        val unprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION

        val inMin = AudioRecord.getMinBufferSize(fs, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val outMin = AudioTrack.getMinBufferSize(fs, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        var rec: AudioRecord? = null
        var track: AudioTrack? = null
        var ns: NoiseSuppressor? = null
        try {
            val mic = AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(fs)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(inMin * 2)
                .build()
            rec = mic
            am.getDevices(AudioManager.GET_DEVICES_INPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
                ?.let { mic.setPreferredDevice(it) }
            if (NoiseSuppressor.isAvailable()) ns = NoiseSuppressor.create(mic.audioSessionId)

            val out = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(fs)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(outMin)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track = out
            val sup = ns

            mic.startRecording()
            out.play()
            Headset.log("Microfono: ${if (unprocessed) "non elaborato" else "voice recognition"}, NS di sistema: ${sup != null}")

            val hp = Biquad(); val pk = Biquad()
            var seen = -1
            val n = 240 // 5 ms
            val buf = ShortArray(n)
            var env = 0f
            var gate = 1f
            val atk = exp(-1.0 / (0.002 * fs)).toFloat()
            val rel = exp(-1.0 / (0.120 * fs)).toFloat()
            val gateSmooth = exp(-1.0 / (0.050 * fs)).toFloat()
            var gainLin = 1f

            while (!stopFlag) {
                if (seen != paramsVersion) {
                    seen = paramsVersion
                    if (cutRumble) hp.highPass(fs.toDouble(), 150.0) else hp.bypass()
                    pk.peak(fs.toDouble(), 2500.0, voiceDb.toDouble())
                    gainLin = 10.0.pow(gainDb / 20.0).toFloat()
                    sup?.enabled = noiseReduction
                }
                val r = mic.read(buf, 0, n)
                if (r <= 0) continue
                val thr = 0.004f   // ~ -48 dBFS
                for (i in 0 until r) {
                    var x = buf[i] / 32768f
                    x = pk.process(hp.process(x)) * gainLin
                    // envelope + expander (abbassa il fruscio quando nessuno parla)
                    val ax = abs(x)
                    env = if (ax > env) atk * env + (1 - atk) * ax else rel * env + (1 - rel) * ax
                    val target = if (!noiseReduction || env * 1f >= thr * gainLin) 1f
                        else maxOf(0.18f, sqrt(env / (thr * gainLin)))
                    gate = gateSmooth * gate + (1 - gateSmooth) * target
                    x *= gate
                    // limitatore morbido: protegge le orecchie
                    val a = abs(x)
                    if (a > 0.5f) x = (if (x > 0) 1 else -1) * (0.5f + 0.4f * tanh((a - 0.5f) / 0.4f))
                    buf[i] = (x * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                }
                out.write(buf, 0, r)
            }
        } catch (e: Exception) {
            Headset.log("Apparecchio acustico: errore ${e.message}")
        } finally {
            try { rec?.stop() } catch (_: Exception) {}
            try { track?.stop() } catch (_: Exception) {}
            ns?.release(); rec?.release(); track?.release()
            _running.value = false
            Headset.log("Apparecchio acustico fermato")
        }
    }
}
