import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Play upload key: path and password live in the gitignored local.properties (upload.store, upload.password)
val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { load(it) } }

android {
    namespace = "eu.etrunon.howlongsince"
    compileSdk = 36
    defaultConfig {
        applicationId = "eu.etrunon.howlongsince"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }
    signingConfigs {
        create("release") {
            storeFile = local.getProperty("upload.store")?.let(::file)
            storePassword = local.getProperty("upload.password")
            keyAlias = "upload"
            keyPassword = local.getProperty("upload.password")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("release")
        }
    }
    androidResources { localeFilters += listOf("en", "it") } // drop library translations we do not ship
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val room = "2.6.1"
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.zxing:core:3.5.3") // QR code of the share link
    ksp("androidx.room:room-compiler:$room")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // real org.json; Android's is a stub in unit tests
}
