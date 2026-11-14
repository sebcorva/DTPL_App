import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Lectura segura de variables de entorno desde `local.properties` (sin hardcodear en código)
val envProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}

val supabaseUrl: String = envProperties.getProperty("SUPABASE_URL")
    ?: System.getenv("SUPABASE_URL")
    ?: "https://placeholder.supabase.co/"

val supabasePublishableKey: String = envProperties.getProperty("SUPABASE_PUBLISHABLE_KEY")
    ?: envProperties.getProperty("SUPABASE_ANON_KEY")
    ?: System.getenv("SUPABASE_PUBLISHABLE_KEY")
    ?: ""

// Misma regla que SupabaseClient.isConfigured(): un APK release sin nube no se puede generar
val supabaseConfigurado: Boolean = supabaseUrl.startsWith("https://") &&
    !supabaseUrl.contains("placeholder.supabase.co") &&
    supabasePublishableKey.isNotBlank()

// Firma del APK release (ISSUE-28): la keystore y sus contraseñas viven fuera del repositorio,
// en local.properties (ver local.properties.example). Sin ellas el debug compila igual.
val releaseStoreFile: String? = envProperties.getProperty("RELEASE_STORE_FILE")
    ?.replaceFirst("~", System.getProperty("user.home"))
val releaseStorePassword: String? = envProperties.getProperty("RELEASE_STORE_PASSWORD")
val releaseKeyAlias: String? = envProperties.getProperty("RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? = envProperties.getProperty("RELEASE_KEY_PASSWORD") ?: releaseStorePassword

val firmaConfigurada: Boolean = !releaseStoreFile.isNullOrBlank() &&
    file(releaseStoreFile).exists() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank()

tasks.configureEach {
    if (name == "preReleaseBuild") {
        doFirst {
            if (!supabaseConfigurado) {
                throw GradleException(
                    "Build release bloqueado: faltan SUPABASE_URL o SUPABASE_PUBLISHABLE_KEY en local.properties " +
                        "(o como variables de entorno)."
                )
            }
            if (!firmaConfigurada) {
                throw GradleException(
                    "Build release bloqueado: faltan RELEASE_STORE_FILE (que exista), RELEASE_STORE_PASSWORD o " +
                        "RELEASE_KEY_ALIAS en local.properties."
                )
            }
        }
    }
}

android {
    namespace = "cl.logisticaverde.app"
    compileSdk = 35

    defaultConfig {
        // Identificador público (Play Store / teléfono). El paquete del código (namespace) sigue siendo cl.logisticaverde.app
        applicationId = "dejatuplayalimpia.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // Inyección en tiempo de compilación vía BuildConfig desde .env / local.properties
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabasePublishableKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (firmaConfigurada) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (firmaConfigurada) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Esquemas de Room versionados (ISSUE-17): base para MigrationTestHelper
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core & Compose UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room Database (Offline-First Persistence)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Retrofit 2 + OkHttp 4 (Sync to Google Sheets / Apps Script)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    // CameraX & ML Kit Barcode Scanning (QR & 720p Evidence Photos)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    // Google Play Services Location (km_recorridos GPS)
    implementation(libs.play.services.location)

    // WorkManager & Coroutines
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Testing & Debug
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
