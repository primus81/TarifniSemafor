plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
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
    // Compose 1.12 (BOM 2026.09.00) requires compiling against API 37. Only the compile API changes:
    // targetSdk stays 36, so the runtime behaviour of the app on devices is the same.
    compileSdk = 37
    defaultConfig {
        applicationId = "si.semafor.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "2.3.0"
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
            // The bundle contains native code of the AndroidX graphics library that Compose uses
            // (libandroidx.graphics.path.so). If a native library carries symbols, they are put into the bundle so
            // that Google Play can read native crashes. AndroidX ships this particular library already stripped
            // (no symbol table), so nothing can be extracted from it; the task nativeSymbolsToIzdaje below packs the
            // libraries themselves (same build IDs) for the Play Console warning.
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    // Per-app language (Slovenian / English) on every Android version: AppCompatDelegate.setApplicationLocales.
    implementation("androidx.appcompat:appcompat:1.7.1")
    // ViewModels of the screens (viewModelScope) and by viewModels().
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.activity:activity-ktx:1.13.0")
    // collectAsStateWithLifecycle: the screens collect the ViewModel state only while the Activity is started.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    // Jetpack Compose: the whole UI (the home screen widget stays RemoteViews). All Compose versions come from the BOM.
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // ComponentActivity + setContent and BackHandler.
    implementation("androidx.activity:activity-compose:1.13.0")
    // Android Studio @Preview support; the tooling itself is a debug-only dependency.
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM unit tests (the Android stubs throw); test-only, not part of the app.
    testImplementation("org.json:json:20231013")
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

// Every build leaves its result in ONE folder, izdaje/ in the project root, named by version: from Android Studio
// (Build menu and the "Generate Signed App Bundle" wizard) and from the terminal alike. Gradle's own outputs in
// app/build/outputs are its working copies and are overwritten by the next build.
//   TarifniSemafor-<versionName>-vc<versionCode>-debug.apk   debug build for the phone
//   TarifniSemafor-<versionName>-vc<versionCode>.aab         signed bundle for Google Play
//   ...-mapping.txt, ...-native-debug-symbols.zip            to upload with it in Play Console
// A release bundle built without a signing key is named ...-NEPODPISAN.aab, so it can never be uploaded by mistake.
val izdaje = rootProject.layout.projectDirectory.dir("izdaje")
val releaseSigned = hasReleaseSigning || project.hasProperty("android.injected.signing.store.file")
fun buildBaseName() = "TarifniSemafor-${android.defaultConfig.versionName}-vc${android.defaultConfig.versionCode}"

val copyDebugApkToIzdaje = tasks.register<Copy>("copyDebugApkToIzdaje") {
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")) { rename { "${buildBaseName()}-debug.apk" } }
    into(izdaje)
}
val copyReleaseBundleToIzdaje = tasks.register<Copy>("copyReleaseBundleToIzdaje") {
    from(layout.buildDirectory.file("outputs/bundle/release/app-release.aab")) {
        rename { buildBaseName() + (if (releaseSigned) "" else "-NEPODPISAN") + ".aab" }
    }
    from(layout.buildDirectory.file("outputs/mapping/release/mapping.txt")) { rename { "${buildBaseName()}-mapping.txt" } }
    into(izdaje)
}
// Play Console asks for native debug symbols because the bundle contains a native library. The only one,
// libandroidx.graphics.path.so (AndroidX, used by Compose), is shipped already stripped, so AGP writes no symbols
// file. Play accepts the libraries themselves: <abi>/<library>.so in a zip, byte-identical to those in the bundle.
val nativeSymbolsToIzdaje = tasks.register<Zip>("nativeSymbolsToIzdaje") {
    from(layout.buildDirectory.dir("intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib"))
    destinationDirectory.set(izdaje)
    archiveFileName.set(provider { "${buildBaseName()}-native-debug-symbols.zip" })
}
tasks.matching { it.name == "assembleDebug" }.configureEach { finalizedBy(copyDebugApkToIzdaje) }
tasks.matching { it.name == "bundleRelease" }.configureEach { finalizedBy(copyReleaseBundleToIzdaje, nativeSymbolsToIzdaje) }
