plugins {
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    // Compose compiler plugin: must always have the same version as the Kotlin plugin above.
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
