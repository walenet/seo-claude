package app.y8control

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Headset.init(this)
        Prefs.init(this)
    }
}

/** Impostazioni persistenti */
object Prefs {
    private lateinit var sp: android.content.SharedPreferences
    fun init(c: Context) {
        sp = c.getSharedPreferences("y8", Context.MODE_PRIVATE)
        HearingAid.gainDb = sp.getFloat("ha_gain", 10f)
        HearingAid.voiceDb = sp.getFloat("ha_voice", 6f)
        HearingAid.noiseReduction = sp.getBoolean("ha_nr", true)
        HearingAid.cutRumble = sp.getBoolean("ha_hp", true)
        Headset.eightBandFactor = sp.getFloat("eq8_factor", 1f).toDouble()
    }

    var autoOff: Boolean
        get() = sp.getBoolean("auto_off", false)
        set(v) = sp.edit().putBoolean("auto_off", v).apply()
    var autoOffMin: Int
        get() = sp.getInt("auto_off_min", 20)
        set(v) = sp.edit().putInt("auto_off_min", v).apply()
    var haLowLatency: Boolean
        get() = sp.getBoolean("ha_lowlat", true)
        set(v) = sp.edit().putBoolean("ha_lowlat", v).apply()

    fun saveHearing() {
        sp.edit().putFloat("ha_gain", HearingAid.gainDb).putFloat("ha_voice", HearingAid.voiceDb)
            .putBoolean("ha_nr", HearingAid.noiseReduction).putBoolean("ha_hp", HearingAid.cutRumble).apply()
    }

    fun saveFactor() = sp.edit().putFloat("eq8_factor", Headset.eightBandFactor.toFloat()).apply()
}

/** Stato dell'autospegnimento, mostrato in app */
object AutoOff {
    val idleMinutes = MutableStateFlow(0)
    val armed = MutableStateFlow(false)
}

class HeadsetService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val CH = "y8"
        const val ACTION_STOP = "stop"
        const val ACTION_ANC = "anc"
        const val ACTION_REFRESH_FG = "fg"

        fun start(c: Context, action: String? = null) {
            c.startForegroundService(Intent(c, HeadsetService::class.java).setAction(action))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Headset.init(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CH, "Cuffie Y8", NotificationManager.IMPORTANCE_LOW))
        goForeground()
        Headset.start()
        scope.launch {
            combine(Headset.state, HearingAid.running) { s, h -> s to h }.collect { (s, h) ->
                getSystemService(NotificationManager::class.java).notify(1, buildNotif(s, h))
            }
        }
        scope.launch { autoOffLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                HearingAid.stop(); Headset.stop()
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            }
            ACTION_ANC -> Headset.setAnc(((Headset.state.value.anc ?: 0) + 1) % 3)
            ACTION_REFRESH_FG -> goForeground()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground() {
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (HearingAid.running.value) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        try {
            startForeground(1, buildNotif(Headset.state.value, HearingAid.running.value), type)
        } catch (e: Exception) {
            Headset.log("Servizio: ${e.message}")
            startForeground(1, buildNotif(Headset.state.value, false), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        }
    }

    private fun pi(action: String?, code: Int): PendingIntent =
        if (action == null) PendingIntent.getActivity(this, code, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        else PendingIntent.getService(this, code, Intent(this, HeadsetService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun buildNotif(s: HeadsetState, hearing: Boolean): Notification {
        val title = when (s.conn) {
            Conn.CONNECTED -> "Y8+ connesse" + (s.battery?.let { " · $it%" } ?: "")
            else -> s.conn.label
        }
        val text = buildString {
            s.anc?.let { append(Presets.ANC.getOrElse(it) { "?" }) }
            if (hearing) append(" · 👂 apparecchio acustico attivo")
            if (Prefs.autoOff) append(" · auto-off ${Prefs.autoOffMin} min")
        }
        return Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi(null, 0))
            .addAction(Notification.Action.Builder(null, "ANC", pi(ACTION_ANC, 1)).build())
            .addAction(Notification.Action.Builder(null, "Chiudi", pi(ACTION_STOP, 2)).build())
            .build()
    }

    /**
     * Autospegnimento "vero": conta i minuti in cui dal telefono NON esce audio
     * (musica, video, chiamate, apparecchio acustico). Allo scadere chiede alle cuffie
     * di spegnersi tra 1 minuto; se nel frattempo riparte l'audio, annulla.
     */
    private suspend fun autoOffLoop() {
        val am = getSystemService(AudioManager::class.java)
        var lastActive = System.currentTimeMillis()
        var wasConnected = false
        while (scope.isActive) {
            delay(15_000)
            Headset.poll()
            val s = Headset.state.value
            val connected = s.conn == Conn.CONNECTED
            if (connected && !wasConnected) lastActive = System.currentTimeMillis()
            wasConnected = connected
            val active = am.isMusicActive || HearingAid.running.value || am.mode != AudioManager.MODE_NORMAL
            val now = System.currentTimeMillis()
            if (active || !Prefs.autoOff || !connected) {
                lastActive = now
                if (AutoOff.armed.value && connected) {
                    Headset.cancelShutdown()
                    Headset.log("Auto-off annullato: audio ripreso")
                }
                AutoOff.armed.value = false
                AutoOff.idleMinutes.value = 0
                continue
            }
            val idle = ((now - lastActive) / 60_000).toInt()
            AutoOff.idleMinutes.value = idle
            if (idle >= Prefs.autoOffMin && !AutoOff.armed.value) {
                Headset.shutdownIn(1)
                AutoOff.armed.value = true
                Headset.log("Auto-off: inattive da $idle min, spegnimento tra 1 minuto")
            }
        }
    }
}
