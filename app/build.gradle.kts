plugins {
    id("com.android.application")
}

android {
    namespace = "com.okeybot"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.okeybot"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":brain"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.opencv:opencv:4.10.0")
    testImplementation(kotlin("test"))
}
