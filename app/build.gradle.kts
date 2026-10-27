import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    val propFile = rootProject.file("local.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}

fun resolveBuildSecret(key: String, fallbackKey: String = "", default: String = ""): String {
    val raw = localProps.getProperty(key)
        ?: (if (fallbackKey.isNotEmpty()) localProps.getProperty(fallbackKey) else null)
        ?: System.getenv(key)
        ?: (if (fallbackKey.isNotEmpty()) System.getenv(fallbackKey) else null)
        ?: default
    return raw.trim().replace("\\", "\\\\").replace("\"", "\\\"")
}

android {
    namespace = "org.umn.application.studenthub"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.umn.application.studenthub"
        minSdk = 27
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "AI_API_KEY", "\"${resolveBuildSecret("AI_API_KEY", "GEMINI_API_KEY", "")}\"")
        buildConfigField("String", "AI_BASE_URL", "\"${resolveBuildSecret("AI_BASE_URL", "", "https://generativelanguage.googleapis.com/v1beta")}\"")
        buildConfigField("String", "AI_MODEL", "\"${resolveBuildSecret("AI_MODEL", "", "gemini-2.5-flash")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "1536m"
    maxParallelForks = 1
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}