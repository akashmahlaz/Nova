// Top-level build file.
buildscript {
    dependencies {
        // AGP 9 ships built-in Kotlin (KGP 2.2.10). Declaring a newer KGP here upgrades it,
        // as documented in the AGP 9 release notes ("Upgrade to a higher KGP version").
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
