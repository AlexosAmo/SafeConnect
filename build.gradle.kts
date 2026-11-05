plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.amogelang.safeconnect.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Other configuration...
        minSdk = 26

        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${providers.gradleProperty("safeConnectApiBaseUrl")
                .orElse("https://safeconnect-api-youruniqueid-a3bnfsbzbhfxfnhw.italynorth-01.azurewebsites.net/")
                .get()}\""
        )
    }
    buildFeatures { buildConfig = true }

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

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.material)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation(libs.play.services.games.v2)
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Phase 2: provider sign-in and device-local biometric unlock.
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    implementation("androidx.biometric:biometric:1.1.0")

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
    implementation(kotlin("test"))
}