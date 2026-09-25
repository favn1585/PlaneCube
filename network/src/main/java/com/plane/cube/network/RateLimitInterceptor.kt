package com.plane.cube.network

import android.os.SystemClock
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Spaces requests at least [minIntervalMs] apart across the whole client.
 * adsb.fi's open data API allows about one request per second; the map can
 * issue two per tick (visible area + tracking area) and the background worker
 * shares the same client, so throttling here keeps every caller within quota.
 */
internal class RateLimitInterceptor(
    private val minIntervalMs: Long,
) : Interceptor {

    private val lock = Any()
    private var lastRequestAt = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        synchronized(lock) {
            val wait = lastRequestAt + minIntervalMs - SystemClock.elapsedRealtime()
            if (wait > 0) Thread.sleep(wait)
            lastRequestAt = SystemClock.elapsedRealtime()
        }
        return chain.proceed(chain.request())
    }
}
