plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.aeternum"
    compileSdk = 36  // Android 16

    defaultConfig {
        applicationId = "io.aeternum"
        minSdk = 31  // Android 12
        targetSdk = 36  // Android 16
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }

    kotlinOptions {
        // 启用 Compose 强跳过模式 (性能优化)
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        freeCompilerArgs += "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        freeCompilerArgs += "-opt-in=androidx.compose.animation.ExperimentalAnimationApi"
        freeCompilerArgs += "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        // 稳定性参数 (启用 Compose 稳定性推断)
        freeCompilerArgs += "-opt-in=androidx.compose.runtime.InternalComposeApi"

        // 性能优化：启用强跳过模式（Compose Compiler 1.5.3+）
        // 自动跳过不需要重组的 Composable 函数
        freeCompilerArgs += listOf(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:experimentalStrongSkipping=true"
        )
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir("src/main/jniLibs")
        }
    }
}

dependencies {
    // UniFFI JNA 依赖 - UniFFI 生成的 Kotlin 代码需要 JNA 调用原生方法
    // 注意：JNA 5.12.0+ 提供 Android AAR 支持
    implementation("net.java.dev.jna:jna:5.14.0@aar")

    // Jetpack Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Security & Crypto
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // SQLCipher
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite:2.4.0")

    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Dependency Injection
    implementation("io.insert-koin:koin-android:3.5.3")
    implementation("io.insert-koin:koin-androidx-compose:3.5.3")

    // Accompanist (UI 工具库)
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.36.0")
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // Animation & Graphics (使用 BOM 版本管理)
    implementation(composeBom)
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-graphics")

    // Window Insets (边缘到边缘支持)
    implementation("com.google.accompanist:accompanist-insets-ui:0.36.0")

    // Material Icons Extended
    implementation("androidx.compose.material:material-icons-extended")

    // DataStore (数据持久化)
    implementation("androidx.datastore:datastore-preferences:1.1.2")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.21")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.navigation:navigation-testing:2.7.6")
}
