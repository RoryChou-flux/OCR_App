plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.vision"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.vision"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    // 已有的 CameraX 相关依赖
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // Guava
    implementation("com.google.guava:guava:31.0.1-android")

    // 网络请求相关
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0") // OkHttp日志拦截器
    implementation("com.squareup.retrofit2:retrofit:2.9.0") // Retrofit
    implementation("com.squareup.retrofit2:converter-gson:2.9.0") // Retrofit Gson转换器
    implementation("com.google.code.gson:gson:2.10.1") // Gson JSON处理

    // 图片处理
    implementation("com.github.bumptech.glide:glide:4.16.0") // 图片加载
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // UI组件
    implementation("com.google.android.material:material:1.11.0") // Material Design
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0") // 下拉刷新
    implementation("androidx.recyclerview:recyclerview:1.3.2") // RecyclerView

    // AppCompat 和 ConstraintLayout 依赖
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)


    implementation("com.vanniktech:android-image-cropper:4.6.0")
    implementation ("com.github.yalantis:ucrop:2.2.8")



    // 其他已有的依赖
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
