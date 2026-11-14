plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.nova.designsystem"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.ui.text)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.graphics.shapes)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
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
