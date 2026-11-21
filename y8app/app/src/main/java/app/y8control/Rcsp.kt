package app.y8control

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Protocollo RCSP JieLi (canale BLE AE00: scrittura AE01, notifiche AE02).
 * Pacchetto: FE DC BA | flag | opcode | len(2, BE) | [comando: sn, parametri] [risposta: status, sn, dati] | EF
 * flag: bit7 = comando (1) / risposta (0), bit6 = richiede risposta.
 */
object Rcsp {
    val SERVICE: UUID = UUID.fromString("0000ae00-0000-1000-8000-00805f9b34fb")
    val WRITE: UUID = UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb")
    val NOTIFY: UUID = UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb")

    const val OP_GET_TARGET_INFO = 0x03
    const val OP_GET_SYS_INFO = 0x07
    const val OP_SET_SYS_INFO = 0x08
    const val OP_SYS_INFO_AUTO_UPDATE = 0x09
    const val FUNC_PUBLIC = 0xFF

    // attributi della funzione "public"
    const val ATTR_VOLUME = 1
    const val ATTR_CUR_NOISE = 13
    const val ATTR_ALL_NOISE = 14
    const val ATTR_PHONE = 15
    const val ATTR_HEARING = 20
    const val ATTR_ADAPTIVE_ANC = 21
    const val ATTR_SMART_NO_PICK = 22
    const val ATTR_SCENE_DENOISE = 23
    const val ATTR_WIND = 24
    const val ATTR_VOCAL = 25

    val ATTR_NAMES = mapOf(
        0 to "batteria", 1 to "volume", 4 to "EQ", 12 to "preset EQ", 13 to "ANC corrente", 14 to "modalità ANC",
        15 to "telefonata", 20 to "assistenza udito", 21 to "ANC adattivo", 22 to "smart no-pick",
        23 to "denoise scena", 24 to "anti-vento", 25 to "rinforzo voce", 27 to "doppia connessione",
    )

    /** Modalità rumore JieLi: 0 normale, 1 anti-rumore, 2 trasparenza; livelli a 16 bit */
    data class VoiceMode(val mode: Int, val leftMax: Int, val rightMax: Int, val leftCur: Int, val rightCur: Int) {
        fun bytes(): ByteArray = byteArrayOf(
            mode.toByte(),
            (leftMax shr 8).toByte(), leftMax.toByte(),
            (rightMax shr 8).toByte(), rightMax.toByte(),
            (leftCur shr 8).toByte(), leftCur.toByte(),
            (rightCur shr 8).toByte(), rightCur.toByte(),
        )

        companion object {
            fun parse(b: ByteArray, o: Int = 0): VoiceMode? {
                if (b.size < o + 9) return null
                fun u16(i: Int) = ((b[o + i].toInt() and 0xFF) shl 8) or (b[o + i + 1].toInt() and 0xFF)
                return VoiceMode(b[o].toInt() and 0xFF, u16(1), u16(3), u16(5), u16(7))
            }
        }
    }

    data class State(
        val responded: Boolean = false,
        val lastStatus: Int? = null,
        val current: VoiceMode? = null,
        val modes: Map<Int, VoiceMode> = emptyMap(),
        val attrs: Map<Int, String> = emptyMap(),   // attributi riportati dal dispositivo (hex)
        val auth: String = "non avviata",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var sn = 0

    fun packet(op: Int, params: ByteArray, needResponse: Boolean = true): ByteArray {
        sn = (sn + 1) and 0xFF
        val len = params.size + 1
        val flag = 0x80 or (if (needResponse) 0x40 else 0)
        return byteArrayOf(0xFE.toByte(), 0xDC.toByte(), 0xBA.toByte(), flag.toByte(), op.toByte(),
            (len shr 8).toByte(), len.toByte(), sn.toByte()) + params + byteArrayOf(0xEF.toByte())
    }

    private fun mask(vararg attrs: Int): ByteArray {
        var m = 0L
        attrs.forEach { m = m or (1L shl it) }
        return byteArrayOf((m shr 24).toByte(), (m shr 16).toByte(), (m shr 8).toByte(), m.toByte())
    }

    /** Chiede alle cuffie le funzioni "avanzate" */
    fun query() {
        Headset.sendRcsp(packet(OP_GET_SYS_INFO, byteArrayOf(FUNC_PUBLIC.toByte()) +
            mask(ATTR_VOLUME, ATTR_CUR_NOISE, ATTR_ALL_NOISE, ATTR_HEARING, ATTR_ADAPTIVE_ANC,
                ATTR_SCENE_DENOISE, ATTR_WIND, ATTR_VOCAL)))
    }

    /** Info generali del dispositivo (piattaforma, versione, funzioni) */
    fun queryTargetInfo() {
        Headset.sendRcsp(packet(OP_GET_TARGET_INFO, byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0)))
    }

    /** Imposta modalità + livello (stesso livello per sinistra e destra) */
    fun setNoise(mode: Int, level: Int) {
        val base = _state.value.modes[mode] ?: _state.value.current?.takeIf { it.mode == mode }
        val lMax = base?.leftMax ?: 0
        val rMax = base?.rightMax ?: 0
        val vm = VoiceMode(mode, lMax, rMax, level.coerceIn(0, maxOf(lMax, 0)), level.coerceIn(0, maxOf(rMax, 0)))
        val data = vm.bytes()
        Headset.sendRcsp(packet(OP_SET_SYS_INFO,
            byteArrayOf(FUNC_PUBLIC.toByte(), (data.size + 1).toByte(), ATTR_CUR_NOISE.toByte()) + data))
        Headset.log("RCSP: imposto modalità $mode livello $level")
    }

