import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

private fun localProperty(key: String): String {
    val file = rootProject.file("local.properties")
    if (!file.exists()) return ""
    val props = Properties().apply { file.inputStream().use { load(it) } }
    return props.getProperty(key).orEmpty()
}

// MUST stay in sync with Endpoints.OBFUSCATION_KEY in the Kotlin source.
// This is obfuscation, not encryption: it only keeps the plaintext host out of
// the APK string table and logs. A network capture can still reveal the domain.
val OBFUSCATION_KEY = "pl4n3cub3-xrk-2026-aDsB"

private fun obfuscate(value: String): String {
    val key = OBFUSCATION_KEY.toByteArray(Charsets.UTF_8)
    val data = value.toByteArray(Charsets.UTF_8)
    val out = ByteArray(data.size) { i -> (data[i].toInt() xor key[i % key.size].toInt()).toByte() }
    return Base64.getEncoder().encodeToString(out)
}

android {
    namespace = "com.plane.cube.network"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField(
            "String",
            "ADSB_BASE_URL_ENC",
            "\"${obfuscate(localProperty("ADSB_BASE_URL"))}\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":domain"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.logging.interceptor)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
