package com.rav.asistent

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.view.KeyEvent

/** Izvršava prepoznate komande: Google mape, zvuk, muzika. */
class Izvrsilac(private val ctx: Context, private val govori: (String) -> Unit) {

    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    companion object {
        private const val MAPE = "com.google.android.apps.maps"
    }

    fun izvrsi(a: Akcija) {
        when (a) {
            is Akcija.Navigacija -> navigiraj(a.cilj, "Vodim te do ${a.cilj}")
            is Akcija.Kuci -> {
                val adr = Prefs.kuca(ctx)
                if (adr.isBlank()) govori("Nisi upisao kućnu adresu. Otvori RAV Asistent i upiši je.")
                else navigiraj(adr, "Idemo kući")
            }
            is Akcija.NaPosao -> {
                val adr = Prefs.posao(ctx)
                if (adr.isBlank()) govori("Nisi upisao adresu posla. Otvori RAV Asistent i upiši je.")
                else navigiraj(adr, "Idemo na posao")
            }
            is Akcija.PonoviRutu -> {
                val c = Prefs.poslednjiCilj(ctx)
                if (c.isBlank()) govori("Nemam zapamćen cilj. Prvo reci gde ideš.")
                else navigiraj(c, "Proveravam najbržu rutu")
            }
            is Akcija.Pretraga -> {
                if (otvoriMape(Uri.parse("geo:0,0?q=" + Uri.encode(a.upit)))) govori("Tražim ${a.upit}")
            }
            is Akcija.Jacina -> jacina(a)
            is Akcija.Medij -> medij(a.tip)
            is Akcija.PustiPesmu -> pustiPesmu(a.upit)
            is Akcija.Poruka -> govori(a.tekst)
            is Akcija.Nepoznato ->
                govori(if (a.tekst.isBlank()) "Nisam čuo komandu." else "Ne znam šta da radim sa: ${a.tekst}")
        }
    }

    private fun navigiraj(cilj: String, poruka: String) {
        Prefs.sacuvajCilj(ctx, cilj)
        // Ponovno pokretanje navigacije do istog cilja tera Mape da preračunaju rutu po trenutnoj gužvi
        if (otvoriMape(Uri.parse("google.navigation:q=" + Uri.encode(cilj) + "&mode=d"))) govori(poruka)
    }

    private fun otvoriMape(uri: Uri): Boolean {
        val i = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.setPackage(MAPE)
        return try {
            ctx.startActivity(i); true
        } catch (e: Exception) {
            try {
                i.setPackage(null); ctx.startActivity(i); true
            } catch (e2: Exception) {
                govori("Ne mogu da otvorim Google mape."); false
            }
        }
    }

    private fun jacina(a: Akcija.Jacina) {
        val s = AudioManager.STREAM_MUSIC
        val f = AudioManager.FLAG_SHOW_UI
        when (a.tip) {
            TipJacine.GORE -> repeat(a.vrednost) { audio.adjustStreamVolume(s, AudioManager.ADJUST_RAISE, f) }
            TipJacine.DOLE -> repeat(a.vrednost) { audio.adjustStreamVolume(s, AudioManager.ADJUST_LOWER, f) }
            TipJacine.ISKLJUCI -> audio.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, f)
            TipJacine.UKLJUCI -> audio.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, f)
            TipJacine.POSTAVI -> {
                val max = audio.getStreamMaxVolume(s)
                val v = if (a.vrednost < 0) max / 2 else a.vrednost.coerceIn(0, max)
                audio.setStreamVolume(s, v, f)
            }
        }
    }

    private fun medij(t: TipMedija) {
        val kod = when (t) {
            TipMedija.PUSTI -> KeyEvent.KEYCODE_MEDIA_PLAY
            TipMedija.PAUZA -> KeyEvent.KEYCODE_MEDIA_PAUSE
            TipMedija.SLEDECA -> KeyEvent.KEYCODE_MEDIA_NEXT
            TipMedija.PRETHODNA -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, kod))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, kod))
    }

    private fun pustiPesmu(upit: String) {
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(SearchManager.QUERY, upit)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(i)
            govori("Puštam $upit")
        } catch (e: Exception) {
            govori("Nemam aplikaciju koja može da pusti $upit")
        }
    }
}
