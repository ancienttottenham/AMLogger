import com.vanniktech.maven.publish.AndroidSingleVariantLibrary

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("com.vanniktech.maven.publish") version "0.34.0"
    id("signing")
}

val ciVersion = providers.gradleProperty("version").orNull
val publishedVersion = ciVersion ?: "1.0.0"       // local default; CI passes -Pversion=…

signing {
    useGpgCmd()
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(
        groupId = "io.github.ancienttottenham",
        artifactId = "amlogger",
        version = publishedVersion
    )

    // Ensure Android release variant is published, with sources and an empty javadoc jar
    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = true
        )
    )

    pom {
        name.set("AMLogger")
        description.set("Colorful and human-readable logger")
        url.set("https://github.com/ancienttottenham/AMLogger")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("ancienttottenham")
                name.set("ancienttottenham")
                url.set("https://github.com/ancienttottenham")
            }
        }
        scm {
            url.set("https://github.com/ancienttottenham/AMLogger")
            connection.set("scm:git:https://github.com/ancienttottenham/AMLogger.git")
            developerConnection.set("scm:git:ssh://git@github.com/ancienttottenham/AMLogger.git")
        }
    }
}

android {
    namespace = "com.ancienttottenham.amlogger"
    compileSdk = 36

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("io.github.ancienttottenham:amlogger-core:1.0.4")
}