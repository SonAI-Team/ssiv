plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

group = "io.github.sonai-team"
version = "1.1.1"

mavenPublishing {
    signAllPublications()

    coordinates("io.github.sonai-team", "ssiv-coil", version.toString())

    pom {
        name.set("Subsampling Scale Image View - Coil")
        description.set("Coil integration for Subsampling Scale Image View.")
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
    namespace = "com.sonai.ssiv.coil"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":library"))
    implementation(libs.coil.core)
    implementation(libs.coil.network)
    implementation(libs.androidx.core.ktx)
}
