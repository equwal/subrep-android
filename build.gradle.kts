// Plugin versions live in gradle/libs.versions.toml; the app module applies its own.
plugins {
    alias(libs.plugins.android.application) apply false
}
