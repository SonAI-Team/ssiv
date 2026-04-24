plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.sonai.ssiv.test"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sonai.ssiv.test"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.txt")
        }
    }

    sourceSets {
        getByName("main") {
            assets.directories.add("assets")
        }
    }
}

dependencies {
    implementation(project(":library"))
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.appcompat)
}
