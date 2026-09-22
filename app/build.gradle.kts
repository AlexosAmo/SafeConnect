plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.amogelang.safeconnect.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.amogelang.safeconnect.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.ktx)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.material)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation(libs.play.services.games.v2)

// --- Coroutines, for clean async network calls ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

// --- Encrypted local storage for the session token ---
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

// --- Google Play Services Location SDK (external SDK requirement) ---
    implementation("com.google.android.gms:play-services-location:21.3.0")

// --- Lifecycle / coroutine scope helpers (usually already present) ---
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

// --- Unit testing ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}