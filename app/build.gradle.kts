plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "om.swiitch.smartparking"
    compileSdk = 36

    defaultConfig {
        applicationId = "om.switch.smartparking"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "4.0.0"

        val databaseUrl = providers.gradleProperty("FIREBASE_DATABASE_URL").orNull ?: ""
        buildConfigField("String", "FIREBASE_DATABASE_URL", "\"$databaseUrl\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.foundation:foundation:1.11.4")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-database")
}
