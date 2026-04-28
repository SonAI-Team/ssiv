plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.sonai.ssiv.test"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sonai.ssiv.test"
        minSdk = 21
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    sourceSets {
        getByName("main") {
            assets.directories.add("assets")
        }
    }
    buildToolsVersion = "37.0.0"
    compileOptions {
        targetCompatibility = JavaVersion.VERSION_21
        sourceCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":library"))
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.material)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
}
