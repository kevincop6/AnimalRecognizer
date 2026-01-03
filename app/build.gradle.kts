plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.ulpro.animalrecognizer"
    compileSdk = 35
    buildFeatures {
        viewBinding = true
    }
    packaging {
        resources {
            excludes += setOf("META-INF/INDEX.LIST", "META-INF/DEPENDENCIES")
        }
    }
    defaultConfig {
        applicationId = "com.ulpro.animalrecognizer"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 1. Activa la ofuscación y reducción de código
            isMinifyEnabled = true

            // 2. Activa la eliminación de recursos (imágenes/xml) no usados
            isShrinkResources = true

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
    kotlinOptions {
        jvmTarget = "11"
    }
    androidResources {
        noCompress += listOf("tflite", "lite")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(platform("com.google.firebase:firebase-bom:33.10.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.android.volley:volley:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.github.bumptech.glide:glide:4.14.2")
    implementation(libs.androidx.preference)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    annotationProcessor("com.github.bumptech.glide:compiler:4.14.2")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.airbnb.android:lottie:5.0.3")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.github.f0ris.sweetalert:library:1.6.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // TensorFlow Lite (versión estable más reciente)
    implementation("org.tensorflow:tensorflow-lite:2.16.1") // Core

    // Soporte para imágenes, texto, etc. (opcional)
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // Aceleración con GPU (opcional)
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")

    // Si usas metadatos en el modelo (ej: modelos descargados de TF Hub)
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")

    // Se usa para encriptar datos
    implementation("androidx.security:security-crypto:1.1.0")

}