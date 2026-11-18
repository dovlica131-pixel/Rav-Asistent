package com.rav.asistent

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle

/** Ikonica "Govori": odmah pokreće slušanje i nestaje. Može se vezati za dugme na volanu. */
class ListenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startForegroundService(Intent(this, AsistentService::class.java).setAction(AsistentService.ACTION_SLUSAJ))
        }
        finish()
    }
}
