pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "bankdash"

// Every folder under libs/ and services/ that has a build script becomes a subproject,
// e.g. libs/test-support -> :libs:test-support.
listOf("libs", "services").forEach { group ->
    rootDir.resolve(group).listFiles()
        ?.filter { it.isDirectory && it.resolve("build.gradle.kts").exists() }
        ?.sortedBy { it.name }
        ?.forEach { include(":$group:${it.name}") }
}
