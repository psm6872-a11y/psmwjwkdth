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
        applicationId = "com.example.danallacalendar"
        minSdk = 24
        targetSdk = 34
        versionCode = 66
        versionName = "1.0.66"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("keystore.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("STORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val keystoreFile = file("keystore.jks")
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
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

  // HTTP & JSON for Updater
  implementation(libs.okhttp)
  implementation(libs.gson)
}

tasks.withType<org.jetbrains.kotlin.gradle.internal.KaptWithoutKotlincTask>().configureEach {
    val isWindows = System.getProperty("os.name").contains("Windows", ignoreCase = true)
    if (isWindows) {
        kaptProcessJvmArgs.add("-Djava.io.tmpdir=C:\\Users\\me\\AppData\\Local\\Temp")
        kaptProcessJvmArgs.add("-Dorg.sqlite.tmpdir=C:\\Users\\me\\AppData\\Local\\Temp")
    }
}

