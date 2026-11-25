import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Dev API address per machine. Set `nova.api.base.url` in local.properties (not committed):
//   physical phone:  nova.api.base.url=http://<your Mac's Wi-Fi IP>:3000
//   emulator:        nova.api.base.url=http://10.0.2.2:3000
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val apiBaseUrl: String = localProps.getProperty("nova.api.base.url", "http://10.0.2.2:3000")

android {
    namespace = "com.nova.rider"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.nova.kiwiriders"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("release-key.jks")
            storePassword = localProps.getProperty("keystore.password")
            keyAlias = localProps.getProperty("key.alias")
            keyPassword = localProps.getProperty("key.password")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:designsystem"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.libphonenumber)
    implementation(libs.google.play.services.auth.api.phone)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
}

kotlin {
    compilerOptions {
        // MaterialShapes, RoundedPolygon.toShape() and LoadingIndicator are still marked
        // @ExperimentalMaterial3ExpressiveApi in material3 1.5.0-rc01.
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        // ModalBottomSheet drag handle, sheet state etc.
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}
