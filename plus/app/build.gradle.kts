plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "app.locker.plus"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.sam.locker"
        minSdk = 35
        targetSdk = 37
        versionCode = 9
        versionName = "1.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "Sam App Locker"
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin.compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    testBuildType = if (project.hasProperty("deviceTests")) "benchmark" else "debug"
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            // Owner-requested release identity; legacy packages remain separate.
            // Release uses app.sam.locker.
            isMinifyEnabled = false
            isDebuggable = false
        }
        create("benchmark") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".testbed"
            manifestPlaceholders["appLabel"] = "Sam App Locker Test"
            matchingFallbacks += "debug"
        }
    }
}
dependencies {
    implementation("com.lambdapioneer.argon2kt:argon2kt:1.6.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
