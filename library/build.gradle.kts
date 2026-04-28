import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.detekt)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
    id("tech.apter.junit5.jupiter.robolectric-extension-gradle-plugin") version "0.9.0"
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
}

group = "io.github.sonai-team"
version = "1.0.2"

mavenPublishing {
    signAllPublications()

    coordinates("io.github.sonai-team", "ssiv", version.toString())

    pom {
        name.set("Subsampling Scale Image View")
        description.set("A custom image view for Android, designed for displaying large images without OutOfMemoryErrors.")
        inceptionYear.set("2025")
        url.set("https://github.com/SonAI-TEAM/ssiv")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("sonai-team")
                name.set("SonAI Team")
                url.set("https://github.com/SonAI-Team")
            }
        }
        scm {
            url.set("https://github.com/SonAI-Team/ssiv")
            connection.set("scm:git:git://github.com/SonAI-Team/ssiv.git")
            developerConnection.set("scm:git:ssh://git@github.com/SonAI-Team/ssiv.git")
        }
    }
}

android {
    namespace = "com.sonai.ssiv"
    compileSdk = 37

    defaultConfig {
        minSdk = 21
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
