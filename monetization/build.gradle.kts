import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("maven-publish")
}

val agpVersion = com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
val isAgp9OrAbove = agpVersion.split(".")[0].toIntOrNull()?.let { it >= 9 } ?: false

if (!isAgp9OrAbove) {
    project.plugins.apply("org.jetbrains.kotlin.android")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.tomo.monetization"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    publishing {
        singleVariant("release") {
            withJavadocJar()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                groupId = "com.github.vodoigame4-droid"
                artifactId = "monetization-sdk"
                version = "1.1.3"

                from(components["release"])
            }
        }
    }
}

dependencies {
    implementation(libs.timber)

    implementation(libs.bundles.androidx.core)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose.core)
    implementation(libs.androidx.constraintlayout)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Ads
    api(libs.play.services.ads)
    api(libs.user.messaging.platform)
    api(libs.shimmer)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.bundles.firebase)

    // App Update
    api(libs.app.update.ktx)
    api(libs.androidx.preference.ktx)

    // Billing
    api(libs.guava)
    api(libs.billing.ktx)

    // Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Mediation Ads
    api("com.google.ads.mediation:applovin:13.6.2.0")
    api("com.google.ads.mediation:inmobi:11.1.0.1")
    api("com.google.ads.mediation:vungle:7.7.0.1")
    api("com.google.ads.mediation:facebook:6.21.0.1")
    api("com.google.ads.mediation:mintegral:17.0.81.0")
    api("com.google.ads.mediation:pangle:7.9.0.9.0")
    api("com.unity3d.ads:unity-ads:4.18.0")
    api("com.google.ads.mediation:unity:4.18.0.0")
    api("com.google.code.gson:gson:2.13.2")
    api("com.facebook.android:facebook-android-sdk:17.0.0")
    api("com.adjust.sdk:adjust-android:5.5.0")
    api("com.android.installreferrer:installreferrer:2.2")
    api("com.google.android.gms:play-services-ads-identifier:18.3.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
