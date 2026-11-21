package app.y8control

import app.y8control.JlAuthTables.ISBOX
import app.y8control.JlAuthTables.KS_TABLE
import app.y8control.JlAuthTables.SBOX
import app.y8control.JlAuthTables.STATIC_KEY

/**
 * Autenticazione RCSP JieLi (porting Kotlin di web-bluetooth-e87/jl-auth.ts, licenza MIT).
 * Sequenza sul canale AE01/AE02 (byte grezzi, non incapsulati in FE DC BA):
 *   tel → cuffie  [00, rand×16]
 *   cuffie → tel  [01, enc×16]
 *   tel → cuffie  [02, 'p','a','s','s']
 *   cuffie → tel  [00, challenge×16]
 *   tel → cuffie  [01, E1(challenge)×16]
 *   cuffie → tel  [02, 'p','a','s','s']   ← autenticate
 */
object JlAuth {
    private val MAGIC = intArrayOf(0x11, 0x22, 0x33, 0x33, 0x22, 0x11)
    private const val MASK = 0x9999

    private fun keySchedule(d: IntArray): IntArray {
        val out = IntArray(272)
        for (i in 0 until 16) out[i] = d[i]
        val buf = IntArray(17)
        var cs = 0
        for (i in 0 until 16) { buf[i] = d[i]; cs = cs xor d[i] }
        buf[16] = cs and 0xFF
        for (rnd in 0 until 16) {
            for (i in 0 until 17) { val b = buf[i]; buf[i] = ((b shl 3) or (b ushr 5)) and 0xFF }
            var rp = (rnd + 1) % 17
            for (j in 0 until 16) {
                out[16 + rnd * 16 + j] = (KS_TABLE[0xF + rnd * 16 - j] + buf[rp]) and 0xFF
                rp++
                if (rp > 16) rp = 0
            }
        }
        return out
    }

    private fun fib(s: IntArray): IntArray {
        val r = LongArray(32)
        val M = 0xFFFFFFFFL
        r[16] = s[0].toLong(); r[17] = s[1].toLong(); r[3] = s[2].toLong(); r[4] = s[3].toLong()
        r[5] = s[4].toLong(); r[6] = s[5].toLong(); r[7] = s[6].toLong(); r[19] = s[7].toLong()
        r[20] = s[8].toLong(); r[21] = s[9].toLong(); r[22] = s[10].toLong(); r[23] = s[11].toLong()
        r[24] = s[12].toLong(); r[25] = s[13].toLong(); r[26] = s[14].toLong(); r[27] = s[15].toLong()
        fun a(x: Long, y: Long) = (x + y) and M
        fun d(x: Long, y: Long) = (x + y * 2) and M
        // stage 1
        r[28] = d(r[17], r[16]); r[16] = a(r[17], r[16])
        r[17] = d(r[4], r[3]); r[3] = a(r[4], r[3])
        r[4] = d(r[6], r[5]); r[5] = a(r[6], r[5])
        r[6] = d(r[19], r[7]); r[7] = a(r[19], r[7])
        r[19] = d(r[21], r[20]); r[20] = a(r[21], r[20])
        r[21] = d(r[23], r[22]); r[22] = a(r[23], r[22])
        r[23] = d(r[25], r[24]); r[24] = a(r[25], r[24])
        r[25] = d(r[27], r[26]); r[26] = a(r[27], r[26])
        // stage 2
        r[27] = d(r[22], r[19]); r[19] = a(r[22], r[19])
        r[22] = d(r[26], r[23]); r[23] = a(r[26], r[23])
        r[26] = d(r[16], r[17]); r[16] = a(r[17], r[16])
        r[17] = d(r[5], r[6]); r[5] = a(r[6], r[5])
        r[6] = d(r[20], r[21]); r[20] = a(r[21], r[20])
        r[21] = d(r[24], r[25]); r[24] = a(r[25], r[24])
        r[25] = d(r[7], r[28]); r[7] = a(r[7], r[28])
        r[28] = d(r[3], r[4]); r[3] = a(r[4], r[3])
        // stage 3
        r[4] = d(r[24], r[6]); r[6] = a(r[24], r[6])
        r[24] = d(r[3], r[25]); r[3] = a(r[25], r[3])
        r[25] = d(r[19], r[22]); r[19] = a(r[22], r[19])
        r[22] = d(r[16], r[17]); r[16] = a(r[17], r[16])
        r[17] = d(r[20], r[21]); r[20] = a(r[21], r[20])
        r[21] = d(r[7], r[28]); r[7] = a(r[7], r[28])
        r[28] = d(r[5], r[27]); r[5] = a(r[27], r[5])
        r[27] = d(r[23], r[26]); r[23] = a(r[23], r[26])
        // stage 4
        r[26] = d(r[7], r[17]); r[17] = a(r[17], r[7])
        r[7] = d(r[23], r[28]); r[23] = a(r[23], r[28])
        r[28] = d(r[6], r[24]); r[6] = a(r[6], r[24])
        r[24] = d(r[19], r[22]); r[19] = a(r[19], r[22])
        r[22] = d(r[20], r[21]); r[20] = a(r[20], r[21])
        r[21] = d(r[5], r[27]); r[5] = a(r[27], r[5])
        r[27] = d(r[16], r[4]); r[16] = a(r[4], r[16])
        r[4] = d(r[3], r[25]); r[3] = a(r[25], r[3])
        val idx = kotlin.intArrayOf(26, 17, 7, 23, 28, 6, 24, 19, 22, 20, 21, 5, 27, 16, 4, 3)
        return IntArray(16) { (r[idx[it]] and 0xFF).toInt() }
    }

