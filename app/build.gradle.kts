plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "eu.etrunon.howlongsince"
    compileSdk = 35
    defaultConfig {
        applicationId = "eu.etrunon.howlongsince"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
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
    ksp("androidx.room:room-compiler:$room")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // real org.json; Android's is a stub in unit tests
}
