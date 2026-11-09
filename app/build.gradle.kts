plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
// Optional release signing. Credentials are read from Gradle properties (e.g. ~/.gradle/gradle.properties)
// or environment variables and are NEVER stored in the project:
//   TS_STORE_FILE, TS_STORE_PASSWORD, TS_KEY_ALIAS, TS_KEY_PASSWORD
// If any of them is missing, the release build stays unsigned (Android Studio's
// "Generate Signed App Bundle" wizard can still sign it).
fun signingValue(name: String): String? =
    (findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: System.getenv(name)
        ?.takeIf { it.isNotBlank() }

val tsStoreFile = signingValue("TS_STORE_FILE")
val tsStorePassword = signingValue("TS_STORE_PASSWORD")
val tsKeyAlias = signingValue("TS_KEY_ALIAS")
val tsKeyPassword = signingValue("TS_KEY_PASSWORD")
val hasReleaseSigning =
    tsStoreFile != null && tsStorePassword != null && tsKeyAlias != null && tsKeyPassword != null

android {
    namespace = "si.semafor.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "si.semafor.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "2.1.3"
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(tsStoreFile!!)
                storePassword = tsStorePassword
                keyAlias = tsKeyAlias
                keyPassword = tsKeyPassword
            }
        }
    }
    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM unit tests (the Android stubs throw); test-only, not part of the app.
    testImplementation("org.json:json:20231013")
}
