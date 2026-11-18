package com.rav.asistent

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("asistent", Context.MODE_PRIVATE)

    fun kuca(c: Context) = sp(c).getString("kuca", "") ?: ""
    fun posao(c: Context) = sp(c).getString("posao", "") ?: ""
    fun sacuvajAdrese(c: Context, kuca: String, posao: String) =
        sp(c).edit().putString("kuca", kuca.trim()).putString("posao", posao.trim()).apply()

    fun poslednjiCilj(c: Context) = sp(c).getString("cilj", "") ?: ""
    fun sacuvajCilj(c: Context, cilj: String) = sp(c).edit().putString("cilj", cilj).apply()

    fun balonX(c: Context) = sp(c).getInt("bx", 40)
    fun balonY(c: Context) = sp(c).getInt("by", 200)
    fun sacuvajBalon(c: Context, x: Int, y: Int) = sp(c).edit().putInt("bx", x).putInt("by", y).apply()

    fun autostart(c: Context) = sp(c).getBoolean("auto", false)
    fun postaviAutostart(c: Context, v: Boolean) = sp(c).edit().putBoolean("auto", v).apply()

    fun log(c: Context) = sp(c).getString("log", "") ?: ""
    fun sacuvajLog(c: Context, red: String) {
        val vreme = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        val linije = (listOf("$vreme  $red") + log(c).lines().filter { it.isNotBlank() }).take(8)
        sp(c).edit().putString("log", linije.joinToString("\n")).apply()
    }
}
