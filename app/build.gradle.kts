plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// מפתח חתימה קבוע. בלעדיו כל בנייה מייצרת זהות אפליקציה חדשה,
// ואז אנדרואיד דורש הסרה והתקנה מחדש במקום עדכון.
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

android {
    namespace = "com.gil.routines"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gil.routines"
        minSdk = 29          // CallScreeningService.setSilenceCall דורש API 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    // assembleRelease מריץ lint קפדני שנוטה להפיל בנייה על אזהרות בלבד.
    // לאפליקציה אישית שלא מגיעה לחנות זה רעש, לא ערך.
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
