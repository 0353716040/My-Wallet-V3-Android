plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("kotlin-android-extensions")
}

apply(from = "../../quality/ktlint.gradle")

kotlin {
    android("android") {
    }

    sourceSets.all {
        languageSettings.apply {
            useExperimentalAnnotation("kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))

                implementation(Libraries.timber)
            }
        }

        val commonTest by getting {
            dependencies {
            }
        }

        val androidTest by getting {
            dependencies {
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
    }
}

android {
    compileSdkVersion(Versions.compileSdk)
    buildToolsVersion(Versions.buildTools)
    defaultConfig {
        minSdkVersion(Versions.minSdk)
        targetSdkVersion(Versions.targetSdk)
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }
    sourceSets {
        // Change 'main' to 'androidMain' for clarity in multiplatform environment
        getByName("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
            java.srcDirs(file("src/androidMain/kotlin"))
            res.srcDirs(file("src/androidMain/res"))
        }
        getByName("androidTest") {
            java.srcDirs(file("src/androidTest/kotlin"))
            res.srcDirs(file("src/androidTest/res"))
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions.jvmTarget = Versions.kotlinJvmTarget
}