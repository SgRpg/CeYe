plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose"); id("com.google.devtools.ksp") }
android {
    namespace = "io.github.sgrpg.ceye"
    compileSdk = 36
    defaultConfig { applicationId = "io.github.sgrpg.ceye"; minSdk = 30; targetSdk = 36; versionCode = 3; versionName = "0.1.2"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    signingConfigs { create("personal") {
        storeFile = file(System.getenv("CEYE_KEYSTORE") ?: "${System.getProperty("user.home")}/.ceye/release.jks")
        storePassword = System.getenv("CEYE_STORE_PASSWORD")
        keyAlias = "ceye"
        keyPassword = System.getenv("CEYE_KEY_PASSWORD")
    } }
    buildTypes { release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("personal") } }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
