plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val liveMarketBaseUrl = providers.gradleProperty("PARABOLA_LIVE_URL")
    .orElse("http://10.0.2.2:8787")
    .get()
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.solanadistributionmarketdemo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.solanadistributionmarketdemo"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "PARABOLA_LIVE_URL", "\"$liveMarketBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.foundation:foundation:1.6.8")
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.0.7")
    implementation("com.solanamobile:web3-solana:0.3.2-beta6")
    implementation("com.solanamobile:rpc-core:0.2.10")
    implementation("com.solanamobile:rpc-solana:0.2.10")
    implementation("com.solanamobile:rpc-ktordriver:0.2.10")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")
}
