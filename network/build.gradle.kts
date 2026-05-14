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

        buildConfigField("String", "OPENSKY_CLIENT_ID", "\"${localProperty("OPENSKY_CLIENT_ID")}\"")
        buildConfigField("String", "OPENSKY_CLIENT_SECRET", "\"${localProperty("OPENSKY_CLIENT_SECRET")}\"")
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
