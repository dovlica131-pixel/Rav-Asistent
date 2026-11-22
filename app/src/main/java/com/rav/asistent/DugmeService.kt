package com.rav.asistent

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Hvata dugmiće na volanu (preko Pristupačnosti).
 * Zapamćeno dugme (ili glasovno dugme ako ništa nije zapamćeno) otvara slušanje.
 */
class DugmeService : AccessibilityService() {

    private val podrazumevana = setOf(
        KeyEvent.KEYCODE_VOICE_ASSIST,
        KeyEvent.KEYCODE_ASSIST,
        KeyEvent.KEYCODE_SEARCH
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val kod = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Prefs.sacuvajPoslednjeDugme(this, "${KeyEvent.keyCodeToString(kod)} ($kod)")

            if (Prefs.ucenje(this)) {
                Prefs.postaviDugme(this, kod)
                Prefs.postaviUcenje(this, false)
                Toast.makeText(this, "Zapamtio dugme: ${KeyEvent.keyCodeToString(kod)}", Toast.LENGTH_LONG).show()
                return true
            }
        }

        val zapamceno = Prefs.dugme(this)
        val jeOkidac = if (zapamceno > 0) kod == zapamceno else kod in podrazumevana
        if (!jeOkidac) return false

        if (event.action == KeyEvent.ACTION_UP) {
            try {
                startActivity(Intent(this, ListenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Prefs.sacuvajLog(this, "Dugme: ne mogu da pokrenem slušanje: ${e.message}")
            }
        }
        return true
    }
}
