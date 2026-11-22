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
        // La CI de publication passe -Pkiko.versionCode (numéro de run, toujours croissant) et -Pkiko.versionName (tag).
        versionCode = providers.gradleProperty("kiko.versionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("kiko.versionName").getOrElse("0.1.0")
        buildConfigField("String", "FIREBASE_APP_ID", firebaseValue("app_id"))
        buildConfigField("String", "FIREBASE_API_KEY", firebaseValue("api_key"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", firebaseValue("project_id"))
        buildConfigField("String", "FIREBASE_SENDER_ID", firebaseValue("sender_id"))
    }
    // Clé d'envoi Play (upload key) : fournie par la CI depuis les secrets de l'environnement GitHub,
    // jamais versionnée. Google re-signe ensuite l'app avec la clé de Play App Signing.
    val uploadKeystore = providers.environmentVariable("KIKO_UPLOAD_KEYSTORE")
    signingConfigs {
        if (uploadKeystore.isPresent) {
            create("upload") {
                storeFile = file(uploadKeystore.get())
                storePassword = providers.environmentVariable("KIKO_UPLOAD_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("KIKO_UPLOAD_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("KIKO_UPLOAD_KEY_PASSWORD").get()
            }
        }
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
            // R8 : retire le code inutilisé et rend la rétro-ingénierie plus coûteuse.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("upload")?.let { signingConfig = it }
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
    // Appels audio : LiveKit pour le son, Telecom pour que le téléphone sache qu'un appel est en cours.
    implementation(libs.livekit.android)
    implementation(libs.androidx.core.telecom)
    debugImplementation(libs.compose.ui.tooling)
}