    fun sendRaw(op: Int, params: ByteArray) = Headset.sendRcsp(packet(op, params))

    // ---------------- autenticazione ----------------

    private var authOk = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun startAuth() {
        authOk = false
        advCount = 0
        if (!JlAuth.selfTest()) { setAuth("cifrario errato (self-test fallito)"); return }
        setAuth("in corso…")
        Headset.log("RCSP: avvio autenticazione JieLi")
        Headset.sendRcsp(JlAuth.randomPacket())
        handler.postDelayed({
            if (!authOk) {
                setAuth("nessuna risposta: provo comunque i comandi")
                query()
            }
        }, 4000)
    }

    private fun setAuth(s: String) = _state.update { it.copy(auth = s) }

    /** Byte grezzi (non FE DC BA) ricevuti su AE02: fasi dell'autenticazione */
    fun onRaw(b: ByteArray) {
        Headset.log("AUTH << ${Proto.hex(b)}")
        if (b.isEmpty()) return
        when {
            b[0].toInt() == 1 && b.size == 17 -> {        // risposta alla nostra sfida
                Headset.sendRcsp(JlAuth.PASS)
            }
            b[0].toInt() == 0 && b.size == 17 -> {        // sfida delle cuffie
                Headset.sendRcsp(JlAuth.answer(b))
            }
            b[0].toInt() == 2 && b.size >= 5 && String(b, 1, 4, Charsets.US_ASCII) == "pass" -> {
                authOk = true
                setAuth("riuscita ✔")
                Headset.log("RCSP: autenticazione riuscita ✔")
                handler.postDelayed({ query() }, 200)
            }
        }
    }

    // ---------------- ricezione ----------------

    private var advCount = 0

    fun onPacket(p: ByteArray) {
        if (p.size < 8) return
        // C2 = notifica periodica di stato (ogni ~0,5 s): la registro una volta e chiedo di fermarla
        if ((p[4].toInt() and 0xFF) == 0xC2) {
            advCount++
            if (advCount == 1) {
                Headset.log("RCSP stato periodico (C2): ${Proto.hex(p)}")
                Headset.sendRcsp(packet(0xC3, byteArrayOf(0), needResponse = true))
            }
            return
        }
        Headset.log("RCSP << ${Proto.hex(p)}")
        val flag = p[3].toInt() and 0xFF
        val op = p[4].toInt() and 0xFF
        val len = ((p[5].toInt() and 0xFF) shl 8) or (p[6].toInt() and 0xFF)
        val body = p.copyOfRange(7, minOf(p.size - 1, 7 + len))
        val isCmd = flag and 0x80 != 0
        val data: ByteArray
        if (isCmd) {
            data = if (body.isNotEmpty()) body.copyOfRange(1, body.size) else body   // salta sn
        } else {
            val status = body.firstOrNull()?.toInt()?.and(0xFF)
            _state.update { it.copy(responded = true, lastStatus = status) }
            if (status != 0) {
                Headset.log("RCSP: risposta con errore $status a op ${"%02X".format(op)}" +
                    if (status == 1 || status == 3) " (probabile autenticazione richiesta / non supportato)" else "")
                return
            }
            data = if (body.size > 2) body.copyOfRange(2, body.size) else ByteArray(0)  // salta status + sn
        }
        when (op) {
            OP_GET_SYS_INFO, OP_SYS_INFO_AUTO_UPDATE -> parseSysInfo(data)
            OP_SET_SYS_INFO -> { Headset.log("RCSP: impostazione accettata ✔"); query() }
            OP_GET_TARGET_INFO -> Headset.log("RCSP target info: ${Proto.hex(data)}")
        }
    }

    private fun parseSysInfo(d: ByteArray) {
        if (d.isEmpty()) return
        val func = d[0].toInt() and 0xFF
        var i = 1
        val found = mutableMapOf<Int, String>()
        var cur: VoiceMode? = null
        val modes = mutableMapOf<Int, VoiceMode>()
        while (i < d.size) {
            val l = d[i].toInt() and 0xFF
            if (l < 1 || i + 1 + l > d.size) break
            val attr = d[i + 1].toInt() and 0xFF
            val v = d.copyOfRange(i + 2, i + 1 + l)
            if (func == FUNC_PUBLIC) {
                found[attr] = Proto.hex(v)
                Headset.log("RCSP attr $attr (${ATTR_NAMES[attr] ?: "?"}): ${Proto.hex(v)}")
                when (attr) {
                    ATTR_CUR_NOISE -> cur = VoiceMode.parse(v)
                    ATTR_ALL_NOISE -> {
                        // a volte preceduto da un byte di conteggio
                        val off = if (v.size % 9 == 1) 1 else 0
                        var k = off
                        while (k + 9 <= v.size) { VoiceMode.parse(v, k)?.let { modes[it.mode] = it }; k += 9 }
                    }
                }
            }
            i += 1 + l
        }
        _state.update {
            it.copy(responded = true,
                current = cur ?: it.current,
                modes = if (modes.isNotEmpty()) modes else it.modes,
                attrs = it.attrs + found)
        }
        cur?.let { c -> Headset.log("RCSP ANC: modalità ${c.mode}, livello ${c.leftCur}/${c.leftMax} (sx) ${c.rightCur}/${c.rightMax} (dx)") }
    }
}
