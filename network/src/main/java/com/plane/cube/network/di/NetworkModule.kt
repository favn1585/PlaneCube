package com.plane.cube.network.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.plane.cube.domain.repository.PlaneRepository
import com.plane.cube.network.BuildConfig
import com.plane.cube.network.Endpoints
import com.plane.cube.network.api.AdsbApi
import com.plane.cube.network.repository.PlaneNetworkRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

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
    fun provideOkHttp(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
        // HTTP logging only in debug builds, so the feed URL never lands in
        // release logcat.
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY },
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(Endpoints.adsbBaseUrl())
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAdsbApi(retrofit: Retrofit): AdsbApi = retrofit.create(AdsbApi::class.java)
}
