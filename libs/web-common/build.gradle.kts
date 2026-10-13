// What every business service's HTTP API shares: RFC 9457 errors (including 401s), the stateless JWT
// resource-server setup, and @CurrentUser. Spring Boot auto-configuration; see WebCommonAutoConfiguration.
plugins {
    id("bankdash.java-library")
}

dependencies {
    api(libs.spring.boot.starter.webmvc)
    api(libs.spring.boot.starter.security)
    api(libs.spring.boot.starter.security.oauth2.resource.server)
    api(libs.spring.boot.starter.validation)

    testImplementation(testFixtures(project(":libs:test-support")))
    testImplementation(libs.spring.boot.starter.webmvc.test)
}
