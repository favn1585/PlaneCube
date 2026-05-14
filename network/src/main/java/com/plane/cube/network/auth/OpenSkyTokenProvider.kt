package com.plane.cube.network.auth

import com.plane.cube.network.BuildConfig
import com.plane.cube.network.di.AuthClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String,
)

/**
 * Caches an OpenSky OAuth2 client-credentials token in memory and refreshes
 * it slightly before expiry. OpenSky tokens are valid for ~30 minutes.
 */
@Singleton
class OpenSkyTokenProvider @Inject constructor(
    @AuthClient private val authHttpClient: HttpClient,
) {
    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var expiresAtEpochMs: Long = 0L

    suspend fun bearer(): String = mutex.withLock {
        val now = System.currentTimeMillis()
        val cached = cachedToken
        if (cached != null && now < expiresAtEpochMs - REFRESH_SKEW_MS) return cached

        val response: TokenResponse = authHttpClient.submitForm(
            url = TOKEN_URL,
            formParameters = Parameters.build {
                append("grant_type", "client_credentials")
                append("client_id", BuildConfig.OPENSKY_CLIENT_ID)
                append("client_secret", BuildConfig.OPENSKY_CLIENT_SECRET)
            },
        ).body()

        cachedToken = response.accessToken
        expiresAtEpochMs = now + response.expiresIn * 1_000L
        response.accessToken
    }

    companion object {
        private const val TOKEN_URL =
            "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token"
        private const val REFRESH_SKEW_MS = 60_000L
    }
}
