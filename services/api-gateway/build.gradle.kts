// Single entry point /api/**: routes to the services, validates JWTs against auth-service's JWKS, CORS.
plugins {
    id("bankdash.spring-service")
}

dependencies {
    implementation(libs.spring.cloud.starter.gateway.server.webmvc)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)

    testImplementation(testFixtures(project(":libs:test-support")))
}
