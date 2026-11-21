import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

/**
 * Environnement Supabase visé par ce build (test ou production).
 *
 * Valeurs par défaut (environnement de test) dans gradle.properties. La CI de production les remplace :
 *   ./gradlew … -Pkiko.env=prod -Pkiko.supabaseUrl=https://<prod>.supabase.co -Pkiko.supabaseKey=sb_publishable_…
 * Seules l'URL et la clé publiable vont dans l'app : la clé service_role ne doit jamais y entrer.
 */
abstract class GenerateBuildEnv : DefaultTask() {
    @get:Input abstract val environment: Property<String>
    @get:Input abstract val supabaseUrl: Property<String>
    @get:Input abstract val supabaseKey: Property<String>
    @get:Input abstract val stagingUrl: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val env = environment.get()
        val url = supabaseUrl.get()
        val key = supabaseKey.get()
        require(env == "staging" || env == "prod") { "kiko.env doit valoir staging ou prod (reçu : $env)." }
        require(url.startsWith("https://")) { "kiko.supabaseUrl doit commencer par https://" }
        require(!key.startsWith("sb_secret_") && !key.contains("service_role")) {
            "kiko.supabaseKey doit être la clé PUBLIABLE : une clé secrète ne doit jamais être livrée dans l'app."
        }
        if (env == "prod") {
            require(url != stagingUrl.get()) { "Build de production pointé sur la base de test : passe -Pkiko.supabaseUrl et -Pkiko.supabaseKey." }
        }
        val file = outputDir.get().file("app/kiko/data/BuildEnv.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |// Fichier généré par la tâche Gradle generateBuildEnv : ne pas modifier.
            |package app.kiko.data
            |
            |internal object BuildEnv {
            |    const val NAME: String = "$env"
            |    const val SUPABASE_URL: String = "$url"
            |    const val SUPABASE_PUBLISHABLE_KEY: String = "$key"
            |}
            |""".trimMargin(),
        )
    }
}

val generateBuildEnv = tasks.register<GenerateBuildEnv>("generateBuildEnv") {
    environment.set(providers.gradleProperty("kiko.env"))
    supabaseUrl.set(providers.gradleProperty("kiko.supabaseUrl"))
    supabaseKey.set(providers.gradleProperty("kiko.supabaseKey"))
    stagingUrl.set(providers.gradleProperty("kiko.stagingSupabaseUrl"))
    outputDir.set(layout.buildDirectory.dir("generated/buildEnv/commonMain/kotlin"))
}

kotlin {
    android {
        namespace = "app.kiko.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        withHostTest {}
        // Les ressources Compose (polices) sont empaquetées comme ressources Android.
        androidResources { enable = true }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    // Version web (WebAssembly) : sert d'aperçu cliquable de l'interface partagée.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("composeApp")
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        // BuildEnv.kt (environnement Supabase visé), généré à partir des propriétés Gradle.
        commonMain.get().kotlin.srcDir(generateBuildEnv)
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(project.dependencies.platform(libs.supabase.bom))
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.realtime)
            implementation(libs.supabase.storage)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            // Fuseaux horaires pour kotlinx-datetime dans le navigateur (heure locale des partenaires).
            implementation(npm("@js-joda/timezone", "2.22.0"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "app.kiko.resources"
}
