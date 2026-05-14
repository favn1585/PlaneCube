package com.plane.cube.network.auth

import com.plane.cube.network.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Caches an OpenSky OAuth2 client-credentials token in memory and refreshes
 * it slightly before expiry. OpenSky tokens are valid for ~30 minutes.
 */
@Singleton
class OpenSkyTokenProvider @Inject constructor(
    private val authApi: AuthApi,
) {
    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var expiresAtEpochMs: Long = 0L

    suspend fun bearer(): String = mutex.withLock {
        val now = System.currentTimeMillis()
        val cached = cachedToken
        if (cached != null && now < expiresAtEpochMs - REFRESH_SKEW_MS) return cached

        val response = authApi.token(
            grantType = "client_credentials",
            clientId = BuildConfig.OPENSKY_CLIENT_ID,
            clientSecret = BuildConfig.OPENSKY_CLIENT_SECRET,
        )

        cachedToken = response.accessToken
        expiresAtEpochMs = now + response.expiresIn * 1_000L
        response.accessToken
    }

    /** Drop the cached token so the next request fetches a fresh one. */
    suspend fun invalidate() = mutex.withLock {
        cachedToken = null
        expiresAtEpochMs = 0L
    }

    companion object {
        private const val REFRESH_SKEW_MS = 60_000L
    }
}
