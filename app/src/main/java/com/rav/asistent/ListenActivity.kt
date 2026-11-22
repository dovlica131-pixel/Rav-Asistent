package com.rav.asistent

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent

/**
 * Otvara Google-ov prozor za slušanje (radi pouzdano jer je u prvom planu),
 * a prepoznati tekst šalje servisu da izvrši komandu.
 */
class ListenActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sr-RS")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "sr-RS")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Reci komandu")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        try {
            startActivityForResult(i, 1)
        } catch (e: Exception) {
            Prefs.sacuvajLog(this, "Ne mogu da otvorim Google slušanje: ${e.message}")
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val lista = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        if (resultCode == RESULT_OK && !lista.isNullOrEmpty()) {
            startForegroundService(
                Intent(this, AsistentService::class.java)
                    .setAction(AsistentService.ACTION_REZULTAT)
                    .putStringArrayListExtra(AsistentService.EXTRA_TEKST, lista)
            )
        } else {
            Prefs.sacuvajLog(this, "Ništa nije prepoznato (kod $resultCode)")
        }
        finish()
    }
}
