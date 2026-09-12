plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.github.opscalehub.avacore"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.github.opscalehub.avacore"
        minSdk = 24
        targetSdk = 34

        // versionName is managed by release-please (do not edit by hand — bump via
        // conventional commits). versionCode is derived from it so it stays monotonic.
        val releaseVersion = "1.1.0" // x-release-please-version
        versionName = releaseVersion
        versionCode = releaseVersion.split(".").map { it.toIntOrNull() ?: 0 }
            .let { (it.getOrElse(0) { 0 }) * 1_000_000 + (it.getOrElse(1) { 0 }) * 1_000 + (it.getOrElse(2) { 0 }) }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // بهینه‌سازی حجم: فقط معماری‌های پرکاربرد را نگه می‌داریم
        ndk {
            abiFilters.add("arm64-v8a")
            abiFilters.add("armeabi-v7a")
        }
    }

    // Release signing. Locally (no keystore) assembleRelease falls back to debug signing.
    // In CI the keystore arrives via SIGNING_KEY_BASE64 secret; passwords via env vars.
    val signingKeyPath: String? = System.getenv("SIGNING_KEY_PATH")
    signingConfigs {
        if (signingKeyPath != null) {
            create("release") {
                storeFile = file(signingKeyPath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (signingKeyPath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    @Suppress("UnstableApiUsage")
    kotlinOptions {
        jvmTarget = "17"
        // Kotlin 2.0 compiles lambdas to invokedynamic by default, producing a
        // synthetic lambda that only exposes the erased invoke(Object). Sherpa-ONNX's
        // native generateWithCallback looks up the specialized invoke([F)Integer via
        // JNI, so we force class-based lambdas/SAM conversions to keep that method.
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xlambdas=class",
            "-Xsam-conversions=class"
        )
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes.add("/META-INF/AL2.0")
            excludes.add("/META-INF/LGPL2.1")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Local Sherpa-ONNX Engine
    implementation(files("libs/sherpa-onnx.aar"))

    // tar+bzip2 extraction for on-demand voice downloads (ModelDownloader) —
    // the same .tar.bz2 format the k2-fsa/sherpa-onnx release bundles ship as.
    implementation(libs.commons.compress)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
