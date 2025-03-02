import java.net.URI

plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

android {
    namespace = "io.codeconcept.realtimecutvadlibrary"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf("arm64-v8a","armeabi-v7a","x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
            }
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"  // Android Gradle Plugin の推奨バージョンを指定
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
            res.srcDirs("src/main/res")
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// -----------------------------
// jniLibs のダウンロードタスク
// -----------------------------
val mainDir = file("${projectDir}/src/main/")
val jniLibsZip = file("${projectDir}/RealTimeCutVADCXXLibrary.jniLibs.zip")

tasks.register("downloadJniLibs") {
    doLast {
        if (!jniLibsZip.exists()) {
            val url = URI("https://github.com/helloooideeeeea/RealTimeCutVADCXXLibrary/releases/download/v1.0.2/RealTimeCutVADCXXLibrary.jniLibs.zip").toURL()
            println("Downloading jniLibs from $url")

            url.openStream().use { input ->
                jniLibsZip.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        println("Extracting jniLibs...")
        copy {
            from(zipTree(jniLibsZip))
            into(mainDir)
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn("downloadJniLibs")
}


// -----------------------------
// publishing
// -----------------------------
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components.findByName("release") ?: error("Component 'release' not found."))

                groupId = "com.github.helloooideeeeea"
                artifactId = "realtimecutvadlibrary"
                version = "1.0.2"

                pom {
                    name.set("RealTimeCutVADLibrary")
                    description.set("A real-time voice activity detection library")
                    url.set("https://github.com/helloooideeeeea/RealTimeCutVADLibraryForAndroid")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }

                    developers {
                        developer {
                            id.set("helloooideeeeea")
                            name.set("Yasushi Sakita")
                            email.set("yasushi.sakita@gmail.com")
                        }
                    }

                    scm {
                        connection.set("scm:git:github.com/helloooideeeeea/RealTimeCutVADLibraryForAndroid.git")
                        developerConnection.set("scm:git:ssh://github.com/helloooideeeeea/RealTimeCutVADLibraryForAndroid.git")
                        url.set("https://github.com/helloooideeeeea/RealTimeCutVADLibraryForAndroid")
                    }
                }
            }
        }
    }
}