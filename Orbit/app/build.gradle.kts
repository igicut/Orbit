import java.io.File
import java.util.Properties

/** Podesavanja po racunaru; local.properties nije u git-u */
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

/**
 * Adresa servera. Emulator vidi racunar kao 10.0.2.2; telefon preko USB-a koristi
 * 127.0.0.1 uz `adb reverse`, koji zadatak `adbReverse` postavlja sam.
 * Menja se u local.properties kao `orbit.baseUrl`, da build fajl ostane isti na svakom uredjaju.
 */
val serverBaseUrl: String = localProperties.getProperty("orbit.baseUrl") ?: "http://10.0.2.2:8080/"

/** Mapbox javni token iz local.properties (`mapbox.accessToken`); prazan token daje praznu mapu, ne gresku pri build-u */
val mapboxAccessToken: String = localProperties.getProperty("mapbox.accessToken").orEmpty()

/** Putanja do SDK-a, istim redom kao Android plugin: local.properties pa okruzenje */
val androidSdkDir: String? = localProperties.getProperty("sdk.dir")
    ?: System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.symbol.processing)
    alias(libs.plugins.hilt.android)
}


android {
    namespace = "com.example.orbit"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.igicut.orbit"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BASE_URL", "\"$serverBaseUrl\"")
        resValue("string", "mapbox_access_token", mapboxAccessToken)
        if (mapboxAccessToken.isEmpty()) logger.warn("mapbox.accessToken nije podesen u local.properties; mapa nece raditi")
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
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    // F-37: coil-compose nema mrezni fetcher, bez ovoga se http slike ne ucitavaju
    implementation(libs.coil.network.okhttp)

    // kamera
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.mapbox.maps)
    implementation(libs.mapbox.maps.compose)
    implementation(libs.play.services.location)
    implementation(libs.androidx.navigation.compose)

    // mreza
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // room baza
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler) // potreban i plugin

    // hilt di
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler) // potreban i plugin

    // F-25: periodicna provera, hilt-work za Worker sa injekcijom
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)

        // Umesto kotlinOptions (uklonjen u AGP 9); add() cuva argumente plugina
        freeCompilerArgs.add("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
    }
}

/**
 * Telefon preko USB-a: port 8080 na telefonu vodi na server na racunaru.
 * Vezuje se za `adb`, pa se gubi pri svakom iskljucivanju kabla; zato visi
 * na `install*` zadacima umesto da se kuca rucno posle prikljucivanja.
 */
val adbReverse = tasks.register<Exec>("adbReverse") {
    group = "orbit"
    description = "Mapira tcp:8080 sa telefona na server na racunaru"

    val adbName = if (System.getProperty("os.name").startsWith("Windows")) "adb.exe" else "adb"
    val adb = androidSdkDir?.let { File(it, "platform-tools/$adbName") }

    executable = adb?.absolutePath ?: adbName
    args("reverse", "tcp:8080", "tcp:8080")

    // Bez nadjenog adb-a se preskace, da build ne pukne na tudjem racunaru
    onlyIf { adb != null && adb.exists() }
    // Bez prikljucenog telefona ovo nije greska, build ide dalje
    isIgnoreExitValue = true
}

tasks.matching { it.name.startsWith("install") }.configureEach {
    dependsOn(adbReverse)
}
