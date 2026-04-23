import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.detekt)
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
}

group = "com.davemorrissey.labs"
version = "3.10.0"

android {
    namespace = "com.sonai.ssiv"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.txt")
        }
    }
}

configurations {
    create("javadocs")
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)
    
    "javadocs"(libs.androidx.annotation)
    "javadocs"(libs.androidx.exifinterface)
}

// apply(from = rootProject.file("release.gradle"))
