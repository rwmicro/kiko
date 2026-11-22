plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

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
    debugImplementation(libs.compose.ui.tooling)
}
