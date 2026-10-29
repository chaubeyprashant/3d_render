plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val firebaseApiKey = providers.gradleProperty("FIREBASE_API_KEY").orElse("")
val firebaseAppId = providers.gradleProperty("FIREBASE_APP_ID").orElse("")
val firebaseProjectId = providers.gradleProperty("FIREBASE_PROJECT_ID").orElse("")
val firebaseGcmSenderId = providers.gradleProperty("FIREBASE_GCM_SENDER_ID").orElse("")
val oneDriveClientId = providers.gradleProperty("ONEDRIVE_CLIENT_ID").orElse("")
val oneDriveTenantId = providers.gradleProperty("ONEDRIVE_TENANT_ID").orElse("common")
val releaseStoreFile = providers.gradleProperty("RELEASE_STORE_FILE")
    .orElse(providers.environmentVariable("RELEASE_STORE_FILE"))
    .orElse("")
val releaseStorePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD")
    .orElse(providers.environmentVariable("RELEASE_STORE_PASSWORD"))
    .orElse("")
val releaseKeyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS")
    .orElse(providers.environmentVariable("RELEASE_KEY_ALIAS"))
    .orElse("")
val releaseKeyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD")
    .orElse(providers.environmentVariable("RELEASE_KEY_PASSWORD"))
    .orElse("")
val hasReleaseSigning = releaseStoreFile.get().isNotBlank() &&
    releaseStorePassword.get().isNotBlank() &&
    releaseKeyAlias.get().isNotBlank() &&
    releaseKeyPassword.get().isNotBlank()

android {
    namespace = "com.example.a3d_render"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.a3d_render"
        // Android 10 (API 29)+; tuned for ~4 GB RAM devices.
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Phones only; x86/x86_64 are emulator ABIs and add ~12 MB of Filament native libs.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebaseApiKey.get()}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebaseAppId.get()}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebaseProjectId.get()}\"")
        buildConfigField("String", "FIREBASE_GCM_SENDER_ID", "\"${firebaseGcmSenderId.get()}\"")
        buildConfigField("String", "ONEDRIVE_CLIENT_ID", "\"${oneDriveClientId.get()}\"")
        buildConfigField("String", "ONEDRIVE_TENANT_ID", "\"${oneDriveTenantId.get()}\"")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
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
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation(libs.androidx.activity.compose)
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("io.github.sceneview:sceneview:4.3.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))
    implementation("com.google.firebase:firebase-config")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    // OneDrive sign-in page (Microsoft identity platform) opens in a Custom Tab
    implementation("androidx.browser:browser:1.8.0")
    // Google Sign-In + Drive API
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.api-client:google-api-client-android:2.7.2") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20241027-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}