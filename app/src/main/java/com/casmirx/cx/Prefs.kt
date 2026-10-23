package com.casmirx.cx

import android.content.Context

object Prefs {
    private val DEFAULT_GAMES = setOf(
        "com.activision.callofduty.shooter", // CODM global
        "com.garena.game.codm",              // CODM Garena
        "com.vng.codmvn"                     // CODM Vietnam
    )
    private fun sp(c: Context) = c.getSharedPreferences("cx", Context.MODE_PRIVATE)

    fun ratio(c: Context) = sp(c).getFloat("ratio", 1.3333f).toDouble()
    fun setRatio(c: Context, r: Double) = sp(c).edit().putFloat("ratio", r.toFloat()).apply()

    fun auto(c: Context) = sp(c).getBoolean("auto", false)
    fun setAuto(c: Context, v: Boolean) = sp(c).edit().putBoolean("auto", v).apply()

    fun extra(c: Context) = sp(c).getString("extra", "") ?: ""
    fun setExtra(c: Context, v: String) = sp(c).edit().putString("extra", v.trim()).apply()

    fun games(c: Context): Set<String> =
        DEFAULT_GAMES + extra(c).split(",", " ", "\n").map { it.trim() }.filter { it.isNotEmpty() }
}
