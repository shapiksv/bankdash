plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.spring.boot.gradle.plugin)
    // Makes the version catalog usable from precompiled script plugins.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
