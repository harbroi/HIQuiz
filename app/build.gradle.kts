import java.text.SimpleDateFormat
import java.util.Date

val date = SimpleDateFormat("yyyyMMdd").format(Date())
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "net.harbroi.quizgenerator"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "net.harbroi.quizgenerator"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "2.1.10"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    applicationVariants.all {
        val variantName = name
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            output.outputFileName = "HIQuiz_v${defaultConfig.versionName}_${date}_$variantName.apk"
        }
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}