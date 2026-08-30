plugins {
    kotlin("jvm")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "11"
}

// Version conflicts are fatal for production code classpaths owned by ToolBox.
// Test/build-tool graphs remain exact through dependency locking + artifact verification
// without treating legitimate tool-internal convergence as an application defect.
val productionConfigurations = setOf("compileClasspath", "runtimeClasspath")
configurations.configureEach {
    if (name in productionConfigurations) {
        resolutionStrategy.failOnVersionConflict()
    }
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}
