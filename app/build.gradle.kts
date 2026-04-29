plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.shower.voicectrl"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.shower.voicectrl"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 只为真实手机的 ABI 打包，省掉 x86 / x86_64（只用于模拟器）。
        // 开发阶段跑模拟器的话，可以临时加回来。
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Release 签名从 ~/.gradle/gradle.properties 读取（不进仓库）。
    // 未配置时跳过 signingConfig，只能构建 debug。
    // 所有值都 trim()，容忍文件末尾的空格 / 换行。
    signingConfigs {
        fun prop(name: String): String? = (project.findProperty(name) as String?)?.trim()
        val storePath = prop("GEKONG_STORE_FILE")
        if (!storePath.isNullOrEmpty()) {
            create("release") {
                storeFile = file(storePath)
                storePassword = prop("GEKONG_STORE_PASSWORD") ?: ""
                keyAlias = prop("GEKONG_KEY_ALIAS") ?: ""
                keyPassword = prop("GEKONG_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // Vosk 模型中的二进制文件不能被压缩，否则运行时读取会出错
    androidResources {
        noCompress += listOf("mdl", "fst", "int", "bin")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.vosk.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
