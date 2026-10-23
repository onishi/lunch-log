plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

val functionsBaseUrl: String = providers.gradleProperty("lunchlog.functionsBaseUrl").getOrElse("")
val googleWebClientId: String = providers.gradleProperty("lunchlog.googleWebClientId").getOrElse("")

android {
    namespace = "app.lunchlog"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.lunchlog"
        minSdk = 29 // SPEC §4: Android 10 以上
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 環境ごとの値はコミットせず、local.properties か -P で渡す。
        // 未設定でもビルドは通り、その機能だけが無効になる (アプリは止まらない)。
        buildConfigField("String", "FUNCTIONS_BASE_URL", "\"$functionsBaseUrl\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // java.time を minSdk 29 で使う
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // src/main/kotlin を使う (KGP の既定に頼らず明示する)
    sourceSets["main"].kotlin.directories.add("src/main/kotlin")
    sourceSets["test"].kotlin.directories.add("src/test/kotlin")
    sourceSets["androidTest"].kotlin.directories.add("src/androidTest/kotlin")

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    // Room のスキーマを書き出す (exportSchema = true に必要)
    arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.path)
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose) // collectAsStateWithLifecycle
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    // CameraX の ListenableFuture が実体を持つよう、コンパイル時にも本物の guava を解決させる
    // (未指定だと guava の空シム jar に解決され、ProcessCameraProvider.getInstance() が使えない)
    implementation(libs.guava)

    implementation(libs.coil.compose)
    implementation(libs.play.services.location)
    implementation(libs.androidx.exifinterface)
    implementation(libs.mlkit.text.recognition.japanese) // メニュー写真の OCR (SPEC §6.2)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services) // Task.await()

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
