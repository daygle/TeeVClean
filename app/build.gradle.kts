import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.teevclean"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    // Versions come from the build environment first (the release workflow derives both from
    // the pushed tag), then from -P Gradle properties, then from the defaults below.
    val buildVersionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
        ?: (project.findProperty("VERSION_NAME") as? String)?.takeIf { it.isNotBlank() }
        ?: "1.0.0"
    val buildVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull()
        ?: (project.findProperty("VERSION_CODE") as? String)?.toIntOrNull()
        ?: 1

    defaultConfig {
        applicationId = "com.teevclean"
        minSdk = 24
        targetSdk = 37
        versionCode = buildVersionCode
        versionName = buildVersionName
    }

    // Release signing: credentials come from environment variables (CI secrets) or, for local
    // builds, from an untracked keystore.properties in the project root. See RELEASING.md.
    // When no keystore is configured the release build stays unsigned, which Google Play will
    // not accept.
    val releaseSigningProps = Properties().apply {
        val propsFile = rootProject.file("keystore.properties")
        if (propsFile.exists()) {
            propsFile.inputStream().use { load(it) }
        }
    }
    fun releaseSigningValue(name: String): String? =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: releaseSigningProps.getProperty(name)?.takeIf { it.isNotBlank() }

    val releaseKeystore = releaseSigningValue("RELEASE_KEYSTORE_FILE")
        ?.let { rootProject.file(it) }
        ?.takeIf { it.exists() }

    if (releaseKeystore != null) {
        signingConfigs.create("release") {
            storeFile = releaseKeystore
            storePassword = releaseSigningValue("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = releaseSigningValue("RELEASE_KEY_ALIAS")
            keyPassword = releaseSigningValue("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            // R8 strips unused code and obfuscates; the resource shrinker drops unused
            // resources. Keep rules live in proguard-rules.pro. The R8 mapping is embedded in
            // the .aab, so Google Play picks up the deobfuscation file automatically.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        disable.add("IconMissingDensityFolder")
    }

    testOptions {
        unitTests {
            // Robolectric renders the real Compose tree for store screenshots, so it needs
            // the merged resources and native graphics (see ScreenshotRenderTest).
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                // Robolectric fetches its Android runtime jar from Maven while the tests run,
                // which the CI runner's network does not allow. The renderer only exists to
                // regenerate listing images locally, so CI skips it instead of failing.
                if (System.getenv("CI") != null) it.exclude("**/ScreenshotRenderTest*")
            }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.11.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.14.12")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.robolectric:robolectric:4.18.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.ext:junit-ktx:1.3.0")
    // Robolectric's createComposeRule() needs the test activity from this manifest.
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
