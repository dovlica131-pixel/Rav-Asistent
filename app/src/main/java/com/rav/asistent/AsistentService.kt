package com.rav.asistent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import java.util.Locale
import kotlin.math.abs

/**
 * Radi u pozadini: drži plutajuće dugme sa mikrofonom.
 * Dodir otvara Google slušanje (ListenActivity), a rezultat stiže nazad ovde i izvršava se.
 */
class AsistentService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_SLUSAJ = "com.rav.asistent.SLUSAJ"
        const val ACTION_REZULTAT = "com.rav.asistent.REZULTAT"
        const val ACTION_STOP = "com.rav.asistent.STOP"
        const val EXTRA_TEKST = "tekst"
        private const val KANAL = "asistent"
        private const val NOTIF_ID = 1
        private val PLAVA = 0xFF1E88E5.toInt()
    }

    private lateinit var wm: WindowManager
    private lateinit var izvrsilac: Izvrsilac
    private val handler = Handler(Looper.getMainLooper())

    private var balon: ImageView? = null
    private var balonParams: WindowManager.LayoutParams? = null
    private var tts: TextToSpeech? = null
    private var ttsSpreman = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        izvrsilac = Izvrsilac(this) { govori(it) }
        napraviKanal()
        startForeground(NOTIF_ID, napraviNotifikaciju())
        tts = TextToSpeech(this, this)
        prikaziBalon()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Prefs.postaviAutostart(this, false)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SLUSAJ -> otvoriSlusanje()
            ACTION_REZULTAT -> obradi(intent.getStringArrayListExtra(EXTRA_TEKST) ?: arrayListOf())
        }
        if (balon == null) prikaziBalon()
        return START_STICKY
    }

    override fun onDestroy() {
        tts?.shutdown()
        balon?.let { try { wm.removeView(it) } catch (ignored: Exception) { } }
        balon = null
        super.onDestroy()
    }

    // ---------- Slušanje i izvršavanje ----------

    private fun otvoriSlusanje() {
        tts?.stop()
        try {
            startActivity(Intent(this, ListenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Prefs.sacuvajLog(this, "Ne mogu da pokrenem slušanje: ${e.message}")
            govori("Ne mogu da pokrenem slušanje.")
        }
    }

    private fun obradi(kandidati: List<String>) {
        val (tekst, akcija) = Komande.izaberi(kandidati)
        Prefs.sacuvajLog(this, "\"$tekst\" → ${akcija.opis()}")
        // mala pauza da se Google prozor zatvori i muzika vrati
        handler.postDelayed({ izvrsilac.izvrsi(akcija) }, 400)
    }

    // ---------- Govor (odgovori asistenta) ----------

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val t = tts ?: return
        for (loc in listOf(Locale("sr", "RS"), Locale("sr"), Locale("hr", "HR"), Locale("bs", "BA"))) {
            val r = t.setLanguage(loc)
            if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsSpreman = true
                return
            }
        }
    }

    fun govori(tekst: String) {
        Toast.makeText(this, tekst, Toast.LENGTH_SHORT).show()
        if (ttsSpreman) tts?.speak(tekst, TextToSpeech.QUEUE_FLUSH, null, "asistent")
    }

    // ---------- Plutajuće dugme ----------

    private fun prikaziBalon() {
        if (balon != null || !Settings.canDrawOverlays(this)) return
        val velicina = (64 * resources.displayMetrics.density).toInt()
        val v = ImageView(this)
        v.setImageResource(R.drawable.ic_mic)
        val pad = velicina / 5
        v.setPadding(pad, pad, pad, pad)
        v.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(PLAVA)
        }
        v.elevation = 8f
        val p = WindowManager.LayoutParams(
            velicina, velicina,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = Prefs.balonX(this)
        p.y = Prefs.balonY(this)
        v.setOnTouchListener(DodirBalona())
        try {
            wm.addView(v, p)
            balon = v
            balonParams = p
        } catch (e: Exception) {
            Prefs.sacuvajLog(this, "Ne mogu da prikažem dugme: ${e.message}")
        }
    }

    /** Kratak dodir = slušaj, prevlačenje = pomeri dugme. */
    private inner class DodirBalona : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var dodirX = 0f
        private var dodirY = 0f
        private var pomeren = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val p = balonParams ?: return false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = p.x; startY = p.y
                    dodirX = e.rawX; dodirY = e.rawY
                    pomeren = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val mx = e.rawX - dodirX
                    val my = e.rawY - dodirY
                    if (abs(mx) > 12 || abs(my) > 12) pomeren = true
                    if (pomeren) {
                        p.x = startX + mx.toInt()
                        p.y = startY + my.toInt()
                        wm.updateViewLayout(v, p)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (pomeren) {
                        Prefs.sacuvajBalon(this@AsistentService, p.x, p.y)
                    } else {
                        v.performClick()
                        otvoriSlusanje()
                    }
                }
            }
            return true
        }
    }

    // ---------- Notifikacija ----------

    private fun napraviKanal() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(KANAL, "RAV Asistent", NotificationManager.IMPORTANCE_LOW))
    }

    private fun napraviNotifikaciju(): Notification {
        val slusajPi = PendingIntent.getService(
            this, 0,
            Intent(this, AsistentService::class.java).setAction(ACTION_SLUSAJ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, AsistentService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, KANAL)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("RAV Asistent radi")
            .setContentText("Dodirni da kažeš komandu")
            .setContentIntent(slusajPi)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_mic), "Ugasi", stopPi).build())
            .setOngoing(true)
            .build()
    }
}
