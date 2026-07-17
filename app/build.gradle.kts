plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.kapt)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt)
  alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.danallacalendar"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.danalla.calendar"
        minSdk = 24
        targetSdk = 35
        versionCode = 365
        versionName = "1.7.80"
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore.jks")
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.foundation:foundation:1.6.0")
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Firebase
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.firestore.ktx)
  implementation(libs.firebase.messaging.ktx)
  implementation("com.google.firebase:firebase-config-ktx:21.6.3")

  // Hilt
  implementation(libs.hilt.android)
  kapt(libs.hilt.compiler)

  // Navigation & DataStore
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.androidx.datastore.preferences)

  // Material Icons Extended
  implementation(libs.androidx.compose.material.icons.extended)

  // Kotlin Serialization
  implementation(libs.kotlinx.serialization.json)

  // Room
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  kapt(libs.room.compiler)

  // JSON library
  implementation(libs.gson)

  // WorkManager + Hilt-Work (for backup scheduler)
  implementation("androidx.work:work-runtime-ktx:2.9.0")
  implementation("androidx.hilt:hilt-work:1.2.0")
  kapt("androidx.hilt:hilt-compiler:1.2.0")
  
  // PrintHelper support for printing images
  implementation("androidx.print:print:1.0.0")

  // Google Sign-In & Drive API
  implementation("com.google.android.gms:play-services-auth:21.1.1")
  // Google Play In-App Update
  implementation("com.google.android.play:app-update:2.1.0")
  implementation("com.google.android.play:app-update-ktx:2.1.0")
  implementation("com.google.api-client:google-api-client-android:2.2.0") {
      exclude(group = "org.apache.httpcomponents")
  }
  implementation("com.google.apis:google-api-services-drive:v3-rev20230822-2.0.0") {
      exclude(group = "org.apache.httpcomponents")
  }
}

tasks.withType<org.jetbrains.kotlin.gradle.internal.KaptWithoutKotlincTask>().configureEach {
    val isWindows = System.getProperty("os.name").contains("Windows", ignoreCase = true)
    if (isWindows) {
        kaptProcessJvmArgs.add("-Djava.io.tmpdir=C:\\Users\\me\\AppData\\Local\\Temp")
        kaptProcessJvmArgs.add("-Dorg.sqlite.tmpdir=C:\\Users\\me\\AppData\\Local\\Temp")
    }
}

