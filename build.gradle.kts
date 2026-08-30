plugins {
    kotlin("jvm") version "1.9.24" apply false
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.1"
}

allprojects {
    group = "io.toolbox"
    version = "0.3.0"

    dependencyLocking {
        lockAllConfigurations()
    }
}
