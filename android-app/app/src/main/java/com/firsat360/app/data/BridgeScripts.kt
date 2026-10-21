package com.firsat360.app.data

import android.content.Context

/** assets/bridge/common.js + <profile>.js (React Native bileşeniyle ORTAK tek kaynak). Bellekte önbelleklenir. */
object BridgeScripts {
    private val cache = HashMap<String, String>()

    @Synchronized
    fun get(ctx: Context, profile: String): String = cache.getOrPut(profile) {
        fun read(n: String) = ctx.assets.open("bridge/$n.js").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val body = try { read(profile) } catch (e: Exception) { read("generic") }
        read("common") + "\n" + body
    }
}
