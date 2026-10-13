import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("com.android.application")
}

// Sem URL configurada o app funciona somente com o motor offline.
// Emulador com API local: -PURL_SHIELD_API_URL=http://10.0.2.2:8000
val urlShieldApiUrl = providers.gradleProperty("URL_SHIELD_API_URL")
    .orElse("")
val alternateJavac = providers.environmentVariable("URLSHIELD_JAVAC")
val localDebugKeystore = file(".local/urlshield-debug.keystore")

android {
    namespace = "br.com.urlshield"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.urlshield"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField(
            "String",
            "URL_SHIELD_API_URL",
            "\"${urlShieldApiUrl.get()}\"",
        )
    }

    signingConfigs {
        if (localDebugKeystore.exists()) {
            create("localDebug") {
                storeFile = localDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            if (localDebugKeystore.exists()) {
                signingConfig = signingConfigs.getByName("localDebug")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

tasks.withType<JavaCompile>().configureEach {
    alternateJavac.orNull?.let { executablePath ->
        options.isFork = true
        options.forkOptions.executable = executablePath
    }
}
