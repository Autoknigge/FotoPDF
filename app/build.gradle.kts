plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// AdMob-IDs: ohne Angabe werden die Google-Test-IDs verwendet (sicher für Tests).
// Echte IDs per -PADMOB_APP_ID=... -PADMOB_BANNER_ID=... oder als GitHub-Secrets (siehe README).
val admobAppId: String =
    (project.findProperty("ADMOB_APP_ID") as String?)?.takeIf { it.isNotBlank() }
        ?: "ca-app-pub-3940256099942544~3347511713"
val admobBannerId: String =
    (project.findProperty("ADMOB_BANNER_ID") as String?)?.takeIf { it.isNotBlank() }
        ?: "ca-app-pub-3940256099942544/9214589741"

val ksFile = project.findProperty("KEYSTORE_FILE") as String?
val ksPassword = project.findProperty("KEYSTORE_PASSWORD") as String?
val ksAlias = project.findProperty("KEY_ALIAS") as String?
val ksKeyPassword = project.findProperty("KEY_PASSWORD") as String?
val hasSigning = !ksFile.isNullOrBlank() && !ksPassword.isNullOrBlank() &&
    !ksAlias.isNullOrBlank() && !ksKeyPassword.isNullOrBlank()

android {
    namespace = "de.autoknigge.fotopdf"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.autoknigge.fotopdf"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(ksFile!!)
                storePassword = ksPassword
                keyAlias = ksAlias
                keyPassword = ksKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
}
