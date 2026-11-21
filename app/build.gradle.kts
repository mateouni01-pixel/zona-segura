plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.zonasegura"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.zonasegura"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        resValues = true
    }

    buildTypes {
        debug {
            resValue("string", "backend_base_url", providers.gradleProperty("zonaApiUrl").getOrElse("http://127.0.0.1:8080"))
        }
        release {
            resValue("string", "backend_base_url", providers.gradleProperty("zonaApiUrl").getOrElse(""))
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation("androidx.work:work-runtime:2.10.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
