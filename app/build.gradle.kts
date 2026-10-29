plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.recompress.bypass"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.recompress.bypass"
        minSdk = 26
        targetSdk = 35
        versionCode = 11400
        versionName = "1.1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}

abstract class ExportApkTask : DefaultTask() {
    @get:InputDirectory
    abstract val apkSourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val destinationDir: DirectoryProperty

    @get:Input
    abstract val versionName: Property<String>

    @TaskAction
    fun export() {
        val destDir = destinationDir.get().asFile
        if (!destDir.exists()) destDir.mkdirs()

        val srcDir = apkSourceDir.get().asFile
        val ver = versionName.get()

        val debugApk = File(srcDir, "debug/app-debug.apk")
        if (debugApk.exists()) {
            val destDebug = File(destDir, "RecompressBypass-v${ver}-debug.apk")
            debugApk.copyTo(destDebug, overwrite = true)
            val sizeMb = destDebug.length() / (1024.0 * 1024.0)
            println("[RELEASE EXPORT] Copied ${debugApk.name} -> release/${destDebug.name} (" + String.format("%.2f", sizeMb) + " MB)")
        }

        val releaseApk = File(srcDir, "release/app-release.apk")
        if (releaseApk.exists()) {
            val destRelease = File(destDir, "RecompressBypass-v${ver}-release.apk")
            releaseApk.copyTo(destRelease, overwrite = true)
            val sizeMb = destRelease.length() / (1024.0 * 1024.0)
            println("[RELEASE EXPORT] Copied ${releaseApk.name} -> release/${destRelease.name} (" + String.format("%.2f", sizeMb) + " MB)")
        }
    }
}

tasks.register<ExportApkTask>("copyApksToRelease") {
    apkSourceDir.set(layout.buildDirectory.dir("outputs/apk"))
    destinationDir.set(rootProject.layout.projectDirectory.dir("release"))
    versionName.set(android.defaultConfig.versionName ?: "latest")
}

tasks.configureEach {
    if (name == "assembleDebug" || name == "assembleRelease") {
        finalizedBy("copyApksToRelease")
    }
}
