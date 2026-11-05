import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing: keystore.properties in the project root, or environment variables (CI).
// Without either, the release APK is built unsigned so you can sign it yourself.
val signing = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(key: String, env: String): String? = signing.getProperty(key) ?: System.getenv(env)?.takeIf { it.isNotBlank() }
val storeFilePath = signingValue("storeFile", "HELPER_STORE_FILE")

android {
    namespace = "com.zyll.photo.helper"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zyll.photo.helper"
        // The cloud media feature in Zyll Photo is only offered on Android 15+, where
        // device_config overrides survive server config syncs.
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    androidResources {
        generateLocaleConfig = true
    }

    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = signingValue("storePassword", "HELPER_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "HELPER_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "HELPER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

// libadb-android bundles bcprov-jdk15to18, which clashes with bcprov-jdk18on
configurations.configureEach {
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
}

dependencies {
    // Wireless debugging pairing (TLS + SPAKE2) and the ADB protocol: GPL-3.0 / Apache-2.0
    implementation(libs.libadb.android)
    // libadb needs a Conscrypt that can export TLS keying material (Apache-2.0)
    implementation(libs.conscrypt.android)
    // Self-signed ADB client certificate
    implementation(libs.bouncycastle)
    implementation(libs.bouncycastle.pkix)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    testImplementation(libs.junit4)
}
