// Conventions for a runnable Spring Boot microservice under services/.
import org.gradle.accessors.dm.LibrariesForLibs
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("bankdash.java-library")
    id("org.springframework.boot")
}

val libs = the<LibrariesForLibs>()

dependencies {
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.webmvc)

    testImplementation(libs.spring.boot.starter.webmvc.test)
}

springBoot {
    buildInfo()
}

// Local runs use the "dev" profile unless SPRING_PROFILES_ACTIVE says otherwise, and UTC for the
// same reason as tests (see bankdash.java-library).
tasks.named<BootRun>("bootRun") {
    systemProperty("spring.profiles.default", "dev")
    systemProperty("user.timezone", "UTC")
}
