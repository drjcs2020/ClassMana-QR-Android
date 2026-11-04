plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "br.com.classmana.qr"; compileSdk = 35
 val signingFile = System.getenv("CLASSMANA_KEYSTORE_PATH")
 val signingPass = System.getenv("CLASSMANA_KEYSTORE_PASSWORD")
 val signingAlias = System.getenv("CLASSMANA_KEY_ALIAS")
 val signingKeyPass = System.getenv("CLASSMANA_KEY_PASSWORD")
 if (!signingFile.isNullOrBlank() && !signingPass.isNullOrBlank() && !signingAlias.isNullOrBlank() && !signingKeyPass.isNullOrBlank()) {
  signingConfigs {
   create("classmanaPermanent") {
    storeFile = file(signingFile)
    storePassword = signingPass
    keyAlias = signingAlias
    keyPassword = signingKeyPass
   }
  }
  buildTypes { getByName("release") {
   isMinifyEnabled = false
   signingConfig = signingConfigs.getByName("classmanaPermanent")
  } }
 }
 defaultConfig { applicationId = "br.com.classmana.qr"; minSdk = 26; targetSdk = 35; versionCode = 7; versionName = "1.0.7" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.activity:activity-ktx:1.9.3")
 implementation("androidx.camera:camera-core:1.4.1")
 implementation("androidx.camera:camera-camera2:1.4.1")
 implementation("androidx.camera:camera-lifecycle:1.4.1")
 implementation("androidx.camera:camera-view:1.4.1")
 implementation("com.google.mlkit:barcode-scanning:17.3.0")
}
