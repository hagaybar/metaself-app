import com.android.build.api.variant.HasUnitTestBuilder

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.metaself.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.metaself.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 93

        // The barcode reader ships a native model for every processor Android runs on. Only one of
        // them is a phone: arm64 is every Android handset of the last decade, and the x86 pair
        // exists for emulators this app is never run on. Keeping all four made the file 21 MB, of
        // which 10 MB could not possibly execute on the owner's phone.
        //
        // If this ever has to run on an emulator, add "x86_64" back and the file grows by 5 MB.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        versionName = "0.46.2"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Release signing is configured only when the four MS_* properties are present (put them in
    // ~/.gradle/gradle.properties — NEVER in the repo). Without them the release build still
    // assembles, just unsigned, and debug builds are unaffected.
    signingConfigs {
        val storePath = providers.gradleProperty("MS_STORE_FILE").orNull
        if (storePath != null) {
            create("release") {
                storeFile = file(storePath)
                storePassword = providers.gradleProperty("MS_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("MS_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("MS_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        // MigrationTestHelper loads the exported schemas from the ASSETS the app was built with,
        // not from disk. The schemas live in app/schemas and are committed; this puts them where
        // the helper looks.
        //
        // On the DEBUG variant rather than `test`: unit-test source-set assets are not merged into
        // what Robolectric reads (tried, and it failed on CI with "Cannot find the schema file in
        // the assets folder"), whereas the debug variant's are. Debug rather than main so that the
        // schemas are not shipped inside the release APK the owner installs.
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Conscrypt ships no JNI library for linux-aarch64, and this development box is
                // ARM64. Robolectric 4.11.1 defaults it off on mac/aarch64 but not on Linux, so
                // without this every Robolectric test dies in `new OpenSSLProvider()` with
                // UnsatisfiedLinkError. Nothing under test uses Conscrypt, and the switch is only
                // readable as a system property — `robolectric.properties` cannot set it.
                it.systemProperty("robolectric.conscryptMode", "OFF")
            }
        }
    }

    // The DEBUG variant is the unit-test suite's gate, and this says so out loud. A `check` that
    // also runs testReleaseUnitTest fails every Robolectric test that builds an activity:
    // compose-ui-test-manifest — the artifact that puts ComponentActivity into the merged
    // manifest — is debugImplementation, and moving it to release would ship test scaffolding
    // inside the installed APK. A command that fails whenever it is invoked trains people not to
    // invoke it.
    androidComponents {
        beforeVariants(selector().withBuildType("release")) { variant ->
            (variant as HasUnitTestBuilder).enableUnitTest = false
        }
    }
}

// Room writes the schema here so every later migration has a committed baseline to be tested
// against. The directory is committed; it is the record of what version 1 was.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Reading a barcode. CameraX for the preview, ML Kit's BUNDLED barcode model for the reading —
    // bundled rather than the Play Services variant so that scanning works with no network and on a
    // phone with no Google services at all. It costs a few megabytes of APK and buys never having a
    // scanner that has to download itself before it can be used.
    // Writing into a folder the owner picked, without knowing or caring whether it is on the
    // phone, a card, or Drive. This is what lets automatic backup exist with no Cloud project.
    // Steps, from whichever app is writing them — the phone itself, a fitness band's companion
    // app, or Google Fit.
    // Health Connect rather than the Google Fit APIs, which shut down at the end of 2026 and whose
    // developer sign-ups closed in May 2024.
    //
    // Pinned to 1.1.0-alpha07 because it is the last release that compiles against SDK 34. Stable
    // 1.1.0 requires SDK 36, which needs a newer Gradle plugin than this project runs, and an
    // upgrade of the whole toolchain is not something to smuggle in underneath a step counter.
    //
    // This does not contradict the rule that kept the encrypted key store off an alpha. That rule
    // was about a credential that spends money; the worst this one can do is fail to report steps,
    // which the app already treats as an ordinary Tuesday.
    // Authorising Drive. play-services-auth ONLY, for a token — not Google's Java API client
    // libraries, which are a heavy and awkward dependency tree on Android. The upload itself goes
    // through OkHttp, which this app already has, so this adds one dependency rather than thirty.
    implementation(libs.play.services.auth)

    implementation(libs.androidx.health.connect)

    // Health Connect brings full Guava at RUNTIME but not at compile time, and with it a constraint
    // pinning com.google.guava:listenablefuture to an artifact that is deliberately EMPTY — it
    // exists precisely so that a project which also has full Guava does not get the class twice.
    //
    // The effect was that ListenableFuture existed when the app ran and not when it compiled, and
    // CameraX — which returns one from every call — stopped building. Forcing the standalone 1.0
    // artifact fixed the compile and then duplicated the class at packaging, which is the exact
    // collision the empty artifact was invented to avoid.
    //
    // compileOnly is the answer: the compiler gets the class, and the APK gets nothing it did not
    // already have.
    compileOnly(libs.guava)

    implementation(libs.androidx.documentfile)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.security.crypto)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    // Puts ComponentActivity into the debug merged manifest. The Compose render tests build one
    // directly, so removing this breaks every one of them.
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Unit testing: JUnit 5 for everything pure.
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)

    // Robolectric: JVM-hosted Android so Compose can be rendered in src/test. There is no emulator
    // on this machine or in CI, so an androidTest suite would never execute — which is why no
    // androidTest sourceSet exists in this project.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(composeBom)
    // Compose's own test driver, for the session harness alone.
    //
    // It is here because driving a live composition by hand does not work: pressing a button
    // invalidates the scope of the CHILD composable that holds the state, and nothing short of
    // Compose's own frame clock will then recompose it. Advancing Robolectric's looper recomposes
    // the root content lambda and stops there, which looks like a working session right up until
    // the first real screen, where every piece of state lives a level down.
    //
    // This brings in JUnit 4 rule machinery, which is exactly what the rest of this suite avoids —
    // and the existing exception covers it: JUnit 4 ONLY where a framework demands it, which is
    // Robolectric and Compose. It is testImplementation, so nothing reaches the app.
    testImplementation(libs.compose.ui.test.junit4)
    // Robolectric's runner is JUnit 4; vintage runs it on the JUnit 5 platform beside the rest.
    // The rule: JUnit 4 ONLY where a framework demands it. Everything else is JUnit 5.
    testRuntimeOnly(libs.junit.vintage.engine)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
