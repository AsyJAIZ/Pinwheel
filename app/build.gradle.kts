plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.asyjaiz.pinwheeltester"
    compileSdk = 30

    defaultConfig {
        applicationId = "com.asyjaiz.pinwheeltester"
        minSdk = 29
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
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
}

dependencies {

    //implementation(libs.libxposed.service)
    //compileOnly(libs.libxposed.api)
    compileOnly(fileTree("libs") {include("*.jar")})
    implementation(libs.appcompat.v160)
    implementation(libs.activity)
    implementation(libs.constraintlayout.v214)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

tasks {
    withType<JavaCompile> {
        options.compilerArgs.add("-Xlint:deprecation")
        //options.compilerArgs.add("-Xbootclasspath/p:libs/android10.jar")
    }
}