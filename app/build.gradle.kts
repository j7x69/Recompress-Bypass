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
        versionCode = 12000
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
    ndkVersion = "27.2.12479018"

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

    lint {
        disable += setOf(
            "OldTargetApi",
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            "UseTomlInstead",
            "ObsoleteSdkInt",
            "UnusedResources",
            "IconLauncherShape",
            "MonochromeLauncherIcon",
            "IconDuplicatesConfig",
            "IconLocation"
        )
    }
}

@Suppress("UseTomlInstead", "GradleDependency")
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
            val sizeMb = ((destDebug.length() * 100L) / (1024L * 1024L)) / 100.0
            println("[RELEASE EXPORT] Copied ${debugApk.name} -> release/${destDebug.name} ($sizeMb MB)")
        }

        val releaseApk = File(srcDir, "release/app-release.apk")
        if (releaseApk.exists()) {
            val destRelease = File(destDir, "RecompressBypass-v${ver}-release.apk")
            releaseApk.copyTo(destRelease, overwrite = true)
            val sizeMb = ((destRelease.length() * 100L) / (1024L * 1024L)) / 100.0
            println("[RELEASE EXPORT] Copied ${releaseApk.name} -> release/${destRelease.name} ($sizeMb MB)")
        }
    }
}

tasks.register<ExportApkTask>("copyApksToRelease") {
    apkSourceDir.set(layout.buildDirectory.dir("outputs/apk"))
    destinationDir.set(rootProject.layout.projectDirectory.dir("release"))
    versionName.set(android.defaultConfig.versionName ?: "latest")
}

abstract class BuildRustJniTask : DefaultTask() {
    @get:OutputDirectory
    abstract val jniLibsDir: DirectoryProperty

    @TaskAction
    fun buildRust() {
        val outJniDir = jniLibsDir.get().asFile
        val rustDir = File(outJniDir.parentFile, "rust")
        val cargoToml = File(rustDir, "Cargo.toml")
        if (!cargoToml.exists()) {
            println("[RUST JNI] Local Rust source hidden/skipped, using existing native libraries.")
            return
        }
        val userHome = System.getProperty("user.home")
        val cargoBin = File(userHome, ".cargo/bin/cargo.exe").let {
            if (it.exists()) it.absolutePath else "cargo"
        }

        val targets = mapOf(
            "aarch64-linux-android" to "arm64-v8a",
            "armv7-linux-androideabi" to "armeabi-v7a",
            "x86_64-linux-android" to "x86_64"
        )

        for ((rustTarget, androidAbi) in targets) {
            val proc = ProcessBuilder(cargoBin, "build", "--target", rustTarget, "--release")
                .directory(rustDir)
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            val exitCode = proc.waitFor()
            if (exitCode != 0) {
                throw GradleException("Rust build failed for $rustTarget:\n$output")
            }

            val builtSo = File(rustDir, "target/$rustTarget/release/librecompress_core.so")
            val abiDir = File(outJniDir, androidAbi).apply { mkdirs() }
            builtSo.copyTo(File(abiDir, "librecompress_core.so"), overwrite = true)
            println("[RUST JNI] Packaged $rustTarget -> jniLibs/$androidAbi/librecompress_core.so (${builtSo.length()} bytes)")
        }
    }
}

tasks.register<BuildRustJniTask>("buildRustJniLibs") {
    jniLibsDir.set(layout.projectDirectory.dir("src/main/jniLibs"))
}

tasks.configureEach {
    if (name == "preBuild") {
        dependsOn("buildRustJniLibs")
    }
    if (name == "assembleDebug" || name == "assembleRelease") {
        finalizedBy("copyApksToRelease")
    }
}
