plugins {
    id("com.android.application")
    // Built-in Kotlin (AGP 9+) — do not apply org.jetbrains.kotlin.android
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Firebase is OPTIONAL (opt-in cloud backup). The google-services plugin is applied only when
// app/google-services.json exists, so the project builds and runs without any Firebase setup.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.example.fitwell"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.fitwell"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // Lifecycle / ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Room
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // DataStore, splash screen, image loading
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // CameraX (real camera pulse measurement)
    val cameraxVersion = "1.4.2"

    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Health Connect (read-only: steps, heart rate, sleep, active calories from watches and other health apps)
    implementation("androidx.health.connect:connect-client:1.1.0-alpha10")

    // ML Kit on-device image labeling (meal photo analysis; model is bundled, works offline)
    implementation("com.google.mlkit:image-labeling:17.0.9")

    // ML Kit on-device pose detection (form coach; base model is bundled, works offline)
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")

    // Firebase (opt-in cloud backup only)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("androidx.core:core-ktx:1.13.1")
    // Unit tests
    testImplementation("junit:junit:4.13.2")
}
