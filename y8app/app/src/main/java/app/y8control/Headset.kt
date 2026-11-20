package app.y8control

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Connessione BLE con le cuffie + stato. Singleton usato da servizio e interfaccia. */
@SuppressLint("MissingPermission")
object Headset {
    private lateinit var ctx: Context
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(HeadsetState())
    val state: StateFlow<HeadsetState> = _state.asStateFlow()
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private val queue = ArrayDeque<ByteArray>()
    private var writing = false
    private var wanted = false
    private var scanning = false
    private var lastPoll = 0L

    fun init(context: Context) {
        if (!::ctx.isInitialized) ctx = context.applicationContext
    }

    fun log(msg: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.ITALY).format(Date())
        _log.update { (listOf("$t  $msg") + it).take(200) }
    }

    private val adapter get() = ctx.getSystemService(BluetoothManager::class.java)?.adapter

    // ---------------- connessione ----------------

    fun start() {
        wanted = true
        if (gatt == null && !scanning) scan()
    }

    fun stop() {
        wanted = false
        stopScan()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        writeChar = null
        queue.clear(); writing = false
        _state.update { it.copy(conn = Conn.OFF) }
    }

    fun reconnect() {
        log("Riconnessione manuale")
        stop(); start()
    }

    /** MAC delle cuffie già abbinate (Impostazioni → Bluetooth) il cui nome inizia con "Y8". */
    private fun bondedMacs(): List<ByteArray> = try {
        adapter?.bondedDevices.orEmpty()
            .filter { (it.name ?: "").startsWith("Y8") }
            .map { d -> d.address.split(":").map { it.toInt(16).toByte() }.toByteArray() }
    } catch (e: SecurityException) { emptyList() }

    private fun containsSeq(hay: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || hay.size < needle.size) return false
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val rec = result.scanRecord
            val name = rec?.deviceName ?: try { result.device.name } catch (e: SecurityException) { null }
            val raw = rec?.bytes ?: ByteArray(0)
            val byName = name != null && (name.trim() == "Y8+" || name.trim() == "Y8" || name.startsWith("Y8"))
            val byMac = bondedMacs().any { containsSeq(raw, it) || containsSeq(raw, it.reversedArray()) }
            if (byName || byMac) {
                log("Trovate: ${name ?: "?"} ${result.device.address} (${if (byName) "nome" else "MAC"})")
                stopScan()
                connect(result.device, name)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            log("Scansione fallita ($errorCode)")
            scanning = false
            retryLater()
        }
    }

    private fun scan() {
        val sc = adapter?.bluetoothLeScanner
        if (adapter?.isEnabled != true || sc == null) {
            log("Bluetooth spento")
            retryLater(); return
        }
        scanning = true
        _state.update { it.copy(conn = Conn.SCANNING) }
        try {
            sc.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCb)
        } catch (e: SecurityException) {
            log("Permesso Bluetooth mancante"); scanning = false; return
        }
        main.postDelayed({
            if (scanning) {
                stopScan()
                log("Cuffie non trovate: sono accese? L'app YeeMall è chiusa?")
                retryLater()
            }
        }, 20_000)
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        try { adapter?.bluetoothLeScanner?.stopScan(scanCb) } catch (_: Exception) {}
    }

    private fun retryLater() {
        if (!wanted) return
        _state.update { it.copy(conn = Conn.OFF) }
        main.postDelayed({ if (wanted && gatt == null && !scanning) scan() }, 10_000)
    }

    private fun connect(device: BluetoothDevice, name: String?) {
        _state.update { it.copy(conn = Conn.CONNECTING, name = name ?: it.name, address = device.address) }
        gatt = device.connectGatt(ctx, false, gattCb, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) = main.post {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("BLE connesso, cerco i servizi")
                g.requestMtu(185)
                main.postDelayed({ g.discoverServices() }, 300)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("BLE disconnesso (status $status)")
                g.close()
                if (gatt == g) gatt = null
                writeChar = null; queue.clear(); writing = false
                retryLater()
            }
        }.let {}

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = main.post {
            // diagnostica: elenco completo di servizi e caratteristiche
            g.services.forEach { sv ->
                log("SVC ${short(sv.uuid)}: " + sv.characteristics.joinToString { c -> short(c.uuid) + "[" + props(c.properties) + "]" })
            }
            val svc = g.getService(Proto.SERVICE)
            if (svc == null) {
                log("Servizio FAA0 non trovato")
                return@post
            }
            writeChar = svc.getCharacteristic(Proto.WRITE)
            if (writeChar == null) { log("Caratteristica FAA1 mancante"); return@post }
            // attiva notifiche/indicazioni su TUTTE le caratteristiche che le supportano (servizi non standard)
            cccdQueue.clear()
            g.services.filter { !short(it.uuid).startsWith("180") }.forEach { sv ->
                sv.characteristics.forEach { c ->
                    val canN = c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                    val canI = c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                    if (canN || canI) {
                        g.setCharacteristicNotification(c, true)
                        c.getDescriptor(Proto.CCCD)?.let { d ->
                            val v = if (canN) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                            cccdQueue.addLast(Pair(d, v))
                        }
                    }
                }
            }
            nextCccd(g)
        }.let {}

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) = main.post {
            log("Notifiche ${short(d.characteristic.uuid)}: " + if (status == 0) "attive" else "ERRORE $status")
            nextCccd(g)
        }.let {}

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) = main.post {
            if (status != BluetoothGatt.GATT_SUCCESS) log("Scrittura fallita ($status)")
            writing = false
            pump()
        }.let {}

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            val copy = value.copyOf()
            main.post {
                if (c.uuid != Proto.NOTIFY) log("(da ${short(c.uuid)})")
                val pk = Proto.split(copy)
                if (pk.isEmpty()) log("<< grezzo ${Proto.hex(copy)}") else pk.forEach { handle(it) }
            }
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            val copy = value.copyOf()
            main.post { log("lettura ${short(c.uuid)} ($status): ${Proto.hex(copy)}"); Proto.split(copy).forEach { handle(it) } }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) = main.post { log("MTU $mtu ($status)") }.let {}
    }

    private val cccdQueue = ArrayDeque<Pair<BluetoothGattDescriptor, ByteArray>>()

    private fun nextCccd(g: BluetoothGatt) {
        val next = cccdQueue.removeFirstOrNull()
        if (next == null) { onReady(); return }
        val rc = g.writeDescriptor(next.first, next.second)
        if (rc != 0) { log("CCCD ${short(next.first.characteristic.uuid)} errore avvio $rc"); main.postDelayed({ nextCccd(g) }, 200) }
    }

    private fun short(u: java.util.UUID) = u.toString().substring(4, 8).uppercase()

    private fun props(p: Int) = buildString {
        if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) append("R")
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) append("W")
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) append("w")
        if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) append("N")
        if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) append("I")
    }

    /** Lettura diretta della caratteristica di notifica (diagnostica) */
    fun readNotifyChar() {
        val c = gatt?.getService(Proto.SERVICE)?.getCharacteristic(Proto.NOTIFY) ?: return
        if (c.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) gatt?.readCharacteristic(c)
        else log("FAA2 non leggibile")
    }

    private fun onReady() {
        _state.update { it.copy(conn = Conn.CONNECTED) }
        log("Pronte ✔")
        refreshAll()
    }

    // ---------------- invio ----------------

    fun send(cmd: String, data: ByteArray = byteArrayOf(0)) {
        val f = Proto.frame(cmd, data)
        if (writeChar == null) { log("Non connesse, ignoro $cmd"); return }
        queue.addLast(f)
        pump()
    }

    private fun pump() {
        val g = gatt ?: return
        val c = writeChar ?: return
        if (writing || queue.isEmpty()) return
        val f = queue.removeFirst()
        val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val rc = g.writeCharacteristic(c, f, type)
        log(">> ${Proto.hex(f)}" + (if (type == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) " [nr]" else "") +
            (if (rc != 0) "  (errore $rc)" else ""))
        writing = true
        main.postDelayed({ if (writing) { writing = false; pump() } }, 600)
    }

    // ---------------- risposte ----------------

    private fun u(b: Byte) = b.toInt() and 0xFF

    private fun handle(p: ByteArray) {
        log("<< ${Proto.hex(p)}")
        if (p.size < 5) return
        val cmd = String(byteArrayOf(p[1], p[2]), Charsets.US_ASCII)
        val len = u(p[3])
        val d = p.copyOfRange(4, minOf(p.size, 4 + len))
        fun at(i: Int) = if (i < d.size) u(d[i]) else null
        when (cmd) {
            "GA" -> _state.update {
                it.copy(anc = at(0), eqMode = at(1), playing = at(2)?.let { v -> v != 0 },
                    ldac = at(3)?.let { v -> v == 1 }, multipoint = at(4)?.let { v -> v == 1 },
                    game = if (d.size > 5) at(5)?.let { v -> v == 1 } else it.game)
            }
            "BV" -> _state.update { it.copy(battery = at(0)?.takeIf { v -> v in 0..100 && at(1) != 0 }) }
            "SF" -> if (d.size >= 3) _state.update { it.copy(firmware = "${u(d[0])}.${u(d[1])}.${u(d[2])}") }
            "TA" -> _state.update { it.copy(timerMin = at(0)) }
            "AC" -> _state.update { it.copy(anc = at(0)) }
            "EQ" -> _state.update { it.copy(eqMode = at(0)) }
            "LC" -> _state.update { it.copy(ldac = at(0) == 1) }
            "OM" -> _state.update { it.copy(multipoint = at(0) == 1) }
            "GM" -> _state.update { it.copy(game = at(0) == 1) }
            "PL" -> _state.update { it.copy(playing = at(0) == 1) }
            "LS" -> if (at(0) == 0) log("Audio Bluetooth disconnesso")
            "SW" -> send("GE")
            "SL" -> send("GE")
            "GE" -> {
                // Y8 = 10 guadagni, Y8+U = preset + 8 guadagni
                val payload = d.toList()
                if (payload.size >= 10) {
                    _state.update { it.copy(variant = Variant.TEN_BAND, bands = payload.take(10).map { b -> b.toInt() }) }
                } else if (payload.size >= 9) {
                    _state.update {
                        it.copy(variant = Variant.EIGHT_BAND, eqMode = u(payload[0]),
                            bands = payload.subList(1, 9).map { b -> b.toInt() })
                    }
                }
            }
        }
    }

    // ---------------- comandi ad alto livello ----------------

    fun refreshAll() {
        send("GA"); send("BV"); send("SF"); send("GE"); send("TA")
    }

    fun poll() {
        val now = System.currentTimeMillis()
        if (state.value.conn == Conn.CONNECTED && now - lastPoll > 60_000) {
            lastPoll = now
            send("BV"); send("GA")
        }
    }

    private fun refreshSoon() = main.postDelayed({ send("GA") }, 500)

    fun setAnc(mode: Int) { send("AC", byteArrayOf(mode.toByte())); refreshSoon() }

    fun setLdac(on: Boolean) {
        if (on) { send("OM", byteArrayOf(0)); send("GM", byteArrayOf(0)) }
        send("LC", byteArrayOf(if (on) 1 else 0)); refreshSoon()
    }

    fun setGame(on: Boolean) {
        if (on && state.value.ldac == true) send("LC", byteArrayOf(0))
        send("GM", byteArrayOf(if (on) 1 else 0))
        _state.update { it.copy(game = on) }
        refreshSoon()
    }

    fun setMultipoint(on: Boolean) {
        if (on && state.value.ldac == true) send("LC", byteArrayOf(0))
        send("OM", byteArrayOf(if (on) 1 else 0)); refreshSoon()
    }

    /** Preset di fabbrica Y8 (0..7) */
    fun setFactoryEq10(i: Int) { send("EQ", byteArrayOf(i.toByte())); refreshSoon() }

    /** Fattore di scala EQ per Y8+U: 1.0 (JieLi) o 0.5 (Bluetrum) */
    var eightBandFactor: Double = 1.0

    private fun to8(g: List<Double>) = g.map { (it * eightBandFactor * 10).roundToInt().coerceIn(-127, 127).toByte() }

    fun setEq8(index: Int, gains: List<Double>) {
        send("SW", byteArrayOf(index.toByte()) + to8(gains).toByteArray())
        _state.update { it.copy(eqMode = index) }
    }

    /** EQ personalizzato Y8 10 bande: modo 7 + una banda alla volta */
    fun setCustom10(gains: List<Int>) {
        send("EQ", byteArrayOf(7))
        gains.forEachIndexed { i, g -> send("SQ", byteArrayOf(i.toByte(), g.coerceIn(-12, 12).toByte())) }
        _state.update { it.copy(eqMode = 7, bands = gains) }
    }

    fun setBand10(i: Int, g: Int) {
        if (state.value.eqMode != 7) send("EQ", byteArrayOf(7))
        send("SQ", byteArrayOf(i.toByte(), g.toByte()))
        _state.update { s -> s.copy(eqMode = 7, bands = s.bands.toMutableList().also { if (i < it.size) it[i] = g }) }
    }

    fun applyMine(p: Presets.Mine) {
        when (state.value.variant) {
            Variant.EIGHT_BAND -> setEq8(0, p.g8)
            else -> setCustom10(p.g10)
        }
    }

    fun shutdownIn(min: Int) = send("OD", byteArrayOf(min.coerceIn(1, 255).toByte()))
    fun cancelShutdown() = send("CL")
    fun play(on: Boolean) = send("PL", byteArrayOf(if (on) 1 else 0))
    fun track(next: Boolean) = send("LT", byteArrayOf(if (next) 1 else 0))
}
