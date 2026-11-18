package com.rav.asistent

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
 * Radi u pozadini: drži plutajuće dugme sa mikrofonom, sluša komandu i izvršava je.
 */
class AsistentService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_SLUSAJ = "com.rav.asistent.SLUSAJ"
        const val ACTION_STOP = "com.rav.asistent.STOP"
        private const val KANAL = "asistent"
        private const val NOTIF_ID = 1
        private val PLAVA = 0xFF1E88E5.toInt()
        private val CRVENA = 0xFFE53935.toInt()
    }

    private lateinit var wm: WindowManager
    private lateinit var audio: AudioManager
    private lateinit var izvrsilac: Izvrsilac
    private val handler = Handler(Looper.getMainLooper())

    private var balon: ImageView? = null
    private var balonParams: WindowManager.LayoutParams? = null
    private var prepoznavac: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsSpreman = false
    private var fokus: AudioFocusRequest? = null
    private var slusa = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
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
            ACTION_SLUSAJ -> pocniSlusanje()
        }
        if (balon == null) prikaziBalon()
        return START_STICKY
    }

    override fun onDestroy() {
        prepoznavac?.destroy()
        prepoznavac = null
        tts?.shutdown()
        balon?.let { try { wm.removeView(it) } catch (ignored: Exception) { } }
        balon = null
        vratiFokus()
        super.onDestroy()
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

    // ---------- Slušanje ----------

    private fun pocniSlusanje() {
        if (slusa) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            govori("Treba mi dozvola za mikrofon. Otvori aplikaciju RAV Asistent.")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            govori("Na uređaju nema prepoznavanja govora. Instaliraj Google aplikaciju.")
            return
        }
        tts?.stop()
        uzmiFokus()
        prepoznavac?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        r.setRecognitionListener(slusalac)
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sr-RS")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "sr-RS")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        prepoznavac = r
        slusa = true
        oboji(true)
        r.startListening(i)
    }

    private fun prekiniSlusanje() {
        slusa = false
        prepoznavac?.cancel()
        oboji(false)
        vratiFokus()
    }

    private fun zavrsiSlusanje() {
        slusa = false
        oboji(false)
        vratiFokus()
    }

    private val slusalac = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            if (!slusa) return
            zavrsiSlusanje()
            val poruka = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nisam razumeo, probaj ponovo."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Nema interneta za prepoznavanje govora."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Nema dozvole za mikrofon."
                SpeechRecognizer.ERROR_AUDIO -> "Problem sa mikrofonom."
                12, 13 -> "Srpski jezik nije podržan na ovom uređaju."
                else -> "Greška pri slušanju, broj $error."
            }
            Prefs.sacuvajLog(this@AsistentService, "Greška $error")
            govori(poruka)
        }

        override fun onResults(results: Bundle?) {
            if (!slusa) return
            zavrsiSlusanje()
            val kandidati = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf<String>()
            val (tekst, akcija) = Komande.izaberi(kandidati)
            Prefs.sacuvajLog(this@AsistentService, "\"$tekst\" → ${akcija.opis()}")
            // mala pauza da se muzika vrati na normalnu jačinu pre izvršenja
            handler.postDelayed({ izvrsilac.izvrsi(akcija) }, 350)
        }
    }

    /** Dok slušamo, muzika se utiša da bi mikrofon bolje čuo. */
    private fun uzmiFokus() {
        val z = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        audio.requestAudioFocus(z)
        fokus = z
    }

    private fun vratiFokus() {
        fokus?.let { audio.abandonAudioFocusRequest(it) }
        fokus = null
    }

    // ---------- Plutajuće dugme ----------

    private fun krug(boja: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(boja)
    }

    private fun oboji(aktivno: Boolean) {
        balon?.background = krug(if (aktivno) CRVENA else PLAVA)
    }

    private fun prikaziBalon() {
        if (balon != null || !Settings.canDrawOverlays(this)) return
        val velicina = (64 * resources.displayMetrics.density).toInt()
        val v = ImageView(this)
        v.setImageResource(R.drawable.ic_mic)
        val pad = velicina / 5
        v.setPadding(pad, pad, pad, pad)
        v.background = krug(PLAVA)
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
                        if (slusa) prekiniSlusanje() else pocniSlusanje()
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
