import java.util.Properties

plugins {
    id("com.android.application")
}

// Absent on any machine lacking the signing secrets, which then produces an unsigned
// APK rather than failing, so third parties can still build from a clean checkout.
val signingSecrets = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use(::load) }
}

// Pinned so a machine with several JDKs, or one whose default is newer than AGP
// accepts, still compiles against the version this project is built for.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

android {
    namespace = "net.fosterish.prongs"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.fosterish.prongs"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        signingSecrets?.let { secrets ->
            create("release") {
                storeFile = file(secrets.getProperty("storeFile"))
                storeType = secrets.getProperty("storeType")
                keyAlias = secrets.getProperty("keyAlias")
                storePassword = secrets.getProperty("storePassword")
                keyPassword = secrets.getProperty("keyPassword")
                // v1 predates minSdk. v3 only buys key rotation, and its extra block
                // crosses a 4096-byte padding boundary, so it costs 4KB until needed.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = false
            }
        }
    }

    // Only for VERSION_NAME, which the about screen shows; R8 inlines it.
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            // Lets a dev build sit alongside the released one, whose signature differs.
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                file("proguard-rules.pro"),
            )
        }
    }

    // AGP otherwise embeds an opaque, Google-signed dependency blob in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
