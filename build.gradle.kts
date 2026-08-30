plugins {
    kotlin("jvm") version "1.9.24" apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.1"
}

allprojects {
    group = "io.toolbox"
    version = "0.3.0"

    repositories {
        mavenCentral()
    }
}
