// Conventions for a runnable Spring Boot microservice under services/.
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("bankdash.java-library")
    id("org.springframework.boot")
}

val libs = the<LibrariesForLibs>()

dependencies {
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.webmvc)
}

springBoot {
    buildInfo()
}