    private fun condMix(s: IntArray, k: IntArray, off: Int, phase: Int): IntArray {
        val r = s.copyOf()
        for (i in 0 until 16) {
            val bit = ((1 shl i) and MASK) != 0
            val kv = k[off + i]
            r[i] = if (phase == 3) {
                if (bit) r[i] xor kv else (kv + r[i]) and 0xFF
            } else {
                if (bit) (kv + r[i]) and 0xFF else r[i] xor kv
            }
        }
        return r
    }

    private fun sbox(s: IntArray): IntArray {
        val r = s.copyOf()
        for (p in intArrayOf(0, 3, 4, 7, 8, 11, 12, 15)) r[p] = SBOX[r[p]]
        for (p in intArrayOf(1, 2, 5, 6, 9, 10, 13, 14)) r[p] = ISBOX[r[p]]
        return r
    }

    private fun block(input: IntArray, ek: IntArray, mode: Int): IntArray {
        var s = input.copyOf()
        val initial = input.copyOf()
        s = condMix(s, ek, 0, 3); s = sbox(s); s = condMix(s, ek, 16, 5)
        for (x9 in 1..8) {
            s = fib(s)
            val o = x9 * 0x20
            if (x9 == 8) { s = condMix(s, ek, 0x100, 3); break }
            if (mode != 0 && x9 == 2) {
                for (i in 0 until 16) {
                    val bit = ((1 shl i) and MASK) != 0
                    s[i] = if (bit) s[i] xor initial[i] else (initial[i] + s[i]) and 0xFF
                }
            }
            s = condMix(s, ek, o, 3); s = sbox(s); s = condMix(s, ek, o + 16, 5)
        }
        return s
    }

    fun e1(key6: IntArray, input: IntArray, seed: IntArray): IntArray {
        val ek = IntArray(16) { key6[it % 6] }
        val c = block(input, keySchedule(seed), 0)
        for (i in 0 until 16) c[i] = (ek[i] + (c[i] xor input[i])) and 0xFF
        val sub = intArrayOf(0x17, 0, 0x21, 0, 0x4D, 0, 0x6B, 0, 0, 0x1B, 0, 0x3F, 0, 0x59, 0, 0x7D)
        val xr = intArrayOf(0, 0xE5, 0, 0xC1, 0, 0xA7, 0, 0x83, 0xE9, 0, 0xDF, 0, 0xB3, 0, 0x95, 0)
        val obf = IntArray(16) { i -> if (sub[i] != 0) (seed[i] - sub[i]) and 0xFF else seed[i] xor xr[i] }
        return block(c, keySchedule(obf), 1)
    }

    fun randomPacket(): ByteArray {
        val r = ByteArray(17)
        java.security.SecureRandom().nextBytes(r)
        r[0] = 0
        return r
    }

    /** [00, challenge×16] → [01, enc×16] */
    fun answer(challenge: ByteArray): ByteArray {
        val inp = IntArray(16) { challenge[it + 1].toInt() and 0xFF }
        val enc = e1(MAGIC, inp, STATIC_KEY)
        return byteArrayOf(1) + ByteArray(16) { enc[it].toByte() }
    }

    val PASS = byteArrayOf(2, 'p'.code.toByte(), 'a'.code.toByte(), 's'.code.toByte(), 's'.code.toByte())

    /** Verifica con il vettore reale del progetto originale */
    fun selfTest(): Boolean {
        val ch = intArrayOf(0xb6, 0xe0, 0x80, 0xec, 0xaf, 0xf3, 0x22, 0x91, 0x6d, 0x88, 0xfa, 0xd5, 0xaa, 0x34, 0xc2, 0xac)
        val exp = intArrayOf(0x1d, 0x88, 0x97, 0xac, 0x46, 0x04, 0xd3, 0x32, 0xe8, 0x17, 0x5e, 0x81, 0xbb, 0x29, 0x25, 0x24)
        return e1(MAGIC, ch, STATIC_KEY).contentEquals(exp)
    }
}
