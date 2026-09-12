plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Android に依存しない純 Kotlin モジュール。
// ドメインモデルと、端末なしで検証できるロジックだけを置く。
// Android SDK がない環境 (CI の一部、コンテナ) でも ./gradlew :core:test が通ること。
dependencies {
    testImplementation(libs.junit)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<Test> {
    useJUnit()
    testLogging { events("passed", "failed", "skipped") }
}
