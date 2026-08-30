plugins {
    kotlin("jvm") version "1.9.24" apply false
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

allprojects {
    group = "io.toolbox"
    version = "0.1.0"

    dependencyLocking {
        lockAllConfigurations()
    }

    tasks.register("resolveAndLockAll") {
        notCompatibleWithConfigurationCache("Resolves configurations at execution time for dependency trust closure")
        doFirst {
            require(gradle.startParameter.isWriteDependencyLocks) {
                "$path must be run with --write-locks"
            }
        }
        doLast {
            configurations
                .filter { it.isCanBeResolved }
                .forEach { it.resolve() }
        }
    }
}
