plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.rsebade.adfreeunzip"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rsebade.adfreeunzip"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Play upload key, supplied only by CI from GitHub Actions secrets (see docs/release.md).
    // Without it, bundleRelease still builds (catching R8 problems) but the bundle is unsigned.
    val uploadKeystore = System.getenv("UNZIP_UPLOAD_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
    val uploadSigning =
        uploadKeystore?.let { keystore ->
            signingConfigs.create("upload") {
                storeFile = keystore
                storePassword = System.getenv("UNZIP_UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UNZIP_UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UNZIP_UPLOAD_KEY_PASSWORD")
            }
        }

    buildTypes {
        release {
            signingConfig = uploadSigning
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        // Version bumps are handled by Dependabot, not lint.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Names the APK "Unzip-debug.apk" / "Unzip-release.apk" instead of "app-debug.apk".
base {
    archivesName.set("Unzip")
}

// The installable copy CI uploads, named for the version: build/dist/Unzip-<versionName>.apk.
// AGP has no public API to rename variant outputs, so this copies the debug APK.
val distApkName = "Unzip-${android.defaultConfig.versionName}.apk"
tasks.register<Copy>("distDebugApk") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.dir("outputs/apk/debug")) {
        include("*.apk")
        rename(".*\\.apk", distApkName)
    }
    into(layout.buildDirectory.dir("dist"))
}

// The bundle uploaded to Google Play, named for the version: build/dist/Unzip-<versionName>.aab.
val distBundleName = "Unzip-${android.defaultConfig.versionName}.aab"
tasks.register<Copy>("distReleaseBundle") {
    dependsOn("bundleRelease")
    from(layout.buildDirectory.dir("outputs/bundle/release")) {
        include("*.aab")
        rename(".*\\.aab", distBundleName)
    }
    into(layout.buildDirectory.dir("dist"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
