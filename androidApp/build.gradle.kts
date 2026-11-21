import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Identifiants Firebase de l'app Android (publics par nature, comme google-services.json).
// À remplir dans androidApp/firebase.properties ; vides, l'app tourne sans notifications.
val firebase = Properties().apply {
    val file = project.file("firebase.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun firebaseValue(key: String): String = "\"" + (firebase.getProperty(key) ?: System.getenv("FIREBASE_" + key.uppercase()) ?: "") + "\""

android {
    namespace = "app.kiko.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.kiko"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "FIREBASE_APP_ID", firebaseValue("app_id"))
        buildConfigField("String", "FIREBASE_API_KEY", firebaseValue("api_key"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", firebaseValue("project_id"))
        buildConfigField("String", "FIREBASE_SENDER_ID", firebaseValue("sender_id"))
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.koin.android)
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines.android)
    // Notifications : FCM ; widgets de l'écran d'accueil : Glance.
    implementation(project.dependencies.platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.glance.appwidget)
    debugImplementation(libs.compose.ui.tooling)
}
