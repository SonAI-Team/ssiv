plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
}

group = "io.github.sonai-team"
version = "1.1.1"

mavenPublishing {
    signAllPublications()

    coordinates("io.github.sonai-team", "ssiv-compose", version.toString())

    pom {
        name.set("Subsampling Scale Image View - Compose")
        description.set("Jetpack Compose integration for Subsampling Scale Image View.")
        inceptionYear.set("2026")
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
    namespace = "com.sonai.ssiv.compose"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":library"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.androidx.core.ktx)
}
