plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.chaquo.python")
}

val configuredBuildPython = providers.gradleProperty("nanzhufeng.buildPython")
    .orElse(providers.environmentVariable("NANZHUFENG_BUILD_PYTHON"))
    .orNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)

fun releaseEnvironment(name: String): String? = providers.environmentVariable(name)
    .orNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)

val releaseStoreFilePath = releaseEnvironment("NANFENG_RELEASE_STORE_FILE")
val releaseStorePassword = releaseEnvironment("NANFENG_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseEnvironment("NANFENG_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseEnvironment("NANFENG_RELEASE_KEY_PASSWORD")
val releaseSigningValues = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
val releaseSigningConfigured = releaseSigningValues.all { it != null }

check(releaseSigningValues.none { it != null } || releaseSigningConfigured) {
    "Release signing is partially configured. Provide all NANFENG_RELEASE_* environment variables."
}

android {
    namespace = "com.nanzhufeng.videodownloader"
    compileSdk = 35
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.nanzhufeng.videodownloader"
        minSdk = 29
        targetSdk = 35
        versionCode = 10100
        versionName = "1.1.0"
        testApplicationId = "com.nanzhufeng.videodownloader.codextest"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = file(requireNotNull(releaseStoreFilePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

@Suppress("DEPRECATION")
android.applicationVariants.all {
    outputs.all {
        if (buildType.name == "release") {
            (this as com.android.build.gradle.api.ApkVariantOutput).outputFileName =
                "南枫下载-Android-v1.1.0.apk"
        } else {
            (this as com.android.build.gradle.api.ApkVariantOutput).outputFileName = "南枫下载.apk"
        }
    }
}

val verifyReleaseSigningConfig by tasks.registering {
    doLast {
        check(releaseSigningConfigured) {
            "Release packaging requires NANFENG_RELEASE_STORE_FILE, " +
                "NANFENG_RELEASE_STORE_PASSWORD, NANFENG_RELEASE_KEY_ALIAS and " +
                "NANFENG_RELEASE_KEY_PASSWORD."
        }
        check(file(requireNotNull(releaseStoreFilePath)).isFile) {
            "Release keystore does not exist at NANFENG_RELEASE_STORE_FILE."
        }
    }
}

tasks.matching { it.name == "packageRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(verifyReleaseSigningConfig)
}

tasks.register<Copy>("stageFormalReleaseArtifacts") {
    dependsOn("assembleRelease", "bundleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/南枫下载-Android-v1.1.0.apk"))
    from(layout.buildDirectory.file("outputs/bundle/release/app-release.aab")) {
        rename { "南枫下载-Android-v1.1.0.aab" }
    }
    into(layout.buildDirectory.dir("outputs/formal-release"))
}

chaquopy {
    defaultConfig {
        version = "3.13"
        configuredBuildPython?.let { buildPython(it) }
        pip {
            install("yt-dlp==2026.6.9")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.media3:media3-common:1.8.1")
    implementation("androidx.media3:media3-transformer:1.8.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
