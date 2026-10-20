// Base conventions for every Java project: Java 25 toolchain, Spring BOMs, JUnit 5.
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    `java-library`
}

val libs = the<LibrariesForLibs>()

group = "com.bankdash"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
    }
}

dependencies {
    // Platforms are added to every bucket so tests and test fixtures get managed versions too.
    listOf("implementation", "testImplementation", "annotationProcessor").forEach { conf ->
        add(conf, platform(libs.spring.boot.dependencies))
        add(conf, platform(libs.spring.cloud.dependencies))
    }

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

pluginManager.withPlugin("java-test-fixtures") {
    dependencies {
        add("testFixturesImplementation", platform(libs.spring.boot.dependencies))
        add("testFixturesImplementation", platform(libs.spring.cloud.dependencies))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Deterministic time zone. The PostgreSQL driver sends the JVM zone on connect, and Postgres
    // rejects deprecated aliases such as Europe/Kiev that some hosts still use.
    systemProperty("user.timezone", "UTC")
    // libs/test-support reads infra/kafka/topics.txt from here to create the topics in the test broker.
    systemProperty("bankdash.repo-root", rootDir.absolutePath)
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
