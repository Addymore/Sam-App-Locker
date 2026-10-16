plugins { id("com.android.application") }
android {
    namespace = "app.locker.fixture"
    compileSdk = 37
    defaultConfig { applicationId = "app.locker.fixture"; minSdk = 26; targetSdk = 37; versionCode = 1; versionName = "1" }
    flavorDimensions += "identity"
    productFlavors {
        create("red") { dimension = "identity"; applicationIdSuffix = ".red" }
        create("blue") { dimension = "identity"; applicationIdSuffix = ".blue" }
        create("public") { dimension = "identity"; applicationIdSuffix = ".public" }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
