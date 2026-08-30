import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystorePath = providers.environmentVariable("TOOLBOX_KEYSTORE_PATH")
val releaseStorePassword = providers.environmentVariable("TOOLBOX_KEYSTORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("TOOLBOX_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("TOOLBOX_KEY_PASSWORD")

android {
    namespace = "io.toolbox.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.toolbox.app"
        minSdk = 30
        targetSdk = 30
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    signingConfigs {
        if (
            releaseKeystorePath.isPresent &&
            releaseStorePassword.isPresent &&
            releaseKeyAlias.isPresent &&
            releaseKeyPassword.isPresent
        ) {
            create("release") {
                storeFile = File(releaseKeystorePath.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        animationsDisabled = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf(
            "-Xjsr305=strict",
            "-Xjvm-default=all"
        )
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1"
        )
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = true
        checkDependencies = true
        htmlReport = true
        xmlReport = true
        // API 30 is an explicit product contract in AGENTS.md, not an accidental stale target.
        disable += "ExpiredTargetSdkVersion"
    }
}

// Conflict failure is intentionally scoped to the final APK product graph.
// AGP internal UTP/tool configurations may legitimately converge multiple tool versions;
// all resolved artifacts remain locked and checksum-verified independently.
val releaseProductConfigurations = setOf("releaseCompileClasspath", "releaseRuntimeClasspath")
configurations.configureEach {
    if (name in releaseProductConfigurations) {
        resolutionStrategy.failOnVersionConflict()
    }
}

dependencies {
    implementation(project(":toolbox-kernel"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")

    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0")
    androidTestUtil("androidx.test:orchestrator:1.6.1")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.register("writeReleaseDependencyInventory") {
    description = "Writes exact release runtime dependency inventory used as R6 evidence."
    group = "verification"
    val output = layout.buildDirectory.file("reports/verification/release-dependencies.txt")
    outputs.file(output)
    doLast {
        val releaseRuntime = configurations.getByName("releaseRuntimeClasspath")
        val lines = releaseRuntime.resolvedConfiguration.resolvedArtifacts
            .map { artifact ->
                val id = artifact.moduleVersion.id
                "${id.group}:${id.name}:${id.version}|${artifact.file.name}|${artifact.file.length()}"
            }
            .sorted()
        val file = output.get().asFile
        file.parentFile.mkdirs()
        file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    }
}
