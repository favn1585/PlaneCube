package com.plane.cube.network

import android.util.Base64

/**
 * Resolves the ADS-B feed base URL at runtime. The host is stored XOR+Base64
 * obfuscated in BuildConfig so that:
 *  - `strings`/decompiling the APK does not reveal the plaintext domain, and
 *  - the URL never appears in release logs (HTTP logging is debug-only).
 *
 * This is obfuscation, not security: a TLS/network capture still exposes the
 * domain via SNI and the Host header. It only raises the bar for casual
 * reverse engineering.
 */
internal object Endpoints {

    // MUST stay in sync with OBFUSCATION_KEY in network/build.gradle.kts.
    private const val OBFUSCATION_KEY = "pl4n3cub3-xrk-2026-aDsB"

    fun adsbBaseUrl(): String = deobfuscate(BuildConfig.ADSB_BASE_URL_ENC)

    private fun deobfuscate(encoded: String): String {
        if (encoded.isEmpty()) return ""
        val data = Base64.decode(encoded, Base64.DEFAULT)
        val key = OBFUSCATION_KEY.toByteArray(Charsets.UTF_8)
        val out = ByteArray(data.size) { i -> (data[i].toInt() xor key[i % key.size].toInt()).toByte() }
        return String(out, Charsets.UTF_8)
    }
}
