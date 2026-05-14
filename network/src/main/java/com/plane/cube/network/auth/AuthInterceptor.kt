package com.plane.cube.network.auth

import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <token>` to every OpenSky API call. If the
 * upstream returns 401 we invalidate the cached token once and retry — covers
 * the case where the cached token expired earlier than the cache thinks
 * (clock skew, server-side revocation).
 */
class AuthInterceptor @Inject constructor(
    private val tokenProvider: OpenSkyTokenProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenProvider.bearer() }
        val response = chain.proceed(chain.request().withBearer(token))
        if (response.code != 401) return response

        response.close()
        runBlocking { tokenProvider.invalidate() }
        val refreshed = runBlocking { tokenProvider.bearer() }
        return chain.proceed(chain.request().withBearer(refreshed))
    }

    private fun okhttp3.Request.withBearer(token: String) = newBuilder()
        .header("Authorization", "Bearer $token")
        .build()
}
