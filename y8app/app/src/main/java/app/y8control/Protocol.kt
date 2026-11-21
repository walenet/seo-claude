package app.y8control

import java.util.UUID

/** Protocollo BLE 1Mii Y8 / Y8+ ricostruito da YeeMall 2.8.7. Vedi y8plus/PROTOCOLLO.md */
object Proto {
    val SERVICE: UUID = UUID.fromString("0000faa0-0000-1000-8000-00805f9b34fb")
    val WRITE: UUID = UUID.fromString("0000faa1-0000-1000-8000-00805f9b34fb")
    val NOTIFY: UUID = UUID.fromString("0000faa2-0000-1000-8000-00805f9b34fb")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** AA | C1 | C2 | LEN | DATA | somma(byte precedenti) & 0xFF */
    fun frame(cmd: String, data: ByteArray = byteArrayOf(0)): ByteArray {
        require(cmd.length == 2)
        val out = ByteArray(5 + data.size)
        out[0] = 0xAA.toByte()
        out[1] = cmd[0].code.toByte()
        out[2] = cmd[1].code.toByte()
        out[3] = data.size.toByte()
        data.copyInto(out, 4)
        var sum = 0
        for (i in 0 until out.size - 1) sum += out[i].toInt() and 0xFF
        out[out.size - 1] = sum.toByte()
        return out
    }

    fun hex(b: ByteArray): String = b.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    fun parseHex(s: String): ByteArray {
        val clean = s.replace(" ", "").replace(":", "")
        if (clean.isEmpty()) return byteArrayOf(0)
        return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    /**
     * Le cuffie rispondono con intestazione A5 (le richieste usano AA).
     * Il campo lunghezza delle risposte non è affidabile (GA dice 04 ma porta 5 byte),
     * quindi ogni notifica è trattata come un pacchetto intero: A5 C1 C2 LEN DATI… CHK.
     */
    fun split(bytes: ByteArray): List<ByteArray> {
        if (bytes.size < 5) return emptyList()
        val h = bytes[0].toInt() and 0xFF
        return if (h == 0xA5 || h == 0xAA) listOf(bytes) else emptyList()
    }
}

enum class Conn(val label: String) {
    OFF("Spento"), SCANNING("Ricerca cuffie…"), CONNECTING("Connessione…"), CONNECTED("Connesse")
}

enum class Variant(val label: String) {
    UNKNOWN("da rilevare"), TEN_BAND("Y8 / Y8+ (EQ 10 bande)"), EIGHT_BAND("Y8+ U (EQ 8 bande)")
}

data class HeadsetState(
    val conn: Conn = Conn.OFF,
    val name: String? = null,
    val address: String? = null,
    val battery: Int? = null,
    val anc: Int? = null,
    val eqMode: Int? = null,
    val playing: Boolean? = null,
    val ldac: Boolean? = null,
    val multipoint: Boolean? = null,
    val game: Boolean? = null,
    val firmware: String? = null,
    val timerMin: Int? = null,
    val bands: List<Int> = emptyList(),
    val variant: Variant = Variant.UNKNOWN,
)

object Presets {
    val ANC = listOf("Normale", "Anti-rumore", "Trasparenza")

    /** Preset di fabbrica Y8 (comando EQ 0..7) */
    val FACTORY_10 = listOf("Default", "Pop", "Deep Bass", "Rock", "Classic", "Jazz", "Country", "Personalizzato")
    val FREQ_10 = listOf("32", "64", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")

    /** Preset di fabbrica Y8+ U: indice SW, nome, 8 guadagni in dB */
    data class P8(val index: Int, val name: String, val gains: List<Double>)

    val FACTORY_8 = listOf(
        P8(1, "Classic", listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
        P8(2, "Acoustic", listOf(4.0, 1.0, 2.0, 2.0, 4.0, 4.0, 4.0, 2.0)),
        P8(3, "Bass+", listOf(4.0, 3.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
        P8(4, "Bass-", listOf(-4.0, -3.0, -1.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
        P8(5, "Classical", listOf(3.0, 3.0, -2.0, -2.0, 0.0, 2.0, 3.0, 4.0)),
        P8(6, "Podcast", listOf(-3.0, 2.0, 4.0, 4.0, 3.0, 2.0, 0.0, -2.0)),
        P8(7, "Dance", listOf(2.0, -3.0, -1.0, 1.0, 2.0, 2.0, 1.0, -3.0)),
        P8(8, "Calm", listOf(2.0, 1.0, 3.0, 3.0, 2.0, -2.0, -4.0, -5.0)),
        P8(9, "Electronic", listOf(3.0, 2.0, -2.0, 2.0, 1.0, 2.0, 3.0, 3.0)),
        P8(10, "Flat", listOf(-2.0, -2.0, -1.0, 0.0, 0.0, 0.0, -2.0, -2.0)),
        P8(11, "Hip-Hop", listOf(2.0, 3.0, -1.0, -1.0, 2.0, -1.0, 2.0, 3.0)),
        P8(12, "Jazz", listOf(2.0, 2.0, -2.0, -2.0, 0.0, 2.0, 3.0, 4.0)),
        P8(13, "Latin", listOf(0.0, 0.0, -2.0, -2.0, -2.0, 0.0, 3.0, 5.0)),
        P8(14, "Chill", listOf(-1.0, 2.0, 4.0, 3.0, 0.0, -2.0, 2.0, 1.0)),
        P8(15, "Piano", listOf(0.0, 3.0, 3.0, 2.0, 4.0, 5.0, 3.0, 4.0)),
        P8(16, "Pop", listOf(-1.0, 1.0, 3.0, 3.0, 1.0, -1.0, -2.0, -3.0)),
        P8(17, "R&B", listOf(6.0, 2.0, -2.0, -2.0, 2.0, 3.0, 3.0, 4.0)),
        P8(18, "Rock", listOf(3.0, 2.0, -1.0, -1.0, 1.0, 3.0, 4.0, 5.0)),
        P8(19, "Small Speaker", listOf(4.0, 3.0, 1.0, 0.0, -2.0, -3.0, -4.0, -4.0)),
        P8(20, "Voice", listOf(-3.0, -2.0, 1.0, 2.0, 2.0, 1.0, 0.0, -3.0)),
        P8(21, "Treble+", listOf(-2.0, -2.0, -2.0, -1.0, 1.0, 2.0, 2.0, 4.0)),
        P8(22, "Treble-", listOf(0.0, 0.0, 0.0, -2.0, -3.0, -4.0, -4.0, -6.0)),
    )

    /** Preset studiati per questa app (10 bande, dB). Su 8 bande si usano le bande 64..8K. */
    data class Mine(val name: String, val g10: List<Int>) {
        val g8: List<Double> get() = g10.subList(1, 9).map { (it.coerceIn(-6, 6)).toDouble() }
    }

    val MINE = listOf(
        Mine("Bassi puliti", listOf(6, 5, 3, 0, -1, -1, 0, 1, 2, 1)),
        Mine("Bassi estremi", listOf(8, 7, 5, 1, -1, -2, 0, 1, 1, 0)),
        Mine("Equilibrato", listOf(2, 1, 0, -1, 0, 0, 1, 2, 1, 0)),
        Mine("Voce / podcast", listOf(-4, -3, -1, 0, 1, 2, 3, 2, 0, -2)),
        Mine("Notte (morbido)", listOf(2, 2, 1, 0, 0, -1, -2, -3, -4, -4)),
    )
}
