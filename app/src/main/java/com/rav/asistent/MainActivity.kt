package com.rav.asistent

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var dugmeStatus: TextView
    private lateinit var kuca: EditText
    private lateinit var posao: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val pad = (20 * d).toInt()
        val kol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun naslov(t: String) = TextView(this).apply {
            text = t
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, (14 * d).toInt(), 0, (6 * d).toInt())
        }
        fun tekst(t: String) = TextView(this).apply {
            text = t
            textSize = 17f
            setPadding(0, (6 * d).toInt(), 0, (6 * d).toInt())
        }
        fun dugme(t: String, klik: () -> Unit) = Button(this).apply {
            text = t
            textSize = 18f
            isAllCaps = false
            setOnClickListener { klik() }
        }
        fun polje(hint: String, vrednost: String) = EditText(this).apply {
            this.hint = hint
            setText(vrednost)
            textSize = 18f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        kol.addView(naslov("RAV Asistent"))
        status = tekst("")
        kol.addView(status)

        kol.addView(naslov("Podešavanje (jednom)"))
        kol.addView(dugme("1. Dozvoli mikrofon") { traziDozvole() })
        kol.addView(dugme("2. Dozvoli prikaz preko drugih aplikacija") { otvoriOverlayPodesavanje() })
        kol.addView(dugme("3. Uključi asistenta") {
            Prefs.postaviAutostart(this, true)
            startForegroundService(Intent(this, AsistentService::class.java))
            Toast.makeText(this, "Asistent je uključen. Plavo dugme sa mikrofonom je na ekranu.", Toast.LENGTH_LONG).show()
        })
        kol.addView(dugme("Isključi asistenta") {
            Prefs.postaviAutostart(this, false)
            stopService(Intent(this, AsistentService::class.java))
        })

        kol.addView(naslov("Adrese"))
        kuca = polje("Kućna adresa (npr. Bulevar oslobođenja 10, Novi Sad)", Prefs.kuca(this))
        posao = polje("Adresa posla", Prefs.posao(this))
        kol.addView(kuca)
        kol.addView(posao)
        kol.addView(dugme("Sačuvaj adrese") {
            Prefs.sacuvajAdrese(this, kuca.text.toString(), posao.text.toString())
            Toast.makeText(this, "Sačuvano", Toast.LENGTH_SHORT).show()
        })

        kol.addView(naslov("Probaj"))
        kol.addView(dugme("🎤  Slušaj sada") {
            startForegroundService(Intent(this, AsistentService::class.java).setAction(AsistentService.ACTION_SLUSAJ))
        })

        kol.addView(naslov("Dugme na volanu"))
        dugmeStatus = tekst("")
        kol.addView(dugmeStatus)
        kol.addView(dugme("A. Uključi \"RAV Asistent – dugme na volanu\" u Pristupačnosti") {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Otvori Podešavanja → Pristupačnost ručno", Toast.LENGTH_LONG).show()
            }
        })
        kol.addView(dugme("B. Nauči dugme (klikni ovde, pa pritisni dugme na volanu)") {
            Prefs.postaviUcenje(this, true)
            Toast.makeText(this, "Sad pritisni dugme na volanu…", Toast.LENGTH_LONG).show()
        })
        kol.addView(dugme("Zaboravi naučeno dugme") {
            Prefs.postaviDugme(this, -1)
            osvezi()
        })

        kol.addView(naslov("Komande"))
        kol.addView(tekst(
            "• Vodi me kući / Idemo na posao\n" +
            "• Vodi me do Kragujevca / Navigacija do Ušća\n" +
            "• Proveri rutu ponovo / Najbrža ruta\n" +
            "• Najbliža pumpa / Nađi apoteku / Najbliži parking\n" +
            "• Pojačaj / Smanji / Pojačaj malo / Smanji dosta\n" +
            "• Jačina 10 / Zvuk na pola / Isključi zvuk / Vrati zvuk\n" +
            "• Pusti muziku / Pauza / Sledeća pesma / Prethodna\n" +
            "• Pusti Riblju čorbu (traži u Spotify/YouTube Music)"
        ))

        kol.addView(naslov("Poslednje što sam čuo"))
        log = tekst("")
        kol.addView(log)

        setContentView(ScrollView(this).apply { addView(kol) })
    }

    override fun onResume() {
        super.onResume()
        osvezi()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        osvezi()
    }

    private fun osvezi() {
        fun oznaka(ok: Boolean) = if (ok) "✅" else "❌"
        val mik = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val overlay = Settings.canDrawOverlays(this)
        val govor = SpeechRecognizer.isRecognitionAvailable(this)
        status.text = "${oznaka(mik)} Mikrofon\n" +
                "${oznaka(overlay)} Prikaz preko drugih aplikacija\n" +
                "${oznaka(govor)} Prepoznavanje govora (Google)\n" +
                "Android ${Build.VERSION.RELEASE} · verzija 1.2"
        val pristupacnost = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .contains(packageName)
        val kod = Prefs.dugme(this)
        val naucen = if (kod > 0) android.view.KeyEvent.keyCodeToString(kod) else "nije naučeno (koristi glasovno dugme)"
        val poslednje = Prefs.poslednjeDugme(this).ifBlank { "još ništa" }
        dugmeStatus.text = "${oznaka(pristupacnost)} Pristupačnost uključena\n" +
                "Dugme za slušanje: $naucen\n" +
                "Poslednje pritisnuto dugme: $poslednje"
        val l = Prefs.log(this)
        log.text = if (l.isBlank()) "Još ništa." else l
    }

    private fun traziDozvole() {
        val lista = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) lista.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(lista.toTypedArray(), 1)
    }

    private fun otvoriOverlayPodesavanje() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (e2: Exception) {
                Toast.makeText(this, "Otvori Podešavanja → Aplikacije → RAV Asistent ručno", Toast.LENGTH_LONG).show()
            }
        }
    }
}
