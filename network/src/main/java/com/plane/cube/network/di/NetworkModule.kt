package com.plane.cube.network.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.plane.cube.domain.repository.PlaneRepository
import com.plane.cube.network.api.OpenSkyApi
import com.plane.cube.network.auth.AuthApi
import com.plane.cube.network.auth.AuthInterceptor
import com.plane.cube.network.repository.PlaneNetworkRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenSkyClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenSkyRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthRetrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindingsModule {

    @Binds
    @Singleton
    abstract fun bindPlaneRepository(impl: PlaneNetworkRepository): PlaneRepository
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val OPENSKY_BASE_URL = "https://opensky-network.org/"
    private const val AUTH_BASE_URL = "https://auth.opensky-network.org/"
    private const val JSON_MEDIA_TYPE = "application/json"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

    @Provides
    @Singleton
    @AuthClient
    fun provideAuthOkHttp(logging: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @OpenSkyClient
    fun provideOpenSkyOkHttp(
        logging: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(logging)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @AuthRetrofit
    fun provideAuthRetrofit(@AuthClient client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(AUTH_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
            .build()

    @Provides
    @Singleton
    @OpenSkyRetrofit
    fun provideOpenSkyRetrofit(@OpenSkyClient client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(OPENSKY_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAuthApi(@AuthRetrofit retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideOpenSkyApi(@OpenSkyRetrofit retrofit: Retrofit): OpenSkyApi =
        retrofit.create(OpenSkyApi::class.java)
}
