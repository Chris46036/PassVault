import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.passvault.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.passvault.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "1.4.1"
    }

    // Builds reproducibles: sin el bloque de dependencias cifrado de Google
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.autofill:autofill:1.1.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    // KDF resistente a GPU para la clave maestra (MIT, compatible con F-Droid)
    implementation("com.lambdapioneer.argon2kt:argon2kt:1.5.0")
    // Escaneo de códigos QR para secretos TOTP
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // Proveedor de passkeys (Credential Manager)
    implementation("androidx.credentials:credentials:1.3.0")
    // Lectura de bases de datos KeePass (.kdbx)
    implementation("app.keemobile:kotpass:0.10.0")

    testImplementation("junit:junit:4.13.2")
}
