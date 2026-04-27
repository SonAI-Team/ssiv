import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.detekt)
    id("tech.apter.junit5.jupiter.robolectric-extension-gradle-plugin") version "0.9.0"
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
}

group = "com.sonai.ssiv"
version = "1.0.1"

android {
    namespace = "com.sonai.ssiv"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("proguard-rules.txt")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.txt"
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
    buildToolsVersion = "37.0.0"
}

configurations {
    create("javadocs")
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.collection.ktx)
    implementation(libs.kotlinx.coroutines.android)

    "javadocs"(libs.androidx.annotation)
    "javadocs"(libs.androidx.exifinterface)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.robolectric)
    testImplementation(libs.robolectric.extension)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.core.ktx)
}

