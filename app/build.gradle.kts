plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    kotlin("plugin.serialization")
}

android {
    namespace = "com.example.finalyear"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.finalyear"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.protolite.well.known.types)
    implementation(kotlin("reflect"))
    implementation("com.github.doyaaaaaken:kotlin-csv:1.9.0")
    implementation("com.google.guava:guava:31.1-android")
    implementation("joda-time:joda-time:2.10.14")
    implementation("org.ejml:ejml-all:0.41")
    implementation("org.orekit:orekit:12.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation(files("libs/commons-codec-1.10.jar"))
    implementation(files("libs/commons-math3-3.6.1.jar"))
    implementation(files("libs/asn1-supl2.jar"))
    implementation(files("libs/asn1-base.jar"))
    implementation(files("libs/suplClient.jar"))
    implementation(files("libs/protobuf-nano.jar"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
