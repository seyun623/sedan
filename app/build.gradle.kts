plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.hilt)
  alias(libs.plugins.room)
}

android {
  namespace = "com.moneyflow"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "io.github.seyun623.moneyflow"
    minSdk = 24
    targetSdk = 36
    // Overridable from CI: ./gradlew bundleRelease -PversionCode=12 -PversionName=1.2.0
    versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
    versionName = (project.findProperty("versionName") as String?) ?: "2.2.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Release signing is configured only when the keystore env vars are provided
  // (KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD; optional KEY_ALIAS).
  // Debug builds use the default Android debug keystore.
  val releaseKeystore = System.getenv("KEYSTORE_PATH")?.let { file(it) }
  signingConfigs {
    if (releaseKeystore != null && releaseKeystore.exists()) {
      create("release") {
        storeFile = releaseKeystore
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      // Family phones are ARM; leaving out x86 emulator libraries keeps the APK small.
      ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfigs.findByName("release")?.let { signingConfig = it }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    isCoreLibraryDesugaringEnabled = true
  }
  // basic: installs normally on any phone (no SMS / notification-access permissions).
  // full : adds payment auto-capture; blocked by Play Protect's enhanced fraud protection when
  //        sideloaded from a browser/messenger, see docs/family-install-guide.md.
  // Both use the same application id and signing key, so a phone can switch and keep its data.
  flavorDimensions += "capture"
  productFlavors {
    create("basic") {
      dimension = "capture"
      isDefault = true
      buildConfigField("boolean", "AUTO_CAPTURE_AVAILABLE", "false")
    }
    create("full") {
      dimension = "capture"
      buildConfigField("boolean", "AUTO_CAPTURE_AVAILABLE", "true")
    }
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  // Room schemas as debug assets so MigrationTestHelper can read them in Robolectric tests
  // (debug builds only; release APKs do not contain them).
  sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}


room { schemaDirectory("$projectDir/schemas") }


dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  coreLibraryDesugaring(libs.desugar.jdk.libs)
  implementation(libs.hilt.android)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.androidx.hilt.work)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.sqlcipher.android)
  implementation(libs.androidx.sqlite.ktx)
  "ksp"(libs.hilt.compiler)
  "ksp"(libs.androidx.hilt.compiler)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.turbine)
  testImplementation(libs.androidx.room.testing)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  androidTestImplementation(libs.androidx.room.testing)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
