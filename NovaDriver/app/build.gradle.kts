import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Same API addresses as the rider app (see Nova/app/build.gradle.kts). In local.properties:
//   nova.api.base.url=https://...   release builds (production)
//   nova.api.dev.url=http://...     debug builds; default is this Mac's Wi-Fi IP, port 3000
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val apiBaseUrl: String = localProps.getProperty("nova.api.base.url", "http://10.0.2.2:3000")
fun lanIpv4(): String? = NetworkInterface.getNetworkInterfaces().toList()
    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
    .sortedBy { if (it.name == "en0") 0 else 1 }
    .flatMap { it.inetAddresses.toList() }
    .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
    ?.hostAddress
val devApiBaseUrl: String = (findProperty("nova.api.dev.url") as String?)
    ?: localProps.getProperty("nova.api.dev.url")
    ?: "http://${lanIpv4() ?: "10.0.2.2"}:3000"

android {
    namespace = "com.nova.driver"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.nova.driver"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$devApiBaseUrl\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
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
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.okhttp)
    implementation(libs.google.play.services.location)
    implementation(libs.libphonenumber)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.compose)

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
